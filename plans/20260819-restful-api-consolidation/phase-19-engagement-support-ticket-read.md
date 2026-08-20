# Phase 19 — Engagement support-ticket read candidate

## Outcome

Move the support-ticket read side of the engagement domain into the single Java
REST API as a legacy-compatible, read-only candidate. The Node engagement
service remains the route owner, canonical writer, responder/assignment owner,
RabbitMQ event publisher, and rollback target.

## Boundary and authority

- Candidate routes are `GET /api/v1/support-tickets/my`,
  `GET /api/v1/support-tickets/my/{id}`, admin-only
  `GET /api/v1/support-tickets`, and admin-only
  `GET /api/v1/support-tickets/{id}`.
- Routes exist only with the `persistence` profile and
  `migration.engagement-read.enabled=true`; `ENGAGEMENT_READ_ENABLED=false`
  remains the production default.
- JDBC reads the Prisma-owned `"engagement"."SupportTicket"` and
  `"engagement"."TicketResponse"` tables using `SELECT` only. This phase adds
  no JPA entity, Flyway migration, DDL, create, update, delete, assignment,
  response creation, or nginx routing change.
- The shared active PostgreSQL container is not a test target. Runtime parity
  must use the isolated rehearsal environment defined by Phase 11.

## Compatibility contract

- Preserve the Nest envelope `{ data, meta: { total, page, limit, totalPages } }`,
  one-based pagination, page-size range 1–200, newest-first ticket ordering,
  ascending response ordering, optional admin filters for `status`, `priority`
  and `category`, and rejection of unknown/repeated query parameters.
- `/my` and `/my/{id}` are scoped to the authenticated JWT subject. A user-owned
  detail request for another user's ticket fails as 404.
- `/support-tickets` and `/support-tickets/{id}` remain limited to `ADMIN` and
  `SUPER_ADMIN`.
- Responses retain flattened Prisma fields plus the derived `user` object on
  tickets and responses, including display-name fallback to email.
- Mutating legacy routes (`POST`, `PUT`, `assign`, `respond`, `DELETE`) remain
  out of scope and legacy-owned until writer handoff and rollback evidence pass.

## Acceptance and verification

- Feature-default-off route behavior remains covered by the monolith shell
  contract through the disabled migration flag posture.
- Feature-on H2 contract tests cover user list/detail, subject isolation, admin
  list/detail, filters, nested responses, pagination envelope, anonymous
  access, admin authorization, missing subject, invalid enum filters and
  unexpected query parameters.
- Static review proves the engagement runtime package contains no write
  statement or schema ownership path; `git diff --check` and the complete Java
  monolith test suite pass before commit when host capacity permits.

## Verification observed

- Focused support-ticket command passed locally on Windows with Java 24, using
  D: for JVM temporary files:

  ```powershell
  $env:JAVA_HOME='C:\Program Files\Java\jdk-24'
  $env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Djava.io.tmpdir=D:\Student_Management-recovery\java-test-temp'
  mvn -q -pl restful-api '-Dtest=SupportTicketReadPersistenceTest' test
  ```

- Engagement pair regression passed:

  ```powershell
  mvn -q -pl restful-api '-Dtest=AnnouncementReadPersistenceTest,SupportTicketReadPersistenceTest' test
  ```

- Static engagement scan found no runtime JDBC mutation/DDL statement and no
  optional-null SQL pattern.

This evidence is H2/Spring source evidence only. PostgreSQL differential reads,
route canary, live runtime smoke, writer handoff, rollback, and independent
exact-head review remain `NOT_RUN`.

## Remaining gates

No source or H2 result authorizes traffic. PostgreSQL differential reads with a
read-only role, authenticated source-current smoke, route comparison corpus,
canary metrics, route rollback, and fresh exact-head Advisor/Kongming/Wukong
review remain required before any cutover claim.
