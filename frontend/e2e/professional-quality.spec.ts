import AxeBuilder from '@axe-core/playwright';
import { expect, test, type Page, type TestInfo } from '@playwright/test';

// Cold Next.js development compilation plus the 56-route admin sweep can take
// several minutes on Windows. Keep assertions strict while giving the deep
// matrix enough time to finish instead of turning compiler latency into a
// false timeout.
test.describe.configure({ timeout: 600_000 });

type Persona = 'student' | 'lecturer' | 'admin';

const accounts: Record<Persona, { email: string; password: string }> = {
  student: { email: 'student@campuscore.edu', password: 'password123' },
  lecturer: { email: 'lecturer@campuscore.edu', password: 'password123' },
  admin: { email: 'admin@campuscore.edu', password: 'admin123' },
};

const routes: Record<Persona, string[]> = {
  student: [
    '/dashboard',
    '/dashboard/register',
    '/dashboard/enrollments',
    '/dashboard/schedule',
    '/dashboard/grades',
    '/dashboard/transcript',
    '/dashboard/announcements',
    '/dashboard/notifications',
    '/dashboard/profile',
    '/dashboard/thesis',
    '/dashboard/thesis/topics',
    '/dashboard/thesis/topics/22222222-2222-2222-2222-222222222201',
    '/dashboard/thesis/22222222-2222-2222-2222-222222222101',
    '/dashboard/thesis/progress',
  ],
  lecturer: [
    '/dashboard/lecturer',
    '/dashboard/lecturer/schedule',
    '/dashboard/lecturer/grades',
    '/dashboard/lecturer/grades/section-java-demo',
    '/dashboard/lecturer/announcements',
  ],
  admin: [
    '/admin',
    '/admin/users',
    '/admin/lecturers',
    '/admin/departments',
    '/admin/academic-years',
    '/admin/semesters',
    '/admin/courses',
    '/admin/sections',
    '/admin/classrooms',
    '/admin/enrollments',
    '/admin/announcements',
    '/admin/appearance',
    '/admin/assistant-knowledge',
    '/admin/thesis',
  ],
};

const fullRouteProjects = new Set(['mobile-390', 'desktop-1440']);
const archetypeProjects = new Set(['tablet-768', 'desktop-1024']);

function localized(locale: 'en' | 'vi', route: string) {
  return `/${locale}${route === '/' ? '' : route}`;
}

