# Execution Ledger
Active plan: plans/20260819-restful-api-consolidation/plan.md

## Current execution state

- Current branch snapshot: `feature/java-thesis-platform` at `d92ce53e884adcd83b5dd479aebeb584d9a83946`, ahead of `origin/feature/java-thesis-platform` by two commits after the thesis differential repair baseline and the academic internal-context bridge on 2026-08-21.
- Repo state before implementation: only user-owned untracked `.agents/`, `.codex/` and `.tmp/`; preserve them and do not stage them.
- Disk snapshot before implementation: C: ~16.55 GiB free, D: ~36.88 GiB free.

## Completed evidence carried forward

- Phase 47 academic schedule read foundation is committed in `7dfda6a` and included in remote HEAD lineage through `57cde78`.
- Last known post-merge evidence from the previous checkpoint: focused Java schedule gate passed, full Java reactor passed with 22 reports / 150 tests / 0 failures / 0 errors / 0 skipped, frontend `npm test --prefix frontend` passed 29/29, and doc/architecture/diff hygiene passed. This ledger treats that as historical evidence only; new claims require fresh commands.

## Current step

- Phase 55 academic internal-context bridge code commit is complete at `d92ce53e884adcd83b5dd479aebeb584d9a83946`; docs/ledger update and push are next.
- Exit criterion: Phase 55 report, plan ledger, execution ledger and migration doc name the exact source commit, gates, open HOLD boundaries and next safe action without claiming public route ownership or Java cutover.

## Phase 48 evidence

- Implemented feature-default-off Java `GET /api/v1/waitlist`, `GET /api/v1/waitlist/my`, `GET /api/v1/waitlist/section/{sectionId}` and `GET /api/v1/waitlist/{id}` read routes.
- Added `migration.academic-waitlist-read.enabled` / `ACADEMIC_WAITLIST_READ_ENABLED:false` and read-only migration safety condition coverage.
- Added H2 persistence tests for student self active scope, admin envelope/filter/order, section active filter, detail/not-found, role/claim/query failures, and default-off route absence.
- Focused gate PASS: `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicWaitlistReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Full Java reactor PASS: `mvn -q -f java-services/pom.xml test`; surefire summary 23 reports / 156 tests / 0 failures / 0 errors / 0 skipped.
- Production waitlist SQL-write grep PASS and `git diff --check` PASS with only Git line-ending warnings.

## Phase 49 evidence

- Implemented feature-default-off Java `GET /api/v1/attendance`, `GET /api/v1/attendance/my`, `GET /api/v1/attendance/my/summary`, `GET /api/v1/attendance/lecturer/my`, `GET /api/v1/attendance/section/{sectionId}`, `GET /api/v1/attendance/section/{sectionId}/summary` and `GET /api/v1/attendance/{id}` read routes.
- Added `migration.academic-attendance-read.enabled` / `ACADEMIC_ATTENDANCE_READ_ENABLED:false` and read-only migration safety condition coverage.
- Added H2 persistence tests for student self filters, student summary formula, admin envelope/date filter/order, lecturer-owned section scope, section ordering/summary formula, detail/not-found, role/claim/query failures, and default-off route absence.
- Focused gate PASS after one fixture/oracle repair: `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicAttendanceReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Full Java reactor PASS: `mvn -q -f java-services/pom.xml test`; surefire summary 24 reports / 162 tests / 0 failures / 0 errors / 0 skipped.
- Production attendance candidate SQL-write grep PASS and `git diff --check` PASS with only Git line-ending warnings.
- Source commit/push complete: `e61dbd3 feat(java): add academic attendance reads` and `4ca7a4b docs(plan): record attendance read checkpoint` pushed to `origin/feature/java-thesis-platform` on 2026-08-21; post-push local and remote branch tips matched `4ca7a4bbeb95136d9fa41df252ce45ff96dc2d5d`.

## Phase 50 evidence

