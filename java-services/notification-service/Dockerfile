FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY auth-service/pom.xml auth-service/pom.xml
COPY engagement-service/pom.xml engagement-service/pom.xml
COPY notification-service/pom.xml notification-service/pom.xml
COPY thesis-service/pom.xml thesis-service/pom.xml
RUN mvn -B -q dependency:go-offline
COPY auth-service/src auth-service/src
COPY engagement-service/src engagement-service/src
COPY notification-service/src notification-service/src
COPY thesis-service/src thesis-service/src
RUN mvn -B -q -pl notification-service -am clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/notification-service/target/campuscore-notification-service-*.jar app.jar
EXPOSE 4011
ENTRYPOINT ["java", "-jar", "app.jar"]
