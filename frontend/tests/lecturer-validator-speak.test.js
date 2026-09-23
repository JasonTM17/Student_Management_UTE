const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function readSource(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

/**
 * Native `required` attributes on app inputs make the browser bubble fire
 * before the app's inline Vietnamese validator can explain the problem
 * (round-10 polish: "let the app's own validator speak"). Commit 13ae3683
 * removed them from the lecturer notice modal; this test locks the contract
 * on every lecturer-facing input surface so they cannot creep back.
 *
 * A "native required attribute" is the JSX boolean/valued attribute:
 *  - a bare `required` token alone on a line (multi-line JSX props), or
 *  - `required` appearing inside an opening tag right before `=`, `/>` or `>`.
 * Prose such as "required software" inside copy never matches, because the
 * token there is followed by more prose words, not a tag/attribute boundary,
 * and `aria-required` / identifier names (`requiredProp`) are excluded by the
 * token boundary.
 */
function findNativeRequiredAttributeLines(source) {
  const hits = [];
  for (const [index, line] of source.split('\n').entries()) {
    if (/^\s+required\s*$/.test(line)) {
      hits.push({ line: index + 1, text: line.trim(), kind: 'standalone' });
      continue;
    }
    const inline = line.replace(/aria-required/g, '');
    if (/(<|{\s)required(?=[\s=/>}])|(^[^{]*<[^>]*\s)required(?=\s*[/=>])/.test(inline)) {
      hits.push({ line: index + 1, text: line.trim(), kind: 'inline' });
    }
  }
  return hits;
}

test('the lecturer notice modal lets the inline validator speak (no native required)', () => {
  const source = readSource('src/components/announcements/LecturerAnnouncementCreateModal.tsx');
  // Failing-before: at 13ae3683^ lines 435 (title) and 574 (body) carried a
  // bare `required`, so the browser bubble fired before the app's message.
  assert.deepEqual(
    findNativeRequiredAttributeLines(source),
    [],
    'LecturerAnnouncementCreateModal must not use the native required attribute',
  );
});

test('the lecturer grade matrix score inputs carry no native required', () => {
  const source = readSource('src/app/dashboard/lecturer/grades/[id]/page.tsx');
  assert.deepEqual(
    findNativeRequiredAttributeLines(source),
    [],
    'grade matrix score inputs must not use the native required attribute',
  );
  // The inputs exist and are wired to the app-side validator via data-cell
  // handles and the error channel — without these the contract is vacuous.
  const dataCellHits = source.match(/data-cell=\{`grade-\$\{enrollment\.id\}:(processScore|finalExamScore)`\}/g);
  assert.ok(Array.isArray(dataCellHits) && dataCellHits.length >= 4, 'score cells use data-cell handles');
  assert.match(source, /handleScoreChange\(enrollment\.id, 'processScore', event\.target\.value/);
  assert.match(source, /handleScoreChange\(enrollment\.id, 'finalExamScore', event\.target\.value/);
});

test('the grade matrix inline validator rejects bad scores and keeps the last valid value', () => {
  const source = readSource('src/app/dashboard/lecturer/grades/[id]/page.tsx');
  // badInput is the reliable signal that a type="number" input rejected text.
  assert.match(source, /event\.target\.validity\.badInput/);
  // Out-of-range / non-numeric drafts raise the inline message…
  assert.match(source, /if \(invalid\) next\.set\(errorKey, copy\.invalidScore\);/);
  // …and the previous value stays in state (early return before setGrades).
  assert.match(source, /if \(invalid\) \{\s*\/\/ Keep the last valid value in state/);
});

test('the shared Input component renders the inline error message', () => {
  const inputSource = readSource('src/components/ui/input.tsx');
  assert.match(inputSource, /error/, 'Input must accept an error prop');
});
