---
title: "[Feature Name]"
description: "Deliver a measurable capability through independently verifiable AgentKit phases."
status: pending
priority: P2
effort: TBD
issue: null
branch: null
tags: [feature]
blockedBy: []
blocks: []
created: YYYY-MM-DD
---

# [Feature Name] AgentKit Plan

**Archetype**: Feature
**Workflow**: `/ak:scout` -> `/ak:plan` -> `/ak:cook` -> `/ak:test` -> `/ak:code-review`

## Executive Summary

[State the user/maintainer outcome, why it matters, and the bounded delivery
shape in no more than three sentences.]

## Outcome Contract

- **Outcome**: [New observable capability]
- **Success signal**: [Metric, behavior, artifact, or contract]
- **Target identity**: [commit/build/schema/API version]
- **In scope**: [components/contracts]
- **Non-goals**: [explicit exclusions]
- **Authority**: [write/deploy/external permissions]
- **Stop conditions**: [risk, missing decision, or evidence threshold]

## Requirements and Context

- **Actors and primary flow**: [concise]
- **Failure/empty/permission states**: [concise]
- **Affected contracts**: [API/schema/event/CLI/config paths]
- **Compatibility/migration**: [required or not applicable]
- **Context links**: [issues, ADRs, source and test paths]
- **Decisions still required**: [owner + deadline/gate]

## Phase Index

| Phase | File | Independently verifiable outcome | Dependencies | Owner |
|---|---|---|---|---|
| 01 | [phase-01-foundation.md](./phase-01-foundation.md) | [contract/foundation outcome] | [] | [owner] |
| 02 | [phase-02-capability.md](./phase-02-capability.md) | [user-visible capability] | [01] | [owner] |
| 03 | [phase-03-release-evidence.md](./phase-03-release-evidence.md) | [operational/release proof when in scope] | [02] | [owner] |

Keep only needed phases, but retain at least one real `phase-NN-*.md`. Detailed
file lists, task checkboxes, implementation order, and commands belong in the
phase files.

## Acceptance Matrix

| Requirement | Authoritative evidence | Owner | Required result |
|---|---|---|---|
| [functional outcome] | [test/demo/API response] | [role] | [observable threshold] |
| [compatibility/security] | [command/report] | [role] | PASS |
| [operability, if applicable] | [health/SLO/rollback evidence] | [role] | PASS |

## Architecture and Risk Summary

- **State/data flow**: [link to diagram/ADR or concise summary]
- **Trust boundaries**: [inputs, identities, permissions]
- **Highest risks**: [risk -> phase/gate]
- **Residual limits**: [what local/deterministic evidence cannot prove]

## Delivery and Recovery

- **Rollout**: [flags/order/compatibility window or not applicable]
- **Rollback**: [exact reversible action and irreversible limits]
- **Observability**: [logs/metrics/alerts required]
- **Release identity**: [commit/build/image/SBOM when applicable]

## Handoff

- **Current decision**: [READY/BLOCKED/DECISION_REQUIRED]
- **Next phase/owner**: [phase file and role]
- **Required Advisor/Kongming/Wukong gate**: [scope and trigger]
- **Commit/push/CI state**: [recorded honestly]
