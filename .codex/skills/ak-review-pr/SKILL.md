---
name: ak:review-pr
description: "Review a GitHub pull request thoroughly — analyze diff for correctness, security, breaking changes, code quality, and AI-slop patterns. Supports --fix to auto-remediate findings, --reply to post the review back to GitHub via the gh CLI as a formal review, and --merge to merge a ready PR and watch post-merge CI to green."
user-invocable: true
when_to_use: "Invoke to review a GitHub PR by number/URL, optionally fix findings, optionally post the review back to GitHub, optionally merge when ready and watch CI."
category: utilities
keywords: [pr, pull request, review, github, gh, fix, reply, merge, ci, anti-slop, ai-slop]
argument-hint: "<PR number or URL> [--fix] [--reply] [--merge]"
allowed-tools:
  - Bash(gh pr view *)
  - Bash(gh pr diff *)
  - Bash(gh pr checks *)
  - Bash(gh pr review *)
  - Bash(gh pr comment *)
  - Bash(gh pr merge *)
  - Bash(gh pr list *)
  - Bash(gh run view *)
  - Bash(gh run list *)
  - Bash(gh run watch *)
  - Bash(sleep *)
  - Bash(gh api *)
  - Bash(gh auth status *)
  - Bash(command *)
  - Bash(git log *)
  - Bash(git fetch *)
  - Bash(git diff *)
  - Bash(git status *)
  - Bash(git branch *)
  - Bash(git rev-parse *)
  - Bash(git add *)
  - Bash(git commit *)
  - Bash(git push *)
  - Bash(date *)
  - Read
  - Edit
  - MultiEdit
  - Write
  - Glob
  - Grep
  - Task
metadata:
  author: agentkit
  version: "2.2.0"
---

# Review Pull Request

Review PR `$ARGUMENTS` in this repository.

## Modes

- **Review-only** (default): review the PR and print findings to chat. Do not edit, commit, or push.
- **Fix loop** (`--fix`): review, fix all actionable findings, commit+push, then re-review. Repeat until no actionable findings remain.
- **Reply** (`--reply`): after the review (or after the fix loop converges), post the review back to the PR via `gh pr review`.
- **Merge** (`--merge`): after all other modes complete, if the PR is ready to merge, activate `ak:git merge-pr` to merge it, watch post-merge CI until green, and verify follow-up before stopping.

Flags compose: `review-pr 123 --fix --reply` runs the fix loop and posts the final re-review at the end. `review-pr 123 --fix --reply --merge` additionally merges once the loop converges on Approve. Flag order does not matter.

## Argument parsing

Derive `PR_REF` from `$ARGUMENTS` by stripping `--fix` and `--reply` flags:

```
!`PR_REF="$(printf '%s' "$ARGUMENTS" | sed -E 's/[[:space:]]*--(fix|reply|merge)([[:space:]]+|$)/ /g; s/^[[:space:]]+//; s/[[:space:]]+$//')" && printf 'PR_REF=%s\n' "$PR_REF"`
```

Detect flags (the substring match below is intentional — flags may appear in any order):

- `--fix` present → fix-loop mode active
- `--reply` present → reply mode active
- `--merge` present → merge mode active

## Context

PR metadata:
```
!`PR_REF="$(printf '%s' "$ARGUMENTS" | sed -E 's/[[:space:]]*--(fix|reply|merge)([[:space:]]+|$)/ /g; s/^[[:space:]]+//; s/[[:space:]]+$//')" && gh pr view "$PR_REF" --json title,body,author,baseRefName,headRefName,files,additions,deletions,changedFiles`
```

PR diff:
```
!`PR_REF="$(printf '%s' "$ARGUMENTS" | sed -E 's/[[:space:]]*--(fix|reply|merge)([[:space:]]+|$)/ /g; s/^[[:space:]]+//; s/[[:space:]]+$//')" && gh pr diff "$PR_REF"`
```

CI check status:
```
!`PR_REF="$(printf '%s' "$ARGUMENTS" | sed -E 's/[[:space:]]*--(fix|reply|merge)([[:space:]]+|$)/ /g; s/^[[:space:]]+//; s/[[:space:]]+$//')" && gh pr checks "$PR_REF" 2>/dev/null || echo "No checks found"`
```

