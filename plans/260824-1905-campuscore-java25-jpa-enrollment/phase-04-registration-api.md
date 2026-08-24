---
title: "Phase 3: Registration domain, API, idempotency and PDF slip"
status: in-progress
---

# Phase 3: Registration domain, API, idempotency and PDF slip

## Objective

Deliver a server-authoritative HCMUTE-style registration workflow with stable
REST/problem-details contracts and deterministic concurrency behavior.

## Owned paths

Registration domain entities/services/controllers/DTOs, OpenAPI, PDF renderer,
and focused backend/contract/integration tests.

## Contract checkpoints

Implement canonical round, eligibility, section, enrollment, summary and slip
routes under `/api/v1`; retain aliases with `Deprecation`. Require
`Idempotency-Key` on mutations, canonicalize payloads, replay same-key/same-
payload, and reject reuse or in-progress requests with stable problem codes.
Enforce round/cohort windows, section status/capacity, 28-credit default,
prerequisite/corequisite, schedule conflicts, owner isolation, and optimistic
version conflicts in the backend.

## Transaction boundary

Lock idempotency row, round, sorted sections, and student enrollments in that
order. Never call PDF/provider/network code while capacity locks are held.
Commit enrollment/audit first; render the PDF afterward and return its stable
SHA-256 hash.

## Exit criterion

Contract tests, 409/422 reason matrix, owner isolation, deterministic capacity
race, drop/enroll race, idempotency replay/conflict, pagination, and PDF
snapshot/hash tests pass against the candidate schema.
