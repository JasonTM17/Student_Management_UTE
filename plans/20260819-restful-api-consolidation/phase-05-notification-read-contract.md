# Phase 05 — Notification read contract in the Java RESTful API

## Status

**Candidate implemented / HOLD for runtime acceptance.** The disabled-by-
default JDBC read adapter and source-level tests are present. No public traffic
or writer ownership moved. Acceptance still requires an isolated PostgreSQL
schema/permission check, subject-claim/auth verification, and a differential
Node-versus-Java run.

## Outcome and success signal

Expose the authenticated user's notification inbox and unread count from the
single `java-services/restful-api` application while the legacy notification
service remains the canonical writer, Socket.IO owner, RabbitMQ consumer, and
public route owner. The success signal is a repeatable, side-by-side response
comparison for the same bearer subject and database snapshot, followed by a
reversible read-only route shadow/canary decision.

This phase is not complete when Java compiles alone. It is complete only when
the response shape, pagination/filter semantics, user isolation, timestamps,
error behavior, and rollback procedure have been exercised against a restored
PostgreSQL copy and the exact reviewed candidate.

## Authority, scope, and non-goals

### Authority

- Integration owner controls the monolith changes, migration flag, evidence,
  commit, and any later route decision.
- Legacy notification service remains the canonical writer and public owner
  until an explicit, reviewed cutover decision.
- A Java database credential used by this phase must be read-only; no Flyway
  DDL or data mutation is allowed against the legacy notification schema.

### In scope

- `GET /api/v1/notifications/my`
- `GET /api/v1/notifications/my/unread-count`
- Java domain/repository/service/controller code inside
  `java-services/restful-api`, behind an explicit migration property.
- Contract tests, negative user-isolation tests, and a differential comparison
  harness or documented equivalent.
- Mapping the legacy `notifications.notification` table into a read model.

### Non-goals

- No create/update/delete endpoints, mark-read, mark-all-read, or notification
  event consumption in this phase.
- No Socket.IO gateway, RabbitMQ consumer, websocket/realtime parity, or
  notification writer move.
- No schema rename, destructive migration, ownership transfer, or public
  proxy/edge route switch.
- No client cutover until the authenticated differential and rollback gates
  pass; preview mode remains explicitly local and unauthenticated.

## Current source evidence (not live proof)

The following facts are read from the repository and must be rechecked against
the deployed legacy service before implementation is accepted:

| Boundary | Observed source contract | Required confirmation |
| --- | --- | --- |
| Legacy list route | `GET /api/v1/notifications/my`; defaults `page=1`, `limit=20`; optional `isRead`; ordered by `createdAt desc`; response `{data, meta}` with `total`, `page`, `limit`, `totalPages`. | Authenticated HTTP probe and fixture with empty, read, unread, and multi-page rows. |
| Legacy unread route | `GET /api/v1/notifications/my/unread-count`; response `{unreadCount}`. | Authenticated HTTP probe plus count cross-check against the same snapshot. |
| Subject isolation | Controller obtains `@CurrentUser('id')`; service filters by `userId`. | Two-subject negative test, including a valid token that requests another ID indirectly. |
| Legacy record fields | `id`, `userId`, `title`, `message`, `type`, nullable `link`, `isRead`, nullable `readAt`, `createdAt`, `updatedAt`. | JSON and column-level sample from a restored copy; verify casing and timezone. |
| Legacy table | `notifications.notification`, indexed by `(user_id, created_at)` and `is_read`. | Read-only schema inspection and permission check. |
| Frontend consumer | `notificationsApi.getMy` consumes `data`/`meta` and normalizes `message` to `content`; unread count is used by the dashboard shell. | Browser/API contract run with real auth; no preview success may be counted. |

## Candidate implementation evidence

The source-level candidate is documented in
`reports/notification-read-candidate.md`. It uses string-preserving JDBC,
qualified `notifications.notification` reads, an explicit
`migration.notifications-read.enabled` flag, and no Flyway migration or write
path. The default route-disabled contract remains covered.

The current local compose PostgreSQL stack is **not** the required fixture: a
catalog-only read on 2026-08-20 found the `notifications` schema but no
`notifications.notification` table, and a legacy notification-service log
showed a database-connectivity error. No bootstrap, migration, restart, or
other mutation was attempted. Treat this as `BLOCKED_CAPABILITY` for the
PostgreSQL gate, not as proof of absence in the intended legacy deployment.

The existing standalone Java notification service is useful as source
material, but it is not proof for the monolith: it currently has separate
application configuration and its list implementation must be checked for
`isRead` and response-shape parity before any code is reused.

Later focused PostgreSQL compatibility evidence was observed against the
disposable local cluster on `127.0.0.1:56439` with
`currentSchema=notifications`:

