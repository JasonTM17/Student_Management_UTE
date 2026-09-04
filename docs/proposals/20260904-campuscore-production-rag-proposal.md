# Đề xuất AK: RAG học vụ toàn CampusCore và DeepSeek fallback

Trạng thái: **DRAFT — chờ chốt Outcome Contract trước khi mở phase triển khai production**.

Tài liệu này được lập theo chuỗi `/ak:goal-warmup → /ak:advise → /ak:scout → /ak:plan → /ak:cook → /ak:test → /ak:code-review`. Nó là đề xuất triển khai, không phải bằng chứng đã cutover production.

## Kết quả cần đạt

CampusCore có một trợ lý học vụ dùng chung cho các trang đăng ký, học phần, thời khóa biểu, điểm, thông báo, hồ sơ và thesis. Trợ lý trả lời từ corpus đã kiểm duyệt, có citation và phiên bản phát hành. Câu hỏi ngắn, một ý được trả lời trực tiếp bằng RAG. Chỉ câu hỏi cần so sánh, giải thích nhiều bước, nhiều miền dữ liệu hoặc có ngoại lệ mới được chuyển sang DeepSeek V4 Flash để tổng hợp trên context đã truy xuất.

## Phạm vi và nguyên tắc khóa

- Supabase schema `assistant` là nơi authoring và phát hành corpus production.
- `rag-service` là boundary duy nhất đọc release Supabase, kiểm tra hash/count/privacy rồi promote thành một runtime snapshot nguyên tử.
- REST API chỉ chuyển tiếp request đến RAG service; browser và mobile không biết Supabase service key hay DeepSeek key.
- RAG luôn truy xuất trước; không có tài liệu phù hợp thì không gọi DeepSeek.
- Bộ định tuyến khó/dễ chạy deterministic, không gọi thêm model để quyết định.
- DeepSeek chỉ nhận câu hỏi hiện tại và context bounded từ các tài liệu đã publish; không nhận bearer token, email, hồ sơ hoặc raw conversation.
- Nếu Supabase, RAG hoặc DeepSeek lỗi, API trả degraded response có `reasonCode` ổn định và citation còn dùng được.
- Không tự động ghi dữ liệu học vụ giao dịch vào Supabase; dữ liệu đăng ký, điểm và lịch vẫn thuộc PostgreSQL nghiệp vụ.

## Bằng chứng đã scout

- `docker-compose.prod.yml` đã tách `rag-service`, đặt `ASSISTANT_KNOWLEDGE_AUTHORITY_MODE=supabase`, mount secret file và tắt DeepSeek ở REST edge.
- `SupabaseKnowledgeSyncService` đã có kiểm tra release published, row count, SHA-256, domain/locale/privacy và chuyển pointer trong transaction.
- `ThesisAssistantKnowledgeRepository` đang đọc runtime release projection bằng lexical search; các domain catalog học vụ đã được bổ sung cùng corpus thesis.
- `ThesisAssistantService` đã kiểm soát lease, quota, stream, citation và provider output. Bộ định tuyến mới `AssistantDifficultyRouter` giữ truy vấn đơn giản ở RAG và chỉ bật synthesis cho truy vấn khó.
- Supabase hosted target của workspace trước đó trả HTTP 401 và CLI chưa linked project; vì vậy chưa có bằng chứng import/cutover hosted trong tài liệu này.

## Kiến trúc đề xuất

```text
Web/Mobile
    │  /api/v1/assistant/chat (JWT, clientRequestId)
    ▼
REST edge (4010)
    │  private token, owner header
    ▼
RAG service (4011)
    ├─ local PostgreSQL runtime snapshot: lexical retrieval + catalog joins
    ├─ Supabase assistant schema: authoring + published release (sync/reconcile)
    └─ DeepSeek V4 Flash: synthesis only after deterministic hard-query gate
```

Runtime retrieval tiếp tục dùng snapshot PostgreSQL để tránh latency và giữ khả năng phục vụ khi Supabase tạm thời không truy cập được. Supabase vẫn là nguồn phát hành duy nhất; snapshot chỉ được thay pointer sau khi toàn bộ release hợp lệ. Nếu yêu cầu “đọc trực tiếp Supabase mỗi request” được chốt thay cho snapshot, đó là một thay đổi kiến trúc cần user quyết định vì tăng latency, coupling và blast radius.

## Luồng một câu hỏi

1. Xác thực role STUDENT/LECTURER và giới hạn độ dài request.
2. Chặn prompt injection, email, mã sinh viên, token và dữ liệu nhạy cảm.
3. Lexical retrieve theo locale từ release đang active, sau đó bổ sung catalog học vụ.
4. Nếu không có tài liệu: trả `NO_MATCH`, không dispatch provider.
5. Nếu là fact lookup: trả `RAG_GROUNDED`, model `curated-lexical-rag`, kèm citation.
6. Nếu là câu hỏi khó: reserve/dispatch lease, quota-check, gọi `deepseek-v4-flash` với context tối đa 6.000 ký tự và output tối đa 800 token.
7. Validate từng provider segment chỉ tham chiếu source IDs đã truy xuất; nếu provider lỗi hoặc output không an toàn, replace bằng lexical answer và reason degraded.
8. Commit turn/citation bằng CAS; stream chỉ hoàn tất khi có event `done`.

