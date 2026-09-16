/**
 * Verifies keyboard reordering of announcements with TRUSTED key input.
 *
 * Playwright's keyboard.press injects through the Chrome DevTools Protocol,
 * so Chrome treats these as real user input — unlike page.evaluate-dispatched
 * KeyboardEvents, which are untrusted synthetic events. This closes the earlier
 * gap where reordering was only proven with dispatched events.
 *
 * Reverts the move at the end. Run against the local stack.
 *
 *   VERIFY_BASE_URL            default http://127.0.0.1:3100
 *   ADMIN_EMAIL / ADMIN_PASSWORD               required
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.VERIFY_BASE_URL || 'http://127.0.0.1:3100').replace(/\/$/, '');
const EMAIL = process.env.ADMIN_EMAIL;
const PASSWORD = process.env.ADMIN_PASSWORD;

if (!EMAIL || !PASSWORD) {
  console.error('Missing ADMIN_EMAIL / ADMIN_PASSWORD in the environment.');
  process.exit(2);
}

const results = [];
const record = (name, ok, detail) => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

async function login(page) {
  await page.goto(`${BASE_URL}/vi/login?portal=admin`, { waitUntil: 'domcontentloaded', timeout: 120000 });
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
  throw new Error('admin login did not reach the workspace');
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
  const page = await context.newPage();

  try {
    await login(page);
    record('admin login', true, page.url());

    await page.goto(`${BASE_URL}/vi/admin/announcements`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(9000);

    // The reorder list lives inside a modal opened by this toggle.
    await page.getByRole('button', { name: /Sắp xếp thứ tự ghim|Reorder feed/ }).first().click();
    const handle = page.getByRole('button', { name: /Kéo hoặc dùng phím mũi tên/ }).first();
    await handle.waitFor({ state: 'visible', timeout: 45000 });

    const readOrder = () =>
      page.evaluate(() => {
        const badge = [...document.querySelectorAll('span')].filter(
          (s) => /^\d{2}$/.test(s.textContent.trim()) && s.closest('li, div')?.textContent.includes(''),
        );
        // Titles sit next to the numbered badges inside each row.
        const rows = [...document.querySelectorAll('li')].filter((li) => li.querySelector('p'));
        return rows.slice(0, 3).map((li) => li.querySelector('p')?.textContent.trim().slice(0, 40));
      });

    const before = await readOrder();
    record('reorder list has at least two rows', (before?.length ?? 0) >= 2, JSON.stringify(before));

    await handle.click(); // focus the handle without dragging
    await page.keyboard.press('ArrowDown');
    await page.waitForTimeout(1200);

    const afterDown = await readOrder();
    const moved = JSON.stringify(afterDown) !== JSON.stringify(before);
    record('trusted ArrowDown reorders the list', moved, `before=${JSON.stringify(before)} after=${JSON.stringify(afterDown)}`);

    // Restore the original order.
    await page.keyboard.press('ArrowUp');
    await page.waitForTimeout(1200);
    const restored = await readOrder();
    record(
      'ArrowUp restores the original order (reverted)',
      JSON.stringify(restored) === JSON.stringify(before),
      `restored=${JSON.stringify(restored)}`,
    );
  } catch (error) {
    record('harness completed without throwing', false, String(error).slice(0, 300));
  } finally {
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n==== ${results.length - failed.length}/${results.length} checks passed ====`);
  process.exit(failed.length === 0 ? 0 : 1);
}

main();
