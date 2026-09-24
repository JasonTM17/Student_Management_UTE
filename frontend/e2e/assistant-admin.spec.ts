import { test, expect, type Page } from '@playwright/test';

const student = { email: 'student@campuscore.edu', password: 'password123' };
const admin = { email: 'admin@campuscore.edu', password: 'password123' };

const assistantLauncherName = /Open CampusUTE assistant|Mở trợ lý CampusUTE|CampusUTE assistant|Trợ lý CampusUTE/i;
const assistantPanelTitle = /CampusUTE assistant|Trợ lý CampusUTE/i;
const assistantCloseName = /Close CampusUTE assistant|Đóng trợ lý CampusUTE/i;
const mobileNavigationName = /campus navigation on mobile|điều hướng cổng học vụ trên điện thoại/i;

async function login(
  page: Page,
  account: typeof student,
  portal: 'student' | 'lecturer' | 'admin' = account.email.startsWith('admin') ? 'admin' : 'student',
) {
  await page.goto(`/login?portal=${portal}`);
  const submit = page.locator('form').getByRole('button', { name: /sign in|đăng nhập/i });
  // The login form keeps controls disabled until client hydration completes;
  // its demo-credential effect can otherwise overwrite values filled too early.
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(account.email);
  await page.locator('#password').fill(account.password);
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

const assistantMessageId = '55555555-5555-4555-8555-555555555555';

function streamedAssistantAnswer() {
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
      'event: delta\n',
      'data: {"sequence":0,"text":"Use the published thesis guide."}\n\n',
      `event: done\ndata: {"messageId":"${assistantMessageId}","reasonCode":"ANSWERED","degraded":false}\n\n`,
    ].join(''),
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

async function openAssistantAndSubmit(page: Page, prompt: string) {
  const launcher = page.getByRole('button', { name: assistantLauncherName }).last();
  await expect(launcher).toBeVisible();
  await launcher.click();
  await expect(page.getByRole('dialog')).toContainText(assistantPanelTitle);
  const composer = page.getByRole('textbox', { name: /Ask about registration, schedules, announcements|Hỏi về đăng ký, lịch học, thông báo/i });
  await composer.fill(prompt);
  await page.getByRole('button', { name: /Send message|Gửi tin nhắn/i }).click();
}

test('authenticated student can use the assistant launcher, stream, citation, and feedback', async ({ page }) => {
  await page.route('**/api/v1/semesters**', (route) =>
    route.fulfill(jsonResponse({ data: [{ id: 'semester-1', name: '2026 Spring', status: 'ACTIVE' }] })),
  );
  await page.route('**/api/v1/enrollments/my**', (route) =>
    route.fulfill(jsonResponse([])),
  );

  let feedbackCalls = 0;
  await page.route('**/api/v1/assistant/conversations**', (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill(jsonResponse([]));
    }
    return route.fulfill(jsonResponse({ id: 'conversation-e2e', locale: 'en' }));
  });
  await page.route('**/api/v1/assistant/chat/stream', (route) =>
    route.fulfill({
      status: 200,
      headers: {
        'Content-Type': 'text/event-stream',
        'Cache-Control': 'no-cache',
        Connection: 'keep-alive',
      },
      body: [
        'event: meta\n',
        'data: {"requestId":"11111111-1111-4111-8111-111111111111","clientRequestId":"22222222-2222-4222-8222-222222222222","turnId":"33333333-3333-4333-8333-333333333333","conversationId":"44444444-4444-4444-8444-444444444444","model":"lexical-fallback","locale":"en"}\n\n',
        'event: delta\n',
        'data: {"sequence":0,"text":"Use the published thesis guide.","sourceIds":["thesis-guide"]}\n\n',
        'event: citation\n',
        'data: {"citation":{"id":"citation-e2e","title":"Thesis guide","source":"curated","locale":"en","excerpt":"Published thesis workflow guidance.","domain":"THESIS","sourceKind":"CURATED","sourceId":"thesis-guide","snapshotHash":"hash-e2e"}}\n\n',
        'event: done\n',
        'data: {"messageId":"55555555-5555-4555-8555-555555555555","reasonCode":"ANSWERED","degraded":false}\n\n',
      ].join(''),
    }),
  );
  await page.route('**/api/v1/assistant/messages/**', (route) => {
    if (route.request().method() === 'PUT') {
      feedbackCalls += 1;
      return route.fulfill(jsonResponse({ messageId: '55555555-5555-4555-8555-555555555555', rating: 'UP', reason: 'HELPFUL', removed: false }));
    }
    return route.fulfill(jsonResponse({}, 204));
  });

  await login(page, student);
  await expect(page).toHaveURL(/\/dashboard(?:$|[/?#])/);

  const launcher = page.getByRole('button', { name: assistantLauncherName }).last();
  await expect(launcher).toBeVisible();
  await launcher.click();
  // Opening the panel is intentionally side-effect free. Sources appear only
  // after a submitted question, so an empty conversation cannot look like a
  // fabricated answer.
  await expect(page.getByRole('dialog')).toContainText(assistantPanelTitle);
  await page.getByRole('button', { name: /Conversation history|Lịch sử hội thoại/i }).click();
  await expect(page.getByText(/No saved conversations yet\.|Chưa có hội thoại nào được lưu\./i)).toBeVisible();
  await page.getByRole('button', { name: /Back to chat|Quay lại chat/i }).click();

  const composer = page.getByRole('textbox', { name: /Ask about registration, schedules, announcements|Hỏi về đăng ký, lịch học, thông báo/i });
  await composer.fill('What campus guidance is available for new learners?');
  await page.getByRole('button', { name: /Send message|Gửi tin nhắn/i }).click();
  await expect(page.getByRole('article', { name: /Campus helpdesk|Trợ lý học vụ CampusUTE/i })).toContainText('Use the published thesis guide.');
  // Citations are a collapsible card (default closed); expand it before asserting.
  await page.getByRole('button', { name: /Sources|Nguồn tham khảo/i }).click();
  await expect(page.getByText('Thesis guide', { exact: true })).toBeVisible();

  await page.getByRole('button', { name: /Mark answer helpful|Đánh dấu câu trả lời hữu ích/i }).click();
  await expect.poll(() => feedbackCalls).toBe(1);
});

test('assistant feedback failure keeps the saved rating truthful and can retry PUT and DELETE', async ({ page }) => {
  await mockAssistantShell(page);
  await page.route('**/api/v1/assistant/chat/stream', (route) => route.fulfill(streamedAssistantAnswer()));

  let putCalls = 0;
  let deleteCalls = 0;
  const putPayloads: Array<{ rating: string; reason?: string }> = [];
  let releaseFirstPut!: () => void;
  const firstPutGate = new Promise<void>((resolve) => {
    releaseFirstPut = resolve;
  });
  await page.route('**/api/v1/assistant/messages/**', (route) => {
    if (route.request().method() === 'PUT') {
      putCalls += 1;
      putPayloads.push(route.request().postDataJSON());
      if (putCalls === 1) return firstPutGate.then(() => route.fulfill(jsonResponse({}, 503)));
      if (putCalls === 4) return route.fulfill(jsonResponse({}, 503));
      return route.fulfill(jsonResponse({}, 200));
    }
    deleteCalls += 1;
    return route.fulfill(deleteCalls === 1 ? jsonResponse({}, 503) : { status: 204, body: '' });
  });

  await login(page, student);
  await openAssistantAndSubmit(page, 'What campus guidance is available for new learners?');

  const feedback = page.locator(`[data-assistant-feedback="${assistantMessageId}"]`);
  const helpful = feedback.getByRole('button', { name: /Mark answer helpful|Đánh dấu câu trả lời hữu ích/i });
  await expect(page.getByRole('article', { name: /Campus helpdesk|Trợ lý học vụ CampusUTE/i })).toContainText('Use the published thesis guide.');

  await helpful.click();
  await expect(feedback.getByRole('status')).toContainText(/saving feedback|đang lưu phản hồi/i);
  await expect(helpful).toBeDisabled();
  await expect(helpful).toHaveAttribute('aria-pressed', 'false');
  releaseFirstPut();
  await expect(feedback.getByRole('alert')).toContainText(/feedback was not saved|phản hồi chưa được lưu/i);
  await expect(helpful).toHaveAttribute('aria-pressed', 'false');
  await feedback.getByRole('button', { name: /Retry feedback|Thử lưu lại/i }).click();
  await expect(helpful).toHaveAttribute('aria-pressed', 'true');
  await expect(feedback.getByRole('alert')).toHaveCount(0);
  expect(putCalls).toBe(2);

  const notHelpful = feedback.getByRole('button', { name: /Mark answer not helpful|Đánh dấu câu trả lời chưa hữu ích/i });
  await notHelpful.click();
  await expect(notHelpful).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: /Incorrect|Sai thông tin/i }).click();
  await expect(feedback.getByRole('alert')).toContainText(/feedback was not saved|phản hồi chưa được lưu/i);
  await expect(notHelpful).toHaveAttribute('aria-pressed', 'true');
  await feedback.getByRole('button', { name: /Retry feedback|Thử lưu lại/i }).click();
  await expect(feedback.getByRole('alert')).toHaveCount(0);
  expect(putCalls).toBe(5);
  expect(putPayloads[3]).toMatchObject({ rating: 'DOWN', reason: 'INCORRECT' });

  await notHelpful.click();
  await expect(feedback.getByRole('alert')).toContainText(/feedback was not saved|phản hồi chưa được lưu/i);
  await expect(notHelpful).toHaveAttribute('aria-pressed', 'true');
  await feedback.getByRole('button', { name: /Retry feedback|Thử lưu lại/i }).click();
  await expect(notHelpful).toHaveAttribute('aria-pressed', 'false');
  await expect(feedback.getByRole('alert')).toHaveCount(0);
  expect(deleteCalls).toBe(2);
});

test('assistant retry resends the same prompt with the same idempotency key', async ({ page }) => {
  await mockAssistantShell(page);

  const prompts: Array<{ message: string; clientRequestId: string }> = [];
  let streamCalls = 0;
  let fallbackCalls = 0;
  await page.route('**/assistant/chat**', async (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/assistant/chat/stream')) {
      prompts.push(route.request().postDataJSON());
      streamCalls += 1;
      if (streamCalls === 1) {
        return route.fulfill(jsonResponse({ message: 'Temporary network failure' }, 503));
      }
      return route.fulfill(streamedAssistantAnswer());
    }
    fallbackCalls += 1;
    return route.fulfill(jsonResponse({ message: 'Temporary network failure' }, 503));
  });

  await login(page, student);
  const prompt = 'What campus guidance is available for new learners?';
  await openAssistantAndSubmit(page, prompt);
  await expect.poll(() => streamCalls).toBe(1);
  await expect.poll(() => fallbackCalls).toBe(1);
  const alert = page.getByRole('dialog').getByRole('alert');
  await expect(alert).toBeVisible();
  await alert.getByRole('button', { name: /Retry|Thử lại/i }).click();

  await expect(page.getByRole('article', { name: /Campus helpdesk|Trợ lý học vụ CampusUTE/i })).toContainText('Use the published thesis guide.');
  expect(prompts).toHaveLength(2);
  expect(prompts.map((request) => request.message)).toEqual([prompt, prompt]);
  expect(prompts[1].clientRequestId).toBe(prompts[0].clientRequestId);
});

