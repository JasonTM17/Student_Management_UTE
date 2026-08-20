import { mkdir, writeFile } from 'node:fs/promises';
import { createRequire } from 'node:module';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const planDir = path.resolve(__dirname, '..');
const repoRoot = path.resolve(planDir, '..', '..');
const requireFromFrontend = createRequire(path.join(repoRoot, 'frontend', 'package.json'));
const { chromium, request: playwrightRequest } = requireFromFrontend('@playwright/test');
const artifactsDir = path.join(planDir, 'assets', 'fe-stitch-visual-qa');
const screenshotsDir = path.join(artifactsDir, 'screenshots');
const baseURL = (process.env.STITCH_QA_BASE_URL ?? 'http://127.0.0.1').replace(/\/$/, '');
const apiBaseURL = (
  process.env.STITCH_QA_API_URL ?? new URL('/api/v1', `${baseURL}/`).toString()
).replace(/\/$/, '');

const users = {
  admin: { email: 'admin@campuscore.edu', password: 'admin123' },
  student: { email: 'student1@campuscore.edu', password: 'password123' },
  lecturer: { email: 'john.doe@campuscore.edu', password: 'password123' },
};

const viewports = [
  {
    name: 'desktop',
    width: 1280,
    height: 900,
    isMobile: false,
    hasTouch: false,
  },
  {
    name: 'mobile',
    width: 390,
    height: 844,
    isMobile: true,
    hasTouch: true,
  },
  {
    name: 'tablet',
    width: 768,
    height: 1024,
    isMobile: false,
    hasTouch: true,
  },
];

const routes = [
  { role: 'public', path: '/', label: 'Home' },
  { role: 'public', path: '/login', label: 'Login' },
  { role: 'public', path: '/forgot-password', label: 'Forgot password' },
  { role: 'public', path: '/reset-password', label: 'Reset password' },
  { role: 'student', path: '/dashboard', label: 'Student dashboard' },
  { role: 'student', path: '/dashboard/register', label: 'Registration' },
  { role: 'student', path: '/dashboard/enrollments', label: 'Enrollments' },
  { role: 'student', path: '/dashboard/schedule', label: 'Schedule' },
  { role: 'student', path: '/dashboard/grades', label: 'Grades' },
  { role: 'student', path: '/dashboard/transcript', label: 'Transcript' },
  { role: 'student', path: '/dashboard/invoices', label: 'Invoices' },
  { role: 'student', path: '/dashboard/announcements', label: 'Announcements' },
  { role: 'student', path: '/dashboard/notifications', label: 'Notifications' },
  { role: 'student', path: '/dashboard/profile', label: 'Profile' },
  { role: 'student', path: '/dashboard/sign-out', label: 'Sign out' },
  { role: 'student', path: '/dashboard/thesis', label: 'Thesis home' },
  { role: 'student', path: '/dashboard/thesis/topics', label: 'Thesis topics' },
  {
    role: 'student',
    path: '/dashboard/thesis/topics/[id]',
    label: 'Thesis topic detail',
    discoverFrom: {
      path: '/dashboard/thesis/topics',
      hrefIncludes: '/dashboard/thesis/topics/',
    },
  },
  { role: 'student', path: '/dashboard/thesis/progress', label: 'Thesis progress' },
  { role: 'student', path: '/dashboard/thesis/evaluation', label: 'Thesis evaluation' },
  { role: 'lecturer', path: '/dashboard/lecturer', label: 'Lecturer dashboard' },
  { role: 'lecturer', path: '/dashboard/lecturer/schedule', label: 'Lecturer schedule' },
  { role: 'lecturer', path: '/dashboard/lecturer/announcements', label: 'Lecturer announcements' },
  { role: 'lecturer', path: '/dashboard/lecturer/grades', label: 'Lecturer grades' },
  {
    role: 'lecturer',
    path: '/dashboard/lecturer/grades/[id]',
    label: 'Lecturer grade detail',
    discoverFrom: {
      path: '/dashboard/lecturer/grades',
      hrefIncludes: '/dashboard/lecturer/grades/',
    },
  },
  { role: 'admin', path: '/admin', label: 'Admin dashboard' },
  { role: 'admin', path: '/admin/users', label: 'Admin users' },
  { role: 'admin', path: '/admin/courses', label: 'Admin courses' },
  { role: 'admin', path: '/admin/academic-years', label: 'Admin academic years' },
  { role: 'admin', path: '/admin/classrooms', label: 'Admin classrooms' },
  { role: 'admin', path: '/admin/departments', label: 'Admin departments' },
  { role: 'admin', path: '/admin/enrollments', label: 'Admin enrollments' },
  { role: 'admin', path: '/admin/lecturers', label: 'Admin lecturers' },
  { role: 'admin', path: '/admin/sections', label: 'Admin sections' },
  { role: 'admin', path: '/admin/semesters', label: 'Admin semesters' },
  { role: 'admin', path: '/admin/announcements', label: 'Admin announcements' },
  { role: 'admin', path: '/admin/invoices', label: 'Admin invoices' },
  { role: 'admin', path: '/admin/analytics', label: 'Admin analytics' },
];

