const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('public home and login copy stays campus-facing', () => {
  const home = read('src/app/page.tsx');
  const login = read('src/app/login/page.tsx');
  const messages = read('src/i18n/messages.ts');
  const rendered = `${home}\n${login}\n${messages}`;

  assert.match(home, /messages\.home\.publicProof/);
  assert.doesNotMatch(home, /whyPoints|pillars\[2\]/);
  assert.match(messages, /publicProof:/);
  assert.match(messages, /One campus portal/);
  assert.match(messages, /Một cổng học vụ/);
  assert.doesNotMatch(login, /Stay signed in for the day|sessionTitle|KeyRound/);
  assert.doesNotMatch(messages, /sessionBehaviorTitle|sessionTitle:/);
  assert.doesNotMatch(
    rendered,
    /sessionBehaviorTitle:[\s\S]{0,40}One steady session|sessionBehaviorTitle:[\s\S]{0,80}Một phiên làm việc liền mạch/,
  );
  assert.doesNotMatch(messages, /whyPoints:[\s\S]*?\/api\/v1/);
  assert.doesNotMatch(messages, /whyPoints:[\s\S]*?OpenAPI/);
  assert.doesNotMatch(messages, /whyPoints:[\s\S]*?Flyway/);
  assert.doesNotMatch(rendered, /Java API|RESTful|PostgreSQL|OpenAPI|Flyway|\/api\/v1/);
  assert.match(messages, /campusErrors:/);
});

test('public homepage keeps navy chrome so dark mode cannot invert the hero panel', () => {
  const home = read('src/app/page.tsx');

  assert.match(home, /messages\.home\.eyebrow/);
  assert.match(home, /variant="warm"/);
  assert.match(home, /LanguageToggle inverse/);
  assert.match(home, /HomeIdentityBoard/);
  const board = read('src/components/home/HomeIdentityBoard.tsx');
  assert.match(board, /role="tab"/);
  assert.match(board, /lecturerIdentityRows/);
  assert.match(board, /adminIdentityRows/);
  assert.doesNotMatch(home, /lg:min-h-\[calc\(100dvh-4rem\)\]/);
  assert.match(home, /bg-\[var\(--portal-sidebar\)\]/);
  assert.match(home, /text-\[var\(--portal-sidebar-text\)\]/);
  assert.doesNotMatch(home, /aside className="[^"]*bg-primary/);
  assert.doesNotMatch(home, /section className="bg-primary text-primary-foreground"/);
  assert.doesNotMatch(
    home,
    /href="\/login"[\s\S]{0,80}variant="outline"/,
  );
});

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

test('login chrome is role-specific and admin can publish live appearance', () => {
  const login = read('src/app/login/page.tsx');
  const messages = read('src/i18n/messages.ts');
  const appearance = read('src/app/admin/appearance/page.tsx');
  const globals = read('src/app/globals.css');

  assert.match(login, /parseLoginPortal/);
  assert.match(login, /messages\.login\.portals/);
  assert.match(messages, /Student sign-in/);
  assert.match(messages, /Faculty sign-in/);
  assert.match(messages, /Operations sign-in/);
  assert.match(messages, /Đăng nhập sinh viên/);
  assert.match(messages, /Đăng nhập giảng viên/);
  assert.match(messages, /Đăng nhập quản trị/);
  assert.match(appearance, /Site appearance|copy\.postsTitle/);
  assert.match(globals, /data-accent='campus-gold'/);
  assert.match(globals, /data-accent='river-blue'/);
});

test('student dashboard does not repeat the welcome title in a second hero band', () => {
  const dashboard = read('src/app/dashboard/page.tsx');
  const layout = read('src/app/dashboard/layout.tsx');

  assert.match(dashboard, /PageHeader/);
  assert.doesNotMatch(dashboard, /section className="[^"]*bg-primary/);
  assert.match(layout, /hidden sm:flex/);
});

