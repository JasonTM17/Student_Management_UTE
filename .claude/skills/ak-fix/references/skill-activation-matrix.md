# Skill Activation Matrix

When to activate each skill and tool during fixing workflows.

## Always Activate (ALL Workflows)

Before activating tools, capture or reuse the opening outcome, constraints,
non-goals, and acceptance criteria. This is a workflow gate, not a separate
mandatory subagent.

| Skill/Tool | Step | Reason |
|------------|------|--------|
| `ak:scout` OR parallel `Explore` when permitted | Step 1 | Understand codebase context before diagnosing |
| `ak:debug` | Step 2 | Systematic root cause investigation |
| `ak:sequential-thinking` | Step 2 | Structured hypothesis formation — NO guessing |
| `the engineer project-management skill` | Step 6 | MANDATORY for sync-back and progress tracking, every fix |

## Task Orchestration (Moderate+ Only)

| Tool | Activate When |
|------|---------------|
| Live task management | After complexity assessment, when runtime discovery confirms support |
| `delegate_agent` | User explicitly requested subagents/delegation/parallel work and runtime permits it |

Skip progress orchestration for Quick workflow (< 3 steps).

## Auto-Triggered Activation

| Skill | Auto-Trigger Condition |
|-------|------------------------|
| `ak:problem-solving` | 2+ hypotheses REFUTED in Step 2 diagnosis |
| `ak:sequential-thinking` | Always in Step 2 (mandatory for hypothesis formation) |

## Conditional Activation

| Skill | Activate When |
|-------|---------------|
| `ak:brainstorm` | After diagnosis, when multiple valid fix approaches or an architecture decision remain |
| `ak:context-engineering` | Fixing AI/LLM/agent code, context window issues |
| `ak:ai-multimodal` | UI issues, screenshots provided, visual bugs |

## Subagent Usage

| Subagent | Activate When |
|----------|---------------|
| `debugger` | Root cause unclear, need deep investigation (Step 2) |
| `Explore` (parallel) | Scout multiple areas simultaneously (Step 1), test hypotheses (Step 2), only when delegation is explicitly requested/permitted |
| Verification workers | Verify implementation: typecheck, lint, build, test (Step 5), only when delegation is explicitly requested/permitted |
| `researcher` | External docs needed, latest best practices (Deep only) |
| `planner` | Complex fix needs breakdown, multiple phases (Deep only) |
| `tester` | After implementation, verify fix works (Step 5) |
| `ak:code-review` | After fix, verify quality and security (Step 5) |
| `git-manager` | After approval, commit changes (Step 6) |
| `docs-manager` | API/behavior changes need doc updates (Step 6) |
| `fullstack-developer` | Parallel independent issues (each gets own agent) |

## Parallel Patterns

See `references/parallel-exploration.md` for detailed patterns.

| When | Parallel Strategy |
|------|-------------------|
| Scouting (Step 1) | 2-3 `Explore` agents on different areas, only when delegation is permitted |
| Testing hypotheses (Step 2) | 2-3 `Explore` agents per hypothesis, only when delegation is permitted |
| Multi-module fix | `Explore` each module in parallel, only when delegation is permitted |
| After implementation (Step 5) | `run_shell`: typecheck + lint + build + test; delegate only when permitted |
| 2+ independent issues | Plan trees + delegated agents per issue when permitted |

## Workflow → Skills Map

| Workflow | Skills Activated |
|----------|------------------|
| Quick | opening intent frame, `ak:scout` (minimal), `ak:debug`, `ak:sequential-thinking`, `ak:code-review`, `the engineer project-management skill`, `run_shell` verification |
| Standard | opening intent frame + Quick tools, optional live task management, `ak:problem-solving` (auto), optional post-diagnosis `ak:brainstorm`, optional delegated tester/Explore when permitted |
| Deep | opening intent frame + all above, post-diagnosis `ak:brainstorm`, `ak:context-engineering`, `researcher`, `planner` |
| Parallel | Per-issue plan trees + `ak:project-management` + delegated agents + live coordination when available |

## Step → Skills Chain (Mandatory Order)

| Step | Mandatory Chain |
|------|----------------|
| Opening gate | capture or reuse outcome → constraints → non-goals → acceptance criteria |
| Step 0: Mode | `ask_user capability` only when mode is not explicit or safely inferable |
| Step 1: Scout | `ak:scout` OR 2-3 parallel `Explore` when delegation is permitted → map files, deps, tests |
| Step 2: Diagnose | Capture pre-fix state → `ak:debug` → `ak:sequential-thinking` → optional delegated Explore hypotheses → (`ak:problem-solving` if 2+ fail) |
| Step 3: Assess | Classify complexity → choose direct cause-aligned fix or post-diagnosis `ak:brainstorm` → record dependencies in the active plan and optional live surface (moderate+) |
| Step 4: Fix | Implement per workflow → follow root cause |
| Step 5: Verify+Prevent | Iron-law verify → regression test → defense-in-depth → `run_shell` verify |
| Step 6: Finalize | Report → `the engineer project-management skill` (MANDATORY) → docs-impact decision → conditional `docs-manager` → sync runtime tracking when available → `git-manager` → `/ak:journal` |

## Detection Triggers

| Keyword/Pattern | Skill to Consider |
|-----------------|-------------------|
| "AI", "LLM", "agent", "context" | `ak:context-engineering` |
| "stuck", "tried everything" | `ak:problem-solving` |
| "complex", "multi-step" | `ak:sequential-thinking` |
| "which approach", "options" | `ak:brainstorm` |
| "latest docs", "best practice" | `researcher` subagent |
| Screenshot attached | `ak:ai-multimodal` |
