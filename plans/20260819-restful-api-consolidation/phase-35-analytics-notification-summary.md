# Phase 35 — Analytics notification summary candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read candidate
with the legacy notification delivery summary endpoint used by the admin
cockpit.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/notification-summary`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads the analytics service's legacy Prisma `public.Notification` table,
  not the separate notification-service schema.
- Java issues JDBC `SELECT` queries only and does not add DDL, writer ownership,
  event ownership, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve the response shape:
  - `total`;
  - `unread`;
  - `read`;
  - `byType[]` with `type` and `count`;
  - `recentAttention[]` with `id`, `title`, `message`, `type` and ISO
    `createdAt`.
- Preserve recent attention as the five newest `ERROR` or `WARNING`
  notifications.
- Reject unexpected query parameters for the selected route.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - total, unread and read counts;
  - grouped notification type buckets;
  - newest warning/error recent attention rows;
  - ISO `createdAt` serialization from UTC database values;
  - student access denied;
  - unexpected query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for
  `GET /api/v1/analytics/notification-summary`.

## Verification observed

- First focused run found a timestamp mapping bug: H2 `TIMESTAMP` values were
  read through `getTimestamp().toInstant()`, which interpreted local machine
  timezone instead of treating legacy Prisma timestamp values as UTC.
- The repository now reads `LocalDateTime` and converts with `ZoneOffset.UTC`,
  matching the engagement repository pattern.
- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 29 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 131 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, cockpit
composition parity, route canary, rollback rehearsal, public analytics route
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
