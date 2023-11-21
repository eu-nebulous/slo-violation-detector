/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

//import eu.melodic.event.brokerclient.BrokerPublisher;
//import eu.melodic.event.brokerclient.BrokerSubscriber;
//import eu.melodic.event.brokerclient.templates.EventFields;
//import eu.melodic.event.brokerclient.templates.TopicNames;

import slo_violation_detector_engine.DetectorSubcomponent;
import utility_beans.*;
import utility_beans.BrokerSubscriber.*;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;

import slo_rule_modelling.SLORule;
import slo_rule_modelling.SLOSubRule;
import utilities.MonitoringAttributeUtilities;

import java.io.*;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_rule_modelling.SLORule.process_rule_value;
import static slo_violation_detector_engine.DetectorSubcomponentUtilities.initialize_subrule_and_attribute_associations;
import static utility_beans.CharacterizedThread.CharacterizedThreadRunMode.detached;
import static utility_beans.PredictedMonitoringAttribute.getPredicted_monitoring_attributes;
import static utility_beans.RealtimeMonitoringAttribute.update_monitoring_attribute_value;

class MetricConfiguration{
    public String name;
    public double base_metric_value;
    public double forecasted_metric_value;
    public double confidence_interval;
    public double probability;
    public MetricConfiguration(String name, double base_metric_value, double forecasted_metric_value, double confidence_interval, double probability){
        this.name = name;
        this.base_metric_value = base_metric_value;
        this.forecasted_metric_value = forecasted_metric_value;
        this.confidence_interval = confidence_interval;
        this.probability = probability;
    }
}

public class UnboundedMonitoringAttributeTests {

    /**
     * This 30-second test assumes the availability of a broker, which is configured in the standard configuration file location employed by the Main method. It also assumes that random input of a custom metric centered around a value (75 for the first test) is provided (using an independent data provider). Based on this constant input, the standard deviation and mean is calculated, and the lower/upper bounds are estimated - in the case of the first test it is assumed that the metric is upwards and downwards unbounded. The assertions of the test are estimations based on repeated iterations with 100-sample data.
     */

    //private String metric_1_name = "custom_metric_1";
    private static final Long targeted_prediction_time = 100000000000L;
    private final DetectorSubcomponent detector = new DetectorSubcomponent(default_handled_application_name,detached);
    @Test
    public void unbounded_monitoring_attribute_test_1() throws IOException, ParseException {
        unbounded_monitoring_attribute_test_core("src/main/resources/test_v3_custom_metric_1_simple.json","custom_metric_1",new Double[]{20.0,35.0},new Double[]{110.0,130.0},0.0,50,100, 90,10,0.80);
    }
    @Test
    public void unbounded_monitoring_attribute_test_2() throws IOException, ParseException {
        unbounded_monitoring_attribute_test_core("src/main/resources/test_v3_custom_metric_unequal.json","number_of_users",new Double[]{-50.0,0.0},new Double[]{0.0,50.0},0.0,-25,20,5,0.3,0.90);
    }


