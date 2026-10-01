-- V93: reformat the 24 SPECIALIZED domain documents as markdown.
--
-- Degraded assistant answers (QUOTA_EXCEEDED, provider truncated/unavailable)
-- serve the top document verbatim, and the V71 corpus is one inline
-- "1. ... N. ..." paragraph with no structure, so the degraded answer rendered
-- as a wall of text (round-4 user report). This migration rewrites the
-- authoring content with a heading per topic and one bullet per numbered
-- point, keeping every fact and retrieval keyword verbatim; the assistant
-- formatter and the LLM pathway are unchanged. Authoring derives from the
-- post-V78 state (the ai-rag-vi guard repair is preserved; the blocked
-- phrase is NOT resurrected). Protocol mirrors V78: guarded in-place update,
-- archive the PUBLISHED revision, append a reviewed system revision, and
-- activate a new immutable runtime snapshot only while local SQL owns the
-- active release.

CREATE TEMP TABLE v93_changed_document (id UUID PRIMARY KEY) ON COMMIT DROP;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Kỹ thuật Phần mềm — OOP & Design Patterns

- **Bốn trụ cột OOP**: đóng gói (ẩn trạng thái, expose hành vi), trừu tượng (interface thay vì lớp cụ thể), kế thừa (dùng cho tái sử dụng hành vi, không lạm dụng phân cấp), đa hình (một interface — nhiều hiện thực).
- **SOLID**: S — mỗi lớp chỉ một lý do thay đổi; O — mở rộng bằng thêm lớp mới, không sửa lớp cũ (Strategy, Template Method); L — lớp con phải thay thế được lớp cha without breaking contract (tuân thủ pre/post-condition); I — nhiều interface nhỏ tốt hơn interface khổng lồ; D — phụ thuộc abstraction, tiêm dependency qua constructor.
- **GoF thiết dụng nhất**: Factory Method/Abstract Factory (khởi tạo họ đối tượng), Builder (đối tượng nhiều tham số tùy chọn), Strategy (hoán đổi thuật toán runtime), Observer (event bus, pub-sub), Decorator (mở rộng hành vi không sửa lớp), Adapter (cầu nối interface bên thứ ba), Facade (che độ phức tạp subsystem).
- **Anti-pattern cần tránh**: God class, hướng dữ liệu anemic domain model, kế thừa sâu >2 cấp, singleton gắn trạng thái toàn cục gây khó test.
- **Áp dụng trong đồ án**: định nghĩa interface repository/service trước khi viết implementation để có thể mock khi viết unit test (JUnit + Mockito).',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-oop-solid-design-patterns-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## OOP & Design Patterns

- **Four OOP pillars**: encapsulation (hide state, expose behavior), abstraction (program to interfaces), inheritance (reuse behavior; avoid deep hierarchies), polymorphism (one interface, many implementations).
- **SOLID**: S — one reason to change per class; O — extend by adding classes, not editing stable ones (Strategy, Template Method); L — subtypes must be substitutable without breaking contracts; I — many small interfaces beat one bloated interface; D — depend on abstractions injected via constructors.
- **Most-used GoF patterns**: Factory Method/Abstract Factory (object families), Builder (many optional parameters), Strategy (swappable algorithms at runtime), Observer (event bus, pub-sub), Decorator (extend behavior without subclassing), Adapter (third-party interface bridge), Facade (hide subsystem complexity).
- **Anti-patterns to avoid**: God classes, anemic domain models, inheritance chains deeper than two levels, stateful singletons that break testability.
- **Capstone application**: define repository/service interfaces before implementations so unit tests can mock them (JUnit + Mockito).',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-oop-solid-design-patterns-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Cơ sở dữ liệu quan hệ

- **Chuẩn hóa**: 1NF (giá trị nguyên tử), 2NF (phụ thuộc đầy đủ vào khóa), 3NF (loại phụ thuộc bắc cầu); phi chuẩn hóa có chủ đích chỉ khi profile truy vấn chứng minh cần thiết (bảng báo cáo, counter cache).
- **Khóa và ràng buộc**: khóa chính UUID v4 hoặc BIGINT identity; khóa ngoại bắt buộc có index; ràng buộc CHECK để dữ liệu phi hợp lệ không bao giờ ghi được vào DB — defensive sâu hơn validation ở tầng ứng dụng.
- **Index**: B-tree cho equality/range; composite index theo thứ tự cột trong mệnh đề WHERE/ORDER BY (quy tắc trái nhất); index một phần (partial index WHERE status=''ACTIVE'') tiết kiệm không gian và tăng tốc truy vấn lọc. Tránh index thừa làm chậm ghi.
- **Tối ưu truy vấn**: đọc EXPLAIN (ANALYZE) trước khi tối ưu; tránh SELECT *; tránh hàm bọc cột trong WHERE làm mất index (LOWER(col) cần index biểu thức); phân trang keyset (WHERE id > :last ORDER BY id LIMIT n) thay cho OFFSET lớn.
- **Giao dịch**: giữ transaction ngắn; mức cô lập READ COMMITTED đủ cho đa số nghiệp vụ; chống mất cập nhật bằng optimistic locking (cột version) hoặc SELECT ... FOR UPDATE cho Critical Section.
- **Trong CampusCore**: tham khảo luận cứ phòng race đăng ký học phần 4 lớp (ràng buộc DB, CAS, lease, idempotency) tại tài liệu kiến trúc.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-relational-database-design-sql-optimization-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Relational databases

