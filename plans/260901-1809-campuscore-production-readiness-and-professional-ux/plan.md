---
title: "CampusCore production readiness and professional UX"
description: "Web-first product polish, campus assistant expansion, and verified production packaging."
status: in-progress
priority: P1
effort: XL
issue: null
branch: release/campuscore-production-readiness
tags: [frontend, chatbot, supabase, docker, release]
blockedBy: ["specialist-agent-capacity", "rotated-deepseek-key", "supabase-production-credentials", "vps-domain-for-cutover"]
blocks: []
created: 2026-09-01
---

# CampusCore Production Readiness and Professional UX

**Archetype**: Feature / release hardening  
**Workflow**: `/ak:goal-warmup` -> `/ak:advise` -> `/ak:scout` -> `/ak:plan-lock` -> `/ak:team` -> `/ak:test` -> `/ak:code-review` -> `/ak:wukong` -> `/ak:ship`

## Outcome Contract

- **Outcome**: Professional bilingual web portal, broad cited campus assistant, hardened production Compose bundle, and reviewed dual-registry release.
- **Success signal**: final main SHA has green CI, exact-head review, four image digest parity, passing FE/BE/chatbot/security/release gates, and reproducible backup/restore/rollback evidence.
- **Target identity**: `origin/main` frozen at execution; current expected base `e56f599d4edac216864eb6a2d61fa4509b53d627`; immutable full-SHA image tags.
- **In scope**: `frontend/**`, Java assistant/API and Flyway, Supabase authoring/sync, mobile API compatibility, Compose/Caddy/CI/ops docs, GitHub/Docker Hub/GHCR publication.
- **Non-goals**: VPS/domain cutover, real-device mobile certification, copying university branding/assets, storing a DeepSeek credential in Git or images.
- **Authority**: user authorized code commits, main merge, GitHub push, and paired registry publication after gates; no force-push or destructive cleanup.
- **Stop conditions**: leaked secret, failed isolation/migration/rollback invariant, stale review after a source change, missing required specialist capability, or missing external VPS/domain evidence.

## Current Evidence and Safety Boundary

The original checkout is dirty on `fix/chrome-dogfood-findings`; its modified and untracked work is preserved in the external recovery bundle `D:/Student_Management-recovery/production-readiness-20260901`. Existing worktrees remain untouched. Named feature branches currently contain no unique commits over main, so only reviewed new commits and explicitly reconciled WIP enter the release set.

The repository is `JasonTM17/Student_Management_UTE`. GitHub Actions currently exposes Docker Hub credential names only; DeepSeek and Supabase values must be provisioned later through runtime/protected secrets. The key pasted in chat is compromised and must be revoked before any live provider smoke.

## Phase Index

| Phase | File | Independently verifiable outcome | Dependencies | Owner |
|---|---|---|---|---|
| 01 | [phase-01-foundation.md](./phase-01-foundation.md) | Clean snapshot, ownership ledger, contract freeze | [] | integration |
| 02 | [phase-02-fe-design.md](./phase-02-fe-design.md) | Audited and approved hybrid editorial design system | 01 | FE specialist |
| 03 | [phase-03-fe-implementation.md](./phase-03-fe-implementation.md) | All critical web routes restyled and accessible | 02 | FE writer |
| 04 | [phase-04-campus-assistant.md](./phase-04-campus-assistant.md) | Supabase-authoritative campus RAG with SQL last-good projection | 01 | backend/RAG writer |
| 05 | [phase-05-production-bundle.md](./phase-05-production-bundle.md) | Hardened Caddy Compose, secrets, backup and release workflow | 01 | platform writer |
| 06 | [phase-06-mobile-compatibility.md](./phase-06-mobile-compatibility.md) | Mobile source/API compatibility without device-ready claim | 04 | mobile writer |
| 07 | [phase-07-qualification.md](./phase-07-qualification.md) | Independent tester and adversarial reports on one candidate SHA | 03,04,05,06 | tester wave |
| 08 | [phase-08-ship.md](./phase-08-ship.md) | Reviewed main merge and immutable dual-registry publication | 07 | integration |

## Acceptance Matrix

| Requirement | Evidence | Required result |
|---|---|---|
| Web quality | Playwright route/persona matrix, axe, screenshots, keyboard and performance report | PASS; no serious/critical axe issue, no console/hydration/API error, WCAG AA |
| Backend/API | Maven verify, PostgreSQL/Flyway, OpenAPI, auth and regression tests | PASS |
| Assistant | JSON/SSE, citation, owner isolation, idempotency, cancellation, quota, fallback, injection/PII tests | PASS |
| Knowledge projection | Supabase publish/sync fault injection and active-release hash invariants | PASS |
| Production package | Compose/Caddy validation, secret scan, fresh image stack, backup/restore/rollback | PASS |
| Release | Exact-head reviewer, main CI, four images on Docker Hub and GHCR with matching digest/SBOM/provenance | PASS |

## Delivery and Recovery

- Merge only the reviewed release set; do not create empty merges for ancestor branches.
- Use additive Flyway V16 migration; previous verified application remains able to start during rollback.
- Roll back code by previous immutable image digest. Restore the database only through the isolated, checksummed restore procedure when compatibility requires it.
- Never claim hosted production until DNS, TLS, health, authenticated browser, JSON/SSE, backup and rollback evidence exists on the supplied VPS.

## Handoff

- **Current decision**: READY_TO_EXECUTE; release is blocked until required capability and external secrets are available.
- **Next step**: phase 01 foundation and specialist delegation.
- **Required gates**: Kongming architecture review, four FE testers, backend/chatbot/security/release testers, Wukong adversarial checks, exact-head code review.
- **Commit/push/CI state**: not yet changed by this plan execution.
