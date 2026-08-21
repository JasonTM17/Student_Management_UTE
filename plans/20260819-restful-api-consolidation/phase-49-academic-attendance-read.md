# Phase 49 — Academic attendance read candidate

## Outcome

Add a feature-default-off Java RESTful monolith candidate for legacy academic
attendance read routes while preserving attendance mark/update/delete ownership
in the Nest `academic-service`.

## Scope

- Candidate routes:
  - `GET /api/v1/attendance`
  - `GET /api/v1/attendance/my`
  - `GET /api/v1/attendance/my/summary`
  - `GET /api/v1/attendance/lecturer/my`
  - `GET /api/v1/attendance/section/{sectionId}`
  - `GET /api/v1/attendance/section/{sectionId}/summary`
  - `GET /api/v1/attendance/{id}`
- Source database: migrated legacy Prisma `academic` schema tables:
  `Attendance`, `Student`, `User`, `Section` and `Course`.
- Feature flag: `migration.academic-attendance-read.enabled`, defaulted by
  `ACADEMIC_ATTENDANCE_READ_ENABLED:false`.

## Non-goals

- No `POST /attendance`, `POST /attendance/bulk` or
  `POST /attendance/section/{sectionId}/mark` port.
- No `PUT /attendance/{id}` or `DELETE /attendance/{id}` port.
- No lecturer timetable shortcut, grade write, event publication or
  notification side effect.
- No public gateway canary, frontend traffic switch, PostgreSQL restore parity,
  rollback rehearsal or production cutover in this phase.

## Acceptance criteria

- Java attendance routes are absent/404 when the feature flag is disabled.
- Student self read requires a `STUDENT` role and `studentId` JWT claim,
  supports optional `sectionId` or `semesterId`, and orders by legacy
  `date DESC`.
- Student summary groups by section and preserves the legacy present-only
  attendance-rate formula.
- Admin/lecturer list requires `ADMIN`/`SUPER_ADMIN`/`LECTURER`, preserves the
  legacy `data/meta` envelope, supports `page`, `limit`, `sectionId`,
  `studentId` and `date`, and orders by `date DESC`.
- Lecturer self read requires a `LECTURER` role and `lecturerId` JWT claim,
  restricts rows to sections owned by that lecturer, and supports optional
  `sectionId` and `date`.
- Section read requires `ADMIN`/`SUPER_ADMIN`/`LECTURER`, supports optional
  `date`, and preserves student first-name ordering.
- Section summary preserves the legacy `(present + late) / totalRecords`
  attendance-rate formula and distinct-date session count.
- Detail read returns a single row or the stable not-found envelope.
- Unexpected or repeated query parameters, invalid page sizes and missing
  profile claims fail with stable envelopes.
- Candidate repository remains SELECT-only.

## Planned verification

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicAttendanceReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test
mvn -q -f java-services/pom.xml test
rg -n "\b(INSERT|UPDATE|DELETE|MERGE|CREATE|DROP|ALTER|TRUNCATE)\b|jdbc\.update|execute\(" java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/repository/AcademicAttendanceReadRepository.java java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicAttendanceReadService.java java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/web/AcademicAttendanceReadController.java
git diff --check
```

The SQL-write grep is expected to return no matches for candidate production
code. Test fixtures may write to H2.

## Observed evidence

- `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicAttendanceReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test` — PASS on 2026-08-21 after one fixture/oracle repair for H2 timestamp serialization and student self-scope fixture data.
- `mvn -q -f java-services/pom.xml test` — PASS on 2026-08-21.
- Surefire summary after full Java reactor: 24 reports / 162 tests / 0 failures / 0 errors / 0 skipped.
- Production attendance candidate SQL-write grep — PASS, no matches.
- `git diff --check` — PASS; only Git line-ending warnings for existing Java files.
- Source commit: `e61dbd3 feat(java): add academic attendance reads`, pushed to
  `origin/feature/java-thesis-platform` on 2026-08-21.

## HOLD gates

- PostgreSQL read parity is not run.
- Gateway canary and rollback are not run.
- Authenticated FE/mobile runtime parity is not run.
- Advisor/Kongming/Wukong exact-head release review is not run for this slice.
