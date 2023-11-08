/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package slo_processing;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import runtime.Main;
import utilities.MathUtils;
import utilities.SLOViolationCalculator;
import utility_beans.RealtimeMonitoringAttribute;
import utility_beans.PredictedMonitoringAttribute;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static configuration.Constants.*;
import static slo_processing.SLOSubRule.find_rule_type;
import static utilities.SLOViolationDetectorStateUtils.severity_calculation_event_recording_queue;
import static utility_beans.PredictedMonitoringAttribute.getPredicted_monitoring_attributes;

public class SLORule {
    private static HashMap<String,Integer> attribute_ids = new HashMap<>(6);
    private ArrayList<SLOSubRule> slo_subrules = new ArrayList<>(6);
    private ArrayList<String> monitoring_attributes  = new ArrayList<>();
    private JSONObject rule_representation;
    private SLOFormatVersion rule_format;
    private enum SLOFormatVersion {invalid,older,newer}
    private static int id = 0;
    //double SLO_severity;

    public SLORule(String rule_representation, ArrayList<String> metric_list){
        for (String metric: metric_list) {
            RealtimeMonitoringAttribute monitoring_attribute;
            if (!RealtimeMonitoringAttribute.getMonitoring_attributes().containsKey(metric)){
                monitoring_attribute = new RealtimeMonitoringAttribute(metric);
                RealtimeMonitoringAttribute.getMonitoring_attributes().put(metric,monitoring_attribute);
            }
            monitoring_attributes.add(metric);
        }
        try {
            this.rule_representation = (JSONObject) new JSONParser().parse(rule_representation);
        }catch (ParseException p){
            p.printStackTrace();
        }
        this.rule_format = find_rule_format(this.rule_representation);
        this.slo_subrules = parse_subrules(this.rule_representation,this.rule_format);
    }

    private static SLOFormatVersion find_rule_format(JSONObject rule_representation) {
        String rule_id = (String) rule_representation.get("id");
        String rule_name = (String) rule_representation.get("name");
        SLOFormatVersion rule_format = SLOFormatVersion.invalid;
        if (rule_id!=null){
            rule_format = SLOFormatVersion.older;
        }
        else if (rule_name!=null){
            rule_format = SLOFormatVersion.newer;
        }else {
            try{
                Exception e = new Exception("An invalid rule was sent to the SLO Violation detector - ignoring the rule having the following representation\n"+rule_representation.toJSONString());
            }catch (Exception e){
                Logger.getAnonymousLogger().log(Level.SEVERE,"An invalid rule was sent to the SLO Violation detector");
                return rule_format;
            }
        }
        return rule_format;
    }

    public static ArrayList<SLOSubRule> parse_subrules(JSONObject rule_json, SLOFormatVersion rule_format){
        ArrayList<SLOSubRule> subrules = new ArrayList<>(); //initialization
        String rule_id = (String) rule_json.get("id");
        String rule_operator = (String) rule_json.get("operator");
        //First checking for older-format rules, then newer format rules
        if (rule_format.equals(SLOFormatVersion.older)){
            if (is_composite_rule_from_id(rule_id)) {
                JSONArray subrules_json_array = (JSONArray) rule_json.get(rule_id);
                for (Object subrule : subrules_json_array) {
                    JSONObject json_subrule = (JSONObject) subrule;
                    subrules.addAll(parse_subrules(json_subrule,rule_format));
                }
            } else {
                String attribute = (String) rule_json.get("attribute");
                String threshold = (String) rule_json.get("threshold");
                if (rule_operator.equals("<>")) {
                    subrules.add(new SLOSubRule(attribute, "<", Double.parseDouble(threshold), Integer.parseInt(rule_id)));
                    subrules.add(new SLOSubRule(attribute, ">", Double.parseDouble(threshold), Integer.parseInt(rule_id)+1000000)); //assuming that there are less than 1000000 subrules
                } else {
                    subrules.add(new SLOSubRule(attribute, rule_operator, Double.parseDouble(threshold), Integer.parseInt(rule_id)));
                }
            }
        }
        //newer format
        else if (rule_format.equals(SLOFormatVersion.newer)){
            if (is_composite_rule_from_operator(rule_operator)) {
                JSONArray subrules_json_array = (JSONArray) rule_json.get("constraints");
                for (Object subrule : subrules_json_array) {
                    JSONObject json_subrule = (JSONObject) subrule;
                    subrules.addAll(parse_subrules(json_subrule,rule_format));
                }
            } else {
                String attribute = (String) rule_json.get("metric");
                Double threshold = (Double) rule_json.get("threshold");
                if (is_composite_rule_from_threshold_operator(rule_operator)) {
                    subrules.add(new SLOSubRule(attribute, ">", threshold,get_id_for(attribute)));
                    subrules.add(new SLOSubRule(attribute, "<", threshold,get_id_for(attribute))); //TODO perhaps here change the id
                }else{
                    subrules.add(new SLOSubRule(attribute, rule_operator, threshold,get_id_for(attribute)));
                }
            }
        }
        return subrules;
    }

