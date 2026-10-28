# Hướng Dẫn & Nhật Ký Triển Khai (Deployment Guide)

## 1. Cấu hình triển khai và địa chỉ được ghi nhận

Tài liệu đối chiếu cấu hình tại HEAD; không xác nhận trạng thái dịch vụ hiện tại. URL/domain dưới đây là địa chỉ được repository ghi nhận. Lịch sử bên dưới là ghi chép tại thời điểm phát hành, có SHA trước lần viết lại lịch sử 27/10/2026; không phải bằng chứng triển khai HEAD hay lượt kiểm thử mới. Xem [ghi chú release](RELEASE.md#history-rewrite-note).

| Thành phần | Nền tảng | Domain sản xuất | Trạng thái |
|---|---|---|---|
| **Frontend Web** | Vercel | `https://campusute.io.vn` / `https://www.campusute.io.vn` | Cần xác minh ở môi trường đích |
| **Backend REST API** | Render | `https://campuscore-backend-p4em.onrender.com` | Cần xác minh ở môi trường đích |
| **Database** | Neon PostgreSQL (Free, Singapore) | project `gentle-sunset-44685098` → `campusute_prod_20261015t060105z` | Cutover được ghi nhận 15/10/2026; chưa xác minh hiện tại |
| **DB Rollback** | Supabase PostgreSQL | `https://mjodncaunvjekwlgyclg.supabase.co` | Snapshot lịch sử 15/10; workflow heartbeat ghi nguồn đã nghỉ, không phải standby hiện tại |
| **Mã nguồn Git** | GitHub | `https://github.com/JasonTM17/Student_Management_UTE` | Nguồn cấu hình; trạng thái remote chưa kiểm tra |

---

## 2. Quy Trình Triển Khai Frontend (Vercel)

- **Project Name được ghi nhận**: `campusute`. `frontend/vercel.json` chỉ ghim region `sin1`; domain, root directory và auto-deploy cần kiểm tra riêng trong cấu hình dự án Vercel.
- **Lệnh triển khai Production** (Vercel CLI, cần xác thực và thẩm quyền phát hành riêng):
  ```bash
  cd frontend
  npx vercel --prod --yes
  ```
- **Biến môi trường thiết yếu**:
  - `NEXT_PUBLIC_SITE_URL`: `https://campusute.io.vn`
  - `BACKEND_INTERNAL_ORIGIN`: origin API được ghi nhận ở trên; proxy ưu tiên `JAVA_API_ORIGIN` nếu biến này tồn tại.
  - `NEXT_PUBLIC_API_URL`: đường dẫn cùng origin `/api/v1`; thay đổi `NEXT_PUBLIC_*` cần build lại. Không đưa secret vào biến public.
- **Tùy chỉnh Project Avatar trên Vercel**:
  - Truy cập: Vercel Dashboard -> `campusute` -> Settings -> General -> Project Avatar.
  - Tải lên file: `frontend/public/icon.png` (512x512 Logo CampusUTE).

---

## 3. Quy Trình Triển Khai Backend (Render)

- **Service Name**: `campuscore-backend`
- **Cơ chế cấu hình**: `render.yaml` dùng Dockerfile `java-services/restful-api/Dockerfile`, context gốc, region Singapore và plan free; file không ghim nhánh hoặc `autoDeploy`. Chính sách auto-deploy thực tế cần xác minh trong dashboard, không suy từ workflow xuất image.
- **Topology**: Blueprint chạy API đơn lẻ, bật Flyway, dùng SQL knowledge authority theo mặc định ứng dụng; không khai báo RAG sidecar. `THESIS_REPORT_STORAGE_PROVIDER=database`, `APP_KEEPALIVE_ENABLED=false`, cookie secure và demo accounts được bật. Datasource/assistant credentials, SMTP và các secret phải được cấu hình ở runtime; không công bố giá trị. Compose local và production tự host có topology riêng.
- **Quota**: Blueprint đặt `ASSISTANT_USER_DAILY_QUOTA` cao hơn trần code; `AssistantProperties` vẫn chặn ở 20/người/ngày và 200 toàn cục/ngày. Không suy ra quota hiệu lực chỉ từ giá trị dashboard.
- **Health Check Endpoint**:
  ```http
  GET https://campuscore-backend-p4em.onrender.com/api/v1/health/liveness
  ```
  Phản hồi có `status=ok`, `service=restful-api` và `timestamp`; liveness không kiểm DB. Readiness ở `/api/v1/health/readiness` yêu cầu `X-Health-Key`, trả tình trạng DB và trạng thái RLS riêng.

### 3.1. Cấu hình Chatbot AI & Định Tuyến Phân Tầng (Tiered Difficulty Routing)

Hệ thống trợ lý học vụ AI (`ThesisAssistantService`) áp dụng kiến trúc định tuyến thông minh 2 tầng:

1. **Câu hỏi thông thường & Tra cứu quy chế (RAG Bot nội bộ)**:
   - Tra cứu học vụ dùng weighted lexical retrieval từ snapshot release đang công bố, không phải mô hình được huấn luyện. Fast path đủ confidence trả `curated-lexical-fast` / `ANSWERED`; nhánh RAG không cần synthesis trả `curated-lexical-rag` / `RAG_GROUNDED`.
   - Các nhánh lexical này không dispatch provider; kiểm tra trích dẫn theo corpus. Không có cam kết độ trễ hoặc đúng tuyệt đối cho mọi câu hỏi.

2. **Câu hỏi quá khó / Phân tích tổng hợp (Escalation DeepSeek V4 Flash)**:
   - `AssistantDifficultyRouter` tự động kiểm tra và nhận diện câu hỏi khó khi:
     - Câu hỏi dài (chuỗi chuẩn hóa > 110 ký tự hoặc >= 8 từ tách bởi khoảng trắng).
     - Chứa từ khóa phức tạp / lập luận: `"so sánh"`, `"khác nhau"`, `"tại sao"`, `"vì sao"`, `"như thế nào"`, `"làm thế nào"`, `"hướng dẫn"`, `"các bước"`, `"ngoại lệ"`, `"trường hợp"`, `"nếu"`, `"compare"`, `"why"`, `"how"`, `"steps"`,...
     - Dữ liệu liên quan trải rộng trên $\ge$ 3 tài liệu hoặc $\ge$ 2 phân hệ tri thức độc lập.
   - Với kết quả retrieval không rỗng và provider usable, hệ thống có thể gọi mô hình `deepseek-v4-flash`, nạp context trích xuất từ tài liệu học vụ nội bộ để mô hình tổng hợp phân tích đa chiều cho sinh viên.
   - **Cơ chế an toàn (Graceful Fallback)**: Khi chưa bật DeepSeek hoặc API Key hết hạn, hệ thống tự động fallback về câu trả lời RAG nội bộ (`degraded: true`), đây không phải cam kết không gián đoạn: lỗi DB/xác thực/RLS vẫn có thể trả lỗi. Nhánh `generalAnswerIfOffTopic` riêng có thể gọi provider không có citation cho câu ngoài học vụ; xem [hợp đồng assistant](integrations/deepseek-assistant.md).

3. **Hướng dẫn thêm DeepSeek API Key vào Render Dashboard**:
   - **Bước 1**: Đăng nhập vào [Render Dashboard](https://dashboard.render.com).
   - **Bước 2**: Chọn Web Service `campuscore-backend`.
   - **Bước 3**: Chọn tab **Environment** ở cột điều hướng bên trái.
   - **Bước 4**: Thêm hoặc cập nhật các biến môi trường:
     - `DEEPSEEK_ENABLED`: `true`
     - `DEEPSEEK_API_KEY`: `<API Key bắt đầu bằng sk-...>` (Lấy từ [DeepSeek Platform](https://platform.deepseek.com))
     - `DEEPSEEK_MODEL`: `deepseek-v4-flash` *(Đã đặt mặc định chuẩn)*
     - `DEEPSEEK_BASE_URL`: `https://api.deepseek.com` *(Đã đặt mặc định chuẩn)*
   - **Bước 5**: Nhấn **Save Changes**. Render sẽ tự động redeploy phiên bản mới có kích hoạt kết nối DeepSeek.

### 3.2. Hạn mức tín chỉ đăng ký

- `REGISTRATION_SUMMER_MAX_CREDITS` (property `registration.summer-max-credits`, mặc định `10`) cấu hình giới hạn tín chỉ thông thường trong học kỳ hè. Máy chủ nhận diện học kỳ hè bằng `Semester.type = SUMMER`; hạn mức của đợt đăng ký vẫn có thể đặt thấp hơn. Mức `8` trong quy chế không được áp làm trần mặc định.
- `REGISTRATION_WITHDRAWAL_WINDOW_DAYS` (property `registration.withdrawal.window.days`, mặc định `14`) cấu hình số ngày đầu học kỳ chính (`FIRST`/`SECOND`, trạng thái `OPEN`) mà sinh viên được nộp đơn rút học phần có phê duyệt — điểm `W` trên bảng điểm, không tính GPA/tín chỉ. Tính từ `Semester.startDate` theo giờ Asia/Bangkok, ngày cuối của cửa sổ được tính trọn ngày; đơn đang chờ vẫn được xét duyệt sau khi cửa sổ đóng.
- `GRADE_APPEAL_WINDOW_DAYS` (property `grade.appeal-window-days`, mặc định `7`) cấu hình thời hạn sinh viên gửi một lần phúc khảo điểm cuối kỳ đã công bố. Thời hạn tính đủ số ngày từ `Enrollment.gradePublishedAt` và thời điểm cuối được tính bao gồm. V147 ghi thời điểm khi công bố, xóa khi gỡ công bố, và đặt thời điểm chạy migration cho các điểm `PUBLISHED`/`APPEALED` hiện có còn NULL. Vì vậy, các điểm đã công bố trước khi có tính năng nhận **một cửa sổ ân hạn mới** bắt đầu từ lúc triển khai V147; chạy lại migration không làm mới các thời điểm đã có.
- `ATTENDANCE_EDIT_WINDOW_DAYS` (property `attendance.edit-window-days`, mặc định `30`): giảng viên chỉ tạo/sửa/`RESET` điểm danh trong N ngày gần nhất theo ngày campus (Asia/Bangkok, ngày biên được tính trọn); bản ghi cũ hơn chỉ `ADMIN`/`SUPER_ADMIN` đổi được, lỗi `409 ATTENDANCE_EDIT_WINDOW_CLOSED`. `ATTENDANCE_MIN_RATE` (`attendance.min-rate`, mặc định `0.80`) và `ATTENDANCE_MIN_SESSIONS` (`attendance.min-sessions-for-signal`, mặc định `5`) điều chỉnh tín hiệu tư vấn đủ điều kiện thi trên read model — chỉ thông tin, không tự động chặn thi hay đăng ký.
- Đơn nâng hạn mức đã được duyệt tiếp tục theo hành vi hiện có, kể cả ở học kỳ hè. Giới hạn tối thiểu `14` chỉ là thông tin tư vấn trên trang đăng ký và không chặn việc thêm từng học phần. Dữ liệu hiện không xác định chắc chắn học kỳ cuối, vì vậy Phòng Đào tạo phải áp dụng ngoại lệ học kỳ cuối thủ công khi cần.
- Đăng ký theo chương trình đào tạo (curriculum default) không thêm biến môi trường mới. Cột đánh dấu nguồn `academic."Enrollment"."registrationSource"` (V146, `VARCHAR(40)`, nullable) phân biệt đăng ký do hệ thống tạo (`CURRICULUM_DEFAULT`) với đăng ký thủ công (`NULL`); hàng bị sinh viên huỷ giữ nguyên dấu nguồn để lần áp dụng loạt sau không tự thêm lại. Giá trị này chỉ đọc qua API, không cấu hình được.

### 3.3. Cấu hình runtime dùng chung

Backend đọc các cấu hình dưới đây. Các biến ghi đè thời hạn phiên, tên cookie và CORS chưa có trong `.env.example` hoặc Compose; Compose không chuyển tiếp biến giới hạn tốc độ, còn `.env.example` chỉ liệt kê hai tuỳ chọn tin cậy proxy. Cấu hình mail và SMTP đã có trong `.env.example` và Compose. Mặc định cùng tên property được khai báo trong [application.yml](../java-services/restful-api/src/main/resources/application.yml); CORS có credential do [SecurityConfig](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/config/SecurityConfig.java) quản lý.

| Nhóm | Biến và mặc định |
|---|---|
| Phiên | `JWT_ACCESS_TOKEN_TTL_SECONDS=900`, `JWT_REFRESH_TOKEN_TTL_SECONDS=604800`; cookie mặc định `ACCESS_TOKEN_COOKIE=cc_access_token`, `REFRESH_TOKEN_COOKIE=cc_refresh_token`, `CSRF_COOKIE=cc_csrf`, header `CSRF_HEADER=X-CSRF-Token`; `COOKIE_SECURE=false`. |
| Email | `MAIL_ENABLED` mặc định `true` trong ứng dụng; `.env.example` và Compose đặt `false`. SMTP dùng `MAIL_FROM` và `SPRING_MAIL_*` như trong `.env.example`. |
| Giới hạn tốc độ | `RATE_LIMIT_ENABLED=true`; `RATE_LIMIT_LOGIN=15`, `RATE_LIMIT_DEFAULT=40`, `RATE_LIMIT_WINDOW_SECONDS=60`; `RATE_LIMIT_TRUST_PROXY_HEADERS` và `RATE_LIMIT_TRUST_REAL_IP` mặc định `false`. Khi dùng xác thực IP ở Next.js proxy, đặt cùng giá trị bí mật cho `CAMPUSCORE_PROXY_TOKEN` (web) và `RATE_LIMIT_PROXY_TOKEN` (API); cả hai hiện chưa có trong ví dụ môi trường hoặc Compose. Các bucket mail, thesis và enrollment được cố định trong [RateLimitFilter](../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/ratelimit/filter/RateLimitFilter.java). |
| CORS | `APP_CORS_ALLOWED_ORIGIN_PATTERNS` mặc định allowlist localhost và hai domain CampusUTE; chỉ thêm origin triển khai cụ thể, không dùng wildcard công khai. |

---

## 4. Tối Ưu Hóa Nhận Diện Thương Hiệu & SEO

- **Logo chính thức**: Huy hiệu CampusUTE mang chuẩn phong cách HCMUTE (Xanh Hoàng gia `#002D62` kết hợp Vàng cam `#F59E0B`).
- **SEO & Social Preview**:
  - Schema.org JSON-LD: `CollegeOrUniversity` (HCMUTE), `WebApplication`, `WebSite`.
  - OpenGraph / Twitter Banner: Tự động render 1200x630 chuẩn nhận diện tại `/opengraph-image`.
  - Sitemap: được sinh tại `/sitemap.xml` từ các route công khai đã khai báo; không phải crawler tự phát hiện mọi trang.
  - Robots: `/robots.txt` hướng dẫn crawler tránh route nội bộ; kiểm soát truy cập vẫn do xác thực/phân quyền, không phải robots.

---

## 5. Nhật ký lịch sử (chưa xác minh lại tại HEAD)

- **Thời gian xác minh**: 14/09/2026 17:38 (UTC+7)
- **Git Commit**: `c4f67886` (`origin/main`)
- **Vercel Production**:
  - URL triển khai: `https://www.campusute.io.vn` / `https://campusute.io.vn`
  - HTTP Status: `200 OK`
  - Build: Next.js 15 App Router hoàn tất sạch sẽ (`dpl_H1zYp8H4tuUFB9ECPHKxaT6ju8iN`).
- **Render Backend REST API**:
  - URL: `https://campuscore-backend-p4em.onrender.com`
  - Endpoint liveness: `{"service":"restful-api","status":"ok"}`
  - Supabase Flyway Migrations: Đã tự động áp dụng thành công V40, V41, V42 nâng tổng tri thức lên 81 văn bản song ngữ (`local-demo-v42`).
- **Trợ lý Học vụ AI Live Production (RAG + DeepSeek V4 Flash)**:
  - **Truy vấn Tiên quyết vs Học trước**: Khớp chính xác tài liệu `prerequisite-prior-corequisite-vi` (Rank 1), trích dẫn và phân biệt rõ 3 loại môn học.
  - **Truy vấn Điểm rèn luyện**: Định tuyến thành công tới DeepSeek V4 Flash (`model: deepseek-v4-flash`, `degraded: false`), tổng hợp 5 tiêu chí thang điểm 100 từ tài liệu `student-conduct-score-handbook-vi`.
  - **Truy vấn Thời hạn học phí**: DeepSeek V4 Flash giải đáp chi tiết mốc 4 tuần và chế tài nợ học phí từ tài liệu `tuition-payment-deadline-rules-vi`.

### Phát hành giao diện đăng ký học phần — 07/10/2026 (UTC+7)

Phát hành phần xem học phần đã đăng ký và lịch tuần, các sửa lỗi/tăng cường U01–U08, E01–E06 cùng những sửa nền liên quan. Rationale và điểm sở hữu nằm trong [kiến trúc giao diện](ARCHITECTURE.md); các kịch bản kiểm chứng nằm trong [kiểm thử đăng ký](../frontend/e2e/registration-details.spec.ts).

- **Nguồn triển khai**: commit `bdcc922909a910f50acdb423461e85574157e133`, cây frontend `dd64b051a1b3f7fd327ae52b98189b28168a18bb`. Metadata Vercel `gitCommitSha` và `releaseCommit` đều khớp commit này. Commit tài liệu bổ sung sau phát hành không thay thế SHA nguồn triển khai.
- **Vercel Production**: `dpl_5suCtSu3UpT9HriStRnNPG2PtsQR`, trạng thái **READY**, [URL deployment](https://campusute-m4p3qmh0i-nguyen-sons-projects-4f98af92.vercel.app). Alias [campusute.io.vn](https://campusute.io.vn) đã được xác nhận trỏ tới đúng deployment này.
- **Đầu vào**: xuất từ cây frontend đã commit, không sao chép `.env`, `node_modules`, `.next` hoặc trạng thái xác thực. Cấu hình Vercel `rootDirectory=frontend` yêu cầu giữ thư mục `frontend` dưới gốc gói triển khai; lần đầu dừng trước build do bố trí sai thư mục, lần tiếp theo chỉ sửa cách đóng gói, không sửa nguồn.
- **Kiểm chứng trước phát hành được ghi nhận**: unit, lint, typecheck, build và browser đăng ký/lịch; viewport tùy chọn từng bị bỏ qua khi thiếu server cổng 4317. Không dùng tổng test lịch sử làm số liệu hiện tại; các lane/command hiện hành nằm trong [CI](../.github/workflows/ci.yml). Review lịch sử không thay thế review HEAD.
- **Kiểm chứng sau phát hành**: trang công khai VI/EN HTTP 200; đăng nhập demo thực HTTP 200. Các hành trình đọc dữ liệu thực VI/EN tại độ rộng 390/768/1440 được ghi nhận pass ở lần đó: API học phần HTTP 200, có thẻ học phần, xem lịch/chuyển focus, lịch sử URL/tải lại và không tràn ngang; không đăng ký/rút học phần. Đây là bằng chứng trình duyệt theo viewport, chưa chứng minh thiết bị vật lý, trình đọc màn hình thực hay toàn bộ WCAG.
- **Ranh giới**: chỉ triển khai frontend; backend liveness HTTP 200 là kiểm tra dịch vụ hiện hữu. Không phát hành backend, schema hoặc dữ liệu; không Git push, không có lượt CI cho phát hành này.
- **Khôi phục**: deployment trước là `dpl_G5n256qdHSgw2kk453nTXZaTpeaF` ([URL](https://campusute-j1ada315h-nguyen-sons-projects-4f98af92.vercel.app)). Lệnh đã lưu: `vercel rollback dpl_G5n256qdHSgw2kk453nTXZaTpeaF --yes`; **chưa thực thi rollback**.



### Cutover Database: Supabase → Neon Free (Singapore) — 15/10/2026 (UTC)

Chỉ chuyển **database**; backend vẫn trên Render, frontend vẫn trên Vercel. File báo cáo khóa luận không thuộc phạm vi (vẫn lưu `local` trên Render — mất khi redeploy; 8 record `s3` chỉ là metadata seed, không có blob nào trong DB).

- **Cửa sổ ngưng ghi**: ~06:00–06:12Z. Write-fence `campusute_cutover_20261015t060039z` cài trigger `reject_write` trên 71 bảng nghiệp vụ của nguồn trước khi chụp; probe ghi xác nhận bị chặn (SQLSTATE 25006).
- **Snapshot**: `pg_dump --snapshot` nhất quán, 3.321.916 bytes, sha256 `67b3bbece0fcda31f307acf5628d548618174f38c78a1814fb8e6d754348e91f`, manifest `neon-source-snapshot-20261015T060105Z.json` (71 bảng, 59 policy, sequence + grant runtime).
- **Đích**: database cô lập `campusute_prod_20261015t060105z` trên project `gentle-sunset-44685098` (ap-southeast-1). 71 trigger fence được lọc khỏi TOC trước restore (hàm guard ở schema ngoài phạm vi dump).
- **Kiểm chứng sau restore**: các kiểm tra được ghi nhận pass tại thời điểm đó, chưa chạy lại — checksum MD5 từng bảng khớp nguồn, sequence, policy RLS (canonical hóa biểu thức), FK hợp lệ, không grant PUBLIC vào schema `assistant`, verifier RLS đúng của ứng dụng chạy bằng role `campuscore_assistant_runtime` (13 bảng RLS/FORCE, 59 policy, connlimit 8), probe từ chối truy cập sai chủ PASS.
- **Bất biến nghiệp vụ**: Flyway `116` success; nhóm demo `SUBMITTED`/`PENDING`, Sơn nhóm trưởng, đủ 4 thành viên ACTIVE; Vũ Quốc Huy 0 đăng ký/0 điểm, role STUDENT.
- **Chuyển đổi**: cập nhật `SPRING_DATASOURCE_*` + `ASSISTANT_DB_*` trên Render → Neon; deploy `dep-db86riou01pc73eofudg` LIVE. `lastLoginAt` ghi mới vào Neon xác nhận app đang dùng đúng đích.
- **Kiểm chứng production sau cutover**: `prod-smoke` được ghi nhận pass tại thời điểm đó (chưa chạy lại) (6 login, roster 4 người, giảng viên thấy nhóm pending, assistant grounded, admin tìm được thành viên mới); trình duyệt thật student4: đăng nhập → dashboard → trang khóa luận hiển thị đủ 4 tên, 0 console error.
- **Quota Neon Free — BLOCKED cho cam kết dài hạn**: API quản trị xác nhận plan `free_v3`, Singapore, compute min=max `0.25 CU`; `suspend_timeout_seconds=0` nghĩa là dùng mặc định của plan (Free: 300 giây), không phải tắt autosuspend. Snapshot API lúc `2026-10-15T06:26Z` báo `compute_time_seconds=996` (= khoảng `0.277 CU-h`), egress `1,501,595 bytes` và logical size của branch khoảng `82.08 MiB`; đây là bộ đếm tích lũy có độ trễ, không phải dự báo tháng. Rút lại ước lượng `15–25 CU-h/tháng`: nếu luôn chạy ở `0.25 CU`, kịch bản 30 ngày là `0.25 × 24 × 30 = 180 CU-h`, vượt hạn Free công bố `100 CU-h/project/tháng`. Đổi riêng job thông báo sang 15 phút **không đủ**: `ThesisAssistantRetentionJob` vẫn truy vấn lease mỗi 30 giây; `DatabaseKeepAliveScheduler` lúc đó mặc định bật và ping `SELECT 1` mỗi 4 giờ (HikariCP `keepaliveTime` mặc định là 0 — không có ping idle nào từ pool). Lượt quan sát metadata từ `06:24:47Z` đến `06:31:47Z` ngày 15/10 ghi nhận `8/8` mẫu đều `active`, không có mẫu `idle`; không gửi SQL/health/browser từ lượt quan sát này và không thể loại trừ lưu lượng của client khác. Chưa xác minh autosuspend độc lập với Render ngủ. Không tắt privacy purge hoặc nâng gói trả phí để làm đẹp số quota. Bản local được người dùng duyệt bổ sung recovery theo hoạt động: vẫn đánh lịch mỗi 30 giây, quét nhanh trong 5 phút kể từ lời gọi tạo lease, quét dự phòng mỗi 15 phút kể từ lần quét thành công, luôn quét ở tick đầu sau khởi động và tiếp tục ở tick kế khi lỗi hoặc backlog đầy 100 lease. Trong trường hợp bảo thủ, lần quét dự phòng có thể cách hoạt động cuối khoảng 20 phút cộng độ trễ scheduler; retry cùng request vẫn dùng recovery nội tuyến. `ASSISTANT_RECOVERY_ACTIVITY_AWARE=false` khôi phục quét mỗi tick. Hai pool local đặt minimum-idle=0, idle-timeout=60000 ms, keepalive-time=0; các giá trị có thể ghi đè qua môi trường. Đây là tình trạng ở lần ghi nhận đó; source HEAD đã có recovery theo hoạt động và cấu hình keepalive tắt nêu ở mục sau. Autosuspend thực tế và mức dùng theo tháng trên Neon vẫn `NOT_RUN`, không suy ra tiết kiệm từ test H2/local. Nguồn: [Neon plans](https://neon.com/docs/introduction/plans), [scale to zero](https://neon.com/docs/introduction/scale-to-zero).
- **Rollback — không phải đồng bộ hai chiều**: Supabase giữ snapshot trước cutover và vẫn bị write-fence; các lượt ghi sau cutover chỉ nằm trên Neon. **Không đổi thẳng env về Supabase rồi gỡ fence**: cách đó sẽ bỏ sót dữ liệu mới. Để quay lại mà bảo toàn ghi mới, phải có cửa sổ bảo trì được duyệt, fence các writer trên Neon, chụp bản Neon mới, diễn tập khôi phục vào DB đích cô lập tương thích phiên bản và đối chiếu dữ liệu/RLS trước khi chuyển kết nối; chưa diễn tập reverse migration nên gate này là `NOT_RUN`. Khôi phục đúng snapshot cũ chỉ là phương án mất dữ liệu sau cutover, cần chấp thuận riêng cho phạm vi mất dữ liệu đó. Env cũ nằm trong `.devin/neon-migration/rollback-render-environment-20261015t060039z.json` (chứa secret, không commit/in log). Helper `neon-cutover-fence.py release` đọc env Render hiện tại và chặn host ngoài Supabase; sau cutover không chạy trực tiếp với config Neon. Chỉ gỡ fence nguồn sau khi đích và quyền ghi đã được kiểm soát. Không xóa DB diễn tập hoặc Supabase trong lượt audit này.

### Ngủ đông cơ sở dữ liệu — trạng thái đã áp dụng — 23/10/2026 (UTC+7)

Mục tiêu: để Neon Free autosuspend (~300 giây không query) thay vì chạy liên tục ~180 CU-h/tháng so với hạn 100.

- Pool chính `minimum-idle=0`, `idle-timeout=60000`, `keepalive-time=0` (mặc định Hikari — pool không tự ping kết nối idle); pool assistant `idle-timeout=60000`, `keepalive=0`.
- `DatabaseKeepAliveScheduler` mặc định **tắt** (`matchIfMissing=false`, `app.keepalive.enabled` mặc định `false`, `render.yaml` ghim `APP_KEEPALIVE_ENABLED=false`).
- Job thông báo đặt lịch `engagement.announcement-notify.delay-ms` mặc định `900000` (15 phút): thông báo đặt lịch có thể đến inbox muộn tối đa một chu kỳ quét; thông báo đăng ngay không qua job này.
- Recovery trợ lý giữ nguyên: quét nhanh 5 phút sau hoạt động, quét dự phòng mỗi 15 phút, mỗi tick 30 giây chỉ đọc bộ nhớ khi idle.
- Demo-day overrides (đánh đổi tiết kiệm lấy độ trễ): `DB_POOL_IDLE_TIMEOUT_MS` + `DB_POOL_KEEPALIVE_MS` (ví dụ `300000`/`120000`), `APP_KEEPALIVE_ENABLED=true`, `ENGAGEMENT_ANNOUNCEMENTNOTIFY_DELAYMS` (ví dụ `300000`).

### Phát hành tăng cường bảo mật & audit cuối — 18/10/2026 (UTC+7)

Hai PR squash-merge kế tiếp chốt đợt audit toàn diện (cross-role thesis, auth lifecycle, export, runtime):

- **Nguồn triển khai**: `dd92c747` (PR #62 — hợp nhất nội dung branch còn lại + `V123`), `cd69c702` (PR #63 — dean faculty scope, thu hồi lecturer deactivate, vá CSV formula, vá race logout/rotation, compose `ASSISTANT_DB_*` + mail opt-in), `925aff5a` (đồng bộ chiều đọc đề tài/GVHD của Trưởng khoa), `05fe8300` + `fc5471ed` (`V124` + `V125` đồng bộ mật khẩu/session demo role `truongkhoa`/`truongbomon`/`admin002`, tách `TRUONG_KHOA` khỏi `admin-user`, hỗ trợ đầy đủ `TRUONG_BO_MON` trên `/admin/users` và siết `prod-smoke.mjs`).
- **Render Backend**: auto-deploy từ `main`; liveness `{"status":"ok"}`; `node scripts/prod-smoke.mjs` được ghi nhận pass tại thời điểm đó (chưa chạy lại) (9 tài khoản demo, 4 thành viên nhóm KLTN, GVHD, `/thesis/dean/overview` của Trưởng khoa, phân cấp đề tài `Admin 41 > Dean 16 > TBM 11`, quy trình Four-Eyes duyệt tri thức RAG giữa `admin` và `admin002`).
- **Vercel Production**: chunk thesis và `/admin/users?role=TRUONG_BO_MON` hiển thị đúng vai trò `Trưởng bộ môn` và các guard mới.
- **Git hygiene**: 10 branch đã merge/patch-equivalent xóa local + remote (verify bằng `git cherry`/diff trước khi xóa); draft PR #60 đóng; repo chỉ còn `main`.
- **Rollback**: giữ nguyên phương án Render manual rollback và `vercel rollback` — **chưa thực thi**, `NOT_RUN`.
- **Ranh giới honest**: luồng mail 2FA/reset `NOT_RUN` trên prod (chưa cấu hình provider thật); đường compose `ASSISTANT_DB_*` là bề mặt self-hosted — Render đã mang env này từ cutover Neon; diff nội dung hàng V123 `NOT_RUN` (không có quyền đọc DB trực tiếp).

### Cấu hình thu hồi công bố kết quả khóa luận (thesis results retraction)

- `THESIS_RESULTS_RETRACTGRACEDAYS` (property `thesis.results.retract-grace-days`, mặc định `7`): số ngày sau `RESULTS_PUBLISHED` mà admin toàn cục được phép thu hồi công bố qua `POST /api/v1/thesis/rounds/{id}/retract-results`.
- Thu hồi đòi lý do + xác nhận gõ `RETRACT`; không bao giờ sửa điểm; inbox "kết quả đã công bố" bị thu hồi và sinh viên nhận thông báo chỉnh chính; email đã gửi **không** thu hồi được (ghi rõ trong audit). Vòng quay về `REGISTRATION_CLOSED` để chạy lại luồng preview → publish bình thường.
- Sai sót phát hiện sau khi hết hạn ân hạn đi qua đường khiếu nại từng đề tài (chưa xây dựng — follow-up sản phẩm).

### Cấu hình điều kiện đăng ký khóa luận (thesis eligibility)

- `THESIS_ELIGIBILITY_MIN_CREDITS` (`thesis.eligibility.min-credits`, mặc định `110`): số tín chỉ tích lũy tối thiểu theo quy chế trước khi sinh viên được đăng ký khóa luận.
- `THESIS_ELIGIBILITY_MIN_CPA` (`thesis.eligibility.min-cpa`, mặc định `2.0`): CPA tối thiểu trên thang 4.0. Cả hai số đọc từ bảng điểm chuẩn (điểm đã công bố, lần học tốt nhất mỗi học phần).
- `THESIS_ELIGIBILITY_ENFORCEMENT` (`thesis.eligibility.enforcement`, mặc định `ADVISORY` kể từ release 2): `ENFORCE` từ chối tạo nhóm/thêm thành viên/chọn đề tài bằng `409 THESIS_ELIGIBILITY_NOT_MET` kèm `details` số liệu; `ADVISORY` vẫn tính và hiển thị trong read model (`enforced=false`) nhưng không chặn. Giá trị không nhận diện được được xử như `ADVISORY` và cảnh báo lúc khởi động (rollout an toàn: không bao giờ chặn ngoài ý muốn). Override roster của governance không bị chặn nhưng ghi nhận tình trạng không đủ điều kiện vào audit.
- `THESIS_ELIGIBILITY_ROUND_TYPES` (`thesis.eligibility.round-types`, mặc định `KLTN`): danh sách loại đợt (phẩy) mà quy chế tín chỉ áp dụng — quy chế chỉ nói khóa luận tốt nghiệp (KLTN); `TLCN`, `MON_HOC`, `NCKH` không bị chặn và read model không trả `eligibility` cho các loại đó.
- Quyền riêng tư: sinh viên chỉ thấy `eligible`/số liệu của chính mình trong roster và lỗi 409; giảng viên/head/dean/admin thấy đầy đủ.
- V143 bổ sung enrollment lịch sử cho bộ demo đã tồn tại, không tạo nhóm/đề tài mới. Seed phụ thuộc học phần và học kỳ đã có; tổng tín chỉ/CPA và số dòng thêm cần kiểm tra trên DB đích thay vì dùng số lịch sử làm oracle. Tài khoản chính là `student@campuscore.edu`; ngưỡng điều kiện được đọc từ transcript chuẩn.
