# Phase 62 — Academic curriculum differential and rollback harness

## Outcome

Prove the existing Phase 53 academic curricula read candidate can be compared
through a dedicated legacy → Java → legacy differential harness with route-
switch rollback staging, without widening scope to curriculum writes, client
cutover, or production claims.

This phase stays backend-first and read-only. It does not move public routes or
writer ownership.

## Scope

In scope:

- add a dedicated academic curriculum differential/rehearsal harness;
- compare `GET /api/v1/curricula` and `GET /api/v1/curricula/{id}`;
- preserve the legacy list contract of no curriculum-course hydration on list;
- preserve detail-only curriculum-course hydration and 404 not-found behavior;
- add a bounded route-switch rollback check in the harness itself.

Out of scope:

- curriculum create/update/delete;
- public client wiring or FE cutover;
- production traffic switch or writer handoff.

## Evidence

Observed on the current backend provenance checkpoint after the exact-head
review pack rebinding:

- source commit: `09ae74a83320c24d075e675369f29dcc2735fd94`
- harness file: `scripts/run-academic-curriculum-differential-rehearsal.mjs`

Verification run:

```powershell
node --check scripts/run-academic-curriculum-differential-rehearsal.mjs
node scripts/run-academic-curriculum-differential-rehearsal.mjs --self-test
node scripts/run-academic-curriculum-differential-rehearsal.mjs --self-test --edge-route-switch
```

Result:

- syntax check: PASS
- self-test: PASS
- edge-route-switch self-test: PASS

The live PostgreSQL curricula restore target still needs a non-empty restored
fixture before this rehearsal can be upgraded from harness validation to live
legacy-versus-Java differential evidence.

Live rehearsal evidence now captured on the disposable PostgreSQL restore at
`127.0.0.1:56452` with legacy service `127.0.0.1:4003` and Java candidate
`127.0.0.1:4010`:

```powershell
$env:ACADEMIC_CURRICULUM_DIFF_LEGACY_BASE_URL='http://127.0.0.1:4003'
$env:ACADEMIC_CURRICULUM_DIFF_JAVA_BASE_URL='http://127.0.0.1:4010'
$env:JWT_SECRET='abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789'
node scripts/run-academic-curriculum-differential-rehearsal.mjs
node scripts/run-academic-curriculum-differential-rehearsal.mjs --edge-route-switch
```

Result:

- live differential: PASS
- live edge-route-switch rollback: PASS

## Remaining holds

This phase now proves both harness shape and live restored-PostgreSQL parity
for the seeded curricula restore. The remaining work is checkpoint hygiene:
commit the exact source/docs state, push it safely, and continue with the next
backend slice.