- **Normalization**: 1NF atomic values, 2NF full-key dependency, 3NF no transitive dependency; denormalize only when query profiling proves it (reporting tables, counter caches).
- **Keys and constraints**: UUIDv4 or BIGINT identity primary keys; every foreign key indexed; CHECK constraints make invalid data unwritable — deeper defense than application validation.
- **Indexing**: B-tree for equality/range; composite indexes follow the leftmost-prefix rule of WHERE/ORDER BY; partial indexes (WHERE status=''ACTIVE'') save space and speed filtered scans; redundant indexes slow writes.
- **Query tuning**: read EXPLAIN (ANALYZE) first; avoid SELECT *; wrapping a column in a function (LOWER(col)) defeats plain indexes — use expression indexes; prefer keyset pagination (WHERE id > :last ORDER BY id LIMIT n) over large OFFSET.
- **Transactions**: keep them short; READ COMMITTED suffices for most workloads; prevent lost updates with optimistic locking (version column) or SELECT ... FOR UPDATE for critical sections.
- **In CampusCore**: study the four-layer registration race defense (DB constraints, CAS, lease, idempotency) in the architecture doc.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-relational-database-design-sql-optimization-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Kiểm thử phần mềm

- **Kim tự tháp test**: nhiều unit test (nhanh, cô lập, mock dependency), vừa integration test (chạy thật DB/HTTP trên H2/Testcontainers), ít E2E test (Playwright — chỉ các luồng kinh doanh chính). Đảo ngược kim tự tháp (nhiều E2E) làm CI chậm và.flaky.
- **Cấu trúc một unit test tốt**: Arrange–Act–Assert; một hành vi mỗi test; tên test mô tả hành vi (detectsExpiredToken_returns401) thay vì testMethod1; không test chi tiết implementation — test hợp đồng quan sát được.
- **TDD**: viết test đỏ → code xanh tối thiểu → refactor; chu kỳ ngắn giữ thiết kế testable. Khi sửa bug: viết trước một test tái hiện bug (đỏ trên code cũ, xanh trên code mới) — đây là regression test vĩnh viễn.
- **Độ phủ**: coverage là chỉ báo, không phải mục tiêu; đạt ~80% nhánh trên module nghiệp vụ trọng yếu (validation, tính điểm, tiền/quota) quan trọng hơn phủ kín getter.
- **Trong dự án này**: FE dùng node:test (259 test hợp đồng/khả quan trạng thái), BE dùng JUnit+Mockito+H2 (87 file), E2E Playwright — mẫu tham chiếu cho đồ án của bạn.
- **Test dữ liệu**: builder/factory thay vì khối setup khổng lồ; fixture chia sẻ phải read-only; thời gian dùng clock tiêm vào, không gọi LocalDate.now() rải rác.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-software-testing-pyramid-tdd-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Testing

- **Test pyramid**: many unit tests (fast, isolated, mocked), some integration tests (real DB/HTTP via H2/Testcontainers), few E2E tests (Playwright — only core business flows). An inverted pyramid makes CI slow and flaky.
- **Anatomy of a good unit test**: Arrange–Act–Assert; one behavior per test; behavior-describing names (detectsExpiredToken_returns401); test observable contracts, not implementation details.
- **TDD**: red test → minimal green code → refactor; short cycles keep the design testable. For bug fixes: write the reproducing test first (red on old code, green on new) — a permanent regression test.
- **Coverage**: an indicator, not a goal; ~80% branch coverage on critical business modules (validation, grading, money/quota) matters more than covering getters.
- **In this project**: FE uses node:test (259 contract/ honesty tests), BE uses JUnit+Mockito+H2 (87 files), E2E Playwright — reference models for your own capstone.
- **Test data**: builders/factories over giant setups; shared fixtures must be read-only; inject clocks instead of sprinkling LocalDate.now().',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-software-testing-pyramid-tdd-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Git & cộng tác

- **Mô hình nhánh**: main luôn deploy được; nhánh tính năng feature/<ten>-ngan theo từng deliverable; nhánh sửa lỗi fix/<ten>; xoá nhánh sau khi merge; giữ vòng đời nhánh ngắn (< vài ngày) để giảm xung đột.
- **Conventional Commits**: feat|fix|refactor|docs|test|chore(scope): mô tả thì hiện tại, dòng thân giải thích vì sao; mỗi commit một logic đơn vị — giúp revert, bisect và sinh changelog tự động.
- **Trước khi mở PR**: chạy đủ test + lint + build cục bộ; diff nhỏ (<400 dòng dễ review); tự review diff của mình trước. Trong PR: mô tả thay đổi, rủi ro, cách kiểm chứng; gắn ảnh trước/sau nếu đổi UI.
- **Xung đột merge**: fetch origin → rebase nhánh của bạn lên base → sửa từng file xung đột (giữ ý nghĩa nghiệp vụ, không chọn mù một phía) → chạy lại test → force-push chỉ với --force-with-lease trên nhánh của mình.
- **An toàn**: không commit secret (.env, key) — dùng biến môi trường; chú ý git status trước khi commit để không kèm file không liên quan; git stash khi cần đổi ngữ cảnh nhanh.
- **Trong nhóm đồ án**: một integration owner chịu trách nhiệm merge lên main; thành viên khác chỉ merge vào nhánh của mình — tránh hai người cùng sửa một file bằng cách phân chia ownership rõ ràng.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-git-branching-collaboration-workflow-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Git & collaboration

