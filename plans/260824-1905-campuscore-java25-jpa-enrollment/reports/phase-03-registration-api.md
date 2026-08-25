# Phase 3 - Registration API and slip evidence

## Candidate scope

This report covers the canonical registration service/controller slice and its
backend contract tests. It is evidence for the current feature candidate, not
for `main` or for a production deployment.

## Delivered

- Canonical round, section, eligibility, summary, validation, enrollment,
  drop, and registration-slip routes under `/api/v1`.
- `Idempotency-Key` is required and parsed as UUID. Same-key replay returns the
  stored result; a different canonical payload returns
  `IDEMPOTENCY_KEY_REUSED`; an in-flight key returns `REQUEST_IN_PROGRESS` with
  retry metadata.
- Stable `application/problem+json` responses for registration failures,
  including missing/invalid idempotency headers. Non-registration malformed
  query parameters retain the legacy `ApiError` shape.
- Round status/window, cohort, credit, prerequisite/corequisite, duplicate,
  capacity and schedule checks are server-authoritative. Drop verifies owner,
  active state and add/drop window.
- Registration-slip snapshots now persist Base64 PDF bytes and the canonical
  SHA-256 checksum. The canonical body includes student/semester/round,
  generated timestamp, course/section/credits, lecturer, classroom, every
  schedule slot and enrollment id in deterministic order.
- V18 adds a forward-only PostgreSQL partial unique index covering every active
  enrollment status (`ACTIVE`, `ENROLLED`, `PENDING`, `CONFIRMED`) with a
  duplicate preflight stop.

## Evidence

| Gate | Environment | Result |
|---|---|---|
| Focused Java contract/persistence/slip/assistant slice | `maven:3.9.12-eclipse-temurin-25` | PASS, 69 tests, 0 failures, 0 errors, 2 skipped PG ITs without env |
| Full Java reactor | same Java 25.0.2 container | PASS, 180 tests, 0 failures, 0 errors, 2 skipped PG ITs without env |
| Fresh PostgreSQL migration + capacity race | isolated PostgreSQL 15.19 container, Flyway V1→V18 | PASS; two concurrent seat claims produced exactly one winner and no count drift |
| Problem-details regression | Java 25, `ThesisTopicPersistenceTest` | PASS |
| PDF snapshot/canonical-order regression | Java 25, `RegistrationSlipSnapshotTest` | PASS |

## Compatibility and persistence boundary

- The legacy `/enrollments/*` aliases deliberately point to the same
  `RegistrationService` mutation writer and return deprecation/successor
  headers. Read/export compatibility endpoints remain JDBC adapters; no
  duplicate enrollment writer exists.
- Registration slips use pinned PDFBox 3.0.8 and the embedded OFL Noto Sans
  Vietnamese subset. `SimplePdfTest` and `RegistrationSlipSnapshotTest`
  prove deterministic bytes, Unicode extraction and pagination.
- The mutation lock order is operation reservation → round row → section row →
  student enrollment rows. `RegistrationLockOrderTest` and the PostgreSQL
  concurrency suite cover the ordering and capacity/idempotency race.
- V10/V12 upgraded-copy rehearsal and authenticated browser evidence are
  recorded in the terminal/review checkpoints; whole-domain catalog/admin
  JPA conversion is outside this registration writer boundary.

## Exit ruling

The canonical and compatibility mutation phase is **PASS**. No production
readiness is inferred; remote CI, device and production cutover remain separate
gates.
