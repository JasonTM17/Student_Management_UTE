# Phase 25 — Engagement support-ticket deletion candidate

## Outcome

Add the Java REST API monolith's feature-default-off support-ticket deletion
candidate while preserving the legacy engagement service as the public deletion
owner, canonical writer and rollback target.

## Boundary and authority

- Candidate route: `DELETE /api/v1/support-tickets/{id}`.
- The route exists only with the `persistence` profile and
  `migration.engagement-write.enabled=true`; `ENGAGEMENT_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java verifies the ticket exists, deletes only the Prisma-owned
  `"engagement"."SupportTicket"` row, and returns the legacy success envelope:
  `{ "message": "Support ticket deleted successfully" }`.
- The real Prisma relationship owns response cleanup through
  `"TicketResponse" -> "SupportTicket"` `ON DELETE CASCADE`; Java does not add
  manual response deletion, DDL, RabbitMQ publishing, notification fan-out,
  nginx routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy path, method and admin-only authorization.
- Preserve the legacy not-found behavior by probing the ticket before deletion.
- Preserve database-owned cascade semantics for responses instead of inventing a
  second Java cleanup workflow.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin deletion and the exact legacy success message;
  - database cascade of existing responses through an H2 FK matching the Prisma
    relation;
  - student access denied and missing-ticket not found behavior.
- The monolith shell contract covers feature-default-off behavior for
  `DELETE /api/v1/support-tickets/{id}`.

## Verification observed

Current focused Java gate passed locally on Windows, using repository-local temp
settings on the D drive:

```powershell
$env:TEMP='<d-drive-repo-temp>'
$env:TMP='<d-drive-repo-temp>'
$env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Djava.io.tmpdir=<d-drive-repo-temp>'
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.engagement.SupportTicketWriteServiceTest,io.campuscore.restfulapi.engagement.SupportTicketWritePersistenceTest,io.campuscore.restfulapi.engagement.SupportTicketReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test
```

Surefire recorded:

- `SupportTicketWritePersistenceTest`: 17/17 passed, including blank-string
  response/update parity regressions added after the reviewer finding.
- `SupportTicketWriteServiceTest`: 1/1 passed.
- `SupportTicketReadPersistenceTest`: 4/4 passed.
- `RestfulApiContractTest`: 21/21 passed.
- `MigrationSafetyConfigTest`: 3/3 passed.

The root Java reactor also passed locally with the same D-drive temp posture:

```powershell
mvn -q -f java-services/pom.xml test
```

Surefire recorded 113 tests, 0 failures, 0 errors and 0 skipped across 19
report files.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, deletion
semantics under the real schema, event/notification parity, route canary,
rollback rehearsal, public deletion handoff and independent
Advisor/Kongming/Wukong exact-head review remain `NOT_RUN`.