- **Branch model**: main is always deployable; short-lived feature/<name> branches per deliverable; fix/<name> for bugfixes; delete branches after merge; keep branch lifetimes short (days) to reduce conflicts.
- **Conventional Commits**: feat|fix|refactor|docs|test|chore(scope): present-tense summary; the body explains why; one logical unit per commit — enabling revert, bisect and automatic changelogs.
- **Before opening a PR**: run tests + lint + build locally; keep diffs reviewable (<400 lines); self-review your diff first. In the PR: describe the change, risks, and how it was verified; attach before/after screenshots for UI changes.
- **Merge conflicts**: fetch origin → rebase onto base → resolve file by file (preserve business intent; never blindly take one side) → rerun tests → force-push only with --force-with-lease on your own branch.
- **Safety**: never commit secrets (.env, keys) — use environment variables; check git status before committing to avoid unrelated files; git stash for quick context switches.
- **Team capstones**: one integration owner merges to main; everyone else merges only into their own branch — prevent same-file collisions through explicit ownership.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-git-branching-collaboration-workflow-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Thiết kế REST API

- **Tài nguyên & phương thức**: danh từ số nhiều (/courses, /enrollments), động từ hành động phức tạp đặt làm sub-action (POST /enrollments/{id}/drop); GET an toàn không đổi trạng thái; POST tạo/thực thi; PUT thay toàn phần idempotent; PATCH thay một phần; DELETE xóa (204).
- **Mã trạng thái**: 200 OK, 201 Created (+Location), 204 No Content, 400 payload sai, 401 chưa đăng nhập, 403 đủ đăng nhập thiếu quyền, 404 không tồn tại/không được thấy, 409 xung đột trạng thái (đăng ký trùng, hội đồng đã chốt), 422 nghiệp vụ không hợp lệ, 429 vượt hạn mức, 503 phụ thuộc lỗi.
- **Lỗi thống nhất**: một envelope duy nhất {status, code (ổn định, không dịch), message (đã bản địa hóa), timestamp, fields}; FE ánh xạ code → thông điệp người dùng, không parse message tiếng Anh.
- **Phân trang & bộ lọc**: page/limit + meta {total, totalPages} hoặc cursor; whitelist tham số truy vấn cho phép — tham số lạ phải 400 thay vì bỏ qua âm thầm.
- **Idempotency**: thao tác tiền/nhập học/đăng ký cần clientRequestId — retry cùng key không tạo bản ghi kép; server CAS trạng thái để chống race.
- **Phiên bản & tài liệu**: /api/v1 tiền tố; OpenAPI/Swagger cập nhật cùng PR (mỗi endpoint có @Operation/@ApiResponse) — hợp đồng API là sản phẩm, không phải phụ lục.
- **Tham chiếu mẫu trong dự án này**: /api/v1/enrollments (idempotency key), /api/v1/announcements (whitelist + phân trang), GlobalExceptionHandler (envelope lỗi thống nhất).',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-rest-api-design-conventions-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## REST API design

- **Resources & verbs**: plural nouns (/courses, /enrollments); complex actions as sub-actions (POST /enrollments/{id}/drop); GET is safe; POST creates/executes; PUT full-replace idempotent; PATCH partial; DELETE removes (204).
- **Status codes**: 200 OK, 201 Created (+Location), 204 No Content, 400 malformed payload, 401 unauthenticated, 403 unauthorized, 404 missing/hidden, 409 state conflict (duplicate enrollment, sealed council), 422 invalid business state, 429 quota, 503 dependency down.
- **Consistent errors**: one envelope {status, code (stable, untranslated), message (localized), timestamp, fields}; the FE maps code → user copy instead of parsing English messages.
- **Pagination & filtering**: page/limit + meta {total, totalPages} or cursors; whitelist allowed query parameters — unknown params must 400, never be silently dropped.
- **Idempotency**: money/enrollment/registration operations need clientRequestId — retrying with the same key must not duplicate records; server-side CAS guards races.
- **Versioning & docs**: /api/v1 prefix; OpenAPI/Swagger updated in the same PR (@Operation/@ApiResponse per endpoint) — the API contract is the product, not an appendix.
- **In-project references**: /api/v1/enrollments (idempotency keys), /api/v1/announcements (allowlist + pagination), GlobalExceptionHandler (unified error envelope).',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-rest-api-design-conventions-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Kiến trúc ứng dụng

- **Bắt đầu modular monolith**: một deployment, phân ranh giới module rõ (auth, academic, engagement, thesis) qua package + interface; tách microservice chỉ khi cần scale độc lập, đội ngũ riêng, hoặc chu kỳ triển khai riêng — chi phí thật của microservices là mạng, phân tán dữ liệu, tracing, triển khai.
- **Phân tầng within module**: Controller (HTTP, validation, ánh xạ DTO) → Service (nghiệp vụ, transaction) → Repository (truy cập dữ liệu); nghiệp vụ không nằm ở controller; DTO không lộ entity.
- **Spring Boot thực dụng**: spring-boot-starter-validation (@Valid + record DTO), NamedParameterJdbcTemplate/JPA tùy độ phức tạp truy vấn, Flyway cho migration (mọi thay đổi schema có migration, không sửa tay DB), @Profile để tách cấu hình môi trường, GlobalExceptionHandler để envelope lỗi nhất quán.
- **Cấu hình 12-factor**: mọi bí mật/endpoint qua biến môi trường (PORT, DB URL, API key); không hard-code; file .env chỉ cục bộ, .env.example committing các tên biến không giá trị.
- **Quan sát được**: log có cấu trúc kèm requestId; health endpoint readiness/liveness; đo thời gian truy vấn chậm.
- **Ví dụ tham chiếu**: CampusCore là modular monolith Spring Boot 3.5/Java 21 (35 controller, Flyway V1–V71, profile persistence), tách sidecar rag-service chỉ cho nhiệm vụ RAG — ranh giới service theo năng lực, không theo cảm tính.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-microservices-monolith-spring-architecture-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Application architecture

