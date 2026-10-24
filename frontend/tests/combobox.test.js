const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');

// The transpile harness the ui primitive tests use, extended so the combobox's
// real @/ dependencies resolve and only the i18n provider is stubbed.
function load(relativePath, aliases = {}) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.ReactJSX,
      esModuleInterop: true,
    },
    fileName: relativePath,
  }).outputText;
  const record = { exports: {} };
  const resolve = (name) => {
    if (name === '@/lib/utils') return load('src/lib/utils.ts', aliases);
    if (name === '@/lib/search-text') return load('src/lib/search-text.ts', aliases);
    if (name === '@/lib/registration-browse') return load('src/lib/registration-browse.ts', aliases);
    if (Object.prototype.hasOwnProperty.call(aliases, name)) return aliases[name];
    return require(name);
  };
  Function('module', 'exports', 'require', output)(record, record.exports, resolve);
  return record.exports;
}

const i18nStub = {
  '@/i18n': {
    useI18n: () => ({
      messages: {
        common: {
          states: { loading: 'Loading…' },
          combobox: { noResults: 'No matching options', clearSelection: 'Clear selection' },
        },
      },
    }),
  },
};

const comboboxModule = load('src/components/ui/combobox.tsx', i18nStub);
const { Combobox, resolveComboboxNavigation } = comboboxModule;

const OPTIONS = [
  { value: 'l1', label: 'Nguyễn Văn An', description: 'CNTT · GVPB ×2' },
  { value: 'l2', label: 'Trần Thị Bích' },
  { value: 'l3', label: 'Đặng Minh Đức', disabled: true, disabledReason: 'Supervises this topic' },
  { value: 'l4', label: 'An Nguyễn' },
];

const render = (props) =>
  renderToStaticMarkup(
    React.createElement(Combobox, {
      options: OPTIONS,
      value: '',
      onChange: () => {},
      ...props,
    }),
  );

// ---------- WAI-ARIA markup contract ----------

test('closed combobox renders role=combobox with collapsed listbox state', () => {
  const html = render({ 'aria-label': 'Assign reviewer' });
  assert.match(html, /role="combobox"/);
  assert.match(html, /aria-expanded="false"/);
  assert.match(html, /aria-autocomplete="list"/);
  assert.match(html, /aria-label="Assign reviewer"/);
  assert.doesNotMatch(html, /role="listbox"/);
});

test('open combobox exposes the listbox, options and active descendant wiring', () => {
  const html = render({ 'aria-label': 'Assign reviewer', defaultOpen: true });
  assert.match(html, /aria-expanded="true"/);
  const controls = html.match(/aria-controls="([^"]+)"/);
  assert.ok(controls, 'aria-controls is present while open');
  const listboxId = controls[1];
  assert.match(html, new RegExp(`<ul[^>]*id="${listboxId}"[^>]*role="listbox"`));
  assert.match(html, new RegExp(`id="${listboxId}-opt-0" role="option"[^>]*>[\\s\\S]{0,900}Nguyễn Văn An`));
});

test('the selected option is marked aria-selected and checked', () => {
  const html = render({ defaultOpen: true, value: 'l2', allowClear: true });
  assert.match(html, /role="option"[^>]*aria-selected="true"[\s\S]{0,1200}Trần Thị Bích/);
});

test('the selected label is the resting input value', () => {
  const html = render({ value: 'l4' });
  assert.match(html, /value="An Nguyễn"/);
});

test('a disabled option carries aria-disabled and its visible reason', () => {
  const html = render({ defaultOpen: true });
  const row = html.match(/<li id="[^"]+" role="option"[^>]*aria-disabled="true"[\s\S]{0,1600}?<\/li>/);
  assert.ok(row, 'an option row carries aria-disabled');
  assert.match(row[0], /Đặng Minh Đức/);
  assert.match(row[0], /Supervises this topic/);
});

test('a description renders as secondary muted text', () => {
  const html = render({ defaultOpen: true });
  assert.match(html, /CNTT · GVPB ×2/);
});

test('error wiring matches the Select primitive (aria-invalid + describedby)', () => {
  const html = render({ error: 'Pick one' });
  assert.match(html, /aria-invalid="true"/);
  const describedby = html.match(/aria-describedby="([^"]+)"/)[1];
  assert.match(html, new RegExp(`<p id="${describedby}"[^>]*>Pick one`));
});

test('a visible label points at the input via htmlFor', () => {
  const html = render({ label: 'Council member' });
  const inputId = html.match(/id="([^"]+)"[^>]*role="combobox"/)[1];
  assert.match(html, new RegExp(`<label for="${inputId}"[^>]*>Council member`));
});

test('allowClear renders a clear row and button only when a value is set', () => {
  const withValue = render({ defaultOpen: true, value: 'l1', allowClear: true, emptyLabel: '-- No reviewer --' });
  assert.match(withValue, /-- No reviewer --/);
  assert.match(withValue, /aria-label="Clear selection"/);
  const withoutValue = render({ defaultOpen: true, value: '', allowClear: true, emptyLabel: '-- No reviewer --' });
  assert.doesNotMatch(withoutValue, /-- No reviewer --/);
  assert.doesNotMatch(withoutValue, /aria-label="Clear selection"/);
  const notClearable = render({ defaultOpen: true, value: 'l1' });
  assert.doesNotMatch(notClearable, /aria-label="Clear selection"/);
});

