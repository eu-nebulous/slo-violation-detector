package utility_beans.reconfiguration_suggestion;

import configuration.Constants;

public class SeverityResult {
    private double severity_value;
    private Constants.reconfiguration_triggering_reason reason;
    public SeverityResult(double severity, Constants.reconfiguration_triggering_reason reason){
        this.severity_value = severity;
        this.reason=reason;
    }
    public double getSeverityValue() {
        return severity_value;
    }
    public Constants.reconfiguration_triggering_reason getReason() {
        return reason;
    }
    public void setSeverityValue(double severity_value) {
        this.severity_value = severity_value;
    }
    public void setReason(Constants.reconfiguration_triggering_reason reason){
        this.reason = reason;
    }
}
