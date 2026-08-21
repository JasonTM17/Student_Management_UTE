# Phase 42 — Analytics enrollment trends candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read
candidate with the legacy enrollment-trends endpoint used by admin dashboard
monthly activity charts.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/enrollment-trends`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads the analytics service's legacy Prisma `public.Enrollment` table.
- Java issues JDBC `SELECT` queries only and does not add DDL, writer
  ownership, cockpit composition, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve the legacy `months` query behavior:
  - missing, blank, non-numeric, `NaN` or infinity values use the default of 12;
  - numeric values are truncated;
  - the final bucket count is clamped from 1 through 24.
- Preserve UTC monthly buckets ordered from oldest to newest.
- Preserve `month`, `year`, `monthNumber`, `startDate`, `endDate`, `labelEn`,
  `labelVi`, `enrolled`, `dropped`, `completed`, `net` and `totalActivity`.
- Preserve enrolled counting for `CONFIRMED` and `PENDING`, dropped counting
  for `DROPPED`, and completed counting for `COMPLETED`.
- Preserve `net` as `enrolled + completed - dropped`.
- Preserve `totalActivity` as `enrolled + completed + dropped`.
- Reject repeated or unexpected query parameters for the selected route.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - a custom fractional `months` value truncated to the requested bucket count;
  - oldest-to-newest month ordering;
  - UTC start and end date boundaries;
  - English and Vietnamese labels matching the legacy Intl output shape;
  - `CONFIRMED`, `PENDING`, `DROPPED` and `COMPLETED` counting;
  - exclusion of out-of-window enrollments and unrelated statuses;
  - `net` and `totalActivity` calculations;
  - invalid `months` falling back to the default 12 buckets;
  - student access denied;
  - repeated query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for
  `GET /api/v1/analytics/enrollment-trends`.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 26.0.1
  and temporary files redirected to a D: recovery temp directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 36 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 26.0.1 and temporary
  files redirected to a D: recovery temp directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result: 138 tests, 0 failures, 0 errors, 0 skipped
  across the configured `java-services/pom.xml` module set. Older stale
  surefire XML files remain under non-reactor legacy service `target`
  directories and are not evidence for this gate.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, cockpit
composition parity, route canary, rollback rehearsal, public analytics route
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
