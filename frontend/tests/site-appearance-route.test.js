const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
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

test('an unparseable PUT body is rejected instead of resetting saved branding', async () => {
  const { PUT } = loadAppearanceRoute();
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'appearance-'));
  const savedPath = path.join(dir, 'site-appearance.json');
  fs.writeFileSync(savedPath, `${JSON.stringify({
    version: 42,
    updatedAt: '2026-01-01T00:00:00.000Z',
    accent: 'river-blue',
    hero: { image: '', en: { eyebrow: 'a', title: 'b', description: 'c' }, vi: { eyebrow: 'd', title: 'e', description: 'f' } },
    postOrder: ['post-1'],
  })}\n`);

  const previousPath = process.env.SITE_APPEARANCE_PATH;
  process.env.SITE_APPEARANCE_PATH = savedPath;
  try {
    await withFetchStub(
      () => new Response('{"roles":["ADMIN"]}', { status: 200 }),
      async () => {
        const response = await PUT(
          requestFor('http://local.test/api/site-appearance', {
            method: 'PUT',
            headers: { cookie: 'cc_csrf=tok', 'content-type': 'application/json' },
            body: '{broken json',
          }),
        );
        assert.equal(response.status, 400);
      },
    );
    const persisted = JSON.parse(fs.readFileSync(savedPath, 'utf8'));
    assert.equal(persisted.accent, 'river-blue');
    assert.equal(persisted.version, 42);
    assert.deepEqual(persisted.postOrder, ['post-1']);
  } finally {
    if (previousPath === undefined) {
      delete process.env.SITE_APPEARANCE_PATH;
    } else {
      process.env.SITE_APPEARANCE_PATH = previousPath;
    }
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
