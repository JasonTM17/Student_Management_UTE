import {
  expect,
  type APIRequestContext,
  type Locator,
  type Page,
} from '@playwright/test';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { io, type Socket } from 'socket.io-client';

export const isExternalStack = process.env.E2E_EXTERNAL_STACK === '1';
export const frontendBaseURL =
  process.env.E2E_BASE_URL ??
  (isExternalStack ? 'http://127.0.0.1' : 'http://127.0.0.1:3101');
export const apiBaseURL =
  process.env.E2E_API_URL ??
  (isExternalStack
    ? 'http://127.0.0.1/api/v1'
    : 'http://127.0.0.1:4100/api/v1');
// Session setup may use a separately published auth-service endpoint in the
// shared external Compose stack. This keeps seeded-session creation from
// exhausting the public gateway throttle while the edge auth contract remains
// covered by the dedicated gateway tests.
export const authLoginBaseURL =
  process.env.E2E_AUTH_LOGIN_URL ?? apiBaseURL;
export const apiOrigin = new URL(apiBaseURL).origin;
export const notificationsURL = new URL(
  '/notifications',
  frontendBaseURL,
).toString();

export const SEEDED_USERS = {
  admin: {
    email: 'admin@campuscore.edu',
    password: 'admin123',
  },
  student: {
    email: 'student1@campuscore.edu',
    password: 'password123',
  },
  lecturer: {
    email: 'john.doe@campuscore.edu',
    password: 'password123',
  },
} as const;

export type ControlSpec = {
  role: 'button' | 'link';
  name: string | RegExp;
};

export type RouteSpec = {
  path: string;
  heading?: string | RegExp;
  controls?: ControlSpec[];
  settle?: (page: Page, timeoutMs: number) => Promise<void>;
  assertVisible?: (page: Page, timeoutMs: number) => Promise<void>;
  ready?: (page: Page, timeoutMs: number) => Promise<void>;
};

function getRouteAssertionTimeout() {
  return isExternalStack ? 15_000 : 12_000;
}

const dashboardNotificationToggleName =
  /Toggle notifications panel|Bật tắt bảng thông báo/i;
const dashboardProfileToggleName =
  /Toggle profile menu|Bật tắt menu hồ sơ/i;
const adminBackLinkName =
  /Back to admin dashboard|Quay lại bảng điều khiển quản trị/i;
const invoiceDetailsCloseName =
  /Close invoice details|Đóng chi tiết hóa đơn/i;
const studentInformationHeading =
  /Student information|Thông tin sinh viên/i;
const lecturerBackLinkName =
  /Back to lecturer dashboard|Quay lại bảng điều khiển giảng viên/i;
const nextActionsHeading =
  /Next actions|Hành động tiếp theo/i;
const currentCoursesHeading =
  /Current courses|Môn học hiện tại/i;
const accountProfileHeading =
  /Account profile|Hồ sơ tài khoản/i;
const passwordSafetyHeading =
  /Password and session safety|Mật khẩu và an toàn phiên/i;
const invoiceDetailsHeading =
  /Invoice details|Chi tiết hóa đơn/i;
const quickActionsHeading =
  /Quick actions|Tác vụ nhanh/i;
const gradingQueueHeading =
  /Grading queue|Hàng chờ chấm điểm/i;

export const publicRoutes = ['/', '/login', '/forgot-password', '/reset-password'];