function slug(value) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '');
}

function getSetCookieHeaders(response) {
  const headersArray = response.headersArray();
  return headersArray
    .filter((header) => header.name.toLowerCase() === 'set-cookie')
    .map((header) => header.value);
}

function parseSetCookie(cookie, domain) {
  const [pair, ...attributeParts] = cookie.split(';');
  const separatorIndex = pair.indexOf('=');

  if (separatorIndex < 1) {
    return null;
  }

  const parsed = {
    name: pair.slice(0, separatorIndex).trim(),
    value: pair.slice(separatorIndex + 1).trim(),
    domain,
    path: '/',
    httpOnly: false,
    secure: false,
    sameSite: 'Lax',
    expires: -1,
  };

  for (const part of attributeParts) {
    const [rawKey, ...rawValueParts] = part.trim().split('=');
    const key = rawKey.toLowerCase();
    const value = rawValueParts.join('=').trim();

    if (key === 'path' && value) {
      parsed.path = value;
    } else if (key === 'httponly') {
      parsed.httpOnly = true;
    } else if (key === 'secure') {
      parsed.secure = true;
    } else if (key === 'samesite') {
      parsed.sameSite =
        value.toLowerCase() === 'strict'
          ? 'Strict'
          : value.toLowerCase() === 'none'
            ? 'None'
            : 'Lax';
    } else if (key === 'max-age' && value) {
      const seconds = Number(value);
      if (!Number.isNaN(seconds)) {
        parsed.expires = Math.floor(Date.now() / 1000) + seconds;
      }
    } else if (key === 'expires' && value) {
      const expires = Date.parse(value);
      if (!Number.isNaN(expires)) {
        parsed.expires = Math.floor(expires / 1000);
      }
    }
  }

  return parsed;
}

async function loginSessions() {
  const domain = new URL(baseURL).hostname;
  const forwardedForByUser = {
    admin: '198.51.100.10',
    student: '198.51.100.11',
    lecturer: '198.51.100.12',
  };
  const sessions = { public: [] };
  let loginAttemptCounter = 0;

  for (const [role, credentials] of Object.entries(users)) {
    const api = await playwrightRequest.newContext();
    try {
      loginAttemptCounter += 1;
      const response = await api.post(`${apiBaseURL}/auth/login`, {
        data: credentials,
        headers: {
          'X-Forwarded-For': forwardedForByUser[role],
          'X-E2E-Login-Attempt': String(loginAttemptCounter),
        },
      });
      if (!response.ok()) {
        throw new Error(`${role} login failed: ${response.status()} ${await response.text()}`);
      }

      const cookies = getSetCookieHeaders(response)
        .map((cookie) => parseSetCookie(cookie, domain))
        .filter(Boolean);

      if (cookies.length === 0) {
        throw new Error(`${role} login returned no cookies`);
      }

      sessions[role] = cookies;
    } finally {
      await api.dispose();
    }
  }

  return sessions;
}

