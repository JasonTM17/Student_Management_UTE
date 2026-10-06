const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

// Executes the real transpiled streamChat against a stubbed fetch that answers
// a 429 rate-limit envelope — the contract the useAssistantStream classifier
// consumes. This pins behavior, not source text: a regression that drops the
// code/retry-after propagation fails here even if a static grep still passes.
function loadThesisApi({ fetch }) {
  const source = fs.readFileSync(path.join(root, 'src/lib/thesis-api.ts'), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, esModuleInterop: true },
  }).outputText;
  const record = { exports: {} };
  const stubs = {
    '@/lib/api': {
      API_BASE_URL: 'http://api.test/api/v1',
      createRequestId: () => '00000000-0000-4000-8000-000000000000',
      refreshSessionSingleFlight: async () => {},
      default: { post: async () => ({ data: {} }) },
    },
    '@/lib/assistant-stream-helpers': { CANCEL_REQUEST_TIMEOUT_MS: 5000 },
    '@/lib/assistant-stream': {
      createAssistantSseParser: () => ({ push() {}, flush() {} }),
      parseAssistantStreamEvent: (e) => e,
      AssistantStreamOrder: class { constructor() { this.currentPhase = 'terminal'; } accept() {} },
    },
    '@/lib/assistant-output-guard': {},
  };
  const context = vm.createContext({ fetch, AbortController, TextDecoder, setTimeout, clearTimeout, console });
  vm.runInContext(`(function(require,module,exports){${output}\n})`, context)(
    (name) => stubs[name] ?? {},
    record,
    record.exports,
  );
  return record.exports.thesisApi;
}

test('streamChat surfaces 429 with rate-limit code and retry-after header', async () => {
  const api = loadThesisApi({
    fetch: async () =>
      new Response(JSON.stringify({ code: 'RATE_LIMIT_EXCEEDED', message: 'Too many requests' }), {
        status: 429,
        headers: { 'retry-after': '17', 'content-type': 'application/json' },
      }),
  });
  const failure = await api
    .streamChat('xin chào', 'vi', { onEvent: () => {} })
    .then(() => ({ ok: true }))
    .catch((error) => error);
  assert.equal(failure.status, 429);
  assert.equal(failure.response.status, 429);
  assert.equal(failure.response.data.code, 'RATE_LIMIT_EXCEEDED');
  assert.equal(failure.response.headers['retry-after'], '17');
});

test('streamChat 429 without retry-after still carries status and code', async () => {
  const api = loadThesisApi({
    fetch: async () =>
      new Response(JSON.stringify({ code: 'QUOTA_EXCEEDED' }), {
        status: 429,
        headers: { 'content-type': 'application/json' },
      }),
  });
  const failure = await api
    .streamChat('hello', 'en', { onEvent: () => {} })
    .catch((error) => error);
  assert.equal(failure.response.status, 429);
  assert.equal(failure.response.data.code, 'QUOTA_EXCEEDED');
  assert.equal(failure.response.headers, undefined);
});
