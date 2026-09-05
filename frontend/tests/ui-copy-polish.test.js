const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('student status badges use localized labels instead of raw service enums', () => {
  const dashboard = read('src/app/dashboard/page.tsx');

  assert.match(dashboard, /enrollmentStatusLabel/);
  assert.match(dashboard, /messages\.common\.statuses/);
  assert.doesNotMatch(dashboard, /\{enrollment\.status\}/);
});

test('assistant never exposes model identifiers or unknown reason codes', () => {
  const assistantPanel = read('src/components/assistant/AssistantPanel.tsx');
  const assistantMessages = read('src/components/assistant/AssistantMessages.tsx');

  assert.match(assistantMessages, /message\.reasonCode === 'CANCELLED'/);
  assert.match(assistantMessages, /messages\.assistant\.answered/);
  for (const assistant of [assistantPanel, assistantMessages]) {
    assert.doesNotMatch(assistant, /messages\.assistant\.model\}: \{message\.model\}/);
    assert.doesNotMatch(assistant, /: message\.reasonCode;/);
  }
});

test('visible portal copy avoids known implementation jargon', () => {
  const messages = read('src/i18n/messages.ts');
  const forbiddenVisiblePhrases = [
    'workspace services',
    'dịch vụ workspace',
    'giao diện workspace',
    'demo local',
    'database đồ án',
    'client tập trung',
    'Reset token',
    'Token hết hạn',
    'Management console',
    'curated retrieval boundary',
    'AI assistant knowledge',
    'Chrome campus',
    'owner của',
    'broadcast chính',
  ];

  for (const phrase of forbiddenVisiblePhrases) {
    assert.doesNotMatch(messages, new RegExp(phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'), phrase);
  }
});

test('student and admin surfaces keep copy task-focused', () => {
  const files = [
    'src/app/dashboard/enrollments/page.tsx',
    'src/app/dashboard/schedule/page.tsx',
    'src/app/dashboard/announcements/page.tsx',
    'src/app/dashboard/lecturer/announcements/page.tsx',
    'src/app/admin/assistant-knowledge/page.tsx',
    'src/app/admin/semesters/page.tsx',
    'src/app/admin/sections/page.tsx',
    'src/app/admin/departments/page.tsx',
    'src/app/admin/courses/page.tsx',
    'src/app/admin/lecturers/page.tsx',
    'src/app/dashboard/lecturer/grades/page.tsx',
    'src/app/dashboard/lecturer/page.tsx',
    'src/app/dashboard/lecturer/schedule/page.tsx',
    'src/app/dashboard/grades/page.tsx',
    'src/app/dashboard/transcript/page.tsx',
    'src/app/admin/users/page.tsx',
    'src/app/dashboard/lecturer/grades/[id]/page.tsx',
    'src/components/thesis/StatusBadge.tsx',
  ];
  const source = files.map(read).join('\n');
  const retiredCopy = [
    'Workspace sinh viên',
    'Student workspace',
    'Không gian sinh viên',
    'protected student workspace',
    'workspace dùng chung',
    'section status',
    'catalog môn học',
    'ownership rõ ràng',
    'metadata học thuật',
    'timeline học thuật',
    'curated sources',
    'Public catalog coverage',
    'status drift',
    'Workspace giảng viên',
    'Lecturer workspace',
    'Trạng thái section',
    'Section status',
    'Không gian giảng viên',
    'workspace giảng dạy',
    'Shared notices for your teaching workspace',
  ];

  for (const phrase of retiredCopy) {
    assert.doesNotMatch(source, new RegExp(phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'), phrase);
  }
  assert.doesNotMatch(source, /\{(?:enrollment|section|semester)\.status\}/);
  assert.doesNotMatch(source, /\{(?:record|enrollment)\.(?:status|gradeStatus|enrollmentStatus)\}/);
  assert.doesNotMatch(source, /\{sectionData\.status/);
});

test('enrollment and thesis status fallbacks stay human-readable', () => {
  const enrollmentPage = read('src/app/admin/enrollments/page.tsx');
  assert.match(enrollmentPage, /ENROLLED: 'Đã đăng ký'/);
  assert.match(enrollmentPage, /ENROLLED: 'Registered'/);
  assert.match(enrollmentPage, /messages\.common\.statuses\.UNKNOWN/);
  assert.doesNotMatch(enrollmentPage, /\?\?\s*(?:enrollment|selectedEnrollment)\.status/);

  const thesisPages = [
    read('src/components/dashboard/thesis/useThesisWorkspace.ts'),
    read('src/app/dashboard/thesis/page.tsx'),
    read('src/app/dashboard/thesis/[roundId]/page.tsx'),
  ].join('\n');
  assert.match(thesisPages, /messages\.common\.statuses\.UNKNOWN/);
  assert.doesNotMatch(thesisPages, /messages\.thesis\.status\[[^\n]+\]\s*\?\?\s*status/);
});
