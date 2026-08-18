# Merge PR Workflow (`merge-pr`)

Merge a GitHub pull request via `gh`, then watch post-merge CI on the target branch until green and verify the result before stopping.

## Variables

- PR_REF: PR number or URL (required)
- MERGE_METHOD: repo convention (`--squash`, `--rebase`, or default merge commit)
- TO_BRANCH: target branch (`baseRefName` from Step 1)

## Step 1: Pre-merge readiness gate

All checks must pass before merging. On any failure, STOP and report — never merge past a red gate.

```bash
gh pr view "$PR_REF" --json state,mergeable,mergeStateStatus,reviewDecision,baseRefName,headRefName,title,url
gh pr checks "$PR_REF"
```

Gate conditions:

| Check | Requirement |
|-------|-------------|
| `state` | `OPEN` |
| `mergeable` | `MERGEABLE` (no conflicts) |
| CI checks | All passing, or only pending (pending → use `--auto`) |
| `reviewDecision` | Not `CHANGES_REQUESTED` |
| Branch | Never merge into a branch the repo forbids; respect branch protection |

If any check fails deterministically (red CI, conflicts), report the blocker instead of merging.

## Step 2: Pick merge method

Follow repository convention, in priority order:

1. Method documented in the project's `CLAUDE.md` / `CONTRIBUTING.md`.
2. Method used by recent merged PRs: `gh pr list --state merged --limit 5 --json mergedAt,number` then inspect.
3. Repo settings: if only one method is allowed, `gh pr merge` fails with a clear error — retry with the allowed method.
4. Default: merge commit.

## Step 3: Merge

```bash
# All checks green:
gh pr merge "$PR_REF" {MERGE_METHOD}

# Required checks still pending:
gh pr merge "$PR_REF" {MERGE_METHOD} --auto
```

Rules:
- Never force push. Never direct-push to protected target branches.
- Do not pass `--delete-branch` unless the repo convention deletes head branches.
- With `--auto`, poll until the PR actually merges before moving to Step 4:

```bash
gh pr view "$PR_REF" --json state,mergedAt   # repeat with sleep 30 until state == MERGED
```

If auto-merge waits on a check that never completes (stuck > 30 min), report the stall as a blocker.

## Step 4: Watch post-merge CI

Run only after Step 3 confirms `state == MERGED` — before that, `mergeCommit.oid` is empty and `gh run list --commit ""` silently returns nothing. Watch the target-branch workflows triggered by the merge commit:

```bash
MERGE_SHA=$(gh pr view "$PR_REF" --json mergeCommit -q .mergeCommit.oid)
gh run list --branch {TO_BRANCH} --commit "$MERGE_SHA" --json databaseId,name,status,conclusion
gh run watch <run-id> --exit-status
```

Repeat until every run for the merge commit concludes. `sleep 30` between polls if `gh run watch` is unavailable.

## Step 5: Handle CI failure

If a post-merge run fails with a deterministic, repo-fixable error:

1. Inspect logs: `gh run view <run-id> --log-failed`.
2. Create a follow-up fix branch from the target branch (never fix on the merged head branch).
3. Activate `ak:fix --auto` with the exact failing command and error evidence.
4. Ship the fix through the repo's normal PR flow, merge it with this same workflow, and watch again.

Stop conditions — stop and report when any of:
- target-branch CI is green (success)
- the failure is an external blocker (infra outage, missing secret, flaky third-party)
- the same failure survives 3 fix attempts (not converging)

## Step 6: Verify follow-up

Before declaring done:

- Confirm PR state is `MERGED` and the merge commit exists on the target branch:
  `git fetch origin && git log origin/{TO_BRANCH} --oneline -5`
- Confirm all CI runs for the merge commit concluded `success`.
- If the repo has post-merge automation (release tagging, deploy workflows), confirm those runs also succeeded or are intentionally out of scope.
- **Close a plan-backed change's index row.** Match this merged PR to a plan by
  its recorded `--linked-pr`, or by plan branch == the PR head branch (`ak plan
  list --json` — post-merge you are on the target branch, so `resolve`, scoped to
  the current branch, will not find the head-branch plan). On a unique match,
  `ak plan close <id>` — an
  index-only transition; the plan files already carry `status: completed` from
  the ship commit. A match miss means the plan is already closed or there is no
  plan — skip silently; on ambiguity, skip and report. Optionally append (never
  edit) a completion comment to a linked issue per the "Delivery finalization"
  section of `.codex/skills/ak-cook/references/plan-state-files-first.md`.
  Never delete or hand-edit plan files.
- Report: PR URL, merge commit SHA, merge method, CI runs watched and their conclusions, follow-up fixes shipped (if any).

## Error Handling

| Error | Action |
|-------|--------|
| Merge conflicts (`CONFLICTING`) | Report; suggest updating the head branch, do not resolve on the target branch |
| Branch protection blocks merge | Report required approvals/checks; never bypass |
| `gh` not authenticated | Report; suggest `gh auth login` |
| Self-merge forbidden by repo policy | Report; hand off to a human maintainer |
| CI stuck pending > 30 min | Report stall with run URL |