## Các phase nên triển khai

### Phase 1 — Chốt contract và authority

- Xác nhận Supabase project/ref, schema `assistant`, Data API exposure và staff `app_metadata`.
- Chọn snapshot runtime (khuyến nghị) hoặc direct read (không khuyến nghị).
- Xác nhận non-goal: không đưa service-role key vào FE/mobile, không dùng DeepSeek cho fact lookup.

Exit: có project identity đã xác thực, secret file placeholder hợp lệ, Outcome Contract được user chốt.

### Phase 2 — Corpus học vụ toàn trang

- Chuẩn hóa tài liệu cho `REGISTRATION`, `ACADEMIC_CATALOG`, `SCHEDULE`, `GRADES`, `ANNOUNCEMENT`, `PROFILE`, `THESIS`.
- Mỗi tài liệu có slug locale, source, priority, revision, owner, effective/expiry và citation title.
- Tạo release manifest deterministic, count và SHA-256; review bởi admin thứ hai trước publish.
- Đồng bộ 100 học phần và thông báo đã seed vào corpus sau khi nội dung thật được duyệt.

Exit: 100% tài liệu public có domain/locale/source hợp lệ; release hash tái lập; không có PII.

### Phase 3 — RAG router và provider fallback

- Giữ `AssistantDifficultyRouter` deterministic; đo tỷ lệ easy/hard và provider dispatch.
- Bổ sung metric không chứa nội dung câu hỏi: `rag_grounded_total`, `provider_dispatch_total`, `provider_fallback_total`, `knowledge_no_match_total`.
- Giữ quota user/global, timeout, circuit breaker và `FAILED_AMBIGUOUS` hiện có.
- Viết regression cho easy lookup không gọi provider, hard lookup gọi provider, provider outage trả lexical fallback.

Exit: easy lookup không phát sinh provider dispatch; hard lookup chỉ dùng source IDs trong context; mọi lỗi có reason code.

### Phase 4 — Production readiness

- Chạy Supabase advisor/RLS audit; chỉ role quản trị được authoring, anon không có grant.
- Đặt secret bằng file manager, rotate key trước smoke; không ghi key vào log/CI/browser.
- Canary một instance RAG, kiểm tra sync status, release rollback về pointer trước.
- Chạy authenticated smoke JSON/SSE, citation, quota và fallback; sau đó mới mở traffic.

Exit: health/readiness, sync status, rollback và audit log đều có evidence; chưa được gọi là production-ready nếu thiếu Supabase auth hoặc DeepSeek live smoke đã được ủy quyền.

## Acceptance metrics

- `RAG_GROUNDED` chiếm toàn bộ fact lookup; `provider_dispatch_total` bằng 0 cho các request easy trong regression.
- Không có câu trả lời provider nào chứa source ID ngoài retrieval set.
- Release Supabase: `row_count == fetched_count`, hash manifest khớp SHA-256, pointer chuyển nguyên tử.
- Khi provider timeout/429/5xx: HTTP vẫn trả fallback lexical có citation trong budget 8 giây.
- Không có service key trong tracked files, frontend bundle, response body hoặc log.
- Web/mobile giữ nguyên contract JSON/SSE và hiển thị `degraded`/`reasonCode` minh bạch.

## Rủi ro và cách giảm

| Rủi ro | Cách xử lý |
|---|---|
| Supabase target sai hoặc 401 | Dừng import, xác minh project/ref và OAuth/service-role ở server; không retry mù |
| Corpus cũ trả lời sai | Release immutable, hash/count, second review và rollback pointer |
| Gọi DeepSeek quá nhiều | Router deterministic, quota user/global, metric dispatch và budget |
| Provider trả PII hoặc prompt injection | Input/output guard, source-ID gate, replace lexical |
| Snapshot lệch Supabase | Reconcile định kỳ, status endpoint, alert khi version/hash không khớp |
| Direct Supabase read làm tăng latency | Giữ snapshot PostgreSQL làm runtime mặc định; chỉ đổi khi có quyết định riêng |

## Quyết định đang chờ

1. Chốt project Supabase production nào sẽ được dùng và cấp quyền authoring cho role nào.
2. Chốt snapshot runtime (khuyến nghị) hay direct read mỗi request.
3. Chốt danh sách tài liệu học vụ thật sẽ thay thế dữ liệu synthetic hiện tại.
4. Chốt ngưỡng hard-query nếu muốn tinh chỉnh ngoài các marker deterministic hiện có.

Sau khi bốn điểm này được chốt, chuyển tài liệu thành `plan.md` có phase/exit criterion riêng rồi mới mở `/ak:cook` và `/ak:test` cho production path.
