const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// CROSS_ROLE_SRC_ROOT lets the same regressions run against a snapshot of the
// pre-fix sources (e.g. git show HEAD:...) without touching the worktree —
// that is the recorded red pass for this file.
const root = process.env.CROSS_ROLE_SRC_ROOT
  ? path.resolve(process.env.CROSS_ROLE_SRC_ROOT)
  : path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// Render the real module under test with its imports replaced by stubs; the
// same TypeScript transpile harness the thesis council tests use.
function loadModule(relative, aliases = {}) {
  const output = ts.transpileModule(read(relative), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
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

// Execute an actual page callback, supplying its hook/API dependencies. Ref
// objects are injected as plain { current } stand-ins for useRef.
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
    ...Object.values(dependencies), (fn) => fn,
  );
}

// Extract an actual useEffect body (matched by a marker inside the call) and
// return its setup function — the returned cleanup is then invoked directly.
function pageEffect(file, marker, dependencies = {}) {
  const source = read('src/' + file);
  const tree = ts.createSourceFile(file, source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let call;
  function visit(node) {
    if (ts.isCallExpression(node)
        && node.expression.getText(tree) === 'useEffect'
        && node.getText(tree).includes(marker)) call = node;
    ts.forEachChild(node, visit);
  }
  visit(tree);
  assert.ok(call, `useEffect containing "${marker}" exists`);
  const output = ts.transpileModule(call.getText(tree), {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.CommonJS },
  }).outputText;
  let captured;
  const useEffectStub = (fn) => { captured = fn; };
  Function(...Object.keys(dependencies), 'useEffect', output)(
    ...Object.values(dependencies), useEffectStub,
  );
  return captured;
}

const flush = async () => {
  for (let i = 0; i < 12; i += 1) await new Promise((resolve) => setImmediate(resolve));
};

const recorder = () => {
  const calls = [];
  const record = (name) => (value) => { calls.push([name, value]); };
  return { calls, record };
};

const deferred = () => {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
};

// ---------- enrollment export forwards the committed search ----------

function loadEnrollmentsApi(getImpl) {
  const noop = () => {};
  const apiInstance = {
    get: getImpl,
    post: async () => ({}),
    put: async () => ({}),
    patch: async () => ({}),
    delete: async () => ({}),
    interceptors: { request: { use: noop }, response: { use: noop } },
  };
  class AxiosError extends Error {}
  return loadModule('src/lib/api.ts', {
    axios: {
      __esModule: true,
      default: { create: () => apiInstance, isAxiosError: () => false },
      AxiosError,
    },
    '@/i18n/paths': { addLocalePrefix: (p) => p, stripLocaleFromPathname: (p) => p },
    '@/lib/public-api-url': { resolvePublicApiBaseUrl: () => '/api/v1' },
    '@/lib/session-hint': { CSRF_COOKIE_NAME: 'cc_csrf', isLogoutInProgress: () => false },
    '@/lib/session-sync': { publishSessionChange: noop },
  }).enrollmentsApi;
}

test('enrollments exportCsv accepts and forwards a committed search term', async () => {
  let captured;
  const enrollmentsApi = loadEnrollmentsApi(async (_url, config) => {
    captured = config?.params;
    return { data: 'csv-body' };
  });
  await enrollmentsApi.exportCsv({ status: 'ENROLLED', search: 'kim nguyen' });
  assert.equal(captured.search, 'kim nguyen', 'committed search reaches the API params');
  assert.equal(captured.status, 'ENROLLED');
});

test('the export handler sends the committed search, never the debouncing input', async () => {
  let captured;
  const calls = [];
  const exportCsv = pageFunction('app/admin/enrollments/page.tsx', 'handleExportCsv', {
    filters: { semesterId: 'term-1', courseId: '', sectionId: '', status: 'ENROLLED' },
    search: 'committed-term',
    enrollmentsApi: {
      exportCsv: async (params) => { captured = params; return 'a,b'; },
    },
    toast: { success: (m) => calls.push(['success', m]), error: (m) => calls.push(['error', m]) },
    copy: { exportStarted: 'STARTED', exportFailed: 'FAILED' },
  });
  // jsdom-free blob/url stubs: the handler touches document + URL.
  global.Blob = class { constructor() {} };
  global.URL = { createObjectURL: () => 'blob:x', revokeObjectURL: () => {} };
  global.document = { createElement: () => ({ click: () => {} }) };
  await exportCsv();
  assert.equal(captured.search, 'committed-term', 'export carries the committed search value');
  assert.equal(captured.status, 'ENROLLED', 'filters still forward');
  assert.deepEqual(calls, [['success', 'STARTED']]);
});

