# Phase 57 — People read PostgreSQL parity repair

## Outcome

Repair the existing feature-gated people read candidate so its student list
query survives a real PostgreSQL rehearsal when the optional `status` filter is
absent.

## Exact source checkpoint

- Source repair commit: `feb6213e20fb14c67f2345007ed9485c0571777d`
- Parent baseline: `5c0327dba90a6fad8fd5552c05116a49b1ded135`
- Branch: `feature/java-thesis-platform`

## Repaired scope

- Production file:
  `java-services/restful-api/src/main/java/io/campuscore/restfulapi/people/repository/PeopleReadRepository.java`
- Replaced the nullable optional predicate
  `(:status IS NULL OR "status" = :status)` with a dynamic `WHERE "status" =
  :status` clause only when a status filter is present.
- The repair is SELECT-only and keeps the existing public API shape for
  `GET /api/v1/students` and `GET /api/v1/students/{id}`.

## Verification

- H2 focused regression PASS:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.people.PeopleReadPersistenceTest' '-DforkCount=0' test`
- PostgreSQL focused rehearsal PASS against the disposable target on
  `127.0.0.1:56434` with `currentSchema=people`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.people.PeopleReadPersistenceTest' '-DforkCount=0' test`
- Surefire summary from
  `java-services/restful-api/target/surefire-reports/TEST-io.campuscore.restfulapi.people.PeopleReadPersistenceTest.xml`:
  3 tests / 0 failures / 0 errors / 0 skipped.
- Production SQL-write marker grep on the repaired repository: PASS.
- Staged secret scan for the repaired repository: PASS.
- `git diff --check` on the repaired repository: PASS with only the expected
  Git Windows LF-to-CRLF working-copy warning.

## Open gates

- This is a focused people read PostgreSQL repair, not a public people-service
  route handoff.
- People restore parity, route canary, rollback observation, independent
  exact-head review and FE/mobile live wiring remain open.
