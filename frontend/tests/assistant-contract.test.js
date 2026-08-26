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

test('assistant stream contract rejects incomplete citation frames before render', () => {
  const { parseAssistantStreamEvent } = load('src/lib/assistant-stream.ts');
  assert.throws(
    () => parseAssistantStreamEvent({ type: 'citation', citation: {} }),
    /citation id is required/,
  );
  assert.throws(
    () => parseAssistantStreamEvent({ type: 'citation', citation: [] }),
    /assistant event must be an object/,
  );
});

test('assistant parser reports malformed JSON through opt-in invalid callback', () => {
  const { createAssistantSseParser } = load('src/lib/assistant-stream.ts');
  const invalid = [];
  const parser = createAssistantSseParser(() => undefined, { onInvalid: (error) => invalid.push(error.message) });
  parser.push('data: {bad-json}\n\n');
  assert.equal(invalid.length, 1);
});

test('assistant reducer owns streaming, citations, degraded, and retry states', () => {
  const source = fs.readFileSync(path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
  assert.match(source, /export function assistantReducer/);
  assert.match(source, /type: 'delta'/);
  assert.match(source, /type: 'citation'/);
  assert.match(source, /KNOWLEDGE_UNAVAILABLE/);
  assert.match(source, /QUOTA_EXCEEDED/);
  assert.match(source, /AbortController/);
  assert.match(source, /TURN_TERMINAL_RACE/);
  assert.match(source, /TURN_NOT_ACTIVE/);
  assert.match(source, /Do not issue a JSON replay/);
});

test('assistant launcher has a thesis-specific icon mark and full interaction states', () => {
  const source = fs.readFileSync(path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
  assert.match(source, /data-assistant-launcher="thesis-mark"/);
  assert.match(source, /function AssistantLauncherMark/);
  assert.match(source, /<Bookmark/);
  assert.match(source, /hover:-translate-y-0\.5/);
  assert.match(source, /focus-visible:ring-2/);
  assert.match(source, /motion-reduce:transition-none/);
  assert.match(source, /useDialogFocusTrap/);
  assert.match(source, /aria-modal="true"/);
});

test('assistant history and feedback states recover focus and avoid contradictory empty output', () => {
  const source = fs.readFileSync(path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
  assert.match(source, /const closeHistory = useCallback/);
  assert.match(source, /historyRef\.current\?\.focus/);
  assert.match(source, /historyStatus === 'loaded' && !history\.length/);
  assert.match(source, /feedbackPendingMessageId/);
  assert.match(source, /feedbackErrorMessageId/);
  assert.match(source, /rating: previous/);
  assert.match(source, /messages\.assistant\.feedbackUnavailable/);
  assert.match(source, /thesisApi\.cancelRequest\(requestId\)/);
});

test('closing an active assistant stream removes its optimistic pending bubble', () => {
  const source = fs.readFileSync(path.join(root, 'src/components/assistant/AssistantPanel.tsx'), 'utf8');
  assert.match(source, /type: 'discard-pending'/);
  assert.match(source, /message\.role === 'assistant' && message\.pending/);
  assert.match(source, /const closePanel = useCallback\(\(\) => \{[\s\S]*?dispatch\(\{ type: 'discard-pending' \}\)/);
});

test('admin knowledge action errors remain visible inside active dialogs', () => {
  const page = fs.readFileSync(path.join(root, 'src/app/admin/assistant-knowledge/page.tsx'), 'utf8');
  const modal = fs.readFileSync(path.join(root, 'src/components/ui/modal.tsx'), 'utf8');
  assert.match(page, /role="alert"/);
  assert.match(page, /error=\{archiveTarget \? error : undefined\}/);
  assert.match(modal, /error\?: string/);
  assert.match(modal, /\{error\}/);
});

test('admin knowledge is available through the localized route tree', () => {
  const localizedPage = fs.readFileSync(
    path.join(root, 'src/app/[locale]/admin/assistant-knowledge/page.tsx'),
    'utf8',
  );
  assert.match(localizedPage, /admin\/assistant-knowledge\/page/);
});

test('assistant history routes URI-encode owner-scoped identifiers', () => {
  const source = fs.readFileSync(path.join(root, 'src/lib/thesis-api.ts'), 'utf8');
  assert.match(source, /conversations\/\$\{encodeURIComponent\(conversationId\)\}\/messages/);
  assert.match(source, /delete\(`\/thesis\/assistant\/conversations\/\$\{encodeURIComponent\(conversationId\)\}`\)/);
});