- Implemented feature-default-off Java `GET /api/v1/sections`, `GET /api/v1/sections/my/schedule`, `GET /api/v1/sections/my/grading`, `GET /api/v1/sections/{id}` and `GET /api/v1/sections/{id}/grades` read routes.
- Added `migration.academic-section-read.enabled` / `ACADEMIC_SECTION_READ_ENABLED:false` and read-only migration safety condition coverage.
- Added H2 persistence tests for section list envelope/filter/order/hydration, lecturer semester schedule scope and active-enrollment count, lecturer grading counts/publishability formula, detail/not-found, section grade rows, role/claim/query failures, and default-off route absence.
- Focused gate PASS: `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicSectionReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Full Java monolith gate PASS after cleaning stale reports outside the canonical monolith target: `mvn -q -f java-services/pom.xml clean test`; `java-services/restful-api/target` surefire summary 25 reports / 169 tests / 0 failures / 0 errors / 0 skipped.
- Production section candidate SQL-write grep PASS: no SQL write/DDL markers in the new section read repository, service or controller.
- Source commit complete locally: `38a4f71 feat(java): add academic section reads`.
- Incidental in-scope NOW defect fixed in the same source commit: attendance read date filters and H2 fixtures now bind UTC timestamps so the selected legacy date filter behavior remains stable under the local Asia/Bangkok test runtime.

## Phase 51 evidence

- Implemented feature-default-off Java `GET /api/v1/academic-years`, `GET /api/v1/academic-years/{id}`, `GET /api/v1/classrooms` and `GET /api/v1/classrooms/{id}` read routes under the existing `migration.academic-read.enabled` / `ACADEMIC_READ_ENABLED:false` academic catalog flag.
- Preserved selected legacy behavior for academic-year list/detail envelopes, `startDate` descending order, nested semester summaries, classroom list envelopes, classroom building/room ordering and classroom detail section summaries. Classroom `equipment String[]` parity is deferred to a PostgreSQL-read parity slice.
- Added H2 persistence/default-off tests for academic-year envelope/order/hydration, classroom envelope/detail sections, anonymous/default-off behavior, invalid/repeated/unexpected query failures, bad pagination and missing details.
- Focused gate PASS after the fixture compatibility repair: `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`.
- First full Java reactor attempt FALSIFIED a test-fixture assumption: Phase 51 made H2 `Classroom` columns non-null without defaults, while older schedule/section/waitlist tests still insert minimal classroom rows. The repair added H2 defaults to preserve those older fixtures.
- Full Java reactor PASS after repair: `mvn -q -f java-services/pom.xml test`; surefire summary from `java-services/restful-api/target/surefire-reports`: 25 reports / 171 tests / 0 failures / 0 errors / 0 skipped.
- Production academic catalog candidate SQL-write grep PASS: no SQL write/DDL markers in the changed academic read repository, service or controller.
- `git diff --check` PASS with only Git Windows CRLF working-copy warnings.
- Broad staged secret scan hit JWT test-helper terminology only; high-confidence secret marker scan PASS.
- Source commit complete locally: `e68faa7 feat(java): add academic catalog reads`.

## Phase 52 evidence

- Implemented feature-default-off Java `GET /api/v1/faculties`, `GET /api/v1/faculties/{id}`, `GET /api/v1/departments` and `GET /api/v1/departments/{id}` read routes under the existing `migration.academic-read.enabled` / `ACADEMIC_READ_ENABLED:false` academic catalog flag.
- Preserved selected legacy behavior for admin/super-admin list boundaries, authenticated detail reads, list envelopes, faculty nested department summaries, department nested faculty and lecturer summaries, not-found/default-off behavior, invalid query failures and localization defaults.
- Added H2 persistence/default-off tests for faculty envelope/order/hydration, faculty detail departments, department envelope/faculty hydration, department detail lecturers, student-list forbidden cases, missing details and default-off route absence.
- Focused gate PASS after one test-authority repair: `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`.
- Test-authority repair: MockMvc `jwt().jwt(...)` claims did not supply method-security authorities, so the new tests now use `SimpleGrantedAuthority("ROLE_ADMIN")` / `ROLE_STUDENT`, matching existing persistence-test convention. Production authorization was not weakened.
- Full Java reactor PASS: `mvn -q -f java-services/pom.xml test`; surefire summary from `java-services/restful-api/target/surefire-reports`: 25 reports / 173 tests / 0 failures / 0 errors / 0 skipped.
- Production academic catalog candidate SQL-write grep PASS: no SQL write/DDL markers in the changed production academic read repository, service, controller or localizer.
- `git diff --check` PASS with only Git Windows CRLF working-copy warnings.
- High-confidence secret marker scan PASS for the changed academic production and test surface.
- Source commit complete locally: `3cc9799 feat(java): add academic faculty department reads`.

## Phase 53 evidence

- Implemented feature-default-off Java `GET /api/v1/curricula` and `GET /api/v1/curricula/{id}` read routes under the existing `migration.academic-read.enabled` / `ACADEMIC_READ_ENABLED:false` academic catalog flag.
- Preserved selected legacy behavior for authenticated list/detail reads, list envelopes, curriculum name ordering, department hydration, detail-only `CurriculumCourse` mapping hydration, not-found/default-off behavior, invalid query failures and curriculum localization defaults.
- Added H2 persistence/default-off tests for curriculum list envelope/order/hydration, list path without curriculum-course hydration, curriculum detail courses, missing detail and default-off route absence.
- Focused gate PASS: `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`.
- Full Java reactor PASS: `mvn -q -f java-services/pom.xml test`; surefire summary from `java-services/restful-api/target/surefire-reports`: 25 reports / 174 tests / 0 failures / 0 errors / 0 skipped.
- Production academic catalog candidate SQL-write grep PASS: no SQL write/DDL markers in the changed production academic read repository, service, controller or localizer.
- `git diff --check` PASS with only Git Windows CRLF working-copy warnings.
- High-confidence staged secret marker scan PASS. The broad staged scan matched JWT test `token` terminology, so it was recorded as a false positive without printing matched content.
- Source commit complete locally: `27d34f7 feat(java): add academic curriculum reads` (`27d34f736da87f439a7ac600f839a0819402d109`).

## Execution rulings

- User-provided turn instructions add plan-lock discipline and restrict unnecessary thread/subagent spawning. This small read-only backend slice stays controller-owned; Advisor/Kongming/Wukong remain deferred to exact-head cutover/high-risk gates.
- Legacy-compatible scope for this slice includes `GET /waitlist`, `GET /waitlist/my`, `GET /waitlist/section/:sectionId`, and `GET /waitlist/:id`. `POST /waitlist/:id/promote` and `DELETE /waitlist/:id` are explicitly non-goals.
- Legacy-compatible scope for Phase 49 includes attendance read routes only. `POST /attendance`, `POST /attendance/bulk`, `POST /attendance/section/:sectionId/mark`, `PUT /attendance/:id` and `DELETE /attendance/:id` are explicitly non-goals.
- Legacy-compatible scope for Phase 50 includes selected section read routes only. `POST /sections`, `PUT /sections/:id`, `DELETE /sections/:id`, `PUT /sections/:id/grades` and `POST /sections/:id/grades/publish` are explicitly non-goals.
- Legacy-compatible scope for Phase 51 includes selected academic-year/classroom read routes only. `POST /academic-years`, `PUT /academic-years/:id`, `DELETE /academic-years/:id`, `POST /classrooms`, `PUT /classrooms/:id` and `DELETE /classrooms/:id` are explicitly non-goals. Classroom `equipment String[]` is deferred until a PostgreSQL parity gate.
- Legacy-compatible scope for Phase 52 includes selected faculty/department read routes only. `POST /faculties`, `PUT /faculties/:id`, `DELETE /faculties/:id`, `POST /departments`, `PUT /departments/:id`, `DELETE /departments/:id` and curricula routes are explicitly non-goals until a later scoped parity slice.
- Legacy-compatible scope for Phase 53 includes selected curriculum read routes only. `POST /curricula`, `PUT /curricula/:id`, `DELETE /curricula/:id`, student/curriculum writer ownership and richer nested course-object joins are explicitly non-goals until a later scoped parity slice or PostgreSQL parity gate.

## Backend foundation capability preflight — 2026-08-21

- Exact source snapshot at preflight start: `67927c79cd3a0832c87936969dede21a00a0e1c6`, matching
  `origin/feature/java-thesis-platform` before this docs-only record.
- `psql`, `pg_dump`, `pg_restore` and `postgres` 18.0.4 are installed, but the
  Docker daemon is unavailable on the read-only probe (`dockerDesktopLinuxEngine`
  named pipe missing).
- At preflight start, no approved disposable PostgreSQL restore/fixture,
  isolated target identity or rehearsal credentials were present in the known D:
  paths or environment. The active CampusCore stack was not contacted or
  mutated.
- Later in the same turn, a disposable PostgreSQL cluster was created under
  `.tmp/pg-phase53/cluster` and verified on loopback port `56433`.
- Verdict: the original `BLOCKED_CAPABILITY` preflight was only a transient
  start-state; the disposable target requirement is now satisfied for a
  read-only rehearsal, but the full PostgreSQL differential/rollback gate is
  still incomplete.

## Independent Wukong gate — 2026-08-21

- Verdict: `FALSIFIED` for reusing the prior Phase 11 engagement rehearsal as
  current exact-head parity evidence or as a substitute for the academic
  curriculum gate.
- The prior probe identities (`f4188d7...` / Java source `b6c6e0d...`) differ
  from current HEAD, and the rehearsal blob has drifted (`229 added / 105
  deleted` between the recorded probe and current source).
- The current rehearsal corpus targets announcements and has no curriculum
  cases; Phase 53 curriculum PostgreSQL parity and exact-head review remain
  open. Phase 11's no-go still requires an approved D: disposable target,
  scrubbed backup or deterministic fixture with checksum, read-only role and
  migrations/DDL/seed disabled.
- Handoff: keep the backend foundation gate `BLOCKED_CAPABILITY` until those
  artifacts and authority are supplied, then create a fresh academic
  curriculum differential plus rollback rehearsal and fresh independent review.

## Backend foundation disposable PostgreSQL rehearsal — 2026-08-21

- Disposable target: `.tmp/pg-phase53/cluster` on `127.0.0.1:56433`, started
  with `initdb` and `pg_ctl`, isolated from the active CampusCore stack.
- Verified against that target:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest' '-DforkCount=0' test`
  with `SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:56433/campuscore?currentSchema=academic`
  and `SPRING_DATASOURCE_USERNAME=postgres`.
