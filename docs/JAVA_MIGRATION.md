# Java Migration Boundary

CampusCore currently runs NestJS/TypeScript services. The Java target is one
`java-services/restful-api` modular monolith; the root `java-services/pom.xml`
reactor builds only that application. The NestJS stack remains the canonical
writer until a Java wave has passed contract, data, authorization,
observability, and rollback gates. Retained sibling Java services are
shadow/rollback source only and must be built through an explicit child
`pom.xml` when a compatibility rehearsal needs one.

## First boundary

`java-services/thesis-service` is an isolated Spring Boot service. It is not yet
a replacement for `academic-service` and must not receive shared staging or
production traffic. The dedicated `k8s/overlays/thesis-pilot` overlay now gives
it an isolated namespace, readiness-gated Deployment, and explicit nginx route
for production-shaped validation; the canonical base and generic overlays stay
on the nine-image release baseline. Its tables use a dedicated `thesis` schema
so legacy Prisma `db push` cannot own or remove them.

The service targets Java 21, uses Flyway for versioned SQL migrations, validates
existing access-token JWTs with `JWT_SECRET`, accepts the legacy access-token
cookie or bearer header, and applies the existing cookie CSRF contract to
mutating requests.

`contracts/thesis-public-contract.json` is the checked-in source contract for
the 22 Java thesis mappings currently exposed by the controller. The
`scripts/check-thesis-contract.mjs` gate also checks the eight FE bindings and
the pilot-enabled versus production-disabled nginx fragments. It catches source
drift only; it does not prove response, auth, database, event, or rollback
parity.

## Migration rules

1. Keep one canonical writer for each migrated domain.
2. Do not use `ddl-auto=update`, `prisma db push`, or destructive migrations for
   production data.
3. Compare status, body, headers, cookies, errors, authorization, and event
   behavior before gateway cutover.
4. Use expand/contract migrations and a tested restore path before a write
   handover.
5. Keep provider keys in the runtime secret manager. Never commit `.env` or a
   real LLM key.

## Local verification

```text
# Canonical Java modular-monolith build
mvn -f java-services/pom.xml test
mvn -f java-services/pom.xml verify

# A retained shadow boundary, only when a bounded rehearsal requires it
mvn -f java-services/thesis-service/pom.xml verify
```

The current checkout does not claim a production cutover. Existing Node tests
remain required while the old services are still canonical.

## RESTful API read candidates

The `java-services/restful-api` monolith now contains disabled-by-default,
read-only candidates for thesis, notification, engagement announcement and
academic catalog reads plus people student/lecturer profile reads. The academic
slice covers semester/course list and detail routes only. The people slice
covers student/lecturer list and detail routes only. These candidates use JDBC
against legacy schemas when their feature flags are explicitly enabled under
the `persistence` profile; they do not add DDL, write data, or own public
traffic.

## RESTful API auth candidate

The monolith also contains a disabled-by-default Java auth session candidate
behind `AUTH_LOGIN_ENABLED=true`. It verifies BCrypt passwords against the
migrated legacy `auth` schema, emits and clears the shared `cc_access_token`,
`cc_refresh_token` and `cc_csrf` browser cookies, returns body tokens for mobile
clients, records failed login attempts, locks after the fifth failed attempt,
stores only hashed refresh sessions, returns the current authenticated user at
`/api/v1/auth/me`, updates current-user profile fields, rotates refresh tokens,
changes passwords while revoking stored refresh sessions, and clears sessions
on logout. It is not a full auth cutover: registration, forgot/reset password,
email verification, audit publishing, PostgreSQL parity, route canary and
rollback evidence remain required before public ownership can move.

## Implemented slice

- Registration rounds with explicit lifecycle transitions.
- Published thesis topics and coordinator approval boundary.
- Student groups with a database-enforced maximum of three members per round.
- Defense councils with three-to-five-member scheduling validation.
- Council reviews, score locking, and result publication after all council
  members submit a score.
- Server-side read-only assistant with permission-filtered thesis context,
  Redis rate limiting, bounded provider timeout, and no mutation tools.
- Next.js bilingual thesis workspace and session-only assistant panel.
- Isolated Kubernetes thesis pilot route with Postgres/Redis startup waits,
  secret-backed JWT/readiness settings, and local-only Java image provenance.

The old Node services remain canonical for all existing CampusCore domains. This
branch is not a full backend cutover and must not be described as production
ready until differential parity, data reconciliation, gateway canary, image
provenance, and rollback gates pass.

The local Compose thesis route is enabled only when the matching Java service
and route fragments are mounted. The semver production Compose file mounts
comment-only fragments, so a release containing only the nine public images
cannot accidentally resolve `thesis-service:4010`.
