/**
 * Production verification for the deployed CampusCore portal.
 *
 * Covers two claims that a local-only run cannot prove:
 *   1. the English thesis area renders English chrome (no Vietnamese leakage),
 *   2. regulation questions reach the server API instead of being answered by
 *      the client-side resolver (requirement R1).
 *
 * Credentials come from the environment on purpose. Never inline them here.
 *
 *   PROD_BASE_URL          default https://www.campusute.io.vn
 *   PROD_STUDENT_EMAIL     required
 *   PROD_STUDENT_PASSWORD  required
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.PROD_BASE_URL || 'https://www.campusute.io.vn').replace(/\/$/, '');
const EMAIL = process.env.PROD_STUDENT_EMAIL;
const PASSWORD = process.env.PROD_STUDENT_PASSWORD;

if (!EMAIL || !PASSWORD) {
  console.error('Missing PROD_STUDENT_EMAIL / PROD_STUDENT_PASSWORD in the environment.');
  process.exit(2);
}

const VI_CHARS =
  /[\u0103\u00e2\u0111\u00ea\u00f4\u01a1\u01b0\u00c2\u0110\u00ca\u00d4\u01a0\u01af\u00e1\u00e0\u1ea3\u00e3\u1ea1\u1eaf\u1eb1\u1eb3\u1eb5\u1eb7\u1ea5\u1ea7\u1ea9\u1eab\u1ead\u00e9\u00e8\u1ebb\u1ebd\u1eb9\u1ebf\u1ec1\u1ec3\u1ec5\u1ec7\u00ed\u00ec\u1ec9\u0129\u1ecb\u00f3\u00f2\u1ecf\u00f5\u1ecd\u1ed1\u1ed3\u1ed5\u1ed7\u1ed9\u1edb\u1edd\u1edf\u1ee1\u1ee3\u00fa\u00f9\u1ee7\u0169\u1ee5\u1ee9\u1eeb\u1eed\u1eef\u1ef1\u00fd\u1ef3\u1ef7\u1ef9\u1ef5]/g;

const REGULATION_QUESTIONS = [
  'Quy định về học phần tiên quyết và học phần học trước khác nhau như thế nào?',
  'Thời hạn và hình thức nộp học phí học kỳ như thế nào?',
  'Điểm F bị xử lý ra sao và học lại, học cải thiện được quy định thế nào?',
  'Điều kiện tốt nghiệp và học bổng khuyến khích học tập được quy định thế nào?',
];

/**
 * Chrome assertions only. Topic titles, round names and the signed-in user's
 * name are API data and legitimately stay Vietnamese on the English portal, so
 * a raw "no Vietnamese characters anywhere" check would be wrong.
 *
 * Matching is case-insensitive: several of these badges render through CSS
 * `text-transform: uppercase`, and innerText reflects the transformed text.
 */
const EN_CHROME = [
  'Standard 5-stage process',
  'Project & graduation thesis workflow',
  'Responsible:',
  'Regulation note:',
  'HCMUTE academic regulation',
];
const VI_CHROME = [
  'Quy trình chuẩn 5 giai đoạn',
  'Tiến trình thực hiện đề tài',
  'Chủ thể thực hiện:',
  'Lưu ý quy chế:',
  'Quy Chế Đào Tạo HCMUTE',
];
const LOADING = /(Loading thesis area|Đang tải khu vực đồ án)/;

