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

Stage only the reviewed candidate paths and create the final Conventional
Commits after the terminal local diff/secret checks. Obtain fresh exact-head
Kongming/Wukong/FE/test/debug/code-reviewer evidence on that clean commit, then
make no further source changes before tag/merge through a clean integration
worktree while preserving dirty `main`. Remote database synchronization is complete; app
traffic cutover, remote CI, live provider billing and native-device evidence
remain separate gates.

## Successor repair and terminal verification continuation (2026-08-25)

- [x] The candidate repair set was reverified from the dirty successor worktree
  at `24ed5028c8a3fa285c072f7925dd91ecb38eef38`. The Java 25 Maven reactor
  `verify` command exited 0; the refreshed `restful-api` Surefire report set
  contains 237 tests with zero failures, errors or skips. This is current
  candidate evidence, not the earlier historical report count.
- [x] Registration PostgreSQL concurrency on disposable PostgreSQL 15.19
  passed all 6 tests after fixing the production slip identifier to a fixed
  64-character SHA-256 value and tightening test-only fixture cleanup. The
  database was `cc_terminal_20260825_registration` in task container
  `ccv20pg`; no unrelated container or database was targeted.
- [x] V12/V18 upgrade, source-to-baseline parity and Flyway safety matrix passed
  on disposable PostgreSQL databases. After the SQL callback hardening, the
  safety matrix was rerun against `cc_wave2_flyway_safety_20260825` and passed
  5/5, including duplicate history-table rejection, repeatable-history
  rejection, checksum drift and pre-existing-object refusal. The only failed
  attempt was a fixture-cleanup leak; the `shadow_history` schema is now part
  of the test cleanup list and the rerun is green.
- [x] Web tests/typecheck/lint/build (38 tests), mobile tests/typecheck (19
  tests), both Compose config checks and authenticated Chrome E2E (6/6) remain
  green after the repair set. The first E2E invocation was `NOT_RUN` because
  Playwright ffmpeg was absent; after installing the local test dependency, the
  same disposable stack passed and was removed by the runner.
- [x] Flyway SQL callbacks now reject more than one `flyway_schema_history`
  table before evaluating individual rows; Java's hosted-history inspection and
  the SQL callback path therefore share the same single-location boundary.

The next incomplete step is still a clean exact-head review cycle. No commit,
tag, merge, push, Docker Hub upload or candidate-worktree deletion is approved
by this evidence alone. At the time of this checkpoint, hosted
Student_Management was still recorded as the B20-only schema baseline; the
later exact-project V21 synchronization is recorded below.

## Supabase V21 successor synchronization (2026-08-25)

- [x] Exact-project MCP preflight re-checked `Student_Management`
  (`kbptwmwitojjjwvwckom`): history marker + B20 checksum `1841726166`, no V21,
  36 managed `auth`/`storage`/`realtime` relations, zero application rows,
  zero ambiguous registration-round semesters and no `Enrollment.roundId`.
- [x] Current Supabase migration documentation was read through the project-
  scoped docs search before the write. The reviewed V21 SQL was applied only to
  the Student_Management connector with migration name
  `persist_enrollment_registration_round`; no Healthcare connector or generic
  project was used.
- [x] Remote postflight is structurally PASS: Supabase migration record
  `20260825155849`; `thesis.flyway_schema_history` has exactly marker, B20 and
  V21 (`-249127582`) rows; `Enrollment.roundId` is non-null; the composite FK,
  round uniqueness constraint and student/round/status index exist; managed
  relation count remains 36; all CampusCore application row counts remain 0.
- [ ] Supabase advisor reports a current critical `rls_disabled` finding for
  all 48 CampusCore tables. No blanket remediation was applied because RLS
  without a policy design would block access. Public Supabase Data API use and
  production cutover remain HOLD pending an explicit least-privilege policy
  decision; Spring direct-server traffic is not a substitute for that proof.
- [ ] Hosted PITR/restore remains `HOLD_NOT_PROVEN`; the external schema-only
  backup hash predates V21 and is retained outside the repository. The remote
  migration is forward-only and no destructive rollback was attempted.

