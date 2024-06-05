/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package runtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import slo_violation_detector_engine.director.DirectorSubcomponent;
import utility_beans.generic_component_functionality.OperationalMode;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.logging.Logger;

import static configuration.Constants.*;

import static java.util.logging.Level.INFO;
import static slo_violation_detector_engine.generic.ComponentState.*;
import static utilities.OperationalModeUtils.getSLOViolationDetectionOperationalMode;
import static slo_violation_detector_engine.generic.SLOViolationDetectorStateUtils.*;
import static utility_beans.generic_component_functionality.CharacterizedThread.CharacterizedThreadRunMode.detached;


@SpringBootApplication
public class Main {
    public static HashMap<String,DetectorSubcomponent> detectors = new HashMap<>();
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
                InputStream inputStream;
                if (args.length == 0) {
                    operational_mode = getSLOViolationDetectionOperationalMode("DIRECTOR");
                    inputStream = getPreferencesFileInputStream(EMPTY);
                } else if (args.length == 1) {
                    Logger.getGlobal().log(info_logging_level, "Preferences file has been manually specified");
                    operational_mode = getSLOViolationDetectionOperationalMode("DIRECTOR");
                    inputStream = getPreferencesFileInputStream(args[0]);
                } else {
                    Logger.getGlobal().log(info_logging_level, "Operational mode and preferences file has been manually specified");
                    inputStream = getPreferencesFileInputStream(args[0]);
                    operational_mode = getSLOViolationDetectionOperationalMode(args[1]);

                }
                prop.load(inputStream);
                slo_rules_topic = prop.getProperty("slo_rules_topic");
                kept_values_per_metric = Integer.parseInt(prop.getProperty("stored_values_per_metric", "5"));
                //TODO remove from docs as well: self_publish_rule_file = Boolean.parseBoolean(prop.getProperty("self_publish_rule_file"));
                single_slo_rule_active = Boolean.parseBoolean(prop.getProperty("single_slo_rule_active"));
                assume_slo_rule_version_is_always_updated = Boolean.parseBoolean((prop.getProperty("assume_slo_rule_version_is_always_updated")));
                time_horizon_seconds = Integer.parseInt(prop.getProperty("time_horizon_seconds"));

                slo_violation_probability_threshold = Double.parseDouble(prop.getProperty("slo_violation_probability_threshold"));
                slo_violation_determination_method = prop.getProperty("slo_violation_determination_method");
                maximum_acceptable_forward_predictions = Integer.parseInt(prop.getProperty("maximum_acceptable_forward_predictions"));

                broker_ip = prop.getProperty("broker_ip_url");
                broker_port = Integer.parseInt(prop.getProperty("broker_port"));
                broker_username = prop.getProperty("broker_username");
                broker_password = prop.getProperty("broker_password");
                unbounded_metric_strings = new ArrayList<>(Arrays.asList(prop.getProperty("metrics_bounds").split(",")));

                //TODO Delete below two lines
                //DetectorSubcomponent detector = new DetectorSubcomponent(default_application_name,detached);
                //detectors.put(default_application_name,detector);


            } //initialization
            if (operational_mode.equals(OperationalMode.DETECTOR)) {
                if (args.length>2){
                    DetectorSubcomponent detector = new DetectorSubcomponent(args[args.length - 1],detached);
                    detectors.put(args[args.length-1],detector);
                }else{
                    Logger.getGlobal().log(severe_logging_level,"Error, wanted to start the component as a detector but the application name was not provided");
                }
                Logger.getGlobal().log(INFO,"Started new Detector instance");
            }else if (operational_mode.equals(OperationalMode.DIRECTOR)){
                Logger.getGlobal().log(INFO,"Starting new Director instance");
                DirectorSubcomponent director = new DirectorSubcomponent();
                SpringApplication.run(Main.class, args);
                Logger.getGlobal().log(INFO,"Execution completed");
            }
        }catch (IOException e){
            Logger.getGlobal().log(info_logging_level,"Problem reading input file");
            e.printStackTrace();
        }catch (Exception e){
            Logger.getGlobal().log(info_logging_level,"Miscellaneous issue during startup");
            e.printStackTrace();
        }
    }

}
