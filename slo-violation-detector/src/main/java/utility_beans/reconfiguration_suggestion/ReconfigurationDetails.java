package utility_beans.reconfiguration_suggestion;

public class ReconfigurationDetails implements Comparable<ReconfigurationDetails> {
    private double severity;
    private double reconfiguration_probability;
    private long timestamp;
    private long targeted_reconfiguration_timestamp;
    private boolean will_reconfigure;

    public ReconfigurationDetails(double reconfiguration_probability, double severity, boolean will_reconfigure, long targeted_reconfiguration_timestamp){
        timestamp = System.currentTimeMillis();
        this.severity = severity;
        this.reconfiguration_probability = reconfiguration_probability;
        this.will_reconfigure = true;
        this.targeted_reconfiguration_timestamp = targeted_reconfiguration_timestamp;
    }

    public static ReconfigurationDetails get_details_for_noop_reconfiguration(){
        ReconfigurationDetails reconfiguration_details = new ReconfigurationDetails();
        reconfiguration_details.setWill_reconfigure(false);
        return reconfiguration_details;
    }
    public ReconfigurationDetails(){

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

    public boolean isWill_reconfigure() {
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

}
