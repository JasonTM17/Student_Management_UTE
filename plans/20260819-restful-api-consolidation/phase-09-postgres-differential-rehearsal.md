# Phase 09 — PostgreSQL differential rehearsal plan

## Outcome

Produce repeatable, redacted evidence that the feature-gated Java thesis read
candidate has the same agreed read behavior as the legacy thesis owner against
one immutable PostgreSQL snapshot. This phase is a rehearsal only: it does not
move the public route, canonical writer, traffic, feature default, or deploy
topology.

## Authority and no-go boundary

- The running `campuscore-db` container and its attached microservices are
  shared active state. They are **not** a rehearsal target: do not connect to
  them, enable `THESIS_READ_ENABLED`, run Flyway, seed fixtures, or change
  routes there.
- Do not prune, restart, delete, resize, or reuse a CampusCore container,
  volume, network, port, credential, or Compose profile for this phase.
- Stop immediately with `BLOCKED_CAPABILITY` if a separate backup/restore,
  disposable PostgreSQL, independent legacy runtime, read-only credential, or
  sufficient D: capacity cannot be supplied.

## Prerequisites

1. Freeze the exact Java commit, legacy commit/image identity, route map,
   schema/Flyway checksum and request corpus revision.
2. Obtain an approved logical backup or deterministic fixture, with PII scrub
   or data-use authorization recorded, save it on D:, record a checksum and
   restore manifest, and prove it restores into a new target.
3. Create a PostgreSQL disposable target with a distinct volume, network,
   database, port and test-only credentials. It must not share a volume or
   endpoint with `campuscore-db`.
4. Grant Java a dedicated read-only role: `CONNECT`, schema `USAGE`, and
   `SELECT` only on the thesis read tables. It must not be superuser, create
   databases/roles, create schemas, create temporary tables, or own migrations.
   Enforce `default_transaction_read_only` plus bounded statement and lock
   timeouts.
5. Run the Java candidate with `persistence` plus the thesis read flag only on
   the disposable target, with `FLYWAY_ENABLED=false`. Configure both Java and
   legacy rehearsal processes so neither can execute Flyway, Prisma migration,
   `db push`, DDL, or fixture seeding during the rehearsal.
6. Run legacy and Java on private endpoints against the same immutable snapshot.
   Do not use nginx or the public frontend for the differential loop.

## Differential corpus

The versioned request fixture must cover all currently migrated thesis reads:
rounds, published topics, groups, group detail, and councils. At minimum it
contains:

- a round with topics, groups and councils;
- an existing round with no groups and no councils;
- unknown round and unknown group identifiers;
- malformed UUID requests;
- a council with null schedule/room, non-UTC timestamps, roles/statuses, and
  members inserted out of order;
- nullable topic and rejection fields;
- anonymous and authenticated requests, including the agreed role baseline;
- response headers/error envelopes, request IDs, statement timeout and an
  empty-result case for each list.

Normalize JSON field order but compare HTTP status, body shape, null omission,
enum values, ISO timestamps in UTC, ordering, error code, and relevant headers.
Record latency and the read-only database audit for each corpus request.

## Acceptance evidence

- exact code/image/config identities and backup checksum;
- successful restore into a disposable PostgreSQL target;
- read-only permission audit and `FLYWAY_ENABLED=false` evidence;
- normalized legacy-versus-Java diff with zero unexplained differences for the
  agreed corpus;
- redacted request/response and metrics artifacts, including timeouts;
- private route switch legacy → Java → legacy with the legacy writer preserved;
- cleanup record for only the disposable target, not the shared stack.

## Failure and recovery

Any mismatch, authorization leak, timeout, write/DDL/TEMP permission, shared-
resource identity, missing restore/data-use evidence, or inability to route
back to legacy is a `FAIL` or `BLOCKED_CAPABILITY`, not a partial pass. Restore
means abandoning the disposable Java route and using the unchanged legacy
owner; it is not merely a Java process restart.

## Remaining gates after a successful rehearsal

An isolated differential pass still does not authorize production. Authenticated
source-current smoke, browser/mobile parity, canary, monitoring, canonical
writer acceptance, exercised rollback, and fresh exact-head
Advisor/Kongming/Wukong reviews remain required before any route cutover.
