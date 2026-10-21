const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// Render the real module under test with its imports replaced by stubs; the
// same TypeScript transpile harness the announcement/thesis tests use.
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

// Execute the actual async page callback, supplying its hook/API dependencies.
function pageFunction(file, name, dependencies) {
  const source = read('src/' + file);
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

// Execute the real useEffect body that contains the marker text, with its
// closure dependencies injected, and return the cleanup function it yields.
function pageEffect(file, marker, dependencies) {
  const source = read('src/' + file);
  const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let arrow;
  function visit(node) {
    if (ts.isCallExpression(node)
        && node.expression.getText(tree) === 'useEffect'
        && node.arguments.length > 0
        && ts.isArrowFunction(node.arguments[0])
        && node.arguments[0].getText(tree).includes(marker)) {
      arrow = node.arguments[0];
    }
    ts.forEachChild(node, visit);
  }
  visit(tree);
  assert.ok(arrow, `useEffect containing ${marker} exists`);
  const output = ts.transpileModule(`const __effect = ${arrow.getText(tree)};`, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
  }).outputText;
  return Function(...Object.keys(dependencies), `${output}; return __effect;`)(
    ...Object.values(dependencies),
  );
}

const flush = async () => {
  for (let i = 0; i < 12; i += 1) await new Promise((resolve) => setImmediate(resolve));
};

const recorder = () => {
  const calls = [];
  const record = (name) => (value) => { calls.push([name, value]); };
  return { calls, record };
};

// ---------- thesisApi.listScores must propagate request failures ----------

function loadThesisApi(getImpl) {
  return loadModule('src/lib/thesis-api.ts', {
    '@/lib/api': {
      __esModule: true,
      default: { get: getImpl },
      API_BASE_URL: 'http://api.test/',
      createRequestId: () => 'req-test',
      refreshSessionSingleFlight: async () => {},
    },
    '@/lib/assistant-stream-helpers': { CANCEL_REQUEST_TIMEOUT_MS: 1_000 },
    '@/lib/assistant-stream': {
      createAssistantSseParser: () => ({}),
      parseAssistantStreamEvent: () => ({}),
      AssistantStreamOrder: {},
    },
  }).thesisApi;
}

for (const status of [403, 404, 500]) {
  test(`listScores rejects a ${status} response instead of reporting an empty score list`, async () => {
    const failure = Object.assign(new Error(`HTTP ${status}`), { response: { status } });
    const thesisApi = loadThesisApi(async () => { throw failure; });
    await assert.rejects(
      thesisApi.listScores('council-1', 'topic-1'),
      (err) => err === failure || err.response?.status === status,
    );
  });
}

test('listScores still resolves a genuinely empty score list', async () => {
  const thesisApi = loadThesisApi(async () => ({ data: [] }));
  assert.deepEqual(await thesisApi.listScores('council-1', 'topic-1'), []);
});

// ---------- loadCouncilDetails honors the current-request predicate ----------

function councilDetailDeps(api, calls) {
  const { record } = calls;
  return {
    isAdmin: true,
    myLecturerId: 'me',
    thesisApi: api,
    setCouncilScores: record('councilScores'),
    setCouncilScoreErrors: record('councilScoreErrors'),
    setTopicSupervisors: record('topicSupervisors'),
    setTopicReports: record('topicReports'),
    setTopicReportErrors: record('topicReportErrors'),
    setDraftScores: record('draftScores'),
    setDraftComments: record('draftComments'),
  };
}

test('a stale council detail load issues no requests and writes no state', async () => {
  const calls = recorder();
  const requested = [];
  const load = pageFunction('app/dashboard/thesis/page.tsx', 'loadCouncilDetails',
    councilDetailDeps({
      listScores: async () => { requested.push('scores'); return []; },
      listSupervisors: async () => { requested.push('supervisors'); return []; },
      getTopicReport: async () => { requested.push('report'); return null; },
    }, calls));
  await load(
    [{ id: 'council', topicIds: ['topic-1', 'topic-2'], members: [] }],
    () => false,
  );
  assert.deepEqual(requested, [], 'no request may fire once the load is stale');
  assert.deepEqual(calls.calls, [], 'no state write may happen once the load is stale');
});

