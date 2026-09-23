# Khuyến nghị hoàn thiện — CampusUTE sau v1.0.0

> Tài liệu duy nhất tổng hợp kết quả vòng 7 (deep E2E + audit toàn chức năng bằng 8 subagents chuyên
> trách + hội đồng Kongming/Advisor/Wukong). Trạng thái nền: `v1.0.0` tại `2b785aab` (đã phát hành,
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
- Lỗ hổng kiến trúc duy nhất tìm thấy (không phải lỗ hổng an ninh): **publish tri thức ≠ vào corpus RAG**
  ở chế độ `sql` authority (mục 5.1).

## 3. Đã sửa vòng 7 (finding → commit → test chốt)

| Finding | Nguồn | Mức | Fix | Test chốt |
| --- | --- | --- | --- | --- |
| Nút nộp báo đồ án enabled nhưng server 409 `REPORT_WINDOW_CLOSED` (không có trong i18n map) | thesis-audit | MEDIUM | `REPORT_WINDOW_CLOSED` EN/VI + disabled state + mapping khi submit | `thesis-flow-safety.test.js` guard mới; full FE suite xanh |
| Badge "chưa đọc" lấy length của 5 dòng ⇒ capped 5; endpoint `unread-count` là dead code | engagement-audit | MEDIUM | FE gọi `GET /notifications/my/unread-count` | full FE suite |
| Gmail cá nhân làm default recipient + sender (4 chỗ) | engagement-audit | LOW | `no-reply@campuscore.local` ở recipient, sender config/preview | `MailControllerTest` regression fail-on-old |

## 4. Hạn chế đã biết — nêu trung thực (không giấu)

1. **Publish tri thức chưa tới được corpus RAG** ở local (mode `sql`): sync chỉ cấu hình cho Supabase và
   đang DISABLED; `active_release` không rebuild sau publish ⇒ trợ lý khôngretrieve nội dung mới duyệt.
   Four-eyes thì đúng, chuỗi publish→phục vụ còn thiếu một mắt xích (xem 5.1).
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
| 5.1 | **Rebuild knowledge release sau publish** (nút "rebuild runtime corpus" cho admin hoặc scheduled reconcile bỏ gate DISABLED khi mode=sql) | Four-eyes publish hiện không tới được trợ lý AI ở local; demo "duyệt xong hỏi lại được" sẽ rất ấn tượng | 0.5–1 ngày | Rất cao — khép vòng quản trị RAG |
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

*Tài liệu được tạo tự động từ vòng 7 (2026-09-23) — lead tổng hợp từ 8 báo cáo subagent; mỗi mục số 3
đều có commit + test riêng trong lịch sử git.*
