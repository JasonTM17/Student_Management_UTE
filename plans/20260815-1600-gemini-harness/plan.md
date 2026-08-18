---
title: AgentKit Gemini CLI harness
status: completed
branch: feature/gemini-harness
effort: large
created: 2026-08-15
tags: [agentkit, gemini-cli, adapter, portability]
dependencies: []
---

# AgentKit Gemini CLI harness

## Executive summary

Add a native Gemini CLI adapter that exposes AgentKit skills, `/ak:*` commands,
and specialist subagents from a copy-ready project scaffold. Reuse the existing
`.agents/skills` registry to avoid a sixth skill mirror, and keep Gemini-specific
configuration free of UI, provider credentials, and Claude-only hooks.

## Outcome

A user can copy `.gemini`, `.agents`, `.agentkit`, and `GEMINI.md` into a project,
trust/open that project with Gemini CLI, reload the catalogs, and invoke AgentKit
skills, `/ak:*` commands, and named agents using project-relative assets only.

## Non-goals

- Do not change ClaudeKit.
- Do not configure a Gemini provider, API key, theme, footer, or statusline.
- Do not port Claude/Codex hooks before an event-schema-specific design exists.
- Do not duplicate `.agents/skills` under `.gemini/skills`.
- Do not claim authenticated Gemini model execution without a valid local login.

## Architecture decisions

| Decision | Selected approach | Reason |
| --- | --- | --- |
| Skill registry | `.agents/skills` | Gemini CLI supports it as a workspace alias; avoids drift. |
| Commands | Generated `.gemini/commands/ak/*.toml` | Preserves the `/ak:*` namespace and binds each command to one skill. |
| Agents | Generated `.gemini/agents/*.md` | Uses Gemini-native frontmatter and inherited model/tool access. |
| Context | Root `GEMINI.md` | Gemini-native project rules without importing UI behavior. |
| Hooks | Disabled/omitted | Prevents accidental Claude-only schema or network side effects. |
| Portability | Project-relative paths only | Makes the scaffold copy-ready across machines and project roots. |

## Authority and ownership

- Canonical skills remain `engineer/skills`; `.agents/skills` remains the shared
  runtime mirror consumed by Gemini.
- The Gemini adapter generator owns `.gemini/commands/ak` and
  `.gemini/agents`.
- The Gemini validator owns structural, path, command, agent, and copy-smoke
  checks.
- README and `GEMINI.md` own installation and runtime guidance.

## Success criteria

- Exactly one Gemini command exists for all 102 public AgentKit skill entrypoints;
  the only excluded entrypoint is the internal `ak:common` helper.
- Every command resolves an existing workspace skill and forwards `{{args}}`.
- All 17 AgentKit specialist agents have valid Gemini Markdown definitions.
- No Gemini asset contains a host-specific absolute path, provider secret, UI
  override, or Claude-only hook command.
- A temporary copy containing only the documented scaffold passes the adapter
  validator.
- Existing AgentKit project, manifest, cross-reference, and Wukong gates remain
  green or report an explicit environmental capability block.

## Evidence and stop conditions

- Authoritative local evidence: generator check, Gemini adapter validator,
  project-assets validator, install-manifest check, and `git diff --check`.
- Gemini CLI catalog parsing is required when the installed CLI can run without
  provider dispatch. Authenticated model execution is reported as `NOT_RUN` if
  local credentials reject the request.
- Stop on source/mirror drift, absolute-path leakage, command/skill count drift,
  malformed agent frontmatter, or changes outside the planned scope.

## Completion evidence

- Gemini generator check: 102 commands and 17 agents, no drift.
- Gemini adapter validator: 103 skill entrypoints, 102 public commands, 17
  agents, and a valid minimal-copy smoke test.
- Project-assets validator and 1,490-file install manifest: valid.
- Combined Wukong/adapter gate: `PASS_WITH_BLOCKED_CAPABILITY`; the only blocked
  probe is Windows symlink creation (`EPERM`).
- Authenticated Gemini provider execution remains `NOT_RUN`; adapter/catalog
  compatibility is complete without claiming provider authentication.
- Independent child-agent tester/reviewer dispatch was unavailable because the
  local subagent usage quota was exhausted; deterministic local gates and a
  manual diff review were used instead.

## Phase index

- [Phase 01 — Implement and validate the Gemini adapter](./phase-01-gemini-adapter.md)
