# Phase 31 — Notification admin read candidate

## Outcome

Extend the Java REST API monolith's feature-default-off notification read
candidate with the admin list/detail routes while preserving the legacy
notification service as the public notification route owner and rollback target.

## Boundary and authority

- Candidate routes:
  - `GET /api/v1/notifications`;
  - `GET /api/v1/notifications/{id}`.
- The routes exist only with the `persistence` profile and
  `migration.notifications-read.enabled=true`; `NOTIFICATIONS_READ_ENABLED=false`
  remains the production default.
- The routes are limited to `ADMIN` and `SUPER_ADMIN`, matching the legacy Nest
  controller.
- Java reads only the legacy `notifications.notification` table and adds no
  DDL, write behavior, realtime delivery, nginx routing, public ownership or
  production cutover.

## Compatibility contract

- Preserve the legacy paths and methods.
- Preserve the legacy admin-only authorization boundary.
- Preserve list pagination envelope:
  `{ "data": [...], "meta": { "total", "page", "limit", "totalPages" } }`.
- Preserve optional admin `userId` filtering and created-desc ordering.
- Preserve detail row shape using the existing notification response DTO.
- Preserve missing detail behavior as `Notification not found`.
- Preserve feature-default-off behavior so the Java shell returns the stable 404
  envelope unless the read candidate is explicitly enabled.

## Verification

- Feature-on H2 tests cover:
  - admin list pagination, total count and created-desc ordering;
  - optional `userId` filtering;
  - admin detail read;
  - student access denied for admin detail;
  - missing detail not found.
- The monolith shell contract covers feature-default-off behavior for both
  admin notification read routes.

## Verification observed

- Focused notification read/write/default-off H2 gate passed on 2026-08-21 with
  Java 24.0.2 and temporary files redirected to the repository-local `.tmp`
  directory:
  `mvn -q -f java-services/restful-api/pom.xml '-Dtest=io.campuscore.restfulapi.notification.NotificationReadPersistenceTest,io.campuscore.restfulapi.notification.NotificationWritePersistenceTest,io.campuscore.restfulapi.RestfulApiContractTest,io.campuscore.restfulapi.migration.MigrationSafetyConfigTest' '-DforkCount=0' test`.
- Observed focused result: 35 tests, 0 failures, 0 errors, 0 skipped.
- Full Java reactor gate passed on 2026-08-21 with Java 24.0.2 and temporary
  files redirected to the repository-local `.tmp` directory:
  `mvn -q -f java-services/pom.xml test`.
- Observed reactor result: 125 tests, 0 failures, 0 errors, 0 skipped.

## Remaining gates

This remains source/H2 evidence only. PostgreSQL read parity, realtime delivery
parity, admin notification write parity, route canary, rollback rehearsal,
public notification route handoff and independent exact-head
Advisor/Kongming/Wukong review remain `NOT_RUN`.
