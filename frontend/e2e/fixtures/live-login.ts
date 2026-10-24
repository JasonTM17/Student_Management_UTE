import { expect, type Locator, type Page, type Response } from '@playwright/test';

const LOGIN_PATH = '/api/v1/auth/login';
const throttleAlertName = /quá nhanh|too many requests/i;

function armLoginResponse(page: Page) {
  return page.waitForResponse((response) =>
    new URL(response.url()).pathname === LOGIN_PATH
    && response.request().method() === 'POST');
}

export async function submitLiveLogin(
  page: Page,
  submit: Locator,
  expectedPort: string = '3101',
): Promise<Response> {
  const { hostname, port } = new URL(page.url());
  expect(['localhost', '127.0.0.1']).toContain(hostname);
  expect(port, 'live login submits only against the owned local stack').toBe(expectedPort);

  let responsePromise = armLoginResponse(page);
  await submit.click();
  let response = await responsePromise;
  if (response.status() === 200) {
    return response;
  }

  if (response.status() !== 429) {
    expect(response.status(), 'live login status; response payload is not logged').toBe(200);
    return response;
  }

  const body = await response.json().catch(() => null);
  expect(body?.code).toBe('RATE_LIMIT_EXCEEDED');
  await expect(
    page.locator('#login-error').filter({ hasText: throttleAlertName }),
  ).toBeVisible({ timeout: 5_000 });
  const retryAfter = Number(response.headers()['retry-after']);
  expect(
    Number.isFinite(retryAfter) && retryAfter >= 1 && retryAfter <= 60,
    `server Retry-After ${JSON.stringify(response.headers()['retry-after'])}`,
  ).toBeTruthy();
  await page.waitForTimeout(retryAfter * 1_000 + 250);

  responsePromise = armLoginResponse(page);
  await submit.click();
  response = await responsePromise;
  expect(response.status(), 'live login retry after the server-directed wait').toBe(200);
  return response;
}
