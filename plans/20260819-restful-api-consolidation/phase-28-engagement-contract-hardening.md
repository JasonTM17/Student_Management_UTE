# Phase 28 — Engagement contract hardening

## Outcome

Harden the Java REST API engagement candidate after exact-head review found
request-contract and visibility gaps in the source/H2 slices. This is still a
feature-default-off source candidate, not a public route handoff.

## Boundary and authority

- Applies to the existing Java engagement announcement and support-ticket
  candidate routes only.
- Keeps the legacy engagement service as the public route owner, canonical
  writer, RabbitMQ/event publisher, notification fan-out owner and rollback
  target.
- Adds no DDL, no new table ownership, no public routing change, no RabbitMQ
  publisher and no notification write path.

## Compatibility and safety contract

- Reject unknown JSON body properties for support-ticket create, response,
  update and assignment mutations instead of silently ignoring typos.
- Parse announcement create/update bodies through an explicit JSON-node mapper
  so malformed scalar, array, boolean and timestamp fields fail closed as
  `INVALID_REQUEST`.
- Preserve omitted-versus-explicit-null behavior for announcement updates:
  omitted fields remain unchanged, while explicit `null` clears nullable
  timestamps and academic pointer fields.
- Keep `priority`, `targetRoles` and `targetYears` strict: invalid enum values,
  wrong target-role element types and non-positive target years fail closed.
- Hide `isInternal=true` support-ticket responses from user self-service list
  and detail reads, while preserving full response visibility on admin reads.
  This intentionally differs from the current legacy `/my` behavior and blocks
  public route ownership until a coordinated security change is accepted.

## Verification observed

- Focused H2/source gate passed on 2026-08-20:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.engagement.AnnouncementWritePersistenceTest,io.campuscore.restfulapi.engagement.AnnouncementReadPersistenceTest,io.campuscore.restfulapi.engagement.SupportTicketReadPersistenceTest,io.campuscore.restfulapi.engagement.SupportTicketWritePersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 57 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-20:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 116 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, RabbitMQ/event
parity, notification fan-out parity, route canary, rollback rehearsal, public
writer handoff and a fresh independent exact-head Advisor/Kongming/Wukong
review for the final handoff remain `NOT_RUN`.
