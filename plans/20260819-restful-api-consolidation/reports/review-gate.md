# Architecture review gate — one RESTful API direction

## Review ledger snapshot

- Reviewed exact head: `313537d9a1fb1312ef409dc4f5a421508ebe7df0`
- Ledger freeze commit: `6f9a2dab74be6dd9cee3f638949f00390d664dc6`
- Branch: `feature/java-thesis-platform`
- Review mode: read-only, independent sidecar tasks
- Scope: whether the current bounded Java, Stitch web and native mobile work is
  safe to continue without an unsafe public collapse

## Exact-head verdicts for the reviewed snapshot

| Reviewer | Verdict | Meaning |
| --- | --- | --- |
| Advisor | `NOT_RUN` | Fresh review at `313537d` was not completed after bounded waits/capacity failures. Prior Advisor verdicts are stale. |
| Kongming | `NOT_RUN` | Fresh review at `313537d` was shut down after bounded waits. The prior `CONDITIONAL` verdict was for `b83805f` and is stale. |
| Wukong | `NOT_RUN` | Fresh adversarial review at `313537d` was not completed; an earlier retry also hit model capacity. |

The branch gate is therefore **HOLD**, not acceptance. A `CONDITIONAL`
Kongming review exists for `b83805f` and supports the migration direction, but
it cannot approve the reviewed `313537d` snapshot after the mobile
preview-boundary commits. The later ledger-only commit does not create a fresh
approval for the implementation.

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

This advisory predates the current uncommitted mobile changes and is sequencing
input only. It is not an exact-head approval.

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
- The FE has Stitch-aligned web evidence, but no standalone native mobile app.
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
