# Phase 50 — Academic section read candidate

## Outcome

Add a feature-default-off Java RESTful monolith candidate for selected legacy
academic section read routes while preserving section and grade mutation
ownership in the Nest academic service.

## Scope

- `GET /api/v1/sections`
- `GET /api/v1/sections/my/schedule`
- `GET /api/v1/sections/my/grading`
- `GET /api/v1/sections/{id}`
- `GET /api/v1/sections/{id}/grades`

Non-goals:

- `POST /sections`
- `PUT /sections/{id}`
- `DELETE /sections/{id}`
- `PUT /sections/{id}/grades`
- `POST /sections/{id}/grades/publish`
- PostgreSQL differential parity, route canary, rollback rehearsal, frontend
  runtime wiring and public route handoff

## Implementation notes

- Feature flag: `migration.academic-section-read.enabled`, defaulted by
  `ACADEMIC_SECTION_READ_ENABLED:false`.
- The candidate is active only under the `persistence` profile and is disabled
  by default in test and application config.
- Java code uses `NamedParameterJdbcTemplate` with SELECT-only SQL against the
  legacy `academic` schema.
- The selected response shapes preserve legacy section/course/department/
  semester/lecturer/classroom/schedule hydration, lecturer schedule flattening,
  lecturer grading counts and section grade enrollment rows.
- `GET /sections` and `GET /sections/{id}` keep the legacy authenticated-only
  boundary; lecturer schedule/grading keep the `LECTURER` claim boundary; grade
  reads keep `ADMIN`/`SUPER_ADMIN`/`LECTURER`.

## Verification

- Focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicSectionReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`
- Full Java monolith gate PASS:
  `mvn -q -f java-services/pom.xml clean test`
- Monolith surefire summary from `java-services/restful-api/target`: 25 reports /
  169 tests / 0 failures / 0 errors / 0 skipped.
- Production candidate SQL-write grep PASS: no `INSERT`, `UPDATE`, `DELETE`,
  `MERGE`, DDL or `execute(` calls in the new section read repository/service/
  controller files.
- Source commit: `38a4f71 feat(java): add academic section reads`.

## Open gates

- PostgreSQL read parity against the real legacy academic schema.
- Route canary and rollback rehearsal.
- Live web/mobile authenticated parity and Stitch route wiring.
- Independent exact-head Advisor/Kongming/Wukong review before public cutover.
