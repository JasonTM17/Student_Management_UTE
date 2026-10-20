const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// Execute the real name formatter and JSX consumers without mounting network hooks.
function loadAdvisorNameRenderers() {
  const source = read('src/components/dashboard/thesis/ThesisAdvisorDirectoryPage.tsx');
  const ast = ts.createSourceFile('advisor.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const functions = [];
  const gridDeclarations = [];
  let tableRenderer;
  let gridHeading;
  let modalTitle;
  const visit = (node) => {
    if (ts.isFunctionDeclaration(node) && /^getAdvisor(?:Full|Display)Name$/.test(node.name?.text)) functions.push(node.getText(ast));
    if (ts.isVariableDeclaration(node) && node.name.getText(ast) === 'tableColumns') {
      tableRenderer = node.initializer.elements[0].properties.find((property) => property.name?.getText(ast) === 'render').initializer;
    }
    if (ts.isCallExpression(node) && node.expression.getText(ast) === 'visibleLecturers.map') {
      for (const statement of node.arguments[0].body.statements) {
        if (ts.isVariableStatement(statement)) {
          for (const declaration of statement.declarationList.declarations) {
            if (['titlePrefix', 'fullName'].includes(declaration.name.getText(ast))) gridDeclarations.push(`const ${declaration.getText(ast)};`);
          }
        }
      }
    }
    if (ts.isJsxElement(node) && node.openingElement.tagName.getText(ast) === 'h3') gridHeading = node;
    if (ts.isJsxOpeningElement(node) && node.tagName.getText(ast) === 'Modal') {
      modalTitle = node.attributes.properties.find((property) => property.name?.getText(ast) === 'title')?.initializer?.expression;
    }
    ts.forEachChild(node, visit);
  };
  visit(ast);
  assert.ok(tableRenderer && gridHeading && modalTitle, 'all displayed name consumers are identified');
  const emitted = ts.transpileModule(`${functions.join('\n')}
    const AdvisorAvatar = () => null;
    module.exports = {
      raw: getAdvisorFullName,
      table: ${tableRenderer.getText(ast)},
      grid: (lec) => { ${gridDeclarations.join('\n')} return (${gridHeading.getText(ast)}); },
      modal: (selectedAdvisor) => (${modalTitle.getText(ast)}),
    };`, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX },
    fileName: 'advisor-names.tsx',
  }).outputText;
  const record = { exports: {} };
  Function('module', 'exports', 'require', emitted)(record, record.exports, require);
  return record.exports;
}

const advisorNameCases = [
  ['stored doctorate', 'TS.', 'TS. Nguyễn Minh', 'Đạo', 'TS. Nguyễn Minh Đạo'],
  ['stored associate professor', 'PGS.TS.', 'PGS.TS. Lê Văn', 'Vinh', 'PGS.TS. Lê Văn Vinh'],
  ['stored masters', 'ThS.', 'ThS. Nguyễn Quang', 'Huy', 'ThS. Nguyễn Quang Huy'],
  ['unprefixed name', 'TS.', 'Nguyễn Minh', 'Đạo', 'TS. Nguyễn Minh Đạo'],
  ['no separate title', null, 'TS. Nguyễn Minh', 'Đạo', 'TS. Nguyễn Minh Đạo'],
  ['name equals title', 'TS.', 'TS.', '', 'TS.'],
  ['trimmed title', ' TS. ', 'TS. Nguyễn Minh', 'Đạo', 'TS. Nguyễn Minh Đạo'],
  ['title substring inside a name', 'TS.', 'Nguyễn TS.', 'Đạo', 'TS. Nguyễn TS. Đạo'],
  ['prefix letters without name boundary', 'TS.', 'TS.Nguyễn Minh', 'Đạo', 'TS. TS.Nguyễn Minh Đạo'],
];

