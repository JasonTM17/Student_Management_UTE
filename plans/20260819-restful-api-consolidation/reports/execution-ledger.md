# Execution Ledger
Active plan: plans/20260819-restful-api-consolidation/plan.md

## Current execution state

- Current branch snapshot before this docs update:
  `main` at `3e1bfbbae19fedab6229e18200ed0389c147f613`
  (`fix(frontend): refresh sharp security baseline`), with `origin/main`
  matching and `feature/java-thesis-platform` still carrying the separate
  docs-only phase-54 update.
- Repo state before this docs update: tracked tree is clean; the user-owned
  untracked `.agents/`, `.codex/` and `.tmp/` remain unstaged.
- Source gates refreshed on this exact head: `npm test --prefix frontend`
  29/29, `npm run typecheck --prefix frontend`, `npm run lint --prefix
  frontend`, `npm test --prefix mobile` 6/6, and
  `mvn -q -f java-services/pom.xml test` 26 reports / 177 tests / 0 failures /
  0 errors / 1 skipped.
- Runtime browser gate assessment: Docker daemon is unreachable
  (`dockerDesktopLinuxEngine` pipe missing) and `kubectl` has no current
  context, so exact-source browser/Playwright visual capture remains blocked
  until a local edge/runtime is restored.
- Branch integration ruling: continue using `main` as the integration branch
  for this turn; do not fast-forward or push any other branch unless the user
  explicitly changes the target.
- Disk snapshot before this docs update: C: ~13.64 GiB free, D: ~35.31 GiB
  free. Disposable PostgreSQL clusters observed under `.tmp` on ports `56452`
  and `56453` were stopped by exact data directory; no broad deletion was run.

## Completed evidence carried forward

- Phase 47 academic schedule read foundation is committed in `7dfda6a` and included in remote HEAD lineage through `57cde78`.
- Last known post-merge evidence from the previous checkpoint: focused Java schedule gate passed, full Java reactor passed with 22 reports / 150 tests / 0 failures / 0 errors / 0 skipped, frontend `npm test --prefix frontend` passed 29/29, and doc/architecture/diff hygiene passed. This ledger treats that as historical evidence only; new claims require fresh commands.

## Current step

- Current step after this docs update: record the refreshed exact-head source
  checkpoint, preserve the runtime blocker evidence, and keep the active plan
  intact until a local edge/runtime is available again. Route ownership,
  writer ownership, FE traffic and production cutover remain on HOLD.

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
- Focused PostgreSQL rehearsal PASS on 2026-08-21 against disposable PostgreSQL 18.4 target `127.0.0.1:56452`, database `campuscore_academic_read_20260821_182123`, `currentSchema=academic`: `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest' '-DforkCount=0' test`. Surefire summary: 8 tests / 0 failures / 0 errors / 0 skipped. This is a focused fixture-based PostgreSQL syntax/type rehearsal for the academic catalog/curriculum test suite, not a restored legacy dataset differential.
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

## Phase 56 evidence

- Rehearsed the existing academic enrollment/grade read foundation against the
  disposable PostgreSQL target on `127.0.0.1:56433` with
  `currentSchema=academic` at exact HEAD `3dbfd19f841dc3e9e98a161c9d03161974c2bce6`.
- Focused PostgreSQL rehearsal PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest' '-DforkCount=0' test`
- Surefire summary from
  `java-services/restful-api/target/surefire-reports/TEST-io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest.xml`:
  8 tests / 0 failures / 0 errors / 0 skipped.
- This was rerun on the docs-only branch tip so the parity evidence matches the
  exact current head, not just the prior code-affecting checkpoint.
- This adds real PostgreSQL parity evidence for the academic enrollment/grade
  read foundation but does not on its own clear the broader backend foundation
  gate, route canary, rollback observation or independent review.

## Phase 57 evidence

- Source repair commit:
  `feb6213e20fb14c67f2345007ed9485c0571777d` (`fix(java): support people
  postgres status reads`).
