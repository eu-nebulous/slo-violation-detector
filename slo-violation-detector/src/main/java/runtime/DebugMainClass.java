/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package runtime;
import eu.melodic.event.brokerclient.BrokerPublisher;
import eu.melodic.event.brokerclient.BrokerSubscriber;
import metric_retrieval.AttributeSubscription;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import slo_processing.SLORule;
import slo_processing.SLOSubRule;
import utilities.DebugDataSubscription;
import utilities.MathUtils;
import utilities.MonitoringAttributeUtilities;
import utility_beans.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static configuration.Constants.*;
import static java.lang.Thread.sleep;
import static slo_processing.SLORule.process_rule_value;
import static utility_beans.PredictedMonitoringAttribute.getPredicted_monitoring_attributes;
import static utility_beans.RealtimeMonitoringAttribute.getMonitoring_attributes_bounds_representation;

public class DebugMainClass {
    public static final AtomicBoolean stop_signal = new AtomicBoolean(false);
    public static final SynchronizedBoolean PREDICTION_EXISTS = new SynchronizedBoolean(false);
    public static final SynchronizedBoolean ADAPTATION_TIMES_MODIFY = new SynchronizedBoolean(true);
    public static SynchronizedBooleanMap HAS_MESSAGE_ARRIVED = new SynchronizedBooleanMap();
    public static SynchronizedStringMap MESSAGE_CONTENTS = new SynchronizedStringMap();
    public static ArrayList<SLORule> slo_rules = new ArrayList<>();
    public static HashMap<String,Thread> running_threads = new HashMap<>();
    public static HashSet<Long> adaptation_times = new HashSet<>();
    public static HashSet<Long> adaptation_times_pending_processing = new HashSet<>();
    private static HashSet<Long> adaptation_times_to_remove = new HashSet<>();
    public static Long last_processed_adaptation_time = -1L;//initialization
    public static Long current_slo_rules_version = -1L;//initialization
    public static final AtomicBoolean slo_rule_arrived = new AtomicBoolean(false);
    public static final SynchronizedBoolean can_modify_slo_rules = new SynchronizedBoolean(false);

    //Debugging variables
    public static CircularFifoQueue<Long> slo_violation_event_recording_queue = new CircularFifoQueue<>(50);
    public static CircularFifoQueue<String> severity_calculation_event_recording_queue = new CircularFifoQueue<>(50);
    private static Properties prop = new Properties();