- Verified against that target:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`
  with the same disposable PostgreSQL URL override.
- Limitation: this proves a real PostgreSQL read rehearsal for the academic
  curricula candidate, but not the full legacy-to-Java differential or rollback
  rehearsal yet.

## Backend foundation thesis FK rehearsal — 2026-08-21

- Local source head: `fcfe324151bba30879bdc3893a0b19a207218be3`
  (`test(java): add thesis restore read-only smoke`).
- Disposable target reused: `.tmp/pg-phase53/cluster` on `127.0.0.1:56433`,
  with `currentSchema=thesis` and the exact `postgres` role.
- Verified:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.thesis.ThesisTopicPersistenceTest' '-DforkCount=0' test`
  on PostgreSQL 18.4, including Flyway creation of the thesis schema and the
  thesis topic/group/council read contract.
- Limitation: this is a live PostgreSQL rehearsal for the thesis persistence
  slice, not the final route-canary/rollback gate.

## Backend foundation thesis restore smoke — 2026-08-21

- Local source head: `fcfe324151bba30879bdc3893a0b19a207218be3`
  (`test(java): add thesis restore read-only smoke`).
- Disposable snapshot artifacts:
  - backup: `D:\Student_Management-recovery\pg-thesis-20260821\thesis-schema.dump`
  - checksum: `SHA256 5D7CF84815D85A9CAC7130426D5FFC87D215121EEE53943A24E7E4CD84B9FEB3`
  - restored database: `campuscore_ro` on `127.0.0.1:55432`
