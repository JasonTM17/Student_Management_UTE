import { expect, test, type Page, type Route } from '@playwright/test';

test.describe.configure({ timeout: 60_000 });
test.use({ timezoneId: 'Asia/Bangkok' });

const STUDENT = { id: 'integrity-user', studentId: 'integrity-student', email: 'integrity@example.test', firstName: 'Integrity', lastName: 'Student', roles: ['STUDENT'], status: 'ACTIVE', mustChangePassword: false };
const ADMIN = { ...STUDENT, studentId: undefined, roles: ['ADMIN'] };
const ROUND_A = {
  id: 'integrity-a', name: 'Integrity Round A', thesisType: 'MON_HOC', status: 'PROPOSAL_OPEN',
  registrationStart: '2026-10-16T10:23:45.123Z', registrationEnd: '2026-10-25T10:23:45.456Z',
  lecturerSubmitStart: '2026-10-01T10:23:45.123Z', lecturerSubmitEnd: '2026-10-15T10:23:45.789Z',
};
const ROUND_B = { ...ROUND_A, id: 'integrity-b', name: 'Integrity Round B' };
const GROUP = { id: 'integrity-group', roundId: ROUND_A.id, leaderStudentId: STUDENT.studentId, memberStudentIds: [STUDENT.studentId], status: 'SUBMITTED', approvalStatus: 'PENDING' };
const REPORT = { groupId: GROUP.id, title: 'PRESERVED_REPORT', url: 'https://example.test/retained.pdf', note: 'Retained metadata', submittedBy: STUDENT.id, submittedAt: '2026-10-12T00:00:00Z', updatedAt: '2026-10-12T00:00:00Z' };
const json = (route: Route, body: unknown, status = 200) => route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) });
function gate() {
  let release!: () => void;
  const promise = new Promise<void>((resolve) => { release = resolve; });
  return { promise, release };
}
async function session(page: Page, admin: boolean, handler: (route: Route, url: URL) => Promise<boolean>) {
  await page.context().addCookies([{ name: 'cc_csrf', value: 'integrity-fixture', url: test.info().project.use.baseURL as string }]);
  await page.route('**/api/v1/**', async (route) => {
    const url = new URL(route.request().url());
    if (await handler(route, url)) return;
    if (url.pathname.endsWith('/auth/me')) return json(route, admin ? ADMIN : STUDENT);
    if (url.pathname.endsWith('/auth/refresh')) return json(route, { code: 'UNAUTHORIZED' }, 401);
    if (url.pathname.endsWith('/thesis/rounds')) return json(route, [ROUND_A, ROUND_B]);
    if (url.pathname.endsWith('/thesis/groups/mine')) return json(route, []);
    if (url.pathname.endsWith('/thesis/groups') || url.pathname.endsWith('/thesis/topics') || url.pathname.endsWith('/thesis/councils')) return json(route, []);
    if (url.pathname.includes('/notifications/my/unread-count')) return json(route, { unreadCount: 0 });
    if (url.pathname.includes('/notifications/my')) return json(route, { data: [] });
    return json(route, { data: [], meta: { total: 0, page: 1, limit: 100, totalPages: 0 } });
  });
}

