const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadTs(relativePath) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

// Loader for TS modules with import specifiers (alias paths, JSX components):
// `stubs` maps import specifiers to module records, everything else resolves
// through Node's require (e.g. the real 'react').
function loadTsModule(relativePath, stubs) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.React,
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

function loadRichContentRenderer() {
  const dummyComponent = function DummyComponent() {
    return null;
  };
  return loadTsModule('src/components/ui/rich-content-renderer.tsx', {
    '@/lib/html-sanitizer': loadTs('src/lib/html-sanitizer.ts'),
    '@/lib/utils': { cn: (...args) => args.filter(Boolean).join(' ') },
    'lucide-react': new Proxy({}, { get: () => dummyComponent }),
  });
}

test('announcement HTML sanitizer neutralizes stored-XSS payloads', () => {
  const { sanitizeAnnouncementHtml } = loadTs('src/lib/html-sanitizer.ts');

  // Each entry previously survived the attribute blacklist: its `on\w+` pattern
  // required a leading whitespace, so a slash worked just as well.
  const attacks = [
    ['<img src=x/onerror=alert(1)>', /onerror/i],
    ['<svg/onload=alert(1)>', /onload|<svg/i],
    ['<IMG SRC=JaVaScRiPt:alert(1)>', /javascript/i],
    ['<a href="javas&#99;ript:alert(1)">x</a>', /javascript/i],
    ['<a href="java\tscript:alert(1)">x</a>', /javascript/i],
    ['<a href="vbscript:msgbox(1)">x</a>', /vbscript/i],
    ['<div onclick="alert(1)">x</div>', /onclick/i],
    ['<p style="background:url(javascript:alert(1))">x</p>', /javascript/i],
    ['<script>alert(1)</script>', /alert|<script/i],
    ['<style>body{background:url(javascript:alert(1))}</style>', /style|javascript/i],
    ['<template><script>alert(1)</script></template>', /script|alert/i],
    ['<iframe src="https://evil.example"></iframe>', /iframe/i],
    ['<object data="x"></object>', /object/i],
    ['<embed src="x">', /embed/i],
    ['<form action="/x"><input name="y" value="z"></form>', /form|input/i],
    ['<math><mtext>m</mtext></math>', /math/i],
  ];
  for (const [payload, forbidden] of attacks) {
    const clean = sanitizeAnnouncementHtml(payload);
    assert.doesNotMatch(clean, forbidden, `payload survived sanitizing: ${payload} -> ${clean}`);
  }

  // TinyMCE output and legitimate formatting must survive intact.
  const kept = [
    '<p>Học phần <strong>tiên quyết</strong> và <em>song hành</em></p>',
    '<ul><li>Điểm F phải học lại</li></ul>',
    '<h2>Quy định học phí</h2>',
    '<table><thead><tr><th colspan="2">Mức</th></tr></thead><tbody><tr><td>1</td><td>2</td></tr></tbody></table>',
    '<a href="https://campusute.io.vn/vi/dashboard">Cổng học vụ</a>',
    '<a href="/vi/dashboard/thesis">Đồ án</a>',
    '<a href="mailto:phongdaotao@campusute.edu.vn">Phòng Đào tạo</a>',
    '<img src="data:image/png;base64,iVBORw0KGgoAAAANSUhEUg" alt="Sơ đồ quy trình">',
    '<img src="https://campusute.io.vn/logo.png" alt="Logo" width="120">',
  ];
  // A void tag may be re-serialized self-closed; that is equivalent markup and
  // is the only permitted difference for legitimate content.
  const withoutVoidSlashes = (html) => html.replace(/\s*\/>/g, '>');
  for (const legit of kept) {
    assert.equal(
      withoutVoidSlashes(sanitizeAnnouncementHtml(legit)),
      withoutVoidSlashes(legit),
      `legitimate markup was altered: ${legit} -> ${sanitizeAnnouncementHtml(legit)}`,
    );
  }

  // A literal "<" in prose is escaped, and valid entities are not double-escaped.
  assert.equal(
    sanitizeAnnouncementHtml('<p>điểm < 4.0 là không đạt</p>'),
    '<p>điểm &lt; 4.0 là không đạt</p>',
  );
  assert.equal(
    sanitizeAnnouncementHtml('<p>Đào tạo &amp; Khoa học</p>'),
    '<p>Đào tạo &amp; Khoa học</p>',
  );

  // T-P1-4: vetted inline styles survive so the reader matches the editor
  // preview (template letterheads, callouts, seals), while scheme-bearing
  // style values are stripped with the attribute.
  const styled = sanitizeAnnouncementHtml(
    '<div style="text-align: center; border-bottom: 2px solid #0284c7; color: #0f172a">Trường Đại học</div>',
  );
  assert.match(styled, /style="[^"]*text-align:\s*center/);
  assert.match(styled, /border-bottom:\s*2px solid #0284c7/);
  const unsafeStyled = sanitizeAnnouncementHtml(
    '<div style="background:url(javascript:alert(1));color:red">x</div>',
  );
  assert.doesNotMatch(unsafeStyled, /javascript/i);
  assert.equal(sanitizeAnnouncementHtml(''), '');
});

