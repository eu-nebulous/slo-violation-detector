/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utilities;

import slo_processing.SLORule;
import slo_processing.SLOSubRule;
import utility_beans.PredictedMonitoringAttribute;

import java.util.logging.Logger;

import static configuration.Constants.info_logging_level;
import static configuration.Constants.warning_logging_level;

public class SLOViolationCalculator {

    public static double get_Severity_all_metrics_method(PredictedMonitoringAttribute predictionAttribute, SLOSubRule.RuleType rule_type){

        double all_metrics_method_attribute_severity;
        if (rule_type.equals(SLOSubRule.RuleType.greater_than_rule)){
            double severity_sum =  get_greater_than_severity_sum(predictionAttribute);
            all_metrics_method_attribute_severity = Math.sqrt(severity_sum)/Math.sqrt(3);

            Logger.getAnonymousLogger().log(info_logging_level,"The all-metrics attribute severity for a greater-than rule related to attribute " + predictionAttribute.getName() + " based on a (roc,prconf,normalized_interval,delta) quadraplet of (" + predictionAttribute.getRate_of_change_for_greater_than_rule() + "," + predictionAttribute.getProbability_confidence()+ "," + predictionAttribute.getNormalizedConfidenceIntervalWidth()+","+predictionAttribute.getDelta_for_greater_than_rule() + ") is " + all_metrics_method_attribute_severity);
            if (severity_sum<0){
                Logger.getAnonymousLogger().log(info_logging_level,"The NaN severity value is produced due to the root of a negative severity sum");
            }
        }
        else if (rule_type.equals(SLOSubRule.RuleType.less_than_rule)){
            double severity_sum =  get_less_than_severity_sum(predictionAttribute);
            all_metrics_method_attribute_severity = Math.sqrt(severity_sum)/Math.sqrt(3);

            Logger.getAnonymousLogger().log(info_logging_level,"The all-metrics attribute severity for a less-than rule related to attribute " + predictionAttribute.getName() + " based on a (roc,prconf,normalized_interval,delta) quadraplet of (" + predictionAttribute.getRate_of_change_for_less_than_rule() + "," + predictionAttribute.getProbability_confidence()+ "," + predictionAttribute.getNormalizedConfidenceIntervalWidth()+","+predictionAttribute.getDelta_for_less_than_rule() + ") is " + all_metrics_method_attribute_severity);
            if (severity_sum<0){
                Logger.getAnonymousLogger().log(info_logging_level,"The NaN severity value is produced due to the root of a negative severity sum");
            }
        }
        else if (rule_type.equals(SLOSubRule.RuleType.equal_rule)){

            double greater_than_severity_sum = get_greater_than_severity_sum(predictionAttribute);
            double less_than_severity_sum =  get_less_than_severity_sum(predictionAttribute);

            if (less_than_severity_sum>greater_than_severity_sum){
                all_metrics_method_attribute_severity = Math.sqrt(less_than_severity_sum)/Math.sqrt(3);
                Logger.getAnonymousLogger().log(info_logging_level,"The all-metrics attribute severity for an 'equals' rule related to attribute " + predictionAttribute.getName() + " based on a (roc,prconf,normalized_interval,delta) quadraplet of (" + predictionAttribute.getRate_of_change_for_less_than_rule() + "," + predictionAttribute.getProbability_confidence()+ "," + predictionAttribute.getNormalizedConfidenceIntervalWidth()+","+predictionAttribute.getDelta_for_less_than_rule() + ") is " + all_metrics_method_attribute_severity);
                if (less_than_severity_sum<0){
                    Logger.getAnonymousLogger().log(info_logging_level,"The NaN severity value is produced due to the root of a negative severity sum");
                }
            }else{
                all_metrics_method_attribute_severity = Math.sqrt(greater_than_severity_sum)/Math.sqrt(3);
                Logger.getAnonymousLogger().log(info_logging_level,"The all-metrics attribute severity for a greater-than rule related to attribute " + predictionAttribute.getName() + " based on a (roc,prconf,normalized_interval,delta) quadraplet of (" + predictionAttribute.getRate_of_change_for_greater_than_rule() + "," + predictionAttribute.getProbability_confidence()+ "," + predictionAttribute.getNormalizedConfidenceIntervalWidth()+","+predictionAttribute.getDelta_for_greater_than_rule() + ") is " + all_metrics_method_attribute_severity);
                if (greater_than_severity_sum<0){
                    Logger.getAnonymousLogger().log(info_logging_level,"The NaN severity value is produced due to the root of a negative severity sum");
                }
            }

        }else {
            Logger.getAnonymousLogger().log(info_logging_level,"An unknown type of rule was introduced, therefore setting the severity to -1 to prevent any adaptation");
            all_metrics_method_attribute_severity = -1;
        }

        if (Double.isNaN(all_metrics_method_attribute_severity) || ( all_metrics_method_attribute_severity<0)){
            Logger.getAnonymousLogger().log(warning_logging_level,"Negative or NaN severity produced: "+all_metrics_method_attribute_severity+" using 0 instead");
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
            severity_sum = (predictionAttribute.getDelta_for_greater_than_rule() * predictionAttribute.getProbability_confidence() * (100 - predictionAttribute.getNormalizedConfidenceIntervalWidth() / 100)) / (100 * 100 * 100); //dividing by 10000 to normalize;
            Logger.getAnonymousLogger().log(info_logging_level, "The prconf-delta attribute severity for a greater-than rule related to attribute " + predictionAttribute.getName() + " based on a (prconf,delta,confidence_interval) triplet of (" + predictionAttribute.getProbability_confidence() + "," + predictionAttribute.getDelta_for_greater_than_rule() + "," + predictionAttribute.getConfidence_interval_width() + ") is " + severity_sum);
        }else if (rule_type.equals(SLOSubRule.RuleType.less_than_rule)){
            severity_sum = (predictionAttribute.getDelta_for_less_than_rule() * predictionAttribute.getProbability_confidence() * (100 - predictionAttribute.getNormalizedConfidenceIntervalWidth() / 100)) / (100 * 100 * 100); //dividing by 10000 to normalize;
            Logger.getAnonymousLogger().log(info_logging_level, "The prconf-delta attribute severity for a less-than rule related to attribute " + predictionAttribute.getName() + " based on a (prconf,delta,confidence_interval) triplet of (" + predictionAttribute.getProbability_confidence() + "," + predictionAttribute.getDelta_for_less_than_rule() + "," + predictionAttribute.getConfidence_interval_width() + ") is " + severity_sum);
        }else if (rule_type.equals(SLOSubRule.RuleType.equal_rule)){
            double greater_than_severity_sum = (predictionAttribute.getDelta_for_greater_than_rule() * predictionAttribute.getProbability_confidence() * (100 - predictionAttribute.getNormalizedConfidenceIntervalWidth() / 100)) / (100 * 100 * 100); //dividing by 10000 to normalize;
            Logger.getAnonymousLogger().log(info_logging_level, "The prconf-delta attribute severity for a greater-than rule related to attribute " + predictionAttribute.getName() + " based on a (prconf,delta,confidence_interval) triplet of (" + predictionAttribute.getProbability_confidence() + "," + predictionAttribute.getDelta_for_greater_than_rule() + "," + predictionAttribute.getConfidence_interval_width() + ") is " + greater_than_severity_sum);


            double less_than_severity_sum = (predictionAttribute.getDelta_for_less_than_rule() * predictionAttribute.getProbability_confidence() * (100 - predictionAttribute.getNormalizedConfidenceIntervalWidth() / 100)) / (100 * 100 * 100); //dividing by 10000 to normalize;
            Logger.getAnonymousLogger().log(info_logging_level, "The prconf-delta attribute severity for a less-than rule related to attribute " + predictionAttribute.getName() + " based on a (prconf,delta,confidence_interval) triplet of (" + predictionAttribute.getProbability_confidence() + "," + predictionAttribute.getDelta_for_less_than_rule() + "," + predictionAttribute.getConfidence_interval_width() + ") is " + less_than_severity_sum);

            severity_sum = Math.max(less_than_severity_sum,greater_than_severity_sum);
        }else{
            severity_sum = -1;
        }
        if (severity_sum<0){
            Logger.getAnonymousLogger().log(info_logging_level,"A NaN severity value may be produced due to the root of a negative severity sum - returning zero instead for severity sum");
            severity_sum = 0;
        }

        return  severity_sum;
    }
}