for (const outcome of ['success', 'failure'] as const) {
  test(`admin discards delayed round A ${outcome} after round B opens`, async ({ page }) => {
    const held = gate();
    let requested = false;
    let delivered = false;
    await session(page, true, async (route, url) => {
      if (!url.pathname.endsWith('/thesis/councils')) return false;
      const roundId = url.searchParams.get('roundId');
      if (roundId === ROUND_A.id) {
        requested = true;
        await held.promise;
        await json(route, outcome === 'success' ? [{ id: 'c-a', roundId, name: 'STALE_COUNCIL_A', members: [], topicIds: [] }] : { code: 'INTERNAL_ERROR' }, outcome === 'success' ? 200 : 500);
        delivered = true;
      } else await json(route, [{ id: 'c-b', roundId, name: 'CURRENT_COUNCIL_B', members: [], topicIds: [] }]);
      return true;
    });
    await page.goto('/en/admin/thesis');
    await page.getByText(ROUND_A.name, { exact: true }).click();
    await expect.poll(() => requested).toBe(true);
    await page.getByText(ROUND_B.name, { exact: true }).click();
    await expect(page.getByText('CURRENT_COUNCIL_B', { exact: true })).toBeVisible();
    held.release();
    await expect.poll(() => delivered).toBe(true);
    await page.waitForLoadState('networkidle');
    await expect(page.getByText('CURRENT_COUNCIL_B', { exact: true })).toBeVisible();
    await expect(page.getByText('STALE_COUNCIL_A', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Thesis data could not be loaded right now.', { exact: true })).toHaveCount(0);
  });
}

test('admin unchanged schedule save retains every timestamp and supported precision', async ({ page }) => {
  let patch: Record<string, unknown> | null = null;
  await session(page, true, async (route, url) => {
    if (url.pathname.endsWith(`/thesis/rounds/${ROUND_A.id}`) && route.request().method() === 'PUT') {
      patch = route.request().postDataJSON();
      await json(route, { ...ROUND_A, ...patch }); return true;
    }
    return false;
  });
  await page.goto('/en/admin/thesis');
  await page.getByText(ROUND_A.name, { exact: true }).click();
  await page.getByRole('button', { name: 'Edit schedule', exact: true }).click();
  const modal = page.getByRole('dialog');
  await expect(modal.locator('input[type="datetime-local"]').first()).toHaveValue('2026-10-16T17:23:45.123');
  await modal.getByRole('button', { name: 'Edit schedule', exact: true }).click();
  await expect.poll(() => patch).not.toBeNull();
  for (const field of ['registrationStart', 'registrationEnd', 'lecturerSubmitStart', 'lecturerSubmitEnd'] as const) {
    expect(new Date(patch![field] as string).getTime()).toBe(new Date(ROUND_A[field]).getTime());
  }
});

for (const approvalStatus of ['PENDING', 'REJECTED']) {
  test(`student can read an existing ${approvalStatus} report without editing it`, async ({ page }) => {
    await session(page, false, async (route, url) => {
      if (url.pathname.endsWith('/thesis/rounds')) { await json(route, [{ ...ROUND_A, status: 'REGISTRATION_CLOSED' }, ROUND_B]); return true; }
      if (url.pathname.endsWith('/thesis/groups')) { await json(route, [{ ...GROUP, approvalStatus }]); return true; }
      if (url.pathname.endsWith(`/thesis/groups/${GROUP.id}/report`)) { await json(route, { ...REPORT, fileName: 'retained-report.pdf', fileType: 'application/pdf', fileSize: 42 }); return true; }
      return false;
    });
    await page.goto(`/en/dashboard/thesis?roundId=${ROUND_A.id}`);
    await expect(page.getByText(REPORT.title, { exact: true })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Open report', exact: true })).toHaveAttribute('href', REPORT.url);
    await expect(page.getByText('retained-report.pdf', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Download document', exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Remove document', exact: true })).toHaveCount(0);
    await expect(page.getByRole('button', { name: /^(update report|submit report)$/i })).toHaveCount(0);
    await expect(page.locator('#thesis-report-title')).toHaveCount(0);
  });
}

test('dashboard topic request from the previous round never replaces current topics', async ({ page }) => {
  const held = gate();
  let requested = false;
  let delivered = false;
  await session(page, true, async (route, url) => {
    if (!url.pathname.endsWith('/thesis/topics')) return false;
    const roundId = url.searchParams.get('roundId');
    if (roundId === ROUND_A.id) { requested = true; await held.promise; }
    await json(route, [{ id: `topic-${roundId}`, roundId, title: roundId === ROUND_A.id ? 'STALE_TOPIC_A' : 'CURRENT_TOPIC_B', description: 'Integrity fixture', maxGroups: 2, status: 'PUBLISHED', supervisors: [] }]);
    if (roundId === ROUND_A.id) delivered = true;
    return true;
  });
  await page.goto(`/en/dashboard/thesis?roundId=${ROUND_A.id}`);
  await expect.poll(() => requested).toBe(true);
  await page.getByRole('combobox', { name: /round/i }).selectOption(ROUND_B.id);
  await expect(page.getByText('CURRENT_TOPIC_B', { exact: true })).toBeVisible();
  held.release();
  await expect.poll(() => delivered).toBe(true);
  await page.waitForLoadState('networkidle');
  await expect(page.getByText('CURRENT_TOPIC_B', { exact: true })).toBeVisible();
  await expect(page.getByText('STALE_TOPIC_A', { exact: true })).toHaveCount(0);
});

test('student progress opens the older membership round instead of a new empty round', async ({ page }) => {
  await session(page, false, async (route, url) => {
    if (url.pathname.endsWith('/thesis/rounds')) { await json(route, [{ ...ROUND_B, status: 'REGISTRATION_OPEN', thesisType: 'KLTN' }, { ...ROUND_A, status: 'REGISTRATION_CLOSED' }]); return true; }
    if (url.pathname.endsWith('/thesis/groups/mine')) { await json(route, [GROUP]); return true; }
    if (url.pathname.endsWith('/thesis/me/progress')) {
      const mine = url.searchParams.get('roundId') === ROUND_A.id;
      await json(route, { roundId: mine ? ROUND_A.id : ROUND_B.id, roundStatus: 'REGISTRATION_CLOSED', currentMilestone: 'REPORT_SUBMITTED', completedMilestones: ['ROUND_SELECTED', 'GROUP_CREATED', 'REPORT_SUBMITTED'], participationState: mine ? 'PARTICIPATING' : 'NOT_PARTICIPATING', attentionState: 'NONE', group: mine ? GROUP : null }); return true;
    }
    return false;
  });
  await page.goto('/en/dashboard/thesis/progress');
  await expect(page.getByRole('combobox', { name: /round/i })).toHaveValue(ROUND_A.id);
  await expect(page.getByText(ROUND_A.name, { exact: true }).last()).toBeVisible();
});
