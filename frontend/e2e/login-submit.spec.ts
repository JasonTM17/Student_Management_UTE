import { expect, test } from '@playwright/test';

/**
 * Roadmap 1.5 (a11y): a REAL pointer click on the login submit button and
 * Enter-in-password must both dispatch the form submit — no overlay, no
 * hydration-gated disabled state may swallow them.
 *
 * The probe account does not exist, so every accepted submit ends in the
 * invalid-credentials alert (or the backend-unavailable alert when no API is
 * reachable). An alert appearing is the proof that the submit went through the
 * React handler end to end; silence means a layer ate the interaction.
 *
 * Never run this against a real seeded account: failed attempts must not risk
 * lockout. `a11y-probe` cannot authenticate or lock anything.
 */

const PROBE_EMAIL = 'a11y-probe@campuscore.edu';
const PROBE_PASSWORD = 'probe-password-does-not-matter';

// Post-navigation waits only assert WHERE the router sent us; on a cold dev
// server the first compile of the destination route can outlast Playwright's
// 5s expect default, so these polls get a wider window.
const NAV_TIMEOUT = { timeout: 30_000 };

async function expectSubmitReaction(page: import('@playwright/test').Page) {
  const alert = page.locator('#login-error');
  await expect(alert).toBeVisible({ timeout: 15_000 });
}

test('a real pointer click on the submit button dispatches the login', async ({ page }) => {
  await page.goto('/login?portal=admin');
  await page.locator('#email').fill(PROBE_EMAIL);
  await page.locator('#password').fill(PROBE_PASSWORD);
  // getByRole + plain click: no force, no evaluate — the click must survive
  // the real hit-test path exactly like a user's pointer press.
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
  await expectSubmitReaction(page);
});

test('Enter in the password field submits the form', async ({ page }) => {
  await page.goto('/login?portal=admin');
  await page.locator('#email').fill(PROBE_EMAIL);
  await page.locator('#password').fill(PROBE_PASSWORD);
  await page.locator('#password').press('Enter');
  await expectSubmitReaction(page);
});

/**
 * The admin tab is not rendered: the tab strip offers only the student,
 * lecturer, and dean portals. A direct ?portal=admin deep link still shows
 * the admin chrome (heading, no forgot-password link) with no tab selected.
 */

test('the login tab strip renders student, lecturer, and dean tabs — never admin', async ({ page }) => {
  await page.goto('/login');
  const tabs = page.getByRole('tab');
  await expect(tabs).toHaveCount(3);
  await expect(page.getByRole('tab', { name: /trưởng khoa/i })).toBeVisible();
  await expect(page.getByRole('tab', { name: /quản trị|admin/i })).toHaveCount(0);

  await page.goto('/en/login');
  await expect(page.getByRole('tab')).toHaveCount(3);
  await expect(page.getByRole('tab', { name: /^dean$/i })).toBeVisible();
  await expect(page.getByRole('tab', { name: /quản trị|admin/i })).toHaveCount(0);
});

test('a portal=admin deep link keeps the admin chrome with no tab selected', async ({ page }) => {
  await page.goto('/en/login?portal=admin');
  await expect(page.locator('[data-login-portal="admin"]')).toBeVisible();
  await expect(page.getByRole('heading', { name: /operations sign-in/i })).toBeVisible();
  await expect(page.getByRole('tab')).toHaveCount(3);
  await expect(page.locator('[role="tab"][aria-selected="true"]')).toHaveCount(0);
  await expect(page.getByRole('link', { name: /forgot password|quên mật khẩu/i })).toHaveCount(0);
});

/**
 * Portal-mismatch recovery: a valid account submitted on the wrong tab is
 * still refused (the session is dropped), but the alert now carries an
 * action link to the portal the account actually belongs to — and the
 * typed credentials must survive the portal switch.
 *
 * Privileged accounts are the exception: an ADMIN/SUPER_ADMIN/TRUONG_KHOA
 * sign-in from a non-matching tab is accepted and routed straight to the
 * account's own portal instead of being refused.
 */

async function mockLoginAs(page: import('@playwright/test').Page, roles: string[]) {
  // Catch-all first (last-registered route wins): a routed privileged account
  // lands on the real destination page, whose data calls would otherwise 401
  // and bounce through the session-expired interceptor before the assertion.
  await page.route('**/api/v1/**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: '{}',
    }),
  );
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        user: {
          id: 'probe-user',
          email: PROBE_EMAIL,
          firstName: 'Probe',
          lastName: 'User',
          status: 'ACTIVE',
          mustChangePassword: false,
          roles,
        },
        accessToken: 'stub-access',
        refreshToken: 'stub-refresh',
      }),
    }),
  );
  // The verified-session gate probes /auth/me before committing the login —
  // it must answer the same identity so the portal-mismatch branch is reached.
  // The mismatch logout itself is allowed to no-op.
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'probe-user',
        email: PROBE_EMAIL,
        firstName: 'Probe',
        lastName: 'User',
        status: 'ACTIVE',
        mustChangePassword: false,
        roles,
      }),
    }),
  );
  await page.route('**/api/v1/auth/logout', (route) => route.fulfill({ status: 200 }));
}

async function fillAndSubmit(page: import('@playwright/test').Page) {
  await page.locator('#email').fill(PROBE_EMAIL);
  await page.locator('#password').fill(PROBE_PASSWORD);
  await page.getByRole('button', { name: 'Đăng nhập' }).click();
}

