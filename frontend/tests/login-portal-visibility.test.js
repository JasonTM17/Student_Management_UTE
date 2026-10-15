const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

async function loadLoginPortalModule() {
  const source = fs.readFileSync(path.join(root, 'src/lib/login-portal.ts'), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: 'login-portal.ts',
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('the visible tab strip offers student, lecturer, and dean — never admin', async () => {
  const { VISIBLE_LOGIN_PORTALS } = await loadLoginPortalModule();
  assert.deepEqual([...VISIBLE_LOGIN_PORTALS], ['student', 'lecturer', 'dean']);
  assert.equal(VISIBLE_LOGIN_PORTALS.includes('admin'), false);
});

test("'admin' and 'dean' stay valid portals for deep links and guard redirects", async () => {
  const { LOGIN_PORTALS, parseLoginPortal, portalFromUser } = await loadLoginPortalModule();
  assert.equal(LOGIN_PORTALS.includes('admin'), true);
  assert.equal(LOGIN_PORTALS.includes('dean'), true);
  assert.equal(parseLoginPortal('admin'), 'admin');
  assert.equal(parseLoginPortal('dean'), 'dean');
  assert.equal(portalFromUser({ roles: ['ADMIN'] }), 'admin');
  assert.equal(portalFromUser({ roles: ['SUPER_ADMIN'] }), 'admin');
  assert.equal(portalFromUser({ roles: ['TRUONG_KHOA'] }), 'dean');
  assert.equal(portalFromUser({ roles: ['LECTURER', 'TRUONG_KHOA'] }), 'dean');
  assert.equal(portalFromUser({ roles: ['ADMIN', 'TRUONG_KHOA'] }), 'admin');
});

test('the dean portal matches TRUONG_KHOA only and routes to the dean dashboard', async () => {
  const { portalMatchesUser, postLoginRoute, portalFromPathname } = await loadLoginPortalModule();
  assert.equal(portalMatchesUser('dean', { roles: ['TRUONG_KHOA'] }), true);
  assert.equal(portalMatchesUser('dean', { roles: ['LECTURER', 'TRUONG_KHOA'] }), true);
  assert.equal(portalMatchesUser('dean', { roles: ['LECTURER'] }), false);
  assert.equal(portalMatchesUser('dean', { roles: ['STUDENT'] }), false);
  assert.equal(portalMatchesUser('dean', { roles: ['ADMIN'] }), false);
  // The admin portal no longer claims TRUONG_KHOA: a dean hitting the admin
  // deep link falls through to the dean pass-through, which lands on the
  // council workspace /admin/thesis instead of the observation dashboard.
  assert.equal(portalMatchesUser('admin', { roles: ['TRUONG_KHOA'] }), false);
  assert.equal(portalMatchesUser('admin', { roles: ['ADMIN', 'TRUONG_KHOA'] }), true);
  assert.equal(postLoginRoute('dean'), '/dashboard/dean');
  assert.equal(portalFromPathname('/vi/dashboard/dean'), 'dean');
});

test('portal parsing rejects look-alike strings and keeps dean deep links', async () => {
  const { parseLoginPortal, portalFromPathname } = await loadLoginPortalModule();
  for (const candidate of ['DEAN', 'Dean', ' dean', 'dean ', 'dean%00', 'deanx', '', null, undefined]) {
    assert.equal(parseLoginPortal(candidate), 'student', `unexpected portal accepted: ${String(candidate)}`);
  }
  assert.equal(parseLoginPortal('dean'), 'dean');
  // The thesis workspace is used by department heads and deans; expiry from
  // there must not send a department head to the student tab (they would be
  // refused outright instead of returning to their page).
  assert.equal(portalFromPathname('/vi/dashboard/thesis'), 'lecturer');
  assert.equal(portalFromPathname('/dashboard/thesis'), 'lecturer');
});
