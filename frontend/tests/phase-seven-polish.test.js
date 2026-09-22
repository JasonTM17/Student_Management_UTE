const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function localeHalves(messagesSource) {
  const splitAt = messagesSource.indexOf('export const vi');
  assert.ok(splitAt > 0, 'messages.ts must declare both locales');
  return [messagesSource.slice(0, splitAt), messagesSource.slice(splitAt)];
}

test('register page disabled-button tooltips and rail label are dictionary driven', () => {
  const page = read('src/app/dashboard/register/page.tsx');

  assert.match(page, /copy\.scheduleConflictTooltip/);
  assert.match(page, /copy\.duplicateCourseTooltip/);
  assert.match(page, /copy\.termCreditLimitLabel/);
  // The Vietnamese-only literals must not survive anywhere on the page.
  assert.doesNotMatch(page, /Tr\u00f9ng th\u1eddi kh\u00f3a bi\u1ec3u/);
  assert.doesNotMatch(page, /B\u1ea1n \u0111\u00e3 \u0111\u0103ng k\u00fd m\u1ed9t l\u1edbp/);
  assert.doesNotMatch(page, /\u0110\u1ecbnh m\u1ee9c h\u1ecdc k\u1ef3/);

  const [en, vi] = localeHalves(read('src/i18n/messages.ts'));
  for (const half of [en, vi]) {
    assert.match(half, /scheduleConflictTooltip: '/);
    assert.match(half, /duplicateCourseTooltip: '/);
    assert.match(half, /termCreditLimitLabel: '/);
  }
  assert.match(en, /scheduleConflictTooltip: 'Conflicts with the timetable/);
  assert.match(vi, /scheduleConflictTooltip: 'Tr\u00f9ng th\u1eddi kh\u00f3a bi\u1ec3u/);
});

test('lecturer announcement template picker localizes label, badge, and default title', () => {
  const modal = read('src/components/announcements/LecturerAnnouncementCreateModal.tsx');

  // The preset list itself stays structural (key/icon/priority/content only);
  // visible words come from the dictionary for both locales.
  assert.match(modal, /messages\.announcementTemplates/);
  assert.doesNotMatch(modal, /label: '[^']*[\u00e0-\u017f]/);
  assert.doesNotMatch(modal, /badge: '/);
  assert.doesNotMatch(modal, /defaultTitle: '/);

  const [en, vi] = localeHalves(read('src/i18n/messages.ts'));
  for (const half of [en, vi]) {
    for (const key of ['LEAVE_MAKEUP', 'ASSIGNMENT_DEADLINE', 'EXAM_SCHEDULE', 'COURSE_GENERAL']) {
      assert.match(half, new RegExp(`${key}: \\{`));
    }
    assert.match(half, /announcementTemplates: \{/);
  }
  assert.match(en, /label: 'Class cancellation & makeup session'/);
  assert.match(vi, /label: 'Ngh\u1ec9 h\u1ecdc & H\u1ecdc b\u00f9'/);
});

test('admin analytics charts enforce live honesty and use tone tokens', () => {
  const charts = read('src/components/admin/AdminAnalyticsCharts.tsx');
  const [en, vi] = localeHalves(read('src/i18n/messages.ts'));

  // Zero-mock honesty rule: live data uses liveNotice and liveBadge, never falls back
  // to misleading "sample data" badges, and false "real-time" badge stays gone.
  assert.match(charts, /messages\.admin\.analytics/);
  assert.match(charts, /copy\.liveNotice/);
  assert.match(charts, /copy\.liveBadge/);
  assert.doesNotMatch(charts, /copy\.illustrativeBadge/);
  assert.doesNotMatch(charts, /copy\.illustrativeNotice/);
  assert.doesNotMatch(charts, /Th\u1eddi gian th\u1ef1c/);
  for (const half of [en, vi]) {
    assert.match(half, /liveBadge:\s*'/);
    assert.match(half, /liveNotice:\s*'/);
  }

  // Shared number formatter instead of hardcoded locales.
  assert.doesNotMatch(charts, /toLocaleString\(/);
  assert.match(charts, /formatNumber\(/);

  // DESIGN.md: no raw Tailwind palettes on shared primitives.
  assert.doesNotMatch(charts, /bg-(blue|slate|gray|emerald|red|amber|violet|yellow)-\d/);
  assert.match(charts, /statusToneClass|bg-status-info|bg-status-danger/);
});

test('shared form primitives expose aria-invalid and aria-describedby', () => {
  const input = read('src/components/ui/input.tsx');
  const select = read('src/components/ui/select.tsx');

  for (const primitive of [input, select]) {
    assert.match(primitive, /aria-invalid=/);
    assert.match(primitive, /aria-describedby=/);
    assert.match(primitive, /errorId/);
  }

  // Submit-level errors on the profile form announce themselves.
  const profile = read('src/app/dashboard/profile/page.tsx');
  assert.match(profile, /role="alert" aria-live="assertive"/);
});

test('listed date renderings route through the locale formatter', () => {
  const editor = read('src/app/dashboard/editor/page.tsx');
  const certificates = read('src/app/dashboard/certificates/page.tsx');
  const dispatchSheet = read('src/components/announcements/reader/layouts/AdministrativeDispatchSheet.tsx');
  const assistantMessages = read('src/components/assistant/AssistantMessages.tsx');

  assert.match(editor, /formatDate\(ann\.createdAt\)/);
  assert.doesNotMatch(editor, /toLocaleDateString\('vi-VN'\)/);

  assert.match(certificates, /formatDate\(today, \{ month: 'long'/);
  assert.doesNotMatch(certificates, /toLocaleDateString\('en-US'/);

  assert.match(dispatchSheet, /useI18n\(\)/);
  assert.match(dispatchSheet, /formatDate\(dateObj\)/);
  assert.match(dispatchSheet, /formatDateTime\(dateObj\)/);
  assert.doesNotMatch(dispatchSheet, /toLocaleTimeString\('vi-VN'/);

  assert.match(assistantMessages, /formatDateTime/);
  assert.doesNotMatch(assistantMessages, /'en-GB'/);
});

test('the brand string has one source and CampusCore is gone from user surfaces', () => {
  const messages = read('src/i18n/messages.ts');
  const [en, vi] = localeHalves(messages);
  for (const half of [en, vi]) {
    assert.match(half, /siteName: 'CampusUTE'/);
  }

  for (const file of [
    'src/app/page.tsx',
    'src/app/error.tsx',
    'src/components/ui/rich-text-editor.tsx',
  ]) {
    const source = read(file);
    assert.doesNotMatch(source, /CampusCore/);
    assert.match(source, /siteName|SITE_NAME/);
  }
});

test('dead dictionary keys stay deleted while dynamically indexed statuses remain', () => {
  const messages = read('src/i18n/messages.ts');
  const deadKeys = [
    'requestNewResetLink',
    'tryAnotherEmail',
    'reviewProfileSettings',
    'openView',
    'continueToWorkspace',
    'signInToWorkspace',
    'reviewAdmin',
    'backToSignIn',
    'switchToEnglish',
    'switchToVietnamese',
    'snapshotTitle',
    'snapshotChecks',
    'snapshotPrimaryAccessTitle',
    'snapshotPrimaryAccessDescription',
    'snapshotReleaseTitle',
    'snapshotReleaseDescription',
    'capabilitiesEyebrow',
    'capabilitiesTitle',
    'capabilitiesDescription',
  ];
  for (const key of deadKeys) {
    assert.doesNotMatch(messages, new RegExp(`\\b${key}\\s*:`), key);
  }

  // statusLabel() lookups index `common.statuses` with API enum values, so
  // these keys are reachable and must stay.
  for (const key of ['INACTIVE:', 'ALL_GRADED:', 'NOT_GRADED:', 'APPEALED:']) {
    assert.match(messages, new RegExp(key));
  }
});

test('theme resolves the OS preference pre-hydration and follows the stored choice', () => {
  const layout = read('src/app/layout.tsx');
  const provider = read('src/components/ThemeProvider.tsx');
  const applyTheme = read('src/lib/apply-theme.ts');

  // Bootstrap script: stored choice, then prefers-color-scheme fallback, then
  // the meta theme-color sync.
  assert.match(layout, /prefers-color-scheme: dark/);
  assert.match(layout, /getElementsByName\('theme-color'\)/);
  assert.equal(layout.match(/dangerouslySetInnerHTML/g).length, 1);

  assert.match(applyTheme, /systemPreferredTheme/);
  assert.match(provider, /systemPreferredTheme\(\)/);
});

test('dead shared primitive stays deleted and empty states use the canonical block', () => {
  assert.equal(fs.existsSync(path.join(root, 'src/components/ui/data-table.tsx')), false);

  const knowledge = read('src/app/admin/assistant-knowledge/page.tsx');
  const schedule = read('src/app/dashboard/schedule/page.tsx');
  const lecturerSchedule = read('src/app/dashboard/lecturer/schedule/page.tsx');
  for (const page of [knowledge, schedule, lecturerSchedule]) {
    assert.match(page, /EmptyState/);
    assert.doesNotMatch(page, /py-12 text-center/);
  }
});

test('grade profile view collapses below sm and lets the chart shrink', () => {
  const view = read('src/components/dashboard/StudentUteProfileGradeView.tsx');
  assert.doesNotMatch(view, /grid grid-cols-5/);
  assert.doesNotMatch(view, /grid grid-cols-4/);
  assert.match(view, /sm:grid-cols-5/);
  assert.match(view, /sm:grid-cols-4/);
  assert.doesNotMatch(view, /min-w-\[420px\]/);
});
