const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

test('thesis API mirrors the backend eligibility read model', () => {
  const api = read('src/lib/thesis-api.ts');

  // RoundResponse.eligibility is student-scoped; GroupMemberResponse.eligible
  // flags a member who misses the credit/CPA bar. `enforced` tells the client
  // whether the backend currently refuses on this standing (ENFORCE on a
  // covered round type) or only warns (ADVISORY).
  assert.match(api, /export interface ThesisEligibility\b/);
  assert.match(api, /eligibility\?: ThesisEligibility \| null/);
  assert.match(api, /eligible\?: boolean \| null/);
  assert.match(api, /earnedCredits: number/);
  assert.match(api, /requiredCredits: number/);
  assert.match(api, /reasons: string\[\]/);
  assert.match(api, /enforced\?: boolean/);
});

test('student thesis page renders the eligibility card and gates create', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');

  // The card is a status region (not colour-only) fed by the round's
  // caller-scoped eligibility; the create affordance disappears when the
  // caller is ineligible instead of firing a doomed POST — but only while
  // enforcement is on. In ADVISORY the card warns and create stays offered.
  assert.match(page, /role="status"/);
  assert.match(page, /eligibility\.eligible \?/);
  assert.match(page, /eligibilityTitle/);
  assert.match(page, /eligibilityCredits/);
  assert.match(page, /eligibilityCpa/);
  assert.match(page, /eligibilityAction/);
  assert.match(page, /eligibility\?\.eligible === false/);
  assert.match(page, /eligibility\?\.enforced === true/);
  assert.doesNotMatch(page, /eligibility\?\.eligible !== false/);
  assert.match(page, /href="\/dashboard\/register"/);
});

test('member lists flag ineligible members with a text chip', () => {
  const studentCard = read('src/features/thesis/components/StudentGroupCard.tsx');
  const supervised = read('src/components/dashboard/thesis/SupervisedGroupMembers.tsx');

  for (const source of [studentCard, supervised]) {
    assert.match(source, /member\.eligible === false/);
    assert.match(source, /memberIneligibleBadge/);
  }
});

test('topic choice refuses the ineligible caller only under ENFORCE', () => {
  const detail = read('src/components/dashboard/thesis/ThesisTopicDetailPage.tsx');
  assert.match(detail, /eligibility\?\.eligible === false/);
  assert.match(detail, /eligibility\?\.enforced === true/);
  assert.doesNotMatch(detail, /eligibility\?\.eligible !== false/);
  assert.match(detail, /callerEligible/);
});

test('THESIS_ELIGIBILITY_NOT_MET is localized in en and vi', () => {
  const messages = read('src/i18n/messages.ts');
  assert.equal(
    (messages.match(/THESIS_ELIGIBILITY_NOT_MET:/g) || []).length,
    2,
    'the stable backend code needs one English and one Vietnamese copy',
  );
  for (const key of [
    'eligibilityTitle',
    'eligibilityEligible',
    'eligibilityIneligible',
    'eligibilityCredits',
    'eligibilityCpa',
    'eligibilityCpaMissing',
    'eligibilityAction',
    'memberIneligibleBadge',
  ]) {
    assert.equal(
      (messages.match(new RegExp(`${key}:`, 'g')) || []).length,
      2,
      `${key} needs en + vi copies`,
    );
  }
});
