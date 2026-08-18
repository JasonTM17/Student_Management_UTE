---
name: ak:codex-goal
description: Guide long-running Codex goal work with a verifiable stop condition. Use when users mention /goal, goal mode, durable objectives, or autonomous multi-turn Codex runs.
user-invocable: true
when_to_use: Invoke for Codex-native /goal guidance, not generic iteration loops or multi-CLI orchestration.
category: utilities
keywords: [codex, goal, autonomous, validation, long-running]
license: MIT
argument-hint: "<objective | goal draft>"
metadata:
  author: agentkit
  version: "1.0.0"
  upstream: "Pinned MIT source archive: codex-goal-loop@ce70edaa26247b84c2b9491a0cdb4964f65cf3a5"
---

# Codex Goal

Use Codex /goal for a durable objective that has a clear stopping condition and
a validation loop. It is not a safety boundary, a replacement for product
decisions, or a way to run an unbounded backlog.

## Availability Check

Confirm /goal appears in the Codex slash-command list. If it does not, enable
the documented feature flag in config.toml:

    [features]
    goals = true

Alternatively run: codex features enable goals

As verified on 2026-07-11, the official guide documents setting a goal with
/goal <objective>, checking it with /goal, and controlling it with /goal pause,
/goal resume, and /goal clear. Re-check the current documentation before relying
on any behavior not stated here:
https://learn.chatgpt.com/use-cases/follow-goals

## Use Test

Use a goal only when all three are true:

1. The task is longer than one normal turn and mainly mechanical.
2. The stop condition is verifiable through tests, an eval, a build, or another
   explicit artifact.
3. The scope is sufficiently clear that Codex can make progress without a
   product or architecture decision at each checkpoint.

Do not use it for exploratory work, vague improvement requests, production
credential changes, destructive shared infrastructure, or unrelated backlogs.

## Draft the Goal Contract

Give Codex one objective, files to read first, constraints, a validation command,
checkpoints, and a stop condition:

    /goal Complete <objective>.
    Read first: <plan, issue, files>.
    Constraints: <unchanged contracts and scope boundary>.
    Validate after each checkpoint: <command>.
    Keep a brief progress log.
    Stop when <verifiable end state>, or when further work needs human input.

Include a prohibition against weakening, narrowing, skipping, or deleting tests
to satisfy the goal. Pause for ambiguity instead of inventing a product decision.
Review the final diff before merging.

## Boundaries

- Before multi-hour or high-dependency goals, prefer engineer-kit
  `ak:goal-warmup` to lock an outcome contract and preflight blockers; it does
  not start `/goal` for you.
- Use ak-loop, available in the engineer kit, for local metric-driven iteration.
- Use ak-orchestrate, available in the engineer kit, for dispatch across
  multiple coding-agent CLIs.
- Do not claim undocumented versions, authentication restrictions, or internal
  lifecycle states. Treat the official documentation as the source of truth.
