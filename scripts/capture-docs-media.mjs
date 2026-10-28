import { mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

// A fresh browser per role keeps session cookies and private query caches apart.
const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const base = process.env.DOCS_CAPTURE_BASE_URL;
const password = process.env.DOCS_CAPTURE_DEMO_PASSWORD;
if (!base || !password) throw new Error('Set DOCS_CAPTURE_BASE_URL and DOCS_CAPTURE_DEMO_PASSWORD for a disposable demo runtime.');
const output = path.resolve(root, process.env.DOCS_CAPTURE_OUTPUT || 'docs/assets/showcase');
const { chromium } = await import(pathToFileURL(path.join(root, 'frontend/node_modules/playwright/index.mjs')));
const viewport = { width: 1440, height: 1000 };
const locales = ['vi', 'en'];
const roles = [
  { name: 'student', email: 'student@campuscore.edu', portal: 'student', routes: [
    ['dashboard', '/dashboard'], ['registration', '/dashboard/register'],
    ['enrollments', '/dashboard/enrollments'], ['schedule', '/dashboard/schedule'],
    ['grades', '/dashboard/grades'], ['transcript', '/dashboard/transcript'],
    ['curriculum', '/dashboard/curriculum'], ['attendance', '/dashboard/attendance'],
    ['conduct', '/dashboard/conduct'], ['thesis', '/dashboard/thesis'],
    ['assistant', '/dashboard/assistant-specialized'], ['notifications', '/dashboard/notifications'],
  ] },
  { name: 'lecturer', email: 'lecturer@campuscore.edu', portal: 'lecturer', routes: [
    ['dashboard', '/dashboard/lecturer'], ['schedule', '/dashboard/lecturer/schedule'],
    ['grades', '/dashboard/lecturer/grades'], ['attendance', '/dashboard/lecturer/attendance'],
    ['thesis', '/dashboard/lecturer/thesis'], ['mail', '/dashboard/lecturer/mail'],
    ['announcements', '/dashboard/lecturer/announcements'],
  ] },
  { name: 'admin', email: 'admin@campuscore.edu', portal: 'admin', routes: [
    ['dashboard', '/admin'], ['users', '/admin/users'], ['sections', '/admin/sections'],
    ['thesis', '/admin/thesis'], ['knowledge', '/admin/assistant-knowledge'],
    ['curriculum-defaults', '/admin/registration/curriculum-defaults'],
    ['appeals', '/admin/grade-appeals'], ['announcements', '/admin/announcements'],
    ['audit', '/admin/audit'],
  ] },
  { name: 'dean', email: 'truongkhoa@campuscore.demo', portal: 'dean', routes: [
    ['dashboard', '/dashboard/dean'], ['thesis', '/admin/thesis'],
  ] },
];
const selected = process.env.DOCS_CAPTURE_ROLES?.split(',');
const selectedShots = process.env.DOCS_CAPTURE_SHOTS?.split(',');
const records = [];
const browser = await chromium.launch({ headless: true });
await mkdir(output, { recursive: true });

async function settle(page) {
  await page.locator('body').waitFor();
  await page.evaluate(() => document.fonts.ready);
  await page.waitForLoadState('networkidle', { timeout: 20_000 }).catch(() => {});
  await page.waitForFunction(() => !document.querySelector('[aria-busy="true"]'), { timeout: 20_000 }).catch(() => {});
  // Let hydrated charts finish their first paint; reduced motion is requested.
  await page.waitForTimeout(800);
}

async function capture(page, locale, role, name, route) {
  const errors = [];
  const onResponse = response => {
    if (response.url().startsWith(base) && response.status() >= 400)
      errors.push({ path: new URL(response.url()).pathname, status: response.status() });
  };
  page.on('response', onResponse);
  try {
    const response = await page.goto(new URL(`/${locale}${route}`, base).href, { waitUntil: 'domcontentloaded' });
    await settle(page);
    if (!response?.ok()) throw new Error(`Document status ${response?.status()}`);
    if (role !== 'public' && /\/login(?:\?|$)/.test(page.url())) throw new Error('Session redirected to login');
    if (errors.some(error => error.status >= 500)) throw new Error(`Server error on ${errors.find(error => error.status >= 500).path}`);
    const file = `${role}-${name}-${locale}.png`;
    await page.screenshot({ path: path.join(output, file), fullPage: false });
    const record = { role, locale, route, file, status: response.status(), apiErrors: errors };
    records.push(record);
    console.log(`CAPTURE ${file} ${errors.length ? `(${errors.length} non-success requests recorded)` : ''}`);
    return record;
  } finally { page.off('response', onResponse); }
}

async function login(page, context, role) {
  await page.goto(`${base}/vi/login?portal=${role.portal}`, { waitUntil: 'domcontentloaded' });
  await settle(page);
  await page.locator('input[type="email"]').fill(role.email);
  await page.locator('input[type="password"]').fill(password);
  const responsePromise = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST');
  await page.locator('button[type="submit"]').click();
  let response = await responsePromise;
  if (response.status() === 429) {
    const retry = Number(response.headers()['retry-after']);
    if (!Number.isFinite(retry) || retry < 0 || retry > 65) throw new Error('Invalid or excessive login Retry-After');
    await page.waitForTimeout((retry + 1) * 1000);
    const retryResponse = page.waitForResponse(r => r.url().endsWith('/api/v1/auth/login') && r.request().method() === 'POST');
    await page.locator('button[type="submit"]').click();
    response = await retryResponse;
  }
  if (!response.ok()) throw new Error(`Login failed for ${role.name}: HTTP ${response.status()}`);
  await page.waitForURL(url => !url.pathname.endsWith('/login'), { timeout: 30_000 });
  const identity = await context.request.get(`${base}/api/v1/auth/me`);
  if (!identity.ok()) throw new Error(`Authenticated identity probe failed: ${identity.status()}`);
  const body = await identity.json();
  const user = body.data || body;
  records.push({ role: role.name, probe: '/api/v1/auth/me', status: identity.status(), roles: user.roles || [user.role], identityMatches: user.email === role.email });
  if (user.email !== role.email) throw new Error('Authenticated identity differs from capture account');
}

try {
  if (!selected || selected.includes('public')) {
    const context = await browser.newContext({ viewport, deviceScaleFactor: 1, reducedMotion: 'reduce', colorScheme: 'light' });
    const page = await context.newPage();
    for (const locale of locales) {
      await capture(page, locale, 'public', 'home', '');
      await capture(page, locale, 'public', 'login', '/login');
      await capture(page, locale, 'public', 'recovery', '/forgot-password');
    }
    await context.close();
  }
  for (const role of roles.filter(role => !selected || selected.includes(role.name))) {
    const context = await browser.newContext({ viewport, deviceScaleFactor: 1, reducedMotion: 'reduce', colorScheme: 'light' });
    const page = await context.newPage();
    await login(page, context, role);
    for (const locale of locales) {
      for (const [name, route] of role.routes.filter(([name]) => !selectedShots || selectedShots.includes(name))) {
        await capture(page, locale, role.name, name, route);
        if (role.name === 'student' && name === 'assistant') {
          const composer = page.locator('textarea').first();
          await composer.fill(locale === 'vi' ? 'Thiết kế REST API trong dự án được tổ chức như thế nào?' : 'How is REST API design organized in this project?');
          await composer.press('Enter');
          await page.waitForFunction(() => {
            const box = document.querySelector('textarea');
            return box && !box.disabled && document.querySelectorAll('[role="article"]').length >= 2;
          }, { timeout: 40_000 });
          await page.screenshot({ path: path.join(output, `student-assistant-answer-${locale}.png`), fullPage: false });
          records.push({ role: role.name, locale, route, file: `student-assistant-answer-${locale}.png`, interaction: 'Submitted actual academic question; waited for stream completion.' });
        }
      }
    }
    await context.close();
  }
} finally {
  await writeFile(path.join(output, `capture${selected ? `-${selected.join('-')}` : ''}${selectedShots ? `-${selectedShots.join('-')}` : ''}.json`), JSON.stringify({
    capturedAt: new Date().toISOString(), baseUrl: base, viewport, records,
    note: 'Disposable local fixtures. Real browser rendering. No external SMTP/LLM acceptance claim.',
  }, null, 2) + '\n');
  await browser.close();
}
