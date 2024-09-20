/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package configuration;

import java.net.URI;
import java.util.logging.Level;
public class Constants {

    //String constants
    public static String EMPTY = "";
    public static String SPACE = " ";
    public static Double LOWER_LIMIT_DELTA = - 100.0;
    public static String NAME_SEPARATOR = "#";
    //Operational constants
    public static String severity_calculation_method;
    public static int time_horizon_seconds;
    public static Long buffer_time = 500L ; //This is a time interval in milliseconds allowing for many SLO violations to be gathered
    public static int maximum_acceptable_forward_predictions;
    public static String slo_violation_feedback_method;
    public static double maximum_adaptation_threshold_for_reconfigurations = 0.9; 
    public static double q_learning_initial_value = 100;
    public static double q_learning_exploration_factor = 1;
    public static double q_learning_learning_rate = 0.1;
    public static double q_learning_discounting_factor = 0.95;
    public static String slo_violations_database_url = "jdbc:h2:file:" + System.getProperty("user.dir") + "/sloviolations.db";
    public static String database_username="sa";
    public static String database_password="";
    public static Integer number_of_severity_classes;
    public static String [] logic_operators = {"and","or"};
    public static final String default_application_name = "default_application";
    public static final String slovid_subscriber_key = "slovid_publisher";
    public static URI base_project_path;
    public static String configuration_file_location =  "slo-violation-detector/src/main/resources/config/eu.nebulous.slo_violation_detector.properties";
    public static String amq_library_configuration_location = "slo-violation-detector/src/main/resources/config/eu.melodic.event.brokerclient.properties";
    public static String topic_for_severity_announcement = "eu.nebulouscloud.monitoring.slo.severity_value";
    public static String topic_for_lost_device_announcement = "eu.nebulouscloud.monitoring.device_lost";
    public static String slo_rules_topic = "eu.nebulouscloud.monitoring.slo.new";
    public static String metric_list_topic = "eu.nebulouscloud.monitoring.metric_list";
    public static String topic_prefix_realtime_metrics = "eu.nebulouscloud.monitoring.realtime.";
    public static String topic_prefix_final_predicted_metrics = "eu.nebulouscloud.monitoring.predicted.";
    public static boolean publish_normalized_severity = true;
    public static double slo_violation_probability_threshold = 0.5; //The threshold over which the probability of a predicted slo violation should be to have a violation detection
    public static int kept_values_per_metric = 5; //Default to be overriden from the configuration file. This indicates how many metric values are kept to calculate the "previous" metric value during the rate of change calculation
    public static String roc_calculation_mode = "prototype";
    public static boolean single_slo_rule_active = true; //default value to be overriden
    public static boolean assume_slo_rule_version_is_always_updated = false;
    public static double roc_limit = 1;
    public static double epsilon = 0.00000000001;
    public static Level debug_logging_level = Level.OFF;
    public static Level info_logging_level = Level.INFO; //Default to be overriden from the configuration file
    public static Level warning_logging_level = Level.WARNING;//Default to be overriden from the configuration file
    public static Level severe_logging_level = Level.SEVERE;

    //Formatting constants
    public static String dashed_line = "\n----------------------\n";
}
