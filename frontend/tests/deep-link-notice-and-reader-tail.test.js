const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

const EDITOR_PAGE = read('src/app/dashboard/editor/page.tsx');
const TOOLBAR = read('src/components/announcements/reader/ReadingToolbar.tsx');
const DISPATCH = read('src/components/announcements/reader/layouts/AdministrativeDispatchSheet.tsx');

/**
 * The editor's ?editId= / ?id= deep link used to report both failures through
 * `toast.error` alone. Toasts expire, so the author was left editing a blank
 * draft with no surviving record of which notice failed to resolve.
 */

test('the deep-link effect keeps its toast branches', () => {
  assert.match(EDITOR_PAGE, /setDeepLinkNotice\(\{ targetId, reason: 'not-found' \}\);\s*\n\s*toast\.error\(/);
  assert.match(EDITOR_PAGE, /setDeepLinkNotice\(\{ targetId, reason: 'load-failed' \}\);\s*\n\s*toast\.error\(/);
});

test('both deep-link failures also raise the durable inline notice', () => {
  const raised = EDITOR_PAGE.match(/setDeepLinkNotice\(\{ targetId, reason:/g) ?? [];
  assert.equal(raised.length, 2, 'expected one notice per failing branch');
  // The success branch has to clear it, or the banner would lie after a retry.
  assert.match(EDITOR_PAGE, /setDeepLinkNotice\(null\);\s*\n\s*handleLoadAnnouncement\(found\);/);
});

test('the notice is rendered as a dismissible role=alert banner naming the anchor', () => {
  assert.match(EDITOR_PAGE, /role="alert"/);
  assert.match(EDITOR_PAGE, /aria-live="polite"/);
  assert.match(EDITOR_PAGE, /\$\{deepLinkNotice\.targetId\}/);
  assert.match(EDITOR_PAGE, /onClick=\{\(\) => setDeepLinkNotice\(null\)\}/);
  assert.match(EDITOR_PAGE, /aria-label=\{isVi \? 'Đóng cảnh báo liên kết' : 'Dismiss deep-link warning'\}/);
});

test('the banner states both outcomes in both locales', () => {
  for (const fragment of [
    'was not found',
    'could not be loaded',
    'Không tìm thấy thông báo cần sửa',
    'Không thể tải thông báo cần sửa',
  ]) {
    assert.ok(EDITOR_PAGE.includes(fragment), `missing notice copy: ${fragment}`);
  }
});

test('the inline notice really is what the pre-fix page lacked', () => {
  const before = `useEffect(() => {
      if (found) { handleLoadAnnouncement(found); } else {
        toast.error('The notice to edit was not found.');
      }
    })
    .catch(() => { toast.error('The notice to edit could not be loaded.'); });`;
  assert.equal((before.match(/setDeepLinkNotice\(\{ targetId, reason:/g) ?? []).length, 0);
  assert.equal(/role="alert"/.test(before), false);
});

test('the banner reads in both themes and the [locale] route reuses this page', () => {
  const banner = EDITOR_PAGE.slice(
    EDITOR_PAGE.indexOf('role="alert"'),
    EDITOR_PAGE.indexOf('{/* Main Tab Controller */}'),
  );
  assert.ok(banner.length > 0, 'the banner must sit above the tab controller');
  assert.deepEqual(fixedLightSurfaces(banner), []);
  assert.match(banner, /text-destructive/);
  assert.equal(
    read('src/app/[locale]/dashboard/editor/page.tsx').trim(),
    "export { default } from '../../../dashboard/editor/page';",
  );
});

// ---------------------------------------------------------------------------
// Tail UI defects in the announcement reader.
// ---------------------------------------------------------------------------

/** Opaque fixed-light surfaces with no dark counterpart are unreadable in dark mode. */
function fixedLightSurfaces(source) {
  const raw = /\b(bg-white|bg-black|text-slate-9\d0|text-gray-9\d0|text-slate-800|text-gray-800)\b/;
  const offences = [];
  source.split(/\r?\n/).forEach((line, index) => {
    const code = line.replace(/\/\/.*$/, '').replace(/\/\*.*?\*\//g, '');
    if (!raw.test(code)) return;
    if (code.includes('dark:') || code.includes('print:')) return;
    if (!new RegExp(`${raw.source}(?!/\\d)`).test(code)) return;
    offences.push(`${index + 1} -> ${code.trim()}`);
  });
  return offences;
}

test('the light reading-theme chip no longer paints a fixed-white surface', () => {
  const chip = TOOLBAR.slice(
    TOOLBAR.indexOf("setTheme('light')"),
    TOOLBAR.indexOf("setTheme('sepia')"),
  );
  assert.ok(chip.length > 0, 'the light theme chip is missing');
  assert.deepEqual(fixedLightSurfaces(chip), []);
  assert.match(chip, /preferences\.theme === 'light'\s+\? 'bg-background[^']*'/);
});

test('the whole reading toolbar is free of unpaired fixed-light surfaces', () => {
  assert.deepEqual(fixedLightSurfaces(TOOLBAR), []);
});

test('the pre-fix toolbar chip really is what this guard rejects', () => {
  const before = `? 'bg-white text-amber-600 shadow-xs'`;
  assert.equal(fixedLightSurfaces(before).length, 1);
});

test('the dispatch sheet formats dates through the shared locale helper', () => {
  assert.match(DISPATCH, /const \{ formatDate, formatDateTime \} = useI18n\(\);/);
  assert.deepEqual(handBuiltDateParts(DISPATCH), []);
  assert.match(DISPATCH, /formatDate\(dateObj,\s*\{[^}]*day: '2-digit'/);
  // Every date in this sheet comes from the shared formatter: the numeric
  // signature date and the prose heading each consume a formatDate result, so
  // no local day/month/year arithmetic may appear anywhere in the file.
  assert.ok((DISPATCH.match(/officialDate/g) ?? []).length >= 2);
  assert.match(DISPATCH, /formatDate\(dateObj,\s*\{\s*day: 'numeric'/);
  assert.match(DISPATCH, /formatDate\(dateObj,\s*\{\s*month: 'long'/);
});

/** Day/month/year arithmetic re-implemented in a component instead of the i18n helper. */
function handBuiltDateParts(source) {
  const offenders = [];
  source.split(/\r?\n/).forEach((line, index) => {
    if (/padStart\(2,\s*'0'\)/.test(line) || /getMonth\(\)\s*\+\s*1/.test(line)) {
      offenders.push(`${index + 1} -> ${line.trim()}`);
    }
  });
  return offenders;
}

test('the pre-fix dispatch header really is what this guard rejects', () => {
  const before = `const day = String(dateObj.getDate()).padStart(2, '0');
    const month = String(dateObj.getMonth() + 1).padStart(2, '0');`;
  assert.equal(handBuiltDateParts(before).length, 2);
});

test('the vi locale path still renders the DD/MM/YYYY line the document shows', () => {
  // Read the options the component itself passes, then render them through the
  // same BCP-47 tag the i18n provider uses (localeCodes.vi), so the assertion
  // covers the real locale path rather than a hand-written expectation.
  const block = DISPATCH.match(/formatDate\(dateObj,\s*\{([\s\S]*?)\}\s*\)/);
  assert.ok(block, 'the dispatch sheet must build its date through formatDate');
  const options = {};
  for (const [, key, value] of block[1].matchAll(/(\w+):\s*'([\w-]+)'/g)) {
    options[key] = value;
  }
  assert.deepEqual(Object.keys(options).sort(), ['day', 'month', 'year']);
  const viTag = read('src/i18n/config.ts').match(/vi:\s*'([\w-]+)'/)[1];
  assert.equal(viTag, 'vi-VN');
  const rendered = new Intl.DateTimeFormat(viTag, options).format(new Date(2026, 8, 5));
  assert.equal(rendered, '05/09/2026');
});
