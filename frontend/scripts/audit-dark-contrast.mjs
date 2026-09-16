/**
 * Dark-mode contrast audit.
 *
 * Walks the rendered portal in dark mode and measures the WCAG 2.1 contrast
 * ratio of every visible text node against its *composited* background, then
 * reports the nodes below the AA threshold. Measuring instead of eyeballing
 * keeps the result falsifiable and repeatable.
 *
 * Credentials come from the environment on purpose. Never inline them here.
 *
 *   AUDIT_BASE_URL   default http://127.0.0.1:3000
 *   AUDIT_EMAIL      required
 *   AUDIT_PASSWORD   required
 *   AUDIT_LOCALE     default vi
 *   AUDIT_ROUTES     optional comma-separated path list
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.AUDIT_BASE_URL || 'http://127.0.0.1:3000').replace(/\/$/, '');
const EMAIL = process.env.AUDIT_EMAIL;
const PASSWORD = process.env.AUDIT_PASSWORD;
const LOCALE = process.env.AUDIT_LOCALE || 'vi';
// Production cold starts serve shell content for a while; give each route a
// configurable settle budget instead of a fixed sleep.
const SETTLE_MS = Number(process.env.AUDIT_SETTLE_MS || 3500);

if (!EMAIL || !PASSWORD) {
  console.error('Missing AUDIT_EMAIL / AUDIT_PASSWORD in the environment.');
  process.exit(2);
}

const DEFAULT_ROUTES = [
  '/dashboard',
  '/dashboard/grades',
  '/dashboard/transcript',
  '/dashboard/schedule',
  '/dashboard/enrollments',
  '/dashboard/register',
  '/dashboard/announcements',
  '/dashboard/notifications',
  '/dashboard/conduct',
  '/dashboard/profile',
  '/dashboard/thesis',
];

const ROUTES = (process.env.AUDIT_ROUTES
  ? process.env.AUDIT_ROUTES.split(',')
  : DEFAULT_ROUTES
).map((r) => r.trim()).filter(Boolean);

/**
 * Runs inside the page. Returns the failing text nodes only, so the payload
 * stays small enough to print.
 */
function auditInPage() {
  const parse = (value) => {
    if (!value || value === 'transparent') return null;
    const match = value.match(/rgba?\(([^)]+)\)/);
    if (!match) return null;
    const parts = match[1].split(',').map((n) => parseFloat(n));
    if (parts.length < 3 || parts.some((n) => Number.isNaN(n))) return null;
    return { r: parts[0], g: parts[1], b: parts[2], a: parts.length > 3 ? parts[3] : 1 };
  };

  const channel = (v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  const luminance = ({ r, g, b }) => 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
  const contrast = (a, b) => {
    const la = luminance(a);
    const lb = luminance(b);
    const hi = Math.max(la, lb);
    const lo = Math.min(la, lb);
    return (hi + 0.05) / (lo + 0.05);
  };
  const over = (fg, bg) => ({
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1,
  });

  /** Composite the ancestor background chain from the root down to the element. */
  const effectiveBackground = (element) => {
    const chain = [];
    for (let node = element; node && node.nodeType === 1; node = node.parentElement) chain.push(node);
    chain.reverse();
    let result = { r: 255, g: 255, b: 255, a: 1 };
    for (const node of chain) {
      const bg = parse(getComputedStyle(node).backgroundColor);
      if (bg && bg.a > 0) result = over(bg, result);
    }
    return result;
  };

  const describe = (element) => {
    const parts = [];
    for (let node = element; node && node.nodeType === 1 && parts.length < 5; node = node.parentElement) {
      let part = node.tagName.toLowerCase();
      if (node.id) part += '#' + node.id;
      if (node.className && typeof node.className === 'string') {
        part += '.' + node.className.trim().split(/\s+/).slice(0, 3).join('.');
      }
      parts.unshift(part);
    }
    return parts.join(' > ');
  };

  const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'SVG', 'PATH', 'TEMPLATE']);
  const failures = [];
  let checked = 0;

  const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const text = (node.textContent || '').trim();
    if (text.length < 2) continue;
    const element = node.parentElement;
    if (!element || SKIP_TAGS.has(element.tagName)) continue;

    const style = getComputedStyle(element);
    if (style.visibility === 'hidden' || style.display === 'none' || parseFloat(style.opacity) < 0.1) continue;
    // offsetParent is null when the element or an ancestor is display:none, so
    // this skips collapsed panels without needing scroll position.
    if (element.offsetParent === null && style.position !== 'fixed') continue;
    if (element.closest('[aria-hidden="true"]')) continue;

    const rect = element.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) continue;

    const color = parse(style.color);
    if (!color) continue;
    const bg = effectiveBackground(element);
    const composited = color.a < 1 ? over(color, bg) : color;
    const ratio = contrast(composited, bg);

    const fontSize = parseFloat(style.fontSize);
    const weight = parseInt(style.fontWeight, 10) || 400;
    const isLarge = fontSize >= 24 || (fontSize >= 18.66 && weight >= 700);
    const required = isLarge ? 3 : 4.5;

    checked++;
    if (ratio + 0.01 < required) {
      failures.push({
        text: text.slice(0, 70),
        selector: describe(element),
        ratio: Math.round(ratio * 100) / 100,
        required,
        color: 'rgb(' + [color.r, color.g, color.b].map((n) => Math.round(n)).join(', ') + ')',
        bg: 'rgb(' + [bg.r, bg.g, bg.b].map((n) => Math.round(n)).join(', ') + ')',
        fontSize,
        weight,
      });
    }
  }
  return { checked, failures };
}

