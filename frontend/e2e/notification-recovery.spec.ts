import { test, expect } from '@playwright/test';

test('failed mark-read restores the preview row and unread badge without duplicates', async ({ page }, testInfo) => {
  test.skip(testInfo.project.name !== 'mobile-390', 'This regression targets the mobile unread badge.');
  const errors: string[] = [];
  page.on('pageerror', error => errors.push(error.name));
  await page.goto('/vi/login?portal=student');
  await page.locator('#email').fill('student@campuscore.edu');
  await page.locator('#password').fill('password123');
  const loggedIn = page.waitForResponse(response =>
    new URL(response.url()).pathname === '/api/v1/auth/login'
    && response.request().method() === 'POST');
  await page.locator('form').getByRole('button', { name: /đăng nhập|sign in/i }).click();
  expect((await loggedIn).status()).toBe(200);
  await expect(page).not.toHaveURL(/\/login/);
  let writes = 0;
  let releaseWrite!: () => void;
  const writeGate = new Promise<void>(resolve => { releaseWrite = resolve; });
  await page.route('**/api/v1/notifications/my**', async route => {
    const request = route.request();
    const path = new URL(request.url()).pathname;
    if (request.method() === 'PATCH' && path.endsWith('/read')) {
      writes += 1;
      await writeGate;
      return route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"UNAVAILABLE"}' });
    }
    if (path.endsWith('/unread-count')) {
      return route.fulfill({ json: { unreadCount: 1 } });
    }
    return route.fulfill({ json: { data: [{
      id: '55555555-5555-4555-8555-555555555555', title: 'Fixture cần đọc',
      content: 'Thông báo thử nghiệm', isRead: false, link: '/dashboard',
      createdAt: '2026-10-03T00:00:00Z',
    }], meta: { total: 1 } } });
  });
  await page.goto('/vi/dashboard');
  const menu = page.getByRole('navigation').getByRole('button', { name: 'Mở thanh điều hướng' });
  await expect(menu).toContainText('1');
  const bell = page.locator('button[aria-controls="dashboard-notifications-panel"]');
  await bell.click();
  const panel = page.locator('#dashboard-notifications-panel');
  const row = panel.getByRole('link', { name: /Fixture cần đọc/ });
  await expect(row).toHaveCount(1);
  await row.click();
  await expect.poll(() => writes).toBe(1);
  await expect(menu).not.toContainText('1');
  releaseWrite();
  await expect(page.getByText('Không thể cập nhật thông báo này. Hãy thử lại.', { exact: true })).toBeVisible();
  await expect(menu).toContainText('1');
  await bell.click();
  await expect(row).toHaveCount(1);
  await expect(row).toBeVisible();
  expect(writes).toBe(1);
  expect(errors).toEqual([]);
});
