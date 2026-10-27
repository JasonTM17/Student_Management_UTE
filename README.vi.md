# CampusCore — Cổng học vụ CampusUTE

**Hệ thống học vụ song ngữ Việt–Anh:** đăng ký học phần, quản lý điểm, điểm danh, đồ án–khóa luận, truyền thông học vụ và trợ lý AI có nguồn tham chiếu.

[Tiếng Việt](README.vi.md) · [English](README.en.md) · [Tài liệu kỹ thuật đầy đủ](docs/technical/README.vi.md) · [Bộ ảnh/GIF hiện tại](docs/media-showcase.vi.md)

![Java 21](https://img.shields.io/badge/Java-21-orange?logo=openjdk)
![Spring Boot 4.1](https://img.shields.io/badge/Spring_Boot-4.1-green?logo=springboot)
![Next.js 15](https://img.shields.io/badge/Next.js-15-black?logo=nextdotjs)
![PostgreSQL 15](https://img.shields.io/badge/PostgreSQL-15-blue?logo=postgresql)

CampusCore là tên dự án/codebase; giao diện hiện dùng nhận diện **CampusUTE**. Backend là **Spring Boot modular monolith**, được tổ chức theo miền nghiệp vụ, cùng Next.js Web Portal và PostgreSQL/Flyway. RAG chạy trong một sidecar nội bộ. Expo mobile là client đang phát triển, dùng chung API Java.

## Mục lục

- [Tổng quan và vai trò](#overview)
- [Ảnh giao diện và GIF](#showcase)
- [Kỹ thuật toàn dự án](#techniques)
- [Kiến trúc](#architecture)
- [Công nghệ](#stack)
- [Chạy local](#quick-start)
- [Tài khoản demo](#demo)
- [Kiểm thử và ranh giới bằng chứng](#verification)
- [Tài liệu liên quan](#docs)

<a id="overview"></a>
## Tổng quan và vai trò

| Vai trò | Công việc chính | Ranh giới |
|---|---|---|
| Sinh viên | Đăng ký/rút môn, CTĐT, lịch, điểm, phúc khảo, điểm danh, rèn luyện, nhóm khóa luận | Hồ sơ của mình và tài nguyên được cấp quyền |
| Giảng viên | Lịch dạy, roster, điểm, điểm danh, thông báo/email, đề xuất–hướng dẫn–chấm khóa luận | Lớp/đề tài/phân công thực tế |
| Trưởng bộ môn / Trưởng khoa | Thẩm định và điều hành học thuật, xem tiến độ/khối lượng | Bộ môn/khoa và phân công chuyên môn hợp lệ |
| Admin / Super Admin | Tài khoản, danh mục, lớp, đợt, phúc khảo, audit, tri thức RAG | Authority quản trị và chốt nghiệp vụ tương ứng |

Tài khoản do **Phòng Đào tạo cấp phát**; không có tự đăng ký công khai. Giao diện login hiện có tab Sinh viên, Giảng viên, Trưởng khoa; đường vào quản trị được xử lý theo role/deep link. Quyền trên menu không thay thế kiểm tra ở service và database.

<a id="showcase"></a>
## Ảnh giao diện và GIF

Bộ ảnh chụp từ **build local mới theo source ngày 27/10/2026**, với PostgreSQL/demo riêng, không dùng dữ liệu production. Mỗi vai trò đăng nhập thật và kiểm danh tính qua API. [Nguồn chụp, toàn bộ ảnh và giới hạn](docs/media-showcase.vi.md) · [Manifest runtime](docs/assets/showcase-2026-10/provenance.json).

### Trang công khai và cổng đăng nhập

| Trang chủ Việt | Trang chủ Anh |
|---|---|
| ![Trang chủ Việt](docs/assets/showcase-2026-10/public-home-vi.png) | ![Trang chủ Anh](docs/assets/showcase-2026-10/public-home-en.png) |

| Cổng đăng nhập hiện tại | Hướng dẫn khôi phục tài khoản |
|---|---|
| ![Đăng nhập ba cổng hiện hành](docs/assets/showcase-2026-10/public-login-vi.png) | ![Khôi phục tài khoản](docs/assets/showcase-2026-10/public-recovery-vi.png) |

### Hành trình sinh viên

| Tổng quan | Đăng ký và kế hoạch CTĐT |
|---|---|
| ![Tổng quan sinh viên](docs/assets/showcase-2026-10/student-dashboard-vi.png) | ![Đăng ký học phần](docs/assets/showcase-2026-10/student-registration-vi.png) |

| Thời khóa biểu | Bảng điểm tích lũy |
|---|---|
| ![Lịch học](docs/assets/showcase-2026-10/student-schedule-vi.png) | ![Bảng điểm](docs/assets/showcase-2026-10/student-transcript-vi.png) |

| Điểm danh | Đồ án – khóa luận |
|---|---|
| ![Điểm danh sinh viên](docs/assets/showcase-2026-10/student-attendance-vi.png) | ![Khóa luận sinh viên](docs/assets/showcase-2026-10/student-thesis-vi.png) |

| Điểm rèn luyện | Trợ lý chuyên sâu, trả lời từ corpus kỹ thuật |
|---|---|
| ![Điểm rèn luyện](docs/assets/showcase-2026-10/student-conduct-vi.png) | ![Trả lời trợ lý chuyên sâu](docs/assets/showcase-2026-10/student-assistant-answer-vi.png) |

### Giảng viên và quản lý học thuật

| Điểm học phần | Email học vụ |
|---|---|
| ![Quản lý điểm](docs/assets/showcase-2026-10/lecturer-grades-vi.png) | ![Soạn email học vụ](docs/assets/showcase-2026-10/lecturer-mail-vi.png) |

| Khóa luận giảng viên | Tổng quan trưởng khoa |
|---|---|
| ![Khóa luận giảng viên](docs/assets/showcase-2026-10/lecturer-thesis-vi.png) | ![Quan sát khóa luận toàn khoa](docs/assets/showcase-2026-10/dean-dashboard-vi.png) |

### Quản trị

| Tài khoản và vai trò | Đợt đồ án – khóa luận |
|---|---|
| ![Người dùng](docs/assets/showcase-2026-10/admin-users-vi.png) | ![Quản trị đợt](docs/assets/showcase-2026-10/admin-thesis-vi.png) |

| Đăng ký mặc định theo CTĐT | Tri thức có kiểm duyệt |
|---|---|
| ![Chạy thử và áp dụng đăng ký CTĐT](docs/assets/showcase-2026-10/admin-curriculum-defaults-vi.png) | ![Quản trị tri thức](docs/assets/showcase-2026-10/admin-knowledge-vi.png) |

### GIF hành trình

GIF là **chuỗi màn hình thật**, mỗi màn hình giữ khoảng 4 giây; không phải video thao tác liên tục và không chứng minh các mutation đã được thực hiện.

**Sinh viên:** tổng quan → đăng ký → lịch → bảng điểm → khóa luận → trợ lý.

![Hành trình sinh viên tiếng Việt](docs/assets/showcase-2026-10/student-tour-vi.gif)

**Giảng viên và quản trị:** điểm → điểm danh → email → tổng quan quản trị → tri thức.

![Hành trình giảng viên và quản trị](docs/assets/showcase-2026-10/staff-tour-vi.gif)

**Quản trị khóa luận:** sinh viên → giảng viên → trưởng khoa → quản lý đợt → audit.

![Hành trình khóa luận](docs/assets/showcase-2026-10/thesis-governance-tour-vi.gif)

**Song ngữ:** đối chiếu trang chủ, dashboard sinh viên và trưởng khoa Việt/Anh.

![Giao diện Việt Anh](docs/assets/showcase-2026-10/language-tour.gif)

<a id="techniques"></a>
## Kỹ thuật toàn dự án

Bộ [tài liệu kỹ thuật Việt](docs/technical/README.vi.md) / [English technical guide](docs/technical/README.en.md) giải thích **42 chủ đề** với bài toán, luồng, invariant, lỗi, đánh đổi và nguồn mã/kiểm thử.

| Nhóm kỹ thuật | Điểm chính | Chi tiết |
|---|---|---|
| Kiến trúc và frontend | Modular monolith, service–repository, JDBC/JPA có giới hạn; Next BFF bảo toàn cookie/SSE, chống traversal; React Query theo danh tính; locale/ngày dân sự | [T01–T05](docs/technical/architecture-security.vi.md#t01-modular-monolith-và-sidecar-có-ranh-giới-rõ-ràng) |
| Bảo mật và phiên | JWT access/refresh riêng; kiểm authority hiện tại/credential revision; CSRF; 2FA; single-flight, Web Locks, rotation overlap; RBAC + scope; rate limit | [T06–T10](docs/technical/architecture-security.vi.md#t06-jwt-và-cookie-xác-thực-không-đồng-nghĩa-quyền-hiện-tại) |
| Đăng ký và CTĐT | Khóa Student/Section, tăng sĩ số có điều kiện, unique index, idempotency/hash; tiên quyết/lịch/tín chỉ; batch dry-run/apply và cursor; rút môn W | [T11–T14](docs/technical/academic.vi.md) |
| Điểm và hoạt động học tập | Nháp/công bố, điểm 50/50, GPA best attempt; phúc khảo có deadline/audit; điểm danh theo ngày và cảnh báo chính xác; rèn luyện; CSV/PDF/iCalendar | [T15–T18](docs/technical/academic.vi.md#t15-nhập-điểm-công-bố-và-bảng-điểm-tích-lũy) |
| Đồ án – khóa luận | Facade giữ transaction khi tách service; state machine theo loại đợt; khoa/bộ môn; điều kiện học thuật; nhóm/ngoại lệ; file rollback; GVPB; hội đồng; preview/công bố | [T19–T26](docs/technical/thesis.vi.md) |
| AI và tri thức | Weighted lexical RAG, difficulty routing, corpus scope, context cá nhân; SSE/heartbeat/cancel; quota/lease/CAS; RLS; kiểm duyệt hai người và guards | [T27–T32](docs/technical/assistant-engagement.vi.md) |
| Truyền thông | TinyMCE self-host + sanitizer hai tầng; audience/version/lịch thông báo; deep link an toàn; audit; email scoped recipients và DTO/client validation | [T33–T34](docs/technical/assistant-engagement.vi.md#t33-tinymce-bài-viết-thông-báo-và-audit) |
| Mobile và vận hành | Expo Bearer/refresh; Flyway; storage local/database/Supabase; Docker nhiều stage; readiness/timeout; backup/restore; CI/image identity; kiểm thử và tái tạo media | [T35–T42](docs/technical/operations-verification.vi.md) |

Các chốt quan trọng: khóa dòng tuần tự hóa truy cập vào bản ghi cạnh tranh, không tự bảo đảm toàn bộ transaction serializable; RAG lexical có trọng số, không phải BM25/vector search; cảnh báo điểm danh mang tính tư vấn; quota tính theo nhánh dispatch, không phải mọi lần bấm gửi; metadata báo cáo không chứng minh file còn tồn tại.

<a id="architecture"></a>
## Kiến trúc

![Kiến trúc hiện tại](docs/assets/showcase-2026-10/architecture-vi.png)

[SVG](docs/assets/showcase-2026-10/architecture-vi.svg) · [Chi tiết kiến trúc và ranh giới](docs/ARCHITECTURE.md)

```text
Web Next.js ── same-origin /api/v1 ──► Java REST API ──► PostgreSQL
Mobile Expo ───── Bearer JWT ─────────►       │
                                            ├──► Report storage
                                            ├──► SMTP (tùy chọn)
                                            └──► RAG sidecar nội bộ ──► DeepSeek (tùy chọn)
```

<a id="stack"></a>
## Công nghệ

| Tầng | Công nghệ và chủ sở hữu phiên bản |
|---|---|
| Java API | Java 21, Spring Boot 4.1, Spring Security/Validation, JDBC, JPA có giới hạn — [Maven](java-services/pom.xml) |
| Web | Next.js 15, React 18, TypeScript, React Query, Tailwind, Radix, TinyMCE — [package.json](frontend/package.json) |
| Dữ liệu | PostgreSQL 15, Flyway, constraint/trigger/index/RLS — [database image](database/Dockerfile), [migrations](java-services/restful-api/src/main/resources/db/migration/) |
| Mobile | Expo, React Native, TypeScript — [mobile manifest](mobile/package.json) |
| Tích hợp | RAG lexical nội bộ; DeepSeek, SMTP, Supabase storage theo cấu hình — [Compose](docker-compose.yml) |
| Kiểm thử/vận hành | JUnit/Mockito, PostgreSQL IT, node:test, Playwright, Docker/Compose, GitHub Actions — [CI](.github/workflows/ci.yml) |

<a id="quick-start"></a>
## Chạy local

Cần Docker/Compose đang hoạt động. Đọc `.env.example` và [runbook demo](docs/DEMO_RUNBOOK.md) trước khi chọn cổng, demo account hoặc SMTP.

```powershell
git clone https://github.com/JasonTM17/Student_Management_UTE.git
cd Student_Management_UTE
Copy-Item .env.example .env
# Chỉnh cấu hình local và secret theo .env.example.
docker compose up -d --build
docker compose ps
curl.exe http://127.0.0.1:4010/api/v1/health/liveness
```

| Dịch vụ | Host mặc định | Biến ghi đè |
|---|---|---|
| Web | 3000 | `FRONTEND_HOST_PORT` |
| API | 4010 | `RESTFUL_API_HOST_PORT` |
| PostgreSQL | 5432 | `POSTGRES_HOST_PORT` |
| Mailpit UI / SMTP | 8025 / 1025 | `MAILPIT_UI_HOST_PORT` / `MAILPIT_SMTP_HOST_PORT` |
| RAG | Chỉ nội bộ 4011 | Không publish ra host |

OpenAPI ở `/v3/api-docs`, Swagger ở `/swagger-ui.html` trên origin API. Readiness cần `X-Health-Key` đúng cấu hình. Compose chạy role provisioner trước migration; RAG chạy Flyway, API tắt Flyway. Mailpit có mặt không đồng nghĩa `MAIL_ENABLED` đã bật. Xem [hướng dẫn vận hành](docs/technical/operations-verification.vi.md) cho build, storage và restore.

<a id="demo"></a>
## Tài khoản demo

Chỉ dùng trong môi trường local/đánh giá đã chủ động bật `DEMO_ACCOUNTS_ENABLED=true`; Compose local opt-in, deployment công khai phải kiểm cấu hình khóa demo. `DemoAccountGate` áp lại chính sách mỗi lần boot; không mở khóa bằng SQL để lách gate.

| Vai trò | Tài khoản seed |
|---|---|
| Sinh viên | `student@campuscore.edu`, `student2@campuscore.edu`, `student3@campuscore.edu`, `student4@campuscore.edu` |
| Giảng viên | `lecturer@campuscore.edu` |
| Trưởng khoa | `truongkhoa@campuscore.demo` |
| Trưởng bộ môn | `truongbomon@campuscore.demo` |
| Admin / người duyệt tri thức thứ hai | `admin@campuscore.edu`, `admin002@campuscore.demo` |

Mật khẩu seed công khai cho demo là `password123`. Quick-fill còn cần opt-in build và các biến password theo [runbook](docs/DEMO_RUNBOOK.md); admin không có password prefill trong client. Các profile chỉ để minh họa có thể bị khóa theo chủ ý; không suy từ danh bạ rằng mọi tài khoản đều đăng nhập được. Email OTP/2FA và khôi phục phụ thuộc SMTP thật được cấu hình.

<a id="verification"></a>
## Kiểm thử và ranh giới bằng chứng

```powershell
npm test --prefix frontend
npm run typecheck --prefix frontend
npm test --prefix mobile
mvn -f java-services/pom.xml verify
node scripts/check-doc-hygiene.mjs
git diff --check
```

PostgreSQL race/RLS suites cần database tạm và cấu hình theo [CI](.github/workflows/ci.yml). [Ma trận bằng chứng](docs/technical/operations-verification.vi.md#t40-các-tầng-kiểm-thử-và-giới-hạn-bằng-chứng) phân biệt unit, H2, PostgreSQL, browser và provider thật. Bộ ảnh mới chứng minh render local và các request đã ghi trong manifest; không chứng minh tất cả mutation, độ chịu tải, email giao thành công hoặc production.

<a id="docs"></a>
## Tài liệu liên quan

- [Mục lục tài liệu](docs/README.md)
- [Kỹ thuật đầy đủ — Tiếng Việt](docs/technical/README.vi.md) / [English](docs/technical/README.en.md)
- [Bộ ảnh/GIF — Tiếng Việt](docs/media-showcase.vi.md) / [English](docs/media-showcase.en.md)
- [Kiến trúc](docs/ARCHITECTURE.md) · [Phân quyền](docs/security/authorization-model.md)
- [Quản trị nhóm khóa luận](docs/integrations/thesis-group-governance.md) · [Storage và khôi phục báo cáo](docs/integrations/thesis-report-storage.md)
- [Demo](docs/DEMO_RUNBOOK.md) · [Deployment](docs/deployment.md) · [Production runbook](docs/PRODUCTION_RUNBOOK.md) · [Backup](ops/backup/README.md)
- [Mobile](mobile/README.md) · [Release](docs/RELEASE.md)
