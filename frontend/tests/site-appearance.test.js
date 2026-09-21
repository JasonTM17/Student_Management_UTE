const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadTs(relativePath, deps = {}) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      // Without interop the emitted `path.default.join` is undefined at
      // runtime, so a default-imported node module cannot be loaded here.
      esModuleInterop: true,
    },
  }).outputText;
  const moduleRecord = { exports: {} };
  const requireShim = (id) => (id in deps ? deps[id] : require(id));
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, requireShim);
  return moduleRecord.exports;
}

const LEGACY_ROUTE = '/api/site-appearance';

// The real predicate, so a fabricated error only falls back the way an actual
// AxiosError would.
const { isAxiosError } = require('axios');

function apiError(status) {
  const error = new Error(status === undefined ? 'Network Error' : `Request failed with ${status}`);
  error.isAxiosError = true;
  if (status !== undefined) {
    error.response = { status };
  }
  return error;
}

function stampedAppearance(overrides = {}) {
  return {
    version: 1_700_000_000_000,
    updatedAt: '2026-09-21T09:00:00.000Z',
    accent: 'river-blue',
    hero: {
      en: { eyebrow: 'UTE', title: 'Welcome', description: '' },
      vi: { eyebrow: 'UTE', title: 'Chào mừng', description: '' },
    },
    postOrder: ['a1'],
    ...overrides,
  };
}

/**
 * Loads the client with the API module and the legacy same-origin route
 * replaced by spies, and returns { client, calls, restore }.
 */
function withAppearanceClient({ get, put, legacy }) {
  const appearance = loadTs('src/lib/site-appearance.ts');
  const calls = { get: 0, put: 0, legacy: [] };
  const client = loadTs('src/lib/site-appearance-client.ts', {
    axios: { isAxiosError },
    '@/lib/api': {
      siteAppearanceApi: {
        get: async () => {
          calls.get += 1;
          if (get instanceof Error) throw get;
          return get;
        },
        put: async (payload) => {
          calls.put += 1;
          if (put instanceof Error) throw put;
          return put;
        },
      },
    },
    '@/lib/site-appearance': appearance,
    '@/lib/session-hint': { CSRF_COOKIE_NAME: 'campuscore_csrf' },
  });

  const originalFetch = globalThis.fetch;
  globalThis.fetch = async (url, init) => {
    calls.legacy.push({ url, method: init?.method ?? 'GET', body: init?.body });
    const answer = typeof legacy === 'function' ? legacy(url, init) : legacy;
    if (answer instanceof Error) throw answer;
    return answer;
  };

  return {
    client,
    calls,
    appearance,
    restore: () => {
      globalThis.fetch = originalFetch;
    },
  };
}

function jsonResponse(payload, ok = true) {
  return { ok, status: ok ? 200 : 500, json: async () => payload };
}

test('an unstamped appearance payload never masquerades as a fresh change', () => {
  const { sanitizeSiteAppearance, DEFAULT_SITE_APPEARANCE, SITE_APPEARANCE_UNSET_UPDATED_AT } =
    loadTs('src/lib/site-appearance.ts');

  for (const payload of [{}, null, { accent: 'river-blue' }]) {
    const first = sanitizeSiteAppearance(payload);
    const second = sanitizeSiteAppearance(payload);
    // Change detection compares these two fields; minting "now" would make
    // every 15s poll look like a new branding write.
    assert.equal(first.updatedAt, SITE_APPEARANCE_UNSET_UPDATED_AT);
    assert.equal(first.version, DEFAULT_SITE_APPEARANCE.version);
    assert.equal(first.updatedAt, second.updatedAt);
  }

  // A real stamp survives untouched.
  const stamped = stampedAppearance();
  assert.equal(sanitizeSiteAppearance(stamped).updatedAt, stamped.updatedAt);
  assert.equal(sanitizeSiteAppearance(stamped).version, stamped.version);
});

