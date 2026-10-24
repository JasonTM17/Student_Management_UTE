const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function readSource(file) {
  return fs.readFileSync(path.join(root, file), 'utf8');
}

// ---------------------------------------------------------------------------
// axe-core findings reproduced on the live stack (serious severity):
//
// 1. color-contrast — `text-muted-foreground/70` paints muted-foreground at
//    70% alpha, which measures ~3.4:1 on the card background; WCAG AA needs
//    4.5:1 for small text. The full-strength token measures comfortably above
//    AA, so the opacity suffix may only appear on decorative icon glyphs, not
//    on text-bearing elements (span/p/div).
//
// 2. nested-interactive — the faculty bar chart svg carried role="img" while
//    its bars are <g role="link"> children; an image must not contain
//    interactive descendants. A chart with interactive children is a named
//    `group`, not an `img`.
//
// 3. aria-conditional-attr — the audit trail expandable row put
//    aria-expanded/aria-controls on a plain <tr> (role=row does not allow
//    conditional interactive attributes). The expand control must be a real
//    button inside the row.
// ---------------------------------------------------------------------------

test('muted text keeps full-strength foreground on text-bearing elements', () => {
  const files = [
    'src/app/dashboard/grades/page.tsx',
    'src/app/dashboard/transcript/page.tsx',
    'src/app/error.tsx',
    'src/components/dashboard/StudentUteProfileGradeView.tsx',
  ];
  for (const file of files) {
    const source = readSource(file);
    const offenders =
      source.match(/<(?:span|p|div|small|td|th)[^>]*text-muted-foreground\/70/g) ?? [];
    assert.equal(
      offenders.length,
      0,
      `${file} dims text with text-muted-foreground/70 (~3.4:1, below AA 4.5:1): ${offenders.join(' | ')}`,
    );
  }
});

test('an svg that hosts interactive children is exposed as a group, not an image', () => {
  const source = readSource('src/components/admin/AdminAnalyticsCharts.tsx');
  const chunks = source.split(/<svg\s/).slice(1).map((chunk) => chunk.split('</svg>')[0]);
  assert.ok(chunks.length > 0, 'expected at least one svg in the analytics charts');
  const nested = chunks.filter(
    (chunk) => /role="img"/.test(chunk.split('>')[0]) && /role="link"|role="button"/.test(chunk),
  );
  assert.equal(
    nested.length,
    0,
    `svg role="img" must not wrap interactive children (${nested.length} found)`,
  );
  // The departments chart keeps its accessible name under the group role.
  assert.match(source, /<svg[^>]*role="group"[^>]*aria-label=\{copy\.chartAria\}/);
});

test('expandable audit rows expose the toggle on a button, not the tr', () => {
  const source = readSource('src/app/admin/audit/page.tsx');
  assert.doesNotMatch(
    source,
    /<tr[^>]*aria-expanded/,
    'aria-expanded on <tr> violates aria-conditional-attr (role=row allows no conditional attributes)',
  );
  assert.doesNotMatch(
    source,
    /<tr[^>]*aria-controls/,
    'aria-controls on <tr> violates aria-conditional-attr',
  );
  assert.match(
    source,
    /<button[^>]*aria-expanded=\{expanded === entry\.id\}/,
    'the expand toggle must be a real button carrying the conditional attributes',
  );
  assert.match(source, /<button[^>]*aria-controls=\{`audit-detail-\$\{entry\.id\}`\}/);
});
