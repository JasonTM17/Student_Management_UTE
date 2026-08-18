---
title: "[Refactor]"
description: "Improve internal structure while preserving an explicit behavior contract."
status: pending
priority: P2
effort: TBD
issue: null
branch: null
tags: [refactor]
blockedBy: []
blocks: []
created: YYYY-MM-DD
---

# [Refactor] AgentKit Plan

**Archetype**: Refactor
**Workflow**: `/ak:scout` -> `/ak:plan` -> `/ak:cook` -> `/ak:test` -> `/ak:code-review`

## Executive Summary

[State the maintainability/performance objective and the behavior that must
remain unchanged in no more than three sentences.]

## Outcome Contract

- **Structural outcome**: [measurable internal improvement]
- **Preserved behavior**: [public API/UI/data/error semantics]
- **Baseline identity/evidence**: [commit + characterization artifact]
- **Success signal**: [objective test/measurement]
- **In scope**: [components]
- **Non-goals**: [behavior changes or adjacent cleanup]
- **Authority**: [read/write/deploy permissions]
- **Stop conditions**: [unexpected behavior/migration/performance drift]

## Current State and Constraints

- **Primary structural problem**: [evidence-backed]
- **Coupling/dependency boundary**: [source links]
- **Backward Compatibility**: [API/schema/config/data invariants]
- **Characterization coverage**: [test/report paths]
- **Performance baseline, if relevant**: [method + identity]
- **Unresolved decisions**: [owner and gate]

## Phase Index

| Phase | File | Preserved behavior / structural outcome | Owner | Gate |
|---|---|---|---|---|
| 01 | [phase-01-characterize.md](./phase-01-characterize.md) | Freeze behavior and risk boundaries | [owner] | [baseline command] |
| 02 | [phase-02-refactor.md](./phase-02-refactor.md) | Apply bounded structural change | [owner] | [focused tests] |
| 03 | [phase-03-verify.md](./phase-03-verify.md) | Compare before/after and review | [owner] | [snapshot verdict] |

Keep only needed phases, but retain at least one real `phase-NN-*.md`. Put
file-level transformations, ordered task checkboxes, and exact commands in the
phase files.

## Verification Matrix

| Invariant | Baseline evidence | Post-change evidence | Owner |
|---|---|---|---|
| [public behavior] | [artifact/command] | [same command/result] | Test |
| [compatibility/data] | [fixture/schema] | [comparison] | Reviewer |
| [performance, if claimed] | [controlled measurement] | [same method] | Reviewer |

## Risk and Recovery

- **Semantic drift risk**: [control]
- **Migration/compatibility risk**: [control or not applicable]
- **Hard-negative/edge controls**: [tests]
- **Rollback**: [exact revert/restore action]
- **Residual limits**: [unmeasured environments or behavior]

## Handoff

- **Current decision**: [READY/BLOCKED/DECISION_REQUIRED]
- **Next phase/owner**: [phase file and role]
- **Independent review**: `/ak:code-review` [plus Wukong if high-risk claim]
- **Commit/push/CI state**: [recorded honestly]
