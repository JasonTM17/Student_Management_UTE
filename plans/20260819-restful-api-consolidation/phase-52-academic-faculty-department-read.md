# Phase 52 — Academic faculty and department catalog reads

## Outcome

Extend the existing feature-default-off Java academic catalog read candidate with
legacy-compatible faculty and department read routes inside the single
`java-services/restful-api` modular monolith.

## Exact source checkpoint

- Source commit: `3cc9799d38678306431f1fbc93c2e5d72bb1cfcf`
- Branch: `feature/java-thesis-platform`
- Feature gate: existing `migration.academic-read.enabled` /
  `ACADEMIC_READ_ENABLED:false`

## Implemented scope

- `GET /api/v1/faculties`
  - Admin and super-admin only, matching the selected Nest list boundary.
  - Preserves legacy `{ data, meta }` pagination envelope.
  - Orders by `Faculty.name ASC, Faculty.id ASC`.
  - Hydrates nested `departments` summaries.
- `GET /api/v1/faculties/{id}`
  - Authenticated detail read.
  - Returns nested department summaries and stable not-found envelope.
- `GET /api/v1/departments`
  - Admin and super-admin only, matching the selected Nest list boundary.
  - Preserves legacy `{ data, meta }` pagination envelope.
  - Orders by `Department.name ASC, Department.id ASC`.
  - Hydrates nested faculty summary.
- `GET /api/v1/departments/{id}`
  - Authenticated detail read.
  - Returns nested faculty and lecturer summaries.

The slice keeps JDBC `SELECT` ownership only. Faculty/department create, update,
delete, curricula reads, curriculum writes, course writes, lecturer/user joins
beyond the selected department summary, and public route ownership remain legacy
owned.

## Verification

- Focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`
- Full Java reactor PASS:
  `mvn -q -f java-services/pom.xml test`
- Surefire summary from `java-services/restful-api/target/surefire-reports`:
  25 reports / 173 tests / 0 failures / 0 errors / 0 skipped.
- Production academic catalog SQL-write grep PASS: no `INSERT`, `UPDATE`,
  `DELETE`, `MERGE`, `ALTER`, `DROP`, `CREATE` or `TRUNCATE` markers in the
  changed production academic read repository/service/controller/localizer.
- `git diff --check` PASS with only Git Windows LF-to-CRLF working-copy
  warnings.
- High-confidence secret marker scan PASS for the changed academic production
  and test surface.

## Notable repair

The first focused gate falsified a test-only assumption: `jwt().jwt(...)` claims
do not automatically provide `ROLE_ADMIN` authorities for method-security
checks. The test was repaired to use `SimpleGrantedAuthority("ROLE_ADMIN")`
and `ROLE_STUDENT`, matching the convention already used by other persistence
tests. Production authorization was not weakened.

## Open gates

- PostgreSQL read parity against an approved disposable restore.
- Live route canary and rollback rehearsal.
- Curricula catalog parity.
- Full FE web/mobile authenticated route convergence.
- Exact-head Advisor/Kongming/Wukong review before any public academic route
  handoff.
