# Release

CampusCore hiện không còn theo mô hình public nine-image release. Mục tiêu là
chạy được một course demo reproducible với một Java RESTful API và một PostgreSQL.

## Current policy

- `main` là nhánh chính.
- Chỉ merge khi candidate sạch và các gate cục bộ pass.
- Không push/ship theo topology microservices cũ.
- Không gọi đây là production-ready nếu chưa có proof live tương ứng.

## Required gates

- `mvn -q -f java-services/pom.xml verify`
- `docker compose config`
- `docker compose up -d --build postgres restful-api`
- `curl http://127.0.0.1:4010/api/v1/health/liveness`
- `curl http://127.0.0.1:4010/api/docs/openapi.json`
- `npm test --prefix frontend`
- `npm run typecheck --prefix frontend`
- `npm run lint --prefix frontend`
- `npm test --prefix mobile`
- `npm run typecheck --prefix mobile`
- `git diff --check`

## Not in scope

- Kubernetes
- multi-image registry publication
- Nginx edge
- canary / rollback / production handoff
- Redis, RabbitMQ, MinIO and observability stack as release gates

## Notes

The historical release notes under `docs/releases/` are kept as archive material
for the older stack. They are not the current operating target.
