# Architecture review gate — one RESTful API direction

## Review ledger snapshot

- Reviewed exact head: `01c2155e9b95b5a41b44e9f2934256fed797b958`
- Prior ledger freeze: `6f9a2dab74be6dd9cee3f638949f00390d664dc6`
- Branch: `feature/java-thesis-platform`
- Review mode: read-only, independent sidecar tasks
- Scope: whether the current bounded Java, Stitch web and native mobile work is
  safe to continue without an unsafe public collapse

## Exact-head verdicts for `01c2155`

| Reviewer | Verdict | Meaning |
| --- | --- | --- |
| Advisor | `HOLD` | The source atlas records 22 named Stitch screens (9 desktop, 13 mobile) plus one supplementary record, and the native registry asserts 23 routes; browser visual capture, Expo/device runtime, and live API proof remain `NOT_RUN`. |
| Kongming | `HOLD` | The planned notification GET slice preserves Node as writer/public/realtime owner, but isolated PostgreSQL permissions, subject isolation, differential parity, and rollback evidence are missing. |
| Wukong | `NOT_FALSIFIED` | The bounded mobile session/route claim was not falsified after restricting preview entry to `auth.signIn` plus preview mode; this is source-level only and does not prove server authorization. |

The branch gate is therefore **HOLD**, not acceptance. Wukong closes only the
specific mobile source-level falsification found at `b5df849`; Advisor and
Kongming still require runtime and migration evidence. These verdicts do not
approve authenticated parity, visual fidelity, Java cutover, or production
readiness. A later documentation-only commit does not create implementation
approval.

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
