package runtime;

import org.springframework.web.bind.annotation.*;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utility_beans.broker_communication.BrokerSubscriptionDetails;

import java.util.logging.Logger;

import static configuration.Constants.EMPTY;
import static configuration.Constants.info_logging_level;
import static runtime.Main.detectors;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.detector_integer_id;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.detector_subcomponents;
import static utilities.DebugDataSubscription.*;
import static utility_beans.generic_component_functionality.CharacterizedThread.CharacterizedThreadRunMode.detached;
@RestController
@RequestMapping("/api")
public class DetectorRequestMappings {

    @RequestMapping("/add-new-detector/{application_name}")
    public static String start_new_detector_subcomponent(@PathVariable String application_name) {
        Logger.getGlobal().log(info_logging_level,"Creating new SLO Violation Detector subcomponent using the Spring API");
        detectors.put(application_name,new DetectorSubcomponent(application_name,detached));
        return ("Spawned new SLO Detector subcomponent instance! Currently, there have been "+detector_integer_id+" detectors spawned");
    }

    //TODO refine calls to debug_data_generation below, once the interface to AMQP is available
    @GetMapping("/component-statistics")
    public static String get_component_statistics() {
        debug_data_generation.apply(new BrokerSubscriptionDetails(false),EMPTY);
        return "Debug data generation was successful";
    }
    @GetMapping("/component-statistics/detectors/{application_name}")
    public static String get_detector_subcomponent_statistics(@PathVariable String application_name) {
        String detector_name = "detector_"+application_name;
        debug_data_generation.apply(detector_subcomponents.get(detector_name).getBrokerSubscriptionDetails(debug_data_trigger_topic_name),EMPTY);
        return DetectorSubcomponent.get_detector_subcomponent_statistics();
    }
}
