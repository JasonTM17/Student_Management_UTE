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

test('assistant SSE parser handles split frames and ignores malformed data', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const events = [];
  const parser = createAssistantSseParser((event) => events.push(event));
  parser.push('data: {"type":"delta","text":"Hel');
  parser.push('lo"}\n\ndata: not-json\n\ndata: {"type":"done"}');
  parser.end();
  assert.deepEqual(events, [{ type: 'delta', text: 'Hello' }, { type: 'done' }]);
});

test('assistant SSE parser preserves Spring event names when payload omits discriminator', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const events = [];
  const parser = createAssistantSseParser((event) => events.push(event));
  parser.push('event: meta\ndata: {"conversationId":"c1"}\n\n');
  parser.push('event: done\ndata: {"messageId":"m1","degraded":true}\n\n');
  parser.end();
  assert.deepEqual(events, [
    { conversationId: 'c1', type: 'meta' },
    { messageId: 'm1', degraded: true, type: 'done' },
  ]);
});

test('assistant stream contract rejects out-of-order and duplicate delta sequences', () => {
  const { AssistantStreamOrder, parseAssistantStreamEvent } = load('src/lib/assistant-stream.ts');
  const order = new AssistantStreamOrder();
  order.accept(parseAssistantStreamEvent({ type: 'meta' }));
  order.accept(parseAssistantStreamEvent({ type: 'delta', sequence: 0, text: 'a' }));
  assert.throws(() => order.accept(parseAssistantStreamEvent({ type: 'delta', sequence: 0, text: 'b' })), /not increasing/);
  assert.throws(() => new AssistantStreamOrder().accept(parseAssistantStreamEvent({ type: 'done' })), /missing meta/);
});

test('assistant parser reports malformed JSON through opt-in invalid callback', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const invalid = [];
  const parser = createAssistantSseParser(() => undefined, { onInvalid: (error) => invalid.push(error.message) });
  parser.push('data: {bad-json}\n\n');
  assert.equal(invalid.length, 1);
});

test('assistant reducer owns streaming, citations, degraded, and retry states', () => {
  const reducerSource = fs.readFileSync(path.join(root, 'src/components/assistant/assistant-reducer.ts'), 'utf8');
  const panelSource = fs.readFileSync(path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
  assert.match(reducerSource, /export function assistantReducer/);
  assert.match(reducerSource, /type: 'delta'/);
  assert.match(reducerSource, /type: 'citation'/);
  assert.match(reducerSource, /TURN_TERMINAL_RACE/);
  assert.match(reducerSource, /TURN_NOT_ACTIVE/);
  assert.match(panelSource, /TRANSIENT_TERMINAL_CODES/);
  assert.match(panelSource, /KNOWLEDGE_UNAVAILABLE/);
  assert.match(panelSource, /QUOTA_EXCEEDED/);
  assert.match(panelSource, /AbortController/);
  assert.match(panelSource, /Do not issue a JSON replay/);
});

test('assistant launcher has a campus helpdesk mark and full interaction states', () => {
  const source = fs.readFileSync(path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
  assert.match(source, /data-assistant-launcher="campus-mark"/);
  assert.match(source, /function AssistantLauncherMark/);
  assert.match(source, /<Bookmark/);
  assert.match(source, /hover:-translate-y-0\.5/);
  assert.match(source, /focus-visible:ring-2/);
  assert.match(source, /motion-reduce:transition-none/);
});

test('assistant history routes URI-encode owner-scoped identifiers', () => {
  const source = fs.readFileSync(path.join(root, 'src/lib/thesis-api.ts'), 'utf8');
  assert.match(source, /conversations\/\$\{encodeURIComponent\(conversationId\)\}\/messages/);
  assert.match(source, /delete\(`\/assistant\/conversations\/\$\{encodeURIComponent\(conversationId\)\}`\)/);
  assert.match(source, /post<AssistantReply>\('\/assistant\/chat'/);
  assert.doesNotMatch(source, /post<AssistantReply>\('\/thesis\/assistant\/chat'/);
});
