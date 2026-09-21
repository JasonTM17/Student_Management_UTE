const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

/** Transpile a DOM-free TS module (a lib) and evaluate it. */
function loadTs(relativePath) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

/**
 * Transpile a TSX module whose imports must be redirected: `stubs` maps an
 * import specifier to a module record, anything else goes to Node's require
 * (real `react`, `react-dom/server`, `typescript`).
 */
function loadTsModule(relativePath, stubs) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.React,
      // The transpiled JSX calls `React.createElement` through the default
      // import; without the interop helper that binding is undefined in CJS.
      esModuleInterop: true,
    },
  }).outputText;
  const moduleRecord = { exports: {} };
  const stubRequire = (name) => {
    if (Object.prototype.hasOwnProperty.call(stubs, name)) return stubs[name];
    return require(name);
  };
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, stubRequire);
  return moduleRecord.exports;
}

const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const dummyComponent = function DummyComponent() {
  return null;
};
// Every icon (and the `default` a namespace import may ask for) is the same
// inert component; `__esModule` keeps the interop helper from re-wrapping it.
const lucideStub = new Proxy(
  {},
  { get: (_target, prop) => (prop === '__esModule' ? true : dummyComponent) },
);
const utilsStub = { cn: (...args) => args.filter(Boolean).join(' ') };

const sanitizer = loadTs('src/lib/html-sanitizer.ts');
const presentation = loadTs('src/lib/announcement-presentation.ts');
const FIXTURE = JSON.parse(read('tests/fixtures/announcement-content-policy.json'));

function rendererStubs() {
  return {
    '@/lib/html-sanitizer': sanitizer,
    '@/lib/utils': utilsStub,
    'lucide-react': lucideStub,
  };
}

function loadRenderer(stubs) {
  return loadTsModule('src/components/ui/rich-content-renderer.tsx', stubs || rendererStubs());
}

function loadToc(renderer) {
  return loadTsModule('src/components/announcements/reader/TableOfContents.tsx', {
    ...rendererStubs(),
    '@/components/ui/rich-content-renderer': renderer,
  });
}

function renderElement(Component, props) {
  return renderToStaticMarkup(React.createElement(Component, props));
}

/** The `.rich-html-content` body of a rendered article, without the wrapper. */
function renderedBodyHtml(content) {
  const renderer = loadRenderer();
  return renderElement(renderer.RichContentRenderer, { content });
}

const HTML_ARTICLE = [
  '<h2>Điều khoản</h2><p>Nội dung một.</p>',
  '<h2>Điều khoản</h2><p>Nội dung hai.</p>',
  '<h3>Phụ lục &amp; biểu mẫu</h3>',
  '<h3><strong>Quy</strong> trình</h3>',
].join('');

// --- (a) contents for HTML bodies -------------------------------------------

test('RT-P3-a an HTML article produces contents entries with de-duplicated anchors', () => {
  const { extractTocHeadings } = loadToc(loadRenderer());

  const headings = extractTocHeadings(HTML_ARTICLE);
  // The slug alphabet is `slugify`'s existing one, which keeps ASCII letters and
  // digits only; what matters here is that TOC and markup derive the same ids.
  assert.deepEqual(
    headings.map((heading) => [heading.level, heading.id, heading.text]),
    [
      [2, 'ieu-khoan', 'Điều khoản'],
      [2, 'ieu-khoan-2', 'Điều khoản'],
      [3, 'phu-luc-bieu-mau', 'Phụ lục biểu mẫu'],
      [3, 'quy-trinh', 'Quy trình'],
    ],
    'HTML headings must be read, inner markup and character references removed, repeats suffixed',
  );

  // Nothing collides, and the empty/absent cases stay empty.
  assert.equal(new Set(headings.map((h) => h.id)).size, headings.length);
  assert.deepEqual(extractTocHeadings(''), []);
  assert.deepEqual(extractTocHeadings(null), []);
  assert.deepEqual(extractTocHeadings('<p>Không có tiêu đề nào</p>'), []);
});

