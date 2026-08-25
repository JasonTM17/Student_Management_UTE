# Phase 7 - Review and handoff checkpoint

## Current state

Writer waves have delivered Java 25/JPA, registration, assistant, web and
mobile slices in the isolated feature worktree. Local focused/full gates,
Mailpit/browser flows and PostgreSQL concurrency rehearsals are green. The
Flyway fail-closed repair and FE/BE terminal defect pass are verified; the
candidate is ready to be committed and frozen for exact-head review. This
handoff must not be read as merge approval before those reviews finish.

## Required read-only lanes

1. Kongming: architecture/sequence, single-API boundary, migration and
   rollback review at the exact frozen SHA.
2. Wukong: falsify PostgreSQL locking, idempotency, owner isolation, cancel/
   completion and migration-preflight claims at that same SHA.
3. FE reviewer: authenticated/unauthenticated responsive, keyboard, focus,
   safe-area, reduced-motion and contrast review.
4. Tester/debugger/code-reviewer: exact-head regression, security and
   maintainability review; no source writes.

## Open gates

- authenticated Playwright: `PASS (5/5)`; native device runtime: `NOT_RUN`;
- final Flyway guard matrix: `PASS (25/25 PostgreSQL authority matrix in total)`;
- fresh exact-head Kongming/Wukong/FE/test/debug/code review: `PENDING` until
  the clean candidate commit is frozen;
- live DeepSeek, remote CI, push/PR and production: separate gates;
- whole-domain academic/catalog/admin/thesis JDBC writer conversion and
  compatibility-alias parity: `DEFERRED/FOLLOW-UP` (outside the bounded
  assistant/auth delivery; not silently treated as PASS);
- hosted Supabase restore rehearsal: `HOLD/NOT_PROVEN` because the current
  project plan exposes no PITR/restore point.

## Handoff rule

The integration owner first stages only the reviewed paths, creates the final
Conventional Commits and proves the candidate clean. Reviewers then inspect
that exact SHA read-only. Only evidence-backed verdicts on the unchanged SHA
authorize tag/merge through a clean integration worktree and image push. No
direct write to dirty `main` and no production claim is permitted by this
report.
