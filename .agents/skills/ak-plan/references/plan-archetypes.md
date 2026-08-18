# Plan Archetypes

Use this reference after repository scouting and before phase design. It adapts
ClaudeKit's feature, bug-fix, and refactor plan templates into overlays for the
AgentKit files-first plan model. It does not replace the canonical frontmatter,
directory layout, phase schema, or live `ak plan` operations.

## Selection Contract

Choose one primary archetype from the requested outcome:

| Archetype | Select when | Required proof before implementation |
| --- | --- | --- |
| Feature | The outcome adds a capability or changes user-visible behavior. | Requirements, affected contracts, architecture/data flow, acceptance evidence. |
| Bug fix | Existing behavior violates a reproducible expectation. | Reproduction or equivalent evidence, root cause, regression boundary. |
| Refactor | Behavior should remain stable while internals change. | Preserved-behavior contract, baseline evidence, characterization coverage. |
| Generic | Research, migration, documentation, operations, or mixed work does not fit one archetype. | Scope-specific evidence and measurable acceptance criteria. |

When a task crosses types, choose the type that owns the user-visible outcome
and mark secondary concerns inside the affected phases. Do not create duplicate
plans solely because a feature contains a small refactor or a bug fix needs a
small migration.

## Common Invariants

Every archetype must:

- preserve the canonical `plan.md` and `phase-XX-*.md` schemas;
- link to source files, tests, issues, decisions, and reports instead of copying
  large context blocks;
- distinguish verified facts, assumptions, and unresolved decisions;
- list concrete files or bounded discovery steps instead of “update all files”;
- define observable success criteria and the command or evidence that proves
  each criterion;
- include security, compatibility, rollout, and rollback only where the scope
  makes them real;
- avoid invented measurements, coverage percentages, dates, estimates, API
  behavior, or infrastructure state;
- keep `plan.md` concise and put execution detail in phase files.

## Feature Overlay

Use for new functionality, endpoints, services, integrations, or modules.

### Plan index requirements

In `plan.md`, make the overview answer:

1. What user or maintainer outcome becomes possible?
2. What is explicitly in scope and out of scope?
3. Which public contracts, data boundaries, or workflows may change?
4. Which phases deliver an independently verifiable increment?

Include links to requirements, architecture decisions, relevant existing plans,
and the phase files. Do not copy detailed implementation steps into the index.

### Phase requirements

Across the feature phases, cover the applicable items:

- **Functional requirements:** behavior, actors, inputs, outputs, permissions,
  and failure states.
- **Non-functional requirements:** performance, reliability, accessibility,
  privacy, operability, and compatibility targets that are actually required.
- **Architecture and data flow:** component ownership, trust boundaries, state
  transitions, persistence, external services, and error propagation.
- **Contract changes:** API/schema/event/CLI/config changes plus versioning or
  migration needs.
- **Delivery sequence:** foundations before consumers; schema or contract work
  before dependent application work; observability before production claims.
- **Verification:** unit, integration, end-to-end, security, accessibility, or
  operational checks proportional to the changed contracts.
- **Rollout and rollback:** flags, compatibility windows, data rollback limits,
  and recovery evidence when deployment is in scope.

### Recommended feature phase shape

Use only the phases the work needs:

1. Contract and foundation.
2. Core implementation.
3. Consumer or UI integration.
4. Verification and operational readiness.
5. Documentation, migration, or rollout.

Avoid phases named only “backend”, “frontend”, or “testing” when they do not
produce an independently testable outcome. Name phases after delivered behavior.

### Feature acceptance gate

A feature plan is ready when every requirement maps to at least one phase and
one observable acceptance check; dependency order is explicit; failure modes
and permission boundaries are addressed; and no phase relies on an unnamed
contract or unverified component.

## Bug-Fix Overlay

Use for defects, errors, regressions, test failures, or broken CI/CD behavior.

### Plan index requirements

Record concisely:

- expected behavior;
- actual behavior and impact;
- reproduction or strongest available evidence;
- verified root cause, or a clear statement that diagnosis remains a phase;
- affected components and regression boundary;
- proposed repair outcome, not an assumed implementation.

Logs should be referenced by safe path or summarized error signature. Never
paste secrets, customer data, full private logs, cookies, or environment values
into a plan.

### Root-cause gate

Do not plan a speculative code change as the fix. Before implementation, the
plan must either:

