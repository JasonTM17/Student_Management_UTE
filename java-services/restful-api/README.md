# CampusCore RESTful API

This directory is the single Java deployable for the course project:
Spring Boot 3.5 / Java 21 / PostgreSQL / Flyway.

## Current scope

- `/api/v1/health/liveness` and key-protected readiness
- shared cookie-or-bearer token resolution
- the existing double-submit web CSRF contract
- request correlation through `X-Request-Id`
- stable validation and error envelopes
- Actuator and OpenAPI paths
- a non-root Dockerfile for the single API image
- auth session support for login, refresh, logout, profile and password change
- people reads for students and lecturers
- academic catalog, sections, enrollments, grades and schedules
- notifications inbox and thesis core

## Out of scope for the course cut

- finance / payment orchestration
- analytics beyond the core dashboard needs
- support tickets
- chatbot / external AI provider integration
- Redis, RabbitMQ, MinIO
- Nginx, Kubernetes and multi-image release flow

## Local verification

```powershell
mvn -q -f java-services/pom.xml verify
mvn -q -f java-services/restful-api/pom.xml test
mvn -q -f java-services/restful-api/pom.xml -Dspring.profiles.active=test,persistence test
```

For a local process, provide real server-side `JWT_SECRET` and
`JWT_REFRESH_SECRET` values with at least 32 characters plus a
`HEALTH_READINESS_KEY`. Never put these values in source, client bundles or
committed `.env` files.
