# Phase 40 — Analytics student statistics candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read
candidate with the legacy student-statistics endpoint used by admin dashboard
summary charts.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/student-statistics`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads the analytics service's legacy Prisma `public.Student` table.
- Java issues JDBC `SELECT` queries only and does not add DDL, writer
  ownership, cockpit composition, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve the legacy response shape:
  `total`, `active`, `graduated`, `suspended` and `byYear`.
- Preserve status-specific counts for `ACTIVE`, `GRADUATED` and `SUSPENDED`.
- Preserve grouped year buckets with `{ year, count }` entries.
- Reject unexpected query parameters for the selected route.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - total student count;
  - active, graduated and suspended status aggregates;
  - exclusion of other statuses from the named status aggregates while still
    counting them in total;
  - grouped by-year bucket shape and counts;
  - student access denied;
  - unexpected query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for
  `GET /api/v1/analytics/student-statistics`.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 34 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result: 136 tests, 0 failures, 0 errors, 0 skipped
  across the configured `java-services/pom.xml` module set. Older stale
  surefire XML files remain under non-reactor legacy service `target`
  directories and are not evidence for this gate.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, cockpit
composition parity, route canary, rollback rehearsal, public analytics route
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
