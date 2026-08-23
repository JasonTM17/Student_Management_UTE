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

Observed on exact head `e9ba1568e7ce14d4dc286f464b3344f6b22fa71b`:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:postgresql://127.0.0.1:56473/postgres?currentSchema=auth'
$env:SPRING_DATASOURCE_USERNAME = 'postgres'
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.auth.AuthLoginPersistenceTest' '-DforkCount=0' test
```

Live run env:

- disposable PostgreSQL target `127.0.0.1:56473`
- database `postgres`
- auth schema created by the test fixture

Result: PASS.

- Surefire summary: 9 tests / 0 failures / 0 errors / 0 skipped.
- The test exercised login, refresh rotation, `/api/v1/auth/me`, profile
  update, password change, logout and lockout behavior on real PostgreSQL.

Follow-up current-head verification:

```powershell
mvn -q -f java-services/pom.xml test
```
- Result: PASS. Canonical RESTful API sure-fire summary on the current
  checkout: 27 reports / 178 tests / 0 failures / 0 errors / 1 skipped.

## Remaining holds

This adds focused PostgreSQL evidence for the auth login/session slice, but it
still does not clear public auth route ownership, auth canary routing,
rollback, or wider client convergence.