test('rich-content-renderer delegates HTML sanitizing to the allowlist module', () => {
  const source = read('src/components/ui/rich-content-renderer.tsx');
  assert.match(source, /from '@\/lib\/html-sanitizer'/);
  assert.match(source, /return sanitizeAnnouncementHtml\(html\)/);
  // The bypassable attribute blacklist must not come back.
  assert.doesNotMatch(source, /\\son\\w\+/);
});

test('rich-text-editor component provides comprehensive toolbar and view modes', () => {
  const source = read('src/components/ui/rich-text-editor.tsx');

  // Exports
  assert.match(source, /export function RichTextEditor/);
  assert.match(source, /export interface RichTextEditorProps/);

  // Formatting actions
  assert.match(source, /insertText\('\*\*', '\*\*'/); // Bold
  assert.match(source, /insertText\('\*', '\*'/); // Italic
  assert.match(source, /insertText\('~~', '~~'/); // Strikethrough
  assert.match(source, /insertText\('`', '`'/); // Inline Code

  // Headings & Lists
  assert.match(source, /insertLinePrefix\('# '/); // H1
  assert.match(source, /insertLinePrefix\('## '/); // H2
  assert.match(source, /insertLinePrefix\('### '/); // H3
  assert.match(source, /insertLinePrefix\('- '/); // Bullet list
  assert.match(source, /insertLinePrefix\('1\. '/); // Numbered list
  assert.match(source, /insertLinePrefix\('- \[ \] '/); // Checklist
  assert.match(source, /insertLinePrefix\('> '/); // Quote

  // Alerts & Tables
  assert.match(source, /insertSnippet\(`> \[!NOTE\]/);
  assert.match(source, /insertSnippet\(`> \[!WARNING\]/);
  assert.match(source, /\| :--- \| :--- \| :--- \|/); // Table template

  // View modes
  assert.match(source, /type EditorViewMode = 'write' \| 'split' \| 'preview'/);
  assert.match(source, /viewMode === 'split'/);
  assert.match(source, /viewMode === 'preview'/);
  assert.match(source, /viewMode === 'write'/);

  // Shortcuts & Fullscreen
  assert.match(source, /event\.key\.toLowerCase\(\) === 'b'/);
  assert.match(source, /event\.key\.toLowerCase\(\) === 'i'/);
  assert.match(source, /event\.key\.toLowerCase\(\) === 'k'/);
  assert.match(source, /event\.key === 'Escape'/);
  assert.match(source, /isFullscreen/);

  // Templates & Stats
  assert.match(source, /ACADEMIC_TEMPLATES/);
  assert.match(source, /stats\.chars/);
  assert.match(source, /stats\.words/);
  assert.match(source, /stats\.minutes/);
});

test('rich-content-renderer securely parses and renders markdown structures and callouts', () => {
  const source = read('src/components/ui/rich-content-renderer.tsx');

  // Exports
  assert.match(source, /export function RichContentRenderer/);
  assert.match(source, /export interface RichContentRendererProps/);

  // Security: RT-P1-1 — the markdown branch must not carry its own scheme
  // check; it delegates to the sanitizer's policy (asserted behaviorally
  // below). The old weak `isSafeUrl` must stay gone.
  assert.doesNotMatch(source, /function isSafeUrl/);
  assert.doesNotMatch(source, /trim\(\)\.toLowerCase\(\)\.startsWith\('javascript:'/);
  assert.match(source, /isSafeAnnouncementUrl/);
  assert.match(source, /isSafeAnnouncementImageUrl/);

  // Callouts support
  assert.match(source, /alertMatch/);
  assert.match(source, /AlertCallout/);
  assert.match(source, /LƯU Ý \(NOTE\)/);
  assert.match(source, /CẢNH BÁO \(WARNING\)/);

  // Code Block and Table
  assert.match(source, /function CodeBlock/);
  assert.match(source, /navigator\.clipboard\.writeText/); // Copy action
  assert.match(source, /function MarkdownTable/);
  assert.match(source, /parseRow/);

  // Task list items
  assert.match(source, /\[([ xX])\]/);
  assert.match(source, /type="checkbox"/);
});

test('RT-P1-1 markdown link and image schemes share the sanitizer policy', () => {
  // Regression for the stored-XSS markdown bypass: the pre-fix renderer kept
  // its own `trim().toLowerCase().startsWith('javascript:')` guard, which
  // interior tab/newline/carriage-return defeats because browsers strip
  // \t\n\r from URLs before resolving them. The shared check normalizes
  // control characters and entities away and rejects unknown schemes.
  const { isSafeAnnouncementUrl, isSafeAnnouncementImageUrl } = loadTs('src/lib/html-sanitizer.ts');

  const bypasses = [
    'java\tscript:alert(1)',
    'java\nscript:alert(1)',
    'java\rscript:alert(1)',
    'JavaScript:alert(1)',
    'JaVaScRiPt:alert(1)',
    'jav&#x61;script:alert(1)',
    ' javascript:alert(1)',
    'data:text/html,<script>alert(1)</script>',
    'vbscript:msgbox(1)',
  ];
  for (const url of bypasses) {
    assert.equal(isSafeAnnouncementUrl(url), false, `scheme must be rejected: ${JSON.stringify(url)}`);
  }
  // Only base64 image payloads are allowed, matching the HTML branch's src rule.
  assert.equal(isSafeAnnouncementImageUrl('data:text/html,<script>'), false);
  assert.equal(isSafeAnnouncementImageUrl('data:image/png;base64,iVBORw0KGgoAAAANSUhEUg'), true);

  // Control: legitimate links and images still pass and the renderer still
  // renders an https link as a link.
  assert.equal(isSafeAnnouncementUrl('https://campusute.io.vn/vi/dashboard'), true);
  assert.equal(isSafeAnnouncementUrl('mailto:phongdaotao@campusute.edu.vn'), true);
  assert.equal(isSafeAnnouncementUrl('/vi/dashboard/thesis'), true);
});

test('RT-P2-3 list-only and blockquote-only HTML bodies render as HTML, not literal tags', () => {
  const renderer = loadRichContentRenderer();
  const { sanitizeAnnouncementHtml } = loadTs('src/lib/html-sanitizer.ts');

  // TinyMCE emits exactly these bare fragments from ordinary authoring; the
  // old leading-tag allowlist missed them and printed the raw tags as text.
  for (const fragment of [
    '<ul><li>Điểm F phải học lại</li></ul>',
    '<ol><li>Đăng ký tín chỉ</li></ol>',
    '<blockquote>Trích dẫn quy định</blockquote>',
    '<figure><img src="/logo.png" alt="logo"><figcaption>Chú thích</figcaption></figure>',
    '<pre><code>let x = 1;</code></pre>',
    '<details><summary>Mục lục</summary><p>Nội dung</p></details>',
  ]) {
    assert.equal(renderer.isHtmlDocument(fragment), true, `must be detected as HTML: ${fragment}`);
  }

  // Plain markdown and prose with a bare "<" stay on the markdown branch.
  assert.equal(renderer.isHtmlDocument('Văn bản thuần\n- mục 1\n- mục 2\n# Tiêu đề'), false);
  assert.equal(renderer.isHtmlDocument('điểm < 4.0 là không đạt'), false);
  // HTML inside a fenced code block does not flip the document to HTML mode.
  assert.equal(renderer.isHtmlDocument('Mã mẫu:\n```html\n<p>markup</p>\n```\nHết.'), false);

  // With structural detection routing the fragment to the sanitizer branch,
  // the list survives as real elements and no literal tag text leaks.
  const sanitized = sanitizeAnnouncementHtml('<ul><li>a</li></ul>');
  assert.match(sanitized, /<ul><li>/);
  assert.equal(sanitized.includes('&lt;li&gt;'), false);
  const quoted = sanitizeAnnouncementHtml('<blockquote>x</blockquote>');
  assert.match(quoted, /<blockquote>/);
});

test('admin announcements integration uses RichTextEditor and RichContentRenderer', () => {  const page = read('src/app/admin/announcements/page.tsx');

  assert.match(page, /import \{ RichTextEditor \} from '@\/components\/ui\/rich-text-editor'/);
  assert.match(page, /import \{ RichContentRenderer \} from '@\/components\/ui\/rich-content-renderer'/);
  assert.match(page, /<RichTextEditor/);
  assert.match(page, /<RichContentRenderer/);
  assert.match(page, /fallbackText=\{vi \? 'Nội dung xem trước/);
});

test('admin assistant-knowledge integration uses RichTextEditor with academic placeholders', () => {
  const page = read('src/app/admin/assistant-knowledge/page.tsx');

  assert.match(page, /import \{ RichTextEditor \} from '@\/components\/ui\/rich-text-editor'/);
  assert.match(page, /<RichTextEditor/);
  assert.match(page, /value=\{form\.content\}/);
  assert.match(page, /minHeight="240px"/);
});

test('student and lecturer feeds render rich announcements via RichContentRenderer', () => {
  for (const relativePath of [
    'src/app/dashboard/announcements/page.tsx',
    'src/app/dashboard/lecturer/announcements/page.tsx',
  ]) {
    const page = read(relativePath);
    assert.match(page, /import \{ RichContentRenderer \} from '@\/components\/ui\/rich-content-renderer'/);
    assert.match(page, /<RichContentRenderer content=\{announcement\.content\} \/>/);
  }
});

test('thesis page integrates RichTextEditor for proposal and RichContentRenderer for description', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');

  assert.match(page, /import \{ RichTextEditor \} from '@\/components\/ui\/rich-text-editor'/);
  assert.match(page, /import \{ RichContentRenderer \} from '@\/components\/ui\/rich-content-renderer'/);
  assert.match(page, /<RichTextEditor[\s\S]*?value=\{proposeDescription\}/);
  // Topic selection moved to the catalog, so the thesis page renders rich topic
  // descriptions in the topic modal instead of an inline topic grid.
  assert.match(page, /<RichContentRenderer content=\{viewingTopic\.description\} \/>/);

  // The catalog detail page is where a student reads a topic, so it must render
  // the description as rich content too.
  const topicDetail = read('src/components/dashboard/thesis/ThesisTopicDetailPage.tsx');
  assert.match(topicDetail, /<RichContentRenderer content=\{topic\.description\} \/>/);
});

test('dedicated academic editor workbench provides templates, stats, copy and export actions', () => {
  const editorPage = read('src/app/dashboard/editor/page.tsx');
  const localeEditorPage = read('src/app/[locale]/dashboard/editor/page.tsx');
  const layout = read('src/app/dashboard/layout.tsx');

  assert.match(editorPage, /export default function AcademicEditorPage/);
  assert.match(editorPage, /<RichTextEditor/);
  assert.match(editorPage, /campuscore_editor_document/);
  assert.match(editorPage, /handleCopy/);
  assert.match(editorPage, /handleDownload/);
  assert.match(editorPage, /stats\.words/);
  assert.match(localeEditorPage, /export \{ default \} from '\.\.\/\.\.\/\.\.\/dashboard\/editor\/page'/);
  assert.match(layout, /href: '\/dashboard\/editor'/);
});

test('tinymce-editor component provides self-hosted offline wysiwyg capabilities', () => {
  const source = read('src/components/ui/tinymce-editor.tsx');
  const pkg = JSON.parse(read('package.json'));
  const editorPage = read('src/app/dashboard/editor/page.tsx');

  // Exports and component
  assert.match(source, /export function TinyMceEditor/);
  assert.match(source, /export interface TinyMceEditorProps/);

  // Self-hosted configuration: no Tiny Cloud dependency, no API key warnings
  assert.match(source, /tinymceScriptSrc="\/tinymce\/tinymce\.min\.js"/);
  assert.match(source, /branding:\s*false/);
  assert.match(source, /promotion:\s*false/);
  assert.match(source, /oxide-dark/);
  assert.match(source, /oxide/);

  // Verification that public/tinymce exists
  assert.ok(fs.existsSync(path.join(root, 'public/tinymce/tinymce.min.js')), 'public/tinymce/tinymce.min.js must exist');

  // Package dependencies and copy hook
  assert.ok(pkg.dependencies['@tinymce/tinymce-react'], '@tinymce/tinymce-react must be a dependency');
  assert.ok(pkg.dependencies['tinymce'], 'tinymce must be a dependency');
  assert.match(pkg.scripts.prebuild, /copy-tinymce\.mjs/);

  // Dashboard editor integration
  assert.match(editorPage, /import \{ TinyMceEditor \} from '@\/components\/ui\/tinymce-editor'/);
  assert.match(editorPage, /<TinyMceEditor/);
});

test('assistant stream hook integrates student resolver for personal schedules and materials', () => {
  const streamHook = read('src/components/assistant/useAssistantStream.ts');
  const resolver = read('src/lib/assistant-student-resolver.ts');

  assert.match(streamHook, /isStudentAssistantQuery\(message\)/);
  assert.match(streamHook, /resolveStudentAssistantQuery\(message, locale\)/);
  assert.match(resolver, /export function isStudentAssistantQuery/);
  assert.match(resolver, /export async function resolveStudentAssistantQuery/);
});

test('sortablejs integration empowers admin block building and announcement reordering', () => {
  const pkg = JSON.parse(read('package.json'));
  const sortableComponent = read('src/components/ui/sortable-list.tsx');
  const editorPage = read('src/app/dashboard/editor/page.tsx');
  const appearancePage = read('src/app/admin/appearance/page.tsx');
  const announcementsPage = read('src/app/admin/announcements/page.tsx');

  // Package dependencies
  assert.ok(pkg.dependencies['sortablejs'], 'sortablejs must be installed');

  // SortableList component exports and properties
  assert.match(sortableComponent, /export function SortableList/);
  assert.match(sortableComponent, /export function DragHandle/);
  assert.match(sortableComponent, /handle:\s*`\.\$\{handleClassName\}`/);
  assert.match(sortableComponent, /ghostClass:\s*'sortable-ghost'/);

  // Dashboard Editor integration: Sortable Content Blocks and Announcements Table
  assert.match(editorPage, /import \{ SortableList, DragHandle \} from '@\/components\/ui\/sortable-list'/);
  assert.match(editorPage, /handleCompileBlocksToTinyMce/);
  assert.match(editorPage, /handleSortableNoticeReorder/);
  assert.match(editorPage, /handleSaveNoticeOrder/);

  // Appearance and Announcements page integration
  assert.match(appearancePage, /<SortableList/);
  assert.match(announcementsPage, /<SortableList/);
  assert.match(announcementsPage, /modal === 'reorder'/);

  // The reorder dialog is portal-rendered, so keyboard handling must be a native
  // listener on the container: a React onKeyDown prop never fired there and the
  // arrow keys silently did nothing in the browser.
  assert.match(sortableComponent, /addEventListener\('keydown', onKeyDown\)/);
  assert.match(sortableComponent, /removeEventListener\('keydown', onKeyDown\)/);
  assert.doesNotMatch(sortableComponent, /onKeyDown=\{handleKeyDown\}/);
  assert.match(sortableComponent, /phím mũi tên/);
});