test('the export handler omits search when nothing is committed', async () => {
  let captured;
  const exportCsv = pageFunction('app/admin/enrollments/page.tsx', 'handleExportCsv', {
    filters: { semesterId: '', courseId: '', sectionId: '', status: '' },
    search: '',
    enrollmentsApi: { exportCsv: async (params) => { captured = params; return 'a,b'; } },
    toast: { success: () => {}, error: () => {} },
    copy: { exportStarted: 'STARTED', exportFailed: 'FAILED' },
  });
  await exportCsv();
  assert.equal(captured.search, undefined, 'no uncommitted search leaks into the export');
});

// ---------- admin enrollments list + section options generation guards ----------

function enrollmentDeps(api, calls, overrides = {}) {
  const { record } = calls;
  return {
    filters: { semesterId: '', courseId: '', sectionId: '', status: '' },
    page: 1,
    search: '',
    locale: 'en',
    enrollmentsApi: api,
    setEnrollments: record('enrollments'),
    setTotalPages: record('totalPages'),
    setTotal: record('total'),
    setIsLoading: record('isLoading'),
    setError: record('error'),
    enrollmentsRequestRef: { current: 0 },
    ...overrides,
  };
}

test('a stale enrollment list completion cannot overwrite a newer page', async () => {
  const calls = recorder();
  const first = deferred();
  let call = 0;
  const fetchEnrollments = pageFunction('app/admin/enrollments/page.tsx', 'fetchEnrollments',
    enrollmentDeps({
      getAll: () => {
        call += 1;
        return call === 1 ? first.promise : Promise.resolve({ data: [{ id: 'new' }], meta: { totalPages: 2, total: 5 } });
      },
    }, calls));
  const pending = fetchEnrollments();
  await fetchEnrollments();
  first.resolve({ data: [{ id: 'old' }], meta: { totalPages: 9, total: 99 } });
  await pending;
  const writes = new Map(calls.calls.filter(([n]) => n === 'enrollments'));
  assert.deepEqual(writes.get('enrollments'), [{ id: 'new' }], 'old response must not replace the newer list');
  assert.equal(calls.calls.filter(([n, v]) => n === 'isLoading' && v === false).length, 1,
    'the stale finally must not clear the newer loading flag');
});

test('a stale enrollment list failure cannot surface after a newer load', async () => {
  const calls = recorder();
  const first = deferred();
  let call = 0;
  const fetchEnrollments = pageFunction('app/admin/enrollments/page.tsx', 'fetchEnrollments',
    enrollmentDeps({
      getAll: () => {
        call += 1;
        return call === 1 ? first.promise : Promise.resolve({ data: [], meta: { totalPages: 1, total: 0 } });
      },
    }, calls));
  const pending = fetchEnrollments();
  await fetchEnrollments();
  first.reject(new Error('HTTP 500'));
  await pending;
  assert.ok(
    calls.calls.filter(([n]) => n === 'error').every(([, v]) => v === ''),
    'the stale failure must not write a visible error over the newer result',
  );
});

test('course sections clear immediately for an empty selection and invalidate a pending load', async () => {
  const calls = recorder();
  const { record } = calls;
  const pending = deferred();
  const deps = {
    locale: 'en',
    sectionsApi: { getAll: () => pending.promise },
    fetchAllPages: async (fn) => (await fn(1, 50)).data,
    setSections: record('sections'),
    setSectionReferenceError: record('sectionReferenceError'),
    sectionsRequestRef: { current: 0 },
  };
  const fetchSections = pageFunction('app/admin/enrollments/page.tsx', 'fetchSectionsForCourse', deps);
  const inFlight = fetchSections('course-1');
  await flush();
  await fetchSections('');
  assert.deepEqual(new Map(calls.calls).get('sections'), [],
    'an empty course clears the options immediately');
  pending.resolve({ data: [{ id: 'late-section' }] });
  await inFlight;
  assert.equal(new Map(calls.calls).get('sections').length, 0,
    'the cleared selection must not be repopulated by the stale response');
});

