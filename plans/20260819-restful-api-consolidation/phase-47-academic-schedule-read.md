# Phase 47 — Backend-first academic schedule read foundation

## Outcome

Add a small Java monolith academic schedule read foundation needed before the
web/mobile timetable views can converge on the RESTful API:

- feature-gated schedule list read at `GET /api/v1/schedules`;
- feature-gated schedule detail read at `GET /api/v1/schedules/{id}`;
- legacy-compatible `{ data, meta }` list envelope;
- legacy-compatible nested `section` and `classroom` summaries.

This is not a schedule writer handoff, lecturer `sections/my/schedule` port,
enrollment timetable composition, conflict detection, PostgreSQL parity claim,
gateway canary, rollback proof, frontend change or mobile runtime claim.

## Scope and authority

In scope:

- expose Java schedule reads only when both the `persistence` profile and
  `migration.academic-schedule-read.enabled=true` are active;
- use JDBC `SELECT` queries only against the migrated legacy Prisma
  `academic` schema;
- preserve the selected legacy schedule envelope shape with direct section and
  classroom hydration;
- keep default schedule routes returning `404` in the normal RESTful API shell;
- include the new flag in migration safety so Flyway and Hibernate DDL do not
  become migration authorities for this legacy-schema candidate.

Non-goals:

- no schedule create, update or delete;
- no lecturer-owned schedule shortcut, calendar aggregation, attendance
  integration, enrollment write, RabbitMQ/event, export or conflict-resolution
  behavior;
- no schema DDL, Flyway migration or data reconciliation claim;
- no Stitch web/mobile rewiring.

## Acceptance criteria

- Schedule list returns a legacy-style `{ data, meta }` envelope, supports
  `page`/`limit`, orders by day-of-week then start time, and hydrates direct
  section/classroom fields.
- Schedule detail returns the same shape for an existing schedule and a stable
  `404` envelope for a missing id.
- Role/query boundaries reject anonymous access, invalid page sizes and
  unexpected query parameters.
- The route remains default-off unless
  `migration.academic-schedule-read.enabled=true` is explicit.
- Legacy-schema migration safety covers academic schedule read mode so
  Hibernate DDL and Flyway are not accidentally used as a migration authority
  for this candidate.

## Verification

Observed local gates for this phase on Windows with Java 24.0.2, `forkCount=0`
and Maven temp files redirected to the D-drive project temp directory:

```powershell
$env:MAVEN_OPTS='-Djava.io.tmpdir=D:\Student_Management\.tmp\maven'
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicScheduleReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test
mvn -q -f java-services/pom.xml test
node scripts/check-doc-hygiene.mjs
node scripts/check-architecture.mjs
git diff --check
rg -n "\b(INSERT|UPDATE|DELETE|MERGE|CREATE|DROP|ALTER|TRUNCATE)\b|jdbc\.update|execute\(" java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/repository/AcademicScheduleReadRepository.java java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicScheduleReadService.java java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/web/AcademicScheduleReadController.java
```

The focused Maven gate passed locally. The full Java reactor gate passed with
22 Surefire reports and 150 tests: 0 failures, 0 errors, 0 skipped. Documentation
hygiene, architecture, whitespace and source mutation scans passed. The source
mutation grep returned no runtime academic schedule package matches, supporting
the SELECT-only claim for this slice.

Focused PostgreSQL compatibility evidence was later observed against the
disposable local cluster on `127.0.0.1:56442` with a fresh database
`campuscore_academic_schedule_56442` and `currentSchema=academic`:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.academic.AcademicScheduleReadPersistenceTest' '-DforkCount=0' test
```

Surefire summary: 3 tests / 0 failures / 0 errors / 0 skipped. This proves the
selected academic schedule read tests run against real PostgreSQL syntax/types,
but it is not a restored legacy academic dataset, route canary, rollback
observation or public traffic handoff.

PostgreSQL restore parity, runtime smoke, route canary, schedule write parity,
rollback and independent final review remain open.
