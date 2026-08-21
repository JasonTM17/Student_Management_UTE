# Finance edge route-switch rollback rehearsal — 2026-08-21

- Exact head: `b67aa85f16bb73a5d18d00b35bef070bc61ada32`
  (`test(finance): add edge route-switch rehearsal`).
- Harness syntax/self-test:
  `node --check scripts/run-finance-differential-rehearsal.mjs` and
  `node scripts/run-finance-differential-rehearsal.mjs --self-test` both PASS.
- Live route-switch rehearsal:
  `node scripts/run-finance-differential-rehearsal.mjs --edge-route-switch`
  returned `PASS_WITH_LIMITATIONS`.
- Live environment:
  - legacy Node service `http://127.0.0.1:54121/`
  - Java service `http://127.0.0.1:54122/`
  - restored PostgreSQL snapshot `campuscore_finance_read_20260821_182354`
    on `127.0.0.1:56460`
- Result details:
  - 11/11 direct finance comparisons passed.
  - One limitation remains on
    `GET /api/v1/finance/payments?status=COMPLETED`, because the restored Node
    snapshot lacks the Prisma `finance.PaymentStatus` enum.
  - The stable proxy route sequence hash was
    `0dd68b6caccdf8ae4246e84bb169dfc7fd0029cbd71dec8ada3da8916f58d53d`.
  - The proxy route-switch stage hashes were:
    - `legacy-before`: `ee349b886ea1cf6de05e84fbe2785e3e5e7d8f3c46630f5d2a278aec6278c8b5`
    - `java-candidate`: `e22651f9f3a410bb8d0b1145915d4c69f747f229dd92e7e541f52eb02ead2dd5`
    - `legacy-after`: `ee349b886ea1cf6de05e84fbe2785e3e5e7d8f3c46630f5d2a278aec6278c8b5`
- `git diff --check` passed; only Windows LF-to-CRLF warnings were reported.
- Live Node and Java rehearsal processes were stopped after capture.
