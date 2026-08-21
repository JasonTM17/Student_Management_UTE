# Execution Ledger
Active plan: plans/20260819-restful-api-consolidation/plan.md

## Current execution state

- Current branch snapshot: `feature/java-thesis-platform` at `fdd2601`, matching `origin/feature/java-thesis-platform`, on 2026-08-21 before Phase 49.
- Repo state before implementation: only user-owned untracked `.agents/`, `.codex/` and `.tmp/`; preserve them and do not stage them.
- Disk snapshot before implementation: C: ~16.95 GiB free, D: ~39.20 GiB free.

## Completed evidence carried forward

- Phase 47 academic schedule read foundation is committed in `7dfda6a` and included in remote HEAD lineage through `57cde78`.
- Last known post-merge evidence from the previous checkpoint: focused Java schedule gate passed, full Java reactor passed with 22 reports / 150 tests / 0 failures / 0 errors / 0 skipped, frontend `npm test --prefix frontend` passed 29/29, and doc/architecture/diff hygiene passed. This ledger treats that as historical evidence only; new claims require fresh commands.

## Current step

- Phase 49 candidate: add a feature-default-off Java RESTful monolith read slice for academic attendance routes.
- Exit criterion: Java exposes only read attendance routes under `/api/v1/attendance` when `migration.academic-attendance-read.enabled=true`, preserves selected legacy read shapes, filters, formulas and role/claim boundaries, leaves attendance mark/update/delete ownership with legacy Nest, adds focused persistence/default-off tests, updates plan/docs, and passes focused/full Java gates.

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

## Execution rulings

- User-provided turn instructions add plan-lock discipline and restrict unnecessary thread/subagent spawning. This small read-only backend slice stays controller-owned; Advisor/Kongming/Wukong remain deferred to exact-head cutover/high-risk gates.
- Legacy-compatible scope for this slice includes `GET /waitlist`, `GET /waitlist/my`, `GET /waitlist/section/:sectionId`, and `GET /waitlist/:id`. `POST /waitlist/:id/promote` and `DELETE /waitlist/:id` are explicitly non-goals.
- Legacy-compatible scope for Phase 49 includes attendance read routes only. `POST /attendance`, `POST /attendance/bulk`, `POST /attendance/section/:sectionId/mark`, `PUT /attendance/:id` and `DELETE /attendance/:id` are explicitly non-goals.

## Deferred findings

- PostgreSQL restore parity, route canary, rollback rehearsal, live FE authenticated parity, mobile runtime parity, and Stitch live visual audit remain open gates before any cutover or production-ready claim.

## Next resume point

- Commit and push Phase 49 if final staging and sensitive-material scan pass. Then continue the backend foundation gate with the next missing low-risk academic read or PostgreSQL restore parity/canary preparation before FE Stitch runtime parity.
