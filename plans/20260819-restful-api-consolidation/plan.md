---
title: Consolidate microservices into one Java RESTful API
status: in-progress
---

# Outcome

Chuyển CampusCore từ topology nhiều backend runtime sang **một ứng dụng Java
Spring Boot RESTful API dạng modular monolith**. Ứng dụng này là một deployable
duy nhất, có các package/module nội bộ cho auth, academic, people, enrollment,
finance, engagement, notification, analytics, thesis và chatbot. Next.js vẫn là
web client; Expo/React Native là mobile client dùng chung API; chatbot là module
được kiểm quyền bên trong REST API, không phải một service phải vận hành riêng.

## Success signal

Mục tiêu chỉ được gọi là đạt khi tất cả điều kiện sau có bằng chứng trên cùng
exact HEAD:

1. Một Maven project `java-services/restful-api` build thành một JAR/container
   duy nhất và expose contract `/api/v1`.
2. Các core flow của web và mobile chạy qua API đó: đăng nhập, phân quyền,
   dashboard, học kỳ/môn/lớp, enrollment/grades, thesis, thông báo, hồ sơ và
   finance ở mức phạm vi môn học.
3. Auth cookie + CSRF của web và bearer/refresh của mobile giữ được claims,
   quyền, error contract và negative cases cần thiết.
4. Chatbot chạy được mock mode và provider mode với timeout, rate limit,
   fallback, audit/redaction; provider key không xuất hiện ở client hoặc log.
5. Một PostgreSQL cluster và một migration owner được kiểm tra bằng backup,
   restore, reconciliation và migration rehearsal.
6. FE Stitch web hiện tại không regress; mobile có app độc lập với parity cho
   các flow chính, không chỉ là responsive web.
7. E2E authenticated/mutation, runtime smoke, performance baseline, rollback
   thực tế, và review độc lập Advisor/Kongming/Wukong đều PASS.

# Scope

## In scope

- Thiết kế và triển khai `java-services/restful-api` như một Spring Boot app duy
  nhất, Java 21, package boundaries rõ ràng nhưng không tách thành runtime
  service.
- Đóng băng public route `/api/v1`, JSON response/error, auth, CSRF, JWT claims,
  upload và notification compatibility trước khi chuyển code.
- Migrate từng domain theo strangler sequence, với một canonical writer cho mỗi
  domain và legacy service làm rollback source trong thời gian chuyển tiếp.
- Chuyển database về một PostgreSQL cluster với một Flyway migration stream;
  giữ logical schema theo domain ở wave đầu để giảm rủi ro, chỉ flatten schema
  khi có lý do môn học và rehearsal chứng minh được.
- Giản lược local demo profile còn API, frontend, PostgreSQL; Redis/MinIO và
  observability là profile tùy chọn khi thật sự cần.
- Giữ Stitch design evidence và hoàn thiện mobile client dùng chung API.
- Ghi test, runtime evidence, migration/rollback evidence, journal và commit
  theo từng bounded phase.

## Non-goals

- Không xóa, reset, đổi tên hoặc bỏ qua Node services, Prisma migrations, Java
  thesis pilot, nginx/Kubernetes manifests, `.agents/` hay dữ liệu hiện có trước
  khi có backup, parity và rollback gate.
- Không big-bang merge toàn bộ schema hoặc cho phép nhiều canonical writer.
- Không giữ service mesh, event choreography, multi-region, multi-agent RAG,
  vector database hoặc production payment integration trong scope môn học.
- Không coi một source-level contract test, một image cũ, một health 200 hoặc
  một build xanh là bằng chứng runtime/cutover/production.
- Không gọi responsive web là native mobile app.

# Authority and current evidence

- Branch: `feature/java-thesis-platform`.
- Evidence snapshot trước khi tạo plan: `9ec033b5cd126c5d2051d45ac52bf7a8aee46b73`.
- Worktree có untracked `.agents/`; phải giữ nguyên và không stage.
- Hiện tại có 8 Prisma schema theo service với tổng khoảng 111 model theo các
  schema đang được inventory; Compose còn nhiều backend image, Redis, RabbitMQ,
  MinIO và observability.
- `java-services` hiện có 4 Maven service (`auth`, `engagement`, `notification`,
  `thesis`), trong đó thesis mới là pilot; chưa có REST API monolith hoàn chỉnh.
- FE đã có 22 Stitch-relevant web screens và responsive evidence; `mobile/` đã
  có native Expo/React Native scaffold với 23 screen definitions, nhưng chưa có
  typecheck theo dependency đầy đủ hay Android/iOS runtime evidence.
- Chatbot hiện là assistant adapter trong Java thesis pilot và `AssistantPanel`
  của Next.js, chưa phải một deployable độc lập đã được kiểm chứng.
- Runtime Java image hiện có là image cũ, không được dùng làm source-current
  evidence; browser rerun và isolated E2E hiện vẫn `NOT_RUN` vì giới hạn runtime.
- C: đang có free-space dao động thấp; build image nặng và thao tác dọn dẹp
  broad bị hoãn. Chỉ dùng bounded, read-only disk/process checks hoặc cleanup
  exact target đã chứng minh an toàn.

# Target architecture