for (const [label, title, lastName, firstName, expected] of advisorNameCases) {
  test(`advisor displays one exact academic-title prefix: ${label}`, () => {
    const { renderToStaticMarkup } = require('react-dom/server');
    const renderers = loadAdvisorNameRenderers();
    const lecturer = { id: 'advisor-1', title, employeeId: 'GV-001', user: { lastName, firstName } };
    const table = renderToStaticMarkup(renderers.table(lecturer));
    const grid = renderToStaticMarkup(renderers.grid(lecturer));
    assert.ok(table.includes(`>${expected}</div>`), 'desktop table and its mobile card renderer display the same name');
    assert.ok(grid.includes(`title="${expected}"`), 'grid tooltip displays the same name');
    assert.ok(grid.includes(`>${expected}</h3>`), 'grid heading displays the same name');
    assert.equal(renderers.modal(lecturer), expected, 'detail modal displays the same name');
  });
}

test('advisor formatting preserves identifiers, absent names, accents and raw avatar/search input', () => {
  const { renderToStaticMarkup } = require('react-dom/server');
  const renderers = loadAdvisorNameRenderers();
  for (const [lecturer, expected] of [
    [{ id: 'a', employeeId: 'GV-001' }, 'GV-001'],
    [{ id: 'a', title: 'TS.', employeeId: 'GV-001' }, 'TS. GV-001'],
    [{ id: 'a' }, '—'],
    [{ id: 'a', user: { lastName: ' ', firstName: ' ' }, employeeId: 'GV-001' }, 'GV-001'],
  ]) {
    assert.ok(renderToStaticMarkup(renderers.table(lecturer)).includes(`>${expected}</div>`));
    assert.equal(renderers.modal(lecturer), expected);
  }
  const lecturer = { id: 'a', title: 'TS.', user: { lastName: 'TS. Nguyễn Minh', firstName: 'Đạo' } };
  assert.equal(renderers.raw(lecturer), 'TS. Nguyễn Minh Đạo');
  const source = read('src/components/dashboard/thesis/ThesisAdvisorDirectoryPage.tsx');
  assert.match(source, /const name = getAdvisorFullName\(lecturer\)/, 'avatar keeps raw input');
  assert.match(source, /const haystack = normalizeVietnameseText\(\s*\[\s*getAdvisorFullName\(lec\)/, 'search keeps raw input');
});

function readSourceTree(directory = path.join(root, 'src'), files = []) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolute = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      readSourceTree(absolute, files);
    } else if (/\.(ts|tsx|js|jsx|mjs|cjs)$/.test(entry.name)) {
      files.push(absolute);
    }
  }
  return files;
}

// ---------------------------------------------------------------------------
// Task 01 — the advisor directory invents no faculty
// ---------------------------------------------------------------------------

test('no fabricated advisor fixture survives anywhere in the source tree', () => {
  const offenders = readSourceTree()
    .filter((file) => fs.readFileSync(file, 'utf8').includes('FALLBACK_THESIS_ADVISORS'))
    .map((file) => path.relative(root, file));

  assert.deepEqual(
    offenders,
    [],
    `FALLBACK_THESIS_ADVISORS still referenced in: ${offenders.join(', ')}`,
  );

  // The invented lecturers carried demo mailboxes and demo employee ids; the
  // whole source tree must be free of them.
  const inventedMarkers = [
    'sang.ht@campuscore.demo',
    'thong.cm@campuscore.demo',
    'nguyet.lta@campuscore.demo',
    'LEC-DEMO-',
  ];
  const remaining = [];
  for (const file of readSourceTree()) {
    const source = fs.readFileSync(file, 'utf8');
    for (const marker of inventedMarkers) {
      if (source.includes(marker)) {
        remaining.push(`${path.relative(root, file)}: ${marker}`);
      }
    }
  }
  assert.deepEqual(remaining, [], `fabricated faculty markers remain: ${remaining.join(', ')}`);
});

