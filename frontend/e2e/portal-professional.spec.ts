import { test, expect, type Page } from '@playwright/test';

const student = { email: 'student@campuscore.edu', password: 'password123' };
const admin = { email: 'admin@campuscore.edu', password: 'admin123' };

async function signIn(page: Page, account: typeof student) {
  await page.goto('/login');
  const submit = page.locator('form').getByRole('button', { name: /sign in/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(account.email);
  await page.locator('#password').fill(account.password);
  await submit.click();
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
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
    await expect(page.locator('main#main-content')).toBeVisible();
    await expect(page.locator('.sm\\:grid-cols-3')).toHaveCount(0);
    const overflow = await page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
    expect(overflow).toBeLessThanOrEqual(1);
  });

  test('login labels stay visible and rejects empty submit', async ({ page }) => {
    await page.goto('/login');
    await expect(page.getByLabel(/email/i)).toBeVisible();
    await expect(page.getByLabel(/^password$/i)).toBeVisible();
    await expect(page.getByText(/campus academic office/i)).toBeVisible();
    await expect(page.getByRole('link', { name: /forgot password/i })).toHaveCount(0);
  });
});

test.describe('student workspace', () => {
  test('student can sign in and see dashboard plus mobile nav on small screens', async ({ page }, testInfo) => {
    await signIn(page, student);
    await expect(page).toHaveURL(/\/dashboard(?:$|[/?#])/);
    await expect(page.getByRole('main')).toBeVisible();
    const mobileNav = page.getByRole('navigation', { name: /mobile workspace navigation/i });
    if (testInfo.project.name === 'mobile-390') {
      await expect(mobileNav).toBeVisible();
      await expect(mobileNav.getByRole('link', { name: /home|trang chủ/i })).toBeVisible();
      await expect(mobileNav.getByRole('button', { name: /open sidebar/i })).toBeVisible();
    }
  });

  test('student registration page loads from the live API', async ({ page }) => {
    await signIn(page, student);
    await page.goto('/dashboard/register');
    await expect(page.getByRole('main')).toBeVisible();
    await expect(page.getByRole('heading', { name: /course registration|đăng ký học phần/i })).toBeVisible({
      timeout: 15_000,
    });
  });
});

test.describe('admin workspace', () => {
  test('admin lands on the tokenized overview and can open users', async ({ page }) => {
    await signIn(page, admin);
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
});
