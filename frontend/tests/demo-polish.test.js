const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function load(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

test('GPA trend points read oldest-first even when the API returns newest-first', () => {
  const { buildGpaTrendPoints } = load('src/lib/transcript-charts.ts');
  const semesters = [
    { semesterName: 'Học kỳ 2', semesterNameVi: 'HK2 2025-2026', gpa: 3.07, records: [] },
    { semesterName: 'Học kỳ 1', semesterNameVi: 'HK1 2025-2026', gpa: 2.85, records: [] },
  ];
  const points = buildGpaTrendPoints(semesters, 'vi');
  assert.equal(points.length, 2);
  assert.match(points[0].fullLabel, /HK1/);
  assert.match(points[1].fullLabel, /HK2/);
  assert.equal(points[0].gpa, 2.85);
});

test('GPA trend keeps a single graded semester as one explicit point', () => {
  const { buildGpaTrendPoints } = load('src/lib/transcript-charts.ts');
  const points = buildGpaTrendPoints(
    [{ semesterName: 'Học kỳ 2', gpa: 3.0, records: [] }],
    'en',
  );
  assert.equal(points.length, 1);
  assert.equal(points[0].gpa, 3.0);
});

test('GPA trend names follow the selected language with missing-translation fallbacks', () => {
  const { buildGpaTrendPoints } = load('src/lib/transcript-charts.ts');
  const term = { semesterName: 'Term', semesterNameVi: 'Học kỳ một', semesterNameEn: 'First semester', gpa: 3, records: [] };
  assert.equal(buildGpaTrendPoints([term], 'en')[0].fullLabel, 'First semester');
  assert.equal(buildGpaTrendPoints([term], 'vi')[0].fullLabel, 'Học kỳ một');
  assert.equal(buildGpaTrendPoints([{ ...term, semesterNameEn: null }], 'en')[0].fullLabel, 'Học kỳ một');
  assert.equal(buildGpaTrendPoints([{ ...term, semesterNameVi: null }], 'vi')[0].fullLabel, 'First semester');
  assert.equal(buildGpaTrendPoints([{ ...term, semesterNameVi: null, semesterNameEn: null }], 'en')[0].fullLabel, 'Term');
});

test('grade distribution buckets published letters and ignores ungraded records', () => {
  const { buildGradeDistribution } = load('src/lib/transcript-charts.ts');
  const buckets = buildGradeDistribution([
    {
      records: [
        { letterGrade: 'B+' },
        { letterGrade: 'A' },
        { letterGrade: 'B+' },
        { letterGrade: null },
        { letterGrade: 'F' },
        { letterGrade: 'C+' },
      ],
    },
  ]);
  assert.deepEqual(
    buckets.map((bucket) => bucket.letter),
    ['A', 'B+', 'C+', 'F'],
  );
  assert.deepEqual(
    buckets.map((bucket) => bucket.count),
    [1, 2, 1, 1],
  );
});

test('weekly timetable grid keeps every meeting and covers all seven days', () => {
  const { buildWeeklyGrid } = load('src/lib/weekly-grid.ts');
  const grid = buildWeeklyGrid([
    { dayOfWeek: 2, startTime: '07:00', endTime: '09:30', courseCode: 'SE401', sectionNumber: 'SE401-01' },
    { dayOfWeek: 3, startTime: '09:45', endTime: '12:15', courseCode: 'SE403', sectionNumber: 'SE403-01' },
    { dayOfWeek: 6, startTime: '07:00', endTime: '09:30', courseCode: 'SE405', sectionNumber: 'SE405-01' },
    { dayOfWeek: 7, startTime: '13:00', endTime: '15:30', courseCode: 'SE407', sectionNumber: 'SE407-01' },
  ]);
  assert.deepEqual(grid.days, [1, 2, 3, 4, 5, 6, 7]);
  assert.deepEqual(grid.slots, ['07:00', '09:45', '13:00']);
  const placed = Object.values(grid.cells).reduce((sum, list) => sum + list.length, 0);
  assert.equal(placed, 4);
  assert.equal(grid.cells['7-13:00'][0].courseCode, 'SE407');
  assert.equal(grid.cells['6-07:00'][0].courseCode, 'SE405');
});

test('assistant follow-up chips render from panel state, not markdown', () => {
  const panelSource = fs.readFileSync(
    path.join(root, 'src/components/assistant/AssistantPanel.tsx'),
    'utf8',
  );
  assert.match(panelSource, /followUps=\{isSending \? undefined : messages\.assistant\.suggestions\}/);
  assert.doesNotMatch(panelSource, /dangerouslySetInnerHTML/);
  const messagesSource = fs.readFileSync(
    path.join(root, 'src/components/assistant/AssistantMessages.tsx'),
    'utf8',
  );
  assert.match(messagesSource, /lastAssistantIndex/);
  assert.match(messagesSource, /onFollowUp\(suggestion\)/);
});

test('document titles are wired through the shared hook in both shells', () => {
  const hook = fs.readFileSync(path.join(root, 'src/lib/use-document-title.ts'), 'utf8');
  assert.match(hook, /document\.title = /);
  const dashboardLayout = fs.readFileSync(
    path.join(root, 'src/app/dashboard/layout.tsx'),
    'utf8',
  );
  assert.match(dashboardLayout, /useDocumentTitle\(currentPage\.title\)/);
  const adminFrame = fs.readFileSync(
    path.join(root, 'src/components/admin/AdminFrame.tsx'),
    'utf8',
  );
  assert.match(adminFrame, /useDocumentTitle\(title\)/);
});

test('demo polish regression tests stay registered in the test script', () => {
  const packageJson = JSON.parse(
    fs.readFileSync(path.join(root, 'package.json'), 'utf8'),
  );
  assert.match(packageJson.scripts.test, /demo-polish\.test\.js/);
});
