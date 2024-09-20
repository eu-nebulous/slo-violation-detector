package utility_beans.broker_communication;

import static configuration.Constants.default_application_name;

public class NewSLORuleEventPublisher extends SingleEventPublisher {
    
    String broker_ip="nebulous-activemq";
    String broker_port="5672";
    CustomDataPublisher publisher;
    public NewSLORuleEventPublisher(String topic) {
        
        payload = "{\n" +
                "  \"name\": \""+default_application_name+"\",\n" +
                "  \"operator\": \"OR\",\n" +
                "  \"constraints\": [\n" +
                "    {\n" +
                "      \"name\": \"cpu_and_memory_or_swap_too_high\",\n" +
                "      \"operator\": \"AND\",\n" +
                "      \"constraints\": [\n" +
                "        {\n" +
                "          \"name\": \"cpu_usage_high\",\n" +
                "          \"metric\": \"cpu_usage\",\n" +
                "          \"operator\": \">\",\n" +
                "          \"threshold\": 80.0\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"memory_or_swap_usage_high\",\n" +
                "          \"operator\": \"OR\",\n" +
                "          \"constraints\": [\n" +
                "            {\n" +
                "              \"name\": \"memory_usage_high\",\n" +
                "              \"metric\": \"ram_usage\",\n" +
                "              \"operator\": \">\",\n" +
                "              \"threshold\": 70.0\n" +
                "            },\n" +
                "            {\n" +
                "              \"name\": \"disk_usage_high\",\n" +
                "              \"metric\": \"swap_usage\",\n" +
                "              \"operator\": \">\",\n" +
                "              \"threshold\": 50.0\n" +
                "            }\n" +
                "          ]\n" +
                "        }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}";
        super.topic = topic;
        publisher = new CustomDataPublisher(topic.toString(), broker_ip.toString(), Integer.parseInt(broker_port), "admin", "admin", "", "predicted_event_test_publisher");
    }
    
    
    public void publish (){
       publisher.publish(payload,default_application_name);
    }
}