async function signIn(page: Page, persona: Persona) {
  await page.goto(`/login?portal=${persona}`);
  const submit = page.locator('form').getByRole('button', { name: /sign in/i });
  await expect(submit).toBeEnabled({ timeout: 20_000 });
  await page.locator('#email').fill(accounts[persona].email);
  await page.locator('#password').fill(accounts[persona].password);
  await submit.click();
  await expect(page).not.toHaveURL(/\/login(?:$|[/?#])/, { timeout: 20_000 });
}

async function setTheme(page: Page, theme: 'light' | 'dark') {
  await page.evaluate((nextTheme) => {
    localStorage.setItem('theme', nextTheme);
    document.documentElement.classList.toggle('dark', nextTheme === 'dark');
  }, theme);
}

async function assertViewportIntegrity(page: Page, route: string) {
  await expect(page.getByRole('main')).toBeVisible({ timeout: 20_000 });
  const metrics = await page.evaluate(() => ({
    viewport: window.innerWidth,
    document: document.documentElement.scrollWidth,
    body: document.body.scrollWidth,
  }));
  expect.soft(metrics.document, `${route} document overflow`).toBeLessThanOrEqual(metrics.viewport + 1);
  expect.soft(metrics.body, `${route} body overflow`).toBeLessThanOrEqual(metrics.viewport + 1);
}

function observeRuntimeFailures(page: Page) {
  const errors: string[] = [];
  page.on('console', (message) => {
    if (message.type() === 'error') errors.push(`console: ${message.text()}`);
  });
  page.on('pageerror', (error) => errors.push(`pageerror: ${error.message}`));
  page.on('response', (response) => {
    const url = new URL(response.url());
    if (url.pathname.startsWith('/api/') && response.status() >= 400) {
      errors.push(`network: ${response.status()} ${url.pathname}`);
    }
  });
  return errors;
}

for (const persona of Object.keys(routes) as Persona[]) {
  test(`full ${persona} route matrix has no overflow or runtime failure`, async ({ page }, testInfo) => {
    test.skip(!fullRouteProjects.has(testInfo.project.name), 'Full route sweeps run at 390px and 1440px.');
    const runtimeFailures = observeRuntimeFailures(page);
    await signIn(page, persona);

    for (const locale of ['en', 'vi'] as const) {
      for (const theme of ['light', 'dark'] as const) {
        await setTheme(page, theme);
        for (const route of routes[persona]) {
          runtimeFailures.length = 0;
          const target = localized(locale, route);
          const response = await page.goto(target, { waitUntil: 'domcontentloaded' });
          expect.soft(response?.status(), `${target} response`).toBeLessThan(400);
          await assertViewportIntegrity(page, `${testInfo.project.name} ${locale}/${theme} ${route}`);
          await page.waitForTimeout(150);
          expect.soft(runtimeFailures, `${target} runtime failures`).toEqual([]);
        }
      }
    }
  });
}

test('public/auth matrix is bilingual, theme-safe, and keyboard reachable', async ({ page }, testInfo) => {
  test.skip(!fullRouteProjects.has(testInfo.project.name), 'Public route sweeps run at 390px and 1440px.');
  const runtimeFailures = observeRuntimeFailures(page);
  for (const locale of ['en', 'vi'] as const) {
    for (const theme of ['light', 'dark'] as const) {
      for (const route of ['/', '/login', '/register']) {
        runtimeFailures.length = 0;
        await page.goto(localized(locale, route));
        await setTheme(page, theme);
        await page.reload({ waitUntil: 'domcontentloaded' });
        await assertViewportIntegrity(page, `${locale}/${theme} ${route}`);
        expect(await page.locator('html').getAttribute('lang')).toMatch(locale === 'vi' ? /^vi/ : /^en/);
        await page.keyboard.press('Tab');
        const focused = await page.evaluate(() => document.activeElement?.tagName ?? '');
        expect(focused).not.toBe('BODY');
        expect.soft(runtimeFailures, `${route} runtime failures`).toEqual([]);
      }
    }
  }
});

const archetypes: Array<{ persona: Persona | 'public'; route: string }> = [
  { persona: 'public', route: '/' },
  { persona: 'public', route: '/login' },
  { persona: 'student', route: '/dashboard' },
  { persona: 'student', route: '/dashboard/register' },
  { persona: 'lecturer', route: '/dashboard/lecturer/grades' },
  { persona: 'admin', route: '/admin/users' },
  { persona: 'admin', route: '/admin/assistant-knowledge' },
];

test('layout archetypes have no serious/critical axe findings', async ({ page }, testInfo: TestInfo) => {
  test.skip(!archetypeProjects.has(testInfo.project.name), 'Axe archetypes run at 768px and 1024px.');
  let activePersona: Persona | undefined;
  for (const archetype of archetypes) {
    if (archetype.persona !== 'public' && archetype.persona !== activePersona) {
      await page.context().clearCookies();
      await signIn(page, archetype.persona);
      activePersona = archetype.persona;
    }
    for (const locale of ['en', 'vi'] as const) {
      await page.goto(localized(locale, archetype.route));
      await expect(page.getByRole('main')).toBeVisible({ timeout: 20_000 });
      const result = await new AxeBuilder({ page })
        .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
        .analyze();
      const blocking = result.violations.filter((violation) =>
        violation.impact === 'serious' || violation.impact === 'critical');
      expect(blocking, `${localized(locale, archetype.route)} axe blockers`).toEqual([]);
    }
  }
});

test('reduced-motion preference disables non-essential animation', async ({ page }, testInfo) => {
  test.skip(!archetypeProjects.has(testInfo.project.name), 'Reduced-motion proof runs with the archetype matrix.');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/');
  const offenders = await page.evaluate(() =>
    [...document.querySelectorAll<HTMLElement>('body *')]
      .filter((element) => {
        const style = getComputedStyle(element);
        return (Number.parseFloat(style.animationDuration) > 0.01 && style.animationIterationCount !== '1')
          || Number.parseFloat(style.transitionDuration) > 0.2;
      })
      .slice(0, 20)
      .map((element) => element.outerHTML.slice(0, 160)));
  expect(offenders).toEqual([]);
});
