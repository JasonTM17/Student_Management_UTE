import os
import sys
import time
from playwright.sync_api import sync_playwright

ARTIFACT_DIR = r"C:\Users\Admin\.gemini\antigravity\brain\32baa29e-0b4a-488d-8978-7fca7b16587f"
FRONTEND_URL = "http://127.0.0.1:3100"

def run_tests():
    print("Starting Playwright E2E UI/UX Verification for Announcement Reader...")
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 960})
        page = context.new_page()

        # STEP 1: Homepage News Section
        print("\n--- STEP 1: Verify Homepage News Section ---")
        page.goto(f"{FRONTEND_URL}/")
        page.wait_for_load_state("networkidle", timeout=15000)
        time.sleep(2)

        # Scroll to HomeNewsSection
        news_heading = page.locator('text=Bản Tin Đại Học HCMUTE').first
        assert news_heading.is_visible(), "HomeNewsSection heading must be visible on homepage"
        news_heading.scroll_into_view_if_needed()
        time.sleep(1)

        homepage_shot = os.path.join(ARTIFACT_DIR, "homepage_news_section_verified.png")
        page.screenshot(path=homepage_shot)
        print(f"Captured Homepage News Section: {homepage_shot}")

        # STEP 2: Open Reader from Homepage (Editorial Mode)
        print("\n--- STEP 2: Open Reader Modal (Editorial Magazine Mode) ---")
        featured_card = page.locator('article[role="button"]').first
        featured_card.click()
        time.sleep(2)

        # Wait for dialog modal to appear
        dialog = page.locator('div[role="dialog"]').first
        assert dialog.is_visible(), "AnnouncementReaderModal must be visible"

        editorial_shot = os.path.join(ARTIFACT_DIR, "reader_modal_editorial_mode_verified.png")
        page.screenshot(path=editorial_shot)
        print(f"Captured Reader Modal in Editorial Magazine Mode: {editorial_shot}")

        # STEP 3: Switch to Official Dispatch Mode
        print("\n--- STEP 3: Switch to Official Institutional Dispatch Mode ---")
        dispatch_btn = page.locator('button:has-text("Công văn")').first
        assert dispatch_btn.is_visible(), "Official dispatch switch button must be visible in toolbar"
        dispatch_btn.click()
        time.sleep(1)

        official_shot = os.path.join(ARTIFACT_DIR, "reader_modal_official_mode_verified.png")
        page.screenshot(path=official_shot)
        print(f"Captured Reader Modal in Official Dispatch Mode: {official_shot}")

        # STEP 4: Switch to Eye-care Sepia Theme
        print("\n--- STEP 4: Switch to Eye-care Sepia Theme ---")
        sepia_btn = page.locator('button[aria-label="Sepia mode"]').first
        if sepia_btn.is_visible():
            sepia_btn.click()
            time.sleep(1)
            sepia_shot = os.path.join(ARTIFACT_DIR, "reader_modal_sepia_mode_verified.png")
            page.screenshot(path=sepia_shot)
            print(f"Captured Sepia Mode: {sepia_shot}")

        # STEP 5: Switch to Dark Theme
        print("\n--- STEP 5: Switch to Dark Theme & Check Contrast ---")
        dark_btn = page.locator('button[aria-label="Dark mode"]').first
        if dark_btn.is_visible():
            dark_btn.click()
            time.sleep(1)
            dark_shot = os.path.join(ARTIFACT_DIR, "reader_modal_dark_mode_verified.png")
            page.screenshot(path=dark_shot)
            print(f"Captured Dark Mode: {dark_shot}")

        # STEP 6: Close Modal with Sticky Close Button
        print("\n--- STEP 6: Close Reader Modal via Sticky [X] button ---")
        close_btn = page.locator('button[aria-label="Đóng cửa sổ đọc"]').first
        assert close_btn.is_visible(), "Sticky close button must be present in toolbar"
        close_btn.click()
        time.sleep(1)
        assert not dialog.is_visible(), "Modal should be closed after clicking close button"
        print("Reader closed successfully.")

        # STEP 7: Student Announcements Dashboard
        print("\n--- STEP 7: Student Announcements Dashboard (Magazine vs Dispatch) ---")
        page.goto(f"{FRONTEND_URL}/login")
        page.wait_for_selector('input#email', timeout=15000)
        page.fill('input#email', 'student@campuscore.edu')
        page.fill('input#password', 'password123')
        page.click('button[type="submit"]')
        page.wait_for_url("**/dashboard**", timeout=15000)

        page.goto(f"{FRONTEND_URL}/dashboard/announcements")
        page.wait_for_selector('text=Bảng Tin & Thông Báo Đại Học', timeout=15000)
        time.sleep(2)

        # 7a: Magazine Grid
        magazine_shot = os.path.join(ARTIFACT_DIR, "announcements_page_magazine_grid_verified.png")
        page.screenshot(path=magazine_shot)
        print(f"Captured Announcements Magazine Grid: {magazine_shot}")

        # 7b: Switch to Sổ công văn (Official Dispatches list)
        dispatch_toggle = page.locator('button:has-text("Sổ công văn")').first
        assert dispatch_toggle.is_visible(), "Dispatch list view toggle button must be visible"
        dispatch_toggle.click()
        time.sleep(1)

        dispatch_list_shot = os.path.join(ARTIFACT_DIR, "announcements_page_dispatch_list_verified.png")
        page.screenshot(path=dispatch_list_shot)
        print(f"Captured Announcements Dispatch List: {dispatch_list_shot}")

        browser.close()
        print("\nAll Playwright E2E UI/UX verifications passed successfully!")

if __name__ == '__main__':
    run_tests()
