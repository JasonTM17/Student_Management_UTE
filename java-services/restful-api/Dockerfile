FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system --gid 10001 campuscore \
    && useradd --system --uid 10001 --gid 10001 campuscore

COPY target/campuscore-restful-api-*.jar app.jar

USER 10001:10001
EXPOSE 4010

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