- Read-only role audit:
  - role: `campuscore_ro_reader`
  - `current_setting('default_transaction_read_only') = on`
  - `statement_timeout = 5s`
  - write attempt `INSERT INTO thesis.thesis_topic ...` failed with
    `cannot execute INSERT in a read-only transaction`
- Verified:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.thesis.ThesisReadOnlyRestoreSmokeTest' '-DforkCount=0' test`
  with `THESIS_RESTORE_SMOKE=true`, `THESIS_READ_ENABLED=true`,
  `FLYWAY_ENABLED=false`, valid JWT secrets, and the restored PostgreSQL
  target. The smoke read `/api/v1/thesis/topics`, `/api/v1/thesis/groups` and
  `/api/v1/thesis/councils` successfully against the restored snapshot.
- Limitation: this proves the disposable restore/read-only smoke for the thesis
  candidate, not the legacy-versus-Java differential or rollback rehearsal.

## Backend foundation exact-head review attempt — 2026-08-21

- Reviewed source snapshot: `fcfe324151bba30879bdc3893a0b19a207218be3`
  (`test(java): add thesis restore read-only smoke`).
- Advisor verdict: `insufficient`. The restore smoke is useful evidence, but it
  does not satisfy the backend foundation gate because the Phase 09/11-style
  legacy-versus-Java differential, route rollback rehearsal and full fresh
  review gate are still open.
- Kongming verdict: `NOT_RUN`; the bounded sidecar timed out before returning a
  verdict.
