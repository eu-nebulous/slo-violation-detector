package slo_violation_detector_engine.detector;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import reinforcement_learning.QTable;
import slo_rule_modelling.SLORule;
import utilities.ViolationHandlingActionNames;
import utility_beans.monitoring.MonitoringAttributeStatistics;
import utility_beans.monitoring.RealtimeMonitoringAttribute;
import utility_beans.reconfiguration_suggestion.ReconfigurationDetails;
import utility_beans.reconfiguration_suggestion.SLOViolation;

import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;import java.util.logging.Logger;

import static configuration.Constants.*;

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
    private CircularFifoQueue<SLOViolation> slo_violations = new CircularFifoQueue<>();
    private CircularFifoQueue<Long> deployment_timestamps = new CircularFifoQueue<>();
    public static final Object slo_violations_list_lock = new Object();
    private long last_optimizer_adaptation_initiation_timestamp =-1;
    private SLOViolation last_slo_violation_triggering_optimizer;
    private DetectorSubcomponent associated_detector;


    private Connection conn = DriverManager.getConnection(slo_violations_database_url,database_username,database_password);
    //Debugging variables
    public CircularFifoQueue<Long> slo_violation_event_recording_queue = new CircularFifoQueue<>(50);
    public CircularFifoQueue<String> severity_calculation_event_recording_queue = new CircularFifoQueue<>(50);
	public CircularFifoQueue<ReconfigurationDetails> reconfiguration_time_recording_queue = new CircularFifoQueue<>(50);
    public static final Object deploymentTimeRecordingQueueLock = new Object();
    
    private QTable q_table = new QTable(conn);
    public DetectorSubcomponentState(DetectorSubcomponent detector) throws SQLException {
        Statement statement = conn.createStatement();
        String createSLOViolationTableSQL = "CREATE TABLE IF NOT EXISTS slo_violations ("
                            + "id INT AUTO_INCREMENT PRIMARY KEY, "
                            + "application_name VARCHAR(255) NOT NULL,"
                            + "rule_string VARCHAR(10000) NOT NULL,"                            
                            + "rule_severity REAL NOT NULL,"
                            + "current_threshold REAL NOT NULL,"
                            + "targeted_prediction_time BIGINT NOT NULL)";
        String createQTableSQL = "CREATE TABLE IF NOT EXISTS q_table ("
                + "id INT AUTO_INCREMENT, "
                + "application_name VARCHAR(255) NOT NULL,"
                + "severity_value REAL NOT NULL,"
                + "current_threshold REAL NOT NULL,"
                + "action VARCHAR(255) NOT NULL,"
                + "q_value REAL NOT NULL,"
                + "PRIMARY KEY (application_name,severity_value,current_threshold,action)"
                +")";
        statement.executeUpdate(createSLOViolationTableSQL);
        
        statement.executeUpdate(createQTableSQL);
        Logger.getGlobal().log(info_logging_level,"Sql tables for slo violations and the q-table were created");
        associated_detector = detector; 
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
    
	public CircularFifoQueue<ReconfigurationDetails> getReconfiguration_time_recording_queue() {
	    return reconfiguration_time_recording_queue;
    }
	public void setReconfiguration_time_recording_queue(CircularFifoQueue<ReconfigurationDetails> reconfiguration_time_recording_queue) {
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
            Logger.getGlobal().log(severe_logging_level,"Failed to connect to database: " + e.getMessage());
            // Handle the exception appropriately
        }

        if (rowsAffected > 0) {
            Logger.getGlobal().log(info_logging_level,"New record inserted successfully to the slo violations table!");
//            try {
//                conn.close();
//            } catch (SQLException e) {
//                throw new RuntimeException(e);
//            }
        } else {
            Logger.getGlobal().log(severe_logging_level,"Failed to insert new record to the slo violations table.");
        }
        
        //TODO conn.close();
    }
    
    
    public void add_q_table_database_entry(String application_name, double severity_value, double current_threshold, ViolationHandlingActionNames action, double q_value){
        
        double quantized_severity_value = (int) Math.round(severity_value*100);
        double quantized_current_threshold = (int) Math.round(current_threshold*100);
        
        int rowsAffected=0;
        try {

            //UPDATE slo_violations SET application_name = '_App1'
            //WHERE  current_threshold = 0.1
            PreparedStatement stmt = conn.prepareStatement("" +
                    "UPDATE Q_TABLE SET q_value =? WHERE application_name =? AND severity_value =? AND current_threshold =? AND action =?");

            stmt.setDouble(1, q_value);
            stmt.setString(2, application_name);
            stmt.setDouble(3, quantized_severity_value);
            stmt.setDouble(4, quantized_current_threshold);
            stmt.setString(5, String.valueOf(action));

            rowsAffected = stmt.executeUpdate(); // Execute the insert query
            stmt.close();
            
            if (rowsAffected == 0) {
                Logger.getGlobal().log(info_logging_level,"Inserting new record into the Q table.");
                stmt = conn.prepareStatement("" +
                        "INSERT INTO Q_TABLE (application_name,severity_value,current_threshold,action,q_value) VALUES (?,?,?,?,?)"
                );

                stmt.setString(1, application_name);
                stmt.setDouble(2, quantized_severity_value);
                stmt.setDouble(3, quantized_current_threshold);
                stmt.setString(4, String.valueOf(action));
                stmt.setDouble(5, q_value);
                rowsAffected = stmt.executeUpdate(); // Execute the insert query
                stmt.close();
            }
            

        } catch (SQLException e) {
            Logger.getGlobal().log(severe_logging_level,"Failed to connect to database: " + e.getMessage());
            // Handle the exception appropriately
        }

        if (rowsAffected > 0) {
            Logger.getGlobal().log(info_logging_level,"New record inserted successfully to the q-table!");
//            try {
//                conn.close();
//            } catch (SQLException e) {
//                throw new RuntimeException(e);
//            }
        } else {
            Logger.getGlobal().log(severe_logging_level,"Failed to insert new record to the q-table.");
        }
    }
    
    
    public void submitSLOViolation (SLOViolation slo_violation){
        synchronized (slo_violations_list_lock) {
            slo_violations.add(slo_violation);
        }
    }

    public CircularFifoQueue<SLOViolation> getSlo_violations() {
        return slo_violations;
    }

    public void setSlo_Violations(CircularFifoQueue<SLOViolation> slo_violations) {
        this.slo_violations = slo_violations;
    }

    public CircularFifoQueue<Long> getDeployment_timestamps() {
        return deployment_timestamps;
    }

    public long getLast_optimizer_adaptation_initiation_timestamp() {
        return last_optimizer_adaptation_initiation_timestamp;
    }

    public void setLast_optimizer_adaptation_initiation_timestamp(long last_optimizer_adaptation_initiation_timestamp) {
        this.last_optimizer_adaptation_initiation_timestamp = last_optimizer_adaptation_initiation_timestamp;
        this.last_slo_violation_triggering_optimizer = slo_violations.get((slo_violations.size()-1));
    }

    public SLOViolation getLast_slo_violation_triggering_optimizer() {
        return last_slo_violation_triggering_optimizer;
    }

    public long calculate_last_total_reconfiguration_time_from_last_slo() {
        return System.currentTimeMillis()-last_optimizer_adaptation_initiation_timestamp;
    }

    public QTable getQ_table() {
        return q_table;
    }

    public DetectorSubcomponent getAssociated_detector() {
        return associated_detector;
    }
}
