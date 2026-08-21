# Phase 56 — Academic enrollment PostgreSQL parity rehearsal

## Outcome

Prove the existing academic enrollment/grade read foundation also survives a
real disposable PostgreSQL rehearsal on the exact current Java monolith HEAD.

## Exact source checkpoint

- Source commit: `683829a661f517c7469a4ee7cc85a22aeaeb2a08`
- Parent baseline: `8bedb8b9a8f2b4c9e6ce0f6f2fa0d2b3dfb1f9d3`
- Branch: `feature/java-thesis-platform`

## Rehearsed scope

- `AcademicEnrollmentReadPersistenceTest` against the disposable PostgreSQL
  cluster on `127.0.0.1:56433`
- legacy academic schema at `currentSchema=academic`
- the existing enrollment, grade, transcript and internal-context read paths

This rehearsal does not add new routes or writers. It only checks that the
current Java read foundation can bootstrap and execute against PostgreSQL on
the exact HEAD already carrying the academic internal-context bridge.

## Verification

- Focused PostgreSQL rehearsal PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest' '-DforkCount=0' test`
- Database bootstrap observed on PostgreSQL 18.4 at
  `jdbc:postgresql://127.0.0.1:56433/campuscore?currentSchema=academic`
- Surefire summary from
  `java-services/restful-api/target/surefire-reports/TEST-io.campuscore.restfulapi.academic.AcademicEnrollmentReadPersistenceTest.xml`:
  8 tests / 0 failures / 0 errors / 0 skipped

## Open gates

- This is one disposable-database parity rehearsal, not the full backend
  foundation gate.
- Route canary, rollback observation, and independent exact-head review remain
  open.
- FE Stitch/web/mobile remains later-phase work.
