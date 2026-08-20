FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY notification-service/pom.xml notification-service/pom.xml
RUN mvn -B -q -f notification-service/pom.xml dependency:go-offline
COPY notification-service/src notification-service/src
RUN mvn -B -q -f notification-service/pom.xml clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/notification-service/target/campuscore-notification-service-*.jar app.jar
EXPOSE 4011
ENTRYPOINT ["java", "-jar", "app.jar"]
