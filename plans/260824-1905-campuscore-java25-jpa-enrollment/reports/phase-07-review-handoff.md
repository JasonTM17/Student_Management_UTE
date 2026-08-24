# Phase 7 - Review and handoff checkpoint

## Current state

Writer waves have delivered Java 25/JPA, registration, assistant, web and
mobile slices in the isolated feature worktree. Local focused/full gates and an
isolated PostgreSQL 15 concurrency rehearsal are green. The candidate is not
yet frozen for independent review because the latest problem-details repair and
reports are still being committed.

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

- authenticated Playwright and native device runtime: `NOT_RUN`;
- live DeepSeek, remote CI, push/PR and production: separate gates;
- upgraded-copy/JPA writer-cutover proof and Unicode-capable PDF renderer:
  `DEFERRED/OPEN`;
- old JDBC alias parity and operation-first lock-order proof: `OPEN`.

## Handoff rule

Only after the exact SHA is frozen and reviewers return evidence-backed
verdicts may the integration owner stage explicit paths, create Conventional
Commits and push `feature/campuscore-java25-jpa-enrollment`. No direct push to
`main` and no production claim is permitted by this report.
