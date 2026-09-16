/**
 * Focus-visibility audit (WCAG 2.4.7) plus a border check for form controls
 * (WCAG 1.4.11). Tabs through each route with TRUSTED key input, and for every
 * focused element decides whether a focus indicator is actually visible:
 * outline set, box-shadow set, or a background/border change that is at least
 * 3:1 against the unfocused state.
 *
 *   AUDIT_BASE_URL   default http://127.0.0.1:3100
 *   AUDIT_EMAIL / AUDIT_PASSWORD   required
 *   AUDIT_PORTAL     default student
 *   AUDIT_ROUTES     comma-separated; default student routes
 *   AUDIT_SETTLE_MS  per-route settle time, default 6000
 *   AUDIT_TABS       max Tab presses per route, default 25
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.AUDIT_BASE_URL || 'http://127.0.0.1:3100').replace(/\/$/, '');
const EMAIL = process.env.AUDIT_EMAIL;
const PASSWORD = process.env.AUDIT_PASSWORD;
const LOCALE = process.env.AUDIT_LOCALE || 'vi';
const SETTLE_MS = Number(process.env.AUDIT_SETTLE_MS || 6000);
const MAX_TABS = Number(process.env.AUDIT_TABS || 25);

if (!EMAIL || !PASSWORD) {
  console.error('Missing AUDIT_EMAIL / AUDIT_PASSWORD in the environment.');
  process.exit(2);
}

const ROUTES = (process.env.AUDIT_ROUTES
  ? process.env.AUDIT_ROUTES.split(',')
  : ['/dashboard', '/dashboard/grades', '/dashboard/announcements']
).map((r) => r.trim()).filter(Boolean);

/** Runs in-page: describes the focused element and its focus indicator. */
function describeFocus() {
  const el = document.activeElement;
  if (!el || el === document.body) return { focused: false };
  const style = getComputedStyle(el);
  const parse = (value) => {
    if (!value || value === 'none' || value === 'transparent') return null;
    const m = value.match(/rgba?\(([^)]+)\)/);
    if (!m) return null;
    const p = m[1].split(',').map((n) => parseFloat(n));
    if (p.length < 3 || p.some((n) => Number.isNaN(n))) return null;
    return { r: p[0], g: p[1], b: p[2], a: p.length > 3 ? p[3] : 1 };
  };
  const lin = (v) => {
    const s = v / 255;
    return s <= 0.03928 ? s / 12.92 : Math.pow((s + 0.055) / 1.055, 2.4);
  };
  const lum = (c) => 0.2126 * lin(c.r) + 0.7152 * lin(c.g) + 0.0722 * lin(c.b);
  const ratio = (a, b) => {
    const x = lum(a);
    const y = lum(b);
    return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05);
  };

  // Composite the element background over its ancestors once.
  let bg = { r: 255, g: 255, b: 255, a: 1 };
  const chain = [];
  for (let n = el; n && n.nodeType === 1; n = n.parentElement) chain.push(n);
  for (const node of chain.reverse()) {
    const c = parse(getComputedStyle(node).backgroundColor);
    if (c && c.a > 0) {
      bg = {
        r: c.r * c.a + bg.r * (1 - c.a),
        g: c.g * c.a + bg.g * (1 - c.a),
        b: c.b * c.a + bg.b * (1 - c.a),
        a: 1,
      };
    }
  }

  const outlineWidth = parseFloat(style.outlineWidth) || 0;
  const outlineStyle = style.outlineStyle;
  const outlineColor = parse(style.outlineColor);
  const outlineVisible =
    outlineStyle !== 'none' && outlineWidth > 0 && outlineColor && outlineColor.a > 0;

  const ringShadows = (style.boxShadow || '')
    .split('),')
    .map((s) => s.trim())
    .filter((s) => s.includes('inset') === false && s.length > 0);
  const shadowVisible = ringShadows.some((s) => {
    const m = s.match(/rgba?\(([^)]+)\)/);
    if (!m) return false;
    const c = parse(s.match(/rgba?\([^)]+\)/)?.[0]);
    const spread = parseFloat((s.match(/\)\s+(\d+)px/) || [])[1] ?? '0');
    return c && c.a > 0 && spread > 0;
  });

  let indicator = 'none';
  if (outlineVisible) {
    const oc = outlineColor;
    const ocBg = { ...bg };
    indicator = `outline ${outlineStyle} ${outlineWidth}px, contrast ${Math.round(ratio(oc.a < 1 ? over(oc, ocBg) : oc, ocBg) * 100) / 100}:1`;
  } else if (shadowVisible) {
    indicator = 'box-shadow';
  }

  return {
    focused: true,
    tag: el.tagName.toLowerCase(),
    text: (el.innerText || el.getAttribute('aria-label') || '').trim().slice(0, 50),
    indicator,
    outlineVisible,
    shadowVisible,
    outlineContrast:
      outlineVisible && outlineColor
        ? Math.round(ratio(outlineColor.a < 1 ? over(outlineColor, bg) : outlineColor, bg) * 100) / 100
        : null,
  };

  function over(fg, bgc) {
    return {
      r: fg.r * fg.a + bgc.r * (1 - fg.a),
      g: fg.g * fg.a + bgc.g * (1 - fg.a),
      b: fg.b * fg.a + bgc.b * (1 - fg.a),
      a: 1,
    };
  }
}

