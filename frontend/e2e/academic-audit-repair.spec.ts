import { execFileSync } from 'node:child_process';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { expect, test, type Page } from '@playwright/test';

// Opt-in, real API tests on the disposable audit stack. No seeded records are
// edited: fixtures have their own IDs and are reset before every journey.
test.describe.configure({ timeout: 120_000 });
test.skip(process.env.E2E_AUDIT_LIVE !== '1', 'Requires the isolated audit fixture.');
const sectionId = 'section-ux-audit';

test.beforeEach(() => {
  const container = process.env.E2E_AUDIT_PG_CONTAINER;
  const database = process.env.E2E_AUDIT_PG_DATABASE;
  if (!container?.startsWith('campuscore-audit-') || !database?.startsWith('campuscore_audit_')) {
    throw new Error('Audit fixture requires a dedicated disposable database.');
  }
  const fixture = process.env.E2E_AUDIT_FIXTURE_SQL
    ? resolve(process.env.E2E_AUDIT_FIXTURE_SQL)
    : resolve(__dirname, 'fixtures/academic-audit.sql');
  const sql = readFileSync(fixture, 'utf8');
  execFileSync('docker', ['exec', '-i', container, 'psql', '-U', 'campuscore', '-d', database,
    '-v', 'ON_ERROR_STOP=1'], { input: sql, stdio: ['pipe', 'pipe', 'pipe'] });
});

async function signIn(page: Page, persona: 'student' | 'lecturer' | 'admin') {
  await page.goto(`/vi/login?portal=${persona}`);
  await page.locator('#email').fill(`${persona}@campuscore.edu`);
  await page.locator('#password').fill('password123');
  await page.locator('form').getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 60_000 });
}

async function openSectionEditor(page: Page) {
  await signIn(page, 'admin');
  await page.goto('/vi/admin/sections');
  await page.getByRole('button', { name: 'Chỉnh sửa lớp học phần 000-UX-AUDIT của AUDITUX', exact: true })
    .filter({ visible: true }).click();
  return page.getByRole('dialog', { name: 'Chỉnh sửa lớp học phần', exact: true });
}

test('academic audit: unsaved scores cannot release stale server grades', async ({ page }) => {
  await signIn(page, 'lecturer');
  await page.goto(`/vi/dashboard/lecturer/grades/${sectionId}`);
  const score = page.locator('[data-cell="grade-enrollment-ux-audit:processScore"]').filter({ visible: true });
  await expect(score).toHaveValue('6');
  await score.fill('9');
  await expect(page.getByRole('button', { name: 'Công bố điểm', exact: true })).toBeDisabled();
  await expect(page.getByText('Lưu các thay đổi trước khi công bố điểm.', { exact: true })).toBeVisible();
  await expect(page.getByText('Chưa lưu', { exact: true }).filter({ visible: true })).toHaveCount(1);
  await page.screenshot({ path: test.info().outputPath('unsaved-grades.png'), fullPage: true });
});

