import { expect, test, type Page } from '@playwright/test';

// Browser rendering regression with controlled API responses; no database writes.
const terms = [2, 1].map((number) => ({
  id: `term-${number}`, name: `Term ${number}`, nameEn: `Semester ${number}`,
  nameVi: `Học kỳ ${number}`, isCurrent: number === 2,
}));
const transcriptTerms = terms.map((term, index) => ({
  semesterId: term.id, semesterName: term.name,
  semesterNameEn: term.nameEn, semesterNameVi: term.nameVi,
  gpa: index === 0 ? 3.3 : 3, creditsEarned: 3, creditsAttempted: 3,
  records: [{
    id: term.id, courseCode: `TEST${index}`, courseName: 'Demo course',
    credits: 3, sectionCode: `SEC${index}`, lecturerName: 'Demo Lecturer',
    semester: term.name, semesterId: term.id, finalGrade: 8,
    letterGrade: index === 0 ? 'B+' : 'B', gradePoint: index === 0 ? 3.3 : 3,
    gradeStatus: 'PUBLISHED', enrollmentStatus: 'COMPLETED',
  }],
}));

async function mockStudent(page: Page, singleTerm = false) {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
  await page.context().addCookies([{
    name: 'cc_csrf', value: 'demo-render-fixture',
    url: test.info().project.use.baseURL as string,
  }]);
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    const payloads: Record<string, unknown> = {
      '/api/site-appearance': {},
      '/api/v1/auth/me': { id: 'demo-student', email: 'demo@example.test', firstName: 'Demo', lastName: 'Student', roles: ['STUDENT'] },
      '/api/v1/semesters': { data: terms },
      '/api/v1/notifications/my': { data: [] },
      '/api/v1/enrollments/my/transcript': {
        summary: { cumulativeGpa: 3.15, totalCreditsEarned: 6, totalCreditsAttempted: 6 },
        semesters: singleTerm ? transcriptTerms.slice(0, 1) : transcriptTerms,
      },
      '/api/v1/enrollments/my': [6, 7, 7].map((day, index) => ({
        id: `meeting-${index}`, status: 'CONFIRMED',
        section: {
          sectionNumber: `SECTION-${index}`, course: { code: `WEEKEND${index}`, name: 'Weekend course' },
          schedules: [{ dayOfWeek: day, startTime: '07:00', endTime: '09:30' }],
        },
      })),
    };
    if (!(pathname in payloads)) {
      errors.push(`Unexpected API request: ${pathname}`);
      await route.abort();
      return;
    }
    await route.fulfill({ json: payloads[pathname] });
  });
  return errors;
}

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
}

test('demo polish: weekend meetings remain visible in both languages', async ({ page }, testInfo) => {
  const errors = await mockStudent(page);
  for (const locale of ['en', 'vi']) {
    await page.goto(`/${locale}/dashboard/schedule`);
    const sunday = locale === 'en' ? 'Sunday' : 'Chủ nhật';
    if ((testInfo.project.use.viewport?.width ?? 1280) < 768) {
      const card = page.locator('div.rounded-lg').filter({
        has: page.getByRole('heading', { name: sunday, exact: true }),
      }).filter({ visible: true }).last();
      await expect(card).toBeVisible();
      await expect(card.getByText(/WEEKEND1/)).toBeVisible();
      await expect(card.getByText(/WEEKEND2/)).toBeVisible();
    } else {
      const grid = page.getByRole('table', { name: /Weekly timetable grid|Lưới thời khóa biểu/ });
      await expect(grid.getByText(sunday, { exact: true })).toBeVisible();
      for (const code of ['WEEKEND0', 'WEEKEND1', 'WEEKEND2']) {
        await expect(grid.getByText(code, { exact: true })).toBeVisible();
      }
    }
    await expect(page.getByText(`${sunday} - ${locale === 'en' ? 'Class' : 'Lớp học phần'} SECTION-1`, { exact: true })).toBeVisible();
    await noOverflow(page);
  }
  expect(errors).toEqual([]);
});

for (const singleTerm of [false, true]) {
  test(`demo polish: GPA chart renders ${singleTerm ? 'one term' : 'multiple terms'} and localized tooltips`, async ({ page }) => {
    const errors = await mockStudent(page, singleTerm);
    for (const locale of ['en', 'vi']) {
      await page.goto(`/${locale}/dashboard/transcript`);
      const chart = page.getByRole('img', { name: /GPA trend by semester|Xu hướng GPA theo học kỳ/ });
      await expect(chart).toBeVisible();
      await expect(chart.locator('circle')).toHaveCount(singleTerm ? 1 : 2);
      await expect(chart.locator('polyline')).toHaveCount(singleTerm ? 0 : 1);
      await expect(chart.locator('circle title')).toHaveText(
        (singleTerm ? [2] : [1, 2]).map((number) => `${locale === 'en' ? 'Semester' : 'Học kỳ'} ${number}: ${number === 1 ? '3.00' : '3.30'}`),
      );
      if (!singleTerm) {
        await page.getByRole('combobox', { name: /Select semester for transcript|Chọn học kỳ cho bảng điểm/ }).selectOption('term-1');
        const distribution = page.getByRole('img', { name: /Grade distribution|Phân bố xếp loại/ });
        await expect(distribution.locator('rect title')).toHaveText(['B: 1']);
        await expect(chart.locator('circle')).toHaveCount(2);
      }
      await noOverflow(page);
    }
    await page.screenshot({ path: test.info().outputPath('transcript-vi.png'), fullPage: true });
    expect(errors).toEqual([]);
  });
}
