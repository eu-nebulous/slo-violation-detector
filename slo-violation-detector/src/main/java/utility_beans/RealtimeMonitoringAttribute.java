/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utility_beans;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utilities.MathUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static java.lang.Integer.valueOf;
import static utility_beans.PredictedMonitoringAttribute.*;

public class RealtimeMonitoringAttribute {

    protected String name;
    public enum AttributeValuesType{Integer, Unknown, Double}
    private AttributeValuesType type;
    private Number upper_bound;
    private Number lower_bound;
    private CircularFifoQueue<Number> actual_metric_values = new CircularFifoQueue<>(kept_values_per_metric); //the previous actual values of the metric

    public RealtimeMonitoringAttribute(String name, Number lower_bound, Number upper_bound,AttributeValuesType type){
        this.name = name;
        this.lower_bound = lower_bound;
        this.upper_bound = upper_bound;
        this.type = type;
    }

    public RealtimeMonitoringAttribute(String name, Collection<Double> values,AttributeValuesType type){
        this.lower_bound = 0.0;
        this.upper_bound= 100.0;
        this.name = name;
        this.type = type;
        //Equivalent to below: values.stream().forEach(x -> actual_metric_values.add(x));
        actual_metric_values.addAll(values);
    }
    public RealtimeMonitoringAttribute(String name, Number value){
        this.name = name;
        this.lower_bound = 0;
        this.upper_bound= 100;
        if (value instanceof Integer){
            this.type = AttributeValuesType.Integer;
        }else if (value instanceof Double){
            this.type = AttributeValuesType.Double;
        }
        actual_metric_values.add(value);
    }

    public RealtimeMonitoringAttribute(String name,Boolean has_infinite_bounds,AttributeValuesType type){
        this.name = name;
        this.type = type;
        if (has_infinite_bounds){
            if (type==AttributeValuesType.Double) {
                this.upper_bound = Double.POSITIVE_INFINITY;
                this.lower_bound = Double.NEGATIVE_INFINITY;
            }
            else if (type==AttributeValuesType.Integer){
                this.upper_bound = Integer.MAX_VALUE;
                this.lower_bound = Integer.MIN_VALUE;
            }
        }else {
            this.lower_bound = 0.0;
            this.upper_bound = 100.0;
        }
    }

    public static Double aggregate_metric_values(Iterable<Number> metric_values) {
        return MathUtils.get_average(metric_values);
    }




    public static <T extends Iterable<String>> void initialize_monitoring_attribute_rates_of_change(DetectorSubcomponent detector, T metric_names){
        initialize_monitoring_attribute_hashmap(detector.getSubcomponent_state().getMonitoring_attributes(),metric_names);
        initialize_attribute_double_value_hashmap(getAttributes_maximum_rate_of_change(),metric_names);
        initialize_attribute_double_value_hashmap(getAttributes_minimum_rate_of_change(),metric_names);
    }

    public static <T extends Iterable<String>> void initialize_monitoring_attribute_hashmap(HashMap<String, RealtimeMonitoringAttribute> map, T metric_names){
        for (String metric_name : metric_names){
            map.put(metric_name,new RealtimeMonitoringAttribute(metric_name,false,AttributeValuesType.Unknown));
        }
    }

    public static <T extends Iterable<String>> void simple_initialize_0_100_bounded_attributes(DetectorSubcomponent detector, T metric_names){
        for (String metric_name : metric_names) {
            detector.getSubcomponent_state().getMonitoring_attributes_statistics().put(metric_name, new MonitoringAttributeStatistics(0,100));
        }
    }

    public static void initialize_monitoring_attributes (DetectorSubcomponent detector, HashMap<String,RealtimeMonitoringAttribute> metric_names_bounds){
        for (String metric_name : metric_names_bounds.keySet()) {
            detector.getSubcomponent_state().getMonitoring_attributes_statistics().put(metric_name, new
                    MonitoringAttributeStatistics(metric_names_bounds.get(metric_name).lower_bound.doubleValue(),metric_names_bounds.get(metric_name).upper_bound.doubleValue()));
        }
    }

    private static <T extends Iterable<String>> void initialize_attribute_double_value_hashmap(HashMap<String,Double> hashmap , T metric_names){
        for (String metric_name: metric_names){
            hashmap.put(metric_name,0.0);
        }
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName(){
        return name;
    }

    public CircularFifoQueue<Number> getActual_metric_values() {
        return actual_metric_values;
    }

    public void setActual_metric_values(CircularFifoQueue<Number> actual_metric_values) {
        this.actual_metric_values = actual_metric_values;
    }

    public Double getUpper_bound() {
        return upper_bound.doubleValue();
    }

    public void setUpper_bound(Number upper_bound) {
        this.upper_bound = upper_bound;
    }

    public Double getLower_bound() {
        return lower_bound.doubleValue();
    }

    public void setLower_bound(Number lower_bound) {
        this.lower_bound = lower_bound;
    }

    public void setType(AttributeValuesType type){
        this.type = type;
    }
    public AttributeValuesType getType(){
        return type;
    }
}
