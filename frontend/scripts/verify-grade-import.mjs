/**
 * Browser check for feedback item 9: importing a grade sheet fills the
 * lecturer's grade table for review. Nothing is saved, so the backend keeps
 * its data.
 *
 *   VERIFY_BASE_URL   default http://127.0.0.1:3210
 *   VERIFY_LECTURER_EMAIL / VERIFY_LECTURER_PASSWORD  required
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.VERIFY_BASE_URL || 'http://127.0.0.1:3210').replace(/\/$/, '');
const EMAIL = process.env.VERIFY_LECTURER_EMAIL;
const PASSWORD = process.env.VERIFY_LECTURER_PASSWORD;

if (!EMAIL || !PASSWORD) {
  console.error('Missing VERIFY_LECTURER_EMAIL / VERIFY_LECTURER_PASSWORD in the environment.');
  process.exit(2);
}

const results = [];
const record = (name, ok, detail) => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

async function login(page) {
  await page.goto(`${BASE_URL}/vi/login?portal=lecturer`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.locator('#email').waitFor({ state: 'visible', timeout: 60000 });
  const submit = page.locator('form button[type="submit"]').first();
  for (let attempt = 0; attempt < 8; attempt++) {
    await page.locator('#email').fill(EMAIL);
    await page.locator('#password').fill(PASSWORD);
    await page.waitForTimeout(700);
    if (!(await submit.isEnabled().catch(() => false))) continue;
    await submit.click().catch(() => {});
    if (await page.waitForURL(/\/dashboard(?:$|[/?#])/, { timeout: 60000 }).then(() => true).catch(() => false)) return;
  }
  throw new Error('lecturer login did not reach the dashboard');
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
  const page = await context.newPage();

  try {
    await login(page);
    record('lecturer login', true, page.url());

    // Open a section that still needs grading from the lecturer grade list.
    await page.goto(`${BASE_URL}/vi/dashboard/lecturer/grades`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(4000);
    const sectionLinks = await page.evaluate(() =>
      [...document.querySelectorAll('a[href*="/dashboard/lecturer/grades/"]')].map((a) => a.getAttribute('href')),
    );
    record('found section links', sectionLinks.length > 0, `count=${sectionLinks.length}`);

    let studentCode = null;
    let openedHref = null;
    for (const href of sectionLinks.slice(0, 6)) {
      await page.goto(`${BASE_URL}${href}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
      await page.waitForTimeout(3500);
      studentCode = await page.evaluate(() => {
        const match = document.body.innerText.match(/\bCS-DEMO-\d+\b/);
        return match ? match[0] : null;
      });
      const hasImport = await page
        .getByRole('button', { name: /Nhập điểm từ Excel\/CSV/ })
        .first()
        .isVisible()
        .catch(() => false);
      if (studentCode && hasImport) {
        openedHref = href;
        break;
      }
    }
    record('opened a grade-entry section with students', Boolean(studentCode && openedHref),
      `href=${openedHref ?? 'none'} studentCode=${studentCode ?? 'none'}`);

    const importToggle = page.getByRole('button', { name: /Nhập điểm từ Excel\/CSV/ }).first();
    await importToggle.waitFor({ state: 'visible', timeout: 30000 });
    await importToggle.click();

    const rows = [
      `${studentCode}\t8,5\t9`,
    ].join('\n');
    await page
      .locator('textarea[aria-label*="Dán bảng điểm"]')
      .fill(rows);
    await page.waitForTimeout(600);

    const summary = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' '));
    const matched = /Khớp 1 dòng, bỏ qua 0/.test(summary);
    record('Item 9 — the sheet matches the student', matched, summary.match(/Khớp \d+ dòng[^.]*/)?.[0] ?? '');

    await page.getByRole('button', { name: /Điền vào bảng điểm/ }).click();
    await page.waitForTimeout(1200);

    // The two grade inputs of the first row must now hold the imported values.
    const filled = await page.evaluate(() => {
      const inputs = [...document.querySelectorAll('input[type="number"]')].filter((i) => !i.disabled);
      return inputs.slice(0, 2).map((i) => i.value);
    });
    record(
      'Item 9 — imported scores land in the grade table',
      filled[0] === '8.5' && filled[1] === '9',
      `inputs=${JSON.stringify(filled)}`,
    );

    // Nothing was saved: reloading returns the stored grades.
    await page.reload({ waitUntil: 'domcontentloaded' });
    await page.waitForTimeout(5000);
    const reloaded = await page.evaluate(() => {
      const inputs = [...document.querySelectorAll('input[type="number"]')].filter((i) => !i.disabled);
      return inputs.slice(0, 2).map((i) => i.value);
    });
    record(
      'import is review-only until the lecturer saves',
      !(reloaded[0] === '8.5' && reloaded[1] === '9'),
      `afterReload=${JSON.stringify(reloaded)}`,
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
