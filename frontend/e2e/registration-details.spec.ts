import { expect, test, type Page } from '@playwright/test';

test.describe.configure({ timeout: 90_000 });

// Controlled API fixtures exercise the rendered UI without database mutations.
const terms = [
  { id: 'term-3', name: 'Future term', nameEn: 'Semester 3', nameVi: 'Học kỳ 3', status: 'DRAFT' },
  { id: 'term-2', name: 'Current term', nameEn: 'Semester 2', nameVi: 'Học kỳ 2', status: 'IN_PROGRESS' },
  { id: 'term-1', name: 'Earlier term', nameEn: 'Semester 1', nameVi: 'Học kỳ 1', status: 'CLOSED' },
];
const knownSection = {
  id: 'section-mf101', sectionNumber: 'MF101-01', semesterId: 'term-3',
  course: { id: 'course-mf101', code: 'MF101', name: 'Methods', nameEn: 'Mathematical Methods', nameVi: 'Phương pháp toán học', credits: 3 },
  lecturer: { user: { firstName: 'Linh', lastName: 'Nguyễn' } },
  schedules: [
    { dayOfWeek: 2, startTime: '07:00', endTime: '09:30', classroom: { roomNumber: 'A101', building: 'Building A' } },
    { dayOfWeek: 1, startTime: '13:00', endTime: '15:30', classroom: { roomNumber: 'B202', building: 'Building B' } },
  ],
};
const enrollment = {
  id: 'enrollment-mf101', sectionId: knownSection.id, semesterId: 'term-3',
  status: 'ENROLLED', enrolledAt: '2026-09-30T10:00:00Z', section: knownSection,
};
const pendingEnrollment = {
  id: 'enrollment-ls102', sectionId: 'section-ls102', semesterId: 'term-3',
  status: 'PENDING', enrolledAt: '2026-10-01T10:00:00Z',
  section: {
    id: 'section-ls102', sectionNumber: '02',
    course: { code: 'LS102', name: 'Legacy Sunday', nameEn: 'Legacy Sunday', nameVi: 'Lịch Chủ nhật', credits: 2 },
    schedules: [{ dayOfWeek: 0, startTime: '09:45', endTime: '11:15' }],
  },
};
const unscheduledEnrollment = {
  id: 'enrollment-us103', sectionId: 'section-us103', semesterId: 'term-3',
  status: 'CONFIRMED', enrolledAt: '2026-10-02T10:00:00Z',
  section: { sectionNumber: '03', course: { code: 'US103', name: 'Unscheduled course', credits: 1 }, schedules: [] },
};
const inactiveEnrollments = ['DROPPED', 'COMPLETED', 'CANCELLED'].map((status, index) => ({
  ...enrollment, id: `inactive-${index}`, status,
  section: { ...knownSection, course: { ...knownSection.course, code: `EXCLUDED${index}` } },
}));
const partialEnrollment = {
  id: 'earlier-enrollment', sectionId: 'earlier-section', semesterId: 'term-1', status: 'ENROLLED',
  section: { sectionNumber: '04', course: { code: 'PAST104', name: 'Earlier course' }, schedules: [] },
};

type FixtureOptions = {
  rounds?: 'open' | 'closed' | 'error';
  enrollmentError?: boolean;
  semesterError?: boolean;
  holdEnrollments?: boolean;
};

