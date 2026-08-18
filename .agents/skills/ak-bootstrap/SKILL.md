---
name: ak:bootstrap
description: "Bootstrap new projects with research, tech stack, design, planning, and implementation. Modes: full (default interactive), auto (explicit autonomous), fast (skip research), parallel (multi-agent)."
user-invocable: true
when_to_use: "Invoke to start a new project or full-stack setup from scratch."
category: utilities
keywords: [scaffold, project, setup, boilerplate]
license: MIT
argument-hint: "[requirements] [--full|--auto|--fast|--parallel]"
metadata:
  author: agentkit
  version: "1.0.0"
---

# Bootstrap - New Project Scaffolding

End-to-end project bootstrapping from idea to running code.

**Principles:** YAGNI, KISS, DRY | Token efficiency | Concise reports

## Usage

```
/ak:bootstrap <user-requirements>
```

**Flags** (optional, default `--full`):

| Flag | Mode | Thinking | User Gates | Planning Skill | Cook Skill |
|------|------|----------|------------|----------------|------------|
| `--full` | Full interactive | Ultrathink | Every phase | `--hard` | (interactive) |
| `--auto` | Automatic explicit opt-in | Ultrathink | Design only | `--auto` | `--auto` |
| `--fast` | Quick | Think hard | Cook review gates | `--fast` | (interactive) |
| `--parallel` | Multi-agent | Ultrathink | Design only | `--parallel` | `--parallel` |

**Example:**
```
/ak:bootstrap "Build a SaaS dashboard with auth" --fast
/ak:bootstrap "E-commerce platform with Stripe" --parallel
```

## Opening brainstorm gate (all modes)

Before Git initialization, research, design, planning, or scaffolding, capture:

- the intended product outcome;
- technology, safety, compatibility, and delivery constraints;
- explicit non-goals for this bootstrap;
- observable acceptance criteria for the running project.

Reuse an accepted brief or plan when it already contains these fields. Ask only
about a missing decision that would materially change the product or safety.
`--fast`, `--parallel`, and explicit `--auto` do not skip this gate; they only
change execution and approval behavior after the contract is concrete.

## Workflow Overview

```
[Brainstorm Contract] → [Git Init] → [Research?] → [Tech Stack?] → [Design?] → [Planning] → [Implementation] → [Test] → [Review] → [Docs] → [Onboard] → [Final]
```

Each mode loads a specific workflow reference + shared phases.

## Mode Detection

If no flag provided, default to `--full`.

Load the appropriate workflow reference:
- `--full`: Load `references/workflow-full.md`
- `--auto`: Load `references/workflow-auto.md` only when explicitly requested
- `--fast`: Load `references/workflow-fast.md`
- `--parallel`: Load `references/workflow-parallel.md`

All mode references inherit the opening brainstorm contract. Load
`references/shared-phases.md` for implementation through final report.

## Step 0: Git Init (ALL modes)

Check if Git initialized. If not:
- `--full`: Ask user if they want to init → `git-manager` subagent (`main` branch)
- Others: Auto-init via `git-manager` subagent (`main` branch)

## Skill Triggers (MANDATORY)

After early phases (research, tech stack, design), trigger downstream skills:

### Planning Phase
Activate **ak:plan** skill with mode-appropriate flag:
- `--full` → `/ak:plan --hard <requirements>` (thorough research + validation)
- `--auto` → `/ak:plan --auto <requirements>` (auto-detect complexity)
- `--fast` → `/ak:plan --fast <requirements>` (skip research)
- `--parallel` → `/ak:plan --parallel <requirements>` (file ownership + dependency graph)

Pass the brainstorm contract with the requirements so planning preserves the
accepted outcome, constraints, non-goals, and acceptance criteria.

Planning skill outputs a plan path. Pass this to cook.

### Implementation Phase
Activate **ak:cook** skill with the plan path and mode-appropriate flag:
- `--full` → `/ak:cook <plan-path>` (interactive review gates)
- `--auto` → `/ak:cook --auto <plan-path>` (explicit autonomous implementation)
- `--fast` → `/ak:cook <plan-path>` (skip extra research, keep cook review gates)
- `--parallel` → `/ak:cook --parallel <plan-path>` (multi-agent execution)

## Role

Elite software engineering expert specializing in system architecture and technical decisions. Brutally honest about feasibility and trade-offs.

## Critical Rules

- Activate relevant skills from catalog during the process
- Keep all research reports ≤150 lines
- All docs written to `./docs` directory
- Plans written to `./plans` directory using naming from `## Naming` section
- DO NOT implement code directly — delegate through planning + cook skills
- Sacrifice grammar for concision in reports
- List unresolved questions at end of reports
- Run `/ak:journal` to write a concise technical journal entry upon completion

## References

- `references/workflow-full.md` - Full interactive workflow
- `references/workflow-auto.md` - Explicit auto workflow
- `references/workflow-fast.md` - Fast workflow
- `references/workflow-parallel.md` - Parallel workflow
- `references/shared-phases.md` - Common phases (implementation → final report)
