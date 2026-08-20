# Phase 20 — Engagement support-ticket create candidate

## Outcome

Add the first support-ticket write candidate to the Java REST API monolith while
keeping the legacy Node engagement service as the canonical writer, responder,
assignment owner, event publisher, route owner, and rollback target.

## Boundary and authority

- Candidate route: `POST /api/v1/support-tickets`.
- The route exists only with the `persistence` profile and
  `migration.engagement-write.enabled=true`; `ENGAGEMENT_WRITE_ENABLED=false`
  remains the production default.
- The Java candidate inserts a new row into the Prisma-owned
  `"engagement"."SupportTicket"` table and returns the same flattened
  ticket/user response shape used by the support-ticket read candidate.
- This phase does not add assignment, response creation, status update,
  deletion, RabbitMQ publishing, notification fan-out, nginx routing, public
  ownership, or production cutover.

## Compatibility contract

- Preserve the legacy create defaults: generated `TKT-xxxxx` number, `OPEN`
  status, `MEDIUM` default priority, JWT subject/email ownership, and display
  name fallback to email.
- Preserve priority validation for `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`.
- Feature-default-off behavior must continue returning the stable Java 404
  envelope for `POST /api/v1/support-tickets`.
- Because ticket-number generation follows the legacy count-based shape, public
  route ownership still needs a PostgreSQL concurrency/retry decision before
  cutover.

## Acceptance and verification

- Feature-on H2 tests cover ticket creation, default priority/status, explicit
  priority, email display fallback, anonymous access, missing email claim,
  invalid priority, and validation errors.
- The monolith shell contract covers feature-default-off behavior.
- `migration.engagement-write.enabled=true` participates in the migration
  safety condition, so Flyway/Hibernate schema ownership is rejected while this
  legacy-schema write candidate is enabled.

## Verification observed

- Current exact-head focused gate passed locally on Windows with Java 24, using
  Maven heap and temp settings that kept test artifacts off the low-space
  system drive:

  ```powershell
  $env:JAVA_HOME='C:\Program Files\Java\jdk-24'
  $env:Path="$env:JAVA_HOME\bin;$env:Path"
  $env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Djava.io.tmpdir=D:\Student_Management-recovery\java-test-temp'
  mvn -q -pl restful-api '-Dtest=SupportTicketWritePersistenceTest,SupportTicketReadPersistenceTest,RestfulApiContractTest,MigrationSafetyConfigTest' test
  ```

- Full Java reactor gate also passed locally on the same host/JDK:

  ```powershell
  $env:JAVA_HOME='C:\Program Files\Java\jdk-24'
  $env:Path="$env:JAVA_HOME\bin;$env:Path"
  $env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Djava.io.tmpdir=D:\Student_Management-recovery\java-test-temp'
  mvn -q -f java-services/pom.xml test
  ```

- `node scripts/check-doc-hygiene.mjs`: PASS.
- `node scripts/check-architecture.mjs`: PASS.
- `node scripts/check-thesis-contract.mjs`: PASS as a static source contract
  gate; runtime response, auth, mutation, data, image provenance, and rollback
  parity remain separate gates.
- Runtime engagement write/DDL scan found only the intended support-ticket
  `INSERT`; no `UPDATE`, `DELETE`, `CREATE`, `ALTER`, `DROP`, `TRUNCATE` or
  `MERGE` statement was present in the engagement runtime package.

This evidence is H2/Spring source evidence only. PostgreSQL differential writes,
idempotency/concurrency behavior, event parity, notification fan-out, route
canary, live runtime smoke, writer handoff, rollback and independent exact-head
review remain `NOT_RUN`.

## Remaining gates

No source or H2 result authorizes traffic. Before any write ownership move,
freeze the legacy create contract, compare Java and Node responses against a
disposable PostgreSQL restore, decide the ticket-number concurrency strategy,
prove event/notification behavior or explicitly defer it, rehearse route
rollback, and run fresh exact-head Advisor/Kongming/Wukong review.