async function mockRegistration(page: Page, options: FixtureOptions = {}) {
  const errors: string[] = [];
  const requests: string[] = [];
  let enrollmentError = options.enrollmentError ?? false;
  let semesterError = options.semesterError ?? false;
  let account = 'student-a';
  let added = false;
  let dropped = false;
  let releaseEnrollments: () => void = () => {};
  const enrollmentGate = options.holdEnrollments
    ? new Promise<void>((resolve) => { releaseEnrollments = resolve; })
    : Promise.resolve();
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('console', (message) => {
    // Intentionally failed HTTP requests are expected in the retry scenarios.
    if (message.type() === 'error' && !message.text().startsWith('Failed to load resource:')) errors.push(message.text());
  });
  await page.context().addCookies([{ name: 'cc_csrf', value: 'registration-render-fixture', url: test.info().project.use.baseURL as string }]);
  await page.route('**/api/**', async (route) => {
    const url = new URL(route.request().url());
    const path = url.pathname;
    requests.push(path + url.search);
    const user = { id: account, email: `${account}@example.test`, firstName: 'Demo', lastName: 'Student', roles: ['STUDENT'], studentId: `${account}-profile` };
    if ((path === '/api/v1/registration/rounds' && options.rounds === 'error') ||
        (path === '/api/v1/enrollments/my' && enrollmentError) ||
        (path === '/api/v1/semesters' && semesterError)) {
      await route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE', message: 'Fixture service unavailable' } });
      return;
    }
    if (path === '/api/v1/enrollments/my') {
      await enrollmentGate;
      const term = url.searchParams.get('semesterId');
      const rows = account === 'student-b' ? [] : (term === 'term-3' || term === null)
        ? [enrollment, pendingEnrollment, unscheduledEnrollment, ...inactiveEnrollments,
          ...(added ? [{ ...enrollment, id: 'added-enrollment', sectionId: 'section-new', section: { ...knownSection, sectionNumber: 'NEW105-01', course: { ...knownSection.course, code: 'NEW105' } } }] : [])]
          .filter((row) => !dropped || row.id !== enrollment.id)
        : term === 'term-1' ? [partialEnrollment] : [];
      await route.fulfill({ json: rows });
      return;
    }
    if (path === '/api/v1/me/enrollments' && route.request().method() === 'POST') {
      added = true;
      await route.fulfill({ json: { ...enrollment, id: 'added-enrollment', sectionId: 'section-new', status: 'ENROLLED', section: { ...knownSection, sectionNumber: 'NEW105-01', course: { ...knownSection.course, code: 'NEW105' } } } });
      return;
    }
    if (path === '/api/v1/me/enrollments/enrollment-mf101/drop') {
      dropped = true;
      await route.fulfill({ json: { message: 'Dropped' } });
      return;
    }
    if (path === '/api/v1/auth/login') {
      account = 'student-b';
      await route.fulfill({ json: { user: { ...user, id: account, email: `${account}@example.test` } } });
      return;
    }
    const payloads: Record<string, unknown> = {
      '/api/site-appearance': {}, '/api/v1/site-appearance': {},
      '/api/v1/health/liveness': { status: 'UP' },
      '/api/v1/auth/me': user, '/api/v1/auth/logout': {},
      '/api/v1/semesters': { data: terms },
      '/api/v1/notifications/my': { data: [] }, '/api/v1/notifications/my/unread-count': { unreadCount: 0 },
      '/api/v1/assistant/conversations': [],
      '/api/v1/announcements/my': { data: [], meta: { total: 0, page: 1, totalPages: 0 } },
      '/api/v1/registration/rounds': options.rounds === 'closed' ? [] : [{
        id: 'round-fixture', semesterId: 'term-3', name: 'Fixture registration', kind: 'REGISTRATION', status: 'OPEN',
        windowStart: new Date(Date.now() - 86_400_000).toISOString(), windowEnd: new Date(Date.now() + 30 * 86_400_000).toISOString(), creditLimit: 18,
      }],
      '/api/v1/me/registration/eligibility': { eligible: true, creditLimit: 18, creditsUsed: 6, creditsRemaining: 12 },
      '/api/v1/me/registration/summary': { creditLimit: 18, creditsUsed: 6, creditsRemaining: 12 },
      '/api/v1/me/registration/credit-limit-application': null,
      // The register page polls the withdrawal window and the caller's
      // applications on mount; a closed window keeps the Withdraw action
      // absent so these registration-UX cases see the same DOM as before.
      '/api/v1/me/registration/withdrawal-window': {
        semesterId: 'term-3', semesterName: 'Future term', regularSemester: true,
        semesterOpen: false, windowStart: null, windowEnd: null, open: false,
      },
      '/api/v1/me/registration/withdrawal-applications': [],
      '/api/v1/me/registration/sections': [
        { id: knownSection.id, sectionNumber: knownSection.sectionNumber, courseId: 'course-mf101', courseCode: 'MF101', courseName: 'Mathematical Methods', credits: 3, capacity: 40, enrolledCount: 20, remainingSeats: 20, status: 'OPEN', alreadyEnrolled: !dropped, scheduleConflict: false, schedules: knownSection.schedules },
        { id: 'section-new', sectionNumber: 'NEW105-01', courseId: 'course-new', courseCode: 'NEW105', courseName: 'New registration', credits: 3, capacity: 40, enrolledCount: 20, remainingSeats: 20, status: 'OPEN', alreadyEnrolled: added, scheduleConflict: false, schedules: [{ dayOfWeek: 4, startTime: '10:00', endTime: '11:30' }] },
      ],
      // Logout/login stays in the same client tree to expose stale cross-user cache.
      '/api/v1/me/registration': {},
      '/api/v1/me/curriculum': {
        curriculum: { id: 'curriculum-fixture', code: 'MATH-DEMO', name: 'Math program', nameEn: 'Math program', nameVi: 'Chương trình Toán', totalCredits: 6 },
        courses: [{ courseId: 'course-mf101', code: 'MF101', name: 'Methods', nameEn: 'Mathematical Methods', nameVi: 'Phương pháp toán học', credits: 3, year: 1, semester: 1, isMandatory: true, status: 'IN_PROGRESS', prerequisites: [] }],
      },
    };
    if (!(path in payloads)) {
      errors.push(`Unexpected API request: ${path}`);
      await route.abort();
      return;
    }
    await route.fulfill({ json: payloads[path] });
  });
  return { errors, requests, releaseEnrollments, recoverEnrollments: () => { enrollmentError = false; }, recoverSemesters: () => { semesterError = false; } };
}

