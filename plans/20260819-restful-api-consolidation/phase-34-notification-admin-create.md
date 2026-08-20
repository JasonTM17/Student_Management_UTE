# Phase 34 — Notification admin create candidate

## Outcome

Add the Java REST API monolith's feature-default-off admin notification create
candidate while preserving the legacy notification service as the public
notification writer, realtime delivery owner and rollback target.

## Boundary and authority

- Candidate route: `POST /api/v1/notifications`.
- The route exists only with the `persistence` profile and
  `migration.notifications-write.enabled=true`; `NOTIFICATIONS_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java accepts the legacy create fields: `userId`, `title`, `message`, `type`
  and optional `link`.
- Java persists a generated id, `isRead=false`, `readAt=null`, and matching
  create/update timestamps, then returns the shared notification response
  shape.
- This phase does not add realtime delivery, event publication, nginx routing,
  public ownership, PostgreSQL parity or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve `201 Created` for the admin create route.
- Preserve required string body fields for `userId`, `title`, `message` and
  `type`.
- Preserve valid notification types: `INFO`, `WARNING`, `ERROR`, `SUCCESS`.
- Preserve optional `link`, including omitted or `null` link values.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin create with legacy response shape;
  - generated id and persisted unread default;
  - optional link hydration;
  - invalid type rejection;
  - missing required field rejection;
  - unknown body property rejection;
  - student access denied;
  - no row inserted after failed create attempts.
- The monolith shell contract covers feature-default-off behavior for
  `POST /api/v1/notifications`.

## Verification observed

- Focused notification write/read/default-off H2 gate passed on 2026-08-21 with
  Java 24.0.2 and temporary files redirected to the repository-local `.tmp`
  directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.notification.NotificationWritePersistenceTest,io.campuscore.restfulapi.notification.NotificationReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 40 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 130 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, realtime delivery
and event parity, route canary, rollback rehearsal, public notification writer
handoff and independent exact-head Advisor/Kongming/Wukong review remain
`NOT_RUN`.
