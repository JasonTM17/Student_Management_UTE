---
name: ak:vibe
description: "Run the full vibe pipeline from request intake to PR readiness, with optional merge and post-merge CI convergence. Orchestrates worktree, plan, cook/fix, code-review, ship, and review-pr. Supports dual-stage beta-then-stable ships via --both and kongming advisory supervision via --advice. Use for GitHub issues, feature requests, bug fixes, or autonomous ship runs."
user-invocable: true
when_to_use: "Invoke when a user wants one command to take a GitHub issue or feature request from planning through implementation, PR review, shipping, and optional merge."
category: dev-tools
keywords: [vibe, pipeline, autonomous, ship, worktree, plan, cook, fix, review-pr, ci, advice, kongming]
argument-hint: "[--ship] [--beta] [--both] [--advice] <github-issue-url | feature request>"
license: MIT
metadata:
  author: agentkit
  version: "1.2.0"
---

# Vibe Pipeline

Run a full autonomous product-development pipeline from request intake to PR
readiness, with optional merge and post-merge CI convergence.

This skill orchestrates across `/ak:worktree`, `/ak:plan`, `/ak:cook`, `/ak:fix`,
`/ak:code-review`, `/ak:ship`, and `/ak:review-pr`. It does NOT bypass those
skills' approval gates, tests, code-review blockers, branch protections, or
security policies.

## Inputs

Accepted forms:

```bash
/ak:vibe <github-issue-url>
/ak:vibe --ship --beta <github-issue-url>
/ak:vibe --both <github-issue-url>
/ak:vibe --ship <feature request>
```

Flags:

| Flag | Effect |
| --- | --- |
| `--beta` | Ship to beta/dev target via `/ak:ship beta`; final ready label is `ready to ship beta`. |
| `--ship` | After review/fix/reply, merge the PR and watch/fix CI until success or true external blocker. |
| `--both` | Dual-stage ship: run the full beta stage first (ship, review, merge, watch CI until green), then the stable stage (ship official, review, merge, watch CI until green). Implies `--ship` for both stages; supersedes `--beta`. |
| `--advice` | Run the whole pipeline under `kongming` advisory supervision (see Advisory supervision). Composes with any ship mode. |
| no `--beta` | Ship stable via `/ak:ship official`; final ready label is `ready to ship stable`. |
| no `--ship` | Stop after PR is reviewed, fixed, replied, and labeled ready. |

Rows describe individual flags in isolation; when `--both` is present, mode
resolution below wins. Mode resolution: `--both` > `--beta` > default stable.
If `--both` and `--beta` are given together, warn once and proceed in `both`
mode. `--advice` is orthogonal to ship mode and composes with all of them.

## Advisory supervision (`--advice`)

When `--advice` is present, run the whole pipeline under `kongming`
supervision. `kongming` is an advisory-only supervisor: it returns counsel,
never code, and the main agent stays responsible for every decision, edit, and
gate.

Spawn `kongming` at these checkpoints:

- **After each pipeline phase completes** — after the plan gates (step 3), after
  implementation (step 5), and after the local code review (step 6). Pass the
  phase goal, what changed, and the evidence; ask for a go/no-go and the next
  risk to watch before continuing.
- **When stuck** — repeated failures, a blocked step, or contradictory evidence;
  pass everything already tried and the exact obstacle.
- **Before a high-stakes decision** — a design fork, a public-contract or
  security-sensitive change, or an irreversible action (including a promotion
  merge that sweeps unrelated work); get counsel first.
- **After the PR is opened and CI is green** — this is the mandatory review
  gate described below.

Invoke with
`delegate_agent capability(subagent_type="kongming", prompt="<task, evidence, approaches tried, the exact question>", description="advice: <checkpoint>")`.
Give it enough context to answer in one reply; it does not interview.

**Mandatory post-PR review gate:** once the PR is opened, watch and fix CI until
every required check is green (steps 8 and 10), then spawn `kongming` to review
the whole implementation and post its assessment plus concrete next steps as a
comment directly on the PR and the source issue. In `--both` mode this gate runs
per stage (after the beta PR and again after the stable PR).

