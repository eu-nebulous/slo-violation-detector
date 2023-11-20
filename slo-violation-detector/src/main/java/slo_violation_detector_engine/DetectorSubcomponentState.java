package slo_violation_detector_engine;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_rule_modelling.SLORule;
import utility_beans.MonitoringAttributeStatistics;
import utility_beans.RealtimeMonitoringAttribute;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import static configuration.Constants.kept_values_per_metric;

public class DetectorSubcomponentState {
    private HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_statistics = new HashMap<>();
    private HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_roc_statistics = new HashMap<>();
    private HashMap<String, RealtimeMonitoringAttribute> monitoring_attributes = new HashMap<>();

    private HashMap<String,String> monitoring_attributes_bounds_representation = new HashMap<>();

    public HashSet<Long> adaptation_times = new HashSet<>();
    public HashSet<Long> adaptation_times_pending_processing = new HashSet<>();

    public HashMap<String,Thread> slo_bound_running_threads = new HashMap<>();
    public HashMap<String,Thread> persistent_running_detector_threads = new HashMap<>();

    public HashSet<Long> adaptation_times_to_remove = new HashSet<>();

    public ArrayList<SLORule> slo_rules = new ArrayList<>();

    //Debugging variables
    public CircularFifoQueue<Long> slo_violation_event_recording_queue = new CircularFifoQueue<>(50);
    public CircularFifoQueue<String> severity_calculation_event_recording_queue = new CircularFifoQueue<>(50);



    //private static HashMap<String, Double> monitoring_attributes_min_values = new HashMap<>();
    //private static HashMap<String, Double> monitoring_attributes_max_values = new HashMap<>();

    public Double get_initial_upper_bound(String attribute_name){

        if (monitoring_attributes_bounds_representation.get(attribute_name)==null) {
            return 100.0;
        }
        if (monitoring_attributes_bounds_representation.get(attribute_name).split(";")[1].equals("unbounded")){
            return Double.NEGATIVE_INFINITY;
        }else{
            return Double.parseDouble(monitoring_attributes_bounds_representation.get(attribute_name).split(";")[1]);
        }
    }

    public Double get_initial_lower_bound(String attribute_name){

        if (monitoring_attributes_bounds_representation.get(attribute_name)==null) {
            return 0.0;
        }
        if (monitoring_attributes_bounds_representation.get(attribute_name).split(";")[0].equals("unbounded")){
            return Double.POSITIVE_INFINITY;
        }else{
            return Double.parseDouble(monitoring_attributes_bounds_representation.get(attribute_name).split(";")[0]);
        }
    }

    public HashMap<String, MonitoringAttributeStatistics> getMonitoring_attributes_statistics() {
        return monitoring_attributes_statistics;
    }

    public void setMonitoring_attributes_statistics(HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_statistics) {
        this.monitoring_attributes_statistics = monitoring_attributes_statistics;
    }

    public HashMap<String, MonitoringAttributeStatistics> getMonitoring_attributes_roc_statistics() {
        return monitoring_attributes_roc_statistics;
    }

    public void setMonitoring_attributes_roc_statistics(HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_roc_statistics) {
        this.monitoring_attributes_roc_statistics = monitoring_attributes_roc_statistics;
    }

    public HashMap<String, RealtimeMonitoringAttribute> getMonitoring_attributes() {
        return monitoring_attributes;
    }

    public void setMonitoring_attributes(HashMap<String, RealtimeMonitoringAttribute> monitoring_attributes) {
        this.monitoring_attributes = monitoring_attributes;
    }

    public HashMap<String, String> getMonitoring_attributes_bounds_representation() {
        return monitoring_attributes_bounds_representation;
    }

    public void setMonitoring_attributes_bounds_representation(HashMap<String, String> monitoring_attributes_bounds_representation) {
        this.monitoring_attributes_bounds_representation = monitoring_attributes_bounds_representation;
    }

    public HashSet<Long> getAdaptation_times_to_remove() {
        return adaptation_times_to_remove;
    }

    public void setAdaptation_times_to_remove(HashSet<Long> adaptation_times_to_remove) {
        this.adaptation_times_to_remove = adaptation_times_to_remove;
    }
}
