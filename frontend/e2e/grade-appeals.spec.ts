import { expect, test, type Page } from '@playwright/test';
import { submitLiveLogin } from './fixtures/live-login';

const student = { email: 'student@campuscore.edu', password: 'password123' };
const lecturer = { email: 'lecturer@campuscore.edu', password: 'password123' };
const admin = { email: 'admin@campuscore.edu', password: 'password123' };
const enrollmentId = 'enrollment-grade-appeal-e2e';
const sectionId = 'section-grade-appeal-e2e';
const studentReason = 'Please review the final exam answer omitted from my published mark.';
const reviewReason = 'The grading record confirms the omitted answer earns the corrected points.';
const unpublishReason = 'The appeal must be reviewed before this published grade can change.';

async function login(
  page: Page,
  account: typeof student,
  portal: 'student' | 'lecturer' | 'admin',
  expectedPort: string,
) {
  await page.goto(`/en/login?portal=${portal}`);
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(account.email);
  await page.locator('#password').fill(account.password);
  await submitLiveLogin(page, submit, expectedPort);
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
}

test('student appeal, admin unpublish refusal, lecturer correction, and transcript update', async ({ page, browser }) => {
  test.setTimeout(180_000);
  const base = new URL(process.env.E2E_BASE_URL ?? page.url());
  const port = base.port || '3101';

  await login(page, student, 'student', port);
  await page.goto('/en/dashboard/grades');
  const grade = page.getByRole('button', { name: /Open grade breakdown for APPEAL1E2E/ });
  await expect(grade).toBeVisible({ timeout: 30_000 });
  await grade.click();

  const studentDialog = page.getByRole('dialog');
  await expect(studentDialog.getByRole('button', { name: 'Appeal', exact: true })).toBeVisible();
  await studentDialog.getByRole('button', { name: 'Appeal', exact: true }).click();
  await studentDialog.locator('#student-grade-appeal-reason').fill(studentReason);
  await studentDialog.getByRole('checkbox').check();
  const submitAppeal = page.waitForResponse((response) =>
    new URL(response.url()).pathname === '/api/v1/me/grade-appeals'
    && response.request().method() === 'POST');
  await studentDialog.getByRole('button', { name: 'Submit appeal', exact: true }).click();
  const appealResponse = await submitAppeal;
  expect(appealResponse.ok(), 'student appeal API response').toBeTruthy();
  const appeal = await appealResponse.json() as { id: string; status: string };
  expect(appeal.status).toBe('PENDING');
  await expect(studentDialog.getByText('Pending review', { exact: true })).toBeVisible();

  const adminContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const adminPage = await adminContext.newPage();
  try {
    await login(adminPage, admin, 'admin', port);
    await adminPage.goto('/en/admin/grade-appeals', { waitUntil: 'commit' });
    await adminPage.waitForURL(/grade-appeals/, { timeout: 60_000 });
    await adminPage.locator('#grade-unpublish-search').fill('APPEAL1E2E');
    await expect(adminPage.locator(`#grade-unpublish-section option[value="${sectionId}"]`)).toBeAttached({ timeout: 20_000 });
    await adminPage.locator('#grade-unpublish-section').selectOption(sectionId);
    await expect(adminPage.getByText('Appeal pending; unpublishing will be refused.')).toBeVisible({ timeout: 20_000 });
    await adminPage.getByRole('button', { name: 'Unpublish grades', exact: true }).click();
    const unpublishDialog = adminPage.getByRole('dialog');
    await unpublishDialog.locator('#grade-unpublish-reason').fill(unpublishReason);
    await unpublishDialog.getByRole('checkbox').check();
    const unpublishResponse = adminPage.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/v1/admin/sections/${sectionId}/grades/unpublish`
      && response.request().method() === 'POST');
    await unpublishDialog.getByRole('button', { name: 'Unpublish grades', exact: true }).click();
    const refused = await unpublishResponse;
    expect(refused.status()).toBe(409);
    expect((await refused.json()).code).toBe('GRADE_UNPUBLISH_APPEAL_PENDING');
    await expect(unpublishDialog.getByRole('alert')).toBeVisible();
  } finally {
    await adminContext.close();
  }

  const lecturerContext = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const lecturerPage = await lecturerContext.newPage();
  try {
    await login(lecturerPage, lecturer, 'lecturer', port);
    await lecturerPage.goto('/en/dashboard/lecturer/grade-appeals', { waitUntil: 'commit' });
    await lecturerPage.waitForURL(/grade-appeals/, { timeout: 60_000 });
    const row = lecturerPage.locator('tr').filter({ hasText: studentReason });
    await expect(row).toBeVisible({ timeout: 30_000 });
    await row.getByRole('button', { name: 'Review appeal', exact: true }).click();
    const reviewDialog = lecturerPage.getByRole('dialog');
    await reviewDialog.locator('#grade-appeal-corrected-grade').fill('9.00');
    await reviewDialog.locator('#grade-appeal-review-reason').fill(reviewReason);
    await reviewDialog.getByRole('checkbox').check();
    const reviewResponse = lecturerPage.waitForResponse((response) =>
      new URL(response.url()).pathname === `/api/v1/lecturer/grade-appeals/${appeal.id}/review`
      && response.request().method() === 'POST');
    await reviewDialog.getByRole('button', { name: 'Save decision', exact: true }).click();
    const reviewed = await reviewResponse;
    expect(reviewed.ok(), 'lecturer review API response').toBeTruthy();
    const result = await reviewed.json() as { status: string; afterLetterGrade: string };
    expect(result.status).toBe('ACCEPTED');
    expect(result.afterLetterGrade).toBe('A+');
  } finally {
    await lecturerContext.close();
  }

  const transcriptResponse = await page.request.get('/api/v1/enrollments/my/transcript');
  expect(transcriptResponse.status()).toBe(200);
  const transcript = await transcriptResponse.json();
  const record = transcript.semesters
    .flatMap((semester: { records: Array<{ id: string; letterGrade: string | null }> }) => semester.records)
    .find((item: { id: string }) => item.id === enrollmentId);
  expect(record?.letterGrade).toBe('A+');

  await page.goto('/en/dashboard/transcript');
  const transcriptRow = page.locator('tr').filter({ hasText: 'APPEAL1E2E' });
  await expect(transcriptRow).toBeVisible({ timeout: 30_000 });
  await expect(transcriptRow).toContainText('A+');
});
