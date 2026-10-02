import { test as base, expect, type BrowserContext, type Page, type Route } from '@playwright/test';

// Real local demo authentication; assistant-only fixtures qualify UI recovery
// and request identity, not provider behavior or server-side deduplication.
type StudentSession = Awaited<ReturnType<BrowserContext['storageState']>>;
type ChatRequest = {
  message: string;
  locale: 'vi' | 'en';
  clientRequestId: string;
  conversationId?: string;
  scope?: 'academic' | 'specialized';
};
type AssistantScope = 'academic' | 'specialized';

const launcherName = /Open CampusUTE assistant|Mở trợ lý CampusUTE|CampusUTE assistant|Trợ lý CampusUTE/i;
const panelName = /CampusUTE assistant|Trợ lý CampusUTE/i;
const composerName = /Ask about registration, schedules, announcements|Hỏi về đăng ký, lịch học, thông báo/i;
const specializedComposerName = /Ask about programming, databases, testing|Hỏi về lập trình, cơ sở dữ liệu, kiểm thử/i;
const answerName = /Campus helpdesk|Trợ lý học vụ CampusUTE/i;
const userName = /^(You|Bạn)$/i;
const sendName = /Send message|Gửi tin nhắn/i;
const historyName = /Conversation history|Lịch sử hội thoại/i;
const academicId = '44444444-4444-4444-8444-444444444444';
const specializedId = '66666666-6666-4666-8666-666666666666';
const recoveredAcademic = 'Academic retry recovered the published guidance.';
const recoveredSpecialized = 'Specialized retry recovered the reviewed professional guidance.';
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function json(body: unknown, status = 200) {
  return { status, contentType: 'application/json', body: JSON.stringify(body) };
}

async function login(page: Page, role: 'student' | 'admin') {
  await page.goto(`/vi/login?portal=${role}`);
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(`${role}@campuscore.edu`);
  await page.locator('#password').fill('password123');
  const responsePromise = page.waitForResponse(response => (
    new URL(response.url()).pathname === '/api/v1/auth/login'
    && response.request().method() === 'POST'
  ));
  await submit.click();
  const response = await responsePromise;
  expect(response.status(), 'Local demo login status; response payload is not logged').toBe(200);
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
}

const test = base.extend<{}, { studentSession: StudentSession }>({
  studentSession: [async ({ browser }, use, workerInfo) => {
    const baseURL = String(workerInfo.project.use.baseURL);
    expect(['localhost', '127.0.0.1']).toContain(new URL(baseURL).hostname);
    const context = await browser.newContext({ baseURL });
    try {
      const page = await context.newPage();
      await login(page, 'student');
      // Kept only in memory: no auth state or credential file is written.
      await use(await context.storageState());
    } finally {
      await context.close();
    }
  }, { scope: 'worker' }],
});

