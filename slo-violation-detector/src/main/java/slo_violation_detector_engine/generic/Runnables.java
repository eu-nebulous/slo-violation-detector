package slo_violation_detector_engine.generic;

//import eu.melodic.event.brokerclient.BrokerPublisher;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import slo_violation_detector_engine.detector.DetectorSubcomponentUtilities;
import utility_beans.BrokerPublisher;
import org.json.simple.JSONObject;
import slo_rule_modelling.SLORule;
import utility_beans.CharacterizedThread;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static java.lang.Thread.sleep;
import static slo_rule_modelling.SLORule.process_rule_value;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.*;
import static slo_violation_detector_engine.generic.SLOViolationDetectorStateUtils.*;
import static slo_violation_detector_engine.detector.DetectorSubcomponentUtilities.*;
import static utilities.DebugDataSubscription.*;

public class Runnables {

    public static class DebugDataRunnable implements Runnable{
        DetectorSubcomponent detector;
        public DebugDataRunnable(DetectorSubcomponent detector){
            this.detector = detector;
        }
        @Override
        public void run() {
            try {
                synchronized (detector.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(debug_data_trigger_topic_name)) {
                    //if (Main.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(debug_data_topic_name).getValue())
                    debug_data_subscriber.subscribe(debug_data_generation, detector.stop_signal);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            } catch (Exception i) {
                Logger.getAnonymousLogger().log(info_logging_level, "Possible interruption of debug data subscriber thread for " + debug_data_trigger_topic_name + " - if not stacktrace follows");
                if (!(i instanceof InterruptedException)) {
                    i.printStackTrace();
                }
            } finally {
                Logger.getAnonymousLogger().log(info_logging_level, "Removing debug data subscriber thread for " + debug_data_trigger_topic_name);
                detector.getSubcomponent_state().slo_bound_running_threads.remove("debug_data_subscription_thread_" + debug_data_trigger_topic_name);
            }
        }
    }

    public static Runnable device_lost_topic_subscriber_runnable = () -> {
        while (true) {
            device_lost_subscriber.subscribe(device_lost_subscriber_function, new AtomicBoolean(false)); //This subscriber should be immune to stop signals
            Logger.getAnonymousLogger().log(info_logging_level,"A device used by the platform was lost, will therefore trigger a reconfiguration");
            try {
                Thread.sleep(10000);
            }catch (InterruptedException i){
                Logger.getAnonymousLogger().log(info_logging_level,"Sleep was interrupted, will immediately try to connect to the broker");
            }
        }
    };


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
            BrokerPublisher persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);

            while (!detector.stop_signal.get()) {
                synchronized (detector.PREDICTION_EXISTS) {
                    while (!detector.PREDICTION_EXISTS.getValue()) {
                        try {
                            detector.PREDICTION_EXISTS.wait();
                        } catch (InterruptedException e) {
                            synchronized (detector.stop_signal) {
                                if (detector.stop_signal.get()) {
                                    detector.getSubcomponent_state().slo_bound_running_threads.remove("severity_calculation_thread_" + rule.toString());
                                    detector.PREDICTION_EXISTS.setValue(false);
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
                                Logger.getAnonymousLogger().log(warning_logging_level, "Interrupted while waiting to access the lock for adaptation times object");
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
                    Logger.getAnonymousLogger().log(info_logging_level, "Targeted_prediction_time " + targeted_prediction_time);
                    Runnable internal_severity_calculation_runnable = () -> {
                        try {
                            synchronized (detector.PREDICTION_EXISTS) {
                                detector.PREDICTION_EXISTS.setValue(detector.getSubcomponent_state().adaptation_times.size() > 0);
                            }

                            Long sleep_time = targeted_prediction_time * 1000 - time_horizon_seconds * 1000L - current_time;
                            if (sleep_time <= 0) {
                                Logger.getAnonymousLogger().log(info_logging_level, "Prediction cancelled as targeted prediction time was " + targeted_prediction_time * 1000 + " current time is " + current_time + " and the time_horizon is " + time_horizon_seconds * 1000);
                                return; //The predictions are too near to the targeted reconfiguration time (or are even obsolete)
                            } else if (sleep_time > current_time + maximum_acceptable_forward_predictions * time_horizon_seconds * 1000L) {
                                Logger.getAnonymousLogger().log(info_logging_level, "Prediction cancelled as targeted prediction time was " + targeted_prediction_time * 1000 + " and the current time is " + current_time + ". The prediction is more than " + maximum_acceptable_forward_predictions + " time_horizon intervals into the future (the time_horizon is " + time_horizon_seconds * 1000 + " milliseconds)");
                                return; //The predictions are too near to the targeted reconfiguration tim
                            }
                            Logger.getAnonymousLogger().log(info_logging_level, "Sleeping for " + sleep_time + " milliseconds");
                            sleep(sleep_time);
                            double rule_severity = process_rule_value(rule, targeted_prediction_time);
                            double slo_violation_probability = determine_slo_violation_probability(rule_severity);
                            Logger.getAnonymousLogger().log(info_logging_level, "The overall " + slo_violation_determination_method + " severity - calculated from real data - for adaptation time " + targeted_prediction_time + " ( " + (new Date((new Timestamp(targeted_prediction_time * 1000)).getTime())) + " ) is " + rule_severity + " and is calculated " + time_horizon_seconds + " seconds beforehand");
                            Logger.getAnonymousLogger().log(info_logging_level, "The probability of an SLO violation is " + ((int) (slo_violation_probability * 100)) + "%" + (slo_violation_probability < slo_violation_probability_threshold ? " so it will not be published" : " and it will be published"));

                            if (slo_violation_probability >= slo_violation_probability_threshold) {
                                JSONObject severity_json = new JSONObject();
                                severity_json.put("severity", rule_severity);
                                severity_json.put("probability", slo_violation_probability);
                                severity_json.put("predictionTime", targeted_prediction_time);
                                persistent_publisher.publish(severity_json.toJSONString());
                            }

                            detector.getSubcomponent_state().slo_violation_event_recording_queue.add(System.currentTimeMillis());

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
                            Logger.getAnonymousLogger().log(severe_logging_level, "Severity calculation thread for epoch time " + targeted_prediction_time + " interrupted, stopping...");
                            return;
                        }
                    };
                    CharacterizedThread.create_new_thread(internal_severity_calculation_runnable, "internal_severity_calculation_thread_" + targeted_prediction_time, true,detector);
                } catch (NoSuchElementException n) {
                    Logger.getAnonymousLogger().log(warning_logging_level, "Could not calculate severity as a value was missing...");
                    continue;
                }
            }
            detector.getSubcomponent_state().slo_bound_running_threads.remove("severity_calculation_thread_" + rule.toString());
        };
        return severity_calculation_runnable;
    }
}
