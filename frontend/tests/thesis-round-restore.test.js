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

test('the API client exposes a restore call on the round lifecycle route', () => {
  const api = read('src/lib/thesis-api.ts');
  assert.match(api, /restoreRound/);
  assert.match(api, /\/thesis\/rounds\/' \+ roundId \+ '\/restore'/);
});

test('the admin page offers restore only for cancelled rounds', () => {
  const page = read('src/app/admin/thesis/page.tsx');
  assert.match(page, /thesisApi\.restoreRound\(round\.id\)/);
  const gate = page.match(/round\.status === 'CANCELLED'[\s\S]{0,400}?restoreRound/);
  assert.ok(gate, 'the restore action must be gated on the CANCELLED status');
});

test('restore and honest-cancellation copy exist in both locales', () => {
  const { en, vi } = loadMessages();
  for (const dict of [en, vi]) {
    for (const key of [
      'restoreRound',
      'restoreRoundTitle',
      'restoreRoundConfirm',
      'restoreRoundConfirmLegacy',
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
  }
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
