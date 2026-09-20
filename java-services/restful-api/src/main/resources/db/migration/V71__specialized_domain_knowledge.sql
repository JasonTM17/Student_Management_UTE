-- Flyway Migration V71: SPECIALIZED assistant-knowledge domain (Trợ lý chuyên sâu)
-- Publishes the curated professional-domain corpus (software engineering expert Q&A)
-- with domain = 'SPECIALIZED', consumed by the specialized assistant scope
-- (ChatRequest.scope = 'specialized'). Mirrors
-- supabase/seed/assistant-specialized-knowledge.json, which is uploaded to the
-- Supabase authoring project via scripts/supabase/assistant-knowledge.mjs using
-- SUPABASE_URL / SUPABASE_SERVICE_ROLE_KEY from the process environment.
-- Regenerate with: node scripts/generate-specialized-knowledge.mjs
-- Replaces the active release with immutable snapshot
-- '00000000-0000-0000-0000-000000000071'.

SET search_path = thesis, assistant, public;

-- 0. Extend the governed domain whitelist (V16) with SPECIALIZED on all three
--    knowledge tables before any row is written.
ALTER TABLE assistant.knowledge_document
    DROP CONSTRAINT IF EXISTS assistant_knowledge_domain_valid;
ALTER TABLE assistant.knowledge_document
    ADD CONSTRAINT assistant_knowledge_domain_valid
        CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED'));
ALTER TABLE assistant.knowledge_document_revision
    DROP CONSTRAINT IF EXISTS assistant_revision_domain_valid;
ALTER TABLE assistant.knowledge_document_revision
    ADD CONSTRAINT assistant_revision_domain_valid
        CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED'));
ALTER TABLE assistant.knowledge_runtime_document
    DROP CONSTRAINT IF EXISTS assistant_runtime_domain_valid;
ALTER TABLE assistant.knowledge_runtime_document
    ADD CONSTRAINT assistant_runtime_domain_valid
        CHECK (domain IN ('THESIS', 'REGISTRATION', 'ACADEMIC_CATALOG', 'ANNOUNCEMENT', 'POLICY', 'GENERAL_FAQ', 'SPECIALIZED'));

-- 1. Insert new documents into assistant.knowledge_document
INSERT INTO assistant.knowledge_document
    (id, slug, locale, title, content, source, priority, domain)
SELECT md5(seed.slug || '-document')::uuid, seed.slug, seed.locale, seed.title, seed.content,
       'campuscore-specialized-corpus', seed.priority, seed.domain
