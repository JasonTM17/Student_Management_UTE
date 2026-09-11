const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

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

  // Security: Protocol sanitization against XSS
  assert.match(source, /isSafeUrl/);
  assert.match(source, /javascript:/);
  assert.match(source, /data:/);
  assert.match(source, /vbscript:/);

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

test('admin announcements integration uses RichTextEditor and RichContentRenderer', () => {
  const page = read('src/app/admin/announcements/page.tsx');

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
  assert.match(page, /<RichContentRenderer content=\{topic\.description\} \/>/);
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
});


