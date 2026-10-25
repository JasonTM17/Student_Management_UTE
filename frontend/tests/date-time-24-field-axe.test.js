'use strict';

const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');

// Same transpile harness as combobox-axe.test.js: real component, stubbed i18n.
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
    if (name === '@/lib/date-time-24') return load('src/lib/date-time-24.ts', aliases);
    if (Object.prototype.hasOwnProperty.call(aliases, name)) return aliases[name];
    return require(name);
  };
  Function('module', 'exports', 'require', output)(record, record.exports, resolve);
  return record.exports;
}

const fieldFor = (messages) =>
  load('src/components/ui/date-time-24-field.tsx', { '@/i18n': { useI18n: () => ({ messages }) } });

const EN = { common: { dateTime24: { date: 'Date', hour: 'Hour', minute: 'Minute' } } };
const VI = { common: { dateTime24: { date: 'Ngày', hour: 'Giờ', minute: 'Phút' } } };

const render = (messages, props) =>
  renderToStaticMarkup(
    React.createElement(fieldFor(messages).DateTime24Field, {
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

test('empty, filled, required and errored states pass axe with no serious violations', async (t) => {
  const browser = await browserOrSkip(t);
  if (!browser) return;
  try {
    const page = await browser.newPage();
    const states = {
      empty: render(EN, { label: 'Registration start' }),
      filled: render(EN, { label: 'Registration start', value: '2026-10-25T09:30' }),
      seconds: render(EN, { label: 'Report deadline', value: '2026-10-25T09:00:00.140', required: true }),
      error: render(EN, { label: 'Registration end', error: 'End is required' }),
      vietnamese: render(VI, { label: 'Bắt đầu đăng ký', value: '2026-10-25T23:45' }),
      unlabelled: render(EN, { 'aria-label': 'Publish at' }),
      disabled: render(EN, { label: 'Locked', disabled: true, value: '2026-10-25T09:30' }),
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

test('the grouped controls resolve their names end-to-end', async (t) => {
  const browser = await browserOrSkip(t);
  if (!browser) return;
  try {
    const page = await browser.newPage();
    await page.setContent(
      `<!doctype html><body><main>${render(EN, { label: 'Registration start', value: '2026-10-25T09:30' })}</main></body>`,
    );
    const names = await page.evaluate(`(() => {
      const group = document.querySelector('[role="group"]');
      const controls = [...group.querySelectorAll('input,select')].map((c) =>
        c.getAttribute('aria-label') ||
        document.querySelector('label[for="' + c.id + '"]')?.textContent || c.id);
      return {
        groupName: document.getElementById(group.getAttribute('aria-labelledby'))?.textContent,
        controls,
      };
    })()`);
    assert.equal(names.groupName, 'Registration start');
    assert.deepEqual(names.controls, ['Date', 'Hour', 'Minute']);
    await page.close();
  } finally {
    await browser.close();
  }
});
