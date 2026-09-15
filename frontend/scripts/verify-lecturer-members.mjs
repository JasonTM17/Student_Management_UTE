/**
 * End-to-end check for feedback items 5 and 11: the supervising lecturer adds
 * then removes a member of a supervised group through the real UI and API.
 *
 * The add is reverted by the remove at the end, so the fixture is left as it
 * started. Requires the locally rebuilt backend (the deployed one may not have
 * the authorization change yet).
 *
 *   VERIFY_BASE_URL            default http://127.0.0.1:3210
 *   VERIFY_LECTURER_EMAIL / VERIFY_LECTURER_PASSWORD  required
 *   VERIFY_SEARCH_QUERY        student search term, default "CS-DEMO"
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.VERIFY_BASE_URL || 'http://127.0.0.1:3210').replace(/\/$/, '');
const EMAIL = process.env.VERIFY_LECTURER_EMAIL;
const PASSWORD = process.env.VERIFY_LECTURER_PASSWORD;
const QUERY = process.env.VERIFY_SEARCH_QUERY || 'CS-DEMO';

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

    await page.goto(`${BASE_URL}/vi/dashboard/thesis`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    const manageButton = page.getByRole('button', { name: /Quản lý thành viên/ }).first();
    await manageButton.waitFor({ state: 'visible', timeout: 60000 });
    record('Item 5 — lecturer sees member management on the supervised group', true);

    await manageButton.click();
    await page.waitForTimeout(600);

    const membersBefore = await page.evaluate(() => document.body.innerText.match(/Quản lý thành viên\s*\((\d+)\/3\)/)?.[1]);
    record('roster count readable', Boolean(membersBefore), `before=${membersBefore}/3`);

    // Pick a candidate from the student search.
    const searchInput = page.locator('input[aria-label*="Mã sinh viên"], input[placeholder*="Mã sinh viên"]').first();
    await searchInput.fill(QUERY);
    await page.getByRole('button', { name: 'Tìm kiếm' }).first().click();
    await page.waitForTimeout(2500);

    const addButtons = page.getByRole('button', { name: 'Thêm', exact: true });
    const addCount = await addButtons.count();
    record('student search returns candidates', addCount > 0, `candidates=${addCount}`);
    if (addCount === 0) {
      throw new Error('no candidate student found to add');
    }

    // Try candidates until one lands: demo students are often already members
    // of a group in another active round, which the server rejects with 409.
    let targetName = '';
    let membersAfter = membersBefore;
    const candidateCount = Math.min(addCount, 6);
    for (let index = 0; index < candidateCount; index += 1) {
      const buttons = page.getByRole('button', { name: 'Thêm', exact: true });
      if ((await buttons.count()) <= index) break;
      // A candidate already in this group renders a disabled Add button.
      if (!(await buttons.nth(index).isEnabled().catch(() => false))) continue;
      targetName = await page.evaluate((skip) => {
        const rows = [...document.querySelectorAll('li')].filter((node) =>
          [...node.querySelectorAll('button')].some((b) => b.innerText.trim() === 'Thêm'),
        );
        return rows[skip] ? rows[skip].innerText.replace(/\s+/g, ' ').slice(0, 80) : '';
      }, index);
      await buttons.nth(index).click();
      await page.waitForTimeout(2500);
      membersAfter = await page.evaluate(
        () => document.body.innerText.match(/Quản lý thành viên\s*\((\d+)\/3\)/)?.[1],
      );
      if (Number(membersAfter) > Number(membersBefore)) break;
    }
    record(
      'Item 5/11 — supervisor added the member',
      Number(membersAfter) > Number(membersBefore),
      `before=${membersBefore} after=${membersAfter} tried=${candidateCount}`,
    );

    // Revert so the fixture stays as it started.
    const removeButton = page.locator('button[aria-label^="Xóa:"]').last();
    await removeButton.click();
    await page.waitForTimeout(3000);
    const membersRestored = await page.evaluate(() => document.body.innerText.match(/Quản lý thành viên\s*\((\d+)\/3\)/)?.[1]);
    record('Item 5/11 — supervisor removed the member (reverted)', Number(membersRestored) === Number(membersBefore),
      `restored=${membersRestored} (target ${membersBefore})`);
    console.log(`  candidate used: ${targetName}`);
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
