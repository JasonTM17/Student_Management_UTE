# Phase 53 — Academic curriculum catalog reads

## Outcome

Extend the feature-default-off Java academic catalog read candidate with
legacy-compatible curriculum list and detail reads inside the single
`java-services/restful-api` modular monolith.

## Exact source checkpoint

- Source commit: `27d34f736da87f439a7ac600f839a0819402d109`
- Branch: `feature/java-thesis-platform`
- Feature gate: existing `migration.academic-read.enabled` /
  `ACADEMIC_READ_ENABLED:false`

## Implemented scope

- `GET /api/v1/curricula`
  - Authenticated list read, matching the selected Nest boundary with no
    admin-only role decorator.
  - Preserves legacy `{ data, meta }` pagination envelope.
  - Orders by `Curriculum.name ASC, Curriculum.id ASC`.
  - Hydrates the nested department summary.
  - Does not hydrate curriculum-course rows on the list path; legacy
    `findAll` includes `department` only.
- `GET /api/v1/curricula/{id}`
  - Authenticated detail read.
  - Returns nested department summary and `CurriculumCourse` mapping rows.
  - Preserves stable not-found behavior.

The slice keeps JDBC `SELECT` ownership only. Curriculum create, update,
delete, student/curriculum writer ownership, course writes, richer course-object
joins inside curriculum-course rows, public route ownership, restored
PostgreSQL read parity, canary and rollback remain open.

## Verification

- Focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`
- Full Java reactor PASS:
  `mvn -q -f java-services/pom.xml test`
- Surefire summary from `java-services/restful-api/target/surefire-reports`:
  25 reports / 174 tests / 0 failures / 0 errors / 0 skipped.
- Focused PostgreSQL rehearsal PASS on 2026-08-21 against a disposable
  PostgreSQL 18.4 target on `127.0.0.1:56452`, database
  `campuscore_academic_read_20260821_182123`, `currentSchema=academic`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicReadPersistenceTest' '-DforkCount=0' test`.
  Surefire summary:
  8 tests / 0 failures / 0 errors / 0 skipped. This is a focused fixture-based
  PostgreSQL syntax/type rehearsal for the academic catalog/curriculum test
  suite, not a restored legacy dataset differential.
- Production academic catalog SQL-write grep PASS: no `INSERT`, `UPDATE`,
  `DELETE`, `MERGE`, `ALTER`, `DROP`, `CREATE` or `TRUNCATE` markers in the
  changed production academic read repository/service/controller/localizer.
- `git diff --check` PASS with only Git Windows LF-to-CRLF working-copy
  warnings.
- High-confidence staged secret marker scan PASS. A broader staged scan matched
  JWT `token` terminology in tests, so it was treated as a false positive after
  the high-confidence scan passed without printing matched content.

## Current exact-head refresh — 2026-08-21

- Exact current HEAD: `4b5e7712f4a9fd309f3bc616e314c6c8db6ce8f0`
- `mvn -q -f java-services/pom.xml test`
  - `JAVA_HOME=C:\Program Files\Java\jdk-26.0.1`
  - result: PASS
  - Surefire summary: 27 reports / 178 tests / 0 failures / 0 errors / 1
    skipped
- This refresh preserves the historical source checkpoint at
  `27d34f736da87f439a7ac600f839a0819402d109` while tying the phase record to
  the current exact head.

## Open gates

- Restored PostgreSQL read parity against an approved legacy-data snapshot.
- Live route canary and rollback rehearsal.
- Curriculum-course response parity if the legacy API later requires nested
  course objects rather than only `CurriculumCourse` rows.
- Full FE web/mobile authenticated route convergence.
- Exact-head Advisor/Kongming/Wukong review before any public academic route
  handoff.
