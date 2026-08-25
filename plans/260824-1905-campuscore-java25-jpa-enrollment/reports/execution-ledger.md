# Execution ledger

Active plan: plans/260824-1905-campuscore-java25-jpa-enrollment/plan.md

## Start snapshot

- Started: 2026-08-24 (Asia/Bangkok), root integration owner.
- Base: `main` = `origin/main` = `38c7447974f93553596d30e4cbb15d5ce626fa28`.
- Dirty boundary: 34 tracked modifications, 34 untracked paths, no staged
  entries at the Phase 0 recheck; tracked diff identity
  `b93a0aabbe56fb1124c8ce43c77a6cfde063eef0fb88858598c2c65a02a82f34`;
  untracked manifest identity
  `c483c1ae06ad0d55578b7e2fb6c06ae430880bddf12a27f83c4d0ba6016483d8`.
- Host evidence: Java 26 executable, Maven 3.9.12 on JDK 24; Java 25 is not
  installed/proven locally and therefore is a future capability gate.

## Workflow and ownership

`ak:goal-warmup` and `ak:plan-lock` were read and applied after the user
approved the complete successor plan. Phase 0 and the integration-owned
toolchain wave are now complete; Phase 2 is the active wave.
Integration owns shared contract, plan state, candidate freeze, staging,
commits and push. DB/JPA, backend/API, Stitch FE, mobile and QA/security
writers receive disjoint worktrees in later waves. Kongming, Wukong, FE
reviewer, tester, debugger and code-reviewer are read-only lanes.

## Phase 0 record

- [x] README, root AGENTS.md and `.agentkit/config.yaml` inspected.
- [x] Successor directory created at the requested path; generated placeholder
  replaced with the accepted outcome contract and phase files.
- [x] Reindex and activate the renamed plan path (`Student_Management_UTE/260824-1218-8`).
- [x] Mark predecessor plan records cancelled/superseded without rewriting
  historical reports.
- [x] Write snapshot/classification reports and create isolated feature
  worktree from the exact base.
- [x] Freeze candidate identity and obtain read-only Phase 0 review (`Kongming
  PASS`, `Wukong NOT_FALSIFIED`).

## Candidate freeze tuple (Phase 0 exact-head review)

- Worktree: `D:\worktrees\Student_Management-feature-campuscore-java25-jpa-enrollment`.
- Branch: `feature/campuscore-java25-jpa-enrollment`.
- `HEAD`: `38c7447974f93553596d30e4cbb15d5ce626fa28`.
- Tracked diff hash: `d1cd51a9b05c8d58f006d3f466edce9973091a19`.
- Untracked manifest hash: `7ae56ceac68ee85c6d6a9ce792ef241c03a599cd` (34 files).
- Candidate status is 34 tracked modifications + 34 untracked files + zero
  staged entries (68 porcelain-v2 lines). The temporary nested-plan duplicate
  is outside the worktree at `D:\worktrees\phase0-quarantine\...` and is not
  included.
- Candidate plan/ledger/reports are available at
  `plans/260824-1905-campuscore-java25-jpa-enrollment/` and resolve through the
  plan store. An ignored `.env` is present; its content/provenance is
  unverified, unread, unstaged and protected from commits (`NOT_PROVEN`).
- Kongming `PASS` and Wukong `NOT_FALSIFIED` were recorded against this exact
  tuple. Any writer change invalidates these reports; rerun exact-head review
  before committing.

## Exact ownership map for writer waves

Only one writer wave runs at a time. Workers receive isolated worktrees and may
not spawn, merge, push, delete, or edit another owner’s files.

