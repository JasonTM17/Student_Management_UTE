'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

const page = read('src/app/admin/thesis/page.tsx');

// Slice the rendered block between two stable markers so assertions bind to
// one picker, not the whole page.
function slice(source, start, end) {
  const i = source.indexOf(start);
  assert.ok(i >= 0, `marker found: ${start.slice(0, 60)}`);
  const j = end ? source.indexOf(end, i) : source.length;
  assert.ok(j > i, `end marker found after start`);
  return source.slice(i, j);
}

test('the GVPB counter-reviewer picker is the searchable Combobox', () => {
  const row = slice(
    page,
    'GVPB counter-reviewer (spec: one reviewer per defended topic)',
    'handleAssignReviewer',
  );
  assert.match(row, /<Combobox/, 'GVPB row renders the searchable combobox');
  assert.doesNotMatch(row, /<Select/, 'the long lecturer list no longer uses a native select');
  assert.match(row, /aria-label=\{messages\.thesis\.admin\.assignReviewer\}/);
});

test('the GVPB picker wires the independence rules as disabled-with-reason', () => {
  const row = slice(
    page,
    'GVPB counter-reviewer (spec: one reviewer per defended topic)',
    'handleAssignReviewer',
  );
  assert.match(row, /reviewerConflict\(\{/, 'the conflict rule from thesis-picker-rules is applied');
  assert.match(row, /supervisorIds: topicSupervisors\[tid\]/, 'topic supervisors gate the option');
  assert.match(row, /councilMemberIds: \(council\.members/, 'council members gate the option');
  assert.match(row, /disabledReason: conflict[\s\S]{0,140}conflicts\[conflict\]/);
});

test('clearing the GVPB picker still posts the empty unassigned draft', () => {
  const row = slice(
    page,
    'GVPB counter-reviewer (spec: one reviewer per defended topic)',
    'handleAssignReviewer',
  );
  assert.match(row, /allowClear/, 'the old empty option survives as a clear action');
  assert.match(row, /\[tid\]: next/, 'the same reviewerDrafts setter receives the value');
});

test('the add-member picker is a searchable Combobox gated by assigned-topic conflicts', () => {
  const modal = slice(page, 'Add Council Member Modal', 'lecturerLoadFailed');
  assert.match(modal, /<Combobox/, 'add-member uses the searchable combobox');
  assert.doesNotMatch(modal, /<Select/, 'the long lecturer list no longer uses a native select');
  assert.match(modal, /councilMemberConflict\(\{/);
  assert.match(modal, /assignedTopics: councilTopics/, 'the council\u2019s topics gate the option');
  assert.match(modal, /disabledReason: conflict[\s\S]{0,140}conflicts\[conflict\]/);
});

test('the replace-member picker keeps the seat filter and adds conflict reasons', () => {
  const modal = slice(page, "reasonAction?.kind === 'replaceMember'", "reasonAction?.kind === 'lateTopic'");
  assert.match(modal, /<Combobox/, 'replace-member uses the searchable combobox');
  assert.doesNotMatch(modal, /<Select/, 'the replacement list no longer uses a native select');
  assert.match(modal, /l\.id !== reasonAction\.lecturerId/, 'the outgoing member is still excluded');
  assert.match(modal, /councilMemberConflict\(\{/);
  assert.match(modal, /assignedTopics: replaceCouncilTopics/);
});

test('the assign-topic picker is a searchable Combobox gated by council-seat conflicts', () => {
  const modal = slice(page, 'Assign Topic Modal', 'handleAssignTopic');
  assert.match(modal, /<Combobox/, 'assign-topic uses the searchable combobox');
  assert.doesNotMatch(modal, /<Select/, 'the topic list no longer uses a native select');
  assert.match(modal, /assignTopicConflict\(\{/);
  assert.match(modal, /memberIds: targetMemberIds/);
  assert.match(modal, /officerIds: targetOfficerIds/);
  assert.match(modal, /topicSupervisorIds: topicSupervisors\[t\.id\]/);
});

test('supervisor ids come from the real thesis API, keyed per topic', () => {
  assert.match(page, /thesisApi\s*\.\s*listSupervisors\(/);
  assert.match(
    page,
    /\[topicId\]: sups\.map\(\(s\) => s\.lecturerId\)/,
    'the picker cache stores raw stored lecturer ids',
  );
  const detail = slice(page, 'const loadRoundDetail', 'if (lecturers.length === 0)');
  assert.match(
    detail,
    /ensureTopicSupervisors\(c\.flatMap\(\(council\) => council\.topicIds/,
    'round detail warms supervisors for the council-assigned topics',
  );
});
