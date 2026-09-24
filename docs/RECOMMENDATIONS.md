# Khuyến nghị hoàn thiện — CampusUTE sau v1.0.0

> Tài liệu duy nhất tổng hợp kết quả vòng 7 (deep E2E + audit toàn chức năng bằng 8 subagents chuyên
> trách + hội đồng Kongming/Advisor/Wukong) và vòng 8 (UI/UX deep sweep toàn frontend, 2026-09-23 —
> mục 8). Trạng thái nền: `v1.0.0` tại `2b785aab` (đã phát hành,
> Render LIVE, prod 200/200). Mọi khuyến nghị xếp theo **giá trị bảo vệ đồ án**, kèm ước lượng effort.

## 1. Các tuyên bố hệ thống ĐÃ CÓ BẰNG CHỨNG (feature → test/bằng chứng → ngày chạy)

| # | Tuyên bố | Bảo vệ bằng | Bằng chứng runtime | Ngày |
| - | --- | --- | --- | --- |
| 1 | Đăng ký chống race 4 lớp: **đúng 1 winner khi 12 SV giành chỗ cuối**, thua cuộc nhận 409 SECTION_FULL sạch, DB không vượt capacity | `AcademicEnrollmentMutationPersistenceTest` (10 SV/capacity 2; idempotency replay) | `plans/2026-09-23-round7-feature-e2e/reports/race-safe-enrollment-stress.log` — 2 lần chạy PASS (winner khác nhau giữa 2 lần ⇒ khóa row công bằng, không thiên vị request đầu) | 2026-09-23 |
| 2 | **Layer 3 — partial unique index** chặn enrollment ACTIVE trùng ngay khi bypass ứng dụng; bản DROPPED vẫn hợp lệ | V14 (DDL) + substring assert (`RegistrationFoundationMigrationTest`) | `reports/layer3-partial-unique-index.log` — insert ACTIVE thứ 2 bị `unique constraint` từ chối, insert DROPPED OK | 2026-09-23 |
| 3 | **Four-eyes tri thức RAG**: tác giả không thể tự duyệt (chặn ở tầng SQL `created_by <> actor`, lỗi `KNOWLEDGE_SECOND_REVIEW_REQUIRED`), admin 2 duyệt → PUBLISHED có `reviewed_by` + audit trail; giảng viên 403; privacy gate chặn dữ liệu nhạy cảm | `ThesisAssistantGovernanceWebTest`, `AssistantInputGuardTest` | E2E 6/6 PASS vòng 7 (G1–G6) — DRAFT→PENDING_REVIEW→PUBLISHED 2 tài khoản, archive sạch | 2026-09-23 |
| 4 | Đơn vượt hạn mức tín chỉ: SV nộp (≥20 ký tự) → PENDING → admin duyệt → APPROVED, hạn mức eligibility 28→**30**; SV tự duyệt = 403 | backend route + test | E2E vòng 7 (F2) — DB `CreditLimitApplication.APPROVED`, `reviewedBy` ghi nhận | 2026-09-23 |
| 5 | Phiếu đăng ký PDF: `X-Content-SHA256` = sha256(body), chỉ sinh theo JWT `studentId` (không tham số chéo) | code + E2E | E2E vòng 7 (F1) | 2026-09-23 |
| 6 | Thông báo cách ly theo user: owner mark-read 200; **giảng viên PATCH cùng id → 404** (không dò được dữ liệu người khác) | `NotificationWriteRepository.findOwned` (WHERE user_id) | E2E vòng 7 (F3) | 2026-09-23 |
| 7 | Mail RBAC: SV 403 với mọi route gửi thư; admin gửi không rò stack trace | `MailEndpointSecurityTest` | E2E vòng 7 (F4) | 2026-09-23 |
| 8 | Rate limiter đăng nhập: 429 khi vượt ngưỡng (quan sát trực tiếp khi chạy stress 2 lần liên tiếp) | config | stress log lần 2 — lần thử ngay lập tức nhận 429 | 2026-09-23 |
| 9 | Trợ lý AI: guard chặn đúng injection/git, không false-positive học thuật; SSE stream 200 + delta frames; heartbeat 15s/45s watchdog | vòng 6 (Wukong 5/5 NOT_FALSIFIED) | 2026-09-22 | |

