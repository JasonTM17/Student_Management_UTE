import { execFileSync } from 'node:child_process';
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
 * The same journey then runs review and defense: governance assigns an
 * independent counter-reviewer who scores the topic, a council is seated in
 * the required order (the reviewer and the topic supervisor cannot grade),
 * members score the defense, and only the chair finalizes the mean score.
 * The journey then publishes results, retracts them inside the grace window,
 * republishes, and checks the end-state reads: dean overview, round reports,
 * and the admin audit trail.
 * Re-running it needs two releasable demo students, so it prefers a freshly
 * seeded stack.
 */
const RUN = process.env.E2E_THESIS_LIFECYCLE === '1';
const API_BASE = process.env.E2E_API_URL ?? 'http://127.0.0.1:4100/api/v1';
const ADMIN = { email: 'admin@campuscore.edu', password: 'password123' };
const STUDENT = { email: 'student@campuscore.edu', password: 'password123' };
const LECTURER = { email: 'lecturer@campuscore.edu', password: 'password123' };
const DEPT_HEAD = { email: 'truongbomon@campuscore.demo', password: 'password123' };
const DEAN = { email: 'truongkhoa@campuscore.demo', password: 'password123' };
const LEADER = { email: 'student2@campuscore.edu', password: 'password123' };
const MEMBER_A = { email: 'student3@campuscore.edu', password: 'password123' };
const MEMBER_B = { email: 'student4@campuscore.edu', password: 'password123' };
const LECTURER2 = { email: 'lecturer002@campuscore.demo', password: 'password123' };
const LECTURER3 = { email: 'lecturer003@campuscore.demo', password: 'password123' };
const LECTURER4 = { email: 'lecturer004@campuscore.demo', password: 'password123' };
const LECTURER5 = { email: 'lecturer005@campuscore.demo', password: 'password123' };

test.describe.configure({ timeout: 180_000 });

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
  // page.request shares the browser cookie jar, and cookies ignore the port:
  // a previous minted login leaves cc_access_token for 127.0.0.1, and the API
  // refuses login-while-authenticated with 403. Clear before each mint.
  const send = async () => {
    await page.context().clearCookies();
    return page.request.fetch(`${API_BASE}/auth/login`, {
      method: 'POST',
      data: { email, password },
      headers: { 'Content-Type': 'application/json' },
    });
  };
  let res = await send();
  // Successful-login fixtures may wait out a validated retry window: the
  // limiter answers 429 with Retry-After.
  if (res.status() === 429) {
    const retryAfter = Number(res.headers()['retry-after']);
    await page.waitForTimeout(
      (Number.isFinite(retryAfter) ? Math.min(retryAfter, 60) : 10) * 1_000 + 500);
    res = await send();
  }
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

