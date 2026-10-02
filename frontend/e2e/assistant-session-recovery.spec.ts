import { test as base, expect, type BrowserContext, type Locator, type Page, type Route } from '@playwright/test';

// Assistant refusal fixtures exercise the visible recovery gate. Local demo
// authentication/refresh remain real; no auth state is persisted to disk.
type StudentSession = Awaited<ReturnType<BrowserContext['storageState']>>;
type ChatRequest = {
  message: string;
  locale: 'vi' | 'en';
  clientRequestId: string;
  conversationId?: string;
  scope?: 'academic' | 'specialized';
};
type Scope = 'academic' | 'specialized';

const assistantPattern = '**/api/v1/assistant/**';
const launcherName = /Open CampusUTE assistant|Mở trợ lý CampusUTE|CampusUTE assistant|Trợ lý CampusUTE/i;
const panelName = /CampusUTE assistant|Trợ lý CampusUTE/i;
const academicComposerName = /Ask about registration, schedules, announcements|Hỏi về đăng ký, lịch học, thông báo/i;
const specializedComposerName = /Ask about programming, databases, testing|Hỏi về lập trình, cơ sở dữ liệu, kiểm thử/i;
const sendName = /Send message|Gửi tin nhắn/i;
const newConversationName = /New conversation|Hội thoại mới/i;
const retryName = /^(Retry|Retry the question|Thử lại|Gửi lại câu hỏi)$/i;
const conversationId = '44444444-4444-4444-8444-444444444444';

function json(body: unknown, status = 200) {
  return { status, contentType: 'application/json', body: JSON.stringify(body) };
}

async function login(page: Page) {
  await page.goto('/vi/login?portal=student');
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill('student@campuscore.edu');
  await page.locator('#password').fill('password123');
  const responsePromise = page.waitForResponse(response => (
    new URL(response.url()).pathname === '/api/v1/auth/login'
    && response.request().method() === 'POST'
  ));
  await submit.click();
  expect((await responsePromise).status(), 'Local demo login HTTP status; no payload logged').toBe(200);
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
}

const test = base.extend<{}, { studentSession: StudentSession }>({
  studentSession: [async ({ browser }, use, workerInfo) => {
    const baseURL = String(workerInfo.project.use.baseURL);
    expect(['localhost', '127.0.0.1']).toContain(new URL(baseURL).hostname);
    const context = await browser.newContext({ baseURL });
    try {
      await login(await context.newPage());
      await use(await context.storageState());
    } finally {
      await context.close();
    }
  }, { scope: 'worker' }],
});

async function sessionRefusalFixture(page: Page) {
  const streams: ChatRequest[] = [];
  const reconciliations: ChatRequest[] = [];
  const conversationCreates: string[] = [];
  const forbiddenCalls: string[] = [];
  const refreshStatuses: number[] = [];
  const refreshListener = (response: import('@playwright/test').Response) => {
    if (new URL(response.url()).pathname === '/api/v1/auth/refresh') refreshStatuses.push(response.status());
  };
  page.on('response', refreshListener);
  const handler = async (route: Route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (request.method() === 'GET' && pathname === '/api/v1/assistant/conversations') {
      return route.fulfill(json([]));
    }
    if (request.method() === 'POST' && pathname === '/api/v1/assistant/conversations') {
      conversationCreates.push(request.postDataJSON().locale);
      return route.fulfill(json({ id: conversationId, locale: request.postDataJSON().locale }));
    }
    if (request.method() === 'POST' && pathname === '/api/v1/assistant/chat/stream') {
      streams.push(request.postDataJSON() as ChatRequest);
      // The same refusal after successful real refresh tests the terminal401
      // UI. It does not expire the real login or alter global auth policy.
      return route.fulfill(json({ code: 'UNAUTHORIZED' }, 401));
    }
    if (request.method() === 'POST' && pathname === '/api/v1/assistant/chat') {
      reconciliations.push(request.postDataJSON() as ChatRequest);
      // Keep JSON unavailable: its global401 redirect must not mask the
      // assistant's own sign-in recovery. The known stream401 stays terminal.
      return route.fulfill(json({ code: 'PROVIDER_UNAVAILABLE' }, 503));
    }
    forbiddenCalls.push(`${request.method()} ${pathname}`);
    return route.fulfill(json({ code: 'UNEXPECTED_TEST_REQUEST' }, 503));
  };
  await page.route(assistantPattern, handler);
  return {
    streams, reconciliations, conversationCreates, forbiddenCalls, refreshStatuses,
    async cleanup() {
      await page.unroute(assistantPattern, handler);
      page.off('response', refreshListener);
    },
  };
}

