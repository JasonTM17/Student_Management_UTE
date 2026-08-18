---
name: ak:handoff
description: Create a concise, redacted conversation handoff for a fresh agent session. Use when switching context, ending a work session, or preserving decisions and blockers.
user-invocable: true
when_to_use: Invoke for conversation-state compaction, not a git-derived project status report.
category: utilities
keywords: [handoff, session, context, decisions, blockers]
license: MIT
argument-hint: "[next-session focus]"
metadata:
  author: agentkit
  version: "1.0.0"
  upstream: "Pinned MIT source archive: handoff@ce70edaa26247b84c2b9491a0cdb4964f65cf3a5"
---

# Handoff

Create a factual, compact handoff that lets a fresh agent continue with minimal
rediscovery. Preserve state and rationale, not a command list for the next
agent.

## Workflow

1. Read the project instructions and relevant plans before drafting. Read the
   previous handoff if one exists for the same focus.
2. Capture the goal, current state, key decisions and rationale, rejected
   approaches, blockers, verification status, and pointers to source artifacts.
3. Reference plans, issues, ADRs, commits, diffs, and tests instead of copying
   them into the handoff.
4. Redact secrets, tokens, passwords, private URLs, customer data, and personal
   data. Mention only the safe location of credentials when needed.
5. Output one fenced Markdown block and save the same content under
   plans/reports/handoff-YYYYMMDD-HHmm-<slug>.md. If the project has no plans
   directory, ask the user for a safe output location first.

## Required Shape

    # HANDOFF: <short title>
    Generated: <timestamp> · Session focus: <one line>

    ## Goal
    ## Why This Matters
    ## Current State
    ## Key Decisions and Why
    ## Rejected Approaches and Traps
    ## Verification Status
    ## Relevant Files and Pointers
    ## Open Work and Dependencies

Describe open work as state and dependencies, not imperatives. End with a short
fresh-agent prompt that tells the next agent to read the listed files and verify
the handoff against the repository before acting.

## Boundaries

- Use ak-watzup for a status report derived from branches, worktrees, plans, and
  repository history.
- Do not duplicate root project instructions or introduce new decisions.
- Never write a handoff outside the agreed project or reports location.
