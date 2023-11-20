package utilities;

//import eu.melodic.event.brokerclient.BrokerPublisher;
//import eu.melodic.event.brokerclient.BrokerSubscriber;
import processing_logic.Runnables;
import slo_violation_detector_engine.DetectorSubcomponent;
import slo_violation_detector_engine.DetectorSubcomponentUtilities;
import utility_beans.BrokerSubscriber;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_rule_modelling.SLOSubRule;
import utility_beans.BrokerPublisher;
import utility_beans.CharacterizedThread;
import utility_beans.RealtimeMonitoringAttribute;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static configuration.Constants.amq_library_configuration_location;
import static configuration.Constants.info_logging_level;
import static slo_violation_detector_engine.DetectorSubcomponent.detector_subcomponents;
import static slo_violation_detector_engine.DirectorSubcomponent.director_subcomponents;
import static slo_violation_detector_engine.SLOViolationDetectorStateUtils.*;
import static utility_beans.CharacterizedThread.CharacterizedThreadType.slo_bound_running_thread;
import static utility_beans.RealtimeMonitoringAttribute.get_metric_value;

/**
 * The objective of this class is to allow a structured synopsis of the current state of the SLO Violation Detector to be created, as a response to a request sent to it through an appropriate topic.
 */
public class DebugDataSubscription {

