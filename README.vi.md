# CampusCore

CampusCore hiện là một đồ án môn học với một Java RESTful API, một web
Next.js, một mobile Expo và một PostgreSQL duy nhất.

## Dạng hiện tại

- API: `java-services/restful-api`
- Web: `frontend`
- Mobile: `mobile`
- Database: PostgreSQL

## Trong phạm vi

- auth, roles, hồ sơ, sinh viên, giảng viên
- học kỳ, khoa, ngành, môn học, phòng học, lớp học phần
- đăng ký học phần, điểm, lịch học
- thông báo và inbox notification
- thesis lõi và trợ lý luận văn dùng dữ liệu trong PostgreSQL

## Ngoài phạm vi

- finance và payment provider
- analytics và support ticket
- provider AI bên ngoài và vận hành RAG production
- Redis, RabbitMQ, MinIO
- Nginx, Kubernetes, Cloudflare tunnel
- multi-image release và production handoff

## Chạy cục bộ

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

## Tài liệu

- [README.md](./README.md)
- [README.en.md](./README.en.md)
- [docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)
- [docs/RELEASE.md](./docs/RELEASE.md)
