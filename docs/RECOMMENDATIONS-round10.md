# Khuyến nghị vòng 10 — Deep scan toàn hệ thống (2026-09-24)

> Vòng 10 chạy đúng quy trình AgentKit: **6 subagents scout song song** (backend security/concurrency,
> frontend quality, review diff pending, tests/CI/hygiene, data model/migrations, RAG/Supabase/deploy/mobile)
> → **hội đồng 3 role** (Wukong phản biện 5 tuyên bố trọng yếu, Kongming kiến trúc & sequencing,
> Advisor ưu tiên theo giá trị bảo vệ đồ án). Toàn bộ finding trong tài liệu này là **MỚI** so với
> `docs/RECOMMENDATIONS.md` (vòng 7–9); các mục đã biết không lặp lại.
> Ký hiệu: VERIFIED = đã đọc code/bằng chứng trực tiếp; SUSPECTED = suy luận có căn cứ.

---

## 0. Kết luận tổng (verdict hội đồng)

Hệ thống healthy ở lõi (4-lớp chống race, four-eyes SQL-level, 384 test FE xanh, tsc/eslint sạch),
nhưng **"tuyên bố hay nhất đang vượt xa dây nối yếu nhất"**. Ba rủi ro lớn nhất đều không phải lỗi
nghiệp vụ mà là *độ trung thực và độ bền của bằng chứng*:

1. **G2 — Bom checksum Flyway**: 12/80 migration từng bị sửa sau khi tạo → môi trường đã apply
   bản cũ sẽ **chết khi boot lần deploy tới**.
2. **Pending diff 16 file** được đánh giá commit-ready, nhưng **2 spec Playwright mới chưa từng
   chạy** (và CI vốn không chạy e2e — C1).
3. **Render production chạy kiến trúc khác với kiến trúc cutover đã "chứng minh"** trong compose →
   mọi câu "cutover proven" hiện tại sai phạm vi.

Hành động số 1, trước mọi thứ khác: **chạy 2 spec e2e mới trên stack cô lập → xanh thì commit
pending diff**. Ước tính 4–6 ngày làm việc tập trung cho nhóm ưu tiên 1–6 bên dưới là đủ để khép
mọi finding có khả năng làm hỏng demo hoặc phản bội tuyên bố.

---

## 1. Findings mới đã xác minh (theo domain, severity giảm dần)

### 1.1 Dữ liệu & Migration (G)

