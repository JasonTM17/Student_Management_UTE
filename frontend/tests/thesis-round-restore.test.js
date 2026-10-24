const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

function loadModule(relative, aliases = {}) {
  const output = ts.transpileModule(read(relative), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.ReactJSX,
      esModuleInterop: true,
    },
    fileName: relative,
  }).outputText;
  const moduleRecord = { exports: {} };
  const resolve = (name) => Object.prototype.hasOwnProperty.call(aliases, name)
    ? aliases[name]
    : require(name);
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, resolve);
  return moduleRecord.exports;
}

function loadMessages() {
  return loadModule('src/i18n/messages.ts', {
    '@/i18n/config': {},
    '@/i18n/messages-thesis-workflow': loadModule('src/i18n/messages-thesis-workflow.ts'),
    '@/i18n/messages-dean-overview': loadModule('src/i18n/messages-dean-overview.ts'),
  });
}

// --- behavioral checks: real modules against stubbed boundaries ---

const apiCalls = [];
const fakeApi = {
  post: async (url, body) => {
    apiCalls.push({ url, body });
    return { data: { id: 'round-x' } };
  },
  get: async () => ({ data: [] }),
};
const apiModule = loadModule('src/lib/thesis-api.ts', {
  '@/lib/api': {
    __esModule: true,
    default: fakeApi,
    API_BASE_URL: 'http://test.invalid',
    createRequestId: () => 'req-test',
    refreshSessionSingleFlight: async () => null,
  },
  '@/lib/assistant-stream': {
    createAssistantSseParser: () => ({}),
    parseAssistantStreamEvent: () => null,
    AssistantStreamOrder: {},
  },
  '@/lib/assistant-stream-helpers': { CANCEL_REQUEST_TIMEOUT_MS: 1000 },
});

test('restoreRound posts to the lifecycle route, omitting or carrying the target', async () => {
  await apiModule.thesisApi.restoreRound('round-1');
  assert.deepEqual(apiCalls.at(-1), {
    url: '/thesis/rounds/round-1/restore',
    body: {},
  });
  await apiModule.thesisApi.restoreRound('round-2', 'REGISTRATION_CLOSED');
  assert.deepEqual(apiCalls.at(-1), {
    url: '/thesis/rounds/round-2/restore',
    body: { targetStatus: 'REGISTRATION_CLOSED' },
  });
});

const { planThesisRoundRestore } = loadModule('src/lib/thesis-round.ts');

const cancelledRound = (overrides = {}) => ({
  status: 'CANCELLED',
  registrationStart: '2026-09-01T00:00:00Z',
  registrationEnd: '2026-09-10T00:00:00Z',
  ...overrides,
});
const INSIDE_WINDOW = Date.parse('2026-09-05T00:00:00Z');
const AFTER_WINDOW = Date.parse('2026-10-01T00:00:00Z');

test('a recorded marker replays itself without a picker', () => {
  const plan = planThesisRoundRestore(
    cancelledRound({ cancelledFromStatus: 'REGISTRATION_CLOSED' }), true, INSIDE_WINDOW);
  assert.equal(plan.requiresChoice, false);
  assert.equal(plan.implicitTarget, 'REGISTRATION_CLOSED');
  assert.deepEqual(plan.explicitTargets, []);
});

test('a registration-open marker inside its window restores implicitly', () => {
  const plan = planThesisRoundRestore(
    cancelledRound({ cancelledFromStatus: 'REGISTRATION_OPEN' }), true, INSIDE_WINDOW);
  assert.equal(plan.requiresChoice, false);
  assert.equal(plan.implicitTarget, 'REGISTRATION_OPEN');
});

test('a registration-open marker past its window can only land on closed', () => {
  const plan = planThesisRoundRestore(
    cancelledRound({ cancelledFromStatus: 'REGISTRATION_OPEN' }), true, AFTER_WINDOW);
  assert.equal(plan.requiresChoice, true);
  assert.equal(plan.implicitTarget, null);
  assert.deepEqual(plan.explicitTargets, ['REGISTRATION_CLOSED']);
});

