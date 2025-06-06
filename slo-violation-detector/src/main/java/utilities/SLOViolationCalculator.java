/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utilities;

import slo_rule_modelling.SLOSubRule;
import utility_beans.monitoring.PredictedMonitoringAttribute;
import utility_beans.monitoring.RealtimeMonitoringAttribute;

import java.util.logging.Logger;

import static configuration.Constants.*;

public class SLOViolationCalculator {

    public static double get_Severity_all_metrics_method(PredictedMonitoringAttribute predictionAttribute, SLOSubRule.RuleType rule_type){

        double all_metrics_method_attribute_severity;
        if (rule_type.equals(SLOSubRule.RuleType.greater_than_rule)){
            double severity_sum =  get_greater_than_severity_sum(predictionAttribute);
            all_metrics_method_attribute_severity = Math.sqrt(severity_sum)/Math.sqrt(3);

            Logger.getGlobal().log(info_logging_level,"The all-metrics attribute severity for a greater-than rule related to attribute " + predictionAttribute.getName() + " based on a (roc,prconf,normalized_interval,delta) quadraplet of (" + predictionAttribute.getRate_of_change_for_greater_than_rule() + "," + predictionAttribute.getProbability_confidence()+ "," + predictionAttribute.getNormalizedConfidenceIntervalWidth()+","+predictionAttribute.getDelta_for_greater_than_rule() + ") is " + all_metrics_method_attribute_severity+"\nPrediction data for this calculation used the prediction value "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTimestamp() + " for timepoint "+predictionAttribute.getTargeted_prediction_time());
            if (severity_sum<0){
                Logger.getGlobal().log(warning_logging_level,"The NaN severity value is produced due to the root of a negative severity sum");
            }
        }
        else if (rule_type.equals(SLOSubRule.RuleType.less_than_rule)){
            double severity_sum =  get_less_than_severity_sum(predictionAttribute);
            all_metrics_method_attribute_severity = Math.sqrt(severity_sum)/Math.sqrt(3);

            Logger.getGlobal().log(info_logging_level,"The all-metrics attribute severity for a less-than rule related to attribute " + predictionAttribute.getName() + " based on a (roc,prconf,normalized_interval,delta) quadraplet of (" + predictionAttribute.getRate_of_change_for_less_than_rule() + "," + predictionAttribute.getProbability_confidence()+ "," + predictionAttribute.getNormalizedConfidenceIntervalWidth()+","+predictionAttribute.getDelta_for_less_than_rule() + ") is " + all_metrics_method_attribute_severity+"\nPrediction data for this calculation used the prediction value "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTimestamp() + " for timepoint "+predictionAttribute.getTargeted_prediction_time());
            if (severity_sum<0){
                Logger.getGlobal().log(warning_logging_level,"The NaN severity value is produced due to the root of a negative severity sum");
            }
        }
        else if (rule_type.equals(SLOSubRule.RuleType.unequal_rule)){

            double greater_than_severity_sum = get_greater_than_severity_sum(predictionAttribute);
            double less_than_severity_sum =  get_less_than_severity_sum(predictionAttribute);

            if (less_than_severity_sum>greater_than_severity_sum){
                all_metrics_method_attribute_severity = Math.sqrt(less_than_severity_sum)/Math.sqrt(3);
                Logger.getGlobal().log(info_logging_level,"The all-metrics attribute severity for an 'equals' rule related to attribute " + predictionAttribute.getName() + " based on a (roc,prconf,normalized_interval,delta) quadraplet of (" + predictionAttribute.getRate_of_change_for_less_than_rule() + "," + predictionAttribute.getProbability_confidence()+ "," + predictionAttribute.getNormalizedConfidenceIntervalWidth()+","+predictionAttribute.getDelta_for_less_than_rule() + ") is " + all_metrics_method_attribute_severity+"\nPrediction data for this calculation used the prediction value "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTimestamp() + " for timepoint "+predictionAttribute.getTargeted_prediction_time());
                if (less_than_severity_sum<0){
                    Logger.getGlobal().log(warning_logging_level,"The NaN severity value is produced due to the root of a negative severity sum");
                }
            }else{
                all_metrics_method_attribute_severity = Math.sqrt(greater_than_severity_sum)/Math.sqrt(3);
                Logger.getGlobal().log(info_logging_level,"The all-metrics attribute severity for a greater-than rule related to attribute " + predictionAttribute.getName() + " based on a (roc,prconf,normalized_interval,delta) quadraplet of (" + predictionAttribute.getRate_of_change_for_greater_than_rule() + "," + predictionAttribute.getProbability_confidence()+ "," + predictionAttribute.getNormalizedConfidenceIntervalWidth()+","+predictionAttribute.getDelta_for_greater_than_rule() + ") is " + all_metrics_method_attribute_severity+"\nPrediction data for this calculation used the prediction value "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTimestamp() + " for timepoint "+predictionAttribute.getTargeted_prediction_time());
                if (greater_than_severity_sum<0){
                    Logger.getGlobal().log(warning_logging_level,"The NaN severity value is produced due to the root of a negative severity sum");
                }
            }
        }
        else if (rule_type.equals(SLOSubRule.RuleType.equal_rule)) {
            if (predictionAttribute.getEquality_to_threshold()){
                Logger.getGlobal().log(info_logging_level,"Equality found with the threshold for prediction "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTargeted_prediction_time());
                all_metrics_method_attribute_severity=1;
            }else{
                all_metrics_method_attribute_severity=0;
            }
        }
        else {
            Logger.getGlobal().log(info_logging_level,"An unknown type of rule was introduced, therefore setting the severity to -1 to prevent any adaptation");
            all_metrics_method_attribute_severity = -1;
        }

        if (Double.isNaN(all_metrics_method_attribute_severity) || ( all_metrics_method_attribute_severity<0)){
            Logger.getGlobal().log(warning_logging_level,"Negative or NaN severity produced: "+all_metrics_method_attribute_severity+" using 0 instead");
            return 0;
        }
        else {
            return all_metrics_method_attribute_severity; //or think of another way to implement this
        }
    }