export const studentRoutes: RouteSpec[] = [
  {
    path: '/dashboard',
    heading: /Welcome back/i,
    controls: [
      { role: 'button', name: dashboardNotificationToggleName },
      { role: 'button', name: dashboardProfileToggleName },
    ],
    ready: async (page, timeoutMs) => {
      await expect(
        page.getByRole('heading', { name: nextActionsHeading }).first(),
      ).toBeVisible({ timeout: timeoutMs });
      await expect(
        page.getByRole('heading', { name: currentCoursesHeading }).first(),
      ).toBeVisible({ timeout: timeoutMs });
    },
  },
  { path: '/dashboard/register', heading: /^Course registration$/i },
  { path: '/dashboard/enrollments', heading: /^My courses$/i },
  { path: '/dashboard/schedule', heading: /^Schedule$/i },
  { path: '/dashboard/grades', heading: /^Grades$/i },
  {
    path: '/dashboard/transcript',
    settle: async (page, timeoutMs) => {
      await waitForAnyVisible(
        [
          page.getByRole('combobox', {
            name: /Select semester for transcript/i,
          }),
          page.getByRole('heading', {
            name: /No transcript records yet/i,
          }),
          page.getByRole('heading', {
            name: /Transcript unavailable/i,
          }),
        ],
        timeoutMs,
      );
    },
    assertVisible: async (page, timeoutMs) => {
      await waitForAnyVisible(
        [
          page.getByRole('combobox', {
            name: /Select semester for transcript/i,
          }),
          page.getByRole('button', { name: /Open grades/i }),
          page.getByRole('heading', {
            name: /No transcript records yet/i,
          }),
          page.getByRole('heading', {
            name: /Transcript unavailable/i,
          }),
        ],
        timeoutMs,
      );
    },
  },
  { path: '/dashboard/announcements', heading: 'Announcements' },
  { path: '/dashboard/notifications', heading: 'Notifications' },
  { path: '/dashboard/thesis/topics', heading: /Published topic catalog/i },
  { path: '/dashboard/thesis/progress', heading: /Thesis progress/i },
  { path: '/dashboard/thesis/evaluation', heading: /Defense and evaluation/i },
  {
    path: '/dashboard/profile',
    heading: /Profile settings/i,
    controls: [{ role: 'button', name: /Save changes/i }],
    ready: async (page, timeoutMs) => {
      await expect(
        page.getByRole('heading', { name: accountProfileHeading }).first(),
      ).toBeVisible({ timeout: timeoutMs });
      await expect(
        page
          .getByRole('heading', { name: passwordSafetyHeading })
          .first(),
      ).toBeVisible({ timeout: timeoutMs });
    },
  },
  {
    path: '/dashboard/invoices',
    heading: /^Invoices$/i,
    controls: [{ role: 'button', name: /View details for invoice/i }],
    ready: async (page) => {
      await page
        .getByRole('button', { name: /View details for invoice/i })
        .first()
        .click();
      await expect(
        page.getByRole('heading', { name: invoiceDetailsHeading }),
      ).toBeVisible();
      const closeButton = page.getByRole('button', {
        name: invoiceDetailsCloseName,
      });
      await expect(closeButton).toBeVisible();
      await closeButton.click();
      await expect(
        page.getByRole('heading', { name: invoiceDetailsHeading }),
      ).toBeHidden();
    },
  },
];

export const adminHomeRoute: RouteSpec = {
  path: '/admin',
  heading: /^Admin dashboard$/i,
  controls: [
    { role: 'link', name: 'Add user' },
    { role: 'link', name: 'Open analytics' },
  ],
  ready: async (page, timeoutMs) => {
    await expect(
      page.getByRole('heading', { name: /Management console/i }).first(),
    ).toBeVisible({ timeout: timeoutMs });
    await expect(
      page.getByRole('link', { name: /User management/i }).first(),
    ).toBeVisible({ timeout: timeoutMs });
  },
};

