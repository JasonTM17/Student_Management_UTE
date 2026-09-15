/**
 * Verifies the ltweb-report fixes that live in the frontend.
 *
 * Runs against a dev server serving the current source, not the pre-built
 * Docker image, so it actually exercises the changes.
 *
 *   VERIFY_BASE_URL   default http://127.0.0.1:3210
 *   VERIFY_EMAIL      required
 *   VERIFY_PASSWORD   required
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.VERIFY_BASE_URL || 'http://127.0.0.1:3210').replace(/\/$/, '');
const EMAIL = process.env.VERIFY_EMAIL;
const PASSWORD = process.env.VERIFY_PASSWORD;

if (!EMAIL || !PASSWORD) {
  console.error('Missing VERIFY_EMAIL / VERIFY_PASSWORD in the environment.');
  process.exit(2);
}

const VI_CHARS =
  /[\u0103\u00e2\u0111\u00ea\u00f4\u01a1\u01b0\u00c2\u0110\u00ca\u00d4\u01a0\u01af\u00e1\u00e0\u1ea3\u00e3\u1ea1\u1eaf\u1eb1\u1eb3\u1eb5\u1eb7\u1ea5\u1ea7\u1ea9\u1eab\u1ead\u00e9\u00e8\u1ebb\u1ebd\u1eb9\u1ebf\u1ec1\u1ec3\u1ec5\u1ec7\u00ed\u00ec\u1ec9\u0129\u1ecb\u00f3\u00f2\u1ecf\u00f5\u1ecd\u1ed1\u1ed3\u1ed5\u1ed7\u1ed9\u1edb\u1edd\u1edf\u1ee1\u1ee3\u00fa\u00f9\u1ee7\u0169\u1ee5\u1ee9\u1eeb\u1eed\u1eef\u1ef1\u00fd\u1ef3\u1ef7\u1ef9\u1ef5]/g;

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
}

async function login(page) {
  await page.goto(`${BASE_URL}/vi/login?portal=student`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.locator('#email').waitFor({ state: 'visible', timeout: 60000 });
  const submit = page.locator('form button[type="submit"]').first();
  for (let attempt = 0; attempt < 8; attempt++) {
    await page.locator('#email').fill(EMAIL);
    await page.locator('#password').fill(PASSWORD);
    await page.waitForTimeout(700);
    if (!(await submit.isEnabled().catch(() => false))) continue;
    await Promise.race([
      page.waitForResponse((r) => /auth\/login/.test(r.url()), { timeout: 25000 }).catch(() => null),
      submit.click().then(() => null),
    ]);
    if (await page.waitForURL(/\/dashboard(?:$|[/?#])/, { timeout: 30000 }).then(() => true).catch(() => false)) return;
  }
  throw new Error('login did not reach the dashboard');
}

async function openGradeDialog(page, locale) {
  await page.goto(`${BASE_URL}/${locale}/dashboard/grades`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  const row = page.locator('table tbody button').first();
  await row.waitFor({ state: 'visible', timeout: 90000 });
  await row.click();
  const dialog = page.locator('[role="dialog"]').first();
  await dialog.waitFor({ state: 'visible', timeout: 30000 });
  await page.waitForTimeout(1500);
  return dialog.innerText();
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'vi-VN' });
  const page = await context.newPage();

  try {
    await login(page);
    record('login', true, page.url());

    // --- Item 2: the 4.0 conversion must show a value, not "Chưa có" ---
    const viDialog = await openGradeDialog(page, 'vi');
    const gradePointMatch = viDialog.match(/Quy đổi \(4\.0\)\s*\n?\s*([^\n]+)/);
    const gradePointValue = gradePointMatch ? gradePointMatch[1].trim() : '';
    record(
      'Item 2 — 4.0 conversion is computed',
      Boolean(gradePointValue) && gradePointValue !== 'Chưa có' && /^\d/.test(gradePointValue),
      `value=${JSON.stringify(gradePointValue)}`,
    );

    // --- Item 2b: the English dialog carries no Vietnamese *chrome* ---
    // A lecturer's proper name and other record data stay Vietnamese on the
    // English portal by design, so assert on the labels that used to leak.
    const enDialog = await openGradeDialog(page, 'en');
    const leakedLabels = [
      'Điểm quá trình',
      'Điểm cuối kỳ',
      'Học kỳ',
      'Thành phần điểm',
      'Điểm tổng kết',
      'Điểm chữ',
      'Quy đổi',
    ].filter((label) => enDialog.includes(label));
    const expectedEnglish = ['Process score', 'Final exam score', 'GRADE COMPONENTS'].filter((label) =>
      enDialog.includes(label),
    );
    record(
      'Item 2b — EN grade dialog labels are English',
      leakedLabels.length === 0 && expectedEnglish.length === 3,
      `leaked=${JSON.stringify(leakedLabels)} english=${expectedEnglish.length}/3 vietnameseNameChars=${(enDialog.match(VI_CHARS) || []).length}`,
    );
    console.log(`  EN dialog excerpt: ${enDialog.replace(/\s+/g, ' ').slice(0, 260)}`);

    // --- Item 8: the name fields are not editable ---
    await page.goto(`${BASE_URL}/vi/dashboard/profile`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.locator('#profile-first-name').waitFor({ state: 'visible', timeout: 60000 });
    const firstNameDisabled = await page.locator('#profile-first-name').isDisabled();
    const lastNameDisabled = await page.locator('#profile-last-name').isDisabled();
    record('Item 8 — first/last name inputs are disabled', firstNameDisabled && lastNameDisabled,
      `first=${firstNameDisabled} last=${lastNameDisabled}`);

    // --- Item 4: the certificate no longer shows a QR code ---
    await page.goto(`${BASE_URL}/vi/dashboard/conduct`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    const certTrigger = page.getByRole('button', { name: /Xem chứng nhận/i }).first();
    if (await certTrigger.count()) {
      await certTrigger.click();
    } else {
      const alt = page.locator('button:has-text("Xem chứng nhận"), a:has-text("Xem chứng nhận")').first();
      if (await alt.count()) await alt.click();
    }
    await page.waitForTimeout(2500);
    const bodyText = await page.evaluate(() => document.body.innerText);
    const hasSecureCode = /Mã bảo mật/.test(bodyText);
    const hasQrSvg = await page.evaluate(() => {
      // The removed QR was a lucide <svg class="lucide-qr-code">.
      return document.querySelectorAll('svg.lucide-qr-code').length;
    });
    record('Item 4 — certificate has no QR code', !hasSecureCode && hasQrSvg === 0,
      `secureCode=${hasSecureCode} qrSvgs=${hasQrSvg}`);
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
