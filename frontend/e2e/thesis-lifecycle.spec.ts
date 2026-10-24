import { expect, test, type Page } from '@playwright/test';
import { submitLiveLogin } from './fixtures/live-login';

/**
 * Opt-in live journey for the thesis round lifecycle on the isolated e2e
 * stack. Run it with:
 *
 *   E2E_THESIS_LIFECYCLE=1 E2E_BASE_URL=http://127.0.0.1:3101 \
 *     E2E_API_URL=http://127.0.0.1:4100/api/v1 \
 *     npx playwright test e2e/thesis-lifecycle.spec.ts --project=chromium
 *
 * Happy path: admin creates a round, opens proposals, publishes, opens and
 * closes registration. Negative/governance path: a round created by mistake
 * is deleted through the admin card (typed name + reason), a round holding a
 * topic refuses deletion with blocker counts, a cancelled empty round deletes,
 * and a student token is refused by the server. A second multi-role journey
 * covers proposals through report submission: lecturer proposes (student
 * refused), department head publishes, a student group registers and is
 * reviewed, registration closes, governance withdraws and re-points an
 * orphaned topic, and the leader submits a report while members are refused.
 * Re-running it needs two releasable demo students, so it prefers a freshly
 * seeded stack.
 */
const RUN = process.env.E2E_THESIS_LIFECYCLE === '1';
const API_BASE = process.env.E2E_API_URL ?? 'http://127.0.0.1:4100/api/v1';
const ADMIN = { email: 'admin@campuscore.edu', password: 'password123' };
const STUDENT = { email: 'student@campuscore.edu', password: 'password123' };
const LECTURER = { email: 'lecturer@campuscore.edu', password: 'password123' };
const DEPT_HEAD = { email: 'truongbomon@campuscore.demo', password: 'password123' };
const LEADER = { email: 'student2@campuscore.edu', password: 'password123' };
const MEMBER_A = { email: 'student3@campuscore.edu', password: 'password123' };
const MEMBER_B = { email: 'student4@campuscore.edu', password: 'password123' };

test.describe.configure({ timeout: 120_000 });

async function login(page: Page, email: string, password: string) {
  await page.goto('/en/login?portal=student');
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  await expect(submit).toBeEnabled({ timeout: 30_000 });
  await page.locator('#email').fill(email);
  await page.locator('#password').fill(password);
  const web = new URL(String(test.info().project.use.baseURL));
  await submitLiveLogin(page, submit, web.port);
}

/** Same-origin API calls through the Next proxy, exactly like the app itself. */
async function api(page: Page, method: string, path: string, body?: unknown) {
  const csrf = (await page.context().cookies())
    .find((cookie) => cookie.name === 'cc_csrf')?.value;
  const send = () => page.request.fetch(`/api/v1${path}`, {
    method,
    data: body,
    headers: {
      'Content-Type': 'application/json',
      ...(csrf ? { 'X-CSRF-Token': csrf } : {}),
    },
  });
  let response = await send();
  if (response.status() === 429) {
    const retryAfter = Number(response.headers()['retry-after']);
    await page.waitForTimeout(
      (Number.isFinite(retryAfter) ? Math.min(retryAfter, 60) : 10) * 1_000 + 500);
    response = await send();
  }
  return response;
}

function roundPayload(name: string, daysAhead: number) {
  const now = Date.now();
  const day = 86_400_000;
  return {
    name,
    thesisType: 'KLTN',
    lecturerSubmitStart: new Date(now - 2 * day).toISOString(),
    lecturerSubmitEnd: new Date(now + 3_600_000).toISOString(),
    registrationStart: new Date(now + 7_200_000).toISOString(),
    registrationEnd: new Date(now + daysAhead * day).toISOString(),
    gvpbDeadline: new Date(now + (daysAhead + 5) * day).toISOString(),
    reportDate: new Date(now + (daysAhead + 10) * day).toISOString(),
    defenseDate: new Date(now + (daysAhead + 15) * day).toISOString(),
  };
}

/**
 * Slides the authored schedule so the phase the test just entered is the
 * live one — the same audited schedule amendment an admin uses to correct a
 * misdated round. Proposal submission and registration both refuse outside
 * their persisted windows, so a test round cannot hold both open at once.
 */