test('fetch only falls back to the legacy route when the API cannot answer', async () => {
  for (const status of [500, 503, 403, 401, 400, undefined]) {
    const harness = withAppearanceClient({ get: apiError(status), legacy: jsonResponse({}) });
    try {
      await assert.rejects(() => harness.client.fetchSiteAppearance());
      assert.deepEqual(harness.calls.legacy, [], `status ${status} must not read the legacy route`);
    } finally {
      harness.restore();
    }
  }

  // Only an answered 404 — a deployment that predates the endpoint — may read
  // the legacy route. "No answer" is an outage and has to surface as one.
  for (const status of [404]) {
    const harness = withAppearanceClient({
      get: apiError(status),
      legacy: jsonResponse(stampedAppearance({ hasSavedPayload: true })),
    });
    try {
      const appearance = await harness.client.fetchSiteAppearance();
      assert.equal(appearance.accent, 'river-blue');
      assert.equal(harness.calls.legacy.length, 1);
    } finally {
      harness.restore();
    }
  }
});

test('a stamped API row is authoritative and costs no legacy request', async () => {
  const harness = withAppearanceClient({
    get: stampedAppearance({ accent: 'campus-gold', version: 123, updatedAt: '2020-01-01T00:00:00.000Z' }),
    legacy: jsonResponse({}),
  });
  try {
    const appearance = await harness.client.fetchSiteAppearance();
    assert.equal(appearance.accent, 'campus-gold');
    assert.equal(appearance.version, 123);
    assert.deepEqual(harness.calls.legacy, []);
  } finally {
    harness.restore();
  }
});

test('legacy branding migrates forward while the API row is empty', async () => {
  const harness = withAppearanceClient({
    get: {},
    legacy: jsonResponse({
      ...stampedAppearance({ accent: 'campus-gold' }),
      hasSavedPayload: true,
    }),
  });
  try {
    const appearance = await harness.client.fetchSiteAppearance();
    // An empty KV row must not silently reset a live site to the defaults.
    assert.equal(appearance.accent, 'campus-gold');
    assert.notEqual(appearance.updatedAt, '1970-01-01T00:00:00.000Z');
    // The poll must not re-ask every 15s once the answer is known.
    await harness.client.fetchSiteAppearance();
    assert.equal(harness.calls.legacy.length, 1);
  } finally {
    harness.restore();
  }
});

test('an empty API row with an empty legacy store answers defaults, not a fake save', async () => {
  const harness = withAppearanceClient({
    get: {},
    legacy: jsonResponse({ ...stampedAppearance(), hasSavedPayload: false }),
  });
  try {
    const appearance = await harness.client.fetchSiteAppearance();
    assert.equal(appearance.accent, 'ute-yellow');
    assert.equal(appearance.updatedAt, '1970-01-01T00:00:00.000Z');
  } finally {
    harness.restore();
  }
});

test('save reports a legacy-route write as not durable', async () => {
  const harness = withAppearanceClient({
    get: {},
    put: apiError(404),
    legacy: jsonResponse(stampedAppearance()),
  });
  try {
    const result = await harness.client.saveSiteAppearance(stampedAppearance());
    assert.equal(result.persisted, false, 'an ephemeral file write is not a durable save');
    assert.equal(result.accent, 'river-blue');
    assert.equal(harness.calls.legacy.length, 1);
    assert.equal(harness.calls.legacy[0].method, 'PUT');
  } finally {
    harness.restore();
  }
});