| Wave | Owner | Allowed paths | Forbidden overlap |
|---|---|---|---|
| Toolchain | integration owner | `java-services/pom.xml`, `java-services/restful-api/pom.xml`, Maven wrapper, Docker/CI toolchain docs | domain Java, FE/mobile behavior |
| DB/JPA | DB specialist | `java-services/restful-api/src/main/java/**/persistence/**`, typed academic/registration repositories, V13+ and H2 V7+ migrations, DB tests | controllers/DTOs, FE/mobile, assistant provider |
| Backend/API | backend specialist | registration services/controllers/DTOs, problem details, OpenAPI, PDF renderer, API tests | DB entity/migration files, FE/mobile |
| Web | Stitch FE specialist | `frontend/src/**` registration/admin/assistant, frontend tests/e2e/styles | Java, mobile, shared contract unless integration owner approves |
| Mobile | mobile specialist | `mobile/src/**` registration/assistant/client/routes, mobile tests/docs | Java, frontend |
| QA/security/docs | QA/security/docs owner | reports, deterministic abuse tests, docs/check scripts in assigned files | active writer paths; no architecture rewrite |
| Review | Kongming/Wukong/FE reviewer/tester/debugger/code-reviewer | read-only inspection and reports only | all source mutation, commits, pushes |

## Authorized rulings

- Treat all predecessor PASS reports and dirty assistant files as historical or
  candidate input only; none is release evidence for this successor.
- Preserve dirty `main`, existing worktrees, ignored E2E/nginx artifacts and
  Docker objects. No deletion, reset, cleanup, push or deployment occurs in
  Phase 0.
- If Java 25 or PostgreSQL runtime is unavailable, record the exact gate as
  `NOT_RUN`/`BLOCKED_CAPABILITY` rather than substituting another runtime.

## Deferred findings

- Physical microservice/container/image/volume cleanup: `DEFERRED` pending
  exact inventory and authorization.
- Live DeepSeek billing, native device certification, remote CI and production
  cutover: outside local execution authority.

## Phase 1 record

- [x] Added Maven Wrapper 3.9.12 and Maven/Java Enforcer rules.
- [x] Added explicit Java 21 baseline profile and separate CI job.
- [x] Moved compiler, Docker build/runtime, and CI target to Java 25.
- [x] Recorded container evidence in `reports/phase-1-toolchain.md`.
- [x] Java 21 full verify: PASS (Temurin 21 container, exit 0).
- [x] Java 25 full verify: PASS (Temurin 25.0.2 container, 162 tests, exit 0).
- [x] Host JDK 24 default run: EXPECTED FAIL via Enforcer; not used as target
  evidence.

The assistant scheduled-job/H2 schema noise is a known in-scope persistence
isolation finding, not a suppressed failure. It is carried into Phase 2.

## Next resume point

Phase 1 is complete in the candidate container runtime. The DB/JPA writer wave
delivered a foundation checkpoint in candidate commit `94e85c3`; Phase 2 remains
active for PostgreSQL concurrency/upgraded-copy evidence and JDBC writer parity.
The next resume point is the backend/API registration wave only after this
checkpoint is recorded. Do not begin edits on dirty `main` and do not let later
workers overwrite the persistence owner's files.

## Phase 2 foundation checkpoint

- [x] Worker reviewed read-only at source SHA `06a54aa3d955f6658dc8d3431d666414c7b655f7`.
- [x] Integrated as `94e85c3` after excluding dirty assistant config from the
  cherry-pick and applying the minimal persistence profile changes manually.
- [x] V13-V16 PostgreSQL and V7-V10 H2 migrations added; V11/V12 preserved.
- [x] Typed entities/repositories and lock declarations added.
- [x] Java 25 focused suite: 4 tests PASS; ephemeral PostgreSQL 15 SQL
  rehearsal: PASS.
- [ ] PostgreSQL Flyway/Testcontainers concurrency and owner-isolation suite.
- [ ] V10/V12 upgraded-copy rehearsal and invalid-data preflight report.
- [ ] JDBC academic writer parity/cutover inventory.

Phase 2 status is `FOUNDATION_CHECKPOINT`, not `completed`; no downstream agent
may call the JPA migration fully complete from this evidence alone.

