package utility_beans;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.lang.reflect.Array;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static utilities.DebugDataSubscription.debug_data_output_topic_name;

public class BrokerPublisher {
    private static HashMap<String, HashSet<String>> broker_and_topics_to_publish_to = new HashMap<>();
    private Publisher private_publisher_instance;
    private String topic;
    private String broker_ip;
    public BrokerPublisher(String topic, String brokerIpUrl, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {
        boolean publisher_configuration_changed;
        if (!broker_and_topics_to_publish_to.containsKey(brokerIpUrl)){
            HashSet<String> topics_to_publish_to = new HashSet<>();
            topics_to_publish_to.add(debug_data_output_topic_name);
            topics_to_publish_to.add(topic_for_severity_announcement);
            topics_to_publish_to.add(slo_rules_topic);
            topics_to_publish_to.add(topic);
            broker_and_topics_to_publish_to.put(brokerIpUrl,new HashSet<>());
            publisher_configuration_changed = true;
        }else{
            if (!broker_and_topics_to_publish_to.get(brokerIpUrl).contains(topic)){
                broker_and_topics_to_publish_to.get(brokerIpUrl).add(topic);
                publisher_configuration_changed = true;
            }
            else{
                publisher_configuration_changed = false;
            }
        }
        if (publisher_configuration_changed){
            for (String broker_ip : broker_and_topics_to_publish_to.keySet()){
                ArrayList<Publisher> publishers = new ArrayList<>();
                for (String broker_topic : broker_and_topics_to_publish_to.get(broker_ip)){
                    Publisher current_publisher = new Publisher(slovid_publisher_key,broker_topic,true);
                    publishers.add(current_publisher);
                    if (broker_ip.equals(brokerIpUrl) && broker_topic.equals(topic)){
                        this.private_publisher_instance = current_publisher;
                        this.topic = broker_topic;
                        this.broker_ip = broker_ip;
                    }
                }
                Connector connector = new Connector("slovid",
                        new ConnectorHandler() {
                        }, publishers
                        , List.of(),
                        false,
                        false,
                        new StaticExnConfig(
                                broker_ip,
                                5672,
                                brokerUsername,
                                brokerPassword
                        )
                );
                connector.start();
            }
        }
    }

    //TODO This assumes that the only content to be sent is json-like
    public void publish(String json_string_content) {
        JSONParser parser = new JSONParser();
        JSONObject json_object = new JSONObject();
        try{
            json_object = (JSONObject) parser.parse(json_string_content);
        }catch (ParseException p){
            Logger.getGlobal().log(Level.SEVERE,"Could not parse the string content");
        }
        private_publisher_instance.send(json_object);
    }
}
