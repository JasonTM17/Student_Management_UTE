FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S -g 10001 campuscore \
    && adduser -S -u 10001 -G campuscore campuscore

COPY target/campuscore-restful-api-*.jar app.jar

USER 10001:10001
EXPOSE 4010

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
