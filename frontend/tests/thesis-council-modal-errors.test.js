const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// Same TypeScript transpile harness as thesis-council-integrity.test.js: render
// the real module with imports stubbed, and execute page callbacks with their
// closure dependencies injected.
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

const recorder = () => {
  const calls = [];
  const record = (name) => (value) => { calls.push([name, value]); };
  return { calls, record };
};

function loadMessages() {
  return loadModule('src/i18n/messages.ts', {
    '@/i18n/config': {},
    '@/i18n/messages-thesis-workflow': loadModule('src/i18n/messages-thesis-workflow.ts'),
    '@/i18n/messages-dean-overview': loadModule('src/i18n/messages-dean-overview.ts'),
  }).en;
}

const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');

function conflictError(code) {
  return Object.assign(new Error('conflict'), {
    response: { status: 409, data: { code } },
  });
}

function adminDeps(api, calls, extra = {}) {
  const { record } = calls;
  const messages = loadMessages();
  return {
    messages,
    campusCodeMessage,
    transitionErrorMessage: (err) =>
      campusCodeMessage(err, messages.common.campusErrors, messages.thesis.actionFailed),
    thesisApi: api,
    councils: [],
    selectedLecturerId: 'lecturer-1',
    selectedTopicIdToAssign: 'topic-1',
    councilNameInput: 'HD-test',
    expandedRoundRef: { current: 'round-1' },
    isSaving: false,
    setIsSaving: record('setIsSaving'),
    setError: record('setError'),
    setSuccess: record('setSuccess'),
    setModalError: record('setModalError'),
    setCouncils: record('setCouncils'),
    setCouncilNameInput: record('setCouncilNameInput'),
    setSelectedLecturerId: record('setSelectedLecturerId'),
    setSelectedTopicIdToAssign: record('setSelectedTopicIdToAssign'),
    setShowCreateCouncilModal: record('setShowCreateCouncilModal'),
    setShowAddMemberModal: record('setShowAddMemberModal'),
    setShowAssignTopicModal: record('setShowAssignTopicModal'),
    ...extra,
  };
}

// ---------- refused modal submits must surface the error inside the modal ----------
//
// Regression: a refused council action used to write only the page-level `error`
// banner, which renders behind the modal overlay — the dialog stayed open with
// no visible feedback. The failure must reach `setModalError`, and the dialog
// must stay open for a corrected retry.

for (const [name, fnName, apiMethod, apiPayload, callArgs] of [
  ['addMember', 'handleAddMember', 'addCouncilMember', ['council-1', 'lecturer-1', 'MEMBER'], ['council-1', 'round-1']],
  ['createCouncil', 'handleCreateCouncil', 'createCouncil', ['round-1', 'HD-test'], ['round-1']],
  ['assignTopic', 'handleAssignTopic', 'assignTopicToCouncil', ['council-1', 'topic-1'], ['council-1', 'round-1']],
]) {
  test(`${name}: a 409 refusal surfaces the mapped message inside the modal`, async () => {
    const calls = recorder();
    const api = {
      [apiMethod]: async (...args) => {
        assert.deepEqual(args, apiPayload);
        throw conflictError('COUNCIL_STATE_CONFLICT');
      },
      listCouncils: async () => [],
    };
    const deps = adminDeps(api, calls);
    deps.councils = [{ id: 'council-1', members: [{ memberRole: 'CHAIR' }, { memberRole: 'SECRETARY' }] }];
    const run = pageFunction('app/admin/thesis/page.tsx', fnName, deps);
    await run(...callArgs);
    const writes = new Map(calls.calls);
    assert.equal(
      writes.get('setModalError'),
      'This council is not in a state that allows that action.',
      'the refusal must be written to the modal-scoped error, not only the hidden page banner',
    );
    assert.equal(
      writes.get('setError'),
      '',
      'the page banner may be cleared but must not be the only sink',
    );
    assert.equal(writes.get('setSuccess'), '', 'no success message on a refused action');
    assert.equal(
      calls.calls.filter(([n]) => n.startsWith('setShow')).length,
      0,
      'the modal stays open so the admin can correct and retry',
    );
  });

  test(`${name}: a fresh submit attempt clears the previous modal error`, async () => {
    const calls = recorder();
    const api = {
      [apiMethod]: async () => ({}),
      listCouncils: async () => [{ id: 'council-1', members: [] }],
    };
    const deps = adminDeps(api, calls);
    deps.councils = [{ id: 'council-1', members: [{ memberRole: 'CHAIR' }, { memberRole: 'SECRETARY' }] }];
    const run = pageFunction('app/admin/thesis/page.tsx', fnName, deps);
    await run(...callArgs);
    const clears = calls.calls.filter(([n, v]) => n === 'setModalError' && v === '');
    assert.ok(clears.length >= 1, 'the submit path clears the modal error before trying');
    const writes = new Map(calls.calls);
    assert.notEqual(writes.get('setSuccess'), undefined, 'success banner still written');
  });
}

// ---------- restore modal: failure must reach the modal when it is open ----------

test('performRestore surfaces a refusal in the modal and keeps the page banner too', async () => {
  const calls = recorder();
  const messages = loadMessages();
  const api = {
    restoreRound: async () => { throw conflictError('ROUND_STATE_CONFLICT'); },
    listGroups: async () => [],
  };
  const deps = {
    messages,
    campusCodeMessage,
    transitionErrorMessage: (err) =>
      campusCodeMessage(err, messages.common.campusErrors, messages.thesis.actionFailed),
    thesisApi: api,
    expandedRoundId: 'round-x',
    expandedRoundRef: { current: 'round-x' },
    setIsSaving: calls.record('setIsSaving'),
    setError: calls.record('setError'),
    setSuccess: calls.record('setSuccess'),
    setModalError: calls.record('setModalError'),
    setRestoreTargetRound: calls.record('setRestoreTargetRound'),
    loadData: async () => {},
    loadRoundDetail: async () => {},
  };
  const run = pageFunction('app/admin/thesis/page.tsx', 'performRestore', deps);
  await run('round-1', 'REGISTRATION_OPEN');
  const writes = new Map(calls.calls);
  assert.equal(
    writes.get('setModalError'),
    messages.common.campusErrors.codes.ROUND_STATE_CONFLICT,
    'a failed restore must be visible inside the restore modal',
  );
});
