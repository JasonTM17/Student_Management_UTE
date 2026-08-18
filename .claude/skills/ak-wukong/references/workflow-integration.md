# Workflow Integration

## Role boundaries

| Role | Owns | Does not own |
| --- | --- | --- |
| Advisor | user interview, reframing, requirements, goals, trade-offs | implementation or claim falsification |
| Kongming | one-shot strategy, architecture, conflict resolution, supervision | edits or empirical sign-off |
| Wukong | adversarial hypotheses, bounded probes, counterexamples, evidence/gate | repair, test ownership, security/release sign-off |
| Fix | diagnosis-to-repair implementation | independent acceptance |
| Test | test design/execution and regression coverage | product/architecture decision |
| Security | threat assessment and security verdict | generic release approval |
| Review | acceptance against contract and release/merge recommendation | producing the implementation |

## Trigger rules

Route to Wukong when at least one is true:

- a high/critical claim depends on assumptions rather than direct evidence;
- two reasonable attempts did not resolve the same failure;
- reviewers disagree about mechanism or adequacy;
- a plan crosses concurrency, migration, tenant, money, recovery, or production
  boundaries;
- portability across OS/runtime/clean checkout is claimed;
- release, deployment, or production-readiness language exceeds current proof;
- a minimal counterexample would materially improve the next decision.

Do not route routine formatting, simple lookups, ordinary implementation, or
already well-specified low-risk work to Wukong.

Debugger versus Wukong: when the expected behavior and mechanism are already
known and the task is to locate or repair an ordinary defect, Debugger owns the
diagnosis. Route to Wukong when the claim, invariant, mechanism, or portability
assumption is contested, high-risk, or has survived two reasonable fixes. The
two roles may exchange evidence, but Wukong remains report-only and does not
replace Debugger, Test, Security, or Review.

## Supervision pattern

For ambiguous high-risk work:

1. Advisor produces confirmed requirements and non-goals.
2. Kongming checks architecture and decides which claims are load-bearing.
3. Wukong attacks only those concrete claims.
4. The owning executor repairs or implements.
5. Test and independent Review validate the exact head/artifact.

Kongming supervises Wukong's framing, not each command. It reviews:

- whether the right claim and invariant were selected;
- whether the probe budget is proportionate;
- whether a Wukong gate improperly claims architecture authority;
- whether residual risk changes the recommended strategy.

## Handoff contracts

To Wukong provide: task, exact files/target, no file ownership, acceptance
criteria, R0 constraints, report path, and status-line requirement.

From Wukong require:

- target identity;
- machine verdict plus concise report;
- reproduction steps and redacted evidence;
- failed/blocked probes;
- residual risks;
- exact next owner and retest.

Never dispatch Wukong and an implementation writer to overlapping mutable state.
Read-only independent Wukong probes may run in parallel only against a frozen
identity.

## Failure handling

- Advisor unavailable: do not invent user decisions; proceed only if the goal
  contract is already explicit and record the missing advisory gate.
- Kongming unavailable: implementation may stay reversible, but high-risk
  architectural acceptance remains unproved.
- Wukong unavailable: use /ak:fable-thinking Full plus an independent reviewer
  and disclose the degraded route.
- Runtime/model unavailable: re-run live inventory; select a verified C3 route
  or block. Never silently claim that a named agent ran.
