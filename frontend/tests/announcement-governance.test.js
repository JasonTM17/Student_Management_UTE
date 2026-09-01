const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('admin announcements expose governed edit, history, and lifecycle flows', () => {
  const page = read('src/app/admin/announcements/page.tsx');

  assert.match(page, /announcementsApi\.update/);
  assert.match(page, /announcementsApi\.archive/);
  assert.match(page, /announcementsApi\.restore/);
  assert.match(page, /announcementsApi\.history/);
  assert.match(page, /expectedVersion/);
  assert.match(page, /maxLength=\{500\}/);
  assert.match(page, /previewRole/);
  assert.match(page, /announcementHistoryActionLabel\(entry\.action/);
  assert.match(page, /entry\.actorLabel/);
  assert.match(page, /announcementHistoryReason\(entry\.reason/);
  assert.match(page, /selectionUnavailable/);
  assert.doesNotMatch(page, /window\.prompt/);
  assert.doesNotMatch(page, /announcementsApi\.delete/);
  assert.doesNotMatch(page, /\{entry\.action\}/);
});

test('announcement presenter never falls back to a raw service enum', () => {
  const presenter = read('src/lib/announcement-presentation.ts');

  assert.match(presenter, /\['Khác', 'Other'\]/);
  assert.match(presenter, /\['Đã thay đổi', 'Changed'\]/);
  assert.match(presenter, /announcementPriorityLabel/);
  assert.match(presenter, /announcementRoleLabel/);
  assert.match(presenter, /announcementHistoryReason/);
});

test('student and lecturer feeds use shared labels and updated markers', () => {
  for (const relativePath of [
    'src/app/dashboard/announcements/page.tsx',
    'src/app/dashboard/lecturer/announcements/page.tsx',
  ]) {
    const page = read(relativePath);
    assert.match(page, /announcementPriorityLabel/);
    assert.match(page, /announcementIsUpdated/);
    assert.doesNotMatch(page, /\{announcement\.priority\}/);
  }
});

test('announcement API carries lifecycle and audit contracts', () => {
  const api = read('src/lib/api.ts');

  assert.match(api, /version\?: number/);
  assert.match(api, /archivedAt\?: string \| null/);
  assert.match(api, /actorLabel\?: string \| null/);
  assert.match(api, /\/archive/);
  assert.match(api, /\/restore/);
  assert.match(api, /\/history/);
});

test('shared modal keeps typing focus when a parent callback changes', () => {
  const modal = read('src/components/ui/modal.tsx');

  assert.match(modal, /onCloseRef\.current\(\)/);
  assert.match(modal, /\}, \[isOpen\]\);/);
  assert.match(modal, /re-running this effect would steal focus/);
});
