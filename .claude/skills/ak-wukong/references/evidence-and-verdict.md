# Evidence and Verdict

## Evidence ladder

| Grade | Meaning | Examples |
| --- | --- | --- |
| E0 | Assertion only | intuition, unverified report |
| E1 | Static or indirect artifact | code path, config, schema, documentation |
| E2 | Deterministic direct observation | reproducible test, parser result, exact command |
| E3 | Independent corroboration | fresh fixture/process or materially different observation |
| E4 | Production-like or live observation within explicit authority | controlled canary or live telemetry with provenance |

Higher grades improve observation strength only for the stated target, time, and
coverage. E4 is not universal proof. A mix inherits the grade of its decisive
evidence, not the highest decorative artifact.

Each evidence record contains:

- id;
- kind: OBSERVED, DERIVED, PRIOR, or ASSUMED;
- grade;
- source and portable target identity;
- observation;
- SHA-256 of the exact observation text;
- probe ID, execution ID, and affected invariant IDs;
- supports and contradicts hypothesis IDs;
- independence kind plus primary evidence IDs when this is a confirmation;
- reproduction command or inspection;
- timestamp;
- redactions applied.

Evidence IDs must be unique. A decisive record must be named explicitly in
`decisive_evidence_ids`, map to a tested invariant, and discriminate at least
one hypothesis. Decorative higher-grade evidence cannot raise the verdict.
`OBSERVED` means the reproduction actually ran; an `ASSUMED` or `DERIVED`
record cannot masquerade as a direct probe result.

## Claim statuses

### FALSIFIED

Use only when a current-target observation violates a declared invariant.
Require:

- at least E2 decisive evidence;
- non-empty mechanism;
- a minimized counterexample;
- affected invariant;
- primary evidence linked from the counterexample;
- for high/critical severity, a distinct E3 `OBSERVED` confirmation record
  whose execution identity differs and whose `independent_of` points to the
  primary observation.

Gate: BLOCK or REPAIR_THEN_RETEST.

### NOT_FALSIFIED

Use when no tested invariant failed inside the completed bounded probes. Require:

- at least one E2 observation;
- exact tested and untested invariants;
- probe/environment coverage;
- residual risks and assumptions.

Gate: PROCEED_WITH_RESIDUAL_RISK. Never say proven, fully safe, guaranteed, or
production-ready.

### INCONCLUSIVE

Use when evidence conflicts, identity drifts, the budget expires, tooling is
unavailable, independent confirmation is missing, or the decisive probe exceeds
authority.

Gate: BLOCK or REPAIR_THEN_RETEST. Include the single best next probe and its
owner.

### UNDERDEFINED

Use when the claim, failure signal, scope, identity, invariant, or authority is
missing.

Gate: BLOCK. Hand back to Advisor for intent/requirements or Kongming for
architecture boundaries.

## Confidence

Use low, medium, or high. Confidence must be no stronger than:

- evidence grade;
- target identity freshness;
- probe discrimination;
- counterexample minimization;
- independence;
- coverage.

## Mechanism quality

Use one bounded causal statement with this information order:

1. triggering precondition or input;
2. exact state/authority read;
3. missing or effective guard;
4. state transition or committed effect;
5. concrete observed-versus-expected value;
6. affected invariant.

Name real fields, actors, guards, and transitions from the evidence. Avoid
“race condition”, “validation issue”, “unsafe”, or “test passed” as standalone
mechanisms. A negative control still needs the concrete guard and transition
that prevented the tested failure.

## Machine verdict

The verdict JSON must contain:

- protocol_version and mission_id;
- target_identity equal to the mission identity;
- claim_status and recommended_gate;
- severity, confidence, evidence_grade;
- mechanism;
- H0 plus at least two competing hypotheses, their predictions, and falsifiers;
- evidence and tested_invariants;
- decisive_evidence_ids;
- counterexamples;
- probe_summary;
- coverage_limits and residual_risks;
- missing_fields for UNDERDEFINED;
- handoff owner, reason, next_action, and exact_retest.

Validate it with scripts/wukong-contract.cjs. The Markdown report may explain
the reasoning but cannot contradict the machine verdict.
