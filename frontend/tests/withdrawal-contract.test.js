const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function transpile(relativePath) {
  return ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
}

function loadModule(relativePath, extraNamespaces = {}) {
  const output = transpile(relativePath);
  const customRequire = (id) => {
    const namespace = Object.keys(extraNamespaces).find((name) => id.includes(name));
    if (namespace) {
      return loadModule(extraNamespaces[namespace], {});
    }
    return require(id);
  };
  const moduleRecord = { exports: {} };
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, customRequire);
  return moduleRecord.exports;
}

function loadMessages() {
  return loadModule('src/i18n/messages.ts', {
    'messages-thesis-workflow': 'src/i18n/messages-thesis-workflow.ts',
    'messages-dean-overview': 'src/i18n/messages-dean-overview.ts',
  });
}

const WITHDRAWAL_CODES = [
  'WITHDRAWAL_WINDOW_CLOSED',
  'WITHDRAWAL_SEMESTER_INELIGIBLE',
  'WITHDRAWAL_APPLICATION_EXISTS',
  'WITHDRAWAL_APPLICATION_NOT_PENDING',
];

test('student withdraw action is gated on the open window and persisted enrollments', () => {
  const page = read('src/app/dashboard/register/page.tsx');

  // The withdraw button renders only while the semester window is open, and
  // only for enrollments the backend already knows about (local draft rows use
  // a `pending:` id and can never carry a withdrawal application).
  assert.match(page, /withdrawalWindowQuery\.data\?\.open && !enrollment\.id\.startsWith\('pending:'\)/);
  assert.match(page, /registrationApi\.withdrawalWindow\(semesterId as string\)/);

  // An enrollment that already has a pending application shows the cancel
  // affordance instead of a second submit path.
  assert.match(page, /withdrawalByEnrollment\.get\(enrollment\.id\)/);
  assert.match(page, /cancelWithdrawalApplication\(application\.id\)/);
  assert.match(page, /cancellingApplication === application\.id/);

  // The destructive cancel confirm must carry a message — ConfirmationOptions
  // requires one, and bare titles gave no explanation when this regressed.
  const cancelConfirm = page.match(/cancelWithdraw[\s\S]*?confirm\(\{([\s\S]*?)\}\)/);
  assert.ok(cancelConfirm, 'cancelWithdraw confirm call not found');
  assert.match(cancelConfirm[1], /message: copy\.withdrawCancelMessage/);
  assert.match(page, /withdrawCancelMessage/);

  // The modal enforces the backend reason contract client-side (min length 20,
  // 1000-char counter) and cannot be dismissed mid-submit.
  assert.match(page, /withdrawReason\.trim\(\)\.length < 20/);
  assert.match(page, /withdrawReason\.trim\(\)\.length\}\/1000/);
  assert.match(page, /if \(withdrawBusy\) return;/);
  assert.match(page, /disabled=\{withdrawBusy \|\| withdrawReason\.trim\(\)\.length < 20\}/);
});

test('admin withdrawal page keeps review actions behind PENDING status', () => {
  const page = read('src/app/admin/withdrawal-applications/page.tsx');
  const shim = read(path.join('src', 'app', '[locale]', 'admin', 'withdrawal-applications', 'page.tsx'));

  // The English locale tree must re-export the canonical page or the route 404s.
  assert.match(shim, /export \{ default \} from '\.\.\/\.\.\/\.\.\/admin\/withdrawal-applications\/page'/);

  // Approve/reject affordances render only for pending applications.
  assert.match(page, /application\.status === 'PENDING'/);
  assert.match(page, /adminRegistrationApi\.reviewWithdrawalApplication\(/);

  // A rejection without a reviewer note is blocked client-side, matching the
  // backend review-field CHECK constraint.
  assert.match(page, /rejectionNoteRequired/);
  assert.match(page, /useState<StatusFilter>\('PENDING'\)/);
});

test('en and vi define identical withdrawal code and copy surfaces', () => {
  const { en, vi } = loadMessages();

  const enCodes = Object.keys(en.common.campusErrors.codes).sort();
  const viCodes = Object.keys(vi.common.campusErrors.codes).sort();
  assert.deepEqual(enCodes, viCodes, 'campusErrors.codes keys diverged between locales');

  for (const code of WITHDRAWAL_CODES) {
    assert.ok(en.common.campusErrors.codes[code], `en missing ${code}`);
    assert.ok(vi.common.campusErrors.codes[code], `vi missing ${code}`);
    assert.doesNotMatch(vi.common.campusErrors.codes[code], /withdrawal|semester/i,
      `vi copy for ${code} looks untranslated`);
  }

  // Both feature surfaces must have copy in each locale.
  for (const key of ['withdrawTitle', 'withdrawSubmitted', 'withdrawCancel', 'withdrawCancelMessage', 'withdrawReason']) {
    assert.ok(en.courseRegistration[key], `en.courseRegistration missing ${key}`);
    assert.ok(vi.courseRegistration[key], `vi.courseRegistration missing ${key}`);
  }
  for (const key of ['rejectionNoteRequired', 'reviewed', 'statusPending']) {
    assert.ok(en.admin.withdrawalApplications[key], `en.admin.withdrawalApplications missing ${key}`);
    assert.ok(vi.admin.withdrawalApplications[key], `vi.admin.withdrawalApplications missing ${key}`);
  }
});

test('backend withdrawal codes map to specific copy ahead of HTTP-kind fallback', () => {
  const { campusCodeMessage, campusErrorKind } = loadModule('src/lib/campus-error.ts');
  const { en } = loadMessages();
  const copy = en.common.campusErrors;

  // Every withdrawal code the backend emits resolves to its specific message,
  // not the generic conflict copy.
  for (const code of WITHDRAWAL_CODES) {
    const error = { response: { status: 409, data: { code, message: 'internal detail' } } };
    assert.equal(campusCodeMessage(error, copy), copy.codes[code],
      `${code} must resolve to its code-specific copy`);
    assert.equal(campusErrorKind(error), 'conflict');
  }

  // An unmapped code still degrades to the HTTP-kind copy, never a raw backend
  // message.
  const unmapped = { response: { status: 409, data: { code: 'SOME_FUTURE_CODE', message: 'raw detail' } } };
  assert.equal(campusCodeMessage(unmapped, copy), copy.conflict);
});
