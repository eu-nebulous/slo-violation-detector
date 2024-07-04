package utility_beans.reconfiguration_suggestion;



public class ViolationDecision {
    private boolean suggested_adaptation;
    private boolean was_correct_decision;
    private Long decision_timestamp;
    private Long evaluation_timestamp;
    private int reconfiguration_alert_counter =0;
    private SLOViolation slo_violation;

    public ViolationDecision(SLOViolation slo_violation, boolean suggested_adaptation){
        this.slo_violation = slo_violation;
        decision_timestamp = System.currentTimeMillis();
        this.suggested_adaptation = suggested_adaptation;
    }

    public Long getDecision_timestamp() {
        return decision_timestamp;
    }

    public void setDecision_timestamp(Long decision_timestamp) {
        this.decision_timestamp = decision_timestamp;
    }

    public Long getEvaluation_timestamp() {
        return evaluation_timestamp;
    }

    public void setEvaluation_timestamp(Long evaluation_timestamp) {
        this.evaluation_timestamp = evaluation_timestamp;
    }

    public boolean determine_if_decision_was_correct() {
        return was_correct_decision;
    }

    public void set_if_decision_was_correct(boolean was_correct_decision) {
        this.was_correct_decision = was_correct_decision;
    }

    public boolean isSuggested_adaptation() {
        return suggested_adaptation;
    }

    public void setSuggested_adaptation(boolean suggested_adaptation) {
        this.suggested_adaptation = suggested_adaptation;
    }

    public boolean evaluate_correctness() {
        was_correct_decision = (reconfiguration_alert_counter == 0);
        evaluation_timestamp = System.currentTimeMillis();
        return was_correct_decision;
    }

    public int getReconfiguration_alert_counter() {
        return reconfiguration_alert_counter;
    }

    public void setReconfiguration_alert_counter(int reconfiguration_alert_counter) {
        this.reconfiguration_alert_counter = reconfiguration_alert_counter;
    }
}
