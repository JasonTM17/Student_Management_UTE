# Phase 16 — Backend-first analytics read foundation

## Outcome

Add a small Java monolith analytics read foundation needed before dashboard
screens can converge on the RESTful API:

- feature-gated admin analytics overview counts;
- feature-gated finance-summary aggregates for admins, super admins and finance
  officers;
- legacy-compatible JSON shapes for the two selected analytics endpoints.

This is not an analytics-service cutover, full cockpit port, enrollment trend
port, section occupancy port, grade distribution port, attendance port,
lecturer analytics port, metrics/export port, event consumer move, PostgreSQL
parity claim, gateway canary, rollback proof, frontend change or mobile runtime
claim.

## Scope and authority

In scope:

- expose Java analytics reads only when both the `persistence` profile and
  `migration.analytics-read.enabled=true` are active;
- use JDBC `SELECT` queries only against the analytics service's legacy Prisma
  `public` schema;
- preserve the current Nest overview field names;
- preserve the current finance-summary aggregate shape:
  `{ totals, invoiceStatus, paymentStatus, providerFunnel }`;
- enforce admin/super-admin access for overview and admin/super-admin/
  finance-officer access for finance summary;
- keep default `/api/v1/analytics/overview` and
  `/api/v1/analytics/finance-summary` returning `404` in the normal RESTful API
  shell.

Non-goals:

- no trend, occupancy, grade, cockpit, attendance, lecturer, metrics,
  RabbitMQ/event, `$queryRaw` operator, cache, route ownership or frontend
  migration in this phase;
- no schema DDL, Flyway migration or data reconciliation claim;
- no Stitch web/mobile rewiring.

## Acceptance criteria

- Overview returns counts for students, lecturers, courses, sections,
  enrollments, departments, faculties, academic years, semesters and classrooms.
- Finance summary returns invoice status totals, payment status totals,
  provider funnel totals and derived `totals` values matching legacy service
  semantics for completed/failed/pending/overdue records.
- Role boundaries reject anonymous/student access and allow finance officers
  for finance summary.
- Query boundaries reject unexpected parameters for the selected endpoints.
- Legacy-schema migration safety covers analytics read mode so Hibernate DDL
  and Flyway are not accidentally used as a migration authority for this
  candidate.

## Verification

Observed local gates for this phase on Windows with JDK 24, `forkCount=0`, a
bounded Maven heap and test temp files redirected to a D-drive temporary
directory outside the repository:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest' '-DforkCount=0' test
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,MigrationSafetyConfigTest,RestfulApiContractTest' '-DforkCount=0' test
git diff --check
node scripts/check-doc-hygiene.mjs
node scripts/check-architecture.mjs
node scripts/check-thesis-contract.mjs
rg -n "\\b(INSERT|UPDATE|DELETE|MERGE|CREATE|DROP|ALTER|TRUNCATE)\\b|jdbc\\.update|execute\\(" java-services/restful-api/src/main/java/io/campuscore/restfulapi/analytics
rg -n "(?i)(password|secret|token|api[_-]?key|private[_-]?key|BEGIN RSA|BEGIN OPENSSH|credential)" <scoped analytics/config/doc paths>
```

These focused gates passed locally. The source mutation grep returned no
runtime analytics package matches, supporting the SELECT-only claim. The scoped
secret scan only matched existing placeholder/test configuration names and
documentation words; no new credential value was introduced by this phase.

Root reactor remains subject to the current Windows native-memory/pagefile
capacity limitation observed in Phase 15; do not claim a root-reactor pass until
it is actually observed.

PostgreSQL restore parity, runtime smoke, route canary, full analytics parity,
rollback and independent final review remain open.
