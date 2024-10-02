package utility_beans.broker_communication;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;

public class MultiDataPublisher {

    private Publisher private_publisher_instance;

    /**
     * This class is appropriate to send a number of events to a desired topic
     * @param broker_topic The desired broker topic
     * @param broker_ip The desired broker ip
     * @param broker_port The desired broker port
     * @param brokerUsername The broker username
     * @param brokerPassword The broker password
     * @param amqLibraryConfigurationLocation The configuration location for the amqp library
     * @param publisher_key The publisher key to be used
     */
    public MultiDataPublisher(String broker_topic, String broker_ip, Integer broker_port, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation, String publisher_key) {

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



    public static void main(String[] args) throws InterruptedException {

        AtomicReference<String> broker_ip = new AtomicReference<>("localhost");
        AtomicReference<String> broker_port = new AtomicReference<>("5672");
        AtomicInteger message_count = new AtomicInteger(100);
        AtomicBoolean submit_button_pressed = new AtomicBoolean(false);
        int min_bound=0;
        int max_bound=100;
        AtomicInteger interval_between_events=new AtomicInteger(1);
        AtomicReference<Double> random_value = new AtomicReference<>();
        AtomicReference<Double> upper_bound = new AtomicReference<>();
        AtomicReference<Double> lower_bound = new AtomicReference<>();

        AtomicReference<String> application_name = new AtomicReference<>("");
        AtomicReference<String> broker_topic = new AtomicReference<>("eu.nebulouscloud.monitoring.realtime.cpu_usage");

        JFrame frame = new JFrame("Data Input Form");
        frame.setLayout(new GridLayout(9, 2, 5, 5));
        String[] labels = {"Broker IP", "Broker Port", "Repetitions", "Min Bound", "Max Bound", "Interval between events","Broker Topic", "Application Name"};
        JTextField[] fields = {
                new JTextField("localhost"),
                new JTextField("5672"),
                new JTextField("100"),
                new JTextField("0"),
                new JTextField("100"),
                new JTextField("1"),
                new JTextField("eu.nebulouscloud.monitoring.realtime.cpu_usage"),
                new JTextField("_Application1")
        };

        for (int i = 0; i < labels.length; i++) {
            frame.add(new JLabel(labels[i]));
            frame.add(fields[i]);
        }

        JButton submitButton = new JButton("Submit");
        submitButton.addActionListener((ActionEvent e) -> {
            broker_ip.set(fields[0].getText());
            broker_port.set(fields[1].getText());
            message_count.set(Integer.parseInt(fields[2].getText()));
            upper_bound.set( Double.parseDouble(fields[4].getText()));
            lower_bound.set(Double.parseDouble(fields[3].getText()));
            interval_between_events.set(Integer.parseInt(fields[5].getText()));
            broker_topic.set(fields[6].getText());
            application_name.set(fields[7].getText());

            submit_button_pressed.set(true);
        });

        frame.add(submitButton);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setPreferredSize(new Dimension(400, 300));
        frame.pack();
        frame.setVisible(true);
        while (true){
            if (submit_button_pressed.get()) {
                CustomDataPublisher publisher = new CustomDataPublisher(broker_topic.toString(), broker_ip.toString(), Integer.parseInt(broker_port.get()), "admin", "admin", EMPTY, "demo_batch_publisher");

                String message_payload;
                for (int i = 0; i < message_count.get(); i++) {
                    random_value.set(new Random().nextDouble() * (upper_bound.get() - lower_bound.get()) + lower_bound.get());
                    message_payload = "{\n" +
                            "    \"metricValue\": " + random_value + ",\n" +
                            "    \"level\": 1,\n" +
                            "    \"component_id\":\"postgresql_1\",\n" +
                            "    \"timestamp\": " + (System.currentTimeMillis()) + "\n" +
                            "}\n";
                    publisher.publish(message_payload.toString(), application_name.get()); //second argument was EMPTY
                    Thread.sleep(interval_between_events.get() * 1000);
                }
            }
            Thread.sleep(interval_between_events.get() * 1000);
        }
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