## 2. Bằng chứng an ninh & quản trị

- Tách quyền hai tầng kiểm chứng ở vòng 7: `@PreAuthorize` (controller) **và** điều kiện sở hữu trong SQL
  (ví dụ `created_by <> :actor` của four-eyes, `WHERE user_id` của notification) — bypass tầng trên
  vẫn bị tầng dưới chặn. Không tìm thấy đường đọc/ghi chéo nhóm đồ án cho sinh viên
  (`ThesisGroupSupervisorScopeTest`, `ThesisTopicVisibilityTest` giữ regression).
- Phát hiện ở baseline (23/09), đã được sửa và kiểm tra trong vòng 9: publish/archive tri thức ở
  `sql` authority trước đây không cập nhật corpus RAG. Xem bằng chứng và ranh giới kiểm chứng ở mục 9.

## 3. Đã sửa vòng 7 (finding → commit → test chốt)

| Finding | Nguồn | Mức | Fix | Test chốt |
| --- | --- | --- | --- | --- |
| Nút nộp báo đồ án enabled nhưng server 409 `REPORT_WINDOW_CLOSED` (không có trong i18n map) | thesis-audit | MEDIUM | `REPORT_WINDOW_CLOSED` EN/VI + disabled state + mapping khi submit | `thesis-flow-safety.test.js` guard mới; full FE suite xanh |
| Badge "chưa đọc" lấy length của 5 dòng ⇒ capped 5; endpoint `unread-count` là dead code | engagement-audit | MEDIUM | FE gọi `GET /notifications/my/unread-count` | full FE suite |
| Gmail cá nhân làm default recipient + sender (4 chỗ) | engagement-audit | LOW | `no-reply@campuscore.local` ở recipient, sender config/preview | `MailControllerTest` regression fail-on-old |

## 4. Hạn chế đã biết — nêu trung thực (không giấu)

1. **Đã khép ở vòng 9:** publish/archive tri thức local giờ chuyển con trỏ release bất biến trong cùng
   transaction; kiểm thử PostgreSQL hai phiên đã xác nhận hai thứ tự đồng thời. Chưa suy rộng bằng chứng
   này thành cutover Supabase production (xem ưu tiên 2 ở mục 9).
2. **SUPER_ADMIN bị từ chối ở 6 service-layer thesis** dù `@PreAuthorize` cho qua (fail-closed, không rò
   quyền, nhưng console có nút bấm sẽ 403).
3. **Chuyển trưởng nhóm chưa tồn tại**: nhóm có trưởng bất hoạt sẽ kẹt (chỉ admin cứu được thành viên/đề
   tài, không nộp được báo cáo hộ) — xem 5.2.
4. Hai nguồn đếm enrollment: tile 3.810 (gồm DROPPED) vs `distribution totals` 3.807 — cùng thật, khác口径.
5. SSE watchdog 45s/heartbeat 15s chưa có test timing tự động (bằng chứng hiện tại: code + vòng 6 live check).
6. V56 bỏ trống trong chuỗi migration (không mất file, chỉ hụt số).
7. Round MON_HOC/NCKH không thể publish kết quả (chỉ hội đồng tự chấm điểm `final_score`).

## 5. Backlog kiến trúc — đề xuất có ước lượng effort