`--advice` adds supervision; it never bypasses this skill's approval gates,
tests, code-review blockers, branch protections, or security policy.

## Pipeline

1. **Parse and analyze request**
   - Strip `--ship`, `--beta`, and `--both` from arguments, then resolve ship mode (`both` > `beta` > stable). `--both` implies `--ship`.
   - If remaining input is a GitHub issue URL/number, treat that issue as the source of truth. Do not create a duplicate.
   - If remaining input is natural language, treat it as the feature request and create the GitHub issue after plan validation/red-team.
   - Resolve repo with `gh repo view --json nameWithOwner,defaultBranchRef`.
   - For GitHub issue URLs, parse `OWNER/REPO` from the URL and compare it with the current repo. If it differs, stop and ask the user to switch to the matching repo/worktree or provide an issue from the current repo.
   - For issue inputs, read the title, body, and comments with `gh issue view`. For natural-language inputs, use the text directly.
   - Extract concrete outcome, acceptance criteria, scope boundary, non-negotiable constraints, blockers, and likely touched surfaces.
   - Classify implementation route:
     - **Bugfix route** when the issue/request is a bug, regression, broken behavior, failing test/CI, production/staging incident, error log, or explicitly says fix/debug/repair.
     - **Feature route** for net-new capability, enhancement, refactor, or ambiguous product work.
   - Detect an existing plan in this order, verifying the resolved `plan.md` exists on disk before treating it as reusable: (1) a user-provided plan path; (2) for an issue input, the linked plan in the local index via `ak plan search --issue <n>` (plan state is files-first — the index resolves a plan by issue number without a GitHub link); (3) the current-plan pointer, then `ak plan resolve` (repo + branch); (4) an issue body/comment linking a `plans/.../plan.md`, or a matching plan already in the current worktree. Detection runs before worktree creation, so the pointer and `ak plan resolve` are often unset here — that is expected, not a failure. If `ak` is missing/errors, or `resolve` reports an ambiguity, present the candidates and fall through to the file/issue scan rather than treating it as "no plan". The issue-link/worktree scan stays first-class: a teammate-created plan may exist only as repo files plus an issue link, because the index is per-machine.
   - If any of those are ambiguous enough to change implementation, ask before worktree creation. Otherwise proceed and carry the extracted requirements into planning and issue updates.

2. **Create isolated worktree and branch**
   - Activate `/ak:worktree` to create an isolated worktree and branch.
   - Use a descriptive branch name derived from the issue/request.
   - If an existing clean feature worktree/branch already matches the request, reuse it and record why.
   - Never work directly on `main`, `master`, `dev`, `beta`, or `develop`.

3. **Plan intake and gates**
   - If a valid existing `plan.md` was detected, set `plan.md` to its absolute path, reuse it, and skip `/ak:plan --tdd`.
   - If no valid plan exists, in the new worktree activate:
     ```bash
     /ak:plan --tdd "<source issue or feature request>"
     ```
   - For newly created plans, capture the absolute `plan.md` path from `/ak:plan --tdd`.
   - Always run both gates, even when the plan already existed:
     ```bash
     /ak:plan validate <plan.md>
     /ak:plan red-team <plan.md>
     ```
   - Before implementation, perform the whole-plan consistency sweep required by `/ak:plan`.
   - Do not proceed to implementation while validation failures, accepted red-team findings, or unresolved contradictions remain.

