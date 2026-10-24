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
  delete: async () => ({ data: null }),
  get: async (url, config) => {
    apiCalls.push({ url, config });
    return { data: [] };
  },
  post: async (url, body) => {
    apiCalls.push({ url, body });
    return { data: {} };
  },
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

test('closeRound POSTs the governance reason to the close endpoint', async () => {
  await apiModule.thesisApi.closeRound('round-7', 'academic year archived');
  assert.deepEqual(apiCalls.at(-1), {
    url: '/thesis/rounds/round-7/close',
    body: { reason: 'academic year archived' },
  });
});

test('reopenRound POSTs the governance reason to the reopen endpoint', async () => {
  await apiModule.thesisApi.reopenRound('round-7', 'archive was premature');
  assert.deepEqual(apiCalls.at(-1), {
    url: '/thesis/rounds/round-7/reopen',
    body: { reason: 'archive was premature' },
  });
});

test('listRounds passes the status filter only when one is given', async () => {
  await apiModule.thesisApi.listRounds();
  assert.deepEqual(apiCalls.at(-1), { url: '/thesis/rounds', config: { params: {} } });
  await apiModule.thesisApi.listRounds('CLOSED');
  assert.deepEqual(apiCalls.at(-1), { url: '/thesis/rounds', config: { params: { status: 'CLOSED' } } });
});

test('campusCodeMessage maps the new close/reopen codes in both locales', () => {
  const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');
  const { en, vi } = loadMessages();
  const windowOpen = { response: { status: 409, data: { code: 'RETRACT_WINDOW_OPEN' } } };
  const closeAdmin = { response: { status: 403, data: { code: 'CLOSE_ADMIN_REQUIRED' } } };
  const reopenAdmin = { response: { status: 403, data: { code: 'REOPEN_ADMIN_REQUIRED' } } };
  for (const dict of [en, vi]) {
    const copy = dict.common.campusErrors;
    for (const code of ['RETRACT_WINDOW_OPEN', 'CLOSE_ADMIN_REQUIRED', 'REOPEN_ADMIN_REQUIRED']) {
      assert.ok(copy.codes[code], `${code} copy missing`);
    }
    assert.equal(campusCodeMessage(windowOpen, copy), copy.codes.RETRACT_WINDOW_OPEN);
    assert.equal(campusCodeMessage(closeAdmin, copy), copy.codes.CLOSE_ADMIN_REQUIRED);
    assert.equal(campusCodeMessage(reopenAdmin, copy), copy.codes.REOPEN_ADMIN_REQUIRED);
  }
});

test('close/reopen UI copy exists in both locales', () => {
  const { en, vi } = loadMessages();
  for (const dict of [en, vi]) {
    for (const key of [
      'closeRound',
      'closeRoundTitle',
      'closeRoundHint',
      'closeRoundConfirmLabel',
      'closeBlockedRetractWindow',
      'roundClosed',
      'reopenRound',
      'reopenRoundTitle',
      'roundReopened',
      'showClosedRounds',
    ]) {
      assert.ok(dict.thesis.admin[key], `thesis.admin.${key} missing`);
    }
  }
});

// --- page wiring: source assertions pin the close/reopen contract on the
// admin round card, matching the pattern the delete test uses. ---

const page = read('src/app/admin/thesis/page.tsx');

test('the close action only renders on a published round the server marks closeable', () => {
  assert.match(page, /round\.status === 'RESULTS_PUBLISHED' && round\.closure\?\.closeAllowed !== false/);
  assert.match(page, /kind: 'closeRound', roundId: round\.id, roundName: round\.name/);
});

test('an open retract window shows the blocker hint instead of a dead button', () => {
  assert.match(page, /round\.closure\?\.closeBlocker \? \(/);
  assert.match(page, /closeBlockedRetractWindow/);
});

test('the reopen action only renders on a closed round the server allows', () => {
  assert.match(page, /round\.status === 'CLOSED' && round\.closure\?\.reopenAllowed !== false/);
  assert.match(page, /kind: 'reopenRound', roundId: round\.id/);
});

test('closing requires the typed round name; reopening carries only the reason', () => {
  assert.match(page, /action\.kind === 'closeRound'[\s\S]{0,300}retractConfirmation\.trim\(\) !== action\.roundName/);
  assert.match(page, /thesisApi\.closeRound\(action\.roundId, reason\)/);
  assert.match(page, /action\.kind === 'reopenRound'[\s\S]{0,200}thesisApi\.reopenRound\(action\.roundId, reason\)/);
  assert.match(page, /messages\.thesis\.admin\.roundClosed/);
  assert.match(page, /messages\.thesis\.admin\.roundReopened/);
});

test('the archive view is opt-in via the show-closed toggle and the CLOSED filter', () => {
  assert.match(page, /showClosed \? thesisApi\.listRounds\('CLOSED'\)/);
  assert.match(page, /messages\.thesis\.admin\.showClosedRounds/);
});

// --- closed rounds stay resolvable for results/history while interactive
// pickers default to live rounds. ---

test('roundsForPicker hides archived rounds but keeps the selected one named', () => {
  const { roundsForPicker } = loadModule('src/lib/thesis-round.ts');
  const rounds = [
    { id: 'open-1', status: 'REGISTRATION_OPEN' },
    { id: 'pub-1', status: 'RESULTS_PUBLISHED' },
    { id: 'arch-1', status: 'CLOSED' },
    { id: 'arch-2', status: 'CLOSED' },
  ];
  assert.deepEqual(roundsForPicker(rounds).map((r) => r.id), ['open-1', 'pub-1']);
  assert.deepEqual(
    roundsForPicker(rounds, 'arch-1').map((r) => r.id),
    ['open-1', 'pub-1', 'arch-1'],
    'the viewed archived round must stay in the picker or the select goes blank',
  );
});

test('participant workspaces merge the CLOSED list so results still resolve', () => {
  for (const relative of [
    'src/app/dashboard/thesis/page.tsx',
    'src/app/dashboard/thesis/[roundId]/page.tsx',
    'src/app/dashboard/dean/page.tsx',
    'src/components/dashboard/thesis/useThesisWorkspace.ts',
  ]) {
    assert.match(
      read(relative),
      /listRounds\('CLOSED'\)/,
      `${relative} must merge the archived list or a closed round loses its view`,
    );
  }
});

test('interactive pickers route through roundsForPicker instead of raw rounds', () => {
  const workspacePage = read('src/app/dashboard/thesis/page.tsx');
  assert.match(workspacePage, /roundsForPicker\(rounds, selectedRoundId\)/);
  for (const relative of [
    'src/components/dashboard/thesis/ThesisProgressPage.tsx',
    'src/components/dashboard/thesis/ThesisTopicCatalogPage.tsx',
  ]) {
    assert.match(read(relative), /roundsForPicker\(workspace\.rounds/);
  }
});
