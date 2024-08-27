package slo_violation_detector_engine.detector;

import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_rule_modelling.SLORule;
import utility_beans.monitoring.MonitoringAttributeStatistics;
import utility_beans.monitoring.RealtimeMonitoringAttribute;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

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

    public HikariDataSource slo_violations_database = null;

    //Debugging variables
    public CircularFifoQueue<Long> slo_violation_event_recording_queue = new CircularFifoQueue<>(50);
    public CircularFifoQueue<String> severity_calculation_event_recording_queue = new CircularFifoQueue<>(50);



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

    public HikariDataSource getSlo_violations_database() {
        return slo_violations_database;
    }

    public void setSlo_violations_database(HikariDataSource slo_violations_database) {
        this.slo_violations_database = slo_violations_database;
    }

    public void add_violation_record(String rule_string, double rule_severity, double slo_violation_probability, Long targeted_prediction_time) {
        String url = "jdbc:h2:file:path/to/your/database.mv.db"; // For a file-based database
        int rowsAffected = 0;
        try {
            //Connection conn = DriverManager.getConnection(url, "sa", ""); // Username and password (default is sa and empty)
            Connection conn = this.slo_violations_database.getConnection();
            PreparedStatement stmt = conn.prepareStatement("INSERT INTO SLO_VIOLATIONS (column1, column2, ...) VALUES (?, ? , ...)");
            stmt.setString(1, rule_string);  // Set values for each parameter in the order they appear in the query
            //TODO stmt.setInt(2, rule_severity);
            // ...

            rowsAffected = stmt.executeUpdate(); // Execute the insert query

        } catch (SQLException e) {
            System.err.println("Failed to connect to database: " + e.getMessage());
            // Handle the exception appropriately
        }

        if (rowsAffected > 0) {
            System.out.println("New record inserted successfully!");
        } else {
            System.err.println("Failed to insert new record.");
        }
        //TODO stmt.close();
        //TODO conn.close();
    }
}
