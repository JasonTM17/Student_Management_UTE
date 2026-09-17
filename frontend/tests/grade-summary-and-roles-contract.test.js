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

const { summarizeGrades } = loadTs('src/lib/grade-scale.ts');

function grade(overrides) {
  return {
    id: 'grade',
    courseCode: 'CS101',
    courseName: 'Intro',
    credits: 3,
    sectionCode: 'A',
    lecturerName: null,
    semester: '2026-FALL',
    semesterId: 'semester-1',
    processScore: null,
    finalExamScore: null,
    finalGrade: null,
    letterGrade: null,
    gradeStatus: 'PUBLISHED',
    enrollmentStatus: 'COMPLETED',
    ...overrides,
  };
}

// STUD-P2-4: a course taken twice (F then improved to B) used to be counted
// twice by "Tín chỉ hoàn tất" because the stat summed every non-F COMPLETED
// row while the GPA card used best attempts. One derivation now feeds both.

test('completed credits count an improved course once via its best attempt', () => {
  const retake = [
    grade({ id: 'g1', courseCode: 'CS101', letterGrade: 'F', credits: 3, finalGrade: 3.0 }),
    grade({ id: 'g2', courseCode: 'CS101', letterGrade: 'B', credits: 4, finalGrade: 8.0 }),
  ];

  const summary = summarizeGrades(retake);

  // Pre-fix this summed 3 + 4 = 7 credits; the best attempt counts 4 once.
  assert.equal(summary.completedCredits, 4);
  // GPA comes from the same best-attempt list: 3.0 points * 4 credits / 4.
  assert.equal(summary.gpa, '3.00');
});

test('a failed course earns no completed credits even though it is COMPLETED', () => {
  const summary = summarizeGrades([
    grade({ courseCode: 'MA101', letterGrade: 'F', credits: 3 }),
  ]);
  assert.equal(summary.completedCredits, 0);
  assert.equal(summary.gradedCount, 1);
});

test('best attempt wins across attempts regardless of order', () => {
  const summary = summarizeGrades([
    grade({ id: 'g1', courseCode: 'CS101', letterGrade: 'C', credits: 3 }),
    grade({ id: 'g2', courseCode: 'CS101', letterGrade: 'A', credits: 3 }),
    grade({ id: 'g3', courseCode: 'CS102', letterGrade: 'F', credits: 2 }),
    grade({ id: 'g4', courseCode: 'CS102', letterGrade: 'D', credits: 2 }),
  ]);
  // CS101 best = A (3), CS102 best = D (2, D > F).
  assert.equal(summary.completedCredits, 5);
});

test('non-completed rows never add completed credits', () => {
  const summary = summarizeGrades([
    grade({ enrollmentStatus: 'ENROLLED', letterGrade: 'A', credits: 3 }),
    grade({ enrollmentStatus: 'DROPPED', letterGrade: 'B', credits: 3 }),
  ]);
  assert.equal(summary.completedCredits, 0);
});

test('empty grade list yields zeroes without dividing by zero', () => {
  const summary = summarizeGrades([]);
  assert.equal(summary.gpa, '0.00');
  assert.equal(summary.completedCredits, 0);
  assert.equal(summary.courseCount, 0);
});

// ADM-P2-3: the admin users API now returns `roles` as an array (matching
// AuthUserResponse), so the console page must not compensate by splitting a
// comma-joined string.

test('admin users page consumes roles as an array, not a comma string', () => {
  const source = read('src/app/admin/users/page.tsx');

  assert.match(source, /roles\?: string\[\]/);
  assert.doesNotMatch(source, /roles\?\.split\(','\)/);
  assert.doesNotMatch(source, /Array\.isArray\(roles\)/);
  assert.match(source, /recordRoles\(u\)/);
});
