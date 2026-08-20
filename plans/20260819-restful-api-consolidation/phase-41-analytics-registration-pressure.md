# Phase 41 — Analytics registration pressure candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read
candidate with the legacy registration-pressure endpoint used by admin
dashboard capacity and waitlist summary cards.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/registration-pressure`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads the analytics service's legacy Prisma `public.Section`,
  `public.Course`, `public.Semester`, `public.Enrollment` and `public.Waitlist`
  tables.
- Java issues JDBC `SELECT` queries only and does not add DDL, writer
  ownership, cockpit composition, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve the legacy response shape:
  `activeSemesters`, `totalSections`, `atCapacity`, `nearCapacity`,
  `waitlistActive`, `averageOccupancy`, `highestPressure` and
  `waitlistStatus`.
- Preserve active registration semester counting for `REGISTRATION_OPEN` and
  `ADD_DROP_OPEN`.
- Preserve section occupancy using `CONFIRMED` and `PENDING` enrollments, with
  fallback to stored `Section.enrolledCount` when no counted enrollments exist.
- Preserve `atCapacity` as occupancy at or above 100 and `nearCapacity` as
  occupancy from 80 through 99.
- Preserve `waitlistActive` as the total `ACTIVE` waitlist count.
- Preserve rounded average occupancy across all sections.
- Preserve `highestPressure` ordering by occupancy rate descending, then active
  waitlist count descending, limited to eight sections.
- Preserve waitlist status buckets as `{ status, count }` entries.
- Reject unexpected query parameters for the selected route.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - active semester status counting;
  - total section counting;
  - at-capacity and near-capacity thresholds;
  - `CONFIRMED` and `PENDING` enrollment counting with excluded enrollment
    statuses;
  - stored `Section.enrolledCount` fallback when no counted enrollments exist;
  - `ACTIVE` waitlist counting with excluded waitlist statuses;
  - rounded average occupancy;
  - highest-pressure ordering and limit-compatible shape;
  - waitlist status bucket shape and counts;
  - student access denied;
  - unexpected query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for
  `GET /api/v1/analytics/registration-pressure`.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 35 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result: 137 tests, 0 failures, 0 errors, 0 skipped
  across the configured `java-services/pom.xml` module set. Older stale
  surefire XML files remain under non-reactor legacy service `target`
  directories and are not evidence for this gate.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, cockpit
composition parity, route canary, rollback rehearsal, public analytics route
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