test('assistant quota failure stays terminal when stream reconciliation is unavailable', async ({ page }) => {
  await mockAssistantShell(page);
  let streamCalls = 0;
  let fallbackCalls = 0;
  await page.route('**/api/v1/assistant/chat**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/assistant/chat/stream')) {
      streamCalls += 1;
      return route.fulfill(jsonResponse({ message: 'Daily limit reached' }, 429));
    }
    fallbackCalls += 1;
    return route.fulfill(jsonResponse({ message: 'Temporary network failure' }, 503));
  });

  await login(page, student);
  await openAssistantAndSubmit(page, 'How can I contact the campus helpdesk?');

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('article', { name: /Campus helpdesk|Trợ lý học vụ CampusUTE/i }))
    .toContainText(/Daily assistant limit reached|Bạn đã chạm giới hạn trợ lý trong ngày/i);
  await expect(dialog.getByRole('alert')).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: /^Retry$|^Thử lại$/i })).toHaveCount(0);
  expect(streamCalls).toBe(1);
  expect(fallbackCalls).toBe(1);
});

test('assistant does not retry a terminal reconciliation 404 after a transient stream failure', async ({ page }) => {
  await mockAssistantShell(page);
  let streamCalls = 0;
  let fallbackCalls = 0;
  await page.route('**/api/v1/assistant/chat**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/assistant/chat/stream')) {
      streamCalls += 1;
      return route.fulfill(jsonResponse({ message: 'Temporary network failure' }, 503));
    }
    fallbackCalls += 1;
    return route.fulfill(jsonResponse({ message: 'Assistant route not found' }, 404));
  });

  await login(page, student);
  await openAssistantAndSubmit(page, 'How can I contact the campus helpdesk?');

  const dialog = page.getByRole('dialog');
  await expect(dialog.getByRole('article', { name: /Campus helpdesk|Trợ lý học vụ CampusUTE/i }))
    .toContainText(/assistant is not available right now|Trợ lý hiện chưa sẵn sàng/i);
  await expect(dialog.getByRole('alert')).toHaveCount(0);
  await expect(dialog.getByRole('button', { name: /^Retry$|^Thử lại$/i })).toHaveCount(0);
  expect(streamCalls).toBe(1);
  expect(fallbackCalls).toBe(1);
});

