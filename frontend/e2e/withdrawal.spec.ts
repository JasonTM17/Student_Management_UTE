import { expect, test, type Page } from '@playwright/test';
import { submitLiveLogin } from './fixtures/live-login';

/**
 * Live course-withdrawal journey on the disposable e2e stack. Requires the
 * `wd2-sem-late` fixture rows seeded by scripts/course-e2e-fixture.mjs
 * (withdrawalWindowSql) — run via scripts/run-course-e2e.mjs.
 */

const student = { email: 'student@campuscore.edu', password: 'password123' };
const admin = { email: 'admin@campuscore.edu', password: 'password123' };
const REASON = 'I am withdrawing from this course because of a documented medical situation.';
const SHOTS = 'test-results/withdrawal';
const VIEWPORTS = [375, 768, 1280] as const;

async function login(page: Page, account: typeof student, portal: 'student' | 'admin', expectedPort: string) {
  await page.goto(`/en/login?portal=${portal}`);
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(account.email);
  await page.locator('#password').fill(account.password);
  await submitLiveLogin(page, submit, expectedPort);
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
}

async function transcript(page: Page) {
  const response = await page.request.get('/api/v1/enrollments/my/transcript');
  expect(response.status()).toBe(200);
  return response.json();
}

async function enrolledCount(page: Page, sectionId: string) {
  const response = await page.request.get(
    '/api/v1/me/registration/sections?semesterId=semester-demo&roundId=round-registration-demo');
  expect(response.status()).toBe(200);
  const sections = await response.json();
  const section = sections.find((s: { id: string }) => s.id === sectionId);
  expect(section, `section ${sectionId} in the registration catalog`).toBeTruthy();
  return section.enrolledCount as number;
}

test.describe.configure({ mode: 'serial' });
test.use({ viewport: { width: 1280, height: 900 } });

test('student withdraws in-window, admin approves, transcript shows W, seat released', async ({ page, browser }) => {
  test.setTimeout(180_000);
  const base = new URL(process.env.E2E_BASE_URL ?? page.url());
  const port = base.port || '3801';

  await login(page, student, 'student', port);

  const beforeTranscript = await transcript(page);
  const beforeSeats = await enrolledCount(page, 'section-java-demo');

  await page.goto('/en/dashboard/register');
  const card = page.locator('article').filter({ hasText: /SE401-01/ });
  await expect(card).toBeVisible({ timeout: 30_000 });
  const withdrawButton = card.getByRole('button', { name: /^withdraw$|^rút học phần$/i });
  await expect(withdrawButton).toBeVisible({ timeout: 15_000 });
  await withdrawButton.click();

  const dialog = page.getByRole('dialog').filter({ has: page.locator('#withdrawal-reason') });
  await expect(dialog).toBeVisible();

  // Viewport sweep of the open withdrawal modal (screenshots stay out of git).
  for (const width of VIEWPORTS) {
    await page.setViewportSize({ width, height: 900 });
    await expect(dialog).toBeVisible();
    await page.screenshot({ path: `${SHOTS}/student-modal-${width}.png`, fullPage: false });
  }
  await page.setViewportSize({ width: 1280, height: 900 });

  await page.locator('#withdrawal-reason').fill(REASON);
  await dialog.getByRole('button', { name: /submit request|gửi đơn/i }).click();
  await expect(
    page.getByText(/withdrawal request submitted|đã gửi đơn xin rút học phần/i).first(),
  ).toBeVisible({ timeout: 15_000 });
  await expect(
    card.getByRole('button', { name: /withdrawal pending|đang chờ duyệt rút/i }),
  ).toBeVisible({ timeout: 15_000 });

  // Closed-window refusal on a real seeded row (wd2-sem-late started 30d ago).
  // Mutations need the CSRF header the frontend axios attaches from cc_csrf.
  const csrf = (await page.context().cookies()).find((cookie) => cookie.name === 'cc_csrf');
  expect(csrf?.value, 'CSRF cookie on the authenticated session').toBeTruthy();
  const refused = await page.request.post('/api/v1/me/registration/withdrawal-applications', {
    data: { enrollmentId: 'wd2-enr-late', reason: REASON },
    headers: { 'X-CSRF-Token': csrf!.value },
  });
  expect(refused.status()).toBe(409);
  const refusedBody = await refused.json();
  expect(refusedBody.code).toBe('WITHDRAWAL_WINDOW_CLOSED');

  // Admin reviews and approves in a separate context.
  const adminContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminPage = await adminContext.newPage();
  await login(adminPage, admin, 'admin', port);
  // Dev-server route compiles stall 'load'; commit-level nav + URL settle is
  // the honest wait, and middleware may rewrite the locale prefix.
  await adminPage.goto('/en/admin/withdrawal-applications', { waitUntil: 'commit' });
  await adminPage.waitForURL(/withdrawal-applications/, { timeout: 60_000 });
  const reasonText = REASON.slice(0, 40);
  const reasonVisible = () => adminPage.getByText(reasonText).filter({ visible: true });
  await expect(reasonVisible()).toHaveCount(1, { timeout: 20_000 });

  for (const width of VIEWPORTS) {
    await adminPage.setViewportSize({ width, height: 900 });
    await expect(reasonVisible()).toHaveCount(1);
    await adminPage.screenshot({ path: `${SHOTS}/admin-page-${width}.png`, fullPage: false });
  }
  await adminPage.setViewportSize({ width: 1280, height: 900 });

  const pendingRow = adminPage.locator('tr').filter({ hasText: reasonText });
  await expect(pendingRow).toBeVisible();

  await pendingRow.getByRole('button', { name: /review request/i }).click();
  const reviewDialog = adminPage.getByRole('dialog').filter({ has: adminPage.locator('#withdrawal-review-note') });
  await expect(reviewDialog).toBeVisible();
  await reviewDialog.getByLabel(/approve withdrawal|duyệt rút học phần/i).check();
  await reviewDialog.locator('#withdrawal-review-note').fill('Approved under the documented withdrawal policy.');
  await reviewDialog.getByRole('button', { name: /save decision|lưu quyết định/i }).click();
  await expect(
    adminPage.getByText(/application decision saved|đã lưu/i).first(),
  ).toBeVisible({ timeout: 15_000 });
  await expect(adminPage.getByText(/approved|đã duyệt/i).first()).toBeVisible({ timeout: 15_000 });
  await adminContext.close();

  // Transcript: W grade present, GPA untouched.
  const afterTranscript = await transcript(page);
  const record = afterTranscript.semesters
    .find((s: { semesterId: string }) => s.semesterId === 'semester-demo')
    ?.records.find((r: { sectionCode?: string }) => r.sectionCode === 'SE401-01');
  expect(record?.letterGrade, 'transcript letter grade after approval').toBe('W');
  expect(record?.enrollmentStatus).toBe('WITHDRAWN');
  expect(
    String(afterTranscript.summary?.cumulativeGpa),
    'cumulative GPA must not move when a W is recorded',
  ).toBe(String(beforeTranscript.summary?.cumulativeGpa));

  // Seat released back to the catalog.
  const afterSeats = await enrolledCount(page, 'section-java-demo');
  expect(afterSeats, 'enrolledCount must drop by one after approval').toBe(beforeSeats - 1);

  // UI transcript page renders the W mark.
  await page.goto('/en/dashboard/transcript');
  await expect(page.getByText(/^W$/).first()).toBeVisible({ timeout: 20_000 });
});
