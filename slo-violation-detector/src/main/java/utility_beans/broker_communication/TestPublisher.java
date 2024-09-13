package utility_beans.broker_communication;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.*;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.StaticExnConfig;
import org.apache.qpid.protonj2.client.Message;
import org.apache.qpid.protonj2.client.exceptions.ClientException;
import org.json.simple.JSONObject;

import java.util.List;
import java.util.Map;
import java.util.Objects;

class MyPublisher extends Publisher {

    public MyPublisher() {
        super("preferences", "preferences.changed", true);
    }

    public void send(){
        super.send(Map.of(
                "dark_mode",true
        ),"a");
    }
}


class MyPublisherTestConnectorHandler extends ConnectorHandler {


    @Override
    public void onReady(Context context) {
        System.out.println("Ready");


        /*
            Here we are checking to see if the default `state` publisher is
            available. Even though this should be already be known by the
            developer, a check never did any harm.

            The state publisher is a core publisher with the required
            methods to pubilsh component state.

            Calling these methods and bootstraing them into the application
            logic falls on the developer.

         */


        if(context.hasPublisher("state")){

            StatePublisher sp = (StatePublisher) context.getPublisher("state");

            sp.starting();
            sp.started();
            sp.custom("forecasting");
            sp.stopping();
            sp.stopped();

        }

        /**
         * This is an example of a default Publisher just sending an arbitrary message
         *
         */
        if(context.hasPublisher("config")) {

            (context.getPublisher("config")).send(
                    Map.of("hello","world"),
                    "one"
            );
            (context.getPublisher("config")).send(
                    Map.of("hello","world"),
                    "two"
            );

        }

        /**
         * This is an example of an extended publisher where the body of the message
         * is managed internally by the class
         */
        (context.getPublisher("preferences")).send();

    }

}

class TestPublisher{
    public static void main(String[] args) {
        try {
            Connector c1 = new Connector(
                    "ui",
                    new ConnectorHandler(){

                    },
                    List.of(),
                    List.of(
                            new Consumer("ui_all","eu.nebulouscloud.ui.preferences.>", new Handler(){
                                @Override
                                public void onMessage(String key, String address, Map body, Message rawMessage, Context context) {
                                    if(Objects.equals(key, "ui_all")){
                                        System.out.println("These are my preferences => "+ String.valueOf(body));
                                    }
                                }
                            },true,true),
                            new Consumer("config_one","config", new Handler(){
                                @Override
                                public void onMessage(String key, String address, Map body, Message rawMessage, Context context) {
                                    System.out.println("These are my ONE config => "+ String.valueOf(body));
                                }
                            },"one", true),
                            new Consumer("config_two","config", new Handler(){
                                @Override
                                public void onMessage(String key, String address, Map body, Message rawMessage, Context context) {

                                    System.out.println("These are my TWO config => "+ String.valueOf(body));
                                }
                            },"two", true)

                    ),
                    false,
                    false,
                    new StaticExnConfig(
                            "localhost",
                            5672,
                            "admin",
                            "admin"
                    )
            );
            c1.start();






            Publisher publisher = new Publisher("test", "config", true, true);
            Connector c = new Connector(

                    "ui",
                    new ConnectorHandler() {

                    },
                    List.of(
                            publisher
                    ),
                    List.of(),
                    true,
                    true,
                    new StaticExnConfig(
                            "localhost",
                            5672,
                            "admin",
                            "admin"
                    )

            );

            c.start();
            publisher.send(new JSONObject());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


}
