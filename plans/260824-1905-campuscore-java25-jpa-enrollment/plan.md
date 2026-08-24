---
title: "CampusCore Professional Successor - Java 25 JPA Enrollment"
description: "Consolidate CampusCore on one Java REST API, migrate persistence to JPA, and deliver HCMUTE-style course registration across web and mobile."
status: in-progress
priority: P1
effort: ""
tags: [java25, jpa, enrollment, restful-api, frontend, mobile, chatbot]
created: 2026-08-24
---

# CampusCore Professional Successor - Java 25 JPA Enrollment

## Outcome contract

CampusCore is handed off as one Java Spring Boot 3.5.16 REST API, PostgreSQL
15/Flyway, Next.js web, and Expo mobile. Java 25 LTS is the target runtime and
JPA is the primary persistence boundary. Registration is a first-class,
server-authoritative HCMUTE-style workflow with registration rounds, cohort
windows, configurable credit limits (default 28), prerequisites, hard schedule
conflict rejection, capacity locking, idempotent mutations, audit history, and
SHA-256-verified PDF registration slips. The bounded assistant remains
source-bound, privacy-guarded, SSE on web, and JSON on mobile.

## Scope guard

In scope: Java 25 toolchain, JPA migration, additive Flyway V13+, registration
domain/API, problem-details errors, PDF slip, web registration/admin/chatbot UX,
mobile JSON parity, tests, docs, safe runtime consolidation, split commits and
feature-branch push.

Out of scope: Spring Boot 4, Maven 4, PostgreSQL major upgrade, waitlist,
payment/finance, realtime, personal academic data in the assistant, production
cutover, live provider billing, remote branch/image/volume deletion, and any
destructive cleanup without an exact authorized target.

## Current evidence and authority

- Base candidate: `main` and `origin/main` at
  `38c7447974f93553596d30e4cbb15d5ce626fa28`.
- Dirty boundary at plan start: 34 modified tracked paths and 34 untracked
  paths; tracked diff `b93a0aabbe56fb1124c8ce43c77a6cfde063eef0fb88858598c2c65a02a82f34`;
  untracked manifest `c483c1ae06ad0d55578b7e2fb6c06ae430880bddf12a27f83c4d0ba6016483d8`.
- Existing source is already one Maven module/API, but host Maven runs on JDK
  24 and Java 25 proof is not yet available.
- Existing assistant V11/V12 files are dirty/untracked and must be reverified;
  predecessor PASS evidence is historical context only.
- Current plan was approved by the user; integration owner is the root agent.

## Ordered phases

| # | Phase | Exit criterion | Status |
|---|---|---|---|
| 0 | Freeze and successor artifacts | New plan validates, exact snapshot/ownership ledger exists, isolated feature worktree is created, no data deletion | completed |
| 1 | Java 21 baseline and Java 25 toolchain | Reproducible Java 21 baseline plus real Java 25 build/test evidence | completed |
| 2 | JPA foundation and forward migrations | Fresh and upgraded DBs migrate without data loss; JPA parity and negative constraints pass | in-progress |
| 3 | Registration domain/API/PDF | Round, eligibility, locking, idempotency, problem codes, pagination and PDF contract pass | pending |
| 4 | Web registration, admin and chatbot | 390/768/1440 browser flows, accessibility and SSE states pass | pending |
| 5 | Mobile parity | JSON registration, retry/offline/session states and typecheck pass; device status honest | pending |
| 6 | Consolidation and docs | Runtime scan shows one API + PostgreSQL; docs/OpenAPI/CI match implementation | pending |
| 7 | Independent review and handoff | Exact-head reviews, focused/full gates, split commits and branch push complete | pending |

## Public contract

Canonical registration routes are `/api/v1/registration/rounds`,
`/api/v1/me/registration/*`, and `/api/v1/me/enrollments`. Mutations require
`Idempotency-Key`; compatibility aliases remain deprecated. Errors use
`application/problem+json` with stable reason codes for window, cohort,
capacity, credit, prerequisite, corequisite, schedule, owner, idempotency and
version conflicts. Registration slips are generated after commit and return a
stable SHA-256 hash header. Web assistant uses validated SSE; mobile uses the
same JSON core.

## Ownership and review

DB/JPA, backend/API, Stitch FE, mobile, QA/security and documentation writers
must own disjoint paths and run in one wave at a time. Kongming, Wukong, FE
reviewer, tester, debugger and code-reviewer are read-only review lanes. The
root agent integrates, freezes identity, stages explicit paths, commits and
pushes. Workers do not merge, push, delete data or spawn nested agents.

## Verification and release boundary

Focused checks run at each phase; full Maven, PostgreSQL, web, mobile,
Playwright, secret, encoding, docs, Compose and diff gates run at phase/terminal
checkpoints. Java 24/26 host runs are not Java 25 proof. Local/source/runtime
PASS is separate from CI, push, device, provider and production evidence.

## Risks and rollback

JPA conversion is a high-risk rewrite: use repository parity and dual-read
comparison before switching each domain, with JDBC writes disabled only after
parity. Flyway is forward-only; rollback is backup/restore rehearsal, not
down-migration. Do not return to an old writer after idempotency/version columns
are active. Chatbot rollback is provider-disabled lexical fallback. Preserve
archive tags, ignored E2E files, Docker volumes and old images unless an exact
owner-authorized cleanup gate passes.

<!-- slug: campuscore-java25-jpa-enrollment -->
