# Phase 23 — Engagement support-ticket update candidate

## Outcome

Add the Java REST API monolith's feature-default-off support-ticket update
candidate while preserving the legacy engagement service as the public update
owner and rollback target.

## Boundary and authority

- Candidate route: `PUT /api/v1/support-tickets/{id}`.
- The route exists only with the `persistence` profile and
  `migration.engagement-write.enabled=true`; `ENGAGEMENT_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- This phase updates only the Prisma-owned `"engagement"."SupportTicket"` table
  and returns the hydrated legacy ticket shape, including existing responses.
- This phase does not add assignment, deletion, RabbitMQ publishing,
  notification fan-out, nginx routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and partial update fields:
  `subject`, `description`, `category`, `priority`, and `status`.
- Preserve priority values `LOW`, `MEDIUM`, `HIGH`, and `CRITICAL`.
- Preserve status values `OPEN`, `IN_PROGRESS`, `RESOLVED`, and `CLOSED`.
- Preserve the legacy timestamp side effects: setting status `RESOLVED` sets
  `resolvedAt`; setting status `CLOSED` sets `closedAt`.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin partial field update and hydrated response shape;
  - response hydration after update;
  - `RESOLVED` timestamp side effect;
  - `CLOSED` timestamp side effect while preserving an existing `resolvedAt`;
  - student access denied, missing ticket, invalid status and invalid priority.
- The monolith shell contract covers feature-default-off behavior for
  `PUT /api/v1/support-tickets/{id}`.

## Remaining gates

## Verification observed

Current exact-head focused gate passed locally on Windows, using Maven heap and
temp settings that kept test artifacts off the low-space system drive:

```powershell
$env:JAVA_HOME='<java-home>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Djava.io.tmpdir=<d-drive-temp>'
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.engagement.SupportTicketWriteServiceTest,io.campuscore.restfulapi.engagement.SupportTicketWritePersistenceTest,io.campuscore.restfulapi.engagement.SupportTicketReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test
```

This is still source/H2 evidence only. PostgreSQL write parity, timestamp parity
under the real schema, event/notification parity, route canary, rollback
rehearsal, public update handoff and independent Advisor/Kongming/Wukong
exact-head review remain `NOT_RUN`.
