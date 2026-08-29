import { test, expect, type Page } from '@playwright/test';

const student = { email: 'student@campuscore.edu', password: 'password123' };
const admin = { email: 'admin@campuscore.edu', password: 'admin123' };

async function login(
  page: Page,
  account: typeof student,
  portal: 'student' | 'lecturer' | 'admin' = account.email.startsWith('admin') ? 'admin' : 'student',
) {
  await page.goto(`/login?portal=${portal}`);
  const submit = page.locator('form').getByRole('button', { name: /sign in/i });
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

test('authenticated student can use the assistant launcher, stream, citation, and feedback', async ({ page }) => {
  await page.route('**/api/v1/semesters**', (route) =>
    route.fulfill(jsonResponse({ data: [{ id: 'semester-1', name: '2026 Spring', status: 'ACTIVE' }] })),
  );
  await page.route('**/api/v1/enrollments/my**', (route) =>
    route.fulfill(jsonResponse([])),
  );

  let feedbackCalls = 0;
  await page.route('**/api/v1/thesis/assistant/conversations**', (route) => {
    if (route.request().method() === 'GET') {
      return route.fulfill(jsonResponse([]));
    }
    return route.fulfill(jsonResponse({ id: 'conversation-e2e', locale: 'en' }));
  });
  await page.route('**/api/v1/thesis/assistant/chat/stream', (route) =>
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
  await page.route('**/api/v1/thesis/assistant/messages/**', (route) => {
    if (route.request().method() === 'PUT') {
      feedbackCalls += 1;
      return route.fulfill(jsonResponse({ messageId: '55555555-5555-4555-8555-555555555555', rating: 'UP', reason: 'HELPFUL', removed: false }));
    }
    return route.fulfill(jsonResponse({}, 204));
  });

  await login(page, student);
  await expect(page).toHaveURL(/\/dashboard(?:$|[/?#])/);

  const launcher = page.getByRole('button', { name: 'Open thesis assistant' });
  await expect(launcher).toBeVisible();
  await launcher.click();
  await expect(page.getByRole('dialog')).toContainText('Thesis guide');
  await page.getByRole('button', { name: 'Conversation history' }).click();
  await expect(page.getByText('No saved conversations yet.')).toBeVisible();
  await page.getByRole('button', { name: 'Back to chat' }).click();

  const composer = page.getByRole('textbox', { name: 'Ask about registration, topics, groups, or progress...' });
  await composer.fill('How do I choose a thesis topic?');
  await page.getByRole('button', { name: 'Send message' }).click();
  await expect(page.getByRole('article', { name: 'AI assistant' })).toContainText('Use the published thesis guide.');
  await expect(page.getByLabel('Sources').getByText('Thesis guide')).toBeVisible();

  await page.getByRole('button', { name: 'Mark answer helpful' }).click();
  await expect.poll(() => feedbackCalls).toBe(1);
});

test('authenticated admin can inspect curated sources and the public catalog coverage surface', async ({ page }) => {
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
  await page.route('**/api/v1/admin/thesis/assistant/knowledge**', (route) => {
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

  await expect(page.getByRole('heading', { name: 'AI assistant knowledge' })).toBeVisible();
  await expect(page.getByText('Public catalog coverage')).toBeVisible();
  await expect(page.getByText('How to use the thesis assistant')).toBeVisible();
  await expect(page.getByText('Published', { exact: true }).first()).toBeVisible();

  await page.getByLabel('Filter state').selectOption('PUBLISHED');
  await expect(page.getByText('How to use the thesis assistant')).toBeVisible();
  await page.getByRole('button', { name: 'Archive' }).first().click();
  await expect(page.getByRole('dialog')).toContainText('Archive source?');
  await page.getByRole('button', { name: 'Cancel' }).click();
  await expect(page.getByRole('dialog')).toHaveCount(0);
});

test('assistant launcher and panel stay clear of mobile navigation and viewport edges', async ({ page }) => {
  await page.route('**/api/v1/semesters**', (route) =>
    route.fulfill(jsonResponse({ data: [{ id: 'semester-1', name: '2026 Spring', status: 'ACTIVE' }] })),
  );
  await page.route('**/api/v1/enrollments/my**', (route) => route.fulfill(jsonResponse([])));
  await page.route('**/api/v1/thesis/assistant/conversations**', (route) =>
    route.fulfill(jsonResponse([])),
  );

  await page.setViewportSize({ width: 390, height: 844 });
  await login(page, student);
  await expect(page).toHaveURL(/\/dashboard(?:$|[/?#])/);

  for (const viewport of [
    { width: 390, height: 844 },
    { width: 768, height: 900 },
    { width: 1440, height: 900 },
  ]) {
    await page.setViewportSize(viewport);
    await page.goto('/dashboard');
    const launcher = page.getByRole('button', { name: 'Open thesis assistant' });
    await expect(launcher).toBeVisible();
    const launcherBox = await launcher.boundingBox();
    expect(launcherBox).not.toBeNull();
    expect(launcherBox!.x).toBeGreaterThanOrEqual(0);
    expect(launcherBox!.y).toBeGreaterThanOrEqual(0);
    expect(launcherBox!.x + launcherBox!.width).toBeLessThanOrEqual(viewport.width);
    expect(launcherBox!.y + launcherBox!.height).toBeLessThanOrEqual(viewport.height);

    const mobileNav = page.getByRole('navigation', { name: /mobile workspace navigation/i });
    if (viewport.width < 768) {
      await expect(mobileNav).toBeVisible();
      const navBox = await mobileNav.boundingBox();
      expect(navBox).not.toBeNull();
      expect(launcherBox!.y + launcherBox!.height).toBeLessThanOrEqual(navBox!.y);
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
      const maxHeight = await panel.evaluate((element) => Number.parseFloat(getComputedStyle(element).maxHeight));
      expect(maxHeight).toBeLessThanOrEqual(viewport.height - 6.5 * 16 + 1);
      const navBox = await mobileNav.boundingBox();
      expect(navBox).not.toBeNull();
      expect(panelBox!.y + panelBox!.height).toBeLessThanOrEqual(navBox!.y);
    }
    await page.getByRole('button', { name: 'Close thesis assistant' }).click();
  }
});
