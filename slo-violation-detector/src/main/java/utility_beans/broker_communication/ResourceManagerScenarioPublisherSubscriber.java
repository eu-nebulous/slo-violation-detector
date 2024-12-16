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

import static configuration.Constants.EMPTY;
import static java.lang.Thread.sleep;

public class ResourceManagerScenarioPublisherSubscriber {

    private static class MyConsumerHandler extends Handler{
            @Override
            public void onMessage(String key, String address, Map body, Message message, Context context) {
                flag =10;
                System.out.println("Received a message on topic "+address+" with the following body\n"+body.toString());
                if (key.equals("testkey_message1")){
                    handler.start_publishing();
                }
                System.out.println("Published special message");
            }
    }


    private static class AlternativeHandler extends Handler{
        @Override
        public void onMessage(String key, String address, Map body, Message message, Context context) {
            flag =10;
            System.out.println("Received an ALTERNATIVE message on topic "+address+" with the following body\n"+body.toString());
        }
    }
    private final static String nonce = "01313343343"; 
    public static int flag = 0;
    public static Consumer first_message_consumer = new Consumer("testkey_message1", "eu.nebulouscloud.ui.user.get", new MyConsumerHandler() , true,true);
    public static Consumer second_message_consumer = new Consumer("testkey_message2", "eu.nebulouscloud.ui.user.get."+nonce, new MyConsumerHandler(), true,true);
    private static List<Publisher> publisher_list = new ArrayList<>(Arrays.asList(new Publisher("first_message", "eu.nebulouscloud.ui.user.get", true,true), new Publisher("second_message", "eu.nebulouscloud.ui.user.get."+nonce,true,true)));
    private static ArrayList<Consumer> consumer_list = new ArrayList<>(Arrays.asList(first_message_consumer,second_message_consumer));

    private static class ExtendedHandler extends ConnectorHandler{
        private boolean ready = false;
        private Context context;
        @Override
        public void onReady(Context context) {
        System.out.println("Onready");
        ready = true;
        this.context = context;
        }

        public void start_publishing(){
    
            
            HashMap<String,Object> message = new HashMap<>();
            message.put("username","andreas");
    
            HashMap<String,String> organization_map = new HashMap<>();
            organization_map.put("name","nebulous_org");
            organization_map.put("uuid","15AFDD15F34567890");
    
            message.put("organization", organization_map);
            message.put("role","admin");
    
            while(!ready){
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            context.getPublisher("second_message").send(message);
        }
    }

    private static ExtendedHandler handler;





    public static void main(String[] args) throws InterruptedException {

        handler = new ExtendedHandler();
        //consumer_list.add(new Consumer("", "", new Handler() {}));
        Connector connector = new Connector("testcomponent",
                handler, publisher_list
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
        message.put("nonce",nonce);
        message.put("appId","_Application1");

        handler.start_publishing();
        publisher_list.get(0).send(message);
        System.out.println("Sent message "+message);
        //System.out.println(flag);
        //System.out.println("Phase 2");
    }
}
