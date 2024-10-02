package utility_beans.reconfiguration_suggestion;


import reinforcement_learning.QTableEntry;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utilities.ViolationHandlingActionNames;

import java.util.logging.Logger;

import static configuration.Constants.info_logging_level;
import static configuration.Constants.time_horizon_seconds;

public class ViolationHandlingAction {
    private ViolationHandlingActionNames handling_action_name;
    private boolean suggested_adaptation;
    private boolean was_correct_handling_action;
    private Long handling_action_timestamp;
    private Long evaluation_timestamp;
    private int reconfiguration_alert_counter =0;
    private SLOViolation slo_violation;
    private DetectorSubcomponent associated_detector;

    public ViolationHandlingAction(ViolationHandlingActionNames handling_action_name, SLOViolation slo_violation, boolean suggested_adaptation, DetectorSubcomponent detector){
        this.handling_action_name = handling_action_name;
        this.slo_violation = slo_violation;
        handling_action_timestamp = System.currentTimeMillis();
        this.suggested_adaptation = suggested_adaptation;
        this.associated_detector = detector;
    }

    public Long getHandling_action_timestamp() {
        return handling_action_timestamp;
    }

    public void setHandling_action_timestamp(Long handling_action_timestamp) {
        this.handling_action_timestamp = handling_action_timestamp;
    }

    public Long getEvaluation_timestamp() {
        return evaluation_timestamp;
    }

    public void setEvaluation_timestamp(Long evaluation_timestamp) {
        this.evaluation_timestamp = evaluation_timestamp;
    }

    public boolean determine_if_decision_was_correct() {
        return was_correct_handling_action;
    }

    public void set_if_decision_was_correct(boolean was_correct_handling_action) {
        this.was_correct_handling_action = was_correct_handling_action;
    }

    public boolean isSuggested_adaptation() {
        return suggested_adaptation;
    }

    public void setSuggested_adaptation(boolean suggested_adaptation) {
        this.suggested_adaptation = suggested_adaptation;
    }

    public boolean evaluate_correctness(ViolationHandlingActionNames action_name, Long last_reconfiguration_timestamp, Long last_slo_violation_timestamp) {
        
            Long current_time = System.currentTimeMillis();
            //The option below checks for the last reconfiguration. However, a reconfiguration should be mapped to this slo violation in order to have meaning (in the case that the component receiving the SLO violation blocks until the reconfiguration is complete, we can assume there is an implicit mapping). 
            // In addition to this we will measure the interval between two SLO Violations - if this is larger than the time horizon then not adapting was correct, otherwise not adapting was incorrect
            // We define the last "interesting" time point, as one that is the maximum between the last reconfiguration timestamp and the last slo violation timestamp. We examine the interval between the current time and this time point to determine if the decision was correct or not. We calculate below an interval of one year, but this is not something to calculate on, as in the case that the last slo violation is the one which is currently being evaluated then we set the timestamp of this last slo violation to zero.
            Long last_interesting_timepoint = Math.max(last_slo_violation_timestamp, last_reconfiguration_timestamp);
            //was_correct_decision = current_time -last_reconfiguration_timestamp > time_horizon_seconds*1000L;

            if (last_interesting_timepoint<System.currentTimeMillis()-365.25*24*60*60*1000){
                was_correct_handling_action =true;
                Logger.getGlobal().log(info_logging_level,"The last decision on "+slo_violation.getId()+" was correct as no previous reconfiguration existed in the recent past");
            }
            //was_correct_decision = 
            //last_interesting_timepoint < slo_violation.getTime_calculated() ||
            //last_interesting_timepoint - slo_violation.getTime_calculated() > time_horizon_seconds*1000L;

            was_correct_handling_action =
                    last_interesting_timepoint<slo_violation.getTime_calculated() ||
                    (
                        last_slo_violation_timestamp>slo_violation.getTime_calculated() &&
                        last_slo_violation_timestamp-slo_violation.getTime_calculated() >= time_horizon_seconds * 1000L
                    )                                                             ||
                    (
                        last_reconfiguration_timestamp>slo_violation.getTime_calculated() &&
                        last_reconfiguration_timestamp-slo_violation.getTime_calculated() >= time_horizon_seconds * 1000L
                    )
            ;

            long seconds_from_last_slo_violation = -1;
            long seconds_from_last_adaptation = -1;

            if (
                last_slo_violation_timestamp>slo_violation.getTime_calculated() && (
                last_slo_violation_timestamp-slo_violation.getTime_calculated()) >= time_horizon_seconds * 1000L
            ){
                seconds_from_last_slo_violation = (last_slo_violation_timestamp-slo_violation.getTime_calculated()) / 1000L;
            }else if  (
                last_reconfiguration_timestamp>slo_violation.getTime_calculated() &&
                last_reconfiguration_timestamp-slo_violation.getTime_calculated() >= time_horizon_seconds * 1000L
            )
            {
                seconds_from_last_adaptation = (last_reconfiguration_timestamp-slo_violation.getTime_calculated())/1000L;

            }else if  (last_interesting_timepoint<slo_violation.getTime_calculated()){
                seconds_from_last_adaptation = Long.MAX_VALUE;
                seconds_from_last_slo_violation = Long.MAX_VALUE;
            }
            long reward_seconds = Math.min(seconds_from_last_adaptation,seconds_from_last_slo_violation);

            QTableEntry old_q_table_state = associated_detector.getSubcomponent_state().getQ_table().get_entry(slo_violation.getSeverity_value(),associated_detector.getDm().getSeverity_class_model().get_severity_class(slo_violation.getSeverity_value()).getAdaptation_threshold().getValue(), action_name);
            double old_q_table_state_value = old_q_table_state.getQ_table_value();
            QTableEntry new_q_table_state = old_q_table_state.update(slo_violation.getSeverity_value(),associated_detector.getDm().getSeverity_class_model().get_severity_class(slo_violation.getSeverity_value()).getAdaptation_threshold().getValue(),reward_seconds);
            double new_q_table_state_value = new_q_table_state.getQ_table_value();
            //Updating Q-table database entries 
            associated_detector.getSubcomponent_state().add_q_table_database_entry(
                    associated_detector.get_application_name(),
                    slo_violation.getSeverity_value(),
                    associated_detector.getDm().getSeverity_class_model().get_severity_class(slo_violation.getSeverity_value()).getAdaptation_threshold().getValue(),
                    action_name,
                    new_q_table_state_value
            );

            Logger.getGlobal().log(info_logging_level,
                    "Checking last decision...\n" +
                            "SLO Violation:\t\t\t\t\t" + slo_violation + ",\n" +
                            "Last reconfiguration timestamp:\t" + last_reconfiguration_timestamp + ",\n" +
                            "Last slo violation timestamp:\t" + last_slo_violation_timestamp + ",\n" +
                            "Time horizon (seconds):\t\t\t" + time_horizon_seconds + ".\n" +
                            "Current time is:\t\t\t\t"+current_time+"\n" +
                            "Calculating if decision was appropriate... The result is: " + (was_correct_handling_action ? "correct" : "incorrect")+"\n" +
                            "Previous and current q-table values are " +old_q_table_state_value+" and "+new_q_table_state_value+", respectively"
            );

            return was_correct_handling_action;
    }

    public int getReconfiguration_alert_counter() {
        return reconfiguration_alert_counter;
    }

    public void setReconfiguration_alert_counter(int reconfiguration_alert_counter) {
        this.reconfiguration_alert_counter = reconfiguration_alert_counter;
    }
}
