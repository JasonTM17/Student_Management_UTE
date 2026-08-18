# Plan Template Usage Guide

These templates are AgentKit-native. Use `/ak:plan` to fill them and
`/ak:plans-kanban` for portfolio/status views. Use `/ak:issue-to-plan` when the
deliverable must stop after planning.

## Template Selection

### Feature Implementation Template
**Use when**: Adding new functionality, endpoints, services, or modules
**File**: `feature-implementation-template.md`
**Size**: Medium to large scope changes

### Bug Fix Template
**Use when**: Fixing specific issues, errors, or broken functionality
**File**: `bug-fix-template.md`
**Size**: Small to medium scope changes

### Refactoring Template
**Use when**: Improving code structure, performance, or maintainability without changing functionality
**File**: `refactor-template.md`
**Size**: Medium to large scope changes

## Context Management Best Practices

### Keep Plans Focused
- **Executive Summary**: Max 3 sentences
- **Context Links**: Reference files, don't include full content
- **Tasks**: Max 10 per phase
- **Context Tokens**: Target <200 words for summaries

### Template Adaptation
1. Create a canonical plan directory such as
   `plans/20260813-1430-feature-name/` (let `/ak:plan` choose the timestamp when
   possible).
2. Copy the appropriate template to that directory as `plan.md`; add
   at least one `phase-NN-*.md` from `phase-template.md`. AgentKit validation
   requires a phase even for a small one-phase plan.
3. Keep the YAML frontmatter at the top of `plan.md`; replace its placeholders
   with the actual title, branch, effort, tags, dependencies, and creation date.
4. Replace bracketed placeholders in the body with actual content.
5. Remove sections not relevant to your specific use case.
6. Keep the core structure intact for consistency.
7. Record the selected `/ak:*` workflow and named review owners.
8. If `.agentkit/scripts/set-active-plan.cjs` exists, select the new plan
   directory (or its `plan.md`) with it.

### Cross-References Instead of Duplication
- Link to existing documentation in `./docs/`
- Reference other plans without copying content
- Use file paths instead of code blocks where possible
- Focus on "what" and "why", not detailed "how"

## Quality Checklist

Before finalizing any plan:
- [ ] Executive summary is clear and concise
- [ ] Tasks are specific and actionable
- [ ] File paths are included for implementation tasks
- [ ] Success criteria are measurable
- [ ] Context links are used instead of full content
- [ ] TODO checklist is complete and realistic
- [ ] At least one `phase-NN-*.md` exists and follows the phase template
- [ ] Outcome, non-goals, authority, stop conditions, and rollback are explicit
- [ ] Every acceptance criterion names authoritative evidence and an owner
- [ ] Live/CI/cross-platform requirements are separated from local deterministic tests

## Context Refresh Triggers

Use these templates when:
- Starting a new development phase
- Switching between different types of work (feature → bugfix)
- After major context accumulation or a material scope/identity change
- When agent handoffs occur

This ensures each plan starts with fresh, focused context optimized for the specific task type.

## Agent review gates

- **Advisor**: outcome, trade-offs, decision criteria, and non-goals.
- **Kongming**: architecture, sequencing, ownership, failure containment, and
  exact-snapshot release gate.
- **Wukong**: falsifiable high-risk claims and minimized counterexamples; never
  implementation acceptance by itself.

Do not mark a plan complete from a worker report alone. Reinspect the current
artifact identity and run the exact required gates.
