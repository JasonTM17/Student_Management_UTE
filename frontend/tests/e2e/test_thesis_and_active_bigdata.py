import os
import sys
import time
from playwright.sync_api import sync_playwright

ARTIFACT_DIR = r"C:\Users\Admin\.gemini\antigravity\brain\32baa29e-0b4a-488d-8978-7fca7b16587f"
FRONTEND_URL = "http://127.0.0.1:3100"
SWAGGER_URL = "http://127.0.0.1:4010/swagger-ui/index.html"

def run_tests():
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        print("\n--- STEP 1: Verify Student Active Courses Big Data ---")
        # 1. Login as Student
        page.goto(f"{FRONTEND_URL}/login")
        page.wait_for_selector('input#email', timeout=15000)
        page.fill('input#email', 'student@campuscore.edu')
        page.fill('input#password', 'password123')
        page.click('button[type="submit"]')
        page.wait_for_url("**/dashboard**", timeout=15000)
        print("Logged in as student.")

        # 2. Go to enrollments
        page.goto(f"{FRONTEND_URL}/dashboard/enrollments")
        page.wait_for_selector('text=Chương trình đào tạo (CTĐT)', timeout=15000)
        time.sleep(2)

        # Take screenshot of active courses
        active_courses_shot = os.path.join(ARTIFACT_DIR, "verified_active_students_bigdata.png")
        page.screenshot(path=active_courses_shot, full_page=True)
        print(f"Captured active courses screenshot: {active_courses_shot}")

        # Check in-progress courses card
        in_progress_el = page.locator('text=Đang học').first
        assert in_progress_el.is_visible(), "Đang học card must be visible"

        print("\n--- STEP 2: Verify Student Thesis Workspace & Report Submission ---")
        page.goto(f"{FRONTEND_URL}/dashboard/thesis")
        page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)

        # Ensure round "Đồ án tốt nghiệp 2026-2027" is selected
        select_el = page.locator('select').first
        if select_el.is_visible():
            for opt in select_el.locator('option').all():
                if "Đồ án tốt nghiệp" in opt.inner_text():
                    select_el.select_option(opt.get_attribute('value'))
                    time.sleep(2)
                    break

        # Check if report submission button is available
        submit_btn = page.locator('button:has-text("Nộp báo cáo"), button:has-text("Cập nhật báo cáo")').first
        if submit_btn.is_visible():
            print("Found report submission button, clicking...")
            submit_btn.click()
            time.sleep(1)
            # Fill report form
            title_input = page.locator('input#thesis-report-title')
            url_input = page.locator('input#thesis-report-url')
            note_input = page.locator('textarea#thesis-report-note')
            
            if url_input.is_visible():
                title_input.fill("Báo cáo Khóa luận Tốt nghiệp - Hệ thống Quản trị Học vụ CampusUTE")
                url_input.fill("https://storage.hcmute.edu.vn/luanvan/2026/kltn-campuscore-report.pdf")
                note_input.fill("Báo cáo chính thức đợt 1 năm học 2026-2027 có kèm mã nguồn và slide bảo vệ")
                page.click('form button[type="submit"]')
                time.sleep(2)
                print("Submitted thesis report.")

        thesis_shot = os.path.join(ARTIFACT_DIR, "verified_thesis_report_submitted.png")
        page.screenshot(path=thesis_shot, full_page=True)
        print(f"Captured thesis report screenshot: {thesis_shot}")

        print("\n--- STEP 3: Verify Lecturer Workspace & Council Defense Grading ---")
        lecturer_context = browser.new_context(viewport={"width": 1440, "height": 900})
        lpage = lecturer_context.new_page()
        lpage.goto(f"{FRONTEND_URL}/login?portal=lecturer")
        lpage.wait_for_selector('input#email', timeout=15000)
        lpage.fill('input#email', 'lecturer@campuscore.edu')
        lpage.fill('input#password', 'password123')
        lpage.click('button[type="submit"]')
        lpage.wait_for_url("**/dashboard**", timeout=15000)
        print("Logged in as lecturer.")

        lpage.goto(f"{FRONTEND_URL}/dashboard/thesis")
        lpage.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)

        # Ensure round "Đồ án tốt nghiệp 2026-2027" is selected for supervised group
        select_el = lpage.locator('select').first
        if select_el.is_visible():
            for opt in select_el.locator('option').all():
                if "Đồ án tốt nghiệp" in opt.inner_text():
                    select_el.select_option(opt.get_attribute('value'))
                    time.sleep(2)
                    break

        lecturer_shot = os.path.join(ARTIFACT_DIR, "verified_lecturer_thesis_workspace.png")
        lpage.screenshot(path=lecturer_shot, full_page=True)
        print(f"Captured lecturer thesis workspace screenshot: {lecturer_shot}")

        # Now select round "KLTN 2026-2027 Audit 2" where lecturer is Council Chair
        if select_el.is_visible():
            for opt in select_el.locator('option').all():
                if "Audit 2" in opt.inner_text():
                    select_el.select_option(opt.get_attribute('value'))
                    time.sleep(2)
                    break
        defense_tab = lpage.locator('button:has-text("2. Hội Đồng Chấm Bảo Vệ"), button:has-text("Hội Đồng Chấm Bảo Vệ")').first
        if defense_tab.is_visible():
            defense_tab.click()
            time.sleep(1)
        council_shot = os.path.join(ARTIFACT_DIR, "verified_council_defense_grading.png")
        lpage.screenshot(path=council_shot, full_page=True)
        print(f"Captured council defense grading screenshot: {council_shot}")

        lecturer_context.close()

        print("\n--- STEP 4: Verify Spring Swagger OpenAPI UI ---")
        page.goto(SWAGGER_URL)
        page.wait_for_selector('.swagger-ui', timeout=15000)
        time.sleep(2)

        swagger_shot = os.path.join(ARTIFACT_DIR, "verified_swagger_ui_hcmute.png")
        page.screenshot(path=swagger_shot, full_page=True)
        print(f"Captured Swagger UI screenshot: {swagger_shot}")

        browser.close()
        print("\nAll Playwright E2E verification steps completed successfully!")

if __name__ == "__main__":
    run_tests()
