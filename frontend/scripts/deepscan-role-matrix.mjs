/**
 * Role-by-role browser walk of the portal against a running dev server.
 *
 * Lives under frontend/scripts so it resolves the workspace's playwright-core.
 * Every step is observational JSON plus a screenshot, so the same run serves as
 * before/after evidence for a fix. Not part of the app build.
 *
 *   node scripts/deepscan-role-matrix.mjs --roles guest,student
 */
import { existsSync, mkdirSync, readdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { chromium } from 'playwright-core';

const OUT = join(process.cwd(), '..', 'scratch', 'deepscan', 'out');
mkdirSync(OUT, { recursive: true });

const argv = process.argv.slice(2);
const arg = (name, fallback) => {
  const i = argv.indexOf(`--${name}`);
  return i === -1 ? fallback : argv[i + 1];
};

const BASE = arg('base', 'http://127.0.0.1:4317');
const ONLY = (arg('roles', 'guest,student,lecturer,admin') || '')
  .split(',')
  .filter(Boolean);
const PASSWORD = arg(
  'password',
  // `password123` is the seeded demo password documented in the restful-api
  // README; a local database whose demo passwords were rotated needs
  // DEEPSCAN_PASSWORD or --password instead.
  process.env.DEEPSCAN_PASSWORD || 'password123',
);

const ACCOUNTS = {
  student: 'student@campuscore.edu',
  lecturer: 'lecturer@campuscore.edu',
  admin: 'admin@campuscore.edu',
  superadmin: 'sa-e2e@campuscore.edu',
  dean: 'tk-e2e@campuscore.edu',
};

/**
 * `/vi/login` without a `portal` param is the student portal, and it refuses a
 * non-student on purpose (`portalMatchesUser`). Logging in each identity through
 * its own portal is what the product expects an administrator to do.
 */
const PORTAL = {
  student: 'student',
  lecturer: 'lecturer',
  admin: 'admin',
  superadmin: 'admin',
  dean: 'admin',
};

const ROUTES = {
  guest: ['/', '/login', '/register', '/definitely-not-a-page'],
  student: [
    '/dashboard',
    '/dashboard/schedule',
    '/dashboard/grades',
    '/dashboard/transcript',
    '/dashboard/enrollments',
    '/dashboard/courses',
    '/dashboard/thesis',
    '/dashboard/conduct',
    '/dashboard/profile',
    '/dashboard/notifications',
    '/dashboard/announcements',
    '/dashboard/certificates',
  ],
  lecturer: [
    '/dashboard',
    '/dashboard/lecturer',
    '/dashboard/grades',
    '/dashboard/thesis',
    '/dashboard/courses',
    '/dashboard/schedule',
    '/dashboard/profile',
  ],
  admin: [
    '/admin',
    '/admin/announcements',
    '/admin/users',
    '/admin/courses',
    '/admin/sections',
    '/admin/classrooms',
    '/admin/enrollments',
    '/admin/thesis',
    '/admin/knowledge',
    '/admin/assistant-knowledge',
    '/admin/appearance',
    '/admin/departments',
    '/admin/lecturers',
    '/admin/semesters',
    '/admin/academic-years',
    '/admin/credit-limit-applications',
    '/admin/editor',
  ],
  superadmin: [
    '/admin',
    '/admin/users',
    '/admin/appearance',
    '/admin/knowledge',
    '/admin/editor',
    '/dashboard',
  ],
  // A dean (TRUONG_KHOA) owns the thesis console but not the user/appearance
  // admin: `admin/thesis/page.tsx` grants access on
  // `isAdmin || isSuperAdmin || isFacultyHead`, while the others test only
  // `isAdmin || isSuperAdmin`.
  dean: ['/dashboard', '/dashboard/thesis', '/admin/thesis'],
};

/**
 * Routes a role must not reach. `visit` only proves a page rendered, so the
 * boundary check is inverted here: staying on the requested path is the failure.
 */
const DENIED = {
  guest: ['/dashboard/grades', '/dashboard/transcript', '/admin/users'],
  student: ['/admin/users', '/admin/appearance', '/admin/editor', '/dashboard/lecturer'],
  lecturer: ['/admin/users', '/admin/appearance', '/admin/lecturers'],
  dean: ['/admin/users', '/admin/appearance', '/admin/editor'],
};

const SEARCH_INPUT =
  'input[type="search"], input[name*="search" i], input[placeholder*="search" i], input[placeholder*="tìm" i]';

const results = [];
let failures = 0;

function record(step) {
  results.push(step);
  process.stdout.write(`${JSON.stringify(step)}\n`);
  if (step.verdict !== 'ok' && step.verdict !== 'info') failures += 1;
}

/** Items a list actually rendered, across the shapes the portal uses. */
function rowCount(snap) {
  const c = snap?.counts || {};
  return c['tbody tr'] || c.article || c.li || c['[role="listitem"]'] || 0;
}

async function shoot(page, name) {
  await page.screenshot({
    path: join(OUT, `${name.replace(/[^a-z0-9]+/gi, '_')}.png`),
  });
}

async function snapshot(page) {
  return page.evaluate((searchSel) => {
    const counts = {};
    for (const s of ['tbody tr', 'table tr', 'li', 'article', '[role="listitem"]']) {
      counts[s] = document.querySelectorAll(s).length;
    }
    const inputs = Array.from(document.querySelectorAll(searchSel)).map((el) => ({
      name: el.getAttribute('name') || '',
      placeholder: el.getAttribute('placeholder') || '',
      visible: !!(el.offsetWidth || el.offsetHeight),
    }));
    const text = document.body.innerText || '';
    return {
      counts,
      searchInputs: inputs,
      heading: (document.querySelector('h1,h2')?.textContent || '').trim().slice(0, 70),
      emptyState: /không có dữ liệu|không có thông báo|no data|no matching|chưa có|không tìm thấy/i.test(
        text,
      ),
      errorState: /có lỗi|đã lỗi|không tải được|request failed|internal server/i.test(text),
      text: text.replace(/\s+/g, ' ').slice(0, 240),
    };
  }, SEARCH_INPUT);
}

/**
 * Types a query that matches nothing and reports whether the list responded. A
 * feed may filter client side, so responding means *some* observable change:
 * the row count moved, an empty state appeared, or the token reached the API.
 * Nothing moving at all is the signature of a dead search box.
 */
async function probeSearch(page) {  const before = await snapshot(page);
  if (!before.searchInputs.some((i) => i.visible)) {
    return { verdict: 'info', reason: 'search input not rendered', before };
  }
  const seen = [];
  const handler = (req) => {
    if (req.url().includes('/api/v1')) seen.push(req.url());
  };
  page.on('request', handler);
  try {
    const token = 'zzqxkhongtonTai9000';
    await page.locator(SEARCH_INPUT).first().fill(token);
    await page.keyboard.press('Enter').catch(() => {});
    const button = page.getByRole('button', { name: /tìm|search/i }).first();
    if (await button.count()) await button.click({ timeout: 2000 }).catch(() => {});
    await page.waitForTimeout(2000);
    const after = await snapshot(page);
    const rowsBefore = rowCount(before);
    const rowsAfter = rowCount(after);
    const carried = seen.some((u) => u.includes('search=') || u.includes(token));
    const emptied = after.emptyState && !before.emptyState;
    return {
      verdict: rowsBefore !== rowsAfter || carried || emptied ? 'ok' : 'fail',
      rowsBefore,
      rowsAfter,
      tokenReachedApi: carried,
      emptyStateShown: emptied,
      requests: seen.slice(0, 4),
      afterText: after.text.slice(0, 120),
    };
  } finally {
    page.off('request', handler);
  }
}

/**
 * The workspace pins a newer playwright than the browser build already on this
 * machine, so resolve any installed chromium instead of failing on a revision.
 */
function findChromium() {
  if (process.env.CHROMIUM_PATH) return process.env.CHROMIUM_PATH;
  const root = join(process.env.LOCALAPPDATA || '', 'ms-playwright');
  if (!existsSync(root)) return undefined;
  for (const dir of readdirSync(root).filter((d) => /^chromium-\d+$/.test(d)).reverse()) {
    for (const candidate of ['chrome-win64/chrome.exe', 'chrome-win/chrome.exe']) {
      const p = join(root, dir, candidate);
      if (existsSync(p)) return p;
    }
  }
  return undefined;
}

async function main() {
  const executablePath = findChromium();
  const browser = await chromium.launch(executablePath ? { executablePath } : {});
  for (const role of Object.keys(ROUTES)) {
    if (!ONLY.includes(role)) continue;
    const ctx = await browser.newContext({ viewport: { width: 1440, height: 960 } });
    const page = await ctx.newPage();
    const problems = [];
    page.on('console', (m) => {
      if (m.type() === 'error') problems.push(`console: ${m.text().slice(0, 150)}`);
    });
    page.on('pageerror', (e) => problems.push(`pageerror: ${String(e).slice(0, 150)}`));
    page.on('response', (r) => {
      if (r.status() >= 400) problems.push(`http ${r.status()} ${r.url().slice(0, 130)}`);
    });

    if (role !== 'guest') {
      await page.goto(`${BASE}/vi/login?portal=${PORTAL[role] ?? 'student'}`, {
        waitUntil: 'domcontentloaded',
      });
      await page.waitForTimeout(1500);
      await page.locator('input[type="email"]').last().fill(ACCOUNTS[role]);
      await page.locator('input[type="password"]').last().fill(PASSWORD);
      const submit = page.locator('form button[type="submit"]').first();
      await (await submit.count()
        ? submit
        : page.locator('button').filter({ hasText: /đăng nhập/i }).last()
      ).click();
      await page
        .waitForURL(/(dashboard|admin)/, { timeout: 20000 })
        .catch(() => {});
      await page.waitForTimeout(3000);
      record({
        step: 'login',
        role,
        verdict: page.url().includes('login') ? 'fail' : 'ok',
        url: page.url(),
      });
    }

    for (const path of ROUTES[role]) {
      await page
        .goto(`${BASE}/vi${path}`, { waitUntil: 'domcontentloaded', timeout: 40000 })
        .catch(() => {});
      await page.waitForTimeout(3200);
      const snap = await snapshot(page).catch(() => null);
      const finalUrl = page.url();
      // Landing on the sign-in page is the failure only for a route the role
      // should reach; `/login` is itself the sign-in page for a guest.
      const bounced = finalUrl.includes('login');
      record({
        step: 'visit',
        role,
        path,
        verdict: bounced && path !== '/login' ? 'fail' : 'ok',
        url: finalUrl,
        heading: snap?.heading,
        rows: rowCount(snap),
        hasSearch: Boolean(snap?.searchInputs?.length),
        emptyState: snap?.emptyState,
        errorState: snap?.errorState,
        text: snap?.text,
        problems: problems.splice(0, 10),
      });
      await shoot(page, `${role}${path}`);
      if (snap?.searchInputs?.length) {
        const probe = await probeSearch(page).catch((e) => ({
          verdict: 'error',
          reason: String(e).slice(0, 120),
        }));
        record({ step: 'search', role, path, ...probe });
      }
    }
    for (const path of DENIED[role] ?? []) {
      await page
        .goto(`${BASE}/vi${path}`, { waitUntil: 'domcontentloaded', timeout: 40000 })
        .catch(() => {});
      await page.waitForTimeout(2500);
      const snap = await snapshot(page).catch(() => null);
      const landed = new URL(page.url()).pathname;
      record({
        step: 'denied',
        role,
        path,
        verdict: landed === `/vi${path}` ? 'fail' : 'ok',
        url: page.url(),
        landedOn: landed,
        heading: snap?.heading,
        errorState: snap?.errorState,
        problems: problems.splice(0, 6),
      });
      await shoot(page, `deny-${role}${path}`);
    }

    await ctx.close();
  }
  await browser.close();
  writeFileSync(join(OUT, 'role-matrix.json'), JSON.stringify(results, null, 2));
  process.stdout.write(`\nSUMMARY failures=${failures} steps=${results.length}\n`);
}

main().catch((e) => {
  process.stderr.write(`FATAL ${e.stack || e}\n`);
  process.exit(1);
});
