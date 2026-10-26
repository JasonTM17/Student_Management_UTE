const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadHelper() {
  const source = read('src/lib/grade-appeal-deadline.ts');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

test('grade detail renders an appeal deadline only when the API says the grade is appealable', () => {
  const modal = read('src/components/dashboard/GradeDetailModal.tsx');
  assert.match(modal, /shouldShowAppealDeadline\(eligibility\?\.appealAllowed === true, appealDeadline\)/);

  const { shouldShowAppealDeadline } = loadHelper();
  assert.equal(shouldShowAppealDeadline(false, '2026-11-01T00:00:00Z'), false, 'withdrawn grade with a deadline is hidden');
  assert.equal(shouldShowAppealDeadline(true, '2026-11-01T00:00:00Z'), true, 'appealable grade shows its deadline');
  assert.equal(shouldShowAppealDeadline(false, '2026-10-01T00:00:00Z'), false, 'closed window is hidden');
  assert.equal(shouldShowAppealDeadline(true, null), false, 'missing deadline is hidden');
});