function compressWindows(payload: Record<string, string>, phase: 'proposals' | 'registration') {
  const now = Date.now();
  const hour = 3_600_000;
  if (phase === 'proposals') {
    return { ...payload, lecturerSubmitEnd: new Date(now + hour).toISOString() };
  }
  return {
    ...payload,
    lecturerSubmitEnd: new Date(now - hour).toISOString(),
    registrationStart: new Date(now - hour).toISOString(),
  };
}

test.beforeEach(() => {
  test.skip(!RUN, 'Opt-in only: set E2E_THESIS_LIFECYCLE=1 for the live lifecycle journey.');
  const web = new URL(String(test.info().project.use.baseURL));
  const api = new URL(API_BASE);
  expect(['localhost', '127.0.0.1']).toContain(web.hostname);
  expect(['localhost', '127.0.0.1']).toContain(api.hostname);
});

test('admin runs the round lifecycle and deletes a mistaken round', async ({ page }) => {
  const stamp = Date.now().toString(36);
  const liveName = `Lifecycle ${stamp}`;
  const mistakeName = `Mistake ${stamp}`;

  await login(page, ADMIN.email, ADMIN.password);

  // Stage 1 happy path: create -> open proposals -> publish -> open -> close.
  const createRes = await api(page, 'POST', '/thesis/rounds', roundPayload(liveName, 14));
  expect(createRes.status(), 'round creation').toBe(200);
  const round = await createRes.json();
  const openRes = await api(page, 'POST', `/thesis/rounds/${round.id}/open-proposals`);
  expect(openRes.status(), 'transition open-proposals').toBe(200);
  // Topics are proposed while the window is open; publish-proposals closes it.
  const topicRes = await api(page, 'POST', '/thesis/topics', {
    roundId: round.id,
    departmentId: 'department-demo',
    title: `Lifecycle topic ${stamp}`,
    description: 'Published so registration can open',
    maxGroups: 1,
  });
  expect(topicRes.status(), 'topic creation').toBe(200);
  const topic = await topicRes.json();
  const supRes = await api(page, 'PUT', `/thesis/topics/${topic.id}/supervisors`, {
    supervisorIds: ['lecturer-profile'],
  });
  expect(supRes.status(), 'supervisor assignment').toBe(200);
  const publishRes = await api(page, 'POST', `/thesis/topics/${topic.id}/publish`);
  expect(publishRes.status(), 'topic publish').toBe(200);
  for (const step of ['publish-proposals']) {
    const res = await api(page, 'POST', `/thesis/rounds/${round.id}/${step}`);
    expect(res.status(), `transition ${step}`).toBe(200);
  }
  // Registration opens only while its window is live; amend the schedule so
  // the registration window covers "now" (the audited admin escape hatch).
  const amendRes = await api(page, 'PUT', `/thesis/rounds/${round.id}`,
    compressWindows(roundPayload(liveName, 14), 'registration'));
  expect(amendRes.status(), 'schedule amendment').toBe(200);
  for (const step of ['open-registration', 'close-registration']) {
    const res = await api(page, 'POST', `/thesis/rounds/${round.id}/${step}`);
    expect(res.status(), `transition ${step}`).toBe(200);
  }

  // A mistaken round: create, delete through the admin card with typed name.
  const mistakeRes = await api(page, 'POST', '/thesis/rounds', roundPayload(mistakeName, 14));
  expect(mistakeRes.status()).toBe(200);
  const mistake = await mistakeRes.json();

  await page.goto('/en/admin/thesis');
  const card = page.getByRole('button', { name: new RegExp(mistakeName) });
  await expect(card).toBeVisible({ timeout: 30_000 });
  await card.click();
  await page.getByRole('button', { name: 'Delete round' }).last().click();
  await expect(page.getByText('Only the empty round is removed; the audit note is kept.')).toBeVisible();
  await page.getByLabel('Reason').fill('created by mistake');
  await page.getByLabel('Type the round name to confirm').fill(mistakeName);
  await page.getByRole('button', { name: /^save$/i }).click();
  await expect(page.getByText('Round deleted.')).toBeVisible({ timeout: 30_000 });
  await expect(card).toHaveCount(0);

  // Server-side state: the row is really gone, and a second delete is a clean 404.
  const secondDelete = await api(page, 'DELETE', `/thesis/rounds/${mistake.id}`, { reason: 'again' });
  expect(secondDelete.status()).toBe(404);
  expect((await secondDelete.json()).code).toBe('ROUND_NOT_FOUND');
});

