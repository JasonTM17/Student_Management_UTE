# Phase 15 — Backend-first finance read foundation

## Outcome

Add a small Java monolith finance read foundation needed before invoice/payment
screens can converge on the RESTful API:

- feature-gated admin invoice list/detail reads;
- feature-gated student self invoice list/detail reads using the `studentId`
  JWT claim;
- feature-gated admin payment list/detail reads;
- legacy-compatible JSON envelopes, invoice snapshots, item hydration and
  payment invoice joins.

This is not a finance-service cutover, invoice/payment write move, checkout
intent implementation, payment provider callback/webhook implementation, CSV
export port, reconciliation proof, PostgreSQL parity claim, gateway canary,
rollback proof, frontend change or mobile runtime claim.

## Scope and authority

In scope:

- expose Java finance reads only when both the `persistence` profile and
  `migration.finance-read.enabled=true` are active;
- use JDBC `SELECT` queries only against the migrated legacy `finance` schema;
- preserve the current Nest list envelope: `{ data, meta }`;
- preserve invoice detail as flattened invoice fields plus `items` and
  `payments`;
- preserve payment reads with joined invoice summary data;
- enforce legacy role boundaries for admin and student read paths;
- keep default `/api/v1/finance/*` routes returning `404` in the normal RESTful
  API shell unless the feature flag is explicitly enabled.

Non-goals:

- no invoice/payment create, update, delete, generation, checkout, payment
  intent polling, provider handoff, callback, webhook, CSV export, RabbitMQ
  event publishing or route ownership move in this phase;
- no schema DDL, Flyway migration or data reconciliation claim;
- no Stitch web/mobile rewiring.

## Acceptance criteria

- Admin invoice list/detail preserves ordering by `createdAt DESC`, status,
  semester and student filters, pagination metadata, nested student snapshot,
  semester snapshot, item hydration and payment hydration.
- Student invoice list/detail reads only invoices matching the authenticated
  `studentId` claim and returns `404` for another student's invoice.
- Admin payment list/detail preserves ordering by `createdAt DESC`, status,
  invoice and student filters, pagination metadata and joined invoice summary.
- Routes require authentication, enforce role boundaries and fail closed for
  missing student profile claim, invalid page, repeated query, unexpected query
  and not-found cases.
- Legacy-schema migration safety covers finance read mode so Hibernate DDL and
  Flyway are not accidentally used as a migration authority for this candidate.
- Root reactor test and `git diff --check` pass on the exact source snapshot
  before commit.

## Verification

Observed local gates for this phase on Windows with JDK 24, `forkCount=0`, a
bounded Maven heap and test temp files redirected to a D-drive temporary
directory outside the repository:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.finance.FinanceReadPersistenceTest,MigrationSafetyConfigTest,RestfulApiContractTest' '-DforkCount=0' test
mvn -q -f java-services/restful-api/pom.xml '-Dtest=AcademicReadPersistenceTest,PeopleReadPersistenceTest,NotificationReadPersistenceTest' '-DforkCount=0' test
mvn -q -f java-services/restful-api/pom.xml '-Dtest=AuthLoginPersistenceTest' '-DforkCount=0' test
git diff --check
node scripts/check-doc-hygiene.mjs
node scripts/check-architecture.mjs
node scripts/check-thesis-contract.mjs
rg -n "(?i)(password|secret|token|api[_-]?key|private[_-]?key|BEGIN RSA|BEGIN OPENSSH|credential)" <scoped finance/config/doc paths>
```

The finance/default-off focused gate, existing read-candidate chunks,
auth-session chunk and repository hygiene gates passed locally. The scoped
secret scan only matched existing placeholder/test configuration names and
documentation words; no new credential value was introduced by this phase.

The full root reactor remains `BLOCKED_CAPABILITY` on the current Windows
native memory/pagefile capacity after recent exact-worktree attempts exited
inside the JVM with native allocation errors. Crash/replay artifacts from those
attempts were removed from the repository root after verification. No source
assertion failure was observed in this finance slice.

PostgreSQL restore parity, runtime smoke, route canary, event/payment
reconciliation, rollback and independent final review remain open until the
final verification pass records them.
