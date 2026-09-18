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
    console.log('1. Navigating to login page...');
    await page.goto('http://localhost:3005/login?portal=lecturer', { waitUntil: 'networkidle' });

    console.log('2. Waiting for client ready and filling credentials...');
    await page.waitForSelector('button[type="submit"]:not([disabled])', { timeout: 15000 });
    await page.fill('input[type="email"]', 'lecturer@campuscore.edu');
    await page.fill('input[type="password"]', 'password123');
    await page.click('button[type="submit"]');

    console.log('3. Waiting for dashboard navigation...');
    await page.waitForURL('**/dashboard/**', { timeout: 15000 });
    console.log('Navigated to:', page.url());

    console.log('4. Navigating to thesis dashboard...');
    await page.goto('http://localhost:3005/dashboard/thesis', { waitUntil: 'networkidle' });
    await page.waitForTimeout(2000);

    // Capture initial thesis view
    await page.screenshot({
      path: path.join(artifactsDir, 'thesis_audit_01_overview.png'),
      fullPage: false,
    });
    console.log('Captured thesis_audit_01_overview.png');

    console.log('5. Clicking "Đề xuất đề tài mới"...');
    const proposeBtn = page.getByRole('button', { name: /đề xuất đề tài/i }).first();
    await proposeBtn.click();
    await page.waitForTimeout(1000);

    // Capture open modal with round selector
    await page.screenshot({
      path: path.join(artifactsDir, 'thesis_audit_02_propose_modal.png'),
      fullPage: false,
    });
    console.log('Captured thesis_audit_02_propose_modal.png');

    console.log('6. Filling new topic proposal details...');
    const topicTitle = `Nghiên cứu ứng dụng Transformer trong Giám sát Thông minh (HCM-UTE AI Lab ${Date.now().toString().slice(-4)})`;
    await page.fill('input[placeholder*="đề tài"]', topicTitle);

    // Fill description inside editor
    const editor = page.locator('.ProseMirror, textarea, [contenteditable="true"]').first();
    if (await editor.isVisible()) {
      await editor.click();
      await page.keyboard.type('Đề tài tập trung nghiên cứu kiến trúc Vision Transformer và tối ưu hóa suy luận mô hình trên phần cứng GPU NVIDIA A100 tại phòng thực nghiệm AI trường ĐH Sư phạm Kỹ thuật TP.HCM.');
    }

    await page.waitForTimeout(500);
    await page.screenshot({
      path: path.join(artifactsDir, 'thesis_audit_03_form_filled.png'),
      fullPage: false,
    });
    console.log('Captured thesis_audit_03_form_filled.png');

    console.log('7. Submitting topic proposal...');
    const submitBtn = page.getByRole('button', { name: /đề xuất đề tài/i }).last();
    await submitBtn.click();
    await page.waitForTimeout(3000);

    // Capture after submit
    await page.screenshot({
      path: path.join(artifactsDir, 'thesis_audit_04_topic_created.png'),
      fullPage: false,
    });
    console.log('Captured thesis_audit_04_topic_created.png');

    console.log('8. Testing AI Assistant Widget...');
    await page.evaluate(() => {
      window.dispatchEvent(new CustomEvent('open-campus-assistant'));
    });
    await page.waitForTimeout(1000);

    // Capture opened assistant panel
    await page.screenshot({
      path: path.join(artifactsDir, 'assistant_audit_01_open.png'),
      fullPage: false,
    });
    console.log('Captured assistant_audit_01_open.png');

    console.log('9. Sending query to AI Assistant...');
    const assistantInput = page.locator('textarea[placeholder*="hỏi"], textarea[placeholder*="nhập"], textarea').first();
    if (await assistantInput.isVisible()) {
      await assistantInput.fill('Các đề tài tôi đang hướng dẫn và lịch giảng dạy tuần này');
      await page.keyboard.press('Enter');
      console.log('Query sent. Waiting for answer...');
      await page.waitForTimeout(5000);

      // Capture answered assistant
      await page.screenshot({
        path: path.join(artifactsDir, 'assistant_audit_02_answered.png'),
        fullPage: false,
      });
      console.log('Captured assistant_audit_02_answered.png');
    }

    console.log('Verification completed successfully!');
  } catch (err) {
    console.error('Verification error:', err);
    await page.screenshot({
      path: path.join(artifactsDir, 'thesis_assistant_error.png'),
      fullPage: false,
    });
    throw err;
  } finally {
    await browser.close();
  }
}

run();
