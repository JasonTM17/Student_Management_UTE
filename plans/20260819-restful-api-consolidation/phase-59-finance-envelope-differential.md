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

Observed on local `main` at
`ec1b2fed6d471716d35403b74158d513768b0d92` before commit:

```powershell
node scripts/run-finance-differential-rehearsal.mjs --self-test
```

Result: PASS for 11 finance read comparisons plus the legacy-before,
java-candidate and legacy-after route sequence. This is a harness self-test
with local in-process servers, not restored legacy runtime evidence.

```powershell
mvn -q -f java-services/pom.xml clean test
```

Result: PASS. Canonical RESTful API Surefire summary:
26 reports / 177 tests / 0 failures / 0 errors / 1 skipped.

Additional hygiene:

- `git diff --check` for the changed Finance files and the rehearsal harness:
  PASS, with only Windows LF-to-CRLF working-copy warnings.
- High-confidence sensitive-value scan found only JWT/token variable names in
  the harness and test code; no hardcoded credential value was observed.

## Remaining holds

This phase is still source/harness evidence. The backend foundation gate remains
HOLD until a restored PostgreSQL read-only target, live legacy-versus-Java
differential, route rollback observation and fresh exact-head review gates pass.
