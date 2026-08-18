---
name: ak:agentkit
description: "Task router for AgentKit installs. Classifies the task, activates the right installed skills, chains them into the shortest workflow that fits, and spawns installed subagents at defined trigger points to raise output quality. Use at the start of multi-step, multi-domain, or ambiguous work, or when unsure which skill or agent applies."
user-invocable: true
when_to_use: "Invoke at the start of multi-step or multi-domain work, when the right skill is unclear, when skills need sequencing into a workflow, or when deciding whether and when to spawn subagents."
category: utilities
keywords: [routing, dispatch, skills, chaining, subagents, delegation, workflow, quality]
argument-hint: "[task to route]"
metadata:
  author: agentkit
  version: "1.0.0"
---

# AgentKit Router

Route any task to the right installed capability: the correct skill, the
shortest skill chain that fits, and the right subagents at the right moments.
This skill decides and dispatches — the routed skills and agents do the work.

Routing tables live with their owning skills. This skill adds the decision
protocol on top of them and duplicates none of their content. Output quality
comes from four levers this protocol controls: the right specialist per step,
fresh-context subagents for noisy or parallel work, verification steps matched
to risk, and refusing to orchestrate what a single skill does better.

## Boundaries

| Situation | Owner |
|---|---|
| Pick and sequence installed skills, time subagent spawns, in this session | this skill |
| Coordinate headless CLI jobs across runtimes, models, worktrees | the orchestrate skill (`ak-orchestrate`, engineer installs) |
| Run multi-session agent teams | the team skill (`ak-team`, engineer installs) |
| Falsify a concrete high-risk claim with bounded evidence | the Wukong skill/agent (`ak-wukong`, engineer installs) |
| Discover or install skills you do not have yet | the find-skills skill (`ak-find-skills`, engineer installs) |
| Execute the domain work itself | the routed skill or agent owns execution |

If the task is explicitly about running jobs headlessly, across CLIs, or in
parallel worktrees, hand off to the orchestrate skill now (when installed) and
stop.

## The Protocol

Six steps. Steps 0-2 are cheap and mandatory; steps 3-5 scale with the task.

### Step 0 — Proportionality gate (always run first)

Routing ceremony on a trivial task is itself a quality failure.

| Condition | Action |
|---|---|
| User names a skill to use | Invoke that skill. Stop routing. |
| Single domain, single step, one obviously matching installed skill | Invoke it directly. Stop routing. |
| Pure conversation, opinion, or fact question | Answer. No skills, no agents. |
| Multi-step, multi-domain, ambiguous match, high risk, or no obvious skill | Continue to Step 1. |

### Step 1 — Classify the task

Load [references/task-taxonomy.md](references/task-taxonomy.md). Output one
line before acting:

```
Route: <workflow class> | size: <trivial|standard|epic> | risk: <low|elevated|high> | domains: <n>
```

The class gives the default route shape; the modifiers bend it (bigger size
adds planning and delegation, higher risk adds verification, more domains add
domain-skill links).

### Step 2 — Inventory what is actually installed

Never route to a capability that is not installed. Discovery is
runtime-native:

- **Claude Code**: installed skills and their descriptions are listed in your
  context (Skill tool); installed agents are the available subagent types
  (Agent tool). Trust that list, not memory.
- **Codex**: skills auto-discovered from `~/.agents/skills/` and the repo's
  `.agents/skills/`; agents are `agent_<slug>` MCP tools when the
  `ak-codex-agent-runtime` MCP server is registered, or TOML files under
  `~/.codex/agents/` / project `.codex/agents/`.

Capability missing? Use the find-skills skill (`ak-find-skills`) to discover
and install it when present; otherwise do the work inline and name the gap in
your final report. Do not silently pretend the capability exists.

### Step 3 — Select and chain skills

Selection precedence:

1. Skill the user named.
2. Domain-specific skill over workflow-generic skill (a React feature routes
   to the frontend skill first, then executes through the workflow skills).
3. One primary skill per distinct intent; secondary skills are follow-up
   helpers, not co-owners.

Consult the owning routing references instead of guessing — and instead of
re-deriving what they already encode:

| Decision | Load |
|---|---|
| Which domain skill fits this intent | `../ak-find-skills/references/domain-routing.md` (engineer installs; if absent, match installed skill descriptions) |
| Which sequence fits multi-step dev work | `../ak-cook/references/workflow-routing.md` |
| Which visual/preview mode fits | `../ak-preview/references/visual-explanation-routing.md` |
| How to compose, pass context, and recover mid-chain | [references/chaining-patterns.md](references/chaining-patterns.md) |

