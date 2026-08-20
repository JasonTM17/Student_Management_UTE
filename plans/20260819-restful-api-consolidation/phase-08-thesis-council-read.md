# Phase 08 — Thesis council read candidate

## Outcome

Introduce only the legacy-compatible thesis council **list** read path in the
single Java REST API candidate. The path is deliberately not a cutover: legacy
services remain the canonical writer and public route owner until future
runtime, parity, canary and rollback gates pass.

## Boundary

- `GET /api/v1/thesis/councils?roundId=<uuid>` is available only when both the
  `persistence` profile and `migration.thesis-read.enabled=true` are active.
- Production configuration keeps `THESIS_READ_ENABLED=false` by default.
- The adapter uses JDBC `SELECT` statements only. It has no create, membership,
  scheduling, scoring, review, result or writer capability.
- Authentication remains the shared Spring Security boundary. Anonymous calls
  are expected to receive the existing `UNAUTHENTICATED` envelope.

## Compatibility contract

- Response matches the existing frontend/legacy `ThesisCouncil` shape: council
  identifiers, round/department, nullable schedule/room, status, and members.
- Existing round with no councils returns an empty list; unknown round is 404.
- Council ordering is `scheduled_at ASC NULLS LAST`, matching PostgreSQL's
  default ascending null behavior used by the legacy JPA query.
- Members are batch-loaded and ordered by `(council_id, member_order)`.

## Verification plan

- static source/format and diff checks;
- feature-on H2 persistence contract coverage for ordering, null fields,
  members, 404, malformed UUID and anonymous access;
- feature-default-off contract coverage;
- exact-head Advisor, Kongming and degraded Wukong review before source commit;
- PostgreSQL differential reads, authenticated runtime smoke, canary and
  rollback remain required before exposing or routing production traffic.

## Verification observed

- `node scripts/check-thesis-contract.mjs` passed its static compatibility
  inventory (22 Java endpoints and 8 frontend bindings).
- With bounded JVM memory, `mvn -q -f java-services/restful-api/pom.xml
  '-Dtest=ThesisTopicPersistenceTest,RestfulApiContractTest'
  '-DargLine=-Xmx256m -XX:MaxMetaspaceSize=160m' test` passed locally on
  2026-08-20 on exact source checkpoint `24f5e8b`. It covers the feature-on
  H2/Flyway contract, feature-default disabled route, and the follow-up
  `councilsAllowAnExistingRoundWithoutCouncils` assertion, but not a live
  PostgreSQL runtime.

## Remaining limitations

PostgreSQL differential reads, authenticated live smoke, canary and rollback
remain open. The source test result is not runtime/cutover proof.
