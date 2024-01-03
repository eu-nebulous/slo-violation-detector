# Copyright (c) 2023 Institute of Communication and Computer Systems
#
# This Source Code Form is subject to the terms of the Mozilla Public
# License, v. 2.0. If a copy of the MPL was not distributed with this
# file, You can obtain one at https://mozilla.org/MPL/2.0/.

#
# Build stage
#
FROM docker.io/library/maven:3.9.2-eclipse-temurin-17 AS build
COPY src /home/app/src
COPY pom.xml /home/app
RUN mvn -f /home/app/pom.xml -DfailIfNoTests=false -Dtest=!UnboundedMonitoringAttributeTests,!ConnectivityTests clean package

FROM docker.io/library/eclipse-temurin:17
RUN mkdir -p /home/src/main/resources/
COPY src/main/resources/config/eu.nebulous.slo_violation_detector.properties /home/src/main/resources/config/input_data.properties
COPY src/main/resources/config/eu.melodic.event.brokerclient.properties /home/src/main/resources/config/eu.melodic.event.brokerclient.properties
COPY --from=build /home/app/target/SLO-Violation-Detector-4.0-SNAPSHOT.jar /home/SLOSeverityCalculator-4.0-SNAPSHOT.jar
WORKDIR /home
ENV LOG_FILE /home/slo_violation_detector.log
#CMD ["/bin/sh","-c","java -jar SLOSeverityCalculator-4.0-SNAPSHOT.jar > $LOG_FILE 2>&1"]
#CMD ["/bin/sh","-c","java -jar SLOSeverityCalculator-4.0-SNAPSHOT.jar 2>&1 | tee $LOG_FILE"]
CMD ["/bin/sh","-c","java -jar SLOSeverityCalculator-4.0-SNAPSHOT.jar 2>&1"]