# Execution Ledger
Active plan: plans/20260819-restful-api-consolidation/plan.md

## Current execution state

- Current branch snapshot: `feature/java-thesis-platform` at `e68faa7`, ahead of `origin/feature/java-thesis-platform`, on 2026-08-21 after the Phase 51 source commit and before the docs checkpoint commit/push.
- Repo state before implementation: only user-owned untracked `.agents/`, `.codex/` and `.tmp/`; preserve them and do not stage them.
- Disk snapshot before implementation: C: ~17.39 GiB free, D: ~39.12 GiB free.

## Completed evidence carried forward

- Phase 47 academic schedule read foundation is committed in `7dfda6a` and included in remote HEAD lineage through `57cde78`.
- Last known post-merge evidence from the previous checkpoint: focused Java schedule gate passed, full Java reactor passed with 22 reports / 150 tests / 0 failures / 0 errors / 0 skipped, frontend `npm test --prefix frontend` passed 29/29, and doc/architecture/diff hygiene passed. This ledger treats that as historical evidence only; new claims require fresh commands.

## Current step

- Phase 51 candidate: extend the existing feature-default-off Java RESTful monolith academic catalog read slice with academic-year and classroom routes.
- Exit criterion: Java exposes only selected academic-year and classroom read routes under `/api/v1` when `migration.academic-read.enabled=true`, preserves selected legacy list/detail envelopes plus academic-year semester hydration and classroom detail section summaries, leaves academic-year/classroom writes with legacy Nest, adds focused persistence/default-off tests, updates plan/docs, and passes focused/full Java gates.

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

## Execution rulings

- User-provided turn instructions add plan-lock discipline and restrict unnecessary thread/subagent spawning. This small read-only backend slice stays controller-owned; Advisor/Kongming/Wukong remain deferred to exact-head cutover/high-risk gates.
- Legacy-compatible scope for this slice includes `GET /waitlist`, `GET /waitlist/my`, `GET /waitlist/section/:sectionId`, and `GET /waitlist/:id`. `POST /waitlist/:id/promote` and `DELETE /waitlist/:id` are explicitly non-goals.
- Legacy-compatible scope for Phase 49 includes attendance read routes only. `POST /attendance`, `POST /attendance/bulk`, `POST /attendance/section/:sectionId/mark`, `PUT /attendance/:id` and `DELETE /attendance/:id` are explicitly non-goals.
- Legacy-compatible scope for Phase 50 includes selected section read routes only. `POST /sections`, `PUT /sections/:id`, `DELETE /sections/:id`, `PUT /sections/:id/grades` and `POST /sections/:id/grades/publish` are explicitly non-goals.
- Legacy-compatible scope for Phase 51 includes selected academic-year/classroom read routes only. `POST /academic-years`, `PUT /academic-years/:id`, `DELETE /academic-years/:id`, `POST /classrooms`, `PUT /classrooms/:id` and `DELETE /classrooms/:id` are explicitly non-goals. Classroom `equipment String[]` is deferred until a PostgreSQL parity gate.

## Deferred findings

- PostgreSQL restore parity, route canary, rollback rehearsal, live FE authenticated parity, mobile runtime parity, and Stitch live visual audit remain open gates before any cutover or production-ready claim.

## Next resume point

- Commit/push the Phase 51 docs checkpoint, then continue the backend foundation gate with PostgreSQL restore parity/canary preparation or the next missing low-risk academic read before FE Stitch runtime parity. Preserve untracked `.agents/`, `.codex/` and `.tmp/` unless the user explicitly authorizes a safe cleanup target.
