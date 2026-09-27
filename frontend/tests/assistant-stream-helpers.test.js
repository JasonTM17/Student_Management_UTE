const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function load(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

const {
  CANCEL_REQUEST_TIMEOUT_MS,
  RECONCILE_BACKOFF_BASE_MS,
  RECONCILE_MAX_ATTEMPTS,
  RECONCILE_TOTAL_BUDGET_MS,
  canAttemptReconciliation,
  hasOrphanedSendLock,
  nextReconciliationBackoffMs,
  remainingReconcileMs,
  startReconciliationBudget,
} = load('src/lib/assistant-stream-helpers.ts');

test('ASSIST-LOCK: an orphaned lock is detected when no message is pending', () => {
  // Settled conversation: every turn finished, yet the lock is still held.
  assert.equal(
    hasOrphanedSendLock([
      { role: 'user', content: 'hi' },
      { role: 'assistant', content: 'answer', pending: false },
    ]),
    true,
  );
  // Empty conversation is also an orphan (the previous dispatch never landed).
  assert.equal(hasOrphanedSendLock([]), true);
});

test('ASSIST-LOCK: an in-flight turn keeps a pending message so it never self-heals', () => {
  assert.equal(
    hasOrphanedSendLock([
      { role: 'user', content: 'hi' },
      { role: 'assistant', content: '', pending: true },
    ]),
    false,
  );
  // Streaming deltas and replaces keep pending=true on the last assistant row.
  assert.equal(
    hasOrphanedSendLock([
      { role: 'user', content: 'hi' },
      { role: 'assistant', content: 'partial', pending: true },
    ]),
    false,
  );
});

test('ASSIST-BUDGET: the budget starts at now + total and counts zero attempts', () => {
  const budget = startReconciliationBudget(1_000);
  assert.equal(budget.deadline, 1_000 + RECONCILE_TOTAL_BUDGET_MS);
  assert.equal(budget.attemptsStarted, 0);
  assert.equal(RECONCILE_TOTAL_BUDGET_MS, 30_000);
});

test('ASSIST-BUDGET: attempts are capped and the deadline wins over the cap', () => {
  const budget = startReconciliationBudget(0);
  budget.attemptsStarted = RECONCILE_MAX_ATTEMPTS;
  assert.equal(canAttemptReconciliation(budget, 1), false, 'attempt cap stops new attempts');

  const tight = startReconciliationBudget(0, 5_000);
  tight.attemptsStarted = 1;
  assert.equal(canAttemptReconciliation(tight, 4_999), true);
  assert.equal(canAttemptReconciliation(tight, 5_000), false, 'deadline stops new attempts');
});

test('ASSIST-BUDGET: backoff ladder matches 250ms x attempt and stops at the cap', () => {
  const budget = startReconciliationBudget(0);
  budget.attemptsStarted = 1;
  assert.equal(nextReconciliationBackoffMs(budget, 0), 1 * RECONCILE_BACKOFF_BASE_MS);
  budget.attemptsStarted = 2;
  assert.equal(nextReconciliationBackoffMs(budget, 0), 2 * RECONCILE_BACKOFF_BASE_MS);
  budget.attemptsStarted = 3;
  assert.equal(nextReconciliationBackoffMs(budget, 0), 3 * RECONCILE_BACKOFF_BASE_MS);
  budget.attemptsStarted = RECONCILE_MAX_ATTEMPTS;
  assert.equal(nextReconciliationBackoffMs(budget, 0), null, 'no wait past the attempt cap');
});

test('ASSIST-BUDGET: the deadline forbids a backoff that would overshoot it', () => {
  const budget = startReconciliationBudget(0, 600);
  budget.attemptsStarted = 3; // 750ms backoff would overshoot the 600ms budget.
  assert.equal(nextReconciliationBackoffMs(budget, 0), null);
});

test('ASSIST-BUDGET: remaining time never goes negative and cancel timeout is bounded', () => {
  const budget = startReconciliationBudget(0, 1_000);
  assert.equal(remainingReconcileMs(budget, 400), 600);
  assert.equal(remainingReconcileMs(budget, 2_000), 0);
  assert.equal(CANCEL_REQUEST_TIMEOUT_MS, 8_000);
});