FROM (VALUES
    ('specialized-oop-solid-design-patterns-vi', 'vi',
     'Lập trình hướng đối tượng, nguyên tắc SOLID và các mẫu thiết kế GoF thiết yếu',
     'Kiến thức chuyên môn Kỹ thuật Phần mềm — OOP & Design Patterns: 1. Bốn trụ cột OOP: đóng gói (ẩn trạng thái, expose hành vi), trừu tượng (interface thay vì lớp cụ thể), kế thừa (dùng cho tái sử dụng hành vi, không lạm dụng phân cấp), đa hình (một interface — nhiều hiện thực). 2. SOLID: S — mỗi lớp chỉ một lý do thay đổi; O — mở rộng bằng thêm lớp mới, không sửa lớp cũ (Strategy, Template Method); L — lớp con phải thay thế được lớp cha without breaking contract (tuân thủ pre/post-condition); I — nhiều interface nhỏ tốt hơn interface khổng lồ; D — phụ thuộc abstraction, tiêm dependency qua constructor. 3. GoF thiết dụng nhất: Factory Method/Abstract Factory (khởi tạo họ đối tượng), Builder (đối tượng nhiều tham số tùy chọn), Strategy (hoán đổi thuật toán runtime), Observer (event bus, pub-sub), Decorator (mở rộng hành vi không sửa lớp), Adapter (cầu nối interface bên thứ ba), Facade (che độ phức tạp subsystem). 4. Anti-pattern cần tránh: God class, hướng dữ liệu anemic domain model, kế thừa sâu >2 cấp, singleton gắn trạng thái toàn cục gây khó test. 5. Áp dụng trong đồ án: định nghĩa interface repository/service trước khi viết implementation để có thể mock khi viết unit test (JUnit + Mockito).',
     10, 'SPECIALIZED'),
    ('specialized-oop-solid-design-patterns-en', 'en',
     'Object-oriented programming, SOLID principles and essential GoF design patterns',
     'Software engineering domain knowledge — OOP & Design Patterns: 1. Four OOP pillars: encapsulation (hide state, expose behavior), abstraction (program to interfaces), inheritance (reuse behavior; avoid deep hierarchies), polymorphism (one interface, many implementations). 2. SOLID: S — one reason to change per class; O — extend by adding classes, not editing stable ones (Strategy, Template Method); L — subtypes must be substitutable without breaking contracts; I — many small interfaces beat one bloated interface; D — depend on abstractions injected via constructors. 3. Most-used GoF patterns: Factory Method/Abstract Factory (object families), Builder (many optional parameters), Strategy (swappable algorithms at runtime), Observer (event bus, pub-sub), Decorator (extend behavior without subclassing), Adapter (third-party interface bridge), Facade (hide subsystem complexity). 4. Anti-patterns to avoid: God classes, anemic domain models, inheritance chains deeper than two levels, stateful singletons that break testability. 5. Capstone application: define repository/service interfaces before implementations so unit tests can mock them (JUnit + Mockito).',
     10, 'SPECIALIZED'),
    ('specialized-relational-database-design-sql-optimization-vi', 'vi',
     'Thiết kế cơ sở dữ liệu quan hệ và tối ưu truy vấn SQL',
     'Kiến thức chuyên môn — Cơ sở dữ liệu quan hệ: 1. Chuẩn hóa: 1NF (giá trị nguyên tử), 2NF (phụ thuộc đầy đủ vào khóa), 3NF (loại phụ thuộc bắc cầu); phi chuẩn hóa có chủ đích chỉ khi profile truy vấn chứng minh cần thiết (bảng báo cáo, counter cache). 2. Khóa và ràng buộc: khóa chính UUID v4 hoặc BIGINT identity; khóa ngoại bắt buộc có index; ràng buộc CHECK để dữ liệu phi hợp lệ không bao giờ ghi được vào DB — defensive sâu hơn validation ở tầng ứng dụng. 3. Index: B-tree cho equality/range; composite index theo thứ tự cột trong mệnh đề WHERE/ORDER BY (quy tắc trái nhất); index một phần (partial index WHERE status=''ACTIVE'') tiết kiệm không gian và tăng tốc truy vấn lọc. Tránh index thừa làm chậm ghi. 4. Tối ưu truy vấn: đọc EXPLAIN (ANALYZE) trước khi tối ưu; tránh SELECT *; tránh hàm bọc cột trong WHERE làm mất index (LOWER(col) cần index biểu thức); phân trang keyset (WHERE id > :last ORDER BY id LIMIT n) thay cho OFFSET lớn. 5. Giao dịch: giữ transaction ngắn; mức cô lập READ COMMITTED đủ cho đa số nghiệp vụ; chống mất cập nhật bằng optimistic locking (cột version) hoặc SELECT ... FOR UPDATE cho Critical Section. 6. Trong CampusCore: tham khảo luận cứ phòng race đăng ký học phần 4 lớp (ràng buộc DB, CAS, lease, idempotency) tại tài liệu kiến trúc.',
     10, 'SPECIALIZED'),
    ('specialized-relational-database-design-sql-optimization-en', 'en',
     'Relational database design and SQL query optimization',
     'Software engineering domain knowledge — Relational databases: 1. Normalization: 1NF atomic values, 2NF full-key dependency, 3NF no transitive dependency; denormalize only when query profiling proves it (reporting tables, counter caches). 2. Keys and constraints: UUIDv4 or BIGINT identity primary keys; every foreign key indexed; CHECK constraints make invalid data unwritable — deeper defense than application validation. 3. Indexing: B-tree for equality/range; composite indexes follow the leftmost-prefix rule of WHERE/ORDER BY; partial indexes (WHERE status=''ACTIVE'') save space and speed filtered scans; redundant indexes slow writes. 4. Query tuning: read EXPLAIN (ANALYZE) first; avoid SELECT *; wrapping a column in a function (LOWER(col)) defeats plain indexes — use expression indexes; prefer keyset pagination (WHERE id > :last ORDER BY id LIMIT n) over large OFFSET. 5. Transactions: keep them short; READ COMMITTED suffices for most workloads; prevent lost updates with optimistic locking (version column) or SELECT ... FOR UPDATE for critical sections. 6. In CampusCore: study the four-layer registration race defense (DB constraints, CAS, lease, idempotency) in the architecture doc.',
     10, 'SPECIALIZED'),
    ('specialized-software-testing-pyramid-tdd-vi', 'vi',
     'Chiến lược kiểm thử phần mềm: kim tự tháp test, TDD và tiêu chí done',
     'Kiến thức chuyên môn — Kiểm thử phần mềm: 1. Kim tự tháp test: nhiều unit test (nhanh, cô lập, mock dependency), vừa integration test (chạy thật DB/HTTP trên H2/Testcontainers), ít E2E test (Playwright — chỉ các luồng kinh doanh chính). Đảo ngược kim tự tháp (nhiều E2E) làm CI chậm và.flaky. 2. Cấu trúc một unit test tốt: Arrange–Act–Assert; một hành vi mỗi test; tên test mô tả hành vi (detectsExpiredToken_returns401) thay vì testMethod1; không test chi tiết implementation — test hợp đồng quan sát được. 3. TDD: viết test đỏ → code xanh tối thiểu → refactor; chu kỳ ngắn giữ thiết kế testable. Khi sửa bug: viết trước một test tái hiện bug (đỏ trên code cũ, xanh trên code mới) — đây là regression test vĩnh viễn. 4. Độ phủ: coverage là chỉ báo, không phải mục tiêu; đạt ~80% nhánh trên module nghiệp vụ trọng yếu (validation, tính điểm, tiền/quota) quan trọng hơn phủ kín getter. 5. Trong dự án này: FE dùng node:test (259 test hợp đồng/khả quan trạng thái), BE dùng JUnit+Mockito+H2 (87 file), E2E Playwright — mẫu tham chiếu cho đồ án của bạn. 6. Test dữ liệu: builder/factory thay vì khối setup khổng lồ; fixture chia sẻ phải read-only; thời gian dùng clock tiêm vào, không gọi LocalDate.now() rải rác.',
     10, 'SPECIALIZED'),
    ('specialized-software-testing-pyramid-tdd-en', 'en',
     'Software testing strategy: the test pyramid, TDD and definition of done',
     'Software engineering domain knowledge — Testing: 1. Test pyramid: many unit tests (fast, isolated, mocked), some integration tests (real DB/HTTP via H2/Testcontainers), few E2E tests (Playwright — only core business flows). An inverted pyramid makes CI slow and flaky. 2. Anatomy of a good unit test: Arrange–Act–Assert; one behavior per test; behavior-describing names (detectsExpiredToken_returns401); test observable contracts, not implementation details. 3. TDD: red test → minimal green code → refactor; short cycles keep the design testable. For bug fixes: write the reproducing test first (red on old code, green on new) — a permanent regression test. 4. Coverage: an indicator, not a goal; ~80% branch coverage on critical business modules (validation, grading, money/quota) matters more than covering getters. 5. In this project: FE uses node:test (259 contract/ honesty tests), BE uses JUnit+Mockito+H2 (87 files), E2E Playwright — reference models for your own capstone. 6. Test data: builders/factories over giant setups; shared fixtures must be read-only; inject clocks instead of sprinkling LocalDate.now().',
     10, 'SPECIALIZED'),
    ('specialized-git-branching-collaboration-workflow-vi', 'vi',
     'Quy trình Git chuyên nghiệp: branching, commit chuẩn, code review và xử lý xung đột',
     'Kiến thức chuyên môn — Git & cộng tác: 1. Mô hình nhánh: main luôn deploy được; nhánh tính năng feature/<ten>-ngan theo từng deliverable; nhánh sửa lỗi fix/<ten>; xoá nhánh sau khi merge; giữ vòng đời nhánh ngắn (< vài ngày) để giảm xung đột. 2. Conventional Commits: feat|fix|refactor|docs|test|chore(scope): mô tả thì hiện tại, dòng thân giải thích vì sao; mỗi commit một logic đơn vị — giúp revert, bisect và sinh changelog tự động. 3. Trước khi mở PR: chạy đủ test + lint + build cục bộ; diff nhỏ (<400 dòng dễ review); tự review diff của mình trước. Trong PR: mô tả thay đổi, rủi ro, cách kiểm chứng; gắn ảnh trước/sau nếu đổi UI. 4. Xung đột merge: fetch origin → rebase nhánh của bạn lên base → sửa từng file xung đột (giữ ý nghĩa nghiệp vụ, không chọn mù một phía) → chạy lại test → force-push chỉ với --force-with-lease trên nhánh của mình. 5. An toàn: không commit secret (.env, key) — dùng biến môi trường; chú ý git status trước khi commit để không kèm file không liên quan; git stash khi cần đổi ngữ cảnh nhanh. 6. Trong nhóm đồ án: một integration owner chịu trách nhiệm merge lên main; thành viên khác chỉ merge vào nhánh của mình — tránh hai người cùng sửa một file bằng cách phân chia ownership rõ ràng.',
     11, 'SPECIALIZED'),
    ('specialized-git-branching-collaboration-workflow-en', 'en',
     'Professional Git workflow: branching, conventional commits, code review and conflict resolution',
     'Software engineering domain knowledge — Git & collaboration: 1. Branch model: main is always deployable; short-lived feature/<name> branches per deliverable; fix/<name> for bugfixes; delete branches after merge; keep branch lifetimes short (days) to reduce conflicts. 2. Conventional Commits: feat|fix|refactor|docs|test|chore(scope): present-tense summary; the body explains why; one logical unit per commit — enabling revert, bisect and automatic changelogs. 3. Before opening a PR: run tests + lint + build locally; keep diffs reviewable (<400 lines); self-review your diff first. In the PR: describe the change, risks, and how it was verified; attach before/after screenshots for UI changes. 4. Merge conflicts: fetch origin → rebase onto base → resolve file by file (preserve business intent; never blindly take one side) → rerun tests → force-push only with --force-with-lease on your own branch. 5. Safety: never commit secrets (.env, keys) — use environment variables; check git status before committing to avoid unrelated files; git stash for quick context switches. 6. Team capstones: one integration owner merges to main; everyone else merges only into their own branch — prevent same-file collisions through explicit ownership.',
     11, 'SPECIALIZED'),
    ('specialized-rest-api-design-conventions-vi', 'vi',
     'Thiết kế REST API chuyên nghiệp: tài nguyên, mã trạng thái, phân trang, lỗi và idempotency',
     'Kiến thức chuyên môn — Thiết kế REST API: 1. Tài nguyên & phương thức: danh từ số nhiều (/courses, /enrollments), động từ hành động phức tạp đặt làm sub-action (POST /enrollments/{id}/drop); GET an toàn không đổi trạng thái; POST tạo/thực thi; PUT thay toàn phần idempotent; PATCH thay một phần; DELETE xóa (204). 2. Mã trạng thái: 200 OK, 201 Created (+Location), 204 No Content, 400 payload sai, 401 chưa đăng nhập, 403 đủ đăng nhập thiếu quyền, 404 không tồn tại/không được thấy, 409 xung đột trạng thái (đăng ký trùng, hội đồng đã chốt), 422 nghiệp vụ không hợp lệ, 429 vượt hạn mức, 503 phụ thuộc lỗi. 3. Lỗi thống nhất: một envelope duy nhất {status, code (ổn định, không dịch), message (đã bản địa hóa), timestamp, fields}; FE ánh xạ code → thông điệp người dùng, không parse message tiếng Anh. 4. Phân trang & bộ lọc: page/limit + meta {total, totalPages} hoặc cursor; whitelist tham số truy vấn cho phép — tham số lạ phải 400 thay vì bỏ qua âm thầm. 5. Idempotency: thao tác tiền/nhập học/đăng ký cần clientRequestId — retry cùng key không tạo bản ghi kép; server CAS trạng thái để chống race. 6. Phiên bản & tài liệu: /api/v1 tiền tố; OpenAPI/Swagger cập nhật cùng PR (mỗi endpoint có @Operation/@ApiResponse) — hợp đồng API là sản phẩm, không phải phụ lục. 7. Tham chiếu mẫu trong dự án này: /api/v1/enrollments (idempotency key), /api/v1/announcements (whitelist + phân trang), GlobalExceptionHandler (envelope lỗi thống nhất).',
     11, 'SPECIALIZED'),
    ('specialized-rest-api-design-conventions-en', 'en',
     'Professional REST API design: resources, status codes, pagination, errors and idempotency',
     'Software engineering domain knowledge — REST API design: 1. Resources & verbs: plural nouns (/courses, /enrollments); complex actions as sub-actions (POST /enrollments/{id}/drop); GET is safe; POST creates/executes; PUT full-replace idempotent; PATCH partial; DELETE removes (204). 2. Status codes: 200 OK, 201 Created (+Location), 204 No Content, 400 malformed payload, 401 unauthenticated, 403 unauthorized, 404 missing/hidden, 409 state conflict (duplicate enrollment, sealed council), 422 invalid business state, 429 quota, 503 dependency down. 3. Consistent errors: one envelope {status, code (stable, untranslated), message (localized), timestamp, fields}; the FE maps code → user copy instead of parsing English messages. 4. Pagination & filtering: page/limit + meta {total, totalPages} or cursors; whitelist allowed query parameters — unknown params must 400, never be silently dropped. 5. Idempotency: money/enrollment/registration operations need clientRequestId — retrying with the same key must not duplicate records; server-side CAS guards races. 6. Versioning & docs: /api/v1 prefix; OpenAPI/Swagger updated in the same PR (@Operation/@ApiResponse per endpoint) — the API contract is the product, not an appendix. 7. In-project references: /api/v1/enrollments (idempotency keys), /api/v1/announcements (allowlist + pagination), GlobalExceptionHandler (unified error envelope).',
     11, 'SPECIALIZED'),
    ('specialized-microservices-monolith-spring-architecture-vi', 'vi',
     'Kiến trúc monolith vs microservices và stack Spring Boot thực dụng cho đồ án',
     'Kiến thức chuyên môn — Kiến trúc ứng dụng: 1. Bắt đầu modular monolith: một deployment, phân ranh giới module rõ (auth, academic, engagement, thesis) qua package + interface; tách microservice chỉ khi cần scale độc lập, đội ngũ riêng, hoặc chu kỳ triển khai riêng — chi phí thật của microservices là mạng, phân tán dữ liệu, tracing, triển khai. 2. Phân tầng within module: Controller (HTTP, validation, ánh xạ DTO) → Service (nghiệp vụ, transaction) → Repository (truy cập dữ liệu); nghiệp vụ không nằm ở controller; DTO không lộ entity. 3. Spring Boot thực dụng: spring-boot-starter-validation (@Valid + record DTO), NamedParameterJdbcTemplate/JPA tùy độ phức tạp truy vấn, Flyway cho migration (mọi thay đổi schema có migration, không sửa tay DB), @Profile để tách cấu hình môi trường, GlobalExceptionHandler để envelope lỗi nhất quán. 4. Cấu hình 12-factor: mọi bí mật/endpoint qua biến môi trường (PORT, DB URL, API key); không hard-code; file .env chỉ cục bộ, .env.example committing các tên biến không giá trị. 5. Quan sát được: log có cấu trúc kèm requestId; health endpoint readiness/liveness; đo thời gian truy vấn chậm. 6. Ví dụ tham chiếu: CampusCore là modular monolith Spring Boot 3.5/Java 21 (35 controller, Flyway V1–V71, profile persistence), tách sidecar rag-service chỉ cho nhiệm vụ RAG — ranh giới service theo năng lực, không theo cảm tính.',
     12, 'SPECIALIZED'),
    ('specialized-microservices-monolith-spring-architecture-en', 'en',
     'Monolith vs microservices architecture and a pragmatic Spring Boot stack for capstones',
     'Software engineering domain knowledge — Application architecture: 1. Start with a modular monolith: one deployment, clear module boundaries (auth, academic, engagement, thesis) via packages and interfaces; extract microservices only for independent scaling, teams, or release cadence — the real costs are networking, data distribution, tracing and deployment. 2. Layering inside a module: Controller (HTTP, validation, DTO mapping) → Service (business logic, transactions) → Repository (data access); no business logic in controllers; never expose entities as API payloads. 3. Pragmatic Spring Boot: spring-boot-starter-validation (@Valid + record DTOs), NamedParameterJdbcTemplate/JPA by query complexity, Flyway migrations for every schema change (never hand-edit the DB), @Profile per environment, a GlobalExceptionHandler for a consistent error envelope. 4. Twelve-factor configuration: every secret/endpoint via environment variables (PORT, DB URL, API keys); no hard-coding; local .env is git-ignored while .env.example commits variable names without values. 5. Observability: structured logs with a requestId; readiness/liveness health endpoints; slow-query instrumentation. 6. Reference example: CampusCore is a Spring Boot 3.5 / Java 21 modular monolith (35 controllers, Flyway V1–V71, persistence profile) with a rag-service sidecar extracted only for the RAG capability — service boundaries follow capability, not fashion.',
     12, 'SPECIALIZED'),
    ('specialized-react-nextjs-frontend-patterns-vi', 'vi',
     'Mẫu React/Next.js chuyên nghiệp: state, dữ liệu bất đồng bộ, i18n và trạng thái UI',
     'Kiến thức chuyên môn — Frontend React/Next.js: 1. Luôn hiển thị 4 trạng thái cho dữ liệu bất đồng bộ: loading (skeleton/spinner), error (thông điệp + nút thử lại), empty (empty state có call-to-action), thành công. Một trang chỉ hiện bảng trắng khi lỗi là lỗi UX chuyên nghiệp. 2. State: state cục bộ dùng useState/useReducer; trạng thái máy chủ để thư viện đảm nhiệm (TanStack Query: cache, retry, refetch) — không tự sao chép server state vào useState; trạng thái toàn cục thật sự (auth, theme, i18n) mới dùng context. 3. i18n: mọi chữ giao diện đi qua dictionary (messages.ts) qua hook useI18n(); không viết chữ cứng dạng locale===''vi''?…:… rải trong component — dictionary là nguồn sự thật hai ngôn ngữ, giúp review bản dịch nhất quán. 4. Hành vi phá hoại: confirm destructive bằng dialog chuẩn của app (không window.confirm), toast cho phản hồi nhanh, aria-label cho nút icon, vai trò/label cho input, giữ focus sau khi đóng dialog. 5. Hiệu năng: dynamic import cho bundle nặng (editor), keyset/limit phân trang phía server, useMemo/useCallback chỉ khi đo được lợi ích; tránh đặt state dẫn xuất (derive khi render). 6. Hợp đồng API phía FE: một lớp api client trung tâm (axios instance + interceptor CSRF/refresh) — component không tự fetch rải rác với URL cứng; kiểu dữ liệu chia sẻ ở types/api.ts. 7. Tham chiếu: thư mục frontend/src của dự án này — components/assistant cho luồng SSE phức tạp, state-block cho Loading/Error/Empty chuẩn.',
     12, 'SPECIALIZED'),
    ('specialized-react-nextjs-frontend-patterns-en', 'en',
     'Professional React/Next.js patterns: state, async data, i18n and UI states',
     'Software engineering domain knowledge — Frontend React/Next.js: 1. Always render four states for async data: loading (skeleton/spinner), error (message + retry), empty (empty state with a call-to-action), success. A blank table on error is a UX defect. 2. State: local state via useState/useReducer; server state belongs to a data library (TanStack Query: cache, retry, refetch) — do not copy server state into useState; only true global state (auth, theme, i18n) goes into context. 3. i18n: all UI copy flows through the dictionary (messages.ts) via useI18n(); avoid hardcoded locale===''vi''?…:… ternaries scattered in components — the dictionary is the bilingual source of truth. 4. Destructive actions: confirm with the app-standard dialog (not window.confirm); toasts for quick feedback; aria-label on icon buttons; labeled inputs; restore focus after closing dialogs. 5. Performance: dynamic imports for heavy bundles (editors); server-side pagination; useMemo/useCallback only with measured wins; avoid derived state (derive during render). 6. API contract layer: one central API client (axios instance + CSRF/refresh interceptors) — components never fetch raw URLs; shared types live in types/api.ts. 7. Reference: this project''s frontend/src — components/assistant for a complex SSE flow, state-block for standardized Loading/Error/Empty.',
     12, 'SPECIALIZED'),
    ('specialized-devops-cicd-docker-kubernetes-vi', 'vi',
     'DevOps cho sinh viên: Docker, CI/CD và hạ tầng chạy thử nghiệm lặp lại được',
     'Kiến thức chuyên môn — DevOps: 1. Container hoá: Dockerfile đa giai đoạn (build → runtime slim); .dockerignore loại node_modules/target; một tiến trình một container; cấu hình qua ENV không bake bí mật vào ảnh. 2. Compose cho môi trường local: định nghĩa đủ postgres/api/web/worker; healthcheck (pg_isready, readiness HTTP) + depends_on condition để thứ tự khởi động đúng; volume cho dữ liệu; port chỉ expose khi cần. 3. CI/CD: pipeline tối thiểu = lint → unit test → build → integration test → đóng gói ảnh theo SHA commit; chặn merge khi đỏ (branch protection); sinh artifact bản dựng có thể tái lập. 4. Biến môi trường & bí mật: secret nằm trong secret manager/biến CI, không trong repo; xoay khóa định kỳ; nguyên tắc quyền tối thiểu cho tài khoản dịch vụ. 5. Khả năng tái lập: một lệnh dựng toàn hệ thống (docker compose up -d --build), seed dữ liệu qua migration thay vì tay; tài liệu runbook ghi đúng lệnh và thông tin đăng nhập demo (cập nhật khi đổi). 6. Khi sự cố: đọc log container trước (docker compose logs <service>), kiểm tra healthcheck, tái hiện ở local bằng cùng ảnh; sau fix luôn có regression test. 7. Tham chiếu trong dự án: docker-compose.yml chính + override (prod/rag/e2e), healthcheck readiness có X-Health-Key, Makefile-less scripts trong README.',
     13, 'SPECIALIZED'),
    ('specialized-devops-cicd-docker-kubernetes-en', 'en',
     'DevOps for students: Docker, CI/CD and reproducible runtime environments',
     'Software engineering domain knowledge — DevOps: 1. Containerization: multi-stage Dockerfiles (build → slim runtime); .dockerignore excludes node_modules/target; one process per container; configuration via ENV, never baked secrets. 2. Compose for local environments: define postgres/api/web/worker; healthchecks (pg_isready, HTTP readiness) + depends_on conditions for correct startup order; volumes for data; expose only needed ports. 3. CI/CD: minimal pipeline = lint → unit tests → build → integration tests → image tagged by commit SHA; branch protection blocks red merges; reproducible build artifacts. 4. Environment & secrets: secrets live in a secret manager or CI variables, never in the repo; rotate keys; least-privilege service accounts. 5. Reproducibility: one command boots the whole system (docker compose up -d --build); data via migrations, not hand-seeding; a runbook documents exact commands and demo credentials (kept current). 6. Incident response: read container logs first (docker compose logs <service>), check healthchecks, reproduce locally with the same image; ship a regression test with every fix. 7. In-project references: the main docker-compose.yml plus overrides (prod/rag/e2e), an X-Health-Key readiness check, README scripts.',
     13, 'SPECIALIZED'),
    ('specialized-owasp-web-security-fundamentals-vi', 'vi',
     'An toàn web thiết yếu: OWASP Top 10 rút gọn và biện pháp phòng thủ trong code',
     'Kiến thức chuyên môn — An toàn ứng dụng web: 1. Injection (SQL/NoSQL): luôn parameterized query (:ten_tham_so) — không nối chuỗi SQL; ORM/JdbcTemplate đặt tên tham số là mặc định an toàn; raw query phải đi qua review. 2. Broken auth & session: mật khẩu bcrypt/argon2; token ngắn hạn + refresh; khóa tài khoản sau N lần sai; đăng xuất thu hồi refresh token; JWT ký HS256/RS256 với secret từ biến môi trường, xác minh exp + aud. 3. Phân quyền (IDOR): mọi endpoint theo tài nguyên phải kiểm tra sở hữu/quyền ở tầng service (studentId từ JWT, không từ request); test phủ trường hợp truy cập tài nguyên người khác → 403/404. 4. XSS: thoát mặc định (React làm sẵn); tuyệt đối hóa dữ liệu HTML động qua sanitizer whitelist; CSP Report-Only trước khi enforce; không chèn href/src từ input người dùng không kiểm scheme (javascript:). 5. CSRF: double-submit cookie (cc_csrf + X-CSRF-Token) hoặc SameSite=Strict; bắt buộc với mọi POST/PUT/PATCH/DELETE có cookie xác thực. 6. Misconfiguration & data exposure: tắt Swagger/metrics công khai trong production, không log token/mật khẩu, phân trang + giới hạn số lượng để chống bơm dữ liệu, hạn mức (rate limit) mỗi người dùng cho API tốn kém (AI, export). 7. Tham chiếu trong dự án: SecurityConfig (JWT, CSRF, rate limit), AssistantInputGuard (chống prompt injection), AnnouncementHtmlSanitizer, ownership check tại conduct/assistant — đọc để xem phòng thủ thật trong code, không chỉ trên lý thuyết.',
     13, 'SPECIALIZED'),
    ('specialized-owasp-web-security-fundamentals-en', 'en',
     'Essential web security: a condensed OWASP Top 10 and concrete code-level defenses',
     'Software engineering domain knowledge — Web application security: 1. Injection (SQL/NoSQL): always parameterized queries (:named_params) — never string-concatenate SQL; ORM/JdbcTemplate named parameters are safe defaults; raw SQL needs review. 2. Auth & sessions: bcrypt/argon2 password hashing; short-lived tokens + refresh; lock accounts after repeated failures; logout revokes refresh tokens; JWT (HS256/RS256) with secrets from env vars, verified exp + aud. 3. Authorization (IDOR): every resource endpoint checks ownership/role in the service layer (studentId from the JWT, never the request); tests must cover cross-user access → 403/404. 4. XSS: escape by default (React does); sanitize dynamic HTML through a whitelist sanitizer; stage CSP Report-Only before enforcing; validate URL schemes (no javascript:) in href/src from user input. 5. CSRF: double-submit cookie (cc_csrf + X-CSRF-Token) or SameSite=Strict; mandatory for every authenticated POST/PUT/PATCH/DELETE. 6. Misconfiguration & data exposure: hide Swagger/metrics in production; never log tokens/passwords; paginate and cap responses against mass extraction; per-user rate limits on expensive APIs (AI, exports). 7. In-project references: SecurityConfig (JWT, CSRF, rate limiting), AssistantInputGuard (prompt-injection defense), AnnouncementHtmlSanitizer, ownership checks in conduct/assistant — read them to see defenses in real code, not just theory.',
     13, 'SPECIALIZED'),
    ('specialized-ai-rag-llm-foundations-vi', 'vi',
     'Nền tảng AI ứng dụng: RAG, LLM, embedding và xây trợ lý trả lời có dẫn chiếu',
     'Kiến thức chuyên môn — AI ứng dụng (RAG/LLM): 1. Vấn đề LLM thuần: hallucination và kiến thức tĩnh. RAG (Retrieval-Augmented Generation) sửa bằng 2 pha: retrieval tìm tài liệu liên quan từ kho có kiểm soát, generation cho LLM viết câu trả lời CHỈ dựa trên ngữ cảnh truy được, kèm trích dẫn. 2. Retrieval: lexical (BM25/tsvector/LIKE chấm điểm) rẻ, chính xác từ khóa, không cần hạ tầng; semantic (embedding vector + cosine) bắt được nghĩa từ đồng nghĩa; hệ chuyên nghiệp hay dùng hybrid. Chia nhỏ tài liệu (chunking) và gắn priority/domain để lọc theo phạm vi. 3. Prompt an toàn: system prompt giới hạn phạm vi trả lời; chặn prompt injection bằng input guard (từ khóa kỹ thuật, ''bỏ qua hướng dẫn''); output guard rà kết quả LLM trước khi trả người dùng; chốt thông điệp degraded khi provider lỗi thay vì bịa. 4. Kinh tế vận hành: LLM tốn kém và chậm — cache, hạn mức theo người dùng/ngày, interception các câu hỏi cá nhân ở tầng DB (không cần LLM), fallback lexical khi quá hạn mức. 5. Đánh giá chất lượng: bộ câu hỏi vàng + đáp án mong đợi; đo tỷ lệ có citation, tỷ lệ no-match; theo dõi feedback 👍👎 theo message. 6. Tham chiếu trực tiếp: pipeline CampusCore — ThesisAssistantService (lexical + DeepSeek + degraded), rag-service sidecar, AssistantInputGuard/OutputGuard, kho kiến thức governance 2-admin (publish cần admin thứ hai), domain SPECIALIZED cho kiến thức chuyên môn. Đây là mẫu RAG production-grade để bạn học và mở rộng cho đồ án.',
     14, 'SPECIALIZED'),
    ('specialized-ai-rag-llm-foundations-en', 'en',
     'Applied AI foundations: RAG, LLMs, embeddings and building a citation-grounded assistant',
     'Software engineering domain knowledge — Applied AI (RAG/LLM): 1. The plain-LLM problem: hallucination and static knowledge. RAG (Retrieval-Augmented Generation) fixes it in two phases: retrieval finds related documents from a governed corpus; generation asks the LLM to answer ONLY from retrieved context, with citations. 2. Retrieval: lexical (BM25/tsvector/scored LIKE) is cheap, keyword-exact, zero infrastructure; semantic (embeddings + cosine) captures synonyms; production systems often go hybrid. Chunk documents and attach priority/domain for scope filtering. 3. Prompt safety: a system prompt bounding the answer scope; block prompt injection via input guards (technical phrases, ''ignore instructions''); output guards re-screen LLM output; a clear degraded message when the provider fails — never invented content. 4. Operating economics: LLMs are costly and slow — cache, per-user daily quotas, intercept personal-data questions at the DB layer (no LLM needed), lexical fallback when quota is exhausted. 5. Quality evaluation: a golden question set with expected answers; track citation coverage and no-match rate; per-message 👍👎 feedback as a live signal. 6. Direct reference: the CampusCore pipeline — ThesisAssistantService (lexical + DeepSeek + degraded), a rag-service sidecar, AssistantInputGuard/OutputGuard, two-admin knowledge governance (publishing requires a second admin), and the SPECIALIZED domain for professional knowledge. A production-grade RAG model to learn from and extend in your capstone.',
     14, 'SPECIALIZED'),
    ('specialized-clean-code-code-review-standards-vi', 'vi',
     'Clean code và chuẩn code review: đặt tên, hàm nhỏ, comment đúng chỗ, review có trách nhiệm',
     'Kiến thức chuyên môn — Clean code & review: 1. Đặt tên: tên nói được ý định (remainingCredits thay vì x, isEligibleForDefense thay vì check); nhất quán một từ khái niệm (chọn fetch hoặc get — không trộn); tránh viết tắt riêng tư. 2. Hàm: làm một việc; tham số ít (≤3; nhiều thì góm object); không boolean flag điều khiển hai hành vi — tách hai hàm; tầng trừu tượng đồng nhất trong một hàm. 3. Comment: chỉ ghi CHÍNH TRẠCH code không diễn tả được (ràng buộc nghiệp vụ, lí do bất khả kháng, tham chiếu quy định); comment dịch lại dòng lệnh kế bên là nhiễu; xóa code chết thay vì comment lại. 4. Review có trách nhiệm: review trước khi có quyết định đúng/sai; nhận xét gắn dòng cụ thể + đề nghị cụ thể; phân biệt bắt buộc (bug, bảo mật, vi phạm hợp đồng) và gợi ý (đọc tốt hơn); reviewer đọc bối cảnh test đã chạy gì — không duyệt diff chưa thấy gate xanh. 5. Refactor an toàn: đổi nhỏ từng bước, test xanh sau mỗi bước; dùng công cụ rename/extract của IDE; không trộn refactor với feature trong một commit. 6. Quy ước dự án áp dụng thực tế (tham khảo AGENTS.md của repo): search trước khi viết, thay đổi nhỏ nhất thỏa mục tiêu, không làm yếu gate test để xanh, bảo toàn phần đang dở của người khác.',
     14, 'SPECIALIZED'),
    ('specialized-clean-code-code-review-standards-en', 'en',
     'Clean code and review standards: naming, small functions, purposeful comments, responsible reviews',
     'Software engineering domain knowledge — Clean code & review: 1. Naming: intention-revealing names (remainingCredits not x, isEligibleForDefense not check); one word per concept (pick fetch or get — not both); no private abbreviations. 2. Functions: do one thing; few parameters (≤3 — otherwise bundle into an object); no boolean flags driving two behaviors — split the functions; keep one abstraction level per function. 3. Comments: state only what the code cannot express (business constraints, hard-won reasons, regulation references); restating the next line is noise; delete dead code instead of commenting it out. 4. Responsible review: review before approval decisions; comments anchored to specific lines with concrete suggestions; distinguish blocking (bugs, security, contract violations) from suggestions (readability); reviewers verify which gates ran — never approve an unverified diff. 5. Safe refactoring: small steps, tests green after each; use IDE rename/extract; never mix refactors with features in one commit. 6. This repo''s working rules (see AGENTS.md): search before writing; the smallest change that satisfies the goal; never weaken a test gate to go green; preserve others'' in-progress work.',
     14, 'SPECIALIZED'),
    ('specialized-se-career-roadmap-interview-prep-vi', 'vi',
     'Lộ trình nghề nghiệp Kỹ thuật phần mềm và chuẩn bị phỏng vấn chuyên môn',
     'Kiến thức chuyên môn — Nghề nghiệp & phỏng vấn: 1. Lộ trình vị trí: Intern/Fresher (thực thi có hướng dẫn) → Junior (hoàn thành task độc lập) → Middle (thiết kế module, review code) → Senior (thiết kế hệ thống, dẫn dắt kỹ thuật) → Tech Lead/Architect (cân bằng nghiệp vụ–kỹ thuật–con người). Tại mỗi mốc, kỹ năng giao tiếp và làm việc nhóm tăng trọng số. 2. Kỹ năng nền lấy được từ đồ án: một dự án end-to-end (DB migration, API có phân quyền, FE có đủ 4 trạng thái dữ liệu, test + CI, chạy được bằng một lệnh docker compose) thuyết phục hơn nhiều dự án demo rời rạc; viết README/runbook cho nó. 3. Chuẩn bị phỏng vấn kỹ thuật: (a) CS core — cấu trúc dữ liệu (mảng, hash map, cây, đồ thị), độ phức tạp Big-O, mạng (HTTP/TCP/DNS), OS (process/thread, memory); (b) framework thật sự bạn dùng (Spring Boot, React); (c) hệ thống — vẽ và giải thích một hệ bạn viết: luồng request, dữ liệu, điểm fail và cách phục hồi. 4. Coding interview: giải thích hướng giải trước khi code; đặt câu hỏi làm rõ input/output/rìa; test case tự sinh (rỗng, 1 phần tử, trùng lặp, cực đại); nói to suy luận khi code. 5. Behavioral: chuẩn bị 5 câu chuyện STAR (thành tựu, xung đột nhóm, thất bại rút kinh nghiệm, deadline, đã sửa lỗi khó); hỏi ngược về quy trình review, onboarding, chuẩn hóa code của công ty. 6. Hồ sơ: GitHub sạch (commit chuẩn, README từng dự án), LinkedIn/CV một trang định hướng thành quả (động từ mạnh + con số), portfolio nêu vấn đề–giải pháp–kết quả của mỗi dự án.',
     15, 'SPECIALIZED'),
    ('specialized-se-career-roadmap-interview-prep-en', 'en',
     'Software engineering career roadmap and technical interview preparation',
     'Software engineering domain knowledge — Careers & interviews: 1. Role ladder: Intern/Fresher (guided execution) → Junior (independent tasks) → Middle (module design, code review) → Senior (system design, technical leadership) → Tech Lead/Architect (balancing business, tech and people). Communication weight grows at every step. 2. Portfolio leverage from your capstone: one end-to-end project (DB migrations, an authorized API, a FE with all four data states, tests + CI, bootable via one docker compose command) beats many disjoint demos; write its README/runbook. 3. Technical interview prep: (a) CS core — data structures (arrays, hash maps, trees, graphs), Big-O, networking (HTTP/TCP/DNS), OS (processes/threads, memory); (b) the frameworks you truly used (Spring Boot, React); (c) system design — draw and explain a system you built: request flow, data, failure points, recovery. 4. Coding interviews: explain the approach before coding; clarify inputs/outputs/edges; generate your own tests (empty, single element, duplicates, max bounds); think out loud while coding. 5. Behavioral: prepare five STAR stories (achievement, team conflict, instructive failure, deadline, a hard bug you fixed); ask back about review process, onboarding, and code standards. 6. Profile hygiene: a clean GitHub (conventional commits, per-project READMEs), one-page outcome-oriented CV (strong verbs + numbers), a portfolio framing each project as problem–solution–result.',
     15, 'SPECIALIZED')
) AS seed(slug, locale, title, content, priority, domain)
ON CONFLICT (slug) DO UPDATE
SET title = EXCLUDED.title,
    content = EXCLUDED.content,
    priority = EXCLUDED.priority,
    domain = EXCLUDED.domain,
    updated_at = CURRENT_TIMESTAMP;

