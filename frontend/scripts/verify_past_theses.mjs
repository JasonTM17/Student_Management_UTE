import { chromium } from 'playwright';
import path from 'path';

const artifactsDir = 'C:\\Users\\Admin\\.gemini\\antigravity\\brain\\149f1b30-2f42-4e2f-890c-021712bdeaf1';

async function run() {
  console.log('Launching browser with msedge channel...');
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
    console.log('1. Navigating to login page for student portal...');
    await page.goto('http://localhost:3005/login?portal=student', { waitUntil: 'networkidle' });

    console.log('2. Filling student credentials...');
    await page.waitForSelector('button[type="submit"]:not([disabled])', { timeout: 15000 });
    await page.fill('input[type="email"]', 'student@campuscore.edu');
    await page.fill('input[type="password"]', 'password123');
    await page.click('button[type="submit"]');

    console.log('3. Waiting for dashboard navigation...');
    await page.waitForURL('**/dashboard*', { timeout: 15000 });
    console.log('Navigated to:', page.url());

    console.log('4. Navigating to Thesis Repository tab (/dashboard/thesis?tab=repository)...');
    await page.goto('http://localhost:3005/dashboard/thesis?tab=repository', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);

    // Capture repository page view
    await page.screenshot({
      path: path.join(artifactsDir, 'assistant_audit_03_thesis_repository.png'),
      fullPage: false,
    });
    console.log('Captured assistant_audit_03_thesis_repository.png');

    console.log('5. Triggering AI Assistant Widget...');
    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('open-campus-assistant'));
    });
    await page.waitForTimeout(1000);

    const assistantInput = page.locator('textarea[placeholder*="hỏi"], textarea[placeholder*="nhập"], textarea').first();
    await assistantInput.waitFor({ state: 'visible', timeout: 5000 });

    console.log('6. Asking AI Assistant about past cohort graduation theses...');
    await assistantInput.fill('Cho tôi xem các đề tài khóa luận của sinh viên khóa trước ngành CNTT để tham khảo');
    await page.keyboard.press('Enter');
    console.log('Query sent. Waiting for response...');
    await page.waitForTimeout(4000);

    // Capture answered past theses in assistant
    await page.screenshot({
      path: path.join(artifactsDir, 'assistant_audit_04_past_theses_chat.png'),
      fullPage: false,
    });
    console.log('Captured assistant_audit_04_past_theses_chat.png');

    console.log('7. Asking AI Assistant about student certificates (Nghị định 13/2016)...');
    await assistantInput.fill('Làm sao xin giấy tạm hoãn nghĩa vụ quân sự và vay vốn sinh viên?');
    await page.keyboard.press('Enter');
    console.log('Query sent. Waiting for response...');
    await page.waitForTimeout(4000);

    // Capture answered certificates in assistant
    await page.screenshot({
      path: path.join(artifactsDir, 'assistant_audit_05_certificates_chat.png'),
      fullPage: false,
    });
    console.log('Captured assistant_audit_05_certificates_chat.png');

    console.log('8. Navigating to Certificates page (/dashboard/certificates)...');
    await page.goto('http://localhost:3005/dashboard/certificates', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);

    // Capture certificates self-service page
    await page.screenshot({
      path: path.join(artifactsDir, 'assistant_audit_06_certificates_page.png'),
      fullPage: false,
    });
    console.log('Captured assistant_audit_06_certificates_page.png');

    console.log('9. Navigating to lecturer login...');
    await page.goto('http://localhost:3005/login?portal=lecturer', { waitUntil: 'networkidle' });
    await page.waitForSelector('button[type="submit"]:not([disabled])', { timeout: 15000 });
    await page.fill('input[type="email"]', 'lecturer@campuscore.edu');
    await page.fill('input[type="password"]', 'password123');
    await page.click('button[type="submit"]');
    await page.waitForURL('**/dashboard*', { timeout: 15000 });

    console.log('10. Navigating to lecturer thesis repository tab...');
    await page.goto('http://localhost:3005/dashboard/thesis?tab=repository', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);

    await page.screenshot({
      path: path.join(artifactsDir, 'assistant_audit_07_lecturer_repository.png'),
      fullPage: false,
    });
    console.log('Captured assistant_audit_07_lecturer_repository.png');

    console.log('Verification completed successfully!');
  } catch (err) {
    console.error('Verification error:', err);
    await page.screenshot({
      path: path.join(artifactsDir, 'past_theses_assistant_error.png'),
      fullPage: false,
    });
    throw err;
  } finally {
    await browser.close();
  }
}

run();
