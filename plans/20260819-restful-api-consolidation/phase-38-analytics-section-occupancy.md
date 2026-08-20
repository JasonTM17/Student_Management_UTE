# Phase 38 — Analytics section occupancy candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read
candidate with the legacy section-occupancy endpoint used by admin dashboard
charts.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/section-occupancy`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads the analytics service's legacy Prisma `public.Section`,
  `public.Course`, `public.Semester` and `public.Enrollment` tables.
- Java issues JDBC `SELECT` queries only and does not add DDL, writer
  ownership, cockpit composition, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve the legacy section/course/semester response shape:
  `sectionId`, `sectionNumber`, `courseCode`, `courseName`, `courseNameEn`,
  `courseNameVi`, `semesterName`, `semesterNameEn`, `semesterNameVi`,
  `capacity`, `enrolledCount` and `occupancyRate`.
- Count only section enrollments with `status` in `CONFIRMED` or `PENDING`.
- Preserve the legacy `Section.enrolledCount` fallback when the counted
  enrollment value is zero.
- Preserve the legacy `Section.enrolledCount` descending order and limit of 20
  section buckets.
- Preserve rounded occupancy percentage semantics and return zero when capacity
  is not positive.
- Reject unexpected query parameters for the selected route.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - `CONFIRMED` and `PENDING` occupancy counting;
  - exclusion of completed enrollments from occupancy counts;
  - `Section.enrolledCount` fallback when the counted value is zero;
  - section/course/semester response hydration;
  - `Section.enrolledCount` descending order;
  - rounded occupancy percentage calculation;
  - student access denied;
  - unexpected query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for
  `GET /api/v1/analytics/section-occupancy`.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 32 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result: 134 tests, 0 failures, 0 errors, 0 skipped
  across the configured `java-services/pom.xml` module set. Older stale
  surefire XML files remain under non-reactor legacy service `target`
  directories and are not evidence for this gate.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, cockpit
composition parity, route canary, rollback rehearsal, public analytics route
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
