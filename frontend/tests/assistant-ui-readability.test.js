const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');
const baseline = process.env.ASSISTANT_UI_BASELINE;
const messages = { assistant: {
  you: 'You', label: 'Assistant', thinking: 'Thinking', technicalBlocked: 'Blocked',
  placeholder: 'Ask about registration', specializedPlaceholder: 'Ask about programming',
  stop: 'Stop generating', stopLabel: 'Stop', responding: 'Responding…',
  respondingHint: 'The composer is locked', composerHint: 'Enter to send', send: 'Send', answered: 'Answered',
  cancelled: 'Generation stopped. You can retry when ready.',
} };

function load(relative, cache = new Map()) {
  if (cache.has(relative)) return cache.get(relative);
  const snapshot = baseline && path.join(baseline, 'frontend', relative);
  const filename = snapshot && fs.existsSync(snapshot) ? snapshot : path.join(root, relative);
  const output = ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText;
  const record = { exports: {} };
  const dependency = (name) => {
    if (name === '@/i18n') return { useI18n: () => ({ messages, locale: 'en', href: (value) => value }) };
    if (name === 'next/navigation') return { useRouter: () => ({ push() {} }) };
    if (name === 'sonner') return { toast: { success() {}, error() {} } };
    if (name === '@/lib/utils') return { cn: (...values) => values.filter(Boolean).join(' ') };
    if (name === '@/lib/assistant-output-guard') return {
      sanitizeAssistantOutput: (content) => content, isAssistantOutputSafe: () => true,
      normalizeAssistantCopy: (content) => content,
    };
    if (name === '@/components/ui/button') return { Button: ({ variant: _variant, size: _size, ...props }) => React.createElement('button', props) };
    if (name.startsWith('@/lib/')) return load(`src/lib/${name.slice(6)}.ts`, cache);
    if (name.startsWith('.')) {
      const base = path.posix.join(path.posix.dirname(relative), name);
      return load(`${base}${fs.existsSync(path.join(root, `${base}.tsx`)) ? '.tsx' : '.ts'}`, cache);
    }
    return require(name);
  };
  Function('require', 'module', 'exports', output)(dependency, record, record.exports);
  cache.set(relative, record.exports);
  return record.exports;
}

test('user messages preserve literal Markdown and escaped HTML with inherited bubble ink', () => {
  const { AssistantMessages } = load('src/components/assistant/AssistantMessages.tsx');
  const prompt = '**Cần hỗ trợ** [Xem lịch](/dashboard/schedule) `SE421` <img src=x>';
  const html = renderToStaticMarkup(React.createElement(AssistantMessages, {
    messageList: [{ id: 'user-1', role: 'user', content: prompt }], onFeedback() {},
  }));
  assert.match(html, /<p class="whitespace-pre-wrap">\*\*Cần hỗ trợ\*\* \[Xem lịch\]\(\/dashboard\/schedule\) `SE421` &lt;img src=x&gt;<\/p>/);
  assert.doesNotMatch(html, /<a |<code|<strong/);
});

test('ordered and unordered answer items expose actual grouped list semantics', () => {
  const { AssistantMarkdownContent } = load('src/components/assistant/AssistantMarkdownContent.tsx');
  const html = renderToStaticMarkup(React.createElement(AssistantMarkdownContent, {
    content: '1. First\n2. Second\n\nParagraph\n\n- Alpha\n- Beta', guardOutput: false,
  }));
  assert.equal((html.match(/<ol /g) || []).length, 1);
  assert.equal((html.match(/<ul /g) || []).length, 1);
  assert.equal((html.match(/<li /g) || []).length, 4);
  assert.match(html, /<li value="2"/);
});

test('Stop is visibly an action and specialized composer describes its actual scope', () => {
  const { AssistantComposer } = load('src/components/assistant/AssistantComposer.tsx');
  const html = renderToStaticMarkup(React.createElement(AssistantComposer, {
    input: '', inputRef: { current: null }, isSending: true, scope: 'specialized',
    onInputChange() {}, onSubmit() {}, onStop() {},
  }));
  assert.match(html, /<span>Stop<\/span>/);
  assert.match(html, /placeholder="Ask about programming"/);
  assert.match(html, /min-w-0/);
});

test('two mounted composers never share their hint or character-count IDs', () => {
  const { AssistantComposer } = load('src/components/assistant/AssistantComposer.tsx');
  const props = { input: '', inputRef: { current: null }, isSending: false, onInputChange() {}, onSubmit() {}, onStop() {} };
  const html = renderToStaticMarkup(React.createElement(React.Fragment, null,
    React.createElement(AssistantComposer, props), React.createElement(AssistantComposer, props),
  ));
  const ids = [...html.matchAll(/\bid="([^"]+)"/g)].map((match) => match[1]);
  assert.equal(new Set(ids).size, ids.length);
  const references = [...html.matchAll(/aria-describedby="([^"]+)"/g)].flatMap((match) => match[1].split(' '));
  assert.deepEqual(new Set(references), new Set(ids));
});

test('cancelled placeholder appears once while a partial answer retains its stopped status', () => {
  const { AssistantMessages } = load('src/components/assistant/AssistantMessages.tsx');
  const renderCancelled = (content) => renderToStaticMarkup(React.createElement(AssistantMessages, {
    messageList: [{ id: 'cancelled-1', role: 'assistant', content, reasonCode: 'CANCELLED', degraded: true }],
    onFeedback() {},
  }));
  const placeholder = renderCancelled(messages.assistant.cancelled);
  assert.equal(placeholder.split(messages.assistant.cancelled).length - 1, 1);
  const partial = renderCancelled('A partial answer arrived before cancellation.');
  assert.match(partial, /A partial answer arrived before cancellation\./);
  assert.equal(partial.split(messages.assistant.cancelled).length - 1, 1);
});

test('localized page title survives replacement by navigation metadata and releases its observer', () => {
  const head = { parentNode: null };
  let titleNode = { textContent: 'Initial title', parentNode: head };
  let observation;
  let disconnected = false;
  let cleanup;
  const notify = (target) => {
    if (!observation || disconnected) return;
    for (let node = target; node; node = observation.options.subtree ? node.parentNode : null) {
      if (node === observation.target) { observation.callback(); break; }
    }
  };
  const document = {
    head,
    querySelector: () => titleNode,
    get title() { return titleNode.textContent; },
    set title(value) { titleNode.textContent = value; notify(titleNode); },
  };
  class MutationObserver {
    constructor(callback) { this.callback = callback; }
    observe(target, options) { observation = { target, options, callback: this.callback }; }
    disconnect() { disconnected = true; }
  }
  const output = ts.transpileModule(fs.readFileSync(path.join(root, 'src/lib/use-document-title.ts'), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const exports = {};
  Function('require', 'document', 'MutationObserver', 'exports', output)(
    () => ({ useEffect: (effect) => { cleanup = effect(); } }), document, MutationObserver, exports,
  );
  exports.useDocumentTitle('Specialized Assistant');
  assert.equal(document.title, 'Specialized Assistant | CampusUTE');
  titleNode.parentNode = null;
  titleNode = { textContent: 'Campus portal | CampusUTE', parentNode: head };
  notify(head);
  assert.equal(document.title, 'Specialized Assistant | CampusUTE');
  document.title = 'Another metadata update';
  assert.equal(document.title, 'Specialized Assistant | CampusUTE');
  cleanup();
  document.title = 'Next route title';
  assert.equal(document.title, 'Next route title');
});
