package utility_beans.broker_communication;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.apache.qpid.protonj2.client.Message;


import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static configuration.Constants.EMPTY;
import static java.lang.Thread.sleep;

public class PublisherSubscriber {

    private static class MyConsumerHandler extends Handler{
            @Override
            public void onMessage(String key, String address, Map body, Message message, Context context) {
                flag =10;
                System.out.println("Received a message on topic "+address+" with the following body\n"+body.toString());
            }
    }


    private static class AlternativeHandler extends Handler{
        @Override
        public void onMessage(String key, String address, Map body, Message message, Context context) {
            flag =10;
            System.out.println("Received an ALTERNATIVE message on topic "+address+" with the following body\n"+body.toString());
        }
    }
    public static int flag = 0;
    public static Consumer original_consumer = new Consumer("testkey", "test_topic", new MyConsumerHandler() , true);
    private static List<Publisher> publisher_list = new ArrayList<>(Arrays.asList(new Publisher("testkey", "test_topic", true)));
    private static ArrayList<Consumer> consumer_list = new ArrayList<>(Arrays.asList(original_consumer));
    public static void main(String[] args) throws InterruptedException {

        consumer_list.add(new Consumer("", "", new Handler() {}));
        Connector connector = new Connector("slovid",
                new ConnectorHandler() {
                }, publisher_list
                , consumer_list,
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
        connector.start();
        sleep(1000);

        System.out.println("Phase 1");
        Map<String,String> message = new HashMap<>();
        message.put("entry","test_message");

        publisher_list.get(0).send(message);
        System.out.println("Sent message "+message);
        System.out.println(flag);
        System.out.println("Phase 2");

        sleep(1000);
        consumer_list.add(new Consumer("testkey","newtopic",new MyConsumerHandler()));
        publisher_list.add(new Publisher("testkey","newtopic",true));
        System.out.println("Phase 3");
        sleep(1000);

        //The test below demonstrates it is not possible to add a new publisher at runtime
        try {
            message.clear();
            message.put("newentry", "new test message");
            System.out.println(publisher_list.size());
            publisher_list.get(1).send(message);
            sleep(1000);
        }catch (Exception e){
            System.out.println("Exception caught "+e);
        }
        System.out.println("Phase 4");

        //The test below tries to change the logic of the handling of a rule at runtime
        original_consumer = new Consumer("testkey","test_topic",new AlternativeHandler());
        publisher_list.get(0).send(message);
    }
}
