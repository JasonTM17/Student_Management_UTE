# Phase 59 — Finance envelope differential preparation

## Outcome

Tighten the feature-default-off Java finance read envelope so the student and
admin payment shapes are safer to compare against the legacy finance service,
then add a bounded finance differential rehearsal harness.

This phase does not move finance writer ownership, checkout/provider callbacks,
public traffic, route canary, rollback ownership, or FE/mobile client wiring.

## Scope

In scope:

- keep `/api/v1/finance/my/invoices` student-scoped and avoid returning admin
  student identity/update fields in that list response;
- preserve admin invoice list/detail hydration while avoiding recursive invoice
  payloads inside invoice-detail payments;
- hydrate payment invoice snapshots with the legacy finance invoice fields
  needed for differential comparison;
- add a local self-testable differential harness for the finance read corpus and
  legacy → Java → legacy route sequence.

Out of scope:

- finance writes, checkout, payment providers, webhooks, exports or
  reconciliation;
- restored legacy-data parity, live route canary, rollback observation or public
  finance route handoff;
- web/mobile traffic rewiring.

## Evidence

Observed on local `main` at source commit
`b567a02f8c152b909470c96ea638a129c4f23c1c`:

```powershell
node --check scripts/run-finance-differential-rehearsal.mjs
node scripts/run-finance-differential-rehearsal.mjs --self-test
```

Result: PASS. The harness self-test covers 11 finance comparisons plus the
legacy-before → java-candidate → legacy-after route sequence.

Restored PostgreSQL live rehearsal on `127.0.0.1:56460` using the disposable
finance snapshot `campuscore_finance_read_20260821_182354`:

```powershell
node scripts/run-finance-differential-rehearsal.mjs
```

Result: `PASS_WITH_LIMITATIONS`.

- 10 comparable finance checks passed.
- `GET /api/v1/finance/payments?status=COMPLETED` is a known restored-legacy
  limitation: the Node legacy service returns 500 on this restored varchar
  schema because the Prisma `finance.PaymentStatus` enum is absent.
- The legacy-before / java-candidate / legacy-after route sequence hash stayed
  stable.

Focused Java verification:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.finance.FinanceReadPersistenceTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test
mvn -q -f java-services/pom.xml test
```

Result: PASS.
Canonical RESTful API Surefire summary:
26 reports / 177 tests / 0 failures / 0 errors / 1 skipped.

Additional hygiene:

- `git diff --check` for the touched finance source and harness: PASS, with
  only Windows LF-to-CRLF working-copy warnings.
- High-confidence sensitive-value scan found only JWT/token variable names in
  the harness and test code; no hardcoded credential value was observed.

## Remaining holds

This phase now has live restored-PostgreSQL evidence, but the backend foundation
gate still remains HOLD until the known legacy schema limitation is resolved or
accounted for, route rollback observation is expanded, and fresh exact-head
review gates pass.
