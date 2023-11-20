package utility_beans;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

public class BrokerSubscriber {
    public BrokerSubscriber(String testTopic, String brokerIpUrl, String brokerUsername, String brokerPassword, String amqLibraryConfigurationLocation) {

    }

    public void subscribe(BiFunction<String, String, String> function, AtomicBoolean atomicBoolean) {
    }

    public enum EventFields{
        ;

        public enum PredictionMetricEventFields {timestamp, prediction_time, probability, metric_value, confidence_interval}
    }
    public static class TopicNames{
        public static String realtime_metric_values_topic(String metric) {
            return null;
        }

        public static String final_metric_predictions_topic(String metric) {
            return null;
        }
    }
}
