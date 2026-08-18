FROM maven:3.9.12-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./pom.xml
COPY thesis-service/pom.xml ./thesis-service/pom.xml
COPY thesis-service/src ./thesis-service/src
RUN mvn -pl thesis-service -am -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --create-home --uid 10001 campuscore
COPY --from=build /workspace/thesis-service/target/campuscore-thesis-service-0.1.0-SNAPSHOT.jar /app/app.jar
USER 10001
EXPOSE 4010
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
