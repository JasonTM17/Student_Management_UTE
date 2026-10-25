const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// Execute the actual async page callback, supplying its hook/API dependencies.
function callback(file, name, dependencies) {
  const source = fs.readFileSync(path.join(__dirname, '../src', file), 'utf8');
  const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let declaration;
  function visit(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(tree) === name) declaration = node;
    ts.forEachChild(node, visit);
  }
  visit(tree);
  assert.ok(declaration, `page callback ${name} exists`);
  const output = ts.transpileModule(`const ${declaration.getText(tree)};`, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
  }).outputText;
  return Function(...Object.keys(dependencies), 'useCallback', `${output}; return ${name};`)(
    ...Object.values(dependencies), fn => fn,
  );
}

test('lecturer semester fetch catches failure and a successful retry clears the warning', async () => {
  let fail = true;
  let error = '';
  let semesters = [];
  const load = callback('app/dashboard/lecturer/grades/page.tsx', 'fetchSemesters', {
    semestersApi: { getAll: async () => { if (fail) throw new Error('503'); return { data: [{ id: 'term' }] }; } },
    messages: { lecturerGrades: { errors: { semestersFailed: 'Semesters unavailable' } } },
    setSemesters: value => { semesters = value; },
    setSemesterError: value => { error = value; },
  });
  await assert.doesNotReject(load());
  assert.equal(error, 'Semesters unavailable');
  fail = false;
  await load();
  assert.equal(error, '');
  assert.deepEqual(semesters, [{ id: 'term' }]);
});

test('council report 200 then 204 clears stale document links', async () => {
  let report = { groupId: 'group', title: 'Report' };
  let reports = {};
  let errors = {};
  const load = callback('app/dashboard/thesis/page.tsx', 'loadCouncilDetails', {
    isAdmin: true, myLecturerId: 'lecturer',
    thesisApi: { listScores: async () => [], listSupervisors: async () => [], getTopicReport: async () => report },
    setCouncilScores: () => {}, setCouncilScoreErrors: () => {}, setTopicSupervisors: () => {},
    topicSupervisorRowsRef: { current: {} },
    setTopicReports: update => { reports = update(reports); },
    setTopicReportErrors: update => { errors = update(errors); },
    setDraftScores: () => {}, setDraftComments: () => {},
  });
  const councils = [{ id: 'council', topicIds: ['topic'] }];
  await load(councils);
  assert.equal(reports.topic.title, 'Report');
  report = null;
  await load(councils);
  assert.equal(reports.topic, null);
  assert.equal(errors.topic, false);
});

test('council report failure is distinct from no report, and retry restores it', async () => {
  let fail = true;
  let reports = {};
  let errors = {};
  const load = callback('app/dashboard/thesis/page.tsx', 'loadCouncilDetails', {
    isAdmin: true, myLecturerId: 'lecturer',
    thesisApi: { listScores: async () => [], listSupervisors: async () => [], getTopicReport: async () => {
      if (fail) throw new Error('500'); return { groupId: 'group' };
    } },
    setCouncilScores: () => {}, setCouncilScoreErrors: () => {}, setTopicSupervisors: () => {},
    topicSupervisorRowsRef: { current: {} },
    setTopicReports: update => { reports = update(reports); },
    setTopicReportErrors: update => { errors = update(errors); },
    setDraftScores: () => {}, setDraftComments: () => {},
  });
  const councils = [{ id: 'council', topicIds: ['topic'] }];
  await load(councils);
  assert.equal(errors.topic, true);
  assert.equal(reports.topic, null);
  fail = false;
  await load(councils);
  assert.equal(errors.topic, false);
  assert.equal(reports.topic.groupId, 'group');
});