The hosted schema synchronization is complete for the empty schema-only target,
but it does not authorize public traffic, seed data, RLS policy changes or
production release. The next implementation gate is the clean exact-head review
cycle on the candidate after this documentation/evidence update.

## Successor repair checkpoint c717d288 (2026-08-25)

- [x] The candidate is clean at exact SHA
  `c717d2883aa9abc10627b9c34c37ea8801c2c8b7` after the independent review
  findings were repaired. Verification cannot use the earlier `be5cc934` review
  identities because that snapshot is now historical.
- [x] Auth lifecycle regression: `AuthLifecyclePersistenceTest` 7/7 passed.
  Email verification now updates only `PENDING_VERIFICATION` users and refuses
  to reactivate an administrator-disabled account; the challenge is consumed
  and the stable invalid code is returned.
- [x] Assistant ledger regression: `ThesisAssistantTurnLedgerH2Test` 16/16
  passed. Disconnect cancellation now compares the existing request hash before
  changing a row, returning `IDEMPOTENCY_CONFLICT` for a reused key with a
  different payload.
- [x] Frontend tests 38/38, typecheck, zero-warning lint and production build
  passed. Mobile tests 19/19 and typecheck passed. The Next route handlers now
  read `JAVA_API_ORIGIN` at request time; the local Compose stack includes the
  production web service and runtime API origin wiring.
- [x] Compose configuration and secret/text-encoding checks passed. The
  frontend Docker image must still receive an authenticated API smoke test after
  the final image is built; the prior `be5cc934` browser E2E 6/6 result is not
  final-head evidence for this successor.
- [ ] Fresh exact-head Kongming, Wukong and FE/code-reviewer evidence is still
  required on the final clean SHA. No archive tag, Docker push, GitHub release,
  merge or branch cleanup is authorized until that review cycle and terminal
  browser/image gates pass.

## Scope ruling and hosted evidence bundle (2026-08-25)

- [x] Independent review finding about remaining JDBC writers was reconciled
  explicitly: this plan claims JPA cutover for assistant state/knowledge and
  registration mutation writers, not a whole-domain conversion. Catalog/admin,
  academic-read, thesis-group and auth compatibility JDBC paths are a named
  follow-up wave requiring their own parity proof.
- [x] The resend cooldown enumeration finding was repaired and covered by an
  H2 regression; known and unknown addresses now receive the same accepted
  response and no mail is sent during cooldown.
- [x] Redacted, commit-bound Supabase evidence was added under
  `reports/supabase-student-management-evidence/`. It records target identity,
  external artifact hashes, Flyway output summary, before/after managed-schema
  counts, row counts, advisor results, SSL/JIT state and the rollback boundary.
- [x] Hosted schema synchronization is structurally PASS for the empty B20
  baseline. It is infrastructure-only; application readiness, hosted restore
  rehearsal and production traffic remain `HOLD/NOT_PROVEN`.

## Flyway safety repair checkpoint (2026-08-25)

- [x] Independent Kongming/Wukong review of `70e0971` found and falsified the
  old fail-open `auth AND storage AND realtime` predicate and the unreleased
  lower-version V0 validation drift on existing V12/V18/V20 histories.
- [x] Replaced the unreleased V0 versioned guard with profile-independent
  `beforeValidate.sql` and `beforeMigrate.sql` callbacks. The callbacks fail
  closed on any managed/unknown platform schema or `supabase_*` role, alternate
  `auth` owner, unknown auth relations/functions, and untrusted partial private
  signatures. Existing local historical auth relation subsets remain allowed so
  V12/V18 upgrades can reach V20.
- [x] Hosted baseline selection is now an exact single-location allowlist:
  `classpath:db/supabase-baseline`. The baseline callbacks reject non-empty
  legacy/incompatible Flyway history while allowing the one exact B20
  `SQL_BASELINE` record on later validation/restart; the Java persistence
  strategy applies the same check. The legacy and baseline locations are
  never combined.
- [x] PostgreSQL disposable safety matrix passes: auth-only/empty marker,
  managed auth relation with storage/realtime absent, alternate owner, trusted
  legacy signature, trusted post-V20 private signature, direct-style callback
  refusal, existing-history callback refusal and no-V0 validation drift.
