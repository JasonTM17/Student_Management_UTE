# CampusCore

CampusCore is now a course project built around one Java RESTful API, one
Next.js web app, one Expo mobile app, and one PostgreSQL database.

## Current shape

- API: `java-services/restful-api`
- Web: `frontend`
- Mobile: `mobile`
- Database: PostgreSQL

## In scope

- auth, roles, profile, students, lecturers
- semesters, faculties, departments, courses, classrooms, sections
- enrollments, grades, schedules
- announcements and notifications
- thesis core and a local DB-backed thesis assistant

## Out of scope

- finance and payment providers
- analytics and support tickets
- external AI providers and production RAG operations
- Redis, RabbitMQ, MinIO
- Nginx, Kubernetes, Cloudflare tunnel
- multi-image release and production handoff

## Run locally

```powershell
docker compose up -d --build postgres restful-api
curl http://127.0.0.1:4010/api/v1/health/liveness
curl http://127.0.0.1:4010/api/docs/openapi.json
npm test --prefix frontend
npm run typecheck --prefix frontend
npm run lint --prefix frontend
npm test --prefix mobile
npm run typecheck --prefix mobile
```

## Docs

- [README.md](./README.md)
- [README.vi.md](./README.vi.md)
- [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)
- [docs/RELEASE.md](./docs/RELEASE.md)
