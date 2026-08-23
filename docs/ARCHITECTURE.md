# Architecture

## Runtime

```text
Next.js web ─────┐
                 ├── Java Spring Boot REST API ─── PostgreSQL
Expo mobile ─────┘
```

The Java application is the only public backend owner of `/api/v1`. Web uses
cookie authentication with CSRF protection; mobile uses bearer access and
refresh tokens. Both clients consume the same OpenAPI contract.

## Domain ownership

The Java API owns authentication, identity, people, catalog, sections,
enrollment, grades, schedules, announcements, notifications, thesis core and
the local thesis assistant. Auth and academic records use stable string IDs;
thesis and assistant records use UUIDs. Student and lecturer codes remain
unique business identifiers.

Flyway owns the schema. A fresh course database is seeded deterministically
with roles, demo users, academic catalog, sections, announcements,
notifications, thesis data and curated assistant knowledge. No legacy service
schema is read at runtime and no old data migration is required.

## Assistant

The thesis assistant performs bounded lexical retrieval over active curated
knowledge rows in PostgreSQL. It uses a fixed top-k, explicit locale fallback,
stable citations and explicit `NO_MATCH` or `KNOWLEDGE_UNAVAILABLE` reason
codes. It does not use an external model, vector database, Redis or a
microservice.

## Client and UI direction

The web portal uses the HCMUTE reference language: institutional blue shell,
student identity rail, yellow navigation groups, blue header tables, ribbon
page titles, tab strips, dense information layout and explicit loading/error/
empty/forbidden states. The implementation uses project-owned assets and does
not copy credentials, cookies, HTML or proprietary source code.

## Non-goals

Finance, analytics, support tickets, realtime sockets, email verification,
email password reset, thesis council/review/evaluation, external AI, Redis,
RabbitMQ, MinIO, Nginx, Kubernetes, observability stack and production
cutover are outside the course runtime.