    public static void main(String[] args) {

        //The input of this program is the type of the SLO violations which are monitored, and the predicted metric values which are evaluated. Its output are events which produce an estimate of the probability of an adaptation.
        //The SLO violations which are monitored need to mention the following data:
        // - The name of the predicted metrics which are monitored as part of the rule
        // - The threshold and whether it is a more-than or less-than threshold
        //The predicted metrics need to include the following data:
        // - The predicted value
        // - The prediction confidence

        //During 'normal' execution, business code starts being executed inside the while loop in the try-catch block, and more specifically, code in the 'else' part of the first *if (first_run && USE_CONFIGURATION_FILE_FOR_METRIC_VALUES_INPUT)* statement, specifically inside the 'else' part, of the second *if (first_run && USE_CONFIGURATION_FILE_FOR_METRIC_TOPICS_INPUT)* statement. This code initializes a subscriber to the topic where rules are expected to be received. This debug class includes also other options for the initial startup of the component.

        try {
            InputStream inputStream = null;
            if (args.length == 0) {
                base_project_path = new File("").toURI();
                URI absolute_configuration_file_path = new File(configuration_file_location).toURI();
                URI relative_configuration_file_path = base_project_path.relativize(absolute_configuration_file_path);
                Logger.getAnonymousLogger().log(info_logging_level,"This is the base project path:"+base_project_path);
                inputStream = new FileInputStream(base_project_path.getPath()+relative_configuration_file_path);
            } else {
                Logger.getAnonymousLogger().log(info_logging_level, "A preferences file has been manually specified");

                if (base_project_path == null || base_project_path.getPath().equals(EMPTY)) {
                    base_project_path = new File(args[0]).toURI();
                }
                inputStream = new FileInputStream(base_project_path.getPath());
            }
            prop.load(inputStream);
            String slo_rules_topic = prop.getProperty("slo_rules_topic");
            kept_values_per_metric = Integer.parseInt(prop.getProperty("stored_values_per_metric","5"));
            self_publish_rule_file = Boolean.parseBoolean(prop.getProperty("self_publish_rule_file"));
            single_slo_rule_active = Boolean.parseBoolean(prop.getProperty("single_slo_rule_active"));
            time_horizon_seconds = Integer.parseInt(prop.getProperty("time_horizon_seconds"));

            slo_violation_probability_threshold = Double.parseDouble(prop.getProperty("slo_violation_probability_threshold"));
            slo_violation_determination_method = prop.getProperty("slo_violation_determination_method");
            maximum_acceptable_forward_predictions = Integer.parseInt(prop.getProperty("maximum_acceptable_forward_predictions"));
            ArrayList<String> unbounded_metric_strings = new ArrayList<String>(Arrays.asList(prop.getProperty("metrics_bounds").split(",")));
            for (String metric_string : unbounded_metric_strings){
                getMonitoring_attributes_bounds_representation().put(metric_string.split(";")[0], metric_string.split(";",2)[1]);
            }

            while (true) {

                if (first_run && USE_CONFIGURATION_FILE_FOR_METRIC_VALUES_INPUT) {

                    String json_file_name = prop.getProperty("input_file");
                    slo_violation_determination_method = prop.getProperty("slo_violation_determination_method");
                    confidence_interval = Double.parseDouble(prop.getProperty("confidence_interval"));
                    prediction_certainty = Double.parseDouble(prop.getProperty("prediction_certainty"));

                    ArrayList<String> metric_names = new ArrayList<>() {{
                        add("cpu");
                        add("ram");
                        add("bandwidth");
                        add("disk");
                    }};
                    HashMap<String, Double> input_data = new HashMap<>();
                    for (String metric_name : metric_names) {

                        Double metric_input_data = MathUtils.get_average(new ArrayList<>(Arrays.stream(prop.getProperty(metric_name).split(",")).map(Double::parseDouble).collect(Collectors.toList())));

                        input_data.put(metric_name, metric_input_data);
                    }


                    RealtimeMonitoringAttribute.initialize_monitoring_attribute_rates_of_change(metric_names);
                    RealtimeMonitoringAttribute.simple_initialize_0_100_bounded_attributes(metric_names);

                    RealtimeMonitoringAttribute.update_monitoring_attributes_values_map(input_data);

                    //Parsing of file
                    String rules_json_string = String.join(EMPTY, Files.readAllLines(Paths.get(new File(json_file_name).getAbsolutePath())));
                    Logger.getAnonymousLogger().log(info_logging_level, rules_json_string);
                    MESSAGE_CONTENTS.assign_value(slo_rules_topic, rules_json_string);
                    slo_rules.add(new SLORule(MESSAGE_CONTENTS.get_synchronized_contents(slo_rules_topic), new ArrayList<>(Arrays.asList(prop.getProperty("metrics_list").split(",")))));

                } else {
                    if (first_run && USE_CONFIGURATION_FILE_FOR_METRIC_TOPICS_INPUT) {
                        synchronized (can_modify_slo_rules) {
                            //do  {
                            //    can_modify_slo_rules.wait();
                            //}while(!can_modify_slo_rules.getValue());
                            can_modify_slo_rules.setValue(false);

                            slo_rules.add(new SLORule(MESSAGE_CONTENTS.get_synchronized_contents(slo_rules_topic), new ArrayList<>(Arrays.asList(prop.getProperty("metrics_list").split(",")))));
                            can_modify_slo_rules.setValue(true);
                            slo_rule_arrived.set(true);
                            can_modify_slo_rules.notifyAll();
                        }
                    } else if (first_run){

                        BiFunction<String, String, String> function = (topic, message) -> {
                            synchronized (can_modify_slo_rules) {
                                can_modify_slo_rules.setValue(true);
                                MESSAGE_CONTENTS.assign_value(topic, message);
                                slo_rule_arrived.set(true);
                                can_modify_slo_rules.notifyAll();

                                Logger.getAnonymousLogger().log(info_logging_level, "BrokerClientApp:  - Received text message: " + message + " at topic " + topic);

                            }
                            return topic + ":MSG:" + message;
                        };

                        BrokerSubscriber subscriber = new BrokerSubscriber(slo_rules_topic, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);
                        new Thread(() -> {
                            while (true) {
                                subscriber.subscribe(function, new AtomicBoolean(false)); //This subscriber should be immune to stop signals
                                Logger.getAnonymousLogger().log(info_logging_level,"Broker unavailable, will try to reconnect after 10 seconds");
                                try {
                                    Thread.sleep(10000);
                                }catch (InterruptedException i){
                                    Logger.getAnonymousLogger().log(info_logging_level,"Sleep was interrupted, will immediately try to connect to the broker");
                                }
                            }
                        }).start();

                        if (self_publish_rule_file) {
                            String json_file_name = prop.getProperty("input_file");
                            String rules_json_string = String.join(EMPTY, Files.readAllLines(Paths.get(new File(json_file_name).getAbsolutePath())));
                            BrokerPublisher publisher = new BrokerPublisher(slo_rules_topic, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);
                            publisher.publish(rules_json_string);
                            Logger.getAnonymousLogger().log(info_logging_level, "Sent message\n" + rules_json_string);
                        }
                    }
                }
                first_run = false;
                synchronized (can_modify_slo_rules) {
                    do  {
                        try {
                            can_modify_slo_rules.wait();
                        }catch (InterruptedException i){
                            i.printStackTrace();
                        }
                    }while((!can_modify_slo_rules.getValue()) || (!slo_rule_arrived.get()));
                    can_modify_slo_rules.setValue(false);
                    slo_rule_arrived.set(false);
                    String rule_representation = MESSAGE_CONTENTS.get_synchronized_contents(slo_rules_topic);
                    if (slo_rule_arrived_has_updated_version(rule_representation)) {
                        if (single_slo_rule_active) {
                            slo_rules.clear();
                        }

                        ArrayList<String> additional_metrics_from_new_slo = get_metric_list_from_JSON_slo(rule_representation);

                        if (additional_metrics_from_new_slo.size() > 0) {
                            slo_rules.add(new SLORule(rule_representation, additional_metrics_from_new_slo));
                        }
                        can_modify_slo_rules.setValue(true);
                        can_modify_slo_rules.notifyAll();
                    }else{
                        can_modify_slo_rules.setValue(true);
                        can_modify_slo_rules.notifyAll();
                        continue;
                    }



                }

                stop_all_running_threads();
                DebugDataSubscription.initiate(prop.getProperty("broker_ip_url"),prop.getProperty("broker_username"), prop.getProperty("broker_password"));
                initialize_monitoring_datastructures_with_empty_data(slo_rules);
                //
                initialize_subrule_and_attribute_associations(slo_rules);
                initialize_attribute_subscribers(slo_rules, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"));
                initialize_slo_processing(slo_rules);

                //while (true) {

                //}
            }
        }catch (Exception e){
            Logger.getAnonymousLogger().log(info_logging_level,"Problem reading input file");
            e.printStackTrace();
        }
    }

    private static boolean slo_rule_arrived_has_updated_version(String rule_representation) {
        JSONObject json_object = null;
        long json_object_version = Integer.MAX_VALUE;
        try {
            json_object = (JSONObject) new JSONParser().parse(rule_representation);
            json_object_version = (Long) json_object.get("version");
        } catch (NullPointerException n){
            n.printStackTrace();
            Logger.getAnonymousLogger().log(info_logging_level,"Unfortunately a null message was sent to the SLO Violation Detector, which is being ignored");
            return false;
        } catch (Exception e){
            e.printStackTrace();
            Logger.getAnonymousLogger().log(info_logging_level,"Could not parse the JSON of the new SLO, assuming it is not an updated rule...");
            return false;
        }
        if (json_object_version > current_slo_rules_version){
            Logger.getAnonymousLogger().log(info_logging_level,"An SLO with updated version ("+json_object_version+" vs older "+current_slo_rules_version+") has arrived");
            current_slo_rules_version=json_object_version;
            return true;
        }else {
            Logger.getAnonymousLogger().log(info_logging_level,"Taking no action for the received SLO message as the version number is not updated");
            return false;
        }
    }

    private static void stop_all_running_threads() {
        Logger.getAnonymousLogger().log(info_logging_level,"Asking previously existing threads to terminate");
        int initial_number_of_running_threads = running_threads.size();
        while (running_threads.size()>0) {
            synchronized (stop_signal) {
                stop_signal.set(true);
                stop_signal.notifyAll();
            }
            try {
                Thread.sleep(3000);
                running_threads.values().forEach(Thread::interrupt);
            }catch (Exception e){
            }
            Logger.getAnonymousLogger().log(info_logging_level,"Stopped "+(initial_number_of_running_threads-running_threads.size())+"/"+initial_number_of_running_threads+" already running threads");
            if (running_threads.size()>1){
                Logger.getAnonymousLogger().log(info_logging_level,"The threads which are still running are the following: "+running_threads);
            }else if (running_threads.size()>0){
                Logger.getAnonymousLogger().log(info_logging_level,"The thread which is still running is the following: "+running_threads);
            }
        }
        Logger.getAnonymousLogger().log(info_logging_level,"All threads have terminated");
        synchronized (stop_signal) {
            stop_signal.set(false);
        }
        synchronized (PREDICTION_EXISTS){
            PREDICTION_EXISTS.setValue(false);
        }
        adaptation_times.clear();
    }

    public static void initialize_subrule_and_attribute_associations(ArrayList<SLORule> slo_rules) {
        synchronized (can_modify_slo_rules) {
            while (!can_modify_slo_rules.getValue()){
                try {
                    can_modify_slo_rules.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            can_modify_slo_rules.setValue(false);
            for (SLORule slo_rule : slo_rules) {
                for (SLOSubRule subrule : SLORule.parse_subrules(slo_rule.getRule_representation(),slo_rule.getRule_format())) {
                    SLOSubRule.getSlo_subrules_per_monitoring_attribute().computeIfAbsent(subrule.getMetric(), k -> new ArrayList<>());
                    SLOSubRule.getSlo_subrules_per_monitoring_attribute().get(subrule.getMetric()).add(subrule);
                }
            }
            can_modify_slo_rules.setValue(true);
            can_modify_slo_rules.notifyAll();
        }
    }


    private static void initialize_monitoring_datastructures_with_empty_data(ArrayList<SLORule> slo_rules){
        for(SLORule slo_rule: slo_rules){
            for (String metric_name : slo_rule.get_monitoring_attributes()) {
                MonitoringAttributeUtilities.initialize_values(metric_name);
            }
        }
    }

    public static ArrayList<String> get_metric_list_from_JSON_slo(String json_object_string) {
        HashSet<String> metric_list = new HashSet<>();
        try {
            JSONObject json_object = (JSONObject) new JSONParser().parse(json_object_string);
            String json_object_id = (String) json_object.get("id");
            String json_object_name = (String) json_object.get("name");
            //Older format uses id-based fields, newer format uses a non-variable structure
            //We first check if an event using the older format is sent, and then check if the event is sent using the newer format
            if (json_object_id!=null) {
                if (json_object_id.split("-").length > 1) {
                    //String composite_rule_type = json_object_id.split("-")[0];
                    JSONArray internal_json_slos = (JSONArray) json_object.get(json_object_id);
                    for (Object o : internal_json_slos) {
                        JSONObject internal_json_slo = (JSONObject) o;
                        metric_list.addAll(get_metric_list_from_JSON_slo(internal_json_slo.toJSONString()));
                    }
                } else {
                    metric_list.add((String) json_object.get("attribute"));
                }
            }
            //If newer format is used
            else if (json_object_name!=null){
                JSONArray internal_json_slos = (JSONArray) json_object.get("constraints");
                if ((internal_json_slos!=null) && (internal_json_slos.size()>0)){
                    for (Object o : internal_json_slos) {
                        JSONObject internal_json_slo = (JSONObject) o;
                        metric_list.addAll(get_metric_list_from_JSON_slo(internal_json_slo.toJSONString()));
                    }
                }else{
                    metric_list.add((String) json_object.get("metric"));
                }
            }else{
                Logger.getAnonymousLogger().log(Level.INFO,"An SLO rule was sent in a format which could not be fully parsed, therefore ignoring this rule. The non-understandable part of the SLO rule is printed below"+"\n"+json_object_string);
            }
        }catch (Exception p){
            p.printStackTrace();
            return new ArrayList<String>();
        }
        return new ArrayList<String>(metric_list);
    }

    private static double get_metric_value_from_JSON(String data_arrived) {
        double result = -1;

        JSONObject json_data_representation = render_valid_json(data_arrived,"=");
        //JSONObject json_data_representation = (JSONObject) new JSONParser().parse(data_arrived);
        result  = Double.parseDouble((String)json_data_representation.get("metricValue"));

        return result;
    }

    /**
     * This method replaces any invalid characters in the json which is received from the broker, and creates a valid JSON object
     * @param data_arrived The data which is received from the broker
     * @param string_to_replace The invalid character which should be substituted by an 'equals' sign
     * @return A JSON object
     */
    private static JSONObject render_valid_json(String data_arrived, String string_to_replace) {
        String valid_json_string = new String(data_arrived);
        JSONObject json_object = new JSONObject();
        valid_json_string = valid_json_string.replaceAll(string_to_replace,":");

        valid_json_string = valid_json_string.replaceAll("[{}]","");

        String [] json_elements = valid_json_string.split(",");
        List <String> json_elements_list = Arrays.stream(json_elements).map(String::trim).collect(Collectors.toList());

        for (String element: json_elements_list) {
            json_object.put(element.split(":")[0],element.split(":")[1]);
        }

        return json_object;
    }


    private static void initialize_global_prediction_attribute_data(){
        Logger.getAnonymousLogger().log(warning_logging_level,"Get global prediction attribute data needs implementation");
    }


    private static PredictionAttributeSet get_prediction_attribute_set(ArrayList<SLORule> rules){
        //usedglobalHashmap: attribute_data,
        return null;
    }

    private static PredictionAttributeSet initialize_with_existing_values(Double cpu_value, Double ram_value) {
        ArrayList<String> metric_names = new ArrayList<>(){{
            add("cpu");
            add("ram");
            add("hard_disk");
        }};
        RealtimeMonitoringAttribute.initialize_monitoring_attribute_rates_of_change(metric_names);
        RealtimeMonitoringAttribute.update_monitoring_attribute_value("cpu",cpu_value);
        RealtimeMonitoringAttribute.update_monitoring_attribute_value("ram",ram_value);

        PredictedMonitoringAttribute cpuPredictionAttribute = new PredictedMonitoringAttribute("cpu", 70,1, 90.0,80,10,System.currentTimeMillis(),System.currentTimeMillis()+10000);
        PredictedMonitoringAttribute ramPredictionAttribute = new PredictedMonitoringAttribute("ram", 50,2, 70.0,80,10,System.currentTimeMillis(),System.currentTimeMillis()+10000);


        PredictionAttributeSet predictionAttributeSet = new PredictionAttributeSet(new ArrayList<>(){{add(cpuPredictionAttribute);add(ramPredictionAttribute);}});

        return predictionAttributeSet;
    }
    private static PredictionAttributeSet pseudo_initialize() throws Exception {
        ArrayList<String> metric_names = new ArrayList<>(){{
            add("cpu");
            add("ram");
            add("hard_disk");
        }};
        RealtimeMonitoringAttribute.initialize_monitoring_attribute_rates_of_change(metric_names);

        //initial cpu values
        ArrayList<Double> cpu_values = new ArrayList<>();
        cpu_values.add(10.0);
        cpu_values.add(20.0);
        cpu_values.add(30.0);
        cpu_values.add(40.0);
        cpu_values.add(50.0);
        cpu_values.add(40.0);
        cpu_values.add(50.0);
        cpu_values.add(50.0);
        cpu_values.add(50.0);
        cpu_values.add(50.0);
        cpu_values.add(50.0);
        cpu_values.add(50.0);
        cpu_values.add(50.0);
        cpu_values.add(50.0);
        cpu_values.add(50.0);

        MonitoringAttributeUtilities.initialize_values("cpu",MathUtils.get_average(cpu_values));

        //initial ram values
        ArrayList<Double> ram_values = new ArrayList<>();
        ram_values.add(20.0);
        ram_values.add(20.0);
        ram_values.add(25.0);
        ram_values.add(45.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(40.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        ram_values.add(30.0);
        MonitoringAttributeUtilities.initialize_values("ram",MathUtils.get_average(ram_values));

        //Get prediction_attribute_sets and calculate method 1 on top of them
        //Get individual prediction_attributes and calculate method 2 on top of them

        PredictedMonitoringAttribute cpuPredictionAttribute = new PredictedMonitoringAttribute("cpu", 70,1, 90.0,80,10,System.currentTimeMillis(),System.currentTimeMillis()+10000);
        PredictedMonitoringAttribute ramPredictionAttribute = new PredictedMonitoringAttribute("ram", 50,2, 70.0,80,10,System.currentTimeMillis(),System.currentTimeMillis()+10000);


        PredictionAttributeSet predictionAttributeSet = new PredictionAttributeSet(new ArrayList<>(){{add(cpuPredictionAttribute);add(ramPredictionAttribute);}});

        return predictionAttributeSet;
    }

    private static ArrayList<AttributeSubscription> initialize_attribute_subscribers(ArrayList<SLORule> rules_list, String broker_ip_address, String broker_username, String broker_password){
        ArrayList<AttributeSubscription> attribute_subscribers = new ArrayList<>();
        for (SLORule rule:rules_list){
            attribute_subscribers.add(new AttributeSubscription(rule,broker_ip_address,broker_username,broker_password));
        }
        return attribute_subscribers;
    }

    public static void initialize_slo_processing(ArrayList<SLORule> rules_list){

        for (SLORule rule:rules_list) {

            Thread severity_calculation_thread = new Thread(() -> {
                BrokerPublisher persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);

                while (!stop_signal.get()) {
                /*try {
                    Thread.sleep(time_horizon_seconds*1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }*/
                    synchronized (PREDICTION_EXISTS) {
                        while (!PREDICTION_EXISTS.getValue()) {
                            try {
                                PREDICTION_EXISTS.wait();
                            } catch (InterruptedException e) {
                                synchronized (stop_signal) {
                                    if (stop_signal.get()) {
                                        running_threads.remove("severity_calculation_thread_" + rule.toString());
                                        PREDICTION_EXISTS.setValue(false);
                                        return;
                                    }
                                }
                                e.printStackTrace();
                            }
                        }

                    }
                    try {
                        Clock clock = Clock.systemUTC();
                        Long current_time = clock.millis();
                        Long targeted_prediction_time;
                        synchronized (ADAPTATION_TIMES_MODIFY) {
                            while (!ADAPTATION_TIMES_MODIFY.getValue()) {
                                try {
                                    ADAPTATION_TIMES_MODIFY.wait();
                                } catch (InterruptedException e) {
                                    Logger.getAnonymousLogger().log(warning_logging_level, "Interrupted while waiting to access the lock for adaptation times object");
                                    e.printStackTrace();
                                }
                            }
                            ADAPTATION_TIMES_MODIFY.setValue(false);
                            clean_data(adaptation_times_to_remove);
                            //targeted_prediction_time = adaptation_times.stream().min(Long::compare).get();
                            targeted_prediction_time = get_next_targeted_prediction_time();
                            ADAPTATION_TIMES_MODIFY.setValue(true);
                            ADAPTATION_TIMES_MODIFY.notifyAll();
                        }
                        if (targeted_prediction_time==null){
                            continue;
                        }
                        Logger.getAnonymousLogger().log(info_logging_level, "Targeted_prediction_time " + targeted_prediction_time);
                        Thread internal_severity_calculation_thread = new Thread(() -> {
                            try {
                            /*
                            synchronized (ADAPTATION_TIMES_MODIFY) {
                                while (!ADAPTATION_TIMES_MODIFY.getValue()) {
                                    ADAPTATION_TIMES_MODIFY.wait();
                                }
                                ADAPTATION_TIMES_MODIFY.setValue(false);
                                adaptation_times.remove(targeted_prediction_time);//remove from the list of timepoints which should be processed. Later this timepoint will be added to the adaptation_times_to_remove HashSet to remove any data associated with it
                                ADAPTATION_TIMES_MODIFY.setValue(true);
                                ADAPTATION_TIMES_MODIFY.notifyAll();
                            }
                            //adaptation_times_pending_processing.add(targeted_prediction_time);

                             */
                                synchronized (PREDICTION_EXISTS) {
                                    PREDICTION_EXISTS.setValue(adaptation_times.size() > 0);
                                }

                                Long sleep_time = targeted_prediction_time * 1000 - time_horizon_seconds * 1000L - current_time;
                                if (sleep_time <= 0) {
                                    Logger.getAnonymousLogger().log(info_logging_level, "Prediction cancelled as targeted prediction time was " + targeted_prediction_time * 1000 + " current time is " + current_time + " and the time_horizon is " + time_horizon_seconds * 1000);
                                    return; //The predictions are too near to the targeted reconfiguration time (or are even obsolete)
                                } else if (sleep_time > current_time + maximum_acceptable_forward_predictions * time_horizon_seconds * 1000L) {
                                    Logger.getAnonymousLogger().log(info_logging_level, "Prediction cancelled as targeted prediction time was " + targeted_prediction_time * 1000 + " and the current time is " + current_time + ". The prediction is more than " + maximum_acceptable_forward_predictions + " time_horizon intervals into the future (the time_horizon is " + time_horizon_seconds * 1000 + " milliseconds)");
                                    return; //The predictions are too near to the targeted reconfiguration tim
                                }
                                Logger.getAnonymousLogger().log(info_logging_level, "Sleeping for " + sleep_time + " milliseconds");
                                sleep(sleep_time);
                                double rule_severity = process_rule_value(rule.getRule_representation(), targeted_prediction_time, rule.getRule_format());
                                double slo_violation_probability = determine_slo_violation_probability(rule_severity);
                                Logger.getAnonymousLogger().log(info_logging_level, "The overall " + slo_violation_determination_method + " severity - calculated from real data - for adaptation time " + targeted_prediction_time + " ( " + (new Date((new Timestamp(targeted_prediction_time * 1000)).getTime())) + " ) is " + rule_severity + " and is calculated " + time_horizon_seconds + " seconds beforehand");
                                Logger.getAnonymousLogger().log(info_logging_level, "The probability of an SLO violation is " + ((int) (slo_violation_probability * 100)) + "%" + (slo_violation_probability< slo_violation_probability_threshold ?" so it will not be published":" and it will be published"));

                                if (slo_violation_probability>= slo_violation_probability_threshold) {
                                    JSONObject severity_json = new JSONObject();
                                    severity_json.put("severity", rule_severity);
                                    severity_json.put("probability", slo_violation_probability);
                                    severity_json.put("predictionTime", targeted_prediction_time);
                                    persistent_publisher.publish(severity_json.toJSONString());
                                }

                                slo_violation_event_recording_queue.add(System.currentTimeMillis());

                                //Probably not necessary to synchronize the line below as each removal will happen only once in a reconfiguration interval, and reconfiguration intervals are assumed to have a duration of minutes.
                                //Necessary to synchronize because another severity calculation thread might invoke clean_data above, and then a concurrent modification exception may arise
                                synchronized (ADAPTATION_TIMES_MODIFY){
                                    while (!ADAPTATION_TIMES_MODIFY.getValue()){
                                        ADAPTATION_TIMES_MODIFY.wait();
                                    }
                                    ADAPTATION_TIMES_MODIFY.setValue(false);
                                    adaptation_times_to_remove.add(targeted_prediction_time); //This line serves a different purpose from the adaptation_times.remove(...) directive above, as the adaptation_times_to_remove HashSet contains timepoints which should be processed to delete their data.
                                    adaptation_times_pending_processing.remove(targeted_prediction_time);
                                    ADAPTATION_TIMES_MODIFY.setValue(true);
                                    ADAPTATION_TIMES_MODIFY.notifyAll();
                                }
                            } catch (InterruptedException i) {
                                Logger.getAnonymousLogger().log(severe_logging_level, "Severity calculation thread for epoch time " + targeted_prediction_time + " interrupted, stopping...");
                                return;
                            }
                        });
                        internal_severity_calculation_thread.setName("internal_severity_calculation_thread_" + targeted_prediction_time);
                        internal_severity_calculation_thread.start();
                    } catch (NoSuchElementException n) {
                        Logger.getAnonymousLogger().log(warning_logging_level, "Could not calculate severity as a value was missing...");
                        continue;
                    }
                }
                running_threads.remove("severity_calculation_thread_"+rule.toString());
            });
            String severity_calculation_thread_name = "severity_calculation_thread_"+rule.toString();
            severity_calculation_thread.setName(severity_calculation_thread_name);
            severity_calculation_thread.start();
            running_threads.put(severity_calculation_thread_name,severity_calculation_thread);
        }/*
    while (true){

    }*/

        //return slo_processors;
    }

    private static Long get_next_targeted_prediction_time() {
        List<Long> possible_targeted_prediction_times = adaptation_times.stream().sorted().limit(maximum_acceptable_forward_predictions).collect(Collectors.toList());
        for (int i=0; i<possible_targeted_prediction_times.size(); i++){
            Long possible_targeted_adaptation_time = possible_targeted_prediction_times.get(i);
            if (!adaptation_times_pending_processing.contains(possible_targeted_adaptation_time)){
                adaptation_times.remove(possible_targeted_adaptation_time);
                Logger.getAnonymousLogger().log(info_logging_level,"Removing targeted prediction time "+possible_targeted_adaptation_time+" as it is going to be used");
                adaptation_times_pending_processing.add(possible_targeted_adaptation_time);
                return possible_targeted_adaptation_time;
            }
        }
        return null;
    }

    private static void clean_data(HashSet<Long> adaptation_times_to_remove) {
        for (Long processed_adaptation_time:adaptation_times_to_remove){
            if (processed_adaptation_time>last_processed_adaptation_time){
                last_processed_adaptation_time = processed_adaptation_time;
            }
            synchronized (can_modify_slo_rules) {
                while (!can_modify_slo_rules.getValue()) {
                    try {
                        can_modify_slo_rules.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                can_modify_slo_rules.setValue(false);
                for (SLORule slo_rule : slo_rules) {
                    for (SLOSubRule subrule : slo_rule.getSlo_subrules()) {
                        if (getPredicted_monitoring_attributes().containsKey(subrule.getId())) {
                            getPredicted_monitoring_attributes().get(subrule.getId()).remove(processed_adaptation_time);
                        }
                    }
                }
                can_modify_slo_rules.setValue(true);
                can_modify_slo_rules.notifyAll();
            }
        }
    }

    /**
     * This function determines the probability of an SLO violation
     * @param rule_severity The severity of the rule which has been determined
     * @return The probability of the rule being violated. The minimum value of this probability is 0, and increases as the severity increases
     */
    public static double determine_slo_violation_probability(double rule_severity) {
        if (slo_violation_determination_method.equals("all-metrics")) {
            //39.64 is the mean severity value when examining all integer severity values for roc x probability x confidence_interval x delta_value in (-100,100)x(0,100)x(0,100)x(-100,100)
        /*
        if (rule_severity >= 40) {
            return Math.min((50 + 50*(rule_severity - 40) / 60)/100,1); // in case we desire the probability to start from 50%
           // return Math.min((100*(rule_severity - 40) / 60)/100,1); // in case we desire the probability to start from 0%
        } else {
            return 0;
        }

         */
            return Math.min(rule_severity/100,100);
        }else if (slo_violation_determination_method.equals("prconf-delta")){
            //Logger.getAnonymousLogger().log(warning_logging_level,"The calculation of probability for the prconf-delta method needs to be implemented");
            //return 0;
            if (rule_severity >= 6.52){
                return Math.min((50+50*(rule_severity-6.52)/93.48)/100,1);
            }else{
                return 0;
            }

        }else{
            Logger.getAnonymousLogger().log(warning_logging_level,"Unknown severity calculation method");
            return 0;
        }
    }

    public static AtomicBoolean getStop_signal() {
        return stop_signal;
    }

}