test('a pending load for the new course clears the previous options midflight', async () => {
  const calls = recorder();
  const { record } = calls;
  const second = deferred();
  let call = 0;
  const deps = {
    locale: 'en',
    sectionsApi: {
      getAll: ({ courseId }) => {
        call += 1;
        return call === 1
          ? Promise.resolve({ data: [{ id: 'sec-course-a' }] })
          : second.promise.then(() => ({ data: [{ id: `sec-${courseId}` }] }));
      },
    },
    fetchAllPages: async (fn) => (await fn(1, 50)).data,
    setSections: record('sections'),
    setSectionReferenceError: record('sectionReferenceError'),
    sectionsRequestRef: { current: 0 },
  };
  const fetchSections = pageFunction('app/admin/enrollments/page.tsx', 'fetchSectionsForCourse', deps);
  await fetchSections('course-a');
  assert.deepEqual(new Map(calls.calls).get('sections'), [{ id: 'sec-course-a' }]);
  const pending = fetchSections('course-b');
  assert.deepEqual(new Map(calls.calls).get('sections'), [],
    'selecting a new course must drop the previous options while the request runs');
  second.resolve();
  await pending;
  assert.deepEqual(new Map(calls.calls).get('sections'), [{ id: 'sec-course-b' }]);
});

test('a stale section-options completion cannot overwrite the newer course', async () => {
  const calls = recorder();
  const { record } = calls;
  const first = deferred();
  let call = 0;
  const deps = {
    locale: 'en',
    sectionsApi: {
      getAll: ({ courseId }) => {
        call += 1;
        return call === 1 ? first.promise : Promise.resolve({ data: [{ id: `sec-${courseId}` }] });
      },
    },
    fetchAllPages: async (fn) => (await fn(1, 50)).data,
    setSections: record('sections'),
    setSectionReferenceError: record('sectionReferenceError'),
    sectionsRequestRef: { current: 0 },
  };
  const fetchSections = pageFunction('app/admin/enrollments/page.tsx', 'fetchSectionsForCourse', deps);
  const pendingA = fetchSections('course-a');
  await fetchSections('course-b');
  first.resolve({ data: [{ id: 'sec-course-a' }] });
  await pendingA;
  assert.deepEqual(new Map(calls.calls).get('sections'), [{ id: 'sec-course-b' }],
    'course A options must not land after course B was selected');
});

// ---------- admin users list generation guard ----------

test('a stale users completion cannot overwrite the newer filtered list', async () => {
  const calls = recorder();
  const first = deferred();
  let call = 0;
  const fetchUsers = pageFunction('app/admin/users/page.tsx', 'fetchUsers', {
    locale: 'en',
    page: 1,
    search: '',
    roleFilter: 'ALL',
    usersApi: {
      getAll: () => {
        call += 1;
        return call === 1 ? first.promise : Promise.resolve({ data: [{ id: 'new-user' }], meta: { total: 1, totalPages: 1 } });
      },
    },
    setUsers: calls.record('users'),
    setTotalItems: calls.record('totalItems'),
    setTotalPages: calls.record('totalPages'),
    setIsLoading: calls.record('isLoading'),
    setError: calls.record('error'),
    usersRequestRef: { current: 0 },
  });
  const pending = fetchUsers();
  await fetchUsers();
  first.resolve({ data: [{ id: 'old-user' }], meta: { total: 1, totalPages: 1 } });
  await pending;
  assert.deepEqual(new Map(calls.calls).get('users'), [{ id: 'new-user' }],
    'the older response must not overwrite the newer list');
  assert.equal(calls.calls.filter(([n, v]) => n === 'isLoading' && v === false).length, 1);
});

// ---------- lecturer grading section-scoped guards ----------

const gradesCopy = { loadFailed: 'LOAD_FAILED', missingSection: 'MISSING', saved: 'SAVED' };

