# CampusCore RESTful API shell

This directory is the first bounded implementation of the selected
architecture: one standalone Spring Boot 3.4 / Java 21 application. It is a
shell, not a domain-parity or cutover claim.

## Included in this phase

- one Maven project and one Spring Boot main class;
- `/api/v1/health/liveness` and key-protected readiness;
- shared cookie-or-bearer token resolution;
- the existing double-submit web CSRF contract;
- request correlation through `X-Request-Id`;
- authenticated identity claim and mutation probes;
- stable validation/error envelope;
- Actuator and OpenAPI paths;
- a non-root Dockerfile for the eventual single image.

The default profile intentionally excludes database auto-configuration, so the
shell can be tested without a running service dependency. The `persistence`
profile enables one PostgreSQL datasource, one Flyway migration owner and the
first thesis read path only when `THESIS_READ_ENABLED=true` is also supplied.
No legacy route, payment provider or LLM provider is cut over; those
dependencies enter through later migration phases with one canonical writer and
explicit rollback evidence.

## Local verification

From the repository root:

```powershell
mvn -q -f java-services/restful-api/pom.xml test

# Persistence rehearsal uses the H2 test profile; it does not touch Postgres.
mvn -q -f java-services/restful-api/pom.xml -Dspring.profiles.active=test,persistence test
```

For a local process, provide a real server-side `JWT_SECRET` with at least 32
characters and a `HEALTH_READINESS_KEY`. Never put either value in source,
client bundles or committed `.env` files.

The endpoint `/api/v1/contract` intentionally reports `migration: not-cut-over`.
It is a phase probe and must be removed or replaced by real module contracts
before any public route switch. The persistence profile is not a production
readiness signal until it has been exercised against a disposable PostgreSQL
restore as well as H2.
