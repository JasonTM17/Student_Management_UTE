# Notification read candidate report — 2026-08-19

## Decision

The first notification strangler slice is implemented as a **disabled-by-
default, read-only Java candidate**. It is not a route cutover and does not
change the legacy notification writer, Socket.IO gateway, RabbitMQ consumer,
or public edge owner.

Candidate routes:

- `GET /api/v1/notifications/my`
- `GET /api/v1/notifications/my/unread-count`

The routes are active only when the `persistence` profile is active and
`NOTIFICATIONS_READ_ENABLED=true`. The default application and test profile
keep the boundary absent; the default contract test asserts a `404` for the
disabled route.

## Why the adapter uses JDBC and string IDs

The legacy Prisma schema declares both notification `id` and `userId` as
`String`, and integration fixtures use values such as `n-1` and
`student-user-1`. The existing standalone Java notification service parses
both values as `UUID`, and its list implementation ignores the `isRead`
argument. Reusing that model would therefore create an observable parity bug.

The candidate uses a qualified JDBC read adapter for
`notifications.notification`, keeps IDs as strings, and does not add a JPA
entity or Flyway migration. No DDL or write query is present in the candidate.

## Source-level contract implemented

| Concern | Candidate behavior | Evidence boundary |
| --- | --- | --- |
| Subject isolation | Takes the JWT subject only; there is no personal-route `userId` parameter. | Spring MVC/H2 test; real auth still unrun. |
| List envelope | Returns `{data, meta}` with `total`, `page`, `limit`, `totalPages`. | 4 notification persistence tests. |
| Filtering | `isRead=true` maps to true; any other supplied string maps to false, matching current Nest source. | H2 test covers `true` and `TRUE`. |
| Ordering | `created_at DESC`, matching the current service query. | H2 fixture; tie behavior still needs live contract freeze. |
| Limits | Page must be at least 1; limit is bounded to 1–100. | H2 negative test; exact legacy boundary requires differential confirmation. |
| Unread count | Counts only the JWT subject with `is_read = FALSE`. | H2 subject-isolation test. |
| Nulls/timestamps | Maps nullable `link`/`read_at` and UTC-compatible timestamps. | H2 fixture includes null link/readAt; PostgreSQL precision still unverified. |
| Ownership | Read-only beans behind `migration.notifications-read.enabled`; no mutation/realtime code. | Source inspection; no live permission proof. |

## Files

- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/notification/web/NotificationReadController.java`
- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/notification/web/NotificationReadDtos.java`
- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/notification/service/NotificationReadService.java`
- `java-services/restful-api/src/main/java/io/campuscore/restfulapi/notification/repository/NotificationReadRepository.java`
- `java-services/restful-api/src/main/resources/application.yml`
- `java-services/restful-api/src/test/resources/application-test.yml`
- `java-services/restful-api/src/test/java/io/campuscore/restfulapi/notification/NotificationReadPersistenceTest.java`
- `java-services/restful-api/src/test/java/io/campuscore/restfulapi/RestfulApiContractTest.java`

## Observed verification

Environment: Windows 11, Maven 3.9.12, Java 24.0.2, H2 2.3, local checkout.

| Command/gate | Result | Limitation |
| --- | --- | --- |
| `mvn -q -f pom.xml -DskipTests compile` | `PASS` | Compile only; no PostgreSQL or deployed runtime. |
| `mvn -q -f pom.xml "-Dtest=RestfulApiContractTest,NotificationReadPersistenceTest" test` | `PASS` — 14 tests | H2 fixture and MockMvc; not live auth/database. |
| `mvn -q -f pom.xml test` | `PASS` — 26 tests, 0 failures/errors | Includes existing thesis/CSRF tests; still local H2. |
| Default route-disabled assertion | `PASS` | Proves fail-closed configuration, not edge behavior. |
| `git diff --check` | `PASS` before staging | Must rerun at final commit. |

## Open runtime gates

The candidate remains `HOLD` until all of the following are observed on an
isolated restored PostgreSQL copy and the exact reviewed implementation:

1. The legacy table schema, casing, timestamp precision, and index behavior
   are inspected without granting the Java process write or DDL permission.
2. Real bearer/cookie auth proves the JWT subject format and rejects missing,
   malformed, and cross-user access cases.
3. Node and Java responses are compared against the same immutable fixture for
   default/list/filter/page/empty/count cases, including error envelopes.
4. The public edge can shadow or canary without changing the user-visible Node
   response, and a route rollback to Node is rehearsed.
5. Advisor, Kongming, and Wukong review the frozen exact implementation head;
   a docs-only ledger update cannot substitute for a fresh implementation
   review.

No client is pointed at this candidate, no legacy service is deleted, and no
production/cutover claim is made by this report.
