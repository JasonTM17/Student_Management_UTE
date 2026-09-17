const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const appDir = path.join(root, 'src/app');

/**
 * The English locale is served from `src/app/[locale]/**`, whose pages are one
 * line re-exports of the canonical `src/app/**` implementation. Nothing generates
 * those shims, so a route that exists without its counterpart is a 404 for every
 * English user — which is exactly how `/admin/credit-limit-applications` shipped
 * broken while sitting in the always-visible admin sidebar.
 *
 * The checks below derive the requirement from the repository rather than from a
 * hand-kept list, so a new page cannot be added without either a shim or a
 * deliberate exemption. There are currently no exemptions: every canonical route
 * has a counterpart, so the strictest form of the check is the correct one.
 */

/** Nested files that must exist in both trees for a segment to work. */
const SHIMMED_FILES = ['page.tsx', 'layout.tsx'];

/** The root layout is shared by both trees, so it has no `[locale]` counterpart. */
const ROOT_SEGMENT = '';

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

/** Every canonical `page.tsx` segment, excluding the locale tree and route handlers. */
function canonicalSegments(fileName) {
  const segments = [];
  const walk = (directory, prefix) => {
    for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
      if (entry.name === '[locale]' || entry.name === 'api') continue;
      const absolute = path.join(directory, entry.name);
      if (entry.isDirectory()) {
        walk(absolute, `${prefix}/${entry.name}`);
      } else if (entry.name === fileName) {
        segments.push(prefix);
      }
    }
  };
  walk(appDir, '');
  return segments.sort();
}

function localePathFor(segment, fileName) {
  return path.join(appDir, '[locale]', segment.replace(/^\//, ''), fileName);
}

/** Every `href: '<path>'` literal in the two navigation declarations. */
function navigationHrefs() {
  const sources = ['src/components/admin/AdminFrame.tsx', 'src/app/dashboard/layout.tsx'];
  const found = new Set();
  for (const source of sources) {
    for (const match of read(source).matchAll(/href:\s*'(\/[^']*)'/g)) {
      found.add(match[1]);
    }
  }
  return [...found].sort();
}

test('the navigation declaration is discoverable', () => {
  const hrefs = navigationHrefs();
  // Guards the extractor itself: a refactor that renames the `href:` key would
  // otherwise silently empty this suite and make the assertions below vacuous.
  assert.ok(hrefs.length >= 25, `expected the nav to declare at least 25 routes, saw ${hrefs.length}`);
  assert.ok(hrefs.includes('/admin/credit-limit-applications'), 'the admin sidebar route must be discoverable');
});

test('every navigable route resolves to a real page', () => {
  const missing = navigationHrefs().filter(
    (route) => !fs.existsSync(path.join(appDir, route.replace(/^\//, ''), 'page.tsx')),
  );
  assert.deepEqual(missing, [], `navigation points at routes with no page: ${missing.join(', ')}`);
});

test('every navigable route has an English-locale counterpart', () => {
  const missing = navigationHrefs().filter(
    (route) => !fs.existsSync(localePathFor(route, 'page.tsx')),
  );
  assert.deepEqual(
    missing,
    [],
    `these routes 404 under /en because no [locale] page exists: ${missing.join(', ')}`,
  );
});

test('every canonical route and layout is reachable under the locale prefix', () => {
  for (const fileName of SHIMMED_FILES) {
    const missing = canonicalSegments(fileName)
      .filter((segment) => segment !== ROOT_SEGMENT)
      .filter((segment) => !fs.existsSync(localePathFor(segment, fileName)));
    assert.deepEqual(
      missing,
      [],
      `these ${fileName} files have no [locale] counterpart: ${missing.join(', ') || '(none)'}`,
    );
  }
});

/**
 * A `[locale]` page is acceptable if it forwards rather than implements:
 *
 *  - a re-export, with or without named bindings, e.g.
 *    `export { default, dynamic } from '../../login/page';`
 *  - a redirect-only shim, which is the one case that cannot be a plain
 *    re-export because it has to rebuild the target with the locale prefix
 *    (`[locale]/admin/editor` forwards to `[locale]/dashboard/editor`).
 *
 * Anything that renders its own markup is a second implementation of a page that
 * already exists, and the two copies will drift.
 */

/**
 * A JSX opening tag: a letter-initial name followed by whitespace, `/` or `>`.
 * Deliberately stricter than "looks like angle brackets", because TypeScript
 * generics such as `Record<string, string>` and `Promise<{ locale: string }>`
 * appear in perfectively valid redirect shims.
 */
const JSX_OPENING_TAG = /<[A-Za-z][A-Za-z0-9.]*(?=[\s/>])/;

function forwardsInsteadOfImplementing(source) {
  const body = source.trim();
  if (/^export\s*\{[^}]*\}\s*from\s*'[^']+';?$/m.test(body)) {
    return true;
  }
  const importsNavigation = /from\s*'next\/navigation'/.test(body);
  const isRedirectOnly =
    importsNavigation
    && /\bredirect\s*\(/.test(body)
    && !JSX_OPENING_TAG.test(body)
    && !/'use client'/.test(body);
  return isRedirectOnly;
}

test('[locale] pages forward to the canonical page instead of implementing it', () => {
  const divergent = [];
  for (const segment of canonicalSegments('page.tsx')) {
    if (segment === ROOT_SEGMENT) continue;
    const localePage = localePathFor(segment, 'page.tsx');
    if (!fs.existsSync(localePage)) continue;
    if (!forwardsInsteadOfImplementing(fs.readFileSync(localePage, 'utf8'))) {
      divergent.push(segment || '/');
    }
  }
  assert.deepEqual(
    divergent,
    [],
    `these [locale] pages render their own markup, so the two trees can drift: ${divergent.join(', ')}`,
  );
});
