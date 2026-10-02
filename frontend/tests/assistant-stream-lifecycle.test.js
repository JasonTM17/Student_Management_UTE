const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const frame = (event) => `data: ${JSON.stringify(event)}\n\n`;
const flush = async () => { for (let i = 0; i < 12; i++) await Promise.resolve(); };

function harness() {
  let now = 0;
  let nextTimer = 0;
  const timers = new Map();
  let streamController;
  let fetches = 0;
  let cancelled = 0;
  const source = new ReadableStream({
    start(controller) { streamController = controller; },
    cancel() { cancelled++; },
  });
  const context = vm.createContext({
    TextDecoder, AbortController, console,
    setTimeout(callback, delay) {
      const id = ++nextTimer;
      timers.set(id, { at: now + delay, callback });
      return id;
    },
    clearTimeout(id) { timers.delete(id); },
    fetch: async (_url, options) => {
      fetches++;
      const fail = () => streamController.error(new Error('aborted'));
      if (options.signal.aborted) throw new Error('aborted');
      options.signal.addEventListener('abort', fail, { once: true });
      return { ok: true, status: 200, body: source };
    },
  });
  const cache = new Map();
  function load(relative) {
    if (cache.has(relative)) return cache.get(relative);
    const record = { exports: {} };
    cache.set(relative, record.exports);
    const output = ts.transpileModule(fs.readFileSync(path.join(root, relative), 'utf8'), {
      compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    }).outputText;
    const requireDependency = (name) => {
      if (name === '@/lib/api') return { default: {}, API_BASE_URL: '/api', createRequestId: () => 'test-request', refreshSessionSingleFlight: async () => {} };
      if (name.startsWith('@/lib/')) return load(`src/lib/${name.slice(6)}.ts`);
      throw new Error(`Unexpected dependency ${name}`);
    };
    vm.runInContext(`(function(require,module,exports){${output}\n})`, context)(requireDependency, record, record.exports);
    return record.exports;
  }
  const { thesisApi } = load('src/lib/thesis-api.ts');
  const caller = new AbortController();
  const events = [];
  let result;
  const start = () => {
    void thesisApi.streamChat('Question', 'en', {
      clientRequestId: 'test-request', signal: caller.signal,
      onEvent: (event) => events.push(event),
    }).then(() => { result = 'resolved'; }, (error) => { result = error; });
  };
  return {
    caller, events, start,
    get result() { return result; },
    get fetches() { return fetches; },
    get cancelled() { return cancelled; },
    get timerCount() { return timers.size; },
    push(text) { streamController.enqueue(new TextEncoder().encode(text)); },
    close() { streamController.close(); },
    async advance(ms) {
      const target = now + ms;
      while (true) {
        const due = [...timers].filter(([, timer]) => timer.at <= target).sort((a, b) => a[1].at - b[1].at)[0];
        if (!due) break;
        now = due[1].at;
        timers.delete(due[0]);
        due[1].callback();
        await flush();
      }
      now = target;
      await flush();
    },
  };
}

test('comment heartbeats cannot extend the wait for first useful content', async () => {
  const h = harness();
  h.start();
  await flush();
  for (let i = 0; i < 4 && !h.result; i++) {
    await h.advance(14_000);
    if (!h.result) { h.push(':heartbeat\n\n'); await flush(); }
  }
  const observed = h.result;
  h.caller.abort();
  await flush();
  assert.ok(observed instanceof Object && /aborted/.test(observed.message), 'a heartbeat-only socket must expire by 45 seconds');
  assert.equal(h.timerCount, 0);
});

test('accepted done releases the transport without waiting for socket EOF', async () => {
  const h = harness();
  h.start();
  await flush();
  h.push(frame({ type: 'meta' }) + frame({ type: 'delta', text: 'Answer', sequence: 0 }) + frame({ type: 'done' }));
  await flush();
  const observed = h.result;
  h.caller.abort();
  await flush();
  assert.equal(observed, 'resolved');
  assert.equal(h.cancelled, 1);
  assert.equal(h.timerCount, 0);
});

test('continuous deltas cannot extend the non-resettable overall deadline', async () => {
  const h = harness();
  h.start();
  await flush();
  h.push(frame({ type: 'meta' }));
  await flush();
  for (let i = 0; i < 10 && !h.result; i++) {
    h.push(frame({ type: 'delta', sequence: i, text: 'a' }));
    await flush();
    await h.advance(14_000);
  }
  const observed = h.result;
  h.caller.abort();
  await flush();
  assert.ok(observed && /aborted/.test(observed.message), 'an endless stream must expire by 120 seconds');
});

test('a pre-aborted caller never starts a network request', async () => {
  const h = harness();
  h.caller.abort();
  h.start();
  await flush();
  assert.equal(h.fetches, 0);
  assert.ok(h.result);
  assert.equal(h.timerCount, 0);
});

test('fragmented healthy content and terminal errors clean up all deadlines', async () => {
  for (const terminal of [{ type: 'done' }, { type: 'error', code: 'TURN_CANCELLED' }]) {
    const h = harness();
    h.start();
    await flush();
    const payload = frame({ type: 'meta' }) + frame({ type: 'delta', text: 'Answer', sequence: 0 }) + frame(terminal);
    h.push(payload.slice(0, 18));
    await flush();
    h.push(payload.slice(18));
    h.close();
    await flush();
    assert.equal(h.result, 'resolved');
    assert.equal(h.events.at(-1).type, terminal.type);
    assert.equal(h.timerCount, 0);
  }
});
