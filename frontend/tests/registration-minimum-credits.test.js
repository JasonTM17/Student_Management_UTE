const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadNotice() {
  const source = read('src/components/registration/minimum-credits-notice.tsx');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX },
  }).outputText;
  const loadedModule = { exports: {} };
  Function('module', 'exports', 'require', output)(loadedModule, loadedModule.exports, require);
  return loadedModule.exports.MinimumCreditsNotice;
}

test('registration summary contract and page consume the advisory fields', () => {
  const apiSource = read('src/lib/api.ts');
  const pageSource = read('src/app/dashboard/register/page.tsx');
  const messagesSource = read('src/i18n/messages.ts');

  assert.match(apiSource, /minCredits:\s*number/);
  assert.match(apiSource, /belowMinimum:\s*boolean/);
  assert.match(pageSource, /summaryQuery\.data\?\.belowMinimum/);
  assert.match(pageSource, /summaryQuery\.data\.minCredits/);
  assert.match(pageSource, /<MinimumCreditsNotice/);
  assert.equal((messagesSource.match(/minimumCreditsNotice:/g) || []).length, 2);
});

test('below-minimum notice renders as a calm accessible status in English and Vietnamese', () => {
  const MinimumCreditsNotice = loadNotice();
  const messages = [
    'You are below the 14-credit advisory minimum. Registration is still available.',
    'Bạn đang dưới mức khuyến nghị tối thiểu 14 tín chỉ. Bạn vẫn có thể đăng ký.',
  ];

  for (const message of messages) {
    const markup = renderToStaticMarkup(React.createElement(MinimumCreditsNotice, {
      belowMinimum: true,
      message,
    }));
    assert.match(markup, /role="status"/);
    assert.match(markup, /bg-status-warning/);
    assert.match(markup, new RegExp(message.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
    assert.doesNotMatch(markup, /role="alert"/);
  }
});

test('notice stays hidden when the summary is at or above its minimum', () => {
  const MinimumCreditsNotice = loadNotice();

  const markup = renderToStaticMarkup(React.createElement(MinimumCreditsNotice, {
    belowMinimum: false,
    message: 'This advisory notice is hidden.',
  }));

  assert.equal(markup, '');
});