async function openAcademicPanel(page: Page) {
  const launcher = page.getByRole('button', { name: launcherName }).last();
  await expect(launcher).toBeVisible();
  await launcher.click();
  const panel = page.getByRole('dialog', { name: panelName });
  await expect(panel).toBeVisible();
  return panel;
}

type Diagnostics = { pageErrorNames: string[] };
const diagnostics = new WeakMap<Page, Diagnostics>();
test.beforeEach(({ page }) => {
  const observed: Diagnostics = { pageErrorNames: [] };
  diagnostics.set(page, observed);
  page.on('pageerror', error => observed.pageErrorNames.push(error.name));
});
test.afterEach(async ({ page, studentSession }, testInfo) => {
  // Real refresh can rotate cookies. Carry that rotation to the next test in
  // this worker without printing or writing an authentication state file.
  studentSession.cookies = await page.context().cookies();
  expect(diagnostics.get(page)?.pageErrorNames, 'No uncaught client errors').toEqual([]);
  await testInfo.attach('session-recovery-client-error-counts', {
    body: Buffer.from(JSON.stringify(diagnostics.get(page))), contentType: 'application/json',
  });
});

const scenarios = [
  {
    scope: 'academic' as Scope, locale: 'vi' as const,
    composerName: academicComposerName, signIn: 'Đăng nhập lại',
    expired: 'Lần đăng nhập đã hết hạn. Vui lòng đăng nhập lại.',
    lockedHint: 'Cần đăng nhập lại trước khi tiếp tục hỏi trợ lý.',
    suggestions: ['Lớp tôi đang học', 'TKB tuần này của tôi', 'Điểm của tôi thế nào?', 'Điều kiện tốt nghiệp'],
  },
  {
    scope: 'specialized' as Scope, locale: 'en' as const,
    composerName: specializedComposerName, signIn: 'Sign in again',
    expired: 'Your session has expired. Please sign in again.',
    lockedHint: 'Asking is locked until you sign in again.',
    suggestions: [
      'How do SOLID principles and design patterns apply?',
      'Database normalization and SQL query tuning',
      'Effective unit testing and TDD',
      'Software engineering career roadmap',
    ],
  },
];

