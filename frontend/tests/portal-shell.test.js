const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('portal tokens and page ribbon define the institutional visual grammar', () => {
  const globals = read('src/app/globals.css');
  const pageHeader = read('src/components/ui/page-header.tsx');

  assert.match(globals, /--portal-sidebar:\s*oklch\(/);
  assert.match(globals, /--portal-yellow:\s*oklch\(/);
  assert.match(globals, /--portal-canvas:\s*oklch\(/);
  assert.match(globals, /\.portal-page-ribbon/);
  assert.match(globals, /prefers-reduced-motion:\s*reduce/);
  assert.match(pageHeader, /portal-page-ribbon/);
  assert.match(pageHeader, /portal-page-actions/);
});

test('student and lecturer routes share one accessible responsive sidebar', () => {
  const layout = read('src/app/dashboard/layout.tsx');

  assert.match(layout, /studentMenuSections/);
  assert.match(layout, /lecturerMenuSections/);
  assert.match(layout, /portal-sidebar/);
  assert.match(layout, /aria-current=\{isActive \? 'page'/);
  assert.match(layout, /inert=\{!isDesktopSidebar && !sidebarOpen/);
  assert.match(layout, /id="dashboard-main-content"/);
  assert.match(layout, /2xl:grid-cols/);
  assert.doesNotMatch(layout, /mobileMenuItems/);
  assert.doesNotMatch(
    layout,
    /dashboard\/invoices|dashboard\/thesis\/(councils|evaluation|reviews)/,
  );
});

test('admin routes use the same fixed sidebar and retained route inventory', () => {
  const frame = read('src/components/admin/AdminFrame.tsx');

  assert.match(frame, /adminMenuSections/);
  assert.match(frame, /id="admin-sidebar"/);
  assert.match(frame, /lg:pl-\[var\(--portal-sidebar-width\)\]/);
  assert.match(frame, /aria-controls="admin-sidebar"/);
  assert.match(frame, /id="admin-main-content"/);
  assert.match(frame, /\/admin\/announcements/);
  assert.doesNotMatch(frame, /\/admin\/(analytics|invoices|support)/);
  assert.doesNotMatch(frame, /\/admin\/thesis\/(councils|evaluation|reviews)/);
});

test('shared states and notification tabs preserve keyboard and feedback semantics', () => {
  const states = read('src/components/ui/state-block.tsx');
  const notifications = read('src/components/dashboard/NotificationsCenterPage.tsx');
  const button = read('src/components/ui/button.tsx');
  const input = read('src/components/ui/input.tsx');

  assert.match(states, /role="status"/);
  assert.match(states, /aria-live="polite"/);
  assert.match(states, /role="alert"/);
  assert.match(states, /export function ForbiddenState/);
  assert.match(notifications, /role="tablist"/);
  assert.match(notifications, /onKeyDown=\{\(event\) => moveFilterFocus/);
  assert.match(notifications, /role="tabpanel"/);
  assert.match(notifications, /min-h-11/);
  assert.match(button, /icon: 'h-11 w-11'/);
  assert.match(input, /text-base[\s\S]*sm:text-sm/);
});