```text
                 +-----------------------+
                 | Next.js Stitch web    |
                 +-----------+-----------+
                             |
                 +-----------v-----------+
                 | Java RESTful API      |  one deployable / one JAR
                 | Spring Boot 3 + J21   |
                 |-----------------------|
                 | auth                  |
                 | academic              |
                 | people                |
                 | enrollment + grades   |
                 | finance               |
                 | engagement            |
                 | notification (REST)   |
                 | analytics              |
                 | thesis                |
                 | chatbot adapter       |
                 +-----------+-----------+
                             |
                 +-----------v-----------+
                 | PostgreSQL + Flyway   |
                 +-----------------------+

       Expo/React Native mobile app uses the same public API contract.
```

The final course profile has one backend runtime. An external LLM provider is a
dependency behind `chatbot`'s server-side port, not a public application block.
The old topology remains available only as a migration/rollback profile until
the retirement gates pass.

# Ordered stages

1. **Architecture contract and freeze** — this plan, route/auth/data inventory,
   ownership matrix, non-goals and exact review gate. No traffic change.
2. **Single-app shell** — create the standalone `restful-api` Maven project,
   common error/security/health/observability contracts, configuration profiles
   and test harness. Do not claim domain parity yet.
3. **Low-risk domain waves** — port thesis and engagement, then notification
   REST read paths, with compatibility aliases and differential fixtures.
4. **Academic and people** — port academic catalog, people, enrollment and
   grades; reconcile IDs, pagination, filtering, role checks and writes.
5. **Analytics and finance** — port read models first; finance/payment writes
   only after a separate security and data reconciliation review.
6. **Auth last** — preserve cookie/CSRF/JWT compatibility, then switch identity
   ownership only after session, refresh, revocation and negative tests pass.
7. **Client convergence** — point Next.js and the new Expo client to the one API;
   keep the Stitch routes and add mobile smoke coverage for shared contracts.
8. **Canary, rollback and retirement** — freeze exact candidate, run Advisor,
   Kongming and Wukong again, canary by route, observe a rollback window, then
   retire old services only with explicit evidence.

## Phase ledger

- Phase 01 architecture contract: complete at `426e024`.
- Phase 02 single-app shell: complete at `e086c04`; see
  `phase-02-single-app-shell.md`.
- Phase 03 persistence/migration seam: bounded candidate committed at `0064df8`;
  see `phase-03-persistence-thesis-read.md`. It has no public route or writer
  handoff yet, and PostgreSQL/rollback/review gates remain open.
- Phase 04 Stitch web/mobile client track: deferred by the backend-first
  sequencing decision; see `phase-04-stitch-web-mobile.md` and
  `reports/fe-stitch-audit.md`. Its existing source evidence is preserved, but
  no additional web/mobile implementation is authorized until the backend
  foundation gate below has passed.
- Phase 05 notification read contract: source-level candidate/HOLD; see
  `phase-05-notification-read-contract.md` and
  `reports/notification-read-candidate.md`. The feature-flagged Java read
  adapter exists, but no public route or writer ownership has moved.
- Phase 06 thesis registration-round read: source candidate; see
  `phase-06-thesis-round-read.md`. Compile passed, while Spring test execution
  is `BLOCKED_CAPABILITY` by the current Windows commit-memory limit; no route
  or writer ownership has moved.
- Phase 07 thesis group read: source candidate; see
  `phase-07-thesis-group-read.md`. It is JDBC-only and compile-verified; the
  same host resource limit blocks Spring persistence verification.
- Phase 08 thesis council read: source candidate through `24f5e8b`; JDBC-only,
  feature-gated list reads and bounded H2/Spring contract tests passed on the
  exact source checkpoint. Legacy writer/public traffic ownership, PostgreSQL
  parity, canary and rollback are still open.
- Phase 09 PostgreSQL differential rehearsal: planned and explicitly blocked
  on an isolated disposable restore; see
  `phase-09-postgres-differential-rehearsal.md`. The active shared CampusCore
  PostgreSQL container is not an authorized rehearsal target.
- Phase 10 engagement announcement read: source candidate/HOLD; see
  `phase-10-engagement-announcement-read.md`. The feature-default-off Java
  adapter preserves the current Nest list and personalized-read contract using
  JDBC `SELECT` only. The repaired 45-test monolith H2/Spring suite passed
  locally, but PostgreSQL parity, route canary, writer ownership and rollback
  remain open.
- Phase 11 engagement PostgreSQL differential rehearsal: planned/HOLD; see
  `phase-11-engagement-postgres-differential-rehearsal.md`. It adds the physical
  Prisma schema/type, audience-array, signed-auth, error-divergence and private
  rollback corpus missing from the thesis-only Phase 09 rehearsal.
- Phase 12 canonical monolith build boundary: in progress; see
  `phase-12-backend-first-monolith-boundary.md`. It makes the Java reactor name
  the one supported modular-monolith build target while preserving legacy Java
  services as direct-build rollback references. It makes no route, writer,
  database, image, or client change.
- Phase 13 backend-first auth and academic read foundation:
  source candidate/HOLD; see `phase-13-auth-academic-foundation.md`. It adds
  Java access/refresh token issuing, a feature-default-off Java auth session
  candidate for login, current-user read, profile update, password change,
  refresh rotation and logout clearing against the migrated legacy `auth`
  schema, and feature-default-off academic semester/course reads through the
  legacy academic schema. Legacy auth/academic services remain public route
  owners and canonical writers; registration, forgot/reset password, email
  verification, audit publishing, PostgreSQL parity, route canary, rollback and
  frontend convergence remain open.
- Phase 14 backend-first people read foundation:
  source candidate/HOLD; see `phase-14-people-read-foundation.md`. It adds
  feature-default-off Java student and lecturer list/detail reads against the
  migrated legacy `people` schema, preserving list metadata and nested
  user/academic snapshots. People writes, enrollment history, events,
  PostgreSQL parity, route canary, rollback and frontend convergence remain
  open.