export const adminRoutes: RouteSpec[] = [
  {
    path: '/admin/thesis',
    heading: /Thesis administration/i,
    controls: [{ role: 'button', name: /Create round/i }],
  },
  {
    path: '/admin/users',
    heading: /^User management$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Edit user / },
      { role: 'button', name: /Delete user / },
    ],
  },
  {
    path: '/admin/lecturers',
    heading: /^Lecturers$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Edit lecturer / },
      { role: 'button', name: /Delete lecturer / },
    ],
  },
  {
    path: '/admin/courses',
    heading: /^Courses$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Edit course / },
      { role: 'button', name: /Delete course / },
    ],
  },
  {
    path: '/admin/sections',
    heading: /^Sections$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Edit section / },
      { role: 'button', name: /Delete section / },
    ],
  },
  {
    path: '/admin/enrollments',
    heading: /^Enrollments$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /View enrollment details for / },
      { role: 'button', name: /Delete enrollment for / },
    ],
  },
  {
    path: '/admin/classrooms',
    heading: /^Classrooms$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Edit classroom / },
      { role: 'button', name: /Delete classroom / },
    ],
  },
  {
    path: '/admin/semesters',
    heading: /^Semesters$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Edit semester / },
      { role: 'button', name: /Delete semester / },
    ],
  },
  {
    path: '/admin/academic-years',
    heading: /^Academic years$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Edit academic year / },
      { role: 'button', name: /Delete academic year / },
    ],
  },
  {
    path: '/admin/departments',
    heading: /^Departments$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Edit department / },
      { role: 'button', name: /Delete department / },
    ],
  },
  {
    path: '/admin/announcements',
    heading: 'Announcements',
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /Delete announcement / },
    ],
  },
  {
    path: '/admin/invoices',
    heading: /^Invoices$/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: /View invoice / },
      { role: 'button', name: /Delete invoice / },
    ],
    ready: async (page, timeoutMs) => {
      const viewButtons = page.getByRole('button', { name: /View invoice /i });
      await expect(viewButtons.first()).toBeVisible({ timeout: timeoutMs });
      const detailResponsePromise = page.waitForResponse(
        (response) =>
          response.request().method() === 'GET' &&
          /\/finance\/invoices\/[^/?]+$/.test(response.url()),
        { timeout: timeoutMs },
      );
      await viewButtons.first().click();
      await detailResponsePromise;

      const closeButton = page.getByRole('button', {
        name: invoiceDetailsCloseName,
      });
      const detailHeading = page.getByRole('heading', {
        name: studentInformationHeading,
      });

      await waitForAnyVisible([closeButton, detailHeading], timeoutMs);
      await expect(closeButton).toBeVisible({ timeout: timeoutMs });
      await expect(detailHeading).toBeVisible({ timeout: timeoutMs });
      await closeButton.click();
      await expect(
        page.getByRole('button', { name: invoiceDetailsCloseName }),
      ).toBeHidden({ timeout: timeoutMs });
    },
  },
  {
    path: '/admin/analytics',
    heading: /Reports (?:&|and) analytics/i,
    controls: [
      { role: 'link', name: adminBackLinkName },
      { role: 'button', name: 'Refresh data' },
    ],
    ready: async (page, timeoutMs) => {
      await expect(
        page.getByRole('heading', { name: /Enrollment movement/i }).first(),
      ).toBeVisible({ timeout: timeoutMs });
      await expect(
        page.getByRole('heading', { name: /Registration pressure/i }).first(),
      ).toBeVisible({ timeout: timeoutMs });
      await expect(
        page.getByRole('heading', { name: /Finance checkout posture/i }).first(),
      ).toBeVisible({ timeout: timeoutMs });
    },
  },
];

export const lecturerRoutes: RouteSpec[] = [
  {
    path: '/dashboard/lecturer',
    heading: /Welcome back/i,
    controls: [
      { role: 'button', name: dashboardNotificationToggleName },
      { role: 'button', name: dashboardProfileToggleName },
    ],
    ready: async (page, timeoutMs) => {
      await expect(
        page.getByRole('heading', { name: quickActionsHeading }).first(),
      ).toBeVisible({ timeout: timeoutMs });
      await expect(
        page.getByRole('heading', { name: gradingQueueHeading }).first(),
      ).toBeVisible({ timeout: timeoutMs });
    },
  },
  {
    path: '/dashboard/profile',
    heading: /Profile settings/i,
    controls: [{ role: 'button', name: /Save changes/i }],
    ready: async (page, timeoutMs) => {
      await expect(
        page.getByRole('heading', { name: accountProfileHeading }).first(),
      ).toBeVisible({ timeout: timeoutMs });
      await expect(
        page
          .getByRole('heading', { name: passwordSafetyHeading })
          .first(),
      ).toBeVisible({ timeout: timeoutMs });
    },
  },
  {
    path: '/dashboard/lecturer/schedule',
    assertVisible: async (page, timeoutMs) => {
      await waitForAnyVisible(
        [
          page.getByRole('heading', { name: /Assigned sections/i }),
          page.getByRole('heading', { name: /No teaching assignments yet/i }),
        ],
        timeoutMs,
      );
    },
    ready: async (page, timeoutMs) => {
      await waitForAnyVisible(
        [
          page.getByRole('heading', { name: /Assigned sections/i }),
          page.getByRole('heading', { name: /No teaching assignments yet/i }),
        ],
        timeoutMs,
      );
    },
  },
  {
    path: '/dashboard/lecturer/announcements',
    heading: 'Announcements',
    controls: [{ role: 'link', name: lecturerBackLinkName }],
  },
  { path: '/dashboard/notifications', heading: 'Notifications' },
  { path: '/dashboard/thesis/topics', heading: /Published topic catalog/i },
  { path: '/dashboard/thesis/progress', heading: /Thesis progress/i },
  { path: '/dashboard/thesis/evaluation', heading: /Defense and evaluation/i },
  {
    path: '/dashboard/thesis/councils',
    heading: /Schedule and manage defense councils/i,
  },
  {
    path: '/dashboard/thesis/reviews',
    heading: /Score thesis defenses with a calm, structured workflow/i,
  },
];

