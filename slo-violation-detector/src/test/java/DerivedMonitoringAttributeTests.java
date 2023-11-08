/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

import org.junit.Test;
import utility_beans.MonitoringAttributeStatistics;
import utility_beans.RealtimeMonitoringAttribute;
import utility_beans.PredictedMonitoringAttribute;

import java.util.Arrays;

import static configuration.Constants.roc_limit;
import static utility_beans.PredictedMonitoringAttribute.getAttributes_maximum_rate_of_change;
import static utility_beans.PredictedMonitoringAttribute.getAttributes_minimum_rate_of_change;
import static utility_beans.RealtimeMonitoringAttribute.getMonitoring_attributes_roc_statistics;

public class DerivedMonitoringAttributeTests {
    @Test
    public void roc_calculation_test(){

        RealtimeMonitoringAttribute.simple_initialize_0_100_bounded_attributes(Arrays.asList(new String[]{"cpu"}));
        RealtimeMonitoringAttribute.update_monitoring_attribute_value("cpu",0.0);
        getMonitoring_attributes_roc_statistics().put("cpu", new MonitoringAttributeStatistics()); //The rate of change of a metric, is a metric which itself should be monitored for its upper bound

        getAttributes_maximum_rate_of_change().put("cpu",roc_limit);
        getAttributes_minimum_rate_of_change().put("cpu",-roc_limit);

        PredictedMonitoringAttribute prediction_attribute = new PredictedMonitoringAttribute("cpu",70,1,100.0,100,1,System.currentTimeMillis(),System.currentTimeMillis()+20000);

        assert prediction_attribute.getRate_of_change_for_greater_than_rule() == 100.0;
    }
}
