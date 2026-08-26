FROM maven:3.9.12-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY java-services/pom.xml java-services/pom.xml
COPY java-services/restful-api/pom.xml java-services/restful-api/pom.xml

COPY java-services/restful-api/src java-services/restful-api/src
RUN mvn -q -f java-services/pom.xml -pl restful-api -am package -DskipTests -Dstyle.color=never

FROM eclipse-temurin:25-jre-jammy

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
