# Phase 24 — Engagement support-ticket assignment candidate

## Outcome

Add the Java REST API monolith's feature-default-off support-ticket assignment
candidate while preserving the legacy engagement service as the public
assignment owner and rollback target.

## Boundary and authority

- Candidate route: `POST /api/v1/support-tickets/{id}/assign`.
- The route exists only with the `persistence` profile and
  `migration.engagement-write.enabled=true`; `ENGAGEMENT_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- This phase updates only `"assignedTo"` and `"updatedAt"` on the Prisma-owned
  `"engagement"."SupportTicket"` table and returns the hydrated legacy ticket
  shape, including existing responses.
- This phase does not add deletion, RabbitMQ publishing, notification fan-out,
  nginx routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and request field: `assignedTo`.
- Preserve the legacy `assignedTo`-only write; Java does not invent or resolve
  `assignedToDisplayName`.
- Preserve existing response hydration and current ticket fields after the
  assignment update.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin assignment and hydrated response shape;
  - preservation of `assignedToDisplayName = null`;
  - response hydration after assignment;
  - student access denied, missing ticket and missing assignee validation.
- The monolith shell contract covers feature-default-off behavior for
  `POST /api/v1/support-tickets/{id}/assign`.

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

This is still source/H2 evidence only. PostgreSQL write parity, assignment
semantics under the real schema, event/notification parity, route canary,
rollback rehearsal, public assignment handoff and independent
Advisor/Kongming/Wukong exact-head review remain `NOT_RUN`.
