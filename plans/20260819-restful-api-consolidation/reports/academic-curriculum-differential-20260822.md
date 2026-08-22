# Academic curriculum differential rehearsal — 2026-08-22

- Exact head during the refresh: `09ae74a83320c24d075e675369f29dcc2735fd94`
- Harness source file:
  `scripts/run-academic-curriculum-differential-rehearsal.mjs`
- Commands observed:
  - `node --check scripts/run-academic-curriculum-differential-rehearsal.mjs`
  - `node scripts/run-academic-curriculum-differential-rehearsal.mjs --self-test`
  - `node scripts/run-academic-curriculum-differential-rehearsal.mjs --self-test --edge-route-switch`
- Result:
  - syntax check: PASS
  - self-test: PASS
  - edge-route-switch self-test: PASS
- The harness now compares curriculum list/detail reads, preserves the list
  no-hydration rule, checks 404 behavior, and exercises a bounded rollback
  route-switch proxy.
- Live rehearsal against the restored academic snapshot passed on the
  disposable PostgreSQL curriculum cluster seeded at `127.0.0.1:56452`.
- Legacy base: `http://127.0.0.1:4003/`
- Java base: `http://127.0.0.1:4010/`
- Commands observed:
  - `node scripts/run-academic-curriculum-differential-rehearsal.mjs`
  - `node scripts/run-academic-curriculum-differential-rehearsal.mjs --edge-route-switch`
- Result:
  - live differential: PASS
  - live edge-route-switch rollback: PASS
- The seeded curricula restore now confirms list no-hydration, detail-only
  curriculum-course hydration, not-found parity, and a rollback-safe route
  switch on the same stable client URL.