for (const conflict of [
  { label: 'another code', body: { code: 'IDEMPOTENCY_CONFLICT' } },
  { label: 'no code', body: { message: 'Request conflict' } },
]) {
  test(`assistant does not retry a 409 reconciliation conflict with ${conflict.label}`, async ({ page }) => {
    await mockAssistantShell(page);
    let streamCalls = 0;
    let fallbackCalls = 0;
    await page.route('**/api/v1/assistant/chat**', (route) => {
      const path = new URL(route.request().url()).pathname;
      if (path.endsWith('/assistant/chat/stream')) {
        streamCalls += 1;
        return route.fulfill(jsonResponse({ message: 'Temporary network failure' }, 503));
      }
      fallbackCalls += 1;
      return route.fulfill(jsonResponse(conflict.body, 409));
    });

    await login(page, student);
    await openAssistantAndSubmit(page, 'How can I contact the campus helpdesk?');

    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('article', { name: /Campus helpdesk|Trợ lý học vụ CampusUTE/i }))
      .toContainText(/assistant is not available right now|Trợ lý hiện chưa sẵn sàng/i);
    await expect(dialog.getByRole('alert')).toHaveCount(0);
    await expect(dialog.getByRole('button', { name: /^Retry$|^Thử lại$/i })).toHaveCount(0);
    expect(streamCalls).toBe(1);
    expect(fallbackCalls).toBe(1);
  });
}

