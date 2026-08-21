# Phase 45 — Analytics attendance candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read candidate
with the legacy attendance analytics endpoint used by admin reporting surfaces.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/attendance`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- The optional `semesterId` query parameter filters attendance records through
  the related section's semester, matching the legacy Prisma query shape.
- Java issues read-only SQL over the legacy `public.Attendance` and
  `public.Section` tables. It does not add DDL, writer ownership, attendance
  workflow ownership, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve the response shape: `totalRecords`, `present`, `absent`, `late`,
  `excused` and `attendanceRate`.
- Preserve legacy status counting for `PRESENT`, `ABSENT`, `LATE` and
  `EXCUSED`.
- Preserve legacy attendance-rate arithmetic:
  `round(((present + late) / totalRecords) * 100)`, or `0` when there are no
  records.
- Reject repeated or unexpected query parameters.
- Preserve feature-default-off behavior so the Java shell returns the stable
  404 envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin access;
  - optional `semesterId` filtering through `Section.semesterId`;
  - all-semester aggregate behavior;
  - present/absent/late/excused counts;
  - rounded attendance-rate arithmetic;
  - student access denied;
  - repeated and unexpected query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for the route.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected outside C: through `java.io.tmpdir`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result from Surefire XML: 40 tests, 0 failures, 0 errors,
  0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected outside C: through `java.io.tmpdir`:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result from Surefire XML: 142 tests, 0 failures,
  0 errors, 0 skipped across 20 reports in the configured
  `java-services/pom.xml` module set.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, live admin route
wiring, route canary, rollback rehearsal, public analytics route handoff and
independent exact-head Advisor/Kongming/Wukong review remain `NOT_RUN`.
