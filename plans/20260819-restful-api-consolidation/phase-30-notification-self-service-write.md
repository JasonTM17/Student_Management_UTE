# Phase 30 — Notification self-service write candidate

## Outcome

Add the Java REST API monolith's feature-default-off current-user notification
write candidate while preserving the legacy notification service as the public
notification writer, realtime delivery owner and rollback target.

## Boundary and authority

- Candidate routes:
  - `PATCH /api/v1/notifications/my/{id}/read`;
  - `PATCH /api/v1/notifications/my/read-all`;
  - `DELETE /api/v1/notifications/my/{id}`.
- The routes exist only with the `persistence` profile and
  `migration.notifications-write.enabled=true`; `NOTIFICATIONS_WRITE_ENABLED=false`
  remains the production default.
- Java scopes every mutation to the authenticated JWT subject and does not
  accept a body or query `userId` override.
- Java mutates only the legacy `notifications.notification` table and adds no
  DDL, notification event listener, WebSocket/realtime behavior, admin
  notification writer, nginx routing, public ownership or production cutover.

## Compatibility contract

- Preserve the legacy current-user paths and methods.
- Preserve mark-read shape by returning the notification row.
- Preserve mark-all-read shape: `{ "updated": <count> }`.
- Preserve delete-my success shape:
  `{ "message": "Notification deleted successfully" }`.
- Preserve delete-my ownership failure as forbidden with
  `Cannot delete this notification`.
- Preserve mark-read missing or cross-user behavior as not found.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the write candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - marking one owned notification read and returning the legacy row shape;
  - cross-user and missing mark-read attempts failing closed;
  - read-all updating only the current user's unread notifications;
  - deleting only an owned notification with the legacy success message;
  - forbidden delete for another user's notification.
- The monolith shell contract covers feature-default-off behavior for all three
  notification write routes.
- Migration safety tests cover the new `migration.notifications-write.enabled`
  flag activating the legacy-schema safety condition.

## Verification observed

- Focused notification write/read/default-off H2 gate passed on 2026-08-21 with
  Java 24.0.2 and temporary files redirected to the repository-local `.tmp`
  directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.notification.NotificationWritePersistenceTest,io.campuscore.restfulapi.notification.NotificationReadPersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 33 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 123 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL write parity, realtime delivery
parity, admin notification write parity, route canary, rollback rehearsal,
public notification writer handoff and independent exact-head
Advisor/Kongming/Wukong review remain `NOT_RUN`.