test('assistant retries a 409 only when reconciliation returns TURN_IN_PROGRESS', async ({ page }) => {
  await mockAssistantShell(page);
  let streamCalls = 0;
  let fallbackCalls = 0;
  await page.route('**/api/v1/assistant/chat**', (route) => {
    const path = new URL(route.request().url()).pathname;
    if (path.endsWith('/assistant/chat/stream')) {
      streamCalls += 1;
      return route.fulfill(jsonResponse({ code: 'TURN_IN_PROGRESS' }, 409));
    }
    fallbackCalls += 1;
    return route.fulfill(jsonResponse({ code: 'TURN_IN_PROGRESS' }, 409));
  });

  await login(page, student);
  await openAssistantAndSubmit(page, 'How can I contact the campus helpdesk?');

  const alert = page.getByRole('dialog').getByRole('alert');
  await expect(alert).toContainText(/previous request is still being completed|Yêu cầu trước vẫn đang được hoàn tất/i);
  await expect(alert.getByRole('button', { name: /^Retry$|^Thử lại$/i })).toBeVisible();
  expect(streamCalls).toBe(1);
  expect(fallbackCalls).toBe(4);
});

test('authenticated admin can inspect reviewed guidance and public coverage', async ({ page }) => {
  const source = {
    documentId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    revisionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    version: 2,
    state: 'PUBLISHED',
    locale: 'en',
    slug: 'thesis-guide',
    title: 'How to use the thesis assistant',
    content: 'Use published academic workflow guidance.',
    source: 'assistant-guidance',
    priority: 50,
  };
  await page.route('**/api/v1/admin/assistant/knowledge**', (route) => {
    if (route.request().method() === 'GET') return route.fulfill(jsonResponse([source]));
    return route.fulfill(jsonResponse({ documentId: source.documentId, revisionId: source.revisionId, version: 2, state: 'PUBLISHED' }));
  });
  for (const endpoint of ['departments', 'courses', 'curricula', 'semesters']) {
    await page.route(`**/api/v1/${endpoint}**`, (route) =>
      route.fulfill(jsonResponse({ meta: { total: endpoint === 'courses' ? 12 : 3 }, data: [] })),
    );
  }

  await login(page, admin);
  await expect(page).toHaveURL(/\/admin(?:$|[/?#])/);
  await page.goto('/admin/assistant-knowledge');

  await expect(page.getByRole('heading', { name: /CampusCore knowledge|Kho kiến thức CampusCore/i })).toBeVisible();
  await expect(page.getByText(/Public CampusCore guidance|Phạm vi nội dung CampusCore công khai/i)).toBeVisible();
  await expect(page.getByText('How to use the thesis assistant')).toBeVisible();
  await expect(page.getByText(/Published|Đã xuất bản/i).first()).toBeVisible();

  await page.getByLabel(/Filter status|Lọc trạng thái/i).selectOption('PUBLISHED');
  await expect(page.getByText('How to use the thesis assistant')).toBeVisible();
  await page.getByRole('button', { name: /Archive|Lưu trữ/i }).first().click();
  await expect(page.getByRole('dialog')).toContainText(/Archive guidance\?|Lưu trữ nội dung\?/i);
  await page.getByRole('button', { name: /Cancel|Hủy/i }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('assistant launcher and panel stay clear of mobile navigation and viewport edges', async ({ page }) => {
  await page.route('**/api/v1/semesters**', (route) =>
    route.fulfill(jsonResponse({ data: [{ id: 'semester-1', name: 'Semester 1 2026-2027', status: 'ACTIVE' }] })),
  );
  await page.route('**/api/v1/enrollments/my**', (route) => route.fulfill(jsonResponse([])));
  await page.route('**/api/v1/assistant/conversations**', (route) =>
    route.fulfill(jsonResponse([])),
  );

  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, student);
  await expect(page).toHaveURL(/\/dashboard(?:$|[/?#])/);

  for (const viewport of [
    { width: 390, height: 844 },
    { width: 600, height: 844 },
    { width: 768, height: 900 },
    { width: 1440, height: 900 },
  ]) {
    await page.setViewportSize(viewport);
    await page.goto('/dashboard');
    if (viewport.width < 768) {
      const metrics = page.locator('[data-dashboard-metrics="student-overview"]');
      await expect(metrics).toBeVisible();
      const columnCount = await metrics.evaluate((element) =>
        getComputedStyle(element).gridTemplateColumns.trim().split(/\s+/).length,
      );
      expect(columnCount).toBe(1);

      const semesterValue = page.getByText('Semester 1 2026-2027', { exact: true });
      await expect(semesterValue).toBeVisible();
      const semesterHeight = await semesterValue.evaluate((element) => {
        const style = getComputedStyle(element);
        return {
          height: element.getBoundingClientRect().height,
          lineHeight: Number.parseFloat(style.lineHeight),
        };
      });
      expect(semesterHeight.height).toBeLessThanOrEqual(semesterHeight.lineHeight * 1.25);
    }
    // Two buttons now carry the assistant-open name: the mobile nav slot and a
    // floating desktop launcher (AssistantPanel). At mobile the launcher under
    // test is the nav slot itself; on desktop it is the floating button.
    const launcher = viewport.width < 768
      ? page.locator('[data-mobile-assistant-slot="true"]')
      : page.getByRole('button', { name: assistantLauncherName }).last();
    await expect(launcher).toBeVisible();
    const launcherBox = await launcher.boundingBox();
    expect(launcherBox).not.toBeNull();
    expect(launcherBox!.x).toBeGreaterThanOrEqual(0);
    expect(launcherBox!.y).toBeGreaterThanOrEqual(0);
    expect(launcherBox!.x + launcherBox!.width).toBeLessThanOrEqual(viewport.width);
    expect(launcherBox!.y + launcherBox!.height).toBeLessThanOrEqual(viewport.height);

    const mobileNav = page.getByRole('navigation', { name: mobileNavigationName });
    if (viewport.width < 768) {
      await expect(mobileNav).toBeVisible();
      const navBox = await mobileNav.boundingBox();
      expect(navBox).not.toBeNull();
      const assistantSlot = page.locator('[data-mobile-assistant-slot="true"]');
      await expect(assistantSlot).toBeVisible();
      const slotBox = await assistantSlot.boundingBox();
      expect(slotBox).not.toBeNull();
      expect(launcherBox!.x).toBeGreaterThanOrEqual(slotBox!.x);
      expect(launcherBox!.x + launcherBox!.width).toBeLessThanOrEqual(slotBox!.x + slotBox!.width);
      expect(launcherBox!.y).toBeGreaterThanOrEqual(navBox!.y);
      expect(launcherBox!.y + launcherBox!.height).toBeLessThanOrEqual(navBox!.y + navBox!.height);
    } else {
      await expect(mobileNav).toBeHidden();
    }

    await launcher.click();
    const panel = page.getByRole('dialog');
    await expect(panel).toBeVisible();
    const panelBox = await panel.boundingBox();
    expect(panelBox).not.toBeNull();
    expect(panelBox!.x).toBeGreaterThanOrEqual(0);
    expect(panelBox!.y).toBeGreaterThanOrEqual(0);
    expect(panelBox!.x + panelBox!.width).toBeLessThanOrEqual(viewport.width);
    expect(panelBox!.y + panelBox!.height).toBeLessThanOrEqual(viewport.height);
    if (viewport.width < 768) {
      // The mobile assistant is a full-screen sheet (fixed inset-0), not the
      // old floating card that sat above the nav bar. Assert it fills the
      // viewport (the within-edges checks above still bound it).
      expect(Math.round(panelBox!.x)).toBe(0);
      expect(Math.round(panelBox!.y)).toBe(0);
      expect(Math.round(panelBox!.width)).toBeGreaterThanOrEqual(viewport.width - 1);
      expect(Math.round(panelBox!.height)).toBeGreaterThanOrEqual(viewport.height - 1);
    }
    await page.getByRole('button', { name: assistantCloseName }).click();
  }
});