- [ ] Fresh exact-head Kongming/Wukong/code-reviewer evidence after this repair;
  the previous `70e0971` verdicts remain historical and cannot approve merge.

## Artifact portability ruling

The Supabase evidence manifest retains hashes and portable artifact IDs only;
developer `%TEMP%` paths are intentionally redacted. The external backup and
before/after snapshots remain outside the repository under operator-controlled
retention. Hosted rollback remains `HOLD/NOT_PROVEN` because PITR/restore
evidence was unavailable; no application traffic cutover is authorized.

## Terminal local verification checkpoint (2026-08-25)

- [x] Java 25 full Maven reactor verification: 205 fresh tests, zero failures
  or errors. PostgreSQL 15.19 authority matrix: 25/25 across auth registration
  race, registration capacity/idempotency/drop, assistant ownership/lease/
  recovery, V12/V18 upgrade, B20 parity and Flyway safety.
- [x] Frontend: 36/36 tests, typecheck, zero-warning lint and production build.
  Mobile: 19/19 tests and typecheck. Both Compose configurations validate.
- [x] Authenticated Chrome Playwright: 5/5 scenarios, including Mailpit
  register/verify/login/logout/forgot/reset/fresh-login, assistant streaming/
  citations/feedback, admin knowledge and 390/768/1440 responsive keyboard/
  referrer coverage. The first build attempt stopped before app startup on a
  transient Docker Hub 502; the disposable project was proven empty, the base
  image was pulled by digest and the bounded retry passed.
- [x] Git diff, secret, documentation hygiene and text encoding checks pass.
  Fresh exact-head independent review remains the next gate; live DeepSeek,
  native-device, remote CI, hosted restore and production traffic stay
  `NOT_RUN/HOLD`.

## Resume checkpoint after exact-head review (2026-08-25)

- Active phase: repair the release-blocking findings from the exact clean
  candidate `24ed5028c8a3fa285c072f7925dd91ecb38eef38`, then freeze a successor
  SHA and repeat every required independent review before any tag, merge or
  push.
- Required repairs: complete Flyway history allowlist (schema marker, exact
  B20 checksum and successor migrations; reject repeatable/checksum drift and
  pre-existing application objects), durable assistant cancellation before
  asynchronous reservation, FE close/reopen reconciliation and non-committal
  verification outage copy, registration-round ownership plus stable student
  locking, release-document wording/whitespace drift.
- Active disjoint specialists: FE (`/root/fe_review_repairs`), registration
  (`/root/registration_round_repairs`) and assistant cancellation
  (`/root/assistant_cancel_repairs`). They may edit only their assigned slices;
  integration remains owned by `/root`.
- Safe boundary: candidate and task PostgreSQL containers remain available for
  focused regressions; dirty `D:\Student_Management` must not be reset,
  stashed, overwritten or merged directly. Before merge, recompute the actual
  tracked/untracked content and index identities, move the dirty checkout to a
  named WIP branch without changing files, and merge through a clean
  integration worktree only if the before/after identities match.
- Next resume action after context/usage interruption: read this section,
  inspect each specialist return and current candidate status, integrate only
  evidence-backed repairs, run focused regressions, update this checkpoint with
  exact commands/results, then continue to fresh exact-head reviews. Do not
  repeat already evidenced terminal gates or create a second plan.

## Successor repair and terminal local gates (2026-08-26)

- [x] Candidate repair slice is complete on pre-commit
  `81cdac1e4b6d2e682dcad2c9d5a1ec3c304093ad`: disabled-account reset races now
  serialize on the user row and the password update is status-guarded;
  assistant pre-reservation cancellation enters the conversation-first lock
  order; mobile refresh is single-flight and generation-fenced for JSON and
  binary requests; stale lifecycle cookies are ignored while cookie refresh
  still requires CSRF; knowledge slug conflicts return a stable code; the
  frontend default locale is Vietnamese and E2E JWT defaults satisfy the
  runtime minimum.
