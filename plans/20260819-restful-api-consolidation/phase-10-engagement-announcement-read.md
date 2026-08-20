# Phase 10 — Engagement announcement read candidate

## Outcome

Move the first low-risk engagement boundary into the single Java REST API as a
legacy-compatible, read-only candidate. The Node engagement service remains the
route owner, canonical writer, RabbitMQ event publisher, and rollback target.

## Boundary and authority

- Candidate routes are `GET /api/v1/announcements/my` and the admin-only
  `GET /api/v1/announcements`.
- Both routes exist only with the `persistence` profile and
  `migration.engagement-read.enabled=true`; `ENGAGEMENT_READ_ENABLED=false` is
  the production default.
- JDBC reads the Prisma-owned `"engagement"."Announcement"` table using
  `SELECT` only. This phase adds no JPA entity, Flyway migration, DDL, create,
  update, delete, RabbitMQ publication, or nginx routing change.
- The shared active PostgreSQL container is not a test target. Runtime parity
  must use the isolated rehearsal environment defined by Phase 09.

## Compatibility contract

- Preserve the Nest envelope `{ data, meta: { total, page, limit, totalPages } }`,
  one-based pagination, page-size range 1–200, newest-first ordering, optional
  admin filters, and the existing priority vocabulary.
- `/my` preserves global-or-role targeting, publish/expiry windows, student-year
  targeting, and lecturer-specific targeting from the signed JWT claims.
- `/announcements` remains limited to `ADMIN` and `SUPER_ADMIN`.
- Responses retain both the flattened Prisma fields and the derived semester,
  section/course, and lecturer objects consumed by the frontend.

## Acceptance and verification

- Feature-default-off route returns the stable 404 envelope.
- Feature-on H2 contract tests cover pagination, filters, nested response shape,
  anonymous access, admin authorization, role/year/time isolation, lecturer
  isolation, and invalid/unbounded requests.
- Static review proves the candidate repository contains no write statement or
  schema ownership path; `git diff --check` and the complete monolith test suite
  pass before commit when host capacity permits.
- The frozen exact commit receives independent Advisor, Kongming, and degraded
  Wukong review before push.

## Verification observed

- The first focused run compiled the candidate and exposed a shared shell bug:
  method-security denials were being translated by the generic exception advice
  into HTTP 500. The common advice now maps Spring `AccessDeniedException` to
  the stable HTTP 403 `ACCESS_DENIED` envelope, and the negative admin-role
  regression covers it.
- The focused command for `AnnouncementReadPersistenceTest` and
  `RestfulApiContractTest` passed 19 tests after the repair.
- The complete bounded command below passed locally on Windows with Java 24,
  using D: for JVM temporary files because C: remained under 1 GiB free:

  ```powershell
  $env:MAVEN_OPTS='-Xmx256m -XX:MaxMetaspaceSize=160m -Djava.io.tmpdir=D:\Student_Management-recovery\java-test-temp'
  mvn -q -f java-services/restful-api/pom.xml '-DargLine=-Xmx256m -XX:MaxMetaspaceSize=160m -Djava.io.tmpdir=D:\Student_Management-recovery\java-test-temp' test
  ```

- Maven reported 40 tests with zero failures, errors, or skips: 5 engagement,
  4 notification, 14 shell contract, 5 CSRF, and 12 thesis tests.
- Source scanning found no JDBC mutation or DDL statement in the production
  engagement package. `git diff --check` passed; line-ending notices are Git's
  existing Windows LF-to-CRLF normalization warning, not whitespace errors.
- This evidence is H2/Spring source evidence only. No shared CampusCore database
  or running engagement container was queried or changed.

## Remaining gates

No source or H2 result authorizes traffic. PostgreSQL differential reads,
authenticated source-current smoke, an immutable comparison corpus, read-only
database credentials, canary metrics, route rollback, and a fresh exact-head
review remain required before any cutover claim.
