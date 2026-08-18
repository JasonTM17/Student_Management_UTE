---
title: "[Bug Fix]"
description: "Reproduce, falsify, repair, and independently verify a bounded defect."
status: pending
priority: P2
effort: TBD
issue: null
branch: null
tags: [bug-fix]
blockedBy: []
blocks: []
created: YYYY-MM-DD
---

# [Bug Fix] AgentKit Plan

**Archetype**: Bug Fix
**Risk severity**: [Critical/High/Medium/Low]
**Workflow**: `/ak:scout` -> `/ak:debug` -> `/ak:wukong` (high-risk claims) -> `/ak:fix` -> `/ak:test` -> `/ak:code-review`

## Executive Summary

[Describe the defect, affected users/system, and intended outcome in no more
than three sentences. Detailed execution belongs in the linked phase files.]

## Outcome Contract

- **Claim to falsify**: [Current behavior is safe/correct because ...]
- **Expected outcome**: [Observable post-fix behavior]
- **Success signal**: [Exact evidence and threshold]
- **Target identity**: [commit/build/config/data version]
- **Scope**: [Included components]
- **Non-goals**: [Explicit exclusions]
- **Authority**: [Read/write/deploy permissions]
- **Stop conditions**: [When to block or request a decision]

## Reproduction and Evidence

- **Observed symptom**: [Fact, not hypothesis]
- **Portable reproduction**: `[exact command or artifact]`
- **Expected failure signal**: [exit/status/assertion]
- **Current causal hypothesis**: [mark unverified assumptions]
- **Evidence grade**: [E0/E1/E2/E3/E4]
- **Links**: [issue/log/test/source paths; do not copy large logs]

## Phase Index

| Phase | File | Outcome | Owner | Gate |
|---|---|---|---|---|
| 01 | [phase-01-reproduce-and-repair.md](./phase-01-reproduce-and-repair.md) | Reproduce, repair, and add regression coverage | [owner] | [command/result] |
| 02 | [phase-02-independent-retest.md](./phase-02-independent-retest.md) | Independent adversarial and release review, when warranted | [owner] | [identity/verdict] |

Keep only the phases needed, but retain at least one real
`phase-NN-*.md`. Put file-level changes, ordered steps, and task checkboxes in
those phase files—not in this index.

## Required Gates

| Gate | Owner | Exact command/evidence | Required result |
|---|---|---|---|
| Focused regression | Test | `[command]` | PASS |
| Affected integration | Test | `[command]` | PASS |
| Adversarial retest (high/critical) | Wukong + independent reviewer | `[artifact/command]` | NOT_FALSIFIED within coverage |
| Release review | Kongming/Reviewer | `[snapshot identity]` | ACCEPT |

## Risk, Rollback, and Residual Limits

- **Primary regression risk**: [risk and containment]
- **Hard-negative control**: [behavior that must remain valid]
- **Rollback**: [revert/restore command and data limits]
- **Residual limits**: [what the bounded evidence cannot prove]

## Evidence Ledger

| Artifact | Identity/digest | Observation | Owner |
|---|---|---|---|
| [test/log/report] | [SHA/build/run ID] | [result] | [role] |

## Handoff

- **Current decision**: [READY/BLOCKED/DECISION_REQUIRED]
- **Next phase/owner**: [phase file and role]
- **Manifest/mirror/docs impact**: [none or exact files]
- **Commit/push/CI state**: [recorded honestly]
