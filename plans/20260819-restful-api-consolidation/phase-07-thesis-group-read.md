# Phase 07 — Thesis group read boundary

Adds feature-gated, JDBC-only reads for legacy-compatible Thesis groups:
`GET /api/v1/thesis/groups?roundId=…` and `GET /api/v1/thesis/groups/{id}`.
The adapter batch-loads member IDs in `member_order`, preserves empty-list and
404 behavior, and exposes no generic write repository. It remains disabled
unless `persistence` and `THESIS_READ_ENABLED=true` are both active.

Verification: bounded-heap `mvn -DskipTests compile` passed. Spring
persistence tests remain `BLOCKED_CAPABILITY` on this host because Windows
previously rejected JVM native-memory allocation under C: pressure. No edge,
frontend route, canonical writer, or deployment was changed.

The retained feature-on H2 test covers group sort order, ordered member IDs,
nullable topic/rejection fields, and group/round not-found behavior. Its source
compiles with `mvn -DskipTests test-compile`; it still needs an observed green
Spring execution on a host with sufficient commit memory.