test('student and lecturer routes share one accessible responsive sidebar', () => {
  const layout = read('src/app/dashboard/layout.tsx');

  assert.match(layout, /studentMenuSections/);
  assert.match(layout, /lecturerMenuSections/);
  assert.match(layout, /portal-sidebar/);
  assert.match(layout, /portal-skip-link/);
  assert.match(layout, /aria-current=\{isActive \? 'page'/);
  assert.match(layout, /inert=\{!isDesktopSidebar && !sidebarOpen/);
  assert.match(layout, /id="dashboard-main-content"/);
  assert.match(layout, /xl:grid-cols/);
  assert.match(layout, /hidden xl:block/);
  assert.match(layout, /WorkspaceForbiddenState/);
  assert.doesNotMatch(layout, /mobileMenuItems/);
  assert.doesNotMatch(
    layout,
    /dashboard\/invoices|dashboard\/thesis\/(councils|evaluation|reviews)/,
  );
});

test('student and lecturer mobile nav reserves an assistant slot below md', () => {
  const layout = read('src/app/dashboard/layout.tsx');

  assert.match(layout, /studentMobileNavItems/);
  assert.match(layout, /lecturerMobileNavItems/);
  assert.match(layout, /grid-cols-6/);
  assert.match(layout, /data-mobile-assistant-slot/);
  assert.match(layout, /md:hidden/);
  assert.match(layout, /min-h-11/);
  assert.match(layout, /text-xs/);
  assert.match(layout, /kind: 'menu'/);
  assert.match(layout, /setSidebarOpen\(true\)/);
  assert.match(layout, /aria-hidden=\{sidebarOpen \? true : undefined\}/);
  assert.match(layout, /inert=\{sidebarOpen \? true : undefined\}/);
  assert.match(layout, /className="fixed inset-x-0 bottom-0 z-30/);
  assert.match(layout, /href: '\/dashboard\/schedule'/);
  assert.match(layout, /href: '\/dashboard\/register'/);
  assert.match(layout, /href: '\/dashboard\/grades'/);
  assert.match(layout, /href: '\/dashboard\/lecturer\/schedule'/);
  assert.match(layout, /href: '\/dashboard\/lecturer\/grades'/);
  assert.match(layout, /href: '\/dashboard\/thesis'/);
  assert.doesNotMatch(layout, /grid-cols-4/);
  assert.doesNotMatch(layout, /slice\(0,\s*4\)/);
  assert.doesNotMatch(layout, /mobileMenuItems/);
  assert.doesNotMatch(
    layout,
    /dashboard\/invoices|dashboard\/thesis\/(councils|evaluation|reviews)/,
  );
});

test('wrong-role and unsigned access surfaces ForbiddenState instead of a spinner', () => {
  const auth = read('src/context/AuthContext.tsx');
  const route = read('src/components/ProtectedRoute.tsx');
  const register = read('src/app/dashboard/register/page.tsx');

  assert.match(auth, /isForbidden/);
  assert.match(route, /ForbiddenState/);
  assert.match(route, /WorkspaceForbiddenState/);
  assert.match(register, /WorkspaceForbiddenState/);
  assert.match(register, /useConfirmationDialog/);
  assert.doesNotMatch(route, /animate-spin/);
  assert.doesNotMatch(register, /window\.confirm/);
  assert.doesNotMatch(register, /authLoading \|\| !hasAccess/);
});

test('admin routes use the same fixed sidebar and retained route inventory', () => {
  const frame = read('src/components/admin/AdminFrame.tsx');

  assert.match(frame, /adminMenuSections/);
  assert.match(frame, /id="admin-sidebar"/);
  assert.match(frame, /lg:pl-\[var\(--portal-sidebar-width\)\]/);
  assert.match(frame, /aria-controls="admin-sidebar"/);
  assert.match(frame, /id="admin-main-content"/);
  assert.match(frame, /\/admin\/announcements/);
  assert.match(frame, /\/admin\/appearance/);
  assert.doesNotMatch(frame, /\/admin\/(analytics|invoices|support)/);
  assert.doesNotMatch(frame, /\/admin\/thesis\/(councils|evaluation|reviews)/);
});

test('status primitives use semantic tokens instead of raw Tailwind palettes', () => {
  const globals = read('src/app/globals.css');
  const status = read('src/components/ui/status.ts');
  const badge = read('src/components/thesis/StatusBadge.tsx');
  const table = read('src/components/ui/data-table.tsx');
  const layout = read('src/app/layout.tsx');

  assert.match(globals, /--status-success:/);
  assert.match(globals, /--status-warning:/);
  assert.match(globals, /--status-danger:/);
  assert.match(globals, /--status-info:/);
  assert.match(globals, /--status-neutral:/);
  assert.match(globals, /--status-success-foreground:/);
  assert.match(globals, /--portal-yellow:/);
  assert.match(status, /export function statusToneClass/);
  assert.match(status, /export function metricToneClass/);
  assert.doesNotMatch(badge, /bg-emerald-500/);
  assert.doesNotMatch(status, /bg-emerald-500/);
  assert.doesNotMatch(badge, /bg-(red|amber|blue|violet)-500/);
  assert.doesNotMatch(status, /bg-(emerald|red|amber|blue|violet|yellow)-/);
  assert.doesNotMatch(table, /bg-gray-50|text-gray-500/);
  assert.match(layout, /QueryProvider/);
  assert.doesNotMatch(layout, /richColors/);
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