4. **Create or update GitHub issue**
   - Ensure labels exist:
     ```bash
     gh label list --json name --jq '.[].name' | grep -Fx "ready to cook" >/dev/null \
       || gh label create "ready to cook" --color "0E8A16" --description "Plan validated; ready for ak:cook or ak:fix"
     gh label list --json name --jq '.[].name' | grep -Fx "in progress" >/dev/null \
       || gh label create "in progress" --color "FBCA04" --description "Implementation is in progress"
     gh label list --json name --jq '.[].name' | grep -Fx "ready to ship stable" >/dev/null \
       || gh label create "ready to ship stable" --color "5319E7" --description "PR reviewed and ready for stable merge"
     gh label list --json name --jq '.[].name' | grep -Fx "ready to ship beta" >/dev/null \
       || gh label create "ready to ship beta" --color "1D76DB" --description "PR reviewed and ready for beta merge"
     ```
   - If label creation fails for anything other than an existing label, stop and report the exact `gh` error.
   - Compute relative plan link from repo root.
   - If source issue exists, update/comment on it. If input was natural language, create a new issue.
   - Issue update must include:
     - branch name
     - implementation route (`feature` via `/ak:cook` or `bugfix` via `/ak:fix`)
     - implementation summary
     - relative plan link
     - ship mode (`official`, `beta`, or `both`)
     - acceptance criteria from the plan
   - Add `ready to cook`; remove stale `ready to ship stable` and `ready to ship beta`.
   - Record the linkage in the plan index so later runs resolve this plan by issue number: `ak plan update --issue <issue-number>` (add `--root-comment-id <id>` when a tracking comment was posted). Follow the publish-safety protocol in the shared files-first plan-state reference for author-verification and idempotent projection.

5. **Implement or fix**
   - Before activating `/ak:cook` or `/ak:fix`, update the pipeline GitHub issue:
     ```bash
     gh issue edit <issue-number-or-url> --add-label "in progress" --remove-label "ready to cook"
     ```
   - If `ready to cook` is not currently on the issue, use `--add-label "in progress"` without `--remove-label`.
   - If the label update fails for any other reason, stop and report the exact `gh` error. Do not start implementation while the issue state still says `ready to cook`.
   - If the request is on the bugfix route, activate:
     ```bash
     /ak:fix --auto <plan.md>
     ```
   - Pass the source issue/request, failure evidence, validated plan path, scope boundary, and acceptance criteria into `/ak:fix`.
   - If the request is on the feature route, activate:
     ```bash
     /ak:cook --tdd --auto <plan.md>
     ```
   - Honor every hard gate in `/ak:cook`.
   - Honor every hard gate in `/ak:fix` on the bugfix route.
   - If implementation stops for user/business decision, update the GitHub issue with blocker details and stop.

6. **Review local implementation**
   - Activate:
     ```bash
     /ak:code-review --pending
     ```
   - Fix Critical and Important findings before shipping.
   - Re-run relevant validation after fixes.

7. **Ship PR**
   - If `--both` is present, start with the beta stage:
     ```bash
     /ak:ship beta
     ```
     The stable stage runs later, in step 10, only after beta merge and beta CI success.
   - Else if `--beta` is present:
     ```bash
     /ak:ship beta
     ```
   - Otherwise:
     ```bash
     /ak:ship official
     ```
   - Capture PR URL/number from `/ak:ship` output.
   - The ship skill finalizes a plan-backed change as part of its pipeline: it writes `status: completed` to the plan files before committing (so the finalized files ride the ship commit) and records `--linked-pr` after PR creation. The index `close` is deferred to merge (step 10) — no extra action here.

8. **Review/fix/reply PR**
   - Activate:
     ```bash
     /ak:review-pr <pr-url-or-number> --fix --reply
     ```
   - Do not continue until actionable findings are resolved or an external blocker is documented.
   - PR checks must be terminal and green unless the blocker is external and recorded.
   - When `--advice` is present, after CI is terminal and green, run the mandatory post-PR review gate: spawn `kongming` to review the whole implementation and post its assessment plus concrete next steps as a comment on the PR and the source issue (see Advisory supervision).

9. **Apply ready label**
   - If beta mode: add `ready to ship beta`.
   - If both mode: add `ready to ship beta` now; `ready to ship stable` is added in step 10 when the stable-stage PR passes review.
   - Otherwise: add `ready to ship stable`.
   - Add the label to both the source issue and PR when possible.
   - Remove `ready to cook` and `in progress` after PR review/fix succeeds.

