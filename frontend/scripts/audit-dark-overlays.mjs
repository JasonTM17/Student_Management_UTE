/**
 * Dark-mode contrast check for overlay surfaces, which the route sweep cannot
 * reach: the assistant panel, the announcement reader, and the grade modal.
 * Read-only: it opens dialogs and measures, never saves.
 *
 *   OVERLAY_BASE_URL   default https://www.campusute.io.vn
 *   OVERLAY_EMAIL / OVERLAY_PASSWORD  required
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.OVERLAY_BASE_URL || 'https://www.campusute.io.vn').replace(/\/$/, '');
const EMAIL = process.env.OVERLAY_EMAIL;
const PASSWORD = process.env.OVERLAY_PASSWORD;

if (!EMAIL || !PASSWORD) {
  console.error('Missing OVERLAY_EMAIL / OVERLAY_PASSWORD in the environment.');
  process.exit(2);
}

/** Same measurement core as the route audit, scoped to one subtree. */
function auditScope(selector) {
  const parse = (value) => {
    if (!value || value === 'transparent') return null;
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
  const over = (fg, bg) => ({
    r: fg.r * fg.a + bg.r * (1 - fg.a),
    g: fg.g * fg.a + bg.g * (1 - fg.a),
    b: fg.b * fg.a + bg.b * (1 - fg.a),
    a: 1,
  });

  const root = document.querySelector(selector);
  if (!root) return { checked: 0, failures: [], missing: true };
  const failures = [];
  let checked = 0;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let node;
  while ((node = walker.nextNode())) {
    const text = (node.textContent || '').trim();
    if (text.length < 2) continue;
    const el = node.parentElement;
    if (!el) continue;
    const style = getComputedStyle(el);
    if (style.visibility === 'hidden' || style.display === 'none') continue;
    if (el.offsetParent === null && style.position !== 'fixed') continue;
    const rect = el.getBoundingClientRect();
    if (rect.width < 2 || rect.height < 2) continue;

    // Composite the ancestor chain inside the overlay only.
    const chain = [];
    for (let n = el; n && n !== root.parentElement; n = n.parentElement) chain.push(n);
    chain.reverse();
    let bg = { r: 255, g: 255, b: 255, a: 1 };
    for (const anc of chain) {
      const c = parse(getComputedStyle(anc).backgroundColor);
      if (c && c.a > 0) bg = over(c, bg);
    }
    const fg = parse(style.color);
    if (!fg) continue;
    const composited = fg.a < 1 ? over(fg, bg) : fg;
    const la = lum(composited);
    const lb = lum(bg);
    const ratio = (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
    const fontSize = parseFloat(style.fontSize);
    const weight = parseInt(style.fontWeight, 10) || 400;
    const required = fontSize >= 24 || (fontSize >= 18.66 && weight >= 700) ? 3 : 4.5;
    checked++;
    if (ratio + 0.01 < required) {
      failures.push({
        text: text.slice(0, 60),
        ratio: Math.round(ratio * 100) / 100,
        required,
        color: style.color,
        bg: `rgb(${[bg.r, bg.g, bg.b].map((n) => Math.round(n)).join(', ')})`,
      });
    }
  }
  return { checked, failures, missing: false };
}

async function login(page) {
  await page.goto(`${BASE_URL}/vi/login?portal=student`, { waitUntil: 'domcontentloaded', timeout: 120000 });
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
    if (await page.waitForURL(/\/dashboard(?:$|[/?#])/, { timeout: 75000 }).then(() => true).catch(() => false)) return;
  }
  throw new Error('login did not reach the dashboard');
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
  const report = [];

  const audit = async (label, selector) => {
    const r = await page.evaluate(auditScope, selector);
    report.push({ label, ...r });
    console.log(
      `${r.missing ? 'SKIP' : r.failures.length === 0 ? 'PASS' : 'FAIL'}  ${label} — checked ${r.checked}` +
        (r.failures.length ? `, below AA ${r.failures.length}` : ''),
    );
    r.failures.slice(0, 8).forEach((f) =>
      console.log(`   ${f.ratio} (need ${f.required}) fg=${f.color} bg=${f.bg} :: "${f.text}"`),
    );
  };

  try {
    await login(page);
    console.log('logged in:', page.url());

    // --- Assistant panel ---
    await page.goto(`${BASE_URL}/vi/dashboard`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(6000);
    const launcher = page.getByRole('button', { name: /CampusCore|trợ lý|assistant/i }).last();
    if (await launcher.isVisible().catch(() => false)) {
      await launcher.click();
      await page.waitForTimeout(4000);
      const hasDialog = await page.evaluate(() => Boolean(document.querySelector('[role="dialog"]')));
      if (hasDialog) {
        await audit('assistant panel', '[role="dialog"]');
      } else {
        console.log('SKIP  assistant panel — dialog did not open');
      }
      await page.keyboard.press('Escape').catch(() => {});
    } else {
      console.log('SKIP  assistant panel — launcher not found');
    }

    // --- Announcement reader ---
    await page.goto(`${BASE_URL}/vi/dashboard/announcements`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(9000);
    // Announcement cards are the reader trigger.
    const reader = page.locator('article').first();
    if (await reader.isVisible().catch(() => false)) {
      await reader.click();
      await page.waitForTimeout(3500);
      const hasDialog = await page.evaluate(() => Boolean(document.querySelector('[role="dialog"]')));
      if (hasDialog) await audit('announcement reader', '[role="dialog"]');
      else console.log('SKIP  announcement reader — dialog did not open');
      await page.keyboard.press('Escape').catch(() => {});
    } else {
      console.log('SKIP  announcement reader — no card visible');
    }

    // --- Grade modal ---
    await page.goto(`${BASE_URL}/vi/dashboard/grades`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(4000);
    const row = page.locator('table tbody button').first();
    await row.waitFor({ state: 'visible', timeout: 45000 }).catch(() => {});
    if (await row.isVisible().catch(() => false)) {
      await row.click();
      const dialog = page.locator('[role="dialog"]').first();
      await dialog.waitFor({ state: 'visible', timeout: 30000 });
      await page.waitForTimeout(2500);
      await audit('grade modal', '[role="dialog"]');
    } else {
      console.log('SKIP  grade modal — no graded rows');
    }
  } catch (error) {
    console.log(`HARNESS ERROR: ${String(error).slice(0, 300)}`);
  } finally {
    await browser.close();
  }

  const measured = report.filter((r) => !r.missing);
  const failing = measured.filter((r) => r.failures.length > 0);
  console.log(
    `\n==== overlays measured ${measured.length}/${report.length}, failing ${failing.length} ====`,
  );
  process.exit(failing.length === 0 && measured.length > 0 ? 0 : 1);
}

main();
