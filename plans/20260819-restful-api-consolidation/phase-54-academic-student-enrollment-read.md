# Phase 54 — Academic admin student enrollment read

## Outcome

Extend the feature-default-off Java academic enrollment read candidate with the
legacy admin-only student enrollment shortcut inside the single
`java-services/restful-api` modular monolith.

## Exact source checkpoint

- Source commit: `99c9c1d6e5dbb2a22169ddd684dcd22cfb7d23fe`
- Branch: `feature/java-thesis-platform`
- Feature gate: existing `migration.academic-enrollment-read.enabled` /
  `ACADEMIC_ENROLLMENT_READ_ENABLED:false`

## Implemented scope

- `GET /api/v1/enrollments/student/{studentId}`
  - Preserves the selected Nest boundary `GET /enrollments/student/:studentId`.
  - Keeps the legacy `ADMIN` / `SUPER_ADMIN` role boundary.
  - Returns the same enrollment list shape used by the student self-read path.
  - Supports the legacy optional `semesterId` filter.
  - Preserves `enrolledAt DESC` ordering through the existing read repository.

The slice reuses the existing read-only JDBC enrollment service/repository and
does not add enrollment mutations, student impersonation, CSV export, public
route ownership, PostgreSQL parity, canary or rollback.

## Verification

- Focused gate PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`
- Full canonical Java monolith gate PASS:
  `mvn -q -f java-services/pom.xml clean test`
- Surefire summary from `java-services/restful-api/target/surefire-reports`:
  26 reports / 176 tests / 0 failures / 0 errors / 1 skipped. The skipped test
  is the opt-in restore smoke when `THESIS_RESTORE_SMOKE` is not set.
- Production controller SQL-write grep PASS: no `INSERT`, `UPDATE`, `DELETE`,
  `MERGE`, `ALTER`, `DROP`, `CREATE` or `TRUNCATE` markers in the changed
  production controller.
- `git diff --check` PASS with only Git Windows LF-to-CRLF working-copy
  warnings on touched files.

## Open gates

- PostgreSQL read parity against an approved disposable restore.
- Live route canary and rollback rehearsal.
- CSV export parity for `GET /api/v1/enrollments/export/csv`.
- Enrollment create/drop/update/delete ownership.
- Exact-head Advisor/Kongming/Wukong review before any public enrollment route
  handoff.