test('save refuses to hide an API failure behind the legacy route', async () => {
  for (const status of [500, 502, 403, 401, 400]) {
    const harness = withAppearanceClient({
      get: {},
      put: apiError(status),
      legacy: jsonResponse(stampedAppearance()),
    });
    try {
      await assert.rejects(() => harness.client.saveSiteAppearance(stampedAppearance()));
      assert.deepEqual(harness.calls.legacy, [], `status ${status} must not write the legacy route`);
    } finally {
      harness.restore();
    }
  }

  // Even on the fallback path, a failed legacy write is a failure.
  const failing = withAppearanceClient({
    get: {},
    put: apiError(404),
    legacy: jsonResponse({ message: 'SITE_APPEARANCE_NOT_SAVED' }, false),
  });
  try {
    await assert.rejects(
      () => failing.client.saveSiteAppearance(stampedAppearance()),
      /appearance-save-failed/,
    );
  } finally {
    failing.restore();
  }
});

test('a durable save keeps the stamp the API stored', async () => {
  const harness = withAppearanceClient({
    get: {},
    put: stampedAppearance({ version: 1_700_000_100_000, updatedAt: '2026-09-21T10:00:00.000Z' }),
    legacy: jsonResponse({}),
  });
  try {
    const result = await harness.client.saveSiteAppearance(stampedAppearance());
    assert.equal(result.persisted, true);
    assert.equal(result.version, 1_700_000_100_000);
    assert.equal(result.updatedAt, '2026-09-21T10:00:00.000Z');
    assert.deepEqual(harness.calls.legacy, []);
  } finally {
    harness.restore();
  }
});

test('the legacy file store distinguishes an unsaved store from a saved one', async () => {
  const appearance = loadTs('src/lib/site-appearance.ts');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'appearance-'));
  const file = path.join(dir, 'nested', 'campuscore-site-appearance.json');
  const previous = process.env.SITE_APPEARANCE_PATH;
  process.env.SITE_APPEARANCE_PATH = file;
  const store = loadTs('src/lib/site-appearance-store.ts', { '@/lib/site-appearance': appearance });
  try {
    // Nothing saved: the migration forward must not promote defaults.
    assert.equal(await store.readSavedSiteAppearance(), null);
    assert.equal((await store.readSiteAppearance()).updatedAt, '1970-01-01T00:00:00.000Z');

    const saved = await store.writeSiteAppearance({ accent: 'river-blue', postOrder: ['x1'] });
    assert.equal(saved.accent, 'river-blue');
    assert.notEqual(saved.version, 1);
    assert.notEqual(saved.updatedAt, '1970-01-01T00:00:00.000Z');
    const reloaded = await store.readSavedSiteAppearance();
    assert.equal(reloaded.updatedAt, saved.updatedAt);
    assert.equal(reloaded.version, saved.version);
    assert.ok(fs.existsSync(file));
  } finally {
    if (previous === undefined) delete process.env.SITE_APPEARANCE_PATH;
    else process.env.SITE_APPEARANCE_PATH = previous;
    fs.rmSync(dir, { recursive: true, force: true });
  }
});


test('applyPageOrder lands a reorder and never moves off-page pins', () => {
  const { applyPageOrder } = loadTs('src/lib/site-appearance.ts');

  // First save with nothing pinned yet.
  assert.deepEqual(applyPageOrder([], ['c', 'a', 'b']), ['c', 'a', 'b']);

  // A repeat reorder of the same page must actually apply. A helper that keeps
  // the existing order would return ['c','a','b'] here, discarding the drag.
  assert.deepEqual(applyPageOrder(['c', 'a', 'b'], ['b', 'c', 'a']), ['b', 'c', 'a']);

  // Pins for announcements outside the reordered page keep their positions.
  assert.deepEqual(applyPageOrder(['z', 'c', 'a', 'b'], ['b', 'a', 'c']), ['z', 'b', 'a', 'c']);
  assert.deepEqual(
    applyPageOrder(['p', 'c', 'q', 'a', 'b', 'r'], ['a', 'b', 'c']),
    ['p', 'a', 'q', 'b', 'c', 'r'],
  );

  // Pins outside the offered page are preserved — including ones this batch
  // does not mention — so a reorder never silently unpins an announcement.
  assert.deepEqual(applyPageOrder(['z', 'c', 'a'], ['a']), ['z', 'c', 'a']);
  assert.deepEqual(applyPageOrder(['z', 'c', 'a', 'b'], ['a', 'b']), ['z', 'c', 'a', 'b']);

  // Nothing pinned and nothing offered stays empty.
  assert.deepEqual(applyPageOrder([], []), []);
});

