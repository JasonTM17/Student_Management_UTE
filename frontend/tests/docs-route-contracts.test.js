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

function loadDocsRoute() {
  return loadTs('src/app/api/docs/[[...path]]/route.ts', {
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

test('docs proxy refuses dot-segment traversal before contacting the backend', async () => {
  const { GET } = loadDocsRoute();
  const traversals = [
    ['..', '..', 'api', 'v1', 'auth', 'me'],
    ['..', 'actuator', 'env'],
    ['../..', 'api'],
    ['..\\..\\actuator'],
  ];
  for (const path of traversals) {
    await withFetchStub(
      () => new Response('{}', { status: 200 }),
      async (calls) => {
        const response = await GET(
          requestFor('http://local.test/api/docs/' + path.join('/')),
          contextFor(path),
        );
        assert.equal(response.status, 400, `segments ${JSON.stringify(path)} must be rejected`);
        assert.equal(calls.length, 0, 'a rejected segment must never reach the backend');
      },
    );
  }
});

test('docs proxy keeps ordinary doc paths under /v3/api-docs', async () => {
  const { GET } = loadDocsRoute();
  await withFetchStub(
    () => new Response('{}', { status: 200, headers: { 'Content-Type': 'application/json' } }),
    async (calls) => {
      for (const path of [['openapi.json'], ['swagger-config'], []]) {
        await GET(requestFor('http://local.test/api/docs/' + path.join('/')), contextFor(path));
      }
      assert.match(calls[0].url, /\/v3\/api-docs$/);
      assert.match(calls[1].url, /\/v3\/api-docs\/swagger-config$/);
      assert.match(calls[2].url, /\/v3\/api-docs$/);
    },
  );
});

test('docs proxy does not forward a caller-supplied X-Forwarded-For', async () => {
  const { GET } = loadDocsRoute();
  await withFetchStub(
    () => new Response('{}', { status: 200 }),
    async (calls) => {
      await GET(
        requestFor('http://local.test/api/docs/openapi.json', {
          headers: { 'x-forwarded-for': '6.6.6.6', 'x-real-ip': '6.6.6.6' },
        }),
        contextFor(['openapi.json']),
      );
      const forwarded = calls[0].init.headers;
      assert.notEqual(forwarded.get('x-forwarded-for'), '6.6.6.6');
      assert.equal(forwarded.get('x-real-ip'), null);
    },
  );
});
