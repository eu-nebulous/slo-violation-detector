package utilities;

import eu.melodic.event.brokerclient.BrokerPublisher;
import eu.melodic.event.brokerclient.BrokerSubscriber;
import org.apache.commons.collections4.queue.CircularFifoQueue;
import runtime.Main;
import slo_processing.SLOSubRule;
import utility_beans.CharacterizedThread;
import utility_beans.RealtimeMonitoringAttribute;

import java.util.ArrayList;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static configuration.Constants.amq_library_configuration_location;
import static configuration.Constants.info_logging_level;
import static runtime.Main.*;
import static utilities.SLOViolationDetectorStateUtils.*;
import static utility_beans.CharacterizedThread.CharacterizedThreadType.slo_bound_running_thread;

/**
 * The objective of this class is to allow a structured synopsis of the current state of the SLO Violation Detector to be created, as a response to a request sent to it through an appropriate topic.
 */
public class DebugDataSubscription {

    private static String debug_data_trigger_topic_name = "sloviolationdetector.debug";
    private static String debug_data_output_topic_name = "sloviolationdetector.debug_output";
    private static String broker_username,broker_password,broker_ip_address;
    static BiFunction <String,String,String> debug_data_generation = (topic, message) ->{

        String output_debug_data = "";
        StringBuilder intermediate_debug_string = new StringBuilder();
        intermediate_debug_string = new StringBuilder(intermediate_debug_string + "The following threads are currently running" + "\n");

        boolean flag_first_element_iterated = true;
        intermediate_debug_string.append("Persistent running threads:\n");
        for (String s : persistent_running_threads.keySet()){
            if (flag_first_element_iterated) {
                intermediate_debug_string.append(s);
                flag_first_element_iterated = false;
            }else{
                intermediate_debug_string.append(",\n").append(s);
            }
        }
        flag_first_element_iterated = true;
        intermediate_debug_string.append("SLO-bound running threads:\n");
        for (String s : slo_bound_running_threads.keySet()){
            if (flag_first_element_iterated) {
                intermediate_debug_string.append(s);
                flag_first_element_iterated = false;
            }else{
                intermediate_debug_string.append(",\n").append(s);
            }
        }
        intermediate_debug_string.append("\n");
        output_debug_data = output_debug_data+intermediate_debug_string;
        intermediate_debug_string = new StringBuilder();

        flag_first_element_iterated = true;
        intermediate_debug_string.append("The following Monitoring Attribute values are currently stored:\n");
        for ( Map.Entry<String, RealtimeMonitoringAttribute> entry :RealtimeMonitoringAttribute.getMonitoring_attributes().entrySet() ){
            if (flag_first_element_iterated){
                intermediate_debug_string.append("\n-  Metric name: ").append(entry.getKey());
                flag_first_element_iterated = false;
            }else{
                intermediate_debug_string.append("-  Metric name: ").append(entry.getKey());
            }

            Double metric_value = RealtimeMonitoringAttribute.get_metric_value(entry.getKey());
            CircularFifoQueue<Double> metric_values = entry.getValue().getActual_metric_values();
            if (metric_value.isNaN()){
                intermediate_debug_string.append(" - value was determined as NaN, individual collected values are ").append(metric_values).append("\n");
            }
            else if (metric_value.isInfinite()){
                intermediate_debug_string.append(" - value was determined as infinite, individual collected values are ").append(metric_values).append("\n");
            } else {
                intermediate_debug_string.append(" - value from individual values").append(metric_values).append(" is ").append(metric_value).append("\n");
            }

        }
        output_debug_data = output_debug_data+intermediate_debug_string;
        intermediate_debug_string = new StringBuilder();

        intermediate_debug_string.append("The following subrules have been parsed and are stored:\n");
        for (Map.Entry<String, ArrayList<SLOSubRule>> entry : SLOSubRule.getSlo_subrules_per_monitoring_attribute().entrySet()){
            intermediate_debug_string.append("Metric name: ").append(entry.getKey());
            for (SLOSubRule rule : entry.getValue()) {
                intermediate_debug_string.append("\n").append(rule.toString());
            }
            intermediate_debug_string.append("\n");
        }
        output_debug_data = output_debug_data+intermediate_debug_string;
        intermediate_debug_string = new StringBuilder();

        output_debug_data = output_debug_data+"\nShowing the adaptation times that pend processing:\n"+ adaptation_times_pending_processing;
        intermediate_debug_string.append("\nThese are the timestamps of the latest adaptation events\n").append(slo_violation_event_recording_queue);

        Logger.getGlobal().log(info_logging_level,"Debug data generated:\n"+output_debug_data);
        BrokerPublisher publisher = new BrokerPublisher(debug_data_output_topic_name, broker_ip_address, broker_username, broker_password, amq_library_configuration_location);
        publisher.publish(output_debug_data);
        return output_debug_data;
    };
    public static void initiate(String broker_ip_address, String broker_username, String broker_password) {
        BrokerSubscriber debug_data_subscriber = new BrokerSubscriber(debug_data_trigger_topic_name, broker_ip_address, broker_username, broker_password, amq_library_configuration_location);
        Runnable debug_data_subscription_runnable = () -> {
            try {
                synchronized (HAS_MESSAGE_ARRIVED.get_synchronized_boolean(debug_data_trigger_topic_name)) {
                    //if (Main.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(debug_data_topic_name).getValue())
                    debug_data_subscriber.subscribe(debug_data_generation, stop_signal);
                }
                if (Thread.interrupted()) {
                    throw new InterruptedException();
                }
            } catch (Exception i) {
                Logger.getAnonymousLogger().log(info_logging_level, "Possible interruption of debug data subscriber thread for " + debug_data_trigger_topic_name + " - if not stacktrace follows");
                if (!(i instanceof InterruptedException)) {
                    i.printStackTrace();
                }
            } finally {
                Logger.getAnonymousLogger().log(info_logging_level, "Removing debug data subscriber thread for " + debug_data_trigger_topic_name);
                slo_bound_running_threads.remove("debug_data_subscription_thread_" + debug_data_trigger_topic_name);
            }
        };
        CharacterizedThread.create_new_thread(debug_data_subscription_runnable,"debug_data_subscription_thread_" + debug_data_trigger_topic_name,slo_bound_running_thread,true);
    }
}
