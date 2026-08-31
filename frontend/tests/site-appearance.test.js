const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

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
  assert.match(home, /roleLanes\.lecturer\.href/);
  assert.match(home, /roleLanes\.admin\.href/);
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
