package processing_logic;

import eu.melodic.event.brokerclient.BrokerPublisher;
import org.json.simple.JSONObject;
import slo_processing.SLORule;
import utility_beans.CharacterizedThread;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Date;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static java.lang.Thread.sleep;
import static runtime.Main.run_slo_violation_detection_engine;
import static slo_processing.SLORule.process_rule_value;
import static utilities.SLOViolationDetectorStateUtils.*;
import static utilities.SLOViolationDetectorUtils.*;
import static utility_beans.CharacterizedThread.CharacterizedThreadType.slo_bound_running_thread;

public class Runnables {
    public static Runnable slo_detector_mode_runnable = () -> {
        try {
            run_slo_violation_detection_engine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    };

    public static Runnable get_severity_calculation_runnable(SLORule rule) {

        Runnable severity_calculation_runnable = () -> {
            BrokerPublisher persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);

            while (!stop_signal.get()) {
                    /*try {
                        Thread.sleep(time_horizon_seconds*1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }*/
                synchronized (PREDICTION_EXISTS) {
                    while (!PREDICTION_EXISTS.getValue()) {
                        try {
                            PREDICTION_EXISTS.wait();
                        } catch (InterruptedException e) {
                            synchronized (stop_signal) {
                                if (stop_signal.get()) {
                                    slo_bound_running_threads.remove("severity_calculation_thread_" + rule.toString());
                                    PREDICTION_EXISTS.setValue(false);
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
                    synchronized (ADAPTATION_TIMES_MODIFY) {
                        while (!ADAPTATION_TIMES_MODIFY.getValue()) {
                            try {
                                ADAPTATION_TIMES_MODIFY.wait();
                            } catch (InterruptedException e) {
                                Logger.getAnonymousLogger().log(warning_logging_level, "Interrupted while waiting to access the lock for adaptation times object");
                                e.printStackTrace();
                            }
                        }
                        ADAPTATION_TIMES_MODIFY.setValue(false);
                        clean_data(adaptation_times_to_remove);
                        //targeted_prediction_time = adaptation_times.stream().min(Long::compare).get();
                        targeted_prediction_time = get_next_targeted_prediction_time();
                        ADAPTATION_TIMES_MODIFY.setValue(true);
                        ADAPTATION_TIMES_MODIFY.notifyAll();
                    }
                    if (targeted_prediction_time == null) {
                        continue;
                    }
                    Logger.getAnonymousLogger().log(info_logging_level, "Targeted_prediction_time " + targeted_prediction_time);
                    Runnable internal_severity_calculation_runnable = () -> {
                        try {
                                /*
                                synchronized (ADAPTATION_TIMES_MODIFY) {
                                    while (!ADAPTATION_TIMES_MODIFY.getValue()) {
                                        ADAPTATION_TIMES_MODIFY.wait();
                                    }
                                    ADAPTATION_TIMES_MODIFY.setValue(false);
                                    adaptation_times.remove(targeted_prediction_time);//remove from the list of timepoints which should be processed. Later this timepoint will be added to the adaptation_times_to_remove HashSet to remove any data associated with it
                                    ADAPTATION_TIMES_MODIFY.setValue(true);
                                    ADAPTATION_TIMES_MODIFY.notifyAll();
                                }
                                //adaptation_times_pending_processing.add(targeted_prediction_time);

                                 */
                            synchronized (PREDICTION_EXISTS) {
                                PREDICTION_EXISTS.setValue(adaptation_times.size() > 0);
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
                            double rule_severity = process_rule_value(rule.getRule_representation(), targeted_prediction_time, rule.getRule_format());
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

                            slo_violation_event_recording_queue.add(System.currentTimeMillis());

                            //Probably not necessary to synchronize the line below as each removal will happen only once in a reconfiguration interval, and reconfiguration intervals are assumed to have a duration of minutes.
                            //Necessary to synchronize because another severity calculation thread might invoke clean_data above, and then a concurrent modification exception may arise
                            synchronized (ADAPTATION_TIMES_MODIFY) {
                                while (!ADAPTATION_TIMES_MODIFY.getValue()) {
                                    ADAPTATION_TIMES_MODIFY.wait();
                                }
                                ADAPTATION_TIMES_MODIFY.setValue(false);
                                adaptation_times_to_remove.add(targeted_prediction_time); //This line serves a different purpose from the adaptation_times.remove(...) directive above, as the adaptation_times_to_remove HashSet contains timepoints which should be processed to delete their data.
                                adaptation_times_pending_processing.remove(targeted_prediction_time);
                                ADAPTATION_TIMES_MODIFY.setValue(true);
                                ADAPTATION_TIMES_MODIFY.notifyAll();
                            }
                        } catch (InterruptedException i) {
                            Logger.getAnonymousLogger().log(severe_logging_level, "Severity calculation thread for epoch time " + targeted_prediction_time + " interrupted, stopping...");
                            return;
                        }
                    };
                    CharacterizedThread.create_new_thread(internal_severity_calculation_runnable, "internal_severity_calculation_thread_" + targeted_prediction_time, slo_bound_running_thread, true);
                } catch (NoSuchElementException n) {
                    Logger.getAnonymousLogger().log(warning_logging_level, "Could not calculate severity as a value was missing...");
                    continue;
                }
            }
            slo_bound_running_threads.remove("severity_calculation_thread_" + rule.toString());
        };
        return severity_calculation_runnable;
    }
}
