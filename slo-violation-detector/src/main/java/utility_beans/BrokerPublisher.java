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
import static java.lang.Thread.sleep;
import static java.util.logging.Level.INFO;
import static slo_violation_detector_engine.detector.DetectorSubcomponent.detector_subcomponents;
import static utilities.DebugDataSubscription.debug_data_output_topic_name;

public class BrokerPublisher {
    private static HashMap<String, HashSet<String>> broker_and_topics_to_publish_to = new HashMap<>();
    private Publisher private_publisher_instance;
    private ArrayList<Publisher> publishers = new ArrayList<>();

    private Connector active_connector;
    private String topic;
    private String broker_ip;

    public BrokerPublisher(String topic, String broker_ip, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {
        boolean able_to_initialize_BrokerPublisher = topic!=null && broker_ip!=null && brokerUsername!=null && brokerPassword!=null && !topic.equals(EMPTY) && !broker_ip.equals(EMPTY) && !brokerUsername.equals(EMPTY) && !brokerPassword.equals(EMPTY);

        if (!able_to_initialize_BrokerPublisher){
            return;
        }
        boolean publisher_configuration_changed;
        if (!broker_and_topics_to_publish_to.containsKey(broker_ip)){
            HashSet<String> topics_to_publish_to = new HashSet<>();
            topics_to_publish_to.add(debug_data_output_topic_name);
            topics_to_publish_to.add(topic_for_severity_announcement);
            //topics_to_publish_to.add(slo_rules_topic);
            topics_to_publish_to.add(topic);
            broker_and_topics_to_publish_to.put(broker_ip,topics_to_publish_to);
            publisher_configuration_changed = true;
        }else{
            if (!broker_and_topics_to_publish_to.get(broker_ip).contains(topic)){
                broker_and_topics_to_publish_to.get(broker_ip).add(topic);
                publisher_configuration_changed = true;
            }
            else{
                publisher_configuration_changed = false;
            }
        }


        if (publisher_configuration_changed){
//            for (String current_broker_ip : broker_and_topics_to_publish_to.keySet()){
                publishers.clear();
                for (String broker_topic : broker_and_topics_to_publish_to.get(broker_ip)){
                    //ArrayList<Publisher> publishers = new ArrayList<>();
                    Publisher publisher = new Publisher(slovid_publisher_key + NAME_SEPARATOR +broker_topic, broker_topic, true, true);
                    publishers.add(publisher);
                    if (broker_topic.equals(topic)){
                        this.private_publisher_instance = publishers.get(publishers.size()-1);
                        this.topic = broker_topic;
                        this.broker_ip = broker_ip;
                    }
                }
                //CustomConnectorHandler custom_handler = new CustomConnectorHandler();

                active_connector = new Connector("slovid"
                        , new ConnectorHandler() {}
                        , publishers
                        , List.of(),
                        false,
                        false,
                        new StaticExnConfig(
                                broker_ip,
                                5672,
                                brokerUsername,
                                brokerPassword,
                                60,
                                EMPTY
                        )
                );
                active_connector.start();
                //Logger.getGlobal().log(INFO,"Sending from EXTERIOR");
                //private_publisher_instance.send(new JSONObject());

            }
    }

    //TODO The methods below assume that the only content to be sent is json-like
    public void publish (String json_string_content, Iterable<String> application_names){

        for (String application_name : application_names) {
            JSONParser parser = new JSONParser();
            JSONObject json_object = new JSONObject();
            try {
                json_object = (JSONObject) parser.parse(json_string_content);
            } catch (ParseException p) {
                Logger.getGlobal().log(Level.WARNING, "Could not parse the string content to be published to the broker as json");
            }
            if (private_publisher_instance != null) {
                private_publisher_instance.send(json_object);
            } else {
                Logger.getGlobal().log(Level.SEVERE, "Could not send message to AMQP broker, as the broker ip to be used has not been specified");
            }
        }
    }

    public void publish(String json_string_content) {
        publish(json_string_content,detector_subcomponents.keySet());
    }
}