Chain rules are in
[references/chaining-patterns.md](references/chaining-patterns.md): the
understand → decide → execute → verify → deliver skeleton, entry/exit criteria
per link, artifact passing through report files, and the collapse rule that
keeps chains short.

### Step 4 — Spawn subagents at trigger points

Subagents raise quality when they add a fresh context window, an enforced tool
boundary, parallel wall-clock, or a specialist system prompt — and lower it
when they fragment a task that needed full conversation context.

Load [references/subagent-timing.md](references/subagent-timing.md) for the
trigger table (stage × condition → role), the delegation contract every spawn
must carry, parallel-safety rules, and the per-runtime dispatch dialect
(Claude Code Agent tool vs Codex `agent_<slug>` MCP tools).

Fast triggers you should never miss:

- Investigation spanning more than two areas → parallel read-only explorer
  agents at the start, not after you are lost.
- Implementation finished → tester role before you claim done.
- Ship, publish, or public-contract change ahead → reviewer role first.
- Same failure twice → debugger role with the evidence so far.
- Consequential claim rests on assumptions, evidence conflicts, or a failure
  survives two reasonable attempts → Wukong at R0 before repair or acceptance.

### Step 5 — Quality gates by risk

Verification is part of done, not optional polish:

| Risk class | Mandatory before delivering |
|---|---|
| low (internal, reversible) | Executor skill's own checks |
| elevated (user-visible behavior, cross-module) | Test/verification link + self-review of the diff or draft |
| high (public contract, security, data, money, destructive, mass-audience send) | Verification link + independent reviewer role + explicit user confirmation before the irreversible step |

For high-risk work, use Wukong only when a load-bearing claim needs
falsification or a minimal counterexample. Wukong recommends a gate but never
replaces the domain tester, security owner, or independent reviewer.

Report outcome-first when the chain completes: what was delivered, which
links ran, which agents were used, what was verified, what gaps remain.

## Worked Routes

Every route below first applies the bounded outcome gate in
`../../rules/primary-workflow.md`. The examples describe only the routing that
follows; they are not alternate workflow authorities.

**"Fix the failing CI on this branch"** — after framing the repaired behavior
and safety boundary, Step 0 finds a single domain and obvious owner. Route to
`/ak:fix`. No chain, no agents unless `/ak:fix`
itself escalates. Total router overhead: one classification line.

**"Add team billing with Stripe and a settings page"** — class:
build-feature, size: epic, risk: high (money), domains: 3 (backend, payments,
frontend). After the opening outcome gate, chain: scout → plan → implement (payment + frontend domain skills
under the workflow skill) → test → review. Agents: explorer roles scout
payment and settings code in parallel; implementer roles take disjoint file
sets per plan phase; tester after implementation; reviewer before ship (high
risk makes it mandatory).

**"Launch a campaign for the new feature"** (marketing install) — class:
plan-campaign, size: standard, domains: 2+. Chain per the marketing sequences
in chaining-patterns: research → persona/funnel → campaign brief → per-channel
content production → analytics setup. Agents: researcher roles in parallel at
the start; content-creator/copywriter roles per channel; content-reviewer role
before anything publishes (mass-audience send = high risk).

## Anti-Patterns

| Do not | Because |
|---|---|
| Spawn a subagent for a two-minute single-file edit | Delegation overhead exceeds the work; quality drops with context loss |
| Build a five-link chain for a single-domain ask | Every link adds handoff loss; the collapse rule exists for this |
| Route to a skill or agent you have not confirmed installed | Broken dispatch mid-task; inventory is Step 2 for a reason |
| Re-route mid-chain without new evidence | Thrash; reroute once per link on evidence, else surface to the user |
| Copy routing tables from owning references into prompts or docs | They drift; load them at decision time instead |
| Use this skill for headless cross-CLI or multi-worktree runs | That is the orchestrate skill's layer |
| Skip the reviewer role on high-risk work because the diff "looks clean" | The gate exists precisely for confident mistakes |

## Failure Handling

A link that fails does not advance the chain. Detour (fix or debug the
blocker, or rescope the link), then resume at the failed link. Two consecutive
failures on the same link: stop, report what was attempted, what failed, and
the smallest missing input — do not loop.

Subagent status handling: `BLOCKED` or `NEEDS_CONTEXT` means change the
context, scope, or approach before re-delegating. Never resend the same
failing prompt.

## Handoffs

- Work should run headlessly, across CLIs, or in parallel worktrees → the
  orchestrate skill (`ak-orchestrate`, engineer installs).
- Work needs multiple coordinated sessions → the team skill (`ak-team`,
  engineer installs).
- Needed capability is not installed → the find-skills skill
  (`ak-find-skills`, engineer installs), else proceed inline and report the
  gap.
