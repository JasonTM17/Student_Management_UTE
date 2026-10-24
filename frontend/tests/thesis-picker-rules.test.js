'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const Module = require('module');
const ts = require('typescript');

function load(absPath, mocks = {}) {
  const code = ts.transpileModule(
    require('fs').readFileSync(absPath, 'utf8'),
    { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true } },
  ).outputText;
  const mod = new Module(absPath, module);
  mod.filename = absPath;
  mod.paths = Module._nodeModulePaths(path.dirname(absPath));
  const realRequire = mod.require.bind(mod);
  mod.require = (spec) => (spec in mocks ? mocks[spec] : realRequire(spec));
  mod._compile(code, absPath);
  return mod.exports;
}

const rules = load(path.join(__dirname, '..', 'src', 'lib', 'thesis-picker-rules.ts'));

const {
  identityKeySet,
  lecturerMatches,
  reviewerConflict,
  councilMemberConflict,
  assignTopicConflict,
} = rules;

const L = (id, userId) => ({ id, userId });
const LECTURERS = [L('lec-1', 'usr-1'), L('lec-2', 'usr-2'), L('lec-3', 'usr-3')];

test('identityKeySet expands raw ids to both Lecturer.id and legacy User.id forms', () => {
  const keys = identityKeySet(LECTURERS, ['usr-1', 'lec-2', 'unknown-raw']);
  assert.ok(keys.has('usr-1'));
  assert.ok(keys.has('lec-1'), 'resolves a stored User.id to its Lecturer.id');
  assert.ok(keys.has('lec-2'));
  assert.ok(keys.has('usr-2'), 'resolves a stored Lecturer.id to its User.id');
  assert.ok(keys.has('unknown-raw'), 'unmatched raw ids pass through');
});

test('lecturerMatches hits on either stored key form', () => {
  assert.equal(lecturerMatches(L('lec-1', 'usr-1'), ['usr-1']), true);
  assert.equal(lecturerMatches(L('lec-1', 'usr-1'), ['lec-1']), true);
  assert.equal(lecturerMatches(L('lec-1', 'usr-1'), ['lec-9']), false);
  assert.equal(lecturerMatches(L('lec-1', undefined), ['usr-1']), false);
});

test('reviewerConflict flags a topic supervisor as supervisorOfTopic', () => {
  // SUPERVISOR_CANNOT_REVIEW — ThesisCouncilService.assignReviewer
  assert.equal(
    reviewerConflict({ lecturer: L('lec-1', 'usr-1'), supervisorIds: ['usr-1'], councilMemberIds: [] }),
    'conflictSupervisorOfTopic',
  );
});

test('reviewerConflict flags a seated council member as councilMemberOfTopic', () => {
  // REVIEWER_MUST_BE_INDEPENDENT — same service, second guard
  assert.equal(
    reviewerConflict({ lecturer: L('lec-2', 'usr-2'), supervisorIds: [], councilMemberIds: ['lec-2'] }),
    'conflictCouncilMemberOfTopic',
  );
  assert.equal(
    reviewerConflict({ lecturer: L('lec-2', 'usr-2'), supervisorIds: [], councilMemberIds: ['usr-2'] }),
    'conflictCouncilMemberOfTopic',
  );
});

test('reviewerConflict leaves an unrelated lecturer enabled', () => {
  assert.equal(
    reviewerConflict({ lecturer: L('lec-3', 'usr-3'), supervisorIds: ['usr-1'], councilMemberIds: ['lec-2'] }),
    null,
  );
});

test('councilMemberConflict flags the reviewer of an assigned topic', () => {
  // REVIEWER_MUST_BE_INDEPENDENT inside addMember/replaceMember
  const assignedTopics = [{ id: 't1', gvpbLecturerId: 'lec-1' }];
  assert.equal(
    councilMemberConflict({ lecturer: L('lec-1', 'usr-1'), assignedTopics, topicSupervisors: {} }),
    'conflictReviewsAssignedTopic',
  );
});