test('empty and loading states read through the i18n labels', () => {
  const empty = render({ defaultOpen: true, options: [] });
  assert.match(empty, /No matching options/);
  const loading = render({ defaultOpen: true, options: [], loading: true });
  assert.match(loading, /Loading…/);
  assert.doesNotMatch(loading, /No matching options/);
});

// ---------- keyboard navigation reducer ----------

const nav = (key, state) => resolveComboboxNavigation(key, state);
const list = (disabled = []) => OPTIONS.map((o, i) => ({ disabled: disabled.includes(i) }));

test('ArrowDown opens a closed combobox on the selected option, else the first', () => {
  assert.deepEqual(nav('ArrowDown', { open: false, activeIndex: -1, selectedIndex: 2, options: list() }),
    { preventDefault: true, open: true, activeIndex: 2 });
  assert.deepEqual(nav('ArrowDown', { open: false, activeIndex: -1, selectedIndex: -1, options: list() }),
    { preventDefault: true, open: true, activeIndex: 0 });
});

test('ArrowDown/ArrowUp move within the open list, skipping disabled and wrapping', () => {
  assert.deepEqual(nav('ArrowDown', { open: true, activeIndex: 1, options: list([2]) }),
    { preventDefault: true, activeIndex: 3 });
  // wrap past the end
  assert.deepEqual(nav('ArrowDown', { open: true, activeIndex: 3, options: list() }),
    { preventDefault: true, activeIndex: 0 });
  // wrap past the start going up
  assert.deepEqual(nav('ArrowUp', { open: true, activeIndex: 0, options: list() }),
    { preventDefault: true, activeIndex: 3 });
  assert.deepEqual(nav('ArrowUp', { open: true, activeIndex: 3, options: list([2]) }),
    { preventDefault: true, activeIndex: 1 });
});

test('Home and End jump to the first and last enabled option', () => {
  assert.deepEqual(nav('Home', { open: true, activeIndex: 2, options: list([0]) }),
    { preventDefault: true, activeIndex: 1 });
  assert.deepEqual(nav('End', { open: true, activeIndex: 0, options: list([3]) }),
    { preventDefault: true, activeIndex: 2 });
});

test('Enter commits the active option and never fires on a disabled one', () => {
  assert.deepEqual(nav('Enter', { open: true, activeIndex: 1, options: list() }),
    { preventDefault: true, commitIndex: 1 });
  // active disabled option: Enter is inert but still consumed
  assert.deepEqual(nav('Enter', { open: true, activeIndex: 2, options: list([2]) }),
    { preventDefault: true });
  // no active option: commit the first enabled match
  assert.deepEqual(nav('Enter', { open: true, activeIndex: -1, options: list([0]) }),
    { preventDefault: true, commitIndex: 1 });
});

test('Enter opens a closed combobox without submitting the form', () => {
  assert.deepEqual(nav('Enter', { open: false, activeIndex: -1, options: list() }),
    { preventDefault: true, open: true, activeIndex: 0 });
});

test('Escape closes only the popup and stays quiet when already closed', () => {
  assert.deepEqual(nav('Escape', { open: true, activeIndex: 0, options: list() }),
    { preventDefault: true, close: true });
  assert.deepEqual(nav('Escape', { open: false, activeIndex: -1, options: list() }), {});
});

test('Tab closes the popup without stealing the keypress', () => {
  assert.deepEqual(nav('Tab', { open: true, activeIndex: 0, options: list() }),
    { close: true });
  assert.deepEqual(nav('Tab', { open: false, activeIndex: -1, options: list() }), {});
});

test('unrelated keys do nothing', () => {
  assert.deepEqual(nav('a', { open: true, activeIndex: 0, options: list() }), {});
  assert.deepEqual(nav('PageDown', { open: true, activeIndex: 0, options: list() }), {});
});

// ---------- i18n parity for the combobox keys ----------

function loadMessages() {
  return load('src/i18n/messages.ts', {
    '@/i18n/config': {},
    '@/i18n/messages-thesis-workflow': load('src/i18n/messages-thesis-workflow.ts'),
    '@/i18n/messages-dean-overview': load('src/i18n/messages-dean-overview.ts'),
  });
}

test('en and vi both carry the combobox strings', () => {
  const { en, vi } = loadMessages();
  for (const key of ['noResults', 'clearSelection']) {
    assert.equal(typeof en.common.combobox[key], 'string', `en.common.combobox.${key}`);
    assert.equal(typeof vi.common.combobox[key], 'string', `vi.common.combobox.${key}`);
    assert.notEqual(en.common.combobox[key], vi.common.combobox[key],
      `${key} is actually translated, not copied`);
  }
});
