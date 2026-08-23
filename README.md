# CampusCore

CampusCore là đồ án quản lý sinh viên dạng RESTful API tầm trung, gồm một
Spring Boot Java API, một ứng dụng Next.js, một ứng dụng Expo và một PostgreSQL.

## Thành phần

- API: `java-services/restful-api`
- Web: `frontend`
- Mobile: `mobile`
- Database: PostgreSQL qua Flyway

## Chức năng lõi

- Đăng ký, đăng nhập, refresh, logout, profile và đổi mật khẩu.
- Student, lecturer, admin; học kỳ, khoa, ngành, môn học, phòng học, lớp học phần.
- Đăng ký/hủy học phần, lịch học, điểm và bảng điểm.
- Announcement và notification inbox.
- Thesis core: round, topic, group, member, progress/status.
- Thesis assistant dùng curated lexical RAG trong PostgreSQL, có citation.

Finance, analytics, support ticket, external AI provider, vector database,
Redis, RabbitMQ, MinIO, Nginx, Kubernetes, Cloudflare tunnel và multi-image
release không thuộc runtime của đồ án.

## Chạy local

```powershell
docker compose up -d --build postgres restful-api
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
mvn -q -f java-services/pom.xml verify
npm test --prefix frontend
npm run typecheck --prefix frontend
npm run lint --prefix frontend
npm run build --prefix frontend
npm test --prefix mobile
npm run typecheck --prefix mobile
```

## Tài khoản demo

| Role | Email | Mật khẩu |
| --- | --- | --- |
| Student | `student@campuscore.edu` | `password123` |
| Lecturer | `lecturer@campuscore.edu` | `password123` |
| Admin | `admin@campuscore.edu` | `admin123` |

Các tài khoản này chỉ dành cho database seed local của đồ án.

## Tài liệu

- [README.vi.md](README.vi.md)
- [README.en.md](README.en.md)
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)
- [docs/RELEASE.md](docs/RELEASE.md)
- [docs/RESTFUL_API_CONSOLIDATION.md](docs/RESTFUL_API_CONSOLIDATION.md)

Đây là local/course demo reproducible, không phải production deployment.
