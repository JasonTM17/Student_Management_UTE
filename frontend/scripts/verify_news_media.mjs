import { chromium } from 'playwright';

const artifactDir = 'C:/Users/Admin/.gemini/antigravity/brain/149f1b30-2f42-4e2f-890c-021712bdeaf1';

async function main() {
  console.log('Launching browser (msedge)...');
  const browser = await chromium.launch({ channel: 'msedge', headless: true });
  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: 'vi-VN',
  });
  const page = await context.newPage();

  console.log('Navigating to http://localhost:3005 ...');
  await page.goto('http://localhost:3005', { waitUntil: 'networkidle' });

  // Find news section
  const newsSection = page.locator('section').filter({ hasText: 'Bản Tin Đại Học HCMUTE' });
  await newsSection.scrollIntoViewIfNeeded();
  await page.waitForTimeout(1000);

  console.log('Taking screenshot of Home News Section...');
  await newsSection.screenshot({ path: `${artifactDir}/news_01_homepage_section.png` });

  // Click on the featured news card
  console.log('Clicking featured news card...');
  const featuredCard = newsSection.locator('article').first();
  await featuredCard.click();
  await page.waitForTimeout(1500);

  // Check reader modal
  console.log('Taking screenshot of Reader Modal (Big Data Lab)...');
  await page.screenshot({ path: `${artifactDir}/news_02_reader_modal_bigdata.png` });

  // Close modal (Escape)
  console.log('Closing reader modal...');
  await page.keyboard.press('Escape');
  await page.waitForTimeout(800);

  // Click second news card
  console.log('Clicking second news card (Career Expo)...');
  const secondCard = newsSection.locator('article').nth(1);
  await secondCard.click();
  await page.waitForTimeout(1500);

  console.log('Taking screenshot of Reader Modal (Career Expo)...');
  await page.screenshot({ path: `${artifactDir}/news_03_reader_modal_career_expo.png` });

  await page.keyboard.press('Escape');
  await page.waitForTimeout(800);

  // Navigate to login with student portal
  console.log('Navigating to http://localhost:3005/login?portal=student ...');
  await page.goto('http://localhost:3005/login?portal=student', { waitUntil: 'networkidle' });
  await page.waitForTimeout(500);

  console.log('Filling student credentials and submitting...');
  await page.locator('input[type="email"]').fill('student@campuscore.edu');
  await page.locator('input[type="password"]').fill('password123');
  await page.locator('button[type="submit"]').click();
  await page.waitForNavigation({ waitUntil: 'networkidle' }).catch(() => {});
  await page.waitForTimeout(3000);

  console.log('Navigating to http://localhost:3005/dashboard/announcements ...');
  await page.goto('http://localhost:3005/dashboard/announcements', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2000);

  console.log('Taking screenshot of Dashboard Announcements Magazine...');
  await page.screenshot({ path: `${artifactDir}/news_04_dashboard_announcements_magazine.png` });

  await browser.close();
  console.log('All verification screenshots captured successfully!');
}

main().catch((err) => {
  console.error('Error during verification:', err);
  process.exit(1);
});
