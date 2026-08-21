# Phase 58 — Finance PostgreSQL optional-filter repair

## Outcome

Repair the existing feature-default-off Java finance read candidate so invoice
and payment reads work on PostgreSQL when optional filters are absent.

This phase does not add finance routes, move writer ownership, implement
checkout/payment providers, change public traffic, or claim production cutover.

## Scope

In scope:

- replace PostgreSQL-hostile nullable predicates such as
  `(:status IS NULL OR ...)` with dynamic `WHERE` clauses for finance reads;
- preserve the existing list/detail envelopes, role boundaries, ordering,
  pagination and joined invoice/payment hydration;
- add regression coverage for payment reads without optional filters;
- verify the slice on H2 and a disposable UTF-8 PostgreSQL target.

Out of scope:

- invoice or payment writes;
- provider callbacks, webhooks, exports or reconciliation;
- restored legacy-data parity, route canary, rollback observation or FE/mobile
  traffic rewiring.

## Evidence

Source repair commit:
`3327f8318bc9aa775a4e48185b8f74b54ab3215a`
(`fix(java): support finance postgres optional filters`).

Observed checks on Windows with JDK 24.0.2 and Maven temp files redirected to
D-drive workspace temp paths:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.finance.FinanceReadPersistenceTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test
```

Result: PASS.

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.finance.FinanceReadPersistenceTest' '-DforkCount=0' test
```

Result: PASS against disposable PostgreSQL 18.4 on `127.0.0.1:56448`,
`currentSchema=finance`, `server_encoding=UTF8`. The cluster was initialized
under `.tmp/pg-finance-main-20260821-181519`, used only for this rehearsal and
stopped afterward.

```powershell
mvn -q -f java-services/pom.xml clean test
```

Result: PASS. Canonical RESTful API surefire summary:
26 reports / 177 tests / 0 failures / 0 errors / 1 skipped.

Additional hygiene:

- `git diff --check` on touched Finance files: PASS with only Windows
  LF-to-CRLF working-copy warnings.
- Production finance repository SQL-write marker scan: PASS, no
  `INSERT`/`UPDATE`/`DELETE`/DDL markers.
- Staged sensitive-value diff scan before commit: PASS.

## Remaining holds

The finance slice is still not public route ownership or cutover evidence.
Restored legacy-data parity, route canary, rollback observation, payment
reconciliation, FE/mobile live wiring and exact-head release review remain open
before any production-ready claim.
