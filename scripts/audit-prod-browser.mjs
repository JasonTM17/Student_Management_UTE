import pkg from '../frontend/node_modules/playwright/index.js';
const { chromium } = pkg;

async function run() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const BASE = 'https://www.campusute.io.vn';

  const ROLES_TO_TEST = [
    {
      name: 'Sinh viên',
      email: 'student@campuscore.edu',
      portal: 'student',
      pages: ['/vi/dashboard', '/vi/dashboard/courses', '/vi/dashboard/grades', '/vi/dashboard/curriculum', '/vi/dashboard/schedule', '/vi/dashboard/thesis']
    },
    {
      name: 'Giảng viên',
      email: 'lecturer@campuscore.edu',
      portal: 'lecturer',
      pages: ['/vi/dashboard/lecturer', '/vi/dashboard/thesis', '/vi/dashboard/lecturer/schedule']
    },
    {
      name: 'Trưởng bộ môn',
      email: 'truongbomon@campuscore.demo',
      portal: 'lecturer',
      pages: ['/vi/dashboard/thesis', '/vi/dashboard/lecturer']
    },
    {
      name: 'Trưởng khoa',
      email: 'truongkhoa@campuscore.demo',
      portal: 'dean',
      pages: ['/vi/dashboard/dean', '/vi/dashboard/thesis']
    },
    {
      name: 'Admin',
      email: 'admin@campuscore.edu',
      portal: 'admin',
      pages: ['/vi/admin', '/vi/admin/users', '/vi/admin/courses', '/vi/admin/departments']
    }
  ];

  try {
    for (const role of ROLES_TO_TEST) {
      console.log('\n======================================================');
      console.log(`TESTING ROLE: ${role.name} (${role.email}) - Portal: ${role.portal}`);
      console.log('======================================================');

      const requests = [];
      const onRequest = req => {
        requests.push({ url: req.url(), method: req.method(), start: performance.now() });
      };
      const onResponse = res => {
        const item = requests.find(r => r.url === res.url() && !r.end);
        if (item) {
          item.end = performance.now();
          item.status = res.status();
          item.duration = Math.round(item.end - item.start);
        }
      };

      page.on('request', onRequest);
      page.on('response', onResponse);

      // 1. Login with portal query
      const loginUrl = `${BASE}/vi/login?portal=${role.portal}`;
      const t0 = performance.now();
      await page.goto(loginUrl, { waitUntil: 'domcontentloaded' });
      console.log(`1. Login page DOM ready: ${Math.round(performance.now() - t0)}ms`);

      // 2. Submit credentials
      await page.fill('input[type="email"], input[name="email"]', role.email);
      await page.fill('input[type="password"], input[name="password"]', 'password123');
      
      const tSubmit = performance.now();
      await page.click('button[type="submit"]');
      
      // Wait for URL to leave login page
      await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 20000 });
      const loginDuration = Math.round(performance.now() - tSubmit);
      console.log(`2. Login + Auth redirect took: ${loginDuration}ms -> URL: ${page.url()}`);

      // Wait 1.5s for initial dashboard hydration
      await page.waitForTimeout(1500);

      // 3. Test each page for this role
      for (const targetPath of role.pages) {
        const tNav = performance.now();
        await page.goto(`${BASE}${targetPath}`, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1500); // allow client-side hydration & query to finish
        const pageLoadTime = Math.round(performance.now() - tNav);

        const pageTitle = await page.title();
        console.log(`   Page ${targetPath}: ${pageLoadTime}ms | "${pageTitle}"`);
      }

      // Top slowest API calls for this entire session
      const apiReqs = requests.filter(r => r.url.includes('/api/'));
      apiReqs.sort((a, b) => (b.duration || 0) - (a.duration || 0));
      console.log(`\n   Total API calls: ${apiReqs.length}. Slowest 5 API calls:`);
      for (const r of apiReqs.slice(0, 5)) {
        console.log(`     [${r.status || '?'}] ${r.duration || '?'}ms | ${r.method} ${r.url.replace(BASE, '')}`);
      }

      page.off('request', onRequest);
      page.off('response', onResponse);

      // Clear session for next role
      await context.clearCookies();
      await page.evaluate(() => localStorage.clear()).catch(() => {});
    }
  } catch (err) {
    console.error('Audit Error:', err);
  } finally {
    await browser.close();
    process.exit(0);
  }
}

run();