- Phase 15 backend-first finance read foundation:
  source/PostgreSQL-syntax verified candidate/HOLD; see
  `phase-15-finance-read-foundation.md`. It adds
  feature-default-off Java invoice and payment read paths against the migrated
  legacy `finance` schema, preserving admin list/detail envelopes, student
  self-invoice isolation, invoice item/payment hydration and payment invoice
  joins. Phase 58 repaired the nullable optional filters for PostgreSQL syntax.
  Finance writes, checkout/payment provider orchestration, exports, restored
  legacy-data parity, route canary, rollback and frontend convergence remain
  open.
- Phase 16 backend-first analytics read foundation:
  source candidate/HOLD; see `phase-16-analytics-read-foundation.md`. It adds
  feature-default-off Java analytics overview and finance-summary reads against
  the analytics service's legacy Prisma `public` schema, preserving selected
  dashboard aggregate shapes and role boundaries. Lecturer analytics,
  metrics/events, PostgreSQL parity, route canary, rollback and frontend
  convergence were still open at that phase; later analytics waves below add
  more source candidates without changing the public cutover HOLD.
- Phase 17 backend-first academic enrollment read foundation:
  source candidate/HOLD; see `phase-17-academic-enrollment-read-foundation.md`.
  It adds feature-default-off Java current-student enrollment, grade and
  transcript reads, admin enrollment list/detail reads, and selected lecturer/
  admin grade item and student-grade reads against the migrated legacy
  `academic` schema. It preserves the selected enrollment envelope, nested
  section/course/semester/lecturer hydration, student self-scope boundary,
  weighted grade totals and transcript GPA summary. Enroll/drop, waitlist,
  grade writes/publishing, timetable, export, events, PostgreSQL parity, route
  canary, rollback and frontend convergence remain open.
- Phase 18 backend-first thesis assistant/chatbotAI contract foundation:
  source candidate/HOLD; see `phase-18-thesis-assistant-foundation.md`. It adds
  a feature-default-off Java `/api/v1/thesis/assistant/chat` endpoint with the
  response shape already expected by the web and mobile clients. The current
  implementation is a deterministic local fallback (`degraded=true`) only; it
  does not call an LLM provider, persist prompts, use a vector store, expose
  tools, or move public chatbot traffic. Provider mode, prompt governance,
  moderation, telemetry, rate limiting, PostgreSQL parity, route canary,
  rollback and frontend convergence remain open.
- Phase 19 backend-first engagement support-ticket read foundation:
  source candidate/HOLD; see `phase-19-engagement-support-ticket-read.md`. It
  adds feature-default-off Java support-ticket list/detail reads for the current
  user and admins against the migrated legacy `engagement` schema. It preserves
  the selected support-ticket envelope, user isolation, admin filters, response
  hydration and nested user display fallback. Ticket creation, assignment,
  response mutation, status updates, deletion, PostgreSQL parity, route canary,
  rollback and frontend convergence remain open.
- Phase 20 backend-first engagement support-ticket create foundation:
  source candidate/HOLD; see `phase-20-engagement-support-ticket-create.md`.
  It adds feature-default-off Java `POST /api/v1/support-tickets` creation with
  legacy-shaped `TKT-xxxxx` numbering from the maximum existing suffix plus a
  bounded unique-collision retry, ownership from JWT subject/email, `OPEN`
  status, `MEDIUM` default priority and the shared ticket response shape.
  Assignment, responses, status updates, deletion, event/notification parity,
  PostgreSQL write rehearsal, true contention/idempotency proof, route canary,
  rollback and public writer handoff remain open.
- Phase 21 backend-first engagement support-ticket numbering guard:
  source candidate/HOLD; see
  `phase-21-engagement-support-ticket-numbering-guard.md`. It hardens the
  support-ticket create candidate to allocate from the maximum existing
  `TKT-xxxxx` suffix and retry bounded unique collisions, replacing raw
  row-count allocation before any PostgreSQL rehearsal or writer handoff.
  PostgreSQL duplicate-key translation, true contention/idempotency proof,
  route canary, rollback and independent exact-head review remain open.
- Phase 22 backend-first engagement support-ticket response foundation:
  source candidate/HOLD; see `phase-22-engagement-support-ticket-response.md`.
  It adds feature-default-off Java `POST /api/v1/support-tickets/{id}/respond`
  for `ADMIN`/`SUPER_ADMIN`, preserving the legacy response shape, current admin
  identity mapping, `isInternal=false` default and `OPEN` to `IN_PROGRESS`
  transition. Assignment, status edits beyond this transition, deletion,
  event/notification parity, PostgreSQL write rehearsal, canary routing,
  rollback and public responder handoff remain open.
- Phase 23 backend-first engagement support-ticket update foundation:
  source candidate/HOLD; see `phase-23-engagement-support-ticket-update.md`.
  It adds feature-default-off Java `PUT /api/v1/support-tickets/{id}` for
  `ADMIN`/`SUPER_ADMIN`, preserving partial subject/description/category/
  priority/status edits plus the legacy `RESOLVED` and `CLOSED` timestamp
  side effects. Assignment, delete, event/notification parity, PostgreSQL write
  rehearsal, canary routing, rollback and public update handoff remain open.