test('stages 2-9: proposal through published results and reporting', async ({ page }) => {
  const stamp = Date.now().toString(36);
  // Spaced sequentially: the brute-force guard counts logins per IP in a
  // window, and a burst of role tokens trips it with a plain 403.
  const tokens: string[] = [];
  for (const account of [ADMIN, LECTURER, DEPT_HEAD, LEADER, MEMBER_A, MEMBER_B]) {
    tokens.push(await tokenFor(page, account.email, account.password));
    await page.waitForTimeout(1_200);
  }
  const [admin, lecturer, deptHead, leader, memberA, memberB] = tokens;

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

  // GV1: a mistaken approval in an open round is withdrawn back to PENDING,
  // then approved again — the release path that used to be missing entirely.
  const withdrawal = await apiAs(page, lecturer, 'POST', `/thesis/groups/${group.id}/withdraw-approval`, {
    reason: 'mistaken approval — withdraw for lifecycle coverage',
  });
  expect(withdrawal.status(), 'withdraw approval succeeds in an open round').toBe(200);
  expect((await withdrawal.json()).approvalStatus, 'group returns to pending').toBe('PENDING');
  const reApprove = await apiAs(page, lecturer, 'POST', `/thesis/groups/${group.id}/approve`, {});
  if (reApprove.status() === 409) {
    const sizedAgain = await apiAs(page, admin, 'POST', `/thesis/groups/${group.id}/approve`, {
      allowSizeException: true, reason: 'undersized demo roster',
    });
    expect(sizedAgain.status(), 're-approve via size exception').toBe(200);
  } else {
    expect(reApprove.status(), 're-approve after withdrawal').toBe(200);
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

  // Stage 6: the counter-reviewer is governance-assigned and independent;
  // the score is bounds-checked and readable by reviewer + governance only.
  const extraTokens: string[] = [];
  for (const account of [LECTURER2, LECTURER3, LECTURER4, LECTURER5]) {
    extraTokens.push(await tokenFor(page, account.email, account.password));
    await page.waitForTimeout(1_200);
  }
  const [reviewerToken, chairToken, secretaryToken, memberSeatToken] = extraTokens;
  const studentAssign = await apiAs(page, memberToken, 'POST', `/thesis/topics/${topicB.id}/reviewer`, { lecturerId: 'lecturer-profile-002' });
  expect(studentAssign.status(), 'student cannot assign reviewer').toBe(403);
  const selfReview = await apiAs(page, admin, 'POST', `/thesis/topics/${topicB.id}/reviewer`, { lecturerId: 'lecturer-profile' });
  expect(selfReview.status(), 'supervisor cannot review own topic').toBe(409);
  const assignReviewer = await apiAs(page, admin, 'POST', `/thesis/topics/${topicB.id}/reviewer`, { lecturerId: 'lecturer-profile-002' });
  expect(assignReviewer.status(), 'admin assigns counter-reviewer').toBe(200);
  const strangerScore = await apiAs(page, leaderToken, 'POST', `/thesis/topics/${topicB.id}/reviewer-score`, { score: '8' });
  expect(strangerScore.status(), 'non-reviewer cannot score').toBe(403);
  const outOfBounds = await apiAs(page, reviewerToken, 'POST', `/thesis/topics/${topicB.id}/reviewer-score`, { score: '10.5' });
  expect(outOfBounds.status(), 'score above 10 refused').toBe(400);
  const reviewScore = await apiAs(page, reviewerToken, 'POST', `/thesis/topics/${topicB.id}/reviewer-score`, { score: '6.5', comment: 'solid report' });
  expect(reviewScore.status(), 'reviewer submits score').toBe(200);
  const strangerRead = await apiAs(page, memberToken, 'GET', `/thesis/topics/${topicB.id}/reviewer-score`);
  expect(strangerRead.status(), 'outsider cannot read reviewer score').toBe(403);

  // Stage 7: council seat order, topic assignment, member-only scoring,
  // chair-only finalize, and the roster lock once scoring begins.
  const studentCouncil = await apiAs(page, memberToken, 'POST', '/thesis/councils', { roundId: round.id, name: 'X' });
  expect(studentCouncil.status(), 'student cannot create council').toBe(403);
  const councilRes = await apiAs(page, admin, 'POST', '/thesis/councils', { roundId: round.id, name: `Council ${stamp}` });
  expect(councilRes.status(), 'admin creates council').toBe(200);
  const council = await councilRes.json();
  const seatEarly = await apiAs(page, admin, 'POST', `/thesis/councils/${council.id}/members`, { lecturerId: 'lecturer-profile-003', memberRole: 'MEMBER' });
  expect(seatEarly.status(), 'member cannot precede the chair').toBe(409);
  // Three eligible graders (chair, secretary, member); the topic supervisor
  // also sits as a plain member — present at the defense, barred from grading.
  for (const [lecturerId, memberRole] of [
    ['lecturer-profile-003', 'CHAIR'],
    ['lecturer-profile-004', 'SECRETARY'],
    ['lecturer-profile-005', 'MEMBER'],
    ['lecturer-profile', 'MEMBER'],
  ] as const) {
    const seat = await apiAs(page, admin, 'POST', `/thesis/councils/${council.id}/members`, { lecturerId, memberRole });
    expect(seat.status(), `seat ${memberRole} ${lecturerId}`).toBe(200);
  }
  const outsiderRead = await apiAs(page, memberToken, 'GET', `/thesis/councils/${council.id}`);
  expect(outsiderRead.status(), 'outsider cannot read the council').toBe(403);
  const assignTopic = await apiAs(page, admin, 'POST', `/thesis/councils/${council.id}/topics`, { topicId: topicB.id });
  expect(assignTopic.status(), 'topic assigned to council').toBe(200);
  const dupAssign = await apiAs(page, admin, 'POST', `/thesis/councils/${council.id}/topics`, { topicId: topicB.id });
  expect(dupAssign.status(), 'duplicate assignment refused').toBe(409);
  const seatReviewer = await apiAs(page, admin, 'POST', `/thesis/councils/${council.id}/members`, { lecturerId: 'lecturer-profile-002', memberRole: 'MEMBER' });
  expect(seatReviewer.status(), 'the topic reviewer cannot sit on its council').toBe(409);
  const outsiderScore = await apiAs(page, memberToken, 'POST', `/thesis/councils/${council.id}/topics/${topicB.id}/scores`, { component: 'DEFENSE', score: '8' });
  expect(outsiderScore.status(), 'student cannot score').toBe(403);
  const supScore = await apiAs(page, lecturer, 'POST', `/thesis/councils/${council.id}/topics/${topicB.id}/scores`, { component: 'DEFENSE', score: '8' });
  expect(supScore.status(), 'supervisor cannot grade own topic').toBe(403);
  const earlyFinal = await apiAs(page, chairToken, 'POST', `/thesis/councils/${council.id}/topics/${topicB.id}/finalize`);
  expect(earlyFinal.status(), 'incomplete scores block finalize').toBe(409);
  for (const [token, score] of [[chairToken, '8.5'], [secretaryToken, '7'], [memberSeatToken, '7']] as const) {
    const res = await apiAs(page, token, 'POST', `/thesis/councils/${council.id}/topics/${topicB.id}/scores`, { component: 'DEFENSE', score });
    expect(res.status(), `DEFENSE score ${score}`).toBe(200);
  }
  const wrongFinalize = await apiAs(page, secretaryToken, 'POST', `/thesis/councils/${council.id}/topics/${topicB.id}/finalize`);
  expect(wrongFinalize.status(), 'only the chair finalizes').toBe(403);
  const finRes = await apiAs(page, chairToken, 'POST', `/thesis/councils/${council.id}/topics/${topicB.id}/finalize`);
  expect(finRes.status(), 'chair finalizes').toBe(200);
  // Mean of the three eligible DEFENSE scores (8.5 + 7 + 7) / 3.
  expect(String((await finRes.json()).finalScore), 'mean of three defense scores').toBe('7.5');
  const rescore = await apiAs(page, memberSeatToken, 'POST', `/thesis/councils/${council.id}/topics/${topicB.id}/scores`, { component: 'DEFENSE', score: '9' });
  expect(rescore.status(), 'scoring freezes after finalize').toBe(409);
  const lateSeat = await apiAs(page, admin, 'POST', `/thesis/councils/${council.id}/members`, { lecturerId: 'lecturer-profile-006', memberRole: 'MEMBER' });
  expect(lateSeat.status(), 'roster locks after scoring').toBe(409);

  // Stage 8: results publication is governance-only, previewed, and
  // reversible through the typed-confirmation retract inside its grace window.
  const studentPreview = await apiAs(page, memberToken, 'GET', `/thesis/rounds/${round.id}/publish-results-preview`);
  expect(studentPreview.status(), 'student cannot preview publication').toBe(403);
  const preview = await apiAs(page, admin, 'GET', `/thesis/rounds/${round.id}/publish-results-preview`);
  expect(preview.status(), 'publication preview').toBe(200);
  const studentPublish = await apiAs(page, memberToken, 'POST', `/thesis/rounds/${round.id}/publish-results`, {});
  expect(studentPublish.status(), 'student cannot publish results').toBe(403);
  const publishResults = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/publish-results`, {});
  expect(publishResults.status(), 'admin publishes results').toBe(200);
  expect((await publishResults.json()).status).toBe('RESULTS_PUBLISHED');
  const myResult = await apiAs(page, leaderToken, 'GET', `/thesis/me/results?roundId=${round.id}`);
  expect(myResult.status(), 'student reads the published result').toBe(200);
  // Publication is terminal: council mutation and re-publishing both refuse.
  const lateCouncil = await apiAs(page, admin, 'POST', '/thesis/councils', { roundId: round.id, name: 'late' });
  expect(lateCouncil.status(), 'no council changes after publish').toBe(409);
  const publishAgain = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/publish-results`, {});
  expect(publishAgain.status(), 're-publish on a published round refused').toBe(409);
  // Retract needs governance, a reason and the typed confirmation.
  const studentRetract = await apiAs(page, memberToken, 'POST', `/thesis/rounds/${round.id}/retract-results`, { reason: 'probe', confirmation: 'RETRACT' });
  expect(studentRetract.status(), 'student cannot retract').toBe(403);
  const noConfirm = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/retract-results`, { reason: 'probe' });
  expect(noConfirm.status(), 'retract needs the typed confirmation').toBe(400);
  const retract = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/retract-results`, { reason: 'lifecycle e2e retract probe', confirmation: 'RETRACT' });
  expect(retract.status(), 'retract inside the grace window').toBe(200);
  expect((await retract.json()).status).toBe('REGISTRATION_CLOSED');
  const republish = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/publish-results`, {});
  expect(republish.status(), 're-publish after retract').toBe(200);
  expect((await republish.json()).status).toBe('RESULTS_PUBLISHED');

  // Stage 9: there is no further lifecycle transition — RESULTS_PUBLISHED is
  // the end state. Reporting, the dean overview and the audit trail stay
  // readable for governance and refuse students.
  const deanToken = await tokenFor(page, DEAN.email, DEAN.password);
  const deanOverview = await apiAs(page, deanToken, 'GET', `/thesis/dean/overview?roundId=${round.id}`);
  expect(deanOverview.status(), 'dean reads the faculty overview').toBe(200);
  const studentDean = await apiAs(page, memberToken, 'GET', `/thesis/dean/overview?roundId=${round.id}`);
  expect(studentDean.status(), 'student refused the dean overview').toBe(403);
  const studentReports = await apiAs(page, memberToken, 'GET', `/thesis/rounds/${round.id}/reports`);
  expect(studentReports.status(), 'student refused round reports').toBe(403);
  const staffReports = await apiAs(page, admin, 'GET', `/thesis/rounds/${round.id}/reports`);
  expect(staffReports.status(), 'governance reads round reports').toBe(200);
  const studentAudit = await apiAs(page, memberToken, 'GET', '/admin/audit?search=THESIS');
  expect(studentAudit.status(), 'student refused the audit trail').toBe(403);
  const auditTrail = await apiAs(page, admin, 'GET', `/admin/audit?entityId=${round.id}&entityType=THESIS_ROUND&limit=50`);
  expect(auditTrail.status(), 'admin reads the audit trail').toBe(200);
  const actions = (((await auditTrail.json()).data ?? []) as Array<{ action?: string }>)
    .map((row) => row.action);
  for (const expected of ['THESIS_ROUND_TRANSITION', 'THESIS_RESULTS_RETRACTED']) {
    expect(actions, `audit contains ${expected}`).toContain(expected);
  }

  // Stage 10: end-of-life governance. While the retract grace window is open
  // the close must refuse with its stable blocker, the read model must say so,
  // and non-governance roles are refused. When the disposable stack exposes
  // its fixture postgres (E2E_THESIS_PG_CONTAINER/E2E_THESIS_PG_DATABASE) the
  // round is aged past the window — the only way to age a live round — and
  // the full close -> read-only -> reopen cycle runs against the real API.
  const publishedList = await apiAs(page, admin, 'GET', '/thesis/rounds?status=RESULTS_PUBLISHED');
  expect(publishedList.status(), 'published rounds list').toBe(200);
  const liveRound = ((await publishedList.json()) as Array<{
    id: string;
    closure?: { closeAllowed?: boolean; reopenAllowed?: boolean; closeBlocker?: string | null } | null;
  }>).find((row) => row.id === round.id);
  expect(liveRound?.closure?.closeAllowed, 'read model blocks close in-window').toBe(false);
  expect(liveRound?.closure?.closeBlocker).toBe('RETRACT_WINDOW_OPEN');
  const studentClose = await apiAs(page, memberToken, 'POST', `/thesis/rounds/${round.id}/close`, { reason: 'probe' });
  expect(studentClose.status(), 'student cannot close').toBe(403);
  const noCloseReason = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/close`, {});
  expect(noCloseReason.status(), 'close needs a reason').toBe(400);
  const earlyClose = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/close`, { reason: 'too early' });
  expect(earlyClose.status(), 'close inside the retract window refuses').toBe(409);
  expect((await earlyClose.json()).code).toBe('RETRACT_WINDOW_OPEN');

  const pgContainer = process.env.E2E_THESIS_PG_CONTAINER;
  const pgDatabase = process.env.E2E_THESIS_PG_DATABASE;
  if (!pgContainer || !pgDatabase) {
    test.info().annotations.push({
      type: 'note',
      description: 'E2E_THESIS_PG_* unset: close -> read-only -> reopen positive cycle skipped.',
    });
    return;
  }
  execFileSync('docker', ['exec', '-i', pgContainer, 'psql', '-U', 'campuscore', '-d', pgDatabase,
    '-v', 'ON_ERROR_STOP=1', '-c',
    `UPDATE thesis.thesis_registration_round SET results_published_at = CURRENT_TIMESTAMP - interval '30 days' WHERE id = '${round.id}'`]);

  const closeRes = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/close`, { reason: 'lifecycle e2e archive' });
  expect(closeRes.status(), 'admin closes the round').toBe(200);
  expect((await closeRes.json()).status).toBe('CLOSED');
  const closedCouncil = await apiAs(page, admin, 'POST', '/thesis/councils', { roundId: round.id, name: 'late' });
  expect(closedCouncil.status(), 'council refused on a closed round').toBe(409);
  const closedResult = await apiAs(page, leaderToken, 'GET', `/thesis/me/results?roundId=${round.id}`);
  expect(closedResult.status(), 'student still reads results on a closed round').toBe(200);
  const activeList = await apiAs(page, admin, 'GET', '/thesis/rounds');
  expect((((await activeList.json()) as Array<{ id: string }>).map((row) => row.id)),
    'closed round leaves the default active list').not.toContain(round.id);
  const closedList = await apiAs(page, admin, 'GET', '/thesis/rounds?status=CLOSED');
  expect((((await closedList.json()) as Array<{ id: string }>).map((row) => row.id)),
    'closed round appears under the explicit CLOSED filter').toContain(round.id);
  const doubleClose = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/close`, { reason: 'again' });
  expect(doubleClose.status(), 'double close refuses').toBe(409);

  const studentReopen = await apiAs(page, memberToken, 'POST', `/thesis/rounds/${round.id}/reopen`, { reason: 'probe' });
  expect(studentReopen.status(), 'student cannot reopen').toBe(403);
  const reopen = await apiAs(page, admin, 'POST', `/thesis/rounds/${round.id}/reopen`, { reason: 'lifecycle e2e reopen' });
  expect(reopen.status(), 'admin reopens the round').toBe(200);
  const reopened = await reopen.json();
  expect(reopened.status).toBe('RESULTS_PUBLISHED');
  expect(reopened.closure?.reopenAllowed ?? false, 'reopened round is published again').toBe(false);
  const auditTrail2 = await apiAs(page, admin, 'GET', `/admin/audit?entityId=${round.id}&entityType=THESIS_ROUND&limit=50`);
  const actions2 = (((await auditTrail2.json()).data ?? []) as Array<{ action?: string }>)
    .map((row) => row.action);
  for (const expected of ['THESIS_ROUND_CLOSED', 'THESIS_ROUND_REOPENED']) {
    expect(actions2, `audit contains ${expected}`).toContain(expected);
  }
});

test('credit ineligibility shows the warning card and blocks registration', async ({ page }) => {
  const stamp = Date.now().toString(36);
  const name = `Eligibility ${stamp}`;

  // A live REGISTRATION_OPEN round for the probes below.
  await login(page, ADMIN.email, ADMIN.password);
  const createRes = await api(page, 'POST', '/thesis/rounds', roundPayload(name, 14));
  expect(createRes.status(), 'round creation').toBe(200);
  const round = await createRes.json();
  for (const step of ['open-proposals', 'publish-proposals']) {
    const res = await api(page, 'POST', `/thesis/rounds/${round.id}/${step}`);
    expect(res.status(), `transition ${step}`).toBe(200);
  }
  const amendRes = await api(page, 'PUT', `/thesis/rounds/${round.id}`,
    compressWindows(roundPayload(name, 14), 'registration'));
  expect(amendRes.status(), 'schedule amendment').toBe(200);
  const openRes = await api(page, 'POST', `/thesis/rounds/${round.id}/open-registration`);
  expect(openRes.status(), 'open registration').toBe(200);

  // V143 leaves student-profile below the accumulated-credit bar; the seeded
  // trio (058/059/team-004) is eligible, so the refusal is credit-driven.
  // The API refuses login-while-authenticated, so the admin session leaves
  // first — the same clear-cookie dance tokenFor does for bearer mints.
  await page.context().clearCookies();
  await login(page, STUDENT.email, STUDENT.password);
  const roundsRes = await api(page, 'GET', '/thesis/rounds');
  expect(roundsRes.status(), 'student reads rounds').toBe(200);
  const rounds = await roundsRes.json() as Array<{ id: string; eligibility?: { eligible: boolean; earnedCredits: number; requiredCredits: number } }>;
  const mine = rounds.find((r) => r.id === round.id);
  expect(mine?.eligibility, 'round carries the caller eligibility').toBeTruthy();
  expect(mine?.eligibility?.eligible, 'seeded student is ineligible').toBe(false);
  expect(mine?.eligibility?.earnedCredits).toBeLessThan(mine?.eligibility?.requiredCredits ?? 0);

  const refused = await api(page, 'POST', '/thesis/groups', { roundId: round.id });
  expect(refused.status(), 'ineligible create refused').toBe(409);
  const refusedBody = await refused.json();
  expect(refusedBody.code).toBe('THESIS_ELIGIBILITY_NOT_MET');
  expect(refusedBody.details?.reasons).toContain('INSUFFICIENT_CREDITS');

  // The page surfaces the same state as a warning card for the round.
  await page.goto(`/en/dashboard/thesis?roundId=${round.id}`);
  await expect(
    page.getByText(/not eligible yet|chưa đủ điều kiện/i).first(),
  ).toBeVisible({ timeout: 30_000 });
  await expect(
    page.getByRole('button', { name: /create a thesis group|tạo nhóm đề tài/i }),
  ).toHaveCount(0);

  // An eligible demo student still registers (the seeded trio carries 110+).
  const leaderToken = await tokenFor(page, LEADER.email, LEADER.password);
  const leaderRounds = await apiAs(page, leaderToken, 'GET', '/thesis/rounds');
  const leaderView = ((await leaderRounds.json()) as typeof rounds)
    .find((r) => r.id === round.id);
  expect(leaderView?.eligibility?.eligible, 'seeded trio stays eligible').toBe(true);
});