- [x] Focused Java persistence regressions: 38 tests, zero failures/errors
  (`AuthLifecyclePersistenceTest`, token resolver/CSRF, knowledge writer and
  assistant H2 ledger).
- [x] FE/mobile static gates: frontend 39/39 tests, typecheck, lint with
  `--max-warnings=0`, production build; mobile 23/23 tests and typecheck.
  Compose config, assistant-secret scan (556 files) and text-encoding guard
  pass. The one failed typecheck invocation was a parallel `.next` generation
  race; the sequential rerun passed and is the accepted evidence.
- [x] Fresh Java 25 Maven `clean verify`: 215 tests across 46 freshly generated
  Surefire suites, zero failures/errors/skipped. Isolated PostgreSQL 15.19 container
  `campuscore-final-postgres-20260826` ran auth (4), registration (6) and
  assistant ledger (12) concurrency/ownership/recovery tests: 22/22 passed.
  The container is task-owned and must be stopped/removed only at the final
  disposable-container cleanup gate.
- [x] Authenticated Chrome/Playwright and Mailpit E2E: 6/6 passed. The flow
  proved student register (202), HTML/text verification mail, token confirm,
  login/logout, password-reset mail, reset and fresh login; it also exercised
  assistant SSE/citation/feedback, cancel/reopen, localized admin knowledge,
  keyboard focus and responsive layouts at 390/768/1440. The first run exposed
  English assertions against the new Vietnamese default; the bounded repair
  made English routes explicit. The second run then exposed the genuinely
  missing localized admin-knowledge route; its wrapper and regression were
  added without weakening assertions. The final run passed and removed its
  exact disposable containers, network and volume.
- [ ] Exact-head independent reviews still require a clean successor SHA. No
  archive tag, Docker push, GitHub release, merge or branch cleanup is
  authorized before those gates.

## Successor exact-head terminal matrix (2026-08-26)

- [x] Frozen candidate `e25d9aee16c2e82427b772b4909fa2ca98ed3499` remained clean
  during the terminal matrix. The four repair commits are `b6bcbb1c`
  (auth token/hash scrubbing), `917a3333` (FE assistant unmount/registration
  scope), `aa4dd65b` (knowledge archive and H2 ledger parity), and `e25d9aee`
  (early cancellation-handle cleanup).
- [x] Frontend exact-head gates: `npm test` 44/44, `npm run typecheck`,
  `npm run lint -- --max-warnings=0`, and `npm run build` all passed. Mobile
  gates remain 23/23 tests plus typecheck from the same clean source checkpoint.
- [x] Java 25 Docker `clean verify` passed 47 Surefire suites, 227 tests,
  zero failures/errors/skips. Focused assistant regressions passed Service 6/6,
  H2 ledger 17/17, and archived knowledge JPA writer 4/4.
- [x] Fresh PostgreSQL 15.19 authority databases inside task-owned
  `campuscore-pg-e25d` passed assistant ledger 12/12, registration concurrency
  6/6, and auth concurrency 10/10. URLs were explicit and no hosted or
  developer database was selected.
- [x] Disposable Playwright/Mailpit runtime on the exact source passed all 6
  scenarios (`frontend/test-results/playwright/.last-run.json` status `passed`,
  empty `failedTests`). The runner removed only project
  `campuscore-course-e2e-e25d-r2` containers, volume and network afterward.
- [x] Secret scan (558 git-visible files, values redacted), text-encoding,
  documentation hygiene, normal/E2E Compose config and `git diff --check`
  passed. Live DeepSeek/provider billing, remote CI, production cutover and
  registry publication remain unobserved and therefore HOLD.
- [ ] Fresh exact-head Kongming, Wukong and code-review evidence is still
  required after this ledger commit; no merge, push, archive tag, release or
  branch/worktree cleanup is authorized before those reviews and safe-boundary
  identity checks pass.

## Pre-reservation fence repair checkpoint (2026-08-26)

- [x] Wukong falsified a generation-zero cancellation-fence leak when
  `cancelBeforeStart` parsed a malformed `conversationId`. The repair moves
  fence installation after guard/locale/UUID/hash validation and adds a
  regression asserting `INVALID_CONVERSATION_ID` leaves no pre-cancel marker.
