package utility_beans.broker_communication;

import static configuration.Constants.default_application_name;

public class RealtimeEventPublisher extends SingleEventPublisher {
    
    String broker_ip="nebulous-activemq";
    String broker_port="5672";
    CustomDataPublisher publisher;
    public RealtimeEventPublisher(String topic, String value) {
        payload = "{\n" +
                "    \"metricValue\": "+value+",\n" +
                "    \"level\": 1,\n" +
                "    \"component_id\":\"postgresql_1\",\n" +
                "    \"timestamp\": "+(System.currentTimeMillis())+"\n" +
                "}\n";
        super.topic = topic;
        publisher = new CustomDataPublisher(topic.toString(), broker_ip.toString(), Integer.parseInt(broker_port), "admin", "admin", "", "predicted_event_test_publisher");
    }
    
    
    public void publish (){
       publisher.publish(payload,default_application_name);
    }
}
