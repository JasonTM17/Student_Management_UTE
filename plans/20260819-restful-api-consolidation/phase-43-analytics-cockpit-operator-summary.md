# Phase 43 — Analytics cockpit and operator summary candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read
candidate with the legacy `operator-summary` and `cockpit` endpoints used by
the admin analytics dashboard shell.

## Boundary and authority

- Candidate routes:
  - `GET /api/v1/analytics/operator-summary`;
  - `GET /api/v1/analytics/cockpit`.
- The routes exist only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The routes are limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- `operator-summary` preserves the legacy static local-operator posture:
  service count `8`, zero dependencies down, zero high-latency dependencies and
  the Grafana/Prometheus/Loki/Tempo local dashboard links.
- `cockpit` composes the Java analytics candidate's existing read-only
  overview, enrollment-trends, section-occupancy, grade-distribution,
  finance-summary, notification-summary, registration-pressure and
  operator-summary payloads.
- Java issues no new SQL for `operator-summary`, uses the existing read-only
  analytics queries for `cockpit`, and does not add DDL, writer ownership,
  metrics export, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy paths and methods.
- Preserve legacy admin-only authorization.
- Preserve `operator-summary` response shape:
  `generatedAt`, `serviceCount`, `dependencyDown`, `highLatency` and
  `dashboards`.
- Preserve `cockpit` response shape:
  `generatedAt`, `overview`, `enrollmentTrends`, `sectionOccupancy`,
  `gradeDistribution`, `finance`, `notifications`, `registrationPressure` and
  `operator`.
- Preserve the cockpit's default enrollment-trends width at 12 months.
- Reject unexpected query parameters for both selected routes.
- Preserve feature-default-off behavior so the Java shell returns the stable
  404 envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - operator summary generated timestamp presence;
  - legacy service/dependency/high-latency constants;
  - dashboard labels and local URLs;
  - cockpit nested response shape and selected composed aggregate values;
  - 12-month default trend composition;
  - student access denied for both routes;
  - unexpected query parameter rejection for both routes.
- The monolith shell contract covers feature-default-off behavior for both
  routes.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 26.0.1
  and temporary files redirected outside C: through `java.io.tmpdir`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result from Surefire XML: 38 tests, 0 failures, 0 errors,
  0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 26.0.1 and temporary
  files redirected outside C: through `java.io.tmpdir`:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result from Surefire XML: 140 tests, 0 failures,
  0 errors, 0 skipped across 20 reports in the configured
  `java-services/pom.xml` module set.
- `git diff --check` passed. Git emitted only Windows line-ending warnings for
  touched Java/test files.
- Static analytics DML/DDL scan returned no matches:
  `rg -n -i --pcre2 '"\s*(insert|update|delete|create|alter|drop|truncate)\b' java-services/restful-api/src/main/java/io/campuscore/restfulapi/analytics`.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, live cockpit UI
wiring, route canary, rollback rehearsal, public analytics route handoff and
independent exact-head Advisor/Kongming/Wukong review remain `NOT_RUN`.