test('a legacy cancellation with live groups must be picked explicitly', () => {
  const plan = planThesisRoundRestore(
    cancelledRound({ cancelledFromStatus: null }), true, INSIDE_WINDOW);
  assert.equal(plan.requiresChoice, true);
  assert.equal(plan.implicitTarget, null);
  assert.deepEqual(plan.explicitTargets, ['REGISTRATION_OPEN', 'REGISTRATION_CLOSED']);
});

test('a legacy cancellation past the window only offers closed', () => {
  const plan = planThesisRoundRestore(
    cancelledRound({ cancelledFromStatus: null }), true, AFTER_WINDOW);
  assert.equal(plan.requiresChoice, true);
  assert.deepEqual(plan.explicitTargets, ['REGISTRATION_CLOSED']);
});

test('a legacy cancellation without live groups defaults to draft', () => {
  const plan = planThesisRoundRestore(
    cancelledRound({ cancelledFromStatus: null }), false, AFTER_WINDOW);
  assert.equal(plan.requiresChoice, false);
  assert.equal(plan.implicitTarget, 'DRAFT');
});

test('a non-cancelled round has no restore plan', () => {
  const plan = planThesisRoundRestore(
    cancelledRound({ status: 'REGISTRATION_OPEN' }), true, INSIDE_WINDOW);
  assert.equal(plan.requiresChoice, false);
  assert.equal(plan.implicitTarget, null);
  assert.deepEqual(plan.explicitTargets, []);
});

// --- copy exists in both locales (behavioral: the real message module) ---

test('restore and honest-cancellation copy exist in both locales', () => {
  const { en, vi } = loadMessages();
  for (const dict of [en, vi]) {
    for (const key of [
      'restoreRound',
      'restoreRoundTitle',
      'restoreRoundConfirm',
      'restoreRoundConfirmLegacy',
      'restoreTargetLabel',
      'restoreRoundPickTargetRequired',
      'restoreRoundWindowPassed',
      'roundRestored',
    ]) {
      assert.ok(dict.thesis.admin[key], `thesis.admin.${key} missing`);
    }
    assert.ok(dict.thesis.progressRoundCancelled, 'progressRoundCancelled missing');
    assert.ok(dict.thesis.roundCancelledNotice, 'roundCancelledNotice missing');
    assert.ok(dict.thesis.review.roundCancelled, 'review.roundCancelled missing');
    assert.ok(dict.thesis.grading.roundCancelled, 'grading.roundCancelled missing');
    const codes = dict.common.campusErrors.codes;
    assert.ok(codes.ROUND_RESTORE_MEMBER_CONFLICT, 'ROUND_RESTORE_MEMBER_CONFLICT missing');
    assert.ok(codes.ROUND_RESTORE_FROZEN_HISTORY, 'ROUND_RESTORE_FROZEN_HISTORY missing');
    assert.ok(codes.ROUND_RESTORE_TARGET_REQUIRED, 'ROUND_RESTORE_TARGET_REQUIRED missing');
    assert.ok(codes.ROUND_RESTORE_TARGET_INVALID, 'ROUND_RESTORE_TARGET_INVALID missing');
  }
});

// --- page wiring: the admin route component depends on auth, routing and data
// effects, so this harness cannot render it; the structure is asserted on
// source and limited to the lines that pin the restore contract. ---

test('the admin page gates restore on CANCELLED and drives the picker from the plan', () => {
  const page = read('src/app/admin/thesis/page.tsx');
  const gate = page.match(/round\.status === 'CANCELLED'[\s\S]{0,400}?restoreRound/);
  assert.ok(gate, 'the restore action must be gated on the CANCELLED status');
  assert.match(page, /planThesisRoundRestore\(round, hasLiveGroups\)/);
  assert.match(page, /plan\.requiresChoice/);
  assert.match(page, /performRestore\(restoreTargetRound\.id, restoreStatusDraft\)/);
});

test('the progress view explains a cancelled round', () => {
  const page = read('src/components/dashboard/thesis/ThesisProgressPage.tsx');
  assert.match(page, /attentionState === 'ROUND_CANCELLED'/);
  assert.match(page, /progressRoundCancelled/);
});

test('lecturer auto-selection skips cancelled rounds', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');
  assert.match(page, /topic\.roundStatus !== 'CANCELLED'/);
});
