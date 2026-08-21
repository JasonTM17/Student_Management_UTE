# Auth login PostgreSQL focused rehearsal — 2026-08-21

- Exact head: `e43ecf86f433afb0f409c0bd9f33d7c844ac1a9a`.
- Disposable PostgreSQL target: `127.0.0.1:56470`.
- Database: `postgres` with `currentSchema=auth`.
- Run:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.auth.AuthLoginPersistenceTest' '-DforkCount=0' test`
- Result: PASS.
- Surefire summary: `9 tests / 0 failures / 0 errors / 0 skipped`.
- Behavior covered on PostgreSQL: login, refresh rotation, `/api/v1/auth/me`,
  profile update, password change, logout and lockout.
- Limitation: focused PostgreSQL syntax/type parity only; no public auth route
  handoff, canary, rollback or mobile runtime proof.