Diff stat (use to gauge scope vs description claims):
```
!`PR_REF="$(printf '%s' "$ARGUMENTS" | sed -E 's/[[:space:]]*--(fix|reply|merge)([[:space:]]+|$)/ /g; s/^[[:space:]]+//; s/[[:space:]]+$//')" && gh pr diff "$PR_REF" --name-only 2>/dev/null | head -50`
```

## Instructions

Perform a thorough code review of this PR. Follow these steps:

### 0. Resolve writing language
```bash
WL_BIN=.codex/hooks/lib/writing-language.cjs
test -f "$WL_BIN" || { echo "Missing AgentKit Codex hooks; copy .codex alongside .agentkit."; exit 1; }
node "$WL_BIN" --json
```
Load `references/writing-language.md`. Author Summary, Risk level, Findings,
Verdict, blocker/handoff text, and reply prose in that language. Keep severity
labels and GitHub review mechanics (`--approve` / `--request-changes` /
`--comment`) independent of language. If `fallbackReason` is set, note the
fallback in the review body.

Also load `references/pr-body-contract.md` and validate the PR description:
```bash
PR_BIN=.codex/hooks/lib/pr-body-contract.cjs
test -f "$PR_BIN" || { echo "Missing AgentKit Codex hooks; copy .codex alongside .agentkit."; exit 1; }
gh pr view "$PR_REF" --json body -q .body | node "$PR_BIN"
```
Missing required evidence sections or unsupported claims → **Important**
findings. Do not encourage content padding; prefer honest gaps.

### 1. Understand the PR
- Read the PR title, description, and linked issues
- Understand the intent and scope of the changes
- Compare stated scope vs `additions`/`deletions`/`changedFiles` — a wide gap is itself a signal (see anti-slop reference)

### 2. Analyze the diff
- Read every changed file carefully
- For modified files, read the full file (not just the diff) to understand surrounding context
- Check if the changes align with the stated PR purpose

### 3. Check for issues

**Correctness**
- Logic errors, off-by-one, nil/null dereference
- Missing error handling or swallowed errors
- Race conditions in concurrent code
- Edge cases not handled

**Security**
- Injection (SQL, XSS, command, SSRF, path traversal)
- Hardcoded secrets or credentials
- Missing input validation at system boundaries
- Authentication/authorization gaps

**Breaking changes**
- API contract changes (request/response shapes, status codes)
- Database schema changes without migrations
- Config format changes without backwards compatibility
- Removed or renamed exports/public interfaces

**Code quality (anti-slop — terse checklist)**
LLM-assisted PRs commonly introduce code that *runs fine* but pollutes the codebase. Scan the diff for these high-signal patterns:

- New file in dumping-ground dirs (`utils/`, `helpers/`, `lib/common/`, `*manager.ts`) without a clear domain anchor
- Parallel reimplementation of a utility that already exists in the repo (grep for prior art)
- New abstraction (interface + factory + builder) with only one caller — premature
- New config flag for behavior that should be hardcoded
- Defensive paranoia — try/catch around code that cannot throw; null checks on typed-non-null params
- Catch-and-swallow — `catch (e) { console.log(e) }` or `catch { return null }`
- Over-comment — comments paraphrasing code (`// increment counter` next to `counter++`)
- One-line wrappers that add indirection with no value
- Re-implementing stdlib (`chunk`, `range`, `groupBy`) when language or existing dep covers it
- `any` widening, `@ts-ignore`, `// eslint-disable` introduced to silence (not fix) warnings
- Phantom test coverage — tests that exercise lines without meaningful assertions
- Unused imports / exports / parameters / variables introduced
- File grows past the project's size limit (commonly 200 lines) without splitting
- Diff size doesn't match scope ("fix typo" with +800/−60)
- Touches files unrelated to stated purpose
- Commit messages with generic LLM phrasing ("improve code quality and enhance maintainability")

**Load the full taxonomy** in `references/anti-ai-slop.md` when ANY of:
- diff adds >300 lines, OR
- ≥2 inline anti-slop flags above fire, OR
- PR creates >2 new files in `utils/`/`helpers/`/`lib/common/`, OR
- you cannot confidently judge whether a pattern is genuine YAGNI vs slop

The reference covers: structural slop, micro slop, process slop, how to phrase the finding without becoming an AI-witch-hunt, when NOT to flag, and stack-specific appendix (Go, React/TS, Tailwind).

