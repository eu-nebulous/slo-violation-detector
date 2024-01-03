/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utility_beans;

import slo_violation_detector_engine.detector.DetectorSubcomponent;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static utilities.MonitoringAttributeUtilities.isZero;

public class PredictedMonitoringAttribute {

    private static HashMap <String,Double> attributes_maximum_rate_of_change = new HashMap<>(); //initialization
    private static HashMap <String, Double> attributes_minimum_rate_of_change = new HashMap<>();
    private static HashMap <String, Double> predicted_monitoring_attribute_values = new HashMap<>();
    private static HashMap <Integer,HashMap<Long,PredictedMonitoringAttribute>> predicted_monitoring_attributes = new HashMap<>();

    private String name;
    private boolean initialized = false;
    private double delta_for_greater_than_rule;
    private double delta_for_less_than_rule;
    private double threshold;
    private double rate_of_change_for_greater_than_rule; // the rate of change for the metric
    private double rate_of_change_for_less_than_rule; // the rate of change for the metric
    private double probability_confidence; //the probability confidence for the prediction
    private double confidence_interval_width;
    private long timestamp;

    private DetectorSubcomponent detector;
    private long targeted_prediction_time;

    public PredictedMonitoringAttribute(DetectorSubcomponent detector, String name){
        this.detector = detector;
        this.name = name;
    }
    public PredictedMonitoringAttribute(DetectorSubcomponent detector, String name, double threshold, int associated_subrule_id, Double forecasted_value, double probability_confidence, double confidence_interval_width,long timestamp, long targeted_prediction_time){

        //Below, it is assumed that the maximum of an attribute is 100, and the minimum of an attribute is 0
        this.detector = detector;
        this.initialized = true;
        this.name = name;
        this.threshold = threshold;
        double current_value = RealtimeMonitoringAttribute.get_metric_value(detector,name);
        if (Double.isNaN(current_value)){
            Logger.getGlobal().log(info_logging_level,"Detected NaN value for metric "+name+". Thus we cannot compute severity although a predicted value of "+forecasted_value+" has arrived");
            this.initialized = false;
            return;
        }
        this.rate_of_change_for_greater_than_rule = getRateOfChange(forecasted_value, current_value,name);
        this.rate_of_change_for_less_than_rule = -this.rate_of_change_for_greater_than_rule; //inversion necessary, as when a rate of change is positive, it means that the metric is increasing and thus not directed towards the interval in which a less-than rule is fired.

        //Calculations for greater_than rule delta metric

        if(detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(name).getUpper_bound()>threshold){
            this.delta_for_greater_than_rule = 100*(forecasted_value - threshold)/(detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(name).getUpper_bound()-threshold);
        }else /*if (getMonitoring_attributes_statistics().get(name).getUpper_bound()<=threshold)*/{
            if (forecasted_value>threshold){
                this.delta_for_greater_than_rule = 100;
            }else if (forecasted_value==threshold){
                this.delta_for_greater_than_rule = 0;
            }else{
                this.delta_for_greater_than_rule = -100;
            }
        }
        this.delta_for_greater_than_rule = Math.min(Math.max(this.delta_for_greater_than_rule,-100),100);
        //this.previous_delta  = 100*Math.abs(current_value - threshold)/(getMonitoring_attributes_statistics().get(name).getUpper_bound()-threshold);

        //Calculations for less_than rule delta metric

        if(threshold>detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(name).getLower_bound()) {

            this.delta_for_less_than_rule = 100 * (threshold - forecasted_value) / (threshold - detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(name).getLower_bound());

            //this.previous_delta = 100*Math.abs(current_value-threshold)/(threshold-getMonitoring_attributes_statistics().get(name).getLower_bound());
        }else{
            if (threshold>forecasted_value){
                this.delta_for_less_than_rule = 100;
            }else if (threshold==forecasted_value){
                this.delta_for_less_than_rule = 0;
            }else{
                this.delta_for_less_than_rule = -100;
            }
        }
        this.delta_for_less_than_rule = Math.min(Math.max(this.delta_for_less_than_rule,-100),100);
        //this.previous_delta = 100*Math.abs(current_value-threshold)/(threshold-getMonitoring_attributes_statistics().get(name).getLower_bound());

        this.probability_confidence = probability_confidence;
        this.confidence_interval_width = confidence_interval_width;
        //actual_metric_values = get_last_n_actual_values(Constants.elements_considered_in_prediction, MonitoringAttribute.get_monitoring_attributes_values_map().get(name),true);
        this.timestamp = timestamp;
        this.targeted_prediction_time = targeted_prediction_time;
    }