    private static double get_less_than_severity_sum(PredictedMonitoringAttribute predictionAttribute) {
        Double roc_sign = predictionAttribute.getRate_of_change_for_less_than_rule()/Math.abs(predictionAttribute.getRate_of_change_for_less_than_rule());
        Double delta_sign = predictionAttribute.getDelta_for_less_than_rule()/Math.abs(predictionAttribute.getDelta_for_less_than_rule());
        if (delta_sign.isNaN()){
            delta_sign = 1.0;
        }
        if (roc_sign.isNaN()){
            roc_sign = 1.0;
        }

        Double rate_of_change_factor = roc_sign*predictionAttribute.getRate_of_change_for_less_than_rule()*predictionAttribute.getRate_of_change_for_less_than_rule();
        Double probability_confidence_factor =
                predictionAttribute.getProbability_confidence()*
                        predictionAttribute.getProbability_confidence()*
                        (100-predictionAttribute.getNormalizedConfidenceIntervalWidth())*
                        (100-predictionAttribute.getNormalizedConfidenceIntervalWidth())/
                        (100*100);//to normalize values
        Double delta_factor = delta_sign*predictionAttribute.getDelta_for_less_than_rule()*predictionAttribute.getDelta_for_less_than_rule();
        return rate_of_change_factor+probability_confidence_factor+delta_factor;
    }

