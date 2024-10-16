package slo_violation_detector_engine.generic;

//import eu.melodic.event.brokerclient.BrokerPublisher;
import reinforcement_learning.SeverityClassModel;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import slo_violation_detector_engine.detector.DetectorSubcomponentUtilities;
import utility_beans.broker_communication.BrokerPublisher;
import org.json.simple.JSONObject;
import slo_rule_modelling.SLORule;
import utility_beans.broker_communication.BrokerSubscriber;
import utility_beans.broker_communication.BrokerSubscriptionDetails;
import utility_beans.generic_component_functionality.CharacterizedThread;import utility_beans.reconfiguration_suggestion.DecisionMaker;import utility_beans.reconfiguration_suggestion.ReconfigurationDetails;import utility_beans.reconfiguration_suggestion.SLOViolation;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Collections;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static java.lang.Thread.sleep;
import static slo_rule_modelling.SLORule.process_rule_value;
import static slo_violation_detector_engine.generic.ComponentState.*;
import static slo_violation_detector_engine.detector.DetectorSubcomponentUtilities.*;
import static utilities.DebugDataSubscription.*;

public class Runnables {

    public static class DebugDataRunnable implements Runnable{
        private DetectorSubcomponent detector; //TODO Verify whether or not we need this message per detector or on a detector-independent, all-application way
        public DebugDataRunnable(DetectorSubcomponent detector){
            this.detector = detector;
        }
        @Override
        public void run() {
            try {
                Logger.getGlobal().log(info_logging_level,"Starting to subscribe to debug output trigger");
                synchronized (detector.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(debug_data_trigger_topic_name)) {
                    //if (Main.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(debug_data_topic_name).getValue())
                    BrokerSubscriptionDetails broker_details = detector.getBrokerSubscriptionDetails(debug_data_trigger_topic_name);
                    debug_data_subscriber = new BrokerSubscriber(debug_data_trigger_topic_name, broker_details.getBroker_ip(),broker_details.getBroker_port(),broker_details.getBroker_username(),broker_details.getBroker_password(), amq_library_configuration_location,detector.get_application_name());
                    debug_data_subscriber.subscribe(debug_data_generation, EMPTY,detector.stop_signal);
                    Logger.getGlobal().log(info_logging_level,"Debug data subscriber initiated");
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            } catch (Exception i) {
                Logger.getGlobal().log(severe_logging_level, "Possible interruption of debug data subscriber thread for " + debug_data_trigger_topic_name + " - if not stacktrace follows");
                if (!(i instanceof InterruptedException)) {
                    i.printStackTrace();
                }
            } finally {
                Logger.getGlobal().log(info_logging_level, "Removing debug data subscriber thread for " + debug_data_trigger_topic_name);
                detector.getSubcomponent_state().slo_bound_running_threads.remove("debug_data_subscription_thread_" + debug_data_trigger_topic_name);
            }
        }
    }


    public static class SLODetectionEngineRunnable implements Runnable {
        private DetectorSubcomponent detector;
        public SLODetectionEngineRunnable(DetectorSubcomponent detector) {
            this.detector = detector;
        }
        @Override
        public void run() {
            run_slo_violation_detection_engine(detector);
        }
    }

    public static Runnable get_severity_calculation_runnable(SLORule rule, DetectorSubcomponent detector) {

        Runnable severity_calculation_runnable = () -> {
            Logger.getGlobal().log(info_logging_level,"Will now attempt to get the BrokerPublisher connector for application "+detector.get_application_name());
            BrokerPublisher persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, broker_ip,broker_port,broker_username,broker_password, amq_library_configuration_location);

            int attempts = 1;
            while (persistent_publisher.is_publisher_null()){
                if (attempts<=2) {
                    Logger.getGlobal().log(warning_logging_level,"Will now attempt to reset the BrokerPublisher connector for application "+detector.get_application_name());
                    persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, broker_ip, broker_port, broker_username, broker_password, amq_library_configuration_location);
                }else{
                    Logger.getGlobal().log(warning_logging_level,"Will now attempt to reset the BrokerPublisher connector for application "+detector.get_application_name());
                    persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, broker_ip, broker_port, broker_username, broker_password, amq_library_configuration_location,true);
                }
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                attempts++;
            }

