# Phase 37 — Analytics enrollments-by-semester candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read
candidate with the legacy enrollments-by-semester endpoint used by admin
dashboard charts.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/enrollments-by-semester`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads the analytics service's legacy Prisma `public.Enrollment`,
  `public.Semester` and `public.AcademicYear` tables.
- Java issues JDBC `SELECT` queries only and does not add DDL, writer
  ownership, cockpit composition, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Count only enrollments with `status` in `CONFIRMED` or `COMPLETED`.
- Preserve the legacy semester response shape:
  `semesterId`, `semesterName`, `semesterNameEn`, `semesterNameVi`,
  `academicYear` and `enrollmentCount`.
- Preserve the legacy newest-semester-first order using semester `startDate`
  descending.
- Preserve the legacy limit of 10 semester buckets.
- Reject unexpected query parameters for the selected route.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - `CONFIRMED` and `COMPLETED` enrollment counting;
  - exclusion of pending enrollments;
  - semester name and academic-year response hydration;
  - semester `startDate` descending order;
  - student access denied;
  - unexpected query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for
  `GET /api/v1/analytics/enrollments-by-semester`.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 31 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result: 133 tests, 0 failures, 0 errors, 0 skipped
  across the configured `java-services/pom.xml` module set. Older stale
  surefire XML files remain under non-reactor legacy service `target`
  directories and are not evidence for this gate.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, cockpit
composition parity, route canary, rollback rehearsal, public analytics route
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
