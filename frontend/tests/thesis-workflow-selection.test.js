const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const { chromium } = require('@playwright/test');

const root = path.resolve(__dirname, '..');
function compile(relative) {
  return ts.transpileModule(fs.readFileSync(path.join(root, relative), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.React, jsxFactory: 'React.createElement', esModuleInterop: true },
    fileName: relative,
  }).outputText;
}

test('workflow guidance follows round and status changes but preserves manual browsing on refresh', async () => {
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage();
    await page.setContent('<div id="root"></div>');
    await page.addScriptTag({ path: path.join(root, 'node_modules/react/umd/react.development.js') });
    await page.addScriptTag({ path: path.join(root, 'node_modules/react-dom/umd/react-dom.development.js') });
    await page.evaluate(({ component, messages }) => {
      const dictionary = { exports: {} };
      Function('module', 'exports', messages)(dictionary, dictionary.exports);
      const record = { exports: {} };
      const resolve = (name) => {
        if (name === 'react') return window.React;
        if (name === 'lucide-react') return new Proxy({}, { get: () => () => null });
        if (name === '@/i18n') return { useI18n: () => ({ messages: { thesisWorkflow: dictionary.exports.thesisWorkflowEn } }) };
        if (name === '@/lib/utils') return { cn: (...values) => values.filter(Boolean).join(' ') };
        throw new Error(`Unexpected module ${name}`);
      };
      Function('module', 'exports', 'require', 'React', component)(record, record.exports, resolve, window.React);
      const root = window.ReactDOM.createRoot(document.getElementById('root'));
      window.renderRound = (id, status) => root.render(window.React.createElement(record.exports.ThesisWorkflowStepper, {
        round: { id, status, lecturerSubmitStart: '2026-01-01', lecturerSubmitEnd: '2026-02-01', registrationStart: '2026-03-01', registrationEnd: '2026-04-01' },
        formatDateTime: String,
      }));
    }, { component: compile('src/components/thesis/ThesisWorkflowStepper.tsx'), messages: compile('src/i18n/messages-thesis-workflow.ts') });
    const selectedHeading = page.locator('h4').last();
    async function expectStage(step) {
      await page.waitForFunction(n => document.querySelector('h4:last-of-type') && [...document.querySelectorAll('h4')].at(-1).textContent.includes(`Stage ${n}:`), step);
      assert.match(await selectedHeading.textContent(), new RegExp(`Stage ${step}:`));
    }
    await page.evaluate(() => window.renderRound('round-a', 'REGISTRATION_OPEN'));
    await expectStage(3);
    await page.evaluate(() => window.renderRound('round-b', 'REGISTRATION_CLOSED'));
    await expectStage(4);
    await page.locator('button').filter({ has: page.locator('h4') }).first().click();
    await expectStage(1);
    await page.evaluate(() => window.renderRound('round-b', 'REGISTRATION_CLOSED'));
    await expectStage(1);
    await page.evaluate(() => window.renderRound('round-c', 'REGISTRATION_CLOSED'));
    await expectStage(4);
    await page.evaluate(() => window.renderRound('round-c', 'CLOSED'));
    await expectStage(5);
  } finally { await browser.close(); }
});
