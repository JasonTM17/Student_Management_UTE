# Phase 11 — Engagement PostgreSQL differential rehearsal

## Outcome

Compare the legacy Nest engagement reads and the Java monolith candidate on one
immutable, isolated PostgreSQL snapshot. This is a private rehearsal only: it
does not enable the public route, move writer ownership, publish RabbitMQ
events, or modify the active CampusCore stack.

## No-go boundary

- Never connect either rehearsal process to the active `campuscore-db` target.
- Use a disposable PostgreSQL instance, volume, network, port, database and
  credentials on D:, restored from an approved scrubbed backup or deterministic
  fixture with a recorded checksum.
- Both runtimes must have Flyway/Prisma migration, `db push`, DDL and seeding
  disabled. Java starts with `ENGAGEMENT_READ_ENABLED=true` and
  `FLYWAY_ENABLED=false` using a role limited to `CONNECT`, schema `USAGE`, and
  table `SELECT`; deny ownership, TEMP, CREATE, DML and superuser capability.
- With the engagement flag on, Java must show that its Flyway rejection strategy
  is installed and Hibernate schema management resolves to `none`; the role must
  not require thesis-schema visibility merely to start the shared deployable.

## Physical schema gate

Before requests, record `information_schema.columns`, PostgreSQL server/timezone
settings and Prisma schema identity for `"engagement"."Announcement"`.
Verify exact quoted identifiers, nullability, text/integer array types, Boolean,
and all `DateTime` columns. The source candidate expects Prisma's PostgreSQL
default `timestamp(3)` representation and converts it to/from UTC explicitly;
any restored physical difference is a HOLD that requires a reviewed adapter,
not an implicit cast.

## Differential corpus

Run the same signed bearer/cookie identity and request corpus against private
legacy and Java endpoints. Normalize JSON object key order only. Compare status,
data, meta, nulls, arrays, timestamps, ordering and relevant headers for:

- global, role-targeted, year-targeted and lecturer-targeted announcements;
- missing student/lecturer profile claims, missing `sub`/`email`, anonymous,
  wrong admin role and multi-role identities;
- null, past, exact-boundary and future publish/expiry timestamps, including
  millisecond precision and non-UTC client inputs;
- pages 1 and N, empty pages, limits 1/20/200/201, totalPages and concurrent
  insert pressure while the legacy writer remains the sole writer;
- all admin filters alone and combined, blank/whitespace values, unknown and
  repeated query parameters, invalid priority and malformed numbers;
- flattened and derived semester, section/course and lecturer response fields;
- the known Nest-versus-monolith error-body difference and the deliberate Java
  fail-closed profile-claim behavior, each requiring an explicit product/API
  decision rather than being hidden as a zero diff.

## Acceptance evidence

- exact Java/legacy commit or image, config and fixture identities;
- successful isolated restore and physical schema/type report;
- read-only privilege proof plus database audit showing no DML/DDL/TEMP;
- Flyway strategy/default evidence and `FLYWAY_ENABLED=false` startup evidence;
- effective Hibernate `hbm2ddl.auto=none` and no thesis-schema privilege proof;
- redacted signed-auth request corpus and explained differential report;
- bounded latency/query-plan evidence for audience arrays and pagination;
- private legacy → Java → legacy route rehearsal with unchanged legacy writer;
- cleanup of only the disposable target and a fresh exact-head review.

Until all evidence exists, this phase is `NOT_RUN`/`HOLD`; H2 tests and source
inspection are not substitutes for PostgreSQL parity or rollback proof.

## Observed rehearsal — 2026-08-20

Verdict: **bounded runtime PASS; production cutover HOLD**.

The exact provenance-bound probe checkpoint was
`4c0e79b7448235d581469d995652c42a28ed0466`. The Java timestamp source commit
was `b6c6e0d863642a6cdbdb89e2e87dd2fbec8d4d57` and no Java production source
changed between those identities. The rebuilt Spring Boot artifact was
deterministic across the pre-commit and exact-head package runs:

- JAR SHA-256:
  `1866AA8719B67A218D77C11CA1FE0A6087C0C9451A9EE099F6C64F73BD929BE8`;
- Java reference artifact SHA-256:
  `C35AB883F93A0F62A6D446F99692F86CE048604F21782F616A9847922E631A69`;
- sequential differential report SHA-256:
  `B6A7654FC17A2D7BB284400793B92F7CFB2E02F19FB29FA5E62591BC000FF1DE`;
- legacy Nest `dist/src/main.js` SHA-256:
  `DD5B004DC457BA61235704FB4633638907675F1940F747E109EDBB3BF988BD43`.

The response artifacts are outside Git in the D-hosted Phase 11 recovery root.
They contain response data and source/artifact identities, but no bearer or
cookie token values. The capture derives the Git HEAD and artifact hashes from
the checkout and files it actually observes; it rejects a tracked-dirty
checkout instead of accepting caller-supplied identity strings.

### Isolated database and startup evidence

- PostgreSQL 18.4 ran on loopback port `56432` with its data, log and temporary
  files on D. This did not connect to or restart the active CampusCore Docker
  stack.
- The rehearsal database contained the quoted
  `"engagement"."Announcement"` table and nine deterministic rows; it did not
  contain the `thesis` schema.
