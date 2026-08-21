# Phase 44 — Analytics revenue candidate

## Outcome

Extend the Java REST API monolith's feature-default-off analytics read candidate
with the legacy revenue analytics endpoint used by admin and finance operator
surfaces.

## Boundary and authority

- Candidate route: `GET /api/v1/analytics/revenue`.
- The route exists only with the `persistence` profile and
  `migration.analytics-read.enabled=true`; `ANALYTICS_READ_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN`, `SUPER_ADMIN` and `FINANCE_OFFICER`,
  matching the legacy Nest controller.
- The optional `semesterId` query parameter filters invoices by
  `Invoice.semesterId` and completed payments through their related invoice's
  semester, matching the legacy Prisma query shape.
- Java issues read-only SQL over the legacy `public.Invoice` and
  `public.Payment` tables. It does not add DDL, writer ownership, payment
  workflow ownership, route ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve finance-officer access.
- Preserve the response shape: `totalInvoiced`, `totalPaid`, `pending`,
  `invoiceCount`, `paidInvoiceCount` and `pendingInvoiceCount`.
- Preserve legacy revenue arithmetic:
  - `totalInvoiced` is the sum of invoice totals;
  - `totalPaid` is the sum of `COMPLETED` payment amounts;
  - `pending` is `totalInvoiced - totalPaid`;
  - `paidInvoiceCount` counts invoices with status `PAID`;
  - `pendingInvoiceCount` counts invoices with status other than `PAID`.
- Reject repeated or unexpected query parameters.
- Preserve feature-default-off behavior so the Java shell returns the stable
  404 envelope unless the analytics candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - finance-officer access;
  - optional `semesterId` filtering;
  - all-semester aggregate behavior;
  - invoice and completed-payment arithmetic;
  - student access denied;
  - repeated and unexpected query parameter rejection.
- The monolith shell contract covers feature-default-off behavior for the route.

## Verification observed

- Focused analytics/default-off H2 gate passed on 2026-08-21 with Java 24.0.2
  and temporary files redirected outside C: through `java.io.tmpdir`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.analytics.AnalyticsReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result from Surefire XML: 39 tests, 0 failures, 0 errors,
  0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected outside C: through `java.io.tmpdir`:
  `mvn -q -f java-services/pom.xml test`.
- Observed current reactor result from Surefire XML: 141 tests, 0 failures,
  0 errors, 0 skipped across 20 reports in the configured
  `java-services/pom.xml` module set.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, live
finance/admin route wiring, route canary, rollback rehearsal, public analytics
route handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