## Phase 3-6 implementation checkpoint (2026-08-24)

- [x] Added canonical registration API hardening: status/window gates, typed
  drop replay, RFC7807 missing/invalid idempotency errors, round version and
  deterministic slip snapshot body/hash.
- [x] Added PostgreSQL V18 active-status uniqueness preflight/index and the
  isolated `RegistrationPostgresConcurrencyIT`; real PostgreSQL 15.19 + Java
  25 run passed (two active-status/capacity tests, exact container cleanup).
- [x] Rehearsed Flyway upgrades from clean V10 and clean V12 copies to V18 on
  disposable PostgreSQL 15 containers; both paths passed. Injected duplicate
  active enrollment and invalid section capacity into V12 copies; V13 stopped
  with the documented preflight markers in both cases.
- [x] Added asynchronous assistant SSE executor/controller lifecycle, source
  validation/fallback/privacy guard regression, and stable malformed-query
  handling in `RegistrationProblemHandler`.
- [x] Added web assistant/admin/registration surfaces, guarded disposable E2E
  Compose config, mobile JSON parity and phase reports.
- [x] Java 25 focused gate: 69 tests, 0 failures, 0 errors, 2 skipped PG ITs
  without environment. Full reactor: 180 tests, 0 failures, 0 errors, 2
  skipped PG ITs without environment.
- [x] Frontend local gates: 24 tests, typecheck, lint and Next build pass.
- [x] Mobile local gates: 17 tests and typecheck pass.
- [x] Static hygiene/secret/encoding/docs and normal/disposable Compose config
  checks pass.

## Current phase rulings

- Phase 2 remains `FOUNDATION_CHECKPOINT`: upgraded-copy rehearsal and complete
  academic JDBC-writer cutover are still open; H2 does not prove PostgreSQL
  academic locking.
- Phase 3 remains `in-progress`: canonical local API and PostgreSQL capacity
  evidence pass, while legacy alias parity, operation-first lock order and a
  Unicode-capable pinned PDF renderer need review/follow-up.
- Phase 4 remains `in-progress`: authenticated Playwright at 390/768/1440 and
  independent accessibility review are `NOT_RUN`.
- Phase 5 is locally complete; native device/simulator is `NOT_RUN`.
- Phase 6 is source/config pass but remains `in-progress` until exact-head
  docs/review identity is frozen. Physical Docker/nginx/ignored-artifact
  cleanup is deferred and no destructive action was taken.

## Next resume point

Freeze the candidate after the report/docs edits, create split Conventional
Commits with explicit paths, then run the required read-only Kongming, Wukong,
FE reviewer, tester/debugger and code-reviewer lanes against that exact SHA.
Only cause-aligned repairs may follow; push the feature branch only after the
review/gate circuit. Production, remote CI, live provider and device claims
remain separate.

## Auth/mail and assistant JPA extension checkpoint (2026-08-25)

- [x] User-approved successor authority recorded in the active plan; dirty
  `main` remains protected and no merge, stash, reset or cleanup has occurred.
- [x] Auth/mail lifecycle integrated at candidate commit `e8494646`: V19/V12
  hashed challenges and rate buckets, pending registration, verify/resend,
  forgot/reset, refresh-session revocation, Spring Mail, and vi/en HTML/text
  templates. Java 25 compile and focused lifecycle/template/security tests pass.
- [x] Assistant entity/repository foundation integrated at `f3153d2f`.
- [x] Assistant JDBC production path removed at `c0548bac`: conversation,
  message/citation, quota, turn ledger, dispatch, feedback, catalog projection
  and knowledge admin now execute through JPA repositories/`EntityManager`;
  the existing native CAS and `FOR UPDATE` lock order remain intact.
- [x] Assistant Java 25 evidence after cutover: compile PASS; JPA foundation,
  knowledge writer, catalog and repository tests PASS (8 tests); turn-ledger
  H2 PASS (14 tests); API/SSE/ownership/governance/outage/service/provider
  checkpoint converged with no remaining failure (34-test scope, then focused
  governance/state-machine rerun 11/11 PASS).