test('a load that goes stale mid-flight stops before writing or requesting more', async () => {
  const calls = recorder();
  const requested = [];
  let release;
  const gate = new Promise((resolve) => { release = resolve; });
  let current = true;
  const load = pageFunction('app/dashboard/thesis/page.tsx', 'loadCouncilDetails',
    councilDetailDeps({
      listScores: async () => { requested.push('scores'); await gate; return [{ lecturerId: 'me', component: 'DEFENSE', score: 8 }]; },
      listSupervisors: async () => { requested.push('supervisors'); return []; },
      getTopicReport: async () => { requested.push('report'); return null; },
    }, calls));
  const pending = load(
    [{ id: 'council', topicIds: ['topic-1', 'topic-2'], members: [] }],
    () => current,
  );
  // First topic's requests are in flight; switching rounds now must prevent
  // both the state write and the second topic's requests.
  current = false;
  release();
  await pending;
  assert.equal(requested.filter((name) => name === 'scores').length, 1,
    'the stale load must not request the next topic');
  assert.deepEqual(calls.calls, [], 'writes after an await must check the predicate first');
});

test('a current council detail load still records scores, supervisors and reports', async () => {
  const calls = recorder();
  const load = pageFunction('app/dashboard/thesis/page.tsx', 'loadCouncilDetails',
    councilDetailDeps({
      listScores: async () => [{ lecturerId: 'me', component: 'DEFENSE', score: 9, comment: 'ok' }],
      listSupervisors: async () => [{ lecturerId: 'sup-1' }],
      getTopicReport: async () => ({ groupId: 'g-1' }),
    }, calls));
  await load([{ id: 'council', topicIds: ['topic'], members: [] }]);
  const names = calls.calls.map(([name]) => name);
  for (const expected of ['councilScores', 'councilScoreErrors', 'topicSupervisors', 'topicReports', 'topicReportErrors']) {
    assert.ok(names.includes(expected), `${expected} was recorded`);
  }
});

// ---------- the round effect resets council state and invalidates loads ----------

function effectDeps(overrides) {
  const calls = recorder();
  const loadCouncilDetailsCalls = [];
  const deps = {
    selectedRoundId: '',
    selectedRoundRef: { current: '' },
    topicRequestRef: { current: 0 },
    councilRequestRef: { current: 0 },
    repositoryRequestRef: { current: 0 },
    isStudent: false,
    canLoadStaffRelations: true,
    messages: { thesis: { loadFailed: 'LOAD_FAILED' } },
    refreshTopics: async () => {},
    thesisApi: {
      listGroups: async () => [],
      listCouncils: async () => [],
      listRoundRepository: async () => [],
    },
    loadCouncilDetails: async (...args) => { loadCouncilDetailsCalls.push(args); },
    setError: calls.record('error'),
    setIsRepositoryLoading: calls.record('isRepositoryLoading'),
    setRepositoryReports: calls.record('repositoryReports'),
    setRepositoryError: calls.record('repositoryError'),
    ...overrides,
  };
  for (const name of [
    'setTopics', 'setGroups', 'setViewingTopic', 'setActionError', 'setActionSuccess',
    'setCouncils', 'setCouncilScores', 'setCouncilScoreErrors', 'setDraftScores',
    'setDraftComments', 'setTopicSupervisors', 'setTopicReports', 'setTopicReportErrors',
    'setCouncilLoadError',
  ]) {
    if (!(name in deps)) deps[name] = calls.record(name);
  }
  return { deps, calls, loadCouncilDetailsCalls };
}

