const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { pathToFileURL } = require('node:url');

const root = path.resolve(__dirname, '..');

async function headerEntries() {
  const cfg = (await import(pathToFileURL(path.join(root, 'next.config.mjs')).href)).default;
  return cfg.headers();
}

function entriesFor(entries, source) {
  return entries.filter((entry) => entry.source === source);
}

test('catch-all block carries the hardened header set', async () => {
  const entries = await headerEntries();
  const catchAll = entriesFor(entries, '/(.*)');
  assert.equal(catchAll.length, 1, 'exactly one catch-all headers() block');
  const byKey = new Map(catchAll[0].headers.map((h) => [h.key, h.value]));

  // CSP graduated to enforced nonce mode in middleware.ts (RT-P2-4); the
  // static header table must NOT reintroduce a report-only policy.
  assert.equal(byKey.get('Content-Security-Policy-Report-Only'), undefined);
  assert.equal(byKey.get('X-Frame-Options'), 'DENY');

  assert.equal(byKey.get('X-Content-Type-Options'), 'nosniff');
  assert.equal(byKey.get('Referrer-Policy'), 'strict-origin-when-cross-origin');
  assert.equal(
    byKey.get('Permissions-Policy'),
    'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
  );
  assert.equal(byKey.get('Cross-Origin-Opener-Policy'), 'same-origin');
});

test('reset-password paths tighten Referrer-Policy to no-referrer', async () => {
  const entries = await headerEntries();
  for (const source of ['/reset-password', '/:locale(en|vi)/reset-password']) {
    const matches = entriesFor(entries, source);
    assert.equal(matches.length, 1, `exactly one headers() block for ${source}`);
    assert.deepEqual(matches[0].headers, [{ key: 'Referrer-Policy', value: 'no-referrer' }]);
  }
});

test('robots disallows the token-carrying reset paths', () => {
  const source = fs.readFileSync(path.join(root, 'src/app/robots.ts'), 'utf8');
  for (const p of ['/reset-password', '/en/reset-password', '/vi/reset-password']) {
    assert.match(source, new RegExp(`'${p.replace(/\//g, '\\/')}'`), `disallow ${p}`);
  }
  // The existing public allows must not have been displaced.
  assert.match(source, /'\/login'/);
  assert.match(source, /'\/api\/'/);
});
