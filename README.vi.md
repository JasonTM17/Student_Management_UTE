# CampusCore

CampusCore là đồ án môn học dạng RESTful API tầm trung, chạy bằng một Java
Spring Boot API, một RAG service nội bộ, một Next.js web, một Expo mobile và
một PostgreSQL.

Phạm vi giữ lại gồm auth với xác minh email và quên/đặt lại mật khẩu, phân
quyền student/lecturer/admin, danh mục học vụ,
đăng ký học phần, lịch, điểm, thông báo, thesis core và thesis assistant dùng
`rag-service` nội bộ để thực hiện thesis-grounded RAG trong PostgreSQL có
citation, lexical fallback và tùy chọn DeepSeek V4 Flash.

Finance, analytics, support ticket, vector search, Redis, RabbitMQ, MinIO,
Nginx, Kubernetes, Cloudflare Tunnel và release multi-image được loại khỏi đồ
án. DeepSeek là tùy chọn, mặc định tắt; lexical fallback không cần key.

```powershell
docker compose up -d --build postgres mailpit rag-service restful-api
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
.\mvnw.cmd -q -f java-services/pom.xml verify
```

Mail local được gửi vào Mailpit tại `http://127.0.0.1:8025`; template HTML và
plain-text có cả tiếng Việt/Anh. Xem
[auth-mail.md](docs/integrations/auth-mail.md) để cấu hình SMTP bằng biến môi
trường mà không commit credential.

Identity của CampusCore nằm trong schema riêng `campuscore_auth`; các migration
không tạo hoặc sửa schema `auth` do Supabase quản lý. Database Supabase mới chỉ
dùng baseline schema-only B20 rồi successor V21 sau khi kiểm tra đúng project,
backup và drift; database local/cũ tiếp tục chạy V1-V21. Xem
[supabase-database.md](docs/integrations/supabase-database.md).

### Toolchain Java

Bản bàn giao dùng Java 25 LTS và Maven 3.9.x qua Maven Wrapper (`.\mvnw.cmd`
trên Windows, `./mvnw` trên Linux/macOS). Build sẽ dừng sớm nếu dùng JDK hoặc
Maven ngoài allowlist. Java 21 chỉ là baseline tương thích, chạy bằng profile
`-Dcampuscore.java-baseline=true`; JDK 24/26 không được tính là bằng chứng của
gate Java 25.

### Bật DeepSeek an toàn

Key đã từng dán trong hội thoại phải được revoke/rotate. Chỉ đặt key mới trong
`.env` hoặc secret store của server, không đặt vào web/mobile, `NEXT_PUBLIC_*`,
git hay log. Bật `DEEPSEEK_ENABLED=true` sau khi thay placeholder trong
`.env.example`; biến này được dùng bởi `rag-service`, còn API public proxy qua
`ASSISTANT_RAG_SERVICE_TOKEN`. Thiếu key, provider lỗi, hết quota hoặc không
có tài liệu sẽ quay về lexical fallback. Xem
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
