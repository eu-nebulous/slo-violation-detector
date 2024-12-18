package slo_violation_detector_engine.detector;

import communication.AbstractFullBrokerSubscriber;
import communication.AttributeSubscription;
import communication.DeploymentSubscriber;
import communication.ReconfigurationEventSubscriber;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import slo_rule_modelling.SLORule;
import slo_rule_modelling.SLOSubRule;
import utilities.DebugDataSubscription;
import utilities.MonitoringAttributeUtilities;
import utility_beans.generic_component_functionality.CharacterizedThread;
import utility_beans.synchronization.SynchronizedBoolean;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_violation_detector_engine.director.DirectorSubcomponent.MESSAGE_CONTENTS;
import static slo_violation_detector_engine.generic.ComponentState.prop;
import static slo_violation_detector_engine.generic.Runnables.get_severity_calculation_runnable;
import static utility_beans.monitoring.PredictedMonitoringAttribute.getPredicted_monitoring_attributes;

public class DetectorSubcomponentUtilities {


    public static void initialize_subrule_and_attribute_associations(ArrayList<SLORule> slo_rules, SynchronizedBoolean can_modify_slo_rules) {
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
                for (SLOSubRule subrule : SLORule.parse_subrules(slo_rule.getAssociated_detector(),slo_rule.getRule_representation(),slo_rule.getRule_format())) {
                    SLOSubRule.getSlo_subrules_per_monitoring_attribute().computeIfAbsent(subrule.getMetric(), k -> new ArrayList<>());
                    SLOSubRule.getSlo_subrules_per_monitoring_attribute().get(subrule.getMetric()).add(subrule);
                }
            }
            can_modify_slo_rules.setValue(true);
            can_modify_slo_rules.notifyAll();
        }
    }


    public static void initialize_monitoring_datastructures_with_empty_data(ArrayList<SLORule> slo_rules){
        for(SLORule slo_rule: slo_rules){
            for (String metric_name : slo_rule.get_monitoring_attributes()) {
                MonitoringAttributeUtilities.initialize_values(metric_name,slo_rule.getAssociated_detector().getSubcomponent_state());
            }
        }
    }

    public static void initialize_slo_processing(ArrayList<SLORule> rules_list){

        for (SLORule rule:rules_list) {
            Logger.getGlobal().log(Level.INFO,"Asking to initialize slo processing for the app "+rule.getAssociated_application_name());
            String severity_calculation_thread_name = "severity_calculation_thread_"+rule.toString();
            CharacterizedThread.create_new_thread(get_severity_calculation_runnable(rule,rule.getAssociated_detector()),severity_calculation_thread_name, true,rule.getAssociated_detector(), CharacterizedThread.CharacterizedThreadType.slo_bound_running_thread);

        }
    }


    public static void clean_data(DetectorSubcomponent detector) {

        for (Long processed_adaptation_time:detector.getSubcomponent_state().adaptation_times_to_remove){
            if (processed_adaptation_time>detector.last_processed_adaptation_time){
                detector.last_processed_adaptation_time = processed_adaptation_time;
            }
            synchronized (detector.can_modify_slo_rules) {
                while (!detector.can_modify_slo_rules.getValue()) {
                    try {
                        detector.can_modify_slo_rules.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                detector.can_modify_slo_rules.setValue(false);
                for (SLORule slo_rule : detector.getSubcomponent_state().slo_rules) {
                    for (SLOSubRule subrule : slo_rule.getSlo_subrules()) {
                        if (getPredicted_monitoring_attributes().containsKey(subrule.getId())) {
                            getPredicted_monitoring_attributes().get(subrule.getId()).remove(processed_adaptation_time);
                        }
                    }
                }
                detector.can_modify_slo_rules.setValue(true);
                detector.can_modify_slo_rules.notifyAll();
            }
        }
    }

    public static Long get_next_targeted_prediction_time(DetectorSubcomponent detector) {
        List<Long> possible_targeted_prediction_times = detector.getSubcomponent_state().adaptation_times.stream().sorted().limit(maximum_acceptable_forward_predictions).toList();
        for (int i=0; i<possible_targeted_prediction_times.size(); i++){
            Long possible_targeted_adaptation_time = possible_targeted_prediction_times.get(i);
            if (!detector.getSubcomponent_state().adaptation_times_pending_processing.contains(possible_targeted_adaptation_time)){
                detector.getSubcomponent_state().adaptation_times.remove(possible_targeted_adaptation_time);
                Logger.getGlobal().log(debug_logging_level,"Removing targeted prediction time "+possible_targeted_adaptation_time+" as it is going to be used");
                detector.getSubcomponent_state().adaptation_times_pending_processing.add(possible_targeted_adaptation_time);
                return possible_targeted_adaptation_time;
            }
        }
        return null;
    }

    public static ArrayList<AbstractFullBrokerSubscriber> initialize_attribute_and_application_deployment_subscribers( DetectorSubcomponent detector, String broker_ip_address, int broker_port, String broker_username, String broker_password){
        ArrayList<SLORule> rules_list = detector.getSubcomponent_state().slo_rules;
        ArrayList<AbstractFullBrokerSubscriber> subscribers = new ArrayList<>();
        for (SLORule rule:rules_list){
            subscribers.add(new AttributeSubscription(rule,broker_ip_address,broker_port,broker_username,broker_password));
        }
        subscribers.add(new DeploymentSubscriber(broker_ip_address,broker_port,broker_username,broker_password,detector));
        subscribers.add(new ReconfigurationEventSubscriber(broker_ip_address,broker_port,broker_username,broker_password,detector));
        return subscribers;
    }


    public static boolean slo_rule_arrived_has_updated_version(String rule_representation,DetectorSubcomponent detector, boolean assume_version_is_always_updated) {
        if (assume_version_is_always_updated){ //This variable can be used to shortcut this method
            return true;
        }
        //TODO: The json object version is ignored for now. However, it should not be, we should keep track separately per application
        JSONObject json_object = null;
        Double json_object_version = 1.0;
        try {
            json_object = (JSONObject) new JSONParser().parse(rule_representation);
            if (json_object.containsKey("version")){
                json_object_version = (Double) json_object.get("version");
            }else{
                Logger.getGlobal().log(info_logging_level,"The rule which was received does not have a version field, and as we do not assume the version of the rule is always updated, it is ignored");
            }
            //json_object_version++;
        } catch (NullPointerException n){
            n.printStackTrace();
            Logger.getGlobal().log(info_logging_level,"Unfortunately a null message was sent to the SLO Violation Detector, which is being ignored");
            return false;
        } catch (Exception e){
            e.printStackTrace();
            Logger.getGlobal().log(info_logging_level,"Could not parse the JSON of the new SLO, assuming it is not an updated rule...");
            return false;
        }
        if (json_object_version > detector.getCurrent_slo_rule_version()){
            Logger.getGlobal().log(info_logging_level,"An SLO with updated version ("+json_object_version+" vs older "+detector.getCurrent_slo_rule_version()+") has arrived for app "+detector.get_application_name());
            Logger.getGlobal().log(info_logging_level,rule_representation);
            detector.setCurrent_slo_rule_version(json_object_version);
            return true;
        }else {
            Logger.getGlobal().log(debug_logging_level,"Taking no action for the received SLO message as the version number is not updated");
            return false;
        }
    }

    public static void stop_all_running_threads(DetectorSubcomponent associated_detector_subcomponent) {
        Logger.getGlobal().log(info_logging_level,"Asking previously existing threads to terminate");
        int initial_number_of_running_threads = associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads.size();
        while (associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads.size()>0) {
            //synchronized (associated_detector_subcomponent.stop_signal) {
              //  associated_detector_subcomponent.stop_signal.set(true);
              //  associated_detector_subcomponent.stop_signal.notifyAll();
            //}
            associated_detector_subcomponent.stop();
            try {
                Thread.sleep(3000);
                for (Thread thread : associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads.values()) {
                    thread.interrupt();
                    Logger.getGlobal().log(info_logging_level,"Interrupted "+thread.getName());
                }
                for (Thread thread : associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads.values()) {
                    thread.join();
                }
            }catch (Exception e){
            }
            Logger.getGlobal().log(info_logging_level,"Stopped "+(initial_number_of_running_threads- associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads.size())+"/"+initial_number_of_running_threads+" already running threads");
            if (associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads.size()>1){
                Logger.getGlobal().log(info_logging_level,"The threads which are still running are the following: "+ associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads);
            }else if (associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads.size()>0){
                Logger.getGlobal().log(info_logging_level,"The thread which is still running is the following: "+ associated_detector_subcomponent.getSubcomponent_state().slo_bound_running_threads);
            }
        }
        Logger.getGlobal().log(info_logging_level,"All threads have terminated");
        //synchronized (associated_detector_subcomponent.stop_signal) {
            //associated_detector_subcomponent.stop_signal.set(false);
        //}
        synchronized (associated_detector_subcomponent.PREDICTION_EXISTS){
            associated_detector_subcomponent.PREDICTION_EXISTS.setValue(false);
        }
        associated_detector_subcomponent.getSubcomponent_state().adaptation_times.clear();
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
                Logger.getGlobal().log(Level.INFO,"An SLO rule was sent in a format which could not be fully parsed, therefore ignoring this rule. The non-understandable part of the SLO rule is printed below"+"\n"+json_object_string);
            }
        }catch (Exception p){
            p.printStackTrace();
            return new ArrayList<String>();
        }
        return new ArrayList<String>(metric_list);
    }

    /**
     * This function determines the probability of an SLO violation
     * @param normalized_rule_severity The severity of the rule which has been determined
     * @return The probability of the rule being violated. The minimum value of this probability is 0, and increases as the severity increases
     */
    public static double determine_slo_violation_probability(double normalized_rule_severity, String severity_calculation_method) {
        if (severity_calculation_method.equals("all-metrics")) {
            //39.64 is the mean severity value when examining all integer severity values for roc x probability x confidence_interval x delta_value in (-100,100)x(0,100)x(0,100)x(-100,100)
            /*
            if (normalized_rule_severity >= 40) {
                return Math.min((50 + 50*(normalized_rule_severity - 40) / 60)/100,1); // in case we desire the probability to start from 50%
               // return Math.min((100*(normalized_rule_severity - 40) / 60)/100,1); // in case we desire the probability to start from 0%
            } else {
                return 0;
            }

             */
            return Math.min(normalized_rule_severity/100,100);
        }else if (severity_calculation_method.equals("prconf-delta")){
            //Logger.getGlobal().log(warning_logging_level,"The calculation of probability for the prconf-delta method needs to be implemented");
            //return 0;
            if (normalized_rule_severity >= 6.52){
                return Math.min((50+50*(normalized_rule_severity-6.52)/93.48)/100,1);
            }else{
                return 0;
            }

        }else{
            Logger.getGlobal().log(warning_logging_level,"Unknown severity calculation method");
            return 0;
        }
    }


    public static void run_slo_violation_detection_engine(DetectorSubcomponent associated_detector_subcomponent)  {
        while (true) {
            boolean slo_rule_updated = false;
            synchronized (associated_detector_subcomponent.can_modify_slo_rules) {
                while((!associated_detector_subcomponent.can_modify_slo_rules.getValue()) || (!associated_detector_subcomponent.slo_rule_arrived.get())){
                    try {
                        associated_detector_subcomponent.can_modify_slo_rules.wait();
                    }catch (InterruptedException i){
                        i.printStackTrace();
                    }
                }
                associated_detector_subcomponent.can_modify_slo_rules.setValue(false);
                associated_detector_subcomponent.slo_rule_arrived.set(false);
                String rule_representation = MESSAGE_CONTENTS.get_synchronized_contents(associated_detector_subcomponent.get_application_name(),slo_rules_topic);
                if (slo_rule_arrived_has_updated_version(rule_representation,associated_detector_subcomponent,assume_slo_rule_version_is_always_updated)) {
                    slo_rule_updated=true;
                    Logger.getGlobal().log(info_logging_level,"Initializing new slo violation detection engine for "+associated_detector_subcomponent.get_name());
                    if (single_slo_rule_active) {
                        associated_detector_subcomponent.getSubcomponent_state().slo_rules.clear();
                    }

                    ArrayList<String> additional_metrics_from_new_slo = get_metric_list_from_JSON_slo(rule_representation);

                    if (additional_metrics_from_new_slo.size() > 0) {
                        associated_detector_subcomponent.getSubcomponent_state().slo_rules.add(new SLORule(rule_representation, additional_metrics_from_new_slo,associated_detector_subcomponent));
                    }
                    associated_detector_subcomponent.can_modify_slo_rules.setValue(true);
                    associated_detector_subcomponent.can_modify_slo_rules.notifyAll();
                }else{
                    slo_rule_updated = false;
                    associated_detector_subcomponent.can_modify_slo_rules.setValue(true);
                    associated_detector_subcomponent.can_modify_slo_rules.notifyAll();
                    continue;
                }
            }
            if (!slo_rule_updated) {
                Logger.getGlobal().log(severe_logging_level,"STOPPING THREADS UNNECESSARILY");
            }
            stop_all_running_threads(associated_detector_subcomponent);
            Logger.getGlobal().log(info_logging_level,"Initializing debug instance for detector component "+associated_detector_subcomponent.get_name());
            DebugDataSubscription.initiate(prop.getProperty("broker_ip_url"),prop.getProperty("broker_username"), prop.getProperty("broker_password"),associated_detector_subcomponent);
            initialize_monitoring_datastructures_with_empty_data(associated_detector_subcomponent.getSubcomponent_state().slo_rules);
            //
            /*associated_detector_subcomponent.getUtilities().*/initialize_subrule_and_attribute_associations(associated_detector_subcomponent.getSubcomponent_state().slo_rules,associated_detector_subcomponent.can_modify_slo_rules);
            initialize_attribute_and_application_deployment_subscribers(associated_detector_subcomponent, prop.getProperty("broker_ip_url"), Integer.parseInt(prop.getProperty("broker_port")), prop.getProperty("broker_username"), prop.getProperty("broker_password"));
            initialize_slo_processing(associated_detector_subcomponent.getSubcomponent_state().slo_rules);

        }
    }
}
