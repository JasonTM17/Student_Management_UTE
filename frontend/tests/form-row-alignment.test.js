'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

// Two-column form rows pair a labelled control per cell. With the grid default
// `stretch`, a label that wraps to two lines pushes only its own control down,
// so the row's inputs no longer share a baseline (seen in the propose-topic
// modal where `supervisorSecondaryLabel` wraps but `supervisorPrimaryLabel`
// does not). End-aligning the field blocks (`items-end`) puts the controls on
// one line regardless of label length — the same convention the editor page
// uses on its sm:grid-cols-2 field row.
function slice(source, start, end) {
  const i = source.indexOf(start);
  assert.ok(i >= 0, `marker found: ${start.slice(0, 60)}`);
  const j = end ? source.indexOf(end, i) : source.length;
  assert.ok(j > i, `end marker found after start`);
  return source.slice(i, j);
}

// Every two-column class string inside the slice must end-align its field
// blocks so controls share a baseline even when one label wraps.
function assertTwoColumnRowsEndAlign(block, context, expectedCount) {
  const rows = block.match(/className="[^"]*grid-cols-2[^"]*"/g) ?? [];
  assert.equal(rows.length, expectedCount, `${context}: expected ${expectedCount} two-column row(s)`);
  for (const row of rows) {
    assert.match(row, /\bitems-end\b/, `${context}: ${row} must end-align the field blocks`);
  }
}

test('propose-topic modal field rows keep the controls on one baseline', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');
  const modal = slice(page, '{/* Propose Topic Modal */}', '{/* Edit Topic Modal */}');
  assertTwoColumnRowsEndAlign(modal, 'propose modal', 2);
});

test('edit-topic modal field row keeps the controls on one baseline', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');
  const modal = slice(page, '{/* Edit Topic Modal */}');
  assertTwoColumnRowsEndAlign(modal, 'edit modal', 1);
});

test('announcement form modals keep paired selects on one baseline', () => {
  const editModal = read('src/components/announcements/AnnouncementEditModal.tsx');
  assertTwoColumnRowsEndAlign(
    slice(editModal, 'Metadata Controls', 'Editorial Cover Image Preset Tray'),
    'AnnouncementEditModal',
    1,
  );

  const createModal = read('src/components/announcements/LecturerAnnouncementCreateModal.tsx');
  assertTwoColumnRowsEndAlign(
    slice(createModal, 'Priority & Target Audience', '{/* Title */}'),
    'LecturerAnnouncementCreateModal',
    1,
  );
});

test('the secondary supervisor label really is the longer one in both locales', () => {
  // Guards the premise of the layout contract: the row can only misalign when
  // one label wraps further than its neighbour. If copy ever shrinks to equal
  // length the alignment guard above still has to hold for future labels.
  const messages = read('src/i18n/messages.ts');
  const pick = (key) =>
    (messages.match(new RegExp(`${key}: '([^']*)'`, 'g')) ?? [])
      .map((entry) => entry.slice(entry.indexOf("'") + 1, entry.lastIndexOf("'")));
  const primaries = pick('supervisorPrimaryLabel');
  const secondaries = pick('supervisorSecondaryLabel');
  assert.equal(secondaries.length, primaries.length, 'en and vi must both define supervisor labels');
  assert.ok(primaries.length >= 2, 'expected en and vi supervisor labels');
  primaries.forEach((primary, index) => {
    const secondary = secondaries[index];
    assert.ok(
      secondary.length >= primary.length,
      `secondary supervisor label must be at least as long as the primary one (${primary} vs ${secondary})`,
    );
  });
});