- **Start with a modular monolith**: one deployment, clear module boundaries (auth, academic, engagement, thesis) via packages and interfaces; extract microservices only for independent scaling, teams, or release cadence — the real costs are networking, data distribution, tracing and deployment.
- **Layering inside a module**: Controller (HTTP, validation, DTO mapping) → Service (business logic, transactions) → Repository (data access); no business logic in controllers; never expose entities as API payloads.
- **Pragmatic Spring Boot**: spring-boot-starter-validation (@Valid + record DTOs), NamedParameterJdbcTemplate/JPA by query complexity, Flyway migrations for every schema change (never hand-edit the DB), @Profile per environment, a GlobalExceptionHandler for a consistent error envelope.
- **Twelve-factor configuration**: every secret/endpoint via environment variables (PORT, DB URL, API keys); no hard-coding; local .env is git-ignored while .env.example commits variable names without values.
- **Observability**: structured logs with a requestId; readiness/liveness health endpoints; slow-query instrumentation.
- **Reference example**: CampusCore is a Spring Boot 3.5 / Java 21 modular monolith (35 controllers, Flyway V1–V71, persistence profile) with a rag-service sidecar extracted only for the RAG capability — service boundaries follow capability, not fashion.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-microservices-monolith-spring-architecture-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Frontend React/Next.js

- **Luôn hiển thị 4 trạng thái cho dữ liệu bất đồng bộ**: loading (skeleton/spinner), error (thông điệp + nút thử lại), empty (empty state có call-to-action), thành công. Một trang chỉ hiện bảng trắng khi lỗi là lỗi UX chuyên nghiệp.
- **State**: state cục bộ dùng useState/useReducer; trạng thái máy chủ để thư viện đảm nhiệm (TanStack Query: cache, retry, refetch) — không tự sao chép server state vào useState; trạng thái toàn cục thật sự (auth, theme, i18n) mới dùng context.
- **i18n**: mọi chữ giao diện đi qua dictionary (messages.ts) qua hook useI18n(); không viết chữ cứng dạng locale===''vi''?…:… rải trong component — dictionary là nguồn sự thật hai ngôn ngữ, giúp review bản dịch nhất quán.
- **Hành vi phá hoại**: confirm destructive bằng dialog chuẩn của app (không window.confirm), toast cho phản hồi nhanh, aria-label cho nút icon, vai trò/label cho input, giữ focus sau khi đóng dialog.
- **Hiệu năng**: dynamic import cho bundle nặng (editor), keyset/limit phân trang phía server, useMemo/useCallback chỉ khi đo được lợi ích; tránh đặt state dẫn xuất (derive khi render).
- **Hợp đồng API phía FE**: một lớp api client trung tâm (axios instance + interceptor CSRF/refresh) — component không tự fetch rải rác với URL cứng; kiểu dữ liệu chia sẻ ở types/api.ts.
- **Tham chiếu**: thư mục frontend/src của dự án này — components/assistant cho luồng SSE phức tạp, state-block cho Loading/Error/Empty chuẩn.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-react-nextjs-frontend-patterns-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Frontend React/Next.js

- **Always render four states for async data**: loading (skeleton/spinner), error (message + retry), empty (empty state with a call-to-action), success. A blank table on error is a UX defect.
- **State**: local state via useState/useReducer; server state belongs to a data library (TanStack Query: cache, retry, refetch) — do not copy server state into useState; only true global state (auth, theme, i18n) goes into context.
- **i18n**: all UI copy flows through the dictionary (messages.ts) via useI18n(); avoid hardcoded locale===''vi''?…:… ternaries scattered in components — the dictionary is the bilingual source of truth.
- **Destructive actions**: confirm with the app-standard dialog (not window.confirm); toasts for quick feedback; aria-label on icon buttons; labeled inputs; restore focus after closing dialogs.
- **Performance**: dynamic imports for heavy bundles (editors); server-side pagination; useMemo/useCallback only with measured wins; avoid derived state (derive during render).
- **API contract layer**: one central API client (axios instance + CSRF/refresh interceptors) — components never fetch raw URLs; shared types live in types/api.ts.
- **Reference**: this project''s frontend/src — components/assistant for a complex SSE flow, state-block for standardized Loading/Error/Empty.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-react-nextjs-frontend-patterns-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## DevOps

