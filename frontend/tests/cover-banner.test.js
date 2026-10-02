const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

// Execute the actual TypeScript helper using the project's Node20-compatible
// test convention. No generated copy or test-only implementation is involved.
const source = fs.readFileSync(path.join(__dirname, '../src/lib/cover-banner.ts'), 'utf8');
const compiled = ts.transpileModule(source, {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText;
const moduleRecord = { exports: {} };
Function('module', 'exports', compiled)(moduleRecord, moduleRecord.exports);
const { replaceCoverBlock, removeCoverBlock } = moduleRecord.exports;

const BANNER_URL = '/images/banners/campus_academic_banner.jpg';
const BANNER_FIGURE = (url) =>
  `<figure class="my-3 text-center"><img src="${url}" alt="banner" /><figcaption>banner</figcaption></figure>`;

test('replacing the cover leaves an ordinary inline figure untouched', () => {
  const inlineFigure = '<figure><img src="/uploads/screenshot-123.png" alt="csdl" /><figcaption>Hình 1</figcaption></figure>';
  const paragraph = '<p>Đoạn văn giữa hai hình.</p>';
  const document = inlineFigure + paragraph + BANNER_FIGURE(BANNER_URL);

  const result = replaceCoverBlock(document, BANNER_URL, BANNER_FIGURE('/images/news/new.jpg'));

  assert.ok(result);
  assert.ok(result.includes('screenshot-123.png'), 'the inline screenshot must survive');
  assert.ok(result.includes('Hình 1'), 'the inline caption must survive');
  assert.ok(result.includes(paragraph), 'the paragraph between figures must survive');
  assert.ok(!result.includes(BANNER_URL), 'the old banner must be gone');
  assert.equal(result.match(/<figure/g)?.length, 2, 'exactly two figures remain');
});

test('removing the cover deletes only the banner block', () => {
  const inlineFigure = '<figure><img src="/uploads/screenshot-123.png" alt="csdl" /></figure>';
  const document = BANNER_FIGURE(BANNER_URL) + inlineFigure;

  const result = removeCoverBlock(document, BANNER_URL);

  assert.ok(result);
  assert.equal(result, inlineFigure, 'only the banner figure is removed');
});

test('a cover URL that no block carries is reported as unmatched', () => {
  const document = '<p>plain document</p>' + BANNER_FIGURE('/images/banners/other_banner.jpg');
  assert.equal(replaceCoverBlock(document, BANNER_URL, '<figure/>'), null);
  assert.equal(removeCoverBlock(document, BANNER_URL), null);
});

test('a bare cover image outside a figure is replaced in place', () => {
  const document = '<p>intro</p><img src="' + BANNER_URL + '" alt="cover" /><p>body</p>';
  const result = replaceCoverBlock(document, BANNER_URL, BANNER_FIGURE('/images/news/new.jpg'));
  assert.ok(result);
  assert.ok(result.includes('/images/news/new.jpg'));
  assert.ok(!result.includes(BANNER_URL));
  assert.ok(result.includes('<p>body</p>'), 'the trailing paragraph must survive');
});

test('a duplicate cover URL in an ordinary figure does not shadow the authored banner', () => {
  // An inline figure quoting the same banner asset must not become the
  // splice target: the authored banner figure carries the signature class.
  const inlineQuote = '<figure><img src="' + BANNER_URL + '" alt="trích dẫn" /><figcaption>Nhắc lại ảnh cũ</figcaption></figure>';
  const bannerFigure = BANNER_FIGURE(BANNER_URL);
  const document = inlineQuote + bannerFigure;

  const result = removeCoverBlock(document, BANNER_URL);

  assert.ok(result);
  assert.ok(result.includes('trích dẫn'), 'the inline quote must survive');
  assert.ok(!result.includes('my-3 text-center'), 'the authored banner is the removed block');
});