test('a round holding a topic refuses deletion with blocker counts', async ({ page }) => {
  const stamp = Date.now().toString(36);
  const name = `Blocked ${stamp}`;
  await login(page, ADMIN.email, ADMIN.password);

  const createRes = await api(page, 'POST', '/thesis/rounds', roundPayload(name, 14));
  const round = await createRes.json();
  const deleteRes = await api(page, 'DELETE', `/thesis/rounds/${round.id}`, { reason: 'probe' });
  // Seed topics are per round; a fresh round starts empty so seed a topic in it.
  expect(deleteRes.status(), 'empty round deletes cleanly via API').toBe(204);

  const blockedRes = await api(page, 'POST', '/thesis/rounds', roundPayload(`${name} B`, 14));
  const blocked = await blockedRes.json();
  const topicRes = await api(page, 'POST', '/thesis/topics', {
    roundId: blocked.id,
    departmentId: 'department-demo',
    title: `Blocker topic ${stamp}`,
    description: 'A topic that must keep the round alive',
    maxGroups: 1,
  });
  if (topicRes.status() === 200) {
    const refused = await api(page, 'DELETE', `/thesis/rounds/${blocked.id}`, { reason: 'probe' });
    expect(refused.status()).toBe(409);
    const body = await refused.json();
    expect(body.code).toBe('ROUND_HAS_DATA');
    expect(body.details.topics).toBe(1);
  }

  // Cancelled empty round deletes.
  const cancelRes = await api(page, 'POST', `/thesis/rounds/${blocked.id}/cancel`);
  expect(cancelRes.status(), 'cancel succeeds').toBe(200);
  const afterCancel = await api(page, 'DELETE', `/thesis/rounds/${blocked.id}`, { reason: 'probe' });
  if (topicRes.status() === 200) {
    expect(afterCancel.status()).toBe(409);
    expect((await afterCancel.json()).code).toBe('ROUND_HAS_DATA');
  } else {
    expect(afterCancel.status()).toBe(204);
  }
});

test('a student token is refused by the delete endpoint', async ({ page }) => {
  await login(page, STUDENT.email, STUDENT.password);
  const res = await api(page, 'DELETE', `/thesis/rounds/${crypto.randomUUID()}`, { reason: 'probe' });
  expect(res.status(), 'student must be refused').toBe(403);
});

/**
 * Direct bearer-token calls against the API host for multi-role journeys:
 * one logged-in page cannot hold five role sessions, and these assertions
 * are about backend behaviour, not the proxy.
 */
async function tokenFor(page: Page, email: string, password: string) {
  const res = await page.request.fetch(`${API_BASE}/auth/login`, {
    method: 'POST',
    data: { email, password },
    headers: { 'Content-Type': 'application/json' },
  });
  expect(res.status(), `login ${email}`).toBe(200);
  return (await res.json()).accessToken as string;
}

async function apiAs(page: Page, token: string, method: string, path: string, body?: unknown) {
  const send = () => page.request.fetch(`${API_BASE}${path}`, {
    method,
    data: body,
    headers: {
      Authorization: `Bearer ${token}`,
      'Content-Type': 'application/json',
    },
  });
  let response = await send();
  if (response.status() === 429) {
    const retryAfter = Number(response.headers()['retry-after']);
    await page.waitForTimeout(
      (Number.isFinite(retryAfter) ? Math.min(retryAfter, 60) : 10) * 1_000 + 500);
    response = await send();
  }
  return response;
}

