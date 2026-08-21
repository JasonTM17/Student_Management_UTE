# Phase 48 — Academic waitlist read candidate

## Outcome

Add a feature-default-off Java RESTful monolith candidate for legacy academic
waitlist read routes while preserving mutation ownership in the Nest
`academic-service`.

## Scope

- Candidate routes:
  - `GET /api/v1/waitlist`
  - `GET /api/v1/waitlist/my`
  - `GET /api/v1/waitlist/section/{sectionId}`
  - `GET /api/v1/waitlist/{id}`
- Source database: migrated legacy Prisma `academic` schema tables:
  `Waitlist`, `Student`, `User`, `Section`, `Course`, `Department`,
  `Semester`, `SectionSchedule` and `Classroom`.
- Feature flag: `migration.academic-waitlist-read.enabled`, defaulted by
  `ACADEMIC_WAITLIST_READ_ENABLED:false`.

## Non-goals

- No `POST /waitlist/{id}/promote` port.
- No `DELETE /waitlist/{id}` or waitlist resequencing port.
- No enroll/drop mutation port.
- No public gateway canary, frontend traffic switch, PostgreSQL restore parity,
  rollback rehearsal or production cutover in this phase.

## Acceptance criteria

- Java waitlist routes are absent/404 when the feature flag is disabled.
- Student self read requires a `STUDENT` role and `studentId` JWT claim and
  returns only that student's `ACTIVE` waitlist entries ordered by legacy
  `addedAt DESC, position ASC`.
- Admin list requires `ADMIN`/`SUPER_ADMIN`, preserves the legacy `data/meta`
  envelope, supports `page`, `limit` and optional `sectionId`, and orders by
  `position ASC`.
- Section read requires `ADMIN`/`SUPER_ADMIN`/`LECTURER`, returns only
  `ACTIVE` rows for the section, ordered by `position ASC`.
- Detail read requires `ADMIN`/`SUPER_ADMIN`/`LECTURER`, returns a single row
  or the stable not-found envelope.
- Unexpected or repeated query parameters and invalid page sizes fail with the
  stable request envelope.
- Candidate repository remains SELECT-only.

## Planned verification

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicWaitlistReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test
rg -n "\b(INSERT|UPDATE|DELETE|MERGE|CREATE|DROP|ALTER|TRUNCATE)\b|jdbc\.update|execute\(" java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/repository/AcademicWaitlistReadRepository.java java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicWaitlistReadService.java java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/web/AcademicWaitlistReadController.java
git diff --check
```

The SQL-write grep is expected to return no matches for candidate production
code. Test fixtures may write to H2.

## Observed evidence

- `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicWaitlistReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test` — PASS on 2026-08-21 after fixing fixture compatibility with the shared H2 academic schema.
- `mvn -q -f java-services/pom.xml test` — PASS on 2026-08-21.
- Surefire summary after full Java reactor: 23 reports / 156 tests / 0 failures / 0 errors / 0 skipped.
- Production waitlist candidate SQL-write grep — PASS, no matches.
- `git diff --check` — PASS; only Git line-ending warnings for existing Java files.
- Later PostgreSQL-focused rehearsal first exposed a test-fixture binding
  problem, not a production read failure: pgjdbc could not infer the SQL type
  for direct `java.time.Instant` values inserted into timestamp fixture
  columns. The fixture now converts timestamp values with `Timestamp.from(...)`.
- H2 focused regression after the fixture repair:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicWaitlistReadPersistenceTest' '-DforkCount=0' test`
  — PASS.
- PostgreSQL focused rehearsal after the fixture repair against the disposable
  local cluster on `127.0.0.1:56444`, fresh database
  `campuscore_academic_waitlist_56444`, `currentSchema=academic`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicWaitlistReadPersistenceTest' '-DforkCount=0' test`
  — PASS; 4 tests / 0 failures / 0 errors / 0 skipped.
- Full canonical Java monolith gate after the fixture repair:
  `mvn -q -f java-services/pom.xml clean test` — PASS; 26 reports /
  177 tests / 0 failures / 0 errors / 1 skipped.

## HOLD gates

- PostgreSQL focused read compatibility is observed for the selected waitlist
  tests, but restored legacy dataset parity is not run.
- Gateway canary and rollback are not run.
- Authenticated FE/mobile runtime parity is not run.
- Advisor/Kongming/Wukong exact-head release review is not run for this slice.