**Project-specific compliance**
- Read the project's loaded instruction surfaces and follow its documentation navigation to locate current architecture, coding, data, UI, and review standards
- Verify every cited rule against the current path, source, tests, or configuration that owns it
- Check the diff against project conventions for: architecture patterns, ID scoping, SQL store rules, i18n catalogs, UI/CSS conventions, package manager, file-size limits
- See `references/project-rules-example.md` for a worked example of project-specific compliance rules (Go gateway, React/Tailwind UI)

**Testing**
- Are new code paths covered by tests?
- Do existing tests still pass with these changes?
- Are edge cases tested?
- Watch for phantom coverage (assertions that always pass)

### 4. Summarize findings

Present your review as:

**Summary**: 1-2 sentence overview of what the PR does.

**Risk level**: Low / Medium / High — based on scope, complexity, and breakage potential.

**Findings**: List issues found, categorized by severity:
- **Critical**: Must fix before merge (bugs, security, data loss)
- **Important**: Should fix (logic issues, missing validation, *structural* AI slop)
- **Suggestion**: Nice to have (style, minor improvements, *micro* AI slop)

> Anti-slop severity rule: **structural** slop (new dumping-ground file, parallel reimpl, abstraction with one caller, schema change without migration, large file growth) → **Important**. **Micro** slop (over-comments, defensive paranoia, one-line wrappers) → **Suggestion**. This keeps `--fix` from churning the diff with cosmetic rewrites the original author won't recognize.

**Verdict**: One of:
- **Approve** — No critical or important issues found
- **Request changes** — Critical or important issues need addressing
- **Comment** — Minor suggestions only, safe to merge as-is

## Fix loop mode (`--fix`)

If `$ARGUMENTS` contains `--fix`, follow this loop after the review steps above:

### 1. Decide whether fixing is needed

- If no actionable findings, stop and report **Approve**.
- Actionable = all **Critical** + **Important** findings, plus **Suggestion** findings that are concrete, low-risk, and tied to PR scope.
- Do not invent new style-only suggestions to keep the loop running.

### 2. Fix all findings

Activate `ak:fix --auto` with the full findings list and PR context:

```
ak:fix --auto "Fix all actionable findings from review-pr <PR_REF>: <finding summary>"
```

Pass the exact evidence:
- PR reference, base branch, head branch
- changed files
- each finding: severity, file path, line/function, expected behavior, actual behavior, why it matters
- constraints: preserve PR scope, avoid unrelated refactors, keep public contracts backward compatible unless the finding requires a contract change

`ak:fix` performs its own scout, diagnose, implementation, verification, and prevention flow. Do not bypass its hard gates.

### 3. Commit and push

After `ak:fix` verifies the fixes, activate:

```
ak:git cp
```

This stages, commits, and pushes the fixes to the PR head branch. Do not run `ak:git cp` if verification failed, secrets are detected, or the working tree contains unrelated user changes.

### 4. Re-review

After the push succeeds, activate `review-pr <PR_REF> --fix` again (carrying `--reply` and `--merge` if they were originally set) and repeat the loop.

Stop only when one of:
- the re-review finds no actionable findings
- `ak:fix` is blocked by a missing user/business decision
- the same finding survives 3 consecutive fix attempts (loop not converging)
- CI or local verification fails in a way `ak:fix` cannot resolve without user input

Final output for `--fix` mode:
- iteration count
- final verdict
- commits pushed
- remaining findings, if any
- blockers or unresolved questions

## Reply mode (`--reply`)

If `$ARGUMENTS` contains `--reply`, post the review back to GitHub as a formal review after the review (review-only) or after the fix loop converges (`--fix`).

### 1. Pre-flight checks

Run these checks. On any failure, **fall back to printing the review locally** and warn the user — never fail the whole skill:

```bash
command -v gh >/dev/null 2>&1 || { echo "gh CLI not installed — printing review locally"; exit 0; }
gh auth status >/dev/null 2>&1 || { echo "gh not authenticated — printing review locally"; exit 0; }
```

### 2. Build the review body

Construct the full markdown body containing the summary, risk level, findings (by severity), and verdict. Append a single-line footer for traceability:

```
*Posted by the installed review-pr skill at <ISO-8601 UTC timestamp>*
```

Use `date -u +"%Y-%m-%dT%H:%M:%SZ"` for the timestamp.

