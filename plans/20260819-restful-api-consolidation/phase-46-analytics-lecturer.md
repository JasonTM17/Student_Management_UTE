# Phase 46 — Analytics lecturer candidate

## Outcome

Add a backend-first Java RESTful API candidate for the lecturer analytics reads
that the legacy analytics service exposes to lecturers:

- `GET /api/v1/analytics/lecturer/my`;
- `GET /api/v1/analytics/lecturer/sections`.

This remains a feature-default-off strangler slice. It does not move public
route ownership, metrics, exports, RabbitMQ/event consumers, cockpit UI wiring,
PostgreSQL parity or production traffic.

## Boundary and compatibility

- The candidate is active only with the `persistence` profile and
  `migration.analytics-read.enabled=true`.
- Both routes require `LECTURER`.
- The lecturer id comes from the authenticated JWT `lecturerId` claim; a missing
  profile claim is denied instead of broadening the query.
- The Java repository issues read-only JDBC `SELECT` statements against the
  legacy analytics-service Prisma `public` schema.
- `lecturer/my` preserves the legacy count shape:
  `{ totalSections, totalStudents, sectionsWithGrades }`.
- `lecturer/sections` preserves the legacy section bucket shape with bilingual
  course/semester fields, `capacity`, `enrolledCount` and rounded
  `occupancyRate`.
- Enrollment counting intentionally follows the legacy behavior:
  confirmed/pending enrollments are counted, and a zero counted value falls back
  to the stored `Section.enrolledCount`.

## Verification observed

Focused Java gate:

```powershell
$env:MAVEN_OPTS='-Djava.io.tmpdir=D:\Student_Management\.tmp\maven'
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test
```

Result: PASS. The focused reports for
`AnalyticsReadPersistenceTest`, `RestfulApiContractTest` and
`MigrationSafetyConfigTest` recorded 41 tests, 0 failures, 0 errors and 0
skipped. The run completed on Java 24.0.2 with the Maven temp directory
redirected under `D:\Student_Management\.tmp\maven`.

The H2 persistence test now covers:

- lecturer self summary for the authenticated `lecturerId`;
- other-lecturer section exclusion;
- section bucket bilingual fields;
- confirmed/pending count and stored-count fallback behavior;
- student role denial for lecturer analytics routes;
- missing `lecturerId` profile-claim denial;
- unexpected query rejection;
- normal RESTful API default-off `404` for both lecturer routes.

Full Java reactor gate:

```powershell
$env:MAVEN_OPTS='-Djava.io.tmpdir=D:\Student_Management\.tmp\maven'
mvn -q -f java-services/pom.xml test
```

Result: PASS. Surefire recorded 143 tests, 0 failures, 0 errors and 0 skipped
across 20 reports.

## Remaining gates

- PostgreSQL read parity: `NOT_RUN`.
- Live lecturer route wiring from web/mobile: `NOT_RUN`.
- Route canary and rollback rehearsal: `NOT_RUN`.
- Public analytics route handoff: `NOT_RUN`.
- Metrics/export/event consumer migration: `NOT_RUN`.
- Fresh exact-head Advisor/Kongming/Wukong review for this expanded analytics
  candidate: `NOT_RUN`.
