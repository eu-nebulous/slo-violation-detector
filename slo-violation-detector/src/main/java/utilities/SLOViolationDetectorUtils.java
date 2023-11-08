package utilities;

import metric_retrieval.AttributeSubscription;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import slo_processing.SLORule;
import slo_processing.SLOSubRule;
import utility_beans.CharacterizedThread;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static configuration.Constants.*;
import static processing_logic.Runnables.get_severity_calculation_runnable;
import static runtime.Main.*;
import static utility_beans.CharacterizedThread.CharacterizedThreadType.slo_bound_running_thread;
import static utilities.SLOViolationDetectorStateUtils.*;
import static utility_beans.PredictedMonitoringAttribute.getPredicted_monitoring_attributes;

public class SLOViolationDetectorUtils {

    public static HashSet<Long> adaptation_times_to_remove = new HashSet<>();


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


    public static void initialize_monitoring_datastructures_with_empty_data(ArrayList<SLORule> slo_rules){
        for(SLORule slo_rule: slo_rules){
            for (String metric_name : slo_rule.get_monitoring_attributes()) {
                MonitoringAttributeUtilities.initialize_values(metric_name);
            }
        }
    }

    public static void initialize_slo_processing(ArrayList<SLORule> rules_list){

        for (SLORule rule:rules_list) {

            String severity_calculation_thread_name = "severity_calculation_thread_"+rule.toString();
            CharacterizedThread.create_new_thread(get_severity_calculation_runnable(rule),severity_calculation_thread_name, slo_bound_running_thread,true);

        }
    }


    public static void clean_data(HashSet<Long> adaptation_times_to_remove) {
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

    public static Long get_next_targeted_prediction_time() {
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

    public static ArrayList<AttributeSubscription> initialize_attribute_subscribers(ArrayList<SLORule> rules_list, String broker_ip_address, String broker_username, String broker_password){
        ArrayList<AttributeSubscription> attribute_subscribers = new ArrayList<>();
        for (SLORule rule:rules_list){
            attribute_subscribers.add(new AttributeSubscription(rule,broker_ip_address,broker_username,broker_password));
        }
        return attribute_subscribers;
    }

    public static AtomicBoolean getStop_signal() {
        return stop_signal;
    }

    public static boolean slo_rule_arrived_has_updated_version(String rule_representation) {
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

    public static void stop_all_running_threads() {
        Logger.getAnonymousLogger().log(info_logging_level,"Asking previously existing threads to terminate");
        int initial_number_of_running_threads = slo_bound_running_threads.size();
        while (slo_bound_running_threads.size()>0) {
            synchronized (stop_signal) {
                stop_signal.set(true);
                stop_signal.notifyAll();
            }
            try {
                Thread.sleep(3000);
                slo_bound_running_threads.values().forEach(Thread::interrupt);
            }catch (Exception e){
            }
            Logger.getAnonymousLogger().log(info_logging_level,"Stopped "+(initial_number_of_running_threads- slo_bound_running_threads.size())+"/"+initial_number_of_running_threads+" already running threads");
            if (slo_bound_running_threads.size()>1){
                Logger.getAnonymousLogger().log(info_logging_level,"The threads which are still running are the following: "+ slo_bound_running_threads);
            }else if (slo_bound_running_threads.size()>0){
                Logger.getAnonymousLogger().log(info_logging_level,"The thread which is still running is the following: "+ slo_bound_running_threads);
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

}
