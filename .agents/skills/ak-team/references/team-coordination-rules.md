# Team Coordination Rules

> These rules only apply when operating as a teammate within an Agent Team.
> They live inside the `ak:team` skill so normal sessions do not load team-only coordination context.

Rules for agents operating as teammates within an Agent Team.

## File Ownership (CRITICAL)

- Each teammate MUST own distinct files — no overlapping edits
- Define ownership via glob patterns in task descriptions: `File ownership: src/api/*, src/models/*`
- Lead resolves ownership conflicts by restructuring tasks or handling shared files directly
- Tester owns test files only; reads implementation files but never edits them
- If ownership violation detected: STOP and report to lead immediately

## Git Safety

- Prefer git worktrees for implementation teams — each dev in own worktree eliminates conflicts
- Never force-push from a teammate session
- Commit frequently with descriptive messages
- Pull before push to catch merge conflicts early
- If working in a git worktree, commit/push to the worktree branch — not main or dev

## Communication Protocol

- Discover the live message surface before communicating
- Use direct messages for scoped questions and handoffs; identify recipients by live roster name
- Use team-wide messages only for critical issues affecting the entire team
- Reconcile shared work state before sending a completion message to the lead
- Include actionable findings in messages, not just "I'm done"
- Never send structured JSON status messages — use plain text

## AgentKit Stack Conventions

### Report Output
- Save reports to `{CK_REPORTS_PATH}` (injected via hook, fallback: `plans/reports/`)
- Naming: `{type}-{date}-{slug}.md` where type = your role (researcher, reviewer, debugger)
- Sacrifice grammar for concision. List unresolved questions at end.

### Commit Messages
- Use conventional commits: `feat:`, `fix:`, `docs:`, `refactor:`, `test:`, `chore:`
- No AI authorship attribution in commit messages; product/runtime names are valid
- Keep commits focused on actual code changes

### Docs Sync (Implementation Teams Only)
- After completing implementation tasks, lead MUST evaluate docs impact
- State explicitly: `Docs impact: [none|minor|major]`
- If impact: update `docs/` directory or note in completion message

## Task Claiming

- Discover the live shared-work surface and claim the assigned or next unblocked item
- Record ownership and active state before starting
- After completion, inspect shared state for newly unblocked work
- If all tasks blocked, notify lead and offer to help unblock

## Plan Approval Flow

When plan approval is required:
1. Research and plan your approach (read-only — no file edits)
2. Submit the plan through the discovered approval surface
3. Wait for the lead's response
4. If rejected: revise based on feedback, resubmit
5. If approved: proceed with implementation

## Conflict Resolution

- If two teammates need the same file: escalate to lead immediately
- If a teammate's plan is rejected twice: lead takes over that task
- If findings conflict between reviewers: lead synthesizes and documents disagreement
- If blocked by another teammate's incomplete work: message them directly first, escalate to lead if unresponsive

## Shutdown Protocol

- Approve shutdown requests unless mid-critical-operation
- Mark current task completed before approving shutdown only when the assigned work is actually complete
- If work is incomplete, leave status unchanged or mark blocked with a concise handoff before approving or rejecting shutdown
- If rejecting shutdown, explain why concisely
- Respond to the original request through the live shutdown surface

## Idle State (Normal Behavior)

- Going idle after sending a message is NORMAL — not an error
- Idle means waiting for input, not disconnected
- Sending a message to an idle teammate wakes them up
- Do not treat idle notifications as completion signals — check task status instead

## Discovery

- Discover teammates from the live team roster
- Always refer to teammates by NAME (not agent ID)
- Use roster names consistently for messages and work ownership
