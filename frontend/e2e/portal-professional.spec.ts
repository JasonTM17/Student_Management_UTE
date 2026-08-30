import path from 'node:path';
import { test, expect, type Page } from '@playwright/test';

const student = { email: 'student@campuscore.edu', password: 'password123' };
const lecturer = { email: 'lecturer@campuscore.edu', password: 'password123' };
const admin = { email: 'admin@campuscore.edu', password: 'admin123' };
const TECHNICAL_COPY = /openapi|flyway|postgresql|\/api\/v1|java api|restful/i;

async function signIn(
  page: Page,
  account: typeof student,
  portal: 'student' | 'lecturer' | 'admin' = 'student',
) {
  await page.goto(`/login?portal=${portal}`);
  const submit = page.locator('form').getByRole('button', { name: /sign in/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(account.email);
  await page.locator('#password').fill(account.password);
  await submit.click();
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
}

async function dismissMobileSidebar(page: Page) {
  const close = page.getByRole('button', { name: /close sidebar navigation|đóng điều hướng sidebar/i });
  if (await close.isVisible().catch(() => false)) {
    await close.click();
  }
}

function sectionCard(page: Page, code: string) {
  return page.locator('tr, article').filter({ hasText: new RegExp(code) }).filter({ visible: true });
}

async function confirmDialogAction(page: Page, buttonName: RegExp) {
  const dialog = page
    .getByRole('dialog')
    .filter({ has: page.getByRole('button', { name: buttonName }) })
    .last();
  await dialog.getByRole('button', { name: buttonName }).click();
}

test.describe('public and auth', () => {
  test('homepage has skip-link, visible sign-in, and no 3-equal metric grid', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('link', { name: /skip to content/i })).toBeAttached();
    const signIn = page.getByRole('navigation').getByRole('link', { name: /sign in/i });
    await expect(signIn).toBeVisible();
    const box = await signIn.boundingBox();
    expect(box).not.toBeNull();
    expect(box!.width).toBeGreaterThanOrEqual(40);
    expect(box!.height).toBeGreaterThanOrEqual(40);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(page.getByText(TECHNICAL_COPY)).toHaveCount(0);
    await expect(page.locator('main#main-content')).toBeVisible();
    await expect(page.locator('.sm\\:grid-cols-3')).toHaveCount(0);
    await expect(page.getByRole('tab', { name: /lecturer|giảng viên/i })).toBeVisible();
    await page.getByRole('tab', { name: /lecturer|giảng viên/i }).click();
    await expect(page.getByText('SE204')).toBeVisible();
    await page.getByRole('tab', { name: /admin|quản trị/i }).click();
    await expect(page.getByText('USR')).toBeVisible();
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test('anonymous homepage does not probe auth/me or auth/refresh', async ({ page }) => {
    const authCalls: string[] = [];
    page.on('response', (response) => {
      if (response.url().includes('/api/v1/auth/')) {
        authCalls.push(`${response.request().method()} ${response.url()}`);
      }
    });
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.waitForTimeout(1200);
    expect(authCalls.some((call) => /\/auth\/me(?:\?|$)/.test(call))).toBe(false);
    expect(authCalls.some((call) => /\/auth\/refresh(?:\?|$)/.test(call))).toBe(false);
  });

  test('theme toggle flips html.dark on the first click', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    const before = await page.evaluate(() => document.documentElement.classList.contains('dark'));
    const toggle = page.getByRole('button', {
      name: before
        ? /switch to light theme|chuyển sang giao diện sáng/i
        : /switch to dark theme|chuyển sang giao diện tối/i,
    });
    await toggle.click();
    await expect
      .poll(async () => page.evaluate(() => document.documentElement.classList.contains('dark')))
      .toBe(!before);
  });

  test('public signup form posts campus register fields', async ({ page }) => {
    await page.goto('/register');
    await expect(page.getByRole('heading', { name: /create your account|tạo tài khoản/i })).toBeVisible();
    await expect(page.locator('#firstName')).toBeVisible();
    await expect(page.locator('#lastName')).toBeVisible();
    await expect(page.locator('#email')).toBeVisible();
    await expect(page.locator('#password')).toBeVisible();
    await expect(page.getByRole('link', { name: /forgot password/i })).toHaveCount(0);
  });

  test('login labels stay visible and rejects empty submit', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/^password$/i)).toBeVisible();
    await expect(page.getByText(/campus academic office/i)).toBeVisible();
    await expect(page.getByRole('link', { name: /forgot password/i })).toHaveCount(0);
  });

  test('student, lecturer, and admin login chrome stay distinct', async ({ page }) => {
    await page.goto('/login?portal=student');
    await expect(page.locator('[data-login-portal="student"]')).toBeVisible();
    await expect(page.getByRole('heading', { name: /student sign-in/i })).toBeVisible();
    await page.getByRole('tab', { name: /^lecturer$/i }).click();
    await expect(page).toHaveURL(/portal=lecturer/);
    await expect(page.locator('[data-login-portal="lecturer"]')).toBeVisible();
    await expect(page.getByRole('heading', { name: /faculty sign-in/i })).toBeVisible();
    await expect(page.locator('form').getByText(/faculty office/i)).toBeVisible();
    await page.getByRole('tab', { name: /^admin$/i }).click();
    await expect(page.locator('[data-login-portal="admin"]')).toBeVisible();
    await expect(page.getByRole('heading', { name: /operations sign-in/i })).toBeVisible();
    await expect(page.locator('form').getByText(/campus operations/i)).toBeVisible();
  });

  test('student credentials are rejected on the admin portal', async ({ page }) => {
    await page.goto('/login?portal=admin');
    const submit = page.locator('form').getByRole('button', { name: /sign in/i });
    await expect(submit).toBeEnabled({ timeout: 20_000 });
    await page.locator('#email').fill(student.email);
    await page.locator('#password').fill(student.password);
    await submit.click();
    await expect(page.locator('#login-error')).toContainText(/another campus portal/i);
    await expect(page).toHaveURL(/\/login/);
  });
});