```powershell
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.notification.NotificationReadPersistenceTest' '-DforkCount=0' test
```

Surefire summary: 6 tests / 0 failures / 0 errors / 0 skipped. This closes the
H2-only syntax/type uncertainty for the selected notification read tests, but
does not close the restored legacy dataset, read-only role, Node-versus-Java
differential, route canary, rollback or public traffic handoff gates.

## Proposed implementation boundary

1. Add a disabled-by-default property such as
   `migration.notifications-read.enabled=false` to the monolith.
2. Add a read-only notification package with explicit DTOs rather than
   exposing JPA entities. Derive `userId` only from the authenticated JWT
   subject; never accept it as a list query parameter for the personal route.
3. Use a repository query that applies `userId`, optional `isRead`, stable
   descending `createdAt` ordering, and bounded page/limit validation. Preserve
   the legacy envelope instead of returning Spring's default `Page` shape.
4. Add a separate unread-count query with the same subject boundary.
5. Keep the module disabled in the default profile and leave the legacy edge
   route untouched. If a shadow mode is introduced, compare responses without
   replacing the client response.

The table/schema mapping, UUID assumptions, null handling, timestamp precision,
and `isRead` query parsing remain unresolved until the live contract and an
isolated PostgreSQL copy are available. Do not silently infer them from H2.

## Ordered work and ownership

1. **Source contract inventory — integration owner (complete).** The current
   Node source, Prisma schema, fixtures, and frontend envelope are recorded;
   live HTTP/auth freeze remains open.
2. **Schema read audit — backend owner (open).** Restore or provision a bounded
   PostgreSQL copy, inspect the exact table/indexes, create a read-only role,
   and prove no write/DDL permission.
3. **Java read model — backend owner (source-level complete).** Implemented
   only the two GET routes and the feature flag; added Spring MVC/H2 positive
   and negative tests. PostgreSQL runtime remains open.
4. **Differential gate — integration owner (open).** Run Node and Java against the
   same immutable fixture and compare normalized JSON, ordering, pagination,
   filtering, counts, and error envelopes.
5. **Independent review — Advisor/Kongming/Wukong (open for this new code).** Freeze the exact commit;
   review architecture and auth isolation read-only; adversarially test the
   claim that no user can read another user's rows. Any verdict is stale after
   a rebase or code change.
6. **Canary decision — integration owner.** Only after all gates pass, record
   an explicit shadow/canary/rollback decision. A local commit is not a
   deployment or production cutover.

## Acceptance criteria and verification commands

Source-level gates:

```powershell
Set-Location java-services/restful-api
mvn -q -f pom.xml -DskipTests compile
mvn -q -f pom.xml test
Set-Location ../..
git diff --check
```

Runtime gates (must be observed, not inferred):

- Java startup with the feature disabled has no notification route exposure or
  database write attempt.
- Java startup with the feature enabled connects using the read-only role and
  passes schema validation against the restored PostgreSQL copy.
- Missing/invalid bearer, malformed subject, another user's rows, `isRead`
  true/false, page boundaries, empty results, and unread-count consistency all
  have recorded outcomes.
- Normalized Node/Java responses match for the same fixture, including field
  names, nulls, ordering, total-pages calculation, and UTC serialization.
- Rollback to the legacy route is rehearsed without data mutation and without
  requiring a Java writer.

If Maven or PostgreSQL cannot run because of the environment, record
`NOT_RUN`/`BLOCKED_CAPABILITY` with the exact command and limitation; do not
convert source inspection into a passing runtime gate.

## Risks, rollback, and recovery

- **Schema drift:** fail closed and keep the flag disabled; do not alter the
  legacy schema from the monolith.
- **Subject mismatch:** return the standard unauthenticated envelope and stop
  the slice; never fall back to a query parameter or a preview role.
- **Pagination mismatch:** keep the legacy route canonical and block canary;
  fix the adapter/query before another review.
- **Resource pressure:** do not install dependencies, build images, or rerun
  memory-heavy Maven jobs while C: is critically low. Use a bounded external
  runtime only after its ownership and cleanup are explicit.
- **Rollback:** disable the migration property or remove the shadow route;
  restore legacy public routing. Since this phase is read-only, no data repair
  should be required.

## Documentation and unresolved decisions

- Exact auth issuer/subject format and whether legacy IDs are always UUIDs.
- Whether the monolith can reach the legacy `notifications` schema through a
  safe read-only connection while the thesis schema remains the default.
- Whether the edge can shadow Java without changing the user-visible Node
  response.
- Normalization rules for Java `Page` versus the legacy `{data, meta}`
  envelope and frontend `message`/`content` compatibility.
- Available PostgreSQL restore, browser, and authenticated test credentials.
