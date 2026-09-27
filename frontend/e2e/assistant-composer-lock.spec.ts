import { test, expect, type Page } from '@playwright/test';

// Covers the composer lock contract fixed by fix/chatbot-grounding-and-input:
// while a turn is in flight the panel is visibly locked (disabled textarea,
// Stop + "Responding…" button, aria-live hint) instead of silently swallowing
// later sends, and a settled conversation immediately accepts a new send.

const student = { email: 'student@campuscore.edu', password: 'password123' };

const assistantLauncherName = /Open CampusUTE assistant|Mở trợ lý CampusUTE|CampusUTE assistant|Trợ lý CampusUTE/i;
const assistantPanelTitle = /CampusUTE assistant|Trợ lý CampusUTE/i;
const composerName = /Ask about registration, schedules, announcements|Hỏi về đăng ký, lịch học, thông báo/i;
const sendButtonName = /Send message|Gửi tin nhắn/i;
const stopButtonName = /Stop generating|Dừng tạo câu trả lời/i;
const chatLogName = /Campus helpdesk|Trợ lý học vụ CampusUTE/i;

async function login(page: Page) {
  await page.goto('/login?portal=student');
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  // The login form keeps controls disabled until client hydration completes.
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(student.email);
  await page.locator('#password').fill(student.password);
  await submit.click();
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
}

function jsonResponse(body: unknown, status = 200) {
  return {
    status,
    contentType: 'application/json',
    body: JSON.stringify(body),
  };
}

async function mockAssistantShell(page: Page) {
  await page.route('**/api/v1/semesters**', (route) =>
    route.fulfill(jsonResponse({ data: [{ id: 'semester-1', name: '2026 Spring', status: 'ACTIVE' }] })),
  );
  await page.route('**/api/v1/enrollments/my**', (route) =>
    route.fulfill(jsonResponse([])),
  );
  await page.route('**/api/v1/assistant/conversations**', (route) =>
    route.fulfill(route.request().method() === 'GET'
      ? jsonResponse([])
      : jsonResponse({ id: 'conversation-e2e', locale: 'en' })),
  );
}

function settledStreamAnswer(text: string) {
  return {
    status: 200,
    headers: {
      'Content-Type': 'text/event-stream',
      'Cache-Control': 'no-cache',
      Connection: 'keep-alive',
    },
    body: [
      'event: meta\n',
      'data: {"requestId":"11111111-1111-4111-8111-111111111111","clientRequestId":"22222222-2222-4222-8222-222222222222","turnId":"33333333-3333-4333-8333-333333333333","conversationId":"44444444-4444-4444-8444-444444444444","model":"lexical-fallback","locale":"en"}\n\n',
      `event: delta\ndata: {"sequence":0,"text":"${text}"}\n\n`,
      'event: done\ndata: {"messageId":"55555555-5555-4555-8555-555555555555","reasonCode":"ANSWERED","degraded":false}\n\n',
    ].join(''),
  };
}

async function openAssistantPanel(page: Page) {
  const launcher = page.getByRole('button', { name: assistantLauncherName }).last();
  await expect(launcher).toBeVisible();
  await launcher.click();
  await expect(page.getByRole('dialog')).toContainText(assistantPanelTitle);
  return page.getByRole('textbox', { name: composerName });
}

test('composer locks while the assistant is answering and unlocks when the turn settles', async ({ page }) => {
  await mockAssistantShell(page);

  // Hold the stream open so the turn stays genuinely in flight.
  let releaseStream!: () => void;
  const streamGate = new Promise<void>((resolve) => {
    releaseStream = resolve;
  });
  await page.route('**/api/v1/assistant/chat/stream', (route) =>
    streamGate.then(() => route.fulfill(settledStreamAnswer('Use the published thesis guide.'))),
  );

  await login(page);
  const composer = await openAssistantPanel(page);
  await composer.fill('What campus guidance is available for new learners?');
  await page.getByRole('button', { name: sendButtonName }).click();

  // Locked panel: the textarea is disabled, the send button became a Stop
  // button labelled with the responding state, and the aria-live hint says why.
  await expect(composer).toBeDisabled();
  const stopButton = page.getByRole('button', { name: stopButtonName });
  await expect(stopButton).toBeVisible();
  await expect(stopButton).toContainText(/Responding…|Đang trả lời…/i);
  await expect(page.getByRole('dialog')).toContainText(/the composer is locked|ô nhập tạm khóa/i);
  // The user message itself must still be visible (the send was not swallowed).
  await expect(page.getByRole('article', { name: chatLogName })).toContainText(
    'What campus guidance is available for new learners?',
  );

  releaseStream();
  await expect(page.getByRole('article', { name: chatLogName })).toContainText('Use the published thesis guide.');
  await expect(composer).toBeEnabled();
  await expect(page.getByRole('button', { name: sendButtonName })).toBeVisible();
});

test('a settled conversation accepts the next send immediately (no stuck lock)', async ({ page }) => {
  await mockAssistantShell(page);

  let streamCalls = 0;
  await page.route('**/api/v1/assistant/chat/stream', (route) => {
    streamCalls += 1;
    return route.fulfill(
      settledStreamAnswer(streamCalls === 1 ? 'First grounded answer.' : 'Second grounded answer.'),
    );
  });

  await login(page);
  const composer = await openAssistantPanel(page);

  await composer.fill('first question');
  await page.getByRole('button', { name: sendButtonName }).click();
  await expect(page.getByRole('article', { name: chatLogName })).toContainText('First grounded answer.');
  await expect(composer).toBeEnabled();

  await composer.fill('second question');
  await page.getByRole('button', { name: sendButtonName }).click();
  await expect(page.getByRole('article', { name: chatLogName })).toContainText('Second grounded answer.');
  expect(streamCalls).toBe(2);
});