| # | Đề xuất | Vì sao | Effort | Giá trị bảo vệ |
| --- | --- | --- | --- | --- |
| 5.1 | **ĐÃ ĐÓNG ở vòng 9:** kích hoạt release tri thức bất biến sau publish/archive | Promotion cùng transaction và hai lịch publish/archive đồng thời đã được kiểm tra cục bộ; xem mục 9 để biết giới hạn môi trường đích | Đã thực hiện | Rất cao — khép vòng quản trị RAG |
| 5.2 | **Leader transfer + self-leave** cho nhóm đồ án (endpoint + 2 nút UI) | Đóng lỗ hổng kẹt nhóm khi trưởng bỏ; quy trình thật có đổi trưởng | 0.5 ngày | Cao — vòng đời nhóm đầy đủ |
| 5.3 | Đồng bộ enrollment tile với `distribution totals` (chọn 1 nguồn) | Provenance số liệu — cùng lớp bug a84d9aee đã sửa | 1–2 giờ | Trung bình |
| 5.4 | Tách module thời khóa biểu trùng ~1000 dòng (giảng viên/sinh viên) | Maintainability; nguy cơ regress khi sửa 1 bên quên bên kia | 1–2 ngày + regression | Trung bình (chất lượng code) |
| 5.5 | SUPER_ADMIN nhất quán tầng service thesis (6 check) | Nhất quán hợp đồng "SA đi mọi nơi ADMIN đi" đã ghi trong controller | 0.5 ngày | Trung bình |
| 5.6 | Testcontainers/Postgres cho L3 (partial index) vào CI | H2 không biểu thị được partial index — test thật chỉ chạy được trên Postgres | 0.5 ngày | Trung bình — khép khoảng trống test |
| 5.7 | Trang admin tạo thông báo (backend đã có `POST /notifications` ADMIN-only, FE chưa có UI) | Đủ vòng đời quản trị thông báo | 0.5–1 ngày | Trung bình |
| 5.8 | Mail disabled → phản hồi trung thực (không "đã gửi thành công") | Đéo trung thực UI, cùng họ vòng 3 | 1–2 giờ | Trung bình |
| 5.9 | Xóa proxy route notifications chết (không hỗ trợ PATCH, forward auth header) | Giảm bề mặt thừa | 30 phút | Thấp |
| 5.10 | Mã lỗi sai `ROUND_NOT_FOUND` khi thiếu slip → `SLIP_NOT_FOUND` | Chẩn đoán client đúng | 30 phút | Thấp |

## 6. Đã cân nhắc và TỪ CHỐI (kèm lý do — để không quay lại)

- **Gộp 2 editor rich-text** (thông báo + tri thức): refactor không có defect visible, đụng pipeline four-eyes.
- **Rebuild notifications bằng WebSocket/push**: SSE đã phủ câu chuyện kỹ thuật; đổi transport = đổi contract.
- **Trang học phí/thanh toán**: schema + API mới, ngoài phạm vi sau 1.0; đã descope từ vòng audit FE.
- **Nâng model/vector DB cho RAG**: không có benchmark so sánh; rủi ro lớn hơn lợi ích bảo vệ.
- Card mobile cho 9 bảng admin, mascot state, citation click-through: polish thuần túy — backlog hợp lệ
  nhưng không phải việc của vòng release.

## 7. Runbook tái lập toàn bộ bằng chứng từ clone sạch

```bash
# 1. Gates đơn vị
cd frontend && npm test && npm run build
cd java-services/restful-api && mvn test

# 2. Stack local + data demo
docker compose up -d --build   # DEMO_ACCOUNTS_ENABLED mặc định true (cả api lẫn rag)

# 3. E2E chính thức (stack cô lập 4100/3101, 34 spec Playwright)
node scripts/run-course-e2e.mjs

# 4. Bằng chứng runtime deep-tech (Postgres local đã migrate V1–V77)
node plans/2026-09-23-round7-feature-e2e/reports/race-safe-enrollment-stress.mjs
node plans/2026-09-23-round7-feature-e2e/reports/layer3-partial-unique-index.mjs

# 5. Secrets gate
scratch/tools/gitleaks.exe git --log-opts='origin/main..HEAD' --no-banner
```

## 8. Vòng 8 — UI/UX deep sweep toàn frontend

> Fleet: 6 audit UI/UX theo domain (public/auth, học vụ SV, hỗ trợ SV, giảng viên, CRUD admin,
> nội dung admin) + 2 contract sweep + hội đồng Kongming/Advisor. 30+ finding được Kongming phân
> loại bằng thuật toán PATTERN/LOCAL/NOISE: defect local đã sửa (8.1), pattern chuyển backlog (8.3),
> noise bị loại khỏi vòng lặp.

### 8.1 Đã sửa vòng 8

