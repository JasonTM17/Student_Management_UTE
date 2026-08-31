FROM maven:3.9.12-eclipse-temurin-21@sha256:c3c9d3ac4ce8431a3995c0318b8d390f448e693dd4fabc16e9b68d2e1f3d7b46 AS build

WORKDIR /workspace

COPY java-services/pom.xml java-services/pom.xml
COPY java-services/restful-api/pom.xml java-services/restful-api/pom.xml

COPY java-services/restful-api/src java-services/restful-api/src
RUN mvn -q -f java-services/pom.xml -pl restful-api -am package -DskipTests -Dstyle.color=never

FROM eclipse-temurin:21-jre-jammy@sha256:eebd356ad7358b7094758e5787a6726f332917cfd56feab6457c56dab895cdbf

WORKDIR /app

RUN groupadd --system --gid 10001 campuscore \
    && useradd --system --uid 10001 --gid 10001 --create-home --home-dir /home/campuscore --shell /usr/sbin/nologin campuscore \
    && apt-get update \
    && apt-get install --yes --no-install-recommends wget \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=campuscore:campuscore /workspace/java-services/restful-api/target/campuscore-restful-api-*.jar app.jar

USER 10001:10001
EXPOSE 4010

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
