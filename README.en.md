# CampusCore

CampusCore is a medium-sized course project built as one Java Spring Boot
RESTful API, one Next.js web app, one Expo mobile app, and one PostgreSQL
database.

The retained scope covers authentication, role-based student/lecturer/admin
flows, academic catalog, enrollment, schedules, grades, announcements,
notifications, thesis core, and a PostgreSQL-backed curated lexical thesis
assistant with citations.

Finance, analytics, support tickets, external AI providers, vector search,
Redis, RabbitMQ, MinIO, Nginx, Kubernetes, Cloudflare Tunnel, and multi-image
production release are intentionally excluded.

Run the stack with:

```powershell
docker compose up -d --build postgres restful-api
curl http://127.0.0.1:4010/api/v1/health/liveness
curl http://127.0.0.1:4010/v3/api-docs
```

See [ARCHITECTURE.md](docs/ARCHITECTURE.md), [RELEASE.md](docs/RELEASE.md),
and the active plan for the complete course acceptance gates.
