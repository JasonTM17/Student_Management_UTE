const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function load(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

test('CB-P2-9: comment-only heartbeat frames produce no events', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const events = [];
  const parser = createAssistantSseParser((event) => events.push(event));
  parser.push(':heartbeat\n\n:heartbeat\n\n');
  parser.end();
  assert.deepEqual(events, []);
});

test('CB-P2-9: heartbeats interleaved between events do not disturb ordering or sequencing', () => {
  const { createAssistantSseParser, AssistantStreamOrder, parseAssistantStreamEvent } =
    load('src/lib/assistant-stream.ts');
  const events = [];
  const parser = createAssistantSseParser((event) => events.push(event));
  // A slow model start: the server emits comment keep-alives while the meta or
  // first delta is still pending, then the normal ordered stream continues.
  parser.push(':heartbeat\n\n');
  parser.push('event: meta\ndata: {"type":"meta","model":"deepseek-v4-flash"}\n\n');
  parser.push(':heartbeat\n\n');
  parser.push('event: delta\ndata: {"type":"delta","sequence":0,"text":"He"}\n\n');
  parser.push(':heartbeat\n\n');
  parser.push('event: delta\ndata: {"type":"delta","sequence":1,"text":"llo"}\n\n');
  parser.push(':heartbeat\n\n');
  parser.push('event: replace\ndata: {"type":"replace","text":"Hello!","reasonCode":"ANSWERED"}\n\n');
  parser.push('event: done\ndata: {"type":"done","messageId":"m1","reasonCode":"ANSWERED","degraded":false}\n\n');
  parser.end();

  assert.deepEqual(events.map((event) => event.type), [
    'meta',
    'delta',
    'delta',
    'replace',
    'done',
  ]);

  // The same byte stream must pass the strict order/sequence validator.
  const order = new AssistantStreamOrder();
  for (const event of events) order.accept(parseAssistantStreamEvent(event));
  assert.equal(order.currentPhase, 'terminal');
});

test('CB-P2-9: a heartbeat frame split across chunk boundaries stays inert', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const events = [];
  const parser = createAssistantSseParser((event) => events.push(event));
  parser.push(':heart');
  parser.push('beat\ndata: {"type":"delta","text":"ok"}\n\n');
  parser.push(':bee');
  parser.end();
  assert.deepEqual(events, [{ type: 'delta', text: 'ok' }]);
});

test('CB-P3-1: replace with reason ANSWERED does not set the degraded flag', () => {
  const { assistantReducer, initialState } = load('src/components/assistant/assistant-reducer.ts');
  let state = assistantReducer(initialState, {
    type: 'assistant-start',
    message: { id: 'a1', role: 'assistant', content: '', pending: true },
  });
  state = assistantReducer(state, { type: 'delta', text: 'Gồm  3 đến  5 ngày' });
  state = assistantReducer(state, { type: 'replace', text: 'Gồm 3 đến 5 ngày', reasonCode: 'ANSWERED' });
  const afterRepair = state.messages[0];
  assert.equal(afterRepair.content, 'Gồm 3 đến 5 ngày');
  assert.equal(afterRepair.degraded, false, 'a spacing repair is not a degradation');
  assert.equal(afterRepair.reasonCode, 'ANSWERED');

  state = assistantReducer(state, {
    type: 'complete',
    reply: { messageId: 'm1', reasonCode: 'ANSWERED', degraded: false },
  });
  assert.equal(state.messages[0].degraded, false);
});

test('CB-P3-1: replace with a non-ANSWERED reason still degrades', () => {
  const { assistantReducer, initialState } = load('src/components/assistant/assistant-reducer.ts');
  let state = assistantReducer(initialState, {
    type: 'assistant-start',
    message: { id: 'a1', role: 'assistant', content: '', pending: true },
  });
  state = assistantReducer(state, { type: 'delta', text: 'partial' });
  state = assistantReducer(state, { type: 'replace', text: 'fallback', reasonCode: 'PROVIDER_UNSAFE_OUTPUT' });
  assert.equal(state.messages[0].degraded, true, 'unsafe provider output is a real degradation');
  // Untagged local replaces (guard blocks, cancels) keep degrading as before.
  state = assistantReducer(state, { type: 'replace', text: '' });
  assert.equal(state.messages[0].degraded, true);
});
