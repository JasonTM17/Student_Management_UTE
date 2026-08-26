# CampusCore

CampusCore is a medium-sized course project built as one Java Spring Boot
RESTful API, one internal RAG service, one Next.js web app, one Expo mobile
app, and one PostgreSQL database.

The retained scope covers pending registration, email verification, password
reset, role-based student/lecturer/admin
flows, academic catalog, HCMUTE-style registration rounds and enrollment,
schedules, grades, announcements, notifications, thesis core, and a
PostgreSQL-backed curated lexical thesis assistant served by the internal
`rag-service` container, with citations and an optional server-only DeepSeek
streaming adapter.

Finance, analytics, support tickets, client-side/provider-unbounded AI, vector
search, Redis, RabbitMQ, MinIO, Nginx, Kubernetes, Cloudflare Tunnel, and
multi-image production cutover are intentionally excluded. DeepSeek is disabled
by default and lexical fallback works without a key.

The handoff target is Java 25 LTS with Spring Boot 3.5.16, Maven 3.9.x and
Flyway-owned PostgreSQL 15. Java 21 is retained as a separately recorded
compatibility baseline; host JDK 24/26 output is not Java 25 evidence.

Run the stack with:

```powershell
docker compose up -d --build postgres mailpit rag-service restful-api web
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
```

Local HTML and plain-text messages are captured by Mailpit at
`http://127.0.0.1:8025`. Configure a real SMTP provider only with server-side
environment variables; see [auth-mail.md](docs/integrations/auth-mail.md).

CampusCore application identities live in the private `campuscore_auth`
schema; the Supabase-managed `auth` schema is never created or modified by the
application migrations. A new reviewed Supabase database uses the schema-only
B20 baseline plus the reviewed V21 successor, while local and existing
CampusCore databases follow V1-V21. See
[supabase-database.md](docs/integrations/supabase-database.md).

Local seed accounts:

| Role | Email | Password |
| --- | --- | --- |
| Student | `student@campuscore.edu` | `password123` |
| Lecturer | `lecturer@campuscore.edu` | `password123` |
| Admin | `admin@campuscore.edu` | `admin123` |

These credentials are only for the local course database.

See [ARCHITECTURE.md](docs/ARCHITECTURE.md), [RELEASE.md](docs/RELEASE.md),
and [RESTFUL_API_CONSOLIDATION.md](docs/RESTFUL_API_CONSOLIDATION.md) for the
complete course acceptance gates.
