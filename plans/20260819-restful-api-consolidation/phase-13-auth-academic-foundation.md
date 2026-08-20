# Phase 13 — Backend-first auth and academic read foundation

## Outcome

Add two small Java monolith foundations needed before a serious Stitch
web/mobile authenticated parity wave:

- a server-side access-token issuer that produces the same claim shape the
  existing Java resource server already decodes;
- a server-side refresh-token issuer with a dedicated configurable secret and
  minimal refresh claims, plus hashed refresh-session persistence only through
  the explicitly enabled auth session candidate;
- feature-gated login, current-user read, refresh rotation and logout-clearing
  endpoints that preserve the shared web cookie/CSRF contract and the mobile
  body-token contract without taking public route ownership;
- feature-gated profile update and password change endpoints for the current
  authenticated user, preserving web CSRF protection and revoking refresh
  sessions after a password change;
- a feature-gated, read-only academic catalog candidate for semesters and
  courses.

This is not an auth cutover, registration implementation, password reset/email
implementation, audit publisher, public route switch, enrollment/grade
migration, PostgreSQL parity claim, canary, rollback, frontend change or mobile
runtime claim.

## Scope and authority

In scope:

- keep auth ownership with the legacy/auth boundary while adding a Java
  `AuthTokenService` that can be used by the future login/refresh owner;
- preserve cookie-or-bearer decoding, role/permission authority mapping and
  CSRF behavior;
- expose Java login only when both the `persistence` profile and
  `migration.auth-login.enabled=true` are active, using the legacy `auth`
  schema and retaining legacy auth-service as the canonical owner;
- expose Java refresh and logout only under the same auth flag, rotating hashed
  refresh sessions, supporting bearer/body-token mobile clients, and clearing
  the shared web cookies without moving public ownership;
- expose Java `/api/v1/auth/me` only under the same auth flag, resolving the
  current user by JWT subject from the migrated legacy `auth` schema;
- expose Java profile update and password change only under the same auth flag,
  preserving the shared cookie/CSRF mutation contract and revoking refresh
  sessions after a successful password change;
- expose academic catalog reads only when both the `persistence` profile and
  `migration.academic-read.enabled=true` are active;
- use JDBC `SELECT` queries only against the legacy Prisma academic schema;
- use JDBC reads and the bounded auth session writes against the migrated
  legacy `auth` schema only while the explicit auth flag is enabled;
- keep the default API shell returning `404` for academic routes.

Non-goals:

- no registration, forgot/reset password flow, email verification, audit
  publisher, public route ownership move or production revocation parity claim
  in this phase;
- no schema DDL, Flyway migration, enrollment, timetable, class, grade or
  academic write path;
- no client rewiring and no claim that Stitch authenticated E2E is now complete.

## Acceptance criteria

- `AuthTokenService` rejects invalid identity/authority inputs and issues HS256
  access tokens that decode through `SecurityConfig.jwtDecoder`.
- Issued tokens preserve subject, email, roles, permissions and optional
  student/lecturer context used by web and mobile.
- Refresh tokens use the refresh secret, carry `tokenType=refresh`, include a
  unique token id for rotation, and avoid embedding role/permission authority
  claims.
- Auth session endpoints are default-off. When explicitly enabled in H2 tests,
  login stores only hashed refresh sessions, increments failed attempts, locks
  after the fifth failed login, refresh requires the browser CSRF double-submit
  contract when cookie-backed, rotates the stored refresh hash, and logout
  clears shared cookies plus the stored session.
- Mobile-style refresh/logout accepts body refresh tokens without depending on
  browser cookies; logout still requires an authenticated access token.
- `/api/v1/auth/me` returns the same FE-facing user shape for bearer and cookie
  access sessions while inactive or missing users fail closed.
- `/api/v1/auth/profile` preserves existing values when fields are omitted,
  persists legacy `auth.User` profile fields, and requires the browser CSRF
  double-submit contract for cookie-backed mutation.
- `/api/v1/auth/change-password` rejects anonymous and wrong-password attempts,
  updates the BCrypt password hash, sets `passwordChangedAt`, clears the legacy
  user refresh token, revokes stored refresh sessions, and allows subsequent
  login only with the new password.
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