-- 2. Archive superseded published revisions of these documents
UPDATE assistant.knowledge_document_revision r
SET state = 'ARCHIVED'
WHERE r.state = 'PUBLISHED'
  AND r.document_id IN (
      SELECT d.id FROM assistant.knowledge_document d
      WHERE d.slug IN (
          'specialized-oop-solid-design-patterns-vi',
          'specialized-oop-solid-design-patterns-en',
          'specialized-relational-database-design-sql-optimization-vi',
          'specialized-relational-database-design-sql-optimization-en',
          'specialized-software-testing-pyramid-tdd-vi',
          'specialized-software-testing-pyramid-tdd-en',
          'specialized-git-branching-collaboration-workflow-vi',
          'specialized-git-branching-collaboration-workflow-en',
          'specialized-rest-api-design-conventions-vi',
          'specialized-rest-api-design-conventions-en',
          'specialized-microservices-monolith-spring-architecture-vi',
          'specialized-microservices-monolith-spring-architecture-en',
          'specialized-react-nextjs-frontend-patterns-vi',
          'specialized-react-nextjs-frontend-patterns-en',
          'specialized-devops-cicd-docker-kubernetes-vi',
          'specialized-devops-cicd-docker-kubernetes-en',
          'specialized-owasp-web-security-fundamentals-vi',
          'specialized-owasp-web-security-fundamentals-en',
          'specialized-ai-rag-llm-foundations-vi',
          'specialized-ai-rag-llm-foundations-en',
          'specialized-clean-code-code-review-standards-vi',
          'specialized-clean-code-code-review-standards-en',
          'specialized-se-career-roadmap-interview-prep-vi',
          'specialized-se-career-roadmap-interview-prep-en'));

