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

const page = read('src/app/admin/thesis/page.tsx');
const apiSource = read('src/lib/thesis-api.ts');

// The waive action is gated on the server-decided round capability, never on
// a hard-coded thesis-type list in the UI.
test('the waive action is driven by the round read-model capability', () => {
  assert.match(page, /round\.counterReviewWaiverAllowed/);
  assert.doesNotMatch(page, /thesisType === 'TLCN' \|\| round\.thesisType === 'KLTN'/);
  assert.doesNotMatch(page, /thesisType === 'KLTN' \|\| round\.thesisType === 'TLCN'/);
});

test('revoke stays reachable for a topic that already carries a waiver', () => {
  // The same button block renders revoke for an existing waiver even when the
  // round capability is off, so stored waivers can always be cleared.
  assert.match(
    page,
    /top\.gvpbWaivedReason != null \|\|[\s\S]{0,60}round\.counterReviewWaiverAllowed &&[\s\S]{0,60}round\.gvpbDeadline/,
  );
  assert.match(page, /top\.gvpbWaivedReason \? \([\s\S]{0,900}kind: 'revokeGvpbWaiver'/);
});

test('the round type exposes the server capability flag', () => {
  assert.match(apiSource, /counterReviewWaiverAllowed\?: boolean/);
});

test('the new conflict code has copy in both locales', () => {
  const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');
  const { en, vi } = loadMessages();
  const err = { response: { status: 409, data: { code: 'COUNTER_REVIEW_WAIVER_NOT_SUPPORTED' } } };
  for (const dict of [en, vi]) {
    const copy = dict.common.campusErrors;
    assert.ok(copy.codes.COUNTER_REVIEW_WAIVER_NOT_SUPPORTED, 'code copy missing');
    assert.equal(campusCodeMessage(err, copy), copy.codes.COUNTER_REVIEW_WAIVER_NOT_SUPPORTED);
  }
});

test('waiver i18n keys stay intact for capability-on surfaces', () => {
  const { en, vi } = loadMessages();
  for (const dict of [en, vi]) {
    for (const key of ['waiveGvpb', 'waiveGvpbTitle', 'revokeGvpbWaiver', 'revokeGvpbWaiverTitle', 'gvpbWaivedBadge']) {
      assert.ok(dict.thesis.admin[key], `thesis.admin.${key} missing`);
    }
    for (const key of ['GVPB_ALREADY_WAIVED', 'GVPB_NOT_WAIVED']) {
      assert.ok(dict.common.campusErrors.codes[key], `campusErrors.${key} missing`);
    }
  }
});