/**
 * The submit button ships `disabled` and only enables once React has hydrated
 * and the fields hold state. Clicking the instant it enables can still race the
 * re-render: the click is accepted but no login request is sent. So each
 * attempt waits for the actual `auth/login` response and retries otherwise.
 */
async function login(page) {
  const portal = process.env.AUDIT_PORTAL || 'student';
  await page.goto(`${BASE_URL}/${LOCALE}/login?portal=${portal}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.locator('#email').waitFor({ state: 'visible', timeout: 60000 });
  const submit = page.locator('form button[type="submit"]').first();

  for (let attempt = 0; attempt < 6; attempt++) {
    try {
      await page.locator('#email').fill(EMAIL);
      await page.locator('#password').fill(PASSWORD);
    } catch {
      // A re-render replaced the inputs mid-fill (hydration); retry.
      await page.waitForTimeout(1500);
      continue;
    }
    await page.waitForTimeout(700);
    if (!(await submit.isEnabled().catch(() => false))) continue;

    const response = await Promise.race([
      page.waitForResponse((r) => /auth\/login/.test(r.url()), { timeout: 20000 }).catch(() => null),
      submit.click().then(() => null),
    ]);
    if (response) {
      console.log(`login POST -> ${response.status()} (attempt ${attempt})`);
    } else {
      console.log(`attempt ${attempt}: click produced no login request, retrying`);
    }
    // Students land on /dashboard; admins land on /admin.
    if (
      await page
        .waitForURL(/\/(dashboard|admin)(?:$|[/?#])/, { timeout: 30000 })
        .then(() => true)
        .catch(() => false)
    ) {
      return;
    }
  }
  console.log(`login failed on ${BASE_URL}/${LOCALE}/login?portal=${process.env.AUDIT_PORTAL || 'student'} — page text head:`);
  try {
    console.log((await page.evaluate(() => document.body.innerText)).replace(/\s+/g, ' ').slice(0, 400));
  } catch {}
  throw new Error('login did not reach the dashboard');
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
  // Seed the stored theme so every navigation starts in dark mode.
  await context.addInitScript(() => {
    try {
      window.localStorage.setItem('theme', 'dark');
    } catch {
      /* storage unavailable — the explicit toggle below still applies */
    }
  });
  const page = await context.newPage();

  const totals = { checked: 0, failures: 0, themeFailures: 0, sparseRoutes: [] };
  try {
    await login(page);
    console.log(`logged in: ${page.url()}`);

    for (const route of ROUTES) {
      await page.goto(`${BASE_URL}/${LOCALE}${route}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
      // Force the class in case the provider re-applied a stored light theme.
      await page.evaluate(() => document.documentElement.classList.add('dark'));
      // Scroll the whole page so lazy sections mount before measuring.
      await page.evaluate(async () => {
        const step = window.innerHeight;
        for (let y = 0; y < document.body.scrollHeight; y += step) {
          window.scrollTo(0, y);
          await new Promise((r) => setTimeout(r, 220));
        }
        window.scrollTo(0, 0);
      });
      await page.waitForTimeout(SETTLE_MS);
      // Pages whose content sits behind a selector render almost nothing until
      // a choice is made; pick the first option so the audit has real content.
      const sparseText = await page.evaluate(() => document.body.innerText.trim().length < 400);
      if (sparseText) {
        const picker = page.locator('select').first();
        if (await picker.count()) {
          const values = await picker.evaluate((el) => [...el.options].map((o) => o.value).filter(Boolean));
          if (values.length) {
            await picker.selectOption(values[0]);
            await page.waitForTimeout(4000);
          }
        }
      }
      // Guard against measuring the light theme: a passing light-theme run
      // would otherwise read as "dark mode is fine".
      const themeState = await page.evaluate(() => {
        const root = document.documentElement;
        return {
          dark: root.classList.contains('dark'),
          dataTheme: root.dataset.theme || null,
          bodyBg: getComputedStyle(document.body).backgroundColor,
          colorScheme: getComputedStyle(root).colorScheme,
        };
      });
      const result = await page.evaluate(auditInPage);
      totals.checked += result.checked;
      totals.failures += result.failures.length;
      if (!themeState.dark) totals.themeFailures++;
      // A 404 or login bounce renders almost nothing and would otherwise be
      // recorded as "no contrast problems" — a silent false pass.
      const sparse = result.checked < 30;
      if (sparse) totals.sparseRoutes.push(`${route} (${result.checked} nodes)`);
      console.log(
        `\n=== ${route} — checked ${result.checked}, below AA ${result.failures.length} ` +
          `| dark=${themeState.dark} scheme=${themeState.colorScheme} bodyBg=${themeState.bodyBg}` +
          `${sparse ? ' | UNDER-COVERED' : ''} ===`,
      );
      result.failures
        .sort((a, b) => a.ratio - b.ratio)
        .slice(0, 25)
        .forEach((f) =>
          console.log(
            `  ${String(f.ratio).padEnd(6)} (need ${f.required}) ${f.fontSize}px/${f.weight} ` +
              `fg=${f.color} bg=${f.bg} :: "${f.text}" @ ${f.selector}`,
          ),
        );
      if (result.failures.length > 25) {
        console.log(`  ... and ${result.failures.length - 25} more`);
      }
    }
  } catch (error) {
    console.log(`AUDIT ERROR: ${String(error).slice(0, 300)}`);
    try {
      console.log(`url at failure: ${page.url()}`);
      const text = await page.evaluate(() => document.body.innerText);
      console.log('--- page text ---\n' + text.slice(0, 1200));
      const alerts = await page.evaluate(() =>
        [...document.querySelectorAll('[role="alert"], .text-destructive, [data-sonner-toast]')]
          .map((n) => n.innerText)
          .slice(0, 10),
      );
      console.log('alerts: ' + JSON.stringify(alerts));
    } catch (inner) {
      console.log(`diagnostics unavailable: ${String(inner).slice(0, 200)}`);
    }
  } finally {
    await browser.close();
  }
  console.log(
    `\n==== total checked ${totals.checked}, below AA ${totals.failures}, ` +
      `routes not in dark mode ${totals.themeFailures} ====`,
  );
  if (totals.sparseRoutes.length) {
    console.log(`under-covered routes (not evidence of a pass): ${totals.sparseRoutes.join(', ')}`);
  }
  process.exit(totals.failures === 0 && totals.themeFailures === 0 && totals.sparseRoutes.length === 0 ? 0 : 1);
}

main();
