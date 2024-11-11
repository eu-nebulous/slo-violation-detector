package utility_beans.broker_communication;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import utility_beans.broker_communication.CustomConnectorHandler;
import utility_beans.broker_communication.ExtendedConnector;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


public class BrokerPublisher {
    public static String EMPTY="";
    //private HashMap<String, HashSet<String>> broker_and_topics_to_publish_to = new HashMap<>();
    private Publisher private_publisher_instance;
    private ArrayList<Publisher> publishers = new ArrayList<>();

    private ExtendedConnector active_connector;
    private String topic;
    private String broker_ip;
    private int broker_port;

    
    public BrokerPublisher(String topic, String broker_ip, int broker_port, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {
        boolean able_to_initialize_BrokerPublisher = topic!=null && broker_ip!=null && brokerUsername!=null && brokerPassword!=null && !topic.equals(EMPTY) && !broker_ip.equals(EMPTY) && !brokerUsername.equals(EMPTY) && !brokerPassword.equals(EMPTY);

        if (!able_to_initialize_BrokerPublisher){
            Logger.getAnonymousLogger().log(Level.SEVERE,"Could not initialize BrokerPublisher");
            return;
        }
        boolean publisher_configuration_changed;
//        if (!broker_and_topics_to_publish_to.containsKey(broker_ip)){
//            HashSet<String> topics_to_publish_to = new HashSet<>();
//            topics_to_publish_to.add(topic);
//            broker_and_topics_to_publish_to.put(broker_ip,topics_to_publish_to);
//            publisher_configuration_changed = true;
//        }else{
//            if (!broker_and_topics_to_publish_to.get(broker_ip).contains(topic)){
//                broker_and_topics_to_publish_to.get(broker_ip).add(topic);
//                publisher_configuration_changed = true;
//            }
//            else{
//                publisher_configuration_changed = false;
//            }
//        }


        //if (publisher_configuration_changed || hard_initialize_connector){
//            for (String current_broker_ip : broker_and_topics_to_publish_to.keySet()){
            Logger.getAnonymousLogger().log(Level.INFO,"Publisher configuration changed, creating new connector at  "+broker_ip+" for topic "+topic);
            if (active_connector!=null) {
                active_connector.stop(new ArrayList<>(), publishers);
            }
            publishers.clear();
            //for (String broker_topic : broker_and_topics_to_publish_to.get(broker_ip)){
                //ArrayList<Publisher> publishers = new ArrayList<>();
                Publisher publisher = new Publisher("resource_manager_"+topic, topic, true, true);
                publishers.add(publisher);
                //if (broker_topic.equals(topic)){
                    this.private_publisher_instance = publisher;
                    //this.topic = broker_topic;
                    this.topic = topic;
                    this.broker_ip = broker_ip;
                    this.broker_port = broker_port;
                //}
            //}
            //CustomConnectorHandler custom_handler = new CustomConnectorHandler();

            active_connector = new ExtendedConnector("resource_manager"
                    , new CustomConnectorHandler() {}
                    , publishers
                    , List.of(),
                    false,
                    false,
                    new StaticExnConfig(
                            broker_ip,
                            broker_port,
                            brokerUsername,
                            brokerPassword,
                            60,
                            EMPTY
                    )
            );
            active_connector.start();

        //}
    }

    //TODO The methods below assume that the only content to be sent is json-like
    public void publish (String json_string_content, Collection<String> application_names){

        for (String application_name : application_names) {
            JSONParser parser = new JSONParser();
            JSONObject json_object = new JSONObject();
            try {
                json_object = (JSONObject) parser.parse(json_string_content);
            } catch (ParseException p) {
                Logger.getAnonymousLogger().log(Level.WARNING, "Could not parse the string content to be published to the broker as json, which is the following: "+json_string_content);
            }
            if (!is_publisher_null()) {
                private_publisher_instance.send(json_object);
                Logger.getAnonymousLogger().log(Level.WARNING,"Sent new message\n"+json_object.toJSONString());
            } else {
                Logger.getAnonymousLogger().log(Level.SEVERE, "Could not send message to AMQP broker, as the publisher instance is null");
            }
        }
    }
    public boolean is_publisher_null(){
        return (private_publisher_instance == null);
    }
}