const results = [];
function record(name, ok, detail) {
  results.push({ name, ok, detail });
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${name}${detail ? ` — ${detail}` : ''}`);
}

function countVi(text) {
  return (text.match(VI_CHARS) || []).length;
}

function present(text, needles) {
  const haystack = text.toLowerCase().replace(/\s+/g, ' ');
  return needles.filter((n) => haystack.includes(n.toLowerCase().replace(/\s+/g, ' ')));
}

function absent(text, needles) {
  const haystack = text.toLowerCase().replace(/\s+/g, ' ');
  return needles.filter((n) => !haystack.includes(n.toLowerCase().replace(/\s+/g, ' ')));
}

async function login(page) {
  await page.goto(`${BASE_URL}/vi/login?portal=student`, { waitUntil: 'domcontentloaded', timeout: 120000 });
  await page.locator('#email').fill(EMAIL, { timeout: 60000 });
  await page.locator('#password').fill(PASSWORD, { timeout: 60000 });
  await page.locator('form').getByRole('button', { name: /Đăng nhập|Sign in/i }).first().click();
  await page.waitForURL(/\/dashboard(?:$|[/?#])/, { timeout: 90000 });
}

async function thesisChrome(page, locale) {
  const badge = (locale === 'vi' ? VI_CHROME[0] : EN_CHROME[0]).toLowerCase();
  await page.goto(`${BASE_URL}/${locale}/dashboard/thesis`, { waitUntil: 'domcontentloaded', timeout: 120000 });

  // The stepper/handbook only mount once a registration round is selected. The
  // page usually restores the last round; if it does not, pick the first one.
  await page.waitForTimeout(4000);
  const hasChrome = async () => {
    const text = await page.evaluate(() => document.body.innerText);
    return text.toLowerCase().includes(badge) && !LOADING.test(text);
  };
  if (!(await hasChrome())) {
    const picker = page.locator('select').first();
    if (await picker.count()) {
      const values = await picker.evaluate((el) => [...el.options].map((o) => o.value).filter(Boolean));
      if (values.length) {
        await picker.selectOption(values[0]);
        await page.waitForTimeout(6000);
      }
    }
  }
  await page
    .waitForFunction(
      ([needle, loading]) =>
        document.body.innerText.toLowerCase().includes(needle) &&
        !new RegExp(loading).test(document.body.innerText),
      [badge, LOADING.source],
      { timeout: 120000 },
    )
    .catch((error) => record(`wait for ${locale} thesis chrome`, false, String(error).slice(0, 200)));
  return page.evaluate(() => document.body.innerText);
}

async function main() {
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({ viewport: { width: 1366, height: 900 }, locale: 'vi-VN' });
  const page = await context.newPage();

  const streamCalls = [];
  page.on('request', (req) => {
    const url = req.url();
    if (/\/api\/v1\/assistant\/(stream|chat)/.test(url) && req.method() === 'POST') {
      streamCalls.push(url);
    }
  });

  try {
    console.log(`Logging in against ${BASE_URL} ...`);
    await login(page);
    record('login', true, page.url());

    // --- 1. English thesis area renders English chrome ---
    const enText = await thesisChrome(page, 'en');
    const enFound = present(enText, EN_CHROME);
    const enLeaked = present(enText, VI_CHROME);
    record(
      'EN thesis chrome is English (stepper + handbook)',
      enFound.length === EN_CHROME.length,
      `found=${enFound.length}/${EN_CHROME.length} viChars=${countVi(enText)}`,
    );
    record(
      'EN thesis chrome carries no Vietnamese UI copy',
      enLeaked.length === 0,
      `leaked=${JSON.stringify(enLeaked)}`,
    );
    console.log('--- EN visible text (first 700) ---\n' + enText.slice(0, 700) + '\n---');

    // --- 2. Vietnamese thesis area still Vietnamese ---
    const viText = await thesisChrome(page, 'vi');
    const viFound = present(viText, VI_CHROME);
    const viLeaked = present(viText, EN_CHROME);
    record(
      'VI thesis chrome keeps Vietnamese',
      viFound.length === VI_CHROME.length,
      `found=${viFound.length}/${VI_CHROME.length} viChars=${countVi(viText)}`,
    );
    record('VI thesis chrome carries no English UI copy', viLeaked.length === 0, `leaked=${JSON.stringify(viLeaked)}`);
    console.log('--- VI visible text (first 500) ---\n' + viText.slice(0, 500) + '\n---');

    // --- 3. R1: regulation questions reach the server API ---
    if (process.env.SKIP_ASSISTANT === '1') {
      console.log('SKIP_ASSISTANT=1 — assistant/R1 checks skipped.');
      return;
    }
    await page.goto(`${BASE_URL}/vi/dashboard`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    const launcher = page
      .getByRole('button', { name: /CampusCore|trợ lý|assistant/i })
      .last();
    await launcher.waitFor({ state: 'visible', timeout: 60000 });
    await launcher.click();

    const composer = page.getByRole('textbox').last();
    const send = page.getByRole('button', { name: /Gửi tin nhắn|Send message|Gửi/i }).last();

    for (const question of REGULATION_QUESTIONS) {
      const before = streamCalls.length;
      await composer.fill(question);
      await send.click({ timeout: 60000 });
      // Server answers stream in; give the turn time to finish and render citations.
      await page.waitForTimeout(9000);
      const after = streamCalls.length;
      const lastAnswer = await page.evaluate(() => {
        const log = document.querySelector('[role="log"]');
        if (!log) return '';
        const articles = [...log.querySelectorAll('[role="article"], article')];
        const last = articles[articles.length - 1];
        return last ? last.innerText : log.innerText.slice(-1500);
      });
      const collapsed =
        /bạn có thể hỏi|ví dụ:|mình có thể giúp|try asking|here are some/i.test(lastAnswer) &&
        lastAnswer.length < 400;
      record(
        `R1 server call for "${question.slice(0, 42)}..."`,
        after > before && !collapsed && lastAnswer.trim().length > 0,
        `streamCalls=${after - before} answerLen=${lastAnswer.length}`,
      );
      console.log(`  answer: ${lastAnswer.replace(/\s+/g, ' ').slice(0, 300)}`);
    }

    record('no client-side interception across all questions', streamCalls.length >= REGULATION_QUESTIONS.length,
      `total server calls=${streamCalls.length}/${REGULATION_QUESTIONS.length}`);
  } catch (error) {
    record('harness completed without throwing', false, String(error).slice(0, 400));
  } finally {
    await browser.close();
  }

  const failed = results.filter((r) => !r.ok);
  console.log(`\n==== ${results.length - failed.length}/${results.length} checks passed ====`);
  process.exit(failed.length === 0 ? 0 : 1);
}

main();
