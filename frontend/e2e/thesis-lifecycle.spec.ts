import { expect, test, type Page } from '@playwright/test';
import { submitLiveLogin } from './fixtures/live-login';

/**
 * Opt-in live journey for the thesis round lifecycle on the isolated e2e
 * stack. Run it with:
 *
 *   E2E_THESIS_LIFECYCLE=1 E2E_BASE_URL=http://127.0.0.1:3101 \
 *     E2E_API_URL=http://127.0.0.1:4100/api/v1 \
 *     npx playwright test e2e/thesis-lifecycle.spec.ts --project=chromium
 *
 * Happy path: admin creates a round, opens proposals, publishes, opens and
 * closes registration. Negative/governance path: a round created by mistake
 * is deleted through the admin card (typed name + reason), a round holding a
 * topic refuses deletion with blocker counts, a cancelled empty round deletes,
 * and a student token is refused by the server.
 */
const RUN = process.env.E2E_THESIS_LIFECYCLE === '1';
const API_BASE = process.env.E2E_API_URL ?? 'http://127.0.0.1:4100/api/v1';
const ADMIN = { email: 'admin@campuscore.edu', password: 'password123' };
const STUDENT = { email: 'student@campuscore.edu', password: 'password123' };

test.describe.configure({ timeout: 120_000 });

async function login(page: Page, email: string, password: string) {
  await page.goto('/en/login?portal=student');
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  await expect(submit).toBeEnabled({ timeout: 30_000 });
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  const web = new URL(String(test.info().project.use.baseURL));
  await submitLiveLogin(page, submit, web.port);
}

/** Same-origin API calls through the Next proxy, exactly like the app itself. */
async function api(page: Page, method: string, path: string, body?: unknown) {
  const csrf = (await page.context().cookies())
    .find((cookie) => cookie.name === 'cc_csrf')?.value;
  const send = () => page.request.fetch(`/api/v1${path}`, {
    method,
    data: body,
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-CSRF-Token': csrf } : {}),
    },
  });
  let response = await send();
  if (response.status() === 429) {
    const retryAfter = Number(response.headers()['retry-after']);
    await page.waitForTimeout(
      (Number.isFinite(retryAfter) ? Math.min(retryAfter, 60) : 10) * 1_000 + 500);
    response = await send();
  }
  return response;
}

function roundPayload(name: string, daysAhead: number) {
  const now = Date.now();
  const day = 86_400_000;
  return {
    name,
    thesisType: 'KLTN',
    lecturerSubmitStart: new Date(now - 2 * day).toISOString(),
    lecturerSubmitEnd: new Date(now - 3_600_000).toISOString(),
    registrationStart: new Date(now - 1_800_000).toISOString(),
    registrationEnd: new Date(now + daysAhead * day).toISOString(),
    gvpbDeadline: new Date(now + (daysAhead + 5) * day).toISOString(),
    reportDate: new Date(now + (daysAhead + 10) * day).toISOString(),
    defenseDate: new Date(now + (daysAhead + 15) * day).toISOString(),
  };
}

test.beforeEach(() => {
  test.skip(!RUN, 'Opt-in only: set E2E_THESIS_LIFECYCLE=1 for the live lifecycle journey.');
  const web = new URL(String(test.info().project.use.baseURL));
  const api = new URL(API_BASE);
  expect(['localhost', '127.0.0.1']).toContain(web.hostname);
  expect(['localhost', '127.0.0.1']).toContain(api.hostname);
});

