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
  delete: async (url, config) => {
    apiCalls.push({ url, config });
    return { data: null };
  },
  get: async () => ({ data: [] }),
  post: async () => ({ data: {} }),
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

test('deleteRound issues DELETE with the governance reason in the body', async () => {
  await apiModule.thesisApi.deleteRound('round-9', 'created by mistake');
  assert.deepEqual(apiCalls.at(-1), {
    url: '/thesis/rounds/round-9',
    config: { data: { reason: 'created by mistake' } },
  });
});

test('campusCodeMessage maps the new conflict codes in both locales', () => {
  const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');
  const { en, vi } = loadMessages();
  const hasData = { response: { status: 409, data: { code: 'ROUND_HAS_DATA' } } };
  const notDeletable = { response: { status: 409, data: { code: 'ROUND_NOT_DELETABLE' } } };
  for (const dict of [en, vi]) {
    const copy = dict.common.campusErrors;
    assert.ok(copy.codes.ROUND_HAS_DATA, 'ROUND_HAS_DATA copy missing');
    assert.ok(copy.codes.ROUND_NOT_DELETABLE, 'ROUND_NOT_DELETABLE copy missing');
    assert.equal(campusCodeMessage(hasData, copy), copy.codes.ROUND_HAS_DATA);
    assert.equal(campusCodeMessage(notDeletable, copy), copy.codes.ROUND_NOT_DELETABLE);
  }
});

test('delete-round UI copy exists in both locales', () => {
  const { en, vi } = loadMessages();
  for (const dict of [en, vi]) {
    for (const key of [
      'deleteRound',
      'deleteRoundTitle',
      'deleteRoundHint',
      'deleteRoundConfirmLabel',
      'roundDeleted',
      'deleteBlockersLabel',
      'deleteBlockerTopics',
      'deleteBlockerGroups',
      'deleteBlockerMembers',
      'deleteBlockerReports',
      'deleteBlockerCouncils',
    ]) {
      assert.ok(dict.thesis.admin[key], `thesis.admin.${key} missing`);
    }
  }
});

// --- page wiring: source assertions pin the delete contract on the admin
// round card, matching the pattern the restore test uses. ---

const page = read('src/app/admin/thesis/page.tsx');

test('the delete button is disabled with a reason until the backend marks the round deletable', () => {
  assert.match(page, /round\.deletion \? \(/);
  assert.match(page, /disabled=\{isSaving \|\| !round\.deletion\.deletable\}/);
  assert.match(page, /deleteRoundUnavailable/);
  assert.match(page, /kind: 'deleteRound', roundId: round\.id, roundName: round\.name/);
});

test('deletion requires the typed round name and carries the reason', () => {
  assert.match(page, /retractConfirmation\.trim\(\) !== action\.roundName/);
  assert.match(page, /thesisApi\.deleteRound\(action\.roundId, reason\)/);
  assert.match(page, /messages\.thesis\.admin\.roundDeleted/);
});

test('a non-empty round shows the data counts beside the disabled delete action', () => {
  assert.match(page, /deleteUnavailableReason = round\.deletion\?\.deletable[\s\S]{0,180}deleteBlockerSummary\(round\)/);
  assert.match(page, /id=\{`delete-round-reason-\$\{round\.id\}`\}/);
});
