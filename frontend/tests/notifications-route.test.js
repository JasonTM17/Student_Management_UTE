const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadTs(relativePath, deps = {}) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
  }).outputText;
  const moduleRecord = { exports: {} };
  const requireShim = (id) => (id in deps ? deps[id] : require(id));
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, requireShim);
  return moduleRecord.exports;
}

function loadNotificationsRoute() {
  return loadTs('src/app/notifications/[[...path]]/route.ts', {
    '@/lib/safe-path': loadTs('src/lib/safe-path.ts'),
  });
}

function requestFor(url, init = {}) {
  const request = new Request(url, init);
  request.nextUrl = new URL(url);
  return request;
}

function contextFor(path) {
  return { params: Promise.resolve({ path }) };
}

function withFetchStub(impl, fn) {
  const original = globalThis.fetch;
  const calls = [];
  globalThis.fetch = async (url, init) => {
    const parsed = typeof url === 'string' ? url : url instanceof URL ? url.href : url.url;
    calls.push({ url: parsed, init });
    return impl(parsed, init);
  };
  return Promise.resolve()
    .then(fn.bind(null, calls))
    .finally(() => {
      globalThis.fetch = original;
    });
}

test('notifications proxy refuses dot-segment traversal', async () => {
  const { GET } = loadNotificationsRoute();
  await withFetchStub(
    () => new Response('{}', { status: 200 }),
    async (calls) => {
      const response = await GET(
        requestFor('http://local.test/notifications/x', {
          headers: { cookie: 'a=b' },
        }),
        contextFor(['..', '..', 'actuator', 'health']),
      );
      assert.equal(response.status, 400);
      assert.equal(calls.length, 0);
    },
  );
});

test('notifications proxy preserves the request query string', async () => {
  const { GET } = loadNotificationsRoute();
  await withFetchStub(
    () => new Response('{}', { status: 200 }),
    async (calls) => {
      await GET(
        requestFor('http://local.test/notifications/my?page=1&limit=10&isRead=false'),
        contextFor(['my']),
      );
      assert.match(calls[0].url, /\/api\/v1\/notifications\/my\?page=1&limit=10&isRead=false$/);
    },
  );
});

test('notifications proxy forwards content type and csrf token on writes', async () => {
  const { POST } = loadNotificationsRoute();
  await withFetchStub(
    () => new Response('{}', { status: 200 }),
    async (calls) => {
      await POST(
        requestFor('http://local.test/notifications', {
          method: 'POST',
          headers: {
            'content-type': 'application/json',
            'x-csrf-token': 'csrf-token-1',
            cookie: 'a=b',
            authorization: 'Bearer token',
          },
          body: JSON.stringify({ title: 'hi' }),
        }),
        contextFor([]),
      );
      const headers = calls[0].init.headers;
      assert.equal(headers.get('content-type'), 'application/json');
      assert.equal(headers.get('x-csrf-token'), 'csrf-token-1');
      assert.equal(headers.get('cookie'), 'a=b');
      assert.equal(headers.get('authorization'), 'Bearer token');
    },
  );
});
