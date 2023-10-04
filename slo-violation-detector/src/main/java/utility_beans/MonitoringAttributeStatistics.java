/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package utility_beans;



public class MonitoringAttributeStatistics {
    private double current_mean;
    private double current_dsquared;
    private int count = 0; //initialize
    private final boolean hard_upper_bound_is_set,hard_lower_bound_is_set;
    private double upper_bound, lower_bound;

    public MonitoringAttributeStatistics(){
        hard_upper_bound_is_set = false;
        hard_lower_bound_is_set = false;
        lower_bound = Double.POSITIVE_INFINITY;
        upper_bound = Double.NEGATIVE_INFINITY;
    }

    public MonitoringAttributeStatistics(double hard_upper_or_hard_lower_bound, boolean is_hard_upper_bound){
        if (is_hard_upper_bound){
            hard_upper_bound_is_set = true;
            hard_lower_bound_is_set = false;
            upper_bound = hard_upper_or_hard_lower_bound;
            lower_bound = Double.POSITIVE_INFINITY;
        }else{
            hard_lower_bound_is_set = true;
            hard_upper_bound_is_set = false;
            lower_bound = hard_upper_or_hard_lower_bound;
            upper_bound = Double.NEGATIVE_INFINITY;
        }
    }

    public MonitoringAttributeStatistics(double lower_bound, double upper_bound){
        hard_lower_bound_is_set = true;
        hard_upper_bound_is_set = true;
        this.upper_bound = upper_bound;
        this.lower_bound = lower_bound;
    }

    public void update_attribute_statistics(double new_attribute_value){
        count++;

        double mean_differential = (new_attribute_value - current_mean) / count;
        double new_mean = current_mean + mean_differential;

        double dsquared_increment = (new_attribute_value - new_mean) * (new_attribute_value - current_mean);
        double new_dsquared = current_dsquared + dsquared_increment;

        current_mean = new_mean;
        current_dsquared = new_dsquared;

        if (!hard_upper_bound_is_set){
            if (count==1) {
                upper_bound = new_attribute_value;
            }else {

                double candidate_upper_value = new_mean + Math.sqrt(10.0) * Math.sqrt(new_dsquared / (count - 1)); //Chebyshev-based 90th percentile value
                //if (candidate_upper_value>upper_bound){
                upper_bound = candidate_upper_value;
                //}
            }
        }
        if (!hard_lower_bound_is_set) {
            if (count==1){
                lower_bound = new_attribute_value;
            }else {
                double candidate_lower_value = new_mean - Math.sqrt(10.0) * Math.sqrt(new_dsquared / (count - 1)); //Chebyshev-based 90th percentile value
                //if (candidate_lower_value < lower_bound) {
                  lower_bound = candidate_lower_value;
                //}
            }
        }
    }

    public double getUpper_bound() {
        return upper_bound;
    }

    public void setUpper_bound(double upper_bound) {
        this.upper_bound = upper_bound;
    }

    public double getLower_bound() {
        return lower_bound;
    }

    public void setLower_bound(double lower_bound) {
        this.lower_bound = lower_bound;
    }

    @Override
    public String toString(){
        return "Upper bound "+ upper_bound + System.lineSeparator()+
               "Lower bound "+ lower_bound + System.lineSeparator()+
               "Count "+ count + System.lineSeparator() +
               "Mean "+ current_mean + System.lineSeparator() +
               "Dsquared " + current_dsquared;

    }
}