    private static double get_greater_than_severity_sum(PredictedMonitoringAttribute predictionAttribute) {
        Double roc_sign = predictionAttribute.getRate_of_change_for_greater_than_rule()/Math.abs(predictionAttribute.getRate_of_change_for_greater_than_rule());
        Double delta_sign = predictionAttribute.getDelta_for_greater_than_rule()/Math.abs(predictionAttribute.getDelta_for_greater_than_rule());
        if (delta_sign.isNaN()){
            delta_sign = 1.0;
        }
        if (roc_sign.isNaN()){
            roc_sign = 1.0;
        }

        double rate_of_change_factor = roc_sign*predictionAttribute.getRate_of_change_for_greater_than_rule()*predictionAttribute.getRate_of_change_for_greater_than_rule();
        double probability_confidence_factor = 0;
        if (predictionAttribute.getConfidence_interval_width()<0){
            probability_confidence_factor = -(100*100);
        }else{
            probability_confidence_factor =
                    predictionAttribute.getProbability_confidence()*
                            predictionAttribute.getProbability_confidence()*
                            (100-predictionAttribute.getNormalizedConfidenceIntervalWidth())*
                            (100-predictionAttribute.getNormalizedConfidenceIntervalWidth())/
                            (100*100);//to normalize values
        }
        double delta_factor = delta_sign*predictionAttribute.getDelta_for_greater_than_rule()*predictionAttribute.getDelta_for_greater_than_rule();
        return rate_of_change_factor+probability_confidence_factor+delta_factor;
    }

    public static double get_Severity_prconf_delta_method(PredictedMonitoringAttribute predictionAttribute,SLOSubRule.RuleType rule_type){

        double severity_sum;
        if (rule_type.equals(SLOSubRule.RuleType.greater_than_rule)) {
            severity_sum = (predictionAttribute.getDelta_for_greater_than_rule() * predictionAttribute.getProbability_confidence() * (100 - predictionAttribute.getNormalizedConfidenceIntervalWidth() / 100)) / (100 * 100); //dividing by 10000 to normalize;
            Logger.getGlobal().log(info_logging_level, "The prconf-delta attribute severity for a greater-than rule related to attribute " + predictionAttribute.getName() + " based on a (prconf,delta,confidence_interval) triplet of (" + predictionAttribute.getProbability_confidence() + "," + predictionAttribute.getDelta_for_greater_than_rule() + "," + predictionAttribute.getConfidence_interval_width() + ") is " + severity_sum+"\nPrediction data for this calculation used the prediction value "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTimestamp() + " for timepoint "+predictionAttribute.getTargeted_prediction_time());
        }else if (rule_type.equals(SLOSubRule.RuleType.less_than_rule)){
            severity_sum = (predictionAttribute.getDelta_for_less_than_rule() * predictionAttribute.getProbability_confidence() * (100 - predictionAttribute.getNormalizedConfidenceIntervalWidth() / 100)) / (100 * 100); //dividing by 10000 to normalize;
            Logger.getGlobal().log(info_logging_level, "The prconf-delta attribute severity for a less-than rule related to attribute " + predictionAttribute.getName() + " based on a (prconf,delta,confidence_interval) triplet of (" + predictionAttribute.getProbability_confidence() + "," + predictionAttribute.getDelta_for_less_than_rule() + "," + predictionAttribute.getConfidence_interval_width() + ") is " + severity_sum +"\nPrediction data for this calculation used the prediction value "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTimestamp() + " for timepoint "+predictionAttribute.getTargeted_prediction_time());
        }else if (rule_type.equals(SLOSubRule.RuleType.unequal_rule)){
            double greater_than_severity_sum = (predictionAttribute.getDelta_for_greater_than_rule() * predictionAttribute.getProbability_confidence() * (100 - predictionAttribute.getNormalizedConfidenceIntervalWidth() / 100)) / (100 * 100); //dividing by 10000 to normalize;
            Logger.getGlobal().log(info_logging_level, "The prconf-delta attribute severity for a greater-than rule related to attribute " + predictionAttribute.getName() + " based on a (prconf,delta,confidence_interval) triplet of (" + predictionAttribute.getProbability_confidence() + "," + predictionAttribute.getDelta_for_greater_than_rule() + "," + predictionAttribute.getConfidence_interval_width() + ") is " + greater_than_severity_sum+"\nPrediction data for this calculation used the prediction value "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTimestamp() + " for timepoint "+predictionAttribute.getTargeted_prediction_time());


            double less_than_severity_sum = (predictionAttribute.getDelta_for_less_than_rule() * predictionAttribute.getProbability_confidence() * (100 - predictionAttribute.getNormalizedConfidenceIntervalWidth() / 100)) / (100 * 100); //dividing by 10000 to normalize;
            Logger.getGlobal().log(info_logging_level, "The prconf-delta attribute severity for a less-than rule related to attribute " + predictionAttribute.getName() + " based on a (prconf,delta,confidence_interval) triplet of (" + predictionAttribute.getProbability_confidence() + "," + predictionAttribute.getDelta_for_less_than_rule() + "," + predictionAttribute.getConfidence_interval_width() + ") is " + less_than_severity_sum+"\nPrediction data for this calculation used the prediction value "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTimestamp() + " for timepoint "+predictionAttribute.getTargeted_prediction_time());
            Logger.getGlobal().log(info_logging_level,"The maximum of the aforementioned values will be used");
            severity_sum = Math.max(less_than_severity_sum,greater_than_severity_sum);
        }else if (rule_type.equals(SLOSubRule.RuleType.equal_rule)){
            if (predictionAttribute.getEquality_to_threshold()){
                Logger.getGlobal().log(info_logging_level,"Equality found with the threshold for prediction "+predictionAttribute.getForecasted_value()+" from timepoint "+predictionAttribute.getTargeted_prediction_time());
                severity_sum=100;
            }else{
                severity_sum=0;
            }
        }
        
        else{
            severity_sum = -1;
        }
        if (severity_sum<0){
            Logger.getGlobal().log(info_logging_level,"A NaN severity value may be produced due to the root of a negative severity sum - returning zero instead for severity sum");
            severity_sum = 0;
        }

        return  severity_sum;
    }

