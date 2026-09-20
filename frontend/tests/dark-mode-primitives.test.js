const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

// ---------------------------------------------------------------------------
// The shared primitive layer keeps to the semantic token system.
//
// `DESIGN.md` forbids raw Tailwind palettes on shared primitives, because a
// primitive sits under every screen: one fixed-light value there renders an
// unreadable patch in the opposite theme on pages nobody thought to check.
//
// Scope is deliberately narrow, and the guard is deliberately partial:
//   * only `src/components/ui/` — the shared primitives, where a defect
//     propagates furthest. Page-level composition and the editorial reader are
//     out of scope.
//   * only OPAQUE raw light surfaces. A translucent value (`bg-white/10`,
//     `bg-black/55`) is an overlay on whatever is behind it and is legitimate in
//     both themes, so it is not flagged.
//   * a value that already carries a `dark:` counterpart, or is scoped to
//     `print:`, is satisfiable and is not flagged.
//
// What this cannot detect, and therefore does not claim: a fixed-light *hairline*
// over a theme-flipping surface (`border-white/10` on `--surface-alt` was exactly
// that defect — translucent, so this guard passes it). Catching that class needs
// rendered contrast measurement, not source inspection.
// ---------------------------------------------------------------------------

const RAW_LIGHT_TOKEN = /\b(bg-white|bg-black|text-slate-9\d0|text-gray-9\d0|text-slate-800|text-gray-800)\b/;

/** Reads every `.tsx` in the shared primitive layer. */
function primitiveFiles() {
  const dir = path.join(root, 'src', 'components', 'ui');
  return fs
    .readdirSync(dir)
    .filter((name) => name.endsWith('.tsx'))
    .map((name) => ({ name, relative: `src/components/ui/${name}`, text: fs.readFileSync(path.join(dir, name), 'utf8') }));
}

/** Returns the offending occurrences across every primitive file. */
function opaqueRawLightSurfaces() {
  const offences = [];
  for (const file of primitiveFiles()) {
    file.text.split(/\r?\n/).forEach((line, index) => {
      // Comments document the rule and name the forbidden values; they are not code.
      const code = line.replace(/\/\/.*$/, '').replace(/\/\*.*?\*\//g, '');
      if (!RAW_LIGHT_TOKEN.test(code)) return;
      if (code.includes('dark:')) return;
      if (code.includes('print:')) return;

      // A translucent value is an overlay, not a light surface.
      const opaqueHit = [...code.matchAll(new RegExp(`${RAW_LIGHT_TOKEN.source}(?!/\\d)`, 'g'))];
      if (opaqueHit.length === 0) return;

      offences.push(`${file.relative}:${index + 1} -> ${opaqueHit.map((m) => m[1]).join(', ')}`);
    });
  }
  return offences;
}

test('shared primitives introduce no opaque raw light surface without a dark counterpart', () => {
  const offences = opaqueRawLightSurfaces();
  assert.deepEqual(
    offences,
    [],
    `Opaque raw light values found in src/components/ui without a dark: counterpart.\n` +
      `Use a semantic token (bg-card, text-foreground, border-border) or pair the value with dark:.\n` +
      `Offences:\n  ${offences.join('\n  ')}`,
  );
});

test('the guard actually recognises an opaque raw light value', () => {
  // A guard that cannot fail is not a guard: prove the matcher against the exact
  // shape it exists to catch, and prove it tolerates the legitimate shapes.
  const opaque = '<div className="rounded-lg bg-white text-slate-900" />';
  const opaqueHits = [...opaque.matchAll(new RegExp(`${RAW_LIGHT_TOKEN.source}(?!/\\d)`, 'g'))];
  assert.equal(opaqueHits.length, 2, 'an opaque bg-white + text-slate-900 must be detected');
  assert.ok(!opaque.includes('dark:'), 'and the fixture must genuinely lack a dark counterpart');

  const guarded = '<div className="bg-white text-slate-900 dark:bg-slate-900 dark:text-slate-100" />';
  assert.ok(guarded.includes('dark:'), 'a paired value is satisfiable and must be excused');

  const overlay = '<div className="bg-white/10 bg-black/55" />';
  assert.equal(
    [...overlay.matchAll(new RegExp(`${RAW_LIGHT_TOKEN.source}(?!/\\d)`, 'g'))].length,
    0,
    'translucent overlays are legitimate in both themes and must not be flagged',
  );
});

test('the primitive layer stays on the token system for its primary surfaces', () => {
  const sources = primitiveFiles().map((file) => file.text).join('\n');
  // Spot-check that the layer really is token-driven rather than merely
  // free of the flagged literals.
  for (const token of ['bg-card', 'text-card-foreground', 'border-border']) {
    assert.ok(sources.includes(token), `expected the primitive layer to use ${token}`);
  }
});
