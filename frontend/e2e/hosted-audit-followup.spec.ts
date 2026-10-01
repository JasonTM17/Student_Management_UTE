import AxeBuilder from '@axe-core/playwright';
import { expect, test } from '@playwright/test';

// Controlled read-only responses on the owned local stack; no academic writes.
test.skip(process.env.E2E_BASE_URL !== 'http://127.0.0.1:3110', 'Requires the owned local production stack.');

for (const locale of ['en', 'vi'] as const) {
  test(`attendance filters and selected states are accessible (${locale})`, async ({ page }) => {
    test.setTimeout(90_000);
    await page.goto('/vi/login?portal=lecturer');
    await page.locator('#email').fill('lecturer@campuscore.edu');
    await page.locator('#password').fill('password123');
    await page.locator('form').getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 30_000 });
    await page.goto(`/${locale}/dashboard/lecturer/attendance`);
    await expect(page.getByLabel(locale === 'vi' ? 'Chọn học kỳ' : 'Select semester', { exact: true })).toBeVisible();
    const section = page.getByLabel(locale === 'vi' ? 'Chọn lớp học phần' : 'Select class section', { exact: true });
    await section.selectOption('section-ux-audit');
    await expect(page.getByLabel(locale === 'vi' ? 'Ngày điểm danh' : 'Attendance date', { exact: true })).toBeVisible();
    const row = page.locator('tbody tr').first();
    await expect(row).toBeVisible();
    let academicWrites = 0;
    page.on('request', request => {
      if (request.url().includes('/api/v1/attendance') && request.method() !== 'GET') academicWrites++;
    });
    const statuses = locale === 'vi' ? ['Có mặt', 'Vắng', 'Đi muộn', 'Có phép'] : ['Present', 'Absent', 'Late', 'Excused'];
    for (const theme of ['light', 'dark']) {
      await page.evaluate(t => document.documentElement.classList.toggle('dark', t === 'dark'), theme);
      for (const status of statuses) {
        const button = row.getByRole('button', { name: status, exact: true });
        await button.click();
        await expect(button).toHaveAttribute('aria-pressed', 'true');
        const axe = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        expect(axe.violations.filter(v => v.impact === 'serious' || v.impact === 'critical')).toEqual([]);
      }
    }
    expect(academicWrites).toBe(0);
    await page.screenshot({ path: test.info().outputPath('attendance-accessible.png'), fullPage: false });
  });

  test(`pending thesis group explains the approval minimum (${locale})`, async ({ page }) => {
    await page.goto(`/vi/login?portal=student`);
    await page.locator('#email').fill('student@campuscore.edu');
    await page.locator('#password').fill('password123');
    await page.locator('form').getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 30_000 });
    let memberCount = 1;
    await page.route('**/api/v1/thesis/groups?**', async route => {
      if (route.request().method() !== 'GET') throw new Error('Unexpected academic mutation');
      const roundId = new URL(route.request().url()).searchParams.get('roundId');
      const members = Array.from({ length: memberCount }, (_, i) => ({
        studentId: i === 0 ? 'student-profile' : `readonly-member-${i}`,
        displayName: `Audit member ${i + 1}`, isLeader: i === 0,
      }));
      await route.fulfill({ json: [{ id: 'readonly-pending-group', roundId,
        leaderStudentId: 'student-profile', topicId: null, status: 'SUBMITTED',
        approvalStatus: 'PENDING', members, memberStudentIds: members.map(m => m.studentId),
      }] });
    });
    for (memberCount = 1; memberCount <= 4; memberCount++) {
      await page.goto(`/${locale}/dashboard/thesis`);
      await expect(page.getByText(`${memberCount}/4 ${locale === 'vi' ? 'thành viên' : 'members'}`, { exact: true })).toBeVisible();
      const helper = page.getByText(locale === 'vi'
        ? `Còn thiếu ${3 - memberCount} thành viên để được xét duyệt (tối thiểu 3).`
        : `${3 - memberCount} more needed for approval (minimum 3).`, { exact: true });
      if (memberCount < 3) await expect(helper).toBeVisible();
      else await expect(page.getByText(/more needed for approval|Còn thiếu \d+ thành viên/)).toHaveCount(0);
      if (memberCount === 4) await expect(page.getByText(locale === 'vi'
        ? 'Nhóm đã đạt số lượng tối đa 4 thành viên.' : 'The maximum group size is 4 members.', { exact: true })).toBeVisible();
      const dimensions = await page.evaluate(() => ({ w: window.innerWidth, doc: document.documentElement.scrollWidth }));
      expect(dimensions.doc).toBeLessThanOrEqual(dimensions.w + 1);
      if (memberCount === 1) await page.screenshot({ path: test.info().outputPath('pending-minimum.png'), fullPage: true });
    }
  });

  test(`demo login badge has readable contrast (${locale})`, async ({ page }) => {
    test.skip(process.env.E2E_DEMO_VISIBLE !== '1', 'Requires explicit demo-enabled production build.');
    await page.goto(`/${locale}/login?portal=student`);
    await page.locator('#email').fill('student@campuscore.edu');
    await expect(page.getByText(locale === 'vi' ? 'Tài khoản demo để trải nghiệm' : 'Demo experience account', { exact: true }).filter({ visible: true })).toBeVisible();
    const result = await new AxeBuilder({ page }).withRules(['color-contrast']).analyze();
    expect(result.violations).toEqual([]);
  });
}