function gradingDeps(overrides, sharedCalls) {
  const calls = sharedCalls || recorder();
  const deps = {
    sectionId: 'section-a',
    copy: gradesCopy,
    messages: { common: { campusErrors: {} } },
    campusErrorMessage: (_err, _dict, fallback) => fallback,
    publishGuard: { current: { sectionId: '', ready: false } },
    gradeRequestRef: { current: 0 },
    gradeMutationRef: { current: 0 },
    loadedSectionRef: { current: '' },
    currentSectionRef: { current: 'section-a' },
    sectionsApi: { getSectionGrades: async () => ({ sectionId: 'section-a', enrollments: [] }) },
    setSectionData: calls.record('sectionData'),
    setGrades: calls.record('grades'),
    setEditedIds: calls.record('editedIds'),
    setScoreErrors: calls.record('scoreErrors'),
    setError: calls.record('error'),
    setIsLoading: calls.record('isLoading'),
    setIsRefreshing: calls.record('isRefreshing'),
    setIsSaving: calls.record('isSaving'),
    setIsPublishing: calls.record('isPublishing'),
    ...overrides,
  };
  return { deps, calls };
}

// Route param changes recreate the callback with a new sectionId binding while
// the refs survive (the component is not remounted). Model that with two
// extracted instances sharing the same refs and recorders.
test('a pending section-A load cannot write into the sheet after navigating to B', async () => {
  const gate = deferred();
  const shared = {
    gradeRequestRef: { current: 0 },
    loadedSectionRef: { current: 'section-a' },
    currentSectionRef: { current: 'section-a' },
    publishGuard: { current: { sectionId: '', ready: false } },
    sectionsApi: {
      getSectionGrades: async (id) => {
        if (id === 'section-a') await gate.promise;
        return { sectionId: id, enrollments: [{ id: `enr-${id}` }] };
      },
    },
  };
  const calls = recorder();
  const loadFor = (sectionId) => pageFunction(
    'app/dashboard/lecturer/grades/[id]/page.tsx', 'fetchSectionGrades',
    gradingDeps({ sectionId, ...shared }, calls).deps,
  );
  // Pre-existing A data is on screen; navigation starts while its load hangs.
  const pending = loadFor('section-a')();
  await flush();
  shared.currentSectionRef.current = 'section-b';
  await loadFor('section-b')();
  gate.resolve();
  await pending;
  const lastData = calls.calls.filter(([n]) => n === 'sectionData').map(([, v]) => v).pop();
  assert.equal(lastData?.sectionId, 'section-b', 'only the current section may write its roster');
  assert.equal(shared.loadedSectionRef.current, 'section-b');
});

test('a stale section load failure/finally cannot mark the new sheet', async () => {
  const gate = deferred();
  const shared = {
    gradeRequestRef: { current: 0 },
    loadedSectionRef: { current: '' },
    currentSectionRef: { current: 'section-a' },
    publishGuard: { current: { sectionId: '', ready: false } },
    sectionsApi: {
      getSectionGrades: async (id) => {
        if (id === 'section-a') { await gate.promise; throw new Error('HTTP 500'); }
        return { sectionId: id, enrollments: [] };
      },
    },
  };
  const calls = recorder();
  const loadFor = (sectionId) => pageFunction(
    'app/dashboard/lecturer/grades/[id]/page.tsx', 'fetchSectionGrades',
    gradingDeps({ sectionId, ...shared }, calls).deps,
  );
  const pending = loadFor('section-a')();
  await flush();
  shared.currentSectionRef.current = 'section-b';
  await loadFor('section-b')();
  gate.resolve();
  await pending;
  assert.ok(
    calls.calls.filter(([n]) => n === 'error').every(([, v]) => v === ''),
    'the stale failure must not write a visible error on the new section',
  );
  assert.equal(calls.calls.filter(([n, v]) => n === 'isLoading' && v === false).length, 1,
    'the stale finally must not clear the newer loading flag');
});

test('a same-section silent reload still overlays preserved partial rows', async () => {
  const { deps, calls } = gradingDeps({
    loadedSectionRef: { current: 'section-a' },
    sectionsApi: {
      getSectionGrades: async (id) => ({
        sectionId: id,
        enrollments: [{ id: 'enr-1', processScore: 7, finalExamScore: 7 }],
      }),
    },
  });
  const load = pageFunction('app/dashboard/lecturer/grades/[id]/page.tsx', 'fetchSectionGrades', deps);
  await load({ silent: true, preserveLocalRows: [{ enrollmentId: 'enr-1', processScore: 9, finalExamScore: null }] });
  const gradesWrite = new Map(calls.calls.filter(([n]) => n === 'grades')).get('grades');
  assert.equal(gradesWrite.get('enr-1').processScore, 9,
    'preserved local rows survive the same-section silent reload');
  const editedWrite = new Map(calls.calls.filter(([n]) => n === 'editedIds')).get('editedIds');
  assert.ok(editedWrite.has('enr-1'), 'preserved rows stay flagged as edited');
});