async function assistantFixture(page: Page, options: {
  savedHistory?: boolean;
  failFirstScope?: AssistantScope;
} = {}) {
  const streams: ChatRequest[] = [];
  const reconciliations: ChatRequest[] = [];
  const loadedHistory: string[] = [];
  const forbiddenCalls: string[] = [];
  let failed = false;
  const pattern = '**/api/v1/assistant/**';
  const handler = async (route: Route) => {
    const request = route.request();
    const pathname = new URL(request.url()).pathname;
    if (request.method() === 'GET' && pathname === '/api/v1/assistant/conversations') {
      return route.fulfill(json(options.savedHistory ? [{
        id: academicId, title: 'Saved academic guidance', locale: 'vi',
        createdAt: '2026-10-02T00:00:00Z', updatedAt: '2026-10-02T00:00:00Z',
      }] : []));
    }
    if (request.method() === 'GET' && pathname === `/api/v1/assistant/conversations/${academicId}/messages`) {
      loadedHistory.push(academicId);
      return route.fulfill(json([
        { id: '77777777-7777-4777-8777-777777777777', role: 'USER', content: 'Saved campus question.', createdAt: '2026-10-02T00:00:00Z' },
        { id: '88888888-8888-4888-8888-888888888888', role: 'ASSISTANT', content: 'Saved guidance remains available.', model: 'lexical-fallback', reasonCode: 'ANSWERED', degraded: false, citations: [], createdAt: '2026-10-02T00:00:01Z' },
      ]));
    }
    if (request.method() === 'POST' && pathname === '/api/v1/assistant/chat') {
      reconciliations.push(request.postDataJSON() as ChatRequest);
      return route.fulfill(json({ code: 'PROVIDER_UNAVAILABLE' }, 503));
    }
    if (request.method() === 'POST' && pathname === '/api/v1/assistant/chat/stream') {
      const body = request.postDataJSON() as ChatRequest;
      streams.push(body);
      const scope = body.scope ?? 'academic';
      const fail = !failed && scope === options.failFirstScope;
      if (fail) failed = true;
      const frame = (event: string, data: unknown) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;
      const meta = frame('meta', {
        requestId: body.clientRequestId, clientRequestId: body.clientRequestId,
        conversationId: scope === 'specialized' ? specializedId : academicId,
        turnId: `33333333-3333-4333-8333-${String(streams.length).padStart(12, '0')}`,
        locale: body.locale, model: 'lexical-fallback',
      });
      return route.fulfill({
        status: 200,
        headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' },
        body: fail
          ? meta + frame('error', { code: 'PROVIDER_UNAVAILABLE', retryable: true })
          : meta + frame('delta', { sequence: 0, text: scope === 'specialized' ? recoveredSpecialized : recoveredAcademic })
            + frame('done', {
              messageId: `55555555-5555-4555-8555-${String(streams.length).padStart(12, '0')}`,
              reasonCode: 'ANSWERED', degraded: false,
            }),
      });
    }
    // Neither deletion nor unplanned assistant writes can escape to the API.
    forbiddenCalls.push(`${request.method()} ${pathname}`);
    return route.fulfill(json({ code: 'UNEXPECTED_TEST_REQUEST' }, 503));
  };
  await page.route(pattern, handler);
  return {
    streams, reconciliations, loadedHistory, forbiddenCalls,
    async cleanup() { await page.unroute(pattern, handler); },
  };
}

async function openAcademicPanel(page: Page) {
  const launcher = page.getByRole('button', { name: launcherName }).last();
  await expect(launcher).toBeVisible();
  await launcher.click();
  const panel = page.getByRole('dialog', { name: panelName });
  await expect(panel).toBeVisible();
  return { panel, launcher, composer: panel.getByRole('textbox', { name: composerName }) };
}

const pageErrors = new WeakMap<Page, string[]>();
test.beforeEach(({ page }) => {
  const observed: string[] = [];
  pageErrors.set(page, observed);
  page.on('pageerror', error => observed.push(error.name));
});
test.afterEach(({ page }) => {
  expect(pageErrors.get(page), 'No uncaught client error names').toEqual([]);
});

test('saved academic history loads and a visible Retry recovers the original turn without duplicating its question', async ({ page, studentSession }) => {
  const fixture = await assistantFixture(page, { savedHistory: true, failFirstScope: 'academic' });
  try {
    await page.context().addCookies(studentSession.cookies);
    await page.goto('/vi/dashboard');
    const { panel, composer } = await openAcademicPanel(page);
    await panel.getByRole('button', { name: historyName }).click();
    const history = panel.getByRole('region', { name: historyName });
    await expect(history).toBeVisible();
    await history.getByRole('button', { name: 'Saved academic guidance', exact: true }).click();
    await expect(history).not.toBeVisible();
    await expect(panel.getByRole('article', { name: userName })).toHaveText('Saved campus question.');
    await expect(panel.getByRole('article', { name: answerName })).toContainText('Saved guidance remains available.');
    expect(fixture.loadedHistory).toEqual([academicId]);

    const prompt = 'Explain the published campus guidance for this saved question.';
    await composer.fill(prompt);
    await panel.getByRole('button', { name: sendName }).click();
    const alert = panel.getByRole('alert');
    await expect(alert).toContainText('Trợ lý hiện chưa sẵn sàng.');
    const retry = alert.getByRole('button', { name: 'Thử lại', exact: true });
    await expect(retry).toBeVisible();
    await expect(composer).toBeEnabled();
    expect(fixture.streams).toHaveLength(1);
    expect(fixture.reconciliations).toEqual([fixture.streams[0]]);
    expect(fixture.streams[0]).toEqual({ message: prompt, locale: 'vi', clientRequestId: expect.stringMatching(uuid), conversationId: academicId });

    await retry.click();
    await expect(panel.getByRole('article', { name: answerName }).last()).toContainText(recoveredAcademic);
    await expect(alert).not.toBeVisible();
    await expect(composer).toBeEnabled();
    expect(fixture.streams).toHaveLength(2);
    expect(fixture.streams[1]).toEqual(fixture.streams[0]);
    await expect(panel.getByRole('article', { name: userName })).toHaveCount(2);
    await expect(panel.getByRole('article', { name: userName }).filter({ hasText: prompt })).toHaveCount(1);
    expect(fixture.forbiddenCalls).toEqual([]);
  } finally {
    await fixture.cleanup();
  }
});

