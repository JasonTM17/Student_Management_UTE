---
title: "Phase 2: JPA foundation and forward migrations"
status: in-progress
---

# Phase 2: JPA foundation and forward migrations

## Objective

Introduce typed JPA entities/repositories as the persistence boundary and
additive Flyway migrations V13+ (H2 V7+) without changing V11/V12. Preserve
data and expose preflight failures before any destructive or ambiguous change.

## Owned paths

`java-services/restful-api/src/main/java/**/persistence`, typed domain
repositories/services, Flyway migrations V13+, H2 parity migrations V7+, JPA
configuration, and repository parity/constraint tests.

## Invariants

- `ddl-auto=validate` and `open-in-view=false`; Flyway is schema authority.
- DTO/projection boundaries prevent entity serialization.
- Optimistic versions and pessimistic locks use the fixed lock order from the
  accepted plan.
- No raw `Map<String,Object>` mutation and no parallel JDBC writer after a
  migrated slice is enabled.
- Fresh, V10-upgraded, V11/V12-upgraded, and anonymized realistic databases are
  migration targets; invalid data causes a clear preflight stop.

## Steps and exit

Map identity/catalog/section/schedule first, then registration-round,
eligibility, enrollment ledger, grades, and thesis/assistant slices. For each
slice, prove repository parity before switching its writer. Exit only when
V13–V16 (or the reviewed final set) migrate without loss, negative constraints
and ownership tests pass, and the JDBC writer inventory is updated.

## Verification budget

Focused repository parity and migration tests first; PostgreSQL concurrency is
the authority. H2 is a parity oracle, not a substitute for PostgreSQL locking.

## Checkpoint result (2026-08-24)

The persistence foundation is implemented in candidate commit `94e85c3` and
passes the focused Java 25/H2 suite plus an ephemeral PostgreSQL 15 SQL
rehearsal. The phase remains in progress because no PostgreSQL concurrency or
upgraded-copy rehearsal has yet falsified the locking and preflight claims,
and academic JDBC writers still exist. See
`reports/phase-2-jpa-foundation.md` for the exact boundary.
