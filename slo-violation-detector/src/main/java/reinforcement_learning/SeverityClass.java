package reinforcement_learning;

import configuration.Constants;
import utility_beans.synchronization.SynchronizedDouble;

import static configuration.Constants.maximum_adaptation_threshold_for_reconfigurations;
import static configuration.Constants.slo_violation_probability_threshold;

public class SeverityClass{
    private Double minimum_severity_value;
    private Double maximum_severity_value;
    private double decrease_factor = 1.0/ Constants.q_learning_severity_quantization_buckets;
    private double increase_factor = 1.0/Constants.q_learning_severity_quantization_buckets;

    private final SynchronizedDouble adaptation_threshold = new SynchronizedDouble(slo_violation_probability_threshold);



    public SeverityClass (Double minimum_severity_value, Double maximum_severity_value, boolean sort_severity_classes){
        this.minimum_severity_value = minimum_severity_value;
        this.maximum_severity_value = maximum_severity_value;
    }

    public void decrease_threshold(){
        synchronized (adaptation_threshold){
            adaptation_threshold.setValue(Math.max(0,adaptation_threshold.getValue()-decrease_factor));
        }
    }
    public void increase_threshold(){
        synchronized (adaptation_threshold){
            adaptation_threshold.setValue(Math.min(maximum_adaptation_threshold_for_reconfigurations,adaptation_threshold.getValue()+increase_factor));
        }
    }
    public Double getMinimum_severity_value() {
        return minimum_severity_value;
    }

    public void setMinimum_severity_value(Double minimum_severity_value) {
        this.minimum_severity_value = minimum_severity_value;
    }

    public Double getMaximum_severity_value() {
        return maximum_severity_value;
    }

    public void setMaximum_severity_value(Double maximum_severity_value) {
        this.maximum_severity_value = maximum_severity_value;
    }


    public SynchronizedDouble getAdaptation_threshold() {
        return adaptation_threshold;
    }

    public void setAdaptation_threshold(Double adaptation_threshold) {
        this.adaptation_threshold.setValue(adaptation_threshold);
    }

}