    public static HashMap<Integer, HashMap<Long, PredictedMonitoringAttribute>> getPredicted_monitoring_attributes() {
        return predicted_monitoring_attributes;
    }

    public static HashMap<String, Double> getAttributes_maximum_rate_of_change() {
        return attributes_maximum_rate_of_change;
    }

    public static HashMap<String, Double> getAttributes_minimum_rate_of_change() {
        return attributes_minimum_rate_of_change;
    }

    private double getRateOfChange(double forecasted_value, double actual_value, String name) {
        double maximum_rate_of_change = attributes_maximum_rate_of_change.get(name);
        double minimum_rate_of_change = attributes_minimum_rate_of_change.get(name);
        double rate_of_change,normalized_rate_of_change;


        if (roc_calculation_mode.equals("prototype")) {
            if (isZero(actual_value)) {
                if (isZero(forecasted_value)){
                    rate_of_change = 0;
                    normalized_rate_of_change = 0;
                }else {
                    rate_of_change = 1 * (forecasted_value/Math.abs(forecasted_value)) *roc_limit; //choosing maximum positive/negative value based on the sign of the forecasted value
                    normalized_rate_of_change = 100*Math.min(Math.max(rate_of_change, -1), 1);
                }
            } else if ( isZero(maximum_rate_of_change - minimum_rate_of_change)) {
                rate_of_change = 1 * Math.max(Math.min((forecasted_value - actual_value) / Math.abs(actual_value),roc_limit),-roc_limit);
                normalized_rate_of_change = 100*Math.min(Math.max(rate_of_change,-1),1);
            } else {
                rate_of_change = 1 * Math.max(Math.min(((forecasted_value - actual_value) / Math.abs(actual_value)),roc_limit),-roc_limit);
                if (forecasted_value>actual_value){
                    normalized_rate_of_change  = 100*Math.min(Math.max(rate_of_change/Math.abs(maximum_rate_of_change),-1),1);
                }else{
                    normalized_rate_of_change  = 100*Math.min(Math.max(rate_of_change/Math.abs(minimum_rate_of_change),-1),1);
                }

            }
        }
        else{
            Logger.getGlobal().log(severe_logging_level,"Effectively disabling rate of change (ROC) metric, setting it to 0, as an invalid roc_calculation_mode has been chosen");
            rate_of_change = 0;
            normalized_rate_of_change = 0;
        }
        String debug_rate_of_change_string = "The rate of change for metric "+name+", having a forecasted value of "+forecasted_value+", previous real value of "+actual_value + ", maximum rate of change equal to "+maximum_rate_of_change+", minimum rate of change equal to "+minimum_rate_of_change+", is "+(int)(rate_of_change*10000)/100.0+"% and the normalized rate of change is "+(int)(normalized_rate_of_change*100)/100.0 +"%";
        if(!debug_logging_level.equals(Level.OFF)) {
            Logger.getGlobal().log(debug_logging_level, debug_rate_of_change_string);
        }

        //Streaming percentile calculation, using non-normalized rate of change
        detector.getSubcomponent_state().getMonitoring_attributes_roc_statistics().get(name).update_attribute_statistics(rate_of_change);

        if (attributes_maximum_rate_of_change.get(name)!=null) {
            attributes_maximum_rate_of_change.remove(name);
        }

        if (attributes_minimum_rate_of_change.get(name)!=null) {
            attributes_minimum_rate_of_change.remove(name);
        }

        attributes_maximum_rate_of_change.put(name,Math.min(detector.getSubcomponent_state().getMonitoring_attributes_roc_statistics().get(name).getUpper_bound(),roc_limit));

        attributes_minimum_rate_of_change.put(name,Math.max(detector.getSubcomponent_state().getMonitoring_attributes_roc_statistics().get(name).getLower_bound(),-roc_limit));

        if (Double.isNaN(detector.getSubcomponent_state().getMonitoring_attributes_roc_statistics().get(name).getUpper_bound())){
            Logger.getGlobal().log(info_logging_level,"NaN value detected for maximum rate of change. The individual metric values are "+detector.getSubcomponent_state().getMonitoring_attributes_roc_statistics().get(name).toString());
        }

        if (Double.isNaN(detector.getSubcomponent_state().getMonitoring_attributes_roc_statistics().get(name).getLower_bound())){
            Logger.getGlobal().log(info_logging_level,"NaN value detected for minimum rate of change. The individual metric values are "+detector.getSubcomponent_state().getMonitoring_attributes_roc_statistics().get(name).toString());
        }

        return Math.max(Math.min(normalized_rate_of_change,100.0),-100.0);
    }

