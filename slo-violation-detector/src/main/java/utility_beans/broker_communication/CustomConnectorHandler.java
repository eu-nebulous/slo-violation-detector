package utility_beans.broker_communication;

import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public class CustomConnectorHandler extends ConnectorHandler {
    private Context context;
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Override
    public void onReady(Context context) {
        synchronized (ready){
            ready.set(true);
            ready.notifyAll();
        }
        this.context = context;
    }
    public void remove_consumer_with_key(String key){
        context.unregisterConsumer(key);
    }
    public void add_consumer(Consumer consumer){
        context.registerConsumer(consumer);
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }
    public AtomicBoolean getReadiness(){
        return ready;
    }
}