    /**
     *This method is used to assign id's to attribute names and create a mapping between them, in the case of 'newer' format SLO definitions
     * @param attribute The name of the monitoring metric (attribute) for which the rule is formulated
     * @return An Integer identifier
     */
    private static Integer get_id_for(String attribute) {
        if (attribute_ids.containsKey(attribute)){
            return attribute_ids.get(attribute);
        }else {
            return attribute_ids.put(attribute, id++);
        }
    }

    public static double process_rule_value(JSONObject rule_json,Long targeted_prediction_time, SLOFormatVersion rule_format) {

        StringBuilder calculation_logging_string = new StringBuilder();
        double rule_result_value = -1; //initialization
        String rule_id = (String)rule_json.get("id");
        String rule_operator = (String)rule_json.get("operator");
        String rule_metric = (String)rule_json.get("metric");
        Double rule_threshold = (Double)rule_json.get("threshold");

        JSONArray subrules_json_array = new JSONArray();
        boolean composite_rule = false;
        boolean special_operator_subrule = false;
        //older format
        if ((rule_format.equals(SLOFormatVersion.older)) && is_composite_rule_from_id(rule_id)) {
            subrules_json_array = (JSONArray) rule_json.get(rule_id);
            composite_rule = true;
        }
        //newer format
        else if ((rule_format.equals(SLOFormatVersion.newer)) && (is_composite_rule_from_operator(rule_operator))){
            subrules_json_array = (JSONArray) rule_json.get("constraints");
            composite_rule = true;
        }
        else if (is_composite_rule_from_threshold_operator(rule_operator)){
            subrules_json_array = new JSONArray();
            composite_rule = true;
            special_operator_subrule = true;
            // operator, metric, name (unnecessary), threshold
            //create simple json object for the two deterministic subrules (the greater-than and the less-than)
            JSONObject first_simple_subrule_json = new JSONObject();
            JSONObject second_simple_subrule_json = new JSONObject();
            first_simple_subrule_json.put("operator",">");
            first_simple_subrule_json.put("threshold",rule_threshold);
            first_simple_subrule_json.put("metric",rule_metric);
            second_simple_subrule_json.put("operator","<");
            second_simple_subrule_json.put("threshold",rule_threshold);
            second_simple_subrule_json.put("metric",rule_metric);
            subrules_json_array.add(first_simple_subrule_json);
            subrules_json_array.add(second_simple_subrule_json);
            //subrules_json_array.add
        }
        if (composite_rule){
            ArrayList<Double>  individual_severity_contributions = new ArrayList<>();
            boolean and_subrules_invalidated = false;
            for (Object subrule: subrules_json_array) {
                JSONObject json_subrule = (JSONObject) subrule;
                //String json_subrule_id = (String) json_subrule.get("id");
                double subrule_result = process_rule_value(json_subrule,targeted_prediction_time,rule_format);
                calculation_logging_string.append("\nThe severity calculation for subrule ").append(json_subrule).append(" is ").append(subrule_result).append("\n");
                String logical_operator = EMPTY;
                if (rule_format.equals(SLOFormatVersion.older)){
                    logical_operator = (get_logical_operator_part(rule_id)).toLowerCase();
                }else if (rule_format.equals(SLOFormatVersion.newer)){
                    logical_operator = rule_operator.toLowerCase();
                }
                if (special_operator_subrule){
                    logical_operator = "or";
                }
                if (logical_operator.equals("and")){
                    if (subrule_result<0){
                        //return -1; //all other rules are invalidated
                        and_subrules_invalidated = true;
                    }else {
                        if (!and_subrules_invalidated /*&& !is_composite_rule(json_subrule_id)*/) {
                            //individual_severity_contributions.add(MonitoringAttribute.get_monitoring_attributes_values_map().get((String)json_subrule.get("id")));
                            //individual_severity_contributions.add(PredictionAttribute.getPredicted_attributes_values().get((String)json_subrule.get("id")));
                            individual_severity_contributions.add(subrule_result);

                        }
                        //rule_result_value = calculate_severity(and_subrule_severity_values);
                    }
                }else if (logical_operator.equals("or")){
                    rule_result_value = Math.max(rule_result_value,subrule_result);
                    calculation_logging_string.append("Calculating maximum of individual severity contributions - current is  ").append(rule_result_value).append(" prospective higher severity is ").append(subrule_result).append("\n");
                }
            }

            if (slo_violation_determination_method.equals("all-metrics")&& individual_severity_contributions.size()>0) {
                rule_result_value = MathUtils.get_average(individual_severity_contributions);
                calculation_logging_string.append("Calculating average of individual severity contributions: ").append(individual_severity_contributions).append(" equals ").append(rule_result_value).append("\n");
            }else if (slo_violation_determination_method.equals("prconf-delta") && individual_severity_contributions.size()>0){
                rule_result_value = Math.sqrt(MathUtils.sum(individual_severity_contributions.stream().map(x->x*x).collect(Collectors.toList())))/Math.sqrt(individual_severity_contributions.size());
                calculation_logging_string.append("Calculating square root of sum of individual severity contributions: ").append(individual_severity_contributions).append(" - the result is ").append(rule_result_value).append("\n");

            }

            //Debugging information logging
            if (rule_result_value>10000){
                calculation_logging_string.append("\nDue to the severity value being over 10000, it is replaced by 10000");
                rule_result_value = 10000;
            }
            severity_calculation_event_recording_queue.add(calculation_logging_string.toString());
            return rule_result_value;
        }

        else{
            //String attribute_name = (String) rule_json.get("attribute");
            //String operator = (String) rule_json.get("operator");
            SLOSubRule.RuleType rule_type = find_rule_type(rule_operator);
            //double threshold = Double.parseDouble((String) rule_json.get("threshold"));
            Integer subrule_id=0;
            if (rule_format.equals(SLOFormatVersion.older)) {
                subrule_id = Integer.parseInt((String) rule_json.get("id"));
            }else if (rule_format.equals(SLOFormatVersion.newer)){
                subrule_id = get_id_for(rule_metric);
            }

            if (getPredicted_monitoring_attributes().get(subrule_id)== null){
                rule_result_value = -1;
                return rule_result_value;
            }
            PredictedMonitoringAttribute new_prediction_attribute = getPredicted_monitoring_attributes().get(subrule_id).get(targeted_prediction_time);

            if (new_prediction_attribute==null || !new_prediction_attribute.isInitialized() || new_prediction_attribute.getDelta_for_less_than_rule()<LOWER_LIMIT_DELTA || new_prediction_attribute.getDelta_for_greater_than_rule() < LOWER_LIMIT_DELTA){ //delta is normalized so only this case is examined here
                rule_result_value = -1;
            }else {
                rule_result_value = 1; //not a real value, but a positive number to signify that there is a threshold violation
                if (slo_violation_determination_method.equals("all-metrics")){
                    rule_result_value = SLOViolationCalculator.get_Severity_all_metrics_method(new_prediction_attribute,rule_type);
                }else if (slo_violation_determination_method.equals("prconf-delta")){
                    rule_result_value = SLOViolationCalculator.get_Severity_prconf_delta_method(new_prediction_attribute,rule_type);
                }
            }
            calculation_logging_string.append(dashed_line).append("\nThe severity calculation for simple subrule ").append(rule_metric).append(rule_operator).append(rule_threshold).append(" is ").append(rule_result_value).append(dashed_line);
            return rule_result_value;
        }
    }

