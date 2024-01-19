package runtime;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import slo_violation_detector_engine.director.DirectorSubcomponent;
import utility_beans.broker_communication.BrokerSubscriptionDetails;
import utility_beans.generic_component_functionality.CharacterizedThread;
import utility_beans.monitoring.RealtimeMonitoringAttribute;

import java.util.HashMap;

import static configuration.Constants.*;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static slo_violation_detector_engine.generic.ComponentState.*;


@RestController
@RequestMapping("/api")
public class DirectorRequestMappings {
    @PostMapping(value = "/new-application-slo",
    consumes = APPLICATION_JSON_VALUE)
    public static String start_new_detector_subcomponent(@RequestBody String string_rule_representation){
/*        JSONObject rule_representation_json;
        JSONParser json_parser = new JSONParser();
        String application_name;
        try {
            rule_representation_json = (JSONObject) json_parser.parse(string_rule_representation);
            application_name = (String) rule_representation_json.get("name");
        } catch (ParseException e) {
            return "Error in parsing the input string, the exception message follows:\n"+e;
        }*/
        BrokerSubscriptionDetails broker_details = new BrokerSubscriptionDetails(broker_ip,broker_username,broker_password,EMPTY,slo_rules_topic);
        DirectorSubcomponent.slo_rule_topic_subscriber_function.apply(broker_details,string_rule_representation);
        return ("New application was spawned");
    }

    @PostMapping(value = "/new-application-metric-list",
            consumes = APPLICATION_JSON_VALUE)
    public static String parse_metric_list(@RequestBody String metric_list){
        JSONObject metric_list_json;
        JSONArray metrics_json_array;
        JSONParser json_parser = new JSONParser();
        String application_name;
        try {
             metric_list_json = (JSONObject) json_parser.parse(metric_list);
        } catch (ParseException e) {
            return "Error in parsing the input string, the exception message follows:\n"+e;
        }
        application_name = (String) metric_list_json.get("name");
        DetectorSubcomponent new_detector = DetectorSubcomponent.detector_subcomponents.getOrDefault(application_name,new DetectorSubcomponent(application_name,CharacterizedThread.CharacterizedThreadRunMode.detached));

        HashMap<String,RealtimeMonitoringAttribute> application_metrics = new_detector.getSubcomponent_state().getMonitoring_attributes();
        metrics_json_array = (JSONArray) metric_list_json.get("metric_list");
        for (Object metric : metrics_json_array){
            JSONObject metric_json = (JSONObject) metric;
            String metric_name = (String) metric_json.get("name");

            RealtimeMonitoringAttribute.AttributeValuesType lower_bound_type,upper_bound_type;

            double upper_bound = 100.0,lower_bound = 0.0;
            if (((String) metric_json.get("upper_bound")).toLowerCase().contains("-inf") || ((String) metric_json.get("upper_bound")).toLowerCase().contains("-infinity")){
                upper_bound = Double.NEGATIVE_INFINITY;
            }else if (((String) metric_json.get("upper_bound")).toLowerCase().contains("inf") || ((String) metric_json.get("upper_bound")).toLowerCase().contains("infinity")){
                upper_bound = Double.NEGATIVE_INFINITY;
            }else{
                String upper_bound_str = (String) metric_json.get("upper_bound");
                try {
                    upper_bound = Integer.parseInt(upper_bound_str);
                    upper_bound_type = RealtimeMonitoringAttribute.AttributeValuesType.Integer;
                }catch (Exception e){
                    try{
                        upper_bound = Double.parseDouble(upper_bound_str);
                        upper_bound_type = RealtimeMonitoringAttribute.AttributeValuesType.Double;
                    }catch (Exception z){
                        e.printStackTrace();
                        z.printStackTrace();
                    }
                }
            }


            if (((String) metric_json.get("lower_bound")).toLowerCase().contains("-inf") || ((String) metric_json.get("lower_bound")).toLowerCase().contains("-infinity")){
                lower_bound = Double.POSITIVE_INFINITY;
            }
            else if (((String) metric_json.get("lower_bound")).toLowerCase().contains("inf") || ((String) metric_json.get("lower_bound")).toLowerCase().contains("infinity")){
                lower_bound = Double.POSITIVE_INFINITY;
            }else {
                application_metrics.put(metric_name, new RealtimeMonitoringAttribute(metric_name, lower_bound, upper_bound, RealtimeMonitoringAttribute.AttributeValuesType.Double));
            }
        }

        RealtimeMonitoringAttribute.initialize_monitoring_attributes(new_detector,application_metrics);
        return ("New application was spawned - The monitoring metrics related to the application are the following: "+application_metrics);
    }
}
