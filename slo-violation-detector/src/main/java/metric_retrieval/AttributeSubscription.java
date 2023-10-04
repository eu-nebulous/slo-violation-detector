/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */        

package metric_retrieval;

import eu.melodic.event.brokerclient.BrokerSubscriber;
import eu.melodic.event.brokerclient.templates.EventFields;
import eu.melodic.event.brokerclient.templates.TopicNames;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import runtime.Main;
import slo_processing.SLORule;
import slo_processing.SLOSubRule;
import utility_beans.PredictedMonitoringAttribute;
import utility_beans.RealtimeMonitoringAttribute;

import java.time.Clock;
import java.util.HashMap;
import java.util.function.BiFunction;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static runtime.Main.*;
import static utility_beans.PredictedMonitoringAttribute.getPredicted_monitoring_attributes;
import static utility_beans.RealtimeMonitoringAttribute.update_monitoring_attribute_value;

public class AttributeSubscription {
    SLORule slo_rule;
    private Thread realtime_subscription_thread, forecasted_subscription_thread;


    public AttributeSubscription(SLORule slo_rule, String broker_ip_address, String broker_username, String broker_password){
        this.slo_rule = slo_rule;
        for (String metric:slo_rule.get_monitoring_attributes()){

            String realtime_metric_topic_name = TopicNames.realtime_metric_values_topic(metric);
            Logger.getAnonymousLogger().log(info_logging_level,"Starting realtime subscription at "+realtime_metric_topic_name);
            BrokerSubscriber subscriber = new BrokerSubscriber(realtime_metric_topic_name, broker_ip_address,broker_username,broker_password, amq_library_configuration_location);
            BiFunction<String,String,String> function = (topic, message) ->{
                synchronized (RealtimeMonitoringAttribute.getMonitoring_attributes().get(topic)) {
                    try {
                        update_monitoring_attribute_value(topic,((Number)((JSONObject)new JSONParser().parse(message)).get("metricValue")).doubleValue());

                        Logger.getAnonymousLogger().log(info_logging_level,"RECEIVED message with value for "+topic+" equal to "+(((JSONObject)new JSONParser().parse(message)).get("metricValue")));
                    } catch (ParseException e) {
                        e.printStackTrace();
                        Logger.getAnonymousLogger().log(info_logging_level,"A parsing exception was caught while parsing message: "+message);
                    } catch (Exception e){
                        e.printStackTrace();
                        Logger.getAnonymousLogger().log(info_logging_level,"An unknown exception was caught while parsing message: "+message);
                    }
                }
                return message;
            };
            realtime_subscription_thread = new Thread(() -> {
                try {
                    subscriber.subscribe(function, Main.stop_signal);
                    if(Thread.interrupted()){
                        throw new InterruptedException();
                    }
                }catch (Exception i){
                    Logger.getAnonymousLogger().log(info_logging_level,"Possible interruption of realtime subscriber thread for "+realtime_metric_topic_name+" - if not stacktrace follows");
                    if (! (i instanceof InterruptedException)){
                        i.printStackTrace();
                    }
                }finally{
                    Logger.getAnonymousLogger().log(info_logging_level,"Removing realtime subscriber thread for "+realtime_metric_topic_name);
                    running_threads.remove("realtime_subscriber_thread_" + realtime_metric_topic_name);
                }
            });
            running_threads.put("realtime_subscriber_thread_"+realtime_metric_topic_name,realtime_subscription_thread);
            realtime_subscription_thread.start();



            String forecasted_metric_topic_name = TopicNames.final_metric_predictions_topic(metric);
            Logger.getAnonymousLogger().log(info_logging_level,"Starting forecasted metric subscription at "+forecasted_metric_topic_name);
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
                        Logger.getAnonymousLogger().log(info_logging_level,"Catching exception successfully");
                        c.printStackTrace();
                        confidence_interval = Double.NEGATIVE_INFINITY;
                    }
                    long timestamp = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.timestamp)).longValue();
                    long targeted_prediction_time = ((Number)((JSONObject)new JSONParser().parse(message)).get(EventFields.PredictionMetricEventFields.prediction_time)).longValue();
                    Logger.getAnonymousLogger().log(info_logging_level,"RECEIVED message with predicted value for "+predicted_attribute_name+" equal to "+ forecasted_value);


                        synchronized (ADAPTATION_TIMES_MODIFY) {
                            while (!ADAPTATION_TIMES_MODIFY.getValue()){
                                try {
                                    ADAPTATION_TIMES_MODIFY.wait();
                                } catch (InterruptedException e) {
                                    Logger.getAnonymousLogger().log(warning_logging_level,"Interrupted while waiting to access the lock for adaptation times object");
                                    e.printStackTrace();
                                }
                            }
                            ADAPTATION_TIMES_MODIFY.setValue(false);
                            if (!adaptation_times.contains(targeted_prediction_time) && (!adaptation_times_pending_processing.contains(targeted_prediction_time)) && ((targeted_prediction_time * 1000 - time_horizon_seconds * 1000L) > (Clock.systemUTC()).millis())) {
                                Logger.getAnonymousLogger().log(info_logging_level, "Adding a new targeted prediction time " + targeted_prediction_time + " expiring in "+(targeted_prediction_time*1000-System.currentTimeMillis())+" from topic "+topic);
                                adaptation_times.add(targeted_prediction_time);
                                synchronized (PREDICTION_EXISTS) {
                                    PREDICTION_EXISTS.setValue(true);
                                    PREDICTION_EXISTS.notifyAll();
                                }
                            }else {
                                if (adaptation_times.contains(targeted_prediction_time)) {
                                    Logger.getAnonymousLogger().log(info_logging_level, "Could not add the new targeted prediction time " + targeted_prediction_time + " from topic " + topic + " as it is already present");
                                } else if (!adaptation_times_pending_processing.contains(targeted_prediction_time)) {
                                    if (targeted_prediction_time * 1000 - time_horizon_seconds * 1000L - (Clock.systemUTC()).millis() <= 0) {
                                    Logger.getAnonymousLogger().log(info_logging_level, "Could not add the new targeted prediction time " + targeted_prediction_time + " from topic " + topic + " as it would expire in " + (targeted_prediction_time * 1000 - System.currentTimeMillis()) + " milliseconds and the prediction horizon is " + time_horizon_seconds * 1000L + " milliseconds");
                                    }else{
                                        Logger.getAnonymousLogger().log(info_logging_level,"Adding new prediction time "+targeted_prediction_time+" which expires in " + (targeted_prediction_time * 1000 - System.currentTimeMillis()));
                                        adaptation_times_pending_processing.add(targeted_prediction_time);
                                    }
                                }
                            }
                            ADAPTATION_TIMES_MODIFY.setValue(true);
                            ADAPTATION_TIMES_MODIFY.notifyAll();
                        }
                    synchronized (Main.can_modify_slo_rules) {
                        while(!Main.can_modify_slo_rules.getValue()) {
                            Main.can_modify_slo_rules.wait();
                        }
                        Main.can_modify_slo_rules.setValue(false);
                        for (SLOSubRule subrule : SLOSubRule.getSlo_subrules_per_monitoring_attribute().get(predicted_attribute_name)) { //Get the subrules which are associated to the monitoring attribute which is predicted, and perform the following processing to each one of them

                            getPredicted_monitoring_attributes().computeIfAbsent(subrule.getId(), k -> new HashMap<>());
                            //if ( (getPredicted_monitoring_attributes().get(subrule.getId()).get(targeted_prediction_time)!=null) &&(getPredicted_monitoring_attributes().get(subrule.getId()).get(targeted_prediction_time).getTimestamp()>timestamp)){
                            if (last_processed_adaptation_time>=targeted_prediction_time){
                                //Do nothing, as in this case the targeted prediction time of the 'new' prediction is older than or equal to the last processed adaptation timepoint. This means that this prediction has arrived delayed, and so it should be disregarded
                            }else {
                                PredictedMonitoringAttribute prediction_attribute = new PredictedMonitoringAttribute(predicted_attribute_name, subrule.getThreshold(), subrule.getId(), forecasted_value, probability_confidence, confidence_interval,timestamp, targeted_prediction_time);

                                subrule.setAssociated_predicted_monitoring_attribute(prediction_attribute);

                                getPredicted_monitoring_attributes().get(subrule.getId()).put(targeted_prediction_time, prediction_attribute);
                            }
                        }
                        can_modify_slo_rules.setValue(true);
                        can_modify_slo_rules.notifyAll();
                    }
                    //SLOViolationCalculator.get_Severity_all_metrics_method(prediction_attribute)

                } catch (ParseException p){
                    p.printStackTrace();
                } catch (InterruptedException e) {
                    Logger.getAnonymousLogger().log(info_logging_level,"Monitoring attribute subscription thread for prediction attribute "+predicted_attribute_name+" is stopped");
                } catch (ClassCastException | NumberFormatException n){
                    Logger.getAnonymousLogger().log(info_logging_level,"Error while trying to parse message\n"+message);
                } catch (Exception e){
                    Logger.getAnonymousLogger().log(info_logging_level,"An unknown exception was caught\n"+message);
                }
                return message;
            };


            forecasted_subscription_thread = new Thread(() -> {
                try {
                    synchronized (Main.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(forecasted_metric_topic_name)) {
                        //if (Main.HAS_MESSAGE_ARRIVED.get_synchronized_boolean(forecasted_metric_topic_name).getValue())
                        forecasted_subscriber.subscribe(forecasted_function,Main.stop_signal);
                    }
                    if (Thread.interrupted()) {
                        throw new InterruptedException();
                    }
                }catch (Exception i){
                    Logger.getAnonymousLogger().log(info_logging_level,"Possible interruption of forecasting subscriber thread for "+forecasted_metric_topic_name+" - if not stacktrace follows");
                    if (! (i instanceof InterruptedException)){
                        i.printStackTrace();
                    }
                }finally {
                    Logger.getAnonymousLogger().log(info_logging_level,"Removing forecasting subscriber thread for "+forecasted_metric_topic_name);
                    running_threads.remove("forecasting_subscriber_thread_"+forecasted_metric_topic_name);
                }
            });
            running_threads.put("forecasting_subscriber_thread_"+forecasted_metric_topic_name,forecasted_subscription_thread);
            forecasted_subscription_thread.start();

        }
    }
}
