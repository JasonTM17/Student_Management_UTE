---
title: "Phase 7: Independent verification and handoff"
status: in-progress
---

# Phase 7: Independent verification and handoff

## Objective

Freeze an exact candidate and obtain independent architecture, adversarial,
frontend, backend, database, mobile, QA/security and code review evidence
before creating split commits and pushing the feature branch.

## Required gates

Run the focused Maven/PostgreSQL, web, mobile, Playwright, Compose, secret,
encoding, documentation and diff commands in the parent plan. Java 25,
PostgreSQL locking, authenticated browser, native device, remote CI, live
DeepSeek and production remain separately reported.

## Release boundary

The root integration owner stages explicit paths and creates the planned
Conventional Commits. Push only
`feature/campuscore-java25-jpa-enrollment`; do not force-push. If all gates
pass, preserve the dirty `main` checkout on a named WIP branch, merge the
candidate through a clean integration worktree, and update `main` only after
the before/after dirty-content and index identities match. The final handoff
must include exact SHA, commit/push/CI status, unresolved blockers, rollback
path, and the next safe action.
