# Phase 22 — Engagement support-ticket response candidate

## Outcome

Add the Java REST API monolith's feature-default-off support-ticket responder
candidate while preserving the legacy engagement service as the public responder
and rollback owner.

## Boundary and authority

- Candidate route: `POST /api/v1/support-tickets/{id}/respond`.
- The route exists only with the `persistence` profile and
  `migration.engagement-write.enabled=true`; `ENGAGEMENT_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- This phase inserts into the Prisma-owned `"engagement"."TicketResponse"`
  table and performs only the legacy `OPEN` to `IN_PROGRESS` ticket transition.
- This phase does not add assignment, arbitrary status edits, deletion, RabbitMQ
  publishing, notification fan-out, nginx routing, public ownership or
  production cutover.

## Compatibility contract

- Preserve the legacy path and response shape for `TicketResponse`.
- Preserve current-admin identity mapping from JWT subject/email and first/last
  display-name fallback to email.
- Preserve `isInternal=false` default when the request omits it.
- Preserve legacy behavior where a response to an `OPEN` ticket moves the ticket
  to `IN_PROGRESS`, while responses to already closed/non-open tickets do not
  reopen them.
- Keep the route feature-default-off so the Java shell still returns the stable
  404 envelope unless the write candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin response creation and response body shape;
  - `OPEN` to `IN_PROGRESS` transition;
  - explicit `isInternal=true` and email display fallback;
  - closed ticket response without reopening;
  - student access denied, missing ticket, invalid JWT claims and missing
    message validation.
- The monolith shell contract covers feature-default-off behavior for
  `POST /api/v1/support-tickets/{id}/respond`.

## Verification observed

Current focused and full Java gates passed locally on Windows, using Maven heap
and temp settings that kept test artifacts off the low-space system drive:

```powershell
$env:JAVA_HOME='<java-home>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Djava.io.tmpdir=<d-drive-temp>'
mvn -q -pl restful-api '-Dtest=SupportTicketWritePersistenceTest,SupportTicketWriteServiceTest,SupportTicketReadPersistenceTest,RestfulApiContractTest,MigrationSafetyConfigTest' test
mvn -q -f java-services/pom.xml test
```

## Remaining gates

This is still source/H2 evidence only. PostgreSQL write parity, status
transition parity under the real schema, event/notification parity, route
canary, rollback rehearsal, public responder handoff and independent
Advisor/Kongming/Wukong exact-head review remain `NOT_RUN`.