test('selecting no round clears every council-specific map and draft', () => {
  const { deps, calls } = effectDeps({});
  pageEffect('app/dashboard/thesis/page.tsx', 'loadWorkspace', deps)();
  const cleared = new Map(calls.calls);
  for (const name of ['setCouncils', 'setCouncilScores', 'setCouncilScoreErrors',
    'setDraftScores', 'setDraftComments', 'setTopicSupervisors',
    'setTopicReports', 'setTopicReportErrors']) {
    assert.deepEqual(cleared.get(name), name === 'setCouncils' ? [] : {},
      `${name} must be reset when the round changes`);
  }
  assert.equal(cleared.get('setCouncilLoadError'), '', 'a previous council load failure must be cleared');
});

test('the council detail load is invalidated by the effect cleanup', async () => {
  const { deps, loadCouncilDetailsCalls } = effectDeps({
    selectedRoundId: 'round-1',
    selectedRoundRef: { current: 'round-1' },
    thesisApi: {
      listGroups: async () => [],
      listCouncils: async () => [{ id: 'council', topicIds: ['topic'], members: [] }],
      listRoundRepository: async () => [],
    },
  });
  const cleanup = pageEffect('app/dashboard/thesis/page.tsx', 'loadWorkspace', deps)();
  await flush();
  assert.equal(loadCouncilDetailsCalls.length, 1, 'council details were requested for the round');
  const isCurrent = loadCouncilDetailsCalls[0][1];
  assert.equal(typeof isCurrent, 'function', 'the loader receives a current-request predicate');
  assert.equal(isCurrent(), true, 'the predicate is live while the round stays selected');
  cleanup();
  assert.equal(isCurrent(), false, 'cleanup must invalidate the in-flight load');
});

test('a council listing failure surfaces a localized error without failing the workspace', async () => {
  const { deps, calls, loadCouncilDetailsCalls } = effectDeps({
    selectedRoundId: 'round-1',
    selectedRoundRef: { current: 'round-1' },
    thesisApi: {
      listGroups: async () => [{ id: 'group-1' }],
      listCouncils: async () => { throw new Error('500'); },
      listRoundRepository: async () => [{ id: 'report-1' }],
    },
  });
  pageEffect('app/dashboard/thesis/page.tsx', 'loadWorkspace', deps)();
  await flush();
  const cleared = new Map(calls.calls);
  assert.equal(cleared.get('setCouncilLoadError'), 'LOAD_FAILED',
    'the failure reads as a load error, not as an empty council list');
  assert.deepEqual(cleared.get('setCouncils'), []);
  assert.equal(loadCouncilDetailsCalls.length, 0, 'no detail load runs for a failed listing');
  assert.equal(cleared.get('setError'), undefined, 'the rest of the workspace is not marked failed');
});

// ---------- reloadCouncils gives the failure a retry path ----------

test('reloadCouncils retries the listing and hands a live predicate to the detail loader', async () => {
  const calls = recorder();
  const loadCouncilDetailsCalls = [];
  let fail = true;
  const councilRequestRef = { current: 0 };
  const load = pageFunction('app/dashboard/thesis/page.tsx', 'reloadCouncils', {
    selectedRoundId: 'round-1',
    selectedRoundRef: { current: 'round-1' },
    councilRequestRef,
    canLoadStaffRelations: true,
    messages: { thesis: { loadFailed: 'LOAD_FAILED' } },
    thesisApi: {
      listCouncils: async () => {
        if (fail) throw new Error('500');
        return [{ id: 'council', topicIds: ['topic'], members: [] }];
      },
    },
    setCouncilLoadError: calls.record('setCouncilLoadError'),
    setCouncils: calls.record('setCouncils'),
    loadCouncilDetails: async (...args) => { loadCouncilDetailsCalls.push(args); },
  });
  await load();
  assert.equal(new Map(calls.calls).get('setCouncilLoadError'), 'LOAD_FAILED');
  fail = false;
  await load();
  const cleared = new Map(calls.calls);
  assert.equal(cleared.get('setCouncilLoadError'), '', 'a successful retry clears the failure');
  assert.deepEqual(cleared.get('setCouncils'), [{ id: 'council', topicIds: ['topic'], members: [] }]);
  assert.equal(loadCouncilDetailsCalls.length, 1, 'the retry reloads council details');
  const isCurrent = loadCouncilDetailsCalls[0][1];
  assert.equal(isCurrent(), true);
  councilRequestRef.current += 1;
  assert.equal(isCurrent(), false, 'a newer request invalidates the older retry');
});

