# Evaluation and qualification

Use this reference only when Wukong itself is being measured or when a caller
asks whether it is production-qualified.

## Trust boundary

Keep four authorities separate:

- The controller freezes and materializes one public case.
- Wukong inspects the raw case and emits a structured verdict at R0.
- Test owns the executable probe and private deterministic oracle.
- Review consumes the exact score/attestation and owns acceptance language.

Advisor owns outcome tolerances and non-goals. Kongming owns corpus architecture,
hidden split, release thresholds, and model-route policy. Wukong must not author,
read, infer from filenames, or modify the oracle; rerunning itself is not an
independent confirmation.

## Case construction

Use paired cases: a failing subject and a corrected or hard-negative control.
Freeze the public case identity and keep provenance/licensing outside the
decision signal. Split held-out cases by source repository, not random files.
Neutralize issue titles and solution-shaped filenames. For public historical
bugs, materialize only a minimized reproduction and keep the fix diff, fixed
commit contents, label, mechanism markers, and full oracle outside the model
workspace.

The qualification controller must reject duplicate case IDs, semantically
duplicated cases under renamed IDs, development-repository overlap, and a
hidden corpus or oracle supplied alone. Require at least four distinct held-out
repositories so raw case count cannot stand in for source diversity.

Bind each corpus/oracle pair by protocol version, corpus ID, exact
bidirectional case-ID set, and a reviewed SHA-256 commitment over the sorted
oracle records. Reject extra, missing, duplicate, or content-drifted oracle
records before scoring.

The model workspace may contain only one controller-projected case, allowlisted
safe probe, Wukong skill/profile, and output schema. Rebuild that case from
fixed per-domain templates plus a strict schema-specific projection; remove
provenance, replace source case/invariant IDs, and pseudonymize every
source-controlled identifier while preserving equality and the independently
checked invariant outcome. Never materialize free-form source claim, invariant,
or subject prose. Restore source identity only inside the controller after
response validation. Encode prompt-injection challenges as a separate
controller-bound protocol instead of contaminating the blind case. This
architectural boundary replaces lexical filtering; independent curation plus a
trusted signed source and challenge manifest is still required for production.

Keep protocol regression and discovery separate. In protocol regression the
controller may execute the frozen probe and ask Wukong to classify its projected
observation; that measures report discipline and classification only. In local
development discovery, do not supply the decisive observation or preselect a
probe. Give Wukong a controller-owned subject plus opaque allowlisted probe
catalog, freeze its selected probe and predicted observation without granting
shell authority, then let the controller independently execute and replay the
observation, minimized counterexample,
repairing counterfactual, and irrelevant control. The checked-in generated
discovery families are synthetic same-host development evidence, never a signed
source holdout or production qualification.
Reject
symlinks, and compare its content manifest
before/after. Mutation,
oracle leak, identity drift, secret exposure, or arbitrary model-selected shell
or network access invalidates the trial.

## Classification

Treat `FALSIFIED` as a positive bug decision and `NOT_FALSIFIED` as a negative
decision. `INCONCLUSIVE` and `UNDERDEFINED` are abstentions, but a positive case
that is abstained or invalid still counts as a miss in effective recall. This
prevents Wukong from improving its score by refusing difficult cases.

Report TP, TN, FP, FN, invalid, abstain, precision, effective recall,
specificity, FPR, FNR, F1, balanced accuracy, counterexample presence for
protocol regression, diagnostic mechanism-marker coverage, and a bounded
protocol-regression score. The bounded score covers correct verdict and report
protocol shape, not lexical mechanism markers. Marker coverage is a diagnostic
proxy: it can be gamed by keyword bags or negation, never gates the development
score, and is never production causal evidence. For discovery qualification,
report independently replayed
counterexample reproduction and controller-executed causal counterfactual
accuracy separately. Never generalize any metric beyond the exact
corpus/model/profile/CLI/protocol digests.

## Codex route

The preferred route requests `gpt-5.6-sol` plus reasoning `max`; fallback is a
hard block. Record Codex version, authentication presence without reading the
credential, requested route, model-catalog digest, skill/profile/public-case/
model-case/prompt and response digests, duration, process exit, mutation
sentinel, and any runtime resolved-route receipt.

Provider inference itself requires network transport. Deny model-selected
network research/tools by policy, but do not describe that as an OS-attested
network block unless an external sandbox actually supplies and records one.
Codex read-only mode is also not a host-read isolation receipt. Until an
external container/VM exposes only the model workspace and signs that fact,
keep filesystem-read isolation unverified and production qualification false.

Requested CLI arguments and a catalog entry prove only that dispatch was
requested and locally supported. An allowlisted JSON event from the local CLI
is still only a route claim because a shadowed or modified executable can emit
it. Preferred-lane qualification requires an independently signed runtime
attestation binding the exact executable/run, resolved model, and reasoning
effort. If that receipt is absent or mismatched, retain the trial as
development evidence only and mark production qualification blocked.

When a machine-wide Codex install cannot parse the approved reasoning effort,
`WUKONG_CODEX_PACKAGE_ROOT` may select a separately installed npm package. The
root must be absolute and link-free; validate `@openai/codex` wrapper metadata,
the matching OS/architecture package and native executable, and bind every
runtime/helper/package role and digest before and after dispatch. Rescan the
runtime tree so additions, removals, links, and content drift are detected. An
invalid override must block instead of falling back. These checks establish stable
runtime identity, not publisher authenticity: preserve
`digest-recorded-not-authenticated` until a trusted external evaluator signs
the exact runtime set together with the provider route receipt.

