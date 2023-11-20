/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utility_beans;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_violation_detector_engine.DetectorSubcomponent;
import utilities.MathUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static utility_beans.PredictedMonitoringAttribute.*;

public class RealtimeMonitoringAttribute {

    protected String name;

    private CircularFifoQueue<Double> actual_metric_values = new CircularFifoQueue<Double>(kept_values_per_metric); //the previous actual values of the metric


    public RealtimeMonitoringAttribute(String name, Collection<Double> values){
        this.name = name;
        values.stream().forEach(x -> actual_metric_values.add(x));
    }
    public RealtimeMonitoringAttribute(String name, Double value){
        this.name = name;
        actual_metric_values.add(value);
    }

    public RealtimeMonitoringAttribute(String name){
        this.name = name;
    }

    public static Double get_metric_value(DetectorSubcomponent detector, String metric_name){
        CircularFifoQueue<Double> actual_metric_values = detector.getSubcomponent_state().getMonitoring_attributes().get(metric_name).getActual_metric_values();
        if (actual_metric_values.size()==0){
            Logger.getAnonymousLogger().log(warning_logging_level,"Trying to retrieve realtime values from an empty queue for metric "+metric_name);
        }
        return aggregate_metric_values(actual_metric_values);
    }

    private static Double aggregate_metric_values(Iterable<Double> metric_values) {
        return MathUtils.get_average(metric_values);
    }

    public static void update_monitoring_attribute_value(DetectorSubcomponent detector, String name,Double value){
        if(detector.getSubcomponent_state().getMonitoring_attributes().get(name)==null){
            detector.getSubcomponent_state().getMonitoring_attributes().put(name,new RealtimeMonitoringAttribute(name));
            //monitoring_attributes_max_values.put(name,value);
            //monitoring_attributes_min_values.put(name,value);

        }
        detector.getSubcomponent_state().getMonitoring_attributes().get(name).getActual_metric_values().add(value);
        detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(name).update_attribute_statistics(value);
        /*
        if(get_90th_percentile_high_value(name,value)>monitoring_attributes_max_values.get(name)){
            monitoring_attributes_max_values.put(name,value);
        }else if (get_90th_percentile_low_value(name,value)<monitoring_attributes_min_values.get(name)){
            monitoring_attributes_min_values.put(name,value);
        }
         */
    }


    public static <T extends Iterable<String>> void initialize_monitoring_attribute_rates_of_change(DetectorSubcomponent detector, T metric_names){
        initialize_monitoring_attribute_hashmap(detector.getSubcomponent_state().getMonitoring_attributes(),metric_names);
        initialize_attribute_value_hashmap(getAttributes_maximum_rate_of_change(),metric_names);
        initialize_attribute_value_hashmap(getAttributes_minimum_rate_of_change(),metric_names);
    }

    public static  <T extends Iterable<String>> void initialize_monitoring_attribute_hashmap(HashMap<String, RealtimeMonitoringAttribute> map, T metric_names){
        for (String metric_name : metric_names){
            map.put(metric_name,new RealtimeMonitoringAttribute(metric_name));
        }
    }

    public static <T extends Iterable<String>> void simple_initialize_0_100_bounded_attributes(DetectorSubcomponent detector, T metric_names){
        for (String metric_name : metric_names) {
            detector.getSubcomponent_state().getMonitoring_attributes_statistics().put(metric_name, new MonitoringAttributeStatistics(0,100));
        }
    }


    /*
    public static <T extends Iterable<String>> void initialize_monitoring_attribute_min_values(T metric_names){
        initialize_attribute_value_hashmap(monitoring_attributes_min_values,metric_names);
    }

    public static <T extends Iterable<String>> void initialize_monitoring_attribute_max_values(T metric_names){
        initialize_attribute_value_hashmap(monitoring_attributes_max_values,metric_names);
    }
*/
    private static <T extends Iterable<String>> void initialize_attribute_value_hashmap(HashMap<String,Double> hashmap ,T metric_names){
        for (String metric_name: metric_names){
            hashmap.put(metric_name,0.0);
        }
    }


/*
    public static HashMap<String, Double> getMonitoring_attributes_min_values() {
        return monitoring_attributes_min_values;
    }

    public static void setMonitoring_attributes_min_values(HashMap<String, Double> monitoring_attributes_min_values) {
        RealtimeMonitoringAttribute.monitoring_attributes_min_values = monitoring_attributes_min_values;
    }

    public static HashMap<String, Double> getMonitoring_attributes_max_values() {
        return monitoring_attributes_max_values;
    }

    public static void setMonitoring_attributes_max_values(HashMap<String, Double> monitoring_attributes_max_values) {
        RealtimeMonitoringAttribute.monitoring_attributes_max_values = monitoring_attributes_max_values;
    }
*/
    public static void update_monitoring_attributes_values_map(DetectorSubcomponent detector, HashMap<String, Double> input_data) {
        for (HashMap.Entry<String,Double> entry: input_data.entrySet()){
            update_monitoring_attribute_value(detector, entry.getKey(),entry.getValue());
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName(){
        return name;
    }

    public CircularFifoQueue<Double> getActual_metric_values() {
        return actual_metric_values;
    }

    public void setActual_metric_values(CircularFifoQueue<Double> actual_metric_values) {
        this.actual_metric_values = actual_metric_values;
    }


}