test('a successful initial council listing re-clears the error after writing councils', async () => {
  const { deps, calls } = effectDeps({
    selectedRoundId: 'round-1',
    selectedRoundRef: { current: 'round-1' },
    thesisApi: {
      listGroups: async () => [],
      listCouncils: async () => [{ id: 'council', topicIds: [], members: [] }],
      listRoundRepository: async () => [],
    },
  });
  pageEffect('app/dashboard/thesis/page.tsx', 'loadWorkspace', deps)();
  await flush();
  const councilWriteIndex = calls.calls.findIndex(
    ([name, value]) => name === 'setCouncils' && Array.isArray(value) && value.length === 1);
  const errorClears = calls.calls
    .map(([name], index) => (name === 'setCouncilLoadError' ? index : -1))
    .filter((index) => index > -1);
  assert.ok(councilWriteIndex > -1, 'the listing wrote the council list');
  assert.ok(errorClears[errorClears.length - 1] > councilWriteIndex,
    'the success path re-clears the error after writing councils');
});

// A retry bumps the shared request identity; a deferred listing from the
// initial effect must then be inert whether it resolves or rejects.
function staleListingSetup() {
  const calls = recorder();
  const councilRequestRef = { current: 0 };
  const selectedRoundRef = { current: 'round-1' };
  const cOld = [{ id: 'council-old', topicIds: [], members: [] }];
  const cNew = [{ id: 'council-new', topicIds: [], members: [] }];
  let releaseOld;
  let rejectOld;
  const oldList = new Promise((resolve, reject) => { releaseOld = resolve; rejectOld = reject; });
  let listCalls = 0;
  const thesisApi = {
    listGroups: async () => [],
    listRoundRepository: async () => [],
    listCouncils: () => {
      listCalls += 1;
      return listCalls === 1 ? oldList : Promise.resolve(cNew);
    },
  };
  const shared = {
    setCouncils: calls.record('setCouncils'),
    setCouncilLoadError: calls.record('setCouncilLoadError'),
  };
  const { deps, loadCouncilDetailsCalls } = effectDeps({
    selectedRoundId: 'round-1',
    selectedRoundRef,
    councilRequestRef,
    thesisApi,
    ...shared,
  });
  pageEffect('app/dashboard/thesis/page.tsx', 'loadWorkspace', deps)();
  const reload = pageFunction('app/dashboard/thesis/page.tsx', 'reloadCouncils', {
    selectedRoundId: 'round-1',
    selectedRoundRef,
    councilRequestRef,
    canLoadStaffRelations: true,
    messages: { thesis: { loadFailed: 'LOAD_FAILED' } },
    thesisApi,
    ...shared,
    loadCouncilDetails: deps.loadCouncilDetails,
  });
  return {
    calls, cOld, cNew, releaseOld, rejectOld, reload, loadCouncilDetailsCalls,
    listCalls: () => listCalls,
  };
}