const names = {
  en: { catalog: 'Course registration', registered: 'Registered courses & timetable', semester: /Semester/, sunday: 'Sunday', retry: /Retry|Try again/, empty: /No registered|No courses|not registered/i },
  vi: { catalog: 'Đăng ký học phần', registered: 'Học phần đã đăng ký & TKB', semester: /Học kỳ/, sunday: 'Chủ Nhật', retry: /Thử lại/, empty: /Chưa có học phần|Chưa đăng ký|chưa có lớp/i },
};

test('UX repair: U01 enrollment failure cannot look like zero registrations and retry recovers', async ({ page }) => {
  const fixture = await mockRegistration(page, { enrollmentError: true });
  await page.goto('/en/dashboard/register');
  const main = page.locator('#dashboard-main-content');
  await expect(main.getByRole('button', { name: /Retry|Try again/ })).toBeVisible({ timeout: 15_000 });
  await expect(main.getByText('0 classes', { exact: true })).toHaveCount(0);
  await expect(main.getByRole('button', { name: 'Register', exact: true })).toHaveCount(0);
  fixture.recoverEnrollments();
  await main.getByRole('button', { name: /Retry|Try again/ }).click();
  await expect(page.getByRole('region', { name: /MF101/ }).getByRole('button', { name: 'Drop course', exact: true })).toBeVisible();
  expect(fixture.errors).toEqual([]);
});

test('UX repair: U02 same-term refresh updates catalog metadata and registration identity', async ({ page }) => {
  const fixture = await mockRegistration(page);
  const panel = await openDetails(page, 'en');
  await expect(panel.getByTestId('registered-courses-list')).toBeVisible();
  await page.route('**/api/v1/enrollments/my*', (route) => route.fulfill({ json: [] }));
  let sectionReads = 0;
  await page.route('**/api/v1/me/registration/sections*', (route) => {
    sectionReads++;
    return route.fulfill({ json: [{ id: knownSection.id, sectionNumber: 'MF101-01', courseId: 'course-mf101', courseCode: 'MF101', courseName: 'Mathematical Methods', credits: 3, capacity: 40, enrolledCount: 19, remainingSeats: 21, status: 'OPEN', alreadyEnrolled: false, schedules: knownSection.schedules }] });
  });
  await panel.getByRole('button', { name: 'Refresh data', exact: true }).click();
  await expect(panel.getByText('No registered courses', { exact: true })).toBeVisible();
  await page.getByRole('tab', { name: names.en.catalog, exact: true }).click();
  const group = page.getByRole('region', { name: /MF101/ });
  await expect(group.getByRole('button', { name: 'Register', exact: true })).toBeVisible();
  await expect(group.getByText('Registered', { exact: true })).toHaveCount(0);
  await expect(group).toContainText('21');
  expect(sectionReads).toBeGreaterThan(0);
  expect(fixture.errors).toEqual([]);
});

test('UX repair: U03 first-course mobile preview and U04 distinct today label', async ({ page }, testInfo) => {
  await mockRegistration(page);
  await page.clock.setFixedTime(new Date('2026-10-07T10:00:00Z'));
  // The page evaluates the round window against the fixed clock, so the window
  // must bracket it with constants — Date.now() bounds drift out of range.
  await page.route('**/api/v1/registration/rounds*', (route) => route.fulfill({ json: [{ id: 'round-fixture', semesterId: 'term-3', name: 'Fixture registration', kind: 'REGISTRATION', status: 'OPEN', windowStart: '2026-10-01T00:00:00Z', windowEnd: '2026-10-31T23:59:59Z', creditLimit: 18 }] }));
  await page.route('**/api/v1/enrollments/my*', (route) => route.fulfill({ json: [] }));
  await page.goto('/en/dashboard/register');
  const group = page.getByRole('region', { name: /NEW105/ });
  await group.getByRole('button', { name: 'Register', exact: true }).focus();
  if ((testInfo.project.use.viewport?.width ?? 1440) < 1024) await page.getByRole('button', { name: 'Show registered sections & weekly preview', exact: true }).click();
  const preview = page.getByTestId('registration-preview');
  await expect(preview).toBeVisible();
  await expect(preview.getByText('NEW105', { exact: true }).filter({ visible: true }).first()).toBeVisible();
  await expect(preview.getByText('TODAY', { exact: true }).filter({ visible: true })).toBeVisible();
  // A single Registered legend remains; the day heading must not reuse it.
  await expect(preview.getByText('Registered', { exact: true }).filter({ visible: true })).toHaveCount(1);
  await noOverflow(page);
});

