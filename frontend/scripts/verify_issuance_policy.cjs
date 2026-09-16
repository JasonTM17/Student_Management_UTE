const { chromium } = require('@playwright/test');
const path = require('path');
const fs = require('fs');

const BASE_URL = 'http://localhost:3210';
const OUT_DIR = path.resolve(__dirname, 'policy_screenshots');
if (!fs.existsSync(OUT_DIR)) {
  fs.mkdirSync(OUT_DIR, { recursive: true });
}

async function main() {
  console.log('Starting policy verification on', BASE_URL);
  const browser = await chromium.launch({
    headless: true,
    executablePath: 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  });
  const context = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  const page = await context.newPage();

  const results = [];

  try {
    // 1. Register page
    console.log('1. Checking /register...');
    await page.goto(`${BASE_URL}/register`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.screenshot({ path: path.join(OUT_DIR, '01_register_page.png'), fullPage: true });
    const content = await page.content();
    const hasOfficePolicy = content.includes('Phòng Đào tạo') || content.includes('Academic Office');
    const hasNoForm = (await page.locator('form').count()) === 0;
    results.push({ test: 'Register page explains Academic Office issuance', pass: hasOfficePolicy });
    results.push({ test: 'Register page has no self-registration form', pass: hasNoForm });

    // 2. Student login page
    console.log('2. Checking student login page...');
    await page.goto(`${BASE_URL}/login?portal=student`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.screenshot({ path: path.join(OUT_DIR, '02_student_login.png') });
    const studentLoginText = await page.textContent('body');
    const hasStudentRegisterLink = studentLoginText.includes('Nhận tài khoản từ Phòng Đào tạo') || studentLoginText.includes('Phòng Đào tạo') || studentLoginText.includes('Academic Office');
    results.push({ test: 'Student login offers Academic Office issuance link', pass: hasStudentRegisterLink });

    // 3. Lecturer login page
    console.log('3. Checking lecturer login page...');
    await page.goto(`${BASE_URL}/login?portal=lecturer`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.screenshot({ path: path.join(OUT_DIR, '03_lecturer_login.png') });
    const lecturerLoginText = await page.textContent('body');
    const hasLecturerRegisterLink = lecturerLoginText.includes('Nhận tài khoản từ Phòng Đào tạo') || lecturerLoginText.includes('Phòng Đào tạo') || lecturerLoginText.includes('Academic Office');
    results.push({ test: 'Lecturer login offers Academic Office issuance link', pass: hasLecturerRegisterLink });

    // 4. Student profile - disabled names
    console.log('4. Logging in as student to check profile...');
    await page.goto(`${BASE_URL}/login?portal=student`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.locator('#email').fill('student@campuscore.edu');
    await page.locator('#password').fill('password123');
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/dashboard/, { timeout: 30000 });

    await page.goto(`${BASE_URL}/dashboard/profile`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.locator('#profile-first-name').waitFor({ state: 'visible', timeout: 15000 });
    await page.screenshot({ path: path.join(OUT_DIR, '04_student_profile.png'), fullPage: true });

    const studentFirstNameDisabled = await page.locator('#profile-first-name').isDisabled();
    const studentLastNameDisabled = await page.locator('#profile-last-name').isDisabled();
    const studentEmailDisabled = await page.locator('#profile-email').isDisabled();
    results.push({ test: 'Student profile first name is disabled', pass: studentFirstNameDisabled });
    results.push({ test: 'Student profile last name is disabled', pass: studentLastNameDisabled });
    results.push({ test: 'Student profile email is disabled', pass: studentEmailDisabled });

    // Logout
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await context.clearCookies();

    // 5. Lecturer profile - disabled names
    console.log('5. Logging in as lecturer to check profile...');
    await page.goto(`${BASE_URL}/login?portal=lecturer`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.locator('#email').fill('lecturer@campuscore.edu');
    await page.locator('#password').fill('password123');
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/dashboard/, { timeout: 30000 });

    await page.goto(`${BASE_URL}/dashboard/profile`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.locator('#profile-first-name').waitFor({ state: 'visible', timeout: 15000 });
    await page.screenshot({ path: path.join(OUT_DIR, '05_lecturer_profile.png'), fullPage: true });

    const lecturerFirstNameDisabled = await page.locator('#profile-first-name').isDisabled();
    const lecturerLastNameDisabled = await page.locator('#profile-last-name').isDisabled();
    results.push({ test: 'Lecturer profile first name is disabled', pass: lecturerFirstNameDisabled });
    results.push({ test: 'Lecturer profile last name is disabled', pass: lecturerLastNameDisabled });

    // Logout
    await page.evaluate(() => {
      localStorage.clear();
      sessionStorage.clear();
    });
    await context.clearCookies();

    // 6. Admin user management - issuance policy
    console.log('6. Logging in as admin to check user management policy...');
    await page.goto(`${BASE_URL}/login?portal=admin`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.locator('#email').fill('admin@campuscore.edu');
    await page.locator('#password').fill('admin123');
    await page.locator('button[type="submit"]').click();
    await page.waitForURL(/admin/, { timeout: 30000 });
    await page.waitForTimeout(1500);

    await page.goto(`${BASE_URL}/vi/admin/users`, { waitUntil: 'networkidle', timeout: 30000 });
    await page.waitForTimeout(1500);
    await page.screenshot({ path: path.join(OUT_DIR, '06_admin_users.png'), fullPage: true });

    const adminPageText = await page.textContent('body');
    const hasIssuanceBanner = adminPageText.includes('Chính sách cấp tài khoản học vụ tập trung') || adminPageText.includes('Phòng Đào tạo cấp tài khoản cho Sinh viên và Giảng viên');
    results.push({ test: 'Admin users console displays centralized issuance policy', pass: hasIssuanceBanner });

  } catch (err) {
    console.error('Error during verification:', err);
    await page.screenshot({ path: path.join(OUT_DIR, 'error_step.png') }).catch(() => {});
    console.log('Current URL on error:', page.url());
    results.push({ test: 'Execution error', pass: false, error: err.message, url: page.url() });
  } finally {
    await browser.close();
  }

  console.log('\n--- VERIFICATION RESULTS ---');
  console.log(JSON.stringify(results, null, 2));
  fs.writeFileSync(path.join(OUT_DIR, 'results.json'), JSON.stringify(results, null, 2));
}

main();
