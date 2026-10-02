const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

function loadProxy(upstream, onFetch = () => {}) {
  const source = fs.readFileSync(path.join(__dirname, '../src/app/api/v1/[...path]/route.ts'), 'utf8');
  const output = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 } }).outputText;
  const record = { exports: {} };
  const context = vm.createContext({
    Headers, Response,
    process: { env: { JAVA_API_ORIGIN: 'http://test-api' } },
    fetch: async (url, options) => { onFetch(url, options); return upstream; },
  });
  vm.runInContext(`(function(require,module,exports){${output}\n})`, context)(
    () => ({ buildApiProxyUrl: (origin, segments, query) => `${origin}/api/v1/${segments.join('/')}${query}` }),
    record, record.exports,
  );
  return record.exports.POST;
}

function request() {
  return {
    method: 'POST', headers: new Headers({ accept: 'text/event-stream' }),
    nextUrl: { search: '' }, signal: new AbortController().signal,
    arrayBuffer: async () => new ArrayBuffer(0),
  };
}
const context = { params: Promise.resolve({ path: ['assistant', 'chat', 'stream'] }) };
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };

test('assistant proxy returns SSE headers and the first chunk before upstream EOF', async () => {
  let writer;
  const upstream = new Response(new ReadableStream({ start(controller) { writer = controller; } }), {
    headers: { 'content-type': 'text/event-stream; charset=utf-8', 'cache-control': 'no-cache', 'x-accel-buffering': 'no' },
  });
  const handle = loadProxy(upstream);
  let forwarded;
  const pending = handle(request(), context).then((value) => { forwarded = value; });
  writer.enqueue(new TextEncoder().encode(':heartbeat\n\n'));
  await flush();
  const beforeEof = forwarded;
  writer.close();
  await pending;
  assert.ok(beforeEof, 'SSE response must become readable while the provider is still working');
  assert.equal(beforeEof.headers.get('x-accel-buffering'), 'no');
  assert.equal(await beforeEof.text(), ':heartbeat\n\n');
});

test('JSON login remains buffered and preserves each session cookie', async () => {
  let writer;
  const headers = new Headers({ 'content-type': 'application/json', 'content-length': '11' });
  headers.append('set-cookie', 'session=test; HttpOnly; Path=/');
  headers.append('set-cookie', 'cc_csrf=test; Path=/');
  const handle = loadProxy(new Response(new ReadableStream({ start(controller) { writer = controller; } }), { headers }));
  let forwarded;
  const pending = handle(request(), context).then((value) => { forwarded = value; });
  writer.enqueue(new TextEncoder().encode('{"ok":true}'));
  await flush();
  assert.equal(forwarded, undefined, 'retain the cookie-safe buffered JSON response');
  writer.close();
  await pending;
  assert.equal(await forwarded.text(), '{"ok":true}');
  assert.equal(forwarded.headers.getSetCookie().length, 2);
  assert.equal(forwarded.headers.get('content-length'), null);
});

test('proxy forwards caller cancellation to the upstream fetch', async () => {
  const incoming = request();
  let outgoingSignal;
  const handle = loadProxy(new Response('ok', { headers: { 'content-type': 'text/event-stream' } }), (_url, options) => { outgoingSignal = options.signal; });
  await handle(incoming, context);
  assert.equal(outgoingSignal, incoming.signal);
});