    public void unbounded_monitoring_attribute_test_core(String json_file_name, String metric_1_name, Double[] metric_lower_bound_range, Double[] metric_upper_bound_range, double severity_lower_bound, double base_metric_value, double metric_max_value, double forecasted_metric_value,double generated_data_confidence_interval, double probability) throws IOException, ParseException {
        detector.can_modify_slo_rules.setValue(true);
        Properties prop = new Properties();

        URI absolute_configuration_file_path = new File(configuration_file_location).toURI();
        base_project_path = new File("").toURI();
        URI relative_path  = base_project_path.relativize(absolute_configuration_file_path);

        InputStream inputStream = new FileInputStream(base_project_path.getPath()+relative_path.getPath());

        prop.load(inputStream);

        String broker_ip_address = prop.getProperty("broker_ip_address");
        String broker_username = prop.getProperty("broker_username");
        String broker_password = prop.getProperty("broker_password");

        String metric_string = metric_1_name+";unbounded;unbounded";
        String [] metric_names = {metric_string.split(";")[0]};
        detector.getSubcomponent_state().getMonitoring_attributes_bounds_representation().put(metric_string.split(";")[0], metric_string.split(";",2)[1]);


        slo_violation_determination_method = "all-metrics";
        JSONObject rule_json = (JSONObject) new JSONParser().parse(String.join(EMPTY, Files.readAllLines(Paths.get(new File(json_file_name).getAbsolutePath()))));

        ArrayList<SLORule> slo_rules = new ArrayList<>();
        SLORule slo_rule = new SLORule(rule_json.toJSONString(), new ArrayList<>(Arrays.asList(metric_1_name)),detector);
        slo_rules.add(slo_rule);
        initialize_subrule_and_attribute_associations(slo_rules,new SynchronizedBoolean());

        data_publisher_for_unbounded_test(metric_1_name, metric_max_value, base_metric_value,forecasted_metric_value,generated_data_confidence_interval,probability);

        ArrayList<Thread> running = new ArrayList<>();
        for (String metric_name : metric_names) {
            MonitoringAttributeUtilities.initialize_values(metric_name, detector.getSubcomponent_state());

            String realtime_metric_topic_name = TopicNames.realtime_metric_values_topic(metric_name);
            Logger.getAnonymousLogger().log(Level.INFO, "Starting realtime subscription at " + realtime_metric_topic_name);
            BrokerSubscriber subscriber = new BrokerSubscriber(realtime_metric_topic_name, broker_ip_address, broker_username, broker_password, amq_library_configuration_location);
            BiFunction<String, String, String> function = (topic, message) -> {
                synchronized (detector.getSubcomponent_state().getMonitoring_attributes().get(topic)) {
                    try {
                        update_monitoring_attribute_value(detector,topic, ((Number) ((JSONObject) new JSONParser().parse(message)).get("metricValue")).doubleValue());

                        Logger.getAnonymousLogger().log(info_logging_level, "RECEIVED message with value for " + topic + " equal to " + (((JSONObject) new JSONParser().parse(message)).get("metricValue")));
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                }
                return message;
            };
            Thread realtime_subscription_thread = new Thread(() -> {
                subscriber.subscribe(function,new AtomicBoolean(false)); //will be a short-lived test, so setting stop signal to false
                // Insert some method call here.
            });
            realtime_subscription_thread.start();
            running.add(realtime_subscription_thread);


            String forecasted_metric_topic_name = TopicNames.final_metric_predictions_topic(metric_name);
            BrokerSubscriber forecasted_subscriber = new BrokerSubscriber(forecasted_metric_topic_name, broker_ip_address,broker_username,broker_password, amq_library_configuration_location);
            BiFunction<String,String,String> forecasted_function = (topic,message) ->{
                String predicted_attribute_name = topic.replaceFirst("prediction\\.",EMPTY);
                HashMap<Integer, HashMap<Long, PredictedMonitoringAttribute>> predicted_attributes = getPredicted_monitoring_attributes();
                try {
                    double forecasted_value = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.metric_value)).doubleValue();
                    double probability_confidence = 100*((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.probability)).doubleValue();
                    //double confidence_interval = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.confidence_interval)).doubleValue();
                    JSONArray json_array_confidence_interval = ((JSONArray)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.confidence_interval));
                    double confidence_interval = ((Number)json_array_confidence_interval.get(1)).doubleValue() - ((Number)json_array_confidence_interval.get(0)).doubleValue();
                    long timestamp = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.timestamp)).longValue();
                    long targeted_prediction_time = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.prediction_time)).longValue();
                    Logger.getAnonymousLogger().log(info_logging_level,"RECEIVED message with predicted value for "+predicted_attribute_name+" equal to "+ forecasted_value);

                    synchronized (detector.can_modify_slo_rules) {
                        if(!detector.can_modify_slo_rules.getValue()) {
                            detector.can_modify_slo_rules.wait();
                        }
                        detector.can_modify_slo_rules.setValue(false);

                        if( detector.getSubcomponent_state().adaptation_times.size()==0 || (!detector.getSubcomponent_state().adaptation_times.contains(targeted_prediction_time)) && targeted_prediction_time>detector.getSubcomponent_state().adaptation_times.stream().min(Long::compare).get()){
                            Logger.getAnonymousLogger().log(info_logging_level,"Adding a new targeted prediction time "+targeted_prediction_time);
                            detector.getSubcomponent_state().adaptation_times.add(targeted_prediction_time);
                            synchronized (detector.PREDICTION_EXISTS) {
                                detector.PREDICTION_EXISTS.setValue(true);
                                detector.PREDICTION_EXISTS.notifyAll();
                            }
                        }
                        //predicted_attributes.get(predicted_attribute_name).clear();
                        for (SLOSubRule subrule : SLOSubRule.getSlo_subrules_per_monitoring_attribute().get(predicted_attribute_name)) {
                            getPredicted_monitoring_attributes().computeIfAbsent(subrule.getId(), k -> new HashMap<>());
                            if ( (getPredicted_monitoring_attributes().get(subrule.getId()).get(targeted_prediction_time)!=null) &&(getPredicted_monitoring_attributes().get(subrule.getId()).get(targeted_prediction_time).getTimestamp()>timestamp)){
                                //do nothing, as in this case an older prediction has arrived for a metric delayed, and so it should be disregarded
                            }else {
                                PredictedMonitoringAttribute prediction_attribute = new PredictedMonitoringAttribute(detector, predicted_attribute_name, subrule.getThreshold(), subrule.getId(), forecasted_value, probability_confidence, confidence_interval, timestamp,targeted_prediction_time);

                                //predicted_attributes.get(predicted_attribute_name).add(prediction_attribute);
                                subrule.setAssociated_predicted_monitoring_attribute(prediction_attribute);

                                getPredicted_monitoring_attributes().get(subrule.getId()).put(targeted_prediction_time, prediction_attribute);
                            }
                        }
                        detector.can_modify_slo_rules.setValue(true);
                    }
                    //SLOViolationCalculator.get_Severity_all_metrics_method(prediction_attribute)

                } catch (ParseException | InterruptedException e) {
                    e.printStackTrace();
                }
                return message;
            };
            Thread forecasted_subscription_thread = new Thread(() -> {
                synchronized (detector.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(forecasted_metric_topic_name)) {
                    //if (Main.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(forecasted_metric_topic_name).getValue())
                    forecasted_subscriber.subscribe(forecasted_function,new AtomicBoolean(false)); //will be a short-lived test, so setting stop signal to false
                }
            });
            running.add(forecasted_subscription_thread);
            forecasted_subscription_thread.start();

        }
        try{
            Thread.sleep(30000);
        }catch (Exception e){
            e.printStackTrace();
        }

        double upper_bound = detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(metric_1_name).getUpper_bound();
        double lower_bound = detector.getSubcomponent_state().getMonitoring_attributes_statistics().get(metric_1_name).getLower_bound();

        Logger.getAnonymousLogger().log(Level.INFO,"The bounds calculated are\nLower bound: "+lower_bound+"\nUpper bound: "+upper_bound);
        //assert (upper_bound<130 && upper_bound>110 && lower_bound>20 && lower_bound <35);

        SLORule rule = new SLORule(detector,rule_json.toJSONString());

        assert (upper_bound<metric_upper_bound_range[1] && upper_bound>metric_upper_bound_range[0] && lower_bound>metric_lower_bound_range[0] && lower_bound <metric_lower_bound_range[1]);

        double rule_severity = process_rule_value(rule,targeted_prediction_time);
        Logger.getAnonymousLogger().log(Level.INFO,"The severity calculated is\nSeverity: "+rule_severity);
        assert (rule_severity>severity_lower_bound);
    }


    @Test
    public void all_metrics_unbounded_monitoring_attribute_Severity_test(){

    }

    public void data_publisher_for_unbounded_test(String metric_name, double metric_max_value, double base_metric_value, double forecasted_metric_value, double confidence_interval, double probability ){

        int publish_interval_in_milliseconds = 100;
        MetricConfiguration custom_metric_1 = new MetricConfiguration(metric_name,base_metric_value,forecasted_metric_value,confidence_interval,probability);
        //MetricConfiguration ram_metric = new MetricConfiguration("ram",90,100,5,100);


        ArrayList<MetricConfiguration> metrics = new ArrayList<>();
        metrics.add(custom_metric_1);

        for (MetricConfiguration metric: metrics) {
            Thread publishing_thread = new Thread(() -> perpetual_metric_publisher(metric.name,metric.base_metric_value,metric.forecasted_metric_value,metric.confidence_interval,metric.probability, metric_max_value, publish_interval_in_milliseconds));
            publishing_thread.start();
        }
    }

    private static void perpetual_metric_publisher(String metric_name, double base_metric_value, double forecasted_metric_value, double confidence_interval, double probability, double metric_max_value, int publish_interval_in_milliseconds) {
        BrokerPublisher realtime_data_publisher = new BrokerPublisher(metric_name, "tcp://localhost:61616", "admin", "admin","src/main/resources/config/eu.melodic.event.brokerclient.properties");
        BrokerPublisher forecasted_data_publisher = new BrokerPublisher("prediction."+metric_name, "tcp://localhost:61616", "admin", "admin","src/main/resources/config/eu.melodic.event.brokerclient.properties");

        while (true) {
            try {
                JSONObject realtime_metric_json_object = new JSONObject();
                //Create values centered around 82.5
                double random_value = ThreadLocalRandom.current().nextDouble();
                realtime_metric_json_object.put("metricValue", base_metric_value+random_value*(metric_max_value-base_metric_value));
                realtime_metric_json_object.put("timestamp",System.currentTimeMillis());
                realtime_data_publisher.publish(realtime_metric_json_object.toJSONString());

                JSONObject forecasted_metric_json_object = new JSONObject();
                forecasted_metric_json_object.put("metricValue", forecasted_metric_value);
                forecasted_metric_json_object.put("timestamp",System.currentTimeMillis());
                forecasted_metric_json_object.put("probability",probability);
                forecasted_metric_json_object.put(EventFields.PredictionMetricEventFields.prediction_time,targeted_prediction_time);
                //((System.currentTimeMillis()/1000)%60)*60000+1); //The prediction supposedly reflects the metric values at the next minute
                JSONArray confidence_interval_list = new JSONArray();
                confidence_interval_list.add((forecasted_metric_value-confidence_interval/2));
                confidence_interval_list.add((forecasted_metric_value+confidence_interval/2));
                forecasted_metric_json_object.put("confidence_interval",confidence_interval_list);
                forecasted_data_publisher.publish(forecasted_metric_json_object.toJSONString());
                Thread.sleep(publish_interval_in_milliseconds);

            }catch (InterruptedException i){
                i.printStackTrace();
            }


        }
    }


}