    public static String debug_data_trigger_topic_name = "sloviolationdetector.debug";
    public static String debug_data_output_topic_name = "sloviolationdetector.debug_output";
    private static String broker_username,broker_password,broker_ip_address;
    public static BiFunction <String,String,String> debug_data_generation = (topic, message) ->{

        String output_debug_data = "";
        StringBuilder intermediate_debug_string = new StringBuilder();
        intermediate_debug_string = new StringBuilder(intermediate_debug_string + "The following threads are currently running:" + "\n\n");

        boolean flag_first_element_is_to_be_iterated = true;
        intermediate_debug_string.append("Persistent running director threads:\n");
        intermediate_debug_string.append("--------------------------:\n\t- ");
        for (String director_id : director_subcomponents.keySet()) {
            intermediate_debug_string.append("Details for threads belonging to director "+director_id+":\n");
            for (String persistent_director_thread_name : director_subcomponents.get(director_id).persistent_running_director_threads.keySet()){
                if (flag_first_element_is_to_be_iterated) {
                    intermediate_debug_string.append(persistent_director_thread_name);
                    flag_first_element_is_to_be_iterated = false;
                }else{
                    intermediate_debug_string.append(",\n\t- ").append(persistent_director_thread_name);
                }
            }
        }
        intermediate_debug_string.append("\n\n");
        flag_first_element_is_to_be_iterated = true;

        intermediate_debug_string.append("Persistent running detector threads:\n");
        intermediate_debug_string.append("--------------------------:\n\t- ");
        for (String detector_id : detector_subcomponents.keySet()) {
            intermediate_debug_string.append("Details for persistent threads belonging to detector ").append(detector_id).append(":\n");
            for (String s : detector_subcomponents.get(detector_id).getSubcomponent_state().persistent_running_detector_threads.keySet()) {
                if (flag_first_element_is_to_be_iterated) {
                    intermediate_debug_string.append(s);
                    flag_first_element_is_to_be_iterated = false;
                } else {
                    intermediate_debug_string.append(",\n\t- ").append(s);
                }
            }
        }
        intermediate_debug_string.append("\n\n");
        flag_first_element_is_to_be_iterated = true;
        intermediate_debug_string.append("SLO-bound running threads:\n");
        intermediate_debug_string.append("-------------------------:\n");
        for (String detector_id : detector_subcomponents.keySet()) {
            intermediate_debug_string.append("Details for slo-bound threads belonging to detector ").append(detector_id).append(":\n");
            for (String s : detector_subcomponents.get(detector_id).getSubcomponent_state().slo_bound_running_threads.keySet()) {
                if (flag_first_element_is_to_be_iterated) {
                    intermediate_debug_string.append(s);
                    flag_first_element_is_to_be_iterated = false;
                } else {
                    intermediate_debug_string.append(",\n\t- ").append(s);
                }
            }
        }
        intermediate_debug_string.append("\n\n");
        output_debug_data = output_debug_data+intermediate_debug_string;
        intermediate_debug_string = new StringBuilder();

        flag_first_element_is_to_be_iterated = true;
        intermediate_debug_string.append("The following Monitoring Attribute values are currently stored:\n");
        for (String detector_id : detector_subcomponents.keySet()) {
            DetectorSubcomponent detector = detector_subcomponents.get(detector_id);
            for (Map.Entry<String, RealtimeMonitoringAttribute> entry : detector.getSubcomponent_state().getMonitoring_attributes().entrySet()) {
                if (flag_first_element_is_to_be_iterated) {
                    intermediate_debug_string.append("\n\t- Metric name: ").append(entry.getKey());
                    flag_first_element_is_to_be_iterated = false;
                } else {
                    intermediate_debug_string.append("\t- Metric name: ").append(entry.getKey());
                }

                Double metric_value = get_metric_value(detector,entry.getKey());
                CircularFifoQueue<Double> metric_values = entry.getValue().getActual_metric_values();
                if (metric_value.isNaN()) {
                    intermediate_debug_string.append(" - value was determined as NaN, individual collected values are ").append(metric_values).append("\n");
                } else if (metric_value.isInfinite()) {
                    intermediate_debug_string.append(" - value was determined as infinite, individual collected values are ").append(metric_values).append("\n");
                } else {
                    intermediate_debug_string.append(" - value from individual values").append(metric_values).append(" is ").append(metric_value).append("\n");
                }

            }
        }
        intermediate_debug_string.append("\n");
        output_debug_data = output_debug_data+intermediate_debug_string;
        intermediate_debug_string = new StringBuilder();

        intermediate_debug_string.append("The following subrules have been parsed and are stored:\n");
        intermediate_debug_string.append("------------------------------------------------------:\n");
        for (Map.Entry<String, ArrayList<SLOSubRule>> entry : SLOSubRule.getSlo_subrules_per_monitoring_attribute().entrySet()){
            intermediate_debug_string.append("- Metric name: ").append(entry.getKey());
            for (SLOSubRule rule : entry.getValue()) {
                intermediate_debug_string.append("\n").append(rule.toString());
            }
            intermediate_debug_string.append("\n");
        }
        intermediate_debug_string = new StringBuilder();

        for (String detector_id : detector_subcomponents.keySet()) {
            intermediate_debug_string.append("Details for threads belonging to detector ").append(detector_id).append(":\n");
            intermediate_debug_string.append("\nShowing the adaptation times that pend processing:\n").append(detector_subcomponents.get(detector_id).getSubcomponent_state().adaptation_times_pending_processing);
            intermediate_debug_string.append("\nThese are the timestamps of the latest adaptation events\n").append(detector_subcomponents.get(detector_id).getSubcomponent_state().slo_violation_event_recording_queue);
        }
        output_debug_data = output_debug_data+intermediate_debug_string;

        Logger.getGlobal().log(info_logging_level,"Debug data generated:\n"+output_debug_data);
        BrokerPublisher publisher = new BrokerPublisher(debug_data_output_topic_name, broker_ip_address, broker_username, broker_password, amq_library_configuration_location);
        publisher.publish(output_debug_data);
        return output_debug_data;
    };

    public static BrokerSubscriber debug_data_subscriber = new BrokerSubscriber(debug_data_trigger_topic_name, broker_ip_address, broker_username, broker_password, amq_library_configuration_location);

    public static void initiate(String broker_ip_address, String broker_username, String broker_password, DetectorSubcomponent detector) {
        CharacterizedThread.create_new_thread(new Runnables.DebugDataRunnable(detector),"debug_data_subscription_thread_" + debug_data_trigger_topic_name,true,detector);
    }
}
