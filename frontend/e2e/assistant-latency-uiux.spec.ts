import { test, expect, type Page, type Route } from '@playwright/test';

// These fixtures qualify browser lifecycle and presentation only. Provider
// latency and answer grounding need the separate real-service Browser audit.
const chatStream = '**/api/v1/assistant/chat/stream';
const panelName = /CampusUTE assistant|Trợ lý CampusUTE/i;
const composerName = /Ask about registration, schedules, announcements|Hỏi về đăng ký, lịch học, thông báo/i;
const specializedComposerName = /Ask about programming, databases, testing|Hỏi về lập trình, cơ sở dữ liệu, kiểm thử/i;
const sendName = /Send message|Gửi tin nhắn/i;
const stopName = /Stop generating|Dừng tạo câu trả lời/i;
const newConversationName = /New conversation|Hội thoại mới/i;
const historyName = /Conversation history|Lịch sử hội thoại/i;
const deleteName = /Delete conversation|Xóa hội thoại/i;
const answerName = /Campus helpdesk|Trợ lý học vụ CampusUTE/i;
const userName = /^(You|Bạn)$/i;
const conversationId = '44444444-4444-4444-8444-444444444444';

function jsonResponse(body: unknown, status = 200) {
  return { status, contentType: 'application/json', body: JSON.stringify(body) };
}