let loginAttemptCounter = 0;
const sharedRoleSessionCache = new Map<
  keyof typeof SEEDED_USERS,
  Promise<SessionArtifacts>
>();
const sharedSessionCacheNamespace = sanitizeFileSegment(
  process.env.E2E_SESSION_CACHE_NAMESPACE ?? 'default',
);
const sharedSessionCacheDir = path.join(
  process.cwd(),
  'test-results',
  'playwright',
  'shared-sessions',
  sharedSessionCacheNamespace,
);
const sharedSessionTtlMs = Number(
  process.env.E2E_SHARED_SESSION_TTL_MS ?? '900000',
);

type RequestFactory = {
  request: {
    newContext: (options?: { baseURL?: string }) => Promise<APIRequestContext>;
  };
};

export function normalizeSetCookie(setCookie?: string[] | string) {
  if (!setCookie) {
    return [];
  }

  return Array.isArray(setCookie) ? setCookie : [setCookie];
}

export function toCookieHeader(setCookie?: string[] | string) {
  return normalizeSetCookie(setCookie)
    .map((cookie) => cookie.split(';', 1)[0])
    .join('; ');
}

export function extractCookieValue(
  setCookie: string[] | string | undefined,
  name: string,
) {
  for (const cookie of normalizeSetCookie(setCookie)) {
    const [pair] = cookie.split(';', 1);
    const [cookieName, ...rest] = pair.split('=');
    if (cookieName === name) {
      return rest.join('=');
    }
  }

  return undefined;
}

export function getSetCookieHeaders(response: {
  headersArray?: () => Array<{ name: string; value: string }>;
  headers?: () => Record<string, string>;
}) {
  const headerEntries = response.headersArray?.() ?? [];
  const setCookies = headerEntries
    .filter((header) => header.name.toLowerCase() === 'set-cookie')
    .map((header) => header.value);

  if (setCookies.length > 0) {
    return setCookies;
  }

  const legacyHeader = response.headers?.()['set-cookie'];
  return normalizeSetCookie(legacyHeader);
}

export type SessionArtifacts = {
  authData: any;
  setCookieHeaders: string[];
  cookieHeader: string;
  csrfToken?: string;
};

function cloneSessionArtifacts(session: SessionArtifacts): SessionArtifacts {
  return {
    authData: JSON.parse(JSON.stringify(session.authData)),
    setCookieHeaders: [...session.setCookieHeaders],
    cookieHeader: session.cookieHeader,
    csrfToken: session.csrfToken,
  };
}

function sanitizeFileSegment(value: string) {
  return value.replace(/[^a-z0-9._-]+/giu, '-').replace(/^-+|-+$/gu, '') || 'default';
}

function getSharedSessionCachePath(user: keyof typeof SEEDED_USERS) {
  return path.join(sharedSessionCacheDir, `${user}.json`);
}