- Java started on `127.0.0.1:56410` with the persistence profile,
  `ENGAGEMENT_READ_ENABLED=true`, `FLYWAY_ENABLED=false`, and all writer
  ownership left with the Nest service. Successful startup without thesis
  schema visibility is runtime evidence for the engagement Hibernate/Flyway
  safety override in this environment.
- The database role had CONNECT, schema USAGE and table SELECT. It lacked TEMP,
  schema CREATE, INSERT, UPDATE and DELETE.
- The fixture checksum remained nine rows and
  `7a3d71488910b1e7f0e84a8819130320` after all Java and Nest requests.
- The PostgreSQL statement log contained no explicit write statement after the
  initial database/role/fixture setup. The intended Java application-name tag
  was not present in connection log lines, so statement attribution by tag is
  not claimed; the role-level deny proof and unchanged checksum are the stronger
  write-safety evidence.
- Local `trust` authentication was used only inside the disposable loopback
  cluster. This proves SQL privilege containment, not production database
  authentication.

### Differential result

The reusable probe is
`engagement-service/test/engagement-read.rehearsal.cjs`. It captures Java
responses with a lightweight process, stops Java, then starts the in-process
Nest application and compares against the immutable reference. This sequential
shape was selected because the C drive had only about 0.3 GiB free and a prior
Jest/ts-jest attempt exhausted its bounded Node heap before running tests; that
attempt remains `NOT_RUN`, not a functional failure.

The provenance-bound checkpoint fails before importing `AppModule` unless every
isolation invariant is present: `NODE_ENV=test`; no inherited RabbitMQ URL; an
exact `postgresql://engagement_reader@127.0.0.1:56432/engagement_rehearsal`
target with the `engagement` schema; a D-hosted Phase 11 run root; and a live
`postmaster.pid` whose data directory, port, listen address and ready marker
match that root. Reference and report paths must be descendants of the same
root. The probe deletes the absent RabbitMQ environment key before Nest config
loads, so an `.env` file or inherited broker URL cannot activate queue setup.
Java capture additionally requires the exact loopback URL
`http://127.0.0.1:56410/`, rejects credentials/query/fragment and redirects,
and binds the listener PID command line to the exact D-hosted JAR whose hash it
records. The report then verifies the immutable Java reference hash, actual
clean source HEAD, JAR path/hash/PID and actual legacy entry-artifact hash.

Thirteen signed/negative cases produced equal HTTP status and normalized
content type. Status, successful response body and normalized content type are
assertions; a mismatch cannot produce `PASS`:

- student bearer and cookie, lecturer bearer and combined admin filters;
- student/admin pagination and an empty page;
- blank priority, unknown query, limit overflow and repeated query;
- anonymous access and a student attempting the admin listing.

All seven successful responses had exact full-body parity, including ordering,
derived semester/section/course/lecturer objects, arrays, nulls, pagination
metadata and millisecond timestamps. The first comparison falsified parity
because Java emitted whole-second instants without `.000`; the engagement DTO
now formats all four timestamp fields as UTC with exactly three millisecond
digits, and a regression test covers this contract.

The six error cases had matching status and content type but deliberately
different bodies: the legacy Nest body did not expose the Java error `code`,
while Java returned `INVALID_REQUEST`, `UNAUTHENTICATED`, or `ACCESS_DENIED`.
The report records `errorBodyParity=false`; this remains an explicit product/API
decision and is not represented as full parity.

### Verification observed at the exact checkpoint

- `node --check engagement-service/test/engagement-read.rehearsal.cjs`: PASS.
- negative preflight cases for a substring-smuggled active database URL,
  inherited RabbitMQ URL, C-hosted run root and a userinfo-smuggled Java URL:
  all rejected before app import or network use.
- exact-head Java capture: 13/13 cases PASS with the listener bound to PID
  `15988` and the JAR/hash shown above; Java was stopped before legacy startup.
- sequential differential: 13/13 status/content-type assertions PASS and 7/7
  successful full-body assertions PASS; the six intentional error-envelope
  differences remained visible as `errorBodyParity=false`.
- post-request fixture proof: nine rows and checksum
  `7a3d71488910b1e7f0e84a8819130320`, unchanged from baseline.
- focused `AnnouncementReadPersistenceTest`: 6/6 PASS.
- full Java test suite: 45/45 PASS, zero failure/error/skip, run after the exact
  source commit.
- offline Maven package: PASS; JAR hash shown above.
- production engagement source scan: read candidate remains GET/SELECT/COUNT
  only, with feature flags default-off.

### Remaining HOLD gates

This rehearsal does not authorize traffic or deletion of the Nest service. The
following remain required:

- a production-version PostgreSQL clone or approved scrubbed snapshot, rather
  than PostgreSQL 18.4 plus deterministic fixtures;
- production-equivalent database authentication and a real signed token from
  the authoritative issuer;
- the remaining null/time-boundary/malformed-claim corpus, bounded query-plan
  and latency evidence, and deterministic concurrent-writer observation;
- an approved error-envelope contract;
- private legacy → Java → legacy route switching, canary thresholds, monitored
  rollback and rollback timing;
- fresh exact-head Advisor, Kongming and Wukong gates after this evidence is
  committed.
