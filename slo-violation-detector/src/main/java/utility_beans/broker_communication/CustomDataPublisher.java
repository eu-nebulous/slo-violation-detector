package utility_beans.broker_communication;

import javax.swing.*;
import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.awt.*;
import java.util.*;
import java.util.List;
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
        presetTexts.put("eu.nebulouscloud.monitoring.metric_list","{\n" +
                "  \"name\": \"_Application1\",\n" +
                "  \"version\": 1,\n" +
                "  \"metric_list\": [\n" +
                "    {\n" +
                "      \"name\": \"cpu_usage\",\n" +
                "      \"upper_bound\": \"100.0\",\n" +
                "      \"lower_bound\": \"0.0\"\n" +
                "    },\n" +
                "    {\n" +
                "      \"name\": \"ram_usage\",\n" +
                "      \"upper_bound\": \"100.0\",\n" +
                "      \"lower_bound\": \"0.0\"\n" +
                "    }\n" +
                "  ]\n" +
                "}");
        presetTexts.put("eu.nebulouscloud.forecasting.start_forecasting.exponentialsmoothing","{\n" +
                "  \"name\": \"_Application1\",\n" +
                "    \"metrics\": [\"cpu_usage\"],\n" +
                "    \"timestamp\": 1705046535,\n" +
                "    \"epoch_start\": 1705046500,\n" +
                "    \"number_of_forward_predictions\": 5,\n" +
                "    \"prediction_horizon\": 10\n" +
                "}");
        presetTexts.put(debug_data_trigger_topic_name,"{}");
    }
    private Publisher private_publisher_instance;
    private String topic;
    private String broker_ip;

    public CustomDataPublisher(String broker_topic, String broker_ip, Integer broker_port,String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation,String publisher_key) {

        boolean publisher_configuration_changed;
        ArrayList<Publisher> publishers = new ArrayList<>();
        private_publisher_instance = new Publisher(slovid_subscriber_key,broker_topic,true,true);
        publishers.add(private_publisher_instance);


        Connector connector = new Connector("slovid",
                new ConnectorHandler() {
                }, publishers
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
        connector.start();

    }


    public CustomDataPublisher(String broker_topic, String broker_ip, Integer broker_port, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {
        this(broker_topic,broker_ip,broker_port,brokerUsername,brokerPassword,amqLibraryConfigurationLocation, slovid_subscriber_key);
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame("Broker input app");
        JTextField broker_ipTextField = new JTextField("localhost", 30);
        JTextField broker_portTextField = new JTextField("5672", 20);
        JComboBox<String> TopicTextField = new JComboBox<>(new String[]{"eu.nebulouscloud.monitoring.slo.new","eu.nebulouscloud.monitoring.realtime.cpu_usage", "eu.nebulouscloud.monitoring.predicted.cpu_usage", "eu.nebulouscloud.monitoring.metric_list","eu.nebulouscloud.forecasting.start_forecasting.exponentialsmoothing",debug_data_trigger_topic_name});
        TopicTextField.setEditable(true);
        JTextField PublisherKeyTextField = new JTextField("slovid", 20);
        JTextField PublisherApplicationTextField = new JTextField(default_application_name, 20);
        JTextArea largeTextArea = new JTextArea(10, 30);
        JButton submitButton = new JButton("Send");

        AtomicReference<String> broker_ip = new AtomicReference<>();
        AtomicReference<Integer> broker_port = new AtomicReference<>();
        AtomicReference<String> broker_topic = new AtomicReference<>();
        AtomicReference<String> message_payload = new AtomicReference<>();
        AtomicReference<String> publisher_key = new AtomicReference<>();
        AtomicReference<String> publisher_app = new AtomicReference<>();

        TopicTextField.addActionListener(e -> {
            update_event_data();
            String selectedOption = (String) TopicTextField.getSelectedItem();
            String presetText = presetTexts.getOrDefault(selectedOption, "");
            largeTextArea.setText(presetText);
        });

        submitButton.addActionListener(e -> {
            broker_ip.set(broker_ipTextField.getText());
            broker_port.set(Integer.parseInt(broker_portTextField.getText()));
            broker_topic.set(TopicTextField.getSelectedItem().toString());
            message_payload.set(largeTextArea.getText());
            publisher_key.set(PublisherKeyTextField.getText());
            publisher_app.set(PublisherApplicationTextField.getText());
            CustomDataPublisher publisher = new CustomDataPublisher(broker_topic.toString(), broker_ip.toString(), broker_port.get(),"admin", "admin", EMPTY, publisher_key.toString());
            if (broker_topic.toString().equals(metric_list_topic)||broker_topic.toString().equals(slo_rules_topic)||broker_topic.toString().equals(debug_data_trigger_topic_name)||broker_topic.toString().equals(topic_for_lost_device_announcement)){
                publisher.publish(message_payload.toString(), String.valueOf(publisher_app)); //second argument was EMPTY
            }else{
                publisher.publish(message_payload.toString(), String.valueOf(publisher_app));
            }
        });

        JPanel broker_config_panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5); // This will add 5 pixels of space on all sides of each component

        int init_value_x =0;
        int init_value_y =0;

        c.gridx = init_value_x;
        c.gridy = init_value_y;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Broker to publish to:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(broker_ipTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Broker port to use:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(broker_portTextField, c);


        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Topic to publish to:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(TopicTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Key to publish with:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(PublisherKeyTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Application to publish with:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(PublisherApplicationTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Text to publish:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        broker_config_panel.add(new JScrollPane(largeTextArea), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0.0;
        c.weighty = 0.0;
        broker_config_panel.add(submitButton, c);

        frame.add(broker_config_panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    //TODO This assumes that the only content to be sent is json-like
    public void publish(String json_string_content, String application_name) {
        JSONParser parser = new JSONParser();
        JSONObject json_object = new JSONObject();
        try{
            json_object = (JSONObject) parser.parse(json_string_content);
        }catch (ParseException p){
            Logger.getGlobal().log(Level.SEVERE,"Could not parse the string content");
        }
        if (application_name!=null && !application_name.equals(EMPTY)){
            private_publisher_instance.send(json_object, application_name);
        }else {
            private_publisher_instance.send(json_object);
        }
    }
}
