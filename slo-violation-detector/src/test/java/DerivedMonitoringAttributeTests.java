/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

import org.junit.Test;
import slo_violation_detector_engine.DetectorSubcomponent;
import utility_beans.CharacterizedThread;
import utility_beans.MonitoringAttributeStatistics;
import utility_beans.RealtimeMonitoringAttribute;
import utility_beans.PredictedMonitoringAttribute;

import java.util.Arrays;

import static configuration.Constants.roc_limit;
import static utility_beans.PredictedMonitoringAttribute.getAttributes_maximum_rate_of_change;
import static utility_beans.PredictedMonitoringAttribute.getAttributes_minimum_rate_of_change;
import static utility_beans.RealtimeMonitoringAttribute.simple_initialize_0_100_bounded_attributes;
import static utility_beans.RealtimeMonitoringAttribute.update_monitoring_attribute_value;

public class DerivedMonitoringAttributeTests {

    DetectorSubcomponent detector = new DetectorSubcomponent(CharacterizedThread.CharacterizedThreadRunMode.detached);
    @Test
    public void roc_calculation_test(){

        RealtimeMonitoringAttribute realtimeMonitoringAttribute = new RealtimeMonitoringAttribute("cpu");

        simple_initialize_0_100_bounded_attributes(detector,Arrays.asList(new String[]{"cpu"}));
        update_monitoring_attribute_value(detector,"cpu",0.0);
        detector.getSubcomponent_state().getMonitoring_attributes_roc_statistics().put("cpu", new MonitoringAttributeStatistics()); //The rate of change of a metric, is a metric which itself should be monitored for its upper bound

        getAttributes_maximum_rate_of_change().put("cpu",roc_limit);
        getAttributes_minimum_rate_of_change().put("cpu",-roc_limit);

        PredictedMonitoringAttribute prediction_attribute = new PredictedMonitoringAttribute(detector,"cpu",70,1,100.0,100,1,System.currentTimeMillis(),System.currentTimeMillis()+20000);

        assert prediction_attribute.getRate_of_change_for_greater_than_rule() == 100.0;
    }
}
