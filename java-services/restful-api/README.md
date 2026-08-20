# CampusCore RESTful API shell

This directory is the first bounded implementation of the selected
architecture: one standalone Spring Boot 3.4 / Java 21 application. It is a
shell, not a domain-parity or cutover claim.

## Included in this phase

- one Maven project and one Spring Boot main class;
- `/api/v1/health/liveness` and key-protected readiness;
- shared cookie-or-bearer token resolution;
- the existing double-submit web CSRF contract;
- request correlation through `X-Request-Id`;
- authenticated identity claim and mutation probes;
- server-side Java access/refresh token issuer compatible with the shared
  access claim shape;
- stable validation/error envelope;
- Actuator and OpenAPI paths;
- a non-root Dockerfile for the eventual single image.
- a disabled-by-default, read-only notification inbox candidate behind
  `NOTIFICATIONS_READ_ENABLED=true` in the `persistence` profile; it does not
  own notification writes, realtime delivery, or public routing.
- a disabled-by-default, read-only academic catalog candidate behind
  `ACADEMIC_READ_ENABLED=true` in the `persistence` profile; it reads semesters
  and courses only, with no enrollment, grade, timetable or write ownership.
- a disabled-by-default, read-only academic enrollment candidate behind
  `ACADEMIC_ENROLLMENT_READ_ENABLED=true` in the `persistence` profile; it
  reads the current student's enrollment list, grade and transcript views plus
  admin enrollment list/detail and lecturer/admin grade item/student-grade
  views from the migrated legacy `academic` schema. It does not own enroll/drop,
  waitlist, grade editing/publishing, timetable, CSV export, public routing or
  full academic cutover.
- a disabled-by-default auth session candidate behind `AUTH_LOGIN_ENABLED=true`
  in the `persistence` profile; it verifies BCrypt passwords against the
  legacy `auth` schema, issues and refreshes shared web cookies plus body
  tokens, returns the current authenticated user through `/api/v1/auth/me`,
  updates the current user's profile fields, rotates hashed refresh sessions,
  changes passwords while revoking stored refresh sessions, and clears session
  state on logout. It does not own registration, forgot/reset password, email
  verification, audit publishing, public routing or full auth cutover.
- a disabled-by-default people read candidate behind `PEOPLE_READ_ENABLED=true`
  in the `persistence` profile; it reads student and lecturer list/detail
  profiles from the migrated legacy `people` schema. It does not own people
  writes, enrollment history, RabbitMQ events, public routing or full people
  cutover.
- a disabled-by-default finance read candidate behind
  `FINANCE_READ_ENABLED=true` in the `persistence` profile; it reads student
  invoice list/detail plus admin invoice/payment list/detail routes from the
  migrated legacy `finance` schema. It does not own invoice/payment writes,
  checkout orchestration, provider callbacks/webhooks, CSV exports, public
  routing or full finance cutover.
- a disabled-by-default analytics read candidate behind
  `ANALYTICS_READ_ENABLED=true` in the `persistence` profile; it reads dashboard
  overview counts and finance-summary aggregates from the legacy Prisma
  `public` schema. It does not own enrollment trends, section occupancy,
  grade distribution, cockpit composition, lecturer analytics, attendance,
  metrics export, public routing or full analytics cutover.

The default profile intentionally excludes database auto-configuration, so the
shell can be tested without a running service dependency. The `persistence`
profile enables one PostgreSQL datasource, one Flyway migration owner and the
first thesis read path only when `THESIS_READ_ENABLED=true` is also supplied.
The notification read candidate is separately gated by
`NOTIFICATIONS_READ_ENABLED=true` and reads the legacy schema through JDBC
without adding notification DDL. The academic catalog read candidate follows
the same pattern with `ACADEMIC_READ_ENABLED=true` and reads the legacy
`academic` schema through JDBC without adding academic DDL. The auth session
candidate is gated by `AUTH_LOGIN_ENABLED=true` and writes only the expected
auth/session login, current-user read, profile update, password-change,
refresh-rotation and logout-clearing state in the legacy `auth` schema; the
legacy auth service remains the public owner. The people read candidate uses
`PEOPLE_READ_ENABLED=true` and reads the migrated `people` schema without
adding people DDL or moving create/update/delete ownership. No legacy route,
payment provider or LLM provider is cut over; the finance read candidate uses
`FINANCE_READ_ENABLED=true` for invoice/payment reads only and does not move
checkout, provider callback/webhook, export or write ownership. The analytics
read candidate uses `ANALYTICS_READ_ENABLED=true` for overview and
finance-summary reads only and does not move cockpit/trend/attendance/lecturer
analytics ownership. The academic enrollment read candidate uses
`ACADEMIC_ENROLLMENT_READ_ENABLED=true` for selected enrollment, grade and
transcript reads only and does not move enroll/drop, waitlist, grade write/
publish, timetable or export ownership. Those
dependencies enter through later migration phases with one canonical writer and
explicit rollback evidence.

## Local verification

From the repository root:

```powershell
# Canonical Java build: the root reactor selects only this modular monolith.
mvn -q -f java-services/pom.xml test

# Equivalent direct build for this application.
mvn -q -f java-services/restful-api/pom.xml test

# Persistence rehearsal uses the H2 test profile; it does not touch Postgres.
mvn -q -f java-services/restful-api/pom.xml -Dspring.profiles.active=test,persistence test
```

The sibling Java service directories are retained only as shadow/rollback
sources. They are not reactor modules or public release artifacts; invoke an
individual child `pom.xml` explicitly only for a bounded compatibility check.

For a local process, provide real server-side `JWT_SECRET` and
`JWT_REFRESH_SECRET` values with at least 32 characters plus a
`HEALTH_READINESS_KEY`. `JWT_ACCESS_TOKEN_TTL_SECONDS` defaults to 900 seconds
and `JWT_REFRESH_TOKEN_TTL_SECONDS` defaults to seven days for the Java token
issuer. Never put these values in source, client bundles or committed `.env`
files.

The endpoint `/api/v1/contract` intentionally reports `migration: not-cut-over`.
It is a phase probe and must be removed or replaced by real module contracts
before any public route switch. The persistence profile is not a production
readiness signal until it has been exercised against a disposable PostgreSQL
restore as well as H2.
