FROM maven:3.9.12-eclipse-temurin-21 AS build

WORKDIR /workspace/java-services

COPY pom.xml ./pom.xml
COPY restful-api/pom.xml ./restful-api/pom.xml
RUN mvn -B -q -f pom.xml -pl restful-api -am dependency:go-offline

COPY restful-api/src ./restful-api/src
RUN mvn -B -q -f pom.xml -pl restful-api -am package -DskipTests

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 campuscore \
    && useradd --system --uid 10001 --gid 10001 --create-home --home-dir /home/campuscore --shell /usr/sbin/nologin campuscore

COPY --from=build /workspace/java-services/restful-api/target/campuscore-restful-api-*.jar /app/app.jar

USER 10001:10001
EXPOSE 4010

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
