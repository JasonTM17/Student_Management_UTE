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

test('dual-domain announcement reader coordinates editorial magazine and official dispatch layouts', () => {
  const reader = read('src/components/announcements/AnnouncementReaderModal.tsx');
  const toolbar = read('src/components/announcements/reader/ReadingToolbar.tsx');
  const progressBar = read('src/components/announcements/reader/ReadingProgressBar.tsx');
  const magazine = read('src/components/announcements/reader/layouts/EditorialArticleMagazine.tsx');
  const dispatch = read('src/components/announcements/reader/layouts/AdministrativeDispatchSheet.tsx');
  const toc = read('src/components/announcements/reader/TableOfContents.tsx');
  const attachments = read('src/components/announcements/reader/DocumentAttachmentsList.tsx');
  const feedCard = read('src/components/announcements/feed/AnnouncementFeedCard.tsx');
  const homeSection = read('src/components/home/HomeNewsSection.tsx');
  const homePage = read('src/app/page.tsx');

  // Reader master coordinator
  assert.match(reader, /resolveAnnouncementDomain/);
  assert.match(reader, /ReadingToolbar/);
  assert.match(reader, /ReadingProgressBar/);
  assert.match(reader, /EditorialArticleMagazine/);
  assert.match(reader, /AdministrativeDispatchSheet/);

  // ReadingToolbar controls
  assert.match(toolbar, /EDITORIAL/);
  assert.match(toolbar, /OFFICIAL/);
  assert.match(toolbar, /sepia/);
  assert.match(toolbar, /serif/);
  assert.match(toolbar, /handleDecreaseFont/);
  assert.match(toolbar, /handleIncreaseFont/);

  // Layouts & Utilities
  assert.match(magazine, /RichContentRenderer/);
  assert.match(magazine, /DocumentAttachmentsList/);
  assert.match(magazine, /TableOfContents/);
  assert.match(dispatch, /ShieldCheck/);
  assert.match(dispatch, /eSealOrg/);
  assert.match(toc, /extractTocHeadings/);
  assert.match(attachments, /extractAttachmentsFromContent/);

  // Feed card & Homepage
  assert.match(feedCard, /aspect-video/);
  assert.match(feedCard, /calculateReadingTime/);
  assert.match(feedCard, /extractAnnouncementExcerpt/);
  assert.match(homeSection, /HomeNewsSection/);
  assert.match(homeSection, /AnnouncementReaderModal/);
  assert.match(homePage, /<HomeNewsSection \/>/);
});

