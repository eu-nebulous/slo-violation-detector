package utility_beans;

import eu.nebulouscloud.exn.Connector;
import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Handler;
import eu.nebulouscloud.exn.core.Publisher;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import eu.nebulouscloud.exn.settings.ExnConfig;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ExtendedConnector extends Connector {
    private ConnectorHandler handler;

    private Connector connector;
    public ExtendedConnector(String component, ConnectorHandler handler, List<Publisher> publishers, List<Consumer> consumers, boolean enableState, boolean enableHealth, ExnConfig configuration) {
        super(component, handler, publishers, consumers, enableState, enableHealth, configuration);
        this.handler = handler;
    }

    public ExtendedConnector(String component, ConnectorHandler handler, List<Publisher> publishers, List<Consumer> consumers, boolean enableState, ExnConfig configuration) {
        super(component, handler, publishers, consumers, enableState, configuration);
        this.handler = handler;
    }

    public ExtendedConnector(String component, ConnectorHandler handler, List<Publisher> publishers, List<Consumer> consumers, ExnConfig configuration) {
        super(component, handler, publishers, consumers, configuration);
        this.handler = handler;
    }

    public ConnectorHandler getHandler() {
        return handler;
    }

    public void setHandler(ConnectorHandler handler) {
        this.handler = handler;
    }

    public void remove_consumer_with_key(String key) {
        try {
            ((CustomConnectorHandler)handler).getContext().unregisterConsumer(key);
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

    public Connector getConnector() {
        return connector;
    }

    public void setConnector(Connector connector) {
        this.connector = connector;
    }
}