| # | Mức | Finding | Bằng chứng | Fix | Effort |
|---|---|---|---|---|---|
| G2 | **HIGH** | 12 migration sửa sau khi tạo → checksum mismatch time bomb: V1, V2, V3, V8, V25, V34, V52, V54, V64, V65, V68, V73. V48 tự viết "Never edit this file once applied" | `git log --diff-filter=M` trên `db/migration`; `validate-on-migrate` default true | `flyway repair` từng env (Render/VPS/local) **trước deploy tiếp theo**; code hoá chính sách forward-fix; guard CI chặn sửa migration đã merge (xem §3) | 0.5–1 ngày |
| G1 | **HIGH** | `admin002@campuscore.demo` + `lecturer002–012@campuscore.demo` **LOCKED vĩnh viễn trên clone sạch**: V48 khóa tất cả `.demo`, chỉ bộ 3 `.edu` được V50/V77/DemoAccountGate mở lại → luồng four-eyes & hội đồng trong DEMO_RUNBOOK không thể chạy out-of-box | V48:8-15, V50, V77:7-26 (chỉ sửa password), `DemoAccountGate.java:41-42`, `AuthLoginService.java:78`; **ci.yml:149-152 đang assert admin002 LOCKED/401** | V82 activate đúng bộ runbook **+ mở rộng `DemoAccountGate.DEMO_EMAILS` + flip assertion CI + drift-guard test — bắt buộc 1 commit** (migration-only → tạo cửa admin công khai; gate-only → clone vẫn hỏng) | 1–2 giờ |
| G3 | MED-HIGH | `supabase/migrations` (18 file) drift khỏi Flyway (81): thiếu `ux_course_code` (V80), FK V81, partial unique L3; không có cơ chế parity | grep toàn bộ supabase set | 1 migration parity V83 + README ranh giới "Flyway = schema authority, Supabase = content authority"; KHÔNG xây parity generator | 1 ngày |
| G4 | MED | FK đường nóng không index: `Section.lecturerId`, `Course.departmentId/semesterId`, `Article.categoryId`, `thesis_council_member.lecturer_id` | census CREATE INDEX qua 81 file | Gộp vào V83 | 1 giờ |
| G5 | MED | P1 vòng 9 (mã học phần) **ĐÃ ĐÓNG trên nhánh Flyway** — V80 renumber SE401–404→SE421–424 + unique index global; nhưng **0 test regression** (không test nào nhắc `ux_course_code`/SE421), `Section.sectionNumber` vẫn không ràng buộc | đọc V80 + grep src/test | Persistence test insert trùng → expect violation | 2–3 giờ |
| G6 | MED | `database/` trộn Dockerfile đang sống với SQL ad-hoc stale + backup cũ → nguy cơ re-apply dữ liệu đã được migration chuẩn hoá | ls database/ | Chỉ giữ Dockerfile, phần còn lại archive/xóa | 30 phút |
| G7–G9 | LOW | 570 TIMESTAMP vs 140 TIMESTAMPTZ trộn (quy ước mới = TIMESTAMPTZ, docs-only); V33 PII-shape tổng hợp (thêm chú thích fictional); `site.Appearance.payload` TEXT không jsonb | — | docs/comment/1h ALTER | ≤2 giờ |

### 1.2 Deploy & Ops (O)

| # | Mức | Finding | Bằng chứng | Fix | Effort |
|---|---|---|---|---|---|
| O1 | **HIGH** | **File báo cáo khóa luận không bền ở môi trường thật**: Render không có `THESIS_REPORT_STORAGE_*` → disk tạm container; prod compose `read_only` + tmpfs 64 MB. Adapter `SupabaseThesisReportStorage` **tồn tại nhưng chưa bao giờ được bật** (nuance Wukong: dev compose có named volume, chỉ dev là bền) | render.yaml full, docker-compose.prod.yml:104-155, `ThesisReportStorageConfiguration.java:14-22` | Tạo bucket private trước → secrets sau → deploy cuối (adapter throw lúc boot nếu thiếu config → crash loop); backfill key cũ | 1–2 giờ + data migration |
| O2 | **HIGH** | `render.yaml` không nối kiến trúc cutover: 0 `SUPABASE_*`, không sidecar, `authority-mode` default `sql`, DeepSeek ON ở Render nhưng OFF ở prod compose → "cutover proven" loại trừ chính service đang live; 2 "production" trả lời chatbot khác nhau | render.yaml vs docker-compose.prod.yml:54,117,60,41 | Chọn prod-compose topology làm chuẩn và cho Render hội tụ, HOẶC ghi rõ Render = "demo surface, sql authority" trong mọi tài liệu | 0.5–1 ngày hoặc 30 phút docs |
| O3 | MED | render.yaml thiếu `HEALTH_READINESS_KEY` → readiness vĩnh viễn 403; liveness không check DB (Render giữ instance khỏe khi DB chết); keepalive cũng chỉ ping liveness | render.yaml:9, `HealthController.java:59-66` | Thêm key (sync:false); **DB check chỉ đặt ở readiness, KHÔNG đưa vào liveness** (tránh restart storm khi Supabase chớp) | 1 giờ |
| O4 | MED | Backup script + systemd timer trỏ vào DB compose VPS, trong khi `deployment.md` tuyên bố DB live là Supabase → backup "đầy đủ nhất" trong repo không chạm vào DB thật | ops/backup/backup-postgres.sh:9-11 | Thêm đường backup Supabase (pg_dump qua pooler) + verify-backup | 2–4 giờ |
| O5 | MED | Bucket `thesis-reports` không có IaC/policy trong repo → privacy PDF sinh viên phụ thuộc thao tác tay dashboard (nếu public = đọc ẩn danh được) | grep storage.buckets trong supabase/migrations | Migration insert bucket private + policy service-role-only + smoke anonymous GET→403 | 1–2 giờ |
| O6 | MED | Không cap byte JSON body (multipart mới có 20MB); dev rag-service không mem_limit; `/internal/rag/**` permitAll trên Render single-jar (đang 503-gated) | application.yml:7-11, compose | Filter Content-Length ~64KB cho route assistant + mem_limit parity | 1–2 giờ |
| O7–O11 | LOW | Swagger/actuator public trên Render (30 phút); mobile memory-only token + trễ 3 tuần so với API (0.5 ngày hoặc ghi chú README); dev compose không xoay log (15 phút); URL Render live cắm cứng trong migration keepalive (30 phút); deployment.md hướng dẫn dán DeepSeek key không có rotation note, mâu thuẫn PRODUCTION_RUNBOOK (30 phút) | — | batch polish | ~2.5 giờ |

