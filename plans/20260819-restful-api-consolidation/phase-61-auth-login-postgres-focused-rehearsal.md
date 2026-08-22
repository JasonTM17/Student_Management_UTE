# Phase 61 — Auth login PostgreSQL focused rehearsal

## Outcome

Prove the feature-gated auth login/session persistence slice runs against a
real disposable PostgreSQL 18.4 target on the current exact head, without
changing public route ownership or claiming auth cutover.

This phase does not move public auth traffic, registration, forgot/reset
password, email verification, mobile runtime, or public ownership.

## Scope

In scope:

- reuse the existing `AuthLoginPersistenceTest` source suite;
- run it against a disposable PostgreSQL target with the auth schema;
- verify login, refresh, `/api/v1/auth/me`, profile update, password change,
  logout and lockout behavior on real PostgreSQL syntax/types.

Out of scope:

- public auth route handoff or cutover;
- registration or forgot/reset password flows;
- external provider or mobile runtime proof;
- broader route canary / rollback evidence.

## Evidence

Observed on exact head `e43ecf86f433afb0f409c0bd9f33d7c844ac1a9a`:

```powershell
& 'C:\Users\Admin\scoop\apps\postgresql\current\bin\pg_ctl.exe' -D 'D:\Student_Management\.tmp\pg-phase53\cluster' -o '-p 56470 -h 127.0.0.1' start
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:56470/postgres?currentSchema=auth'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.auth.AuthLoginPersistenceTest' '-DforkCount=0' test
```

Live run env:

- disposable PostgreSQL target `127.0.0.1:56470`
- database `postgres`
- auth schema created by the test fixture

Result: PASS.

- Surefire summary: 9 tests / 0 failures / 0 errors / 0 skipped.
- The test exercised login, refresh rotation, `/api/v1/auth/me`, profile
  update, password change, logout and lockout behavior on real PostgreSQL.

## Remaining holds

This adds focused PostgreSQL evidence for the auth login/session slice, but it
still does not clear public auth route ownership, auth canary routing,
rollback, or wider client convergence.

## Source checkpoint refresh — 2026-08-22

- Source commit: `4838c323cc99755f15f2242597077c83a571a6da`
- `mvn -q -f java-services/pom.xml test`
  - result: PASS
  - Surefire summary: 27 reports / 178 tests / 0 failures / 0 errors / 1
    skipped
- `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.auth.AuthLoginPersistenceTest' '-DforkCount=0' test`
  - disposable PostgreSQL target `127.0.0.1:56473`
  - result: PASS
  - Surefire summary: 9 tests / 0 failures / 0 errors / 0 skipped
- This confirms the auth login/session rehearsal still holds on the refreshed
  source checkpoint while preserving the 2026-08-21 exact-head evidence above.

## Exact-head continuity note — 2026-08-22

- Current branch tip: `92da1dfc5c3b28847e3dae7911b1331dc783c022`
  (`docs(plan): record auth checkpoint at current tip`).
- This tip is docs-only on the same source checkpoint, so the auth rehearsal
  evidence above remains the relevant source behavior record.
- The exact-head review pack and execution ledger were rebound to the current
  tip so the backend continuation path is not tied to stale SHAs.
