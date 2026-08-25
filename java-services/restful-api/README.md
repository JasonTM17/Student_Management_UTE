# CampusCore Java RESTful API

This is the only backend runtime for the course project. It is a Spring Boot
3.5.16 / Java 25 modular monolith that owns `/api/v1`, persists to one
PostgreSQL database, and publishes its contract through OpenAPI. Java 21 is a
separately tested compatibility baseline.

## Included modules

- pending student registration, email verification/resend, login, refresh
  sessions, logout, profile, password change and forgot/reset;
- students, lecturers and administrative user management;
- faculties, departments, semesters, courses, classrooms and sections;
- HCMUTE-style registration rounds, idempotent enrollment/drop, schedules,
  attendance, grades, transcript reads and SHA-256 registration-slip PDFs;
- announcements and notification inbox;
- thesis rounds, topics, groups, members and progress status;
- curated PostgreSQL lexical RAG with citations, privacy guards, explicit
  degraded states and an optional server-only DeepSeek SSE adapter;
- liveness, readiness, `/api/v1/contract` and `/v3/api-docs`.

There is no Node backend, service gateway, event broker, cache server,
object-storage service, realtime server or observability stack in this runtime.
Finance, analytics, support tickets and advanced thesis council/evaluation
workflows are outside the course scope.

## Run locally

From the repository root:

```powershell
.\mvnw.cmd -q -f java-services/pom.xml verify
docker compose up --build postgres mailpit restful-api
```

The API listens on `http://localhost:4010` by default. Useful checks:

```powershell
curl.exe http://localhost:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://localhost:4010/api/v1/health/readiness
curl.exe http://localhost:4010/v3/api-docs
```

The local seed includes `student@campuscore.edu` with password `password123`
for the reproducible student demo. Configure non-empty values for
`JWT_SECRET`, `JWT_REFRESH_SECRET`, `SPRING_DATASOURCE_PASSWORD` and
`HEALTH_READINESS_KEY`; never commit real secrets.

Local mail is enabled through Mailpit (`SMTP 1025`, UI `8025`) and renders both
HTML and plain-text templates in Vietnamese and English. For an external SMTP
provider, inject `SPRING_MAIL_*`, `MAIL_FROM` and `AUTH_FRONTEND_BASE_URL` only
at server runtime. See `docs/integrations/auth-mail.md`.

Flyway is the only schema owner. The `persistence` profile is enabled by the
Compose service; PostgreSQL V13-V20 are forward-only registration/assistant/
auth-lifecycle and Supabase-compatibility
hardening migrations and must be rehearsed on an isolated copy before any
upgrade. `ddl-auto=validate` and `open-in-view=false` keep JPA at the typed
persistence boundary. The Compose service starts from an empty PostgreSQL
database with deterministic course seed data.

V20 moves all application-owned identity/session/challenge objects from the
legacy `auth` schema to `campuscore_auth`. This leaves Supabase's managed
`auth` schema untouched. A brand-new, verified Supabase target opts into the
schema-only `db/supabase-baseline/B20__campuscore_supabase_baseline.sql`; it
must not use that location for an existing V-history database. See
`docs/integrations/supabase-database.md` for the identity, backup, drift and
verification gates.
