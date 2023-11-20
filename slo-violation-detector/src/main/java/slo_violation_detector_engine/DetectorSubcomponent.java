package slo_violation_detector_engine;

import org.json.simple.JSONObject;
import processing_logic.Runnables;
import utility_beans.*;


import java.time.Clock;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_violation_detector_engine.SLOViolationDetectorStateUtils.*;
import static utility_beans.CharacterizedThread.CharacterizedThreadRunMode.attached;


public class DetectorSubcomponent extends SLOViolationDetectorSubcomponent{
    public static final SynchronizedInteger detector_integer_id = new SynchronizedInteger();
    public static HashMap<String,DetectorSubcomponent> detector_subcomponents = new HashMap<>(); //A HashMap containing all detector subcomponents
    private DetectorSubcomponentUtilities utilities;

    private DetectorSubcomponentState subcomponent_state;
    public final AtomicBoolean stop_signal = new AtomicBoolean(false);
    public final SynchronizedBoolean can_modify_slo_rules = new SynchronizedBoolean(false);
    public SynchronizedBooleanMap HAS_MESSAGE_ARRIVED = new SynchronizedBooleanMap();


    public final SynchronizedBoolean PREDICTION_EXISTS = new SynchronizedBoolean(false);
    public final SynchronizedBoolean ADAPTATION_TIMES_MODIFY = new SynchronizedBoolean(true);
    public final AtomicBoolean slo_rule_arrived = new AtomicBoolean(false);

    public Long last_processed_adaptation_time = -1L;//initialization


    public DetectorSubcomponent(CharacterizedThread.CharacterizedThreadRunMode characterized_thread_run_mode) {
        super.thread_type = CharacterizedThread.CharacterizedThreadType.slo_bound_running_thread;
        subcomponent_state = new DetectorSubcomponentState();
        utilities = new DetectorSubcomponentUtilities();
        Integer current_detector_id = -1;//initialization
        synchronized (detector_integer_id){
            /*try {
                detector_integer_id.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }*/
            detector_integer_id.setValue(detector_integer_id.getValue()+1);
            current_detector_id = detector_integer_id.getValue();
            //detector_integer_id.notify();
        }
        if (characterized_thread_run_mode.equals(attached)) {
            DetectorSubcomponentUtilities.run_slo_violation_detection_engine(this);
        }else/*detached mode*/{
            CharacterizedThread.create_new_thread(new Runnables.SLODetectionEngineRunnable(this), "detector_"+current_detector_id+"_master_thread", true,this);
        }
        detector_subcomponents.put(String.valueOf(current_detector_id),this);
    }

    public BiFunction<String, String, String> slo_rule_topic_subscriber_function = (topic, message) -> {
        synchronized (can_modify_slo_rules) {
            can_modify_slo_rules.setValue(true);
            MESSAGE_CONTENTS.assign_value(topic, message);
            slo_rule_arrived.set(true);
            can_modify_slo_rules.notifyAll();

            Logger.getAnonymousLogger().log(info_logging_level, "BrokerClientApp:  - Received text message: " + message + " at topic " + topic);

        }
        return topic + ":MSG:" + message;
    };
    public static BrokerSubscriber device_lost_subscriber = new BrokerSubscriber(topic_for_lost_device_announcement, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);
    public static BiFunction<String, String, String> device_lost_subscriber_function = (topic, message) -> {
        BrokerPublisher persistent_publisher = new BrokerPublisher(topic_for_severity_announcement, prop.getProperty("broker_ip_url"), prop.getProperty("broker_username"), prop.getProperty("broker_password"), amq_library_configuration_location);

        Clock clock = Clock.systemUTC();
        Long current_time_seconds = (long) Math.floor(clock.millis()/1000.0);
        JSONObject severity_json = new JSONObject();
        severity_json.put("severity", 100);
        severity_json.put("probability", 100);
        severity_json.put("predictionTime", current_time_seconds);
        persistent_publisher.publish(severity_json.toJSONString());

        //TODO possibly necessary to remove the next adaptation time as it will probably not be possible to start an adaptation during it
        return topic + ":MSG:" + message;
    };

    public DetectorSubcomponentUtilities getUtilities() {
        return utilities;
    }

    public void setUtilities(DetectorSubcomponentUtilities utilities) {
        this.utilities = utilities;
    }

    public static String get_detector_subcomponent_statistics() {
        return "Currently, the number of active detectors are "+detector_integer_id;
    }

    public AtomicBoolean getStop_signal() {
        return stop_signal;
    }

    public DetectorSubcomponentState getSubcomponent_state() {
        return subcomponent_state;
    }

    public void setSubcomponent_state(DetectorSubcomponentState subcomponent_state) {
        this.subcomponent_state = subcomponent_state;
    }
}
