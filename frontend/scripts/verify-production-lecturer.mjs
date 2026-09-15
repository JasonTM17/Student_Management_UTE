/**
 * Read-only production check for the lecturer-facing thesis fixes:
 *  - the ASSIGNED group status no longer 500s the group listing,
 *  - the supervisor relationship resolves and the workload carries group counts.
 *
 * Mutates nothing.
 *
 *   VERIFY_BASE_URL               default https://www.campusute.io.vn
 *   VERIFY_LECTURER_EMAIL / VERIFY_LECTURER_PASSWORD  required
 */

import { chromium } from '@playwright/test';

const BASE_URL = (process.env.VERIFY_BASE_URL || 'https://www.campusute.io.vn').replace(/\/$/, '');
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
    if (await page.waitForURL(/\/dashboard(?:$|[/?#])/, { timeout: 75000 }).then(() => true).catch(() => false)) return;
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

    const probe = await page.evaluate(async () => {
      const get = async (url) => {
        const res = await fetch(url, { credentials: 'include', headers: { accept: 'application/json' } });
        const body = res.ok ? await res.json() : null;
        return { status: res.status, body };
      };
      const workload = await get('/api/v1/thesis/me/workload');
      const topics = (workload.body?.topics ?? []).filter((topic) => topic.groupCount > 0);
      const listings = [];
      for (const topic of topics.slice(0, 3)) {
        const groups = await get(`/api/v1/thesis/groups?roundId=${topic.roundId}`);
        listings.push({
          roundId: topic.roundId,
          roundName: topic.roundName,
          groupCount: topic.groupCount,
          status: groups.status,
          listed: Array.isArray(groups.body) ? groups.body.length : (groups.body?.data?.length ?? 0),
        });
      }
      return { workloadStatus: workload.status, topics: topics.length, listings };
    });

    record(
      'workload resolves supervised topics with group counts',
      probe.workloadStatus === 200 && probe.topics > 0,
      `status=${probe.workloadStatus} topicsWithGroups=${probe.topics}`,
    );

    const failing = probe.listings.filter((l) => l.status !== 200);
    record(
      'group listing no longer fails on the ASSIGNED status',
      probe.listings.length > 0 && failing.length === 0,
      probe.listings.map((l) => `${l.roundName}: ${l.status}/${l.listed} listed (workload said ${l.groupCount})`).join(' | '),
    );

    // Render the lecturer workspace read-only and confirm the member panel is
    // offered on a supervised group.
    await page.goto(`${BASE_URL}/vi/dashboard/thesis`, { waitUntil: 'domcontentloaded', timeout: 120000 });
    await page.waitForTimeout(12000);
    const ui = await page.evaluate(() => ({
      manage: /Quản lý thành viên/.test(document.body.innerText),
      text: document.body.innerText.replace(/\s+/g, ' ').slice(0, 200),
    }));
    record('lecturer workspace offers the member panel', ui.manage, ui.text);
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
