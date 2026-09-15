/**
 * Verifies the two behaviour-change items from the ltweb report:
 *   1. the registration catalog stays behind the search box,
 *   3. the 10-point average has its own chart on its own axis.
 *
 *   VERIFY_BASE_URL   default http://127.0.0.1:3210
 *   VERIFY_EMAIL / VERIFY_PASSWORD  required
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.VERIFY_BASE_URL || 'http://127.0.0.1:3210').replace(/\/$/, '');
const EMAIL = process.env.VERIFY_EMAIL;
const PASSWORD = process.env.VERIFY_PASSWORD;

if (!EMAIL || !PASSWORD) {
  console.error('Missing VERIFY_EMAIL / VERIFY_PASSWORD in the environment.');
  process.exit(2);
}

const results = [];
const record = (name, ok, detail) => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

async function login(page) {
  await page.goto(`${BASE_URL}/vi/login?portal=student`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.locator('#email').waitFor({ state: 'visible', timeout: 60000 });
  const submit = page.locator('form button[type="submit"]').first();
  for (let attempt = 0; attempt < 8; attempt++) {
    await page.locator('#email').fill(EMAIL);
    await page.locator('#password').fill(PASSWORD);
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
  const page = await context.newPage();

  try {
    await login(page);
    record('login', true, page.url());

    // --- Item 1: no catalog until the student searches ---
    await page.goto(`${BASE_URL}/vi/dashboard/register`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    // Wait for the page to settle into either state before reading: on a cold
    // production start the catalog area is still mounting after a fixed sleep.
    await page
      .waitForFunction(
        () => {
          const text = document.body.innerText;
          const hasPrompt = /Tìm kiếm để xem lớp học phần/.test(text);
          const hasRegister = [...document.querySelectorAll('button')].some((b) =>
            /^(Đăng ký|Register)$/.test(b.innerText.trim()),
          );
          return hasPrompt || hasRegister;
        },
        undefined,
        { timeout: 60000 },
      )
      .catch(() => {});
    await page.waitForTimeout(1500);
    const emptyState = await page.evaluate(() => document.body.innerText);
    // The registered-course rail legitimately shows course codes, so count the
    // catalog's own register actions instead: those only exist per listed section.
    const catalogCounts = () =>
      page.evaluate(() => {
        const buttons = [...document.querySelectorAll('button')];
        const registerActions = buttons.filter((b) => /^(Đăng ký|Register)$/.test(b.innerText.trim())).length;
        return { registerActions };
      });
    const before = await catalogCounts();
    const promptShown = /Tìm kiếm để xem lớp học phần/.test(emptyState);
    record('Item 1 — catalog hidden until a search', promptShown && before.registerActions === 0,
      `prompt=${promptShown} registerActionsBeforeSearch=${before.registerActions}`);

    const codeInput = page.locator('input[placeholder*="SE101"], input[placeholder*="Mã"]').first();
    await codeInput.fill('SE');
    await page.waitForTimeout(4000);
    const after = await catalogCounts();
    record('Item 1 — searching reveals the catalog', after.registerActions > 0,
      `registerActionsAfterSearch=${after.registerActions}`);

    // --- Item 3: the 10-point average is its own chart ---
    await page.goto(`${BASE_URL}/vi/dashboard/transcript`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(8000);
    const charts = await page.evaluate(() => {
      const svgs = [...document.querySelectorAll('svg[role="img"]')];
      return svgs.map((svg) => ({
        label: svg.getAttribute('aria-label') || '',
        // The 10-point axis is the only one that labels 0/2/4/6/8/10.
        tenAxis: ['10', '8', '6', '4', '2', '0'].every((v) =>
          [...svg.querySelectorAll('text')].some((t) => t.textContent.trim() === v),
        ),
        fourAxis: ['4', '3', '2', '1', '0'].every((v) =>
          [...svg.querySelectorAll('text')].some((t) => t.textContent.trim() === v),
        ),
        dashedTenScale: [...svg.querySelectorAll('polyline')].some(
          (p) => p.getAttribute('stroke-dasharray') === '5 3',
        ),
      }));
    });
    const tenChart = charts.find((c) => c.tenAxis);
    const gpaChart = charts.find((c) => c.fourAxis);
    record('Item 3 — 10-scale has its own chart with a 0-10 axis', Boolean(tenChart),
      `charts=${charts.length} labels=${JSON.stringify(charts.map((c) => c.label.slice(0, 40)))}`);
    record('Item 3 — the 4.0 chart no longer draws the 10-scale line',
      Boolean(gpaChart) && !gpaChart.dashedTenScale,
      `fourAxisChart=${Boolean(gpaChart)} dashedTenScale=${gpaChart?.dashedTenScale}`);
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
