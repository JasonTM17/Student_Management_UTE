# Phase 39 — Analytics top courses candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read
candidate with the legacy top-courses endpoint used by admin dashboard charts.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/top-courses`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads the analytics service's legacy Prisma `public.Course`,
  `public.Section` and `public.Enrollment` tables.
- Java issues JDBC `SELECT` queries only and does not add DDL, writer
  ownership, cockpit composition, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve the legacy course response shape:
  `courseId`, `courseCode`, `courseName`, `courseNameEn`, `courseNameVi`,
  `credits`, `sectionCount` and `totalEnrollments`.
- Count only section enrollments with `status` in `CONFIRMED` or `PENDING`.
- Preserve section count semantics across all sections belonging to each
  course.
- Preserve descending sort by `totalEnrollments`.
- Preserve the default limit of 10 and accept a single `limit` query parameter
  for bounded chart payloads.
- Reject unexpected or repeated query parameters for the selected route.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - course response hydration;
  - section count across multiple sections;
  - `CONFIRMED` and `PENDING` enrollment totals;
  - exclusion of completed enrollments from top-course totals;
  - descending total-enrollment ordering;
  - custom `limit` behavior;
  - student access denied;
  - repeated query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for
  `GET /api/v1/analytics/top-courses`.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 33 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result: 135 tests, 0 failures, 0 errors, 0 skipped
  across the configured `java-services/pom.xml` module set. Older stale
  surefire XML files remain under non-reactor legacy service `target`
  directories and are not evidence for this gate.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, cockpit
composition parity, route canary, rollback rehearsal, public analytics route
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
