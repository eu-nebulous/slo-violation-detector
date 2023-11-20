# SLO Severity-based Violation Detector

## Introduction

The SLO Severity-based Violation Detector is a component which receives predicted and actual monitoring metric values, and produces AMQP messages which denote i) the calculated severity ii) the probability that a reconfiguration will be required and iii) the timestamp which we refer to.

The component can run either using a compiled jar file, or be packaged to a Docker container and run in containerized form. 

## Configuration

### Configuration file

The component comes with a configuration file which can be used to specify the behaviour of the component (eu.morphemic.slo_violation_detector.properties) and a configuration file which is used to configure the AMQP communication (eu.melodic.event.brokerclient.properties). These files are located in the src/main/resources/config directory of the project.

The principal configuration options to be changed before deployment are in the eu.morphemic.slo_violation_detector.properties file which is passed as a runtime argument to the component.  They are the `broker_addres`(also the`broker_username` and`broker_password`), the `horizon`, the `number_of_days_to_aggregate_data_from` and the `number_of_seconds_to_aggregate_on`. 

| Parameter                              | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   | Indicative value                                         |
| -------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------- |
| metrics_bounds                         | A string value which is a csv list of monitoring metrics, and the respective upper and lower bounds which are known for them beforehand. The list contains triplets which are comma separated, while elements of a triplet are separated with semicolons. Each triplet contains the name of the metric, its lowest bound and its highest bound (or the word ‘unbounded’ if these are not known. If a monitoring metric is not registered here, it will be assumed that it can be assigned any real value from 0 (the lowest bound) to 100 (the highest bound) | avgResponseTime;unbounded;unbounded,custom2;0;3          |
| slo_rules_topic                        | A string value indicating the name of the topic which will be used to send messages  (to the SLO Violation Detector) containing the SLOs which should be respected by the application.                                                                                                                                                                                                                                                                                                                                                                        | metrics.metric_list                                      |
| broker_ip_url                          | A string value indicating the url which should be used to connect to the AMQP broker to send and receive messages.                                                                                                                                                                                                                                                                                                                                                                                                                                            | tcp://localhost:61616?wireFormat.maxInactivityDuration=0 |
| broker_username                        | A string value, which is the username to access the AMQP broker                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | User1                                                    |
| broker_password                        | A string value, which is the password to access the AMQP broker                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               | userpassword                                             |
| slo_violation_determination_method     | A string value, indicating the method which is used to determine the probability of a possible future  SLO violation. A choice is offered between all-metrics and prconf-delta                                                                                                                                                                                                                                                                                                                                                                                | all-metrics                                              |
| time_horizon_seconds                   | An integer value indicating the minimum time interval between two successive reconfigurations that the platform can support                                                                                                                                                                                                                                                                                                                                                                                                                                   | 900                                                      |
| maximum_acceptable_forward_predictions | An integer value indicating the maximum number of forward predictions for which the component will keep data                                                                                                                                                                                                                                                                                                                                                                                                                                                  | 30                                                       |

## Component input

The triggering input of the SLO Violation Detector is a JSON message which informs the component about the SLOs which should be respected. The format of these SLOs is the following:

 \<SLO\> ←  \<Metric\> \<Operator\> \<Threshold\>

**Metric**: Any monitoring attribute which can be observed using the EMS can be used in the formulation of an SLO

**Operator**: Either greater than, greater than or equal, less than or less than or equal.

**Threshold**: We assume that metric values used in the description of SLOs are real numbers, so any real number which can be handled by Java 9 can be used.

Multiple SLOs can be joined using an 'AND' or 'OR'-separated syntax. Examples of AND and OR separated SLO's appear below:

#### A simple SLO rule

```json
{
  "name": "_",
  "operator": "OR",
  "version": 1, 
  "constraints": [
    {
      "name": "cpu_usage_too_high",
      "metric": "cpu_usage",
      "operator": ">",
      "threshold": 80
    }
  ]
}
```

#### A complex SLO rule

```json
{
  "name": "application_1",
  "operator": "OR",
  "version": 1,
  "constraints": [
    {
      "name": "cpu_and_memory_or_swap_too_high",
      "operator": "AND",
      "constraints": [
        {
          "name": "cpu_usage_high",
          "metric": "cpu_usage",
          "operator": ">",
          "threshold": 80
        },
        {
          "name": "memory_or_swap_usage_high",
          "operator": "OR",
          "constraints": [
            {
              "name": "memory_usage_high",
              "metric": "ram_usage",
              "operator": ">",
              "threshold": 70
            },
            {
              "name": "disk_usage_high",
              "metric": "swap_usage",
              "operator": ">",
              "threshold": 50
            }
          ]
        }
      ]
    }
  ]
}
```

The simple SLO illustrated above states that the “cpu_usage” monitoring metric should stay ideally below 80 (percent), otherwise an SLO violation should be triggered. On the other hand, the complex SLO involves the use of three monitoring metrics, “cpu_usage”, “free_ram” and “swap_usage”, which should be below 70 and 50 (percent)
respectively. The format illustrated in the second example has been devised to allow
nested AND-based or OR-based SLOs to be defined. The complex SLO rule states that if (cpu_usage>80 AND (ram_usage>70 OR swap_usage> 50))
then an SLO violation should be triggered.

### Building & Running the component

The component can be built using Maven (`mvn clean install -Dtest=!UnboundedMonitoringAttributeTests`). This command should succeed without errors, and verifying that all tests (except for the Unbounded monitoring attribute tests) are successfully executed. Then, any of the produced jar files (either the shaded or non-shaded version) can be run using the following command:

`java -jar <jar_name> <role_type> <configuration_file_location>`

In the above command, jar_name is the file name of the executable jar of the slo-violation-detector; role_type is a string - either DIRECTOR or DETECTOR; and <configuration_file_location> is another string holding the path to the configuration file.

When the component starts correctly it will not display any error logs, and it may also display that it listens for events on the topic in which SLO rules are to be specified (by default **metrics.metric_list**).

It is not mandatory to specify the <configuration_file_location> or the <role_type> but the defaults will be assumed (the location of the configuration file will be based on the Constants.java class and the role will be OperationalMode.DIRECTOR )

When debugging/developing, the component can be started from the Java main method which is located inside the src/runtime/Main.java file.

### Testing process

To test the functionality of the component - provided that a working ActiveMQ Broker / Event Management System (EMS) installation is available, the following steps should be followed:

1. Send a message with the rule to be monitored (In production, the EMS translator is responsible to send this message, as well messages described in step 2. Messages described in step 3 are sent by the Prediction Orchestrator.)

2. Create a monitoring metrics stream, sending at a monitoring topic some values resembling real monitoring values.

3. Create a predicted metrics stream, sending at a prediction topic values which resemble real monitoring values. If the predicted values are over the thresholds defined at Step 1, then an SLO event should be created. It is important to create predicted monitoring data for the same monitoring attribute as the one for which realtime monitoring data is generated.

4. Watch for output on the defined output topic (by default `prediction.slo_severity_value`). The output will have the JSON format illustrated below:
   
   ```json
   {
     "severity": 0.9064,
     "predictionTime": 1626181860,
     "probability": 0.92246521
   }
   ```

To illustrate, in the case that an SLO message identical to the simple SLO example is sent at step 1, then monitoring messages should be sent in step 2 to the `cpu_usage` topic and predicted monitoring messages should be sent in step 3 to the `prediction.cpu_usage` topic. Finally, SLO violations will be announced at the `prediction.slo_severity_value` topic.

### Development

Starting new threads in the SLO Violation Detection component should only be done using the CharacterizedThread class, as opposed to using plain Threads - to reassure that Threads are being defined in a way which permits their appropriate management (registration/removal).


### Docker container build

To run the component in Dockerized form, it is sufficient to build the Dockerfile which is included at the root of the project. When running the docker container, the configuration file which will be used is the `src/main/resources/config/eu.morphemic.slo_violation_detector.properties` file, relative to the root of the project (this location specified as a variable in the `configuration.Constants` class). If another configuration file needs to be used, then it should be mounted over the `/home/src/main/resources/config/eu.morphemic.slo_violation_detector.properties` location.

To start the component, docker run can be used:

`docker run <container_name>`
