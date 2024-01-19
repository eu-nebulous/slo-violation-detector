package utility_beans.broker_communication;

import eu.nebulouscloud.exn.core.Consumer;
import eu.nebulouscloud.exn.core.Context;
import eu.nebulouscloud.exn.handlers.ConnectorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomConnectorHandler extends ConnectorHandler {
    private Context context;

    @Override
    public void onReady(Context context) {
        this.context = context;
    }
    public void remove_consumer_with_key(String key){
        context.unregisterConsumer(key);
    }

    public Context getContext() {
        return context;
    }

    public void setContext(Context context) {
        this.context = context;
    }
}
