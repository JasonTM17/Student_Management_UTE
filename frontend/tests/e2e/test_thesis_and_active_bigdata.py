"""
CampusCore — E2E verification for plan 20260911
(Active Students Big Data + Thesis Lifecycle End-to-End).

Hardened per Kongming continuation review (2026-09-12):
  * artifacts are written INSIDE the repository (previously an external stale dir);
  * every step hard-fails when its element is absent (no silent `if is_visible()` skips);
  * the enrollments check asserts the exact active-course count text ("5 môn"),
    which is the real success signal, instead of merely "Đang học" (visible even at 0 môn);
  * supervisor / council report links and the student result card are asserted.

Run:
  <venv>/python.exe frontend/tests/e2e/test_thesis_and_active_bigdata.py
"""
import os
import sys
import time

if sys.platform == "win32":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")

from playwright.sync_api import sync_playwright

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..", ".."))
ARTIFACT_DIR = os.path.join(
    REPO_ROOT, "plans", "20260911-active-students-bigdata-and-thesis-lifecycle", "assets", "e2e"
)
FRONTEND_URL = "http://127.0.0.1:3100"
SWAGGER_URL = "http://127.0.0.1:4010/swagger-ui/index.html"

os.makedirs(ARTIFACT_DIR, exist_ok=True)

CHECKS = []


def check(name, ok, detail=""):
    CHECKS.append((name, ok, detail))
    status = "PASS" if ok else "FAIL"
    print(f"  [{status}] {name}" + (f" — {detail}" if detail else ""))
    return ok


def login(page, email, password, portal=None):
    url = f"{FRONTEND_URL}/login" + (f"?portal={portal}" if portal else "")
    page.goto(url)
    page.wait_for_selector("input#email", timeout=20000)
    page.fill("input#email", email)
    page.fill("input#password", password)
    page.click('button[type="submit"]')
    page.wait_for_url("**/dashboard**", timeout=20000)


def select_round(page, needle):
    """Select the round option whose label contains `needle`; return False if absent."""
    select_el = page.locator("select").first
    select_el.wait_for(state="visible", timeout=20000)
    for opt in select_el.locator("option").all():
        if needle in opt.inner_text():
            select_el.select_option(opt.get_attribute("value"))
            page.wait_for_load_state("networkidle", timeout=20000)
            time.sleep(2)
            return True
    return False


