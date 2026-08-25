# CampusCore

CampusCore là đồ án quản lý sinh viên dạng RESTful API tầm trung, gồm một
Spring Boot Java API, một ứng dụng Next.js, một ứng dụng Expo và một PostgreSQL.

## Thành phần

- API: `java-services/restful-api`
- Web: `frontend`
- Mobile: `mobile`
- Database: PostgreSQL qua Flyway

## Chức năng lõi

- Đăng ký chờ xác minh email, đăng nhập, refresh, logout, profile, đổi mật
  khẩu và quên/đặt lại mật khẩu bằng challenge băm dùng một lần.
- Student, lecturer, admin; học kỳ, khoa, ngành, môn học, phòng học, lớp học phần.
- Đăng ký/hủy học phần, lịch học, điểm và bảng điểm.
- Announcement và notification inbox.
- Thesis core: round, topic, group, member, progress/status.
- Thesis assistant dùng thesis-grounded RAG trong PostgreSQL, có citation,
  lexical fallback và tùy chọn DeepSeek V4 Flash chỉ ở backend.

Finance, analytics, support ticket, vector database, Redis, RabbitMQ, MinIO,
Nginx, Kubernetes, Cloudflare tunnel và multi-image release không thuộc runtime
của đồ án. DeepSeek là tích hợp tùy chọn, mặc định tắt và không cần để chạy
local lexical fallback.

## Chạy local

```powershell
docker compose up -d --build postgres mailpit restful-api
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
curl.exe -H "X-Health-Key: local-course-health-key" http://127.0.0.1:4010/api/v1/health/readiness
curl.exe http://127.0.0.1:4010/v3/api-docs
.\mvnw.cmd -q -f java-services/pom.xml verify
npm test --prefix frontend
npm run typecheck --prefix frontend
npm run lint --prefix frontend
npm run build --prefix frontend
npm test --prefix mobile
npm run typecheck --prefix mobile
```

Mailpit nhận mail HTML và plain-text local tại `http://127.0.0.1:8025`.
SMTP thật chỉ được cấu hình bằng biến môi trường server; không ghi username,
password hoặc token vào repository. Xem
[docs/integrations/auth-mail.md](docs/integrations/auth-mail.md).

Auth của ứng dụng nằm trong schema riêng `campuscore_auth`; schema Supabase
`auth` không bị CampusCore tạo hoặc sửa. Database Supabase mới chỉ dùng baseline
schema-only `B20` sau khi đã xác minh đúng project, backup và drift; database
local/cũ tiếp tục chạy chuỗi V1-V20. Xem
[docs/integrations/supabase-database.md](docs/integrations/supabase-database.md).

### Java toolchain

The handoff target is Java 25 LTS with Maven 3.9.x. Use the repository Maven
Wrapper (`.\mvnw.cmd` on Windows or `./mvnw` on Linux/macOS); the build fails
early when an unapproved JDK or Maven version is selected. Java 21 remains a
compatibility baseline and is run explicitly with
`-Dcampuscore.java-baseline=true`. JDK 24/26 host output is diagnostic only and
does not satisfy either release gate.

### Tùy chọn DeepSeek RAG

Chỉ inject secret ở process/server runtime sau khi đã revoke và rotate key bị
lộ. Không đặt key trong `NEXT_PUBLIC_*`, Expo, git, ticket, screenshot hoặc
log. Copy `.env.example` thành `.env`, thay placeholder bằng secret mới rồi
bật `DEEPSEEK_ENABLED=true`; nếu thiếu key, provider lỗi, hết quota hoặc không
có tài liệu phù hợp, API tự trả lexical fallback. Model mặc định là
`deepseek-v4-flash`, non-thinking, context tối đa 6.000 ký tự và output tối đa
800 tokens. Xem [docs/integrations/deepseek-assistant.md](docs/integrations/deepseek-assistant.md).

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
- [docs/integrations/auth-mail.md](docs/integrations/auth-mail.md)
- [docs/integrations/supabase-database.md](docs/integrations/supabase-database.md)

Đây là local/course demo reproducible, không phải production deployment.
