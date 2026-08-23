import { expect, test, type Page } from '@playwright/test';
import { frontendBaseURL, seedBrowserSession } from './helpers';

async function seedLocaleCookie(page: Page, locale: 'en' | 'vi') {
  await page.context().addCookies([
    {
      name: 'cc_locale',
      value: locale,
      url: frontendBaseURL,
      sameSite: 'Lax',
      expires: Math.floor(Date.now() / 1000) + 60 * 60 * 24 * 365,
    },
  ]);
}

test('localized public home renders Vietnamese metadata and language chrome', async ({
  page,
}) => {
  await page.goto('/vi');

  await expect(page).toHaveURL(/\/vi$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  await expect(
    page.getByRole('button', { name: /Tiếng Anh|English/i }),
  ).toBeVisible();
});

test('localized auth routes set html lang and persist cc_locale across toggle changes', async ({
  page,
}) => {
  await page.goto('/vi/login');
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  await expect(page).toHaveURL(/\/vi\/login$/);

  await expect
    .poll(async () => {
      const cookies = await page.context().cookies([page.url()]);
      return cookies.find((cookie) => cookie.name === 'cc_locale')?.value ?? null;
    })
    .toBe('vi');

  await page.getByRole('button', { name: /Tiếng Anh|English/i }).click();
  await expect(page).toHaveURL(/\/en\/login$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');

  await expect
    .poll(async () => {
      const cookies = await page.context().cookies([page.url()]);
      return cookies.find((cookie) => cookie.name === 'cc_locale')?.value ?? null;
    })
    .toBe('en');
});

test('localized admin sign-in keeps the Vietnamese workspace prefix through logout', async ({
  page,
  playwright,
}) => {
  await seedBrowserSession(page, playwright, 'admin', { shared: true });
  await seedLocaleCookie(page, 'vi');
  await page.goto('/vi/admin');

  await expect(page).toHaveURL(/\/vi\/admin(?:\?.*)?$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  const usersLink = page.getByRole('link', { name: /Thêm người dùng/i });

  await expect(usersLink).toBeVisible({ timeout: 10_000 });
  await expect(usersLink).toHaveAttribute('href', '/vi/admin/users');
  await expect(page.locator('a[href="/vi/admin/analytics"]')).toHaveCount(0);
  await expect(page.locator('a[href="/vi/admin/invoices"]')).toHaveCount(0);

  await page.getByRole('button', { name: /Đăng xuất|Sign out/i }).click();
  await expect(page).toHaveURL(/\/vi\/login(?:\?.*reason=signed-out.*)?$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
});

test('localized student workspace route keeps Vietnamese chrome with a shared session', async ({
  page,
  playwright,
}) => {
  await seedBrowserSession(page, playwright, 'student', { shared: true });
  await seedLocaleCookie(page, 'vi');
  await page.goto('/vi/dashboard');

  await expect(page).toHaveURL(/\/vi\/dashboard$/);
  await expect(page.locator('html')).toHaveAttribute('lang', 'vi');
  await expect(page.locator('a[href="/vi/dashboard/register"]').first()).toBeVisible({
    timeout: 10_000,
  });
  await expect(
    page.locator('a[href="/vi/dashboard/enrollments"]').first(),
  ).toBeVisible({ timeout: 10_000 });
});
