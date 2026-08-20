# Phase 13 — Backend-first auth and academic read foundation

## Outcome

Add two small Java monolith foundations needed before a serious Stitch
web/mobile authenticated parity wave:

- a server-side access-token issuer that produces the same claim shape the
  existing Java resource server already decodes;
- a feature-gated, read-only academic catalog candidate for semesters and
  courses.

This is not a login cutover, refresh-token implementation, revocation store,
public route switch, enrollment/grade migration, PostgreSQL parity claim,
canary, rollback, frontend change or mobile runtime claim.

## Scope and authority

In scope:

- keep auth ownership with the legacy/auth boundary while adding a Java
  `AuthTokenService` that can be used by the future login owner;
- preserve cookie-or-bearer decoding, role/permission authority mapping and
  CSRF behavior;
- expose academic catalog reads only when both the `persistence` profile and
  `migration.academic-read.enabled=true` are active;
- use JDBC `SELECT` queries only against the legacy Prisma academic schema;
- keep the default API shell returning `404` for academic routes.

Non-goals:

- no password check, user repository, refresh token, logout, session store or
  route ownership move in this phase;
- no schema DDL, Flyway migration, enrollment, timetable, class, grade or
  academic write path;
- no client rewiring and no claim that Stitch authenticated E2E is now complete.

## Acceptance criteria

- `AuthTokenService` rejects invalid identity/authority inputs and issues HS256
  access tokens that decode through `SecurityConfig.jwtDecoder`.
- Issued tokens preserve subject, email, roles, permissions and optional
  student/lecturer context used by web and mobile.
- Academic read routes remain disabled by default in the shell contract.
- Read-only migration safety covers academic read mode so Hibernate DDL and
  Flyway are not accidentally used as a write/migration authority for this
  candidate.
- Academic catalog H2/MockMvc tests cover semester/course ordering, pagination,
  list/detail envelopes, localization fallback, anonymous access, invalid query
  parameters and not-found behavior.
- Root reactor test and `git diff --check` pass on the exact source snapshot.

## Verification

Planned gates for this phase:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=AuthTokenServiceTest,SecurityConfigTest,CsrfCookieFilterTest,AcademicReadPersistenceTest,RestfulApiContractTest,MigrationSafetyConfigTest' test
mvn -q -f java-services/pom.xml test
git diff --check
```

These gates are source/H2/local evidence only. PostgreSQL restore parity,
runtime smoke, route canary, rollback and independent final review remain open.
