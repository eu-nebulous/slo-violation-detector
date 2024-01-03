package runtime;

import configuration.Constants;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utility_beans.CharacterizedThread;
import utility_beans.RealtimeMonitoringAttribute;

import java.util.HashMap;

import static configuration.Constants.default_application_name;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/api")
public class DirectorRequestMappings {
    @PostMapping(value = "/new-application-slo",
    consumes = APPLICATION_JSON_VALUE)
    public static String start_new_detector_subcomponent(@RequestBody String string_rule_representation){
        JSONObject rule_representation_json;
        JSONParser json_parser = new JSONParser();
        String application_name;
        try {
            rule_representation_json = (JSONObject) json_parser.parse(string_rule_representation);
        } catch (ParseException e) {
            return "Error in parsing the input string, the exception message follows:\n"+e;
        }
        application_name = (String) rule_representation_json.get("name");
        DetectorSubcomponent new_detector = DetectorSubcomponent.detector_subcomponents.getOrDefault(application_name,new DetectorSubcomponent(default_application_name,CharacterizedThread.CharacterizedThreadRunMode.detached));
        new_detector.slo_rule_topic_subscriber_function.apply(Constants.slo_rules_topic,string_rule_representation);
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
            double upper_bound = 100.0,lower_bound = 0.0;
            if (((String) metric_json.get("upper_bound")).toLowerCase().contains("-inf")){
                upper_bound = Double.NEGATIVE_INFINITY;
            }else if (((String) metric_json.get("upper_bound")).toLowerCase().contains("inf")){
                upper_bound = Double.NEGATIVE_INFINITY;
            }
            if (((String) metric_json.get("lower_bound")).toLowerCase().contains("-inf")){
                lower_bound = Double.POSITIVE_INFINITY;
            }
            else if (((String) metric_json.get("lower_bound")).toLowerCase().contains("inf")){
                lower_bound = Double.POSITIVE_INFINITY;
            }
            application_metrics.put(metric_name,new RealtimeMonitoringAttribute(metric_name,lower_bound,upper_bound));
        }

        RealtimeMonitoringAttribute.initialize_monitoring_attributes(new_detector,application_metrics);
        return ("New application was spawned - The monitoring metrics related to the application are the following: "+application_metrics);
    }
}