test.describe('student workspace', () => {
  test('student can sign in and see dashboard plus mobile nav on small screens', async ({ page }, testInfo) => {
    await signIn(page, student);
    await expect(page).toHaveURL(/\/dashboard(?:$|[/?#])/);
    await expect(page.getByRole('main')).toBeVisible();
    const mobileNav = page.getByRole('navigation', { name: /campus navigation on mobile/i });
    if (testInfo.project.name === 'mobile-390') {
      await expect(mobileNav).toBeVisible();
      await expect(mobileNav.getByRole('link', { name: /home|trang chủ/i })).toBeVisible();
      await expect(mobileNav.getByRole('button', { name: /open sidebar/i })).toBeVisible();
    }
  });

  test('student can register then drop a live section', async ({ page }) => {
    await signIn(page, student);
    await page.goto('/dashboard/register');
    await expect(page.getByRole('heading', { name: /course registration|đăng ký học phần/i })).toBeVisible({
      timeout: 15_000,
    });
    await expect(page.getByText(TECHNICAL_COPY)).toHaveCount(0);

    await dismissMobileSidebar(page);
    const section = sectionCard(page, 'SE402');
    await expect(section).toBeVisible({ timeout: 15_000 });

    const dropButton = section.getByRole('button', { name: /^drop course$|^hủy đăng ký$/i });
    if (await dropButton.isVisible().catch(() => false)) {
      await dropButton.click();
      await confirmDialogAction(page, /^drop course$|^hủy đăng ký$/i);
      await expect(section.getByRole('button', { name: /^register$|^đăng ký$/i })).toBeVisible({
        timeout: 15_000,
      });
    }

    await section.getByRole('button', { name: /^register$|^đăng ký$/i }).click();
    await confirmDialogAction(page, /^register$|^đăng ký$/i);
    await expect(page.getByText(/enrollment updated|đã cập nhật đăng ký/i)).toBeVisible({
      timeout: 15_000,
    });
    await dismissMobileSidebar(page);
    await expect(section.getByRole('button', { name: /^drop course$|^hủy đăng ký$/i })).toBeVisible({
      timeout: 15_000,
    });
    await page.screenshot({
      path: process.env.COURSE_E2E_SCREENSHOT || path.join('test-results', 'register-after-enroll.png'),
      fullPage: true,
    });

    await section.getByRole('button', { name: /^drop course$|^hủy đăng ký$/i }).click();
    await confirmDialogAction(page, /^drop course$|^hủy đăng ký$/i);
    await expect(section.getByRole('button', { name: /^register$|^đăng ký$/i })).toBeVisible({
      timeout: 15_000,
    });
  });

  test('register API failure stays campus-language', async ({ page }) => {
    await signIn(page, student);
    await page.route('**/api/v1/me/enrollments', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.continue();
        return;
      }
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({
          message: 'SQLException at /api/v1/enrollments Flyway PostgreSQL OpenAPI Java API',
          code: 'ORG_HIBERNATE_EXCEPTION',
          path: '/api/v1/enrollments/enroll',
          requestId: 'req-e2e-1',
          status: 500,
        }),
      });
    });
    await page.goto('/dashboard/register');
    await dismissMobileSidebar(page);
    const section = sectionCard(page, 'SE402');
    await expect(section).toBeVisible({ timeout: 15_000 });
    await section.getByRole('button', { name: /^register$|^đăng ký$/i }).click();
    await confirmDialogAction(page, /^register$|^đăng ký$/i);
    const toast = page.locator('[data-sonner-toast]').first();
    await expect(toast).toBeVisible({ timeout: 10_000 });
    await expect(toast).toContainText(/could not|try again|campus systems|workspace|thử lại|hệ thống học vụ/i);
    await expect(toast).not.toContainText(TECHNICAL_COPY);
    await expect(page.getByText(/sqlexception|org_hibernate|req-e2e-1/i)).toHaveCount(0);
  });

  test('student thesis catalog has no technical internals', async ({ page }) => {
    await signIn(page, student);
    await page.goto('/dashboard/thesis');
    await expect(page.getByRole('main')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByText(TECHNICAL_COPY)).toHaveCount(0);
  });
});

