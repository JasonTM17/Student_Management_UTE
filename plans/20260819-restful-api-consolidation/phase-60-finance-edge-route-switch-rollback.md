# Phase 60 — Finance edge route-switch rollback rehearsal

## Outcome

Prove the finance read candidate can survive a local edge-proxy route switch
from legacy → Java → legacy under one stable client URL, while preserving the
known restored-legacy payment-status limitation.

This phase does not move finance writer ownership, checkout/provider callbacks,
public traffic, or FE/mobile wiring.

## Scope

In scope:

- reuse the phase-59 finance read corpus;
- run the legacy Node service and Java RESTful API against the disposable
  restored PostgreSQL snapshot;
- place a local proxy in front of both services and switch its upstream
  legacy → Java → legacy;
- compare proxy responses against the direct baselines and verify rollback hash
  stability.

Out of scope:

- finance writes, checkout/provider flows, exports, or reconciliation;
- permanent nginx/compose cutover or public route ownership;
- FE/mobile rewiring.

## Evidence

Observed on exact head `9b5ee317f3daa88ce6d379a6d3097b860e0a7aee` after
normalizing volatile Prisma invocation preambles in the harness:

```powershell
node --check scripts/run-finance-differential-rehearsal.mjs
node scripts/run-finance-differential-rehearsal.mjs --self-test
node scripts/run-finance-differential-rehearsal.mjs --edge-route-switch
```

Live run env:

- legacy base URL `http://127.0.0.1:54121/`
- Java base URL `http://127.0.0.1:54122/`
- disposable PostgreSQL `127.0.0.1:56460`
- database `campuscore_finance_read_20260821_182354`

Result: `PASS_WITH_LIMITATIONS`.

- 11/11 direct finance comparisons passed.
- The only limitation remains `GET /api/v1/finance/payments?status=COMPLETED`;
  the restored Node snapshot returns 500 because `finance.PaymentStatus` is
  absent there.
- The proxy route sequence stayed stable:
  `legacy-before → java-candidate → legacy-after`
  with the same edge proxy body hash
  `0dd68b6caccdf8ae4246e84bb169dfc7fd0029cbd71dec8ada3da8916f58d53d`.
- The proxy route-switch sequence also rolled back cleanly:
  `legacy-before` and `legacy-after` shared stage hash
  `19aae5d3114ca5be0ecb061977bf07a8d35cec75adbe08bf739f069b702c8a64`;
  `java-candidate` produced the expected distinct stage hash
  `e22651f9f3a410bb8d0b1145915d4c69f747f229dd92e7e541f52eb02ead2dd5`.
- The harness now hashes the semantic Prisma error cause only, so the
  documented `finance.PaymentStatus` limitation remains stable across the
  legacy-before/after proxy probes.
- `git diff --check` passed with only the usual Windows LF-to-CRLF warning on
  the script file.
- The live Node and Java processes were stopped after capture.

## Remaining holds

This adds local proxy rollback evidence, but it still does not clear public
finance route ownership, checkout ownership, or FE/mobile wiring. Fresh exact-
head review gates remain the next gate before any wider cutover claim.
