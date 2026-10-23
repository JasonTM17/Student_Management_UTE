const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// Execute the actual page callback, supplying its hook/API dependencies —
// same harness as functional-load-recovery.test.js.
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
    ...Object.values(dependencies),
    (fn) => fn,
  );
}

function harness(api, requested = new Set()) {
  const state = { scores: {}, drafts: {}, comments: {} };
  const load = callback('app/dashboard/thesis/page.tsx', 'loadMyReviewerScores', {
    thesisApi: api,
    requestedReviewerScoresRef: { current: requested },
    setGvpbScores: (update) => {
      state.scores = update(state.scores);
    },
    setGvpbDraftScores: (update) => {
      state.drafts = update(state.drafts);
    },
    setGvpbDraftComments: (update) => {
      state.comments = update(state.comments);
    },
  });
  return { load, state, requested };
}

test('a topic-list refresh re-issues the fan-out only for topics not already fetched', async () => {
  const calls = [];
  const api = {
    reviewerScore: async (id) => {
      calls.push(id);
      return { topicId: id, score: 8.5, comment: 'ok' };
    },
  };
  const { load, state } = harness(api);
  const topics = [{ id: 'a' }, { id: 'b' }];

  await load(topics);
  assert.deepEqual(calls.sort(), ['a', 'b']);
  assert.equal(state.scores.a.score, 8.5);
  assert.equal(state.drafts.a, '8.5');
  assert.equal(state.comments.a, 'ok');

  // refreshTopics mints a new array after every mutation; the same assigned
  // set must not re-hit the shared per-user thesis rate-limit bucket.
  calls.length = 0;
  await load([{ id: 'a' }, { id: 'b' }]);
  assert.equal(calls.length, 0, 'an identical assignment set must not refetch');

  // A newly assigned topic still loads; already-known ones stay cached.
  await load([{ id: 'a' }, { id: 'b' }, { id: 'c' }]);
  assert.deepEqual(calls, ['c']);
});

test('a failed read drops its mark so the next refresh retries it', async () => {
  const calls = [];
  let fail = true;
  const api = {
    reviewerScore: async (id) => {
      calls.push(id);
      if (fail && id === 'b') throw new Error('429');
      return null;
    },
  };
  const { load } = harness(api);
  const topics = [{ id: 'a' }, { id: 'b' }];

  await load(topics);
  fail = false;
  await load(topics);
  assert.deepEqual(calls, ['a', 'b', 'b'], 'only the failed topic may be retried');
});