| Finding | Nguồn | Mức | Fix | Test chốt |
| --- | --- | --- | --- | --- |
| Bộ ba data-loss trang điểm: guard chưa-lưu không chặn điều hướng SPA; composer thông báo không reset ⇒ còn khả năng publish trùng; save hàng cục bộ đè làm mất điểm đã nhập dở | audit học vụ SV + nội dung admin | HIGH | (1) bắt chuyển hướng SPA: anchor capture + history sentinel + popstate; (2) composer reset + dirty-gated close; (3) merge-preserve hàng bán điền sau save | Guard riêng từng fix |
| Trang Users: payload tạo bỏ rơi (drop) field faculty đã thu; modal sửa hiển field profile không bao giờ được lưu | audit CRUD admin | MEDIUM | Field faculty disable trung thực (DTO nhận-nhưng-bỏ-qua — office-assigned); modal ẩn field không lưu | Guard riêng |
| Chuỗi "trạng thái giả": options học kỳ `hk1/hk2` bịa (StudentUteProfileGradeView); badge hardcode `Semester 1 • 2026-2027`; in transcript ký khống `TRƯỞNG KHOA CNTT`; meter tín chỉ clamp `Math.min(30)`; in hạnh kiểm + export sai phạm vi in; issuer thông báo bịa đơn vị; deep link notification đoán từ khóa | audit đa domain | MEDIUM | Xóa options giả; badge bind dữ liệu; bỏ chữ ký khống; meter render giá trị API; printable prop + `print:hidden`; issuer fallback `—`; deep link dùng field link/type | Guard riêng từng mục |
| Admin content: Appearance Studio ghi đè + broadcast thứ tự pinned ngay khi tải; quick-edit thông báo rơi binding semester/schedule/audience (state BOTH không phân biệt); lỗi chuyển round đồ án không hiện mã cụ thể; ô đồ án hiển số 0 giả | audit nội dung admin | MEDIUM | Không clobber+broadcast khi load; carry-through binding + phân biệt state BOTH; surfacing đúng mã lỗi (`SCORES_INCOMPLETE`…); nhãn tile theo round đang chọn | Guard riêng từng mục |
| API admin notification (`POST/GET/PUT/DELETE /notifications`) zero-consumer — admin không có UI gửi thông báo | BE→FE contract sweep | — (feature) | **Trang mới `/admin/notifications`**: composer broadcast từng người nhận (thiết kế Stitch, dựng lại bằng primitives của repo) + bảng đã-gửi có xóa; gắn vào admin nav — **đóng mục 5.7** | Guard riêng + xác minh Wukong K2 |
| Tailwind thiếu `shadow-xs`/`shadow-2xs`/`drop-shadow-xs`/`rounded-xs` ⇒ 56 bề mặt âm thầm render phẳng | audit design system | MEDIUM | Thêm alias vào config | 56 bề mặt đủ chiều sâu |

*Mỗi fix kèm guard test riêng; tại mọi bước: full FE suite 362–364 tests / 0 fail, `tsc` sạch,
`npm run build` xanh.*

**Nguồn gốc thiết kế composer (kỷ luật Stitch):** input prompt + screenshot được lưu tĩnh tại
`plans/2026-09-23-round7-feature-e2e/reports/stitch-composer-prompt.txt` và `stitch-composer-screen.png`;
trang được tái hiện bằng primitives của repo (AdminFrame/Card/AdminFormField/status tokens) — markup
Stitch không được paste; các phần trang trí không map được token đã bị loại.

### 8.2 Bằng chứng sweep sạch (release evidence)

Contract sweep — bằng chứng release cho chu kỳ tới: FE→BE **0 cuộc gọi hỏng, 0 lệch tham số** trên
~145 đường dẫn api.ts + fetch inline (17 client method chết được ghi danh sách dọn dẹp); BE→FE map
xong các route zero-consumer với top-3 ứng viên trang: composer thông báo (**đã dựng vòng này**),
attendance dashboard (7 dark route), mail template lab. E2E harness (hạ tầng release) được sửa: spec
passcode admin căn theo V77, fixture mở lại round status (thẩm quyền V76), mock keys cho các endpoint
mới, assertion cũ mã hóa lại theo product truth hiện tại — **official suite 29 passed / 0 failed /
6 viewport-gated skips**. Wukong adversarial: K2 luồng broadcast NOT_FALSIFIED (201 → SV chưa đọc →
cleanup), K3 appearance NOT_FALSIFIED, K5 double-submit NOT_FALSIFIED; K1 grade-guard chỉ FALSIFIED
trên container local stale (nguồn trace sạch — re-probe chờ rebuild); phần còn lại của K4
faculty-drop đã sửa trong source.

