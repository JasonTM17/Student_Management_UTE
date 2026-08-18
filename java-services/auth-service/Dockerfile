FROM maven:3.9.12-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY . .
RUN mvn -pl auth-service -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app
RUN useradd --system --create-home --uid 10001 campuscore
COPY --from=build /workspace/auth-service/target/campuscore-auth-service-0.1.0-SNAPSHOT.jar /app/app.jar
USER 10001
EXPOSE 4007
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
