const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const read = (relative) => fs.readFileSync(path.join(root, relative), 'utf8');

const PAGE = 'src/app/admin/sections/page.tsx';
const MESSAGES = 'src/i18n/messages.ts';

test('disabled course and semester selects explain the enrollment lock', () => {
  const page = read(PAGE);
  // Both selects stay disabled while the section has enrolled students.
  const lockCondition = /editingSection != null && \(editingSection\.enrolledCount \?\? 0\) > 0/;
  assert.ok((page.match(new RegExp(lockCondition.source, 'g')) ?? []).length >= 3,
    'course select, semester select and the helper line share the lock condition');
  // A visible line under the selects names the reason — not a tooltip.
  assert.match(page, /editingSection\.enrolledCount \?\? 0\) > 0 \? \(\s*<p className="[^"]*text-muted-foreground">\s*\{messages\.common\.campusErrors\.codes\.SECTION_HAS_ENROLLMENTS\}/);
});

test('the helper reuses the localized SECTION_HAS_ENROLLMENTS wording', () => {
  const messages = read(MESSAGES);
  assert.match(
    messages,
    /SECTION_HAS_ENROLLMENTS:\s*'Course and semester cannot change once students are enrolled in the section\.'/,
  );
  assert.match(
    messages,
    /SECTION_HAS_ENROLLMENTS:\s*'Không thể đổi môn học hoặc học kỳ khi lớp học phần đã có sinh viên đăng ký\.'/,
  );
});