### 8.3 Backlog bổ sung vòng 8

| Hạng mục | Lý do để backlog / tiền đề |
| --- | --- |
| Song song card di động cho list-view thời khóa biểu; hiển lý do disabled của nút đăng ký | polish khả dụng, không phải defect chặn release — chưa xếp lịch vòng 8 |
| Màu hex thô trong chart | **PATTERN** — cần một lượt token sweep toàn cục |
| Bloc copy điều-kiện-theo-locale lớn → `messages.ts` | **PATTERN** — ~6 file, sửa lẻ dễ lệch hai bản ngữ |
| Nâng cấp pattern CRUD admin: inline field error (8 trang), sort cột, lưu filter, chọn page-size | Pattern chung 8+ trang — không phải sửa đơn trang |
| Bộ lọc role trang Users | Cần BE thêm query param role trước — đổi API contract, không thể pure-FE |
| Trang attendance dashboard (7 dark route) + mail template lab | 2 ứng viên còn lại trong top-3 zero-consumer map (8.2) |
| Leader transfer; SUPER_ADMIN nhất quán service-layer | Đã ghi ở 5.2 / 5.5 — sweep vòng 8 tái khẳng định |

*Tài liệu được tạo tự động từ vòng 7 (2026-09-23), bổ sung vòng 8 (UI/UX deep sweep, 2026-09-23) —
lead tổng hợp từ 8 báo cáo subagent vòng 7 và fleet vòng 8; mỗi mục số 3 và 8.1 đều có commit +
test riêng trong lịch sử git.*

## 9. Vòng rà soát chéo Browser, FE, AI và BE (23–24/09/2026)

### Đã sửa và xác minh tại local

| Vấn đề | Kết quả |
| --- | --- |
| Bài viết tri thức SQL đã duyệt chưa đi vào release mà chatbot truy xuất; bài lưu trữ có thể còn trong kho phục vụ | Phát hành ảnh chụp release bất biến và chuyển con trỏ trong cùng transaction; Java regression qua. Với Supabase, hai thứ tự PUBLISH/ARCHIVE đã qua thử nghiệm PostgreSQL hai phiên dưới `service_role`. Rà soát sâu phát hiện sidecar có thể kích hoạt snapshot cũ nếu hai lần sync tải đồng thời; đã khóa hàng con trỏ trước khi đọc upstream. Regression đồng thời FAIL trên baseline, source sửa PASS. Wukong sau đó chỉ ra pointer lỗi chưa bị chặn; code hiện từ chối trạng thái thiếu/null/dangling/chưa `PUBLISHED` trước upstream read. Regression đỏ trước sửa và suite sync H2 PASS 9/9, gồm ca thiếu singleton. Runtime PostgreSQL, cùng-actor route và môi trường triển khai vẫn chưa kiểm chứng. |
| Miền `SPECIALIZED` bị Java/FE chuẩn hóa sai hoặc thiếu bộ lọc | Đồng bộ miền qua Java, FE và migration Supabase; 24 tài liệu seed được kiểm tra, regression hash release qua; trình duyệt thấy bộ lọc và trích dẫn `SPECIALIZED`. |
| Câu hỏi học thuật về “prompt injection” bị bộ lọc đầu vào từ chối | Bỏ điều kiện bắt trần thuật ngữ, giữ các mẫu lệnh nguy hiểm; test FE/Java và luồng trình duyệt qua. |
| Ảnh đại diện và PDF báo cáo trước đây ghi `NOT_RUN` do giới hạn IAB | Playwright mobile đã chọn tệp, lưu thành công, tải lại và thấy dữ liệu tồn tại cho cả hai luồng. Nhãn điểm thay đổi theo trạng thái lớp là chủ đích, không ghi là lỗi. |
| Email học vụ khó quét trên mobile, tiêu đề đăng ký quá dài và nội dung thông báo dễ dồn dòng | Tinh chỉnh khung chung cùng bốn mẫu; render Java bốn mẫu qua, bản đăng ký được nhìn trực tiếp ở desktop và 390px. Không gửi email ra ngoài. |
| Route matrix mobile sau các sửa FE và kiểm tra khả năng tiếp cận | Public/auth và Student đã qua sau sửa; lượt chạy bổ sung trên source cuối cùng cho Lecturer/Admin qua 2/2. Admin bao phủ 15 route qua hai locale và hai theme. Axe archetype và keyboard ở 768/1024px qua 2/2 mỗi nhóm. |
| Dependency audit FE báo High ở `js-yaml` gián tiếp từ ESLint | Chỉ là dev dependency; production-only audit không có finding. Nâng lockfile `js-yaml` 4.3.1 lên 4.3.2; `npm ci` sau cập nhật báo 0 vulnerabilities. |