test('RT-P3-a the rendered HTML branch carries the ids the contents links to', () => {
  const renderer = loadRenderer();
  const { extractTocHeadings } = loadToc(renderer);

  const markup = renderedBodyHtml(HTML_ARTICLE);
  const headings = extractTocHeadings(HTML_ARTICLE);
  assert.ok(headings.length >= 2, 'the fixture must be rich enough to show a contents list');

  for (const heading of headings) {
    const hits = markup.match(new RegExp(`<h[1-6][^>]*\\bid="${heading.id}"`, 'g')) ?? [];
    assert.equal(
      hits.length,
      1,
      `contents anchor "${heading.id}" must exist exactly once in: ${markup}`,
    );
  }
  // The prose itself is untouched by the pass.
  assert.match(markup, /<p>Nội dung một\.<\/p>/);
});

test('RT-P3-a markdown bodies still produce contents and cannot collide either', () => {
  const renderer = loadRenderer();
  const { extractTocHeadings } = loadToc(renderer);

  const markdown = [
    '# Thông báo',
    'body',
    '## Điều khoản',
    '## Điều khoản',
    '##### Phụ lục',
    '```',
    '# không phải tiêu đề',
    '```',
  ].join('\n');

  assert.deepEqual(
    extractTocHeadings(markdown).map((heading) => heading.id),
    ['thong-bao', 'ieu-khoan', 'ieu-khoan-2', 'phu-luc'],
    'a `#` line inside a fenced code block is source text, not a section',
  );

  const markup = renderedBodyHtml(markdown);
  // React puts the class after the id, so match the attribute, not the tag end.
  assert.match(markup, /<h1 id="thong-bao"/);
  assert.match(markup, /<h2 id="ieu-khoan"/);
  assert.match(markup, /<h2 id="ieu-khoan-2"/);
  assert.match(markup, /<h5 id="phu-luc"/);
  assert.equal(markup.includes('id="ieu-khoan-3"'), false);
  // The fenced `#` line stays inside the code block; it must not become a heading.
  assert.equal(markup.includes('id="khong-phai-tieu-de"'), false);
});

test('RT-P3-a anchoring runs after sanitizing so every generated id survives', () => {
  const renderer = loadRenderer();
  const body = '<h2>2025 Tuyển sinh</h2><h2 id="muc-1">Mục 1</h2>';

  // The sanitizer only accepts letter-initial ids, so injecting before it would
  // silently delete the anchor for a numbered heading.
  assert.doesNotMatch(sanitizer.sanitizeAnnouncementHtml('<h2 id="2025-x">y</h2>'), /id=/);

  const markup = renderedBodyHtml(body);
  assert.match(markup, /<h2 id="2025-tuyen-sinh">/);
  // An authored anchor is left alone, and the contents row points at it.
  assert.match(markup, /<h2 id="muc-1">Mục 1<\/h2>/);
  assert.deepEqual(
    renderer.extractArticleHeadingsFromHtml(sanitizer.sanitizeAnnouncementHtml(body)).map((h) => h.id),
    ['2025-tuyen-sinh', 'muc-1'],
  );
});

// --- (f) render-pipeline cost ------------------------------------------------