- [x] Source scan finds no `NamedParameterJdbcTemplate`, `JdbcTemplate`,
  `jdbc.update` or `jdbc.query` under production `thesis/assistant` code.
- [x] Web/mobile auth contract alignment is implemented. Frontend tests
  (34/34), typecheck, lint with zero warnings and production build pass;
  mobile tests (19/19) and typecheck pass. Canonical lifecycle routes, URL
  token scrubbing and mobile deep-link/fallback handling are covered.

## Registration/PostgreSQL/Mailpit local checkpoint (2026-08-25)

- [x] Registration writer cutover now uses JPA for operation, slip,
  enrollment/drop and grade mutation paths. The canonical and compatibility
  routes share the same idempotent service, and the lock order is
  operation -> round -> section -> student.
- [x] Registration slips use pinned PDFBox 3.0.8 plus the embedded OFL Noto
  Sans Vietnamese subset, deterministic snapshot/hash semantics and Unicode
  pagination. Focused PDF/schema/contract/auth tests pass (25 tests).
- [x] Academic mutation JPA regressions pass (3/3): capacity consistency after
  delete, grade update/publication and rollback of a cross-section batch.
- [x] Fresh PostgreSQL 15.19 Flyway V1 -> V19 passes. Registration service and
  concurrency tests pass (5/5), including same-key replay, capacity-race
  rollback and idempotent drop. Assistant ledger/concurrency tests pass
  (11/11) on the same PostgreSQL boundary.
- [x] Upgraded-copy rehearsals pass: V12 -> V19 and V18 -> V19 preserve the
  asserted data/checksums; V18 -> V19 backfills trusted active users as
  verified and exposes no raw challenge-token column. The legacy assistant
  V11 -> V12 rehearsal also passes (1/1).
- [x] PostgreSQL `ddl-auto=validate` exposed a real `CHAR(64)` versus VARCHAR
  entity mismatch for registration request/content hashes. The two entity
  mappings now use `SqlTypes.CHAR`; the application subsequently started
  successfully against PostgreSQL V19 with schema validation enabled.
- [x] Live Mailpit lifecycle passes locally: register pending without session,
  bilingual HTML/text verification mail capture, single-use verification,
  login, refresh rotation, logout revocation, forgot/reset mail, reset session
  revocation, old-password rejection and new-password login. Database proof:
  two challenges, both 64-character hashes, both consumed; no raw token value
  was printed or found in application request logs.
- [x] Exact temporary Mailpit/PostgreSQL containers `ccmailpit` and `ccmailpg`
  were removed after the proof. The reusable Maven Java 25 cache container
  `ccmvn25g` remains intentionally available until terminal Java verification.
- [x] H2 mapping regression after the `SqlTypes.CHAR` correction passes 16/16.
  Generated OpenAPI regression passes and proves the lifecycle routes, `202`
  responses and `AuthUserResponse.emailVerified` schema.
- [x] Playwright with installed Chrome proves the chatbot/admin/assistant
  responsive and focus-trap flows (3/3) plus the real browser registration,
  Mailpit verify/reset, fresh login, 390/768/1440 auth routes, locale alias,
  keyboard reachability and no-referrer flows (2/2). The first browser attempt
  was `NOT_RUN` because the bundled Chromium binary was absent; no application
  verdict was inferred from it.
- [x] Browser evidence exposed two in-scope defects and both were repaired:
  auth inputs now have explicit labels/descriptions and `AuthShell` has a main
  landmark; raw mail challenges now use `#token=` fragments so frontend/server
  access logs never receive them. Focused mail-template, frontend 34/34,
  typecheck and zero-warning lint gates pass after the repair. Browser auth
  rerun passes 2/2 and its observed access log contains no raw challenge.