test('a stale initial council listing cannot overwrite a newer retry result', async () => {
  const setup = staleListingSetup();
  await flush();
  assert.equal(setup.listCalls(), 1, 'the initial listing is still in flight');
  await setup.reload();
  assert.equal(setup.listCalls(), 2, 'the retry issued a fresh listing');
  setup.releaseOld(setup.cOld);
  await flush();
  const councilWrites = setup.calls.calls
    .filter(([name]) => name === 'setCouncils')
    .map(([, value]) => value);
  assert.deepEqual(councilWrites[councilWrites.length - 1], setup.cNew,
    'the last council write is the retry result');
  assert.ok(!councilWrites.includes(setup.cOld),
    'the stale listing must not overwrite the retry result');
  assert.equal(setup.loadCouncilDetailsCalls.length, 1, 'only the retry loaded details');
});

test('a stale initial listing failure cannot surface after a newer retry succeeds', async () => {
  const setup = staleListingSetup();
  await flush();
  await setup.reload();
  setup.rejectOld(new Error('500'));
  await flush();
  const councilWrites = setup.calls.calls
    .filter(([name]) => name === 'setCouncils')
    .map(([, value]) => value);
  assert.deepEqual(councilWrites[councilWrites.length - 1], setup.cNew,
    'the stale failure must not clear the retry result');
  const errorWrites = setup.calls.calls
    .filter(([name]) => name === 'setCouncilLoadError')
    .map(([, value]) => value);
  assert.equal(errorWrites[errorWrites.length - 1], '',
    'the stale failure must not mark the workspace failed after retry success');
});

// ---------- CouncilDefenseWorkspace finalize gating ----------

const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const uiStubs = {
  '@/components/ui/button': {
    Button: (props) => React.createElement('button', {
      type: 'button',
      'data-disabled': props.disabled ? 'yes' : 'no',
      'data-variant': props.variant || 'default',
    }, props.children),
  },
  '@/components/ui/card': {
    Card: (p) => React.createElement('div', null, p.children),
    CardContent: (p) => React.createElement('div', null, p.children),
    CardDescription: (p) => React.createElement('p', null, p.children),
    CardHeader: (p) => React.createElement('div', null, p.children),
    CardTitle: (p) => React.createElement('h3', null, p.children),
  },
  '@/components/ui/input': {
    Input: (props) => React.createElement('input', {
      disabled: !!props.disabled, value: props.value ?? '', readOnly: true,
    }),
  },
  '@/components/ui/state-block': {
    ErrorState: (p) => React.createElement('div', { role: 'alert' },
      p.title, p.description,
      p.onRetry ? React.createElement('button', { type: 'button' }, 'RETRY_ACTION') : null),
    EmptyState: () => null,
    LoadingState: () => null,
  },
  '@/lib/utils': { cn: (...values) => values.filter(Boolean).join(' ') },
};

function loadWorkspaceComponent() {
  const helpers = loadModule('src/features/thesis/components/thesis-helpers.tsx');
  const thesisText = loadModule('src/lib/thesis-text.ts');
  return loadModule('src/features/thesis/components/CouncilDefenseWorkspace.tsx', {
    ...uiStubs,
    '@/i18n/messages': {},
    '@/types/api': {},
    '@/lib/thesis-api': {},
    '@/features/thesis/components/thesis-helpers': helpers,
    '@/lib/thesis-text': thesisText,
  }).default;
}

function loadMessages() {
  return loadModule('src/i18n/messages.ts', {
    '@/i18n/config': {},
    '@/i18n/messages-thesis-workflow': loadModule('src/i18n/messages-thesis-workflow.ts'),
    '@/i18n/messages-dean-overview': loadModule('src/i18n/messages-dean-overview.ts'),
  }).en;
}

