import { chromium } from 'playwright';
import path from 'path';

const artifactsDir = 'C:\\Users\\Admin\\.gemini\\antigravity\\brain\\149f1b30-2f42-4e2f-890c-021712bdeaf1';

async function run() {
  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
  });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: 'vi-VN',
  });
  const page = await context.newPage();

  try {
    console.log('Logging in as lecturer...');
    await page.goto('http://localhost:3005/login?portal=lecturer', { waitUntil: 'networkidle' });
    await page.waitForSelector('button[type="submit"]:not([disabled])', { timeout: 15000 });
    await page.fill('input[type="email"]', 'lecturer@campuscore.edu');
    await page.fill('input[type="password"]', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForTimeout(3000);

    console.log('Navigating to thesis page repository tab...');
    await page.goto('http://localhost:3005/dashboard/thesis?tab=repository', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);

    await page.screenshot({
      path: path.join(artifactsDir, 'assistant_audit_07_lecturer_repository.png'),
      fullPage: false,
    });
    console.log('Captured assistant_audit_07_lecturer_repository.png');
  } finally {
    await browser.close();
  }
}

run();