### 1.3 Backend (B)

| # | Mức | Finding | Bằng chứng | Fix | Effort |
|---|---|---|---|---|---|
| B1 | MED (latent) | Khi `JWT_REFRESH_SECRET` unset → fallback về `JWT_SECRET` (application.yml:73, AuthTokenService.java:41); access token **không có claim `tokenType`**, decoder không validate claim; token thiếu roles → 0 authority nhưng vẫn `authenticated()` (SecurityConfig.java:210-216) → refresh token dùng được làm Bearer. **Nuance Wukong**: mọi config tracked đều có 2 secret riêng biệt; `SECURITY_JWT_REJECT_KNOWN_DEFAULTS` chỉ check độ dài/denylist, **không check trùng access/refresh** → đây là footgun cấu hình, không phải lỗ hổng active | AuthTokenService.java:84-137, JwtSecretPolicy.java:58-71 | (1) Thêm claim `tokenType: access` + validate; (2) startup check cấm secret trùng; (3) xoá fallback trong commit thứ hai. **Không rotate refresh secret gần ngày bảo vệ** (giết mọi session) | 0.5 ngày |
| B2 | MED→LOW | `ThesisReportService` xoá object storage cũ **trước commit** → rollback để lại storage key lơ lửng. Đơn thuần-local đang tự lành, nhưng **trở thành mất dữ liệu vĩnh viễn ngay khi O1 bật** → phải sửa TRƯỚC O1 | ThesisReportService.java:82-113 | `TransactionSynchronization.afterCommit` | 0.5 ngày |
| B3 | LOW | `COOKIE_SECURE` default false — footgun cho host mới clone từ dev compose | application.yml:80 | Gate theo profile, đừng flip default global (vỡ login dev LAN) | 30 phút |
| B4 | LOW | Toàn bộ GET không throttle, gồm feed công khai ẩn danh | RateLimitFilter.java:38,52-56 | Đọc-limit theo IP cho route ẩn danh | 2–4 giờ |
| B5 | LOW | Swagger/OpenAPI permitAll prod (kèm O7) | SecurityConfig.java:137-140 | Profile-gate | 1 giờ |
| B6 | LOW | markRead/delete notification: SQL chỉ `WHERE id` (guard là SELECT phía trước) — lệch hợp đồng "ownership ở tầng SQL" | NotificationWriteRepository.java:77-83 | Thêm `AND user_id` + test âm | 30 phút |
| B7 | LOW | `phone`/`address` DTO không `@Size` → overflow ra 500 thay vì 400 | AuthDtos.java:27-33 vs V2 (VARCHAR 80/500) | Thêm annotation | 30 phút |

### 1.4 Frontend (F)