function finalizeProps(overrides) {
  return {
    messages: loadMessages(),
    councils: [{
      id: 'council',
      name: 'Council A',
      topicIds: ['topic'],
      members: [
        { lecturerId: 'chair', memberRole: 'CHAIR' },
        { lecturerId: 'member-2', memberRole: 'MEMBER' },
        { lecturerId: 'member-3', memberRole: 'MEMBER' },
      ],
    }],
    myLecturerId: 'chair',
    topics: [{ id: 'topic', title: 'Topic', finalScore: null }],
    lecturers: [],
    councilScores: {},
    councilScoreErrors: {},
    topicSupervisors: { topic: [] },
    topicReports: {},
    topicReportErrors: {},
    onRetryTopicReport: () => {},
    draftScores: {},
    draftComments: {},
    isActionPending: false,
    showProfileClaimNotice: false,
    profileClaimMissingLabel: '',
    onScoreDraftChange: () => {},
    onCommentDraftChange: () => {},
    onSubmitScore: () => {},
    onFinalizeScore: () => {},
    onDownloadReport: () => {},
    ...overrides,
  };
}

const defenseScore = (lecturerId) => ({ lecturerId, component: 'DEFENSE', score: 8 });

function renderFinalizeHtml(props) {
  const Workspace = loadWorkspaceComponent();
  return renderToStaticMarkup(React.createElement(Workspace, props));
}

function renderFinalizeTag(props) {
  const html = renderFinalizeHtml(props);
  const labelIdx = html.indexOf('Finalize');
  assert.ok(labelIdx > -1, `finalize control should render, got: ${html.slice(0, 400)}`);
  const tagStart = html.lastIndexOf('<button', labelIdx);
  return html.slice(tagStart, html.indexOf('>', tagStart) + 1);
}

test('finalize stays disabled until every eligible member has a DEFENSE row', () => {
  const tag = renderFinalizeTag(finalizeProps({
    councilScores: { 'council:topic': [defenseScore('chair'), defenseScore('member-2')] },
  }));
  assert.match(tag, /data-disabled="yes"/, '2 of 3 eligible DEFENSE scores cannot finalize');
});

test('finalize ignores GVPB and non-member score rows when counting', () => {
  const padded = finalizeProps({
    councilScores: { 'council:topic': [
      defenseScore('chair'),
      defenseScore('member-2'),
      { lecturerId: 'member-3', component: 'GVPB', score: 7 },
      { lecturerId: 'outsider', component: 'DEFENSE', score: 9 },
    ] },
  });
  const tag = renderFinalizeTag(padded);
  assert.match(tag, /data-disabled="yes"/,
    'GVPB and non-member rows must not count toward the eligible DEFENSE total');
});

test('finalize requires at least three eligible members even when all scored', () => {
  const tag = renderFinalizeTag(finalizeProps({
    topicSupervisors: { topic: ['member-3'] },
    councilScores: { 'council:topic': [defenseScore('chair'), defenseScore('member-2')] },
  }));
  assert.match(tag, /data-disabled="yes"/,
    'two eligible graders are below the three-member minimum');
});

test('an under-minimum council reports the council-size error, never the ready copy', () => {
  const messages = loadMessages();
  const html = renderFinalizeHtml(finalizeProps({
    topicSupervisors: { topic: ['member-3'] },
    councilScores: { 'council:topic': [defenseScore('chair'), defenseScore('member-2')] },
  }));
  assert.match(html, /data-disabled="yes"/, 'two eligible graders cannot finalize');
  assert.ok(html.includes(messages.common.campusErrors.codes.COUNCIL_SIZE_INVALID),
    'the status explains the council-size failure');
  assert.equal(html.includes(messages.thesis.grading.readyToFinalize), false,
    'the ready copy must not render while the council is under the minimum');
});

test('zero eligible members reports the council-size error, never the ready copy', () => {
  const messages = loadMessages();
  const html = renderFinalizeHtml(finalizeProps({
    topicSupervisors: { topic: ['chair', 'member-2', 'member-3'] },
    councilScores: { 'council:topic': [
      defenseScore('chair'), defenseScore('member-2'), defenseScore('member-3'),
    ] },
  }));
  assert.match(html, /data-disabled="yes"/, 'zero eligible members cannot finalize');
  assert.ok(html.includes(messages.common.campusErrors.codes.COUNCIL_SIZE_INVALID),
    'the status explains the council-size failure');
  assert.equal(html.includes(messages.thesis.grading.readyToFinalize), false,
    'the ready copy must not render for an empty eligible set');
});

