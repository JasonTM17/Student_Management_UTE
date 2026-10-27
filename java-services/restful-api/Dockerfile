FROM maven:3.9.14-eclipse-temurin-21@sha256:98819eb3745bd2007c3f1a19b59085c1fa3929aecb7dbfa431dfcf5a4f18ce3c AS build

WORKDIR /workspace

COPY java-services/pom.xml java-services/pom.xml
COPY java-services/restful-api/pom.xml java-services/restful-api/pom.xml


COPY java-services/restful-api/src java-services/restful-api/src
RUN mvn -q -f java-services/pom.xml -pl restful-api -am package -DskipTests -Dstyle.color=never

FROM eclipse-temurin:21-jre-jammy@sha256:9603446cf51296842eab342514857cf7ab55a8891cd564e6c68ec237fae65a16

WORKDIR /app

RUN groupadd --system --gid 10001 campuscore \
    && useradd --system --uid 10001 --gid 10001 --create-home --home-dir /home/campuscore --shell /usr/sbin/nologin campuscore \
    && apt-get update \
    && apt-get install --yes --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/lib/campuscore/thesis-reports \
    && chown -R campuscore:campuscore /var/lib/campuscore

COPY --from=build --chown=campuscore:campuscore /workspace/java-services/restful-api/target/campuscore-restful-api-*.jar app.jar

USER 10001:10001
EXPOSE 4010

ENV JAVA_OPTS="-XX:MaxRAMPercentage=65.0 -XX:InitialRAMPercentage=40.0 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=160m -XX:ReservedCodeCacheSize=48m -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