10. **Optional merge and CI convergence**
    - Only run this step when `--ship` or `--both` is present.
    - Merge via GitHub using repository convention and branch protection. Prefer `gh pr merge --auto` when required checks are still pending; otherwise use the repo's allowed merge method.
    - Never force push. Never direct-push to protected target branches.
    - After merge, watch target-branch CI/deploy workflows for the merge commit.
    - On merge success, finalize the plan index: match the merged PR to its plan (recorded `--linked-pr`, or plan branch == PR head branch) and run `ak plan close <id>`; optionally append a completion comment to a linked issue per the shared reference's "Delivery finalization" section. A resolve/match miss means already closed or no plan — skip silently. Never delete plan files.
    - If CI fails with a deterministic repo-fixable error:
      1. Inspect the failed run/job logs with `gh run view`.
      2. Create a follow-up fix branch/worktree from the target branch.
      3. Activate `/ak:fix --auto` with exact failing command/error evidence.
      4. Ship the follow-up in the same mode (during `--both`, the current stage's mode: beta stage → beta, stable stage → official), run `/ak:review-pr --fix --reply`, merge, and watch again.
    - Stop only when target-branch CI succeeds, an external blocker remains, or the same blocker survives 3 fix attempts.
    - **Dual-stage (`--both`) sequence:**
      1. **Beta stage:** merge the beta PR and watch beta/dev-branch CI to green using the merge and fix loop above.
      2. Do not start the stable stage while beta CI is red, pending, or blocked. If the beta stage ends on an external blocker or exhausts fix attempts, stop, report, and mark the stable stage as skipped.
      3. **Stable stage:** after beta CI is green, ship stable. Pick the path from how the beta merge landed:
         - If the feature is already merged into the beta/dev branch and the repository promotes beta/dev into stable by convention (release/promotion PR from dev to main), follow that convention. Before merging a promotion PR, list the commits it carries; if it sweeps unrelated work beyond this issue, stop and ask the user instead of merging silently.
         - If the feature branch is still independent of the stable target (no promotion convention; stable receives feature PRs directly), activate `/ak:ship official` from the feature branch.
      4. Capture the stable PR, then activate `/ak:review-pr <stable-pr> --fix --reply`, apply `ready to ship stable` to the source issue and stable PR, and remove `ready to ship beta`. When `--advice` is present, run the mandatory post-PR review gate for the stable PR too: after its CI is terminal and green, spawn `kongming` to review the whole implementation and comment its assessment plus next steps on the stable PR and the source issue (see Advisory supervision).
      5. Merge the stable PR and watch stable-branch CI to green with the same merge and fix loop. The run is complete only when stable CI succeeds or a documented external blocker remains.

## GitHub Issue Body

Use this body when creating a new issue or updating an execution section:

```markdown
## Outcome
<user-visible outcome>

## Implementation
- Branch: `<branch-name>`
- Plan: `<relative/path/to/plan.md>`
- Mode: `<official|beta|both>`
- Route: `<feature|bugfix>`
- PR: `<url once created>`
- Stable PR: `<url once created, only when --both>`

## Acceptance Criteria
- [ ] <criterion from plan>

## Pipeline State
- [x] Worktree and branch created
- [x] TDD plan created or existing plan reused
- [x] Plan validated
- [x] Plan red-teamed
- [x] Issue labeled `in progress` before implementation
- [ ] Implementation complete
- [ ] PR reviewed and fixed
- [ ] Merged and CI green (only when --ship)
- [ ] Beta merged and beta CI green (only when --both)
- [ ] Stable merged and stable CI green (only when --both)
```

## Security

- Never write secrets, tokens, customer data, or private env values into issues, PRs, comments, plans, or logs.
- Redact sensitive command output before posting to GitHub.
- If `gh` auth lacks permission to create labels, issues, PRs, reviews, or merges, stop and report the exact missing capability.
- If CI fails because of missing secrets, unavailable services, or required human approval, record it as an external blocker. Do not weaken tests or hide failures.

## Completion Report

End with:

```markdown
**Vibe Result**
- Source: <issue/request>
- Branch/worktree: <branch> | <path>
- Plan: <relative path>
- Issue: <url>
- PR: <url> (beta-stage PR when --both)
- Stable PR: <url|n/a> (only when --both)
- Mode: official|beta|both
- Route: feature|bugfix
- Review: <approve/request-changes/comment + fix iterations>
- Merge: skipped|merged|blocked (per stage when --both, e.g. `beta: merged / stable: merged`)
- CI: green|failed|blocked (per stage when --both, e.g. `beta: green / stable: green`)

Unresolved questions:
- None
```
