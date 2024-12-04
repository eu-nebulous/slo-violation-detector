package utility_beans.broker_communication;

import static configuration.Constants.EMPTY;
import static configuration.Constants.default_application_name;

public class BrokerSubscriptionDetails {
    String broker_username = "admin";
    String broker_password = "admin";
    String broker_ip = "localhost";
    int broker_port = 5672;
    String application_name = default_application_name;
    String topic = EMPTY;

    public BrokerSubscriptionDetails(String broker_ip, int broker_port, String broker_username, String broker_password, String application_name, String topic) {
        this.broker_ip = broker_ip;
        this.broker_port = broker_port;
        this.broker_username = broker_username;
        this.broker_password = broker_password;
        this.topic = topic;
        this.application_name = application_name;
    }

    public BrokerSubscriptionDetails(boolean fake_broker_subscription) {
        if (fake_broker_subscription) {
            this.broker_username = EMPTY;
            this.broker_password = EMPTY;
            this.broker_ip = EMPTY;
            this.topic = EMPTY;
            this.application_name = EMPTY;
        }
    }

    public String getBroker_username() {
        return broker_username;
    }

    public void setBroker_username(String broker_username) {
        this.broker_username = broker_username;
    }

    public String getBroker_password() {
        return broker_password;
    }

    public void setBroker_password(String broker_password) {
        this.broker_password = broker_password;
    }

    public String getBroker_ip() {
        return broker_ip;
    }

    public void setBroker_ip(String broker_ip) {
        this.broker_ip = broker_ip;
    }

    public String getApplication_name() {
        return application_name;
    }

    public void setApplication_name(String application_name) {
        this.application_name = application_name;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public int getBroker_port() {
        return broker_port;
    }

    public void setBroker_port(int broker_port) {
        this.broker_port = broker_port;
    }
}