For npm-selected Codex, the runtime set covers the recursively enumerated,
link-free wrapper package, imported helpers, matching native-platform tree, and
host Node executable. Hash provider stdout/stderr as bounded raw bytes before
decoding; strict UTF-8, no truncation, and stable before/after runtime-set
digests are mandatory for accepted trial evidence.

Reject PATH-only Codex selection for the qualification lane. A script shim
omits its Node/package/native dependencies, while an arbitrary native path does
not establish a reviewed environment closure. Require the explicit or validated
global npm package selector and fail closed instead of hashing one launcher.

Treat local authentication-file presence only as
`present-not-network-validated`. During an authenticated batch, classify a
revoked session, unauthorized response, quota exhaustion, rate limit, or
provider outage into a bounded non-secret code and stop immediately on fatal
provider state. Never persist raw authentication/provider errors as evidence.

For the public protocol-regression lane, let the controller execute only the
fixed allowlisted deterministic probe, verify a mutation-free workspace, and
bind the raw observation to case and probe digests. Embed that non-verdict
observation and an allowlisted, hash-bound Wukong protocol bundle in a
session-delimited prompt. The model must not receive an oracle boolean, answer
key, score policy, provenance, shell, file, network, or search capability. This
measures reasoning over controlled E2 evidence; it does not replace the hidden
production discovery lane where probe selection and counterexample discovery
must remain model work.

The controller binding must cover the canonical model case, exact probe command,
case/probe/raw-stdout/parsed-observation digests, and the exact protocol path and
content-digest set. Never reconstruct this binding from a model-visible file.
A changed nested observation, forged verdict field, substituted protocol body,
or extra protocol record blocks dispatch.

Authenticated Claude Code and Cursor testing is currently a non-goal. Preserve
their portable adapters without attributing Codex evidence to them.

## Acceptance levels

Protocol-regression gate: at least one positive and one negative per covered domain,
effective recall at least 80%, FPR at most 10%, abstention at most 20%, no
critical miss, no invalid response, and counterexample-presence rate at least 80%.

Production gate: a separately identified hidden-discovery split with at least 140 balanced cases, at least ten positive and
ten negative cases in each of seven domains, three fresh sessions per case, effective recall
at least 90%, per-domain recall at least 80%, FPR at most 5%, per-domain FPR at
most 10%, abstention at most 10%, independently replayed counterexample rate at least 90%,
controller-executed causal validation at least 90%, zero critical causal or classification
miss/invalid/mutation/secret event, exact route receipts, and a signed fresh
attestation from a policy-trusted isolated evaluator. Hidden discovery must not
give Wukong the decisive boolean, answer key, scorer thresholds, or a preselected
probe that directly encodes the verdict. A configured CI matrix is
not evidence that macOS/Linux ran; a
deterministic pass is not an authenticated provider result.

Qualification attestation protocol v2 is mandatory. It rejects legacy v1
envelopes and any signed summary with incomplete execution, a fatal/non-empty
provider-failure ledger, a trial-level provider failure, or runtime mutation.
The local controller-probed Codex runner emits `wukong.codex-run.v1`; it cannot
emit `wukong.discovery-run.v1`. Only an independent evaluator that creates and
executes the approved repairing-counterfactual plus irrelevant-control
challenge protocol may produce the discovery summary. Its records must bind the
exact scored case set, every fresh trial/session receipt, challenge/outcome
digests, unique source identities, and controller replay results. The verifier
derives confusion counts, critical classification misses, counterexample and
causal rates, plus the hard-bug score from that ledger; it must never trust an
asserted metric or reusable identity. Bind hashed source-repository identities,
recompute their distinct count, and prove the hidden/reference repository sets
are disjoint through the signed holdout-manifest digest.

External qualification also requires a policy-pinned exact runtime-set digest
for the target OS/architecture. The set must contain the Node host, Codex npm
wrapper package and launcher, one matching platform package, and one native
Codex executable. Keep the checked-in allowlist empty until a release owner has
reviewed the exact runtime; PATH shims and arbitrary one-file evidence fail
closed.

The external hard-bug score is deterministic: classification 40,
repairing-counterfactual 20, irrelevant-control 15, evidence binding 15, and
counterexample/negative-control correctness 10. Keep the independent causal,
reproduction, recall/FPR, and zero-critical-miss gates; do not let the weighted
score compensate for a failed mandatory gate.

For longitudinal or cross-model Codex tracking, compare only summaries with the
same corpus digest, case count, and canonical scored-case-set digest. Record requested route, observed-route
receipt state, TP/TN/FP/FN, abstention, invalid, counterexample reproduction or
presence (as appropriate to the lane), diagnostic mechanism-marker coverage,
and protocol-regression score. Do not relabel those local proxy metrics as
causal-mechanism validation or production hard-bug evidence.
Hash run identifiers in shared output. A comparison is development evidence;
recompute metrics from its per-case ledger and do not authenticate route or
attestation claims copied from a summary. It cannot establish provider route,
filesystem isolation, source holdout, or production qualification.

Bind signed qualification summaries to the canonical JSON digest of the exact
reviewed production-policy object. The producer and verifier must call the same
digest implementation; do not mix a raw policy-file hash with an object hash.
