package utility_beans.reconfiguration_suggestion;

import utility_beans.generic_component_functionality.ProcessingStatus;

import java.util.HashMap;

import static utilities.SHA256Hash.getShaTruncated;
import static utility_beans.generic_component_functionality.ProcessingStatus.*;

public class SLOViolation {

    private String id;
    private Long default_reconfiguration_interval = 3000L;
    private Double severity_value;
    private Double reconf_probability;
    private Long time_calculated; //System.currentTimeMillis() is used to assign values here
    private Long timepoint_of_implementation_of_reconfiguration;
    private SLODeterminationMethod slo_determination_method;
    private ProcessingStatus processing_status=not_started;
    private Boolean did_propose_adaptation;
    private Boolean proposed_adaptation_successful;
    private Long proposed_reconfiguration_timestamp;
    private HashMap<SLOViolationMetaMetric, Double> slo_metametric_values;
    private ViolationDecision decision;
    private boolean reconfiguration_is_completed = false;


    public SLOViolation(Double severity_value, Double reconf_probability, Long time_calculated, Long proposed_reconfiguration_timestamp, SLODeterminationMethod slo_determination_method, HashMap<SLOViolationMetaMetric, Double> slo_metametric_values) {
        try {
            this.severity_value = severity_value;
            this.reconf_probability = reconf_probability;
            this.time_calculated = time_calculated;
            this.proposed_reconfiguration_timestamp = proposed_reconfiguration_timestamp;
            this.slo_determination_method = slo_determination_method;
            this.slo_metametric_values = slo_metametric_values;
            this.id = getShaTruncated(String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public SLOViolation(Double severity_value) {
        try {
            this.severity_value = severity_value;
            this.reconf_probability = Math.min(severity_value, 1.0);
            this.time_calculated = System.currentTimeMillis();
            this.proposed_reconfiguration_timestamp = time_calculated + default_reconfiguration_interval;
            this.slo_determination_method = SLODeterminationMethod.all_metrics;
            this.slo_metametric_values = new HashMap<>();
            this.id = getShaTruncated(String.valueOf(System.currentTimeMillis()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Double getSeverity_value() {
        return severity_value;
    }

    public void setSeverity_value(Double severity_value) {
        this.severity_value = severity_value;
    }

    public Double getReconf_probability() {
        return reconf_probability;
    }

    public void setReconf_probability(Double reconf_probability) {
        this.reconf_probability = reconf_probability;
    }

    public Long getTime_calculated() {
        return time_calculated;
    }

    public void setTime_calculated(Long time_calculated) {
        this.time_calculated = time_calculated;
    }

    public SLODeterminationMethod getSlo_determination_method() {
        return slo_determination_method;
    }

    public void setSlo_determination_method(SLODeterminationMethod slo_determination_method) {
        this.slo_determination_method = slo_determination_method;
    }

    public Boolean get_if_proposed_adaptation() {
        return did_propose_adaptation;
    }

    public void set_if_proposed_adaptation(Boolean did_propose_adaptation) {
        this.did_propose_adaptation = did_propose_adaptation;
    }

    public Boolean get_if_proposed_adaptation_successful() {
        return proposed_adaptation_successful;
    }

    public void get_if_proposed_adaptation_successful(Boolean proposed_adaptation_successful) {
        this.proposed_adaptation_successful = proposed_adaptation_successful;
    }

    public HashMap<SLOViolationMetaMetric, Double> getSlo_metametric_values() {
        return slo_metametric_values;
    }

    public void setSlo_metametric_values(HashMap<SLOViolationMetaMetric, Double> slo_metametric_values) {
        this.slo_metametric_values = slo_metametric_values;
    }

    public ViolationDecision getDecision() {
        return decision;
    }

    public void setDecision(ViolationDecision decision) {
        this.decision = decision;
    }

    public Long getProposed_reconfiguration_timestamp() {
        return proposed_reconfiguration_timestamp;
    }

    public void setProposed_reconfiguration_timestamp(Long proposed_reconfiguration_timestamp) {
        this.proposed_reconfiguration_timestamp = proposed_reconfiguration_timestamp;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public ProcessingStatus getProcessing_status() {
        return processing_status;
    }

    public void setProcessing_status(ProcessingStatus processing_status) {
        this.processing_status = processing_status;
    }

    public Long getTimepoint_of_implementation_of_reconfiguration() {
        return timepoint_of_implementation_of_reconfiguration;
    }

    public void setTimepoint_of_implementation_of_reconfiguration(Long timepoint_of_implementation_of_reconfiguration) {
        this.timepoint_of_implementation_of_reconfiguration = timepoint_of_implementation_of_reconfiguration;
        this.reconfiguration_is_completed = true;
    }
    public boolean isReconfiguration_completed() {
        return reconfiguration_is_completed;
    }
    @Override
    public String toString(){
        return "SLOv "+id+" - timestamp: "+time_calculated+", severity: "+severity_value+" ("+(processing_status.toString())+")";
    }
}