-- 3. Publish new versioned revisions for the documents
WITH target AS (
    SELECT d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content,
           COALESCE(MAX(r.version), 0) + 1 AS next_version
    FROM assistant.knowledge_document d
    LEFT JOIN assistant.knowledge_document_revision r ON r.document_id = d.id
    WHERE d.active = TRUE AND d.visibility = 'PUBLIC'
      AND d.slug IN (
          'specialized-oop-solid-design-patterns-vi',
          'specialized-oop-solid-design-patterns-en',
          'specialized-relational-database-design-sql-optimization-vi',
          'specialized-relational-database-design-sql-optimization-en',
          'specialized-software-testing-pyramid-tdd-vi',
          'specialized-software-testing-pyramid-tdd-en',
          'specialized-git-branching-collaboration-workflow-vi',
          'specialized-git-branching-collaboration-workflow-en',
          'specialized-rest-api-design-conventions-vi',
          'specialized-rest-api-design-conventions-en',
          'specialized-microservices-monolith-spring-architecture-vi',
          'specialized-microservices-monolith-spring-architecture-en',
          'specialized-react-nextjs-frontend-patterns-vi',
          'specialized-react-nextjs-frontend-patterns-en',
          'specialized-devops-cicd-docker-kubernetes-vi',
          'specialized-devops-cicd-docker-kubernetes-en',
          'specialized-owasp-web-security-fundamentals-vi',
          'specialized-owasp-web-security-fundamentals-en',
          'specialized-ai-rag-llm-foundations-vi',
          'specialized-ai-rag-llm-foundations-en',
          'specialized-clean-code-code-review-standards-vi',
          'specialized-clean-code-code-review-standards-en',
          'specialized-se-career-roadmap-interview-prep-vi',
          'specialized-se-career-roadmap-interview-prep-en')
    GROUP BY d.id, d.locale, d.slug, d.title, d.source, d.priority, d.domain, d.content
)
INSERT INTO assistant.knowledge_document_revision
    (id, document_id, version, state, locale, slug, title, content, source, priority,
     created_by, reviewed_by, published_at, domain)