async function readSharedSessionArtifactsFromDisk(
  user: keyof typeof SEEDED_USERS,
): Promise<SessionArtifacts | null> {
  try {
    const payload = JSON.parse(
      await readFile(getSharedSessionCachePath(user), 'utf8'),
    ) as {
      createdAt: string;
      session: SessionArtifacts;
    };
    const createdAt = Date.parse(payload.createdAt);

    if (Number.isNaN(createdAt) || Date.now() - createdAt > sharedSessionTtlMs) {
      return null;
    }

    return payload.session;
  } catch {
    return null;
  }
}

async function writeSharedSessionArtifactsToDisk(
  user: keyof typeof SEEDED_USERS,
  session: SessionArtifacts,
) {
  await mkdir(sharedSessionCacheDir, { recursive: true });
  await writeFile(
    getSharedSessionCachePath(user),
    JSON.stringify(
      {
        createdAt: new Date().toISOString(),
        session,
      },
      null,
      2,
    ),
    'utf8',
  );
}

function mapSameSite(value: string | undefined): 'Strict' | 'Lax' | 'None' {
  const normalized = value?.toLowerCase();

  if (normalized === 'strict') {
    return 'Strict';
  }

  if (normalized === 'none') {
    return 'None';
  }

  return 'Lax';
}

function parseSetCookieForBrowser(cookie: string) {
  const [pair, ...attributeParts] = cookie.split(';');
  const separatorIndex = pair.indexOf('=');

  if (separatorIndex === -1) {
    return null;
  }

  const name = pair.slice(0, separatorIndex).trim();
  const value = pair.slice(separatorIndex + 1).trim();
  const domain = new URL(frontendBaseURL).hostname;
  let path = '/';
  let httpOnly = false;
  let secure = false;
  let sameSite: 'Strict' | 'Lax' | 'None' = 'Lax';
  let expires = -1;

  for (const attributePart of attributeParts) {
    const [rawKey, ...rawValueParts] = attributePart.trim().split('=');
    const key = rawKey.toLowerCase();
    const attributeValue = rawValueParts.join('=').trim();

    if (key === 'path' && attributeValue) {
      path = attributeValue;
      continue;
    }

    if (key === 'httponly') {
      httpOnly = true;
      continue;
    }

    if (key === 'secure') {
      secure = true;
      continue;
    }

    if (key === 'samesite') {
      sameSite = mapSameSite(attributeValue);
      continue;
    }

    if (key === 'max-age' && attributeValue) {
      const parsedMaxAge = Number(attributeValue);
      if (!Number.isNaN(parsedMaxAge)) {
        expires = Math.floor(Date.now() / 1000) + parsedMaxAge;
      }
      continue;
    }

    if (key === 'expires' && attributeValue) {
      const parsedExpires = Date.parse(attributeValue);
      if (!Number.isNaN(parsedExpires)) {
        expires = Math.floor(parsedExpires / 1000);
      }
    }
  }

  return {
    name,
    value,
    domain,
    path,
    httpOnly,
    secure,
    sameSite,
    expires,
  };
}

export async function applySessionCookiesToPage(
  page: Page,
  setCookieHeaders: string[] | string,
) {
  const cookies = normalizeSetCookie(setCookieHeaders)
    .map(parseSetCookieForBrowser)
    .filter((cookie): cookie is NonNullable<ReturnType<typeof parseSetCookieForBrowser>> =>
      Boolean(cookie),
    );

  if (cookies.length === 0) {
    throw new Error('Login response did not return any session cookies');
  }

  await page.context().addCookies(cookies);
}

export async function expectOkResponse(
  response: Awaited<ReturnType<APIRequestContext['get']>>,
  context: string,
) {
  if (response.ok()) {
    return;
  }

  const responseText = await response.text();
  throw new Error(
    `${context} failed with ${response.status()} ${response.statusText()}: ${responseText.slice(0, 500)}`,
  );
}

export function buildCookieHeaders(
  session: Pick<SessionArtifacts, 'cookieHeader' | 'csrfToken'>,
  extraHeaders: Record<string, string> = {},
) {
  return {
    Cookie: session.cookieHeader,
    ...extraHeaders,
  };
}