1. cite evidence that connects the symptom to the root cause; or
2. begin with a bounded diagnosis phase whose success criterion is producing
   that causal evidence and selecting a cause-aligned repair.

If evidence disproves the reported cause, update the plan rather than preserving
the original theory.

### Phase requirements

Cover the applicable items:

- deterministic reproduction, failing test, trace, or state inspection;
- causal path from trigger through failure to observed symptom;
- smallest safe repair and the contracts intentionally left unchanged;
- regression tests for the original failure and nearby edge cases;
- focused verification first, then broader gates when shared behavior changed;
- rollback or revert path, including data repair when state was corrupted;
- deployment or CI validation when the defect exists only in those surfaces.

### Recommended bug-fix phase shape

1. Reproduce and prove cause.
2. Implement bounded repair and regression coverage.
3. Verify affected contracts and operational surface.

Combine phases for a genuinely small, already-diagnosed fix. Do not add ceremony
that exceeds the risk of the defect.

### Bug-fix acceptance gate

A bug-fix plan is ready when the expected behavior is testable, the cause is
evidenced or diagnosis is explicitly gated, the proposed repair addresses that
cause, the original failure becomes a regression test where practical, and the
rollback path does not claim reversibility that the data model cannot provide.

## Refactor Overlay

Use when structure, maintainability, dependency boundaries, or performance
changes while externally observable behavior should remain stable.

### Plan index requirements

State:

- the protected behavior and public contracts;
- the concrete current-state problem;
- the intended structural or measurable improvement;
- affected modules and dependency boundaries;
- compatibility and migration constraints;
- what is explicitly not being redesigned.

Do not describe a behavior-changing rewrite as a refactor. Split or reclassify
the work when product behavior, schemas, permissions, or public APIs change.

### Baseline and characterization gate

Before structural changes, collect the applicable baseline:

- current tests and uncovered critical behavior;
- performance measurements using a repeatable command or fixture;
- dependency graph, call sites, instantiation sites, and state lifetime;
- public types, schemas, API responses, events, configuration, and CLI behavior.

When coverage is insufficient, plan characterization tests before modifying the
protected code. Metrics must come from an executable measurement; use
“baseline to be measured” instead of inventing a number.

### Phase requirements

Cover the applicable items:

- tests-before protection for existing behavior;
- small structural steps with explicit file ownership;
- compatibility adapters or migration windows when consumers cannot move at
  once;
- caller-by-caller integration rather than “update all callers”;
- tests-after for newly exposed behavior or intentional performance targets;
- before/after measurements using the same method and environment;
- cleanup only after all consumers and rollback needs are resolved.

### Recommended refactor phase shape

1. Baseline and characterize.
2. Introduce the new boundary behind compatible behavior.
3. Migrate consumers in bounded groups.
4. Verify parity or measured improvement.
5. Remove obsolete paths after the rollback window closes.

### Refactor acceptance gate

A refactor plan is ready when preserved behavior is explicit, characterization
evidence exists or is the first gated phase, every consumer migration is
accounted for, compatibility and rollback are realistic, and success is measured
with the same baseline method used before the change.

## Generic Overlay

Use the canonical AgentKit phase template without forcing feature, bug, or
refactor sections. Add only domain-relevant contracts, such as migration
verification, documentation claims, incident evidence, security threat model,
or operational rollback.

## Final Archetype Review

Before finishing the plan, verify:

- [ ] Exactly one primary archetype is named in the plan or planning report.
- [ ] Required archetype evidence is linked or scheduled behind a hard gate.
- [ ] Optional sections with no relevance were removed.
- [ ] No placeholder, fabricated metric, or stale path remains.
- [ ] Each phase has files or bounded discovery, dependencies, success criteria,
      validation, risk, and rollback where applicable.
- [ ] `plan.md` remains an index; detailed execution stays in phase files.
- [ ] The live `ak plan` schema and repository instructions still take
      precedence over this reference.

## Provenance

The feature, bug-fix, and refactor distinctions were adapted from the plan
templates in `JasonTM17/ClaudeKit_Master` at source commit
`23be100cefd972a27a3cb8451f03cbd4e15f1138`. This AgentKit version was rewritten
as schema-preserving overlays so it remains compatible with CLI-managed plan
state, phase files, runtime task hydration, and portable adapters.
