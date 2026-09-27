/**
 * Pure policy for the assistant send-lock and the JSON reconciliation budget.
 * `useAssistantStream` consumes these helpers so the orphan-lock self-heal and
 * the deadline math stay deterministic and unit-testable outside React.
 */

/** Minimal shape of a chat message the orphan predicate needs. */
export interface AssistantPendingLikeMessage {
  pending?: boolean;
}

/**
 * Orphaned send-lock predicate. The lock is held while the visible
 * conversation carries NO pending assistant message, which means the previous
 * turn's cleanup never ran (for example a cleanup skipped by a stale
 * generation) and the panel would silently swallow every new send. A genuinely
 * in-flight turn always keeps a pending message — `assistant-start` /
 * `retry-start` are dispatched synchronously with the lock — so it never
 * qualifies as orphaned.
 */
export function hasOrphanedSendLock(
  messages: ReadonlyArray<AssistantPendingLikeMessage>,
): boolean {
  return !messages.some((message) => message.pending === true);
}

/** Attempt cap preserved from the previous behavior (up to 4 JSON replays). */
export const RECONCILE_MAX_ATTEMPTS = 4;

/**
 * Overall wall-clock budget for the whole reconciliation retry chain. Without
 * it, four attempts at ~12s each could hold the send lock for close to a
 * minute and look like a dead panel.
 */
export const RECONCILE_TOTAL_BUDGET_MS = 30_000;

/** Linear backoff between TURN_IN_PROGRESS attempts (250ms, 500ms, 750ms). */
export const RECONCILE_BACKOFF_BASE_MS = 250;

/** Bounded cancel so a hung cancel call cannot wedge the panel. */
export const CANCEL_REQUEST_TIMEOUT_MS = 8_000;

export interface ReconciliationBudget {
  /** Absolute wall-clock deadline (`Date.now()` based). */
  deadline: number;
  /** Attempts started so far, including the one currently in flight. */
  attemptsStarted: number;
}

export function startReconciliationBudget(
  now: number,
  totalBudgetMs: number = RECONCILE_TOTAL_BUDGET_MS,
): ReconciliationBudget {
  return { deadline: now + totalBudgetMs, attemptsStarted: 0 };
}

/**
 * Whether another JSON replay may start: under the attempt cap AND before the
 * overall deadline. The deadline wins over the attempt cap so a slow chain can
 * never exceed the budget.
 */
export function canAttemptReconciliation(
  budget: ReconciliationBudget,
  now: number,
  maxAttempts: number = RECONCILE_MAX_ATTEMPTS,
): boolean {
  return budget.attemptsStarted < maxAttempts && now < budget.deadline;
}

/**
 * Backoff before the next attempt after `attemptsStarted` attempts have
 * already run, or null when the attempt cap or the deadline forbids waiting
 * for another one. Matches the historical 250ms × attempt ladder.
 */
export function nextReconciliationBackoffMs(
  budget: ReconciliationBudget,
  now: number,
  backoffBaseMs: number = RECONCILE_BACKOFF_BASE_MS,
): number | null {
  if (budget.attemptsStarted >= RECONCILE_MAX_ATTEMPTS) return null;
  const delayMs = backoffBaseMs * budget.attemptsStarted;
  if (now + delayMs >= budget.deadline) return null;
  return delayMs;
}

/** Milliseconds left before the deadline; never negative. */
export function remainingReconcileMs(
  budget: ReconciliationBudget,
  now: number,
): number {
  return Math.max(0, budget.deadline - now);
}