- **Container hoá**: Dockerfile đa giai đoạn (build → runtime slim); .dockerignore loại node_modules/target; một tiến trình một container; cấu hình qua ENV không bake bí mật vào ảnh.
- **Compose cho môi trường local**: định nghĩa đủ postgres/api/web/worker; healthcheck (pg_isready, readiness HTTP) + depends_on condition để thứ tự khởi động đúng; volume cho dữ liệu; port chỉ expose khi cần.
- **CI/CD**: pipeline tối thiểu = lint → unit test → build → integration test → đóng gói ảnh theo SHA commit; chặn merge khi đỏ (branch protection); sinh artifact bản dựng có thể tái lập.
- **Biến môi trường & bí mật**: secret nằm trong secret manager/biến CI, không trong repo; xoay khóa định kỳ; nguyên tắc quyền tối thiểu cho tài khoản dịch vụ.
- **Khả năng tái lập**: một lệnh dựng toàn hệ thống (docker compose up -d --build), seed dữ liệu qua migration thay vì tay; tài liệu runbook ghi đúng lệnh và thông tin đăng nhập demo (cập nhật khi đổi).
- **Khi sự cố**: đọc log container trước (docker compose logs <service>), kiểm tra healthcheck, tái hiện ở local bằng cùng ảnh; sau fix luôn có regression test.
- **Tham chiếu trong dự án**: docker-compose.yml chính + override (prod/rag/e2e), healthcheck readiness có X-Health-Key, Makefile-less scripts trong README.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-devops-cicd-docker-kubernetes-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## DevOps

- **Containerization**: multi-stage Dockerfiles (build → slim runtime); .dockerignore excludes node_modules/target; one process per container; configuration via ENV, never baked secrets.
- **Compose for local environments**: define postgres/api/web/worker; healthchecks (pg_isready, HTTP readiness) + depends_on conditions for correct startup order; volumes for data; expose only needed ports.
- **CI/CD**: minimal pipeline = lint → unit tests → build → integration tests → image tagged by commit SHA; branch protection blocks red merges; reproducible build artifacts.
- **Environment & secrets**: secrets live in a secret manager or CI variables, never in the repo; rotate keys; least-privilege service accounts.
- **Reproducibility**: one command boots the whole system (docker compose up -d --build); data via migrations, not hand-seeding; a runbook documents exact commands and demo credentials (kept current).
- **Incident response**: read container logs first (docker compose logs <service>), check healthchecks, reproduce locally with the same image; ship a regression test with every fix.
- **In-project references**: the main docker-compose.yml plus overrides (prod/rag/e2e), an X-Health-Key readiness check, README scripts.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-devops-cicd-docker-kubernetes-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## An toàn ứng dụng web

- Injection (SQL/NoSQL): luôn parameterized query (:ten_tham_so) — không nối chuỗi SQL; ORM/JdbcTemplate đặt tên tham số là mặc định an toàn; raw query phải đi qua review.
- **Broken auth & session**: mật khẩu bcrypt/argon2; token ngắn hạn + refresh; khóa tài khoản sau N lần sai; đăng xuất thu hồi refresh token; JWT ký HS256/RS256 với secret từ biến môi trường, xác minh exp + aud.
- Phân quyền (IDOR): mọi endpoint theo tài nguyên phải kiểm tra sở hữu/quyền ở tầng service (studentId từ JWT, không từ request); test phủ trường hợp truy cập tài nguyên người khác → 403/404.
- **XSS**: thoát mặc định (React làm sẵn); tuyệt đối hóa dữ liệu HTML động qua sanitizer whitelist; CSP Report-Only trước khi enforce; không chèn href/src từ input người dùng không kiểm scheme (javascript:).
- **CSRF**: double-submit cookie (cc_csrf + X-CSRF-Token) hoặc SameSite=Strict; bắt buộc với mọi POST/PUT/PATCH/DELETE có cookie xác thực.
- **Misconfiguration & data exposure**: tắt Swagger/metrics công khai trong production, không log token/mật khẩu, phân trang + giới hạn số lượng để chống bơm dữ liệu, hạn mức (rate limit) mỗi người dùng cho API tốn kém (AI, export).
- **Tham chiếu trong dự án**: SecurityConfig (JWT, CSRF, rate limit), AssistantInputGuard (chống prompt injection), AnnouncementHtmlSanitizer, ownership check tại conduct/assistant — đọc để xem phòng thủ thật trong code, không chỉ trên lý thuyết.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-owasp-web-security-fundamentals-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Web application security

- Injection (SQL/NoSQL): always parameterized queries (:named_params) — never string-concatenate SQL; ORM/JdbcTemplate named parameters are safe defaults; raw SQL needs review.
- **Auth & sessions**: bcrypt/argon2 password hashing; short-lived tokens + refresh; lock accounts after repeated failures; logout revokes refresh tokens; JWT (HS256/RS256) with secrets from env vars, verified exp + aud.
- Authorization (IDOR): every resource endpoint checks ownership/role in the service layer (studentId from the JWT, never the request); tests must cover cross-user access → 403/404.
- **XSS**: escape by default (React does); sanitize dynamic HTML through a whitelist sanitizer; stage CSP Report-Only before enforcing; validate URL schemes (no javascript:) in href/src from user input.
- **CSRF**: double-submit cookie (cc_csrf + X-CSRF-Token) or SameSite=Strict; mandatory for every authenticated POST/PUT/PATCH/DELETE.
- **Misconfiguration & data exposure**: hide Swagger/metrics in production; never log tokens/passwords; paginate and cap responses against mass extraction; per-user rate limits on expensive APIs (AI, exports).
- **In-project references**: SecurityConfig (JWT, CSRF, rate limiting), AssistantInputGuard (prompt-injection defense), AnnouncementHtmlSanitizer, ownership checks in conduct/assistant — read them to see defenses in real code, not just theory.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-owasp-web-security-fundamentals-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## AI ứng dụng (RAG/LLM)