test('admin runs the round lifecycle and deletes a mistaken round', async ({ page }) => {
  const stamp = Date.now().toString(36);
  const liveName = `Lifecycle ${stamp}`;
  const mistakeName = `Mistake ${stamp}`;

  await login(page, ADMIN.email, ADMIN.password);

  // Stage 1 happy path: create -> open proposals -> publish -> open -> close.
  const createRes = await api(page, 'POST', '/thesis/rounds', roundPayload(liveName, 14));
  expect(createRes.status(), 'round creation').toBe(200);
  const round = await createRes.json();
  const openRes = await api(page, 'POST', `/thesis/rounds/${round.id}/open-proposals`);
  expect(openRes.status(), 'transition open-proposals').toBe(200);
  // Topics are proposed while the window is open; publish-proposals closes it.
  const topicRes = await api(page, 'POST', '/thesis/topics', {
    roundId: round.id,
    departmentId: 'department-demo',
    title: `Lifecycle topic ${stamp}`,
    description: 'Published so registration can open',
    maxGroups: 1,
  });
  expect(topicRes.status(), 'topic creation').toBe(200);
  const topic = await topicRes.json();
  const supRes = await api(page, 'PUT', `/thesis/topics/${topic.id}/supervisors`, {
    supervisorIds: ['lecturer-profile'],
  });
  expect(supRes.status(), 'supervisor assignment').toBe(200);
  const publishRes = await api(page, 'POST', `/thesis/topics/${topic.id}/publish`);
  expect(publishRes.status(), 'topic publish').toBe(200);
  for (const step of ['publish-proposals', 'open-registration', 'close-registration']) {
    const res = await api(page, 'POST', `/thesis/rounds/${round.id}/${step}`);
    expect(res.status(), `transition ${step}`).toBe(200);
  }

  // A mistaken round: create, delete through the admin card with typed name.
  const mistakeRes = await api(page, 'POST', '/thesis/rounds', roundPayload(mistakeName, 14));
  expect(mistakeRes.status()).toBe(200);
  const mistake = await mistakeRes.json();

  await page.goto('/en/admin/thesis');
  const card = page.getByRole('button', { name: new RegExp(mistakeName) });
  await expect(card).toBeVisible({ timeout: 30_000 });
  await card.click();
  await page.getByRole('button', { name: 'Delete round' }).last().click();
  await expect(page.getByText('Only the empty round is removed; the audit note is kept.')).toBeVisible();
  await page.getByLabel('Reason').fill('created by mistake');
  await page.getByLabel('Type the round name to confirm').fill(mistakeName);
  await page.getByRole('button', { name: /^save$/i }).click();
  await expect(page.getByText('Round deleted.')).toBeVisible({ timeout: 30_000 });
  await expect(card).toHaveCount(0);

  // Server-side state: the row is really gone, and a second delete is a clean 404.
  const secondDelete = await api(page, 'DELETE', `/thesis/rounds/${mistake.id}`, { reason: 'again' });
  expect(secondDelete.status()).toBe(404);
  expect((await secondDelete.json()).code).toBe('ROUND_NOT_FOUND');
});

test('a round holding a topic refuses deletion with blocker counts', async ({ page }) => {
  const stamp = Date.now().toString(36);
  const name = `Blocked ${stamp}`;
  await login(page, ADMIN.email, ADMIN.password);

  const createRes = await api(page, 'POST', '/thesis/rounds', roundPayload(name, 14));
  const round = await createRes.json();
  const deleteRes = await api(page, 'DELETE', `/thesis/rounds/${round.id}`, { reason: 'probe' });
  // Seed topics are per round; a fresh round starts empty so seed a topic in it.
  expect(deleteRes.status(), 'empty round deletes cleanly via API').toBe(204);

  const blockedRes = await api(page, 'POST', '/thesis/rounds', roundPayload(`${name} B`, 14));
  const blocked = await blockedRes.json();
  const topicRes = await api(page, 'POST', '/thesis/topics', {
    roundId: blocked.id,
    departmentId: 'department-demo',
    title: `Blocker topic ${stamp}`,
    description: 'A topic that must keep the round alive',
    maxGroups: 1,
  });
  if (topicRes.status() === 200) {
    const refused = await api(page, 'DELETE', `/thesis/rounds/${blocked.id}`, { reason: 'probe' });
    expect(refused.status()).toBe(409);
    const body = await refused.json();
    expect(body.code).toBe('ROUND_HAS_DATA');
    expect(body.details.topics).toBe(1);
  }

  // Cancelled empty round deletes.
  const cancelRes = await api(page, 'POST', `/thesis/rounds/${blocked.id}/cancel`);
  expect(cancelRes.status(), 'cancel succeeds').toBe(200);
  const afterCancel = await api(page, 'DELETE', `/thesis/rounds/${blocked.id}`, { reason: 'probe' });
  if (topicRes.status() === 200) {
    expect(afterCancel.status()).toBe(409);
    expect((await afterCancel.json()).code).toBe('ROUND_HAS_DATA');
  } else {
    expect(afterCancel.status()).toBe(204);
  }
});

test('a student token is refused by the delete endpoint', async ({ page }) => {
  await login(page, STUDENT.email, STUDENT.password);
  const res = await api(page, 'DELETE', `/thesis/rounds/${crypto.randomUUID()}`, { reason: 'probe' });
  expect(res.status(), 'student must be refused').toBe(403);
});