- Confirmed root cause: PostgreSQL rejected the previous nullable optional
  student-status predicate when `status` was absent, producing a
  `BadSqlGrammarException` and 500 for `GET /api/v1/students`. H2 did not
  expose this incompatibility.
- Repaired `PeopleReadRepository` so the `status` `WHERE` clause is emitted
  only when a filter value exists. The repair keeps the people read API,
  response shape, feature flag, route ownership and writer boundaries unchanged.
- H2 focused regression PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.people.PeopleReadPersistenceTest' '-DforkCount=0' test`
- PostgreSQL focused rehearsal PASS against the disposable PostgreSQL 18.4
  target on `127.0.0.1:56434` with `currentSchema=people`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.people.PeopleReadPersistenceTest' '-DforkCount=0' test`
- Surefire summary from
  `java-services/restful-api/target/surefire-reports/TEST-io.campuscore.restfulapi.people.PeopleReadPersistenceTest.xml`:
  3 tests / 0 failures / 0 errors / 0 skipped.
- Disposable cluster note: the older `56433` listener stopped answering
  `pg_isready`; it was stopped through `pg_ctl` against the exact
  `.tmp/pg-phase53/cluster` data directory, restarted on `127.0.0.1:56434`,
  used for the people read rehearsal, then stopped again.
- Production SQL-write grep PASS for the repaired people repository.
- Staged secret marker scan PASS for the repaired people repository.
- `git diff --check` PASS on the repaired repository with only Git Windows
  LF-to-CRLF working-copy warnings.
- Open HOLD boundaries: public people route ownership, people writer
  ownership, restore parity, route canary, rollback observation, independent
  exact-head review and FE/mobile live wiring remain open.

## Finance PostgreSQL parity repair checkpoint — 2026-08-21

- Source repair commit:
  `3327f8318bc9aa775a4e48185b8f74b54ab3215a`
  (`fix(java): support finance postgres optional filters`).
- Confirmed root cause: the finance invoice/payment reads used nullable
  optional filters in the same `(:param IS NULL OR ...)` shape that PostgreSQL
  rejects when the filter is absent, while H2 stayed permissive.
- Repair: finance read repository now emits dynamic `WHERE` clauses only when
  filters exist, matching the existing repository pattern used by other read
  slices, while keeping the public API, envelopes and role boundaries
  unchanged. The regression test now also covers admin payment list reads with
  no optional filter.
- H2 focused regression PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.finance.FinanceReadPersistenceTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`
- PostgreSQL focused rehearsal PASS against the disposable target on
  `127.0.0.1:56448` with `currentSchema=finance` and
  `server_encoding=UTF8`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.finance.FinanceReadPersistenceTest' '-DforkCount=0' test`
- Full canonical Java monolith gate PASS after the repair:
  `mvn -q -f java-services/pom.xml clean test`; canonical surefire summary
  `26 reports / 177 tests / 0 failures / 0 errors / 1 skipped`.
- Hygiene PASS: touched-file `git diff --check` passed with only Windows
  LF-to-CRLF warnings, production finance repository SQL-write marker scan had
  no matches, and staged sensitive-value diff scan passed before commit.
- Open HOLD: no finance route canary, public writer ownership, restored
  legacy-data parity, payment reconciliation, rollback observation or
  production-cutover claim is being made.

## Analytics PostgreSQL focused rehearsal — 2026-08-21

- Working-tree source snapshot: `27eb9a58ddf6f1c9be1610afa89c38376553f85e`
  plus local finance/docs modifications. No analytics source files were
  changed in this checkpoint.
- Disposable target: `.tmp/pg-phase53/cluster` on `127.0.0.1:56438`, started
  and stopped with `pg_ctl`; database `campuscore`, `currentSchema=public`,
  user `postgres`.
- Verified:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest' '-DforkCount=0' test`
- Surefire summary:
  `17 tests / 0 failures / 0 errors / 0 skipped`.