| # | Mức | Finding | Bằng chứng | Fix | Effort |
|---|---|---|---|---|---|
| F1 | **HIGH** | **Bell thông báo vẫn đoán deep-link bằng từ khóa** — fix vòng 8 chỉ landed ở NotificationsCenterPage; bell (`dashboard/layout.tsx:206-241`) match `text.includes(...)` → "lịch thi" rẽ nhánh `'lịch'` trước, mở nhầm trang. Backend trả `link`/`type` (NotificationReadDtos.java:12-23) nhưng FE vứt bỏ → link broadcast admin không bao giờ hoạt động từ bell. Wukong chỉnh: bell dùng local `NotificationItem` (không phải `NotificationRecord` — cả hai đều thiếu field) | layout.tsx:206-241,1110-1128; NotificationsCenterPage.tsx:54-73 (đã fix, có comment "wrong by construction") | Port resolver `link`/`type` sang bell + bổ sung field vào type | 1–2 giờ |
| F2 | MED-HIGH | middleware.ts chỉ xử lý locale; **0 bảo vệ route server-side**; RBAC FE thuần client-side (16 trang copy-paste guard) — không phải data breach (BE rắn), nhưng anonymous nhận full admin shell + 1 page quên guard = 403 thô | middleware.ts:23-69 | Ghi known-limitation (câu trả lời bảo vệ có sẵn: BE `@PreAuthorize` + SQL ownership); không làm middleware mới sát ngày bảo vệ | defer |
| F3 | MED | Vercel proxy `maxDuration = 60` cắt SSE >60s trên chính target khai báo trong vercel.json (Render/docker bỏ qua nên vòng 7 không thấy) | app/api/v1/[...path]/route.ts:6 | Ghi chú known-limitation hoặc bypass proxy cho `/chat/stream` | 1–2 giờ |
| F4 | MED | Guard admin tự viết ở 16 trang (2 trang mới vòng 8 đã lệch hành vi) | grep | Đồng bộ dần, không refactor ồ | defer |
| F5 | MED | react-query cài sẵn nhưng 2 consumer; 45 trang fetch tay không abort/dedup → stale-response race khi lọc nhanh | lib/query.ts vs admin pages | Quyết định: adopt có chọn lọc hoặc xoá wrapper | 1–2 ngày/10 phút |
| F6 | MED | Dropdown phân công admin bị cắt âm thầm đúng cap BE (giảng viên 100, môn 200) — giảng viên #101 không thể gán, không báo lỗi | lib/reference-data.ts, admin/sections/page.tsx:174-177 | Combobox có search (tái dùng picker của composer thông báo) | 1 ngày |
| F7 | MED-LOW | CSV export admin enrollment thiếu BOM UTF-8 (chuẩn nội bộ grade/attendance-export có) → Excel Windows lỗi tiếng Việt; leak `URL.revokeObjectURL` | admin/enrollments/page.tsx:523-538 | Prepend `\uFEFF` + revoke | 30 phút |
| F8 | LOW-MED | Không có `global-error.tsx` → provider root layout crash = trang lỗi Next mặc định (error.tsx không bắt được) | app/ (chỉ có error.tsx) | Thêm global-error tái dùng block copy cookie-locale | 30 phút |
| F9–F12 | LOW | Bell mark-read fail nuốt vào console (30 phút); thesis page 2955 dòng (defer); 3 bộ parse cookie trùng lặp (30 phút); CSRF-hint mất → ép re-login dù session server còn (1h, SUSPECTED) | — | batch | ~2.5 giờ |

### 1.5 Tests & CI (C)