- Phase 24 backend-first engagement support-ticket assignment foundation:
  source candidate/HOLD; see `phase-24-engagement-support-ticket-assign.md`.
  It adds feature-default-off Java `POST /api/v1/support-tickets/{id}/assign`
  for `ADMIN`/`SUPER_ADMIN`, preserving the legacy `assignedTo`-only update and
  hydrated ticket response. Delete, event/notification parity, PostgreSQL write
  rehearsal, canary routing, rollback and public assignment handoff remain open.
- Phase 25 backend-first engagement support-ticket deletion foundation:
  source candidate/HOLD; see `phase-25-engagement-support-ticket-delete.md`.
  It adds feature-default-off Java `DELETE /api/v1/support-tickets/{id}` for
  `ADMIN`/`SUPER_ADMIN`, preserving the legacy success message and relying on
  the Prisma-owned response cascade relationship. Event/notification parity,
  PostgreSQL write rehearsal, canary routing, rollback and public deletion
  handoff remain open.
- Phase 26 backend-first engagement announcement create foundation:
  source candidate/HOLD; see `phase-26-engagement-announcement-create.md`.
  It adds feature-default-off Java `POST /api/v1/announcements` for
  `ADMIN`/`SUPER_ADMIN`, preserving the legacy creation defaults, JWT-subject
  publisher, target arrays, optional publish/expiry windows and response shape.
  RabbitMQ announcement-created publication, PostgreSQL write rehearsal, canary
  routing, rollback and public writer handoff remain open.
- Phase 27 backend-first engagement announcement update foundation:
  source candidate/HOLD; see `phase-27-engagement-announcement-update.md`.
  It adds feature-default-off Java `PUT /api/v1/announcements/{id}` for
  `ADMIN`/`SUPER_ADMIN`, preserving partial legacy field updates, unchanged
  publisher/created timestamp, updated timestamp behavior and the shared
  announcement response shape. Delete, RabbitMQ/event parity, PostgreSQL write
  rehearsal, canary routing, rollback and public writer handoff remain open.
- Phase 28 engagement contract hardening:
  source candidate/HOLD; see `phase-28-engagement-contract-hardening.md`.
  It tightens Java engagement request parsing and read visibility after exact
  review found that some JSON bodies could be coerced or ignored too loosely.
  Java now rejects unknown write-body properties, preserves omitted-versus-null
  announcement update semantics, and hides internal support-ticket responses
  from user self-service reads while leaving admin visibility intact. Legacy
  route ownership, PostgreSQL parity, RabbitMQ/event parity, canary routing and
  rollback remain open.
- Phase 29 backend-first engagement announcement deletion foundation:
  source/H2 verified candidate/HOLD; see
  `phase-29-engagement-announcement-delete.md`.
  It adds feature-default-off Java `DELETE /api/v1/announcements/{id}` for
  `ADMIN`/`SUPER_ADMIN`, preserving the legacy success message and pre-delete
  not-found behavior. RabbitMQ/event parity, PostgreSQL write rehearsal, canary
  routing, rollback and public announcement writer handoff remain open.
- Phase 30 backend-first notification self-service write foundation:
  source/H2 verified candidate/HOLD; see
  `phase-30-notification-self-service-write.md`.
  It adds feature-default-off Java
  `PATCH /api/v1/notifications/my/{id}/read`,
  `PATCH /api/v1/notifications/my/read-all` and
  `DELETE /api/v1/notifications/my/{id}` for the authenticated user,
  preserving legacy response envelopes and subject ownership isolation. Realtime
  delivery, admin notification writes, PostgreSQL write rehearsal, canary
  routing, rollback and public notification writer handoff remain open.
- Phase 31 backend-first notification admin read foundation:
  source/H2 verified candidate/HOLD; see
  `phase-31-notification-admin-read.md`.
  It extends the feature-default-off Java notification read candidate with
  `GET /api/v1/notifications` and `GET /api/v1/notifications/{id}` for
  `ADMIN`/`SUPER_ADMIN`, preserving the legacy list envelope, created-desc
  ordering, optional `userId` filter and not-found detail behavior. PostgreSQL
  read parity, realtime/admin write parity, canary routing, rollback and public
  notification route handoff remain open.
- Phase 32 backend-first notification admin deletion foundation:
  source/H2 verified candidate/HOLD; see
  `phase-32-notification-admin-delete.md`.
  It adds feature-default-off Java `DELETE /api/v1/notifications/{id}` for
  `ADMIN`/`SUPER_ADMIN`, preserving the legacy success message and pre-delete
  not-found behavior. PostgreSQL write parity, realtime/admin create-update
  parity, canary routing, rollback and public notification writer handoff remain
  open.
- Phase 33 backend-first notification admin update foundation:
  source/H2 verified candidate/HOLD; see
  `phase-33-notification-admin-update.md`.
  It adds feature-default-off Java `PUT /api/v1/notifications/{id}` for
  `ADMIN`/`SUPER_ADMIN`, preserving partial legacy field edits, enum validation,
  optional `link` clearing, detail response shape and not-found behavior.
  PostgreSQL write parity, realtime/admin create parity, canary routing,
  rollback and public notification writer handoff remain open.
- Phase 34 backend-first notification admin create foundation:
  source/H2 verified candidate/HOLD; see
  `phase-34-notification-admin-create.md`.
  It adds feature-default-off Java `POST /api/v1/notifications` for
  `ADMIN`/`SUPER_ADMIN`, preserving the legacy create fields, `201 Created`,
  generated id, unread default, null `readAt`, detail response shape and
  admin-only authorization. PostgreSQL write parity, realtime/event parity,
  canary routing, rollback and public notification writer handoff remain open.
