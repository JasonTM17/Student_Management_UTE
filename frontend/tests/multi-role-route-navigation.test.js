const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function readSource(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('route aliases exist for all previous 404 paths', () => {
  // 1. Student /dashboard/courses alias
  assert.equal(fs.existsSync(path.join(root, 'src/app/dashboard/courses/page.tsx')), true);
  const coursesSource = readSource('src/app/dashboard/courses/page.tsx');
  assert.match(coursesSource, /router\.replace\(href\('\/dashboard\/enrollments'\)\)/);

  // 2. Lecturer /dashboard/lecturer/thesis alias
  assert.equal(fs.existsSync(path.join(root, 'src/app/dashboard/lecturer/thesis/page.tsx')), true);
  const lecturerThesisSource = readSource('src/app/dashboard/lecturer/thesis/page.tsx');
  assert.match(lecturerThesisSource, /router\.replace\(href\('\/dashboard\/thesis'\)\)/);

  // 3. Lecturer /dashboard/lecturer/profile alias
  assert.equal(fs.existsSync(path.join(root, 'src/app/dashboard/lecturer/profile/page.tsx')), true);
  const lecturerProfileSource = readSource('src/app/dashboard/lecturer/profile/page.tsx');
  assert.match(lecturerProfileSource, /router\.replace\(href\('\/dashboard\/profile'\)\)/);

  // 4. Admin /admin/knowledge alias
  assert.equal(fs.existsSync(path.join(root, 'src/app/admin/knowledge/page.tsx')), true);
  const adminKnowledgeSource = readSource('src/app/admin/knowledge/page.tsx');
  assert.match(adminKnowledgeSource, /router\.replace\(href\('\/admin\/assistant-knowledge'\)\)/);
});

test('messages and layout use standardized institutional portal names', () => {
  const messages = readSource('src/i18n/messages.ts');

  // Verify standardized Vietnamese terms
  assert.match(messages, /Cổng Sinh viên/);
  assert.match(messages, /Cổng Giảng viên/);
  assert.match(messages, /Cổng Quản trị/);

  const announcementsPage = readSource('src/app/dashboard/announcements/page.tsx');
  assert.match(announcementsPage, /Dạng Bản tin/);
  assert.match(announcementsPage, /Dạng Công văn/);
  assert.doesNotMatch(announcementsPage, /Tạp chí báo/);
  assert.doesNotMatch(announcementsPage, /Sổ công văn/);

  // Verify raw machine-translated or unstandardized strings are completely eliminated
  assert.doesNotMatch(messages, /Luồng giảng viên/);
  assert.doesNotMatch(messages, /Đăng nhập vào khu sinh viên/);
  assert.doesNotMatch(messages, /Đăng nhập vào khu giảng viên/);
  assert.doesNotMatch(messages, /Đăng nhập vào công cụ quản trị/);
});

test('dashboard upcoming schedule displays standardized room formatting', () => {
  const dashboard = readSource('src/app/dashboard/page.tsx');
  assert.match(dashboard, /Phòng \$\{meeting\.building\}-\$\{meeting\.roomNumber\}/);
  assert.match(dashboard, /Room \$\{meeting\.building\}-\$\{meeting\.roomNumber\}/);
});