    private static boolean is_composite_rule_from_id(String rule_id) {
        String rule_id_logical_operator_part = get_logical_operator_part(rule_id);
        return (rule_id_logical_operator_part!=null && !rule_id_logical_operator_part.isEmpty() && Arrays.stream(logic_operators).anyMatch(e-> e.equals(rule_id_logical_operator_part)));
    }

    private static boolean is_composite_rule_from_operator (String operator){
        String lowercase_operator = operator.toLowerCase();
        return (!operator.isEmpty() && Arrays.stream(logic_operators).anyMatch(e-> e.equals(lowercase_operator)));
    }

    private static boolean is_composite_rule_from_threshold_operator (String operator){
        return operator.equals("<>");
    }

    private static String get_logical_operator_part(String rule_id) {
        return rule_id.split("-")[0]; //possibly contains the name of a logical operator
    }

    private static double calculate_severity(ArrayList<Double> and_subrule_severity_values) {
        double severity_value;
        double severity_sum = 0;
        for (Double value : and_subrule_severity_values){
            severity_sum = severity_sum + value*value;
        }
        severity_value = Math.sqrt(severity_sum)/(100*Math.sqrt(and_subrule_severity_values.size()));
        return severity_value;
    }


    public JSONObject getRule_representation() {
        return rule_representation;
    }

    public ArrayList<String> get_monitoring_attributes (){
        return monitoring_attributes;
    }


    public ArrayList<SLOSubRule> getSlo_subrules() {
        return slo_subrules;
    }

    public SLOFormatVersion getRule_format() {
        return rule_format;
    }

    public void setRule_format(SLOFormatVersion rule_format) {
        this.rule_format = rule_format;
    }
}
