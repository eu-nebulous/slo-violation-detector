/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package runtime;

//import eu.melodic.event.brokerclient.BrokerPublisher;
//import eu.melodic.event.brokerclient.BrokerSubscriber;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import slo_violation_detector_engine.DetectorSubcomponent;
import utility_beans.*;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

import static configuration.Constants.*;

import static java.util.logging.Level.INFO;
import static utilities.OperationalModeUtils.getSLOViolationDetectionOperationalMode;
import static slo_violation_detector_engine.SLOViolationDetectorStateUtils.*;
import static utility_beans.CharacterizedThread.CharacterizedThreadRunMode.detached;


@SpringBootApplication
public class Main {
    public static Long current_slo_rules_version = -1L;//initialization
    public static ArrayList<DetectorSubcomponent> detectors = new ArrayList<>();
    public static void main(String[] args) {

        //The input of this program (the SLO Violation Detector) is the type of the SLO violations which are monitored, and the predicted metric values which are evaluated. Its output are events which produce an estimate of the probability of an adaptation.
        //The SLO violations which are monitored need to mention the following data:
        // - The name of the predicted metrics which are monitored as part of the rule
        // - The threshold and whether it is a more-than or less-than threshold
        //The predicted metrics need to include the following data:
        // - The predicted value
        // - The prediction confidence

        //The above functionality is carried out by a subcomponent of the SLO Violation Detector which is the Detector. There is at least one Detector in each SLO Violation Detector, but there is also one Director responsible for guiding the Detector(s).

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
                DetectorSubcomponent detector = new DetectorSubcomponent(detached);
                detectors.add(detector);
                ArrayList<String> unbounded_metric_strings = new ArrayList<String>(Arrays.asList(prop.getProperty("metrics_bounds").split(",")));
                for (String metric_string : unbounded_metric_strings) {
                    detector.getSubcomponent_state().getMonitoring_attributes_bounds_representation().put(metric_string.split(";")[0], metric_string.split(";", 2)[1]);
                }
            } //initialization
            if (operational_mode.equals(OperationalMode.DETECTOR)) {
                Logger.getAnonymousLogger().log(INFO,"Starting new Detector instance");
            }else if (operational_mode.equals(OperationalMode.DIRECTOR)){
                Logger.getAnonymousLogger().log(INFO,"Starting new Director and new Detector instance");
                SpringApplication.run(Main.class, args);
                Logger.getAnonymousLogger().log(INFO,"Execution completed");
            }
        }catch (IOException e){
            Logger.getAnonymousLogger().log(info_logging_level,"Problem reading input file");
            e.printStackTrace();
        }catch (Exception e){
            Logger.getAnonymousLogger().log(info_logging_level,"Miscellaneous issue during startup");
            e.printStackTrace();
        }
    }

}
