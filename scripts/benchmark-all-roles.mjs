import pkg from '../frontend/node_modules/playwright/index.js';
const { chromium } = pkg;

async function run() {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext();
  const page = await context.newPage();

  const BASE = 'https://www.campusute.io.vn';

  const ROLES = [
    {
      name: 'Sinh viên',
      email: 'student@campuscore.edu',
      portal: 'student',
      pages: [
        { path: '/vi/dashboard', name: 'Dashboard' },
        { path: '/vi/dashboard/courses', name: 'Đăng ký môn học' },
        { path: '/vi/dashboard/schedule', name: 'Thời khóa biểu' },
        { path: '/vi/dashboard/grades', name: 'Bảng điểm' },
        { path: '/vi/dashboard/curriculum', name: 'Khung chương trình' },
        { path: '/vi/dashboard/thesis', name: 'Khóa luận tốt nghiệp' },
      ]
    },
    {
      name: 'Giảng viên',
      email: 'lecturer@campuscore.edu',
      portal: 'lecturer',
      pages: [
        { path: '/vi/dashboard/lecturer', name: 'Dashboard Giảng viên' },
        { path: '/vi/dashboard/lecturer/schedule', name: 'Lịch giảng dạy' },
        { path: '/vi/dashboard/lecturer/grades', name: 'Quản lý điểm' },
        { path: '/vi/dashboard/thesis', name: 'Đề tài hướng dẫn' },
      ]
    },
    {
      name: 'Trưởng bộ môn',
      email: 'truongbomon@campuscore.demo',
      portal: 'lecturer',
      pages: [
        { path: '/vi/dashboard/thesis', name: 'Quản lý đề tài bộ môn' },
        { path: '/vi/dashboard/lecturer', name: 'Dashboard Giảng dạy' },
      ]
    },
    {
      name: 'Trưởng khoa',
      email: 'truongkhoa@campuscore.demo',
      portal: 'dean',
      pages: [
        { path: '/vi/dashboard/dean', name: 'Tổng quan hội đồng khoa' },
        { path: '/vi/dashboard/thesis', name: 'Danh mục đề tài khoa' },
      ]
    },
    {
      name: 'Quản trị viên (Admin)',
      email: 'admin@campuscore.edu',
      portal: 'admin',
      pages: [
        { path: '/vi/admin', name: 'Dashboard Quản trị' },
        { path: '/vi/admin/users', name: 'Quản lý người dùng' },
        { path: '/vi/admin/courses', name: 'Quản lý môn học' },
        { path: '/vi/admin/departments', name: 'Quản lý khoa/ngành' },
        { path: '/vi/admin/semesters', name: 'Quản lý học kỳ' },
      ]
    }
  ];

  console.log('========================================================================');
  console.log('KIỂM TRA HIỆU NĂNG RENDER TOÀN DIỆN PRODUCTION (https://www.campusute.io.vn)');
  console.log('========================================================================\n');

  for (const r of ROLES) {
    console.log(`>>> BẮT ĐẦU KIỂM TRA ROLE: [${r.name}] (${r.email}) - Portal: ${r.portal}`);
    
    // 1. Login
    const t0 = performance.now();
    await page.goto(`${BASE}/vi/login?portal=${r.portal}`, { waitUntil: 'domcontentloaded' });
    await page.waitForSelector('button[type="submit"]');
    
    await page.fill('input[type="email"], input[name="email"]', r.email);
    await page.fill('input[type="password"], input[name="password"]', 'password123');
    
    const tSubmit = performance.now();
    await page.click('button[type="submit"]');
    
    try {
      await page.waitForURL(url => !url.pathname.includes('/login'), { timeout: 15000 });
      const loginTime = Math.round(performance.now() - tSubmit);
      console.log(`    [OK] Đăng nhập & chuyển trang thành công: ${loginTime}ms (Landed: ${page.url().replace(BASE, '')})`);
    } catch {
      console.error(`    [LỖI] Đăng nhập timeout hoặc thất bại!`);
      continue;
    }

    await page.waitForTimeout(1500);

    // 2. Visit each page
    for (const p of r.pages) {
      const tPage = performance.now();
      let status = 'OK';
      try {
        await page.goto(`${BASE}${p.path}`, { waitUntil: 'domcontentloaded' });
        await page.waitForTimeout(1200); // allow hydration & API queries to settle
        const duration = Math.round(performance.now() - tPage);
        const title = await page.title();
        console.log(`    - Trang "${p.name}" (${p.path}): ${duration}ms | Tiêu đề: "${title.slice(0, 35)}"`);
      } catch (err) {
        console.error(`    - Trang "${p.name}" (${p.path}): [LỖI] ${err.message}`);
      }
    }

    // Clear session for next role
    await context.clearCookies();
    await page.evaluate(() => localStorage.clear()).catch(() => {});
    console.log('');
  }

  await browser.close();
}

run().catch(console.error);
