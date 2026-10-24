import { expect, test, type Locator, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { submitLiveLogin } from './fixtures/live-login';

test.describe.configure({ timeout: 150_000 });

const student = {
  email: process.env.E2E_CX5_STUDENT_EMAIL ?? process.env.E2E_STUDENT_EMAIL ?? 'student@campuscore.edu',
  passcode: process.env.E2E_CX5_STUDENT_PASSCODE ?? process.env.E2E_STUDENT_PASSCODE ?? '',
};
const admin = {
  email: process.env.E2E_CX5_ADMIN_EMAIL ?? process.env.E2E_ADMIN_EMAIL ?? 'admin@campuscore.edu',
  passcode: process.env.E2E_CX5_ADMIN_PASSCODE ?? process.env.E2E_ADMIN_PASSCODE ?? '',
};

async function signIn(page: Page, persona: 'student' | 'admin', account: { email: string; passcode: string }) {
  await page.goto(`/en/login?portal=${persona}`);
  await page.locator('#email').fill(account.email);
  await page.locator('#password').fill(account.passcode);
  const submit = page.locator('form').getByRole('button', { name: /sign in/i });
  await submitLiveLogin(page, submit, '3901');
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/);
}

async function expectAnyVisible(locator: Locator) {
  await locator.first().waitFor({ state: 'attached', timeout: 30_000 }).catch(() => {});
  for (let index = 0, count = await locator.count(); index < count; index += 1) {
    if (await locator.nth(index).isVisible()) {
      return;
    }
  }
  expect(false, 'Expected at least one matching curriculum marker to be visible').toBe(true);
}

test('student registers curriculum defaults, drops one, and Academic Affairs previews the run', async ({ page, browser }, testInfo) => {
  test.skip(!student.passcode || !admin.passcode, 'Requires the isolated CX5 demo accounts.');

  await signIn(page, 'student', student);
  const planResponsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return url.pathname === '/api/v1/me/registration/curriculum-plan'
      && response.request().method() === 'GET';
  });
  await page.goto('/en/dashboard/register');
  const planResponse = await planResponsePromise;
  expect(planResponse.status()).toBe(200);
  const plan = await planResponse.json() as {
    semesterId: string;
    courses: Array<{ courseCode: string; state: string }>;
  };
  expect(plan.courses.length).toBeGreaterThan(0);

  const planPanel = page.getByTestId('curriculum-plan-panel');
  await expect(planPanel.getByRole('heading', { name: 'Curriculum plan' })).toBeVisible();
  await expect(planPanel.getByRole('button', { name: 'Register my curriculum courses' })).toBeEnabled();
  const planAccessibility = await new AxeBuilder({ page })
    .include('[data-testid="curriculum-plan-panel"]')
    .analyze();
  expect(planAccessibility.violations).toEqual([]);
  for (const width of [375, 768, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    await expect(planPanel).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
  }
  await page.setViewportSize({ width: 1280, height: 900 });
  const registerResponsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return url.pathname === '/api/v1/me/registration/curriculum-plan/register'
      && response.request().method() === 'POST';
  });
  await planPanel.getByRole('button', { name: 'Register my curriculum courses' }).click();
  const registerResponse = await registerResponsePromise;
  expect(registerResponse.status()).toBe(200);
  const registered = await registerResponse.json() as {
    registeredCount: number;
    courses: Array<{ courseCode: string; enrollmentId?: string | null }>;
  };
  expect(registered.registeredCount).toBeGreaterThan(0);
  const droppedCourseCode = registered.courses.find((course) => course.enrollmentId)?.courseCode;
  expect(droppedCourseCode).toBeTruthy();

  await page.goto('/en/dashboard/schedule');
  const curriculumMarkers = page.getByText('Curriculum', { exact: true });
  await expectAnyVisible(curriculumMarkers);
  await expect(curriculumMarkers.first()).toBeAttached();
  await expect(page.getByText(droppedCourseCode!, { exact: true }).first()).toBeVisible();
  for (const width of [375, 768, 1280]) {
    await page.setViewportSize({ width, height: 900 });
    await expectAnyVisible(curriculumMarkers);
    expect(await page.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
  }
  await page.setViewportSize({ width: 1280, height: 900 });

  await page.goto('/en/dashboard/enrollments');
  await page.getByRole('tab', { name: /Enrollment Records/ }).click();
  const enrollmentRow = page.locator('tr').filter({ hasText: droppedCourseCode! }).first();
  await expect(enrollmentRow.getByText('Curriculum', { exact: true })).toBeVisible();
  await enrollmentRow.getByRole('button', { name: new RegExp(`Drop course ${droppedCourseCode}`) }).click();
  const confirmDialog = page.getByRole('dialog');
  await expect(confirmDialog).toBeVisible();
  const dropResponsePromise = page.waitForResponse((response) => {
    const url = new URL(response.url());
    return /^\/api\/v1\/me\/enrollments\/[^/]+\/drop$/.test(url.pathname)
      && response.request().method() === 'POST';
  });
  await confirmDialog.getByRole('button', { name: 'Drop course', exact: true }).click();
  expect((await dropResponsePromise).status()).toBe(200);

  const adminContext = await browser.newContext({ baseURL: String(testInfo.project.use.baseURL) });
  try {
    const adminPage = await adminContext.newPage();
    await signIn(adminPage, 'admin', admin);
    await adminPage.goto('/en/admin/registration/curriculum-defaults');
    const semesterPicker = adminPage.getByRole('combobox', { name: 'Semester', exact: true });
    await expect(semesterPicker).toBeVisible();
    const adminAccessibility = await new AxeBuilder({ page: adminPage })
      .include('[data-testid="curriculum-defaults-panel"]')
      .analyze();
    expect(adminAccessibility.violations).toEqual([]);
    await semesterPicker.selectOption(plan.semesterId);
    for (const width of [375, 768, 1280]) {
      await adminPage.setViewportSize({ width, height: 900 });
      await expect(semesterPicker).toBeVisible();
      expect(await adminPage.evaluate(() => document.documentElement.scrollWidth)).toBeLessThanOrEqual(width);
    }
    await adminPage.setViewportSize({ width: 1280, height: 900 });
    const dryRunResponsePromise = adminPage.waitForResponse((response) => {
      const url = new URL(response.url());
      return url.pathname === '/api/v1/admin/registration/curriculum-defaults/dry-run'
        && response.request().method() === 'POST';
    });
    await adminPage.getByRole('button', { name: 'Dry run', exact: true }).click();
    const dryRunResponse = await dryRunResponsePromise;
    expect(dryRunResponse.status()).toBe(200);
    const dryRun = await dryRunResponse.json() as {
      students: number;
      courses: number;
      wouldRegister: number;
      skipped: number;
      skippedByReason: Array<{ reasonCode: string; count: number }>;
    };
    await expect(adminPage.getByRole('heading', { name: 'Run results' })).toBeVisible();
    await expect(adminPage.getByText('Students in this batch', { exact: true })).toBeVisible();
    await expect(adminPage.getByText('Mandatory courses', { exact: true })).toBeVisible();
    expect(dryRun.students).toBeGreaterThanOrEqual(0);
    expect(dryRun.courses).toBeGreaterThanOrEqual(0);
    expect(dryRun.wouldRegister + dryRun.skipped).toBeLessThanOrEqual(dryRun.courses);
    expect(dryRun.skippedByReason.find((reason) => reason.reasonCode === 'CURRICULUM_DEFAULT_DISMISSED')?.count ?? 0)
      .toBeGreaterThan(0);
  } finally {
    await adminContext.close();
  }
});
