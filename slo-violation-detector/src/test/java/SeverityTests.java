/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

import org.junit.Test;
import slo_processing.SLOSubRule;
import utilities.SLOViolationCalculator;
import utility_beans.MonitoringAttributeStatistics;
import utility_beans.RealtimeMonitoringAttribute;
import utility_beans.PredictedMonitoringAttribute;

import java.util.ArrayList;

import static utility_beans.RealtimeMonitoringAttribute.getMonitoring_attributes_roc_statistics;

public class SeverityTests {
    @Test
    public void all_metrics_Severity_test_1(){

        ArrayList<String> metric_names = new ArrayList<>(){{
            add("cpu");
        }};

        RealtimeMonitoringAttribute.initialize_monitoring_attribute_rates_of_change(metric_names);
        //RealtimeMonitoringAttribute.initialize_monitoring_attribute_min_values(metric_names);
        //RealtimeMonitoringAttribute.initialize_monitoring_attribute_max_values(metric_names);
        RealtimeMonitoringAttribute.simple_initialize_0_100_bounded_attributes(metric_names);
        for(String monitoring_metric_name : metric_names) {
            getMonitoring_attributes_roc_statistics().put(monitoring_metric_name, new MonitoringAttributeStatistics()); //The rate of change of a metric, is a metric which itself should be monitored for its upper bound
        }
        RealtimeMonitoringAttribute.update_monitoring_attribute_value("cpu",0.0);

        PredictedMonitoringAttribute prediction_attribute = new PredictedMonitoringAttribute("cpu",70,1,100.0,100,10,System.currentTimeMillis(),System.currentTimeMillis()+20000);

        assert (prediction_attribute.getRate_of_change_for_greater_than_rule() < 100.0000000001 && prediction_attribute.getRate_of_change_for_greater_than_rule()>99.9999999999); //maximum value
        assert (prediction_attribute.getConfidence_interval_width() <10.000000000001 && prediction_attribute.getConfidence_interval_width()>9.9999999999);
        assert(prediction_attribute.getProbability_confidence()<100.0000000001 && prediction_attribute.getProbability_confidence()>99.99999999);
        assert(prediction_attribute.getDelta_for_greater_than_rule()<100.0000000001 && prediction_attribute.getDelta_for_greater_than_rule()>99.99999999);
        assert  Math.floor(SLOViolationCalculator.get_Severity_all_metrics_method(prediction_attribute, SLOSubRule.RuleType.greater_than_rule)*100) == 9678;
    }

    @Test
    public void all_metrics_Severity_test_2(){

        ArrayList<String> metric_names = new ArrayList<>(){{
            add("cpu");
        }};

        RealtimeMonitoringAttribute.initialize_monitoring_attribute_rates_of_change(metric_names);
        //RealtimeMonitoringAttribute.initialize_monitoring_attribute_min_values(metric_names);
        //RealtimeMonitoringAttribute.initialize_monitoring_attribute_max_values(metric_names);
        RealtimeMonitoringAttribute.simple_initialize_0_100_bounded_attributes(metric_names);
        for(String monitoring_metric_name : metric_names) {
            getMonitoring_attributes_roc_statistics().put(monitoring_metric_name, new MonitoringAttributeStatistics()); //The rate of change of a metric, is a metric which itself should be monitored for its upper bound
        }
        RealtimeMonitoringAttribute.update_monitoring_attribute_value("cpu",30.0);

        PredictedMonitoringAttribute prediction_attribute = new PredictedMonitoringAttribute("cpu",70,1,80.0,90,5,System.currentTimeMillis(),System.currentTimeMillis()+20000);

        assert (prediction_attribute.getRate_of_change_for_greater_than_rule() > 99.99999999 && prediction_attribute.getRate_of_change_for_greater_than_rule()< 100.00000001); //zero value
        assert (prediction_attribute.getConfidence_interval_width() <5.000000000001 && prediction_attribute.getConfidence_interval_width()>4.9999999999);
        assert (prediction_attribute.getProbability_confidence()<90.0000000001 && prediction_attribute.getProbability_confidence()>89.99999999);
        assert (prediction_attribute.getDelta_for_greater_than_rule()<33.3333333334 && prediction_attribute.getDelta_for_greater_than_rule()>33.3333333332);
        assert Math.floor(SLOViolationCalculator.get_Severity_all_metrics_method(prediction_attribute, SLOSubRule.RuleType.greater_than_rule)*100) == 7836;
    }

    @Test
    public void all_metrics_Severity_test_3(){

        ArrayList<String> metric_names = new ArrayList<>(){{
            add("cpu");
        }};

        RealtimeMonitoringAttribute.initialize_monitoring_attribute_rates_of_change(metric_names);
        //RealtimeMonitoringAttribute.initialize_monitoring_attribute_min_values(metric_names);
        //RealtimeMonitoringAttribute.initialize_monitoring_attribute_max_values(metric_names);
        RealtimeMonitoringAttribute.simple_initialize_0_100_bounded_attributes(metric_names);
        for(String monitoring_metric_name : metric_names) {
            getMonitoring_attributes_roc_statistics().put(monitoring_metric_name, new MonitoringAttributeStatistics()); //The rate of change of a metric, is a metric which itself should be monitored for its upper bound
        }
        RealtimeMonitoringAttribute.update_monitoring_attribute_value("cpu",86.0);

        PredictedMonitoringAttribute prediction_attribute = new PredictedMonitoringAttribute("cpu",75,1,92.0,88,7.8,System.currentTimeMillis(),System.currentTimeMillis()+20000);

        assert (prediction_attribute.getRate_of_change_for_greater_than_rule()> 6.97674418604 && prediction_attribute.getRate_of_change_for_greater_than_rule()< 6.97674418605        ); //zero value
        assert (prediction_attribute.getConfidence_interval_width() >7.7999999 && prediction_attribute.getConfidence_interval_width()<7.8000001);
        assert (prediction_attribute.getProbability_confidence()<88.0000000001 && prediction_attribute.getProbability_confidence()>87.99999999);
        assert (prediction_attribute.getDelta_for_greater_than_rule()<68.0000000001 && prediction_attribute.getDelta_for_greater_than_rule()>67.99999999);
        assert Math.floor(SLOViolationCalculator.get_Severity_all_metrics_method(prediction_attribute, SLOSubRule.RuleType.greater_than_rule)*100) == 6125;
    }



    @Test
    public void prconf_delta_Severity_test(){

    }

}