- Wukong verdict: `NOT_RUN`; the sidecar failed before review because the
  runtime could not refresh/connect to its model/transport endpoint.
- Ruling: FE Stitch implementation remains gated off by the backend foundation
  criteria. Continue with the smallest read-only differential/rollback slice
  rather than expanding public route ownership or client traffic.

## Current canonical Java monolith gate — 2026-08-21

- Verified checkout before this ledger update:
  `d92ce53e884adcd83b5dd479aebeb584d9a83946`
  (`feat(java): add academic internal context reads`).
- Canonical parent module check: `java-services/pom.xml` lists only
  `<module>restful-api</module>` for the modular-monolith reactor.
- Verified:
  `mvn -q -f java-services/pom.xml clean test` exited 0.
- Canonical Surefire summary from
  `java-services/restful-api/target/surefire-reports`: 26 reports / 177 tests /
  0 failures / 0 errors / 1 skipped. The skipped test is the opt-in
  `ThesisReadOnlyRestoreSmokeTest` when `THESIS_RESTORE_SMOKE` is not set.
- Limitation: stale XML reports under legacy `auth-service` and
  `thesis-service` target directories are not part of the current parent
  reactor and are not counted as Java monolith evidence.

## Phase 55 evidence

- Implemented internal-only Java academic context reads for curricula,
  departments and student enrollments under
  `migration.academic-context.enabled=true`, with `X-Service-Token`
  compatibility enforcement and the default internal token
  `academic-internal-token-12345`.
- Permitted `/api/v1/internal/**` at the Spring Security layer so the
  controllers can own the 403 contract for missing or invalid internal tokens.
- Pinned the thesis persistence test to H2 so it no longer drifts with external
  datasource overrides during the monolith reactor.
- Focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest,io.campuscore.restfulapi.thesis.ThesisTopicPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`
- Full canonical Java monolith gate PASS:
  `mvn -q -f java-services/pom.xml clean test`
- Production SQL-write grep PASS for the changed security and academic-context
  production files.
- High-confidence secret-marker scan PASS for the changed production and test
  files.
- `git diff --check` PASS with only Git Windows LF-to-CRLF working-copy
  warnings on touched files.
- Source commit complete locally:
  `d92ce53e884adcd83b5dd479aebeb584d9a83946` (`feat(java): add academic
  internal context reads`).

## Thesis differential harness checkpoint — 2026-08-21

- Source snapshot before this checkpoint:
  `e41019d52f0e90c84e7d44dfec8a0772a3c49386`
  (`docs(plan): record canonical monolith gate`).
- Added `scripts/run-thesis-differential-rehearsal.mjs`, a dependency-free
  harness for the Phase 09 thesis read corpus. It compares private legacy and
  Java base URLs for status, normalized content type and stable JSON body hash,
  then probes a bounded legacy -> Java -> legacy route sequence without touching
  nginx or public frontend traffic.
- Harness self-test PASS:
  `node scripts/run-thesis-differential-rehearsal.mjs --self-test`.
- Live private rehearsal executed against the restored read-only PostgreSQL
  snapshot on `127.0.0.1:55432`:
  - legacy/pilot endpoint: `java-services/thesis-service` on
    `http://127.0.0.1:54111`
  - monolith candidate: `java-services/restful-api` on
    `http://127.0.0.1:54112` with `SPRING_PROFILES_ACTIVE=persistence`,
    `THESIS_READ_ENABLED=true` and `FLYWAY_ENABLED=false`
  - both used `SPRING_DATASOURCE_USERNAME=campuscore_ro_reader`
- Result: `FAIL`, with 6/8 corpus checks passing and 2/8 failing:
  - `unknown round topics`: status/content type matched, but the legacy
    endpoint returned `{statusCode,message,timestamp,path}` while the monolith
    returned the shared `{code,message,path,requestId,timestamp,fields}` error
    envelope.
  - `malformed round groups`: status/content type matched, but the same legacy
    versus monolith error-envelope shape difference remained.
- Route sequence evidence: `legacy-before`, `java-candidate`, and
  `legacy-after` all returned 200 with the same body hash for
  `GET /api/v1/thesis/rounds`.
- Ruling: this is useful differential evidence and a reusable gate, but it is
  not a pass. Do not weaken the oracle or claim backend foundation completion;
  decide whether the monolith should preserve the legacy thesis error envelope
  or record this as an intentional contract change before any cutover.

