package utility_beans.reconfiguration_suggestion;


import reinforcement_learning.QTableEntry;
import reinforcement_learning.SeverityClass;
import slo_violation_detector_engine.detector.DetectorSubcomponent;

import java.util.logging.Logger;

import static configuration.Constants.*;

public class ViolationHandlingAction {
    private ViolationHandlingActionName handling_action_name;
    private boolean suggested_adaptation;
    private boolean reconfiguration_triggered_or_happened_during_last_time_horizon;
    private Long handling_action_timestamp;
    private Long evaluation_timestamp;
    private int reconfiguration_alert_counter =0;
    private SLOViolation slo_violation;
    private DetectorSubcomponent associated_detector;

    public ViolationHandlingAction(ViolationHandlingActionName handling_action_name, SLOViolation slo_violation, boolean suggested_adaptation, DetectorSubcomponent detector){
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

    public boolean isSuggested_adaptation() {
        return suggested_adaptation;
    }

    public void setSuggested_adaptation(boolean suggested_adaptation) {
        this.suggested_adaptation = suggested_adaptation;
    }

    /**
     * This method evaluates whether the taken action for the SLO Violation was appropriate or not
     * @param action_name The name of the action that was taken
     * @param last_reconfiguration_timestamp The last reconfiguration timestamp (i.e slo violation that resulted into a notification for a reconfiguration) as provided by the calling method 
     * @param last_slo_violation The last slo violation
     * @return
     */
    public boolean evaluate_correctness(boolean was_adaptation_suggested, ViolationHandlingActionName action_name, Long last_reconfiguration_timestamp, SLOViolation last_slo_violation, SeverityClass severity_class) {
        Long last_slo_violation_timestamp = last_slo_violation.getTime_calculated();
        boolean was_correct_handling_action = false;
        long reward_seconds=0;
        String reason_documenting_correctness_evaluation;
        Long current_time = System.currentTimeMillis();

        if (slo_violation.equals(last_slo_violation)){
            was_correct_handling_action = true;
            reward_seconds = (current_time-last_slo_violation_timestamp)/1000;
            reason_documenting_correctness_evaluation= "Last overall SLO violation is the same as the SLO being examined, after "+1.1*time_horizon_seconds+" seconds, therefore since after more than one time horizon interval there has been no need for an adaptation, the last decision was correct.";
            if ((slo_violation.getViolation_handling_action().handling_action_name.equals(ViolationHandlingActionName.drop_reconfiguration_and_change)  ||
                slo_violation.getViolation_handling_action().handling_action_name.equals
             (ViolationHandlingActionName.drop_reconfiguration_and_do_not_change))){
                reward_seconds = 2*reward_seconds;
                reason_documenting_correctness_evaluation = reason_documenting_correctness_evaluation+" The reward is doubled as the suggested action was to drop the reconfiguration";
            }
        }else{
            //The option below checks for the last reconfiguration. However, a reconfiguration should be mapped to this slo violation in order to have meaning (in the case that the component receiving the SLO violation blocks until the reconfiguration is complete, we can assume there is an implicit mapping). 
            // In addition to this we will measure the interval between two SLO Violations - if this is larger than the time horizon then not adapting was correct, otherwise not adapting was incorrect
            // We define the last "interesting" time point, as one that is the maximum between the last reconfiguration timestamp and the last slo violation timestamp. We examine the interval between the current time and this time point to determine if the decision was correct or not. We calculate below an interval of one year, but this is not something to calculate on, as in the case that the last slo violation is the one which is currently being evaluated then we set the timestamp of this last slo violation to zero.
            Long last_interesting_timepoint = Math.max(last_slo_violation_timestamp, last_reconfiguration_timestamp);
            Long first_interesting_timepoint = Math.min(last_slo_violation_timestamp, last_reconfiguration_timestamp);
            //was_correct_decision = current_time -last_reconfiguration_timestamp > time_horizon_seconds*1000L;
            
            //was_correct_decision = 
            //last_interesting_timepoint < slo_violation.getTime_calculated() ||
            //last_interesting_timepoint - slo_violation.getTime_calculated() > time_horizon_seconds*1000L;

            //Long late_difference =  (last_interesting_timepoint-slo_violation.getTime_calculated());
            //Long early_difference = (first_interesting_timepoint-slo_violation.getTime_calculated());
            //Long effective_difference = Math.min(Math.max(late_difference,0),Math.max(early_difference,0));
            
            Long late_difference =  (current_time - last_interesting_timepoint);
            Long early_difference = (current_time - first_interesting_timepoint);
            

            
            reconfiguration_triggered_or_happened_during_last_time_horizon = ( 
                    (early_difference<time_horizon_seconds*1000L) ||
                    (late_difference<time_horizon_seconds*1000L)
            ); //TODO possibly delete, since we infer that a new SLO was created by the fact that the new and examined SLOs are not the same - and therefore we do not need this check necessarily

     
            if (
                slo_violation.getViolation_handling_action().handling_action_name.equals(ViolationHandlingActionName.drop_reconfiguration_and_change) ||
                slo_violation.getViolation_handling_action().handling_action_name.equals(ViolationHandlingActionName.drop_reconfiguration_and_do_not_change) ||
                (slo_violation.getViolation_handling_action().handling_action_name.equals(ViolationHandlingActionName.consult_threshold_and_change) && !slo_violation.getViolation_handling_action().suggested_adaptation) ||
                (slo_violation.getViolation_handling_action().handling_action_name.equals(ViolationHandlingActionName.consult_threshold_and_do_not_change) && !slo_violation.getViolation_handling_action().suggested_adaptation)
            ){
                    was_correct_handling_action = false;
                    reward_seconds = - Math.max(early_difference,late_difference)/1000;
                    reason_documenting_correctness_evaluation = "For the SLO being examined, we unfortunately decided to drop the adaptation notification, yet during the time horizon interval that elapsed, a new reconfiguration was detected or initiated";
            }
            else {
                was_correct_handling_action = true;
                reward_seconds = (current_time-last_slo_violation_timestamp)/1000;
                reason_documenting_correctness_evaluation = "Two consecutive Reconfiguration triggers were observed, therefore the first reconfiguration was needed";
                //reason_documenting_correctness_evaluation = "Since there was an SLO/Reconfiguration detected after the SLO being evaluated, although we will not generate a negative reward since it was not suggested to forego the triggering of a reconfiguration, we can also not assign a positive reward since the action did not improve the situation - therefore assigning a 0 reward, and noting the action as marginally incorrect";
            }
        }
        
        
        double old_threshold_value = severity_class.getAdaptation_threshold().getValue();
        
        if (
                handling_action_name.equals(ViolationHandlingActionName.consult_threshold_and_change) ||
                        handling_action_name.equals(ViolationHandlingActionName.drop_reconfiguration_and_change) ||
                        handling_action_name.equals(ViolationHandlingActionName.send_reconfiguration_and_change)
        ) {
            if (was_correct_handling_action) {

                switch (handling_action_name){
                    case consult_threshold_and_change -> {
                        if(was_adaptation_suggested) {
                            Logger.getGlobal().log(info_logging_level, "Made a correct violation_handling_action to reconfigure, keeping the same threshold");
                        }else{
                            Logger.getGlobal().log(info_logging_level, "Made a correct violation_handling_action not to reconfigure, keeping the same threshold");
                        }
                    }
                    case drop_reconfiguration_and_change -> {
                        Logger.getGlobal().log(info_logging_level, "Made a correct violation_handling_action to drop the reconfiguration,increasing the threshold");
                        severity_class.increase_threshold();
                    }
                    case send_reconfiguration_and_change -> {
                        Logger.getGlobal().log(info_logging_level, "Made a correct violation_handling_action to send the reconfiguration anyway, decreasing the threshold");
                        severity_class.decrease_threshold();
                    }
                }


            } else {
                switch (handling_action_name){
                    case consult_threshold_and_change -> {
                        if(was_adaptation_suggested) {
                            Logger.getGlobal().log(info_logging_level, "Made a wrong violation_handling_action to reconfigure, increasing the threshold");
                            severity_class.increase_threshold();
                        }else{
                            Logger.getGlobal().log(info_logging_level, "Made a wrong violation_handling_action not to reconfigure, decreasing the threshold");
                            severity_class.decrease_threshold();
                        }
                    }
                    case drop_reconfiguration_and_change -> {
                        Logger.getGlobal().log(info_logging_level, "Made a wrong violation_handling_action to drop the reconfiguration,decreasing the threshold");
                        severity_class.decrease_threshold();
                    }
                    case send_reconfiguration_and_change -> {
                        Logger.getGlobal().log(info_logging_level, "Made a wrong violation_handling_action to send the reconfiguration anyway, increasing the threshold");
                        severity_class.increase_threshold();
                    }
                }
            }
        }else{
            Logger.getGlobal().log(info_logging_level, "Not modifying the threshold for the related severity class due to a "+handling_action_name+" handling action");
        }        
        
        //Update the old Q(s,a), with the new data. The maximum choice is between Q(s',a'), where s' consists of the updated new severity value (the one we receive from the latest slo violation we have detected whichever it is) and the new threshold (resulting from the update done above)

        double severity_value = slo_violation.getSeverity_result().getSeverityValue();
        double new_severity_value = last_slo_violation.getSeverity_result().getSeverityValue();
        
        QTableEntry old_q_table_state = associated_detector.getSubcomponent_state().getQ_table().get_entry(severity_value,old_threshold_value, action_name);
        
        double old_q_table_state_value = old_q_table_state.getQ_table_value();
        QTableEntry new_q_table_state = old_q_table_state.update(severity_value,new_severity_value,old_threshold_value,associated_detector.getDm().getSeverity_class_model().get_severity_class(severity_value).getAdaptation_threshold().getValue(),reward_seconds);
        
        double new_q_table_state_value = new_q_table_state.getQ_table_value();
        //Updating Q-table database entries 
        associated_detector.getSubcomponent_state().add_q_table_database_entry(
                associated_detector.get_application_name(),
                severity_value,
                associated_detector.getDm().getSeverity_class_model().get_severity_class(severity_value).getAdaptation_threshold().getValue(),
                action_name,
                new_q_table_state_value
        );

        Logger.getGlobal().log(info_logging_level,
                "Checking last decision...\n" +
                        "SLO Violation:\t\t\t\t\t" + slo_violation + ",\n" +
                        "Proposed action was:\t\t\t"+action_name+" ("+(slo_violation.getViolation_handling_action().suggested_adaptation?"suggested adaptation":"did not suggest adaptation")+"),\n"+
                        "Last reconfiguration timestamp:\t" + last_reconfiguration_timestamp + ",\n" +
                        "Last slo violation timestamp:\t" + last_slo_violation_timestamp + ",\n" +
                        "Time horizon (seconds):\t\t\t" + time_horizon_seconds + ".\n" +
                        "Current time is:\t\t\t\t"+current_time+"\n" +
                        "Calculating if decision was appropriate... The result is: " + (was_correct_handling_action ? "correct" : "incorrect")+"\n" +
                        "The reason for this assessment is: "+reason_documenting_correctness_evaluation+"\n"+
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
