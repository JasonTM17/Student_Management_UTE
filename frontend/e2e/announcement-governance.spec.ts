import { expect, test, type Page } from '@playwright/test';

const admin = {
  email: process.env.E2E_ADMIN_EMAIL ?? 'admin@campuscore.edu',
  passcode: process.env.E2E_ADMIN_PASSCODE ?? '',
};

async function login(page: Page) {
  if (!admin.passcode) {
    throw new Error('E2E_ADMIN_PASSCODE must be provided by the disposable test environment.');
  }
  await page.goto('/login?portal=admin');
  const submit = page.locator('form').getByRole('button', { name: /sign in/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(admin.email);
  await page.locator('#password').fill(admin.passcode);
  await submit.click();
  await expect(page).toHaveURL(/\/admin(?:$|[/?#])/, { timeout: 20_000 });
}

test('admin manages a student and lecturer announcement without exposing service jargon', async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  const title = `Campus notice ${testInfo.project.name}`;
  const updatedTitle = `${title} updated`;
  const editReason = 'Clarified the message for students and lecturers.';
  const archiveReason = 'Temporarily hidden while the schedule is reviewed.';
  const restoreReason = 'Schedule review completed.';

  await login(page);
  await page.goto('/admin/announcements');

  await expect(page.getByRole('heading', { name: 'Manage announcements' })).toBeVisible();
  await expect(page.locator('body')).not.toContainText(
    /\b(?:ACTIVE|ARCHIVED|STUDENT|LECTURER|SUPER_ADMIN)\b|audit trail|Announcement governance|\/api\/v1/,
  );

  await page.getByRole('button', { name: 'New announcement' }).click();
  let dialog = page.getByRole('dialog');
  await expect(dialog).toHaveAttribute('aria-modal', 'true');
  await dialog.getByLabel(/^Title/).fill(title);
  await dialog.getByLabel(/^Content/).fill('A clear message for both campus audiences.');
  await dialog.getByLabel('Campus-wide').uncheck();
  await dialog.getByLabel('Student', { exact: true }).check();
  await dialog.getByLabel('Lecturer', { exact: true }).check();

  await expect(dialog.getByText('Will be visible for this role')).toBeVisible();
  await dialog.getByRole('tab', { name: 'Lecturer' }).click();
  await expect(dialog.getByText('Will be visible for this role')).toBeVisible();
  await dialog.getByRole('button', { name: 'Publish announcement' }).click();

  let card = page.locator('article').filter({ hasText: title });
  await expect(card).toBeVisible();
  await card.getByRole('button', { name: `Edit: ${title}` }).click();

  dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^Title/).fill(updatedTitle);
  await dialog.getByLabel(/^Reason for change/).fill(editReason);
  await dialog.getByRole('button', { name: 'Save changes' }).click();

  card = page.locator('article').filter({ hasText: updatedTitle });
  await expect(card).toBeVisible();
  await expect(card).toContainText('Update 1');
  await card.getByRole('button', { name: `Change history: ${updatedTitle}` }).click();
  dialog = page.getByRole('dialog');
  await expect(dialog).toContainText(editReason);
  await expect(dialog).toContainText('Changed by: Demo Admin');
  await dialog.getByRole('button', { name: 'Close' }).click();

  await card.getByRole('button', { name: `Archive: ${updatedTitle}` }).click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^Reason for change/).fill(archiveReason);
  await dialog.getByRole('button', { name: 'Archive', exact: true }).click();
  await expect(page.locator('article').filter({ hasText: updatedTitle })).toHaveCount(0);

  await page.getByRole('tab', { name: 'Archived' }).click();
  card = page.locator('article').filter({ hasText: updatedTitle });
  await expect(card).toBeVisible();
  await expect(card).toContainText('Archived');
  await card.getByRole('button', { name: `Restore: ${updatedTitle}` }).click();
  dialog = page.getByRole('dialog');
  await dialog.getByLabel(/^Reason for change/).fill(restoreReason);
  await dialog.getByRole('button', { name: 'Restore', exact: true }).click();
  await expect(page.locator('article').filter({ hasText: updatedTitle })).toHaveCount(0);

  await page.getByRole('tab', { name: 'Visible' }).click();
  card = page.locator('article').filter({ hasText: updatedTitle });
  await expect(card).toBeVisible();
  await card.getByRole('button', { name: `Change history: ${updatedTitle}` }).click();
  dialog = page.getByRole('dialog');
  await expect(dialog).toContainText(editReason);
  await expect(dialog).toContainText(archiveReason);
  await expect(dialog).toContainText(restoreReason);
  await expect(dialog).toContainText('Restored');
  await expect(dialog).toContainText('Archived');
  await expect(dialog).toContainText('Changed');
});
