import pkg from '../frontend/node_modules/playwright/index.js';
const { chromium } = pkg;

async function run() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const BASE = 'https://www.campusute.io.vn';

  page.on('request', req => {
    if (req.url().includes('/api/')) {
      console.log(`-> REQ: ${req.method()} ${req.url().replace(BASE, '')}`);
    }
  });

  page.on('response', res => {
    if (res.url().includes('/api/')) {
      console.log(`<- RES: [${res.status()}] ${res.url().replace(BASE, '')}`);
    }
  });

  try {
    console.log('1. Navigating to login...');
    const t0 = performance.now();
    await page.goto(`${BASE}/vi/login?portal=student`, { waitUntil: 'domcontentloaded' });
    console.log(`   Page loaded in: ${Math.round(performance.now() - t0)}ms`);

    // Wait for the form to be visible and filled
    await page.waitForSelector('button[type="submit"]');

    console.log('2. Clicking submit...');
    const tSubmit = performance.now();
    await page.click('button[type="submit"]');

    console.log('3. Waiting for URL to become dashboard...');
    await page.waitForURL(url => url.pathname.includes('/dashboard'), { timeout: 15000 });
    console.log(`   Login + Navigation completed in: ${Math.round(performance.now() - tSubmit)}ms`);
    console.log('   Final URL:', page.url());

    // Wait 2s for dashboard queries
    await page.waitForTimeout(3000);

    const title = await page.title();
    console.log('   Dashboard title:', title);

    // Take screenshot to inspect visual render
    await page.screenshot({ path: 'scripts/dashboard-rendered.png' });
    console.log('   Screenshot saved: scripts/dashboard-rendered.png');

  } catch (err) {
    console.error('Error:', err);
  } finally {
    await browser.close();
  }
}

run();
