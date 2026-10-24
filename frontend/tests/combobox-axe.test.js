'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');

// Same transpile harness as combobox.test.js: real component, stubbed i18n.
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

const comboboxFor = (messages) =>
  load('src/components/ui/combobox.tsx', { '@/i18n': { useI18n: () => ({ messages }) } });

const EN = {
  common: {
    states: { loading: 'Loading…' },
    combobox: { noResults: 'No matching options', clearSelection: 'Clear selection' },
  },
};
const VI = {
  common: {
    states: { loading: 'Đang tải…' },
    combobox: { noResults: 'Không có kết quả phù hợp', clearSelection: 'Xóa lựa chọn' },
  },
};

const OPTIONS = [
  { value: 'l1', label: 'Nguyễn Văn An', description: 'CNTT · GV0001' },
  { value: 'l2', label: 'Trần Thị Bích', description: 'CKTK · GV0002' },
  { value: 'l3', label: 'Đặng Minh Đức', disabled: true, disabledReason: 'Supervises this topic' },
  { value: 'l4', label: 'An Nguyễn' },
];

const render = (messages, props) =>
  renderToStaticMarkup(
    React.createElement(comboboxFor(messages).Combobox, {
      options: OPTIONS,
      value: '',
      onChange: () => {},
      ...props,
    }),
  );

async function launchBrowser() {
  const { chromium } = require('@playwright/test');
  try {
    return await chromium.launch({ headless: true });
  } catch (bundledBrowserError) {
    for (const channel of ['chrome', 'msedge']) {
      try {
        return await chromium.launch({ channel, headless: true });
      } catch {
        // Try the next installed Chromium channel.
      }
    }
    throw bundledBrowserError;
  }
}

async function browserOrSkip(t) {
  try {
    return await launchBrowser();
  } catch {
    t.skip('no chromium/chrome/msedge channel is installed for Playwright');
    return null;
  }
}

const axeSource = fs.readFileSync(
  path.join(root, 'node_modules', 'axe-core', 'axe.min.js'),
  'utf8',
);

async function analyze(page, fragment) {
  await page.setContent(
    `<!doctype html><html lang="en"><head><title>fixture</title></head>` +
      `<body><main id="fixture">${fragment}</main></body></html>`,
  );
  await page.addScriptTag({ content: axeSource });
  return page.evaluate(
    `axe.run(document.getElementById('fixture'), {
      resultTypes: ['violations', 'incomplete'],
    })`,
  );
}

function assertNoSerious(results, label) {
  const serious = results.violations.filter((v) =>
    ['critical', 'serious'].includes(v.impact),
  );
  assert.deepEqual(
    serious.map((v) => `${v.id}: ${v.nodes.map((n) => n.target).join(', ')}`),
    [],
    `${label} has no serious/critical axe violations`,
  );
}

test('the closed and open combobox states pass axe with no serious violations', async (t) => {
  const browser = await browserOrSkip(t);
  if (!browser) return;
  try {
    const page = await browser.newPage();
    const states = {
      closed: render(EN, { label: 'Assign reviewer', value: 'l2' }),
      open: render(EN, {
        label: 'Assign reviewer',
        value: 'l2',
        allowClear: true,
        defaultOpen: true,
        'aria-label': 'Assign reviewer',
      }),
      loading: render(EN, { label: 'Assign reviewer', defaultOpen: true, loading: true }),
      empty: render(EN, { label: 'Assign reviewer', defaultOpen: true, options: [] }),
      error: render(EN, { label: 'Assign reviewer', error: 'Reviewer required' }),
      vietnamese: render(VI, { label: 'Phản biện', defaultOpen: true }),
    };
    for (const [name, fragment] of Object.entries(states)) {
      const results = await analyze(page, fragment);
      assertNoSerious(results, name);
    }
    await page.close();
  } finally {
    await browser.close();
  }
});

test('the open listbox marks the selection and the disabled reason accessibly', async (t) => {
  const browser = await browserOrSkip(t);
  if (!browser) return;
  try {
    const page = await browser.newPage();
    const results = await analyze(
      page,
      render(EN, { label: 'Assign reviewer', value: 'l2', allowClear: true, defaultOpen: true }),
    );
    // Also fail on anything axe could not fully evaluate inside the fixture.
    assert.equal(results.incomplete.length, 0, 'axe finished every check inside the fixture');
    assertNoSerious(results, 'open listbox');
    await page.close();
  } finally {
    await browser.close();
  }
});