### Ưu tiên tiếp theo

1. **P1 — Xác minh migration mã học phần trước release:** người dùng đã chốt mã duy nhất toàn trường và SE421–SE424. DB local đã chạy Flyway V80/V81; truy vấn xác nhận không còn mã trùng, và Browser hiển thị đủ bốn học phần. Wukong vẫn `INCONCLUSIVE / BLOCK` cho mọi trạng thái dữ liệu vì V80 chỉ đổi section `-01` và `IF NOT EXISTS` có thể bỏ qua index sai cùng tên; local không có hai điều kiện này. Trước production, kiểm tra tất cả section của bốn course và định nghĩa `ux_course_code` trên bản sao đích; chưa chạy production/Supabase.
2. **P1 — Chứng minh release trên môi trường đích:** kiểm tra migration/đối soát trên bản sao Supabase và RAG sidecar có dữ liệu; xác minh rollback, hai quản trị viên, quyền trực tiếp với bảng và đường nội bộ không thể gọi từ client. Edge truyền actor lấy từ JWT admin, còn sidecar dựa vào service token và header owner; chưa tái hiện bypass, nhưng cần negative same-actor qua đúng route. Bài PostgreSQL local trước đây chứng minh hai thứ tự RPC; H2 hiện chứng minh đồng bộ sidecar chặn pointer lỗi và snapshot cũ. Chưa chứng minh cutover, runtime PostgreSQL hoặc route thật.
3. **P2 — Khả năng quét trang quản trị tri thức:** Browser hiện thấy 138 tài liệu; bộ lọc lĩnh vực/trạng thái hoạt động và nội dung dài không nằm inline trong từng dòng. Source cho thấy trang tải toàn bộ kết quả phù hợp, chưa có tìm kiếm hay phân trang; nên thêm tìm kiếm tiêu đề/nguồn và phân trang hoặc virtualize, kèm panel chỉ đọc để xem nội dung đầy đủ mà không phải mở form sửa.
4. **P2 — Khả năng thử lại chatbot:** đã sửa trong working tree hiện tại. Khi stream và lần đối soát JSON cùng trả 503, Browser regression xác nhận nút “Thử lại” gửi lại đúng prompt và `clientRequestId`, rồi hoàn tất khi lần sau thành công. Quota, quyền truy cập và HTTP 4xx không chuyển thành retry; ngoại lệ chỉ là khi đối soát trả đúng mã `TURN_IN_PROGRESS`. Chưa xác minh trên build triển khai hoặc provider thật.
5. **P2 — Feedback chatbot khi API lỗi:** đã sửa trong working tree hiện tại. Browser regression fault-test PUT và DELETE trả 503: UI báo đang lưu/lỗi, khóa thao tác trong lúc gửi, giữ nguyên lựa chọn đã lưu khi thất bại, và retry lại đúng rating/reason; DELETE chỉ xóa rating sau khi server xác nhận. Chưa xác minh trên build triển khai.

Chi tiết tái hiện và ranh giới bằng chứng nằm tại `plans/260923-1705-campuscore-cross-layer-browser-and-ai-audit/reports/2026-09-23-findings.md`. Kiểm thử local không đồng nghĩa CI, push hay triển khai production.
