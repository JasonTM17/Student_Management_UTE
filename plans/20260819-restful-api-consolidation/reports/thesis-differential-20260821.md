# Thesis read differential rehearsal — 2026-08-21

## Verdict

`PASS` for the bounded private thesis-read differential rehearsal after the
error-envelope compatibility repair. This is rehearsal evidence only; it does
not authorize public route ownership, writer handoff, cutover, or a
production-ready claim.

## Source and runtime boundary

- Source basis before this checkpoint: `8574f3c07d90c10d2dba0e1c0c132233418ea08b`.
- Intended checkpoint files: the thesis compatibility advice, its persistence
  regression assertions, this differential harness repair, and this report.
- Unrelated dirty and untracked files were not staged or changed.
- Legacy private runtime: `java-services/thesis-service`, `127.0.0.1:54111`.
- Java candidate private runtime: `java-services/restful-api`,
  `127.0.0.1:54112`, `persistence` profile, `THESIS_READ_ENABLED=true`.
- Both runtimes used `FLYWAY_ENABLED=false` and the same read-only restored
  PostgreSQL target. No nginx, public frontend traffic, or shared CampusCore
  database/container was used.

## Database and safety evidence

- Restored snapshot: `D:\Student_Management-recovery\pg-thesis-20260821\thesis-schema.dump`.
- Recorded checksum: `SHA256 5D7CF84815D85A9CAC7130426D5FFC87D215121EEE53943A24E7E4CD84B9FEB3`.
- Database: `campuscore_ro` on `127.0.0.1:55432`.
- Role audit for `campuscore_ro_reader`: `default_transaction_read_only=on`,
  `statement_timeout=5s`, thesis schema `USAGE=true`, topic `SELECT=true`,
  topic `INSERT=false`, database `CREATE=false`.
- A write attempt had already failed with `cannot execute INSERT in a
  read-only transaction`; no fixture or schema write was performed during the
  differential run.
- The restored server needed a bounded restart with lower shared-memory
  settings after Windows PostgreSQL error 487; the original dump and checksum
  were preserved. Both PostgreSQL rehearsal servers and both Java runtimes
  were stopped after evidence collection.

## Verification

1. Writable disposable regression database:
   `mvn -q -f java-services/restful-api/pom.xml
   '-Dtest=io.campuscore.restfulapi.thesis.ThesisTopicPersistenceTest'
   '-DforkCount=0' test`
   against a new database in the isolated `56433` cluster: `PASS`, 12 tests,
   0 failures, 0 errors.
2. Harness syntax and deterministic self-test:
   `node --check scripts/run-thesis-differential-rehearsal.mjs` and
   `node scripts/run-thesis-differential-rehearsal.mjs --self-test`: `PASS`.
3. Live private differential:
   `node scripts/run-thesis-differential-rehearsal.mjs`: `PASS`, all 8/8
   corpus cases matched on HTTP status, normalized content type and stable
   normalized body hash. The cases cover rounds, filtered rounds, topics,
   groups, group detail, councils, unknown round and malformed UUID.
4. Route sequence `legacy-before -> java-candidate -> legacy-after`: `PASS`;
   all three probes returned status `200` and body hash
   `b24dc28a551161788e2502437643dfb8324b710ee0ad6c8b270e162c0c4e4194`.

## Comparator rule

The legacy error envelope creates a fresh `timestamp` per request. The
comparator now requires every thesis compatibility error timestamp to parse as
an ISO-8601 UTC value ending in `Z`, then replaces only that volatile field with
`__utc_error_timestamp__` before hashing. Status, content type, envelope keys,
messages, path, all data timestamps, ordering, null/omitted fields, and every
other body field remain strict comparisons.

## Remaining gates

This result is limited to the thesis read corpus. Full PostgreSQL differential
coverage, authenticated web/mobile E2E, Stitch visual reference comparison,
canonical writer acceptance, canary observation, exercised rollback, and fresh
independent Advisor/Kongming/Wukong exact-head review remain open.
