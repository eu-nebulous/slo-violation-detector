package runtime;

import org.springframework.web.bind.annotation.*;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utility_beans.BrokerSubscriptionDetails;

import static configuration.Constants.EMPTY;
import static configuration.Constants.default_application_name;
import static runtime.Main.detectors;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.detector_integer_id;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.detector_subcomponents;
import static utilities.DebugDataSubscription.*;
import static utility_beans.CharacterizedThread.CharacterizedThreadRunMode.detached;
@RestController
@RequestMapping("/api")
public class DetectorRequestMappings {

    @RequestMapping("/add-new-detector")
    public static String start_new_detector_subcomponent() {
        detectors.put(default_application_name,new DetectorSubcomponent(default_application_name,detached));
        return ("Spawned new SLO Detector subcomponent instance! Currently, there have been "+detector_integer_id+" detectors spawned");
    }

    //TODO refine calls to debug_data_generation below, once the interface to AMQP is available
    @GetMapping("/component-statistics")
    public static String get_component_statistics() {
        debug_data_generation.apply(new BrokerSubscriptionDetails(false),EMPTY);
        return "Debug data generation was successful";
    }
    @GetMapping("/component-statistics/detectors/{id}")
    public static String get_detector_subcomponent_statistics(@PathVariable String id) {
        String detector_name = "detector_"+id;
        debug_data_generation.apply(detector_subcomponents.get(detector_name).getBrokerSubscriptionDetails(debug_data_trigger_topic_name),EMPTY);
        return DetectorSubcomponent.get_detector_subcomponent_statistics();
    }
}
