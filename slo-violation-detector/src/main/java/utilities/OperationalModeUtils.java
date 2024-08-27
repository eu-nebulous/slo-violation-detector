package utilities;

import utility_beans.generic_component_functionality.OperationalMode;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;

public class OperationalModeUtils{

    public static ArrayList<String> get_director_subscription_topics(){
        return new ArrayList<>
                (List.of(
                        slo_rules_topic,
                        metric_list_topic
                ));
    }

    public static ArrayList<String> get_director_publishing_topics(){
        return new ArrayList<>(
                List.of(
                        topic_for_severity_announcement,
                        topic_for_lost_device_announcement
                ));
    }
    public static OperationalMode getSLOViolationDetectionOperationalMode(String operational_mode) {
        if (operational_mode.equalsIgnoreCase("DIRECTOR")){
            return OperationalMode.DIRECTOR;
        }else if (operational_mode.equalsIgnoreCase("DETECTOR")){
            return OperationalMode.DETECTOR;
        }
        else{
            Logger.getGlobal().log(Level.SEVERE,"Creating new SLO Violation Detection instance as a DETECTOR node, however the specification of the type of node whould be DIRECTOR or DETECTOR, not "+operational_mode);
            return OperationalMode.DIRECTOR;
        }
    }
}