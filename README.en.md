# CampusCore

CampusCore is a medium-sized course project built as one Java Spring Boot
RESTful API, one Next.js web app, one Expo mobile app, and one PostgreSQL
database.

The retained scope covers authentication, role-based student/lecturer/admin
flows, academic catalog, enrollment, schedules, grades, announcements,
notifications, thesis core, and a PostgreSQL-backed curated lexical thesis
assistant served by the internal `rag-service` container with citations.

Finance, analytics, support tickets, external AI providers, vector search,
Redis, RabbitMQ, MinIO, Nginx, Kubernetes, Cloudflare Tunnel, and multi-image
production release are intentionally excluded.

Run the stack with:

```powershell
docker compose up -d --build postgres mailpit rag-service restful-api web
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
```

Mailpit is available at `http://127.0.0.1:8025`.

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