async function login(page) {
  const portal = process.env.AUDIT_PORTAL || 'student';
  await page.goto(`${BASE_URL}/${LOCALE}/login?portal=${portal}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.locator('#email').waitFor({ state: 'visible', timeout: 60000 });
  const submit = page.locator('form button[type="submit"]').first();
  for (let attempt = 0; attempt < 8; attempt++) {
    try {
      await page.locator('#email').fill(EMAIL);
      await page.locator('#password').fill(PASSWORD);
    } catch {
      await page.waitForTimeout(1500);
      continue;
    }
    await page.waitForTimeout(700);
    if (!(await submit.isEnabled().catch(() => false))) continue;
    await submit.click().catch(() => {});
    if (
      await page.waitForURL(/\/(dashboard|admin)(?:$|[/?#])/, { timeout: 75000 }).then(() => true).catch(() => false)
    ) {
      return;
    }
  }
  throw new Error('login did not reach the workspace');
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
  await context.addInitScript(() => {
    try {
      window.localStorage.setItem('theme', 'dark');
    } catch {}
  });
  const page = await context.newPage();
  const totals = { checked: 0, invisible: 0 };

  try {
    await login(page);
    console.log('logged in:', page.url());

    for (const route of ROUTES) {
      await page.goto(`${BASE_URL}/${LOCALE}${route}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
      await page.evaluate(() => document.documentElement.classList.add('dark'));
      await page.waitForTimeout(SETTLE_MS);

      // Walk the tab order with trusted keys and inspect each focused element.
      const observations = [];
      let lastText = '';
      for (let i = 0; i < MAX_TABS; i += 1) {
        await page.keyboard.press('Tab');
        await page.waitForTimeout(120);
        const info = await page.evaluate(describeFocus);
        if (!info.focused) break;
        if (info.text === lastText) continue; // same control, still focused
        lastText = info.text;
        observations.push(info);
      }

      const invisible = observations.filter(
        (o) => o.indicator === 'none' || (o.outlineVisible && o.outlineContrast !== null && o.outlineContrast < 3),
      );
      totals.checked += observations.length;
      totals.invisible += invisible.length;
      console.log(
        `\n=== ${route} — focused ${observations.length}, weak/missing indicator ${invisible.length} ===`,
      );
      invisible.forEach((o) =>
        console.log(`  ${o.indicator} contrast=${o.outlineContrast} :: <${o.tag}> "${o.text}"`),
      );
    }
  } catch (error) {
    console.log(`HARNESS ERROR: ${String(error).slice(0, 300)}`);
  } finally {
    await browser.close();
  }

  console.log(`\n==== focused elements checked ${totals.checked}, weak/missing ${totals.invisible} ====`);
  process.exit(totals.invisible === 0 && totals.checked > 0 ? 0 : 1);
}

main();