**Length cap**: GitHub limits comment bodies to ~65,536 chars. If the body exceeds 60,000 chars, truncate the *Findings* section and append `[truncated — N findings omitted; see local output]` so the reviewer knows to consult the full chat output.

### 3. Map verdict to gh flag

| Verdict | gh command |
|---|---|
| Approve | `gh pr review "$PR_REF" --approve --body-file -` |
| Request changes | `gh pr review "$PR_REF" --request-changes --body-file -` |
| Comment | `gh pr review "$PR_REF" --comment --body-file -` |

Pipe the body via stdin to avoid shell-quoting issues with backticks and code blocks.

### 4. Self-PR fallback

GitHub blocks approving your own PR. If `gh pr review --approve` exits non-zero with a self-review error (HTTP 422, message matching "Can not approve your own pull request"), retry as a neutral formal review:

```bash
gh pr review "$PR_REF" --comment --body-file -
```

The review still lands in the timeline; the verdict text inside the body still reads "Approve". Note the downgrade in the chat output.

### 5. Composition with `--fix`

In `--fix --reply` mode, post **only the final re-review** when the loop converges. Iteration history lives in the commit log; the PR conversation stays clean.

If the loop terminates due to a blocker (non-converging, `ak:fix` blocked, CI unresolvable), still post the final review — but the verdict will reflect remaining findings (likely **Request changes** or **Comment**), and the body should include the blocker so the human reviewer knows where to take over.

### 6. Idempotency

V1 does not dedupe. Re-running `review-pr 123 --reply` posts a fresh review each time. The traceability footer (step 2) is the seed for future dedup work but is not consumed here.

## Merge mode (`--merge`)

If `$ARGUMENTS` contains `--merge`, run this stage LAST — after the review, after the fix loop converges (`--fix`), and after the review is posted (`--reply`).

### 1. Merge-readiness gate

Merge ONLY when ALL of these hold:

- Verdict is **Approve** (no Critical or Important findings; in `--fix` mode the loop converged with no actionable findings).
- The fix loop (if run) did not terminate on a blocker.
- PR is `OPEN` and `mergeable` (no conflicts): `gh pr view "$PR_REF" --json state,mergeable,reviewDecision`.
- `reviewDecision` is not `CHANGES_REQUESTED` from another reviewer.
- CI checks are all passing, or only pending (pending is acceptable — the merge step uses auto-merge).

If any condition fails, do NOT merge. Report the PR as not-ready with the exact failed condition, and stop. `--merge` is an authorization to merge a ready PR, never an instruction to force an unready one through.

### 2. Merge and watch CI

Activate `ak:git merge-pr` with the PR reference:

```
ak:git merge-pr <PR_REF>
```

`ak:git merge-pr` (documented in the `ak:git` skill) owns the mechanics:

- re-checks readiness, picks the repo's merge method, merges via `gh pr merge` (with `--auto` when required checks are still pending)
- watches post-merge CI on the target branch until every run for the merge commit concludes
- on deterministic CI failure, drives a follow-up fix (`ak:fix --auto` on a new branch) and repeats, up to 3 attempts
- verifies follow-up: PR state `MERGED`, merge commit on the target branch, all watched runs green
- closes the index row of a plan-backed change (matches the merged PR to its plan via `--linked-pr` or head branch, then `ak plan close`; skips silently when there is no plan) per the shared "Delivery finalization" protocol

Do not bypass its readiness gate or stop conditions. Do not stop this skill while post-merge CI is still pending — the run is complete only when target-branch CI is green, an external blocker remains, or the fix attempts are exhausted.

### 3. Failure handling

- If `ak:git merge-pr` refuses (gate failure, branch protection, conflicts): report the blocker; do not retry with different flags to force the merge.
- If post-merge CI ends red after exhausted fix attempts or an external blocker: report the failing runs, the fixes attempted, and hand off to the user.

## Final output

After all modes complete, report to the chat:

- Verdict (Approve / Request changes / Comment)
- Iteration count if `--fix` ran
- Commits pushed if `--fix` ran
- Whether `--reply` succeeded, fell back, or printed-locally
- Merge result if `--merge` ran: merged / not-ready (with failed condition) / blocked — plus merge commit SHA, post-merge CI conclusions, and follow-up fixes shipped
- Remaining findings or blockers
- Unresolved questions, if any
