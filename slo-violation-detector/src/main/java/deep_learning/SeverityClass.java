package deep_learning;

import utility_beans.synchronization.SynchronizedDouble;

public class SeverityClass{
    private Double minimum_severity_value;
    private Double maximum_severity_value;
    private double decrease_factor = 0.05;
    private double increase_factor = 0.05;

    private final SynchronizedDouble adaptation_threshold = new SynchronizedDouble(0.5);



    public SeverityClass (Double minimum_severity_value, Double maximum_severity_value, boolean sort_severity_classes){
        this.minimum_severity_value = minimum_severity_value;
        this.maximum_severity_value = maximum_severity_value;
    }

    public void decrease_threshold(){
        synchronized (adaptation_threshold){
            adaptation_threshold.setValue(adaptation_threshold.getValue()-decrease_factor);
        }
    }
    public void increase_threshold(){
        synchronized (adaptation_threshold){
            adaptation_threshold.setValue(adaptation_threshold.getValue()+increase_factor);
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
