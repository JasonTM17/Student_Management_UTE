---
name: ak:ship
description: "Ship pipeline: merge main, test, review, commit, push, PR. Single command from feature branch to PR URL. Use for shipping official releases to main/master or beta releases to dev/beta branches."
user-invocable: true
when_to_use: "Invoke when a completed branch needs PR shipping workflow."
category: dev-tools
keywords: [ship, PR, merge, push, release]
argument-hint: "[official|beta] [--skip-tests] [--skip-review] [--skip-journal] [--skip-docs] [--dry-run]"
license: MIT
metadata:
  author: agentkit
  version: "2.1.0"
---

# Ship: Unified Ship Pipeline

Approval-bounded command to ship a feature branch. It may automate local
verification, but it never silently widens the file scope, overwrites dirty
work, pushes, merges, or creates a PR without the user's explicit authority.

**Inspired by:** gstack `/ship` by Garry Tan. Adapted for framework-agnostic, multi-language support.

## Arguments

| Flag | Effect |
|------|--------|
| `official` | Ship to default branch (main/master). Full pipeline with docs + journal |
| `beta` | Ship to dev/beta branch. Lighter pipeline, skip docs update |
| (none) | Auto-detect: if base branch is main/master → official, else → beta |
| `--skip-tests` | Skip test step (use when tests already passed) |
| `--skip-review` | Skip pre-landing review step |
| `--skip-journal` | Skip journal writing step |
| `--skip-docs` | Skip docs update step |
| `--dry-run` | Show what would happen without executing |

## Ship Mode Detection

```
If argument = "official" → target = main/master (auto-detect default branch)
If argument = "beta"     → target = dev/beta (auto-detect dev branch)
If no argument           → infer from current branch naming:
  - feature/* hotfix/* bugfix/* → official (target main)
  - dev/* beta/* experiment/*  → beta (target dev/beta)
  - unclear                    → ask_user capability
```

## When to Stop (blocking)

- On target branch already → abort
- Merge conflicts that can't be auto-resolved → stop, show conflicts
- Test failures → stop, show failures
- Critical review issues → ask_user capability per issue
- Major/minor version bump needed → ask_user capability

## When NOT to Stop

- Uncommitted changes → inspect them; include only paths explicitly in scope,
  and preserve unrelated dirty/untracked work
- Patch version bump → auto-decide
- Changelog content → auto-generate
- Commit message → auto-compose
- No version file → skip version step silently
- No changelog → skip changelog step silently

## Pipeline

```
Step 1:  Pre-flight      → Branch check, mode detection, status, diff analysis
Step 2:  Link Issues      → Find/create related GitHub issues
Step 3:  Merge target     → Fetch + merge origin/<target-branch>
Step 4:  Run tests        → Auto-detect test runner, run, check results
Step 5:  Review           → Two-pass checklist review (critical + informational)
Step 6:  Version bump     → Auto-detect version file, bump patch/minor
Step 7:  Changelog        → Auto-generate from commits + diff
Step 8:  Journal          → Write technical journal via /ak:journal
Step 9:  Docs update      → Update project docs via /ak:docs update (official only)
Step 9b: Finalize plan    → ak plan update --status completed (plan-backed; foreground, staged by Step 10)
Step 10: Commit           → Conventional commit with version/changelog
Step 11: Push             → git push -u origin <branch> (explicit authorization)
Step 12: Create PR        → gh pr create with structured body + linked issues (explicit authorization)
Step 12b: Link plan↔PR    → ak plan update --linked-pr <n> (plan-backed; no close until merge)
```

**Detailed steps:** Load `references/ship-workflow.md`
**Auto-detection:** Load `references/auto-detect.md`
**PR template:** Load `references/pr-template.md`
**Writing language:** Load `.codex/skills/ak-review-pr/references/writing-language.md`
**PR body contract:** Load `.codex/skills/ak-review-pr/references/pr-body-contract.md`

## Writing language + PR body (#1195)

Before Step 12, resolve language with
`WL_BIN=.codex/hooks/lib/writing-language.cjs
test -f "$WL_BIN" || { echo "Missing AgentKit Codex hooks; copy .codex alongside .agentkit."; exit 1; }
node "$WL_BIN" --json` and author the PR body in
that language. Titles stay English conventional commits. The body must include
the seven evidence sections (plus Linked Issues / Ship Mode). Prefer honest
`None` / `Not run` / `Unavailable` over invented narrative.

## Token Efficiency Rules

- Steps 4 (tests) and 5 (review): delegate to `tester` and `code-reviewer` subagents — don't inline
- Steps 8 (journal) and 9 (docs): run in **background** — don't block pipeline
- Step 2 (issues): use single `gh` command batch — avoid multiple API calls
- Skip steps early via flags to save tokens on unnecessary work
- Beta mode auto-skips: docs update (Step 9)
- Capture step outputs inline — don't re-read files already in context

## Quick Start

User says `/ak:ship` → run full pipeline → output PR URL.
User says `/ak:ship beta` → ship to dev branch with lighter pipeline.
User says `/ak:ship official` → ship to main with full docs + journal.

## Output Format

```
✓ Pre-flight: branch feature/foo, 5 commits, +200/-50 lines (mode: official)
✓ Issues: linked #42, created #43
✓ Merged: origin/main (up to date)
✓ Tests: 42 passed, 0 failed
✓ Review: 0 critical, 2 informational
✓ Version: 1.2.3 → 1.2.4
✓ Changelog: updated
✓ Journal: written (background)
✓ Docs: updated (background)
✓ Committed: feat(auth): add OAuth2 login flow
✓ Pushed: origin/feature/foo
✓ PR: https://github.com/org/repo/pull/123 (linked: #42, #43)
```

## Important Rules

- **Never skip tests** (unless `--skip-tests`). If tests fail, stop.
- **Never force push.** Regular `git push` only.
- **Never ask for confirmation** except for critical review issues and major/minor version bumps.
- **Auto-detect everything.** Test runner, version file, changelog format, target branch — detect from project files.
- **Framework-agnostic.** Works for Node, Python, Rust, Go, Ruby, Java, or any project with a test command.
- **Subagent delegation.** Use `tester` for tests, `code-reviewer` for review, `journal-writer` for journal, `docs-manager` for docs. Don't inline.
- **Background tasks.** Journal and docs run in background to not block the pipeline.

## Workflow Position

**Typically follows:** `/ak:code-review` (ship after review passes)
**Typically precedes:** `/ak:journal` (document after shipping)
**Related:** `/ak:code-review` (review before shipping), `/ak:test` (test before shipping)