- Phase 35 backend-first analytics notification summary foundation:
  source/H2 verified candidate/HOLD; see
  `phase-35-analytics-notification-summary.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/notification-summary` for `ADMIN`/`SUPER_ADMIN`,
  preserving total/unread/read aggregates, type buckets and recent
  warning/error attention rows from the analytics service's legacy Prisma
  `public.Notification` table. PostgreSQL read parity, cockpit composition,
  canary routing, rollback and public analytics route handoff remain open.
- Phase 36 backend-first analytics grade distribution foundation:
  source/H2 verified candidate/HOLD; see
  `phase-36-analytics-grade-distribution.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/grade-distribution` for `ADMIN`/`SUPER_ADMIN`,
  preserving the legacy fixed `A` through `F` grade bucket order, count and
  percentage semantics for completed enrollments with non-null letter grades.
  PostgreSQL read parity, cockpit composition, canary routing, rollback and
  public analytics route handoff remain open.
- Phase 37 backend-first analytics enrollments-by-semester foundation:
  source/H2 verified candidate/HOLD; see
  `phase-37-analytics-enrollments-by-semester.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/enrollments-by-semester` for `ADMIN`/`SUPER_ADMIN`,
  preserving the legacy `CONFIRMED`/`COMPLETED` enrollment filter, semester
  start-date descending order, limit 10, semester names and academic-year
  response shape from the analytics service's legacy Prisma schema.
  PostgreSQL read parity, cockpit composition, canary routing, rollback and
  public analytics route handoff remain open.
- Phase 38 backend-first analytics section occupancy foundation:
  source/H2 verified candidate/HOLD; see
  `phase-38-analytics-section-occupancy.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/section-occupancy` for `ADMIN`/`SUPER_ADMIN`,
  preserving the legacy section/course/semester response shape,
  `CONFIRMED`/`PENDING` occupancy count, `Section.enrolledCount` fallback when
  no counted enrollments exist, `Section.enrolledCount` descending order, limit
  20 and rounded occupancy percentage. PostgreSQL read parity, cockpit
  composition, canary routing, rollback and public analytics route handoff
  remain open.
- Phase 39 backend-first analytics top courses foundation:
  source/H2 verified candidate/HOLD; see
  `phase-39-analytics-top-courses.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/top-courses` for `ADMIN`/`SUPER_ADMIN`, preserving
  the legacy course response shape, section count, `CONFIRMED`/`PENDING`
  enrollment totals, descending total-enrollment sort and default/custom limit
  behavior. PostgreSQL read parity, cockpit composition, canary routing,
  rollback and public analytics route handoff remain open.
- Phase 40 backend-first analytics student statistics foundation:
  source/H2 verified candidate/HOLD; see
  `phase-40-analytics-student-statistics.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/student-statistics` for `ADMIN`/`SUPER_ADMIN`,
  preserving the legacy total, active, graduated, suspended and by-year
  student aggregate response shape. PostgreSQL read parity, cockpit
  composition, canary routing, rollback and public analytics route handoff
  remain open.
- Phase 41 backend-first analytics registration pressure foundation:
  source/H2 verified candidate/HOLD; see
  `phase-41-analytics-registration-pressure.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/registration-pressure` for `ADMIN`/`SUPER_ADMIN`,
  preserving the legacy active registration semester count, capacity pressure
  thresholds, ACTIVE waitlist total, rounded average occupancy, highest-pressure
  ordering and waitlist status bucket shape. PostgreSQL read parity, cockpit
  composition, canary routing, rollback and public analytics route handoff
  remain open.
- Phase 42 backend-first analytics enrollment trends foundation:
  source/H2 verified candidate/HOLD; see
  `phase-42-analytics-enrollment-trends.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/enrollment-trends` for `ADMIN`/`SUPER_ADMIN`,
  preserving the legacy default and clamped `months` behavior, UTC monthly
  bucket ordering, EN/VI labels, enrolled/dropped/completed counts, net and
  total activity shape. PostgreSQL read parity, cockpit composition, canary
  routing, rollback and public analytics route handoff remain open.
- Phase 43 backend-first analytics cockpit and operator-summary foundation:
  source candidate/HOLD; see
  `phase-43-analytics-cockpit-operator-summary.md`.
  It extends the feature-default-off Java analytics read candidate with
  `GET /api/v1/analytics/operator-summary` and
  `GET /api/v1/analytics/cockpit` for `ADMIN`/`SUPER_ADMIN`, preserving the
  legacy local-operator dashboard-link posture and cockpit composition shape
  over the already-ported analytics read aggregates. PostgreSQL read parity,
  live cockpit UI wiring, canary routing, rollback and public analytics route
  handoff remain open.
- Phase 44 backend-first analytics revenue foundation: source candidate/HOLD;
  see `phase-44-analytics-revenue.md`. It extends the feature-default-off Java
  analytics read candidate with `GET /api/v1/analytics/revenue` for
  `ADMIN`/`SUPER_ADMIN`/`FINANCE_OFFICER`, preserving the legacy optional
  `semesterId` filter, invoice totals/counts and completed-payment revenue
  calculation. PostgreSQL read parity, live finance/admin route wiring, canary
  routing, rollback and public analytics route handoff remain open.
- Phase 45 backend-first analytics attendance foundation: source candidate/HOLD;
  see `phase-45-analytics-attendance.md`. It extends the feature-default-off
  Java analytics read candidate with `GET /api/v1/analytics/attendance` for
  `ADMIN`/`SUPER_ADMIN`, preserving the legacy optional `semesterId` section
  filter, status totals and rounded attendance-rate formula. PostgreSQL read
  parity, live admin route wiring, canary routing, rollback and public analytics
  route handoff remain open.
