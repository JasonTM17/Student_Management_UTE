# Architecture review gate — one RESTful API direction

## Current exact-head refresh — `92da1dfc5c3b28847e3dae7911b1331dc783c022`

- Branch: `feature/java-thesis-platform`
- Review mode: read-only, current-state refresh
- Scope: whether the backend foundation review pack is bound to the current
  exact head and safe to continue backend parity work
- Status: `HOLD`

| Reviewer | Verdict | Meaning |
| --- | --- | --- |
| Advisor | `HOLD` | Review-only gate refresh is the next safe move; backend parity can continue later, but source push/cutover/FE wiring remain HOLD. |
| Kongming | `HOLD` | Backend parity may continue, but source push/cutover/FE wiring remain HOLD until exact-head rollback/cutover proof exists. |
| Reviewer | `HOLD` | The previous pack was stale; this refresh now binds the current tip, but it is still only HOLD, not approval. |
| Wukong | `FALSIFIED` | The claim that the old pack was already fresh exact-head evidence is false. |

The current exact head stays behind the backend foundation gate. This refresh
records the exact-head evidence boundary so later client staging can compare
against a non-stale snapshot. The pack is still HOLD, but it now tracks the
current tip instead of an older SHA.

## Review ledger snapshot

- Reviewed exact head: `7f389f2deaaba53ad5f7368b9136f48cab41685a`
- Prior ledger freeze: `6f9a2dab74be6dd9cee3f638949f00390d664dc6`
- Branch: `feature/java-thesis-platform`
- Review mode: read-only, independent sidecar tasks
- Scope: whether the current bounded Java, Stitch web and native mobile work is
  safe to continue without an unsafe public collapse

## Exact-head verdicts for `7f389f2`

| Reviewer | Verdict | Meaning |
| --- | --- | --- |
| Advisor | `HOLD` | Stitch evidence remains 22 named references plus a 23-route native source atlas; browser visual capture, Expo/device runtime, live auth, and client cutover proof remain `NOT_RUN`. |
| Kongming | `CONDITIONAL` | The notification candidate can continue only as a disabled, read-only validation slice; public cutover/writer handoff remains `HOLD` pending PostgreSQL, auth, differential, canary, and rollback proof. |
| Wukong | `NOT_FALSIFIED` | The candidate exposes only subject-derived, feature-gated GET routes; the inspected JDBC adapter has no mutation path and tests cover subject isolation. This is source/H2 evidence, not upstream-token or PostgreSQL proof. |

The branch gate is therefore **HOLD**, not acceptance. The source candidate
may continue to bounded runtime validation, but none of these verdicts approve
authenticated parity, visual fidelity, Java cutover, writer handoff, or
production readiness. A later documentation-only commit does not create a new
implementation approval.

## Previous exact-head client/planning review

The `01c2155e9b95b5a41b44e9f2934256fed797b958` review recorded Advisor
`HOLD`, Kongming `HOLD`, and Wukong `NOT_FALSIFIED` for the earlier Stitch
mobile/session and notification-planning snapshot. It remains historical
evidence only; the current candidate verdicts above supersede it for this
branch head.

## Historical baseline verdicts

The original architecture review at `9ec033b5cd126c5d2051d45ac52bf7a8aee46b73`
returned Advisor `CONDITIONAL`, Kongming `CONDITIONAL`, and Wukong
`FALSIFIED` for a direct four-block collapse. That review remains useful as a
design baseline only; it is stale for the current snapshot.

## Bounded Kongming sequencing advisory

- Reviewed target: `cbd6b64dfd06f53e6ee6890664ac0f488767c5b2`.
- Result: `CONDITIONAL` for planning one read-only Java notification-inbox
  slice; `HOLD` for public route ownership, writer handoff, service retirement,
  or production claims. No source files were changed and no services ran.
- Candidate only: `GET /api/v1/notifications/my` plus the tightly coupled
  `GET /api/v1/notifications/my/unread-count`. During such a slice, the legacy
  notification service remains public default, canonical writer, Socket.IO,
  and RabbitMQ owner; Java must perform no notification DDL or writes.
- Required before any canary: freeze the actual Node contract, verify the
  `notifications` schema on an isolated restored PostgreSQL copy, grant Java a
  read-only role, prove subject-derived user isolation, compare legacy and Java
  responses, and exercise a router/feature-flag rollback. PostgreSQL,
  differential, authenticated E2E, browser/Expo device, and rollback evidence
  were all `NOT_RUN`.

This advisory predates the `1692e7e` session-state fix and remains sequencing
input only. The phase-05 contract now records the same ownership and runtime
preconditions. It is not an exact-head approval for the current branch.

## Evidence behind the gate

- The repository currently has multiple NestJS domain owners, multiple Prisma
  schemas, shared platform auth, RabbitMQ/Redis/MinIO dependencies and a Java
  thesis pilot. `backend` is not already a complete replacement backend.
- Auth depends on `cc_access_token`, `cc_refresh_token`, `cc_csrf`,
  `X-CSRF-Token`, JWT claims and service-token boundaries.
- Notification has Socket.IO channels/events; a REST-only target therefore
  needs a deliberate polling compatibility strategy.
- Finance has payment/idempotency and internal-context coupling; it cannot be
  treated as a trivial package move.
- The FE has Stitch-aligned web evidence and a native source scaffold, but no
  Expo/device runtime evidence.
- The Java image/runtime, authenticated parity, migration reconciliation and
  exercised rollback remain open. The pre-existing local Java image is stale
  relative to current source and is not accepted as proof.
- The native mobile scaffold now has 23 source screen definitions and a
  dependency-free atlas test, but defaults to preview mode and has no Expo,
  emulator, device or live API evidence.
- The Stitch web repair has source-level regression coverage, while fresh
  browser/reference-diff evidence remains `NOT_RUN`.

## Accepted decision

Proceed with **design and bounded implementation** of one standalone Java
RESTful API app. Keep the old topology as the canonical/rollback owner until a
domain has a frozen contract, migration evidence, authenticated differential
tests, runtime smoke, rollback proof and a fresh exact-head review.

## Explicit hold points

- No direct deletion of Node services or Prisma migrations.
- No public route/cookie/CSRF change merely to make tests pass.
- No dual-write without a reviewed consistency design; default is one canonical
  writer and shadow/diff reads.
- No claim that mobile is complete until an Expo/React Native project has
  installable/testable screens and an emulator/device smoke.
- No production-ready or cutover claim from static source checks alone.

## Handoff

The integration owner may continue bounded client/domain work under the phase
plan. Before any route switch, writer handoff, release or retirement, pin the
final exact commit and rerun all three gates independently; any verdict becomes
stale as soon as the reviewed snapshot changes.