test('councilMemberConflict flags a supervisor of an assigned topic on any seat', () => {
  // SUPERVISOR_CANNOT_CHAIR_COUNCIL — addMember joins on any member_role
  const assignedTopics = [{ id: 't1', gvpbLecturerId: null }];
  const topicSupervisors = { t1: ['usr-2'] };
  assert.equal(
    councilMemberConflict({ lecturer: L('lec-2', 'usr-2'), assignedTopics, topicSupervisors }),
    'conflictSupervisesAssignedTopic',
  );
});

test('councilMemberConflict reviewer beats supervisor when both apply', () => {
  const assignedTopics = [{ id: 't1', gvpbLecturerId: 'lec-1' }];
  const topicSupervisors = { t1: ['usr-1'] };
  assert.equal(
    councilMemberConflict({ lecturer: L('lec-1', 'usr-1'), assignedTopics, topicSupervisors }),
    'conflictReviewsAssignedTopic',
  );
});

test('councilMemberConflict passes a lecturer with no ties to the topics', () => {
  const assignedTopics = [{ id: 't1', gvpbLecturerId: 'lec-1' }];
  const topicSupervisors = { t1: ['usr-1'] };
  assert.equal(
    councilMemberConflict({ lecturer: L('lec-3', 'usr-3'), assignedTopics, topicSupervisors }),
    null,
  );
});

test('assignTopicConflict flags a supervisor sitting as chair or secretary', () => {
  // SUPERVISOR_CANNOT_CHAIR_COUNCIL — member_role IN ('CHAIR','SECRETARY')
  assert.equal(
    assignTopicConflict({
      topicSupervisorIds: ['lec-1'],
      memberIds: ['lec-1', 'lec-2', 'lec-3'],
      officerIds: ['lec-1'],
      lecturers: LECTURERS,
    }),
    'conflictSupervisorOfficer',
  );
  // legacy key form: supervisor stored as Lecturer.id, member row as User.id
  assert.equal(
    assignTopicConflict({
      topicSupervisorIds: ['lec-1'],
      memberIds: ['usr-1', 'lec-2', 'lec-3'],
      officerIds: ['usr-1'],
      lecturers: LECTURERS,
    }),
    'conflictSupervisorOfficer',
  );
});

test('assignTopicConflict lets a supervisor sit as ordinary member when graders remain', () => {
  // a supervisor in a MEMBER seat is allowed only while 3 eligible graders stay
  assert.equal(
    assignTopicConflict({
      topicSupervisorIds: ['lec-1'],
      memberIds: ['lec-1', 'lec-2', 'lec-3', 'lec-4'],
      officerIds: ['lec-2'],
      lecturers: [...LECTURERS, L('lec-4', 'usr-4')],
    }),
    null,
  );
});

test('assignTopicConflict flags a council left under three eligible graders', () => {
  // COUNCIL_SIZE_INVALID — eligibleGraderCount excludes topic supervisors
  assert.equal(
    assignTopicConflict({
      topicSupervisorIds: ['lec-1'],
      memberIds: ['lec-1', 'lec-2', 'lec-3'],
      officerIds: ['lec-2'],
      lecturers: LECTURERS,
    }),
    'conflictTooFewGraders',
  );
});

test('assignTopicConflict flags a topic whose reviewer sits on the council', () => {
  // REVIEWER_MUST_BE_INDEPENDENT — the reviewer may hold any seat
  assert.equal(
    assignTopicConflict({
      topicSupervisorIds: [],
      reviewerId: 'lec-3',
      memberIds: ['lec-1', 'lec-2', 'lec-3'],
      officerIds: ['lec-1'],
      lecturers: LECTURERS,
    }),
    'conflictReviewerOnCouncil',
  );
  // legacy: member row keys the reviewer by User.id
  assert.equal(
    assignTopicConflict({
      topicSupervisorIds: [],
      reviewerId: 'lec-3',
      memberIds: ['lec-1', 'lec-2', 'usr-3'],
      officerIds: ['lec-1'],
      lecturers: LECTURERS,
    }),
    'conflictReviewerOnCouncil',
  );
});

test('assignTopicConflict returns null for a clean assignment', () => {
  assert.equal(
    assignTopicConflict({
      topicSupervisorIds: ['lec-9'],
      reviewerId: 'lec-9',
      memberIds: ['lec-1', 'lec-2', 'lec-3'],
      officerIds: ['lec-1'],
      lecturers: LECTURERS,
    }),
    null,
  );
});