| # | Mức | Finding | Bằng chứng | Fix | Effort |
|---|---|---|---|---|---|
| C1 | **HIGH** | **Suite Playwright chính thức không bao giờ chạy trong CI** (ci.yml: java-api, frontend, portal-viewport, mobile, compose — không job nào gọi playwright/run-course-e2e; compose probes chỉ API-level). 9 vòng "official suite" đều manual | ci.yml 182 dòng | Job CI chạy `node scripts/run-course-e2e.mjs` chống compose cô lập, **không bao giờ trỏ vào Render live** | 0.5 ngày |
| C2 | **HIGH** | Domain credit-limit/registration-eligibility **0 test Java** dù README tuyên bố "backend route + test" — chỉ có e2e manual vòng 7 | grep src/test → 0 ref RegistrationController/CreditLimit* | 3–4 test MockMvc/service | 0.5–1 ngày |
| C3 | **HIGH** | 2 file test mồ côi không ai chạy: `frontend/tests/lecturer-validator-speak.test.js` (vắng khỏi danh sách 39 file `npm test`), `scripts/supabase/assistant-knowledge.test.mjs` (không script nào gọi) | diff disk vs package.json:18 | Bổ sung vào runner | 15 phút |
| C4 | MED | 2 IT Postgres nữa env-gated skip trong CI (turn ledger/migration) — cùng họ gap 5.6 cũ; CI compose đã có Postgres thật, trỏ IT vào đó | ci.yml + pom failsafe | +2 giờ vào fix 5.6 | +2 giờ |
| C5 | MED | Trivy scan **sau khi push** → CRITICAL fail job nhưng ảnh đã lên GHCR | publish-ghcr.yml:222-336 | Scan ảnh local trước push | 2–4 giờ |
| C6/C7 | MED | Bằng chứng runtime (plans/) + gitleaks.exe **không track trong git** (plans/ chỉ bị ẩn bằng `.git/info/exclude` — local-only); CI secret grep còn exclude `:!plans` → runbook §7 không tái lập được từ clone sạch | `git ls-files plans` = 0 | Track báo cáo đã redact vào `docs/evidence/`; gitleaks action pinned; đưa `/plans/` vào .gitignore tracked | 2 giờ |
| C8 | MED | Secret scan CI mỏng (1 grep `sk-` + script 3 pattern); gitleaks chỉ chạy tay | ci.yml:123-131 | Pinned gitleaks action + bỏ exclusion stale | 1 giờ |
| C9 | MED | 19 env var docker-compose.yml dùng nhưng thiếu trong `.env.example` — gồm **toàn bộ mail subsystem** và `ASSISTANT_RAG_SERVICE_TOKEN` | diff cơ học | Bổ sung có comment | 1 giờ |
| C10 | MED | Cụm 0-test: notification stack (8 class), academic read controllers/services, people, thesis read controllers, `CookieOrBearerTokenResolver`, `GlobalExceptionHandler` | fixed-string scan | Ưu tiên notification + GlobalExceptionHandler trước | 1–2 ngày |
| C11–C14 | LOW | keepalive ship `SUPABASE_SERVICE_ROLE_KEY` vô ích (OK_CODES gồm 401) — 10 phút; docs "34 spec Playwright" lệch thật tế (5 file/41 test, 29 passed/6 skip) — 30 phút; viewport test chạy 2 lần CI (1 lần thoái hóa) — 15 phút; CI chỉ validate compose gốc; eslint warn chỉ có răng qua `--max-warnings=0` | — | batch | ~2 giờ |

### 1.6 Review pending diff (16 file, +737/−149) — verdict: **COMMIT-READY**

Đã chạy thực tế trong review: `npm test` **384 pass/0 fail/2 skip**, `tsc --noEmit` sạch, eslint
`--max-warnings=0` sạch trên các file đổi, `SupabaseKnowledgeSyncServiceTest` **9/9**.
i18n đủ cả en/vi, selectors e2e khớp source, state-machine reducer sound, docs khớp code.

Việc bắt buộc trước commit: **chạy 2 spec Playwright mới** (`assistant-admin.spec.ts`,
`professional-quality.spec.ts`) trên stack cô lập — reviewer NOT_RUN, docs ghi "green" là
tuyên bố của tác giả. Polish tuỳ chọn: (1) xoá type `FeedbackReason` trùng lặp; (2) xoá/nhận
bootstrap branch chết trong `SupabaseKnowledgeSyncService.activate()` — fresh-env bootstrap
giờ bất khả thi, cần note deployment; (6) bỏ option `turnInProgress` đã chết trong hook.
Kongming lưu ý: giữ lock singleton qua 2 REST call upstream là chủ đích (chống stale snapshot),
đã ghi cost serialize.

