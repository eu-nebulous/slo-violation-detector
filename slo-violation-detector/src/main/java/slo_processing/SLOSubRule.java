/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package slo_processing;

import utility_beans.PredictedMonitoringAttribute;

import java.util.ArrayList;
import java.util.HashMap;

public class SLOSubRule {
    public enum RuleType {greater_than_rule,less_than_rule,equal_rule,unequal_rule,unknown}
    private RuleType rule_type;
    private static HashMap<String, ArrayList<SLOSubRule>> slo_subrules_per_monitoring_attribute = new HashMap<>();
    private String metric;
    private String operator;
    private SLORuleJoin rule_join_type;
    private Double threshold;
    private Integer id;
    private PredictedMonitoringAttribute associated_predicted_monitoring_attribute;

    public SLOSubRule(String metric, String operator, Double threshold,Integer id){
        this.metric = metric;
        this.operator = operator;
        this.threshold = threshold;
        this.id = id;
        this.associated_predicted_monitoring_attribute = new PredictedMonitoringAttribute(metric);
        this.rule_type = find_rule_type(operator);
    }
    public static RuleType find_rule_type(String operator){
        RuleType rule_type = RuleType.unknown;
        if (operator.equals(">") || operator.equals(">=")){
            rule_type = RuleType.greater_than_rule;
        }else if (operator.equals("<") || operator.equals("<=")){
            rule_type = RuleType.less_than_rule;
        }else if (operator.equals("==")){
            rule_type = RuleType.equal_rule;
        }else if (operator.equals("<>")){
            rule_type = RuleType.unequal_rule; //although this rule type will never be handled independently
        }
        return rule_type;
    }

    public String getMetric() {
        return metric;
    }

    public String getOperator() {
        return operator;
    }

    public Double getThreshold() {
        return threshold;
    }

    public Integer getId() {
        return id;
    }

    public static HashMap<String, ArrayList<SLOSubRule>> getSlo_subrules_per_monitoring_attribute() {
        return slo_subrules_per_monitoring_attribute;
    }

    public PredictedMonitoringAttribute getAssociated_predicted_monitoring_attribute() {
        return associated_predicted_monitoring_attribute;
    }

    public void setAssociated_predicted_monitoring_attribute(PredictedMonitoringAttribute associated_predicted_monitoring_attribute) {
        this.associated_predicted_monitoring_attribute = associated_predicted_monitoring_attribute;
    }

    public RuleType getRule_type() {
        return rule_type;
    }

    public void setRule_type(RuleType rule_type) {
        this.rule_type = rule_type;
    }

    @Override
    public String toString(){
        return ("The rule is "+metric+operator+threshold+"\n+ The associated Predicted Monitoring Attribute is "+associated_predicted_monitoring_attribute.toString());
    }
}
