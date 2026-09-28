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

async function expectSubmitReaction(page: import('@playwright/test').Page) {
  const alert = page.getByRole('alert');
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
