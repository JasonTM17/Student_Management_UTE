# Phase 13 — Backend-first auth and academic read foundation

## Outcome

Add two small Java monolith foundations needed before a serious Stitch
web/mobile authenticated parity wave:

- a server-side access-token issuer that produces the same claim shape the
  existing Java resource server already decodes;
- a server-side refresh-token issuer with a dedicated configurable secret and
  minimal refresh claims, plus hashed refresh-session persistence only through
  the explicitly enabled login candidate;
- a feature-gated, read-only academic catalog candidate for semesters and
  courses.

This is not an auth cutover, refresh endpoint, logout implementation,
revocation store, public route switch, enrollment/grade migration, PostgreSQL
parity claim, canary, rollback, frontend change or mobile runtime claim.

## Scope and authority

In scope:

- keep auth ownership with the legacy/auth boundary while adding a Java
  `AuthTokenService` that can be used by the future login/refresh owner;
- preserve cookie-or-bearer decoding, role/permission authority mapping and
  CSRF behavior;
- expose Java login only when both the `persistence` profile and
  `migration.auth-login.enabled=true` are active, using the legacy `auth`
  schema and retaining legacy auth-service as the canonical owner;
- expose academic catalog reads only when both the `persistence` profile and
  `migration.academic-read.enabled=true` are active;
- use JDBC `SELECT` queries only against the legacy Prisma academic schema;
- use JDBC reads and the bounded login writes against the migrated legacy
  `auth` schema only while the explicit login flag is enabled;
- keep the default API shell returning `404` for academic routes.

Non-goals:

- no refresh endpoint, logout, password reset/change, audit publisher,
  revocation store or route ownership move in this phase;
- no schema DDL, Flyway migration, enrollment, timetable, class, grade or
  academic write path;
- no client rewiring and no claim that Stitch authenticated E2E is now complete.

## Acceptance criteria

- `AuthTokenService` rejects invalid identity/authority inputs and issues HS256
  access tokens that decode through `SecurityConfig.jwtDecoder`.
- Issued tokens preserve subject, email, roles, permissions and optional
  student/lecturer context used by web and mobile.
- Refresh tokens use the refresh secret, carry `tokenType=refresh`, and avoid
  embedding role/permission authority claims.
- Auth login is default-off, stores only hashed refresh sessions, increments
  failed attempts, locks after the fifth failed login and emits the shared
  browser cookie contract when explicitly enabled in H2 tests.
- Academic read routes remain disabled by default in the shell contract.
- Legacy-schema migration safety covers academic read and auth login modes so
  Hibernate DDL and Flyway are not accidentally used as a migration authority
  for these candidates.
- Academic catalog H2/MockMvc tests cover semester/course ordering, pagination,
  list/detail envelopes, localization fallback, anonymous access, invalid query
  parameters and not-found behavior.
- Root reactor test and `git diff --check` pass on the exact source snapshot.

## Verification

Observed local gates for this phase on Windows with low pagefile pressure used
`forkCount=0` and a bounded Maven heap:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=AuthLoginPersistenceTest,AuthTokenServiceTest,SecurityConfigTest,CsrfCookieFilterTest,RestfulApiContractTest,MigrationSafetyConfigTest' '-DforkCount=0' test
mvn -q -f java-services/pom.xml '-DforkCount=0' test
git diff --check
```

All three commands passed locally against H2/source tests before commit. These
gates are source/H2/local evidence only. PostgreSQL restore parity, runtime
smoke, route canary, rollback and independent final review remain open.