test('the fabricated advisor data module is gone', () => {
  assert.equal(fs.existsSync(path.join(root, 'src/lib/thesis-advisors-data.ts')), false);
  // Reading it must fail rather than yield seeded advisors to any importer.
  assert.throws(() => read('src/lib/thesis-advisors-data.ts'), /ENOENT/);
});

test('the thesis workspace reports a failed supervisor directory instead of seeding one', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');

  // The old ternary and the old bare catch both substituted the fixture.
  assert.doesNotMatch(page, /response\.data\.length > 0 \? response\.data/);
  assert.doesNotMatch(page, /@\/lib\/thesis-advisors-data/);

  // The failure path is tracked separately from the empty path...
  assert.match(page, /const \[lecturersError, setLecturersError\] = useState\(''\)/);
  assert.match(page, /setLecturers\(\[\]\)/);
  assert.match(page, /setLecturersError\(messages\.thesis\.loadFailed\)/);
  // ...so an outage and a genuinely empty directory never read the same.
  assert.match(page, /lecturersError \? \(/);
  // And the reader gets the real failure with a retry.
  assert.match(
    page,
    /<ErrorState[\s\S]*?description=\{lecturersError\}[\s\S]*?onRetry=\{\(\) => void loadLecturers\(\)\}/,
  );
  assert.match(page, /<EmptyState[\s\S]*?Chưa có giảng viên hướng dẫn/);
});

