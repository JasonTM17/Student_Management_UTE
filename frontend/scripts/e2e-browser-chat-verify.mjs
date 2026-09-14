import { chromium } from '@playwright/test';
import path from 'path';
import fs from 'fs';

const ARTIFACT_DIR = 'C:/Users/Admin/.gemini/antigravity/brain/149f1b30-2f42-4e2f-890c-021712bdeaf1';
const BASE_URL = 'http://127.0.0.1:3100';

async function runVerification() {
  console.log('--- Starting E2E Local Browser Verification for CampusCore Chatbot ---');
  const browser = await chromium.launch({ channel: 'chrome', headless: true });
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    locale: 'vi-VN',
  });
  const page = await context.newPage();

  try {
    console.log(`1. Navigating to ${BASE_URL}/vi/login?portal=student...`);
    await page.goto(`${BASE_URL}/vi/login?portal=student`, { waitUntil: 'networkidle', timeout: 30000 });

    console.log('2. Logging in with student credentials...');
    const emailInput = page.locator('#email');
    const passwordInput = page.locator('#password');
    const submitButton = page.locator('form').getByRole('button', { name: /Đăng nhập|Sign in/i });

    await emailInput.fill('student@campuscore.edu');
    await passwordInput.fill('password123');
    await submitButton.click();

    console.log('3. Waiting for dashboard navigation...');
    await page.waitForURL(/\/dashboard(?:$|[/?#])/, { timeout: 25000 });
    console.log(`Current URL: ${page.url()}`);

    // Take screenshot of student dashboard
    const dashboardShot = path.join(ARTIFACT_DIR, 'browser_01_dashboard.png');
    await page.screenshot({ path: dashboardShot });
    console.log(`Dashboard screenshot saved: ${dashboardShot}`);

    console.log('4. Finding and clicking the Assistant Launcher...');
    const launcher = page.getByRole('button', {
      name: /Mở trợ lý CampusCore|Open CampusCore assistant|Trợ lý CampusCore|CampusCore assistant/i,
    }).last();
    await launcher.waitFor({ state: 'visible', timeout: 15000 });
    await launcher.click();

    console.log('5. Waiting for Assistant Panel dialog...');
    const dialog = page.getByRole('dialog');
    await dialog.waitFor({ state: 'visible', timeout: 10000 });

    const composer = page.getByRole('textbox', {
      name: /Hỏi về đăng ký, lịch học, thông báo|Ask about registration, schedules, announcements/i,
    });
    const sendButton = page.getByRole('button', { name: /Gửi tin nhắn|Send message/i });

    // TEST QUERY 1: Academic Prerequisite vs Prior Course
    const query1 = 'Quy định về học phần tiên quyết và học phần học trước khác nhau như thế nào?';
    console.log(`\n--- Query 1: "${query1}" ---`);
    await composer.fill(query1);
    await sendButton.click();

    // Wait for response to stream in and settle
    console.log('Waiting for response to stream in...');
    await page.waitForTimeout(5000);
    // Wait until send button is enabled again (streaming finished)
    await sendButton.waitFor({ state: 'visible', timeout: 30000 });
    await page.waitForTimeout(2000);

    const shot1 = path.join(ARTIFACT_DIR, 'browser_02_query1_tienquyet.png');
    await page.screenshot({ path: shot1 });
    console.log(`Screenshot saved: ${shot1}`);

    // Extract conversation texts
    const articles = await page.locator('article').allInnerTexts();
    console.log(`Received ${articles.length} message articles.`);
    for (let i = 0; i < articles.length; i++) {
      console.log(`Message [${i}]:\n${articles[i]}\n---`);
    }

    // TEST QUERY 2: Tuition deadlines
    const query2 = 'Thời hạn và hình thức nộp học phí học kỳ như thế nào?';
    console.log(`\n--- Query 2: "${query2}" ---`);
    await composer.fill(query2);
    await sendButton.click();

    console.log('Waiting for response to stream in...');
    await page.waitForTimeout(5000);
    await sendButton.waitFor({ state: 'visible', timeout: 30000 });
    await page.waitForTimeout(2000);

    const shot2 = path.join(ARTIFACT_DIR, 'browser_03_query2_hocphi.png');
    await page.screenshot({ path: shot2 });
    console.log(`Screenshot saved: ${shot2}`);

    const articles2 = await page.locator('article').allInnerTexts();
    console.log(`Latest Message Content:\n${articles2[articles2.length - 1]}\n---`);

    // TEST QUERY 3: Student conduct score
    const query3 = 'Quy chế đánh giá Điểm rèn luyện sinh viên và các mức phân loại ra sao?';
    console.log(`\n--- Query 3: "${query3}" ---`);
    await composer.fill(query3);
    await sendButton.click();

    console.log('Waiting for response to stream in...');
    await page.waitForTimeout(5000);
    await sendButton.waitFor({ state: 'visible', timeout: 30000 });
    await page.waitForTimeout(2000);

    const shot3 = path.join(ARTIFACT_DIR, 'browser_04_query3_drl.png');
    await page.screenshot({ path: shot3 });
    console.log(`Screenshot saved: ${shot3}`);

    const articles3 = await page.locator('article').allInnerTexts();
    console.log(`Latest Message Content:\n${articles3[articles3.length - 1]}\n---`);

    console.log('\n=== ALL 3 BROWSER TEST QUERIES COMPLETED SUCCESSFULLY ===');
  } catch (err) {
    console.error('Error during browser verification:', err);
    const errShot = path.join(ARTIFACT_DIR, 'browser_error.png');
    await page.screenshot({ path: errShot });
    console.log(`Error screenshot saved: ${errShot}`);
  } finally {
    await browser.close();
  }
}

runVerification();
