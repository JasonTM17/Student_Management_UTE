const test = require('node:test');
const assert = require('node:assert/strict');
const { chromium } = require('@playwright/test');

const baseURL = process.env.PORTAL_BASE_URL ?? 'http://127.0.0.1:4317';
const viewports = [
  { name: 'desktop', width: 1440, height: 960 },
  { name: 'tablet', width: 768, height: 1024 },
  { name: 'mobile', width: 390, height: 844 },
];

function apiResponse(data = []) {
  return {
    data,
    meta: { total: data.length, page: 1, limit: 50, totalPages: 1 },
  };
}

async function mockPortalApi(page, roles) {
  await page.route('**/api/v1/**', async (route) => {
    const requestUrl = new URL(route.request().url());
    const path = requestUrl.pathname;

    if (path.endsWith('/auth/me')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: `user-${roles[0].toLowerCase()}`,
          email: `${roles[0].toLowerCase()}@campuscore.edu.vn`,
          firstName: roles.includes('LECTURER') ? 'Minh' : 'An',
          lastName: roles.includes('ADMIN') ? 'Nguyen' : 'Tran',
          status: 'ACTIVE',
          roles,
          createdAt: '2026-08-22T08:00:00.000Z',
        }),
      });
      return;
    }

    if (path.includes('/notifications/my')) {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(apiResponse([])),
      });
      return;
    }

    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(apiResponse([])),
    });
  });
}

async function launchBrowser() {
  try {
    return await chromium.launch({ headless: true });
  } catch (bundledBrowserError) {
    for (const channel of ['chrome', 'msedge']) {
      try {
        return await chromium.launch({ channel, headless: true });
      } catch {
        // Try the next installed Chromium channel.
      }
    }
    throw bundledBrowserError;
  }
}

async function assertNoHorizontalOverflow(page, label) {
  const geometry = await page.evaluate(() => ({
    innerWidth: window.innerWidth,
    scrollWidth: document.documentElement.scrollWidth,
  }));
  assert.ok(
    geometry.scrollWidth <= geometry.innerWidth,
    `${label}: ${geometry.scrollWidth}px content exceeds ${geometry.innerWidth}px viewport`,
  );
}

async function verifySidebar(page, sidebarId, openButtonName, viewport) {
  const sidebar = page.locator(sidebarId);
  const openButton = page.getByRole('button', { name: openButtonName });

  if (viewport.width >= 1024) {
    assert.notEqual(await sidebar.getAttribute('aria-hidden'), 'true');
    assert.equal(await openButton.isVisible(), false);
    const box = await sidebar.boundingBox();
    assert.ok(box && Math.abs(box.x) < 1, `${viewport.name}: desktop sidebar is not fixed at the left edge`);
    assert.ok(box && box.width >= 260, `${viewport.name}: desktop sidebar is unexpectedly narrow`);
    assert.ok(box && box.width <= 320, `${viewport.name}: desktop sidebar is unexpectedly wide`);
    return;
  }

  assert.equal(await sidebar.getAttribute('aria-hidden'), 'true');
  assert.equal(await openButton.isVisible(), true);
  await openButton.click();
  await page.waitForTimeout(250);
  assert.equal(await sidebar.getAttribute('aria-hidden'), 'false');
  assert.equal(await sidebar.getAttribute('role'), 'dialog');
  assert.equal(await sidebar.getAttribute('aria-modal'), 'true');
  assert.equal(
    await page.evaluate((selector) => {
      const drawer = document.querySelector(selector);
      return [...document.querySelectorAll('[inert]')].some(
        (element) => element !== drawer,
      );
    }, sidebarId),
    true,
  );
  assert.equal(
    await sidebar.evaluate((element) => element.contains(document.activeElement)),
    true,
  );
  await page.keyboard.press('Shift+Tab');
  assert.equal(
    await sidebar.evaluate((element) => element.contains(document.activeElement)),
    true,
  );
  const openBox = await sidebar.boundingBox();
  assert.ok(openBox && Math.abs(openBox.x) < 1, `${viewport.name}: drawer did not enter from the left edge`);
  assert.ok(openBox && openBox.width <= viewport.width - 48, `${viewport.name}: drawer exceeds its responsive width`);

  await page.keyboard.press('Escape');
  assert.equal(await sidebar.getAttribute('aria-hidden'), 'true');
  assert.equal(await openButton.evaluate((element) => element === document.activeElement), true);
}

test('student and lecturer portal shell holds at 1440, 768, and 390', async () => {
  const browser = await launchBrowser();
  try {
    for (const viewport of viewports) {
      const context = await browser.newContext({ viewport });
      const page = await context.newPage();
      await mockPortalApi(page, viewport.name === 'tablet' ? ['LECTURER'] : ['STUDENT']);
      await page.goto(`${baseURL}/dashboard/notifications`, { waitUntil: 'networkidle' });

      await page.locator('.portal-page-ribbon').waitFor({ state: 'visible' });
      await verifySidebar(
        page,
        '#dashboard-sidebar',
        /Open sidebar navigation|Mở điều hướng sidebar/,
        viewport,
      );
      await assertNoHorizontalOverflow(page, `dashboard ${viewport.name}`);
      assert.equal(await page.locator('[aria-current="page"]').count() > 0, true);
      await context.close();
    }
  } finally {
    await browser.close();
  }
});

test('admin portal shell holds at 1440, 768, and 390', async () => {
  const browser = await launchBrowser();
  try {
    for (const viewport of viewports) {
      const context = await browser.newContext({ viewport });
      const page = await context.newPage();
      await mockPortalApi(page, ['ADMIN']);
      await page.goto(`${baseURL}/admin`, { waitUntil: 'networkidle' });

      await page.locator('.portal-page-ribbon').waitFor({ state: 'visible' });
      await verifySidebar(
        page,
        '#admin-sidebar',
        /Open admin navigation|Mở điều hướng quản trị/,
        viewport,
      );
      await assertNoHorizontalOverflow(page, `admin ${viewport.name}`);
      assert.equal(await page.locator('[aria-current="page"]').count() > 0, true);
      await context.close();
    }
  } finally {
    await browser.close();
  }
});
