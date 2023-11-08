/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utilities;

import slo_processing.SLOSubRule;
import utility_beans.MonitoringAttributeStatistics;
import utility_beans.RealtimeMonitoringAttribute;

import java.util.ArrayList;
import java.util.HashMap;

import static configuration.Constants.epsilon;
import static configuration.Constants.roc_limit;
import static utility_beans.PredictedMonitoringAttribute.*;
import static utility_beans.RealtimeMonitoringAttribute.*;

public class MonitoringAttributeUtilities {

    public static boolean isZero(double number){
        return ((number <= epsilon) && (number>= -epsilon));
    }

    public static void initialize_values(String monitoring_metric_name, Double monitoring_attribute_value) throws Exception {
        if (monitoring_attribute_value == null) {
            throw new Exception("Empty input of previous metric values for metric " + monitoring_metric_name);
        }

        if (getMonitoring_attributes().get(monitoring_metric_name) != null) {
            getMonitoring_attributes().remove(monitoring_metric_name);
        }
        ArrayList<SLOSubRule> subrules_related_to_monitoring_attribute = SLOSubRule.getSlo_subrules_per_monitoring_attribute().get(monitoring_metric_name);
        for (SLOSubRule subrule : subrules_related_to_monitoring_attribute) {
            getPredicted_monitoring_attributes().remove(subrule.getId());
        }

        getMonitoring_attributes().put(monitoring_metric_name, new RealtimeMonitoringAttribute(monitoring_metric_name, monitoring_attribute_value));

    }

    public static void initialize_values(String monitoring_metric_name){
        //First remove any pre-existing data then add new data
        if (getMonitoring_attributes().get(monitoring_metric_name) != null) {
            getMonitoring_attributes().remove(monitoring_metric_name);
        }

        getMonitoring_attributes().put(monitoring_metric_name, new RealtimeMonitoringAttribute(monitoring_metric_name));

        getMonitoring_attributes_roc_statistics().put(monitoring_metric_name,new MonitoringAttributeStatistics()); //The rate of change of a metric, is a metric which itself should be monitored for its upper bound

        if (!get_initial_upper_bound(monitoring_metric_name).equals(Double.NEGATIVE_INFINITY) &&
            !get_initial_lower_bound(monitoring_metric_name).equals(Double.POSITIVE_INFINITY))    {

            getMonitoring_attributes_statistics().put(monitoring_metric_name,new MonitoringAttributeStatistics(get_initial_lower_bound(monitoring_metric_name), get_initial_upper_bound(monitoring_metric_name)));

        }else if (!get_initial_upper_bound(monitoring_metric_name).equals(Double.NEGATIVE_INFINITY)){
            getMonitoring_attributes_statistics().put(monitoring_metric_name,new MonitoringAttributeStatistics(get_initial_upper_bound(monitoring_metric_name),true));
        }else if (!get_initial_lower_bound(monitoring_metric_name).equals(Double.POSITIVE_INFINITY)){
            getMonitoring_attributes_statistics().put(monitoring_metric_name,new MonitoringAttributeStatistics(get_initial_lower_bound(monitoring_metric_name),false));
        }else {
            getMonitoring_attributes_statistics().put(monitoring_metric_name,new MonitoringAttributeStatistics());
        }



        getAttributes_maximum_rate_of_change().put(monitoring_metric_name,roc_limit);
        getAttributes_minimum_rate_of_change().put(monitoring_metric_name,-roc_limit);

    }

}