- Phase 46 backend-first analytics lecturer foundation: source candidate/HOLD;
  see `phase-46-analytics-lecturer.md`. It extends the feature-default-off Java
  analytics read candidate with `GET /api/v1/analytics/lecturer/my` and
  `GET /api/v1/analytics/lecturer/sections` for `LECTURER`, preserving the
  legacy `lecturerId` claim scope, confirmed/pending student counts, published
  grade counts, bilingual course/semester section buckets and occupancy
  formula. PostgreSQL read parity, live lecturer route wiring, canary routing,
  rollback and public analytics route handoff remain open.
- Phase 47 backend-first academic schedule read foundation: source
  candidate/HOLD; see `phase-47-academic-schedule-read.md`. It adds
  feature-default-off Java `GET /api/v1/schedules` and
  `GET /api/v1/schedules/{id}` reads against the migrated legacy `academic`
  schema, preserving the selected list envelope, day/start ordering, direct
  section/classroom hydration and migration-safety default-off boundary.
  Schedule writes, lecturer timetable shortcuts, PostgreSQL read parity, live
  timetable wiring, canary routing, rollback and public academic route handoff
  remain open.
- Phase 48 backend-first academic waitlist read foundation: source/H2
  verified candidate/HOLD; see `phase-48-academic-waitlist-read.md`. It adds
  feature-default-off Java `GET /api/v1/waitlist`,
  `GET /api/v1/waitlist/my`, `GET /api/v1/waitlist/section/{sectionId}` and
  `GET /api/v1/waitlist/{id}` reads against the migrated legacy `academic`
  schema, preserving the selected list envelope, current-student active
  waitlist scope, section active filter, position/added-at ordering, role
  boundaries and migration-safety default-off boundary. Focused tests and the
  full Java reactor passed on H2 with 23 reports / 156 tests / 0 failures /
  0 errors / 0 skipped. Waitlist promotion, waitlist removal, enroll/drop
  mutations, PostgreSQL read parity, live registration route wiring, canary
  routing, rollback and public academic route handoff remain open.
- Phase 49 backend-first academic attendance read foundation: source/focused
  H2 verified candidate/HOLD; see `phase-49-academic-attendance-read.md`. It
  adds feature-default-off Java `GET /api/v1/attendance`,
  `GET /api/v1/attendance/my`, `GET /api/v1/attendance/my/summary`,
  `GET /api/v1/attendance/lecturer/my`,
  `GET /api/v1/attendance/section/{sectionId}`,
  `GET /api/v1/attendance/section/{sectionId}/summary` and
  `GET /api/v1/attendance/{id}` reads against the migrated legacy `academic`
  schema, preserving the selected list envelope, student self filters,
  student/section summary formulas, lecturer-owned section scope, date filters,
  role/claim/query failures and migration-safety default-off boundary.
  Attendance mark/update/delete mutations, PostgreSQL read parity, live
  attendance route wiring, canary routing, rollback and public academic route
  handoff remain open.
- Phase 50 backend-first academic section read foundation: source/H2 verified
  candidate/HOLD; see `phase-50-academic-section-read.md`. It adds
  feature-default-off Java `GET /api/v1/sections`,
  `GET /api/v1/sections/my/schedule`, `GET /api/v1/sections/my/grading`,
  `GET /api/v1/sections/{id}` and `GET /api/v1/sections/{id}/grades` reads
  against the migrated legacy `academic` schema, preserving selected section
  list/detail hydration, lecturer timetable shortcut shape, lecturer grading
  counts, section grade enrollment rows, role/claim/query failures and the
  migration-safety default-off boundary. Section create/update/delete,
  grade editing/publishing, PostgreSQL read parity, live timetable/grading route
  wiring, canary routing, rollback and public academic route handoff remain
  open.
- Phase 51 backend-first academic catalog read extension: source/H2 verified
  candidate/HOLD; see `phase-51-academic-catalog-read.md`. It extends the
  existing feature-default-off Java academic catalog candidate with
  `GET /api/v1/academic-years`, `GET /api/v1/academic-years/{id}`,
  `GET /api/v1/classrooms` and `GET /api/v1/classrooms/{id}` reads against the
  migrated legacy `academic` schema, preserving selected list/detail envelopes,
  academic-year semester hydration, classroom detail section summaries,
  query/page/default-off failures and auth boundary behavior. Academic-year and
  classroom writes, classroom equipment-array parity, PostgreSQL read parity,
  live admin/catalog route wiring, canary routing, rollback and public academic
  route handoff remain open.
- Phase 52 backend-first academic faculty/department read extension:
  source/H2 verified candidate/HOLD; see
  `phase-52-academic-faculty-department-read.md`. It extends the existing
  feature-default-off Java academic catalog candidate with
  `GET /api/v1/faculties`, `GET /api/v1/faculties/{id}`,
  `GET /api/v1/departments` and `GET /api/v1/departments/{id}` reads against
  the migrated legacy `academic` schema, preserving selected admin-only list
  boundaries, authenticated detail reads, list envelopes, faculty department
  hydration, department faculty/lecturer hydration, localization defaults,
  default-off route absence and negative authorization/query behavior.
  Faculty/department writes, curricula parity, PostgreSQL read parity, live
  admin/catalog route wiring, canary routing, rollback and public academic
  route handoff remain open.