- Limitation: this proves the selected analytics read tests run against real
  PostgreSQL 18.4 syntax/types, but it is not a restored legacy dataset,
  legacy-versus-Java differential, route canary, rollback observation or
  public traffic handoff.

## Notification read PostgreSQL focused rehearsal — 2026-08-21

- Working-tree source snapshot: `27eb9a58ddf6f1c9be1610afa89c38376553f85e`
  plus local finance/docs modifications. No notification source files were
  changed in this checkpoint.
- Disposable target: `.tmp/pg-phase53/cluster` on `127.0.0.1:56439`, started
  and stopped with `pg_ctl`; database `campuscore`,
  `currentSchema=notifications`, user `postgres`.
- Verified:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.notification.NotificationReadPersistenceTest' '-DforkCount=0' test`
- Surefire summary:
  `6 tests / 0 failures / 0 errors / 0 skipped`.
- Limitation: this proves the selected notification read tests run against real
  PostgreSQL 18.4 syntax/types, but it is not a restored legacy notification
  dataset, read-only role permission proof, Node-versus-Java differential,
  route canary, rollback observation or public traffic handoff.

## Engagement read PostgreSQL focused rehearsal — 2026-08-21

- Working-tree source snapshot: `27eb9a58ddf6f1c9be1610afa89c38376553f85e`
  plus local finance/docs modifications. No engagement source files were
  changed in this checkpoint.
- Disposable target: `.tmp/pg-phase53/cluster` on `127.0.0.1:56440`, started
  and stopped with `pg_ctl`; database `campuscore`,
  `currentSchema=engagement`, user `postgres`.
- Verified:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.engagement.AnnouncementReadPersistenceTest,io.campuscore.restfulapi.engagement.SupportTicketReadPersistenceTest' '-DforkCount=0' test`
- Surefire summary:
  `AnnouncementReadPersistenceTest`: 6 tests / 0 failures / 0 errors /
  0 skipped; `SupportTicketReadPersistenceTest`: 4 tests / 0 failures /
  0 errors / 0 skipped.
- Limitation: this proves the selected engagement read tests run against real
  PostgreSQL 18.4 syntax/types, but it is not the Phase 11 private
  legacy-versus-Java differential, restored read-only corpus, route canary,
  rollback observation or public traffic handoff.

## Academic schedule PostgreSQL focused rehearsal — 2026-08-21

- Working-tree source snapshot: `27eb9a58ddf6f1c9be1610afa89c38376553f85e`
  plus local finance/docs modifications. No academic schedule source files were
  changed in this checkpoint.
- An initial command attempt failed before exercising the app because the
  PowerShell JDBC URL string interpolated the database/query string
  incorrectly and PostgreSQL looked for database `=academic`; rerun used
  `${db}` to pin the URL.
- Disposable target for the passing run: `.tmp/pg-phase53/cluster` on
  `127.0.0.1:56442`, started and stopped with `pg_ctl`; fresh database
  `campuscore_academic_schedule_56442`, `currentSchema=academic`, user
  `postgres`.
- Verified:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicScheduleReadPersistenceTest' '-DforkCount=0' test`
- Surefire summary:
  `3 tests / 0 failures / 0 errors / 0 skipped`.
- Limitation: this proves the selected academic schedule read tests run against
  real PostgreSQL 18.4 syntax/types, but it is not a restored legacy academic
  dataset, route canary, rollback observation or public traffic handoff.

## Academic waitlist PostgreSQL fixture repair and focused rehearsal — 2026-08-21

- Working-tree source snapshot: `27eb9a58ddf6f1c9be1610afa89c38376553f85e`
  plus local finance/docs modifications and the waitlist test-fixture repair.
- Failing PostgreSQL reproduction: the first real PostgreSQL run of
  `AcademicWaitlistReadPersistenceTest` reached PostgreSQL 18.4 and failed
  before production reads because the test fixture bound `java.time.Instant`
  values directly into timestamp columns; pgjdbc reported it could not infer
  the SQL type for `java.time.Instant`.
