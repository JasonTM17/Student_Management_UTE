const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

test('thesis repository uses the server-owned projection and opaque download route', () => {
  const api = read('src/lib/thesis-api.ts');
  const page = read('src/app/dashboard/thesis/page.tsx');

  assert.match(api, /listRoundRepository/);
  assert.match(api, /\/thesis\/rounds\/.*\/repository/);
  assert.match(api, /downloadRepositoryReportFile/);
  assert.match(api, /\/thesis\/reports\/.*\/file/);
  assert.match(page, /listRoundRepository\(selectedRoundId\)/);
  assert.match(page, /report\.reportId/);
  assert.doesNotMatch(page, /report\.submittedBy\b/);
  assert.doesNotMatch(page, /groups\.find\(\(g\) => g\.id === report\.groupId\)/);
});

test('thesis progress renders evidence-backed backend milestones', () => {
  const api = read('src/lib/thesis-api.ts');
  const page = read('src/components/dashboard/thesis/ThesisProgressPage.tsx');

  assert.match(api, /getMyProgress/);
  assert.match(api, /\/thesis\/me\/progress/);
  assert.match(page, /PROGRESS_MILESTONES/);
  assert.match(page, /thesisApi\.getMyProgress\(selectedRound\.id\)/);
  assert.doesNotMatch(page, /getProgressIndex/);
});

test('report submission UI exposes writing controls only to the group leader', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');
  assert.match(page, /isGroupLeader \? \(/);
  assert.match(page, /submitReportFile/);
  assert.match(page, /submitReport\(currentGroup\.id/);
});

test('faculty-head role reaches the thesis administration portal', () => {
  const auth = read('src/lib/login-portal.ts');
  const login = read('src/app/login/page.tsx');
  const adminPage = read('src/app/admin/thesis/page.tsx');
  const workspace = read('src/app/dashboard/thesis/page.tsx');

  assert.match(auth, /TRUONG_KHOA/);
  assert.match(login, /TRUONG_KHOA/);
  assert.match(login, /\/admin\/thesis/);
  assert.match(adminPage, /isFacultyHead/);
  assert.match(workspace, /isFacultyHead/);
});

test('global thesis workspaces do not call the lecturer-only workload endpoint', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');

  assert.match(page, /if \(!isLecturer \|\| !selectedRoundId\) return;/);
  assert.match(page, /thesisApi\s*\.myWorkload\(\)/);
});

test('report window lock keeps the backend REPORT_WINDOW_CLOSED code localized', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');
  const messages = read('src/i18n/messages.ts');

  // ThesisReportService answers 409 REPORT_WINDOW_CLOSED until the round
  // reaches REGISTRATION_CLOSED: the panel must map that code and the
  // dictionary must keep both the English and Vietnamese copies.
  assert.ok(
    page.includes('REPORT_WINDOW_CLOSED') &&
      (messages.match(/REPORT_WINDOW_CLOSED:/g) || []).length === 2,
    'REPORT_WINDOW_CLOSED must be mapped by the thesis page and localized in en + vi',
  );
});