test('a real section change clears the previous roster, drafts and errors', async () => {
  const { deps, calls } = gradingDeps({
    loadedSectionRef: { current: 'section-a' },
    sectionId: 'section-b',
    currentSectionRef: { current: 'section-b' },
    sectionsApi: {
      getSectionGrades: async (id) => ({ sectionId: id, enrollments: [{ id: `enr-${id}` }] }),
    },
  });
  const load = pageFunction('app/dashboard/lecturer/grades/[id]/page.tsx', 'fetchSectionGrades', deps);
  await load();
  assert.deepEqual(new Map(calls.calls).get('editedIds'), new Set(),
    'edited rows of the old section must not carry over');
  assert.deepEqual(new Map(calls.calls).get('scoreErrors'), new Map());
  assert.equal(deps.loadedSectionRef.current, 'section-b');
});

test('a save completing after navigation cannot refetch or touch the new sheet', async () => {
  const calls = recorder();
  const refetch = [];
  const { deps } = gradingDeps({});
  const save = pageFunction('app/dashboard/lecturer/grades/[id]/page.tsx', 'handleSave', {
    sectionId: 'section-a',
    sectionData: {
      sectionId: 'section-a',
      enrollments: [{ id: 'enr-1', processScore: 8, finalExamScore: 8 }],
    },
    isSaving: false,
    isPublishing: false,
    isRefreshing: false,
    editedIds: new Set(['enr-1']),
    grades: new Map([['enr-1', { enrollmentId: 'enr-1', processScore: 9, finalExamScore: 9 }]]),
    scoreErrors: new Map(),
    sectionsApi: { updateSectionGrades: async () => { await new Promise((r) => setImmediate(r)); } },
    fetchSectionGrades: async (...args) => { refetch.push(args); },
    toast: { success: () => {}, warning: () => {}, error: () => {} },
    copy: { ...gradesCopy, saveFailed: 'SAVE_FAILED', partialRowsOnly: 'P {count}', partialRowsSkipped: 'S {count}' },
    formatNumber: (n) => String(n),
    messages: { common: { campusErrors: {} } },
    campusErrorMessage: (_e, _d, f) => f,
    publishGuard: deps.publishGuard,
    setIsSaving: calls.record('isSaving'),
    currentSectionRef: deps.currentSectionRef,
    gradeMutationRef: deps.gradeMutationRef,
    hasCompletedGrade: (u) => u != null && u.processScore != null && u.finalExamScore != null,
    focusFirstInvalidCell: () => {},
    focusFirstPartialCell: () => {},
  });
  const pending = save();
  // Render saw the route change: section flips and the mutation epoch bumps.
  deps.currentSectionRef.current = 'section-b';
  deps.gradeMutationRef.current += 1;
  await pending;
  assert.equal(refetch.length, 0, 'a stale save must not refetch the new section');
  assert.deepEqual(calls.calls, [['isSaving', true]],
    'the stale save finally must not clear the new section busy state');
});

function saveDeps(shared, overrides = {}) {
  return {
    sectionId: 'section-a',
    sectionData: {
      sectionId: 'section-a',
      enrollments: [{ id: 'enr-1', processScore: 8, finalExamScore: 8 }],
    },
    isSaving: false,
    isPublishing: false,
    isRefreshing: false,
    editedIds: new Set(['enr-1']),
    grades: new Map([['enr-1', { enrollmentId: 'enr-1', processScore: 9, finalExamScore: 9 }]]),
    scoreErrors: new Map(),
    sectionsApi: { updateSectionGrades: async () => {} },
    fetchSectionGrades: async () => {},
    toast: { success: () => {}, warning: () => {}, error: () => {} },
    copy: { ...gradesCopy, saveFailed: 'SAVE_FAILED', partialRowsOnly: 'P {count}', partialRowsSkipped: 'S {count}' },
    formatNumber: (n) => String(n),
    messages: { common: { campusErrors: {} } },
    campusErrorMessage: (_e, _d, f) => f,
    publishGuard: shared.publishGuard,
    setIsSaving: shared.setIsSaving,
    currentSectionRef: shared.currentSectionRef,
    gradeMutationRef: shared.gradeMutationRef,
    hasCompletedGrade: (u) => u != null && u.processScore != null && u.finalExamScore != null,
    focusFirstInvalidCell: () => {},
    focusFirstPartialCell: () => {},
    ...overrides,
  };
}

