/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utility_beans;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import utilities.MathUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static utility_beans.PredictedMonitoringAttribute.*;

public class RealtimeMonitoringAttribute {
    private static HashMap<String,String> monitoring_attributes_bounds_representation = new HashMap<>();
    //private static HashMap<String, Double> monitoring_attributes_min_values = new HashMap<>();
    //private static HashMap<String, Double> monitoring_attributes_max_values = new HashMap<>();
    private static HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_statistics = new HashMap<>();
    private static HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_roc_statistics = new HashMap<>();
    private static HashMap<String, RealtimeMonitoringAttribute> monitoring_attributes = new HashMap<>();
    private CircularFifoQueue<Double> actual_metric_values = new CircularFifoQueue<Double>(kept_values_per_metric); //the previous actual values of the metric
    protected String name;

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

    public static Double get_metric_value(String metric_name){
        CircularFifoQueue<Double> actual_metric_values = monitoring_attributes.get(metric_name).getActual_metric_values();
        if (actual_metric_values.size()==0){
            Logger.getAnonymousLogger().log(warning_logging_level,"Trying to retrieve realtime values from an empty queue for metric "+metric_name);
        }
        return aggregate_metric_values(actual_metric_values);
    }

    private static Double aggregate_metric_values(Iterable<Double> metric_values) {
        return MathUtils.get_average(metric_values);
    }

    public static void update_monitoring_attribute_value(String name,Double value){
        if(monitoring_attributes.get(name)==null){
            monitoring_attributes.put(name,new RealtimeMonitoringAttribute(name));
            //monitoring_attributes_max_values.put(name,value);
            //monitoring_attributes_min_values.put(name,value);

        }
        monitoring_attributes.get(name).getActual_metric_values().add(value);
        getMonitoring_attributes_statistics().get(name).update_attribute_statistics(value);
        /*
        if(get_90th_percentile_high_value(name,value)>monitoring_attributes_max_values.get(name)){
            monitoring_attributes_max_values.put(name,value);
        }else if (get_90th_percentile_low_value(name,value)<monitoring_attributes_min_values.get(name)){
            monitoring_attributes_min_values.put(name,value);
        }
         */
    }


    public static <T extends Iterable<String>> void initialize_monitoring_attribute_rates_of_change(T metric_names){
        initialize_monitoring_attribute_hashmap(monitoring_attributes,metric_names);
        initialize_attribute_value_hashmap(getAttributes_maximum_rate_of_change(),metric_names);
        initialize_attribute_value_hashmap(getAttributes_minimum_rate_of_change(),metric_names);
    }

    public static <T extends Iterable<String>> void initialize_monitoring_attribute_hashmap(HashMap<String, RealtimeMonitoringAttribute> map, T metric_names){
        for (String metric_name : metric_names){
            map.put(metric_name,new RealtimeMonitoringAttribute(metric_name));
        }
    }

    public static <T extends Iterable<String>> void simple_initialize_0_100_bounded_attributes(T metric_names){
        for (String metric_name : metric_names) {
            getMonitoring_attributes_statistics().put(metric_name, new MonitoringAttributeStatistics(0,100));
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
    public static void update_monitoring_attributes_values_map(HashMap<String, Double> input_data) {
        for (HashMap.Entry<String,Double> entry: input_data.entrySet()){
            update_monitoring_attribute_value(entry.getKey(),entry.getValue());
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

    public static HashMap<String, RealtimeMonitoringAttribute> getMonitoring_attributes() {
        return monitoring_attributes;
    }

    public static void setMonitoring_attributes(HashMap<String, RealtimeMonitoringAttribute> monitoring_attributes) {
        RealtimeMonitoringAttribute.monitoring_attributes = monitoring_attributes;
    }

    public static HashMap<String, MonitoringAttributeStatistics> getMonitoring_attributes_statistics() {
        return monitoring_attributes_statistics;
    }

    public static void setMonitoring_attributes_statistics(HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_statistics) {
        RealtimeMonitoringAttribute.monitoring_attributes_statistics = monitoring_attributes_statistics;
    }

    public static HashMap<String, MonitoringAttributeStatistics> getMonitoring_attributes_roc_statistics() {
        return monitoring_attributes_roc_statistics;
    }

    public static void setMonitoring_attributes_roc_statistics(HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_roc_statistics) {
        RealtimeMonitoringAttribute.monitoring_attributes_roc_statistics = monitoring_attributes_roc_statistics;
    }

    public static Double get_initial_upper_bound(String attribute_name){

        if (monitoring_attributes_bounds_representation.get(attribute_name)==null) {
            return 100.0;
        }
        if (monitoring_attributes_bounds_representation.get(attribute_name).split(";")[1].equals("unbounded")){
            return Double.NEGATIVE_INFINITY;
        }else{
            return Double.parseDouble(monitoring_attributes_bounds_representation.get(attribute_name).split(";")[1]);
        }
    }
    public static Double get_initial_lower_bound(String attribute_name){

        if (monitoring_attributes_bounds_representation.get(attribute_name)==null) {
            return 0.0;
        }
        if (monitoring_attributes_bounds_representation.get(attribute_name).split(";")[0].equals("unbounded")){
            return Double.POSITIVE_INFINITY;
        }else{
            return Double.parseDouble(monitoring_attributes_bounds_representation.get(attribute_name).split(";")[0]);
        }
    }


    public static HashMap<String, String> getMonitoring_attributes_bounds_representation() {
        return monitoring_attributes_bounds_representation;
    }

    public static void setMonitoring_attributes_bounds_representation(HashMap<String, String> monitoring_attributes_bounds_representation) {
        RealtimeMonitoringAttribute.monitoring_attributes_bounds_representation = monitoring_attributes_bounds_representation;
    }

}
