const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('path');

const SRC = path.join(__dirname, '../src');
const read = (rel) => fs.readFileSync(path.join(SRC, rel), 'utf8');

test('thesisApi.scoreBreakdown calls the per-topic breakdown endpoint', () => {
  const api = read('lib/thesis-api.ts');
  assert.match(api, /scoreBreakdown:\s*async\s*\(topicId:\s*string\)/);
  assert.match(api, /'\/thesis\/topics\/' \+ topicId \+ '\/score-breakdown'/);
});

test('the breakdown contract carries names and roles but never emails', () => {
  const api = read('lib/thesis-api.ts');
  const start = api.indexOf('export interface ThesisScoreBreakdownGrader');
  const end = api.indexOf('export interface ThesisFinalScore');
  const block = api.slice(start, end);
  assert.match(block, /displayName:\s*string/);
  assert.match(block, /role:\s*ThesisScoreGraderRole/);
  assert.match(block, /countedInAverage\?:\s*boolean/);
  assert.match(block, /pendingGraders\?:\s*ThesisScoreBreakdownPendingGrader\[\]/);
  // PII guard: the read model must not declare an email field anywhere.
  assert.doesNotMatch(block, /email/);
});

test('the panel hides for denied or unpublished callers and fails loudly otherwise', () => {
  const panel = read('features/thesis/components/ScoreBreakdownPanel.tsx');
  assert.match(panel, /thesisApi\s*\.\s*scoreBreakdown/);
  for (const guard of ['401', '403', '404', 'RESULTS_NOT_PUBLISHED']) {
    assert.ok(panel.includes(guard), `panel treats ${guard} as hidden`);
  }
  assert.match(panel, /copy\.loadFailed/);
});

test('the panel renders a score only when the server returned one', () => {
  const panel = read('features/thesis/components/ScoreBreakdownPanel.tsx');
  // Student payloads null the per-grader score — the UI must not invent a 0.
  assert.match(panel, /grader\.score != null/);
  assert.match(panel, /grader\.comment \?/);
  // The finalizer identity stays staff-only: the panel never renders it.
  assert.doesNotMatch(panel, /finalizedBy/);
});

test('the panel names pending graders so the chair sees who is missing', () => {
  const panel = read('features/thesis/components/ScoreBreakdownPanel.tsx');
  assert.match(panel, /pendingGraders/);
  assert.match(panel, /copy\.missingScores/);
  assert.match(panel, /copy\.statusPending/);
  assert.match(panel, /copy\.notCounted/);
});

test('all four council and reviewer roles have a label', () => {
  const panel = read('features/thesis/components/ScoreBreakdownPanel.tsx');
  for (const role of ['CHAIR', 'SECRETARY', 'MEMBER', 'GVPB_REVIEWER']) {
    assert.ok(panel.includes(`'${role}'`), `role ${role} mapped`);
  }
});

test('en and vi dictionaries define the same breakdown keys', () => {
  const messages = read('i18n/messages.ts');
  const blocks = messages.split('breakdown: {').slice(1);
  assert.equal(blocks.length, 2, 'exactly two breakdown dictionaries');
  const keys = (block) =>
    block
      .slice(0, block.indexOf('}'))
      .split('\n')
      .map((line) => line.trim().split(':')[0])
      .filter((key) => /^[a-zA-Z]+$/.test(key))
      .sort();
  assert.deepEqual(keys(blocks[0]), keys(blocks[1]));
});

test('every authorized surface renders the breakdown panel', () => {
  const dashboard = read('app/dashboard/thesis/page.tsx');
  const admin = read('app/admin/thesis/page.tsx');
  const workspace = read('features/thesis/components/CouncilDefenseWorkspace.tsx');
  const supervised = read('features/thesis/components/SupervisedGroupList.tsx');
  assert.match(dashboard, /import ScoreBreakdownPanel/);
  assert.match(dashboard, /<ScoreBreakdownPanel/);
  assert.match(admin, /import ScoreBreakdownPanel/);
  assert.match(admin, /<ScoreBreakdownPanel/);
  assert.match(workspace, /<ScoreBreakdownPanel/);
  assert.match(supervised, /<ScoreBreakdownPanel/);
  // The student result row resolves the topic through the caller's own group.
  assert.match(dashboard, /result\.groupId === currentGroup\?\.id/);
  // The supervisor surface is publication-gated in the client too.
  assert.match(supervised, /resultsPublished &&/);
});
