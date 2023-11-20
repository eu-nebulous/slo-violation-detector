package runtime;

import configuration.Constants;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import slo_violation_detector_engine.DetectorSubcomponent;
import utility_beans.CharacterizedThread;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

@RestController
@RequestMapping("/api")
public class DirectorRequestMappings {
    @PostMapping(value = "/new-application",
    consumes = APPLICATION_JSON_VALUE)
    public static String start_new_detector_subcomponent(@RequestBody String string_rule_representation){
        DetectorSubcomponent new_detector = new DetectorSubcomponent(CharacterizedThread.CharacterizedThreadRunMode.detached);
        new_detector.slo_rule_topic_subscriber_function.apply(Constants.slo_rules_topic,string_rule_representation);
        return ("New application was spawned");
    }
}