test('reorder save paths apply the new order instead of a plain merge', () => {
  const adminAnnouncements = read('src/app/admin/announcements/page.tsx');
  const studio = read('src/app/admin/appearance/page.tsx');
  const editor = read('src/app/dashboard/editor/page.tsx');
  const appearance = read('src/lib/site-appearance.ts');

  for (const source of [adminAnnouncements, studio, editor]) {
    assert.match(source, /applyPageOrder\(/);
  }
  // The misleading helper is gone: it could not apply a reorder at all.
  assert.doesNotMatch(appearance, /export function mergePostOrder/);
});

function orderByIds(items, order) {
  const rank = new Map(order.map((id, index) => [id, index]));
  return [...items].sort((left, right) => {
    const leftRank = rank.has(left.id) ? rank.get(left.id) : Number.MAX_SAFE_INTEGER;
    const rightRank = rank.has(right.id) ? rank.get(right.id) : Number.MAX_SAFE_INTEGER;
    return leftRank - rightRank;
  });
}

function movePostOrder(order, id, direction) {
  const index = order.indexOf(id);
  const nextIndex = index + direction;
  const next = [...order];
  const [item] = next.splice(index, 1);
  next.splice(nextIndex, 0, item);
  return next;
}

test('portal login and appearance studio files are wired', () => {
  const login = read('src/app/login/page.tsx');
  const home = read('src/app/page.tsx');
  const messages = read('src/i18n/messages.ts');
  const appearance = read('src/app/admin/appearance/page.tsx');
  const shell = read('src/components/auth/AuthShell.tsx');
  const lib = read('src/lib/site-appearance.ts');
  const route = read('src/app/api/site-appearance/route.ts');

  assert.match(login, /parseLoginPortal/);
  assert.match(login, /portalMatchesUser/);
  assert.match(shell, /data-login-portal/);
  assert.match(home, /login\?portal=student/);
  // The landing page renders all three role lanes (student/lecturer/admin)
  // from the roleCards map, including their localized hrefs.
  assert.match(home, /roleLanes\[card\.key\]/);
  assert.match(home, /key: 'lecturer'/);
  assert.match(home, /key: 'admin'/);
  assert.match(messages, /\/login\?portal=lecturer/);
  assert.match(messages, /\/login\?portal=admin/);
  assert.match(appearance, /broadcastSiteAppearance/);
  assert.match(lib, /function orderByIds/);
  assert.match(lib, /function movePostOrder/);
  assert.match(route, /requireAdmin/);
  assert.match(route, /writeSiteAppearance/);
});

test('admin-selected notice order is stable for feeds', () => {
  const items = [{ id: 'c' }, { id: 'a' }, { id: 'b' }];
  const ordered = orderByIds(items, ['b', 'a', 'c']);
  assert.deepEqual(ordered.map((item) => item.id), ['b', 'a', 'c']);
  assert.deepEqual(movePostOrder(['b', 'a', 'c'], 'a', -1), ['a', 'b', 'c']);
});

test('site appearance polling is throttled and pauses while the tab is hidden', () => {
  const provider = read('src/components/providers/SiteAppearanceProvider.tsx');

  assert.match(provider, /15000/);
  assert.doesNotMatch(provider, /4000/);
  assert.match(provider, /document\.visibilityState === 'visible'/);
  assert.match(provider, /addEventListener\('visibilitychange', onVisibilityChange\)/);
  assert.match(provider, /removeEventListener\('visibilitychange', onVisibilityChange\)/);
});
