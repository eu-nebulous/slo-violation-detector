package reinforcement_learning;

import utility_beans.reconfiguration_suggestion.DecisionMaker;

public class QTable {
    private final QTableEntry[][][] q_table = new QTableEntry[101][101][2];
    private long updates = 0;
    public QTable(){
        for(int i=0 ; i<101 ; i++){
            for (int j=0 ; j<101 ;j++){
                for (int k=0 ; k<2 ; k++) {
                    q_table[i][j][k] = new QTableEntry(this);
                }
            }
        }
    }
    
    public QTableEntry get_entry(double severity_value, double severity_threshold, DecisionMaker.ViolationDecisionEnum action){
        int quantized_severity_value  = (int) Math.round(severity_value*100);
        int quantized_current_threshold  = (int) Math.round(severity_threshold*100);
        return q_table[quantized_severity_value][quantized_current_threshold][action.ordinal()];
    }

    public QTableEntry get_entry(int severity_value, int current_threshold, DecisionMaker.ViolationDecisionEnum action){
        return q_table[severity_value][current_threshold][action.ordinal()];
    }
    
}
