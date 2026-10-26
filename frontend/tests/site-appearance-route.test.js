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

function loadAppearanceRoute() {
  const appearance = loadTs('src/lib/site-appearance.ts');
  const sessionHint = loadTs('src/lib/session-hint.ts');
  const store = loadTs('src/lib/site-appearance-store.ts', {
    '@/lib/site-appearance': appearance,
  });
  return loadTs('src/app/api/site-appearance/route.ts', {
    'next/server': {
      NextRequest: class {},
      NextResponse: {
        json: (body, init = {}) =>
          new Response(JSON.stringify(body), {
            status: init.status ?? 200,
            headers: init.headers,
          }),
      },
    },
    '@/lib/site-appearance': appearance,
    '@/lib/session-hint': sessionHint,
    '@/lib/site-appearance-store': store,
  });
}

function requestFor(url, init = {}) {
  const request = new Request(url, init);
  request.nextUrl = new URL(url);
  return request;
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

test('a malformed csrf cookie is forbidden, not a server error', async () => {
  const { PUT } = loadAppearanceRoute();
  await withFetchStub(
    () => new Response('{"id":"u"}', { status: 401 }),
    async () => {
      const response = await PUT(
        requestFor('http://local.test/api/site-appearance', {
          method: 'PUT',
          headers: { cookie: 'cc_csrf=%' },
          body: '{}',
        }),
      );
      assert.equal(response.status, 403);
    },
  );
});