- [x] Repair source/test changes are limited to
  `ThesisAssistantService.java` and `ThesisAssistantServiceTest.java`; no
  public route or persistence schema changed.
- [x] Serialized Java 25 runs on the repaired source passed
  `ThesisAssistantServiceTest` 7/7, `ThesisAssistantTurnLedgerH2Test` 17/17,
  `AssistantKnowledgeJpaWriterPersistenceTest` 4/4,
  `ThesisAssistantGovernanceWebTest` 4/4,
  `ThesisAssistantApiContractTest` 7/7,
  `ThesisAssistantSseControllerTest` 2/2 and
  `io.campuscore.restfulapi.thesis.ThesisAssistantContractTest` 4/4, with
  zero failures/errors/skips. A prior concurrent Surefire artifact reporting
  H2 failures was superseded by these serialized clean-target runs and is not
  release evidence.
- [ ] Commit the repair, freeze the resulting clean SHA, and obtain fresh
  exact-head Kongming/Wukong/code-review verdicts. No merge, push, archive tag,
  release or cleanup is authorized before those reviews and dirty-main
  identity checks pass.

## Auth-mail locale and frontend session-race checkpoint (2026-08-26)

- [x] Commit `fdabcd321671ab27f9a55c2e9e001a7361dd30b5` preserves the
  requested `vi`/`en` locale in verification and password-reset mail links;
  the Mailpit browser contract now follows the captured link instead of
  reconstructing one. The focused Java mail test passed under Java 25.
- [x] Commit `65b2bb65384a3335e8b4e7449a8cb1d0e194b5fc` fences stale
  `AuthProvider` background refresh completion from overwriting an explicit
  login/logout transition. The regression was observed red before the fence
  existed and green afterward; frontend tests are 40/40, typecheck, zero-warning
  lint and production build all pass.
- [x] The strengthened authenticated lifecycle verifies refresh success, logout
  cookie removal and rejected post-logout refresh before reset/fresh login. The
  default Playwright invocation was `NOT_RUN` because its bundled Chromium was
  not installed and its disposable Compose project was removed. Re-running on
  the available system Chrome channel passed the complete Mailpit flow in 35.7
  seconds and removed its exact containers, volume and network. The test-owned
  timeout is 60 seconds because the previous 30-second budget expired before
  the final login on cold Next route compilation.
- [ ] Freeze the clean successor SHA after this ledger update, run the planned
  terminal verification matrix once on that exact SHA, then dispatch fresh
  Kongming/Wukong/FE/test/debug/code-review evidence before shipping or merge.

## Independent review defect checkpoint (2026-08-26)

- Active plan remains `plans/260824-1905-campuscore-java25-jpa-enrollment/plan.md`.
  Current phase is 7; next step is the bounded auth/registration defect repair,
  with failing-before/passing-after regressions and a clean successor review
  as its exit criterion. This is a repair within the accepted contract, not a
  new architecture or release target.
- [x] Clean reviewed source: `42a32ddf6fa46c1dd07c8abd4844ff3481cb75f6`.
  Its only change from `0965068ce62fdcd13f64c1479ab11d5e265e9e69`
  is the frontend regression loader's Node 20 compatibility repair. Frontend
  tests pass 40/40 in `node:20-alpine` with a read-only mount and no network.
  The Java subtree remains `a429bd57a3171f83c58684677cc01b1634256b8b`.
- [x] Java 25 `clean verify` at the identical Java subtree passed 216 tests
  across 46 suites. This command does not select the PostgreSQL `*IT` classes;
  their evidence is recorded separately, not inferred from `verify`.
- [x] Explicit PostgreSQL authority run selected `AuthConcurrencyPostgresIT`,
  `RegistrationPostgresConcurrencyIT` and `ThesisAssistantTurnLedgerPostgresIT`:
  4 + 6 + 12 = 22 tests, zero failures/errors/skips. It used three fresh,
  task-owned databases, one per suite. A preceding reused-database run failed
  a retention-count assertion because an older expired conversation was
  legitimately purged; the disposable-fixture precondition was restored
  without changing the application or weakening the assertion.
