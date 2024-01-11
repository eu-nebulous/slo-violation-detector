package utility_beans;

import static configuration.Constants.EMPTY;

public class BrokerSubscriptionDetails {
    String broker_username = "admin";
    String broker_password = "admin";
    String broker_ip = "localhost";

    public BrokerSubscriptionDetails(String broker_ip, String broker_username, String broker_password) {
        this.broker_ip = broker_ip;
        this.broker_username = broker_username;
        this.broker_password = broker_password;
    }

    public BrokerSubscriptionDetails(boolean fake_broker_subscription) {
        if (fake_broker_subscription) {
            this.broker_username = EMPTY;
            this.broker_password = EMPTY;
            this.broker_ip = EMPTY;
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
}
