package slo_violation_detector_engine.detector;

import org.apache.commons.collections4.queue.CircularFifoQueue;
import slo_violation_detector_engine.generic.Runnables;
import slo_violation_detector_engine.generic.SLOViolationDetectorSubcomponent;
import utility_beans.*;


import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static slo_violation_detector_engine.generic.ComponentState.prop;
import static utility_beans.CharacterizedThread.CharacterizedThreadRunMode.attached;
import static utility_beans.RealtimeMonitoringAttribute.aggregate_metric_values;


public class DetectorSubcomponent extends SLOViolationDetectorSubcomponent {
    public static final SynchronizedInteger detector_integer_id = new SynchronizedInteger();
    public static Map<String,DetectorSubcomponent> detector_subcomponents = Collections.synchronizedMap(new HashMap<>()); //A HashMap containing all detector subcomponents
    private DetectorSubcomponentState subcomponent_state;
    public final AtomicBoolean stop_signal = new AtomicBoolean(false);
    public final SynchronizedBoolean can_modify_slo_rules = new SynchronizedBoolean(false);
    public final SynchronizedBoolean can_modify_monitoring_metrics = new SynchronizedBoolean(false);
    public SynchronizedBooleanMap HAS_MESSAGE_ARRIVED = new SynchronizedBooleanMap();


    public final SynchronizedBoolean PREDICTION_EXISTS = new SynchronizedBoolean(false);
    public final SynchronizedBoolean ADAPTATION_TIMES_MODIFY = new SynchronizedBoolean(true);
    public final AtomicBoolean slo_rule_arrived = new AtomicBoolean(false);

    public Long last_processed_adaptation_time = -1L;//initialization
    private String detector_name;
    private String handled_application_name;
    private static String broker_ip = prop.getProperty("broker_ip_url");
    private static String broker_username = prop.getProperty("broker_username");
    private static String broker_password = prop.getProperty("broker_password");


    public DetectorSubcomponent(String application_name, CharacterizedThread.CharacterizedThreadRunMode characterized_thread_run_mode) {
        super.thread_type = CharacterizedThread.CharacterizedThreadType.persistent_running_detector_thread;
        subcomponent_state = new DetectorSubcomponentState();
        Integer current_detector_id;
        synchronized (detector_integer_id){
            /*try {
                detector_integer_id.wait();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }*/
            detector_integer_id.setValue(detector_integer_id.getValue()+1);
            current_detector_id = detector_integer_id.getValue();
            //detector_integer_id.notify();
            handled_application_name = application_name;
            detector_name = "detector_"+application_name+"_"+current_detector_id;
        }
        if (characterized_thread_run_mode.equals(attached)) {
            DetectorSubcomponentUtilities.run_slo_violation_detection_engine(this);
        }else/*detached mode*/{
            CharacterizedThread.create_new_thread(new Runnables.SLODetectionEngineRunnable(this), detector_name+"_master_thread", true,this, CharacterizedThread.CharacterizedThreadType.persistent_running_detector_thread);
        }
        detector_subcomponents.put(detector_name,this);
    }

    public void update_monitoring_attribute_value(String name,Number value){
        if(getSubcomponent_state().getMonitoring_attributes().get(name)==null){

            RealtimeMonitoringAttribute.AttributeValuesType attribute_type;
            if (value instanceof Integer){
                attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Integer;
            }else if (value instanceof Double){
                attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Double;
            }else{
                attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Unknown;
            }
            getSubcomponent_state().getMonitoring_attributes().put(name,new RealtimeMonitoringAttribute(name,false,attribute_type));
            //monitoring_attributes_max_values.put(name,value);
            //monitoring_attributes_min_values.put(name,value);

        }else if (getSubcomponent_state().getMonitoring_attributes().get(name).getType()==RealtimeMonitoringAttribute.AttributeValuesType.Unknown){
            RealtimeMonitoringAttribute.AttributeValuesType attribute_type;
            if (value instanceof Integer){
                attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Integer;
            }else if (value instanceof Double){
                attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Double;
            }else{
                attribute_type = RealtimeMonitoringAttribute.AttributeValuesType.Unknown;
            }
            getSubcomponent_state().getMonitoring_attributes().get(name).setType(attribute_type);
        }

        getSubcomponent_state().getMonitoring_attributes().get(name).getActual_metric_values().add(value);
        getSubcomponent_state().getMonitoring_attributes_statistics().get(name).update_attribute_statistics(value);
        /*
        if(get_90th_percentile_high_value(name,value)>monitoring_attributes_max_values.get(name)){
            monitoring_attributes_max_values.put(name,value);
        }else if (get_90th_percentile_low_value(name,value)<monitoring_attributes_min_values.get(name)){
            monitoring_attributes_min_values.put(name,value);
        }
         */
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

    @Override
    public String get_name() {
        return detector_name;
    }

    public void set_name(String name){
        detector_name = name;
    }

    public String get_application_name(){
        return handled_application_name;
    }

    public BrokerSubscriptionDetails getBrokerSubscriptionDetails(String topic) {
        return new BrokerSubscriptionDetails(broker_ip,broker_username,broker_password,handled_application_name,topic);
    }

    public double get_metric_value(String metric_name){
        CircularFifoQueue<Number> actual_metric_values = getSubcomponent_state().getMonitoring_attributes().get(metric_name).getActual_metric_values();
        if (actual_metric_values.size()==0){
            Logger.getGlobal().log(warning_logging_level,"Trying to retrieve realtime values from an empty queue for metric "+metric_name);
        }
        return aggregate_metric_values(actual_metric_values);
    }

    public static DetectorSubcomponent get_associated_detector(String application_name){
        DetectorSubcomponent associated_detector = detector_subcomponents.get(application_name);
        if (associated_detector==null){
            if (detector_subcomponents.size()==1 && detector_subcomponents.get(default_application_name)!=null){//This means only the initial 'default' application exists
                associated_detector = detector_subcomponents.get(default_application_name);
                associated_detector.set_name(application_name);
            }
            else {
                associated_detector = new DetectorSubcomponent(application_name, CharacterizedThread.CharacterizedThreadRunMode.detached);
            }
        }
        return associated_detector;

    }
}
