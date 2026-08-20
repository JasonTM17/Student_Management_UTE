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
  admin filters, blank-filter behavior, rejection of unknown/repeated query
  parameters, and the existing priority vocabulary.
- `/my` preserves global-or-role targeting, publish/expiry windows, student-year
  targeting, and lecturer-specific targeting from the signed JWT claims.
- Announcement routes require both `sub` and `email`, matching the legacy JWT
  normalization precondition. The Java candidate additionally fails closed for
  `STUDENT` or `LECTURER` roles missing their profile claims. This deliberate
  abuse-case hardening differs from legacy malformed-token behavior and must be
  accepted explicitly before cutover.
- `/announcements` remains limited to `ADMIN` and `SUPER_ADMIN`.
- Responses retain both the flattened Prisma fields and the derived semester,
  section/course, and lecturer objects consumed by the frontend.
- The legacy Nest error body and the shared Java monolith error body are not the
  same JSON shape. Status/code behavior is covered locally, but exact error-body
  compatibility remains a declared differential blocker rather than a source
  parity claim.

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

- The pre-review snapshot reported 40 passing tests. After the adversarial
  repairs, the complete suite reported 43 tests with zero failures, errors, or
  skips: 6 engagement, 2 migration-safety, 4 notification, 14 shell contract,
  5 CSRF, and 12 thesis tests.
- Source scanning found no JDBC mutation or DDL statement in the production
  engagement package. `git diff --check` passed; line-ending notices are Git's
  existing Windows LF-to-CRLF normalization warning, not whitespace errors.
- This evidence is H2/Spring source evidence only. No shared CampusCore database
  or running engagement container was queried or changed.

## Adversarial review remediation

The first frozen candidate `1d67fe1` received Advisor `HOLD`, Kongming `HOLD`,
and degraded Wukong `FALSIFIED`. Their source-level counterexamples were treated
as stale-gate findings, not approvals:

- blank admin filters and unknown/repeated query parameters now follow the Nest
  source semantics;
- announcement routes now enforce the legacy `sub` plus `email` identity
  precondition and reject missing role-profile claims;
- Prisma's PostgreSQL default for unannotated `DateTime` is `timestamp(3)`.
  The JDBC adapter and H2 fixture now use UTC `LocalDateTime` plus SQL
  `TIMESTAMP`, rather than claiming an unverified timestamptz mapping;
- enabling the engagement read flag installs a Flyway strategy that throws
  before migration and a Hibernate customizer that disables schema management,
  so an engagement-only role does not need thesis-schema visibility. The
  candidate test context explicitly sets Flyway off but no longer overrides
  `ddl-auto`, exercising the customizer during Spring startup;
- Phase 11 now owns an engagement-specific PostgreSQL differential corpus.
- a blank `priority=` remains invalid like the Nest DTO, while blank semester
  and section filters remain optional; malformed non-string profile/identity
  claims and fractional or overflowing student years now fail closed, with
  regression requests covering each counterexample.

The repaired checkpoint `23055a5` then received an Advisor source-gate
`ACCEPT`, Kongming source-gate `PASS`, and degraded Wukong `FALSIFIED`.
Advisor identified the engagement-only database role could still be forced to
validate thesis JPA entities at startup; Wukong identified the blank-priority
and malformed-claim cases above. Those verdicts became stale as soon as these
repairs changed the snapshot. The final exact commit therefore requires a new
three-review gate; none of the earlier verdicts is carried forward.

All three independent reviews must be repeated on the repaired exact commit;
the original verdicts cannot approve a changed snapshot.

## Remaining gates

No source or H2 result authorizes traffic. PostgreSQL differential reads,
authenticated source-current smoke, an immutable comparison corpus, read-only
database credentials, canary metrics, route rollback, and a fresh exact-head
review remain required before any cutover claim.
