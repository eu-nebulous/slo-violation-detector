package slo_violation_detector_engine.detector;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_rule_modelling.SLORule;
import utility_beans.monitoring.MonitoringAttributeStatistics;
import utility_beans.monitoring.RealtimeMonitoringAttribute;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;import java.util.logging.Logger;import static configuration.Constants.info_logging_level;

public class DetectorSubcomponentState{
    private HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_statistics = new HashMap<>();
    private HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_roc_statistics = new HashMap<>();
    private HashMap<String, RealtimeMonitoringAttribute> monitoring_attributes = new HashMap<>();

    private HashMap<String,String> monitoring_attributes_bounds_representation = new HashMap<>();

    public HashSet<Long> adaptation_times = new HashSet<>();
    public HashSet<Long> adaptation_times_pending_processing = new HashSet<>();

    public HashMap<String,Thread> slo_bound_running_threads = new HashMap<>();
    public HashMap<String,Thread> persistent_running_detector_threads = new HashMap<>();

    public HashSet<Long> adaptation_times_to_remove = new HashSet<>();

    public ArrayList<SLORule> slo_rules = new ArrayList<>();

    private static String slo_violations_database_url = "jdbc:h2:file:/home/andreas/Desktop/database.mv.db";
    private static String database_username = "sa";//TODO move to config
    private static String database_password = "";//TODO move to config
    private Connection conn = DriverManager.getConnection(slo_violations_database_url,database_username,database_password);
    //Debugging variables
    public CircularFifoQueue<Long> slo_violation_event_recording_queue = new CircularFifoQueue<>(50);
    public CircularFifoQueue<String> severity_calculation_event_recording_queue = new CircularFifoQueue<>(50);
	public CircularFifoQueue<Long> reconfiguration_time_recording_queue = new CircularFifoQueue<>(50);
    public static final Object reconfigurationTimeRecordingQueueLock = new Object();
    public DetectorSubcomponentState() throws SQLException {
        Statement statement = conn.createStatement();
        String createTableSQL = "CREATE TABLE IF NOT EXISTS slo_violations ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, "
                            + "application_name VARCHAR(255) NOT NULL,"
                            + "rule_string VARCHAR(10000) NOT NULL,"                            
                            + "rule_severity REAL NOT NULL,"
                            + "slo_violation_probability REAL NOT NULL,"
                            + "targeted_prediction_time BIGINT NOT NULL)";
        statement.executeUpdate(createTableSQL);
        Logger.getGlobal().log(info_logging_level,"Sql table created");
    }



    //private static HashMap<String, Double> monitoring_attributes_min_values = new HashMap<>();
    //private static HashMap<String, Double> monitoring_attributes_max_values = new HashMap<>();

    public Double get_initial_upper_bound(String attribute_name){

        if (monitoring_attributes_bounds_representation.get(attribute_name)==null) {
            return 100.0;
        }
        if (monitoring_attributes_bounds_representation.get(attribute_name).split(";")[1].equals("unbounded")){
            return Double.NEGATIVE_INFINITY;
        }else{
            return Double.parseDouble(monitoring_attributes_bounds_representation.get(attribute_name).split(";")[1]);
        }
    }

    public Double get_initial_lower_bound(String attribute_name){

        if (monitoring_attributes_bounds_representation.get(attribute_name)==null) {
            return 0.0;
        }
        if (monitoring_attributes_bounds_representation.get(attribute_name).split(";")[0].equals("unbounded")){
            return Double.POSITIVE_INFINITY;
        }else{
            return Double.parseDouble(monitoring_attributes_bounds_representation.get(attribute_name).split(";")[0]);
        }
    }

    public HashMap<String, MonitoringAttributeStatistics> getMonitoring_attributes_statistics() {
        return monitoring_attributes_statistics;
    }

    public void setMonitoring_attributes_statistics(HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_statistics) {
        this.monitoring_attributes_statistics = monitoring_attributes_statistics;
    }

    public HashMap<String, MonitoringAttributeStatistics> getMonitoring_attributes_roc_statistics() {
        return monitoring_attributes_roc_statistics;
    }

    public void setMonitoring_attributes_roc_statistics(HashMap<String, MonitoringAttributeStatistics> monitoring_attributes_roc_statistics) {
        this.monitoring_attributes_roc_statistics = monitoring_attributes_roc_statistics;
    }

    public HashMap<String, RealtimeMonitoringAttribute> getMonitoring_attributes() {
        return monitoring_attributes;
    }

    public void setMonitoring_attributes(HashMap<String, RealtimeMonitoringAttribute> monitoring_attributes) {
        this.monitoring_attributes = monitoring_attributes;
    }

    public HashMap<String, String> getMonitoring_attributes_bounds_representation() {
        return monitoring_attributes_bounds_representation;
    }

    public void setMonitoring_attributes_bounds_representation(HashMap<String, String> monitoring_attributes_bounds_representation) {
        this.monitoring_attributes_bounds_representation = monitoring_attributes_bounds_representation;
    }

    public HashSet<Long> getAdaptation_times_to_remove() {
        return adaptation_times_to_remove;
    }

    public void setAdaptation_times_to_remove(HashSet<Long> adaptation_times_to_remove) {
        this.adaptation_times_to_remove = adaptation_times_to_remove;
    }
    
	public CircularFifoQueue<Long> getReconfiguration_time_recording_queue() {
	    return reconfiguration_time_recording_queue;
    }
	public void setReconfiguration_time_recording_queue(CircularFifoQueue<Long> reconfiguration_time_recording_queue) {
	this.reconfiguration_time_recording_queue = reconfiguration_time_recording_queue;
    }

    public void add_violation_record(String application_name, String rule_string, double rule_severity, double current_threshold, Long targeted_prediction_time) {
        int rowsAffected = 0;
        try {
            
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO SLO_VIOLATIONS (application_name, rule_string, rule_severity,current_threshold,targeted_prediction_time) VALUES (?,?,?,?,?)");
            stmt.setString(1, application_name);
            stmt.setString(2, rule_string);
            stmt.setDouble(3, rule_severity);
            //stmt.setDouble(4, slo_violation_probability);
            stmt.setDouble (4, current_threshold);
            stmt.setLong(5, targeted_prediction_time);
            // Set values for each parameter in the order they appear in the query

            rowsAffected = stmt.executeUpdate(); // Execute the insert query
            stmt.close();

        } catch (SQLException e) {
            System.err.println("Failed to connect to database: " + e.getMessage());
            // Handle the exception appropriately
        }

        if (rowsAffected > 0) {
            System.out.println("New record inserted successfully!");
//            try {
//                conn.close();
//            } catch (SQLException e) {
//                throw new RuntimeException(e);
//            }
        } else {
            System.err.println("Failed to insert new record.");
        }
        
        //TODO stmt.close();
        //TODO conn.close();
    }
}
