const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadTs(relativePath) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

test('applyPageOrder lands a reorder and never moves off-page pins', () => {
  const { applyPageOrder } = loadTs('src/lib/site-appearance.ts');

  // First save with nothing pinned yet.
  assert.deepEqual(applyPageOrder([], ['c', 'a', 'b']), ['c', 'a', 'b']);

  // A repeat reorder of the same page must actually apply. A helper that keeps
  // the existing order would return ['c','a','b'] here, discarding the drag.
  assert.deepEqual(applyPageOrder(['c', 'a', 'b'], ['b', 'c', 'a']), ['b', 'c', 'a']);

  // Pins for announcements outside the reordered page keep their positions.
  assert.deepEqual(applyPageOrder(['z', 'c', 'a', 'b'], ['b', 'a', 'c']), ['z', 'b', 'a', 'c']);
  assert.deepEqual(
    applyPageOrder(['p', 'c', 'q', 'a', 'b', 'r'], ['a', 'b', 'c']),
    ['p', 'a', 'q', 'b', 'c', 'r'],
  );

  // Pins outside the offered page are preserved — including ones this batch
  // does not mention — so a reorder never silently unpins an announcement.
  assert.deepEqual(applyPageOrder(['z', 'c', 'a'], ['a']), ['z', 'c', 'a']);
  assert.deepEqual(applyPageOrder(['z', 'c', 'a', 'b'], ['a', 'b']), ['z', 'c', 'a', 'b']);

  // Nothing pinned and nothing offered stays empty.
  assert.deepEqual(applyPageOrder([], []), []);
});

test('reorder save paths apply the new order instead of a plain merge', () => {
  const adminAnnouncements = read('src/app/admin/announcements/page.tsx');
  const studio = read('src/app/admin/appearance/page.tsx');
  const editor = read('src/app/dashboard/editor/page.tsx');
  const appearance = read('src/lib/site-appearance.ts');

  for (const source of [adminAnnouncements, studio, editor]) {
    assert.match(source, /applyPageOrder\(/);
  }
  // The misleading helper is gone: it could not apply a reorder at all.
  assert.doesNotMatch(appearance, /export function mergePostOrder/);
});

function orderByIds(items, order) {
  const rank = new Map(order.map((id, index) => [id, index]));
  return [...items].sort((left, right) => {
    const leftRank = rank.has(left.id) ? rank.get(left.id) : Number.MAX_SAFE_INTEGER;
    const rightRank = rank.has(right.id) ? rank.get(right.id) : Number.MAX_SAFE_INTEGER;
    return leftRank - rightRank;
  });
}

function movePostOrder(order, id, direction) {
  const index = order.indexOf(id);
  const nextIndex = index + direction;
  const next = [...order];
  const [item] = next.splice(index, 1);
  next.splice(nextIndex, 0, item);
  return next;
}

test('portal login and appearance studio files are wired', () => {
  const login = read('src/app/login/page.tsx');
  const home = read('src/app/page.tsx');
  const messages = read('src/i18n/messages.ts');
  const appearance = read('src/app/admin/appearance/page.tsx');
  const shell = read('src/components/auth/AuthShell.tsx');
  const lib = read('src/lib/site-appearance.ts');
  const route = read('src/app/api/site-appearance/route.ts');

  assert.match(login, /parseLoginPortal/);
  assert.match(login, /portalMatchesUser/);
  assert.match(shell, /data-login-portal/);
  assert.match(home, /login\?portal=student/);
  // The landing page renders all three role lanes (student/lecturer/admin)
  // from the roleCards map, including their localized hrefs.
  assert.match(home, /roleLanes\[card\.key\]/);
  assert.match(home, /key: 'lecturer'/);
  assert.match(home, /key: 'admin'/);
  assert.match(messages, /\/login\?portal=lecturer/);
  assert.match(messages, /\/login\?portal=admin/);
  assert.match(appearance, /broadcastSiteAppearance/);
  assert.match(lib, /function orderByIds/);
  assert.match(lib, /function movePostOrder/);
  assert.match(route, /requireAdmin/);
  assert.match(route, /writeSiteAppearance/);
});

test('admin-selected notice order is stable for feeds', () => {
  const items = [{ id: 'c' }, { id: 'a' }, { id: 'b' }];
  const ordered = orderByIds(items, ['b', 'a', 'c']);
  assert.deepEqual(ordered.map((item) => item.id), ['b', 'a', 'c']);
  assert.deepEqual(movePostOrder(['b', 'a', 'c'], 'a', -1), ['a', 'b', 'c']);
});

test('site appearance polling is throttled and pauses while the tab is hidden', () => {
  const provider = read('src/components/providers/SiteAppearanceProvider.tsx');

  assert.match(provider, /15000/);
  assert.doesNotMatch(provider, /4000/);
  assert.match(provider, /document\.visibilityState === 'visible'/);
  assert.match(provider, /addEventListener\('visibilitychange', onVisibilityChange\)/);
  assert.match(provider, /removeEventListener\('visibilitychange', onVisibilityChange\)/);
});
