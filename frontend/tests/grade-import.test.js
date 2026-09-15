const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');

function loadGradeSheetModule() {
  const source = fs.readFileSync('src/lib/grade-sheet.ts', 'utf-8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

const { parseGradeSheet } = loadGradeSheetModule();

const STUDENTS = [
  { enrollmentId: 'enr-1', studentCode: 'CS-DEMO-155', email: 'student155@campuscore.demo' },
  { enrollmentId: 'enr-2', studentCode: 'CS-DEMO-131', email: 'student131@campuscore.demo' },
  { enrollmentId: 'enr-3', studentCode: 'CS-DEMO-107', email: 'student107@campuscore.demo' },
];

test('grade sheet import parses a headered CSV and matches by student code', () => {
  const csv = [
    'Mã sinh viên,ĐQT (50%),ĐCK (50%)',
    'CS-DEMO-155,6.5,7.5',
    'cs-demo-131,8,9',
  ].join('\n');

  const result = parseGradeSheet(csv, STUDENTS);

  assert.equal(result.applied.length, 2);
  assert.equal(result.skipped.length, 0);
  assert.deepEqual(result.applied[0], { enrollmentId: 'enr-1', processScore: 6.5, finalExamScore: 7.5 });
  // Case-insensitive matching, and an integer score stays a number.
  assert.deepEqual(result.applied[1], { enrollmentId: 'enr-2', processScore: 8, finalExamScore: 9 });
});

test('grade sheet import accepts the tab-separated block Excel copies and email keys', () => {
  const pasted = [
    'student155@campuscore.demo\t6\t7',
    'CS-DEMO-107\t5,5\t6,5',
  ].join('\n');

  const result = parseGradeSheet(pasted, STUDENTS);

  assert.equal(result.applied.length, 2);
  assert.equal(result.applied[0].enrollmentId, 'enr-1');
  // Vietnamese CSVs use a comma decimal separator.
  assert.equal(result.applied[1].processScore, 5.5);
  assert.equal(result.applied[1].finalExamScore, 6.5);
});

test('grade sheet import falls back to column order without a header', () => {
  const result = parseGradeSheet('CS-DEMO-155;7;8', STUDENTS);

  assert.deepEqual(result.applied, [{ enrollmentId: 'enr-1', processScore: 7, finalExamScore: 8 }]);
});

test('grade sheet import reports the rows it could not use and why', () => {
  const csv = [
    'Mã sinh viên,ĐQT,ĐCK',
    'CS-DEMO-155,6,7', // applied
    'CS-DEMO-999,6,7', // unknown student
    'CS-DEMO-131,abc,7', // invalid score
    'CS-DEMO-107,12,7', // out of range
    'CS-DEMO-155,3,4', // duplicate student
    ',6,7', // missing code
  ].join('\n');

  const result = parseGradeSheet(csv, STUDENTS);

  assert.equal(result.applied.length, 1);
  assert.deepEqual(
    result.skipped.map((s) => s.reason),
    ['unknown-student', 'invalid-score', 'invalid-score', 'duplicate', 'missing-student'],
  );
  assert.deepEqual(
    result.skipped.map((s) => s.line),
    [3, 4, 5, 6, 7],
  );
});

test('grade sheet import ignores an empty sheet', () => {
  const result = parseGradeSheet('\n \n', STUDENTS);

  assert.equal(result.applied.length, 0);
  assert.equal(result.skipped.length, 1);
});
