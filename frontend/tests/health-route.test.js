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

function requestFor(url, init = {}) {
  const request = new Request(url, init);
  request.nextUrl = new URL(url);
  return request;
}

test('health route answers 503 when the backend is unreachable', async () => {
  const { GET } = loadTs('src/app/health/route.ts');
  const original = globalThis.fetch;
  globalThis.fetch = async () => {
    throw new Error('connect ECONNREFUSED');
  };
  try {
    const response = await GET(requestFor('http://local.test/health'));
    assert.equal(response.status, 503);
    assert.match(response.headers.get('content-type') ?? '', /application\/json/);
  } finally {
    globalThis.fetch = original;
  }
});

test('health route forwards the upstream liveness answer unchanged', async () => {
  const { GET } = loadTs('src/app/health/route.ts');
  const original = globalThis.fetch;
  const upstream = new Response('{"status":"UP"}', {
    status: 200,
    headers: { 'content-type': 'application/json' },
  });
  globalThis.fetch = async () => upstream;
  try {
    const response = await GET(requestFor('http://local.test/health'));
    assert.equal(response.status, 200);
    assert.equal(await response.text(), '{"status":"UP"}');
  } finally {
    globalThis.fetch = original;
  }
});
