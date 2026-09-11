import os
import sys
import time
from playwright.sync_api import sync_playwright

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

ARTIFACT_DIR = r"C:\Users\Admin\.gemini\antigravity\brain\32baa29e-0b4a-488d-8978-7fca7b16587f"
FRONTEND_URL = "http://127.0.0.1:3100"

def test_login_rate_limiting_ui():
    print("Starting Playwright UI Verification for Rate Limiting...")
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 960})
        page = context.new_page()

        page.goto(f"{FRONTEND_URL}/login")
        page.wait_for_selector('input#email', timeout=15000)

        # Trigger 6 rapid incorrect logins to trip the 5 req/min rate limit
        print("Submitting rapid login attempts...")
        for i in range(1, 7):
            page.fill('input#email', f"test-hacker-{i}@campuscore.edu")
            page.fill('input#password', 'wrongpass')
            page.click('button[type="submit"]')
            time.sleep(0.4)

        time.sleep(1.5)
        shot_path = os.path.join(ARTIFACT_DIR, "verified_rate_limiting_ui.png")
        page.screenshot(path=shot_path)
        print(f"Captured Rate Limiting UI Screenshot: {shot_path}")

        browser.close()
        print("Playwright UI test finished.")

if __name__ == '__main__':
    test_login_rate_limiting_ui()