test('RT-P3-f the HTML render computation is cached on the content string', () => {
  const source = read('src/components/ui/rich-content-renderer.tsx');

  // The sanitizer rebuilds the whole body (up to the 200k-character server cap),
  // and the reader re-renders per scroll frame, so the work must sit behind a
  // memo keyed on `content` and the render path must not bypass it.
  assert.match(
    source,
    /const htmlBody = useMemo\(\(\) => \{[\s\S]*?return renderHtmlDocument\(content\);[\s\S]*?\}, \[content\]\);/,
  );
  assert.doesNotMatch(source, /dangerouslySetInnerHTML=\{\{ __html: sanitizeHtml\(/);
  assert.doesNotMatch(source, /dangerouslySetInnerHTML=\{\{ __html: annotateHtmlHeadingIds\(/);
  assert.match(source, /__html: htmlBody \}\}/);

  // Caching is only legal because the computation is pure: same bytes out for
  // the same body, every time.
  const renderer = loadRenderer();
  const first = renderer.renderHtmlDocument(HTML_ARTICLE);
  assert.equal(renderer.renderHtmlDocument(HTML_ARTICLE), first);
  assert.equal(first, renderer.annotateHtmlHeadingIds(sanitizer.sanitizeAnnouncementHtml(HTML_ARTICLE)));
});

test('RT-P3-f scroll progress is re-measured when a reading preference reflows the article', () => {
  const source = read('src/components/announcements/AnnouncementReaderModal.tsx');
  const effect = /container\.removeEventListener\('scroll', handleScroll\);[\s\S]*?\}, \[([\s\S]*?)\]\);/.exec(source);
  assert.ok(effect, 'the scroll listener effect must keep its teardown shape');

  // Font size and theme change `scrollHeight`, so the bar has to be recomputed
  // against the new layout instead of keeping the old percentage.
  for (const field of ['preferences.mode', 'preferences.theme', 'preferences.fontSize', 'preferences.fontFamily']) {
    assert.ok(effect[1].includes(field), `the scroll effect must depend on ${field}, saw: ${effect[1]}`);
  }
});

// --- (b) cover de-duplication guard ------------------------------------------

function loadMagazine() {
  return loadTsModule('src/components/announcements/reader/layouts/EditorialArticleMagazine.tsx', {
    ...rendererStubs(),
    'next/image': { __esModule: true, default: dummyComponent },
    '@/lib/announcement-presentation': presentation,
    '@/components/ui/rich-content-renderer': loadRenderer(),
    '@/lib/use-article-taxonomy': { useArticleTaxonomy: () => ({ categories: [], isLoading: false }) },
    '../DocumentAttachmentsList': { DocumentAttachmentsList: dummyComponent },
    '../TableOfContents': { TableOfContents: dummyComponent },
    '../RelatedAnnouncements': { RelatedAnnouncements: dummyComponent },
  });
}

const HERO_MARKER = 'data:image/png;base64,heromarker1234';
const RELATIVE_DIAGRAM = './uploads/sodo.png';

function magazineMarkup(overrides) {
  const magazine = loadMagazine();
  const announcement = {
    id: 'ann-1',
    title: 'Thông báo kế hoạch đào tạo',
    content: `<p>Mở đầu.</p><p><img src="${HERO_MARKER}" alt="Ảnh minh họa"></p><h2>Điều khoản</h2>`,
    priority: 'NORMAL',
    publishAt: '2026-01-05T07:00:00.000Z',
    createdAt: '2026-01-05T07:00:00.000Z',
    publishedBy: null,
    ...overrides,
  };
  return renderElement(magazine.EditorialArticleMagazine, {
    announcement,
    preferences: { mode: 'EDITORIAL', theme: 'light', fontFamily: 'sans', fontSize: 'base' },
    locale: 'vi',
  });
}

function countOccurrences(markup, needle) {
  return markup.split(needle).length - 1;
}

/** The rendered article body, i.e. everything after the hero figure. */
function bodyOf(markup) {
  const start = markup.indexOf('rich-html-content');
  assert.notEqual(start, -1, 'the article body must render');
  return markup.slice(start);
}

test('RT-P3-b the hero picture is shown once, not repeated under the hero', () => {
  const markup = magazineMarkup({});
  assert.equal(countOccurrences(markup, HERO_MARKER), 1, 'the body image became the hero, so the body drops it');
  assert.match(markup, /<h2 id="ieu-khoan"/, 'stripping must not eat the rest of the body');
});

test('RT-P3-b a picture the hero never showed stays in the body', () => {
  // A relative `./` source renders fine for the reader but is invisible to the
  // cover extractors, so the hero fell back to the branded placeholder while the
  // strip deleted the author's only picture — it appeared nowhere.
  const markup = magazineMarkup({
    content: `<p>Mở đầu.</p><p><img src="${RELATIVE_DIAGRAM}" alt="Sơ đồ quy trình"></p><h2>Điều khoản</h2>`,
  });
  assert.match(markup, /<img src="data:image\/svg\+xml,%3Csvg/, 'the branded placeholder is the hero here');
  assert.ok(
    bodyOf(markup).includes(RELATIVE_DIAGRAM),
    `the body must keep ${RELATIVE_DIAGRAM} when it is not the hero`,
  );
});

test('RT-P3-b a strip that would delete the wrong picture is refused', () => {
  // The extractors land on the second image (the first is a relative `./` src),
  // while the strip removes the first one. Losing a picture is worse than
  // showing the hero twice, so the body is kept whole.
  const markup = magazineMarkup({
    content: `<p><img src="${RELATIVE_DIAGRAM}" alt="Ảnh một"></p><p><img src="${HERO_MARKER}" alt="Ảnh hai"></p><h2>Điều khoản</h2>`,
  });
  assert.ok(bodyOf(markup).includes(RELATIVE_DIAGRAM), 'the first picture must not be taken for the hero');
  assert.ok(bodyOf(markup).includes(HERO_MARKER), 'and nothing else may be lost either');
});

test('RT-P3-b an imageless article keeps the branded placeholder hero and its body', () => {
  const markup = renderElement(loadMagazine().EditorialArticleMagazine, {
    announcement: {
      id: 'ann-2',
      title: 'Quy chế đánh giá điểm rèn luyện',
      content: '<p>Không có ảnh trong bài.</p>',
      priority: 'NORMAL',
      publishAt: '2026-01-05T07:00:00.000Z',
      createdAt: '2026-01-05T07:00:00.000Z',
    },
    preferences: { mode: 'EDITORIAL', theme: 'light', fontFamily: 'sans', fontSize: 'base' },
    locale: 'vi',
  });

  assert.match(markup, /<img src="data:image\/svg\+xml,%3Csvg/);
  assert.match(markup, /Không có ảnh trong bài\./);
});

// --- (c) pagebreak ----------------------------------------------------------

test('RT-P3-c the editor offers no page break, which nothing honoured anyway', () => {
  const source = read('src/components/ui/tinymce-editor.tsx');

  const plugins = source.slice(source.indexOf('plugins: ['), source.indexOf('],', source.indexOf('plugins: [')));
  const toolbar = source.slice(source.indexOf('toolbar: ['), source.indexOf('toolbar_mode'));
  const insertMenu = /insert: \{ title: 'Insert', items: '([^']*)' \}/.exec(source);

  assert.ok(plugins.includes("'lists'") && toolbar.includes('codesample') && insertMenu, 'the extraction windows must still find the config');
  for (const [label, region] of [['plugins', plugins], ['toolbar', toolbar], ['insert menu', insertMenu[1]]]) {
    assert.equal(region.includes('pagebreak'), false, `${label} still offers the dead pagebreak control`);
  }
  assert.equal(source.includes("'pagebreak'"), false);
  // The kept plugins stay available.
  assert.ok(plugins.includes("'accordion'") && plugins.includes("'anchor'"));
});

// --- (d) SVG images ---------------------------------------------------------

test('RT-P3-d an inlined SVG really is unpublishable, so the editor must refuse it', () => {
  // Premise of the fix: the sanitizer drops the `src`, leaving a broken image.
  const svgBody = '<p><img src="data:image/svg+xml;base64,PHN2Zy8-" alt="Sơ đồ"></p>';
  const cleaned = sanitizer.sanitizeAnnouncementHtml(svgBody);
  assert.equal(cleaned.includes('src='), false, `svg src survived: ${cleaned}`);
  assert.equal(sanitizer.isSafeAnnouncementImageUrl('data:image/svg+xml;base64,PHN2Zy8='), false);

  const source = read('src/components/ui/tinymce-editor.tsx');
  // Both insert points check the type before any payload is embedded.
  const uploadHandler = source.slice(source.indexOf('images_upload_handler'), source.indexOf('file_picker_callback'));
  const picker = source.slice(source.indexOf('file_picker_callback'), source.indexOf('table_default_attributes'));
  assert.match(uploadHandler, /if \(blobType\.includes\('svg'\)\) \{\s*\n\s*reject\(svgRejected\);/);
  assert.ok(
    uploadHandler.indexOf('includes(\'svg\')') < uploadHandler.indexOf('resolve(`data:'),
    'the svg check must run before the payload is resolved into the document',
  );
  assert.match(picker, /if \(file\.type\.toLowerCase\(\)\.includes\('svg'\)\) \{[\s\S]*?toast\.error\(svgRejected\);/);
  assert.ok(
    picker.indexOf("includes('svg')") < picker.indexOf('readAsDataURL'),
    'the svg check must run before the file is read',
  );

  // The rejection is visible and localized, like the size rejection beside it.
  const message = /const svgRejected = isVi\n\s*\? '([^']+)'\n\s*: '([^']+)';/.exec(source);
  assert.ok(message, 'the rejection must be a vi/en ternary');
  assert.match(message[1], /SVG/);
  assert.match(message[2], /SVG/);
  assert.notEqual(message[1], message[2], 'the two locales must carry different text');
  assert.ok(
    source.indexOf('toast.error(svgRejected)') !== -1,
    'the picker path must surface the rejection to the author',
  );
});

test('RT-P3-d the image picker only lists the formats the reader allowlist keeps', () => {
  const source = read('src/components/ui/tinymce-editor.tsx');
  const accept = /input\.setAttribute\('accept', '([^']+)'\)/.exec(source);
  assert.ok(accept, 'the picker must declare an explicit accept list');
  assert.equal(source.includes("'image/*'"), false, 'image/* offered SVG files the sanitizer rejects');

  const allowed = FIXTURE.imageSchemes
    .map((scheme) => /^data:image\/([a-z+]+);base64$/.exec(scheme)?.[1])
    .filter(Boolean)
    .sort();
  const offered = accept[1].split(',').map((type) => type.replace('image/', '')).sort();
  assert.deepEqual(offered, allowed, 'the picker and the read-time image allowlist must agree');
});

// --- (e) reader style parity ------------------------------------------------

/** Declaration bodies of the rules whose selector list contains `selector`. */
function cssRuleBodies(css, selector) {
  const bodies = [];
  const pattern = /([^{}]+)\{([^{}]*)\}/g;
  let match;
  while ((match = pattern.exec(css)) !== null) {
    // Strip comments first: the explanatory comment in front of a rule is part
    // of this capture and contains commas.
    const selectors = match[1]
      .replace(/\/\*[\s\S]*?\*\//g, '')
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean);
    if (selectors.includes(selector)) bodies.push(match[2]);
  }
  return bodies;
}

test('RT-P3-e readers style the blocks the editor styles, with tokens only', () => {
  const css = read('src/app/globals.css');
  const parity = css.slice(css.indexOf('/* RT-P3-e'), css.indexOf('@media (prefers-reduced-motion'));
  assert.ok(parity.length > 200, 'the reader parity block must exist');

  for (const selector of [
    '.rich-html-content pre',
    '.rich-html-content code',
    '.rich-html-content pre code',
    '.rich-html-content details',
    '.rich-html-content summary',
    '.rich-html-content mark',
    '.rich-html-content figure',
    '.rich-html-content figcaption',
  ]) {
    assert.equal(cssRuleBodies(parity, selector).length, 1, `missing reader rule for ${selector}`);
  }

  // No raw hex or rgba in the block: every colour is a semantic token, so the
  // same markup reads correctly on the light, sepia and dark reader surfaces.
  assert.equal(/#[0-9a-fA-F]{3,8}\b/.test(parity), false, 'reader parity rules must not use raw hex');
  assert.equal(parity.includes('rgba('), false, 'reader parity rules must not use raw rgba');
  assert.ok(parity.includes('hsl(var(--muted))'), 'code blocks use the muted surface token');
  assert.ok(parity.includes('hsl(var(--border))'), 'code and accordions keep a token hairline');
  assert.ok(parity.includes('hsl(var(--primary))'), 'the accordion summary reads as an affordance');
});

// --- (f) print --------------------------------------------------------------

test('RT-P3-f printing stops orphaning the article and forces the paper palette', () => {
  const css = read('src/app/globals.css');
  const printBlock = css.slice(css.indexOf('@media print'), css.indexOf('[data-reader-theme] {'));
  assert.ok(printBlock.length > 100, 'the print block must exist');

  const avoidRule = /([^{}]+)\{[^{}]*break-inside:\s*avoid;[^{}]*\}/.exec(printBlock);
  assert.ok(avoidRule, 'a break-inside guard must remain for blocks that fit on a page');
  const selectors = avoidRule[1]
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .split(',')
    .map((selector) => selector.trim())
    .filter(Boolean);
  assert.equal(
    selectors.some((s) => /\barticle\b/.test(s)),
    false,
    `article must not be break-inside: avoid — it pushed a long article to sheet 2: ${selectors.join(' | ')}`,
  );
  for (const kept of ['.card', 'figure', 'h1', 'h2']) {
    assert.ok(selectors.includes(kept), `the guard must stay for ${kept}`);
  }

  // Dark and sepia readers print as paper: the reader token overrides are the
  // light palette, byte for byte, from `.reader-theme-light`.
  const readerTokens = Object.fromEntries(
    [...css.slice(css.indexOf('[data-reader-theme] {')).matchAll(/(--[\w-]+):\s*([^;!]+?)\s*!important/g)].map(
      (match) => [match[1], match[2]],
    ),
  );
  const lightStart = css.indexOf('.reader-theme-light {');
  const lightBlock = css.slice(lightStart, css.indexOf('}', lightStart));
  const lightTokens = Object.fromEntries(
    [...lightBlock.matchAll(/(--[\w-]+):\s*([^;!]+);/g)].map((match) => [match[1], match[2].trim()]),
  );
  assert.ok(Object.keys(readerTokens).length >= 20, `expected the print reader block to redefine the palette, saw ${Object.keys(readerTokens).length}`);
  const drifted = Object.entries(readerTokens).filter(([token, value]) => lightTokens[token] !== value);
  assert.deepEqual(drifted, [], 'print reader tokens must equal the light reading theme');
  assert.equal(readerTokens['--reader-bg'], '#ffffff');
  assert.equal(lightTokens['--reader-bg'], '#ffffff');

  // A printer drops backgrounds, so the only thing left of a site-dark callout
  // would be its pale text colour. Print pins the light inks instead.
  for (const callout of ['info', 'warning', 'success']) {
    const selector = `[data-reader-theme] .academic-callout-${callout}`;
    const rule = cssRuleBodies(css.slice(css.indexOf('@media print')), selector).join('');
    assert.ok(rule, `the print block must pin the ${callout} callout ink`);
    assert.match(rule, /color:\s*#[0-9a-f]{6}\s*!important/, `print must pin the ${callout} callout ink`);
  }
});

test('RT-P3-a an authored anchor later in the document wins over an earlier generated slug', () => {
  // Adversarial case: the first heading would claim `quy-che` as its generated
  // slug while a later heading authors that exact id. Reserving authored ids in
  // a single forward pass let both end up with the same anchor, so the contents
  // scrolled to the wrong section.
  const { extractTocHeadings } = loadToc(loadRenderer());
  const body =
    '<h2>Quy chế</h2><p>Nội dung một.</p><h2 id="quy-che">Khác</h2><p>Nội dung hai.</p>';

  const headings = extractTocHeadings(body);
  assert.deepEqual(
    headings.map((heading) => heading.id),
    ['quy-che-2', 'quy-che'],
    'the generated slug must step aside for the authored id it would collide with',
  );
  assert.equal(new Set(headings.map((h) => h.id)).size, headings.length);

  const markup = renderedBodyHtml(body);
  for (const heading of headings) {
    const hits = markup.match(new RegExp(`<h[1-6][^>]*\\bid="${heading.id}"`, 'g')) ?? [];
    assert.equal(hits.length, 1, `anchor "${heading.id}" must appear exactly once in: ${markup}`);
  }
});
