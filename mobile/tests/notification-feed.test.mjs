import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ts = require('typescript');

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relativePath) {
  return fs.readFileSync(path.join(mobileRoot, relativePath), 'utf8');
}

function transpile(relativePath) {
  return ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: path.basename(relativePath),
  }).outputText;
}

function loadClientModule() {
  const module = { exports: {} };
  const stubRequire = (id) =>
    id === 'react-native' ? { Platform: { OS: 'android' } } : require(id);
  new Function('exports', 'require', 'module', 'process', transpile('src/api/client.ts'))(
    module.exports,
    stubRequire,
    module,
    { env: {} },
  );
  return module.exports;
}

function loadFeedModule() {
  const module = { exports: {} };
  new Function(
    'exports',
    'require',
    'module',
    'process',
    transpile('src/screens/student/notification-feed.ts'),
  )(module.exports, () => ({}), module, { env: {} });
  return module.exports;
}

function installFetchStub(handler) {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    const call = {
      url: String(url),
      method: init?.method ?? 'GET',
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
    };
    calls.push(call);
    const spec = handler(call, calls.length);
    return new Response(JSON.stringify(spec.body ?? {}), {
      status: spec.status,
      headers: { 'content-type': 'application/json' },
    });
  };
  return calls;
}

const item = (id, isRead = false) => ({ id, title: `t${id}`, message: `m${id}`, isRead, createdAt: 'x' });

test('notifications() requests the page and limit and keeps the meta block', async () => {
  const { campusApi } = loadClientModule();
  const calls = installFetchStub(() => ({
    status: 200,
    body: { data: [item('n1')], meta: { total: 41, page: 2, limit: 20, totalPages: 3 } },
  }));

  const page = await campusApi.notifications(2, 20);

  assert.equal(calls[0].url, 'http://10.0.2.2:4010/api/v1/notifications/my?page=2&limit=20');
  assert.equal(page.data.length, 1);
  assert.equal(page.meta.totalPages, 3);
});

test('notifications() defaults to the first page so existing callers keep working', async () => {
  const { campusApi } = loadClientModule();
  const calls = installFetchStub(() => ({
    status: 200,
    body: { data: [], meta: { total: 0, page: 1, limit: 20, totalPages: 0 } },
  }));

  await campusApi.notifications();

  assert.equal(calls[0].url, 'http://10.0.2.2:4010/api/v1/notifications/my?page=1&limit=20');
});

test('notificationUnreadCount() reads the server unread total', async () => {
  const { campusApi } = loadClientModule();
  const calls = installFetchStub(() => ({ status: 200, body: { unreadCount: 7 } }));

  const result = await campusApi.notificationUnreadCount();

  assert.equal(calls[0].url, 'http://10.0.2.2:4010/api/v1/notifications/my/unread-count');
  assert.equal(result.unreadCount, 7);
});

test('markNotificationRead keeps the per-item read contract', async () => {
  const { campusApi } = loadClientModule();
  const calls = installFetchStub(() => ({ status: 200, body: { id: 'n7', isRead: true } }));

  await campusApi.markNotificationRead('n7');

  assert.equal(calls[0].method, 'PATCH');
  assert.equal(calls[0].url, 'http://10.0.2.2:4010/api/v1/notifications/my/n7/read');
});

test('mergeNotificationItems deduplicates overlapping pages by id and keeps server order', () => {
  const { mergeNotificationItems } = loadFeedModule();

  const pageOne = [item('a'), item('b'), item('c')];
  const pageTwo = [item('c'), item('d'), item('e')];

  const merged = mergeNotificationItems(pageOne, pageTwo);
  assert.deepEqual(merged.map((entry) => entry.id), ['a', 'b', 'c', 'd', 'e']);

  const shifted = mergeNotificationItems(merged, [item('fresh'), item('b'), item('z')]);
  assert.deepEqual(shifted.map((entry) => entry.id), ['a', 'b', 'c', 'd', 'e', 'fresh', 'z']);

  const markingReadPreserves = merged.map((entry) =>
    entry.id === 'b' ? { ...entry, isRead: true } : entry);
  assert.equal(markingReadPreserves[1].isRead, true);
});