- Phase 53 backend-first academic curriculum read extension: source/H2
  verified candidate/HOLD; see `phase-53-academic-curriculum-read.md`. It
  extends the existing feature-default-off Java academic catalog candidate with
  `GET /api/v1/curricula` and `GET /api/v1/curricula/{id}` reads against the
  migrated legacy `academic` schema, preserving selected authenticated list and
  detail boundaries, list envelopes, department hydration, detail-only
  `CurriculumCourse` mapping hydration, localization defaults, default-off
  route absence and negative query behavior. A focused PostgreSQL rehearsal on
  the curriculum read suite has since passed against a disposable target, but
  curriculum writes, student/curriculum writer ownership, richer course-object
  joins, restored PostgreSQL read parity, live admin/catalog route wiring,
  canary routing, rollback and public academic route handoff remain open.
- Phase 54 backend-first academic admin student enrollment read extension:
  source/H2 focused verified candidate/HOLD; see
  `phase-54-academic-student-enrollment-read.md`. It extends the existing
  feature-default-off Java academic enrollment read candidate with
  `GET /api/v1/enrollments/student/{studentId}`, preserving the selected
  legacy admin/super-admin role boundary, list response shape, optional
  `semesterId` filter, `enrolledAt DESC` ordering, default-off route absence
  and negative authorization/query behavior. A focused PostgreSQL rehearsal on
  the admin student-enrollment shortcut has since passed against a disposable
  target, but enrollment create/drop/update/delete ownership, CSV export
  parity, restored PostgreSQL read parity, live route wiring, canary routing,
  rollback and public academic route handoff remain open.
- Phase 55 academic internal-context bridge: complete at
  `d92ce53e884adcd83b5dd479aebeb584d9a83946`; see
  `phase-55-academic-internal-context-bridge.md`. It adds internal-only
  academic context lookups for curricula, departments and student enrollments
  behind `X-Service-Token`, keeps the public academic route surface unchanged
  and does not move public route ownership or Java cutover.
- Phase 56 academic enrollment PostgreSQL parity rehearsal: source/parity
  verified at `3dbfd19f841dc3e9e98a161c9d03161974c2bce6`; see
  `phase-56-academic-enrollment-postgres-parity.md`. It exercises the existing
  academic enrollment/grade read foundation against a disposable PostgreSQL
  target on the exact HEAD carrying the academic internal-context bridge and
  records parity evidence without widening route ownership.
- Phase 57 people read PostgreSQL parity repair: source repair committed at
  `feb6213e20fb14c67f2345007ed9485c0571777d`; see
  `phase-57-people-postgres-parity-repair.md`. It fixes the people student
  status filter so the existing read-only people slice works on PostgreSQL when
  the optional `status` filter is absent, without adding routes, writes or
  route ownership.
- Phase 58 finance read PostgreSQL parity repair: source repair committed at
  `3327f8318bc9aa775a4e48185b8f74b54ab3215a`; see
  `phase-58-finance-postgres-parity-repair.md`. It fixes finance invoice and
  payment optional-filter reads so the existing feature-default-off read slice
  works on PostgreSQL when filters are absent, without adding routes, writes,
  checkout ownership or public traffic handoff.
- Phase 59 finance envelope differential preparation: source repair committed at
  `b567a02f8c152b909470c96ea638a129c4f23c1c`; see
  `phase-59-finance-envelope-differential.md`. It tightens the Java finance
  student invoice and payment invoice envelope for safer legacy comparison and
  adds a self-testable finance differential rehearsal harness. Live restored-
  PostgreSQL rehearsal now passes with one documented legacy schema limitation
  on the Node status-filter route, so it is still not restored legacy-data
  parity, route canary, rollback, checkout ownership or public traffic handoff
  evidence.
- Phase 60 finance edge route-switch rollback rehearsal: source/live-proxy
  candidate/HOLD; see `phase-60-finance-edge-route-switch-rollback.md`. It
  runs the finance read corpus through a local proxy that switches legacy →
  Java → legacy under one stable client URL, using the same disposable
  PostgreSQL restore and the same documented restored-legacy payment-status
  limitation. This adds proxy rollback evidence, but it still does not grant
  public finance route ownership, checkout ownership, or FE/mobile wiring.
- Phase 61 auth login PostgreSQL focused rehearsal: source/PostgreSQL focused
  verified candidate/HOLD; see
  `phase-61-auth-login-postgres-focused-rehearsal.md`. It exercises the
  feature-gated auth login/session persistence test suite against a disposable
  PostgreSQL target on exact head, validating login, refresh, profile,
  password-change and logout behavior on real PostgreSQL syntax/types. Legacy
  auth-service remains the public route owner; route canary, rollback and
  public auth handoff remain open.

## Backend foundation gate before Stitch implementation

The next Stitch web/mobile implementation wave is blocked until all of the
following are observed on one exact source HEAD:

1. the canonical Java build executes only `java-services/restful-api` as the
   modular-monolith target;
2. the security/error/correlation shell and its negative tests remain green;
3. one low-risk, read-only domain is exercised against an approved disposable
   PostgreSQL restore with a read-only role, Flyway/DDL disabled, and a recorded
   legacy-to-Java-to-legacy rollback rehearsal;
4. fresh exact-head Advisor, Kongming, reviewer, and bounded Wukong gates have
   no unresolved high/critical counterexample for that backend scope.

Until then, the existing 22+ web/mobile screen material is design/source
evidence only. It is not a reason to expand the client or point either client
at an incomplete Java API.

