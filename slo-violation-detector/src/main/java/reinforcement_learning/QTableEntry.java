package reinforcement_learning;

import utility_beans.reconfiguration_suggestion.DecisionMaker;

import static configuration.Constants.*;

public class QTableEntry {
    
    private QTable associated_q_table;
    private double q_table_value;
    private DecisionMaker.ViolationDecisionEnum action;
    
    public QTableEntry(QTable q_table){
        q_table_value = q_learning_initial_value;
        associated_q_table = q_table;
    }
    public QTableEntry update(double severity_value, double severity_threshold, double seconds_from_last_adaptation){
        q_table_value = q_table_value + q_learning_learning_rate * (
                (100-100*Math.min(time_horizon_seconds,seconds_from_last_adaptation)/time_horizon_seconds)
                + q_learning_discounting_factor*Math.max(
                        get_q_table_entry_value(severity_value,severity_threshold, DecisionMaker.ViolationDecisionEnum.exploration),
                        get_q_table_entry_value(severity_value,severity_threshold,DecisionMaker.ViolationDecisionEnum.exploitation)
                )
                - q_table_value
        );
        return this;
    }
    
    public double get_q_table_entry_value(double severity_value, double severity_threshold, DecisionMaker.ViolationDecisionEnum action){
        return associated_q_table.get_entry(severity_value,severity_threshold,action).getQ_table_value();
    }

    public double getQ_table_value() {
        return q_table_value;
    }
}
