package communication;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utility_beans.broker_communication.BrokerSubscriber;
import utility_beans.broker_communication.BrokerSubscriptionDetails;
import utility_beans.generic_component_functionality.CharacterizedThread;

import java.util.function.BiFunction;
import java.util.logging.Logger;

import static configuration.Constants.amq_library_configuration_location;
import static configuration.Constants.info_logging_level;

public class ReconfigurationEventSubscriber extends AbstractFullBrokerSubscriber{
    private String reconfiguration_events_topic = "eu.nebulouscloud.optimiser.controller.app_state";
    private enum application_state {UNDEFINED,STOPPED,NEW,DEPLOYING,RUNNING,FAILED,DELETED}
    private application_state current_state;
    private boolean ongoing_reconfiguration = false;

    public ReconfigurationEventSubscriber(String broker_ip_address, int broker_port, String broker_username, String broker_password, DetectorSubcomponent detector) {
        
        BrokerSubscriber subscriber = new BrokerSubscriber(reconfiguration_events_topic, broker_ip_address,broker_port,broker_username,broker_password, amq_library_configuration_location, detector.get_application_name());
        
        BiFunction<BrokerSubscriptionDetails,String,String> function = (broker_details, message) ->{
            JSONParser parser = new JSONParser();
            try {
                JSONObject message_object = (JSONObject) parser.parse(message);
                application_state state_received = application_state.valueOf(message_object.get("state").toString());
               Logger.getGlobal().log(info_logging_level,"Received application state message "+ state_received+" for application "+detector.get_application_name());
                if (state_received.equals(application_state.DEPLOYING) && current_state.equals(application_state.RUNNING)){
                    detector.getSubcomponent_state().setLast_optimizer_adaptation_initiation_timestamp(System.currentTimeMillis());
                    ongoing_reconfiguration=true;
                }else if (state_received.equals(application_state.RUNNING) && ongoing_reconfiguration){
                    ongoing_reconfiguration=false;
                    //slo_violation.setTimepoint_of_implementation_of_reconfiguration(System.currentTimeMillis());
                    //detector.getSubcomponent_state().getReconfiguration_time_recording_queue().add(System.currentTimeMillis());
                    Logger.getGlobal().log(info_logging_level,"Reconfiguration action is now complete as signalled by the optimizer");
                    Logger.getGlobal().log(info_logging_level, "The total reconfiguration time from slo "+detector.getSubcomponent_state().getLast_slo_violation_triggering_optimizer()+" was "+detector.getSubcomponent_state().calculate_last_total_reconfiguration_time_from_last_slo());
                }
                current_state = state_received;
                //state processing here
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }


            return current_state.toString();
        };
        Runnable reconfiguration_topic_runnable = () -> {
            try {
                subscriber.subscribe(function, detector.get_application_name(), detector.stop_signal);
            } catch (Exception e) {
                
            }finally{
                Logger.getGlobal().log(info_logging_level,"Removing reconfiguration topic subscription thread for "+reconfiguration_events_topic);
                detector.getSubcomponent_state().slo_bound_running_threads.remove("reconfiguration_topic_" + reconfiguration_events_topic);
            }
        };

        CharacterizedThread.create_new_thread(reconfiguration_topic_runnable,"reconfiguration_topic_"+reconfiguration_events_topic,true,detector, CharacterizedThread.CharacterizedThreadType.persistent_running_detector_thread);
        
    }
}
