# Phase 36 — Analytics grade distribution candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read candidate
with the legacy grade-distribution endpoint used by admin dashboard charts.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/grade-distribution`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads the analytics service's legacy Prisma `public.Enrollment` table.
- Java issues JDBC `SELECT` queries only and does not add DDL, writer ownership,
  cockpit composition, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve the fixed legacy grade bucket order:
  `A`, `A-`, `B+`, `B`, `B-`, `C+`, `C`, `C-`, `D+`, `D`, `D-`, `F`.
- Count only enrollments with `status=COMPLETED` and non-null `letterGrade`.
- Preserve zero-count buckets so frontend charts receive a stable shape.
- Preserve integer percentage semantics using rounded `(count / total) * 100`.
- Reject unexpected query parameters for the selected route.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - fixed bucket order and 12-bucket response shape;
  - count and percentage calculation;
  - exclusion of non-completed enrollments;
  - exclusion of completed enrollments with null `letterGrade`;
  - student access denied;
  - unexpected query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for
  `GET /api/v1/analytics/grade-distribution`.

## Verification observed

- First focused run found a Java compile issue from an ambiguous Spring JDBC
  `query` lambda overload. The repository now maps rows explicitly and then
  builds the grade-count map.
- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 30 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 132 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, cockpit
composition parity, route canary, rollback rehearsal, public analytics route
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