test('the advisor directory page renders honest failure and empty states', () => {
  const page = read('src/components/dashboard/thesis/ThesisAdvisorDirectoryPage.tsx');

  assert.doesNotMatch(page, /FALLBACK_THESIS_ADVISORS|@\/lib\/thesis-advisors-data/);
  // No swallowed directory failure: the API result carries its own ok flag, so
  // a rejected fetch is never flattened into an empty success.
  assert.doesNotMatch(page, /lecturersApi\.getAll\(\{ limit: 100 \}\)\.catch\(\(\) => \(\{ data: \[\] \}\)\)/);
  assert.match(page, /\.catch\(\(\) => \(\{ ok: false as const, data: \[\] as Lecturer\[\] \}\)\)/);

  // Outage, unpopulated directory and no-match are three distinct surfaces.
  assert.match(page, /loadState === 'failed'/);
  assert.match(page, /<ErrorState[\s\S]*?onRetry=\{retryLoad\}/);
  assert.match(page, /lecturers\.length === 0 \? \(/);
  assert.match(page, /filteredLecturers\.length === 0 \? \(/);

  // No invented contact or research details for a real person: absent fields
  // render an em-dash, never a guess.
  assert.doesNotMatch(page, /@hcmute\.edu\.vn/);
  assert.doesNotMatch(page, /028 3896 8641/);
  assert.doesNotMatch(page, /Tòa nhà Trung tâm - HCMUTE/);
  assert.doesNotMatch(page, /ADVISOR_SPECIALIZATIONS/);
  assert.doesNotMatch(page, /Khoa Công nghệ Thông tin/);
  assert.match(page, /lec\.user\?\.email \|\| '—'/);
  assert.match(page, /lec\.office \|\| '—'/);
  assert.match(page, /selectedAdvisor\.phone \|\| '—'/);
  assert.match(page, /selectedAdvisor\.specialization \|\| '—'/);
});

test('the advisor directory consults only the real API surface', () => {
  const page = read('src/components/dashboard/thesis/ThesisAdvisorDirectoryPage.tsx');

  // The directory must cover every lecturer, not just the first 100-row page.
  assert.match(page, /fetchAllPages<Lecturer>\([\s\S]{0,120}?lecturersApi\.getAll/);
  assert.match(page, /departmentsApi\.getAll\(\{ limit: 50 \}\)/);
  assert.match(page, /thesisApi\.listRounds\(\)/);
  assert.match(page, /thesisApi\s*\.listTopics\(targetRound\.id, 'PUBLISHED'\)/);
  assert.doesNotMatch(page, /fetch\(['"]/);
});

test('the advisor directory keeps the local design primitives instead of a vendor kit', () => {
  const page = read('src/components/dashboard/thesis/ThesisAdvisorDirectoryPage.tsx');

  assert.match(page, /import \{ PageHeader, SectionEyebrow \} from '@\/components\/ui\/page-header'/);
  assert.match(page, /from '@\/components\/ui\/button'/);
  assert.match(page, /from '@\/components\/ui\/card'/);
  assert.match(page, /from '@\/components\/ui\/input'/);
  assert.match(page, /from '@\/components\/ui\/select'/);
  assert.match(page, /from '@\/components\/ui\/modal'/);
  assert.match(page, /EmptyState, ErrorState, LoadingState \} from '@\/components\/ui\/state-block'/);
  assert.match(page, /from '@\/components\/ui\/status'/);
  assert.match(page, /from 'lucide-react'/);

  // The table is a real semantic table, not a div grid wearing a header row.
  assert.match(page, /<caption className="sr-only">/);
  assert.match(page, /scope="col"/);
});

test('advisor quota credits topic.createdBy through the lecturer owner lookup', () => {
  const page = read('src/components/dashboard/thesis/ThesisAdvisorDirectoryPage.tsx');
  const ast = ts.createSourceFile('advisor.tsx', page, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let initializer;
  const visit = (node) => {
    if (ts.isVariableDeclaration(node) && node.name.getText(ast) === 'advisorWorkloadMap') {
      initializer = node.initializer;
    }
    ts.forEachChild(node, visit);
  };
  visit(ast);
  assert.ok(initializer, 'advisorWorkloadMap useMemo exists');
  const callback = initializer.arguments[0];
  const emitted = ts.transpileModule(`module.exports = (${callback.getText(ast)});`, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const record = { exports: {} };
  Function('Map', 'DEFAULT_MAX_TOPICS', 'topics', 'lecturers', 'module', emitted)(
    Map, 5,
    [
      { id: 't1', createdBy: 'user-1' },
      { id: 't2', createdBy: 'lec-2' },
      { id: 't3', createdBy: 'someone-not-a-lecturer' },
    ],
    [
      { id: 'lec-1', user: { id: 'user-1' } },
      { id: 'lec-2', user: { id: 'user-2' } },
    ],
    record);
  const map = record.exports();
  assert.equal(map.get('lec-1').topicCount, 1, 'createdBy resolves via user.id');
  assert.equal(map.get('lec-2').topicCount, 1, 'createdBy resolves via lecturer id');
  assert.equal(map.size, 2, 'a creator outside the directory earns no quota');
});

// ---------------------------------------------------------------------------
// Task 02 — one design language: the vendor UI kit is gone
// ---------------------------------------------------------------------------

test('no antd import remains in the source tree', () => {
  const offenders = readSourceTree()
    .filter((file) => /from 'antd'|from '@ant-design\//.test(fs.readFileSync(file, 'utf8')))
    .map((file) => path.relative(root, file));

  assert.deepEqual(offenders, [], `antd still imported by: ${offenders.join(', ')}`);
});

test('the manifest and the root layout no longer carry antd', () => {
  const manifest = JSON.parse(read('package.json'));
  const dependencyNames = Object.keys(manifest.dependencies || {}).concat(
    Object.keys(manifest.devDependencies || {}),
  );
  assert.equal(dependencyNames.includes('antd'), false);
  assert.equal(dependencyNames.includes('@ant-design/icons'), false);
  assert.equal(dependencyNames.includes('@ant-design/nextjs-registry'), false);

  assert.equal(fs.existsSync(path.join(root, 'src/components/providers/AntdProvider.tsx')), false);
  const layout = read('src/app/layout.tsx');
  assert.doesNotMatch(layout, /AntdProvider/);
});