- **Vấn đề LLM thuần**: hallucination và kiến thức tĩnh. RAG (Retrieval-Augmented Generation) sửa bằng 2 pha: retrieval tìm tài liệu liên quan từ kho có kiểm soát, generation cho LLM viết câu trả lời CHỈ dựa trên ngữ cảnh truy được, kèm trích dẫn.
- **Retrieval**: lexical (BM25/tsvector/LIKE chấm điểm) rẻ, chính xác từ khóa, không cần hạ tầng; semantic (embedding vector + cosine) bắt được nghĩa từ đồng nghĩa; hệ chuyên nghiệp hay dùng hybrid. Chia nhỏ tài liệu (chunking) và gắn priority/domain để lọc theo phạm vi.
- **Prompt an toàn**: system prompt giới hạn phạm vi trả lời; chặn prompt injection bằng input guard (từ khóa kỹ thuật, ''yêu cầu thay đổi chỉ dẫn''); output guard rà kết quả LLM trước khi trả người dùng; chốt thông điệp degraded khi provider lỗi thay vì bịa.
- **Kinh tế vận hành**: LLM tốn kém và chậm — cache, hạn mức theo người dùng/ngày, interception các câu hỏi cá nhân ở tầng DB (không cần LLM), fallback lexical khi quá hạn mức.
- **Đánh giá chất lượng**: bộ câu hỏi vàng + đáp án mong đợi; đo tỷ lệ có citation, tỷ lệ no-match; theo dõi feedback 👍👎 theo message.
- **Tham chiếu trực tiếp**: pipeline CampusCore — ThesisAssistantService (lexical + DeepSeek + degraded), rag-service sidecar, AssistantInputGuard/OutputGuard, kho kiến thức governance 2-admin (publish cần admin thứ hai), domain SPECIALIZED cho kiến thức chuyên môn. Đây là mẫu RAG production-grade để bạn học và mở rộng cho đồ án.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-ai-rag-llm-foundations-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Applied AI (RAG/LLM)

- **The plain-LLM problem**: hallucination and static knowledge. RAG (Retrieval-Augmented Generation) fixes it in two phases: retrieval finds related documents from a governed corpus; generation asks the LLM to answer ONLY from retrieved context, with citations.
- **Retrieval**: lexical (BM25/tsvector/scored LIKE) is cheap, keyword-exact, zero infrastructure; semantic (embeddings + cosine) captures synonyms; production systems often go hybrid. Chunk documents and attach priority/domain for scope filtering.
- **Prompt safety**: a system prompt bounding the answer scope; block prompt injection via input guards (technical phrases, ''ignore instructions''); output guards re-screen LLM output; a clear degraded message when the provider fails — never invented content.
- **Operating economics**: LLMs are costly and slow — cache, per-user daily quotas, intercept personal-data questions at the DB layer (no LLM needed), lexical fallback when quota is exhausted.
- **Quality evaluation**: a golden question set with expected answers; track citation coverage and no-match rate; per-message 👍👎 feedback as a live signal.
- **Direct reference**: the CampusCore pipeline — ThesisAssistantService (lexical + DeepSeek + degraded), a rag-service sidecar, AssistantInputGuard/OutputGuard, two-admin knowledge governance (publishing requires a second admin), and the SPECIALIZED domain for professional knowledge. A production-grade RAG model to learn from and extend in your capstone.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-ai-rag-llm-foundations-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Clean code & review

- **Đặt tên**: tên nói được ý định (remainingCredits thay vì x, isEligibleForDefense thay vì check); nhất quán một từ khái niệm (chọn fetch hoặc get — không trộn); tránh viết tắt riêng tư.
- **Hàm**: làm một việc; tham số ít (≤3; nhiều thì góm object); không boolean flag điều khiển hai hành vi — tách hai hàm; tầng trừu tượng đồng nhất trong một hàm.
- **Comment**: chỉ ghi CHÍNH TRẠCH code không diễn tả được (ràng buộc nghiệp vụ, lí do bất khả kháng, tham chiếu quy định); comment dịch lại dòng lệnh kế bên là nhiễu; xóa code chết thay vì comment lại.
- **Review có trách nhiệm**: review trước khi có quyết định đúng/sai; nhận xét gắn dòng cụ thể + đề nghị cụ thể; phân biệt bắt buộc (bug, bảo mật, vi phạm hợp đồng) và gợi ý (đọc tốt hơn); reviewer đọc bối cảnh test đã chạy gì — không duyệt diff chưa thấy gate xanh.
- **Refactor an toàn**: đổi nhỏ từng bước, test xanh sau mỗi bước; dùng công cụ rename/extract của IDE; không trộn refactor với feature trong một commit.
- Quy ước dự án áp dụng thực tế (tham khảo AGENTS.md của repo): search trước khi viết, thay đổi nhỏ nhất thỏa mục tiêu, không làm yếu gate test để xanh, bảo toàn phần đang dở của người khác.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-clean-code-code-review-standards-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Clean code & review