for (const scenario of scenarios) {
  test(`${scenario.scope} ${scenario.locale} refuses generation through suggestions and new chat after401 until locale-correct sign-in`, async ({ page, studentSession }, testInfo) => {
    test.setTimeout(60_000);
    const fixture = await sessionRefusalFixture(page);
    try {
      await page.context().addCookies(studentSession.cookies);
      await page.goto(`/${scenario.locale}/dashboard${scenario.scope === 'specialized' ? '/assistant-specialized' : ''}`);
      const workspace = scenario.scope === 'academic'
        ? await openAcademicPanel(page)
        : page.locator('#dashboard-main-content');
      const composer = workspace.getByRole('textbox', { name: scenario.composerName });
      const prompt = 'Explain the reviewed guidance available for learners.';
      await composer.fill(prompt);
      await workspace.getByRole('button', { name: sendName }).click();
      await expect.poll(() => fixture.reconciliations.length).toBe(1);
      await expect(workspace.getByRole('button', { name: /Stop generating|Dừng tạo câu trả lời/i })).toHaveCount(0);
      expect(fixture.streams).toHaveLength(2);
      expect(fixture.streams[1]).toEqual(fixture.streams[0]);
      expect(fixture.reconciliations).toEqual([fixture.streams[0]]);
      expect(fixture.refreshStatuses).toEqual([200]);
      expect(fixture.streams[0]).toMatchObject({ message: prompt, locale: scenario.locale });
      if (scenario.scope === 'specialized') expect(fixture.streams[0].scope).toBe('specialized');
      else expect(fixture.streams[0].scope).toBeUndefined();

      const signIn = workspace.getByRole('button', { name: scenario.signIn, exact: true });
      const requireLockedSession = async (checkpoint: string) => {
        await expect.soft(workspace, `${checkpoint}: localized expiry copy`).toContainText(scenario.expired, { timeout: 500 });
        await expect.soft(signIn, `${checkpoint}: localized sign-in action`).toBeVisible({ timeout: 500 });
        await expect.soft(composer, `${checkpoint}: asking remains locked`).toBeDisabled({ timeout: 500 });
        await expect.soft(workspace, `${checkpoint}: lock explained`).toContainText(scenario.lockedHint, { timeout: 500 });
        await expect.soft(workspace.getByRole('button', { name: retryName }), `${checkpoint}: no impossible Retry`).toHaveCount(0);
      };
      await requireLockedSession('initial401');

      const initialCounts = { streams: fixture.streams.length, json: fixture.reconciliations.length };
      const tryVisibleControl = async (control: Locator, checkpoint: string) => {
        if (await control.isVisible() && await control.isEnabled()) {
          const previousJson = fixture.reconciliations.length;
          // This is a bounded negative observation window after a real user
          // click, not a timing delay used to make a positive assertion pass.
          const reattempt = page.waitForRequest(request => (
            request.method() === 'POST'
            && /\/api\/v1\/assistant\/chat(?:\/stream)?$/.test(new URL(request.url()).pathname)
          ), { timeout: 600 }).then(() => true, () => false);
          await control.click();
          if (await reattempt) {
            await expect.poll(() => fixture.reconciliations.length).toBeGreaterThan(previousJson);
            await expect(workspace.getByRole('button', { name: /Stop generating|Dừng tạo câu trả lời/i })).toHaveCount(0);
          }
        }
        expect.soft(fixture.streams.length, `${checkpoint}: no additional stream requests`).toBe(initialCounts.streams);
        expect.soft(fixture.reconciliations.length, `${checkpoint}: no additional JSON requests`).toBe(initialCounts.json);
        await requireLockedSession(checkpoint);
      };

      for (const suggestion of scenario.suggestions) {
        await tryVisibleControl(workspace.getByRole('button', { name: suggestion, exact: true }), `suggestion ${suggestion}`);
      }
      await tryVisibleControl(workspace.getByRole('button', { name: newConversationName }).first(), 'new conversation');
      if (await composer.isEnabled()) {
        // Expose reset's actual consequence if it lifted the lock; the same
        // hard request-count oracle applies to this visible Send action.
        await composer.fill('Explain one more point from the reviewed guidance.');
        await tryVisibleControl(workspace.getByRole('button', { name: sendName }), 'send after reset');
      }
      if (scenario.scope === 'academic') {
        const historyToggle = workspace.getByRole('button', { name: 'Lịch sử hội thoại', exact: true });
        if (await historyToggle.isVisible() && await historyToggle.isEnabled()) {
          await historyToggle.click();
          const history = workspace.getByRole('region', { name: 'Lịch sử hội thoại', exact: true });
          await expect(history).toBeVisible();
          await requireLockedSession('history open');
          await tryVisibleControl(history.getByRole('button', { name: newConversationName }), 'history new conversation');
          if (await history.isVisible()) {
            const back = history.getByRole('button', { name: /Quay lại chat|Back to chat/i });
            if (await back.isVisible() && await back.isEnabled()) await back.click();
          }
        }
        await page.keyboard.press('Escape');
        await expect(workspace).not.toBeVisible();
        await openAcademicPanel(page);
        await requireLockedSession('close and reopen');
      }

      await testInfo.attach('session-recovery-request-counts', {
        body: Buffer.from(JSON.stringify({
          scope: scenario.scope, locale: scenario.locale, initialCounts,
          finalCounts: { streams: fixture.streams.length, json: fixture.reconciliations.length },
          conversationCreates: fixture.conversationCreates.length,
          refreshStatuses: fixture.refreshStatuses, forbiddenCalls: fixture.forbiddenCalls,
        })), contentType: 'application/json',
      });
      expect(fixture.forbiddenCalls).toEqual([]);
      if (await signIn.isVisible()) {
        await signIn.click();
        await expect(page).toHaveURL(new RegExp(`/${scenario.locale}/login(?:\\?|$)`));
        expect(new URL(page.url()).searchParams.get('reason')).toBe('session-expired');
        await expect(page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i })).toBeVisible();
      } else {
        await expect.soft(signIn, 'Sign-in navigation must be reachable').toBeVisible({ timeout: 500 });
      }
    } finally {
      await fixture.cleanup();
    }
  });
}