test('duplicate DEFENSE rows from one member do not enable finalize', () => {
  const messages = loadMessages();
  const html = renderFinalizeHtml(finalizeProps({
    councilScores: { 'council:topic': [
      defenseScore('chair'),
      { ...defenseScore('chair'), score: 9 },
      defenseScore('member-2'),
    ] },
  }));
  assert.match(html, /data-disabled="yes"/,
    'three score rows covering only two unique members cannot finalize');
  assert.ok(html.includes(
    messages.thesis.grading.waitingGrades.replace('{submitted}', '2').replace('{total}', '3')),
    'the waiting copy counts unique eligible submissions');
  assert.equal(html.includes(messages.thesis.grading.readyToFinalize), false);
});

test('a supervising chair cannot finalize the topic', () => {
  const tag = renderFinalizeTag(finalizeProps({
    topicSupervisors: { topic: ['chair'] },
    councilScores: { 'council:topic': [defenseScore('member-2'), defenseScore('member-3')] },
  }));
  assert.match(tag, /data-disabled="yes"/, 'the chair must be an eligible grader');
});

test('a score-load failure hides the finalize control instead of inviting it', () => {
  const Workspace = loadWorkspaceComponent();
  const html = renderToStaticMarkup(React.createElement(Workspace, finalizeProps({
    councilScoreErrors: { 'council:topic': true },
  })));
  assert.equal(html.includes('Finalize &amp;'), false,
    'the finalize button stays hidden while the score list failed to load');
});

test('finalize enables once every one of three eligible members has a DEFENSE row', () => {
  const messages = loadMessages();
  const html = renderFinalizeHtml(finalizeProps({
    councilScores: { 'council:topic': [
      defenseScore('chair'), defenseScore('member-2'), defenseScore('member-3'),
    ] },
  }));
  const labelIdx = html.indexOf('Finalize');
  const tag = html.slice(html.lastIndexOf('<button', labelIdx), html.indexOf('>', labelIdx) + 1);
  assert.match(tag, /data-disabled="no"/, 'all eligible DEFENSE rows present allows finalize');
  assert.ok(html.includes(messages.thesis.grading.readyToFinalize),
    'the ready copy renders only once the council can actually finalize');
});

test('a council listing failure renders the localized alert with a retry control', () => {
  const messages = loadMessages();
  const html = renderFinalizeHtml(finalizeProps({
    councilLoadError: messages.thesis.loadFailed,
    onRetryCouncils: () => {},
  }));
  assert.ok(html.includes('role="alert"'), 'the council failure renders an alert');
  assert.ok(html.includes(messages.thesis.loadFailed), 'the alert uses the localized load failure message');
  assert.ok(html.includes('RETRY_ACTION'), 'the alert exposes the retry control');
});

test('the rendered retry control invokes the page retry handler', () => {
  const Workspace = loadWorkspaceComponent();
  let retried = false;
  const tree = Workspace(finalizeProps({
    councilLoadError: 'LOAD_FAILED',
    onRetryCouncils: () => { retried = true; },
  }));
  const findOnRetry = (node) => {
    if (!node || typeof node !== 'object') return null;
    if (Array.isArray(node)) {
      for (const child of node) {
        const hit = findOnRetry(child);
        if (hit) return hit;
      }
      return null;
    }
    if (typeof node.props?.onRetry === 'function') return node.props.onRetry;
    return findOnRetry(node.props?.children);
  };
  const onRetry = findOnRetry(tree);
  assert.equal(typeof onRetry, 'function', 'the error state element carries a retry handler');
  onRetry();
  assert.equal(retried, true, 'the rendered retry invokes the page retry handler');
});
