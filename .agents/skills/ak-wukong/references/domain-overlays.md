# Domain Overlays

Load only the overlays named by mission risk.domains. These are probe prompts,
not automatic proof obligations.

## Distributed systems and concurrency

- Duplicate, reordered, delayed, lost, and retried messages.
- Simultaneous writers, stale reads, leader change, lease expiry, split brain.
- Idempotency key scope, replay windows, monotonicity, fencing, partial commit.
- Recovery after termination between each pair of state transitions.

## Data, schema, and migrations

- Forward and backward compatibility during mixed-version rollout.
- Ownership, privileges, search path, enum/function signatures, and RLS.
- Null, default, backfill, retry, partial batch, rollback, and rerun behavior.
- Ambiguous identifiers, uniqueness scope, locking, and concurrent writes.

## Security, auth, and tenant isolation

- Authentication versus authorization; user, service, and admin boundaries.
- Tenant/object ownership at every read, write, cache, event, and export.
- Token expiry/revocation, confused deputy, replay, traversal, and injection.
- Fail-open behavior when policy, identity, or dependency data is absent.

Hand findings to the Security owner; Wukong does not issue security sign-off.

## Billing, quota, and usage

- Admission race, retry compensation, duplicate delivery, and atomicity.
- Boundary values, overage, zero limits, currency/precision, clock windows.
- Ledger reconciliation, tombstones, refunds, cancellation, and partial failure.
- UI/reporting truth versus authoritative accounting state.

## AI and agent runtimes

- Prompt injection across fetched content, tool output, plans, and memory.
- Tool authority versus prose-only guardrails; sandbox and approval drift.
- Model/agent availability, fallback honesty, context loss, and stale state.
- Structured-output failure, non-determinism, quota exhaustion, and resume.

## CI, release, and portability

- Clean checkout, untracked dependency, generated artifact, case sensitivity.
- Windows drive paths, separators, quoting, encoding, line endings, and spaces.
- Missing runtime, version skew, offline behavior, and fresh user home.
- Manifest/hash parity, adapter discovery, duplicate registries, and stale cache.

## Frontend and client state

- Loading, empty, error, offline, stale, retry, optimistic, and partial states.
- Double submit, cross-tab race, navigation teardown, cache invalidation.
- Keyboard, screen-reader, 375px/desktop, reduced motion, and localization.
- Fixture/demo claims versus live-service behavior.

## Observability, performance, and recovery

- Silent failure, metric cardinality, clock gaps, sampling, and alert ownership.
- Cold start, p95/p99, saturation, queue growth, and resource exhaustion.
- Rollback prerequisites, restore verification, RPO/RTO, and corrupted backup.
- Health/readiness truth versus dependency reachability and useful service.
