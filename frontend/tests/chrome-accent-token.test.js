const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

// ---------------------------------------------------------------------------
// The navy portal chrome keeps a theme-stable accent.
//
// `--portal-yellow` is a FILL token: it inverts between themes (a light tint in
// light mode, a dark tint in dark mode) so that a chip paired with
// `--portal-yellow-ink` stays legible whichever theme is active. Reusing it as a
// FOREGROUND or hairline paints dark-on-dark in dark mode, because the navy
// chrome it sits on does not lighten with the theme.
//
// That defect shipped: the section eyebrow and the numbered step markers on the
// sign-in panel measured a 1.27:1 contrast ratio against the sidebar (WCAG needs
// 4.5:1 for text), which is invisible. `--portal-chrome-accent` carries the
// light value in every scope and measures 12.74:1.
//
// These assertions keep the two roles separate.
// ---------------------------------------------------------------------------

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

/** Collects the CSS source of the token scopes that must define the accent. */
function globalsCss() {
  return read('src/app/globals.css');
}

test('the chrome accent is defined in every theme and accent scope', () => {
  const css = globalsCss();
  const declarations = css.match(/--portal-chrome-accent:\s*[^;]+;/g) ?? [];
  // :root, the two light accent variants, .dark, and the two dark-accent variants.
  assert.ok(
    declarations.length >= 6,
    `expected --portal-chrome-accent in all 6 token scopes, found ${declarations.length}: ${declarations.join(' ')}`,
  );

  // Every declaration of the accent must be a LIGHT value, because the chrome it
  // is drawn on is navy in every theme. An oklch lightness below 0.6 here would
  // reintroduce the invisible-accent defect.
  for (const declaration of declarations) {
    const lightness = Number(declaration.match(/oklch\(\s*([\d.]+)/)?.[1] ?? NaN);
    assert.ok(!Number.isNaN(lightness), `could not read the lightness from: ${declaration}`);
    assert.ok(
      lightness >= 0.6,
      `--portal-chrome-accent must stay light on the navy chrome, found L=${lightness} in: ${declaration}`,
    );
  }
});

test('the fill token keeps its intentional inversion', () => {
  const css = globalsCss();
  // If someone "fixes" the badge by making --portal-yellow light in dark mode,
  // the fill+ink chip pairing breaks instead. Guard the pair, not one token.
  const dark = css.slice(css.indexOf('.dark {'));
  assert.match(dark, /--portal-yellow:\s*oklch\(\s*0\.3\d/, 'dark --portal-yellow stays a dark fill');
  assert.match(dark, /--portal-yellow-ink:\s*oklch\(\s*0\.9\d/, 'and pairs with a light ink');
});

test('no source file uses the fill token as a foreground or hairline', () => {
  const offenders = [];
  const walk = (dir) => {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name === '.mimosa' || entry.name === 'node_modules') continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
        continue;
      }
      if (!/\.(tsx|ts|css)$/.test(entry.name)) continue;
      const relative = path.relative(root, full).replace(/\\/g, '/');
      const text = fs.readFileSync(full, 'utf8');
      text.split(/\r?\n/).forEach((line, index) => {
        const code = line.replace(/\/\/.*$/, '').replace(/\/\*.*?\*\//g, '');
        // Comments name the forbidden token to explain the rule; skip them.
        if (/^\s*\*/.test(line)) return;
        for (const match of code.matchAll(/(text|border)-\[var\(--portal-yellow\)\]/g)) {
          offenders.push(`${relative}:${index + 1} -> ${match[1]}[var(--portal-yellow)]`);
        }
      });
    }
  };
  walk(path.join(root, 'src'));

  assert.deepEqual(
    offenders,
    [],
    `The fill token is used in a foreground role; use var(--portal-chrome-accent) instead.\n  ${offenders.join('\n  ')}`,
  );
});

test('the eyebrow rule resolves to the chrome accent, not the fill', () => {
  const css = globalsCss();
  const rule = css.match(/\.portal-menu-label\.portal-menu-label-accent\s*\{[^}]*\}/);
  assert.ok(rule, 'the accent eyebrow rule must exist');
  assert.match(rule[0], /color:\s*var\(--portal-chrome-accent\)/);
  assert.doesNotMatch(rule[0], /color:\s*var\(--portal-yellow\)/);
});

test('the guillotine guard can actually fail', () => {
  // A guard that cannot fail is not a guard.
  const fixture = 'className="text-[var(--portal-yellow)]"';
  assert.equal([...fixture.matchAll(/(text|border)-\[var\(--portal-yellow\)\]/g)].length, 1);
  const fine = 'className="text-[var(--portal-chrome-accent)] bg-[var(--portal-yellow)]/10"';
  assert.equal([...fine.matchAll(/(text|border)-\[var\(--portal-yellow\)\]/g)].length, 0);
});