export function buildMutatingSessionHeaders(
  session: Pick<SessionArtifacts, 'cookieHeader' | 'csrfToken'>,
  extraHeaders: Record<string, string> = {},
) {
  return {
    Cookie: session.cookieHeader,
    'X-CSRF-Token': session.csrfToken ?? '',
    ...extraHeaders,
  };
}

export function apiUrl(path: string) {
  return `${apiBaseURL}${path}`;
}

export async function signIn(
  page: Page,
  email: string,
  password: string,
  options: { path?: string } = {},
) {
  await page.goto(options.path ?? '/login');
  const passwordInput = page.locator('input#password');
  const submitButton = page.getByRole('button', { name: /Sign in|Đăng nhập/i });

  await expect(passwordInput).toBeVisible();
  await expect(submitButton).toBeEnabled();
  await page.locator('input#email').fill(email);
  await passwordInput.fill(password);
  await submitButton.click();
}

export async function expectSessionCookies(page: Page) {
  await expect
    .poll(async () => {
      const cookies = await page.context().cookies();
      return {
        hasAccessCookie: cookies.some(
          (cookie) => cookie.name === 'cc_access_token' && cookie.httpOnly,
        ),
        hasRefreshCookie: cookies.some(
          (cookie) => cookie.name === 'cc_refresh_token' && cookie.httpOnly,
        ),
        hasCsrfCookie: cookies.some(
          (cookie) => cookie.name === 'cc_csrf' && !cookie.httpOnly,
        ),
      };
    })
    .toEqual({
      hasAccessCookie: true,
      hasRefreshCookie: true,
      hasCsrfCookie: true,
    });
}

function escapeForRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

export async function expectControl(page: Page, control: ControlSpec) {
  await expect(
    page.getByRole(control.role, { name: control.name }).first(),
  ).toBeVisible({ timeout: getRouteAssertionTimeout() });
}

export async function waitForAnyVisible(
  locators: Locator[],
  timeoutMs = getRouteAssertionTimeout(),
) {
  await expect
    .poll(
      async () => {
        for (const locator of locators) {
          const first = locator.first();
          try {
            if ((await first.count()) > 0 && (await first.isVisible())) {
              return true;
            }
          } catch {
            // Keep polling until one of the acceptable route states becomes visible.
          }
        }

        return false;
      },
      {
        timeout: timeoutMs,
        intervals: [250, 500, 1_000],
      },
    )
    .toBe(true);
}

export async function visitRoute(page: Page, route: RouteSpec) {
  const routeTimeout = isExternalStack ? 20_000 : 60_000;
  const assertionTimeout = getRouteAssertionTimeout();

  await page.goto(route.path, {
    timeout: routeTimeout,
    waitUntil: 'domcontentloaded',
  });
  await expect(page).toHaveURL(new RegExp(`${escapeForRegExp(route.path)}$`));

  if (route.settle) {
    await route.settle(page, assertionTimeout);
  }

  if (route.assertVisible) {
    await route.assertVisible(page, assertionTimeout);
  } else if (route.heading) {
    await expect(
      page.getByRole('heading', { name: route.heading }).first(),
    ).toBeVisible({ timeout: assertionTimeout });
  }

  for (const control of route.controls ?? []) {
    await expectControl(page, control);
  }

  if (route.ready) {
    await route.ready(page, assertionTimeout);
  }
}

export async function visitRoutes(page: Page, routes: RouteSpec[]) {
  const context = page.context();

  for (const route of routes) {
    const routePage = await context.newPage();
    try {
      await visitRoute(routePage, route);
    } finally {
      await routePage.close();
    }
  }
}

export async function emitWithAck<T>(
  socket: Socket,
  event: string,
  payload: Record<string, unknown>,
) {
  return socket.timeout(10_000).emitWithAck(event, payload) as Promise<T>;
}