test('a save on section A cannot act after an A→B→A round trip', async () => {
  const calls = recorder();
  const shared = {
    publishGuard: { current: { sectionId: '', ready: false } },
    currentSectionRef: { current: 'section-a' },
    gradeMutationRef: { current: 0 },
    setIsSaving: calls.record('isSaving'),
  };
  const gate = deferred();
  const refetches = [];
  const save = pageFunction('app/dashboard/lecturer/grades/[id]/page.tsx', 'handleSave',
    saveDeps(shared, {
      sectionsApi: { updateSectionGrades: async () => { await gate.promise; } },
      fetchSectionGrades: async () => { refetches.push(1); },
      toast: {
        success: calls.record('success'),
        warning: calls.record('warning'),
        error: calls.record('toastError'),
      },
    }));
  const pending = save();
  // A→B then B→A: section id equality alone would resurrect the old save —
  // only the mutation epoch catches it.
  shared.currentSectionRef.current = 'section-b';
  shared.gradeMutationRef.current += 1;
  shared.currentSectionRef.current = 'section-a';
  shared.gradeMutationRef.current += 1;
  gate.resolve();
  await pending;
  assert.equal(refetches.length, 0, 'the old-A save must not refetch the new sheet');
  assert.equal(calls.calls.filter(([n]) => n === 'success').length, 0,
    'the old-A save must not toast on the new sheet');
  assert.deepEqual(calls.calls.filter(([n]) => n === 'isSaving'), [['isSaving', true]],
    'the old-A save finally must not clear the new busy flag');
});

test('a save rejection completing after navigation cannot toast or unbusy', async () => {
  const calls = recorder();
  const shared = {
    publishGuard: { current: { sectionId: '', ready: false } },
    currentSectionRef: { current: 'section-a' },
    gradeMutationRef: { current: 0 },
    setIsSaving: calls.record('isSaving'),
  };
  const gate = deferred();
  const save = pageFunction('app/dashboard/lecturer/grades/[id]/page.tsx', 'handleSave',
    saveDeps(shared, {
      sectionsApi: { updateSectionGrades: async () => { await gate.promise; throw new Error('HTTP 500'); } },
      toast: {
        success: calls.record('success'),
        warning: calls.record('warning'),
        error: calls.record('toastError'),
      },
    }));
  const pending = save();
  shared.currentSectionRef.current = 'section-b';
  shared.gradeMutationRef.current += 1;
  gate.resolve();
  await pending;
  assert.equal(calls.calls.filter(([n]) => n === 'toastError').length, 0,
    'the stale save failure must not toast on the new sheet');
  assert.deepEqual(calls.calls.filter(([n]) => n === 'isSaving'), [['isSaving', true]],
    'the stale save finally must not clear the new busy flag');
});

// A locale/copy refresh recreates fetchSectionGrades and reruns the load
// effect; its cleanup may invalidate stale REQUESTS but must never retire a
// still-current save — otherwise the save's finally cannot release isSaving.
test('a load-effect rerun during a pending save keeps the mutation epoch', async () => {
  const calls = recorder();
  const shared = {
    publishGuard: { current: { sectionId: '', ready: false } },
    currentSectionRef: { current: 'section-a' },
    gradeMutationRef: { current: 0 },
    gradeRequestRef: { current: 0 },
    setIsSaving: calls.record('isSaving'),
  };
  const gate = deferred();
  const refetches = [];
  const save = pageFunction('app/dashboard/lecturer/grades/[id]/page.tsx', 'handleSave',
    saveDeps(shared, {
      sectionsApi: { updateSectionGrades: async () => { await gate.promise; } },
      fetchSectionGrades: async () => { refetches.push(1); },
      toast: {
        success: calls.record('success'),
        warning: calls.record('warning'),
        error: calls.record('toastError'),
      },
    }));
  const pending = save();
  // The real load effect's cleanup — extracted from the page — must bump only
  // the request generation, not the mutation epoch.
  const effectSetup = pageEffect('app/dashboard/lecturer/grades/[id]/page.tsx',
    'gradeRequestRef', {
      hasAccess: true,
      fetchSectionGrades: async () => {},
      gradeRequestRef: shared.gradeRequestRef,
    });
  const cleanup = effectSetup();
  cleanup();
  assert.equal(shared.gradeRequestRef.current, 1, 'stale requests are invalidated');
  assert.equal(shared.gradeMutationRef.current, 1,
    'the mutation epoch must NOT advance on a non-navigation rerun');
  gate.resolve();
  await pending;
  assert.equal(refetches.length, 1, 'a still-current save refetches its sheet');
  assert.deepEqual(calls.calls.filter(([n]) => n === 'isSaving'),
    [['isSaving', true], ['isSaving', false]],
    'the save finally releases its own busy flag');
});

