/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package metric_retrieval;

//import eu.melodic.event.brokerclient.BrokerSubscriber;
//import eu.melodic.event.brokerclient.templates.EventFields;
//import eu.melodic.event.brokerclient.templates.TopicNames;
import slo_violation_detector_engine.detector.DetectorSubcomponent;
import utility_beans.BrokerSubscriber;
import utility_beans.BrokerSubscriber.EventFields;
import utility_beans.BrokerSubscriber.TopicNames;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import slo_rule_modelling.SLORule;
import slo_rule_modelling.SLOSubRule;
import utility_beans.CharacterizedThread;
import utility_beans.PredictedMonitoringAttribute;
import utility_beans.RealtimeMonitoringAttribute;

import java.time.Clock;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static utility_beans.PredictedMonitoringAttribute.getPredicted_monitoring_attributes;
import static utility_beans.RealtimeMonitoringAttribute.update_monitoring_attribute_value;

public class AttributeSubscription {
    SLORule slo_rule;

    public AttributeSubscription(SLORule slo_rule, String broker_ip_address, String broker_username, String broker_password){
        this.slo_rule = slo_rule;
        DetectorSubcomponent detector = slo_rule.getAssociated_detector();
        for (String metric:slo_rule.get_monitoring_attributes()){

            String realtime_metric_topic_name = TopicNames.realtime_metric_values_topic(metric);
            Logger.getGlobal().log(info_logging_level,"Starting realtime subscription at "+realtime_metric_topic_name);
            BrokerSubscriber subscriber = new BrokerSubscriber(realtime_metric_topic_name, broker_ip_address,broker_username,broker_password, amq_library_configuration_location);
            BiFunction<String,String,String> function = (topic, message) ->{
                RealtimeMonitoringAttribute realtimeMonitoringAttribute = new RealtimeMonitoringAttribute(topic);
                synchronized (detector.getSubcomponent_state().getMonitoring_attributes().get(topic)) {
                    try {
                        update_monitoring_attribute_value(detector,topic,((Number)((JSONObject)new JSONParser().parse(message)).get("metricValue")).doubleValue());

                        Logger.getGlobal().log(info_logging_level,"RECEIVED message with value for "+topic+" equal to "+(((JSONObject)new JSONParser().parse(message)).get("metricValue")));
                    } catch (ParseException e) {
                        e.printStackTrace();
                        Logger.getGlobal().log(info_logging_level,"A parsing exception was caught while parsing message: "+message);
                    } catch (Exception e){
                        e.printStackTrace();
                        Logger.getGlobal().log(info_logging_level,"An unknown exception was caught while parsing message: "+message);
                    }
                }
                return message;
            };
            Runnable realtime_subscription_runnable = () -> {
                try {
                    subscriber.subscribe(function, detector.stop_signal);
                    if(Thread.interrupted()){
                        throw new InterruptedException();
                    }
                }catch (Exception i){
                    Logger.getGlobal().log(info_logging_level,"Possible interruption of realtime subscriber thread for "+realtime_metric_topic_name+" - if not stacktrace follows");
                    if (! (i instanceof InterruptedException)){
                        i.printStackTrace();
                    }
                }finally{
                    Logger.getGlobal().log(info_logging_level,"Removing realtime subscriber thread for "+realtime_metric_topic_name);
                    detector.getSubcomponent_state().slo_bound_running_threads.remove("realtime_subscriber_thread_" + realtime_metric_topic_name);
                }
            };
            CharacterizedThread.create_new_thread(realtime_subscription_runnable,"realtime_subscriber_thread_"+realtime_metric_topic_name,true,detector);



            String forecasted_metric_topic_name = TopicNames.final_metric_predictions_topic(metric);
            Logger.getGlobal().log(info_logging_level,"Starting forecasted metric subscription at "+forecasted_metric_topic_name);
            BrokerSubscriber forecasted_subscriber = new BrokerSubscriber(forecasted_metric_topic_name, broker_ip_address,broker_username,broker_password, amq_library_configuration_location);

            BiFunction<String,String,String> forecasted_function = (topic,message) ->{
                String predicted_attribute_name = topic.replaceFirst("prediction\\.",EMPTY);
                HashMap<Integer, HashMap<Long,PredictedMonitoringAttribute>> predicted_attributes = getPredicted_monitoring_attributes();
                try {
                    double forecasted_value = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.metric_value)).doubleValue();
                    double probability_confidence = 100*((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.probability)).doubleValue();
                    JSONArray json_array_confidence_interval = ((JSONArray)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.confidence_interval));

                    double confidence_interval;
                    try{
                        confidence_interval = ((Number) json_array_confidence_interval.get(1)).doubleValue() - ((Number) json_array_confidence_interval.get(0)).doubleValue();
                    }catch (ClassCastException | NumberFormatException c){
                        Logger.getGlobal().log(info_logging_level,"Catching exception successfully");
                        c.printStackTrace();
                        confidence_interval = Double.NEGATIVE_INFINITY;
                    }
                    long timestamp = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.timestamp)).longValue();
                    long targeted_prediction_time = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.prediction_time)).longValue();
                    Logger.getGlobal().log(info_logging_level,"RECEIVED message with predicted value for "+predicted_attribute_name+" equal to "+ forecasted_value);


                        synchronized (detector.ADAPTATION_TIMES_MODIFY) {
                            while (!detector.ADAPTATION_TIMES_MODIFY.getValue()){
                                try {
                                    detector.ADAPTATION_TIMES_MODIFY.wait();
                                } catch (InterruptedException e) {
                                    Logger.getGlobal().log(warning_logging_level,"Interrupted while waiting to access the lock for adaptation times object");
                                    e.printStackTrace();
                                }
                            }
                            detector.ADAPTATION_TIMES_MODIFY.setValue(false);
                            if (!detector.getSubcomponent_state().adaptation_times.contains(targeted_prediction_time) && (!detector.getSubcomponent_state().adaptation_times_pending_processing.contains(targeted_prediction_time)) && ((targeted_prediction_time * 1000 - time_horizon_seconds * 1000L) > (Clock.systemUTC()).millis())) {
                                Logger.getGlobal().log(info_logging_level, "Adding a new targeted prediction time " + targeted_prediction_time + " expiring in "+(targeted_prediction_time*1000-System.currentTimeMillis())+" from topic "+topic);
                                detector.getSubcomponent_state().adaptation_times.add(targeted_prediction_time);
                                synchronized (detector.PREDICTION_EXISTS) {
                                    detector.PREDICTION_EXISTS.setValue(true);
                                    detector.PREDICTION_EXISTS.notifyAll();
                                }
                            }else {
                                if (detector.getSubcomponent_state().adaptation_times.contains(targeted_prediction_time)) {
                                    Logger.getGlobal().log(info_logging_level, "Could not add the new targeted prediction time " + targeted_prediction_time + " from topic " + topic + " as it is already present");
                                } else if (!detector.getSubcomponent_state().adaptation_times_pending_processing.contains(targeted_prediction_time)) {
                                    if (targeted_prediction_time * 1000 - time_horizon_seconds * 1000L - (Clock.systemUTC()).millis() <= 0) {
                                    Logger.getGlobal().log(info_logging_level, "Could not add the new targeted prediction time " + targeted_prediction_time + " from topic " + topic + " as it would expire in " + (targeted_prediction_time * 1000 - System.currentTimeMillis()) + " milliseconds and the prediction horizon is " + time_horizon_seconds * 1000L + " milliseconds");
                                    }else{
                                        Logger.getGlobal().log(info_logging_level,"Adding new prediction time "+targeted_prediction_time+" which expires in " + (targeted_prediction_time * 1000 - System.currentTimeMillis()));
                                        detector.getSubcomponent_state().adaptation_times_pending_processing.add(targeted_prediction_time);
                                    }
                                }
                            }
                            detector.ADAPTATION_TIMES_MODIFY.setValue(true);
                            detector.ADAPTATION_TIMES_MODIFY.notifyAll();
                        }
                    synchronized (detector.can_modify_slo_rules) {
                        while(!detector.can_modify_slo_rules.getValue()) {
                            detector.can_modify_slo_rules.wait();
                        }
                        detector.can_modify_slo_rules.setValue(false);
                        for (SLOSubRule subrule : SLOSubRule.getSlo_subrules_per_monitoring_attribute().get(predicted_attribute_name)) { //Get the subrules which are associated to the monitoring attribute which is predicted, and perform the following processing to each one of them

                            getPredicted_monitoring_attributes().computeIfAbsent(subrule.getId(), k -> new HashMap<>());
                            //if ( (getPredicted_monitoring_attributes().get(subrule.getId()).get(targeted_prediction_time)!=null) &&(getPredicted_monitoring_attributes().get(subrule.getId()).get(targeted_prediction_time).getTimestamp()>timestamp)){
                            if (detector.last_processed_adaptation_time>=targeted_prediction_time){
                                //Do nothing, as in this case the targeted prediction time of the 'new' prediction is older than or equal to the last processed adaptation timepoint. This means that this prediction has arrived delayed, and so it should be disregarded
                            }else {
                                PredictedMonitoringAttribute prediction_attribute = new PredictedMonitoringAttribute(detector,predicted_attribute_name, subrule.getThreshold(), subrule.getId(), forecasted_value, probability_confidence, confidence_interval,timestamp, targeted_prediction_time);

                                subrule.setAssociated_predicted_monitoring_attribute(prediction_attribute);

                                getPredicted_monitoring_attributes().get(subrule.getId()).put(targeted_prediction_time, prediction_attribute);
                            }
                        }
                        detector.can_modify_slo_rules.setValue(true);
                        detector.can_modify_slo_rules.notifyAll();
                    }
                    //SLOViolationCalculator.get_Severity_all_metrics_method(prediction_attribute)

                } catch (ParseException p){
                    p.printStackTrace();
                } catch (InterruptedException e) {
                    Logger.getGlobal().log(info_logging_level,"Monitoring attribute subscription thread for prediction attribute "+predicted_attribute_name+" is stopped");
                } catch (ClassCastException | NumberFormatException n){
                    Logger.getGlobal().log(info_logging_level,"Error while trying to parse message\n"+message);
                } catch (Exception e){
                    Logger.getGlobal().log(info_logging_level,"An unknown exception was caught\n"+message);
                }
                return message;
            };

            Runnable forecasted_subscription_runnable = () -> {
                try {
                    synchronized (detector.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(forecasted_metric_topic_name)) {
                        //if (Main.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(forecasted_metric_topic_name).getValue())
                        forecasted_subscriber.subscribe(forecasted_function,detector.stop_signal);
                    }
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                }catch (Exception i){
                    Logger.getGlobal().log(info_logging_level,"Possible interruption of forecasting subscriber thread for "+forecasted_metric_topic_name+" - if not stacktrace follows");
                    if (! (i instanceof InterruptedException)){
                        i.printStackTrace();
                    }
                }finally {
                    Logger.getGlobal().log(info_logging_level,"Removing forecasting subscriber thread for "+forecasted_metric_topic_name);
                    detector.getSubcomponent_state().persistent_running_detector_threads.remove("forecasting_subscriber_thread_"+forecasted_metric_topic_name);
                }
            };
            CharacterizedThread.create_new_thread(forecasted_subscription_runnable, "forecasting_subscriber_thread_" + forecasted_metric_topic_name, true,detector);
        }
    }
}
