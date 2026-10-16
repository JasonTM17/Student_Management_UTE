const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
function loadTs(file, imports = {}) {
  const js = ts.transpileModule(fs.readFileSync(path.join(root, file), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const mod = { exports: {} };
  new Function('module', 'exports', 'require', js)(mod, mod.exports, (id) => imports[id] ?? require(id));
  return mod.exports;
}

test('admin datetime-local open/save preserves the instant and milliseconds', () => {
  const helper = path.join(root, 'src/lib/thesis-datetime.ts');
  const toLocalInput = fs.existsSync(helper)
    ? loadTs('src/lib/thesis-datetime.ts').toThesisLocalInput
    : new Function('iso', `return ${fs.readFileSync(path.join(root, 'src/app/admin/thesis/page.tsx'), 'utf8').match(/const toLocalInput = .*?=> (.*);/)[1]}`);
  const previous = process.env.TZ;
  try {
    for (const timezone of ['Asia/Bangkok', 'UTC']) {
      process.env.TZ = timezone;
      for (const iso of ['2026-10-16T10:23:45.123Z', '2026-10-17T00:03:12.456+07:00']) {
        assert.equal(new Date(toLocalInput(iso)).getTime(), new Date(iso).getTime(), timezone);
      }
    }
    assert.equal(toLocalInput(null), '');
    assert.equal(toLocalInput(undefined), '');
  } finally {
    if (previous === undefined) delete process.env.TZ;
    else process.env.TZ = previous;
  }
});

// A small hook runner executes the real workspace module with controlled API
// promises. This proves state transitions instead of matching source strings.
function workspaceHarness(api, initialRoundId = '', identity = { id: 'user', studentId: 'student' }) {
  const slots = [];
  let cursor = 0;
  let dirty = false;
  let pendingEffects = [];
  let result;
  const changed = (a, b) => !a || a.length !== b.length || a.some((v, i) => !Object.is(v, b[i]));
  const react = {
    useState(initial) {
      const i = cursor++;
      if (!slots[i]) slots[i] = { value: typeof initial === 'function' ? initial() : initial };
      return [slots[i].value, (value) => {
        const next = typeof value === 'function' ? value(slots[i].value) : value;
        if (!Object.is(next, slots[i].value)) { slots[i].value = next; dirty = true; }
      }];
    },
    useRef(initial) { const i = cursor++; return slots[i] ?? (slots[i] = { current: initial }); },
    useMemo(fn, deps) {
      const i = cursor++;
      if (!slots[i] || changed(slots[i].deps, deps)) slots[i] = { deps, value: fn() };
      return slots[i].value;
    },
    useCallback(fn, deps) { return react.useMemo(() => fn, deps); },
    useEffect(fn, deps) {
      const i = cursor++;
      if (!slots[i] || changed(slots[i].deps, deps)) {
        const old = slots[i];
        slots[i] = { deps };
        pendingEffects.push(() => { old?.cleanup?.(); slots[i].cleanup = fn(); });
      }
    },
  };
  const messages = { thesis: { loadFailed: 'LOAD_FAILED', status: {} }, common: { statuses: {} } };
  const { useThesisWorkspace } = loadTs('src/components/dashboard/thesis/useThesisWorkspace.ts', {
    react,
    '@/context/AuthContext': { useAuth: () => ({ user: identity, isStudent: true }) },
    '@/i18n': { useI18n: () => ({ messages }) },
    '@/lib/thesis-api': { thesisApi: api },
    '@/lib/thesis-round': loadTs('src/lib/thesis-round.ts'),
  });
  const WorkspaceProbe = () => {
    cursor = 0; dirty = false;
    result = useThesisWorkspace(initialRoundId);
    const effects = pendingEffects; pendingEffects = [];
    effects.forEach((fn) => fn());
  };
  return {
    get result() { return result; },
    setInitialRound(roundId) { initialRoundId = roundId; dirty = true; },
    async flush() {
      for (let i = 0; i < 20; i++) {
        await new Promise((resolve) => setImmediate(resolve));
        if (dirty || !result) WorkspaceProbe();
      }
    },
    unmount() { slots.forEach((slot) => slot?.cleanup?.()); },
  };
}

const rounds = [
  { id: 'new', name: 'New', thesisType: 'KLTN', status: 'REGISTRATION_OPEN' },
  { id: 'old', name: 'Old', thesisType: 'KLTN', status: 'REGISTRATION_CLOSED' },
];
const deferred = () => { let resolve, reject; const promise = new Promise((a, b) => { resolve = a; reject = b; }); return { promise, resolve, reject }; };
const baseApi = { listRounds: async () => rounds, myGroups: async () => [], listGroups: async () => [], listTopics: async () => [] };

test('shared workspace defaults to student membership, while explicit/manual selection wins', async () => {
  const mine = deferred();
  const api = { ...baseApi, myGroups: () => mine.promise };
  const member = workspaceHarness(api);
  await member.flush();
  mine.resolve([{ roundId: 'old', status: 'SUBMITTED' }]);
  await member.flush();
  assert.equal(member.result.selectedRoundId, 'old');
  member.result.setSelectedRoundId('new');
  await member.flush();
  await member.result.reload(); await member.flush();
  assert.equal(member.result.selectedRoundId, 'new');
  member.unmount();
  const explicit = workspaceHarness(api, 'new');
  await explicit.flush();
  assert.equal(explicit.result.selectedRoundId, 'new');
  explicit.unmount();
  const late = deferred();
  const manual = workspaceHarness({ ...baseApi, myGroups: () => late.promise });
  await manual.flush();
  manual.result.setSelectedRoundId('new'); await manual.flush();
  late.resolve([{ roundId: 'old', status: 'SUBMITTED' }]); await manual.flush();
  assert.equal(manual.result.selectedRoundId, 'new');
  manual.unmount();
});

test('shared workspace rejects late old-round success, failure and loading completion', async () => {
  for (const outcome of ['success', 'error']) {
    const old = deferred();
    const current = deferred();
    const harness = workspaceHarness({ ...baseApi, listTopics: (id) => id === 'old' ? old.promise : current.promise }, 'old');
    await harness.flush();
    harness.result.setSelectedRoundId('new'); await harness.flush();
    assert.deepEqual(harness.result.topics, []);
    if (outcome === 'success') old.resolve([{ id: 'STALE' }]);
    else old.reject(new Error('old failure'));
    await harness.flush();
    assert.deepEqual(harness.result.topics, []);
    assert.equal(harness.result.error, '');
    assert.equal(harness.result.workspaceLoading, true);
    current.resolve([{ id: 'CURRENT' }]); await harness.flush();
    assert.deepEqual(harness.result.topics, [{ id: 'CURRENT' }]);
    assert.equal(harness.result.workspaceLoading, false);
    harness.unmount();
  }
});

test('an explicit round route change wins without undoing a later manual selection', async () => {
  const harness = workspaceHarness(baseApi, 'old');
  await harness.flush();
  harness.setInitialRound('new'); await harness.flush();
  assert.equal(harness.result.selectedRoundId, 'new');
  harness.result.setSelectedRoundId('old'); await harness.flush();
  await harness.result.reload(); await harness.flush();
  assert.equal(harness.result.selectedRoundId, 'old');
  harness.unmount();
});

test('shared workspace clears old groups and topics on an empty new round', async () => {
  const harness = workspaceHarness({ ...baseApi,
    listGroups: async (id) => id === 'old' ? [{ id: 'old-group', memberStudentIds: [] }] : [],
    listTopics: async (id) => id === 'old' ? [{ id: 'old-topic' }] : [],
  }, 'old');
  await harness.flush();
  assert.equal(harness.result.groups.length, 1);
  harness.result.setSelectedRoundId('new'); await harness.flush();
  assert.deepEqual(harness.result.groups, []);
  assert.deepEqual(harness.result.topics, []);
  harness.unmount();
});

test('membership lookup failure remains visible through workspace refresh and can retry', async () => {
  let fail = true;
  const harness = workspaceHarness({ ...baseApi, myGroups: async () => {
    if (fail) throw new Error('membership unavailable');
    return [{ roundId: 'old', status: 'SUBMITTED' }];
  } });
  await harness.flush();
  assert.equal(harness.result.error, 'LOAD_FAILED');
  await harness.result.refreshWorkspace(); await harness.flush();
  assert.equal(harness.result.error, 'LOAD_FAILED');
  fail = false;
  await harness.result.reload(); await harness.flush();
  assert.equal(harness.result.error, '');
  assert.equal(harness.result.selectedRoundId, 'old');
  harness.unmount();
});

test('a post-mutation refresh captured in an old round cannot replace the current workspace', async () => {
  let topicCalls = 0;
  const harness = workspaceHarness({ ...baseApi,
    listTopics: async (id) => { topicCalls += 1; return [{ id }]; },
  }, 'old');
  await harness.flush();
  const oldRefresh = harness.result.refreshWorkspace;
  harness.result.setSelectedRoundId(() => 'new'); await harness.flush();
  const callsBefore = topicCalls;
  await oldRefresh(); await harness.flush();
  assert.equal(topicCalls, callsBefore);
  assert.deepEqual(harness.result.topics, [{ id: 'new' }]);
  harness.unmount();
});
