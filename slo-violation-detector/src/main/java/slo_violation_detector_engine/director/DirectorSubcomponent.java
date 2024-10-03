package slo_violation_detector_engine.director;

import eu.nebulouscloud.exn.Connector;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import slo_violation_detector_engine.generic.SLOViolationDetectorSubcomponent;
import utility_beans.broker_communication.BrokerPublisher;
import utility_beans.broker_communication.BrokerSubscriber;
import utility_beans.broker_communication.BrokerSubscriptionDetails;
import utility_beans.generic_component_functionality.CharacterizedThread;
import utility_beans.monitoring.RealtimeMonitoringAttribute;
import utility_beans.synchronization.SynchronizedBoolean;
import utility_beans.synchronization.SynchronizedStringMap;

import java.text.NumberFormat;
import java.time.Clock;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.detector_subcomponents;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.get_associated_detector;
import static slo_violation_detector_engine.generic.ComponentState.*;
import static utilities.OperationalModeUtils.get_director_publishing_topics;
import static utilities.OperationalModeUtils.get_director_subscription_topics;

public class DirectorSubcomponent extends SLOViolationDetectorSubcomponent {
    public HashMap<String,Thread> persistent_running_director_threads = new HashMap<>();
    public Connector subscribing_connector;
    private Integer id = 1;
    public static HashMap<String,DirectorSubcomponent> director_subcomponents = new HashMap<>();
    private static DirectorSubcomponent master_director;
    public static boolean first_run = true;
    public final AtomicBoolean stop_signal = new AtomicBoolean(false);
    public static SynchronizedStringMap MESSAGE_CONTENTS = new SynchronizedStringMap();
    public final SynchronizedBoolean can_modify_monitoring_metrics = new SynchronizedBoolean(false);

    private String director_name;

    public static DirectorSubcomponent getMaster_director() {
        return master_director;
    }

    public static void setMaster_director(DirectorSubcomponent master_director) {
        DirectorSubcomponent.master_director = master_director;
    }

    public DirectorSubcomponent(){
        super.thread_type = CharacterizedThread.CharacterizedThreadType.persistent_running_director_thread;
        create_director_topic_subscribers();
        director_name = "director_"+id;
        director_subcomponents.put(director_name,this);
        master_director = this;
        id++;
    }

    private void create_director_topic_subscribers(){
        if (first_run){
            //Creation of threads that should always run and are independent of the monitored application.
            //1. Creation of the metric list input subscriber thread, which listens for the metrics to be considered
            //2. Creation of the slo rule input subscriber thread, which listens for new slo rules to be considered
            //3. Creation of the lost device subscriber thread, which listens for a new event signalling a lost edge device

            //Metric list subscription thread
            BrokerSubscriber metric_list_subscriber = new BrokerSubscriber(metric_list_topic, prop.getProperty("broker_ip_url"),  Integer.parseInt(prop.getProperty("broker_port")),prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location,EMPTY);
            Runnable metric_list_topic_subscriber_runnable = () -> {
                boolean did_not_finish_execution_gracefully = true;
                while (did_not_finish_execution_gracefully) {
                    int exit_status = metric_list_subscriber.subscribe(metric_list_subscriber_function, EMPTY,this.stop_signal); //This subscriber should not be immune to stop signals
                    if (exit_status!=0) {
                        Logger.getGlobal().log(info_logging_level,"Broker unavailable, will try to reconnect after 10 seconds");
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException i) {
                            Logger.getGlobal().log(info_logging_level, "Sleep was interrupted, will immediately try to connect to the broker");
                        }
                    }else{
                        did_not_finish_execution_gracefully = false;
                    }
                }
                persistent_running_director_threads.remove(Thread.currentThread().getName().split(NAME_SEPARATOR)[0]);
            };
            CharacterizedThread.create_new_thread(metric_list_topic_subscriber_runnable,"metric_list_topic_subscriber_thread",true,this, CharacterizedThread.CharacterizedThreadType.persistent_running_director_thread);



            //SLO rule subscription thread
            BrokerSubscriber slo_rule_topic_subscriber = new BrokerSubscriber(slo_rules_topic, prop.getProperty("broker_ip_url"),  Integer.parseInt(prop.getProperty("broker_port")),prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location,EMPTY);
            Runnable slo_rules_topic_subscriber_runnable = () -> {
                boolean did_not_finish_execution_gracefully = true;
                while (did_not_finish_execution_gracefully) {
                    int exit_status = slo_rule_topic_subscriber.subscribe(slo_rule_topic_subscriber_function, EMPTY,stop_signal); //This subscriber should not be immune to stop signals
                    if (exit_status!=0) {
                        Logger.getGlobal().log(info_logging_level, "Broker unavailable, will try to reconnect after 10 seconds");
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException i) {
                            Logger.getGlobal().log(info_logging_level, "Sleep was interrupted, will immediately try to connect to the broker");
                        }
                    }else{
                        did_not_finish_execution_gracefully = false;
                    }
                }
                persistent_running_director_threads.remove(Thread.currentThread().getName().split(NAME_SEPARATOR)[0]);
            };
            CharacterizedThread.create_new_thread(slo_rules_topic_subscriber_runnable,"slo_rules_topic_subscriber_thread",true,this, CharacterizedThread.CharacterizedThreadType.persistent_running_director_thread);



