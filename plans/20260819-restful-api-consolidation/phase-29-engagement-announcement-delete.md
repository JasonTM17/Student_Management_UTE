# Phase 29 — Engagement announcement deletion candidate

## Outcome

Add the Java REST API monolith's feature-default-off announcement deletion
candidate while preserving the legacy engagement service as the public
announcement writer, RabbitMQ/event publisher and rollback target.

## Boundary and authority

- Candidate route: `DELETE /api/v1/announcements/{id}`.
- The route exists only with the `persistence` profile and
  `migration.engagement-write.enabled=true`; `ENGAGEMENT_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java verifies the Prisma-owned `"engagement"."Announcement"` row exists,
  deletes only that row, and returns the legacy success message.
- This phase does not add RabbitMQ publication, notification fan-out, nginx
  routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve pre-delete not-found behavior for missing announcements.
- Preserve response body: `{ "message": "Announcement deleted successfully" }`.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.
- Treat RabbitMQ/event and notification behavior as still legacy-owned until a
  separate parity gate exists.

## Verification

- Feature-on H2 tests cover:
  - admin deletion with the legacy success message;
  - row removal from the Prisma-owned announcement table;
  - student access denied;
  - missing announcement not found;
  - no accidental deletion when an unauthorized or missing delete attempt fails.
- The monolith shell contract covers feature-default-off behavior for
  `DELETE /api/v1/announcements/{id}`.

## Verification observed

- Focused delete/default-off H2 gate passed on 2026-08-21 with JDK `26.0.1`
  and temp redirected to `D:\Student_Management-recovery\java-test-temp`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.engagement.AnnouncementWritePersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest' '-DforkCount=0' test`.
- Wider engagement write/read and migration shell gate passed on 2026-08-21
  with JDK `26.0.1` and temp redirected to
  `D:\Student_Management-recovery\java-test-temp`:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.engagement.SupportTicketWritePersistenceTest,io.campuscore.restfulapi.engagement.SupportTicketWriteServiceTest,io.campuscore.restfulapi.engagement.SupportTicketReadPersistenceTest,io.campuscore.restfulapi.engagement.AnnouncementWritePersistenceTest,io.campuscore.restfulapi.engagement.AnnouncementReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed wider result: 60 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with JDK `26.0.1` and temp
  redirected to `D:\Student_Management-recovery\java-test-temp`:
  `mvn -q -f java-services/pom.xml clean test`.
- Observed reactor result: 118 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, RabbitMQ/event
parity, notification fan-out parity, route canary, rollback rehearsal, public
writer handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
