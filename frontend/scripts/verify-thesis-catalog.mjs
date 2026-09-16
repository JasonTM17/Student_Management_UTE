/**
 * Verifies items 6 and 12 from the ltweb report end to end:
 *   6. the inline topic picker is gone and topics live in the topic catalog,
 *  12. a lecturer is never offered a topic chooser.
 *
 * Needs both a student and a lecturer login.
 *
 *   VERIFY_BASE_URL          default http://127.0.0.1:3210
 *   VERIFY_EMAIL / VERIFY_PASSWORD            student
 *   VERIFY_LECTURER_EMAIL / VERIFY_LECTURER_PASSWORD   lecturer
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.VERIFY_BASE_URL || 'http://127.0.0.1:3210').replace(/\/$/, '');
const STUDENT = { email: process.env.VERIFY_EMAIL, password: process.env.VERIFY_PASSWORD };
const LECTURER = {
  email: process.env.VERIFY_LECTURER_EMAIL,
  password: process.env.VERIFY_LECTURER_PASSWORD,
};

const results = [];
const record = (name, ok, detail) => {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
};

async function login(page, who, portal) {
  await page.goto(`${BASE_URL}/vi/login?portal=${portal}`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.locator('#email').waitFor({ state: 'visible', timeout: 60000 });
  const submit = page.locator('form button[type="submit"]').first();
  for (let attempt = 0; attempt < 8; attempt++) {
    await page.locator('#email').fill(who.email);
    await page.locator('#password').fill(who.password);
    await page.waitForTimeout(700);
    if (!(await submit.isEnabled().catch(() => false))) continue;
    await submit.click().catch(() => {});
    if (await page.waitForURL(/\/dashboard(?:$|[/?#])/, { timeout: 60000 }).then(() => true).catch(() => false)) return;
  }
  throw new Error(`login did not reach the dashboard for ${who.email}`);
}

// Strings that only the removed inline grid produced. A bare "Chọn đề tài" is
// still legitimate as the group card's empty-state link to the catalog, so the
// check targets the grid's own affordances instead.
const gridOnlyCopy =
  /Đang chọn đề tài này|Selected topic|Tạo nhóm & Đăng ký đề tài|Create group & Select topic|Xem chi tiết đề tài|View topic details/;

async function thesisText(page) {
  await page.goto(`${BASE_URL}/vi/dashboard/thesis`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.waitForTimeout(9000);
  return page.evaluate(() => document.body.innerText);
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });

  try {
    // --- Student: picker gone, catalog CTA present ---
    const studentCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
    const studentPage = await studentCtx.newPage();
    await login(studentPage, STUDENT, 'student');
    const studentText = await thesisText(studentPage);
    const gridPresent = gridOnlyCopy.test(studentText);
    record(
      'Item 6 — thesis page no longer renders the inline topic picker grid',
      !gridPresent,
      `gridOnlyCopyPresent=${gridPresent}`,
    );
    record(
      'Item 6 — thesis page points students at the topic catalog',
      /danh mục đề tài/i.test(studentText),
      'catalog link present',
    );

    // The catalog is search-first: no topic links may render before a query.
    await studentPage.goto(`${BASE_URL}/vi/dashboard/thesis/topics`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await studentPage.waitForTimeout(6000);
    const beforeSearch = await studentPage.evaluate(
      () => document.querySelectorAll('a[href*="/dashboard/thesis/topics/"]').length,
    );
    record(
      'Item 1 — catalog shows no topics before a search query',
      beforeSearch === 0,
      `topicLinksBeforeSearch=${beforeSearch}`,
    );

    // Search, then the catalog must list topics and offer selection on the detail page.
    // The default round may legitimately hold zero published topics, so the
    // probe switches to the last round in the selector (the open teaching round).
    const roundSelect = studentPage.locator('select').first();
    const optionCount = await roundSelect.locator('option').count();
    if (optionCount > 1) {
      await roundSelect.selectOption({ index: optionCount - 1 });
    }
    const searchForm = studentPage.locator('input[type="search"]').first();
    await searchForm.waitFor({ state: 'visible', timeout: 30000 });
    await searchForm.fill(process.env.VERIFY_SEARCH_QUERY || 'a');
    await searchForm.press('Enter');
    await studentPage
      .waitForFunction(
        () => document.querySelectorAll('a[href*="/dashboard/thesis/topics/"]').length > 0,
        undefined,
        { timeout: 45000 },
      )
      .catch(() => {});
    const catalogState = await studentPage.evaluate(() => ({
      links: [...document.querySelectorAll('a[href*="/dashboard/thesis/topics/"]')].map((a) =>
        a.getAttribute('href'),
      ),
      excerpt: document.body.innerText.replace(/\s+/g, ' ').slice(0, 260),
    }));
    record('Item 6 — catalog lists topics after searching', catalogState.links.length > 0,
      `links=${catalogState.links.length} first=${catalogState.links[0] ?? 'none'}`);
    if (catalogState.links.length === 0) {
      console.log(`  catalog excerpt: ${catalogState.excerpt}`);
    }
    await studentCtx.close();

    // --- Lecturer: no chooser anywhere in the thesis workspace ---
    if (LECTURER.email && LECTURER.password) {
      const lecturerCtx = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
      const lecturerPage = await lecturerCtx.newPage();
      await login(lecturerPage, LECTURER, 'lecturer');
      const lecturerText = await thesisText(lecturerPage);
      const lecturerOffered = gridOnlyCopy.test(lecturerText);
      record('Item 12 — lecturer is not offered a topic chooser', !lecturerOffered,
        `bodyHasChooserCopy=${lecturerOffered}`);
      console.log(`  lecturer thesis excerpt: ${lecturerText.replace(/\s+/g, ' ').slice(0, 260)}`);
      await lecturerCtx.close();
    } else {
      record('Item 12 — lecturer is not offered a topic chooser', false,
        'NOT_RUN — VERIFY_LECTURER_EMAIL / VERIFY_LECTURER_PASSWORD not set');
    }
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
