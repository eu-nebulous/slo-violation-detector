package utility_beans.reconfiguration_suggestion;

public class ReconfigurationDetails implements Comparable<ReconfigurationDetails> {
    private double severity;
    private double reconfiguration_probability;
    private double current_slo_threshold;
    private long timestamp;
    private long targeted_reconfiguration_timestamp;
    private boolean will_reconfigure;

    public ReconfigurationDetails(double reconfiguration_probability, double severity, boolean will_reconfigure, double current_slo_threshold, long targeted_reconfiguration_timestamp){
        timestamp = System.currentTimeMillis();
        this.severity = severity;
        this.reconfiguration_probability = reconfiguration_probability;
        this.will_reconfigure = will_reconfigure;
        this.current_slo_threshold = current_slo_threshold;
        this.targeted_reconfiguration_timestamp = targeted_reconfiguration_timestamp;
    }

    public ReconfigurationDetails(SLOViolation slo_violation, DecisionMaker decision_maker){
        timestamp = System.currentTimeMillis();
        severity = slo_violation.getSeverity_value();
        reconfiguration_probability = slo_violation.getReconf_probability();
        will_reconfigure = true;
        current_slo_threshold = decision_maker.getSeverity_class_model().get_severity_class(severity).getAdaptation_threshold().getValue();
        targeted_reconfiguration_timestamp  = slo_violation.getProposed_reconfiguration_timestamp();
    }
    
    public static ReconfigurationDetails get_details_for_noop_reconfiguration(){
        return new ReconfigurationDetails();
    }
    public ReconfigurationDetails(){
        will_reconfigure = false;
    }
    /*public ReconfigurationDetails (boolean will_reconfigure, long targeted_reconfiguration_timestamp){
        this.will_reconfigure = will_reconfigure;
        this.targeted_reconfiguration_timestamp = targeted_reconfiguration_timestamp;
        if(will_reconfigure){
            this.severity = 1.0;
            this.reconfiguration_probability = 1.0;
        }else{
            this.severity = 0;
            this.reconfiguration_probability = 0;
        }
    }*/

    public double getSeverity() {
        return severity;
    }

    public void setSeverity(double severity) {
        this.severity = severity;
    }

    public double getReconfiguration_probability() {
        return reconfiguration_probability;
    }

    public void setReconfiguration_probability(double reconfiguration_probability) {
        this.reconfiguration_probability = reconfiguration_probability;
    }

    public boolean will_reconfigure() {
        return will_reconfigure;
    }

    public void setWill_reconfigure(boolean will_reconfigure) {
        this.will_reconfigure = will_reconfigure;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public long getTargeted_reconfiguration_timestamp() {
        return targeted_reconfiguration_timestamp;
    }

    public void setTargeted_reconfiguration_timestamp(long targeted_reconfiguration_timestamp) {
        this.targeted_reconfiguration_timestamp = targeted_reconfiguration_timestamp;
    }
    
    public double getCurrent_slo_threshold() {
	    return current_slo_threshold;
	}
	
	public void setCurrent_slo_threshold(double current_slo_threshold) {
	    this.current_slo_threshold = current_slo_threshold;
	}
	@Override
    public int compareTo (ReconfigurationDetails other){
        if (this.will_reconfigure && !other.will_reconfigure){
            return 1;
        }else if (!this.will_reconfigure && other.will_reconfigure){
            return -1;
        }else {
            if (this.will_reconfigure && other.will_reconfigure){
                if (this.severity>other.severity){
                    return 1;
                }else if (this.severity == other.severity){
                    return 0;
                }else{
                    return -1;
                }
            }else{
                return 0;
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (will_reconfigure){
            sb.append("Active reconfiguration: ");
            sb.append(" Severity is: "+severity);
            sb.append(" The reconfiguration probability is "+reconfiguration_probability);
            sb.append(" The targeted timestamp is "+targeted_reconfiguration_timestamp);
        }else{
            sb.append("Will not make a reconfiguration as Severity is "+severity);
        }
        return sb.toString();
    }
}
