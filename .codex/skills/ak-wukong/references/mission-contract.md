# Mission Contract

Build this contract before any probe. A mission is executable only when its
identity, scope, invariants, authority, and budget are explicit.

## Required fields

| Field | Rule |
| --- | --- |
| protocol_version | Exact value 1.0 |
| mission_id | Stable lowercase identifier, 6 to 96 characters |
| mode | challenge, rescue, counterexample, portability, shadow-review, experiment, or chaos |
| target.kind | repo, file, claim, plan, runtime, API, database, or workflow |
| target.locator | Human-readable repo-relative locator; never a secret-bearing URL |
| target.claim | One falsifiable sentence; avoid compound success claims |
| target.identity | Identity kind, value, and UTC capture time |
| scope.include | Non-empty repo-relative globs or named surfaces |
| scope.exclude | Explicit protected/irrelevant surfaces |
| scope.non_goals | Adjacent outcomes Wukong must not absorb |
| invariants | At least one ID, statement, failure signal, and severity |
| risk | Severity plus named risk domains |
| authority | R0 and report-only for the Wukong agent |
| budget | Depth, max probes, and timeout |
| artifact_dir | Relative path beginning plans/reports/ with no parent traversal |
| handoff | Controller plus strategy owner |

## Identity rules

Prefer a Git commit SHA plus dirty-state digest for repository work. For runtime
claims, record the executable, resolved version, OS, architecture, and relevant
adapter version. For a plan or standalone document, record a cryptographic hash.

Re-read identity before verdict. If it changed:

- stop probing;
- preserve already observed evidence with its old identity;
- return INCONCLUSIVE;
- recommend a fresh mission against the new identity.

Never use an absolute local path as durable identity. Store project-relative
locators and a portable identity value instead.

## Claim and invariant quality

A useful claim can fail. Rewrite vague claims such as "ready", "robust", or
"portable" into bounded statements:

- Bad: the package is portable.
- Better: a clean Windows checkout with Node on PATH can invoke every registered
  skill without repository-specific absolute paths.

Each invariant needs:

- a stable ID such as INV-001;
- one must-hold statement;
- an observable failure signal;
- severity: low, medium, high, or critical.

## Authority

Wukong itself accepts only:

- level R0;
- write_mode report-only;
- external_effects false.

Running an existing non-mutating test is R0. Creating or changing product
fixtures, worktrees, services, data, network state, or configuration is outside
the Wukong agent's authority. Return a probe design to the controller instead.

Experiment mode does not grant authority. Chaos mode is design-only inside the
agent. The controller must separately establish disposable isolation, rollback,
credentials boundaries, and explicit user approval before execution.

## Budget

Max probes must agree with depth:

- quick: 1;
- standard: 1 to 3;
- deep: 1 to 7.

A budget limits effort, not truth. When it expires, return INCONCLUSIVE and
state the next highest-information probe; never lower evidence requirements.