---

## 2. Hội đồng role — kết luận từng vai

### Wukong (phản biện): 5/5 tuyên bố trọng yếu **NOT_FALSIFIED** — độ tin cậy fleet cao

| Tuyên bố | Verdict | Điều chỉnh quan trọng |
|---|---|---|
| G1 demo accounts khóa out-of-box | NOT_FALSIFIED | CI (`ci.yml:149-152`) đang **assert** trạng thái LOCKED → fix G1 buộc phải flip CI cùng commit; docs disclosure có nhưng công tắc duy nhất được ghi (`DEMO_ACCOUNTS_ENABLED`) không đủ cho `.demo` |
| O1 storage ephemeral mọi target | NOT_FALSIFIED | Quá khái quát: **dev compose có named volume** (bền); ephemeral đúng cho Render + prod compose |
| B1 refresh-as-bearer | NOT_FALSIFIED | Latent misconfiguration, không phải breach active: mọi config tracked có 2 secret riêng; fail-closed khi cả hai unset; `JwtSecretPolicy` không check trùng — nên trình bày là hardening |
| C1 e2e không chạy CI | NOT_FALSIFIED | Compose probes là thay thế API-level một phần; điều cay: pending diff **đang thêm spec mới vào suite CI không chạy** |
| F1 bell keyword-guess | NOT_FALSIFIED | Chỉnh symbol: bell dùng local `NotificationItem`, không phải `NotificationRecord` (cả hai đều thiếu `link`/`type`) |

### Kongming (kiến trúc): 4 đợt thực thi + các bẫy containment

- **Wave 0 — gỡ bom**: G2 (validate → `flyway repair` từng env → chính sách forward-fix → guard
  CI immutable-migration) + C6/C7 (reproducibility). Trước mọi V82+ và trước push kế tiếp.
- **Wave 1 — CI truth + data-state**: C1 (e2e vào CI, chống compose, không đụng Render), C3/C4,
  G1 (V82+gate+CI một commit), C2+G5, B7.
- **Wave 2 — cutover truth** (thứ tự nội bộ bắt buộc **B2 → O1 → O3 → O2**, vì mỗi bước sau đổi
  blast-radius của bước trước; O1 phải bucket-first để tránh crash loop) + G3/G4 (V83 parity+index
  gộp một) + O4/O5/O6.
- **Wave 3 — security chạm session**: B1 (claim trước, enforce sau; **không rotate secret gần bảo vệ**),
  B6, B3 (profile-gate), B5/B4/F7/F8 batch.
- **Wave 4 — FE đúng trọng tâm demo**: F1 (uy tiên trong đợt — hội đồng sẽ bấm chuông), F6; còn lại defer có ghi chú.
- **Đánh giá kiến trúc**: Flyway là schema authority duy nhất và đủ; **giáng Supabase thành
  bounded content authority** (corpus tri thức + object store báo cáo) bằng văn bản; chọn một
  topology deploy làm chuẩn (prod-compose) và cho Render hội tụ hoặc gắn nhãn trung thực;
  enforcement rẻ = CI grep env-var keys trong render.yaml + guard migration bất biến.

### Advisor (ưu tiên): top-10 tối ưu "điểm mỗi giờ"