            while (true) {
                synchronized (detector.PREDICTION_EXISTS) {
                    while (!detector.PREDICTION_EXISTS.getValue()) {
                        try {
                            detector.PREDICTION_EXISTS.wait();
                        } catch (InterruptedException e) {
                            synchronized (detector.stop_signal) {
                                if (detector.stop_signal.get()) {
                                    detector.getSubcomponent_state().slo_bound_running_threads.remove("severity_calculation_thread_" + rule.toString());
                                    detector.PREDICTION_EXISTS.setValue(false);
                                    detector.getSubcomponent_state().slo_bound_running_threads.remove("severity_calculation_thread_" + rule.toString());
                                    return;
                                }
                            }
                            e.printStackTrace();
                        }
                    }

                    }
                    try {
                        Clock clock = Clock.systemUTC();
                        Long current_time = clock.millis();
                        Long targeted_prediction_time;
                        synchronized (detector.ADAPTATION_TIMES_MODIFY) {
                            while (!detector.ADAPTATION_TIMES_MODIFY.getValue()) {
                                try {
                                    detector.ADAPTATION_TIMES_MODIFY.wait();
                                } catch (InterruptedException e) {
                                    Logger.getGlobal().log(warning_logging_level, "Interrupted while waiting to access the lock for adaptation times object");
                                    e.printStackTrace();
                                }
                            }
                            detector.ADAPTATION_TIMES_MODIFY.setValue(false);
                            DetectorSubcomponentUtilities.clean_data(detector);
                            //targeted_prediction_time = adaptation_times.stream().min(Long::compare).get();
                            targeted_prediction_time = DetectorSubcomponentUtilities.get_next_targeted_prediction_time(detector);
                            detector.ADAPTATION_TIMES_MODIFY.setValue(true);
                            detector.ADAPTATION_TIMES_MODIFY.notifyAll();
                        }
                        if (targeted_prediction_time == null) {
                            continue;
                        }
                        Logger.getGlobal().log(info_logging_level, "Targeted_prediction_time " + targeted_prediction_time);
                        BrokerPublisher finalPersistent_publisher = persistent_publisher;
                        Runnable internal_severity_calculation_runnable = () -> {
                            try {
                                synchronized (detector.PREDICTION_EXISTS) {
                                    detector.PREDICTION_EXISTS.setValue(detector.getSubcomponent_state().adaptation_times.size() > 0);
                                }

                                Long sleep_time = targeted_prediction_time - buffer_time - time_horizon_seconds * 1000L - current_time;
                                Long adjusted_buffer_time = buffer_time;
                                if (sleep_time <= 0) {
                                    if (sleep_time < -buffer_time) {
                                        Logger.getGlobal().log(warning_logging_level, "Prediction will not be used as targeted prediction time was " + targeted_prediction_time + " current time is " + current_time + " and the time_horizon is " + time_horizon_seconds * 1000 + " (sleep time would be " + sleep_time + "ms )");
                                        return; //The predictions are too near to the targeted reconfiguration time (or are even obsolete)
                                    } else {
                                        Logger.getGlobal().log(info_logging_level, "Diminishing buffer time to 0 to check prediction NOW!");
                                        adjusted_buffer_time = 0L;
                                        sleep_time = 0L;
                                    }
                                } else if (sleep_time > current_time + maximum_acceptable_forward_predictions * time_horizon_seconds * 1000L) {
                                    Logger.getGlobal().log(warning_logging_level, "Prediction cancelled as targeted prediction time was " + targeted_prediction_time + " and the current time is " + current_time + ". The prediction is more than " + maximum_acceptable_forward_predictions + " time_horizon intervals into the future (the time_horizon is " + time_horizon_seconds * 1000 + " milliseconds)");
                                    return; //The predictions are too near to the targeted reconfiguration tim
                                }
                                Logger.getGlobal().log(debug_logging_level, "Sleeping for " + sleep_time + " milliseconds");


                                //double rule_severity = process_rule_value(rule, targeted_prediction_time);

                                ReconfigurationDetails reconfiguration_details;
                                double rule_severity;
                                double slo_violation_probability;
                                SLOViolation current_slo_violation;
                                double normalized_rule_severity;
                                
                                if (slo_violation_feedback_method.equals("using_q_learning_severity_threshold")) {
                                    sleep(sleep_time);
                                    rule_severity = process_rule_value(rule, targeted_prediction_time);
                                    normalized_rule_severity = rule_severity / 100;
                                    current_slo_violation = new SLOViolation(normalized_rule_severity);
                                    CircularFifoQueue<ReconfigurationDetails> reconfiguration_queue = detector.getSubcomponent_state().getReconfiguration_time_recording_queue();
                                    if (detector.getDm()!=null){
                                        detector.getSubcomponent_state().submitSLOViolation(current_slo_violation);
                                    }else {
                                        SeverityClassModel scm = new SeverityClassModel(number_of_severity_classes, true);
                                        detector.setDm(new DecisionMaker(scm, reconfiguration_queue,detector.getSubcomponent_state()));
                                        detector.getSubcomponent_state().submitSLOViolation(current_slo_violation);
                                    }
                                    
                                    sleep(adjusted_buffer_time); //Breaking sleep into two parts (sleep_time and adjusted_buffer_time) to allow possibly other slo violations to be gathered during adjusted_buffer_time and only use the highest one. Overdoing it (having large buffer times), may result in ignoring recent realtime/predicted metric data sent during adjusted_buffer_time
                                    reconfiguration_details = detector.getDm().processSLOViolations(Optional.empty());
                                    slo_violation_probability = reconfiguration_details.getReconfiguration_probability();
                                    
                                } else if (slo_violation_feedback_method.equals("none")) {
                                    sleep(sleep_time + adjusted_buffer_time); //Not interested in other SLO Violations, directly processing any SLOs
                                    rule_severity = process_rule_value(rule, targeted_prediction_time);
                                    normalized_rule_severity = rule_severity / 100;
                                    slo_violation_probability = determine_slo_violation_probability(rule_severity);
                                    current_slo_violation = new SLOViolation(normalized_rule_severity);
                                    Logger.getGlobal().log(info_logging_level, "The overall " + severity_calculation_method + " severity - calculated from real data - for adaptation time " + targeted_prediction_time + " ( " + (new Date((new Timestamp(targeted_prediction_time )).getTime())) + " ) is " + rule_severity + " and is calculated " + time_horizon_seconds + " seconds beforehand");
                                    Logger.getGlobal().log(info_logging_level, "The probability of an SLO violation is " + ((int) (slo_violation_probability * 100)) + "%" + (slo_violation_probability < slo_violation_probability_threshold ? " so it will not be published" : " and it will be published"));

                                    
                                    if (slo_violation_probability >= slo_violation_probability_threshold) {
                                        reconfiguration_details = new ReconfigurationDetails(slo_violation_probability,rule_severity,true,-1,targeted_prediction_time);
                                    }else{
                                        reconfiguration_details = ReconfigurationDetails.get_details_for_noop_reconfiguration();
                                    }


                                }else{
                                    reconfiguration_details = ReconfigurationDetails.get_details_for_noop_reconfiguration();
                                    rule_severity = -100;
                                    normalized_rule_severity = -1;
                                    slo_violation_probability = 0;
                                    current_slo_violation=new SLOViolation(-1.0);
                                }

                                if (reconfiguration_details.will_reconfigure()) {
                                    JSONObject severity_json = new JSONObject();
                                    if (publish_normalized_severity) {
                                        rule_severity = rule_severity / 100;
                                    }
                                    severity_json.put("severity", rule_severity);

                                    severity_json.put("probability", slo_violation_probability);
                                    severity_json.put("predictionTime", targeted_prediction_time);
                                    finalPersistent_publisher.publish(severity_json.toJSONString(), Collections.singleton(detector.get_application_name()));
                                    
                                    Logger.getGlobal().log(debug_logging_level,"Adding violation record for violation "+current_slo_violation.getId()+" to database");
                                    detector.getSubcomponent_state().add_violation_record(detector.get_application_name(), rule.getRule_representation().toJSONString(), normalized_rule_severity, reconfiguration_details.getCurrent_slo_threshold(), targeted_prediction_time);
                                    detector.getSubcomponent_state().reconfiguration_time_recording_queue.add(reconfiguration_details);

                                }

                                //Probably not necessary to synchronize the line below as each removal will happen only once in a reconfiguration interval, and reconfiguration intervals are assumed to have a duration of minutes.
                                //Necessary to synchronize because another severity calculation thread might invoke clean_data above, and then a concurrent modification exception may arise
                                synchronized (detector.ADAPTATION_TIMES_MODIFY) {
                                    while (!detector.ADAPTATION_TIMES_MODIFY.getValue()) {
                                        detector.ADAPTATION_TIMES_MODIFY.wait();
                                    }
                                    detector.ADAPTATION_TIMES_MODIFY.setValue(false);
                                    detector.getSubcomponent_state().adaptation_times_to_remove.add(targeted_prediction_time); //This line serves a different purpose from the adaptation_times.remove(...) directive above, as the adaptation_times_to_remove HashSet contains timepoints which should be processed to delete their data.
                                    detector.getSubcomponent_state().adaptation_times_pending_processing.remove(targeted_prediction_time);
                                    detector.ADAPTATION_TIMES_MODIFY.setValue(true);
                                    detector.ADAPTATION_TIMES_MODIFY.notifyAll();
                                }
                            } catch (InterruptedException i) {
                                Logger.getGlobal().log(severe_logging_level, "Severity calculation thread for epoch time " + targeted_prediction_time + " interrupted, stopping...");
                                return;
                            }
                            detector.getSubcomponent_state().slo_bound_running_threads.remove(Thread.currentThread().getName().split(NAME_SEPARATOR)[0]);
                        };
                        CharacterizedThread.create_new_thread(internal_severity_calculation_runnable, "internal_severity_calculation_thread_" + targeted_prediction_time, true, detector, CharacterizedThread.CharacterizedThreadType.slo_bound_running_thread);
                    } catch (NoSuchElementException n) {
                        Logger.getGlobal().log(warning_logging_level, "Could not calculate severity as a value was missing...");
                    }
                }
        };
        return severity_calculation_runnable;
    }
}
