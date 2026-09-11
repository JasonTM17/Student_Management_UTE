import os
import sys
import time
import json
from playwright.sync_api import sync_playwright

ARTIFACT_DIR = r"C:\Users\Admin\.gemini\antigravity\brain\32baa29e-0b4a-488d-8978-7fca7b16587f"
FRONTEND_URL = "http://127.0.0.1:3100"

findings = []

def record_finding(category, severity, page_url, title, description, details=None):
    finding = {
        "category": category,
        "severity": severity,
        "url": page_url,
        "title": title,
        "description": description,
        "details": details or {}
    }
    findings.append(finding)
    print(f"\n[FINDING - {severity.upper()}] [{category}] @ {page_url}: {title}")
    print(f"  -> {description}")
    if details:
        print(f"  -> Details: {details}")

def run_deep_qa_audit():
    print("================================================================")
    print("STARTING DEEP BROWSER QA & REAL BUG HUNTING AUDIT")
    print(f"Target: {FRONTEND_URL}")
    print(f"Artifacts: {ARTIFACT_DIR}")
    print("================================================================\n")

    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0"
        )
        page = context.new_page()

        console_logs = []
        page_errors = []
        failed_requests = []
        bad_responses = []

        page.on("console", lambda msg: console_logs.append({
            "type": msg.type,
            "text": msg.text,
            "location": msg.location
        }))
        page.on("pageerror", lambda err: page_errors.append(str(err)))
        page.on("requestfailed", lambda req: failed_requests.append({
            "url": req.url,
            "method": req.method,
            "failure": req.failure
        }))
        page.on("response", lambda res: (
            bad_responses.append({
                "url": res.url,
                "status": res.status,
                "status_text": res.status_text
            }) if res.status >= 400 and "/api/v1/auth/login" not in res.url else None
        ))

        def flush_events_and_check(current_url, step_name):
            if page_errors:
                while page_errors:
                    err = page_errors.pop(0)
                    record_finding("UNCAUGHT_JS_EXCEPTION", "critical", current_url, f"Uncaught Error in {step_name}", err)

            if bad_responses:
                while bad_responses:
                    bad = bad_responses.pop(0)
                    if "favicon" not in bad["url"]:
                        record_finding("HTTP_ERROR_RESPONSE", "high", current_url, f"Bad HTTP {bad['status']} in {step_name}", f"Request to {bad['url']} returned status {bad['status']}")

            err_logs = [l for l in console_logs if l["type"] == "error"]
            if err_logs:
                for l in err_logs:
                    record_finding("CONSOLE_ERROR", "medium", current_url, f"Console error in {step_name}", l["text"], l["location"])
            console_logs.clear()

        # PHASE 1: GUEST & PUBLIC FLOW
        print("\n>>> PHASE 1: GUEST & PUBLIC FLOW AUDIT <<<")
        page.goto(f"{FRONTEND_URL}/")
        page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        flush_events_and_check(page.url, "Homepage Load")

        # Test Dark mode toggle
        dark_toggle = page.locator('button[aria-label*="giao diện tối"], button:has-text("Chuyển sang giao diện tối")').first
        if dark_toggle.is_visible():
            dark_toggle.click()
            time.sleep(1)
            print("Toggled Dark Mode on Homepage.")
            page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_phase1_dark_mode.png"))
            light_toggle = page.locator('button[aria-label*="giao diện sáng"], button:has-text("Chuyển sang giao diện sáng")').first
            if light_toggle.is_visible():
                light_toggle.click()
                time.sleep(1)

        # Test Language toggle
        en_toggle = page.locator('button:has-text("Tiếng Anh")').first
        if en_toggle.is_visible():
            en_toggle.click()
            time.sleep(1)
            print("Toggled English Language.")
            flush_events_and_check(page.url, "Language Toggle to EN")
            vn_toggle = page.locator('button:has-text("Tiếng Việt")').first
            if vn_toggle.is_visible():
                vn_toggle.click()
                time.sleep(1)

        # Test clicking announcement on Homepage
        announcement_card = page.locator('article[role="button"], button:has-text("HCMUTE Emblem Watermark")').first
        if announcement_card.is_visible():
            announcement_card.click()
            time.sleep(2)
            flush_events_and_check(page.url, "Homepage Announcement Open")
            close_btn = page.locator('button:has-text("Đóng"), button[aria-label="Close"]').first
            if close_btn.is_visible():
                close_btn.click()
                time.sleep(1)

        # ====================================================================
        # PHASE 2: STUDENT ROLE FLOW
        # ====================================================================
        print("\n>>> PHASE 2: STUDENT ROLE FLOW AUDIT <<<")
        student_context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0"
        )
        student_page = student_context.new_page()
        student_page.on("console", lambda msg: console_logs.append({"type": msg.type, "text": msg.text, "location": msg.location}))
        student_page.on("pageerror", lambda err: page_errors.append(str(err)))
        student_page.on("response", lambda res: (
            bad_responses.append({"url": res.url, "status": res.status, "status_text": res.status_text})
            if res.status >= 400 and "/api/v1/auth/login" not in res.url else None
        ))

        student_page.goto(f"{FRONTEND_URL}/login")
        student_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(1)

        student_page.locator('input[type="email"]').fill("student@campuscore.edu")
        student_page.locator('input[type="password"]').fill("password123")
        student_page.locator('button:has-text("Đăng nhập")').click()
        student_page.wait_for_url("**/dashboard**", timeout=10000)
        time.sleep(2)
        print("Logged in as Student successfully.")
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_dashboard.png"))
        flush_events_and_check(student_page.url, "Student Dashboard Load")

        # 2.1 Schedule Page
        print("Checking /dashboard/schedule...")
        student_page.goto(f"{FRONTEND_URL}/dashboard/schedule")
        student_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_schedule.png"))
        flush_events_and_check(student_page.url, "Student Schedule Page")

        # 2.2 Conduct Points Page (ĐRL)
        print("Checking /dashboard/conduct...")
        student_page.goto(f"{FRONTEND_URL}/dashboard/conduct")
        student_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_conduct.png"))
        flush_events_and_check(student_page.url, "Student Conduct Page")

        # 2.3 Course Registration Page
        print("Checking /dashboard/register...")
        student_page.goto(f"{FRONTEND_URL}/dashboard/register")
        student_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_register.png"))
        flush_events_and_check(student_page.url, "Student Register Page")

        # 2.4 Transcript & Grades Page
        print("Checking /dashboard/transcript...")
        student_page.goto(f"{FRONTEND_URL}/dashboard/transcript")
        student_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_transcript.png"))
        flush_events_and_check(student_page.url, "Student Transcript Page")

        # 2.5 Announcements Page
        print("Checking /dashboard/announcements...")
        student_page.goto(f"{FRONTEND_URL}/dashboard/announcements")
        student_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_announcements.png"))
        flush_events_and_check(student_page.url, "Student Announcements Page")

        # 2.6 Profile Page
        print("Checking /dashboard/profile...")
        student_page.goto(f"{FRONTEND_URL}/dashboard/profile")
        student_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_profile.png"))
        flush_events_and_check(student_page.url, "Student Profile Page")

        # 2.7 Thesis Page for Student
        print("Checking /dashboard/thesis...")
        student_page.goto(f"{FRONTEND_URL}/dashboard/thesis")
        student_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_thesis.png"))
        flush_events_and_check(student_page.url, "Student Thesis Page")

        # 2.8 Student attempting to access Admin pages (RBAC boundary probe)
        print("Probing RBAC: Student accessing /admin...")
        student_page.goto(f"{FRONTEND_URL}/admin")
        time.sleep(2)
        print(f"URL after student navigated to /admin: {student_page.url}")
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_rbac_admin_attempt.png"))
        flush_events_and_check(student_page.url, "Student Accessing /admin")

        print("Probing RBAC: Student accessing /admin/editor...")
        student_page.goto(f"{FRONTEND_URL}/admin/editor")
        time.sleep(2)
        print(f"URL after student navigated to /admin/editor: {student_page.url}")
        student_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_student_rbac_editor_attempt.png"))
        flush_events_and_check(student_page.url, "Student Accessing /admin/editor")

        student_context.close()

        # ====================================================================
        # PHASE 3: LECTURER ROLE FLOW
        # ====================================================================
        print("\n>>> PHASE 3: LECTURER ROLE FLOW AUDIT <<<")
        lecturer_context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0"
        )
        lecturer_page = lecturer_context.new_page()
        lecturer_page.on("console", lambda msg: console_logs.append({"type": msg.type, "text": msg.text, "location": msg.location}))
        lecturer_page.on("pageerror", lambda err: page_errors.append(str(err)))
        lecturer_page.on("response", lambda res: (
            bad_responses.append({"url": res.url, "status": res.status, "status_text": res.status_text})
            if res.status >= 400 and "/api/v1/auth/login" not in res.url else None
        ))

        lecturer_page.goto(f"{FRONTEND_URL}/login?portal=lecturer")
        lecturer_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(1)

        lecturer_page.locator('input[type="email"]').fill("lecturer@campuscore.edu")
        lecturer_page.locator('input[type="password"]').fill("password123")
        lecturer_page.locator('button:has-text("Đăng nhập")').click()
        lecturer_page.wait_for_url("**/dashboard**", timeout=10000)
        time.sleep(2)
        print("Logged in as Lecturer successfully.")
        lecturer_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_lecturer_dashboard.png"))
        flush_events_and_check(lecturer_page.url, "Lecturer Dashboard Load")

        # 3.1 Lecturer Schedule
        print("Checking Lecturer Schedule...")
        lecturer_page.goto(f"{FRONTEND_URL}/dashboard/schedule")
        lecturer_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        lecturer_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_lecturer_schedule.png"))
        flush_events_and_check(lecturer_page.url, "Lecturer Schedule Page")

        # 3.2 Lecturer Thesis
        print("Checking Lecturer Thesis (/dashboard/thesis)...")
        lecturer_page.goto(f"{FRONTEND_URL}/dashboard/thesis")
        lecturer_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        lecturer_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_lecturer_thesis.png"))
        flush_events_and_check(lecturer_page.url, "Lecturer Thesis Page")

        # 3.3 Lecturer Grades
        print("Checking Lecturer Grades (/dashboard/grades)...")
        lecturer_page.goto(f"{FRONTEND_URL}/dashboard/grades")
        lecturer_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        lecturer_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_lecturer_grades.png"))
        flush_events_and_check(lecturer_page.url, "Lecturer Grades Page")

        lecturer_context.close()

        # ====================================================================
        # PHASE 4: ADMIN ROLE FLOW
        # ====================================================================
        print("\n>>> PHASE 4: ADMIN ROLE FLOW AUDIT <<<")
        admin_context = browser.new_context(
            viewport={"width": 1440, "height": 900},
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0"
        )
        admin_page = admin_context.new_page()
        admin_page.on("console", lambda msg: console_logs.append({"type": msg.type, "text": msg.text, "location": msg.location}))
        admin_page.on("pageerror", lambda err: page_errors.append(str(err)))
        admin_page.on("response", lambda res: (
            bad_responses.append({"url": res.url, "status": res.status, "status_text": res.status_text})
            if res.status >= 400 and "/api/v1/auth/login" not in res.url else None
        ))

        admin_page.goto(f"{FRONTEND_URL}/login?portal=admin")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(1)

        admin_page.locator('input[type="email"]').fill("admin@campuscore.edu")
        admin_page.locator('input[type="password"]').fill("admin123")
        admin_page.locator('button:has-text("Đăng nhập")').click()
        admin_page.wait_for_url("**/admin**", timeout=10000)
        time.sleep(3)
        print("Logged in as Admin.")
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_dashboard.png"))
        flush_events_and_check(admin_page.url, "Admin Dashboard Load")

        # 4.1 Admin Editor
        print("Checking Admin Editor (/admin/editor)...")
        admin_page.goto(f"{FRONTEND_URL}/admin/editor")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(3)
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_editor.png"))
        flush_events_and_check(admin_page.url, "Admin Editor Page")

        # 4.2 Admin Announcements Management
        print("Checking Admin Announcements (/admin/announcements)...")
        admin_page.goto(f"{FRONTEND_URL}/admin/announcements")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_announcements.png"))
        flush_events_and_check(admin_page.url, "Admin Announcements Page")

        # 4.3 Admin Users Management
        print("Checking Admin Users (/admin/users)...")
        admin_page.goto(f"{FRONTEND_URL}/admin/users")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_users.png"))
        flush_events_and_check(admin_page.url, "Admin Users Page")

        # 4.4 Admin Courses Management
        print("Checking Admin Courses (/admin/courses)...")
        admin_page.goto(f"{FRONTEND_URL}/admin/courses")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_courses.png"))
        flush_events_and_check(admin_page.url, "Admin Courses Page")

        # 4.5 Admin Sections Management
        print("Checking Admin Sections (/admin/sections)...")
        admin_page.goto(f"{FRONTEND_URL}/admin/sections")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_sections.png"))
        flush_events_and_check(admin_page.url, "Admin Sections Page")

        # 4.6 Admin Thesis Management
        print("Checking Admin Thesis (/admin/thesis)...")
        admin_page.goto(f"{FRONTEND_URL}/admin/thesis")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_thesis.png"))
        flush_events_and_check(admin_page.url, "Admin Thesis Page")

        # 4.7 Admin Appearance
        print("Checking Admin Appearance (/admin/appearance)...")
        admin_page.goto(f"{FRONTEND_URL}/admin/appearance")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_appearance.png"))
        flush_events_and_check(admin_page.url, "Admin Appearance Page")

        # 4.8 Admin Assistant Knowledge
        print("Checking Admin Assistant Knowledge (/admin/assistant-knowledge)...")
        admin_page.goto(f"{FRONTEND_URL}/admin/assistant-knowledge")
        admin_page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)
        admin_page.screenshot(path=os.path.join(ARTIFACT_DIR, "audit_admin_assistant_knowledge.png"))
        flush_events_and_check(admin_page.url, "Admin Assistant Knowledge Page")

        admin_context.close()

        browser.close()

    print("\n================================================================")
    print("AUDIT COMPLETE - SUMMARY OF FINDINGS:")
    print(f"Total findings: {len(findings)}")
    severities = {"critical": 0, "high": 0, "medium": 0, "low": 0}
    for f in findings:
        sev = f["severity"].lower()
        severities[sev] = severities.get(sev, 0) + 1

    print(f"  Critical: {severities['critical']}")
    print(f"  High:     {severities['high']}")
    print(f"  Medium:   {severities['medium']}")
    print(f"  Low:      {severities['low']}")
    print("================================================================\n")

    report_path = os.path.join(ARTIFACT_DIR, "browser_qa_audit_findings.json")
    with open(report_path, "w", encoding="utf-8") as f:
        json.dump(findings, f, ensure_ascii=False, indent=2)
    print(f"Saved full findings report to: {report_path}")

if __name__ == "__main__":
    run_deep_qa_audit()