- [ ] Kongming's fresh read-only review returned `FAIL`: cohort windows were
  not enforced by enrollment mutations; prerequisite row counting could accept
  duplicate completions instead of distinct required courses; corequisite and
  schedule checks did not restrict active enrollments to the target semester;
  refresh/reset acquired User and Session locks in opposite order.
- [x] The controller independently reproduced the auth lock cycle on an
  isolated PostgreSQL 15.19 database using two transactions and an observed
  `pg_blocking_pids` wait edge. Rotation failed with SQLSTATE `40P01`; both
  transactions rolled back and the original session remained. The database's
  deadlock counter was exactly 1. No hosted DB, real account or token was used.
- [x] The real-service PostgreSQL regression was then run against the unfixed
  auth implementation: `AuthConcurrencyPostgresIT#passwordResetSerializesWithWaitingSessionMutations`
  ran three cases, with two errors and one assertion failure as expected.
  Refresh deadlocked at the Session insert's User FK check; logout deadlocked
  at its User update; a waiting password change accepted credentials read
  before reset and overwrote the reset password. These are behavioral failures,
  not a source-pattern or missing-module assertion.
- [ ] The narrow auth repair now takes the User row lock before refresh session
  lookup, logout deletion and old-password validation. Tests also cover the
  reverse refresh-before-reset order and concurrent single-use consumption for
  each challenge purpose. Focused green verification is pending.
- [x] Test-first registration policy regression on the unfixed source was
  genuinely red: 6 tests failed (future/expired cohort, mutation rollback,
  duplicate prerequisite, prior-semester corequisite and prior-semester
  schedule isolation). After the bounded patch, the same persisted H2 fixture
  passed 6/6 with zero failures/errors/skips. It uses `academic.Student.year`,
  real enrollment/course/section rows and verifies rejected mutation leaves
  enrollment, operation, audit and section count unchanged.
- [x] A serialized focused batch on the patched working tree reports
  `AuthConcurrencyPostgresIT` 10/10, `AuthLifecyclePersistenceTest` 9/9,
  `AuthLoginPersistenceTest` 11/11 and `RegistrationEligibilityPolicyPersistenceTest`
  6/6, all with zero failures/errors/skips. The report files were written by
  the Java 25 container using the task-owned PostgreSQL database for the auth
  suite and isolated H2 for the registration policy suite.
- [x] The explicit successor-focused rerun selected exactly
  `AuthConcurrencyPostgresIT,RegistrationEligibilityPolicyPersistenceTest`
  with the same Java 25 Maven container and exited `0`. Surefire reports show
  10 + 6 tests, zero failures/errors/skips. This is the accepted focused green
  checkpoint for the repair circuit.
- [x] The bounded code and regression repair is committed as
  `f6e744b7d835ceabb88bc4678b1b178fbb6186b6`
  (`fix(auth-registration): enforce serialized account policy`). Only the three
  production auth/registration files and their two PostgreSQL/H2 regression
  files are in that commit; plan evidence is intentionally separate.
- [ ] Wukong independently identified the source/FK lock conflict and supplied
  a deterministic probe design, but its tool run failed before a final verdict.
  Its required sign-off is `BLOCKED_CAPABILITY`, not PASS and not replaced by
  the controller's reproduction. A fresh successor review is still required.
- Ownership: controller writes auth/tests and plan evidence; the bounded
  registration specialist writes only `RegistrationService.java` and its
  registration tests. Neither changes Flyway, FE/mobile, remote services,
  dirty main or release state. Test-first focused Maven runs are serialized.
- Verification budget: first run the new auth/registration regressions against
  the unfixed implementation; after the fixes, run only the affected auth and
  registration suites. Broaden to the terminal matrix after a clean source
  freeze. Rerun a passing gate only for changed covered code or a documented
  fixture/capability correction.
- Resume point: collect focused regression results, complete the minimal
  User-before-Session and registration-validator repairs, then update this
  ledger and freeze a successor. No archive tag, push, release, merge or branch
  deletion has occurred; all publication and cleanup gates remain pending.