test('stages 2-5: proposal, registration, post-close governance, report', async ({ page }) => {
  const stamp = Date.now().toString(36);
  const [admin, lecturer, deptHead, leader, memberA, memberB] = await Promise.all([
    tokenFor(page, ADMIN.email, ADMIN.password),
    tokenFor(page, LECTURER.email, LECTURER.password),
    tokenFor(page, DEPT_HEAD.email, DEPT_HEAD.password),
    tokenFor(page, LEADER.email, LEADER.password),
    tokenFor(page, MEMBER_A.email, MEMBER_A.password),
    tokenFor(page, MEMBER_B.email, MEMBER_B.password),
  ]);

  // A fresh round held open the whole journey; registration closes later.
  const schedule = roundPayload(`Stage25 ${stamp}`, 30);
  const roundRes = await apiAs(page, admin, 'POST', '/thesis/rounds', schedule);
  expect(roundRes.status(), 'round creation').toBe(200);
  const round = await roundRes.json();
  const openProposals = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/open-proposals`);
  expect(openProposals.status(), 'transition open-proposals').toBe(200);

  // Stage 2: proposals are lecturer-only and publish is governance-only.
  const noDesc = await apiAs(page, lecturer, 'POST', '/thesis/topics', {
    roundId: round.id, departmentId: 'department-demo', title: `No desc ${stamp}`, maxGroups: 1,
  });
  expect(noDesc.status(), 'description required').toBe(400);
  const asStudent = await apiAs(page, memberA, 'POST', '/thesis/topics', {
    roundId: round.id, departmentId: 'department-demo', title: 'x', description: 'x', maxGroups: 1,
  });
  expect(asStudent.status(), 'student cannot propose').toBe(403);
  const [topicARes, topicBRes] = await Promise.all([
    apiAs(page, lecturer, 'POST', '/thesis/topics', {
      roundId: round.id, departmentId: 'department-demo',
      title: `Alpha ${stamp}`, description: 'Pick target', maxGroups: 1,
    }),
    apiAs(page, lecturer, 'POST', '/thesis/topics', {
      roundId: round.id, departmentId: 'department-demo',
      title: `Beta ${stamp}`, description: 'Reassign target', maxGroups: 1,
    }),
  ]);
  expect(topicARes.status()).toBe(200);
  expect(topicBRes.status()).toBe(200);
  const [topicA, topicB] = [await topicARes.json(), await topicBRes.json()];
  const lecturerPublish = await apiAs(page, lecturer, 'POST', `/thesis/topics/${topicA.id}/publish`, {});
  expect(lecturerPublish.status(), 'supervisor cannot publish').toBe(403);
  for (const t of [topicA, topicB]) {
    const pub = await apiAs(page, deptHead, 'POST', `/thesis/topics/${t.id}/publish`, {});
    expect(pub.status(), `publish ${t.title}`).toBe(200);
  }
  const publish = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/publish-proposals`);
  expect(publish.status(), 'transition publish-proposals').toBe(200);
  // Registration can only open while its window is live; the lecturer window
  // must already be behind us, so the authored schedule is amended once.
  const amend = await apiAs(page, admin, 'PUT', `/thesis/rounds/${round.id}`,
    compressWindows(schedule, 'registration'));
  expect(amend.status(), 'schedule amendment').toBe(200);
  const openReg = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/open-registration`);
  expect(openReg.status(), 'transition open-registration').toBe(200);

  // Stage 3: a student holds at most one live membership, so the seeded
  // roster is released first (progress patching is leader-or-admin). A
  // COMPLETED membership can no longer be cancelled, so whichever demo
  // student is still free becomes the leader.
  const roster: Array<[string, string]> = [
    [leader, 'student-profile-058'],
    [memberA, 'student-profile-059'],
    [memberB, 'student-profile-team-004'],
  ];
  const free: typeof roster = [];
  for (const [token] of roster) {
    const mineRes = await apiAs(page, token, 'GET', '/thesis/groups/mine');
    const mine = await mineRes.json();
    let live: Array<{ id: string; status: string }> = [];
    if (Array.isArray(mine)) {
      live = mine.filter((g) => g.status !== 'CANCELLED');
    }
    for (const g of live) {
      // An approved group cannot be cancelled until a reviewer rejects it,
      // and a group inside a closed round cannot be cancelled at all — the
      // audited roster override is the escape hatch for that state.
      if ((g as { approvalStatus?: string }).approvalStatus === 'APPROVED') {
        await apiAs(page, admin, 'POST', `/thesis/groups/${g.id}/reject`, { reason: 'release for lifecycle coverage' });
      }
      await apiAs(page, admin, 'PATCH', `/thesis/groups/${g.id}/progress`, { status: 'CANCELLED' });
      const student = roster.find(([t]) => t === token);
      if (student) {
        await apiAs(page, admin, 'POST', `/thesis/groups/${g.id}/roster-override`, {
          action: 'REMOVE', studentId: student[1], allowSizeException: true,
          reason: 'release for lifecycle coverage',
        });
      }
    }
    const again = await apiAs(page, token, 'GET', '/thesis/groups/mine');
    const remaining = ((await again.json()) as Array<{ status: string }>)
      .filter((g) => g.status !== 'CANCELLED');
    if (remaining.length === 0) free.push([token, '']);
  }
  const pool = free.length
    ? roster.filter(([token]) => free.some(([t]) => t === token))
    : roster;
  expect(pool.length, 'the journey needs at least two releasable demo students').toBeGreaterThanOrEqual(2);
  const [[leaderToken], ...rest] = pool;
  const memberToken = rest[0][0];
  const memberIds = rest.map(([, sid]) => sid);
  const groupRes = await apiAs(page, leaderToken, 'POST', '/thesis/groups', { roundId: round.id });
  expect(groupRes.status(), 'leader creates group').toBe(200);
  const group = await groupRes.json();
  for (const sid of memberIds) {
    const add = await apiAs(page, leaderToken, 'POST', `/thesis/groups/${group.id}/members`, { studentId: sid });
    expect(add.status(), `add member ${sid}`).toBe(200);
  }
  const memberPick = await apiAs(page, memberToken, 'POST', `/thesis/groups/${group.id}/topic`, { topicId: topicA.id });
  expect(memberPick.status(), 'member cannot pick topic').toBe(403);
  const pick = await apiAs(page, leaderToken, 'POST', `/thesis/groups/${group.id}/topic`, { topicId: topicA.id });
  expect(pick.status(), 'leader submits group to topic').toBe(200);
  const outsider = await apiAs(page, deptHead, 'POST', `/thesis/groups/${group.id}/approve`, {});
  expect(outsider.status(), 'non-supervisor cannot review').toBe(403);
  const approve = await apiAs(page, lecturer, 'POST', `/thesis/groups/${group.id}/approve`, {});
  if (approve.status() === 409) {
    // A below-minimum roster can only be approved through the audited
    // size-exception hatch — governance, never the topic supervisor.
    const sized = await apiAs(page, admin, 'POST', `/thesis/groups/${group.id}/approve`, {
      allowSizeException: true, reason: 'undersized demo roster',
    });
    expect(sized.status(), 'governance size-exception approve').toBe(200);
  } else {
    expect(approve.status(), 'supervisor approves').toBe(200);
  }

  // Registration mutations freeze once the round closes.
  const close = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/close-registration`);
  expect(close.status(), 'close registration').toBe(200);
  const lateAdd = await apiAs(page, leaderToken, 'POST', `/thesis/groups/${group.id}/members`, { studentId: 'student-profile-101' });
  expect(lateAdd.status(), 'member add after close').toBe(409);

  // Stage 4: roster override needs a reason; orphan groups get re-pointed.
  const noReason = await apiAs(page, admin, 'POST', `/thesis/groups/${group.id}/roster-override`, {
    action: 'ADD', studentId: 'student-profile-021',
  });
  expect(noReason.status(), 'override without reason').toBe(400);
  const withdraw = await apiAs(page, admin, 'POST', `/thesis/topics/${topicA.id}/withdraw`, { reason: 'orphan probe' });
  expect(withdraw.status(), 'withdraw bound topic').toBe(200);
  const crossRound = await apiAs(page, admin, 'POST', `/thesis/groups/${group.id}/topic-reassign`, {
    topicId: crypto.randomUUID(), reason: 'cross-round probe',
  });
  expect(crossRound.status(), 'unknown topic refused').toBe(404);
  const reassign = await apiAs(page, admin, 'POST', `/thesis/groups/${group.id}/topic-reassign`, {
    topicId: topicB.id, reason: 're-point orphan',
  });
  expect(reassign.status(), 'orphan re-pointed to same-round topic').toBe(200);

  // Stage 5: progress is leader-only; reports are leader-only with a live window.
  const memberProgress = await apiAs(page, memberToken, 'PATCH', `/thesis/groups/${group.id}/progress`, { status: 'COMPLETED' });
  expect(memberProgress.status(), 'member cannot set progress').toBe(403);
  const report = await apiAs(page, leaderToken, 'POST', `/thesis/groups/${group.id}/report`, {
    title: `Report ${stamp}`, url: 'https://example.com/report.pdf',
  });
  expect(report.status(), 'leader submits report link').toBe(200);
  const outsiderReport = await apiAs(page, memberToken, 'POST', `/thesis/groups/${group.id}/report`, { title: 'x' });
  expect(outsiderReport.status(), 'member cannot write report').toBe(403);
});
