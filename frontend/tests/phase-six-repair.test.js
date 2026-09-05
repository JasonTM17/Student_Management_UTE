const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function walk(relativeDirectory) {
  const directory = path.join(root, relativeDirectory);
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const relativePath = path.join(relativeDirectory, entry.name);
    if (entry.isDirectory()) return walk(relativePath);
    return /\.(tsx?|jsx?)$/.test(entry.name) ? [relativePath] : [];
  });
}

test('localized CTA links render one anchor with shared button styling', () => {
  const source = walk('src').map(read).join('\n');
  const linkButton = read('src/components/ui/link-button.tsx');

  assert.doesNotMatch(source, /<LocalizedLink\b[^>]*>\s*<Button\b/);
  assert.doesNotMatch(source, /<Button\b[^>]*>\s*<LocalizedLink\b/);
  assert.match(linkButton, /<LocalizedLink[\s\S]*buttonVariants\(\{ size, variant \}\)/);
  assert.doesNotMatch(linkButton, /role=["']button["']/);
});

test('reference loaders use Java API maxima and expose failed lookups', () => {
  const limits = read('src/lib/reference-data.ts');
  const pages = [
    'src/app/admin/semesters/page.tsx',
    'src/app/admin/sections/page.tsx',
    'src/app/admin/courses/page.tsx',
    'src/app/admin/lecturers/page.tsx',
    'src/app/admin/enrollments/page.tsx',
  ].map(read);
  const source = pages.join('\n');

  assert.match(limits, /ACADEMIC_REFERENCE_LIMIT = 200/);
  assert.match(limits, /PEOPLE_REFERENCE_LIMIT = 100/);
  assert.doesNotMatch(source, /limit:\s*1000/);
  assert.match(source, /lecturersApi\.getAll\(\{ limit: PEOPLE_REFERENCE_LIMIT \}\)/);
  for (const page of pages) assert.match(page, /setReferenceError\(/);
  assert.match(pages[1], /Promise\.allSettled/);
  assert.match(pages[4], /setSectionReferenceError\(/);
});

test('admin thesis and home routing distinguish role state explicitly', () => {
  const thesis = read('src/app/admin/thesis/page.tsx');
  const home = read('src/app/page.tsx');

  assert.match(thesis, /if \(!user\) \{[\s\S]*?<ForbiddenState[\s\S]*?href="\/login\?portal=admin"/);
  assert.match(thesis, /if \(!canAccess\) \{[\s\S]*?<ForbiddenState/);
  assert.ok(thesis.indexOf('if (!canAccess)') < thesis.indexOf('if (isLoading && rounds.length === 0)'));
  assert.match(home, /const workspaceHref = isAdmin \|\| isSuperAdmin/);
  assert.match(home, /isLecturer[\s\S]*?'\/dashboard\/lecturer'/);
  assert.doesNotMatch(home, /href=\{user \? '\/admin' : '\/login'\}/);
});

test('course-demo copy excludes retired finance and monitoring language', () => {
  const copy = [
    'src/i18n/messages.ts',
    'src/app/admin/departments/page.tsx',
    'src/app/admin/semesters/page.tsx',
    'src/app/dashboard/lecturer/schedule/page.tsx',
    'src/app/opengraph-image.tsx',
    'public/screenshots/home-en.svg',
    'public/screenshots/home-vi.svg',
  ].map(read).join('\n');

  assert.doesNotMatch(
    copy,
    /\b(?:finance|financial|billing|invoice|payment|reporting|operational)\b|tài chính|hóa đơn|thanh toán|báo cáo|vận hành/i,
  );
});

test('npm test includes smoke, portal, and Phase 6 source regressions', () => {
  const packageJson = JSON.parse(read('package.json'));

  assert.match(packageJson.scripts.test, /frontend-smoke\.test\.js/);
  assert.match(packageJson.scripts.test, /portal-shell\.test\.js/);
  assert.match(packageJson.scripts.test, /phase-six-repair\.test\.js/);
  assert.match(packageJson.scripts.test, /docker-browser-api-origin\.test\.js/);
  assert.doesNotMatch(packageJson.scripts.test, /viewport/);
});

test('the authenticated portal assistant is a complete bottom-right RAG surface', () => {
  const layout = read('src/app/dashboard/layout.tsx');
  const assistant = read('src/components/assistant/AssistantPanel.tsx');
  const messages = read('src/i18n/messages.ts');

  assert.match(layout, /<AssistantPanel \/>/);
  assert.match(assistant, /fixed z-50/);
  assert.match(assistant, /bottom-\[calc\(5\.5rem/);
  assert.match(assistant, /right-4/);
  assert.match(assistant, /role="dialog"/);
  assert.match(assistant, /max-h-\[min\(42rem,calc\(100dvh-6\.5rem-env\(safe-area-inset-bottom\)\)\)\]/);
  assert.match(assistant, /md:max-h-\[min\(42rem,calc\(100dvh-2rem\)\)\]/);
  assert.match(assistant, /role="log"/);
  assert.match(assistant, /aria-live="polite"/);
  assert.match(assistant, /event\.key !== 'Escape'/);
  assert.match(assistant, /launcherRef\.current\?\.focus\(\)/);
  assert.match(assistant, /thesisApi\.chat\(message, locale\)/);
  assert.match(assistant, /KNOWLEDGE_UNAVAILABLE/);
  const assistantMessages = read('src/components/assistant/AssistantMessages.tsx');
  assert.match(assistantMessages, /citation\.title/);
  assert.match(assistantMessages, /citation\.source/);
  assert.match(assistantMessages, /citation\.excerpt/);
  assert.match(assistantMessages, /messages\.assistant\.noMatch/);
  assert.match(assistantMessages, /messages\.assistant\.degraded/);
  assert.match(messages, /Hỏi về đăng ký học phần, lịch học, thông báo, học liệu, chính sách hoặc hành trình luận văn/);
  assert.doesNotMatch(messages, /nhóm hoặc phản biện|groups, or reviews/i);
});

test('the mobile student context drawer traps focus and inerts the portal shell', () => {
  const layout = read('src/app/dashboard/layout.tsx');
  const rail = read('src/components/dashboard/StudentContextRail.tsx');

  assert.match(layout, /studentRailRef/);
  assert.match(layout, /studentRailTriggerRef/);
  assert.match(layout, /studentRailOpen \\|\\| sidebarOpen/);
  assert.match(layout, /querySelectorAll<HTMLElement>\(focusableSelector\)/);
  assert.match(layout, /event\.key !== 'Tab'/);
  assert.match(layout, /aria-controls="student-context-rail"/);
  assert.match(layout, /containerRef=\{studentRailRef\}/);
  assert.match(rail, /ref=\{containerRef\}/);
  assert.match(rail, /id=\{mobile \? 'student-context-rail' : undefined\}/);
});