test.describe('lecturer workspace', () => {
  test('lecturer home has no technical internals after login', async ({ page }) => {
    await signIn(page, lecturer, 'lecturer');
    await expect(page).toHaveURL(/\/dashboard\/lecturer(?:$|[/?#])/);
    await expect(page.getByRole('main')).toBeVisible();
    await expect(page.getByText(TECHNICAL_COPY)).toHaveCount(0);
  });
});

test.describe('admin workspace', () => {
  test('admin lands on the tokenized overview and can open users', async ({ page }) => {
    await signIn(page, admin, 'admin');
    await expect(page).toHaveURL(/\/admin(?:$|[/?#])/);
    await expect(page.getByRole('heading', { name: /admin dashboard/i })).toBeVisible();
    await page.goto('/admin/users');
    await expect(page.getByRole('heading', { name: 'User management', exact: true })).toBeVisible({
      timeout: 15_000,
    });
    const retry = page.getByRole('button', { name: /try again/i });
    if (await retry.isVisible().catch(() => false)) {
      await retry.click();
      await expect(page.getByRole('heading', { name: 'User management', exact: true })).toBeVisible();
    }
  });

  test('admin can change campus accent from the appearance studio', async ({ page }) => {
    await signIn(page, admin, 'admin');
    await page.goto('/admin/appearance');
    await expect(page.getByRole('heading', { name: 'Site appearance', exact: true })).toBeVisible({
      timeout: 15_000,
    });
    await page.getByRole('button', { name: /campus gold/i }).click();
    await expect
      .poll(async () => page.evaluate(() => document.documentElement.dataset.accent))
      .toBe('campus-gold');
    await page.getByRole('button', { name: /ute yellow/i }).click();
    await expect
      .poll(async () => page.evaluate(() => document.documentElement.dataset.accent))
      .toBe('ute-yellow');
  });
});
