package utility_beans;

import javax.swing.*;
import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;

public class CustomDataPublisher {
    private static HashMap<String, HashSet<String>> broker_and_topics_to_publish_to = new HashMap<>();
    private Publisher private_publisher_instance;
    private String topic;
    private String broker_ip;
    public CustomDataPublisher(String broker_topic, String broker_ip, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation,String publisher_key) {

        boolean publisher_configuration_changed;
        ArrayList<Publisher> publishers = new ArrayList<>();
        private_publisher_instance = new Publisher(slovid_publisher_key,broker_topic,true,true);
        publishers.add(private_publisher_instance);


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


    public CustomDataPublisher(String broker_topic, String broker_ip, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {
        this(broker_topic,broker_ip,brokerUsername,brokerPassword,amqLibraryConfigurationLocation,slovid_publisher_key);
    }
    public static void main(String[] args){

        //JSONObject msg = new JSONObject();
        //msg.put("key","value");

        JFrame frame = new JFrame("Broker input app");
        JTextField smallTextField = new JTextField("eu.nebulouscloud.monitoring.metric_list",30);
        JTextField othersmallTextField = new JTextField("slovid",20);
        JTextArea largeTextArea = new JTextArea(10, 30);
        JButton submitButton = new JButton("Send");

        AtomicReference<String> broker_topic = new AtomicReference<>();
        AtomicReference<String> message_payload = new AtomicReference<>();
        AtomicReference<String> publisher_key = new AtomicReference<>();


        submitButton.addActionListener(e -> {
            broker_topic.set(smallTextField.getText());
            message_payload.set(largeTextArea.getText());
            publisher_key.set(othersmallTextField.getText());
            CustomDataPublisher publisher = new CustomDataPublisher(broker_topic.toString(),"localhost","admin","admin",EMPTY,publisher_key.toString());
            publisher.publish(message_payload.toString());
        });

        JPanel panel = new JPanel();
        panel.add(new JLabel("Topic to publish to:"));
        panel.add(smallTextField);
        panel.add(new JLabel("Key to publish with:"));
        panel.add(othersmallTextField);
        panel.add(new JLabel("Text to publish:"));
        panel.add(new JScrollPane(largeTextArea));
        panel.add(submitButton);

        frame.add(panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        //publisher.publish(msg);
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

    public void publish(JSONObject json_object) {
        private_publisher_instance.send(json_object);
    }
}
