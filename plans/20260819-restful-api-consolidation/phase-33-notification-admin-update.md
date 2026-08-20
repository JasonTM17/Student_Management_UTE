# Phase 33 — Notification admin update candidate

## Outcome

Add the Java REST API monolith's feature-default-off admin notification update
candidate while preserving the legacy notification service as the public
notification writer, realtime delivery owner and rollback target.

## Boundary and authority

- Candidate route: `PUT /api/v1/notifications/{id}`.
- The route exists only with the `persistence` profile and
  `migration.notifications-write.enabled=true`; `NOTIFICATIONS_WRITE_ENABLED=false`
  remains the production default.
- The route is limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java verifies the legacy `notifications.notification` row exists, applies
  only supplied update fields, and returns the updated notification row.
- This phase does not add admin notification creation, realtime delivery, event
  publishing, nginx routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy path and method.
- Preserve legacy admin-only authorization.
- Preserve partial update semantics for `userId`, `title`, `message`, `type`
  and `link`.
- Preserve valid notification types: `INFO`, `WARNING`, `ERROR`, `SUCCESS`.
- Preserve `link` clearing when explicitly supplied as `null`.
- Preserve missing notification behavior as `Notification not found`.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin partial update with legacy response shape;
  - `userId` reassignment and `link` clearing;
  - invalid type rejection;
  - unknown body property rejection;
  - missing notification not found;
  - student access denied;
  - unchanged row after failed update attempts.
- The monolith shell contract covers feature-default-off behavior for
  `PUT /api/v1/notifications/{id}`.

## Verification observed

- Focused notification write/read/default-off H2 gate passed on 2026-08-21 with
  Java 24.0.2 and temporary files redirected to the repository-local `.tmp`
  directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.notification.NotificationWritePersistenceTest,io.campuscore.restfulapi.notification.NotificationReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 38 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 128 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, realtime delivery
parity, admin notification create parity, route canary, rollback rehearsal,
public notification writer handoff and independent exact-head
Advisor/Kongming/Wukong review remain `NOT_RUN`.