test('a student account on the admin tab links to the student portal', async ({ page }) => {
  await mockLoginAs(page, ['STUDENT']);
  await page.goto('/login?portal=admin');
  await fillAndSubmit(page);

  const alert = page.locator('#login-error');
  await expect(alert).toBeVisible({ timeout: 15_000 });
  const action = alert.getByRole('link');
  await expect(action).toBeVisible();
  await expect(action).toHaveAttribute('href', /portal=student/);

  await action.click();
  await expect(page).toHaveURL(/portal=student/, NAV_TIMEOUT);
  await expect(page.locator('#email')).toHaveValue(PROBE_EMAIL);
});

test('a lecturer account on the student tab links to the lecturer portal', async ({ page }) => {
  await mockLoginAs(page, ['LECTURER']);
  await page.goto('/login?portal=student');
  await fillAndSubmit(page);

  const alert = page.locator('#login-error');
  await expect(alert).toBeVisible({ timeout: 15_000 });
  const action = alert.getByRole('link');
  await expect(action).toBeVisible();
  await expect(action).toHaveAttribute('href', /portal=lecturer/);

  await action.click();
  await expect(page).toHaveURL(/portal=lecturer/, NAV_TIMEOUT);
  await expect(page.locator('#email')).toHaveValue(PROBE_EMAIL);
});

test('an admin account on the student tab is routed into the admin portal', async ({ page }) => {
  await mockLoginAs(page, ['ADMIN']);
  await page.goto('/login?portal=student');
  await fillAndSubmit(page);

  // No refusal: the privileged mismatch is accepted and routes like a
  // normal admin sign-in.
  await expect(page.locator('#login-error')).toHaveCount(0);
  await expect(page).toHaveURL(/\/admin(\?|$)/, NAV_TIMEOUT);
});

test('a TRUONG_KHOA-only account on the student tab is routed to the dean dashboard', async ({ page }) => {
  await mockLoginAs(page, ['TRUONG_KHOA']);
  await page.goto('/login?portal=student');
  await fillAndSubmit(page);

  await expect(page.locator('#login-error')).toHaveCount(0);
  await expect(page).toHaveURL(/\/dashboard\/dean(\?|$)/, NAV_TIMEOUT);
});

test('a TRUONG_KHOA account on the dean tab lands on the dean dashboard', async ({ page }) => {
  await mockLoginAs(page, ['TRUONG_KHOA']);
  await page.goto('/login?portal=dean');
  await fillAndSubmit(page);

  await expect(page.locator('#login-error')).toHaveCount(0);
  await expect(page).toHaveURL(/\/dashboard\/dean(\?|$)/, NAV_TIMEOUT);
});

test('a LECTURER+TRUONG_KHOA account on the dean tab lands on the dean dashboard', async ({ page }) => {
  await mockLoginAs(page, ['LECTURER', 'TRUONG_KHOA']);
  await page.goto('/login?portal=dean');
  await fillAndSubmit(page);

  await expect(page.locator('#login-error')).toHaveCount(0);
  await expect(page).toHaveURL(/\/dashboard\/dean(\?|$)/, NAV_TIMEOUT);
});

test('a LECTURER+TRUONG_KHOA account on the lecturer tab stays on the lecturer portal', async ({ page }) => {
  await mockLoginAs(page, ['LECTURER', 'TRUONG_KHOA']);
  await page.goto('/login?portal=lecturer');
  await fillAndSubmit(page);

  await expect(page.locator('#login-error')).toHaveCount(0);
  await expect(page).toHaveURL(/\/dashboard\/lecturer(\?|$)/, NAV_TIMEOUT);
});

test('an admin account on the dean tab is routed into the admin portal', async ({ page }) => {
  await mockLoginAs(page, ['ADMIN']);
  await page.goto('/login?portal=dean');
  await fillAndSubmit(page);

  await expect(page.locator('#login-error')).toHaveCount(0);
  await expect(page).toHaveURL(/\/admin(\?|$)/, NAV_TIMEOUT);
});

test('a TRUONG_KHOA-only account on the admin deep link lands on the admin thesis workspace', async ({ page }) => {
  await mockLoginAs(page, ['TRUONG_KHOA']);
  await page.goto('/login?portal=admin');
  await fillAndSubmit(page);

  await expect(page.locator('#login-error')).toHaveCount(0);
  await expect(page).toHaveURL(/\/admin\/thesis(\?|$)/, NAV_TIMEOUT);
});

test('a student account on the lecturer tab is refused with the recovery link', async ({ page }) => {
  await mockLoginAs(page, ['STUDENT']);
  await page.goto('/login?portal=lecturer');
  await fillAndSubmit(page);

  const alert = page.locator('#login-error');
  await expect(alert).toBeVisible({ timeout: 15_000 });
  const action = alert.getByRole('link');
  await expect(action).toBeVisible();
  await expect(action).toHaveAttribute('href', /portal=student/);
});

test('a lecturer account on the dean tab is refused with the recovery link', async ({ page }) => {
  await mockLoginAs(page, ['LECTURER']);
  await page.goto('/login?portal=dean');
  await fillAndSubmit(page);

  const alert = page.locator('#login-error');
  await expect(alert).toBeVisible({ timeout: 15_000 });
  const action = alert.getByRole('link');
  await expect(action).toBeVisible();
  await expect(action).toHaveAttribute('href', /portal=lecturer/);
});
