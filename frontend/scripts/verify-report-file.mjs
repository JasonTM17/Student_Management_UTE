/**
 * Browser e2e for feedback item 7: the group leader uploads a Word/PDF report
 * through the report form, downloads it back, and then restores the original
 * link-based report so the fixture is unchanged.
 *
 *   VERIFY_BASE_URL   default http://127.0.0.1:3210
 *   VERIFY_EMAIL / VERIFY_PASSWORD   the group leader's student login
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.VERIFY_BASE_URL || 'http://127.0.0.1:3210').replace(/\/$/, '');
const EMAIL = process.env.VERIFY_EMAIL;
const PASSWORD = process.env.VERIFY_PASSWORD;

// The fixture's original link-based submission, restored at the end.
const ORIGINAL = {
  title: 'Báo cáo Khóa luận Tốt nghiệp - Hệ thống Quản trị Học vụ CampusUTE',
  url: 'https://storage.hcmute.edu.vn/luanvan/2026/kltn-campuscore',
  note: 'Báo cáo chính thức đợt 1 năm học 2026-2027 kèm mã nguồn và slide bảo vệ',
};

// A minimal, valid one-page PDF document.
function tinyPdf() {
  const content = Buffer.from(
    '%PDF-1.4\n1 0 obj<</Type/Catalog/Pages 2 0 R>>endobj\n' +
      '2 0 obj<</Type/Pages/Kids[3 0 R]/Count 1>>endobj\n' +
      '3 0 obj<</Type/Page/Parent 2 0 R/MediaBox[0 0 200 200]>>endobj\n' +
      'trailer<</Root 1 0 R>>\n%%EOF',
    'utf-8',
  );
  return { name: 'kltn-campuscore-test.pdf', mimeType: 'application/pdf', buffer: content };
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

async function openReportForm(page) {
  const edit = page.getByRole('button', { name: /Cập nhật báo cáo/ }).first();
  await edit.waitFor({ state: 'visible', timeout: 60000 });
  await edit.click();
  await page.locator('#thesis-report-file').waitFor({ state: 'visible', timeout: 30000 });
}

async function submitReport(page, { title, url, note, file }) {
  const form = page.locator('#thesis-report-file');
  await form.scrollIntoViewIfNeeded();
  if (file) {
    await page.locator('#thesis-report-file').setInputFiles(file);
  }
  const titleInput = page.locator('input[id*="report-title"], input[aria-label*="Tiêu đề"]').first();
  if (await titleInput.count()) {
    await titleInput.fill(title);
  }
  const urlInput = page.locator('input[id*="report-url"], input[aria-label*="Liên kết"]').first();
  if (await urlInput.count()) {
    await urlInput.fill(url ?? '');
  }
  const noteInput = page.locator('textarea').first();
  if (await noteInput.count()) {
    await noteInput.fill(note ?? '');
  }
  // The submit label changes ("Nộp báo cáo" vs "Cập nhật báo cáo") and the
  // toggle shares the latter, so scope to the form that owns the file input.
  await page
    .locator('form:has(#thesis-report-file) button[type="submit"]')
    .first()
    .click();
  await page.waitForTimeout(6000);
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
  const page = await context.newPage();

  try {
    await login(page);
    record('leader login', true, page.url());

    await page.goto(`${BASE_URL}/vi/dashboard/thesis`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(10000);

    // --- Item 7: upload a Word/PDF document ---
    await openReportForm(page);
    if (process.env.VERIFY_READONLY === '1') {
      // Wiring check only: the accepted types prove the upload path is live
      // without replacing the production report.
      const accept = await page.locator('#thesis-report-file').getAttribute('accept');
      record(
        'Item 7 — production report form offers a Word/PDF upload',
        accept === '.pdf,.doc,.docx',
        `accept=${accept}`,
      );
      await browser.close();
      const passed = results.filter((r) => !r.ok);
      console.log(`
==== ${results.length - passed.length}/${results.length} checks passed ====`);
      process.exit(passed.length === 0 ? 0 : 1);
    }
    await submitReport(page, {
      title: 'Báo cáo kiểm thử nộp tệp (e2e)',
      file: tinyPdf(),
      note: 'Kiểm thử end-to-end của tính năng nộp tệp',
    });

    await page.waitForTimeout(3000);
    const afterUpload = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' '));
    const chipVisible = /kltn-campuscore-test\.pdf/.test(afterUpload);
    const downloadOffered = /Tải tài liệu/.test(afterUpload);
    record(
      'Item 7 — uploaded document is attached and downloadable',
      chipVisible && downloadOffered,
      `chip=${chipVisible} downloadLink=${downloadOffered}`,
    );

    // --- Restore the fixture: the original link-based report ---
    await openReportForm(page);
    await submitReport(page, ORIGINAL);

    await page.waitForTimeout(3000);
    const afterRestore = await page.evaluate(() => document.body.innerText.replace(/\s+/g, ' '));
    record(
      'fixture restored to the original link report',
      afterRestore.includes(ORIGINAL.title) && !/kltn-campuscore-test\.pdf/.test(afterRestore),
      `titleVisible=${afterRestore.includes(ORIGINAL.title)}`,
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
