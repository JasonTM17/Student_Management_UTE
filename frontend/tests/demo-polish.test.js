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
  // Follow-ups are still panel-computed (never parsed from model markdown),
  // and now adapt to the domain of the last grounded answer.
  assert.match(panelSource, /followUps=\{followUps\}/);
  assert.match(panelSource, /followUpsByDomain/);
  assert.doesNotMatch(panelSource, /dangerouslySetInnerHTML/);
  const messagesSource = fs.readFileSync(
    path.join(root, 'src/components/assistant/AssistantMessages.tsx'),
    'utf8',
  );
  assert.match(messagesSource, /lastAssistantIndex/);
  assert.match(messagesSource, /onFollowUp\(suggestion\)/);
});

test('conduct certificate shortcut keeps a visible keyboard focus ring', () => {
  const conduct = fs.readFileSync(
    path.join(root, 'src/app/dashboard/conduct/page.tsx'),
    'utf8',
  );
  assert.match(
    conduct,
    /className="inline-flex min-h-8 items-center text-primary font-medium cursor-pointer hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"/,
  );
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

test('document title hook lets the next route title win once its shell unmounts', () => {
  // Fake React: layout-effect cleanups run inside the commit that unmounts the
  // shell, passive-effect cleanups only afterwards. MutationObserver callbacks
  // fire in between, as microtasks after the DOM commit — the window in which a
  // still-connected observer used to write the old title back over /login.
  const layoutEffects = [];
  const passiveEffects = [];
  const fakeReact = {
    useLayoutEffect: (effect) => layoutEffects.push(effect),
    useEffect: (effect) => passiveEffects.push(effect),
  };
  class FakeMutationObserver {
    constructor(callback) {
      this.callback = callback;
      this.active = false;
      observers.push(this);
    }
    observe() { this.active = true; }
    disconnect() { this.active = false; }
  }
  const observers = [];
  const notify = () => observers.filter((o) => o.active).forEach((o) => o.callback([]));
  const doc = { title: 'CampusUTE', head: {} };
  const saved = { window: global.window, document: global.document, MutationObserver: global.MutationObserver };
  global.window = {};
  global.document = doc;
  global.MutationObserver = FakeMutationObserver;
  try {
    const source = fs.readFileSync(path.join(root, 'src/lib/use-document-title.ts'), 'utf8');
    const output = ts.transpileModule(source, {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    }).outputText;
    const moduleRecord = { exports: {} };
    Function('module', 'exports', 'require', output)(
      moduleRecord,
      moduleRecord.exports,
      (name) => (name === 'react' ? fakeReact : require(name)),
    );

    moduleRecord.exports.useDocumentTitle('Đang đăng xuất');
    const layoutCleanups = layoutEffects.map((effect) => effect());
    const passiveCleanups = passiveEffects.map((effect) => effect());
    assert.equal(doc.title, 'Đang đăng xuất | CampusUTE');

    // While mounted, the hook still re-asserts over the root metadata title.
    doc.title = 'Bảng điều khiển học vụ | CampusUTE';
    notify();
    assert.equal(doc.title, 'Đang đăng xuất | CampusUTE');

    // Sign-out navigates to /login: the shell unmounts in the same commit that
    // installs the login metadata title.
    layoutCleanups.forEach((cleanup) => cleanup?.());
    doc.title = 'Đăng nhập | CampusUTE';
    notify();
    passiveCleanups.forEach((cleanup) => cleanup?.());
    assert.equal(doc.title, 'Đăng nhập | CampusUTE');
  } finally {
    for (const [key, value] of Object.entries(saved)) {
      if (value === undefined) delete global[key];
      else global[key] = value;
    }
  }
});

test('demo polish regression tests stay registered in the test script', () => {
  const packageJson = JSON.parse(
    fs.readFileSync(path.join(root, 'package.json'), 'utf8'),
  );
  assert.match(packageJson.scripts.test, /demo-polish\.test\.js/);
});
