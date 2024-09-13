package communication;

import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utility_beans.broker_communication.BrokerSubscriber;import utility_beans.broker_communication.BrokerSubscriptionDetails;import utility_beans.generic_component_functionality.CharacterizedThread;import java.util.function.BiFunction;import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_violation_detector_engine.detector.DetectorSubcomponentState.reconfigurationTimeRecordingQueueLock;

public class DeploymentSubscriber  extends AbstractFullBrokerSubscriber{
    public DeploymentSubscriber(String broker_ip_address, int broker_port, String broker_username, String broker_password, DetectorSubcomponent detector){
		BrokerSubscriber subscriber = new BrokerSubscriber("eu.nebulouscloud.ui.dsl.generic", broker_ip_address,broker_port,broker_username,broker_password, amq_library_configuration_location,detector.get_application_name());
                    BiFunction<BrokerSubscriptionDetails,String,Long> function = (broker_details, message) ->{
                        Long reconfiguration_timestamp = 0L;
                        synchronized (reconfigurationTimeRecordingQueueLock) {
                            try {
                                reconfiguration_timestamp = System.currentTimeMillis();
                                detector.getSubcomponent_state().getReconfiguration_time_recording_queue().add(reconfiguration_timestamp);
                            } catch (Exception e){
                                e.printStackTrace();
                                Logger.getGlobal().log(severe_logging_level,"An unknown exception was caught while parsing message: "+message);
                            }
                        }
                        return reconfiguration_timestamp;
                    };
		Runnable realtime_subscription_runnable = () -> {
             try {
                 Logger.getGlobal().log(info_logging_level,"Starting subscription to new application deployments");
                 subscriber.subscribe(function, detector.get_application_name(),detector.stop_signal);
                            if(Thread.interrupted()){
                                throw new InterruptedException();
                            }
                        }catch (Exception i){
                            Logger.getGlobal().log(info_logging_level,"Possible interruption of new application deployment subscriber thread for eu.nebulouscloud.ui.dsl.generic - if not stacktrace follows");
                            if (! (i instanceof InterruptedException)){
                                i.printStackTrace();
                            }
                        }finally{
                            Logger.getGlobal().log(info_logging_level,"Removing application deployment subscriber thread");
                            detector.getSubcomponent_state().persistent_running_detector_threads.remove("application_deployment_subscriber_thread");
                        }
                    };
                    CharacterizedThread.create_new_thread(realtime_subscription_runnable,"application_deployment_subscriber_thread",true,detector, CharacterizedThread.CharacterizedThreadType.persistent_running_detector_thread);
	}
}
