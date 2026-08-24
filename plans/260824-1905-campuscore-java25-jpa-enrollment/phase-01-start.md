---
title: "Phase 0: Freeze and successor artifacts"
status: completed
---

# Phase 0: Freeze and successor artifacts

## Objective

Create a durable successor record without mutating the dirty `main` checkout,
preserve every pre-existing user change, and establish an isolated feature
worktree for all implementation waves.

## Authorized inputs

- Accepted plan: `plans/260824-1905-campuscore-java25-jpa-enrollment/plan.md`.
- Base identity: `main`/`origin/main` at
  `38c7447974f93553596d30e4cbb15d5ce626fa28`.
- Dirty-boundary identities recorded in the parent plan; they are evidence of
  the starting checkout only and are not candidate implementation proof.

## Steps and evidence

1. Validate and activate this successor plan after reindexing its renamed
   directory. Keep both predecessor plan records and mark them cancelled with
   a superseded note; do not rewrite their historical reports.
2. Record an explicit tracked/untracked manifest and classify files as
   `KEEP`, `REVIEW`, or `DEFER`. Never read `.env` or copy credentials.
3. Create `feature/campuscore-java25-jpa-enrollment` from the exact base in a
   worktree under the configured disposable worktree root. The root checkout
   remains dirty and untouched.
4. Import only reviewed, explicit assistant changes into the candidate when
   required by the implementation plan. Record source paths and hashes; do not
   call imported changes PASS merely because they existed in the dirty tree.
5. Freeze the candidate identity and hand it to the required read-only
   reviewers before the first writer wave.

## Exit evidence

`ak plan validate` passes for this plan; the plan store points to this exact
path; the predecessor records are cancelled/superseded; the snapshot,
provenance, ownership and independent-review reports exist; the isolated
feature worktree is present at the exact base; root and candidate match at
34/34/0 with tracked hash `d1cd51a9...` and manifest hash `7ae56ce...`; no
data, Docker object, branch, or source file was destructively deleted; and the
ledger names Phase 1 as the next incomplete phase. Kongming is `PASS` and
Wukong is `NOT_FALSIFIED` for this bounded gate.

## Verification budget

Run only plan validation, Git identity/status, worktree inspection, and
manifest/hash checks in this phase. Broaden to build/test only after the
candidate is frozen and Phase 1 begins.
