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

const FIXTURE = JSON.parse(read('tests/fixtures/announcement-content-policy.json'));

test('RT-P2-5 TypeScript sanitizer policy matches the shared allowlist fixture', () => {
  const sanitizer = loadTs('src/lib/html-sanitizer.ts');

  assert.deepEqual(
    [...sanitizer.ALLOWED_TAGS].sort(),
    [...FIXTURE.allowedTags].sort(),
    'ALLOWED_TAGS drifted from tests/fixtures/announcement-content-policy.json',
  );
  assert.deepEqual(
    [...sanitizer.GLOBAL_ATTRS].sort(),
    [...FIXTURE.globalAttributes].sort(),
  );
  const tagAttrs = Object.fromEntries(
    Object.entries(sanitizer.TAG_ATTRS).map(([tag, attrs]) => [tag, [...attrs].sort()]),
  );
  for (const [tag, attrs] of Object.entries(FIXTURE.tagAttributes)) {
    assert.deepEqual([...(tagAttrs[tag] ?? [])].sort(), [...attrs].sort(), `<${tag}> attrs drifted`);
  }
  assert.deepEqual(Object.keys(tagAttrs).sort(), Object.keys(FIXTURE.tagAttributes).sort());

  // The unsafe-style screen must be the same regex the fixture enumerates
  // (normalize the regex-literal escaping of "/" before comparing).
  assert.equal(
    sanitizer.UNSAFE_STYLE.source.replace(/\\\//g, '/'),
    FIXTURE.unsafeStylePattern,
  );
  assert.equal(String(sanitizer.ID_VALUE_RE), `/${FIXTURE.idValuePattern}/`);
});

test('RT-P1-2 client image cap is derived from the server cap and stays satisfiable', () => {
  const limits = loadTs('src/lib/announcement-limits.ts');
  const serverCap = FIXTURE.editorLimits.maxContentChars;

  assert.equal(limits.MAX_ANNOUNCEMENT_CONTENT_CHARS, serverCap);
  // The fixture pins the derived cap the editor enforces.
  assert.equal(limits.MAX_INLINE_IMAGE_BYTES, FIXTURE.editorLimits.maxInlineImageBytes);

  // Worst case: one image at the client cap, base64-expanded, plus the full
  // text reserve must stay strictly under the server cap — this is the exact
  // arithmetic that failed before, when a single 1 MB image encoded to ~1.37M
  // chars against a 200k-char server budget.
  const worstCase = limits.worstCaseContentChars();
  assert.ok(
    worstCase < serverCap,
    `worst case ${worstCase} must stay under server cap ${serverCap}`,
  );

  // Composition warning fires before the author reaches the server cap.
  assert.equal(
    limits.assessAnnouncementContentLength('x'.repeat(limits.CONTENT_LENGTH_WARN_CHARS - 1)),
    'ok',
  );
  assert.equal(
    limits.assessAnnouncementContentLength('x'.repeat(limits.CONTENT_LENGTH_WARN_CHARS)),
    'warning',
  );
  assert.equal(limits.assessAnnouncementContentLength('x'.repeat(serverCap + 1)), 'exceeded');
});

test('RT-P2-2 editor ships only plugins whose output survives the sanitizer', () => {
  const source = read('src/components/ui/tinymce-editor.tsx');

  // media was removed: its <iframe>/<video> output cannot survive either
  // sanitizer and there is no real upload endpoint (RT-P2-6).
  for (const plugin of FIXTURE.pluginsRemoved) {
    assert.doesNotMatch(
      source,
      new RegExp(`'${plugin}'`),
      `editor still enables the '${plugin}' plugin whose output the sanitizer drops`,
    );
  }
  // accordion + anchor stay enabled and their output is allowlisted.
  for (const plugin of FIXTURE.pluginsKept) {
    assert.match(source, new RegExp(`'${plugin}'`), `plugin '${plugin}' must stay enabled`);
  }
  // Round-trip: kept plugins' output must survive the read-time sanitizer.
  const { sanitizeAnnouncementHtml } = loadTs('src/lib/html-sanitizer.ts');
  const accordion = sanitizeAnnouncementHtml('<details><summary>Tiêu đề</summary><p>nội dung</p></details>');
  assert.match(accordion, /<details><summary>Tiêu đề<\/summary><p>nội dung<\/p><\/details>/);
  const anchor = sanitizeAnnouncementHtml('<h2 id="muc-1">Mục 1</h2>');
  assert.match(anchor, /<h2 id="muc-1">/);
  // A clobbering or malformed id is dropped even though `id` is allowlisted.
  assert.doesNotMatch(sanitizeAnnouncementHtml('<p id="9bad">x</p>'), /id=/);
  assert.doesNotMatch(sanitizeAnnouncementHtml('<p id="has space">x</p>'), /id=/);
});

test('RT-P1-2 editor enforces the derived image cap and warns before the server limit', () => {
  const source = read('src/components/ui/tinymce-editor.tsx');
  // The cap comes from the shared module, not a second magic number.
  assert.match(source, /MAX_INLINE_IMAGE_BYTES/);
  assert.doesNotMatch(source, /1_000_000/);
  // The author sees the budget while composing, not only at publish time.
  assert.match(source, /assessAnnouncementContentLength/);
  assert.match(source, /role="status"/);
});

test('RT-P1-2 several images at the cap cannot compose an unpublishable document', () => {
  const limits = loadTs('src/lib/announcement-limits.ts');
  const serverCap = FIXTURE.editorLimits.maxContentChars;

  // The per-image cap alone does not bound the document. Two images at the cap
  // are ~360k characters against a 200k server cap, which is the same publish
  // dead-end reached with two files instead of one — so the count must be
  // derived from the same budget rather than left unbounded.
  assert.ok(limits.MAX_INLINE_IMAGES >= 1, 'at least one image must be allowed');

  const worstCase = limits.worstCaseContentChars();
  assert.ok(
    worstCase < serverCap,
    `every permitted image at the cap plus the text reserve is ${worstCase} chars, which must stay under ${serverCap}`,
  );

  // One image beyond the budget must not fit — otherwise the count is too generous
  // and the invariant above would be a coincidence rather than a guarantee.
  const oneImageWorstCase =
    4 * Math.ceil(limits.MAX_INLINE_IMAGE_BYTES / 3) + 200;
  assert.ok(
    limits.MAX_INLINE_IMAGES * oneImageWorstCase + limits.TEXT_RESERVE_CHARS + oneImageWorstCase >
      serverCap,
    `permitting ${limits.MAX_INLINE_IMAGES + 1} images would exceed the server cap, so the count is at its maximum`,
  );
});

test('RT-P1-2 publish is blocked client-side on every authoring surface', () => {
  const limits = loadTs('src/lib/announcement-limits.ts');

  // A warning is not a gate: content over the cap that still reaches the server
  // comes back as an opaque 400 with nothing actionable, which is the dead-end
  // this whole contract exists to close.
  const overCap = 'x'.repeat(limits.MAX_ANNOUNCEMENT_CONTENT_CHARS + 1);
  const violation = limits.findAnnouncementLengthViolation(overCap);
  assert.ok(violation, 'over-cap content must produce a blocking decision');
  assert.equal(violation.excessChars, 1);
  assert.equal(violation.state, 'exceeded');
  assert.equal(
    limits.findAnnouncementLengthViolation('x'.repeat(limits.MAX_ANNOUNCEMENT_CONTENT_CHARS)),
    null,
    'content exactly at the cap is publishable — the gate must not over-block',
  );

  // The message names both numbers so the author knows what to change.
  for (const locale of ['vi', 'en']) {
    const message = limits.announcementLengthViolationMessage(violation, locale);
    assert.match(message, /1/, `${locale} message must state how much to remove`);
  }

  // Both surfaces must consult the gate. The TinyMCE editor page had no length
  // check at all, and the admin page authors through a second editor with no
  // per-image cap, so a gate on only one of them leaves the other open.
  const editorPage = read('src/app/dashboard/editor/page.tsx');
  const adminPage = read('src/app/admin/announcements/page.tsx');
  for (const [name, source] of [
    ['dashboard/editor', editorPage],
    ['admin/announcements', adminPage],
  ]) {
    assert.match(
      source,
      /findAnnouncementLengthViolation/,
      `${name} must gate the publish path on the shared length decision`,
    );
  }
  // Publish and update are separate paths on the editor page; both must be gated.
  assert.ok(
    (editorPage.match(/findAnnouncementLengthViolation/g) ?? []).length >= 2,
    'both the publish and the update path must be gated',
  );
});

test('RT-P3-1 editor loads the self-hosted Vietnamese language pack', () => {
  const source = read('src/components/ui/tinymce-editor.tsx');
  assert.match(source, /language: isVi \? 'vi' : undefined/);
  assert.match(source, /language_url: isVi \? '\/tinymce\/langs\/vi\.js' : undefined/);

  // The copy script pulls the pack from the pinned tinymce-i18n package.
  const copyScript = read('scripts/copy-tinymce.mjs');
  assert.match(copyScript, /tinymce-i18n/);
  assert.match(copyScript, /langs8/);
  const pkg = JSON.parse(read('package.json'));
  assert.ok(pkg.dependencies['tinymce-i18n'], 'tinymce-i18n must be a pinned dependency');

  // The artifact exists after install + copy, mirroring the tinymce.min.js check.
  assert.ok(
    fs.existsSync(path.join(root, 'public/tinymce/langs/vi.js')),
    'public/tinymce/langs/vi.js must exist (run scripts/copy-tinymce.mjs)',
  );
  assert.match(read('public/tinymce/langs/vi.js'), /tinymce\.addI18n\("vi"/);
});

test('RT-P3-2 editor import failure shows a real error with retry, not an eternal spinner', () => {
  const source = read('src/components/ui/tinymce-editor.tsx');
  assert.match(source, /EditorLoadError/);
  assert.match(source, /role="alert"/);
  assert.match(source, /useEditorModule/);
  // next/dynamic without an error state must not come back.
  assert.doesNotMatch(source, /from 'next\/dynamic'/);
});

test('RT-P3-3 unsaved-changes guard is reusable and wired into the announcement editor', () => {
  const hook = read('src/lib/use-unsaved-changes-guard.ts');
  assert.match(hook, /export function useUnsavedChangesGuard/);
  assert.match(hook, /addEventListener\('beforeunload', handleBeforeUnload\)/);
  assert.match(hook, /removeEventListener\('beforeunload', handleBeforeUnload\)/);
  assert.match(hook, /event\.returnValue/);

  const dialog = read('src/components/ui/unsaved-changes-confirm.tsx');
  assert.match(dialog, /ConfirmModal/);

  const editorPage = read('src/app/dashboard/editor/page.tsx');
  assert.match(editorPage, /useUnsavedChangesGuard\(/);
  assert.match(editorPage, /UnsavedChangesConfirmDialog/);
  assert.match(editorPage, /unsaved\.requestLeave\(/);
});

test('RT-P3-4 showWordCount toggles the wordcount toolbar control', () => {
  const source = read('src/components/ui/tinymce-editor.tsx');
  // Conditional toolbar token instead of the previous dead prop.
  assert.match(source, /showWordCount \? 'wordcount' : null/);
});

test('RT-P2-4 staging ships CSP in Report-Only mode, not enforced', () => {
  const config = read('next.config.mjs');
  assert.match(config, /Content-Security-Policy-Report-Only/);
  assert.match(config, /headers\(\)/);
  // Enforcement is deliberately not switched on (see the config's comment
  // block): the inline theme bootstrap, Next flight-data scripts and the
  // TinyMCE iframe would break under `script-src 'self'`.
  assert.match(config, /script-src 'self'/);
  assert.match(config, /frame-src 'self' blob:/);
  assert.doesNotMatch(config, /key: 'Content-Security-Policy'/);
});
