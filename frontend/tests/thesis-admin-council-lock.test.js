const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// The admin round card must not offer council mutations the backend can only
// refuse: ThesisCouncilService.lockMutableRound rejects every council change
// once the round is CANCELLED, CLOSED or RESULTS_PUBLISHED, so an enabled
// control is always a dead affordance. Source assertions pin the gate the same
// way tests/thesis-round-delete.test.js pins the delete contract.
const page = read('src/app/admin/thesis/page.tsx');

test('the admin round card derives a council lock from the terminal statuses', () => {
  assert.match(page, /const councilLocked =\s*round\.status === 'CANCELLED'[\s\S]{0,160}round\.status === 'RESULTS_PUBLISHED'/,
    'councilLocked must cover CANCELLED, CLOSED and RESULTS_PUBLISHED');
});

for (const control of [
  'councils\\.createCouncil',
  'councils\\.addMember',
  'councils\\.replaceMember',
  'councils\\.assignTopic',
]) {
  test(`${control} only renders while the round accepts council changes`, () => {
    const re = new RegExp(`!councilLocked[\\s\\S]{0,900}${control}`);
    assert.match(page, re, `${control} is reachable without the council lock`);
  });
}

for (const control of [
  'councils\\.removeMember',
  'admin\\.assignReviewer',
]) {
  test(`${control} only renders while the round accepts council changes`, () => {
    const re = new RegExp(`!councilLocked[\\s\\S]{0,2200}${control}`);
    assert.match(page, re, `${control} is reachable without the council lock`);
  });
}

// The assigned-topic action row nests each button behind its own
// `top.finalScore == null && !councilLocked` guard; pin every guard and the
// message key rendered inside it.
const topicGates = [...page.matchAll(/\{top\.finalScore == null && !councilLocked/g)];
test('every assigned-topic action block is behind the council lock', () => {
  assert.equal(topicGates.length, 3,
    'unassign, waive/revoke and withdraw/reinstate blocks must each be gated');
});

for (const [gateIdx, control] of [
  [0, 'councils\\.unassignTopic'],
  [1, 'admin\\.(waiveGvpb|revokeGvpbWaiver)'],
  [2, 'admin\\.(withdrawTopic|reinstateTopic)'],
]) {
  test(`${control} sits inside its gated topic block`, () => {
    const start = topicGates[gateIdx].index;
    const end = topicGates[gateIdx + 1]?.index ?? page.length;
    assert.match(page.slice(start, end), new RegExp(control),
      `${control} is not rendered inside gated block ${gateIdx}`);
  });
}