test('UX repair: U05 named progressbar and U07 U08 rail identities and legacy Sunday', async ({ page }, testInfo) => {
  await mockRegistration(page);
  await page.goto('/en/dashboard/register');
  const main = page.locator('#dashboard-main-content');
  await expect(page.getByRole('region', { name: /MF101/ })).toBeVisible();
  if ((testInfo.project.use.viewport?.width ?? 1440) >= 640) await expect(main.getByRole('progressbar', { name: /Registered credits/i })).toBeVisible();
  if ((testInfo.project.use.viewport?.width ?? 1440) < 1024) await page.getByRole('button', { name: 'Show registered sections & weekly preview', exact: true }).click();
  await expect(main.getByRole('button', { name: 'Drop course MF101-01', exact: true })).toBeVisible();
  await expect(main.getByRole('button', { name: 'Drop course LS102-02', exact: true })).toBeVisible();
  await expect(main.getByText(/Sun 09:45–11:15/)).toBeVisible();
  await expect(main.getByText(/undefined/)).toHaveCount(0);
});

test('UX repair: U06 chronological Sunday agenda E03 timetable jump and E04 mobile assistant entry', async ({ page }, testInfo) => {
  await mockRegistration(page);
  const panel = await openDetails(page, 'en');
  await expect(panel.getByTestId('registered-courses-list')).toBeVisible();
  const grid = panel.getByTestId('registered-timetable');
  await panel.getByRole('button', { name: 'Go to timetable', exact: true }).click();
  await expect(grid).toBeFocused();
  const text = await grid.innerText();
  if ((testInfo.project.use.viewport?.width ?? 1440) < 768) {
    const sunday = text.slice(text.lastIndexOf('Sunday'));
    expect(sunday.indexOf('09:45')).toBeLessThan(sunday.indexOf('13:00'));
    await expect(page.getByRole('button', { name: 'Open CampusUTE assistant', exact: true })).toHaveCount(0);
    await expect(page.locator('[data-mobile-assistant-slot]')).toBeVisible();
  }
  await noOverflow(page);
});