async function evaluatePage(page) {
  return page.evaluate(() => {
    const root = document.documentElement;
    const body = document.body;
    const rootStyles = window.getComputedStyle(root);
    const bodyStyles = window.getComputedStyle(body);
    const visible = (element) => {
      const style = window.getComputedStyle(element);
      const rect = element.getBoundingClientRect();
      return style.display !== 'none' && style.visibility !== 'hidden' && rect.width > 0 && rect.height > 0;
    };
    const mobileBottomNavVisible = Array.from(document.querySelectorAll('nav')).some((nav) => {
      const style = window.getComputedStyle(nav);
      return visible(nav) && style.position === 'fixed' && Number.parseFloat(style.bottom || '0') <= 1;
    });
    const headings = Array.from(document.querySelectorAll('h1,h2'))
      .map((heading) => heading.textContent?.trim())
      .filter(Boolean)
      .slice(0, 6);
    const documentWidth = Math.max(root.scrollWidth, body?.scrollWidth ?? 0);
    const clientWidth = root.clientWidth;

    return {
      title: document.title,
      headings,
      textLength: body.innerText.trim().length,
      fontFamily: bodyStyles.fontFamily || rootStyles.fontFamily,
      background: bodyStyles.backgroundColor,
      primaryToken: rootStyles.getPropertyValue('--primary').trim(),
      radiusToken: rootStyles.getPropertyValue('--radius').trim(),
      overflowX: Math.max(0, documentWidth - clientWidth),
      mobileBottomNavVisible,
      focusedMainCount: document.querySelectorAll('main').length,
    };
  });
}

async function resolveRoutePath(page, route) {
  if (!route.discoverFrom) {
    return route.path;
  }

  await page.goto(route.discoverFrom.path, {
    waitUntil: 'domcontentloaded',
    timeout: 30_000,
  });
  await page.waitForLoadState('networkidle', { timeout: 2_500 }).catch(() => {});

  const locator = page.locator(`a[href*="${route.discoverFrom.hrefIncludes}"]`).first();
  const href = await locator.getAttribute('href', { timeout: 10_000 });
  if (!href) {
    throw new Error(
      `Could not discover ${route.path} from ${route.discoverFrom.path} using ${route.discoverFrom.hrefIncludes}`,
    );
  }

  const resolved = new URL(href, baseURL);
  return `${resolved.pathname}${resolved.search}`;
}

async function captureRoute(browser, viewport, sessions, route) {
  const context = await browser.newContext({
    baseURL,
    viewport: { width: viewport.width, height: viewport.height },
    deviceScaleFactor: 1,
    isMobile: viewport.isMobile,
    hasTouch: viewport.hasTouch,
  });

  if (route.role !== 'public') {
    await context.addCookies(sessions[route.role]);
  }

  const page = await context.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  const failedRequests = [];

  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });
  page.on('pageerror', (error) => pageErrors.push(error.message));
  page.on('requestfailed', (request) => {
    const failure = request.failure();
    failedRequests.push({
      url: request.url(),
      method: request.method(),
      errorText: failure?.errorText ?? 'unknown',
    });
  });

  let responseStatus = null;
  let navigationError = null;
  let screenshotError = null;
  let screenshotOk = false;
  let resolvedPath = route.path;

  try {
    resolvedPath = await resolveRoutePath(page, route);
    const response = await page.goto(resolvedPath, {
      waitUntil: 'domcontentloaded',
      timeout: 30_000,
    });
    responseStatus = response?.status() ?? null;
    await page.waitForLoadState('networkidle', { timeout: 2_500 }).catch(() => {});
    await page.waitForTimeout(450);
  } catch (error) {
    navigationError = error instanceof Error ? error.message : String(error);
  }

  const screenshotPath = path.join(
    screenshotsDir,
    `${viewport.name}-${route.role}-${slug(route.path || 'home') || 'home'}.png`,
  );
  try {
    await page.screenshot({
      path: screenshotPath,
      fullPage: false,
      animations: 'disabled',
      timeout: 15_000,
    });
    screenshotOk = true;
  } catch (error) {
    screenshotError = error instanceof Error ? error.message : String(error);
  }
  const metrics = await evaluatePage(page);
  const finalUrl = page.url();
  const isAuthenticatedRoute = route.role !== 'public';
  const expectsMobileBottomNav =
    viewport.name === 'mobile' && (route.role === 'student' || route.role === 'lecturer');

  const checks = {
    navigationOk: !navigationError && responseStatus !== null && responseStatus < 400,
    authenticatedRouteStayedAuthed: !isAuthenticatedRoute || !new URL(finalUrl).pathname.includes('/login'),
    hasReadableContent: metrics.textLength > 120 && metrics.headings.length > 0,
    usesStitchFont: /Be Vietnam Pro/i.test(metrics.fontFamily),
    noHorizontalOverflow: metrics.overflowX <= 8,
    mobileBottomNavOk: !expectsMobileBottomNav || metrics.mobileBottomNavVisible,
    noConsoleErrors: consoleErrors.length === 0,
    noPageErrors: pageErrors.length === 0,
    noFailedRequests: failedRequests.length === 0,
    screenshotOk,
  };
  const ok = Object.values(checks).every(Boolean);

  await context.close();

  return {
    ...route,
    viewport: viewport.name,
    viewportSize: `${viewport.width}x${viewport.height}`,
    responseStatus,
    finalUrl,
    resolvedPath,
    screenshotPath,
    metrics,
    checks,
    ok,
    navigationError,
    consoleErrors: consoleErrors.slice(0, 5),
    pageErrors,
    screenshotError,
    failedRequests: failedRequests.slice(0, 5),
  };
}

