package reinforcement_learning;

import utility_beans.reconfiguration_suggestion.ViolationHandlingActionName;
import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.logging.Logger;

import static configuration.Constants.info_logging_level;
import static configuration.Constants.slo_violations_database_url;

public class QTable {
    private final QTableEntry[][][] q_table = new QTableEntry[101][101][ViolationHandlingActionName.values().length];
    private long updates = 0;
    private final Connection conn;
    public QTable(Connection conn){
        Logger.getGlobal().log(info_logging_level,"Creating new q-table");
        this.conn = conn;
        for(int i=0 ; i<101 ; i++){
            for (int j=0 ; j<101 ;j++){
                for (int k = 0; k< ViolationHandlingActionName.values().length ; k++) {
                    q_table[i][j][k] = new QTableEntry(this);
                }
            }
        }        
        
        if (database_exists(slo_violations_database_url.replace("jdbc:h2:file:",""))) {
            Logger.getGlobal().log(info_logging_level,"Found existing q-table data");
            load_q_table_from_database();
        }else{
            Logger.getGlobal().log(info_logging_level,"No existing q-table data found at "+slo_violations_database_url.replace("jdbc:h2:file:",""));
        }
        

    }

    private void load_q_table_from_database() {
        String query = "SELECT severity_value, current_threshold, action, q_value " +
                "FROM q_table;";
        try {
            java.sql.Statement statement = this.conn.createStatement();
            java.sql.ResultSet rs = statement.executeQuery(query);


            while (rs.next()) {
                int severity_value = rs.getInt("severity_value");
                int current_threshold = rs.getInt("current_threshold");
                String action = rs.getString("action");
                double q_value = rs.getDouble("q_value");

                QTableEntry current_entry = get_entry(severity_value,current_threshold, ViolationHandlingActionName.valueOf(action));
                current_entry.setQ_table_value(q_value);
                // 
                Logger.getGlobal().log(info_logging_level,"Severity Value: " + severity_value);
                Logger.getGlobal().log(info_logging_level,"Current Threshold: " + current_threshold);
                Logger.getGlobal().log(info_logging_level,"Action: " + action);
                Logger.getGlobal().log(info_logging_level,"Q Value: " + q_value);
            }
            statement.close();
            conn.commit();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean database_exists(String dbFile) {
        File file = new File(dbFile);
        if (!file.exists()) {
            return false; // Database file does not exist
        }

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbFile)) {
            return conn.isValid(0); // Check if the connection is valid (i.e., database exists)
        } catch (SQLException e) {
            return false; // Error occurred, assume database does not exist
        }
    }



    public QTableEntry get_entry(double severity_value, double severity_threshold, ViolationHandlingActionName action){
        int quantized_severity_value  = (int) Math.round(severity_value*100);
        int quantized_current_threshold  = (int) Math.round(severity_threshold*100);
        return q_table[quantized_severity_value][quantized_current_threshold][action.ordinal()];
    }

    public QTableEntry get_entry(int severity_value, int current_threshold, ViolationHandlingActionName action){
        return q_table[severity_value][current_threshold][action.ordinal()];
    }
    
}
