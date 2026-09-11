import { expect, test, type Page } from '@playwright/test';

test.describe.configure({ timeout: 90_000 });

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
const curriculumCourses = [
  {
    courseId: 'course-in-progress',
    code: 'TEST0',
    name: 'Active demo course',
    nameEn: 'Active demo course',
    nameVi: 'Môn học đang diễn ra',
    credits: 3,
    year: 1,
    semester: 1,
    isMandatory: true,
    status: 'IN_PROGRESS',
    finalGrade: null,
    letterGrade: null,
  },
  {
    courseId: 'course-completed',
    code: 'TEST1',
    name: 'Completed demo course',
    nameEn: 'Completed demo course',
    nameVi: 'Môn học đã hoàn tất',
    credits: 3,
    year: 1,
    semester: 2,
    isMandatory: true,
    status: 'COMPLETED',
    finalGrade: 8,
    letterGrade: 'B',
  },
  {
    courseId: 'course-not-completed',
    code: 'TEST2',
    name: 'Future demo course',
    nameEn: 'Future demo course',
    nameVi: 'Môn học chưa hoàn tất',
    credits: 3,
    year: 2,
    semester: 1,
    isMandatory: false,
    status: 'NOT_STARTED',
    finalGrade: null,
    letterGrade: null,
  },
];
const gradeRecord = {
  id: 'grade-row-midterm-final',
  courseCode: 'AI201',
  courseName: 'Applied AI',
  courseNameEn: 'Applied AI',
  courseNameVi: 'Trí tuệ nhân tạo ứng dụng',
  credits: 3,
  sectionCode: 'AI201-01',
  lecturerName: 'Demo Lecturer',
  semester: 'Term 2',
  semesterNameEn: 'Semester 2',
  semesterNameVi: 'Học kỳ 2',
  semesterId: 'term-2',
  finalGrade: 8.6,
  letterGrade: 'A-',
  gradePoint: 3.5,
  gradeStatus: 'PUBLISHED',
  enrollmentStatus: 'COMPLETED',
};
const gradeBreakdown = {
  enrollment: {
    id: gradeRecord.id,
    studentId: 'demo-student',
    studentName: 'Demo Student',
    courseCode: gradeRecord.courseCode,
    courseName: gradeRecord.courseName,
    courseNameEn: gradeRecord.courseNameEn,
    courseNameVi: gradeRecord.courseNameVi,
    semester: gradeRecord.semester,
    semesterNameEn: gradeRecord.semesterNameEn,
    semesterNameVi: gradeRecord.semesterNameVi,
  },
  grades: [
    {
      id: 'grade-midterm',
      gradeItemId: 'midterm',
      gradeItemName: 'Midterm Exam',
      gradeItemType: 'MIDTERM',
      score: 8.4,
      maxScore: 10,
      weight: 0.4,
    },
    {
      id: 'grade-final',
      gradeItemId: 'final',
      gradeItemName: 'Final Exam',
      gradeItemType: 'FINAL',
      score: 8.8,
      maxScore: 10,
      weight: 0.6,
    },
  ],
  calculatedTotal: 8.64,
  totalWeight: 1,
};
const adminEnrollment = {
  id: 'enrollment-zero-grade',
  studentId: 'student-row-001',
  sectionId: 'section-admin-01',
  semesterId: 'term-2',
  status: 'COMPLETED',
  enrolledAt: '2026-09-03T08:00:00Z',
  finalGrade: 0,
  letterGrade: 'F',
  student: {
    studentCode: 'S2026001',
    user: {
      firstName: 'Demo',
      lastName: 'Student',
      email: 'demo.student@example.test',
    },
  },
  section: {
    sectionNumber: '01',
    course: {
      code: 'SE101',
      name: 'Software Engineering',
      nameEn: 'Software Engineering',
      nameVi: 'Kỹ thuật phần mềm',
    },
    lecturer: { user: { firstName: 'Demo', lastName: 'Lecturer' } },
  },
  semester: {
    name: 'Term 2',
    nameEn: 'Semester 2',
    nameVi: 'Học kỳ 2',
    type: 'MAIN',
  },
};
const thesisRound = {
  id: 'thesis-round-demo',
  name: 'Thesis registration 2026',
  thesisType: 'Capstone',
  registrationStart: '2026-09-01T00:00:00Z',
  registrationEnd: '2026-09-30T00:00:00Z',
  proposalPublishAt: '2026-10-05T00:00:00Z',
  status: 'REGISTRATION_OPEN',
};
const thesisTopic = {
  id: 'thesis-topic-demo',
  roundId: thesisRound.id,
  departmentId: 'department-demo',
  title: 'Campus analytics thesis',
  description: 'Build practical analytics for academic advising.',
  maxGroups: 3,
  status: 'PUBLISHED',
  createdBy: 'demo-lecturer',
};
const thesisGroup = {
  id: 'thesis-group-demo',
  roundId: thesisRound.id,
  leaderStudentId: 'student-profile',
  topicId: thesisTopic.id,
  status: 'DRAFT',
  approvalStatus: 'PENDING',
  rejectionReason: null,
  memberStudentIds: ['student-profile', 'external-partner'],
  members: [
    {
      studentId: 'student-profile',
      studentNumber: 'S20240001',
      displayName: 'Demo Student',
      contact: 'demo@example.test',
      isExternal: false,
      isLeader: true,
      memberOrder: 1,
    },
    {
      studentId: 'external-partner',
      displayName: 'Cross School Partner',
      contact: 'HCMC Partner Lab',
      isExternal: true,
      isLeader: false,
      memberOrder: 2,
    },
  ],
};

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
      '/api/v1/auth/me': {
        id: 'demo-student',
        email: 'demo@example.test',
        firstName: 'Demo',
        lastName: 'Student',
        roles: ['STUDENT'],
        studentId: 'student-profile',
      },
      '/api/v1/semesters': { data: terms },
      '/api/v1/notifications/my': { data: [] },
      '/api/v1/assistant/conversations': [],
      '/api/v1/announcements/my': {
        data: [{
          id: 'notice-weekend-slides',
          title: 'Week 1 slides',
          content: '<p>Slides and lecture notes for the enrolled class.</p>',
          priority: 'NORMAL',
          createdAt: '2026-09-09T00:00:00Z',
          isGlobal: false,
          sectionId: 'section-se101-01',
          courseCode: 'WEEKEND0',
          sectionNumber: 'SECTION-0',
        }],
        meta: { total: 1, page: 1, limit: 20, totalPages: 1 },
      },
      '/api/v1/registration/rounds': [{
        id: 'round-demo', semesterId: 'term-2', name: 'Registration',
        kind: 'REGISTRATION', status: 'OPEN', windowStart: '2026-09-01T00:00:00Z',
        windowEnd: '2026-09-30T00:00:00Z', creditLimit: 18,
      }],
      '/api/v1/me/registration/sections': [
        {
          id: 'section-se101-01', sectionNumber: '01', courseId: 'course-se101',
          courseCode: 'SE101', courseName: 'Software Engineering', credits: 3,
          capacity: 40, enrolledCount: 28, remainingSeats: 12, status: 'OPEN',
          scheduleConflict: false, alreadyEnrolled: true,
        },
        {
          id: 'section-se101-02', sectionNumber: '02', courseId: 'course-se101',
          courseCode: 'SE101', courseName: 'Software Engineering', credits: 3,
          capacity: 40, enrolledCount: 30, remainingSeats: 10, status: 'OPEN',
          scheduleConflict: false, alreadyEnrolled: false,
        },
        {
          id: 'section-ai201-01', sectionNumber: '01', courseId: 'course-ai201',
          courseCode: 'AI201', courseName: 'Applied AI', credits: 3,
          capacity: 35, enrolledCount: 18, remainingSeats: 17, status: 'OPEN',
          scheduleConflict: false, alreadyEnrolled: false,
        },
      ],
      '/api/v1/enrollments/my/transcript': {
        summary: { cumulativeGpa: 3.15, totalCreditsEarned: 6, totalCreditsAttempted: 6 },
        semesters: singleTerm ? transcriptTerms.slice(0, 1) : transcriptTerms,
      },
      '/api/v1/enrollments/my/grades': [gradeRecord],
      '/api/v1/grades/student-grades/enrollment/grade-row-midterm-final': gradeBreakdown,
      '/api/v1/me/curriculum': {
        curriculum: {
          id: 'curriculum-demo', code: 'SE-DEMO', name: 'Demo curriculum',
          nameEn: 'Demo curriculum', nameVi: 'Chương trình mô phỏng', totalCredits: 9,
        },
        courses: singleTerm ? curriculumCourses.slice(0, 1) : curriculumCourses,
      },
      '/api/v1/thesis/rounds': [thesisRound],
      '/api/v1/thesis/topics': [thesisTopic],
      '/api/v1/thesis/groups': [thesisGroup],
      '/api/v1/enrollments/my': [1, 7, 1].map((day, index) => ({
        id: `meeting-${index}`, sectionId: index === 0 ? 'section-se101-01' : `weekend-${index}`, status: 'CONFIRMED',
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

async function mockAdminEnrollments(page: Page) {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(message.text());
  });
  await page.context().addCookies([{
    name: 'cc_csrf', value: 'admin-render-fixture',
    url: test.info().project.use.baseURL as string,
  }]);
  await page.route('**/api/**', async (route) => {
    const pathname = new URL(route.request().url()).pathname;
    const payloads: Record<string, unknown> = {
      '/api/site-appearance': {},
      '/api/v1/auth/me': {
        id: 'demo-admin',
        email: 'admin@example.test',
        firstName: 'Demo',
        lastName: 'Admin',
        roles: ['ADMIN'],
      },
      '/api/v1/semesters': { data: [terms[0]], meta: { total: 1, page: 1, limit: 100, totalPages: 1 } },
      '/api/v1/courses': {
        data: [{ id: 'course-se101', code: 'SE101', name: 'Software Engineering', nameEn: 'Software Engineering', nameVi: 'Kỹ thuật phần mềm' }],
        meta: { total: 1, page: 1, limit: 100, totalPages: 1 },
      },
      '/api/v1/enrollments': {
        data: [adminEnrollment],
        meta: { total: 1, page: 1, limit: 20, totalPages: 1 },
      },
      '/api/v1/enrollments/enrollment-zero-grade': adminEnrollment,
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
    const sunday = locale === 'en' ? 'Sunday' : 'Chủ Nhật';
    const saturday = locale === 'en' ? 'Saturday' : 'Thứ Bảy';
    if ((testInfo.project.use.viewport?.width ?? 1280) < 768) {
      const sundayCard = page.locator('div.rounded-xl').filter({
        has: page.getByRole('heading', { name: sunday, exact: true }),
      }).filter({ visible: true }).last();
      await expect(sundayCard).toBeVisible();
      await expect(sundayCard.getByText('WEEKEND0', { exact: true })).toBeVisible();
      await expect(sundayCard.getByText('WEEKEND2', { exact: true })).toBeVisible();

      const saturdayCard = page.locator('div.rounded-xl').filter({
        has: page.getByRole('heading', { name: saturday, exact: true }),
      }).filter({ visible: true }).last();
      await expect(saturdayCard).toBeVisible();
      await expect(saturdayCard.getByText('WEEKEND1', { exact: true })).toBeVisible();
    } else {
      const grid = page.getByRole('table', { name: /Weekly timetable grid|Lưới thời khóa biểu/ });
      await expect(grid.getByText(sunday, { exact: true })).toBeVisible();
      for (const code of ['WEEKEND0', 'WEEKEND2']) {
        await expect(grid.getByText(code, { exact: true })).toBeVisible();
      }
      await expect(grid.getByText(saturday, { exact: true })).toBeVisible();
      await expect(grid.getByText('WEEKEND1', { exact: true })).toBeVisible();
    }
    await noOverflow(page);
  }
  expect(errors).toEqual([]);
});

test('demo polish: feedback surfaces stay scannable on registration and schedule', async ({ page }, testInfo) => {
  const errors = await mockStudent(page);
  const width = testInfo.project.use.viewport?.width ?? 1280;

  await page.goto('/en/dashboard/register');
  await expect(page.getByLabel('Course code or class')).toBeVisible();
  await expect(page.getByLabel('Course name')).toBeVisible();
  await expect(page.getByText('SE101', { exact: true }).filter({ visible: true }).first()).toBeVisible();
  await expect(page.getByText('2 classes').filter({ visible: true }).first()).toBeVisible();
  await expect(page.getByRole('button', { name: 'Drop course' }).first()).toBeVisible();
  await noOverflow(page);

  await page.goto('/en/dashboard/schedule');
  await expect(page.getByText('Weekly meetings')).toHaveCount(0);
  if (width >= 768) {
    const timetable = page.getByRole('table', { name: 'Weekly timetable grid' });
    await expect(timetable).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Class section list' })).toHaveCount(0);
    await page.getByRole('button', { name: 'List' }).click();
    await expect(page.getByRole('heading', { name: 'Class section list' })).toBeVisible();
    await expect(page.getByText('Saturday', { exact: true }).filter({ visible: true }).first()).toBeVisible();
    await expect(page.getByText(/WEEKEND1/).filter({ visible: true }).first()).toBeVisible();
  } else {
    await expect(page.getByText('Saturday', { exact: true }).filter({ visible: true }).first()).toBeVisible();
    await expect(page.getByText(/WEEKEND1/).filter({ visible: true }).first()).toBeVisible();
  }
  await noOverflow(page);
  expect(errors).toEqual([]);
});

test('demo polish: curriculum statuses are summarized and filterable', async ({ page }) => {
  const errors = await mockStudent(page);
  await page.goto('/en/dashboard/enrollments');

  const summary = page.getByLabel('Curriculum status summary');
  await expect(summary).toBeVisible();
  await expect(summary.getByText('Credit progress')).toBeVisible();
  await expect(summary.getByText(/3 \/ 9 credits/)).toBeVisible();
  for (const label of ['Completed', 'In Progress', 'Not completed']) {
    await expect(summary.getByText(label, { exact: true })).toBeVisible();
  }

  const notCompletedFilter = page.getByRole('button', {
    name: 'Not completed (1)',
  });
  await expect(notCompletedFilter).toHaveAttribute('aria-pressed', 'false');
  await notCompletedFilter.click();
  await expect(notCompletedFilter).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByText('TEST2', { exact: true }).filter({ visible: true })).toHaveCount(1);
  await expect(page.getByText('TEST0', { exact: true }).filter({ visible: true })).toHaveCount(0);
  await expect(page.getByText('Not Started', { exact: true })).toHaveCount(0);
  await noOverflow(page);
  expect(errors).toEqual([]);
});

test('demo polish: profile exposes photo upload and password visibility controls', async ({ page }) => {
  const errors = await mockStudent(page);
  await page.goto('/en/dashboard/profile');
  await expect(page.getByText('Upload photo')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Show password' })).toHaveCount(3);
  await noOverflow(page);
  expect(errors).toEqual([]);
});

test('demo polish: admin opens a student enrollment profile from the student cell', async ({ page }) => {
  const errors = await mockAdminEnrollments(page);
  await page.goto('/en/admin/enrollments');
  const studentButton = page.getByRole('button', {
    name: 'Open student enrollment profile for Demo Student',
  }).first();
  await expect(studentButton).toBeVisible();
  await studentButton.click();

  const dialog = page.getByRole('dialog', { name: 'Student enrollment profile' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Student profile')).toBeVisible();
  await expect(dialog.getByText('S2026001', { exact: true })).toBeVisible();
  await expect(dialog.getByText('student-row-001', { exact: true })).toBeVisible();
  await expect(dialog.getByText('enrollment-zero-grade', { exact: true })).toBeVisible();
  await expect(dialog.getByText('Final grade', { exact: true })).toBeVisible();
  await expect(dialog.locator('p').filter({ hasText: /^0$/ })).toBeVisible();
  await noOverflow(page);
  expect(errors).toEqual([]);
});

test('demo polish: grade rows open API-backed midterm and final breakdown', async ({ page }) => {
  const errors = await mockStudent(page);
  await page.goto('/en/dashboard/grades');

  const gradeButton = page.getByRole('button', {
    name: /Open grade breakdown for AI201 Applied AI/,
  }).first();
  await expect(gradeButton).toBeVisible();
  await gradeButton.focus();
  await expect(gradeButton).toBeFocused();
  await page.keyboard.press('Enter');

  const dialog = page.getByRole('dialog', { name: 'Course Grade Breakdown' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByText('Component Scores (Midterm & Final)')).toBeVisible();
  await expect(dialog.getByRole('row', {
    name: /Midterm Exam MIDTERM 40% 8\.4 \/ 10/,
  })).toBeVisible();
  await expect(dialog.getByRole('row', {
    name: /Final Exam FINAL 60% 8\.8 \/ 10/,
  })).toBeVisible();
  await expect(dialog.getByText('3.50', { exact: true })).toBeVisible();
  await noOverflow(page);
  expect(errors).toEqual([]);
});

test('demo polish: thesis member details render and add-member form stays concise', async ({ page }) => {
  const errors = await mockStudent(page);
  await page.goto('/en/dashboard/thesis');
  const main = page.locator('#dashboard-main-content');

  await expect(main.getByText('2/3 members')).toBeVisible();
  await expect(main.getByText('Demo Student', { exact: true })).toBeVisible();
  await expect(main.getByText('S20240001', { exact: true })).toBeVisible();
  await expect(main.getByText('demo@example.test', { exact: true })).toBeVisible();
  await expect(main.getByText('Cross School Partner', { exact: true })).toBeVisible();
  await expect(main.getByText('HCMC Partner Lab', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: 'Add member' }).click();
  const dialog = page.getByRole('dialog', { name: 'Add group member' });
  await expect(dialog).toBeVisible();
  await expect(dialog.getByLabel('Search internal students')).toBeVisible();
  await dialog.getByRole('button', { name: 'External student / Cross-major' }).click();
  await expect(dialog.getByLabel('Full name')).toBeVisible();
  await expect(dialog.getByLabel('Department / Institution / Contact')).toBeVisible();
  await noOverflow(page);
  expect(errors).toEqual([]);
});

test('demo polish: assistant answers schedule and materials from portal data', async ({ page }) => {
  const errors = await mockStudent(page);
  await page.goto('/en/dashboard');

  await page.getByRole('button', { name: 'CampusCore assistant' }).first().click();
  const composer = page.getByRole('textbox', {
    name: /Ask about registration, schedules, announcements/,
  });
  await composer.fill('Where are my course materials?');
  await page.getByRole('button', { name: 'Send message' }).click();
  const assistantMessage = page.getByRole('article', { name: 'Campus helpdesk' }).last();
  await expect(assistantMessage).toContainText('WEEKEND0');
  await expect(assistantMessage).toContainText('Week 1 slides');
  await expect(assistantMessage).toContainText('Slides and lecture notes');

  await composer.fill('What classes do I have on Sunday?');
  await page.getByRole('button', { name: 'Send message' }).click();
  await expect(page.getByRole('article', { name: 'Campus helpdesk' }).last()).toContainText('WEEKEND0');
  await expect(page.getByRole('article', { name: 'Campus helpdesk' }).last()).toContainText('07:00 - 09:30');
  await noOverflow(page);
  expect(errors).toEqual([]);
});

test('demo polish: dark mode keeps dashboard contrast and viewport fit', async ({ page }) => {
  const errors = await mockStudent(page);
  await page.goto('/en/dashboard');

  const themeToggle = page.getByRole('button', { name: 'Switch to dark theme' });
  await expect(themeToggle).toBeVisible();
  await themeToggle.click();
  await expect(page.getByRole('button', { name: 'Switch to light theme' })).toBeVisible();

  const themeState = await page.evaluate(() => {
    const root = document.documentElement;
    const body = document.body;
    const main = document.querySelector('main');
    return {
      dark: root.classList.contains('dark'),
      bodyBackground: getComputedStyle(body).backgroundColor,
      bodyColor: getComputedStyle(body).color,
      mainColor: main ? getComputedStyle(main).color : '',
    };
  });
  expect(themeState.dark).toBe(true);
  expect(themeState.bodyBackground).not.toBe(themeState.bodyColor);
  expect(themeState.mainColor).not.toBe('');
  await noOverflow(page);
  await page.screenshot({ path: test.info().outputPath('dashboard-dark.png'), fullPage: true });
  expect(errors).toEqual([]);
});

for (const singleTerm of [false, true]) {
  test(`demo polish: GPA chart renders ${singleTerm ? 'one term' : 'multiple terms'} and localized tooltips`, async ({ page }) => {
    const errors = await mockStudent(page, singleTerm);
    for (const locale of ['en', 'vi']) {
      await page.goto(`/${locale}/dashboard/transcript`);
      const semesterSelector = page.getByRole('combobox', {
        name: /Select semester for transcript|Chọn học kỳ cho bảng điểm/,
      });
      await expect(semesterSelector).toHaveCount(1);
      const chart = page.getByRole('img', { name: /GPA trend|Xu hướng GPA/ });
      await expect(chart).toBeVisible();
      await expect(chart.locator('polyline')).toHaveCount(singleTerm ? 0 : 3);
      const chartTitles = await chart.locator('circle title').allTextContents();
      const termNumbers = singleTerm ? [2] : [1, 2];
      expect(chartTitles.filter((title) => !title.endsWith('/10'))).toEqual(
        termNumbers.map((number) => `${locale === 'en' ? 'Semester' : 'Học kỳ'} ${number}: ${number === 1 ? '3.00' : '3.30'}`),
      );
      expect(chartTitles.filter((title) => title.endsWith('/10'))).toEqual(
        termNumbers.map((number) => `${locale === 'en' ? 'Semester' : 'Học kỳ'} ${number}: 8.0/10`),
      );
      if (!singleTerm) {
        await semesterSelector.selectOption('term-1');
        const distribution = page.getByRole('img', { name: /Grade distribution|Phân bố xếp loại/ });
        await expect(distribution.locator('rect title')).toHaveText(['B: 1']);
      }
      const bothLabel = locale === 'en' ? 'Both' : 'Song song';
      const semesterModeLabel = locale === 'en' ? 'By semester' : 'Theo học kỳ';
      const bothMode = page.getByRole('button', { name: bothLabel });
      const semesterMode = page.getByRole('button', { name: semesterModeLabel });
      await expect(bothMode).toHaveAttribute('aria-pressed', 'true');
      await semesterMode.click();
      await expect(semesterMode).toHaveAttribute('aria-pressed', 'true');
      await noOverflow(page);
    }
    await page.screenshot({ path: test.info().outputPath('transcript-vi.png'), fullPage: true });
    expect(errors).toEqual([]);
  });
}