- [x] Terminal checkpoint passes on the final local source state: both Compose
  configs validate; frontend tests 34/34, typecheck, zero-warning lint and
  production build pass; mobile tests 19/19 and typecheck pass; Java 25 Maven
  verify passes 222/222; all five authenticated Playwright scenarios pass in
  1.1 minutes with installed Chrome; `git diff --check`, secret/encoding scans
  and Git connectivity pass. The connectivity check reported only preserved
  dangling objects, not corruption.
- [x] Exact temporary runtime cleanup is complete: the disposable E2E stack,
  `ccmvn25g`, `ccmailpit`, `ccmailpg` and the exact generated E2E API image were
  removed. No broad Docker prune, volume deletion or developer-container
  mutation occurred.
- [x] The earlier generic Supabase checkpoint is superseded by the exact
  `Student_Management` preflight below. No local user/session/challenge,
  Mailpit, token or credential data was selected for upload.

## Supabase read-only preflight (2026-08-25, historical and superseded)

This section records the earlier wrong-project discovery. It is retained for
audit history only; the exact `Student_Management` evidence and hosted apply
are recorded in the section below.

- [x] No remote write was attempted. The scoped Supabase MCP resolves to
  project ref `awaknzhadjglbfkhigck`; its migration history contains four
  Healthcare migrations and its only relevant application schema is
  `healthcare` (15 tables). CampusCore markers `auth."User"`,
  `assistant.knowledge_document`, `academic."Course"` and
  `thesis.thesis_topic` are absent.
- [x] Supabase CLI 2.109.1 is authenticated read-only for project discovery.
  It lists `our-love-story`, `JasonTM17's Project` and `Food_Delivery_Crab`;
  no accessible project is named `Student_Management`, and the candidate has
  no linked project or Supabase URL/ref credential. Therefore neither the MCP
  target nor any CLI-visible project is an authorized destination.
- [x] The MCP database is PostgreSQL 17.6. Its managed `auth` schema has 23
  tables and is platform-owned; the local CampusCore V2/V8/V19 chain creates
  and references application tables/views in the same schema. Official
  Supabase permissions guidance warns that violating ownership assumptions in
  `auth`/`storage` can break platform services on a later migration.
- [x] Supabase currently exposed no development branches for that historical
  MCP project. This no longer blocks the task because the user supplied and we
  independently verified the separate immutable `Student_Management` ref.

## Supabase-compatible local schema checkpoint (2026-08-25)

- [x] Independent Kongming architecture review falsified direct V1-V19 replay
  against hosted Supabase because CampusCore and the platform both owned
  `auth`. The accepted minimal delta retains Spring auth, uses a forward V20 to
  move application tables/views to `campuscore_auth`, and adds an opt-in B20
  schema-only baseline for a brand-new hosted target. This was a compatibility
  ruling inside the accepted outcome; it did not authorize a remote write.
- [x] Production V1-V19 files remain unchanged. V20 creates
  `campuscore_auth`, moves `User`, `Role`, `Permission`, `UserRole`,
  `RolePermission`, `Session`, `AuthChallenge` and `AuthRateLimitBucket`, and
  recreates the Student/Lecturer compatibility views there. Production runtime
  SQL and H2 fixtures now use the private schema.
- [x] Focused Java 25 auth persistence regressions pass 20/20 after the schema
  cutover. A real PostgreSQL 15.19 V1 -> V20 run produces 10 private-auth
  objects (8 tables plus 2 views), nine foreign keys targeting the private
  schema, and zero CampusCore tables in managed `auth`.
- [x] Flyway upgraded-copy rehearsals V12 -> V20 and V18 -> V20 pass and
  validate 21 migrations. No down-migration or historical V-file edit was
  used.
- [x] `B20__campuscore_supabase_baseline.sql` was generated from the V20 schema
  with `--schema-only --no-owner --no-privileges --no-comments`, contains only
  the six CampusCore schemas, and has no `COPY`, `INSERT`, `GRANT`, `REVOKE` or
  managed-schema reference.