## Phase 54 academic admin student enrollment read — 2026-08-21

- Source commit:
  `99c9c1d6e5dbb2a22169ddd684dcd22cfb7d23fe`
  (`feat(java): add admin student enrollment reads`).
- Implemented feature-default-off Java
  `GET /api/v1/enrollments/student/{studentId}` under the existing
  `migration.academic-enrollment-read.enabled` gate.
- Preserved the selected legacy admin/super-admin role boundary, the list
  response shape already used by student self-enrollment reads, optional
  `semesterId` filtering, `enrolledAt DESC` ordering, default-off route absence
  and negative student-role/unexpected-query behavior.
- Focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`.
- Full canonical Java monolith gate PASS:
  `mvn -q -f java-services/pom.xml clean test`.
- Current canonical Surefire summary from
  `java-services/restful-api/target/surefire-reports`: 26 reports / 176 tests /
  0 failures / 0 errors / 1 skipped.
- Production controller SQL-write grep PASS and touched-file `git diff --check`
  PASS with only Git Windows LF-to-CRLF working-copy warnings.
- Limitation: source/H2 evidence only until PostgreSQL read parity, route
  canary and rollback rehearsal are observed.

## Deferred findings

- PostgreSQL restore parity, route canary, rollback rehearsal, live FE authenticated parity, mobile runtime parity, and Stitch live visual audit remain open gates before any cutover or production-ready claim.

## Next resume point

- Resume when an approved disposable PostgreSQL target, scrubbed backup/fixture
  and rehearsal authority are supplied; then run the Phase 09/11-style
  read-only differential and rollback rehearsal at a fresh exact HEAD. Until
  that capability exists, do not point FE traffic or public routes at Java. FE
  web/mobile remains a required later phase with Stitch, responsive, auth/runtime
  and functional proof. Preserve untracked `.agents/`, `.codex/` and `.tmp/`
  unless the user explicitly authorizes a safe cleanup target.

## Thesis differential repair and rehearsal — 2026-08-21

- Repaired `ThesisCompatibilityExceptionHandler` and the thesis persistence
  assertions so unknown-round and malformed-UUID reads use the legacy
  `{statusCode,message,timestamp,path}` envelope. The production handler is
  scoped to the thesis read controllers; shared API error handling and public
  route ownership were not changed.
- The first live rerun exposed a nondeterministic harness issue: the legacy and
  Java error envelopes had identical stable fields but request-time timestamps.
  The comparator was repaired to validate ISO-8601 UTC timestamps and normalize
  only that volatile error field before hashing. Business/data timestamps remain
  strict.
- Focused regression PASS on a new writable database in the isolated
  `56433` PostgreSQL cluster: `ThesisTopicPersistenceTest`, 12/12, 0 failures,
  0 errors. The restored `55432` database was intentionally not used for this
  mutating test because its read-only role correctly rejects cleanup writes.
- Harness syntax/self-test PASS. Live private thesis differential PASS: 8/8
  corpus comparisons, and legacy → Java → legacy route sequence preserved the
  legacy rounds body hash
  `b24dc28a551161788e2502437643dfb8324b710ee0ad6c8b270e162c0c4e4194`.
- Read-only audit PASS on `campuscore_ro_reader`: transaction read-only on,
  `statement_timeout=5s`, thesis `USAGE` and `SELECT` granted, `INSERT` and
  database `CREATE` denied. Both Java runtimes and disposable PostgreSQL
  servers were stopped after the rehearsal.
- Full evidence is in
  `plans/20260819-restful-api-consolidation/reports/thesis-differential-20260821.md`.
- This does not clear the larger backend foundation gate: authenticated
  client parity, full domain differential coverage, canary, writer handoff,
  rollback observation, and fresh exact-head Advisor/Kongming/Wukong review
  remain open.

## Exact-head independent review — commit 255a25a — 2026-08-21

- Advisor: `ACCEPT_CHECKPOINT` only for the bounded private thesis-read
  rehearsal; FE convergence, full backend convergence and route cutover remain
  blocked. Advisor also flagged that the general ledger snapshot identity was
  stale and needed correction.
- Kongming: `PASS_CHECKPOINT`, with `SOURCE_PUSH: HOLD` and `CUTOVER: HOLD`.
  The sequence in the harness is a base-URL probe, not an actual edge route
  switch/abort/recovery rehearsal. The 8-case corpus and privilege summary are
  narrower than the Phase 09 contract, and the deterministic self-test did not
  exercise timestamp normalization.
- Wukong: `FALSIFIED`, gate `REPAIR_THEN_RETEST`, high confidence/E3. Minimal
  counterexample: `2026-02-30T00:00:00Z` passed the previous
  `endsWith('Z') && !Number.isNaN(Date.parse(value))` predicate and was
  normalized out of the comparable body. This violates the invariant that a
  timestamp must be validated before normalization.
- Immediate repair authorized inside this phase: replace the permissive parser
  with strict UTC ISO/calendar validation; add deterministic positive,
  impossible-date, and non-error-business-timestamp self-tests; rerun the
  focused persistence test, harness self-test, live differential and a fresh
  exact-head independent review. Full corpus/privilege audit and actual route
  switch remain later gates.

## Review-repair verification — 2026-08-21

- Strict timestamp repair verified with `node --check` and
  `node scripts/run-thesis-differential-rehearsal.mjs --self-test`: `PASS`.
  The self-test covers different valid UTC timestamps, rejects the impossible
  calendar date `2026-02-30T10:20:30Z`, and keeps non-error business timestamp
  differences strict.
- Focused `ThesisTopicPersistenceTest`: `PASS`, 12/12, 0 failures, 0 errors.
  The run resolves H2 through `application-test.yml`; it is recorded as
  H2/Spring regression evidence, not PostgreSQL proof.
- Fresh live private differential against the restored PostgreSQL snapshot:
  `PASS`, 8/8, with legacy → Java → legacy body hash unchanged at
  `b24dc28a551161788e2502437643dfb8324b710ee0ad6c8b270e162c0c4e4194`.
- The initial full privilege audit exposed `database_temp=true`. On the
  disposable restore only, the `campuscore` database owner revoked
  `TEMPORARY` from `PUBLIC`; the follow-up reader audit shows
  `database_temp=false`, `default_transaction_read_only=on`, 5s statement and
  1s lock timeouts, no superuser/CREATEDB/CREATEROLE, thesis `USAGE=true`,
  schema `CREATE=false`, database `CREATE=false`, and all 10 thesis tables
  `SELECT=true` with `INSERT/UPDATE/DELETE=false`. Runtime targets were stopped
  after the audit.
- This repair is ready for a new exact-head review wave. Actual route-switch
  rollback, wider corpus (anonymous/unknown IDs/empty lists/headers/latency),
  full client parity, and FE/mobile gates remain outside this checkpoint.

## Exact-head provenance repair run — 2026-08-21

- Review target before this documentation correction:
  `683829a661f517c7469a4ee7cc85a22aeaeb2a08`, tree
  `4c1386178142b6cba4eb6085659e4f9d29bd72cb`, differential harness blob
  `b4e1205fa9448865612a8066c84279aff5d7714d`.
- Exact-head runtime verification execution:
  `e18d7d91-fc49-4c22-90f4-1ef0f80f1f7e`, generated at
  `2026-08-21T07:33:24.840Z`. The two freshly packaged Java runtimes used the
  restored `campuscore_ro` PostgreSQL snapshot on `127.0.0.1:55432` through
  `campuscore_ro_reader`; `FLYWAY_ENABLED=false`, private ports `54111` and
  `54112`, and no shared CampusCore stack or public traffic.
- `node --check scripts/run-thesis-differential-rehearsal.mjs` and the full
  `--self-test` passed. The live differential returned `PASS`, 8/8 comparisons,
  with route sequence `legacy-before -> java-candidate -> legacy-after` all
  `200` and the unchanged rounds body hash
  `b24dc28a551161788e2502437643dfb8324b710ee0ad6c8b270e162c0c4e4194`.
- The captured live result included the exact eight comparison hashes recorded
  in `reports/thesis-differential-20260821.md`; all runtimes and both isolated
  PostgreSQL servers were stopped after collection.
- Exact-head review results on that source snapshot: Advisor
  `ACCEPT_CHECKPOINT` and Kongming `PASS_CHECKPOINT` for the bounded thesis-read
  scope; Wukong `INCONCLUSIVE` on the comparator itself but `BLOCK` on the
  historical report provenance because the earlier live result was not bound
  to an exact source identity. The current correction supplies that identity;
  refresh Advisor/Kongming/Wukong after this report commit. `SOURCE_PUSH` and
  `CUTOVER` remain `HOLD`.