SELECT md5(t.id::text || '-revision-' || t.next_version::text)::uuid,
       t.id, t.next_version, 'PUBLISHED', t.locale, t.slug, t.title, t.content, t.source,
       t.priority, 'system-migration', 'system-migration', CURRENT_TIMESTAMP, t.domain
FROM target t
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_document_revision existing
    WHERE existing.document_id = t.id AND existing.version = t.next_version);

-- 4. Compute release summary and create release '00000000-0000-0000-0000-000000000071'
WITH canonical AS (
    SELECT d.id::text AS source_id, r.version, COALESCE(r.domain, d.domain, 'POLICY') AS domain,
           d.slug, d.locale, d.title, d.content, d.source, d.priority
    FROM assistant.knowledge_document d
    JOIN assistant.knowledge_document_revision r
      ON r.document_id = d.id
     AND r.state = 'PUBLISHED'
    WHERE d.active = TRUE
      AND d.visibility = 'PUBLIC'
),
summary AS (
    SELECT
        encode(
            thesis.digest(
                coalesce(
                    string_agg(
                        concat_ws('|', source_id, domain, slug, locale, title, content, source,
                                  priority::text, version::text),
                        E'\n' ORDER BY source_id
                    ),
                    ''
                ),
                'sha256'
            ),
            'hex'
        ) AS corpus_hash,
        COUNT(*)::integer AS row_count,
        COALESCE(
            jsonb_agg(
                jsonb_build_object('sourceId', source_id, 'domain', domain, 'slug', slug, 'locale', locale)
                ORDER BY source_id
            ),
            '[]'::jsonb
        ) AS documents
    FROM canonical
)
INSERT INTO assistant.knowledge_release
    (id, corpus_version, corpus_hash, row_count, source, status, manifest, created_by, activated_at, previous_release_id)