test('academic audit: saved scores publish and appear in the student account', async ({ page, browser }) => {
  await signIn(page, 'lecturer');
  await page.goto(`/vi/dashboard/lecturer/grades/${sectionId}`);
  await page.locator('[data-cell="grade-enrollment-ux-audit:processScore"]').filter({ visible: true }).fill('9');
  await page.locator('[data-cell="grade-enrollment-ux-audit:finalExamScore"]').filter({ visible: true }).fill('9');
  const saveResponse = page.waitForResponse(r => r.url().endsWith(`/sections/${sectionId}/grades`) && r.request().method() === 'PUT');
  await page.getByRole('button', { name: /^Lưu điểm/ }).click();
  expect((await saveResponse).status()).toBe(200);
  await expect(page.getByText('Chưa lưu', { exact: true }).filter({ visible: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Công bố điểm', exact: true })).toBeEnabled();
  await page.getByRole('button', { name: 'Công bố điểm', exact: true }).click();
  const publishResponse = page.waitForResponse(r => r.url().endsWith(`/sections/${sectionId}/grades/publish`));
  await page.getByRole('dialog').getByRole('button', { name: 'Công bố điểm', exact: true }).click();
  expect((await publishResponse).status()).toBe(200);
  const studentContext = await browser.newContext();
  try {
    const student = await studentContext.newPage();
    await signIn(student, 'student');
    await student.goto('/vi/dashboard/grades');
    const grades = await student.request.get('/api/v1/enrollments/my/grades');
    expect(grades.status()).toBe(200);
    expect((await grades.json()).find((row: { id: string }) => row.id === 'enrollment-ux-audit').finalGrade).toBe(9);
  } finally { await studentContext.close(); }
});

test('academic audit: a ten-student roster identifies only edited rows as unsaved', async ({ page }) => {
  await signIn(page, 'lecturer');
  await page.goto(`/vi/dashboard/lecturer/grades/${sectionId}`);
  const primary = page.locator('[data-cell="grade-enrollment-ux-audit:processScore"]').filter({ visible: true });
  const middle = page.locator('[data-cell="grade-enrollment-ux-audit-05:processScore"]').filter({ visible: true });
  await expect(primary).toHaveValue('6');
  await expect(middle).toHaveValue('6');
  await primary.fill('9');
  await middle.fill('8');
  await expect(page.getByText('Chưa lưu', { exact: true }).filter({ visible: true })).toHaveCount(2);
  await expect(page.getByRole('button', { name: 'Lưu điểm (2)', exact: true })).toBeEnabled();
  const response = page.waitForResponse(r => r.url().endsWith(`/sections/${sectionId}/grades`) && r.request().method() === 'PUT');
  await page.getByRole('button', { name: 'Lưu điểm (2)', exact: true }).click();
  expect((await response).status()).toBe(200);
  await expect(page.getByText('Chưa lưu', { exact: true }).filter({ visible: true })).toHaveCount(0);
});

test('academic audit: deleting the final meeting persists after reopening', async ({ page }) => {
  const dialog = await openSectionEditor(page);
  await dialog.getByRole('button', { name: 'Xóa lịch 1', exact: true }).click();
  const response = page.waitForResponse(r => r.url().endsWith(`/sections/${sectionId}`) && r.request().method() === 'PUT');
  await dialog.getByRole('button', { name: /Lưu thay đổi/ }).click();
  expect((await response).status()).toBe(200);
  await expect(dialog).not.toBeVisible();
  await page.getByRole('button', { name: 'Chỉnh sửa lớp học phần 000-UX-AUDIT của AUDITUX', exact: true })
    .filter({ visible: true }).click();
  await expect(page.getByRole('dialog', { name: 'Chỉnh sửa lớp học phần', exact: true })).toBeVisible();
  await expect(page.getByRole('dialog').getByRole('button', { name: 'Xóa lịch 1', exact: true })).toHaveCount(0);
});

test('academic audit: meeting controls keep keyboard focus during editing', async ({ page }) => {
  const dialog = await openSectionEditor(page);
  const start = dialog.locator('input[type="time"]').first();
  await start.fill('22:15');
  await expect(start).toBeFocused();
  await expect(start).toHaveValue('22:15');
  await expect(dialog.getByLabel('Ngày học', { exact: true })).toBeVisible();
  await expect(dialog.getByLabel('Giờ bắt đầu', { exact: true })).toBeVisible();
  await expect(dialog.getByLabel('Giờ kết thúc', { exact: true })).toBeVisible();
  await expect(dialog.getByLabel('Phòng học', { exact: true })).toBeVisible();
  await expect(dialog.getByRole('button', { name: 'Xóa lịch 1', exact: true })).toBeInViewport({ ratio: 1 });
  expect(await dialog.evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
  await dialog.getByRole('button', { name: 'Thêm lịch', exact: true }).click();
  const secondDelete = dialog.getByRole('button', { name: 'Xóa lịch 2', exact: true });
  await secondDelete.scrollIntoViewIfNeeded();
  await expect(secondDelete).toBeInViewport({ ratio: 1 });
  await page.screenshot({ path: test.info().outputPath('meeting-editor.png'), fullPage: true });
});

test('academic audit: failed schedule reads preserve meetings on metadata save', async ({ page }) => {
  await page.route(`**/api/v1/sections/${sectionId}`, route => route.request().method() === 'GET'
    ? route.fulfill({ status: 503, contentType: 'application/json', body: '{"code":"UNAVAILABLE"}' })
    : route.continue());
  const dialog = await openSectionEditor(page);
  await expect(dialog.getByText('Không tải được lịch học hiện có của lớp học phần này.', { exact: true })).toBeVisible();
  await dialog.getByLabel('Sức chứa', { exact: true }).fill('11');
  const response = page.waitForResponse(r => r.url().endsWith(`/sections/${sectionId}`) && r.request().method() === 'PUT');
  await dialog.getByRole('button', { name: /Lưu thay đổi/ }).click();
  const update = await response;
  expect(update.status()).toBe(200);
  expect(update.request().postDataJSON()).not.toHaveProperty('schedules');
  await page.unroute(`**/api/v1/sections/${sectionId}`);
  const detail = await page.request.get(`/api/v1/sections/${sectionId}`);
  expect((await detail.json()).schedules).toHaveLength(1);
});

test('academic audit: failed curriculum load explains failure and offers retry', async ({ page }) => {
  await page.route('**/api/v1/me/curriculum', route => route.fulfill({ status: 503,
    contentType: 'application/json', body: '{"code":"UNAVAILABLE"}' }));
  await signIn(page, 'student');
  await page.goto('/vi/dashboard/enrollments');
  await expect(page.getByText('Không tải được chương trình đào tạo.', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: /Thử lại/ }).filter({ visible: true })).toBeVisible();
  let enrollmentReloads = 0;
  page.on('request', request => {
    if (new URL(request.url()).pathname === '/api/v1/enrollments/my') enrollmentReloads++;
  });
  const curriculumResponse = page.waitForResponse(response => response.url().endsWith('/me/curriculum'));
  await page.getByRole('button', { name: /Thử lại/ }).filter({ visible: true }).click();
  await curriculumResponse;
  await expect(page.getByText('Không tải được chương trình đào tạo.', { exact: true })).toBeVisible();
  expect(enrollmentReloads).toBe(0);
  await page.unroute('**/api/v1/me/curriculum');
  const recovered = page.waitForResponse(response => new URL(response.url()).pathname === '/api/v1/me/curriculum');
  await page.getByRole('button', { name: /Thử lại/ }).filter({ visible: true }).click();
  expect((await recovered).status()).toBe(200);
  await expect(page.getByText('Không tải được chương trình đào tạo.', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Thử lại/ }).filter({ visible: true })).toHaveCount(0);
  expect(enrollmentReloads).toBe(0);
});

test('academic audit: login throttling gives a localized recovery instruction', async ({ page }) => {
  await page.route('**/api/v1/auth/login', route => route.fulfill({status:429,
    contentType:'application/json',headers:{'Retry-After':'60'},body:'{"code":"RATE_LIMIT_EXCEEDED"}'}));
  for (const locale of ['vi','en']) {
    await page.goto(`/${locale}/login?portal=student`);
    await page.locator('#email').fill('student@campuscore.edu');
    await page.locator('#password').fill('password123');
    await page.locator('form').getByRole('button',{name:locale==='vi'?'Đăng nhập':'Sign in',exact:true}).click();
    await expect(page.locator('#login-error')).toContainText(locale==='vi'
      ?'Bạn thao tác quá nhanh. Hãy chờ một lát rồi thử lại.'
      :'Too many requests. Please wait a moment and try again.');
  }
});


test('academic audit: teaching timetable opens details with keyboard', async ({ page }) => {
  await signIn(page, 'lecturer');
  await page.goto('/vi/dashboard/lecturer/schedule');
  const event = page.getByRole('button').filter({ hasText: 'AUDITUX' }).filter({ visible: true }).first();
  await expect(event).toBeVisible();
  await event.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('dialog')).toBeVisible();
  await expect(page.getByRole('dialog')).toContainText('AUDITUX');
  await page.keyboard.press('Escape');
  await expect(page.getByRole('dialog')).not.toBeVisible();
});

test('academic audit: assistant starters follow the lecturer role in both locales', async ({ page }) => {
  await signIn(page, 'lecturer');
  for (const [locale, prompt, studentPrompt] of [
    ['vi', 'Lịch giảng dạy tuần này', 'Lớp tôi đang học'],
    ['en', 'My teaching schedule this week', 'Which classes am I taking?'],
  ]) {
    await page.goto(`/${locale}/dashboard/lecturer/grades`);
    await page.getByRole('button', { name: locale === 'vi' ? 'Mở trợ lý CampusUTE' : 'Open CampusUTE assistant', exact: true }).click();
    await expect(page.getByRole('button', { name: prompt, exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: studentPrompt, exact: true })).toHaveCount(0);
  }
});