- **Naming**: intention-revealing names (remainingCredits not x, isEligibleForDefense not check); one word per concept (pick fetch or get — not both); no private abbreviations.
- **Functions**: do one thing; few parameters (≤3 — otherwise bundle into an object); no boolean flags driving two behaviors — split the functions; keep one abstraction level per function.
- **Comments**: state only what the code cannot express (business constraints, hard-won reasons, regulation references); restating the next line is noise; delete dead code instead of commenting it out.
- **Responsible review**: review before approval decisions; comments anchored to specific lines with concrete suggestions; distinguish blocking (bugs, security, contract violations) from suggestions (readability); reviewers verify which gates ran — never approve an unverified diff.
- **Safe refactoring**: small steps, tests green after each; use IDE rename/extract; never mix refactors with features in one commit.
- This repo''s working rules (see AGENTS.md): search before writing; the smallest change that satisfies the goal; never weaken a test gate to go green; preserve others'' in-progress work.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-clean-code-code-review-standards-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Nghề nghiệp & phỏng vấn

- **Lộ trình vị trí**: Intern/Fresher (thực thi có hướng dẫn) → Junior (hoàn thành task độc lập) → Middle (thiết kế module, review code) → Senior (thiết kế hệ thống, dẫn dắt kỹ thuật) → Tech Lead/Architect (cân bằng nghiệp vụ–kỹ thuật–con người). Tại mỗi mốc, kỹ năng giao tiếp và làm việc nhóm tăng trọng số.
- **Kỹ năng nền lấy được từ đồ án**: một dự án end-to-end (DB migration, API có phân quyền, FE có đủ 4 trạng thái dữ liệu, test + CI, chạy được bằng một lệnh docker compose) thuyết phục hơn nhiều dự án demo rời rạc; viết README/runbook cho nó.
- **Chuẩn bị phỏng vấn kỹ thuật**: (a) CS core — cấu trúc dữ liệu (mảng, hash map, cây, đồ thị), độ phức tạp Big-O, mạng (HTTP/TCP/DNS), OS (process/thread, memory); (b) framework thật sự bạn dùng (Spring Boot, React); (c) hệ thống — vẽ và giải thích một hệ bạn viết: luồng request, dữ liệu, điểm fail và cách phục hồi.
- **Coding interview**: giải thích hướng giải trước khi code; đặt câu hỏi làm rõ input/output/rìa; test case tự sinh (rỗng, 1 phần tử, trùng lặp, cực đại); nói to suy luận khi code.
- **Behavioral**: chuẩn bị 5 câu chuyện STAR (thành tựu, xung đột nhóm, thất bại rút kinh nghiệm, deadline, đã sửa lỗi khó); hỏi ngược về quy trình review, onboarding, chuẩn hóa code của công ty.
- **Hồ sơ**: GitHub sạch (commit chuẩn, README từng dự án), LinkedIn/CV một trang định hướng thành quả (động từ mạnh + con số), portfolio nêu vấn đề–giải pháp–kết quả của mỗi dự án.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-se-career-roadmap-interview-prep-vi'
       AND content LIKE '%Kiến thức chuyên môn%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

WITH corrected AS (
    UPDATE assistant.knowledge_document
       SET content = '## Careers & interviews

- **Role ladder**: Intern/Fresher (guided execution) → Junior (independent tasks) → Middle (module design, code review) → Senior (system design, technical leadership) → Tech Lead/Architect (balancing business, tech and people). Communication weight grows at every step.
- **Portfolio leverage from your capstone**: one end-to-end project (DB migrations, an authorized API, a FE with all four data states, tests + CI, bootable via one docker compose command) beats many disjoint demos; write its README/runbook.
- **Technical interview prep**: (a) CS core — data structures (arrays, hash maps, trees, graphs), Big-O, networking (HTTP/TCP/DNS), OS (processes/threads, memory); (b) the frameworks you truly used (Spring Boot, React); (c) system design — draw and explain a system you built: request flow, data, failure points, recovery.
- **Coding interviews**: explain the approach before coding; clarify inputs/outputs/edges; generate your own tests (empty, single element, duplicates, max bounds); think out loud while coding.
- **Behavioral**: prepare five STAR stories (achievement, team conflict, instructive failure, deadline, a hard bug you fixed); ask back about review process, onboarding, and code standards.
- **Profile hygiene**: a clean GitHub (conventional commits, per-project READMEs), one-page outcome-oriented CV (strong verbs + numbers), a portfolio framing each project as problem–solution–result.',
           updated_at = CURRENT_TIMESTAMP
     WHERE slug = 'specialized-se-career-roadmap-interview-prep-en'
       AND content LIKE '%Software engineering domain knowledge%'
     RETURNING id
)
INSERT INTO v93_changed_document SELECT id FROM corrected;

UPDATE assistant.knowledge_document_revision r
   SET state = 'ARCHIVED'
 WHERE r.state = 'PUBLISHED'
   AND r.document_id IN (SELECT id FROM v93_changed_document);

INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, domain, locale, slug, title, content,
     source, priority, created_by, reviewed_by, published_at)
SELECT md5(d.id::text || '-specialized-markdown-v93')::uuid,
       d.id, COALESCE((SELECT MAX(version) FROM assistant.knowledge_document_revision r
                        WHERE r.document_id = d.id), 0) + 1,
       'PUBLISHED', d.domain, d.locale, d.slug, d.title, d.content,
       d.source, d.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP
FROM assistant.knowledge_document d
JOIN v93_changed_document changed ON changed.id = d.id;

-- Lock the same singleton used by runtime promotion. Under remote authority,
-- leave its active Supabase projection untouched; the new Supabase seed must
-- instead be published and reconciled through its own governance workflow.
SELECT active_release_id FROM assistant.knowledge_runtime_state
 WHERE singleton = TRUE FOR UPDATE;

