---
title: "Phase 7: Independent verification and handoff"
status: pending
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
`feature/campuscore-java25-jpa-enrollment`; do not force-push or merge `main`.
The final handoff must include exact SHA, commit/push/CI status, unresolved
blockers, rollback path, and the next safe action.
