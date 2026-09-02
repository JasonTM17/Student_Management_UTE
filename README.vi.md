# CampusCore

> Không gian học vụ song ngữ cho định danh, học phần, thông báo và thesis — một
> Java API, một contract và một PostgreSQL dùng chung.

![CampusCore product tour](docs/assets/campuscore-product-tour.gif)

## Ảnh giao diện và sơ đồ hệ thống

Các ảnh homepage là asset do project sở hữu, giúp xem nhanh hướng thiết kế
English/VI mà không hàm ý đây là bằng chứng production deployment.

| English homepage | Trang chủ tiếng Việt |
| --- | --- |
| ![CampusCore English homepage](docs/assets/screenshots/campuscore-home-en-latest.png) | ![Trang chủ CampusCore tiếng Việt](docs/assets/screenshots/campuscore-home-vi-latest.png) |

![Sơ đồ kiến trúc CampusCore](docs/assets/campuscore-system-architecture.png)

Mở [sơ đồ vector](docs/assets/campuscore-system-architecture.svg) để phóng to.
Boundary runtime và non-goal đầy đủ nằm trong
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

CampusCore là đồ án môn học dạng RESTful API tầm trung, chạy bằng một Java
Spring Boot API, một Next.js web, một Expo mobile và một PostgreSQL.

Phạm vi giữ lại gồm auth, phân quyền student/lecturer/admin, danh mục học vụ,
đăng ký học phần, lịch, điểm, thông báo, thesis core và thesis assistant dùng
`rag-service` nội bộ để thực hiện thesis-grounded RAG trong PostgreSQL có
citation, lexical fallback và tùy chọn DeepSeek V4 Flash chỉ ở backend.

Finance, analytics, support ticket, vector search, Redis, RabbitMQ, MinIO,
Nginx, Kubernetes, Cloudflare Tunnel và release multi-image được loại khỏi đồ
án. Workflow GHCR vẫn xuất bản một wrapper PostgreSQL không chứa migration để
đồng bộ artifact của course stack; Flyway trong REST API tiếp tục sở hữu schema
và seed. DeepSeek là tùy chọn, mặc định tắt; lexical fallback không cần key.

```powershell
docker compose up -d --build postgres mailpit rag-service restful-api web
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
```

Mailpit UI ở `http://127.0.0.1:8025`.

### Bật DeepSeek an toàn

Key đã từng dán trong hội thoại phải được revoke/rotate. Chỉ đặt key mới trong
`.env` hoặc secret store của server, không đặt vào web/mobile, `NEXT_PUBLIC_*`,
git hay log. Bật `DEEPSEEK_ENABLED=true` sau khi thay placeholder trong
`.env.example`; thiếu key, provider lỗi, hết quota hoặc không có tài liệu sẽ
quay về lexical fallback. Xem
[docs/integrations/deepseek-assistant.md](docs/integrations/deepseek-assistant.md).

| Role | Email | Mật khẩu |
| --- | --- | --- |
| Student | `student@campuscore.edu` | `password123` |
| Lecturer | `lecturer@campuscore.edu` | `password123` |
| Admin | `admin@campuscore.edu` | `admin123` |

Các tài khoản này chỉ dành cho database seed local của đồ án.

Xem [ARCHITECTURE.md](docs/ARCHITECTURE.md), [RELEASE.md](docs/RELEASE.md) và
[RESTFUL_API_CONSOLIDATION.md](docs/RESTFUL_API_CONSOLIDATION.md) để biết đầy
đủ acceptance gate.
