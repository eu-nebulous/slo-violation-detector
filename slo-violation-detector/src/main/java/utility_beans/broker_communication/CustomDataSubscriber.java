package utility_beans.broker_communication;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import utility_beans.synchronization.SynchronizedBoolean;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static configuration.Constants.*;
import static utilities.DebugDataSubscription.debug_data_trigger_topic_name;

public class CustomDataSubscriber {
    private static final Map<String, String> presetTexts = new HashMap<>();

    private static final SynchronizedBoolean new_message_arrived = new SynchronizedBoolean();
    private static AtomicReference<String> message_payload = new AtomicReference<>(EMPTY);

    static class SimpleConnectorHandler extends CustomConnectorHandler{
        private String broker_topic;
        private String broker_ip;

        private String application_name;

        Handler on_message_handler;
        SimpleConnectorHandler(String broker_ip,String broker_topic, String application_name,boolean log_and_notify_messages){
            this.broker_topic = broker_topic;
            this.broker_ip = broker_ip;
            this.application_name = application_name;

            if(log_and_notify_messages) {
                this.on_message_handler = new Handler() {
                    @Override
                    public void onMessage(String key, String address, Map body, Message message, Context context) {
                        Logger.getGlobal().log(info_logging_level, "Received message with payload" + body.toString() + "\n");
                        synchronized (new_message_arrived) {
                            new_message_arrived.notify();
                            new_message_arrived.setValue(true);
                            message_payload = new AtomicReference<>(body.toString());
                            //message_payload =  body.toString();
                        }
                    }
                };
            }else{
                this.on_message_handler = new Handler() {
                    @Override
                    public void onMessage(String key, String address, Map body, Message message, Context context) {
                        
                    }
                };
            }

        }
        @Override
        public void onReady(Context context) {
            super.onReady(context);
            Consumer consumer = new Consumer(slovid_subscriber_key,broker_topic,on_message_handler,
                    application_name,true,true);
            Logger.getGlobal().log(info_logging_level,"Unregistering old consumer");
            //context.unregisterConsumer(slovid_subscriber_key);
            Logger.getGlobal().log(info_logging_level,"Registering new consumer");
            context.registerConsumer(consumer);
        }
    }

    static {
       update_event_data();
    }

    private static SynchronizedBoolean notify_create_subscriber = new SynchronizedBoolean(false);
    private static void update_event_data(){
        presetTexts.put(debug_data_trigger_topic_name,"{}");
    }
    private static Connector private_connector;
    private static CustomConnectorHandler private_connector_handler;