test('unmount invalidates a pending mutation through the dedicated effect', async () => {
  const calls = recorder();
  const shared = {
    publishGuard: { current: { sectionId: '', ready: false } },
    currentSectionRef: { current: 'section-a' },
    gradeMutationRef: { current: 0 },
    setIsSaving: calls.record('isSaving'),
  };
  const gate = deferred();
  const refetches = [];
  const save = pageFunction('app/dashboard/lecturer/grades/[id]/page.tsx', 'handleSave',
    saveDeps(shared, {
      sectionsApi: { updateSectionGrades: async () => { await gate.promise; } },
      fetchSectionGrades: async () => { refetches.push(1); },
      toast: {
        success: calls.record('success'),
        warning: calls.record('warning'),
        error: calls.record('toastError'),
      },
    }));
  const pending = save();
  const unmountSetup = pageEffect('app/dashboard/lecturer/grades/[id]/page.tsx',
    'gradeMutationRef', { gradeMutationRef: shared.gradeMutationRef });
  const unmountCleanup = unmountSetup();
  unmountCleanup();
  assert.equal(shared.gradeMutationRef.current, 2, 'unmount retires in-flight mutations');
  gate.resolve();
  await pending;
  assert.equal(refetches.length, 0, 'a mutation completing after unmount does not refetch');
  assert.equal(calls.calls.filter(([n]) => n === 'success').length, 0,
    'a mutation completing after unmount does not toast');
  assert.deepEqual(calls.calls.filter(([n]) => n === 'isSaving'), [['isSaving', true]],
    'its finally is retired too — there is no new sheet whose flag it could hold');
});

test('a publish confirmed after navigation must not reach the API', async () => {
  const calls = recorder();
  const shared = {
    publishGuard: { current: { sectionId: 'section-a', ready: true } },
    currentSectionRef: { current: 'section-a' },
    gradeMutationRef: { current: 0 },
  };
  const confirmGate = deferred();
  const publishes = [];
  const publish = pageFunction('app/dashboard/lecturer/grades/[id]/page.tsx', 'handlePublish', {
    sectionId: 'section-a',
    publishGuard: shared.publishGuard,
    currentSectionRef: shared.currentSectionRef,
    gradeMutationRef: shared.gradeMutationRef,
    confirm: () => confirmGate.promise,
    copy: { ...gradesCopy, publishTitle: 'T', publishMessage: 'M', publishGrades: 'P', published: 'DONE', publishFailed: 'FAIL' },
    sectionsApi: { publishSectionGrades: async (id) => { publishes.push(id); } },
    fetchSectionGrades: async () => {},
    toast: { success: calls.record('success'), error: calls.record('toastError') },
    campusErrorMessage: (_e, _d, f) => f,
    messages: { common: { campusErrors: {} } },
    setIsPublishing: calls.record('isPublishing'),
  });
  const pending = publish();
  await flush();
  // Lecturer navigates away while the confirm dialog is still open.
  shared.currentSectionRef.current = 'section-b';
  shared.gradeMutationRef.current += 1;
  confirmGate.resolve(true);
  await pending;
  assert.equal(publishes.length, 0, 'a stale confirmation must never publish');
  assert.equal(calls.calls.filter(([n, v]) => n === 'isPublishing' && v === true).length, 0,
    'the stale publish must not even raise the busy flag');
});
