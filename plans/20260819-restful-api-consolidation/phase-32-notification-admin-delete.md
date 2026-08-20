# Phase 32 — Notification admin deletion candidate

## Outcome

Add the Java REST API monolith's feature-default-off admin notification deletion
candidate while preserving the legacy notification service as the public
notification writer, realtime delivery owner and rollback target.

## Boundary and authority

- Candidate route: `DELETE /api/v1/notifications/{id}`.
- The route exists only with the `persistence` profile and
  `migration.notifications-write.enabled=true`; `NOTIFICATIONS_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java verifies the legacy `notifications.notification` row exists, deletes
  only that row, and returns the legacy success message.
- This phase does not add admin notification create/update, realtime delivery,
  event publishing, nginx routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve pre-delete not-found behavior for missing notifications.
- Preserve response body:
  `{ "message": "Notification deleted successfully" }`.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin deletion with the legacy success message;
  - row removal from the legacy notification table;
  - student access denied;
  - missing notification not found;
  - no accidental deletion when an unauthorized or missing delete attempt fails.
- The monolith shell contract covers feature-default-off behavior for
  `DELETE /api/v1/notifications/{id}`.

## Verification observed

- Focused notification write/read/default-off H2 gate passed on 2026-08-21 with
  Java 24.0.2 and temporary files redirected to the repository-local `.tmp`
  directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.notification.NotificationWritePersistenceTest,io.campuscore.restfulapi.notification.NotificationReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 36 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 126 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, realtime delivery
parity, admin notification create/update parity, route canary, rollback
rehearsal, public notification writer handoff and independent exact-head
Advisor/Kongming/Wukong review remain `NOT_RUN`.
