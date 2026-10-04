const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const SOURCE = fs.readFileSync(
  path.join(root, 'src/components/ui/tinymce-editor.tsx'), 'utf8');

// Extract and evaluate module-level constants the same way
// editor-integrity.test.js does: transpile just the declaration and run it.
function loadConst(name) {
  const ts = require('typescript');
  const ast = ts.createSourceFile('tinymce-editor.tsx', SOURCE,
    ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const node = ast.statements.find((stmt) =>
    ts.isVariableStatement(stmt) && stmt.declarationList.declarations.some(
      (d) => ts.isIdentifier(d.name) && d.name.text === name));
  assert.ok(node, `top-level const ${name} exists in tinymce-editor.tsx`);
  const compiled = ts.transpileModule(node.getText(ast), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', `${compiled}\nmodule.exports.__v = ${name};`)
    (moduleRecord, moduleRecord.exports);
  return moduleRecord.exports.__v;
}

test('inline image src policy keeps publishable schemes only', () => {
  const SAFE = loadConst('SAFE_INLINE_IMAGE_SRC');
  const STRIP = loadConst('INLINE_IMG_SRC_STRIPPED');
  const PUBLISHABLE = loadConst('PUBLISHABLE_INLINE_IMAGE_SRC');
  // The editor tests the normalized value — the same control/space set the
  // server strips before matching (Java \s is ASCII-only; JS \s is not).
  const norm = (src) => src.replace(STRIP, '').toLowerCase();

  const keeps = [
    'https://cdn.example.com/x.png',
    'http://intranet.example.com/x.png',
    '/uploads/x.png',
    './relative/x.png',
    '../parent/x.png',
    'data:image/png;base64,iVBORw0KGgo=',
    'data:image/jpeg;base64,/9j/4AAQ=',
    'data:image/gif;base64,R0lGOD=',
    'data:image/webp;base64,UklGR=',
    'data:image/bmp;base64,Qk0=',
    // blob: survives only as the transient src automatic_uploads replaces —
    // inside the editor. The serializer (publishable) variant drops it.
    'blob:http://localhost:3100/uuid',
    // Whitespace the server strips before checking: the payload below keeps
    // its image client-side AND server-side — parity both directions.
    'data:image/png;base64,QUJD\u2028DEU=',
    'data:image/png;base64,QUJD\u00a0DEU=',
    ' https://cdn.example.com/leading-space.png',
  ];
  for (const src of keeps) assert.ok(SAFE.test(norm(src)), `expected keep: ${JSON.stringify(src)}`);

  const drops = [
    'data:image/svg+xml;base64,PHN2Zw==',      // svg — XSS vector
    'data:image/avif;base64,AAAA',             // stripped by publish sanitizer
    'data:image/heic;base64,AAAA',
    'data:image/tiff;base64,AAAA',
    'data:image/ico;base64,AAAA',
    'data:image/png,notbase64',                // non-base64 data uri
    // Parity with the server: the base64 branch is end-anchored with the
    // publish charset, so a payload the write-time sanitizer would strip
    // cannot survive the editor either (C8).
    'data:image/png;base64,AAA-BBB',
    'data:image/png;base64,AAA<script>',
    'data:image/png;base64,',                  // empty payload
    // U+2007 (figure space) is NOT in the server's strip set and Java's \s
    // does not match it — the server drops this img at publish; the editor
    // must drop it too (Wukong N3 counterexample).
    'data:image/png;base64,QUJD\u2007DEU=',
    'data:image/png;base64,QUJD\u3000DEU=',
    'data:text/html;base64,PGI+',              // non-image data uri
    '//evil.example/x.png',                    // protocol-relative
    'javascript:alert(1)',
    'vbscript:msgbox(1)',
    'file:///etc/passwd',
    '',                                        // src-less img
  ];
  for (const src of drops) assert.ok(!SAFE.test(norm(src)), `expected drop: ${JSON.stringify(src)}`);

  // The outbound serializer is stricter: a blob: src from an in-flight
  // automatic_uploads run would die at publish, so serialized content
  // drops it as well.
  assert.ok(!PUBLISHABLE.test(norm('blob:http://localhost:3100/uuid')));
  assert.ok(PUBLISHABLE.test(norm('data:image/png;base64,iVBORw0KGgo=')));
});

test('the editor rejects non-publishable image MIME types at every insert point', () => {
  // Positive allowlist — not a denylist: AVIF/HEIC/TIFF must fail just like SVG.
  const mimeSet = loadConst('INLINE_IMAGE_MIME_TYPES');
  assert.deepEqual([...mimeSet].sort(),
    ['image/bmp', 'image/gif', 'image/jpeg', 'image/png', 'image/webp'].sort());
  assert.ok(!mimeSet.has('image/svg+xml'));
  assert.ok(!mimeSet.has('image/avif'));

  // TinyMCE's own file-type gate matches the allowlist.
  assert.match(SOURCE, /images_file_types:\s*'jpg,jpeg,png,gif,webp,bmp'/);
  // The picker advertises only publishable types instead of image/*.
  assert.match(SOURCE, /accept',\s*'image\/png,image\/jpeg,image\/gif,image\/webp,image\/bmp'/);
  // The upload handler checks MIME before size so an oversized AVIF reports
  // the right problem.
  assert.match(SOURCE, /INLINE_IMAGE_MIME_TYPES\.has\(blobType\)/);
  assert.match(SOURCE, /INLINE_IMAGE_MIME_TYPES\.has\(file\.type\.toLowerCase\(\)\)/);
});

test('unsafe img src is filtered at insert AND at the parser boundary', () => {
  // Paste path: cleanForeignPasteTree drops foreign <img> that cannot publish.
  assert.match(SOURCE, /tag === 'img'[\s\S]*?SAFE_INLINE_IMAGE_SRC\.test/);
  // Parser path: catches the Image dialog Source field and internal paste,
  // which skip the foreign-paste cleaner.
  assert.match(SOURCE, /parser\.addNodeFilter\('img'/);
  // Serializer path: editing an existing image's Source in the dialog mutates
  // the DOM node directly (no parser re-entry), so the outbound serializer
  // and a NodeChange sweep must strip it too (C8 vector 2).
  assert.match(SOURCE, /serializer\.addNodeFilter\('img'[\s\S]*?PUBLISHABLE_INLINE_IMAGE_SRC/);
  assert.match(SOURCE, /editor\.on\('NodeChange'/);
  // Author-facing feedback exists for both rejection reasons.
  assert.match(SOURCE, /unsupportedImageFormat/);
  assert.match(SOURCE, /svgRejected/);
});
