# Phase 21 — Engagement support-ticket numbering guard

## Outcome

Harden the feature-default-off Java support-ticket create candidate so it no
longer depends on raw row count for `TKT-xxxxx` allocation. This makes the
candidate safer for later PostgreSQL rehearsal while keeping the legacy
engagement service as the only public writer.

## Boundary and authority

- Applies only to the Java `POST /api/v1/support-tickets` candidate behind the
  `persistence` profile and `migration.engagement-write.enabled=true`.
- The legacy Node engagement service remains canonical for public traffic,
  assignment, responses, status mutation, event publication, notification
  fan-out, route ownership and rollback.
- No nginx, deployment, database schema ownership, traffic ownership, or
  production cutover is changed by this phase.

## Compatibility contract

- Preserve the legacy response and ticket-number shape: `TKT-xxxxx`.
- Allocate the next candidate from the highest existing numeric `TKT-` suffix,
  not from row count, so deleted or imported rows do not force a duplicate.
- Retry a bounded unique-key collision rather than silently issuing a duplicate
  or widening the write boundary.
- Keep invalid priority, missing auth claim, anonymous and validation failures
  on the existing Java error envelopes.

## Verification

- `SupportTicketWritePersistenceTest` now defines a unique constraint for
  `ticketNumber` in the H2 fixture.
- The focused test suite covers:
  - default `TKT-00002` creation from an existing `TKT-00001`;
  - `TKT-00003` allocation when `TKT-00001` is absent but `TKT-00002` exists;
  - service-level retry after a translated unique-key collision;
  - default priority/status, explicit priority, email fallback, auth failures
    and validation errors.

Observed local gates on Windows with Java 24:

```powershell
$env:JAVA_HOME='<java-home>'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:MAVEN_OPTS='-Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m -Djava.io.tmpdir=<d-drive-temp>'
mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.engagement.SupportTicketWriteServiceTest,io.campuscore.restfulapi.engagement.SupportTicketWritePersistenceTest,io.campuscore.restfulapi.engagement.SupportTicketReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test
```

Static/docs gates observed:

- `git diff --check`: PASS.
- `node scripts/check-doc-hygiene.mjs`: PASS.
- `node scripts/check-architecture.mjs`: PASS.
- Engagement runtime SQL scan: PASS; only the intended support-ticket `INSERT`
  appears in the Java engagement runtime package.
- Full Java reactor, PostgreSQL write rehearsal and browser/mobile runtime are
  `NOT_RUN` for this phase.

## Remaining gates

This is still source/H2 evidence only. PostgreSQL duplicate-key translation,
real concurrent insert pressure, idempotency behavior, event/notification
parity, canary routing, rollback rehearsal and exact-head Advisor/Kongming/
Wukong review remain required before any public writer handoff.