    public double getNormalizedConfidenceIntervalWidth(){

        double normalized_interval;
        double maximum_metric_value = detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(name).getUpper_bound();
        double minimum_metric_value = detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(name).getLower_bound();

        if (Double.isInfinite(this.confidence_interval_width)){
            Logger.getGlobal().log(info_logging_level,"Since the confidence interval is deemed to be infinite, it will be set to 100 and the relevant probability confidence factor should be reduced to the lowest value");
            return 100;
        }
        if (isZero(maximum_metric_value-minimum_metric_value)){
            normalized_interval = 50; //Assuming an average case, from 0 to 100
        }else{
            normalized_interval = 100*this.confidence_interval_width/(maximum_metric_value-minimum_metric_value);
            double normalized_interval_sign = normalized_interval/Math.abs(normalized_interval);
            if (Math.abs(normalized_interval)>100){
                normalized_interval = 100*normalized_interval_sign;
                Logger.getGlobal().log(info_logging_level,"Due to the maximum and minimum metric values being estimated as "+maximum_metric_value+ " and "+minimum_metric_value+" respectively, and as the value of the confidence interval width is "+this.confidence_interval_width+" the absolute value of the normalized interval is limited to a value of "+normalized_interval);
            }
        }
        return normalized_interval;

    }


    private <T extends  Number> ArrayList<T> get_last_n_actual_values(int n, ArrayList<T> values, boolean truncate_old_values){
        int arraylist_size = values.size();
        ArrayList<T> new_values = new ArrayList<>();
        if (truncate_old_values) {
            for (int i = 0; i < arraylist_size; i++) {
                if (i<(arraylist_size-n)) {
                    values.remove(0);
                }else{
                    new_values.add(values.get(i+n-arraylist_size));
                }
            }
        }
        return new_values;
    }

    public double getDelta_for_greater_than_rule() {
        return delta_for_greater_than_rule;
    }
    public double getDelta_for_less_than_rule() {
        return delta_for_less_than_rule;
    }

    public void setDelta_for_greater_than_rule(double delta) {
        this.delta_for_greater_than_rule = delta_for_greater_than_rule;
    }

    public void setDelta_for_less_than_rule(double delta) {
        this.delta_for_less_than_rule = delta_for_less_than_rule;
    }
    public double getThreshold() {
        return threshold;
    }

    public void setThreshold(double threshold) {
        this.threshold = threshold;
    }

    public double getRate_of_change_for_greater_than_rule() {
        return rate_of_change_for_greater_than_rule;
    }
    public double getRate_of_change_for_less_than_rule() {
        return rate_of_change_for_less_than_rule;
    }

    public void setRate_of_change_for_greater_than_rule(double rate_of_change_for_greater_than_rule) {
        this.rate_of_change_for_greater_than_rule = rate_of_change_for_greater_than_rule;
    }

    public void setRate_of_change_for_less_than_rule(double rate_of_change_for_less_than_rule) {
        this.rate_of_change_for_less_than_rule = rate_of_change_for_less_than_rule;
    }

    public double getProbability_confidence() {
        return probability_confidence;
    }

    public void setProbability_confidence(double probability_confidence) {
        this.probability_confidence = probability_confidence;
    }

    public double getConfidence_interval_width() {
        return confidence_interval_width;
    }

    public void setConfidence_interval_width(double confidence_interval_width) {
        this.confidence_interval_width = confidence_interval_width;
    }
    public String getName() {
        return name;
    }

    public static HashMap<String, Double> getPredicted_monitoring_attribute_values() {
        return predicted_monitoring_attribute_values;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString(){
        String output = "";
        output="{"+name+" deltas: "+delta_for_greater_than_rule+","+delta_for_less_than_rule+" ROCs: "+rate_of_change_for_greater_than_rule+","+rate_of_change_for_less_than_rule+" PrConf:"+probability_confidence+" Confidence Interval: "+confidence_interval_width+" Prediction Timestamp: "+timestamp+"}";
        return output;
    }

}