- Repair: `AcademicWaitlistReadPersistenceTest` now converts timestamp fixture
  values with `Timestamp.from(...)`. Production waitlist code and public API
  behavior were not changed.
- H2 focused regression PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicWaitlistReadPersistenceTest' '-DforkCount=0' test`
- PostgreSQL focused rehearsal PASS against the disposable target on
  `127.0.0.1:56444`, fresh database `campuscore_academic_waitlist_56444`,
  `currentSchema=academic`, user `postgres`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicWaitlistReadPersistenceTest' '-DforkCount=0' test`
- Surefire summary:
  `4 tests / 0 failures / 0 errors / 0 skipped`.
- Full canonical Java monolith gate PASS after the waitlist fixture repair:
  `mvn -q -f java-services/pom.xml clean test`; canonical surefire summary
  `26 reports / 177 tests / 0 failures / 0 errors / 1 skipped`.
- Limitation: this proves the selected academic waitlist read tests run against
  real PostgreSQL 18.4 syntax/types, but it is not a restored legacy academic
  dataset, route canary, rollback observation or public traffic handoff.

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
- Focused PostgreSQL rehearsal PASS on 2026-08-21 against disposable
  PostgreSQL 18.4 target `127.0.0.1:56454`, database
  `campuscore_academic_enrollment_20260821_183305`, `currentSchema=academic`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest' '-DforkCount=0' test`.
  Surefire summary:
  8 tests / 0 failures / 0 errors / 0 skipped. This is a focused fixture-based
  PostgreSQL syntax/type rehearsal for the academic enrollment/admin shortcut
  test suite, not a restored legacy dataset differential.
- Production controller SQL-write grep PASS and touched-file `git diff --check`
  PASS with only Git Windows LF-to-CRLF working-copy warnings.
- Limitation: the enrollment admin shortcut now has a focused PostgreSQL
  rehearsal, but restored legacy-data parity, route canary and rollback
  rehearsal are still open.

## Deferred findings

- PostgreSQL restored-data parity, route canary, rollback rehearsal, live FE
  authenticated parity, mobile runtime parity, and Stitch live visual audit
  remain open gates before any cutover or production-ready claim.
- Finance PostgreSQL optional-filter syntax parity is committed, but finance
  route ownership, writes, checkout/payment provider orchestration,
  reconciliation and rollback remain open.

## Next resume point

- Main-branch integration for the finance repair is complete. Next safe work is
  the next bounded backend parity slice or the accepted FE Stitch phase once
  the backend foundation/canary/rollback/review gates allow client wiring.
  Until then, do not point FE traffic or public routes at Java. FE web/mobile
  remains a required later phase with Stitch, responsive, auth/runtime and
  functional proof. Preserve untracked `.agents/`, `.codex/` and `.tmp` unless
  the user explicitly authorizes a safe cleanup target.

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

## Current feature-branch backend gate refresh — 2026-08-21

- Verified exact head: `4a5b1026d15b246db1e36d6a7260106620fcd2fb`
  (`docs(plan): record phase 54 postgres rehearsal`) on
  `origin/feature/java-thesis-platform`.
- Canonical Java reactor refresh:
  `mvn -q -f java-services/pom.xml clean test`
  `PASS`, with surefire totals from `java-services/restful-api/target/surefire-reports`:
  177 tests / 0 failures / 0 errors / 1 skipped.
- Rollback harness refresh:
  `node scripts/run-thesis-differential-rehearsal.mjs --self-test`
  `PASS`, with the legacy/java/legacy route sequence and normalization checks
  still green on the current source tree.
- Read-only review refresh on the same exact head:
  Kongming `HOLD`, Wukong `NOT_FALSIFIED`.
- Gate conclusion: backend foundation remains `HOLD` because the fresh
  review still lacks a route-switch/cutover-quality exact-head pass that would
  unlock client staging; FE Stitch remains deferred until that gate moves.