export async function connectSocket(accessToken: string) {
  const socket = io(notificationsURL, {
    transports: ['websocket'],
    auth: { token: accessToken },
    reconnection: false,
    forceNew: true,
  });

  await new Promise<void>((resolve, reject) => {
    const timeoutId = setTimeout(() => {
      reject(new Error('Timed out waiting for notifications socket connect'));
    }, 10_000);

    socket.once('connect', () => {
      clearTimeout(timeoutId);
      resolve();
    });
    socket.once('connect_error', (error) => {
      clearTimeout(timeoutId);
      reject(error);
    });
    socket.once('disconnect', (reason) => {
      clearTimeout(timeoutId);
      reject(
        new Error(`Notifications socket disconnected before auth: ${reason}`),
      );
    });
  });

  return socket;
}

export async function waitForRejectedSocket(socket: Socket) {
  return new Promise<string>((resolve, reject) => {
    const timeoutId = setTimeout(() => {
      reject(new Error('Invalid socket token was not rejected in time'));
    }, 10_000);

    socket.once('connect_error', (error) => {
      clearTimeout(timeoutId);
      resolve(error.message);
    });
    socket.once('disconnect', (reason) => {
      clearTimeout(timeoutId);
      resolve(reason);
    });
    socket.once('connect', () => {
      socket.once('disconnect', (reason) => {
        clearTimeout(timeoutId);
        resolve(reason);
      });
    });
  });
}

export async function loginThroughApi(
  request: APIRequestContext,
  user: keyof typeof SEEDED_USERS,
) {
  loginAttemptCounter += 1;
  const forwardedForByUser = {
    admin: '198.51.100.10',
    student: '198.51.100.11',
    lecturer: '198.51.100.12',
  } as const;
  const response = await request.post(`${authLoginBaseURL}/auth/login`, {
    data: SEEDED_USERS[user],
    headers: {
      'X-Forwarded-For': forwardedForByUser[user],
      'X-E2E-Login-Attempt': String(loginAttemptCounter),
    },
  });
  await expectOkResponse(response, `Login for ${user}`);
  return response;
}

export async function createSessionArtifacts(
  request: APIRequestContext,
  user: keyof typeof SEEDED_USERS,
): Promise<SessionArtifacts> {
  const response = await loginThroughApi(request, user);
  return buildSessionArtifactsFromResponse(response);
}

export async function getSharedSessionArtifacts(
  playwright: RequestFactory,
  user: keyof typeof SEEDED_USERS,
): Promise<SessionArtifacts> {
  if (!sharedRoleSessionCache.has(user)) {
    sharedRoleSessionCache.set(
      user,
      (async () => {
        const cachedSession = await readSharedSessionArtifactsFromDisk(user);
        if (cachedSession) {
          return cachedSession;
        }

        const api = await playwright.request.newContext({
          baseURL: apiOrigin,
        });

        try {
          const session = await createSessionArtifacts(api, user);
          await writeSharedSessionArtifactsToDisk(user, session);
          return session;
        } finally {
          await api.dispose();
        }
      })(),
    );
  }

  return cloneSessionArtifacts(await sharedRoleSessionCache.get(user)!);
}

export async function buildSessionArtifactsFromResponse(response: {
  json: () => Promise<any>;
  headersArray?: () => Array<{ name: string; value: string }>;
  headers?: () => Record<string, string>;
}): Promise<SessionArtifacts> {
  const authData = await response.json();
  const setCookieHeaders = getSetCookieHeaders(response);

  return {
    authData,
    setCookieHeaders,
    cookieHeader: toCookieHeader(setCookieHeaders),
    csrfToken: extractCookieValue(setCookieHeaders, 'cc_csrf'),
  };
}

export async function seedBrowserSession(
  page: Page,
  playwright: RequestFactory,
  user: keyof typeof SEEDED_USERS,
  options: { shared?: boolean } = {},
) {
  if (options.shared) {
    const session = await getSharedSessionArtifacts(playwright, user);
    await applySessionCookiesToPage(page, session.setCookieHeaders);
    return session.authData;
  }

  const api = await playwright.request.newContext({
    baseURL: apiOrigin,
  });

  try {
    const session = await createSessionArtifacts(api, user);
    await applySessionCookiesToPage(page, session.setCookieHeaders);
    return session.authData;
  } finally {
    await api.dispose();
  }
}