    private void stop_connector(){
        try {
            private_connector.stop();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    public CustomDataSubscriber(String broker_topic, String broker_ip, int broker_port, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation, String subscriber_key, String application_name) {

        private_connector_handler = new SimpleConnectorHandler(broker_ip, broker_topic, application_name,true);
        private_connector = new Connector("slovid",
                private_connector_handler,
                List.of()
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
        Logger.getGlobal().log(info_logging_level,"Starting private connector");
        private_connector.start();
    }


    public static void main(String[] args) {
        AtomicReference<CustomDataSubscriber> subscriber = new AtomicReference<>();
        AtomicReference<String> broker_ip = new AtomicReference<>();
        AtomicReference<String> broker_topic = new AtomicReference<>();
        AtomicReference<Integer> broker_port = new AtomicReference<>();
        AtomicReference<String> subscriber_key = new AtomicReference<>();
        AtomicReference<String> subscriber_application = new AtomicReference<>();

        AtomicBoolean consumer_has_started = new AtomicBoolean(false);

        broker_topic.set("eu.nebulouscloud.monitoring.slo.nef");
        broker_ip.set("localhost");
        subscriber_key.set("test_subscriber");
        subscriber_application.set("test_application");
        Thread t = new Thread(){
            @Override
            public void run() {
                if (subscriber.get()!=null){
                    subscriber.get().stop_connector();
                }
                subscriber.set(new CustomDataSubscriber(broker_topic.toString(), broker_ip.toString(), 5672,"admin", "admin", EMPTY, subscriber_key.toString(), subscriber_application.toString()));
                CustomDataSubscriber.private_connector.start();
            }
        };
        //t.start();



        /*
        class MyConnectorHandler extends ConnectorHandler{
            @Override
            public void onReady(Context context) {
                Logger.getGlobal().log(info_logging_level,"Starting the connector handler");
            }
        }

        Connector private_connector = new Connector("slovid",
                new MyConnectorHandler(){

                },List.of(),
                List.of(
                        new Consumer("localhost", "eu.nebulouscloud.monitoring.slo.new", new Handler() {
                            @Override
                            public void onMessage(String key, String address, Map body, Message message, Context context) {
                                super.onMessage(key, address, body, message, context);
                                Logger.getGlobal().log(info_logging_level,"A message, "+body.toString()+" has arrived at address "+address);
                            }
                        },true,true)
                )
                ,
                false,
                false,
                new StaticExnConfig(
                        "localhost",
                        5672,
                        "admin",
                        "admin",
                        60,
                        EMPTY
                )
        );
        Logger.getGlobal().log(info_logging_level,"Starting private connector");
        private_connector.start();
*/



        JFrame frame = new JFrame("Broker subscriber app");
        JTextField broker_ipTextField = new JTextField("localhost", 30);
        //JComboBox<String> TopicTextField = new JComboBox<>(new String[]{"eu.nebulouscloud.monitoring.slo.new","eu.nebulouscloud.monitoring.realtime.cpu_usage", "eu.nebulouscloud.monitoring.predicted.cpu_usage", "eu.nebulouscloud.monitoring.metric_list","eu.nebulouscloud.forecasting.start_forecasting.exponentialsmoothing",debug_data_trigger_topic_name});
        //TopicTextField.setEditable(true);
        JTextField TopicTextField = new JTextField("",30);
        JTextField ConsumerKeyTextField = new JTextField("slovid", 20);
        JTextField broker_portTextField = new JTextField("5672",20);
        JTextField ConsumerApplicationTextField = new JTextField(default_application_name, 20);

        JTextArea subscriptionTextArea = new JTextArea(10, 30);
        subscriptionTextArea.setBackground(Color.LIGHT_GRAY);
        subscriptionTextArea.setForeground(Color.BLUE);
        subscriptionTextArea.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
        subscriptionTextArea.setWrapStyleWord(true);

        //Topic listing functionality, unavailable
        /*
        JTextArea topic_list_TextArea = new JTextArea(10, 30);
        subscriptionTextArea.setBackground(Color.LIGHT_GRAY);
        subscriptionTextArea.setForeground(Color.BLUE);
        subscriptionTextArea.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 2));
        subscriptionTextArea.setWrapStyleWord(true);
        */

        JButton subscribeButton = new JButton("Start subscription");
        //JButton get_topic_listButton = new JButton("Refresh topic list");

        /*get_topic_listButton.addActionListener(e -> {
            private_connector.getHandler().ctx.
        });*/

        subscribeButton.addActionListener(e -> {
            broker_ip.set(broker_ipTextField.getText());
            broker_topic.set(TopicTextField.getText());
            broker_port.set(Integer.parseInt(broker_portTextField.getText()));
            subscriber_key.set(ConsumerKeyTextField.getText());
            subscriber_application.set(ConsumerApplicationTextField.getText());
            //notify_create_subscriber.notify();


            if (consumer_has_started.get()) {
                Logger.getGlobal().log(info_logging_level,"Removing consumer");
                private_connector_handler.remove_consumer_with_key(subscriber_key.get());
                Logger.getGlobal().log(info_logging_level,"Consumer should be stopped now");
                Consumer consumer;
                if (subscriber_application.get()!=null && !subscriber_application.get().isEmpty()) {
                    Logger.getGlobal().log(info_logging_level, "The application for which the subscriber is created is the following " + subscriber_application.get());
                    consumer = new Consumer(subscriber_key.get(), broker_topic.get(), new Handler() {
                        @Override
                        public void onMessage(String key, String address, Map body, Message message, Context context) {
                            super.onMessage(key, address, body, message, context);
                            String message_body,message_topic;
                            try {
                                if (body!=null){
                                    Logger.getGlobal().log(info_logging_level, "A message, " + body.toString() + " has arrived at address " + message.to() + " for application " + message.subject());
                                    message_body = body.toString();
                                    message_topic = message.to();
                                }else{
                                    Logger.getGlobal().log(info_logging_level, "A message with a null body has arrived at address " + message.to() + " for application " + message.subject());
                                    message_body = "";
                                    message_topic = message.to();
                                }
                            } catch (Exception ex) {
                                Logger.getGlobal().log(warning_logging_level,"The failing address is "+address);
                                throw new RuntimeException(ex);
                            }
                            String old_text = subscriptionTextArea.getText() + "\n";
                            subscriptionTextArea.setText(old_text + message_topic + ":" + message_body);
                        }
                    }, subscriber_application.get(), true, true);
                }
                else{
                    Logger.getGlobal().log(info_logging_level, "Subscribing for all applications");
                    consumer = new Consumer(subscriber_key.get(), broker_topic.get(), new Handler() {
                        @Override
                        public void onMessage(String key, String address, Map body, Message message, Context context) {
                            super.onMessage(key, address, body, message, context);
                            String message_body,message_topic;
                            try {
                                if (body!=null){
                                    Logger.getGlobal().log(info_logging_level, "A message, " + body.toString() + " has arrived at address " + message.to() + " for application " + message.subject());
                                    message_body = body.toString();
                                    message_topic = message.to();
                                }else{
                                    Logger.getGlobal().log(info_logging_level, "A message with a null body has arrived at address " + message.to() + " for application " + message.subject());
                                    message_body = "";
                                    message_topic = message.to();
                                }
                            } catch (Exception ex) {
                                Logger.getGlobal().log(warning_logging_level,"The failing address is "+address);
                                throw new RuntimeException(ex);
                            }
                            String old_text = subscriptionTextArea.getText() + "\n";
                            subscriptionTextArea.setText(old_text + message_topic + ":" + message_body);
                        }
                    }, true, true);
                }
                private_connector_handler.add_consumer(consumer);
            }
            else {
                if (!subscriber_application.get().isEmpty()) {
                    Logger.getGlobal().log(info_logging_level, "Starting the connector for the consumption of messages with " + subscriber_key + " at " + broker_topic.get() + " for application " + subscriber_application.get());
                    consumer_has_started.set(true);
                    private_connector_handler = new CustomConnectorHandler() {
                        private Consumer current_consumer = null;
                        AtomicBoolean consumer_has_started = new AtomicBoolean(false);

                        @Override
                        public void onReady(Context context) {
                            super.onReady(context);
                            Logger.getGlobal().log(info_logging_level, "On ready activated");
                            if (current_consumer != null) {
                                context.unregisterConsumer(current_consumer.key());
                                Logger.getGlobal().log(info_logging_level, "Unregistering consumer");
                            }
                            Consumer consumer = new Consumer(subscriber_key.get(), broker_topic.get(), new Handler() {
                                @Override
                                public void onMessage(String key, String address, Map body, Message message, Context context) {
                                    super.onMessage(key, address, body, message, context);
                                    String message_body,message_topic;
                                    try {
                                        if (body!=null){
                                            Logger.getGlobal().log(info_logging_level, "A message, " + body.toString() + " has arrived at address " + message.to() + " for application " + message.subject());
                                            message_body = body.toString();
                                            message_topic = message.to();
                                        }else{
                                            Logger.getGlobal().log(info_logging_level, "A message with a null body has arrived at address " + message.to() + " for application " + message.subject());
                                            message_body = "";
                                            message_topic = message.to();
                                        }
                                    } catch (Exception ex) {
                                        Logger.getGlobal().log(warning_logging_level,"The failing address is "+address);
                                        throw new RuntimeException(ex);
                                    }
                                    String old_text = subscriptionTextArea.getText() + "\n";
                                    subscriptionTextArea.setText(old_text + message_topic + ":" + message_body);
                                }
                            }, subscriber_application.get(), true, true);
                            context.registerConsumer(consumer);
                        }
                    };
                    private_connector = new Connector("slovid",private_connector_handler
                            , List.of(),
                            List.of(
                            )
                            ,
                            false,
                            false,
                            new StaticExnConfig(
                                    broker_ip.get(),
                                    broker_port.get(),
                                    "admin",
                                    "admin",
                                    60,
                                    EMPTY
                            )
                    );
                }else {
                    if (!subscriber_application.get().isEmpty()) {
                        Logger.getGlobal().log(info_logging_level, "Starting the connector for the consumption of messages with " + subscriber_key + " at " + broker_topic.get() + " for application "+subscriber_application.get());
                        consumer_has_started.set(true);
                        private_connector_handler = new SimpleConnectorHandler(broker_ip.get(),broker_topic.get(),subscriber_application.get(),true) {
                            private Consumer current_consumer = null;
                            AtomicBoolean consumer_has_started = new AtomicBoolean(false);

                            @Override
                            public void onReady(Context context) {
                                super.onReady(context);
                                Logger.getGlobal().log(info_logging_level, "On ready activated");
                                if (current_consumer != null) {
                                    context.unregisterConsumer(current_consumer.key());
                                    Logger.getGlobal().log(info_logging_level, "Unregistering consumer");
                                }
                                Consumer consumer = new Consumer(subscriber_key.get(), broker_topic.get(), new Handler() {
                                    @Override
                                    public void onMessage(String key, String address, Map body, Message message, Context context) {
                                        super.onMessage(key, address, body, message, context);
                                        String message_topic,message_body;

                                        try {
                                            if (body!=null){
                                                Logger.getGlobal().log(info_logging_level, "A message, " + body.toString() + " has arrived at address " + message.to() + " for application " + message.subject());
                                                message_body = body.toString();
                                                message_topic = message.to();
                                            }else{
                                                Logger.getGlobal().log(info_logging_level, "A message with a null body has arrived at address " + message.to() + " for application " + message.subject());
                                                message_body = "";
                                                message_topic = message.to();
                                            }
                                        } catch (Exception ex) {
                                            Logger.getGlobal().log(warning_logging_level,"The failing address is "+address);
                                            throw new RuntimeException(ex);
                                        }
                                        String old_text = subscriptionTextArea.getText() + "\n";
                                        subscriptionTextArea.setText(old_text + message_topic + ":" + message_body);
                                    }
                                }, subscriber_application.get(), true, true);
                                context.registerConsumer(consumer);
                            }
                        };
                        private_connector = new Connector("slovid",private_connector_handler
                                , List.of(),
                                List.of(
                                )
                                ,
                                false,
                                false,
                                new StaticExnConfig(
                                        broker_ip.get(),
                                        broker_port.get(),
                                        "admin",
                                        "admin",
                                        60,
                                        EMPTY
                                )
                        );
                    }else{
                        Logger.getGlobal().log(info_logging_level, "Starting the connector for the consumption of messages with " + subscriber_key + " at " + broker_topic.get() + " for all applications");
                        consumer_has_started.set(true);
                        private_connector_handler = new CustomConnectorHandler() {
                            private Consumer current_consumer = null;
                            AtomicBoolean consumer_has_started = new AtomicBoolean(false);

                            @Override
                            public void onReady(Context context) {
                                super.onReady(context);
                                Logger.getGlobal().log(info_logging_level, "On ready activated");
                                if (current_consumer != null) {
                                    context.unregisterConsumer(current_consumer.key());
                                    Logger.getGlobal().log(info_logging_level, "Unregistering consumer");
                                }
                                Consumer consumer = new Consumer(subscriber_key.get(), broker_topic.get(), new Handler() {
                                    @Override
                                    public void onMessage(String key, String address, Map body, Message message, Context context) {
                                        super.onMessage(key, address, body, message, context);
                                        String message_body;
                                        String message_topic;
                                        try {
                                            if (body!=null){
                                                Logger.getGlobal().log(info_logging_level, "A message, " + body + " has arrived at address " + message.to() + " for application " + message.subject());
                                                message_body = body.toString();
                                                message_topic = message.to();
                                            }else{
                                                Logger.getGlobal().log(info_logging_level, "A message with a null body has arrived at address " + message.to() + " for application " + message.subject());
                                                message_body = "";
                                                message_topic = message.to();
                                            }
  
                                        } catch (Exception ex) {
                                            try {
                                                Logger.getGlobal().log(warning_logging_level,"The failing address is "+message.to());
                                            } catch (ClientException exc) {
                                                throw new RuntimeException(exc);
                                            }
                                            throw new RuntimeException(ex);
                                        }
                                        String old_text = subscriptionTextArea.getText() + "\n";
                                        subscriptionTextArea.setText(old_text + message_topic + ":" + message_body);
                                    }
                                }, true, true);
                                context.registerConsumer(consumer);
                            }
                        };
                        private_connector = new Connector("slovid",private_connector_handler
                                , List.of(),
                                List.of(
                                )
                                ,
                                false,
                                false,
                                new StaticExnConfig(
                                        broker_ip.get(),
                                        broker_port.get(),
                                        "admin",
                                        "admin",
                                        60,
                                        EMPTY
                                )
                        );
                    }
                }
                Logger.getGlobal().log(info_logging_level, "Starting private connector");
                private_connector.start();
            }
        });

        JPanel broker_config_panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(5, 5, 5, 5); // This will add 5 pixels of space on all sides of each component

        int init_value_x=0;
        int init_value_y=0;

        c.gridx = init_value_x;
        c.gridy = init_value_y;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Broker to subscribe to:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(broker_ipTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Topic to subscribe to:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(TopicTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Port to subscribe at:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(broker_portTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Key to subscribe with:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(ConsumerKeyTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Application to subscribe for:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        broker_config_panel.add(ConsumerApplicationTextField, c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Received text:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        broker_config_panel.add(new JScrollPane(subscriptionTextArea), c);
        /*
        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        broker_config_panel.add(new JLabel("Topic list:"), c);

        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_START;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        c.weightx = 1.0;
        c.weighty = 1.0;
        broker_config_panel.add(new JScrollPane(topic_list_TextArea), c);

        */


        c.gridx = (++init_value_x)%2;
        c.gridy = (++init_value_y)/2;
        c.anchor = GridBagConstraints.LINE_END;
        c.gridwidth = 1;
        c.fill = GridBagConstraints.NONE;
        c.weightx = 0.0;
        c.weighty = 0.0;
        broker_config_panel.add(subscribeButton, c);

        frame.add(broker_config_panel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);

        synchronized (new_message_arrived){
            while(!new_message_arrived.getValue()){
                try {
                    new_message_arrived.wait();
                } catch (InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
                new_message_arrived.setValue(false);
                String older_text = subscriptionTextArea.getText();
                subscriptionTextArea.setText(older_text+"\n"+String.valueOf(message_payload));
            }
        }
    }
}
