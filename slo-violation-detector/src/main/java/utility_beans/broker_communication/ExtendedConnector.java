package utility_beans.broker_communication;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.ExnConfig;
import org.apache.catalina.util.CustomObjectInputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

public class ExtendedConnector extends Connector {
    private String application_name;
    private CustomConnectorHandler handler;
    private List<Publisher> publishers;
    private List<Consumer> consumers;
    private Thread health_thread;

    private Connector connector;
    public ExtendedConnector(String component, ConnectorHandler handler, List<Publisher> publishers, List<Consumer> consumers, boolean enableState, boolean enableHealth, ExnConfig configuration) {
        super(component, handler, publishers, consumers, enableState, enableHealth, configuration);
        this.handler =(CustomConnectorHandler) handler;
        this.application_name = application_name;
        this.health_thread =new Thread(() -> {
           health_check(); 
        });
    }

    public ExtendedConnector(String application_name, String component, ConnectorHandler handler, List<Publisher> publishers, List<Consumer> consumers, boolean enableState, ExnConfig configuration) {
        super(component, handler, publishers, consumers, enableState, configuration);
        this.handler = (CustomConnectorHandler) handler;
        this.application_name = application_name;
        this.health_thread =new Thread(() -> {
            health_check();
        });
    }

    public ExtendedConnector(String application_name, String component, ConnectorHandler handler, List<Publisher> publishers, List<Consumer> consumers, ExnConfig configuration) {
        super(component, handler, publishers, consumers, configuration);
        this.application_name = application_name;
        this.handler = (CustomConnectorHandler) handler;
        this.health_thread =new Thread(() -> {
            health_check();
        });
    }

    private void health_check(){
        while (!Thread.interrupted()) {
            Context context = ((CustomConnectorHandler) handler).getContext();
            for (Consumer consumer : consumers) {
                if ((!context.hasConsumer(consumer.key())) || (context.getConsumer(consumer.key()) == null)) {
                    Logger.getGlobal().log(Level.SEVERE, "A consumer was found to be unexpectedly null, for topic " + consumer.key() + " and application " + application_name);
                }
            }
            for (Publisher publisher: publishers){
                if ((!context.hasPublisher(publisher.key())) || (context.getPublisher(publisher.key())==null)){
                    Logger.getGlobal().log(Level.SEVERE, "A publisher was found to be unexpectedly null for topic "+publisher.key()+" and application "+application_name);
                }
            }
            try {
                sleep(1000);
            } catch (InterruptedException e) {
                Logger.getGlobal().log(Level.SEVERE, "Health check thread interrupted");
            }
        }
    }
    
    public CustomConnectorHandler getHandler() {
        return (CustomConnectorHandler) handler;
    }

    public void setHandler(CustomConnectorHandler handler) {
        this.handler = handler;
    }

    public void remove_consumer_with_key(String key) {
        try {
            Context context = ((CustomConnectorHandler)handler).getContext();
            context.unregisterConsumer(key);
        }catch (ClassCastException c){
            Logger.getAnonymousLogger().log(Level.WARNING,"Could not unregister consumer, as the handler of the Connector it belongs to is not a CustomConnectorHandler");
        }
    }

    private void remove_publisher_with_key(String key) {
        try {
            Context context = ((CustomConnectorHandler)handler).getContext();
            context.unregisterPublisher(key);
        }catch (ClassCastException c){
            Logger.getAnonymousLogger().log(Level.WARNING,"Could not unregister consumer, as the handler of the Connector it belongs to is not a CustomConnectorHandler");
        }
    }

    public void add_consumer(Consumer newConsumer) {

        try {
            ((CustomConnectorHandler)handler).getContext().registerConsumer(newConsumer);
        }catch (ClassCastException c){
            Logger.getAnonymousLogger().log(Level.WARNING,"Could not register consumer, as the handler of the Connector it belongs to is not a CustomConnectorHandler");
        }
    }

    public void stop(ArrayList<Consumer> consumers, ArrayList <Publisher> publishers){
        if (consumers.size()>0) {
            stop_consumers(consumers);
        }
        if (publishers.size()>0) {
            stop_publishers(publishers);
        }
        health_thread.interrupt();
        Context context = ((CustomConnectorHandler)handler).getContext();
        try {
            context.stop();
            this.stop();
            Logger.getAnonymousLogger().log(Level.INFO,"Successfully stopped the ExtendedConnector");
        }catch (Exception e){
            Logger.getAnonymousLogger().log(Level.WARNING,"There was an issue while trying to stop an ExtendedConnector");
        }
    }


    public void stop_consumers(ArrayList<Consumer> consumers){
        for (Consumer consumer : consumers){
            remove_consumer_with_key(consumer.key());
        }
    }
    public void stop_publishers(ArrayList<Publisher> publishers){
        for (Publisher publisher : publishers){
            remove_publisher_with_key(publisher.key());
        }
    }

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }

}
