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
import static utilities.DebugDataSubscription.debug_data_trigger_topic_name;


import java.util.HashMap;
import java.util.Map;

public class CustomDataPublisher {
    private static final Map<String, String> presetTexts = new HashMap<>();

    static {
       update_event_data();
    }

    private static void update_event_data(){
        presetTexts.put("eu.nebulouscloud.monitoring.slo.new", "{\n" +
                "  \"name\": \"_Application1\",\n" +
                "  \"operator\": \"OR\",\n" +
                "  \"constraints\": [\n" +
                "    {\n" +
                "      \"name\": \"cpu_and_memory_or_swap_too_high\",\n" +
                "      \"operator\": \"AND\",\n" +
                "      \"constraints\": [\n" +
                "        {\n" +
                "          \"name\": \"cpu_usage_high\",\n" +
                "          \"metric\": \"cpu_usage\",\n" +
                "          \"operator\": \">\",\n" +
                "          \"threshold\": 80.0\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"memory_or_swap_usage_high\",\n" +
                "          \"operator\": \"OR\",\n" +
                "          \"constraints\": [\n" +
                "            {\n" +
                "              \"name\": \"memory_usage_high\",\n" +
                "              \"metric\": \"ram_usage\",\n" +
                "              \"operator\": \">\",\n" +
                "              \"threshold\": 70.0\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"disk_usage_high\",\n" +
                "              \"metric\": \"swap_usage\",\n" +
                "              \"operator\": \">\",\n" +
                "              \"threshold\": 50.0\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}");
        presetTexts.put("eu.nebulouscloud.monitoring.realtime.cpu_usage",
                "{\n" +
                        "    \"metricValue\": 12.34,\n" +
                        "    \"level\": 1,\n" +
                        "    \"component_id\":\"postgresql_1\",\n" +
                        "    \"timestamp\": "+(int)(System.currentTimeMillis()/1000)+"\n" +
                        "}\n");
        presetTexts.put("eu.nebulouscloud.monitoring.predicted.cpu_usage", "{\n" +
                "    \"metricValue\": 92.34,\n" +
                "    \"level\": 1,\n" +
                "    \"timestamp\": "+(int)(System.currentTimeMillis()/1000)+"\n" +
                "    \"probability\": 0.98,\n" +
                "    \"confidence_interval\" : [8,15]\n" +
                "    \"predictionTime\": "+(int)(10+System.currentTimeMillis()/1000)+"\n" +
                "}");
    }
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
                        brokerPassword,
                        60,
                        EMPTY
                )
        );
        connector.start();

    }


    public CustomDataPublisher(String broker_topic, String broker_ip, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {
        this(broker_topic,broker_ip,brokerUsername,brokerPassword,amqLibraryConfigurationLocation,slovid_publisher_key);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Broker input app");
        JTextField broker_ipTextField = new JTextField("localhost", 10);
        JComboBox<String> smallTextField = new JComboBox<>(new String[]{"eu.nebulouscloud.monitoring.slo.new","eu.nebulouscloud.monitoring.realtime.cpu_usage", "eu.nebulouscloud.monitoring.predicted.cpu_usage", debug_data_trigger_topic_name});
        smallTextField.setEditable(true);
        JTextField othersmallTextField = new JTextField("slovid", 10);
        JTextArea largeTextArea = new JTextArea(10, 25);
        JButton submitButton = new JButton("Send");

        AtomicReference<String> broker_ip = new AtomicReference<>();
        AtomicReference<String> broker_topic = new AtomicReference<>();
        AtomicReference<String> message_payload = new AtomicReference<>();
        AtomicReference<String> publisher_key = new AtomicReference<>();

        smallTextField.addActionListener(e -> {
            update_event_data();
            String selectedOption = (String) smallTextField.getSelectedItem();
            String presetText = presetTexts.getOrDefault(selectedOption, "");
            largeTextArea.setText(presetText);
        });

        submitButton.addActionListener(e -> {
            broker_ip.set(broker_ipTextField.getText());
            broker_topic.set(smallTextField.getSelectedItem().toString());
            message_payload.set(largeTextArea.getText());
            publisher_key.set(othersmallTextField.getText());
            CustomDataPublisher publisher = new CustomDataPublisher(broker_topic.toString(), broker_ip.toString(), "admin", "admin", EMPTY, publisher_key.toString());
            publisher.publish(message_payload.toString());
        });

        JPanel panel = new JPanel();
        panel.add(new JLabel("Broker to publish to:"));
        panel.add(broker_ipTextField);
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


class OldCustomDataPublisher {
    private static HashMap<String, HashSet<String>> broker_and_topics_to_publish_to = new HashMap<>();
    private Publisher private_publisher_instance;
    private String topic;
    private String broker_ip;
    public OldCustomDataPublisher(String broker_topic, String broker_ip, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation,String publisher_key) {

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
                                brokerPassword,
                                60,
                                EMPTY
                        )
                );
                connector.start();

    }


    public OldCustomDataPublisher(String broker_topic, String broker_ip, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {
        this(broker_topic,broker_ip,brokerUsername,brokerPassword,amqLibraryConfigurationLocation,slovid_publisher_key);
    }
    public static void main(String[] args){

        //JSONObject msg = new JSONObject();
        //msg.put("key","value");

        JFrame frame = new JFrame("Broker input app");
        JTextField broker_ipTextField = new JTextField("localhost",10);
        JTextField smallTextField = new JTextField("eu.nebulouscloud.monitoring.metric_list",30);
        JTextField othersmallTextField = new JTextField("slovid",20);
        JTextArea largeTextArea = new JTextArea(10, 30);
        JButton submitButton = new JButton("Send");

        AtomicReference<String> broker_ip = new AtomicReference<>();
        AtomicReference<String> broker_topic = new AtomicReference<>();
        AtomicReference<String> message_payload = new AtomicReference<>();
        AtomicReference<String> publisher_key = new AtomicReference<>();


        submitButton.addActionListener(e -> {
            broker_ip.set(broker_ipTextField.getText());
            broker_topic.set(smallTextField.getText());
            message_payload.set(largeTextArea.getText());
            publisher_key.set(othersmallTextField.getText());
            OldCustomDataPublisher publisher = new OldCustomDataPublisher(broker_topic.toString(),broker_ip.toString(),"admin","admin",EMPTY,publisher_key.toString());
            publisher.publish(message_payload.toString());
        });

        JPanel panel = new JPanel();
        panel.add(new JLabel("Broker to publish to:"));
        panel.add(broker_ipTextField);
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