- [x] `CampusCoreSupabaseBaselinePostgresIT` passes against two disposable
  PostgreSQL 15.19 databases using Flyway 11.7.2. It proves logical column,
  constraint, index and view parity with V1-V20, preserves managed-schema
  sentinels, records only B20 as `SQL_BASELINE`, and finds zero application
  rows. The comparator intentionally uses logical column ordinal because a
  schema dump cannot preserve PostgreSQL's dropped-column physical attnum; it
  also canonicalizes only PostgreSQL's equivalent enum-array cast rendering.
- [x] Portable runbook `docs/integrations/supabase-database.md` records the
  exact identity, backup, drift, Flyway, zero-data and rollback gates. The
  existing assistant-authoring guide no longer treats evidence from another
  project as proof for `Student_Management`.

## Supabase Student_Management hosted synchronization (2026-08-25)

- [x] The project-scoped MCP URL supplied by the user and the Management API
  resolve the immutable target `Student_Management`, ref
  `kbptwmwitojjjwvwckom`, region `ap-south-1`, status `ACTIVE_HEALTHY`, and
  PostgreSQL `17.6`. The Healthcare ref remains a separate connector and was
  never used as a write target.
- [x] Read-only drift preflight found no CampusCore schema, table or Flyway
  history on the target. Before-write metadata snapshot was retained outside
  the repository; managed `auth`, `storage` and `realtime` had 36 relations
  owned by `supabase_admin`, while `supabase_migrations` was absent.
- [x] A schema-only logical backup was created outside the repository before
  the write. The retained backup hash is
  `F42192E9C5951A8302F072A1184C58A509FF6900D4EA3017EFF0348837240E55`.
  The dump was checked for raw token/JWT material; matches were only literal
  role/function identifiers such as `service_role`, not credentials.
- [x] Because temporary database access requires SSL enforcement, the project
  owner token enabled SSL enforcement and a two-hour, IP-unrestricted JIT
  `postgres` mapping only for the migration window. The mapping and JIT
  feature were revoked/disabled immediately after postflight; SSL enforcement
  remains enabled. No token was written to the repository or logs.
- [x] Flyway 11.7.2 `info` against the verified Supabase pooler showed B20 as
  the only pending migration and V1-V20 below baseline. `migrate` applied one
  successful `SQL_BASELINE` at v20; subsequent `validate` passed all 22
  migrations and `info` reported schema version 20.
- [x] Postflight proves six CampusCore schemas, 48 application tables and the
  two private-auth views (`campuscore_auth.Student`/`Lecturer`). All 47
  non-history application tables have zero rows; Flyway history has exactly
  the schema-creation row plus the successful v20 baseline row. No application
  auth object exists in managed `auth`.
- [x] Before/after managed-schema comparison is unchanged (36 relations and
  owners); the retained after-acceptance snapshot hash is
  `57CBDFE1BA9A39179D2BC20D495449153C8EA8EEFF74C766AB6E9B9EB0465788`.
  Supabase security advisors report zero lints. Performance advisors report
  66 INFO-only `unindexed_foreign_keys`/`unused_index` notices on an empty
  baseline; no critical/error finding was introduced and load-tuning is
  deferred until real workload evidence exists.
- [x] Read-only MCP inventory after apply matches the expected 48 zero-row
  tables. No MCP `execute_sql`/`apply_migration` write path was used; the only
  remote DDL path was the reviewed Flyway B20 baseline.

## Next resume point

Freeze the candidate after the final local diff/secret checks, obtain fresh
exact-head Kongming/Wukong/FE/test/debug/code-reviewer evidence on that SHA,
then commit, tag and merge through a clean integration worktree while
preserving dirty `main`. Remote database synchronization is complete; app
traffic cutover, remote CI, live provider billing and native-device evidence
remain separate gates.
