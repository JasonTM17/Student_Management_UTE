# Execution ledger

Active plan: plans/260824-1905-campuscore-java25-jpa-enrollment/plan.md

## Start snapshot

- Started: 2026-08-24 (Asia/Bangkok), root integration owner.
- Base: `main` = `origin/main` = `38c7447974f93553596d30e4cbb15d5ce626fa28`.
- Dirty boundary: 34 tracked modifications, 34 untracked paths, no staged
  entries at the Phase 0 recheck; tracked diff identity
  `b93a0aabbe56fb1124c8ce43c77a6cfde063eef0fb88858598c2c65a02a82f34`;
  untracked manifest identity
  `c483c1ae06ad0d55578b7e2fb6c06ae430880bddf12a27f83c4d0ba6016483d8`.
- Host evidence: Java 26 executable, Maven 3.9.12 on JDK 24; Java 25 is not
  installed/proven locally and therefore is a future capability gate.

## Workflow and ownership

`ak:goal-warmup` and `ak:plan-lock` were read and applied after the user
approved the complete successor plan. Phase 0 and the integration-owned
toolchain wave are now complete; Phase 2 is the active wave.
Integration owns shared contract, plan state, candidate freeze, staging,
commits and push. DB/JPA, backend/API, Stitch FE, mobile and QA/security
writers receive disjoint worktrees in later waves. Kongming, Wukong, FE
reviewer, tester, debugger and code-reviewer are read-only lanes.

## Phase 0 record

- [x] README, root AGENTS.md and `.agentkit/config.yaml` inspected.
- [x] Successor directory created at the requested path; generated placeholder
  replaced with the accepted outcome contract and phase files.
- [x] Reindex and activate the renamed plan path (`Student_Management_UTE/260824-1218-8`).
- [x] Mark predecessor plan records cancelled/superseded without rewriting
  historical reports.
- [x] Write snapshot/classification reports and create isolated feature
  worktree from the exact base.
- [x] Freeze candidate identity and obtain read-only Phase 0 review (`Kongming
  PASS`, `Wukong NOT_FALSIFIED`).

## Candidate freeze tuple (Phase 0 exact-head review)

- Worktree: `D:\worktrees\Student_Management-feature-campuscore-java25-jpa-enrollment`.
- Branch: `feature/campuscore-java25-jpa-enrollment`.
- `HEAD`: `38c7447974f93553596d30e4cbb15d5ce626fa28`.
- Tracked diff hash: `d1cd51a9b05c8d58f006d3f466edce9973091a19`.
- Untracked manifest hash: `7ae56ceac68ee85c6d6a9ce792ef241c03a599cd` (34 files).
- Candidate status is 34 tracked modifications + 34 untracked files + zero
  staged entries (68 porcelain-v2 lines). The temporary nested-plan duplicate
  is outside the worktree at `D:\worktrees\phase0-quarantine\...` and is not
  included.
- Candidate plan/ledger/reports are available at
  `plans/260824-1905-campuscore-java25-jpa-enrollment/` and resolve through the
  plan store. An ignored `.env` is present; its content/provenance is
  unverified, unread, unstaged and protected from commits (`NOT_PROVEN`).
- Kongming `PASS` and Wukong `NOT_FALSIFIED` were recorded against this exact
  tuple. Any writer change invalidates these reports; rerun exact-head review
  before committing.

## Exact ownership map for writer waves

Only one writer wave runs at a time. Workers receive isolated worktrees and may
not spawn, merge, push, delete, or edit another owner’s files.

| Wave | Owner | Allowed paths | Forbidden overlap |
|---|---|---|---|
| Toolchain | integration owner | `java-services/pom.xml`, `java-services/restful-api/pom.xml`, Maven wrapper, Docker/CI toolchain docs | domain Java, FE/mobile behavior |
| DB/JPA | DB specialist | `java-services/restful-api/src/main/java/**/persistence/**`, typed academic/registration repositories, V13+ and H2 V7+ migrations, DB tests | controllers/DTOs, FE/mobile, assistant provider |
| Backend/API | backend specialist | registration services/controllers/DTOs, problem details, OpenAPI, PDF renderer, API tests | DB entity/migration files, FE/mobile |
| Web | Stitch FE specialist | `frontend/src/**` registration/admin/assistant, frontend tests/e2e/styles | Java, mobile, shared contract unless integration owner approves |
| Mobile | mobile specialist | `mobile/src/**` registration/assistant/client/routes, mobile tests/docs | Java, frontend |
| QA/security/docs | QA/security/docs owner | reports, deterministic abuse tests, docs/check scripts in assigned files | active writer paths; no architecture rewrite |
| Review | Kongming/Wukong/FE reviewer/tester/debugger/code-reviewer | read-only inspection and reports only | all source mutation, commits, pushes |

## Authorized rulings

- Treat all predecessor PASS reports and dirty assistant files as historical or
  candidate input only; none is release evidence for this successor.
- Preserve dirty `main`, existing worktrees, ignored E2E/nginx artifacts and
  Docker objects. No deletion, reset, cleanup, push or deployment occurs in
  Phase 0.
- If Java 25 or PostgreSQL runtime is unavailable, record the exact gate as
  `NOT_RUN`/`BLOCKED_CAPABILITY` rather than substituting another runtime.

## Deferred findings

- Physical microservice/container/image/volume cleanup: `DEFERRED` pending
  exact inventory and authorization.
- Live DeepSeek billing, native device certification, remote CI and production
  cutover: outside local execution authority.

## Phase 1 record

- [x] Added Maven Wrapper 3.9.12 and Maven/Java Enforcer rules.
- [x] Added explicit Java 21 baseline profile and separate CI job.
- [x] Moved compiler, Docker build/runtime, and CI target to Java 25.
- [x] Recorded container evidence in `reports/phase-1-toolchain.md`.
- [x] Java 21 full verify: PASS (Temurin 21 container, exit 0).
- [x] Java 25 full verify: PASS (Temurin 25.0.2 container, 162 tests, exit 0).
- [x] Host JDK 24 default run: EXPECTED FAIL via Enforcer; not used as target
  evidence.

The assistant scheduled-job/H2 schema noise is a known in-scope persistence
isolation finding, not a suppressed failure. It is carried into Phase 2.

## Next resume point

Phase 1 is complete in the candidate container runtime. Begin the single DB/JPA
writer wave from the candidate branch state; do not begin JPA/enrollment edits
on dirty `main` and do not let the worker touch controllers, clients, or
assistant provider code.
