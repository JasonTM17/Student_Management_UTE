const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadTs(relativePath) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

const status = loadTs('src/lib/enrollment-status.ts');

test('enrollment status helpers expose exactly one shared definition', () => {
  assert.ok(Array.isArray(status.ACTIVE_ENROLLMENT_STATUSES));
  assert.ok(Array.isArray(status.DROPPABLE_ENROLLMENT_STATUSES));
  assert.equal(typeof status.isActiveEnrollment, 'function');
  assert.equal(typeof status.isDroppableEnrollment, 'function');
});

test('active membership matches the seeded demo statuses', () => {
  // Current contract: confirmed/enrolled rows are active; the phase-4 seat
  // accounting parity change updates this list and this test together.
  assert.equal(status.isActiveEnrollment('ENROLLED'), true);
  assert.equal(status.isActiveEnrollment('CONFIRMED'), true);
  assert.equal(status.isActiveEnrollment('COMPLETED'), false);
  assert.equal(status.isActiveEnrollment('DROPPED'), false);
  assert.equal(status.isActiveEnrollment('CANCELLED'), false);
});

test('droppable membership mirrors the backend drop gate', () => {
  assert.equal(status.isDroppableEnrollment('ENROLLED'), true);
  assert.equal(status.isDroppableEnrollment('PENDING'), true);
  assert.equal(status.isDroppableEnrollment('CONFIRMED'), false);
  assert.equal(status.isDroppableEnrollment('DROPPED'), false);
  assert.equal(status.isDroppableEnrollment('COMPLETED'), false);
});

test('helpers tolerate missing status values', () => {
  assert.equal(status.isActiveEnrollment(undefined), false);
  assert.equal(status.isActiveEnrollment(null), false);
  assert.equal(status.isActiveEnrollment(''), false);
  assert.equal(status.isDroppableEnrollment(undefined), false);
  assert.equal(status.isDroppableEnrollment(null), false);
});