## Continuation evidence — 2026-08-20, `ba90cf6`

The current exact checkout remains on
`ba90cf61e89ac48110a7be680b443513909dd771` and the branch is still ahead of
`origin/feature/java-thesis-platform` by 16 commits. The working tree has only
preserved untracked AgentKit/Codex configuration files plus
`.agents/skills/ak-use-mcp/scripts/package-lock.json`; none of those were used
as implementation evidence.

Observed low-disk-safe gates:

- `npm test --prefix mobile`: PASS, 6/6 dependency-free screen-atlas tests. This
  re-confirms the native registry above the 20-screen requirement, Stitch token
  anchors, preview/live API separation, role navigation, non-impersonating
  preview sign-in, explicit live Java auth handoff, assistant locale payload
  parity, the mobile assistant screen's live-only Java route handoff, and
  in-memory access/refresh token handling plus one-shot refresh retry for the
  Java mobile auth contract. It is not Expo typecheck, emulator, device, secure
  storage, or live API proof.
- `npm test --prefix frontend`: PASS, 29/29 frontend smoke tests. This is source
  and structural coverage only; the added FE Stitch guard proves the visual QA
  harness now covers the ten audit-missing route families, 768px tablet width,
  discovered detail routes, and console/network failure checks. It is not a
  fresh authenticated browser matrix, Stitch pixel/reference diff, or production
  build evidence.
- `mvn -q -f java-services/restful-api/pom.xml test`: PASS with Maven 3.9.12
  and Java 24.0.2. Surefire recorded 45/45 tests passing across engagement,
  notification, thesis, security, migration safety, CSRF and REST contract
  tests. This updates the older resource-limit note for the current host, but it
  still does not prove PostgreSQL parity, route canary, runtime smoke, rollback,
  or public cutover.

Disk observation during the same continuation: C: had about 3.40 GB free and D:
about 39.38 GB free. The exact stale rehearsal snapshot targets under
`<recovery-worktree>\phase11-engagement-a854f90` were verified, but
the environment rejected the bounded `Remove-Item` cleanup, so cleanup remains
`BLOCKED_CAPABILITY` rather than silently worked around.

# Acceptance and verification

Every phase records command, result, environment and limitation. The minimum
gate set is:

- `git diff --check` and targeted static/secret checks;
- `mvn -q -f java-services/restful-api/pom.xml test` plus integration tests;
- `node scripts/check-thesis-contract.mjs` while thesis compatibility remains;
- frontend smoke, typecheck, lint and authenticated E2E;
- Expo typecheck/unit tests and an emulator/device smoke for the agreed mobile
  flows;
- `docker compose -f <minimal-profile> config` and current-source container
  health/readiness/metrics smoke;
- auth/CSRF/JWT negative tests, migration checksum, backup/restore and row-count
  or hash reconciliation;
- chatbot timeout, provider failure, rate-limit, redaction and mock-mode tests;
- exact-head Advisor/Kongming review and a Wukong falsifiable claim with a
  deterministic rollback test.

`PASS`, `FAIL`, `BLOCKED_CAPABILITY` and `NOT_RUN` must remain distinct. A local
commit is not a push, and a published image is not deployment evidence.

# Risks, rollback and recovery

- **Schema drift:** keep per-domain logical schemas in the first Flyway wave;
  one writer per domain; restore into a disposable database before cutover.
- **Auth regression:** keep the legacy auth owner until cookie, CSRF, refresh,
  revocation and role claims pass; route back through nginx on failure.
- **Realtime regression:** target REST notification polling for the simple course
  profile; retain legacy Socket.IO until the client no longer depends on it.
- **File/payment regression:** use an explicit storage/payment adapter; keep
  MinIO/payment providers optional and preserve old endpoints during migration.
- **AI/provider failure:** mock fallback and bounded timeout; never allow the
  model to query the database directly.
- **Disk pressure:** do not build heavyweight images or delete active caches/
  diagnostics without a fresh bounded process/link check and a recoverable plan.

Rollback means restoring the last known route owner and canonical writer, not
merely restarting the new Java process. The plan cannot become `completed`
until rollback has been exercised and evidence is stored under this plan.

# Documentation and handoff

- Canonical architecture decision: `phase-01-architecture-contract.md`.
- Single-app implementation evidence: `phase-02-single-app-shell.md`.
- Persistence/read-path evidence: `phase-03-persistence-thesis-read.md`.
- Stitch web/mobile evidence: `phase-04-stitch-web-mobile.md` and
  `reports/fe-stitch-audit.md`.
- Notification read-wave contract: `phase-05-notification-read-contract.md`.
- Independent review record: `reports/review-gate.md`.
- Current topology remains documented in `docs/ARCHITECTURE.md`; this plan is a
  planned target, not a claim that cutover already happened.
- Each implementation wave must add a phase report and update the exact commit,
  tests, open risks and next safe action.

# Unresolved decisions

1. Whether the first single-app database keeps domain schemas permanently or
   flattens them after the course-scope migration; default is keep schemas first.
2. Whether mobile first release uses bearer/refresh only or also supports a
   carefully scoped cookie mode; default is bearer/refresh with secure storage.
3. Which LLM provider is used in demo mode; default is deterministic mock mode,
   with provider mode opt-in through server environment variables.
4. Whether legacy notification Socket.IO is needed after REST polling parity;
   default is to remove it only after client and rollback evidence.

Until these decisions are resolved with evidence, the public migration gate is
`HOLD` and the legacy topology remains preserved.