function summarize(results) {
  const uniqueRoutes = new Set(results.map((result) => result.path));
  const failed = results.filter((result) => !result.ok);
  const overflow = results.filter((result) => result.metrics.overflowX > 8);
  const missingMobileNav = results.filter((result) => result.checks.mobileBottomNavOk === false);
  const consoleErrorRoutes = results.filter((result) => result.checks.noConsoleErrors === false);
  const failedRequestRoutes = results.filter((result) => result.checks.noFailedRequests === false);

  return {
    baseURL,
    apiBaseURL,
    capturedAt: new Date().toISOString(),
    uniqueRouteCount: uniqueRoutes.size,
    viewportCount: viewports.length,
    screenshotCount: results.length,
    passedCount: results.length - failed.length,
    failedCount: failed.length,
    overflowCount: overflow.length,
    missingMobileNavCount: missingMobileNav.length,
    consoleErrorCount: consoleErrorRoutes.length,
    failedRequestCount: failedRequestRoutes.length,
  };
}

function markdownReport(summary, results) {
  const rows = results
    .map((result) => {
      const relativeScreenshot = path.relative(planDir, result.screenshotPath).replace(/\\/g, '/');
      return [
        result.ok ? 'PASS' : 'FAIL',
        result.viewport,
        result.role,
        result.path,
        result.resolvedPath,
        result.responseStatus ?? 'n/a',
        result.metrics.overflowX,
        result.metrics.mobileBottomNavVisible ? 'yes' : 'no',
        `[png](../${relativeScreenshot})`,
      ].join(' | ');
    })
    .join('\n');

  const failures = results
    .filter((result) => !result.ok)
    .map((result) => {
      const failedChecks = Object.entries(result.checks)
        .filter(([, value]) => !value)
        .map(([key]) => key)
        .join(', ');
      return `- ${result.viewport} ${result.path}: ${failedChecks}`;
    })
    .join('\n');

  return `# FE Stitch visual QA

Date: ${summary.capturedAt}
Base URL: \`${summary.baseURL}\`
API URL: \`${summary.apiBaseURL}\`

## Summary

- Unique routes captured: ${summary.uniqueRouteCount}
- Viewports captured: ${summary.viewportCount}
- Screenshots captured: ${summary.screenshotCount}
- Passed captures: ${summary.passedCount}
- Failed captures: ${summary.failedCount}
- Horizontal overflow findings: ${summary.overflowCount}
- Missing expected mobile bottom navigation: ${summary.missingMobileNavCount}
- Console-error route captures: ${summary.consoleErrorCount}
- Failed-request route captures: ${summary.failedRequestCount}

## Route matrix

Status | Viewport | Role | Route | Resolved route | HTTP | Overflow X | Mobile bottom nav | Screenshot
--- | --- | --- | --- | --- | ---: | ---: | --- | ---
${rows}

## Findings

${failures || '- No automated visual QA failures in the captured matrix.'}
`;
}

async function main() {
  await mkdir(screenshotsDir, { recursive: true });
  const sessions = await loginSessions();
  const browser = await chromium.launch();
  const results = [];

  try {
    for (const viewport of viewports) {
      for (const route of routes) {
        console.log(`[capture] ${viewport.name} ${route.role} ${route.path}`);
        results.push(await captureRoute(browser, viewport, sessions, route));
      }
    }
  } finally {
    await browser.close();
  }

  const summary = summarize(results);
  await writeFile(path.join(artifactsDir, 'summary.json'), JSON.stringify({ summary, results }, null, 2));
  await writeFile(path.join(planDir, 'reports', 'fe-stitch-visual-qa.md'), markdownReport(summary, results));

  console.log(JSON.stringify(summary, null, 2));

  if (summary.failedCount > 0) {
    process.exitCode = 1;
  }
}

main().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
