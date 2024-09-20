package utility_beans.broker_communication;

import static configuration.Constants.default_application_name;

public class PredictedEventPublisher extends SingleEventPublisher {
    
    String broker_ip="nebulous-activemq";
    String broker_port="5672";
    CustomDataPublisher publisher;
    public PredictedEventPublisher(String topic, String value) {
        payload = "{\n" +
                "    \"metricValue\": "+value+",\n" +
                "    \"level\": 1,\n" +
                "    \"timestamp\": " + (System.currentTimeMillis()) + "\n" +
                "    \"probability\": 0.98,\n" +
                "    \"confidence_interval\" : [8,15]\n" +
                "    \"predictionTime\": " + (15000 + System.currentTimeMillis()) + "\n" +
                "}";
        super.topic = topic;
        publisher = new CustomDataPublisher(topic.toString(), broker_ip.toString(), Integer.parseInt(broker_port), "admin", "admin", "", "predicted_event_test_publisher");
    }
    
    
    public void publish (){
       publisher.publish(payload,default_application_name);
    }
}