def run_tests():
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 900})
        page = context.new_page()

        # ------------------------------------------------------------------ STEP 1
        print("\n=== STEP 1: Student active courses big data (/dashboard/enrollments) ===")
        login(page, "student@campuscore.edu", "password123")
        page.goto(f"{FRONTEND_URL}/dashboard/enrollments")
        page.wait_for_selector("text=Chương trình đào tạo (CTĐT)", timeout=20000)
        time.sleep(3)

        body = page.inner_text("body")
        check("enrollments page reachable", "Chương trình đào tạo" in body)
        check(
            "active-course count shows exactly '5 môn'",
            "5 môn" in body,
            "found '5 môn'" if "5 môn" in body else "MISSING '5 môn' — active count is wrong",
        )
        check("active filter chip 'Đang học (5)' present", "Đang học (5)" in body)
        shot = os.path.join(ARTIFACT_DIR, "01_student_active_courses.png")
        page.screenshot(path=shot, full_page=True)
        print(f"  screenshot -> {shot}")

        # ------------------------------------------------------------------ STEP 2
        print("\n=== STEP 2: Student thesis workspace — group report submission ===")
        page.goto(f"{FRONTEND_URL}/dashboard/thesis")
        page.wait_for_load_state("networkidle", timeout=20000)
        time.sleep(2)
        check("round 'Đồ án tốt nghiệp 2026-2027' selectable",
              select_round(page, "Đồ án tốt nghiệp"))

        report_btn = page.locator(
            'button:text-is("Nộp báo cáo"), button:text-is("Cập nhật báo cáo")'
        ).first
        has_btn = report_btn.count() > 0
        check("group leader sees report submit/update button", has_btn)
        if has_btn:
            report_btn.click()
            time.sleep(1)
            title_input = page.locator("input#thesis-report-title")
            url_input = page.locator("input#thesis-report-url")
            note_input = page.locator("textarea#thesis-report-note")
            title_input.wait_for(state="visible", timeout=15000)
            REPORT_TITLE = "Báo cáo Khóa luận Tốt nghiệp - Hệ thống Quản trị Học vụ CampusUTE"
            REPORT_URL = "https://storage.hcmute.edu.vn/luanvan/2026/kltn-campuscore-report.pdf"
            title_input.fill(REPORT_TITLE)
            url_input.fill(REPORT_URL)
            note_input.fill("Báo cáo chính thức đợt 1 năm học 2026-2027 kèm mã nguồn và slide bảo vệ")
            page.click('form button[type="submit"]')
            time.sleep(3)
            after = page.inner_text("body")
            check("submitted report title rendered after submit", REPORT_TITLE in after)
        shot = os.path.join(ARTIFACT_DIR, "02_student_thesis_report.png")
        page.screenshot(path=shot, full_page=True)
        print(f"  screenshot -> {shot}")

        # ------------------------------------------------------------------ STEP 3
        print("\n=== STEP 3: Lecturer — supervisor report view + council grading ===")
        lctx = browser.new_context(viewport={"width": 1440, "height": 900})
        lpage = lctx.new_page()
        login(lpage, "lecturer@campuscore.edu", "password123", portal="lecturer")

        lpage.goto(f"{FRONTEND_URL}/dashboard/thesis")
        lpage.wait_for_load_state("networkidle", timeout=20000)
        time.sleep(2)
        select_round(lpage, "Đồ án tốt nghiệp")
        sup_body = lpage.inner_text("body")
        check("supervisor sees supervised group report link ('Báo cáo luận văn')",
              "Báo cáo luận văn" in sup_body)
        shot = os.path.join(ARTIFACT_DIR, "03_lecturer_supervised_report.png")
        lpage.screenshot(path=shot, full_page=True)
        print(f"  screenshot -> {shot}")

        select_round(lpage, "Audit 2")
        defense_tab = lpage.locator(
            'button:has-text("Hội Đồng Chấm Bảo Vệ"), button:has-text("Hội đồng chấm bảo vệ")'
        ).first
        if defense_tab.count() > 0:
            defense_tab.click()
            time.sleep(2)
        council_body = lpage.inner_text("body")
        check("council grading section exposes topic report link ('Tài liệu luận văn')",
              "Tài liệu luận văn" in council_body)
        shot = os.path.join(ARTIFACT_DIR, "04_council_defense_grading.png")
        lpage.screenshot(path=shot, full_page=True)
        print(f"  screenshot -> {shot}")
        lctx.close()

        # ------------------------------------------------------------------ STEP 4
        print("\n=== STEP 4: Springdoc OpenAPI Swagger UI (sanity, not acceptance evidence) ===")
        page.goto(SWAGGER_URL)
        page.wait_for_selector(".swagger-ui", timeout=20000)
        time.sleep(2)
        check("swagger-ui renders", page.locator(".swagger-ui").count() > 0)
        shot = os.path.join(ARTIFACT_DIR, "05_swagger_ui.png")
        page.screenshot(path=shot, full_page=True)
        print(f"  screenshot -> {shot}")

        browser.close()

    print("\n================ E2E RESULT SUMMARY ================")
    failed = 0
    for name, ok, detail in CHECKS:
        print(f"{'PASS' if ok else 'FAIL'}  {name}")
        if not ok:
            failed += 1
    print(f"total={len(CHECKS)} failed={failed}")
    print(f"artifacts -> {ARTIFACT_DIR}")
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    run_tests()
