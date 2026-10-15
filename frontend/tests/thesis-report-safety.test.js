const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

const api = read('src/lib/thesis-api.ts');
const page = read('src/app/dashboard/thesis/page.tsx');
const adminPage = read('src/app/admin/thesis/page.tsx');
const messages = read('src/i18n/messages.ts');

test('report editing uses the non-destructive PATCH and detach endpoints', () => {
  // PATCH metadata/link edit + DELETE file detach — the file bytes and the
  // submitted_* audit stamp survive both (pinned contract, F4/F7).
  assert.match(api, /updateReport/);
  assert.match(api, /api\.patch<ThesisGroupReport>/);
  assert.match(api, /deleteReportFile/);
  assert.match(api, /api\.delete<ThesisGroupReport>/);
  assert.match(api, /\/thesis\/groups\/' \+ groupId \+ '\/report\/file/);
  assert.match(page, /thesisApi\.updateReport\(currentGroup\.id/);
  assert.match(page, /thesisApi\.deleteReportFile\(currentGroup\.id\)/);
});

test('the metadata PATCH path is never wrapped in the destructive confirm', () => {
  // Only a fresh file replacing a stored document earns the destructive
  // dialog. Slice the existing-report no-file branch of handleSubmitReport
  // and prove it reaches updateReport without any confirm() inside.
  const submitStart = page.indexOf('const handleSubmitReport');
  assert.ok(submitStart > -1, 'handleSubmitReport must exist');
  const patchStart = page.indexOf('} else if (groupReport) {', submitStart);
  assert.ok(patchStart > -1, 'the existing-report PATCH branch must exist');
  const patchEnd = page.indexOf('} else {', patchStart);
  assert.ok(patchEnd > patchStart, 'the first-submission else branch must follow');
  const patchBranch = page.slice(patchStart, patchEnd);
  assert.match(patchBranch, /thesisApi\.updateReport/);
  assert.doesNotMatch(patchBranch, /confirm\(/);
});

test('a file-only report can be edited without providing a link', () => {
  // The old gate demanded a URL even when a stored document already satisfied
  // the artifact invariant — the impossible-edit dead-end (F7).
  assert.match(page, /!url && !groupReport\?\.url && !groupReport\?\.fileName/);
});

test('admin close-registration confirm is backed by the server preview', () => {
  assert.match(api, /closeRegistrationPreview/);
  assert.match(api, /\/thesis\/rounds\/' \+ roundId \+ '\/close-registration-preview/);
  // The preview must load before the confirm dialog opens, and the actual
  // close call comes after the confirm resolves.
  const previewAt = adminPage.indexOf('closeRegistrationPreview(roundId)');
  const confirmAt = adminPage.indexOf('const confirmed = await confirm({', previewAt);
  const closeAt = adminPage.indexOf('thesisApi.closeRegistration(roundId)');
  assert.ok(
    previewAt > -1 && confirmAt > previewAt && closeAt > confirmAt,
    'preview fetch must precede the confirm dialog and the close transition',
  );
  // A preview read failure still allows closing — never block on a read.
  assert.match(adminPage, /\.catch\(\(\) => null\)/);
  // A passed report deadline fills the dialog warning slot.
  assert.match(adminPage, /closePreviewDeadlinePassed/);
});

test('admin exposes the report-deadline escape hatch on closed rounds', () => {
  assert.match(api, /extendReportDeadline/);
  assert.match(api, /\/thesis\/rounds\/' \+ roundId \+ '\/report-deadline/);
  assert.match(adminPage, /thesisApi\.extendReportDeadline\(/);
  assert.match(adminPage, /reportDeadlineExtend/);
});

test('student page keeps a persistent membership banner pointing at the group round', () => {
  // F3: membership is resolved server-side (myGroups) and a banner always
  // offers the way back when the viewed round holds no group of the reader.
  assert.match(page, /thesisApi\s*\.myGroups\(\)/);
  assert.match(page, /pickPreferredThesisRound\(rounds, myGroups\)/);
  assert.match(page, /membershipRoundId !== selectedRoundId/);
  assert.match(page, /membershipRoundHint/);
  assert.match(page, /membershipRoundOpen/);
  assert.equal(
    (messages.match(/membershipRoundHint:/g) || []).length,
    2,
    'membershipRoundHint must be localized in en + vi',
  );
});

test('the report window renders four honest states', () => {
  // notYetOpen / open / deadlinePassed / closedFinal — a published round must
  // never again read "opens after registration closes" (F7).
  assert.match(page, /'notYetOpen' \| 'open' \| 'deadlinePassed' \| 'closedFinal'/);
  assert.match(page, /reportWindowState === 'closedFinal'/);
  assert.match(page, /closedFinalTitle/);
  assert.equal(
    (messages.match(/closedFinalTitle:/g) || []).length,
    2,
    'closedFinalTitle must be localized in en + vi',
  );
});