function streamAnswer(text: string, requestId: string, locale = 'vi', turn = 1) {
  const frame = (type: string, value: unknown) => `event: ${type}\ndata: ${JSON.stringify(value)}\n\n`;
  return {
    status: 200,
    headers: { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-cache' },
    body: frame('meta', {
      requestId, clientRequestId: requestId, conversationId, locale,
      turnId: `33333333-3333-4333-8333-${String(turn).padStart(12, '0')}`,
      model: 'lexical-fallback',
    }) + frame('delta', { sequence: 0, text }) + frame('done', {
      messageId: `55555555-5555-4555-8555-${String(turn).padStart(12, '0')}`,
      reasonCode: 'ANSWERED', degraded: false,
    }),
  };
}

async function login(page: Page, locale: 'vi' | 'en' = 'vi') {
  await page.goto(`/${locale}/login?portal=student`);
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill('student@campuscore.edu');
  await page.locator('#password').fill('password123');
  await submit.click();
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
}

async function mockAssistantShell(page: Page, savedConversation = false) {
  await page.route('**/api/v1/semesters**', route => route.fulfill(jsonResponse({
    data: [{ id: 'semester-e2e', name: '2026 Spring', status: 'ACTIVE' }],
  })));
  await page.route('**/api/v1/enrollments/my**', route => route.fulfill(jsonResponse([])));
  await page.route(/\/api\/v1\/assistant\/conversations(?:\?.*)?$/, route => route.fulfill(
    route.request().method() === 'GET' ? jsonResponse(savedConversation ? [{
      id: conversationId, title: 'Saved test conversation', locale: 'vi',
      createdAt: '2026-10-02T00:00:00Z', updatedAt: '2026-10-02T00:00:00Z',
    }] : []) : jsonResponse({ id: conversationId, locale: 'vi' }),
  ));
}

async function openPanel(page: Page) {
  const launcher = page.getByRole('button', {
    name: /Open CampusUTE assistant|Mở trợ lý CampusUTE|CampusUTE assistant|Trợ lý CampusUTE/i,
  }).last();
  await launcher.click();
  const panel = page.getByRole('dialog', { name: panelName });
  await expect(panel).toBeVisible();
  return { panel, launcher, composer: panel.getByRole('textbox', { name: composerName }) };
}

async function holdFirstAnswer(page: Page, firstText = 'First answer.', nextText = 'Next answer.') {
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  let finishHeld!: () => void;
  const heldFinished = new Promise<void>(resolve => { finishHeld = resolve; });
  const requestIds: string[] = [];
  const handler = async (route: Route) => {
    const { clientRequestId, locale } = route.request().postDataJSON();
    requestIds.push(clientRequestId);
    const turn = requestIds.length;
    if (turn === 1) {
      try {
        await gate;
        await route.fulfill(streamAnswer(firstText, clientRequestId, locale, turn));
      } finally {
        finishHeld();
      }
    } else {
      await route.fulfill(streamAnswer(nextText, clientRequestId, locale, turn));
    }
  };
  await page.route(chatStream, handler);
  return {
    requestIds,
    release,
    async cleanup() {
      release();
      if (requestIds.length) await heldFinished;
      await page.unroute(chatStream, handler);
    },
  };
}

const diagnostics = new WeakMap<Page, { pageErrorNames: string[]; consoleErrorCount: number }>();
test.beforeEach(({ page }) => {
  const observed = { pageErrorNames: [] as string[], consoleErrorCount: 0 };
  diagnostics.set(page, observed);
  page.on('pageerror', error => observed.pageErrorNames.push(error.name));
  page.on('console', message => { if (message.type() === 'error') observed.consoleErrorCount += 1; });
});
test.afterEach(async ({ page }, testInfo) => {
  const observed = diagnostics.get(page);
  // Counts retain diagnostic evidence without logging auth/provider payloads.
  await testInfo.attach('client-error-counts', {
    body: Buffer.from(JSON.stringify(observed)), contentType: 'application/json',
  });
  expect(observed?.pageErrorNames).toEqual([]);
});

for (const dismissal of ['Escape', 'outside click'] as const) {
  test(`${dismissal} cancels the active request and the reopened panel accepts another send`, async ({ page }) => {
    test.skip(dismissal === 'outside click' && (page.viewportSize()?.width ?? 0) < 768,
      'The mobile sheet fills the viewport; Escape covers its dismissal path.');
    await mockAssistantShell(page);
    const held = await holdFirstAnswer(page, 'Cancelled first answer.', 'Next answer is available.');
    const cancellations: { requestId: string; method: string; authenticated: boolean; csrf: boolean }[] = [];
    await page.route('**/api/v1/assistant/requests/*/cancel', async route => {
      const headers = await route.request().allHeaders();
      cancellations.push({
        requestId: new URL(route.request().url()).pathname.split('/').at(-2)!,
        method: route.request().method(),
        authenticated: /(?:^|;\s*)cc_access_token=/.test(headers.cookie ?? ''),
        csrf: Boolean(headers['x-csrf-token']),
      });
      await route.fulfill(jsonResponse({ status: 'CANCELLED' }));
    });
    try {
      await login(page);
      const { panel, launcher, composer } = await openPanel(page);
      await composer.fill('A question that remains in flight.');
      await panel.getByRole('button', { name: sendName }).click();
      await expect.poll(() => held.requestIds.length).toBe(1);
      await expect(composer).toBeDisabled();
      if (dismissal === 'Escape') await page.keyboard.press('Escape');
      else await page.locator('#dashboard-main-content').click({ position: { x: 15, y: 15 } });
      await expect(panel).not.toBeVisible();
      await expect.poll(() => cancellations.length).toBe(1);
      expect(cancellations[0]).toEqual({
        requestId: held.requestIds[0], method: 'POST', authenticated: true, csrf: true,
      });
      if (dismissal === 'Escape') await expect(launcher).toBeFocused();
      const reopened = await openPanel(page);
      await expect(reopened.composer).toBeEnabled();
      await reopened.composer.fill('A second question after cancellation.');
      await reopened.panel.getByRole('button', { name: sendName }).click();
      await expect(reopened.panel.getByRole('article', { name: answerName }).last()).toContainText('Next answer is available.');
      await expect(reopened.composer).toBeEnabled();
      expect(held.requestIds).toHaveLength(2);
    } finally {
      await held.cleanup();
    }
  });
}

test('Escape cancels only the nested delete confirmation and restores history focus', async ({ page }) => {
  await mockAssistantShell(page, true);
  let deleteCalls = 0;
  await page.route(`**/api/v1/assistant/conversations/${conversationId}`, route => {
    if (route.request().method() === 'DELETE') deleteCalls += 1;
    return route.fulfill(jsonResponse({}, 409));
  });
  await login(page);
  const { panel } = await openPanel(page);
  await panel.getByRole('button', { name: historyName }).click();
  const history = panel.getByRole('region', { name: historyName });
  const deleteButton = history.getByRole('button', { name: /(?:Delete conversation|Xóa hội thoại): Saved test conversation/i });
  await deleteButton.click();
  const confirmation = page.getByRole('dialog', { name: deleteName });
  await expect(confirmation).toBeVisible();
  await expect.poll(() => confirmation.evaluate(node => node.contains(document.activeElement))).toBe(true);
  await page.keyboard.press('Escape');
  await expect(confirmation).not.toBeVisible();
  await expect(panel).toBeVisible();
  await expect(history).toBeVisible();
  await expect(deleteButton).toBeFocused();
  expect(deleteCalls).toBe(0);
});

for (const locale of ['vi', 'en'] as const) {
  test(`specialized ${locale} keeps the stop action reachable and settles without moving the outer page`, async ({ page }) => {
    await mockAssistantShell(page);
    const longAnswer = 'Reviewed advice.\n\n' + Array.from({ length: 32 }, (_, i) => `${i + 1}. Read the reviewed guidance for step ${i + 1}.`).join('\n');
    const held = await holdFirstAnswer(page, longAnswer);
    try {
      await login(page, locale);
      await page.goto(`/${locale}/dashboard/assistant-specialized`);
      const heading = page.getByRole('heading', { level: 1, name: /Specialized Assistant|Trợ lý chuyên sâu/i });
      await expect(heading).toBeVisible();
      await expect(page).toHaveTitle(/Specialized Assistant|Trợ lý chuyên sâu/i);
      const composer = page.getByRole('textbox', { name: specializedComposerName });
      await composer.fill('Explain the reviewed professional guidance.');
      await page.getByRole('button', { name: sendName }).click();
      await expect.poll(() => held.requestIds.length).toBe(1);
      await expect(page.getByRole('button', { name: newConversationName })).toBeDisabled();
      const stop = page.getByRole('button', { name: stopName });
      await expect(stop).toHaveText(locale === 'vi' ? 'Dừng' : 'Stop');
      const stopBox = await stop.boundingBox();
      const viewport = page.viewportSize()!;
      expect(stopBox).not.toBeNull();
      expect(stopBox!.x).toBeGreaterThanOrEqual(0);
      expect(stopBox!.x + stopBox!.width).toBeLessThanOrEqual(viewport.width);
      expect(stopBox!.y + stopBox!.height).toBeLessThanOrEqual(viewport.height);
      expect(stopBox!.height).toBeGreaterThanOrEqual(44);
      const before = await heading.evaluate(node => ({ top: node.getBoundingClientRect().top, scrollY: window.scrollY }));
      held.release();
      const answer = page.getByRole('article', { name: answerName }).last();
      await expect(answer).toContainText('Reviewed advice.');
      await expect(composer).toBeEnabled();
      await expect(composer).toBeFocused();
      await expect(page.getByRole('button', { name: newConversationName })).toBeEnabled();
      await expect(answer.getByRole('list')).toHaveCount(1);
      await expect(answer.getByRole('listitem')).toHaveCount(32);
      const after = await heading.evaluate(node => ({ top: node.getBoundingClientRect().top, scrollY: window.scrollY }));
      expect(after.scrollY).toBe(before.scrollY);
      expect(Math.abs(after.top - before.top)).toBeLessThanOrEqual(1);
      await expect(page.getByRole('log', { name: /Specialized AI Assistant|Trợ lý AI chuyên sâu/i })).toHaveAttribute('tabindex', '0');
    } finally {
      await held.cleanup();
    }
  });
}

test('two mounted composers reference distinct existing hint and count elements', async ({ page }) => {
  await mockAssistantShell(page);
  await login(page);
  await page.goto('/vi/dashboard/assistant-specialized');
  await expect(page.getByRole('textbox', { name: specializedComposerName })).toBeVisible();
  await page.getByRole('button', { name: panelName }).click();
  await expect(page.getByRole('dialog', { name: panelName })).toBeVisible();
  const composers = page.locator('textarea[name="assistant-message"]');
  await expect(composers).toHaveCount(2);
  const descriptions = await composers.evaluateAll(nodes => nodes.map(node => (
    node.getAttribute('aria-describedby') ?? ''
  ).split(/\s+/).map(id => ({
    id, occurrences: Array.from(document.querySelectorAll('[id]')).filter(element => element.id === id).length,
  }))));
  const refs = descriptions.flat();
  expect(descriptions.every(items => items.length === 2)).toBe(true);
  expect(new Set(refs.map(item => item.id)).size).toBe(4);
  expect(refs.every(item => item.id && item.occurrences === 1)).toBe(true);
});

for (const theme of ['light', 'dark'] as const) {
  test(`user Markdown remains literal and readable in ${theme} theme`, async ({ page }) => {
    await mockAssistantShell(page);
    await page.route(chatStream, route => {
      const { clientRequestId, locale } = route.request().postDataJSON();
      return route.fulfill(streamAnswer('Read the published guidance.', clientRequestId, locale));
    });
    await login(page);
    const themeAction = page.getByRole('button', {
      name: theme === 'dark' ? /Switch to dark theme|Chuyển sang giao diện tối/i : /Switch to light theme|Chuyển sang giao diện sáng/i,
    }).first();
    if (await themeAction.isVisible()) await themeAction.click();
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
    const { panel, composer } = await openPanel(page);
    const literal = '**Cần hỗ trợ** [Xem lịch](/dashboard/schedule) `SE421`';
    await composer.fill(literal);
    await panel.getByRole('button', { name: sendName }).click();
    const bubble = panel.getByRole('article', { name: userName }).last();
    await expect(bubble).toHaveText(literal);
    await expect(bubble.locator('a, code, strong')).toHaveCount(0);
    const contrast = await bubble.evaluate(node => {
      const rgb = (color: string) => (color.match(/[\d.]+/g) ?? []).slice(0, 3).map(Number);
      const luminance = (color: string) => rgb(color).map(value => {
        const channel = value / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
      }).reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0);
      const style = getComputedStyle(node);
      const foreground = luminance(style.color);
      const background = luminance(style.backgroundColor);
      return (Math.max(foreground, background) + 0.05) / (Math.min(foreground, background) + 0.05);
    });
    expect(contrast).toBeGreaterThanOrEqual(4.5);
    await expect(panel.getByRole('article', { name: answerName }).last()).toContainText('Read the published guidance.');
  });
}