SELECT '00000000-0000-0000-0000-000000000071'::uuid,
       'specialized-domain-corpus-v71', corpus_hash, row_count, 'MANUAL', 'PUBLISHED',
       jsonb_build_object(
           'schemaVersion', 1,
           'corpusVersion', 'specialized-domain-corpus-v71',
           'rowCount', row_count,
           'sha256', corpus_hash,
           'documents', documents
       ),
       'system-migration', CURRENT_TIMESTAMP,
       (SELECT active_release_id
        FROM assistant.knowledge_runtime_state
        WHERE singleton = TRUE)
FROM summary
WHERE NOT EXISTS (
    SELECT 1
    FROM assistant.knowledge_release
    WHERE id = '00000000-0000-0000-0000-000000000071'::uuid
);

-- 5. Project all published public documents into release '00000000-0000-0000-0000-000000000071'
INSERT INTO assistant.knowledge_runtime_document
    (release_id, source_id, revision_id, version, domain, slug, locale, title, content, source,
     priority, active, visibility, published_at)
SELECT '00000000-0000-0000-0000-000000000071'::uuid,
       d.id::text, r.id, r.version, COALESCE(r.domain, d.domain, 'POLICY'), d.slug, d.locale,
       d.title, d.content, d.source, d.priority, TRUE, 'PUBLIC',
       COALESCE(r.published_at, CURRENT_TIMESTAMP)
FROM assistant.knowledge_document d
JOIN assistant.knowledge_document_revision r
  ON r.document_id = d.id
 AND r.state = 'PUBLISHED'
WHERE d.active = TRUE
  AND d.visibility = 'PUBLIC'
  AND NOT EXISTS (
      SELECT 1
      FROM assistant.knowledge_runtime_document existing
      WHERE existing.release_id = '00000000-0000-0000-0000-000000000071'::uuid
        AND existing.source_id = d.id::text
  );

-- 6. Switch the runtime state to active release v71
UPDATE assistant.knowledge_runtime_state
SET active_release_id = '00000000-0000-0000-0000-000000000071'::uuid,
    updated_at = CURRENT_TIMESTAMP
WHERE singleton = TRUE;
