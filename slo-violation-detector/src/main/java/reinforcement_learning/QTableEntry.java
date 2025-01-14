package reinforcement_learning;

import utility_beans.reconfiguration_suggestion.ViolationHandlingActionName;

import java.util.logging.Logger;

import static configuration.Constants.*;
import static utilities.MathUtils.findmax;

public class QTableEntry {
    
    private QTable associated_q_table;
    private double q_table_value;
    private ViolationHandlingActionName action;
    
    public QTableEntry(QTable q_table){
        q_table_value = q_learning_initial_value;
        associated_q_table = q_table;
    }
    public QTableEntry update(double unquantized_severity_value, double unquantized_severity_threshold, double seconds_from_last_adaptation){
        int severity_value = (int)Math.round(unquantized_severity_value*100);
        int severity_threshold = (int)Math.round(unquantized_severity_threshold*100);
        double old_q_table_value = q_table_value;
        double new_q_table_value = q_table_value + q_learning_learning_rate * (
                (100*Math.min(time_horizon_seconds,seconds_from_last_adaptation)/time_horizon_seconds)
                + q_learning_discounting_factor*findmax(
                        get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.send_reconfiguration_and_change),
                        get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.send_reconfiguration_and_do_not_change),
                        get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.drop_reconfiguration_and_change),
                        get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.drop_reconfiguration_and_do_not_change),
                        get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.consult_threshold_and_change),
                        get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.consult_threshold_and_do_not_change)
                )
                - q_table_value
        );
        Logger.getGlobal().log(info_logging_level,"q function updating:\nqtable_value("+severity_value+","+severity_threshold+") = "+old_q_table_value+" + "+q_learning_learning_rate+" * (( 100 * min("+time_horizon_seconds+","+seconds_from_last_adaptation+")/"+time_horizon_seconds+" + "+q_learning_discounting_factor+" * max((send_rec_c,"+get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.send_reconfiguration_and_change)+"),(send_rec_nc,"+get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.send_reconfiguration_and_do_not_change)+"),(drop_rec_c,"+get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.drop_reconfiguration_and_change)+"),(drop_rec_nc,"+get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.drop_reconfiguration_and_do_not_change)+"),(cons_thr_c,"+get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.consult_threshold_and_change)+"),(cons_thr_nc,"+get_q_value_for_other_table_entry(severity_value,severity_threshold, ViolationHandlingActionName.consult_threshold_and_do_not_change)+")) - "+old_q_table_value+") = "+new_q_table_value);
        
        q_table_value = new_q_table_value;
        return this;
    }
    
    private double get_q_value_for_other_table_entry(int severity_value, int severity_threshold, ViolationHandlingActionName action){
        return associated_q_table.get_entry(severity_value,severity_threshold,action).getQ_table_value();
    }

    public double getQ_table_value() {
        return q_table_value;
    }

    public void setQ_table_value(double q_table_value) {
        this.q_table_value = q_table_value;
    }
}