            BrokerSubscriber device_lost_subscriber = new BrokerSubscriber(topic_for_lost_device_announcement, broker_ip, broker_port, broker_username, broker_password, amq_library_configuration_location,EMPTY);
            BiFunction<BrokerSubscriptionDetails, String, String> device_lost_subscriber_function = (broker_details, message) -> {
                BrokerPublisher persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, broker_ip, broker_port, broker_username, broker_password, amq_library_configuration_location, true);

                Clock clock = Clock.systemUTC();
                Long current_time_seconds = (long) Math.floor(clock.millis()/1000.0);
                JSONObject severity_json = new JSONObject();
                if (publish_normalized_severity){
                    severity_json.put("severity", 1.0);
                }else{
                    severity_json.put("severity", 100.0);
                }
                severity_json.put("probability", 100.0);
                severity_json.put("predictionTime", current_time_seconds);
                persistent_publisher.publish(severity_json.toJSONString(), Collections.singleton(EMPTY));

                return topic_for_lost_device_announcement + ":MSG:" + message;
            };


            //Implementation of 'Lost edge device' thread

            Runnable device_lost_topic_subscriber_runnable = () -> {
                boolean did_not_finish_execution_gracefully = true;
                while (did_not_finish_execution_gracefully) {
                    int exit_status = device_lost_subscriber.subscribe(device_lost_subscriber_function, EMPTY,stop_signal); //This subscriber should not be immune to stop signals, else there would be new AtomicBoolean(false)
                    if (exit_status!=0) {
                        Logger.getGlobal().log(info_logging_level, "A device used by the platform was lost, will therefore trigger a reconfiguration");
                        try {
                            Thread.sleep(10000);
                        } catch (InterruptedException i) {
                            Logger.getGlobal().log(info_logging_level, "Sleep was interrupted, will immediately try to connect to the broker");
                        }
                    }else{
                        did_not_finish_execution_gracefully = false;
                    }
                }
                persistent_running_director_threads.remove(Thread.currentThread().getName().split(NAME_SEPARATOR)[0]);
            };

            CharacterizedThread.create_new_thread(device_lost_topic_subscriber_runnable,"device_lost_topic_subscriber_thread",true,this, CharacterizedThread.CharacterizedThreadType.persistent_running_director_thread);


        }
        first_run = false;


        for (String subscription_topic : get_director_subscription_topics()){
            //TODO subscribe to each topic, creating a Characterized thread for each of them

        }
        for (String publishing_topic : get_director_publishing_topics()){
            //TODO do the same for publishing topics
        }
        //subscribing_connector = new Connector("slovid_director",)
    }

    public BiFunction<BrokerSubscriptionDetails, String, String> metric_list_subscriber_function = (broker_details, message) -> {
        synchronized (can_modify_monitoring_metrics) {
            can_modify_monitoring_metrics.setValue(true);
            MESSAGE_CONTENTS.assign_value(broker_details.getApplication_name(),metric_list_topic, message);
            String metric_name;
            double lower_bound,upper_bound;
            JSONParser parser = new JSONParser();
            JSONObject metric_list_object;
            try {
                metric_list_object = (JSONObject) parser.parse(message);
                //DetectorSubcomponent associated_detector = get_associated_detector(broker_details.getApplication_name());
                String application_name = (String) metric_list_object.get("name");
                DetectorSubcomponent associated_detector = get_associated_detector(application_name);
                for (Object element : (JSONArray) metric_list_object.get("metric_list")){
                    metric_name = (String)((JSONObject)element).get("name");
                    String lower_bound_str = String.valueOf(((JSONObject)element).get("lower_bound"));
                    String upper_bound_str = String.valueOf(((JSONObject)element).get("upper_bound"));
                    NumberFormat numberFormat = NumberFormat.getInstance();
                    Number lower_bound_number, upper_bound_number;
                    boolean is_lower_bound_integer=false,is_lower_bound_double=false;
                    boolean is_upper_bound_integer = false,is_upper_bound_double=false;
                    if (!(lower_bound_str.equalsIgnoreCase("-inf") || lower_bound_str.equalsIgnoreCase("-infinity"))){
                        lower_bound_number =  numberFormat.parse(lower_bound_str);
                        if (lower_bound_number instanceof Integer){
                            is_lower_bound_integer = true;
                            is_lower_bound_double = false;
                        }else if (lower_bound_number instanceof Double){
                            is_lower_bound_double = true;
                            is_lower_bound_integer = false;
                        }
                        lower_bound = lower_bound_number.doubleValue();
                    }else{
                        lower_bound = Double.NEGATIVE_INFINITY;
                    }

                    if (!(upper_bound_str.equalsIgnoreCase("inf") || upper_bound_str.equalsIgnoreCase("infinity"))){
                        upper_bound_number =  numberFormat.parse(upper_bound_str);
                        if (upper_bound_number instanceof Integer){
                            is_upper_bound_integer = true;
                            is_upper_bound_double = false;
                        }else if (upper_bound_number instanceof Double){
                            is_upper_bound_double = true;
                            is_upper_bound_integer = false;
                        }
                        upper_bound = upper_bound_number.doubleValue();
                    }else{
                        upper_bound = Double.POSITIVE_INFINITY;
                    }
                    RealtimeMonitoringAttribute.AttributeValuesType attribute_type;
                    if (is_upper_bound_integer && is_lower_bound_integer){
                        attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Integer;
                    }else if (is_lower_bound_double || is_upper_bound_double){
                        attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Double;
                    }else{
                        attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Unknown;
                    }

                    associated_detector.getSubcomponent_state().getMonitoring_attributes().put(metric_name,new RealtimeMonitoringAttribute(metric_name,lower_bound,upper_bound,attribute_type));
                }
            }catch (Exception e){
                e.printStackTrace();
            }

            //slo_rule_arrived.set(true);
            can_modify_monitoring_metrics.notifyAll();

            Logger.getGlobal().log(info_logging_level, "BrokerClientApp:  - Received text message: " + message + " at topic " + metric_list_topic);

        }
        return "Monitoring metrics message processed";
    };

    public static BiFunction<BrokerSubscriptionDetails, String, String> slo_rule_topic_subscriber_function = (broker_details, message) -> {
        //DetectorSubcomponent new_detector = new DetectorSubcomponent(application, CharacterizedThread.CharacterizedThreadRunMode.detached);
        JSONParser parser = new JSONParser();
        String application;
        try{
            JSONObject object = (JSONObject) parser.parse(message);
            application = (String) object.get("name");
            Logger.getGlobal().log(info_logging_level,"Parsed a new slo rule for application "+application);
        }catch (ParseException e) {
            Logger.getGlobal().log(severe_logging_level,"Could not understand the slo rule which was received, skipping...");
            return EMPTY;
        }

        DetectorSubcomponent new_detector = get_associated_detector(application);
        synchronized (new_detector.can_modify_slo_rules) {
            new_detector.can_modify_slo_rules.setValue(true);
            MESSAGE_CONTENTS.assign_value(application,slo_rules_topic, message);
            new_detector.slo_rule_arrived.set(true);
            new_detector.can_modify_slo_rules.notifyAll();

            Logger.getGlobal().log(info_logging_level, "BrokerClientApp:  - Received text message: " + message + " at topic " + slo_rules_topic);

        }
        return slo_rules_topic + ":MSG:" + message;
    };

    @Override
    public String get_name() {
        return director_name;
    }
}
