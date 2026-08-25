import { expect, test, type APIRequestContext, type Page } from '@playwright/test';

const mailpitBaseURL = process.env.E2E_MAILPIT_URL ?? 'http://127.0.0.1:8125';

type MailSummary = {
  ID?: string;
  Id?: string;
  Subject?: string;
  To?: Array<{ Address?: string }>;
};

type MailDetail = MailSummary & {
  Text?: string;
  HTML?: string;
};

async function waitForMail(
  request: APIRequestContext,
  recipient: string,
  purpose: 'verify' | 'reset',
): Promise<MailDetail> {
  const deadline = Date.now() + 30_000;
  while (Date.now() < deadline) {
    const listResponse = await request.get(`${mailpitBaseURL}/api/v1/messages`);
    if (listResponse.ok()) {
      const payload = await listResponse.json() as { messages?: MailSummary[]; Messages?: MailSummary[] };
      const messages = payload.messages ?? payload.Messages ?? [];
      const summary = messages.find((candidate) => {
        const subject = (candidate.Subject ?? '').toLocaleLowerCase('vi');
        const matchesPurpose = purpose === 'verify'
          ? subject.includes('verify') || subject.includes('xác minh')
          : subject.includes('reset') || subject.includes('đặt lại');
        return matchesPurpose
          && (candidate.To ?? []).some((address) => address.Address?.toLowerCase() === recipient.toLowerCase());
      });
      const id = summary?.ID ?? summary?.Id;
      if (id) {
        const detailResponse = await request.get(`${mailpitBaseURL}/api/v1/message/${encodeURIComponent(id)}`);
        if (detailResponse.ok()) return await detailResponse.json() as MailDetail;
      }
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error(`Timed out waiting for the ${purpose} message`);
}

function challengeToken(message: MailDetail): string {
  const body = `${message.Text ?? ''}\n${message.HTML ?? ''}`.replaceAll('&amp;', '&');
  const match = body.match(/[#?&]token=([A-Za-z0-9._~%+-]+)/);
  if (!match) throw new Error('Mail challenge link was missing its token parameter');
  return decodeURIComponent(match[1]);
}

async function login(page: Page, email: string, password: string) {
  await page.goto('/login');
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  await page.getByRole('button', { name: /sign in/i }).click();
  await expect(page).toHaveURL(/\/dashboard(?:$|[/?#])/);
}

test('student completes browser registration, verification, reset, and fresh login through captured mail', async ({ page, request }) => {
  const suffix = `${Date.now()}-${test.info().workerIndex}`;
  const email = `browser-auth-${suffix}@example.test`;
  const firstPassword = 'CampusCore!234';
  const secondPassword = 'CampusCore!567';

  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/register');
  await page.getByLabel('First name').fill('Browser');
  await page.getByLabel('Last name').fill('Student');
  await page.getByLabel('Email address').fill(email);
  await page.getByLabel('Password', { exact: true }).fill(firstPassword);
  await page.getByLabel('Confirm password').fill(firstPassword);
  await page.getByRole('button', { name: 'Create account' }).click();
  await expect(page.getByRole('status')).toContainText('Open the verification link');

  const verificationMail = await waitForMail(request, email, 'verify');
  expect((verificationMail.Text ?? '').length).toBeGreaterThan(100);
  expect((verificationMail.HTML ?? '').length).toBeGreaterThan(100);
  const verificationToken = challengeToken(verificationMail);

  const verificationResponse = await page.goto(`/verify-email?email=${encodeURIComponent(email)}#token=${encodeURIComponent(verificationToken)}`);
  expect(verificationResponse?.headers()['referrer-policy']).toBe('no-referrer');
  await expect(page).not.toHaveURL(/(?:\?|&)token=/);
  await expect(page.getByRole('status')).toContainText('Email verified');

  await login(page, email, firstPassword);
  await page.goto('/dashboard/sign-out');
  await expect(page).toHaveURL(/\/login(?:$|[/?#])/);

  await page.goto('/forgot-password');
  await page.getByLabel('Email address').fill(email);
  await page.getByRole('button', { name: 'Send reset link' }).click();
  await expect(page.getByRole('status')).toContainText('reset link is on the way');

  const resetMail = await waitForMail(request, email, 'reset');
  expect((resetMail.Text ?? '').length).toBeGreaterThan(100);
  expect((resetMail.HTML ?? '').length).toBeGreaterThan(100);
  const resetToken = challengeToken(resetMail);

  const resetResponse = await page.goto(`/reset-password#token=${encodeURIComponent(resetToken)}`);
  expect(resetResponse?.headers()['referrer-policy']).toBe('no-referrer');
  await expect(page).not.toHaveURL(/(?:\?|&)token=/);
  await page.getByLabel('New password').fill(secondPassword);
  await page.getByLabel('Confirm password').fill(secondPassword);
  await page.getByRole('button', { name: 'Reset password' }).click();
  await expect(page.getByRole('status')).toContainText('Password reset complete');

  await login(page, email, secondPassword);
});

test('auth lifecycle pages are responsive, keyboard reachable, localized, and no-referrer protected', async ({ page, request }) => {
  for (const route of ['/verify-email', '/reset-password']) {
    const response = await request.get(route);
    expect(response.headers()['referrer-policy']).toBe('no-referrer');
  }

  for (const viewport of [
    { width: 390, height: 844 },
    { width: 768, height: 900 },
    { width: 1440, height: 900 },
  ]) {
    await page.setViewportSize(viewport);
    for (const route of ['/register', '/verify-email', '/forgot-password', '/reset-password']) {
      await page.goto(route);
      await expect(page.locator('main')).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1)).toBe(true);
      await page.keyboard.press('Tab');
      expect(await page.evaluate(() => document.activeElement?.tagName)).not.toBe('BODY');
    }
  }

  await page.goto('/vi/register');
  await expect(page.getByRole('heading', { name: 'Tạo tài khoản' })).toBeVisible();
});