CREATE TEMP TABLE v93_projected_runtime ON COMMIT DROP AS
SELECT p.source_id,
       COALESCE(r.id, p.revision_id) AS revision_id,
       COALESCE(r.version, p.version) AS version,
       COALESCE(r.domain, p.domain) AS domain,
       COALESCE(r.slug, p.slug) AS slug,
       COALESCE(r.locale, p.locale) AS locale,
       COALESCE(r.title, p.title) AS title,
       COALESCE(r.content, p.content) AS content,
       COALESCE(r.source, p.source) AS source,
       COALESCE(r.priority, p.priority) AS priority,
       p.active, p.visibility,
       COALESCE(r.published_at, p.published_at) AS published_at
FROM assistant.knowledge_runtime_state s
JOIN assistant.knowledge_release current_release ON current_release.id = s.active_release_id
JOIN assistant.knowledge_runtime_document p ON p.release_id = current_release.id
LEFT JOIN v93_changed_document changed ON changed.id::text = p.source_id
LEFT JOIN assistant.knowledge_document_revision r
  ON r.document_id = changed.id AND r.state = 'PUBLISHED'
WHERE s.singleton = TRUE
  AND current_release.status = 'PUBLISHED'
  AND current_release.source IN ('MANUAL', 'LEGACY')
  AND EXISTS (SELECT 1 FROM v93_changed_document);

CREATE TEMP TABLE v93_summary ON COMMIT DROP AS
SELECT COUNT(*)::integer AS row_count,
       encode(thesis.digest(COALESCE(string_agg(
           concat_ws('|', source_id, COALESCE(revision_id::text, ''), version::text,
                     domain, slug, locale, title, content, source, priority::text,
                     active::text, visibility), E'\n' ORDER BY source_id), ''), 'sha256'), 'hex') AS corpus_hash,
       COALESCE(jsonb_agg(jsonb_build_object('sourceId', source_id, 'domain', domain,
           'slug', slug, 'locale', locale) ORDER BY source_id), '[]'::jsonb) AS documents
FROM v93_projected_runtime;

-- A matching hash is not enough to reuse an archived or differently sourced
-- release. Fail the Flyway transaction rather than commit repaired authoring
-- with a stale runtime pointer. An operator can inspect that exceptional
-- history before retrying the migration.
DO $$
DECLARE existing_release assistant.knowledge_release%ROWTYPE;
BEGIN
    IF NOT EXISTS (SELECT 1 FROM v93_projected_runtime) THEN
        RETURN;
    END IF;
    SELECT r.* INTO existing_release
      FROM assistant.knowledge_release r
      JOIN v93_summary s ON s.corpus_hash = r.corpus_hash
     LIMIT 1;
    IF existing_release.id IS NOT NULL THEN
        IF existing_release.status <> 'PUBLISHED'
           OR existing_release.source NOT IN ('MANUAL', 'LEGACY') THEN
            RAISE EXCEPTION 'V93 target corpus exists under an inactive or foreign release';
        END IF;
        IF EXISTS (
            (SELECT source_id, revision_id, version, domain, slug, locale,
                    title, content, source, priority, active, visibility
               FROM v93_projected_runtime
             EXCEPT
             SELECT source_id, revision_id, version, domain, slug, locale,
                    title, content, source, priority, active, visibility
               FROM assistant.knowledge_runtime_document
              WHERE release_id = existing_release.id)
            UNION ALL
            (SELECT source_id, revision_id, version, domain, slug, locale,
                    title, content, source, priority, active, visibility
               FROM assistant.knowledge_runtime_document
              WHERE release_id = existing_release.id
             EXCEPT
             SELECT source_id, revision_id, version, domain, slug, locale,
                    title, content, source, priority, active, visibility
               FROM v93_projected_runtime)
        ) THEN
            RAISE EXCEPTION 'V93 target corpus differs from matching release rows';
        END IF;
    END IF;
END;
$$;

INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest,
     created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000093'::uuid,
       'specialized-domain-knowledge-markdown-v93', summary.corpus_hash, summary.row_count,
       'MANUAL', 'PUBLISHED',
       jsonb_build_object('schemaVersion', 1, 'corpusVersion', 'specialized-domain-knowledge-markdown-v93',
                          'rowCount', summary.row_count, 'sha256', summary.corpus_hash,
                          'documents', summary.documents),
       'system-migration', CURRENT_TIMESTAMP, s.active_release_id
FROM v93_summary summary
CROSS JOIN assistant.knowledge_runtime_state s
WHERE s.singleton = TRUE
  AND EXISTS (SELECT 1 FROM v93_projected_runtime)
  AND NOT EXISTS (SELECT 1 FROM assistant.knowledge_release
                  WHERE corpus_hash = summary.corpus_hash);

INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title,
     content, source, priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000093'::uuid,
       source_id, revision_id, version, domain, slug, locale, title, content,
       source, priority, active, visibility, published_at
FROM v93_projected_runtime
WHERE EXISTS (SELECT 1 FROM assistant.knowledge_release
              WHERE id = '00000000-0000-0000-0000-000000000093'::uuid);

UPDATE assistant.knowledge_runtime_state s
   SET active_release_id = next_release.id, updated_at = CURRENT_TIMESTAMP
  FROM v93_summary summary
  JOIN assistant.knowledge_release next_release
    ON next_release.corpus_hash = summary.corpus_hash AND next_release.status = 'PUBLISHED'
 WHERE s.singleton = TRUE
   AND EXISTS (SELECT 1 FROM v93_projected_runtime);
