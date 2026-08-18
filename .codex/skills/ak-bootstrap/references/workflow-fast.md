# Fast Workflow (`--fast`)

**Thinking level:** Think hard
**User gates:** Fast pre-planning path, then normal cook review gates.

The opening brainstorm contract in the parent skill is already satisfied; fast
mode reduces research overhead, not intent quality.

## Step 1: Combined Research & Planning

All research happens in parallel, then feeds into planning:

**Parallel research batch** (spawn these simultaneously):
- 2 `researcher` subagents (max 5 sources each): explore request, validate idea, find solutions
- 2 `researcher` subagents (max 5 sources each): find best-fit tech stack
- 2 `researcher` subagents (max 5 sources each): research design style, trends, fonts, colors, spacing, positions
  - Predict Google Fonts name (NOT just Inter/Poppins)
  - Describe assets for `ak:ai-multimodal` generation

Keep all reports ≤150 lines.

## Step 2: Design

1. `ui-ux-designer` subagent analyzes research, creates:
   - Design guidelines at the documentation path discovered from repository instructions
   - Wireframes in HTML at `./docs/wireframe/`
2. If no logo provided: generate with `ak:ai-multimodal` skill
3. Screenshot wireframes with `ak:agent-browser` -> save to `./docs/wireframes/`

**Image tools:** `ak:ai-multimodal` for generation/analysis, `imagemagick` for crop/resize, background removal tool as needed.

No design gate in fast mode — proceed directly to planning.

## Step 3: Planning

Activate **ak:plan** skill: `/ak:plan --fast <requirements>`
- Skip research (already done above)
- Read codebase docs → create plan directly
- Plan directory using `## Naming` pattern
- Overview at `plan.md` (<80 lines) + `phase-XX-*.md` files

No pre-implementation gate here — hand off to cook, which keeps review gates unless the user separately asked for `--auto`.

## Step 4: Implementation → Final Report

Load `references/shared-phases.md` for remaining phases.

Activate **ak:cook** skill: `/ak:cook <plan-path>`
- Skips redundant research because planning already happened
- Keeps cook review gates; add `--auto` only when the user explicitly asked for autonomous bootstrap
- Continues according to normal cook mode

**Note:** Fast mode optimizes setup speed, not approval bypass. Use `git-manager` only after normal cook completion.
