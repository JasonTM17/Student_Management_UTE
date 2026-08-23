import { expect, test } from '@playwright/test';
import {
  adminHomeRoute,
  adminRoutes,
  apiUrl,
  buildCookieHeaders,
  expectOkResponse,
  frontendBaseURL,
  getSharedSessionArtifacts,
  lecturerRoutes,
  seedBrowserSession,
  studentRoutes,
  waitForAnyVisible,
  visitRoute,
  visitRoutes,
} from './helpers';

test('student can traverse people and academic routes without losing jwt-backed session claims', async ({
  page,
  playwright,
}) => {
  test.setTimeout(75_000);

  const loginData = await seedBrowserSession(page, playwright, 'student', {
    shared: true,
  });
  expect(loginData.user.studentId).toBeTruthy();

  await page.goto('/dashboard');
  await expect(page).toHaveURL(/\/dashboard$/);
  await expect(page.getByRole('heading', { name: /Welcome back/i })).toBeVisible();

  await visitRoutes(page, studentRoutes);
});

test('Java REST API keeps student enrollment history reachable through the public path', async ({
  playwright,
}) => {
  const api = await playwright.request.newContext();

  try {
    const studentSession = await getSharedSessionArtifacts(playwright, 'student');
    const adminSession = await getSharedSessionArtifacts(playwright, 'admin');
    const studentData = studentSession.authData;
    const adminData = adminSession.authData;

    expect(studentData.user.studentId).toBeTruthy();
    expect(adminData.user.roles).toContain('ADMIN');

    const enrollmentHistoryResponse = await api.get(
      apiUrl(`/students/${studentData.user.studentId}/enrollments`),
      {
        headers: buildCookieHeaders(adminSession, {
          Authorization: `Bearer ${adminData.accessToken}`,
        }),
      },
    );

    await expectOkResponse(
      enrollmentHistoryResponse,
      'GET /students/:id/enrollments',
    );
    const enrollmentHistory = await enrollmentHistoryResponse.json();
    expect(Array.isArray(enrollmentHistory)).toBe(true);
  } finally {
    await api.dispose();
  }
});

test('lecturer can traverse teaching flows without losing lecturer claims', async ({
  page,
  playwright,
}) => {
  test.setTimeout(75_000);

  const loginData = await seedBrowserSession(page, playwright, 'lecturer', {
    shared: true,
  });
  expect(loginData.user.lecturerId).toBeTruthy();

  await page.goto('/dashboard/lecturer');
  await expect(page).toHaveURL(/\/dashboard\/lecturer$/);
  await expect(
    page.getByRole('heading', { name: /Welcome back/i }),
  ).toBeVisible();

  await visitRoutes(page, lecturerRoutes);

  await test.step(
    'visit /dashboard/lecturer/grades and open a section detail route',
    async () => {
      await visitRoute(page, {
        path: '/dashboard/lecturer/grades',
        heading: /^Grade management$/i,
        ready: async (currentPage, timeoutMs) => {
          await expect(
            currentPage.getByRole('heading', {
              name: /Grade management queue/i,
            }),
          ).toBeVisible({ timeout: timeoutMs });

          const manageLinks = currentPage.getByRole('link', {
            name: /Manage Grades|Enter Grades/i,
          });
          const emptyStateHeading = currentPage.getByRole('heading', {
            name: /No grading sections yet/i,
          });

          await waitForAnyVisible(
            [manageLinks, emptyStateHeading],
            timeoutMs,
          );

          const hasManageLinks =
            (await manageLinks.first().count()) > 0 &&
            (await manageLinks.first().isVisible().catch(() => false));

          if (!hasManageLinks) {
            await expect(emptyStateHeading).toBeVisible({ timeout: timeoutMs });
            return;
          }

          await expect(manageLinks.first()).toBeVisible({ timeout: timeoutMs });
          await manageLinks.first().click();
          await expect(currentPage).toHaveURL(
            /\/dashboard\/lecturer\/grades\/[^/]+$/,
          );
          await expect(
            currentPage.getByRole('button', { name: 'Save Grades' }),
          ).toBeVisible({ timeout: timeoutMs });
          await expect(
            currentPage.getByRole('button', { name: 'Publish Grades' }),
          ).toBeVisible({ timeout: timeoutMs });
          await expect(
            currentPage
              .getByRole('link', { name: /Back to grade management/i })
              .first(),
          ).toBeVisible({ timeout: timeoutMs });
        },
      });
    },
  );
});

test('admin can still reach roster-style people and academic management routes', async ({
  page,
  playwright,
}) => {
  test.setTimeout(75_000);

  await seedBrowserSession(page, playwright, 'admin', { shared: true });

  await visitRoute(page, adminHomeRoute);

  for (const route of adminRoutes) {
    await visitRoute(page, route);
  }
});