1. Chạy 2 spec e2e mới → commit pending diff (0.5 ngày) — mở khoá mọi thứ khác.
2. G2 defuse: `flyway repair` local/Render/Supabase + guard (1–2 giờ) — fix rẻ nhất, giảm blast-radius lớn nhất.
3. G1: làm demo path chạy được từ clone sạch 1 lệnh + diễn tập four-eyes end-to-end (1–2 giờ) — demo là điểm số.
4. B1: tokenType claim + từ chối refresh-as-bearer + test (0.5 ngày) — auth là thứ hội đồng dò đầu tiên.
5. O1: bật adapter Supabase storage đã có sẵn (0.5 ngày) — "redeploy xong mất file" là câu hỏi không đỡ được.
6. O2+O3: readiness key + đối chiếu config Render/compose hoặc làm mềm docs (0.5 ngày).
7. C1: e2e tối thiểu vào CI (0.5–1 ngày).
8. C2: 3–4 test cho credit-limit (0.5 ngày) — feature README-claimed không được để 0 test Java.
9. F1: hoàn tất deep-link bell (0.5 ngày).
10. Honesty pass: "34 spec" docs drift, evidence untracked, cutover claims, re-verify "Render LIVE" trong 48h trước bảo vệ kèm screenshot có ngày (2–4 giờ, làm CUỐI).

---

## 3. KHÔNG làm bây giờ (đồng thuận 3 role — YAGNI capstone)

- **Không** adopt react-query 45 trang (F5), **không** tách trang thesis 2955 dòng (F10),
  **không** middleware route protection mới (F2 — rủi ro lockout sát demo), **không** dedupe 16 guard
  admin ồ ạt (F4), **không** đụng RAG/DeepSeek pipeline, **không** tạo parity generator Supabase↔Flyway,
  **không** Testcontainers harness thứ hai (dùng Postgres compose sẵn của CI), **không** Terraform
  một bucket, **không** hồi sinh mobile, **không** chuẩn hoá 570 cột timestamp, **không** re-author
  12 migration cũ thành migration sửa (repair + document + prevent là đủ), **không** mở feature mới
  — feature freeze từ đây.
- Tránh dài hạn đã ghi vòng trước vẫn giữ hiệu lực (mục 6 RECOMMENDATIONS.md).

## 4. Trung thực hoá tuyên bố trước bảo vệ

1. Mọi câu "cutover proven" phải nêu mặt bằng: *"đã chứng minh trên compose stack; Render hiện
   chạy cấu hình legacy sql-authority"* — cho tới khi render.yaml được nối (O2).
2. "34 spec Playwright" trong RECOMMENDATIONS.md §7 sai thật tế (5 file/41 test) → sửa số hoặc
   chạy suite lần nữa post-commit rồi trích run mới.
3. Bằng chứng runtime trong `plans/` clone sạch không thấy → commit bản redact vào
   `docs/evidence/` hoặc trích dẫn dạng "lệnh tái lập + ngày chạy cuối".
4. Re-verify "Render LIVE, prod 200/200" trong 48h trước bảo vệ, kèm screenshot có ngày; xác nhận
   readiness thật sự pass sau khi set `HEALTH_READINESS_KEY` (hiện tại probe đó 403 vĩnh viễn —
   tuyên bố "health check ok" cần soi lại).
5. Credit-limit: giữ claim e2e vòng 7 nhưng ghi "unit coverage pending" hoặc thêm test (mục 8).

## 5. Checklist hành động (thứ tự thực thi) — TRẠNG THÁI THỰC THI 24/09/2026

