package utility_beans;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.apache.qpid.protonj2.client.Message;
import org.json.simple.JSONValue;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.slovid_publisher_key;

public class BrokerSubscriber {

    private static class MessageProcessingHandler extends Handler{
        private static final BiFunction temporary_function = (Object o, Object o2) -> {
            //System.out.println("");
            Logger.getGlobal().log(Level.INFO,"REPLACE_TEMPORARY_HANDLING_FUNCTIONALITY");
            return "IN_PROCESSING";
        };
        private BiFunction<String,String,String> processing_function;
        @Override
        public void onMessage(String key, String address, Map body, Message message, Context context) {
            processing_function.apply(address, JSONValue.toJSONString(body));
        }
        public MessageProcessingHandler(){
            this.processing_function = temporary_function;
        }
        public MessageProcessingHandler(BiFunction biFunction){
            this.processing_function = biFunction;
        }
        public BiFunction getProcessing_function() {
            return processing_function;
        }
        public void setProcessing_function(BiFunction processing_function) {
            this.processing_function = processing_function;
        }
    }
    private static HashMap<String, HashSet<String>> broker_and_topics_to_subscribe_to = new HashMap<>();
    private static HashMap<String,HashMap<String,Consumer>> active_consumers_per_topic_per_broker_ip = new HashMap<>();
    private static HashMap<String,ExtendedConnector> current_connectors = new HashMap<>();
    private String topic;
    private String broker_ip;
    private String brokerUsername;
    private String brokerPassword;
    public BrokerSubscriber(String topic, String broker_ip, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {
        boolean subscriber_configuration_changed;
        if (!broker_and_topics_to_subscribe_to.containsKey(broker_ip)){
            HashSet<String> topics_to_subscribe_to = new HashSet<>();
            //topics_to_subscribe_to.add(realtime_metric_topic_name);
            //topics_to_subscribe_to.add(forecasted_metric_topic_name);
            //topics_to_subscribe_to.add();
            topics_to_subscribe_to.add(topic);
            broker_and_topics_to_subscribe_to.put(broker_ip,new HashSet<>());
            active_consumers_per_topic_per_broker_ip.put(broker_ip,new HashMap<>());
            broker_and_topics_to_subscribe_to.get(broker_ip).add(topic);

            subscriber_configuration_changed = true;
        }else{
            if (!broker_and_topics_to_subscribe_to.get(broker_ip).contains(topic)){
                broker_and_topics_to_subscribe_to.get(broker_ip).add(topic);
                subscriber_configuration_changed = true;
            }
            else{
                subscriber_configuration_changed = false;
            }
        }
        if (subscriber_configuration_changed){
                Consumer current_consumer = new Consumer(topic, topic, new MessageProcessingHandler());
                active_consumers_per_topic_per_broker_ip.get(broker_ip).put(topic,current_consumer);

                this.topic = topic;
                this.broker_ip = broker_ip;
                this.brokerUsername = brokerUsername;
                this.brokerPassword = brokerPassword;
                add_topic_consumer_to_broker_connector(current_consumer);
        }
    }

    /**
     * This method updates the global connector of SLOViD to the AMQP server, by adding support for one more component
     */
    private void add_topic_consumer_to_broker_connector(Consumer new_consumer) {
        if (current_connectors.get(broker_ip)!=null) {
            current_connectors.get(broker_ip).add_consumer(new_consumer);
        }else {
            ArrayList<Consumer> consumers = new ArrayList<>();
            consumers.add(new_consumer);
            ExtendedConnector extended_connector = new ExtendedConnector("slovid",
                    new CustomConnectorHandler() {
                    },
                    List.of(),
                    consumers,
                    false,
                    false,
                    new StaticExnConfig(
                            broker_ip,
                            5672,
                            brokerUsername,
                            brokerPassword
                    )
            );
            extended_connector.start();
            current_connectors.put(broker_ip, extended_connector);
        }
    }

    private void remove_topic_from_broker_connector (String topic_key){
        if (current_connectors.get(broker_ip)!=null){
            current_connectors.get(broker_ip).remove_consumer_with_key(topic_key);
        }
    }

    public void subscribe(BiFunction<String, String, String> function, AtomicBoolean stop_signal) {
        Logger.getGlobal().log(Level.INFO,"ESTABLISHING SUBSCRIPTION");
        //First remove any leftover consumer
        active_consumers_per_topic_per_broker_ip.get(broker_ip).remove(topic);
        remove_topic_from_broker_connector(topic);
        //Then add the new consumer
        Consumer new_consumer = new Consumer(topic,topic,new MessageProcessingHandler(function));
        new_consumer.setProperty("topic",topic);
        active_consumers_per_topic_per_broker_ip.get(broker_ip).put(topic,new_consumer);
        add_topic_consumer_to_broker_connector(new_consumer);

        Logger.getGlobal().log(Level.INFO,"ESTABLISHED SUBSCRIPTION to topic "+topic);
        synchronized (stop_signal){
            while (!stop_signal.get()){
                try{
                    stop_signal.wait();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
            Logger.getGlobal().log(Level.INFO,"Stopping subscription for broker "+broker_ip+" and topic "+topic);
            stop_signal.set(false);
        }
        active_consumers_per_topic_per_broker_ip.get(broker_ip).remove(topic);
        remove_topic_from_broker_connector(topic);
    }

    public enum EventFields{
        ;

        public enum PredictionMetricEventFields {timestamp, prediction_time, probability, metric_value, confidence_interval}
    }


    public static class TopicNames{
        public static String realtime_metric_values_topic(String metric) {
            return null;
        }

        public static String final_metric_predictions_topic(String metric) {
            return null;
        }
    }
}
