/*
 * Copyright (c) 2023 Institute of Communication and Computer Systems
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

//import eu.melodic.event.brokerclient.BrokerPublisher;
//import eu.melodic.event.brokerclient.BrokerSubscriber;
import utility_beans.broker_communication.BrokerPublisher;
import utility_beans.broker_communication.BrokerSubscriber;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.junit.Test;
import utility_beans.broker_communication.BrokerSubscriptionDetails;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Collections;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import static configuration.Constants.*;


public class ConnectivityTests {

    private Boolean connectivity_result_success;
        @Test
        public void test_broker_connectivity() throws IOException {

            Properties prop = new Properties();

            URI absolute_configuration_file_path = new File(configuration_file_location).toURI();
            base_project_path = new File("").toURI();
            URI relative_path  = base_project_path.relativize(absolute_configuration_file_path);

            InputStream inputStream = new FileInputStream(base_project_path.getPath()+relative_path.getPath());

            prop.load(inputStream);

            BrokerPublisher publisher = new BrokerPublisher("test_topic",prop.getProperty("broker_ip_url"), Integer.parseInt(prop.getProperty("broker_port")),prop.getProperty("broker_username"),prop.getProperty("broker_password"), amq_library_configuration_location);

            BrokerSubscriber subscriber = new BrokerSubscriber("test_topic",prop.getProperty("broker_ip_url"), Integer.parseInt(prop.getProperty("broker_port")),prop.getProperty("broker_username"),prop.getProperty("broker_password"),amq_library_configuration_location,default_application_name);

            JSONObject object_to_publish = new JSONObject();
            object_to_publish.put("ram","95");
            object_to_publish.put("cpu","99");

            BiFunction<BrokerSubscriptionDetails,String,String> slo_function = (broker_subscription_details, message)->{
                String topic = broker_subscription_details.getTopic();
                Double cpu_slo_limit = 70.0;
                Double ram_slo_limit = 60.0;
                Boolean return_value = false;
                try {
                    Logger.getGlobal().log(info_logging_level,"Received " + message);
                    JSONObject rules_json = (JSONObject) new JSONParser().parse(message);
                    Double ram_value = Double.parseDouble(rules_json.get("ram").toString());
                    Double cpu_value = Double.parseDouble(rules_json.get("cpu").toString());
                    return_value = (ram_value>ram_slo_limit && cpu_value>cpu_slo_limit);
                } catch (ParseException e) {
                    e.printStackTrace();
                }
                connectivity_result_success  = return_value;
                return return_value.toString();
            };

            Thread subscription_thread = new Thread(() -> {
                subscriber.subscribe(slo_function,default_application_name); //will be a short-lived test, so setting stop signal to false
            });
            subscription_thread.start();
            Logger.getAnonymousLogger().log(Level.INFO,"Waiting 2 seconds before publishing message to broker");
            try {
                Thread.sleep(2000);
            }catch (InterruptedException i){
                i.printStackTrace();
            }
            publisher.publish(object_to_publish.toJSONString(), Collections.singleton(default_application_name));
            Logger.getAnonymousLogger().log(Level.INFO,"Published message "+object_to_publish.toJSONString());
            try {
                Thread.sleep(2000);
            }catch (InterruptedException i){
                i.printStackTrace();
            }
            assert connectivity_result_success;

        }

}