- [x] 1. Chạy `assistant-admin.spec.ts` + `professional-quality.spec.ts` + full official suite trên stack cô lập → **38 passed / 7 viewport-gated skips / 0 failed (chromium)** → commit pending diff — **`7d1ab544`**
- [x] 2. G2 (phần repo-side): guard CI immutable-migration + chính sách forward-fix + runbook `flyway validate/repair` — **`7f7323a8`**. ⚠️ **Còn lại (operator):** chạy `flyway repair` trên DB Render + VPS + Supabase **trước deploy kế tiếp** — cần credentials, NOT_RUN tại máy này.
- [x] 3. G1 một commit: V82 + mở rộng `DemoAccountGate` + flip assertion ci.yml + drift-guard test + cập nhật DEMO_RUNBOOK — **`397885f9`**. Xác minh runtime trên stack dùng một lần: admin002 ACTIVE + login → accessToken ADMIN; `an.hnt@campuscore.demo` LOCKED + 401; biên Flyway 82=82.
- [ ] 4. Diễn tập demo từ clone sạch: four-eyes + hội đồng 3 giảng viên + đăng ký chống race — **chưa chạy** (cần người trình diễn, không tự động hoá được).
- [x] 5. B1: claim `tokenType` + decoder từ chối refresh-as-bearer + pair-guard secret trùng — **`87a95d56`**. Không rotate secret.
- [x] 9. F1: bell deep-link từ authored `link`/`type` + contract test chống drift — **`0ef0906a`**.
- [x] 7. C1: job CI chạy official e2e chống compose + upload trace khi fail; C3: nối 2 test mồ côi (FE list + CI supabase step) — **`b0be164e`** (C1) và **`0ef0906a`** (C3). ⚠️ Chưa có run CI thật trên GitHub để chứng minh job xanh (đi kèm push kế tiếp).
- [x] 10 (một phần). C9 `.env.example` đầy đủ 19 biến theo default compose thật — **`b0be164e`**. Còn lại: V83 parity+FK-index (G3+G4), O4 backup Supabase, O5 bucket policy, O6 byte cap — **backlog, chưa làm**.
- [x] 11 (một phần). B6 (ownership SQL scoped) + B7 (@Size 80/500) — **`473fd4b5`**; F7 (BOM + revoke) + F8 (global-error.tsx) — **`b0be164e`**. Còn lại: B3, B5, F9, C11, C12, O9–O11, G6 — backlog.
- [ ] 12. Honesty pass toàn docs (mục 4) — **chưa làm**: cần chạy sau khi code chốt; các mục "34 spec", cutover claims, evidence untracked (C6/C7) vẫn mở.

**Finding mới phát hiện khi thực thi (chưa sửa, đã chứng minh pre-existing):** chạy
`mvn -Dtest=Notification*Test` **riêng lẻ** fail 15/15 với 503 DATABASE_UNAVAILABLE trên cả
baseline nguyên vẹn (đã stash để đối chứng). Nguyên nhân: H2 named DB dùng chung giữa các test
class + fixture của test notification thiếu cột mà lookup của `AccountStateFilter` cần →
`DatabaseAvailabilityTracker` đánh dấu unavailable và 503 mọi request sau đó. Full suite (gate CI)
không bị ảnh hưởng (EXIT=0). Đây là dạng thứ tự-phụ thuộc của test — nên sửa bằng cách hoàn thiện
fixture bảng `campuscore_auth."User"` trong test notification.

## 6. Thước đo hoàn thành (mỗi mục kiểm chứng được bằng lệnh/trạng thái)

- Pending diff committed với evidence: 2 spec mới xanh + full suite xanh (log lưu kèm commit).
- `flyway validate` PASS trên Render DB + compose DB + clone sạch; guard CI chặn được một commit
  giả định sửa migration (test bằng cách thử sửa V1 trên nhánh).
- Clone sạch + `DEMO_ACCOUNTS_ENABLED=true`: login `admin002@campuscore.demo` **thành công**;
  flow four-eyes DRAFT→PENDING→PUBLISHED bằng 2 admin chạy trọn; CI assertion mới xanh.
- `Authorization: Bearer <refresh-token>` → **401/403** (test tự động đỏ trên baseline cũ).
- Upload báo cáo → `docker compose restart`/redeploy Render → tải lại file **còn nguyên vẹn**; anonymous GET bucket → 403.
- Readiness probe Render trả 200; liveness không phụ thuộc DB.
- CI có ≥1 run xanh của job e2e với artifact; `npm test` list không còn file mồ côi.
- Docs không còn con số "34 spec" sai; mọi bằng chứng trích dẫn đều mở được từ clone sạch hoặc
  có lệnh tái lập + ngày chạy.

---

*Vòng 10 — 6 scout agents + Wukong/Kongming/Advisor. Không có check nào được báo PASS khi
NOT_RUN: hai spec Playwright mới và cutover runtime là hai NOT_RUN lớn còn tồn tại ở thời điểm
viết tài liệu.*
