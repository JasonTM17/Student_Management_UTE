/**
 * One-off ground-truth capture: screenshot the weak focus indicators flagged by
 * audit-focus-indicators so fixes can be judged visually, not just numerically.
 * Writes PNGs to %TEMP%/focus-shots/. Credentials from env: AUDIT_EMAIL/PASSWORD.
 */
import { chromium } from '@playwright/test';
import { mkdirSync } from 'node:fs';
import { join } from 'node:path';

const BASE_URL = (process.env.AUDIT_BASE_URL || 'http://127.0.0.1:3100').replace(/\/$/, '');
const EMAIL = process.env.AUDIT_EMAIL;
const PASSWORD = process.env.AUDIT_PASSWORD;
const OUT_DIR = join(process.env.TEMP || '/tmp', 'focus-shots');
mkdirSync(OUT_DIR, { recursive: true });

// Match by visible text instead of tab index (indices shift after reload).
const TARGET_TEXTS = [
  { needle: 'Trợ lý học vụ CampusCore', label: 'assistant-launcher-header' },
  { needle: 'VI', label: 'lang-vi', exact: true },
  { needle: 'Chuyển sang giao diện sáng', label: 'theme-toggle' },
  { needle: 'Bật tắt bảng thông báo', label: 'notif-bell' },
  { needle: 'Môn học kỳ này', label: 'metric-card' },
  { needle: 'Học kỳ hiện tại', label: 'semester-chip' },
];

const browser = await chromium.launch({ channel: 'chrome', headless: true });
const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
const page = await context.newPage();

await page.goto(`${BASE_URL}/vi/login?portal=student`, { waitUntil: 'domcontentloaded' });
const submit = page.locator('form button[type="submit"]').first();
for (let attempt = 0; attempt < 8; attempt++) {
  await page.locator('#email').fill(EMAIL);
  await page.locator('#password').fill(PASSWORD);
  await page.waitForTimeout(700);
  if (!(await submit.isEnabled().catch(() => false))) continue;
  await submit.click().catch(() => {});
  if (await page.waitForURL(/\/dashboard(?:$|[/?#])/, { timeout: 60000 }).then(() => true).catch(() => false)) break;
}

await page.goto(`${BASE_URL}/vi/dashboard`, { waitUntil: 'domcontentloaded' });
await page.evaluate(() => document.documentElement.classList.add('dark'));
await page.waitForTimeout(5000);

const pending = new Map(TARGET_TEXTS.map((t) => [t.label, t]));
for (let i = 0; i < 32 && pending.size > 0; i += 1) {
  await page.keyboard.press('Tab');
  await page.waitForTimeout(60);
  const info = await page.evaluate(() => {
    const el = document.activeElement;
    if (!el || el === document.body) return null;
    const r = el.getBoundingClientRect();
    const tag = el.tagName.toLowerCase();
    return {
      x: r.x, y: r.y, w: r.width, h: r.height,
      text: (el.innerText || el.getAttribute('aria-label') || '').trim(),
      tag,
    };
  });
  if (!info || info.w === 0) continue;
  for (const target of [...pending.values()]) {
    const hit = target.exact ? info.text === target.needle : info.text.includes(target.needle);
    if (!hit) continue;
    pending.delete(target.label);
    const pad = 40;
    await page.screenshot({
      path: join(OUT_DIR, `${target.label}.png`),
      clip: {
        x: Math.max(0, info.x - pad),
        y: Math.max(0, info.y - pad),
        width: Math.min(1440, info.w + pad * 2),
        height: Math.min(1000, info.h + pad * 2),
      },
    });
    console.log(`${target.label}: captured (${info.tag}) "${info.text.slice(0, 50).replace(/\n/g, ' ')}"`);
  }
}

await browser.close();
console.log('done →', OUT_DIR);
