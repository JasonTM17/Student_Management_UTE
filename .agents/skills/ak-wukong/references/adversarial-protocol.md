# Adversarial Protocol

## 1. Lock the mission

Validate the Mission Contract. Capture target identity, dirty state, relevant
runtime versions, and the precise claim. Define the observation that would
falsify it.

## 2. Build the invariant ledger

For every invariant record:

| ID | Must hold | Failure signal | Severity | Status |
| --- | --- | --- | --- | --- |
| INV-001 | bounded statement | observable signal | high | untested |

Do not infer an invariant from a test name. Read the assertion and owning
contract.

## 3. Calibrate claims

Label statements:

- OBSERVED: direct tool result from the current target identity.
- DERIVED: follows from cited observations and an explicit reasoning step.
- PRIOR: external knowledge or documentation not reproduced here.
- ASSUMED: necessary but unverified premise.

Only OBSERVED and sound DERIVED evidence may decide a verdict.

## 4. Choose transformations

Wukong's "72 transformations" are a selection discipline, not a requirement to
enumerate 72 cases. Choose the smallest relevant set from:

1. scale and boundaries;
2. time, expiry, and clock skew;
3. concurrency and interleaving;
4. state transition and retry;
5. trust, identity, and tenant;
6. environment, OS, path, and locale;
7. dependency version and absence;
8. network loss, duplication, and reordering;
9. compatibility and upgrade/downgrade;
10. observability and silent failure;
11. recovery, rollback, and partial completion;
12. authority, approval, and side effects.

Record why each selected perspective can discriminate between hypotheses.

## 5. Run a hypothesis tournament

Create at least:

- H0: the claim holds within stated scope;
- H1: the leading failure mechanism;
- H2: a materially different mechanism.

For each hypothesis record prior confidence, predicted observations, falsifier,
and affected invariants. Priors rank attention only; they never count as
evidence.

## 6. Rank probes

Prefer probes with high discrimination and impact, high reversibility, low
cost, and low risk. Use the deterministic ranking script when useful. Reject a
probe if it:

- exceeds R0;
- crosses excluded scope;
- requires a secret value;
- reaches production or an external side effect;
- mutates source or durable data;
- cannot preserve exact output and reproduction steps.

## 7. Execute bounded probes

Before each probe state:

- hypothesis split it tests;
- exact command or inspection;
- expected observations per hypothesis;
- timeout and stop condition;
- target identity and environment.

Capture exit code, relevant stdout/stderr, and timestamps. Redact; do not
silently discard failed commands.

## 8. Update, do not defend

Update hypothesis confidence from observations. A result compatible with all
hypotheses is low-information and must not decide the claim.

If two reasonable probes fail for tooling reasons, stop retrying the same
approach. Return INCONCLUSIVE with the missing capability or hand off to the
debugger.

## 9. Minimize the counterexample

Remove one condition at a time while the same mechanism still reproduces.
Minimize input, state, timing, dependency set, and command sequence. Preserve
the exact smallest reproducer and the invariant it violates.

Write the mechanism as a causal chain, never a symptom summary:

    precondition -> state/authority read -> missing or effective guard
    -> committed transition -> observed value -> invariant result

Use the target's exact field, state, guard, and operation names. For a
FALSIFIED claim, identify the first transition where the invariant becomes
unrecoverable and contrast it with the expected transition. For a
NOT_FALSIFIED claim, name the tested guard and explain how the observed
transition prevents the candidate failure; “the test passed” is not a
mechanism.

A smallest counterexample records the minimal initial state, actors/operations,
schedule or input, observed value, expected value, violated invariant, and the
conditions removed during minimization. If one of these is unknown, say so
instead of filling it with generic prose.

## 10. Confirm independently

High or critical findings require a second execution with one meaningful source
of independence: fresh process, clean fixture, alternate observation, different
test harness, or a separately delegated read-only agent. Repeating the same
command in the same contaminated state is not independent.

Record the primary and confirmation as distinct `OBSERVED` evidence records.
The confirmation must have its own probe/execution identity, use
`independence_kind` to name the independence source, and bind `independent_of`
to the primary evidence ID. Link both records from the counterexample and set
`confirmation_evidence_id` to the E3 confirmation. Never encode confirmation as
an unbound boolean or prose claim.

If independent confirmation cannot run, keep the finding but lower confidence
and use INCONCLUSIVE unless direct evidence already makes the invariant failure
unambiguous.

## 11. Recheck coverage and identity

Record invariants tested, untested, blocked, and out of scope. Re-read identity.
Never infer global safety from a bounded sample.

## 12. Verdict and handoff

Apply references/evidence-and-verdict.md mechanically. Hand off:

- mechanism and smallest counterexample to Fix;
- exact regression condition to Test;
- security-relevant evidence to Security;
- plan/architecture challenge to Kongming;
- acceptance packet to Review.

Wukong remains available for the exact retest but does not become the owner.

Before emitting the verdict, run a causal-completeness check:

- mechanism names the trigger, guard, transition, and invariant consequence;
- evidence quotes the decisive concrete observation rather than only its exit;
- counterexample is executable or explicitly bounded as non-reproduced;
- coverage separates what the probe observed from what remains assumed;
- the exact retest targets the causal transition, not an unrelated broad suite.
