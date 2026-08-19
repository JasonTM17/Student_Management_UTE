# Architecture review gate — one RESTful API direction

## Snapshot

- Reviewed exact head: `9ec033b5cd126c5d2051d45ac52bf7a8aee46b73`
- Branch: `feature/java-thesis-platform`
- Review mode: read-only, independent sidecar tasks
- Scope: whether the current microservice topology can be simplified to one
  Java RESTful API without an unsafe direct collapse

## Verdicts

| Reviewer | Verdict | Meaning |
| --- | --- | --- |
| Advisor | `CONDITIONAL` | The simplification is a good course-level direction, but Java runtime, parity and rollback are not proven. |
| Kongming | `CONDITIONAL` | Design one Java modular monolith and migrate by boundary; preserve legacy services and contracts until gates pass. |
| Wukong | `FALSIFIED` | The claim that a direct four-block collapse preserves all current behavior is false; route/auth/data/realtime/file/payment counterexamples exist. |

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

The integration owner may now create the single-app shell and migration fixtures
under the phase plan. The next review must pin the new exact commit and rerun all
three gates; these verdicts become stale as soon as the reviewed snapshot
changes.
