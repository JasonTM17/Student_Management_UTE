# AgentKit for OpenCode

This project includes the AgentKit workflow system for OpenCode. Treat
project-local files as authoritative and keep all behavior portable across
machines and project roots.

## Runtime assets

- Skills: `.agents/skills/**/SKILL.md` — OpenCode discovers this shared
  registry natively, exposes every skill through its `skill` tool, and lists
  each one as an `/ak:<name>` command. `ak:common` is an internal helper that
  also appears in that list; it is not a user entrypoint.
- Subagents: `.opencode/agents/*.md`, invocable with `@agent-name` in the
  composer or delegated by the primary agent through the task tool.
- OpenCode adapter config: `.opencode/opencode.json` (loads this file as an
  instruction; it must stay free of model, theme, and credential settings).
- AgentKit preferences and project helpers: `.agentkit/`
- Project-wide contributor rules: `AGENTS.md` — OpenCode reads it natively.

There is intentionally no `.opencode/skills` mirror and no per-skill
`.opencode/commands` files: OpenCode already consumes `.agents/skills`, and a
second copy would duplicate every `/ak:*` entry. Do not search a user-global
Claude, Codex, Cursor, Gemini, or OpenCode installation before the
project-local AgentKit assets. If a required project entrypoint is missing,
report the exact relative path and stop instead of silently using a stale
global copy.

## Workflow routing

1. Read this file and the nearest applicable `AGENTS.md` before changing files.
2. Match the request to an AgentKit skill. Prefer `/ak:agentkit` when routing is
   unclear and `/ak:help` when the user asks what is available.
3. Read the selected `SKILL.md` completely and follow its referenced resources
   as required.
4. For implementation, preserve unrelated work, plan before multi-file changes,
   make focused edits, and verify proportionally.
5. Use specialist subagents when their description matches the bounded task.
   OpenCode subagents get one bounded assignment; the controller owns
   orchestration and cross-agent handoffs.
6. Treat `BLOCKED` and `NOT_RUN` as evidence states, never as `PASS`.

## Common commands

- `/ak:agentkit <task>` — route a complex or unclear request
- `/ak:plan <task>` — create a file-first implementation plan
- `/ak:cook <plan or task>` — implement through the AgentKit workflow
- `/ak:debug <failure>` — reproduce and isolate a root cause
- `/ak:test <scope>` — run focused verification
- `/ak:code-review <scope>` — independent code review
- `/ak:git <operation>` — focused Git operations
- `/ak:help` — inspect the full AgentKit catalog

Every public AgentKit skill also has a matching `/ak:<skill>` command.

## Safety and delivery rules

- Resolve paths relative to the workspace; never write a machine-specific path
  into project assets.
- Do not edit global kit installations unless the user explicitly names them as
  the target.
- Never discard a dirty worktree, reset unrelated changes, or stage files
  outside the requested scope.
- Do not claim a commit was pushed until the remote ref is independently checked.
- Do not claim live, browser, deployment, provider, or cross-platform behavior
  from static/unit evidence alone.
- Do not expose secrets, tokens, credentials, private host paths, or raw auth
  errors in reports or commits.
- OpenCode provider authentication belongs to the machine/user environment,
  never `.opencode/opencode.json` in the repository.
- The OpenCode adapter has no reviewed active-plan session-state bridge. The
  `.agentkit` wrapper validates plan structure and containment but must report
  validation-only instead of claiming persistence.

## Catalog refresh

OpenCode discovers project skills, subagents, and config when a session starts.
After copying or updating this adapter, start a new session from the project
root or restart OpenCode. Trust the project only after reviewing the copied
files.
