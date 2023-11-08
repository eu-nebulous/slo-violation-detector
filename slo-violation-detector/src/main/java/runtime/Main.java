/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package runtime;

import eu.melodic.event.brokerclient.BrokerPublisher;
import eu.melodic.event.brokerclient.BrokerSubscriber;
import org.json.simple.JSONObject;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import slo_processing.SLORule;
import utilities.DebugDataSubscription;
import utility_beans.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static processing_logic.Runnables.slo_detector_mode_runnable;
import static utilities.OperationalModeUtils.getSLOViolationDetectionOperationalMode;
import static utilities.SLOViolationDetectorUtils.*;
import static utilities.SLOViolationDetectorStateUtils.*;
import static utility_beans.CharacterizedThread.CharacterizedThreadRunMode.attached;
import static utility_beans.CharacterizedThread.CharacterizedThreadRunMode.detached;
import static utility_beans.CharacterizedThread.CharacterizedThreadType.persistent_running_thread;
import static utility_beans.RealtimeMonitoringAttribute.getMonitoring_attributes_bounds_representation;

@RestController
@SpringBootApplication
public class Main {
    public static Long current_slo_rules_version = -1L;//initialization
    public static boolean EXECUTION_NOT_TERMINATED = true;
    public static void main(String[] args) {

        //The input of this program is the type of the SLO violations which are monitored, and the predicted metric values which are evaluated. Its output are events which produce an estimate of the probability of an adaptation.
        //The SLO violations which are monitored need to mention the following data:
        // - The name of the predicted metrics which are monitored as part of the rule
        // - The threshold and whether it is a more-than or less-than threshold
        //The predicted metrics need to include the following data:
        // - The predicted value
        // - The prediction confidence

        try {
            {
                InputStream inputStream = null;
                if (args.length == 0) {
                    operational_mode = getSLOViolationDetectionOperationalMode("DIRECTOR");
                    inputStream = getPreferencesFileInputStream(EMPTY);
                } else if (args.length == 1) {
                    Logger.getAnonymousLogger().log(info_logging_level, "Operational mode has been manually specified");
                    operational_mode = getSLOViolationDetectionOperationalMode(args[0]);
                    inputStream = getPreferencesFileInputStream(EMPTY);
                } else {
                    Logger.getAnonymousLogger().log(info_logging_level, "Operational mode and preferences file has been manually specified");
                    operational_mode = getSLOViolationDetectionOperationalMode(args[0]);
                    inputStream = getPreferencesFileInputStream(args[1]);

                }
                prop.load(inputStream);
                slo_rules_topic = prop.getProperty("slo_rules_topic");
                kept_values_per_metric = Integer.parseInt(prop.getProperty("stored_values_per_metric", "5"));
                self_publish_rule_file = Boolean.parseBoolean(prop.getProperty("self_publish_rule_file"));
                single_slo_rule_active = Boolean.parseBoolean(prop.getProperty("single_slo_rule_active"));
                time_horizon_seconds = Integer.parseInt(prop.getProperty("time_horizon_seconds"));

                slo_violation_probability_threshold = Double.parseDouble(prop.getProperty("slo_violation_probability_threshold"));
                slo_violation_determination_method = prop.getProperty("slo_violation_determination_method");
                maximum_acceptable_forward_predictions = Integer.parseInt(prop.getProperty("maximum_acceptable_forward_predictions"));
                ArrayList<String> unbounded_metric_strings = new ArrayList<String>(Arrays.asList(prop.getProperty("metrics_bounds").split(",")));
                for (String metric_string : unbounded_metric_strings) {
                    getMonitoring_attributes_bounds_representation().put(metric_string.split(";")[0], metric_string.split(";", 2)[1]);
                }
            } //initialization
            if (operational_mode.equals(OperationalMode.DETECTOR)) {
                start_new_slo_violation_detector_subcomponent(attached);
            }else if (operational_mode.equals(OperationalMode.DIRECTOR)){
                /*
                while (EXECUTION_NOT_TERMINATED) {
                    synchronized (create_new_slo_detector) {
                        create_new_slo_detector.wait();
                        for (int i=1; i<=create_new_slo_detector.getValue(); i++) {
                            CharacterizedThread.create_new_thread(slo_detector_mode_runnable, "slo_detector_master_thread", persistent_running_thread, true);
                        }
                        create_new_slo_detector.setValue(0);
                    }
                }
                */
                SpringApplication.run(Main.class, args);
                System.out.println("TEST");
                //TODO start a new DETECTOR instance when a relevant message appears
            }
        }catch (IOException e){
            Logger.getAnonymousLogger().log(info_logging_level,"Problem reading input file");
            e.printStackTrace();
        }catch (Exception e){
            Logger.getAnonymousLogger().log(info_logging_level,"Miscellaneous issue during startup");
            e.printStackTrace();
        }
    }
    @RequestMapping("/add-new-detector")
    public static String start_new_slo_violation_detector_subcomponent() throws IOException {
        start_new_slo_violation_detector_subcomponent(detached);
        return ("Spawned new SLO Detector subcomponent instance!");
    }
    public static void start_new_slo_violation_detector_subcomponent(CharacterizedThread.CharacterizedThreadRunMode characterized_thread_run_mode) throws IOException {
        if (characterized_thread_run_mode.equals(attached)) {
            run_slo_violation_detection_engine();
        }else/*detached mode*/{
            CharacterizedThread.create_new_thread(slo_detector_mode_runnable, "slo_detector_master_thread", persistent_running_thread, true);
        }
    }
    public static void run_slo_violation_detection_engine() throws IOException {
        while (true) {
            if (first_run){
                //Creation of threads that should always run and are independent of the monitored application.
                //1. Creation of the slo rule input subscriber thread, which listens for new slo rules to be considered
                //2. Creation of the lost device subscriber thread, which listens for a new event signalling a lost edge device

                BiFunction<String, String, String> slo_rule_topic_subscriber_function = (topic, message) -> {
                    synchronized (can_modify_slo_rules) {
                        can_modify_slo_rules.setValue(true);
                        MESSAGE_CONTENTS.assign_value(topic, message);
                        slo_rule_arrived.set(true);
                        can_modify_slo_rules.notifyAll();

                        Logger.getAnonymousLogger().log(info_logging_level, "BrokerClientApp:  - Received text message: " + message + " at topic " + topic);

                    }
                    return topic + ":MSG:" + message;
                };

                BrokerSubscriber slo_rule_topic_subscriber = new BrokerSubscriber(slo_rules_topic, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);
                Runnable slo_rules_topic_subscriber_runnable = () -> {
                    while (true) {
                        slo_rule_topic_subscriber.subscribe(slo_rule_topic_subscriber_function, new AtomicBoolean(false)); //This subscriber should be immune to stop signals
                        Logger.getAnonymousLogger().log(info_logging_level,"Broker unavailable, will try to reconnect after 10 seconds");
                        try {
                            Thread.sleep(10000);
                        }catch (InterruptedException i){
                            Logger.getAnonymousLogger().log(info_logging_level,"Sleep was interrupted, will immediately try to connect to the broker");
                        }
                    }
                };
                CharacterizedThread.create_new_thread(slo_rules_topic_subscriber_runnable,"slo_rules_topic_subscriber_thread", persistent_running_thread,true);


                //Implementation of 'Lost edge device' thread

                BiFunction<String, String, String> device_lost_subscriber_function = (topic, message) -> {
                    BrokerPublisher persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);

                    Clock clock = Clock.systemUTC();
                    Long current_time_seconds = (long) Math.floor(clock.millis()/1000.0);
                    JSONObject severity_json = new JSONObject();
                    severity_json.put("severity", 100);
                    severity_json.put("probability", 100);
                    severity_json.put("predictionTime", current_time_seconds);
                    persistent_publisher.publish(severity_json.toJSONString());

                    //TODO possibly necessary to remove the next adaptation time as it will probably not be possible to start an adaptation during it
                    return topic + ":MSG:" + message;
                };

                BrokerSubscriber device_lost_subscriber = new BrokerSubscriber(topic_for_lost_device_announcement, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);

                Runnable device_lost_topic_subscriber_runnable = () -> {
                    while (true) {
                        device_lost_subscriber.subscribe(device_lost_subscriber_function, new AtomicBoolean(false)); //This subscriber should be immune to stop signals
                        Logger.getAnonymousLogger().log(info_logging_level,"Broker unavailable, will try to reconnect after 10 seconds");
                        try {
                            Thread.sleep(10000);
                        }catch (InterruptedException i){
                            Logger.getAnonymousLogger().log(info_logging_level,"Sleep was interrupted, will immediately try to connect to the broker");
                        }
                    }
                };
                CharacterizedThread.create_new_thread(device_lost_topic_subscriber_runnable,"device_lost_topic_subscriber_thread",persistent_running_thread,true);


                if (self_publish_rule_file) {
                    String json_file_name = prop.getProperty("input_file");
                    String rules_json_string = String.join(EMPTY, Files.readAllLines(Paths.get(new File(json_file_name).getAbsolutePath())));
                    BrokerPublisher publisher = new BrokerPublisher(slo_rules_topic, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);
                    publisher.publish(rules_json_string);
                    Logger.getAnonymousLogger().log(info_logging_level, "Sent message\n" + rules_json_string);
                }
            }
            first_run = false;
            synchronized (can_modify_slo_rules) {
                do  {
                    try {
                        can_modify_slo_rules.wait();
                    }catch (InterruptedException i){
                        i.printStackTrace();
                    }
                }while((!can_modify_slo_rules.getValue()) || (!slo_rule_arrived.get()));
                can_modify_slo_rules.setValue(false);
                slo_rule_arrived.set(false);
                String rule_representation = MESSAGE_CONTENTS.get_synchronized_contents(slo_rules_topic);
                if (slo_rule_arrived_has_updated_version(rule_representation)) {
                    if (single_slo_rule_active) {
                        slo_rules.clear();
                    }

                    ArrayList<String> additional_metrics_from_new_slo = get_metric_list_from_JSON_slo(rule_representation);

                    if (additional_metrics_from_new_slo.size() > 0) {
                        slo_rules.add(new SLORule(rule_representation, additional_metrics_from_new_slo));
                    }
                    can_modify_slo_rules.setValue(true);
                    can_modify_slo_rules.notifyAll();
                }else{
                    can_modify_slo_rules.setValue(true);
                    can_modify_slo_rules.notifyAll();
                    continue;
                }
            }

            stop_all_running_threads();
            DebugDataSubscription.initiate(prop.getProperty("broker_ip_url"),prop.getProperty("broker_username"), prop.getProperty("broker_password"));
            initialize_monitoring_datastructures_with_empty_data(slo_rules);
            //
            initialize_subrule_and_attribute_associations(slo_rules);
            initialize_attribute_subscribers(slo_rules, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"));
            initialize_slo_processing(slo_rules);

        }
    }

}
