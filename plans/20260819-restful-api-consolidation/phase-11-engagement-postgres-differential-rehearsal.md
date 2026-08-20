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
- redacted signed-auth request corpus and explained differential report;
- bounded latency/query-plan evidence for audience arrays and pagination;
- private legacy → Java → legacy route rehearsal with unchanged legacy writer;
- cleanup of only the disposable target and a fresh exact-head review.

Until all evidence exists, this phase is `NOT_RUN`/`HOLD`; H2 tests and source
inspection are not substitutes for PostgreSQL parity or rollback proof.
