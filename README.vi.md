# CampusCore

CampusCore là đồ án môn học dạng RESTful API tầm trung, chạy bằng một Java
Spring Boot API, một Next.js web, một Expo mobile và một PostgreSQL.

Phạm vi giữ lại gồm auth, phân quyền student/lecturer/admin, danh mục học vụ,
đăng ký học phần, lịch, điểm, thông báo, thesis core và thesis assistant dùng
curated lexical RAG trong PostgreSQL có citation.

Finance, analytics, support ticket, AI provider bên ngoài, vector search,
Redis, RabbitMQ, MinIO, Nginx, Kubernetes, Cloudflare Tunnel và release
multi-image được loại khỏi đồ án.

```powershell
docker compose up -d --build postgres restful-api
curl http://127.0.0.1:4010/api/v1/health/liveness
curl http://127.0.0.1:4010/v3/api-docs
```

Xem [ARCHITECTURE.md](docs/ARCHITECTURE.md), [RELEASE.md](docs/RELEASE.md) và
plan đang active để biết đầy đủ acceptance gate.