test('specialized Retry preserves a new turn identity and follow-up scope while the academic panel keeps its own scope', async ({ page, studentSession }) => {
  const fixture = await assistantFixture(page, { failFirstScope: 'specialized' });
  try {
    await page.context().addCookies(studentSession.cookies);
    await page.goto('/en/dashboard/assistant-specialized');
    await expect(page.getByRole('heading', { name: 'Specialized Assistant', exact: true })).toBeVisible();
    const specializedLog = page.getByRole('log', { name: 'Specialized AI Assistant', exact: true });
    const composer = page.getByRole('textbox', { name: specializedComposerName });
    const prompt = 'Explain the reviewed professional guidance for learners.';
    await composer.fill(prompt);
    await page.getByRole('button', { name: sendName }).click();
    const alert = specializedLog.getByRole('alert');
    await expect(alert).toContainText('The assistant is not available right now.');
    const retry = alert.getByRole('button', { name: 'Retry the question', exact: true });
    await expect(retry).toBeVisible();
    await expect(composer).toBeEnabled();
    expect(fixture.streams).toHaveLength(1);
    expect(fixture.streams[0]).toEqual({ message: prompt, locale: 'en', clientRequestId: expect.stringMatching(uuid), scope: 'specialized' });
    expect(fixture.reconciliations).toEqual([fixture.streams[0]]);

    await retry.click();
    await expect(specializedLog.getByRole('article', { name: answerName }).last()).toContainText(recoveredSpecialized);
    await expect(alert).not.toBeVisible();
    await expect(composer).toBeEnabled();
    expect(fixture.streams).toHaveLength(2);
    // The failed meta revealed a conversation, but retry must preserve the
    // original absent conversationId in the canonical request hash.
    expect(fixture.streams[1]).toEqual(fixture.streams[0]);
    await expect(specializedLog.getByRole('article', { name: userName })).toHaveCount(1);

    await composer.fill('Explain one more point from the reviewed professional guidance.');
    await page.getByRole('button', { name: sendName }).click();
    await expect(specializedLog.getByRole('article', { name: answerName })).toHaveCount(2);
    await expect(composer).toBeEnabled();
    expect(fixture.streams).toHaveLength(3);
    expect(fixture.streams[2]).toMatchObject({ conversationId: specializedId, scope: 'specialized', locale: 'en' });
    expect(fixture.streams[2].clientRequestId).toMatch(uuid);
    expect(fixture.streams[2].clientRequestId).not.toBe(fixture.streams[0].clientRequestId);

    const academic = await openAcademicPanel(page);
    await expect(academic.composer).toHaveAttribute('placeholder', /registration, schedules, announcements/i);
    await academic.composer.fill('Explain the published campus guidance for students.');
    await academic.panel.getByRole('button', { name: sendName }).click();
    await expect(academic.panel.getByRole('article', { name: answerName })).toContainText(recoveredAcademic);
    expect(fixture.streams).toHaveLength(4);
    expect(fixture.streams[3]).toEqual({
      message: 'Explain the published campus guidance for students.', locale: 'en', clientRequestId: expect.stringMatching(uuid),
    });
    expect(fixture.streams[3].clientRequestId).not.toBe(fixture.streams[2].clientRequestId);
    expect(fixture.reconciliations).toHaveLength(1);
    expect(fixture.forbiddenCalls).toEqual([]);
  } finally {
    await fixture.cleanup();
  }
});

test('Admin can open the floating assistant and Escape returns focus to its attached launcher', async ({ page }) => {
  const fixture = await assistantFixture(page);
  try {
    await login(page, 'admin');
    await expect(page).toHaveURL(/\/admin(?:$|[/?#])/);
    const { panel, launcher, composer } = await openAcademicPanel(page);
    await expect(composer).toBeEnabled();
    await page.keyboard.press('Escape');
    await expect(panel).not.toBeVisible();
    await expect(launcher).toBeVisible();
    await expect(launcher).toBeFocused();
    expect(await launcher.evaluate(node => node.isConnected)).toBe(true);
    expect(fixture.streams).toEqual([]);
    expect(fixture.reconciliations).toEqual([]);
    expect(fixture.forbiddenCalls).toEqual([]);
  } finally {
    await fixture.cleanup();
  }
});