    public static double get_Severity_over_threshold_method(RealtimeMonitoringAttribute realtimeAttribute, SLOSubRule rule){
        SLOSubRule.RuleType rule_type = rule.getRule_type();
        double severity_value,realtime_value;
        int queue_size = realtimeAttribute.getActual_metric_values().size();
        if (queue_size > 0) {
            realtime_value = realtimeAttribute.getActual_metric_values().get(queue_size - 1).doubleValue();
        }else {
            Logger.getGlobal().log(info_logging_level,"The severity value is -100 and was generated due to no realtime values being available");
            return -100;
        }
        
        if (rule_type.equals(SLOSubRule.RuleType.greater_than_rule)) {
            if (realtime_value > rule.getThreshold()) {
                severity_value = 100;
            } else {
                severity_value = -100;
            }
        }
        else if (rule_type.equals(SLOSubRule.RuleType.less_than_rule)){
            if (realtime_value < rule.getThreshold()) {
                severity_value = 100;
            } else {
                severity_value = -100;
            }
        }else if (rule_type.equals(SLOSubRule.RuleType.equal_rule)){
            if (Math.abs(realtime_value - rule.getThreshold())<epsilon) {
                severity_value = 100;
            } else {
                severity_value = -100;
            }
        }else if (rule_type.equals(SLOSubRule.RuleType.unequal_rule)){
            if (Math.abs(realtime_value - rule.getThreshold())>epsilon) {
                severity_value = 100;
            } else {
                severity_value = -100;
            }
        }
        
        else{
            severity_value = -100;
        }
        Logger.getGlobal().log(info_logging_level,"The severity value is: "+severity_value+" and was generated due to the realtime value being "+realtime_value+" and threshold being "+rule.getThreshold());
        return  severity_value;
    }
    
}