test('UX repair: E01 background refresh failure retains records and recovers', async ({ page }) => {
  await mockRegistration(page);
  const panel = await openDetails(page, 'en');
  await expect(panel.getByTestId('registered-courses-list')).toBeVisible();
  let failing = true;
  await page.route('**/api/v1/enrollments/my*', (route) => route.fulfill(failing ? { status: 503, json: { message: 'Fixture unavailable' } } : { json: [enrollment] }));
  await panel.getByRole('button', { name: 'Refresh data', exact: true }).click();
  await expect(panel.getByRole('alert')).toContainText(/previously loaded|out of date/i, { timeout: 15000 });
  await expect(panel.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toBeVisible();
  await expect(panel.getByTestId('registered-timetable')).toBeVisible();
  failing = false;
  await panel.getByRole('button', { name: /Retry|Try again/ }).click();
  await expect(panel.getByRole('alert')).toHaveCount(0);
});

test('UX repair: E02 reload history and unknown URL parameters retain selected context', async ({ page }) => {
  await mockRegistration(page);
  await page.goto('/en/dashboard/register?q=MF101&keep=academic');
  await page.getByRole('tab', { name: names.en.registered, exact: true }).click();
  const panel = page.getByRole('tabpanel', { name: names.en.registered, exact: true });
  await panel.getByRole('combobox', { name: names.en.semester }).selectOption('term-1');
  await expect(panel.getByText('PAST104', { exact: true })).toBeVisible();
  await expect(page).toHaveURL(/tab=registered/);
  expect(new URL(page.url()).searchParams.get('keep')).toBe('academic');
  await page.reload();
  await expect(page.getByRole('tab', { name: names.en.registered, exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(panel.getByRole('combobox', { name: names.en.semester })).toHaveValue('term-1');
  await panel.getByRole('combobox', { name: names.en.semester }).selectOption('term-3');
  await expect(panel.getByText('MF101', { exact: true }).first()).toBeVisible();
  await page.goBack();
  await expect(panel.getByRole('combobox', { name: names.en.semester })).toHaveValue('term-1');
  await page.goForward();
  await expect(panel.getByRole('combobox', { name: names.en.semester })).toHaveValue('term-3');
  await page.goto('/en/dashboard/register?tab=registered&semester=not-a-term');
  await expect(panel.getByRole('combobox', { name: names.en.semester })).toHaveValue('term-3');
});

test('UX repair: E05 visual countdown stays outside live regions', async ({ page }) => {
  await mockRegistration(page);
  await page.route('**/api/v1/registration/rounds*', (route) => route.fulfill({ json: [{ id: 'round-fixture', semesterId: 'term-3', name: 'Fixture registration', kind: 'REGISTRATION', status: 'OPEN', windowStart: new Date(Date.now()-86400000).toISOString(), windowEnd: new Date(Date.now()+3*86400000).toISOString(), creditLimit: 18 }] }));
  await page.goto('/en/dashboard/register');
  const countdown = page.getByTestId('registration-countdown');
  await expect(countdown).toBeVisible();
  expect(await countdown.evaluate((el) => el.closest('[aria-live="polite"], [aria-live="assertive"], [role="status"]'))).toBeNull();
});

test('UX repair: E06 mutation failure leaves durable visible feedback near catalog', async ({ page }) => {
  await mockRegistration(page);
  await page.route('**/api/v1/me/enrollments', (route) => route.fulfill({ status: 503, json: { code: 'SERVICE_UNAVAILABLE' } }));
  await page.goto('/en/dashboard/register');
  await page.getByRole('region', { name: /NEW105/ }).getByRole('button', { name: 'Register', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Register', exact: true }).click();
  const error = page.getByTestId('registration-action-error');
  await expect(error).toBeVisible();
  await expect(error).toHaveAttribute('role', 'alert');
  const box = await error.boundingBox();
  expect(box?.y).toBeGreaterThanOrEqual(0);
  expect((box?.y ?? 0) + (box?.height ?? 0)).toBeLessThanOrEqual(page.viewportSize()!.height);
});

async function openDetails(page: Page, locale: 'en' | 'vi') {
  await page.goto(`/${locale}/dashboard/register`);
  const tab = page.getByRole('tab', { name: names[locale].registered, exact: true });
  await expect(tab).toBeVisible({ timeout: 20_000 });
  await tab.click();
  return page.getByRole('tabpanel', { name: names[locale].registered, exact: true });
}

async function noOverflow(page: Page) {
  expect(await page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
}

for (const locale of ['en', 'vi'] as const) {
  test(`registration details ${locale}: keyboard tabs, active registrations, full meetings and selected semester`, async ({ page }, testInfo) => {
    const fixture = await mockRegistration(page);
    await page.goto(`/${locale}/dashboard/register`);
    const catalog = page.getByRole('tab', { name: names[locale].catalog, exact: true });
    const registered = page.getByRole('tab', { name: names[locale].registered, exact: true });
    await expect(catalog).toBeVisible({ timeout: 20_000 });
    await expect(catalog).toHaveAttribute('aria-selected', 'true');
    await expect(registered).toHaveAttribute('tabindex', '-1');
    await catalog.focus();
    await page.keyboard.press('ArrowRight');
    await expect(registered).toBeFocused();
    await expect(registered).toHaveAttribute('aria-selected', 'true');
    const panel = page.getByRole('tabpanel', { name: names[locale].registered, exact: true });
    const semester = panel.getByRole('combobox', { name: names[locale].semester });
    await expect(semester).toHaveValue('term-3');
    const list = panel.getByTestId('registered-courses-list');
    await expect(list).toBeVisible();
    const summary = panel.getByRole('status').filter({ hasText: locale === 'en' ? '3 classes' : '3 lớp' });
    await expect(summary).toContainText(locale === 'en' ? '6 credits' : '6 tín chỉ');
    for (const code of ['MF101', 'LS102', 'US103']) await expect(list.getByText(code, { exact: true })).toBeVisible();
    for (const code of ['EXCLUDED0', 'EXCLUDED1', 'EXCLUDED2']) await expect(panel.getByText(code, { exact: true })).toHaveCount(0);
    await expect(list).toContainText(locale === 'en' ? 'Mathematical Methods' : 'Phương pháp toán học');
    await expect(list).toContainText('MF101-01');
    await expect(list).toContainText('Nguyễn Linh');
    for (const value of ['07:00', '09:30', '13:00', '15:30', 'A101', 'B202', 'Building A', 'Building B']) await expect(list).toContainText(value);
    await expect(list).toContainText(locale === 'en' ? /Pending|Processing/i : /Đang xử lý|chờ/i);
    await expect(list).toContainText(locale === 'en' ? /Not scheduled|Schedule pending|No schedule|no published|not published/i : /Chưa có lịch|Chưa xếp lịch|chưa.*công bố/i);
    const timetable = panel.getByTestId('registered-timetable');
    await expect(timetable.getByText(names[locale].sunday, { exact: true }).filter({ visible: true })).toBeVisible();
    for (const code of ['MF101', 'LS102']) await expect(timetable.getByText(code, { exact: true }).filter({ visible: true }).first()).toBeVisible();
    await expect(timetable.getByText('US103', { exact: true })).toHaveCount(0);
    await noOverflow(page);
    if (['desktop-1440', 'mobile-390'].includes(testInfo.project.name)) {
        await page.screenshot({ path: `../plans/261007-2306-registration-uiux-release/assets/registered-${locale}-${testInfo.project.name}.png`, fullPage: true });
    }
    await semester.selectOption('term-1');
    await expect(list.getByText('PAST104', { exact: true })).toBeVisible();
    await expect(list.getByText('MF101', { exact: true })).toHaveCount(0);
    const partialSummary = panel.getByRole('status').filter({ hasText: locale === 'en' ? 'Credit total not available yet' : 'Chưa có đủ thông tin tổng tín chỉ' });
    await expect(partialSummary).toContainText(locale === 'en' ? 'Credit total not available yet' : /Chưa có.*tín chỉ/);
    await expect(partialSummary).not.toContainText(locale === 'en' ? '0 credits' : '0 tín chỉ');
    await expect(list).toContainText(locale === 'en' ? /Not available|Unavailable|Not assigned|Pending/ : /Chưa có|Chưa phân công|Chưa cập nhật/);
    await registered.focus();
    await page.keyboard.press('Home');
    await expect(catalog).toBeFocused();
    await page.keyboard.press('End');
    await expect(registered).toBeFocused();
    await expect(semester).toHaveValue('term-1');
    await page.keyboard.press('ArrowLeft');
    await expect(catalog).toBeFocused();
    await page.keyboard.press('ArrowLeft');
    await expect(registered).toBeFocused();
    expect(fixture.errors).toEqual([]);
  });

  test(`registration details ${locale}: semester navigation commits before the RSC round trip`, async ({ page }) => {
    const fixture = await mockRegistration(page);
    await page.goto(`/${locale}/dashboard/register?keep=academic#semester-context`);
    const registered = page.getByRole('tab', { name: names[locale].registered, exact: true });
    await expect(registered).toBeVisible({ timeout: 20_000 });
    await registered.click();
    const panel = page.getByRole('tabpanel', { name: names[locale].registered, exact: true });
    const semester = panel.getByRole('combobox', { name: names[locale].semester });
    const list = panel.getByTestId('registered-courses-list');
    await expect(semester).toHaveValue('term-3');
    await expect(list.getByText('MF101', { exact: true })).toBeVisible();

    let releaseRsc: () => void = () => {};
    const rscGate = new Promise<void>((resolve) => { releaseRsc = resolve; });
    await page.route(`**/${locale}/dashboard/register**`, async (route) => {
      const request = route.request();
      const url = new URL(request.url());
      const isRsc = request.headers()['rsc'] === '1' || url.searchParams.has('_rsc');
      if (request.method() === 'GET' && isRsc && url.searchParams.get('semester') === 'term-1') {
        await rscGate;
      }
      await route.continue();
    });

    try {
      await semester.selectOption('term-1');
      await expect(semester).toHaveValue('term-1');
      const url = new URL(page.url());
      expect(url.searchParams.get('tab')).toBe('registered');
      expect(url.searchParams.get('semester')).toBe('term-1');
      expect(url.searchParams.get('keep')).toBe('academic');
      expect(url.hash).toBe('#semester-context');
      await expect(list.getByText('PAST104', { exact: true })).toBeVisible();
      await expect(list.getByText('MF101', { exact: true })).toHaveCount(0);
    } finally {
      releaseRsc();
    }

    await page.goBack();
    await expect(semester).toHaveValue('term-3');
    await expect(list.getByText('MF101', { exact: true })).toBeVisible();
    await page.goForward();
    await expect(semester).toHaveValue('term-1');
    await expect(list.getByText('PAST104', { exact: true })).toBeVisible();
    await page.reload();
    await expect(semester).toHaveValue('term-1');
    await expect(list.getByText('PAST104', { exact: true })).toBeVisible();
    expect(fixture.errors).toEqual([]);
  });

  test(`registration details ${locale}: closed registration retains semester access and honest empty state`, async ({ page }) => {
    const fixture = await mockRegistration(page, { rounds: 'closed' });
    const panel = await openDetails(page, locale);
    const semester = panel.getByRole('combobox', { name: names[locale].semester });
    await expect(semester).toHaveValue('term-2');
    await expect(panel.getByText(names[locale].empty).first()).toBeVisible();
    await semester.selectOption('term-3');
    await expect(panel.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toBeVisible();
    await noOverflow(page);
    expect(fixture.requests.some((url) => url.includes('/enrollments/my?semesterId=term-3'))).toBe(true);
    expect(fixture.requests.some((url) => url.includes('/enrollments/my?semesterId=term-2'))).toBe(true);
    expect(fixture.errors).toEqual([]);
  });

  test(`registration details ${locale}: enrollment failure is recoverable and not an empty registration`, async ({ page }) => {
    const fixture = await mockRegistration(page, { rounds: 'closed', enrollmentError: true });
    const panel = await openDetails(page, locale);
    await expect(panel.getByRole('button', { name: names[locale].retry })).toBeVisible({ timeout: 15_000 });
    await expect(panel.getByText(names[locale].empty)).toHaveCount(0);
    fixture.recoverEnrollments();
    await panel.getByRole('button', { name: names[locale].retry }).click();
    await expect(panel.getByText(names[locale].empty).first()).toBeVisible();
    await panel.getByRole('combobox', { name: names[locale].semester }).selectOption('term-3');
    await expect(panel.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toBeVisible();
    expect(fixture.errors).toEqual([]);
  });
}

test('registration details: round failure does not block records; semester failure retries independently', async ({ page }) => {
  const fixture = await mockRegistration(page, { rounds: 'error', semesterError: true });
  const panel = await openDetails(page, 'en');
  await expect(panel.getByRole('button', { name: names.en.retry })).toBeVisible({ timeout: 15_000 });
  await expect(panel.getByText(names.en.empty)).toHaveCount(0);
  fixture.recoverSemesters();
  await panel.getByRole('button', { name: names.en.retry }).click();
  await expect(panel.getByRole('combobox', { name: names.en.semester })).toHaveValue('term-2');
  await panel.getByRole('combobox', { name: names.en.semester }).selectOption('term-3');
  await expect(panel.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toBeVisible();
  expect(fixture.errors).toEqual([]);
});

test('registration details: returning to catalog preserves search, filters and course collapse', async ({ page }) => {
  const fixture = await mockRegistration(page);
  await page.goto('/en/dashboard/register');
  const search = page.getByLabel('Search sections by code or name');
  await expect(search).toBeVisible({ timeout: 20_000 });
  await search.fill('MF101');
  const seatsFilter = page.getByRole('button', { name: /^Seats open/ });
  await seatsFilter.click();
  await expect(seatsFilter).toHaveAttribute('aria-pressed', 'true');
  const group = page.getByRole('region', { name: 'MF101 Mathematical Methods' });
  await expect(group).toBeVisible();
  const groupToggle = group.getByRole('button', { expanded: true }).first();
  await groupToggle.click();
  await expect(group.getByRole('button', { expanded: false }).first()).toBeVisible();
  await page.getByRole('tab', { name: names.en.registered, exact: true }).click();
  await expect(page.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toBeVisible();
  await page.getByRole('tab', { name: names.en.catalog, exact: true }).click();
  await expect(search).toHaveValue('MF101');
  await expect(seatsFilter).toHaveAttribute('aria-pressed', 'true');
  await expect(group.getByRole('button', { expanded: false }).first()).toBeVisible();
  await expect(page.getByRole('region', { name: /NEW105/ })).toHaveCount(0);
  await noOverflow(page);
  expect(fixture.errors).toEqual([]);
});

test('registration details: pending network responses expose loading rather than a false empty registration', async ({ page }) => {
  const fixture = await mockRegistration(page, { rounds: 'closed', holdEnrollments: true });
  const panel = await openDetails(page, 'en');
  await expect(panel.getByRole('status').getByText('Loading content', { exact: true })).toBeVisible();
  await expect(panel.getByText(names.en.empty)).toHaveCount(0);
  fixture.releaseEnrollments();
  await expect(panel.getByText(names.en.empty).first()).toBeVisible();
  await panel.getByRole('combobox', { name: names.en.semester }).selectOption('term-3');
  await expect(panel.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toBeVisible();
  expect(fixture.errors).toEqual([]);
});

test('registration details: confirmed register and drop refresh records without a reload', async ({ page }) => {
  const fixture = await mockRegistration(page);
  const panel = await openDetails(page, 'en');
  await expect(panel.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toBeVisible();
  await page.evaluate(() => { document.documentElement.dataset.registrationTestDocument = 'persistent'; });
  await page.getByRole('tab', { name: names.en.catalog, exact: true }).click();
  const newCourse = page.getByRole('region', { name: 'NEW105 New registration' });
  await newCourse.getByRole('button', { name: 'Register', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Register', exact: true }).click();
  await expect(newCourse.getByRole('button', { name: 'Drop course', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: names.en.registered, exact: true }).click();
  await expect(panel.getByTestId('registered-courses-list').getByText('NEW105', { exact: true })).toBeVisible();
  await page.getByRole('tab', { name: names.en.catalog, exact: true }).click();
  const original = page.getByRole('region', { name: 'MF101 Mathematical Methods' });
  await original.getByRole('button', { name: 'Drop course', exact: true }).click();
  await page.getByRole('dialog').getByRole('button', { name: 'Drop course', exact: true }).click();
  await expect(original.getByRole('button', { name: 'Register', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: names.en.registered, exact: true }).click();
  await expect(panel.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toHaveCount(0);
  await expect(panel.getByTestId('registered-courses-list').getByText('NEW105', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.dataset.registrationTestDocument)).toBe('persistent');
  expect(fixture.errors).toEqual([]);
});

test('registration details: SPA account switch cannot reuse the previous student records', async ({ page }) => {
  const fixture = await mockRegistration(page);
  const panel = await openDetails(page, 'en');
  await expect(panel.getByTestId('registered-courses-list').getByText('MF101', { exact: true })).toBeVisible();
  await page.evaluate(() => { document.documentElement.dataset.registrationTestDocument = 'persistent'; });
  await page.getByRole('button', { name: 'Toggle profile menu', exact: true }).click();
  await page.getByRole('menuitem', { name: 'Sign out', exact: true }).click();
  await expect(page.getByLabel('Email address', { exact: true })).toBeVisible({ timeout: 20_000 });
  await page.getByLabel('Email address', { exact: true }).fill('student-b@example.test');
  await page.getByLabel('Password', { exact: true }).fill('local-fixture-password');
  await page.getByRole('button', { name: 'Sign in', exact: true }).click();
  const registrationLink = page.locator('#dashboard-main-content a[href="/en/dashboard/register"]').first();
  await expect(registrationLink).toBeVisible({ timeout: 20_000 });
  await registrationLink.click();
  await page.getByRole('tab', { name: names.en.registered, exact: true }).click();
  await expect(page.getByRole('tabpanel', { name: names.en.registered, exact: true }).getByText(names.en.empty).first()).toBeVisible();
  await expect(page.getByRole('tabpanel', { name: names.en.registered, exact: true }).getByText('MF101', { exact: true })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.dataset.registrationTestDocument)).toBe('persistent');
  expect(fixture.requests).toContain('/api/v1/health/liveness');
  expect(fixture.errors).toEqual([]);
});

for (const locale of ['en', 'vi'] as const) {
  test(`baseline repairs ${locale}: curriculum route, locale navigation and complete enrollment presentation`, async ({ page }, testInfo) => {
    const fixture = await mockRegistration(page);
    const query = '?view=academic-plan';
    const hash = '#study-path';
    const response = await page.goto(`/${locale}/dashboard/curriculum${query}${hash}`);
    expect(response?.status()).toBe(200);
    const curriculumTitle = locale === 'en' ? 'Curriculum' : 'Chương trình đào tạo';
    await expect(page.getByRole('heading', { name: curriculumTitle, exact: true, level: 1 })).toBeVisible({ timeout: 20_000 });
    const main = page.locator('#dashboard-main-content');
    await expect(main.getByText('MF101', { exact: true }).filter({ visible: true }).first()).toBeVisible();
    let language = page.getByRole('group', { name: /Language|Ngôn ngữ/ }).filter({ visible: true }).first();
    if ((testInfo.project.use.viewport?.width ?? 1280) < 640) {
      await page.getByRole('button', { name: locale === 'en' ? 'Toggle profile menu' : 'Bật tắt menu hồ sơ', exact: true }).click();
      language = page.getByRole('group', { name: /Language|Ngôn ngữ/ }).filter({ visible: true }).first();
    }
    await expect(language).toBeVisible();
    const current = language.locator('[aria-current="true"]');
    await expect(current).toHaveText(locale);
    expect(await current.evaluate((element) => element.tagName)).toBe('SPAN');
    expect(await current.evaluate((element) => (element as HTMLElement).tabIndex)).toBe(-1);
    await expect(current).not.toHaveAttribute('href');
    const targetLocale = locale === 'en' ? 'vi' : 'en';
    const switchLink = language.getByRole('link');
    await expect(switchLink).toHaveCount(1);
    await expect(switchLink).toHaveAttribute('href', `/${targetLocale}/dashboard/curriculum${query}`);
    await switchLink.click();
    await expect(page).toHaveURL(new RegExp(`/${targetLocale}/dashboard/curriculum\\?view=academic-plan#study-path$`));
    await expect(page.getByRole('heading', { name: targetLocale === 'en' ? 'Curriculum' : 'Chương trình đào tạo', exact: true, level: 1 })).toBeVisible();
    await noOverflow(page);

    const enrollmentResponse = await page.goto(`/${locale}/dashboard/enrollments`);
    expect(enrollmentResponse?.status()).toBe(200);
    await expect(page.getByRole('heading', { name: locale === 'en' ? 'My courses' : 'Môn học của tôi', exact: true, level: 1 })).toBeVisible({ timeout: 20_000 });
    await main.getByRole('tab', { name: locale === 'en' ? /Enrollment Records|Enrolled/ : /Hồ sơ đăng ký học phần|Đăng ký/ }).click();
    const item = (testInfo.project.use.viewport?.width ?? 1280) < 640
      ? main.getByRole('listitem').filter({ has: page.getByText('MF101', { exact: true }) }).filter({ visible: true }).first()
      : main.getByRole('row').filter({ has: page.getByText('MF101', { exact: true }) }).filter({ visible: true }).first();
    await expect(item).toBeVisible();
    await expect(item).toContainText(locale === 'en' ? 'Mathematical Methods' : 'Phương pháp toán học');
    await expect(item).toContainText('MF101-01');
    await expect(item).toContainText(locale === 'en' ? 'Registered' : 'Đã đăng ký');
    for (const value of ['07:00', '09:30', '13:00', '15:30', 'Nguyễn Linh']) await expect(item).toContainText(value);
    await expect(item).toContainText(locale === 'en' ? 'Sunday' : 'Chủ Nhật');
    const drop = item.getByRole('button', { name: locale === 'en' ? /Drop course/ : /Hủy môn học/ });
    await expect(drop).toBeVisible();
    await drop.click();
    const confirmation = page.getByRole('dialog');
    await expect(confirmation).toContainText('MF101');
    await confirmation.getByRole('button', { name: locale === 'en' ? 'Cancel' : 'Hủy', exact: true }).click();
    await expect(item).toBeVisible();
    expect(fixture.requests.some((url) => url.includes('/me/enrollments/enrollment-mf101/drop'))).toBe(false);
    await noOverflow(page);
    expect(fixture.errors).toEqual([]);
  });
}
