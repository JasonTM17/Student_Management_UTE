import os
import sys
import time

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

from playwright.sync_api import sync_playwright

ARTIFACT_DIR = r"C:\Users\Admin\.gemini\antigravity\brain\32baa29e-0b4a-488d-8978-7fca7b16587f"
FRONTEND_URL = "http://127.0.0.1:3100"

def wait_for_assistant_reply(page, min_messages=2, timeout=20000):
    """Wait until the assistant has finished replying (thinking indicator disappears)"""
    start_time = time.time()
    time.sleep(1.5)
    while time.time() - start_time < (timeout / 1000):
        thinking = page.locator('text=Đang kiểm tra, text=Đang suy nghĩ').first
        if not thinking.is_visible():
            messages = page.locator('[role="log"] > div, [role="log"] article')
            if messages.count() >= min_messages:
                time.sleep(1.5)
                return True
        time.sleep(0.5)
    return False

def run_tests():
    print("Starting AI Chatbot Assistant Personalization Verification...")
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        context = browser.new_context(viewport={"width": 1440, "height": 960})
        page = context.new_page()

        # ==========================================
        # PART 1: STUDENT CHATBOT PERSONALIZATION
        # ==========================================
        print("\n--- [PART 1] Student Assistant Verification ---")
        page.goto(f"{FRONTEND_URL}/login")
        page.wait_for_selector('input#email', timeout=15000)
        page.fill('input#email', 'student@campuscore.edu')
        page.fill('input#password', 'password123')
        page.click('button[type="submit"]')
        page.wait_for_url("**/dashboard**", timeout=15000)
        time.sleep(2)

        # Open Assistant Panel
        print("Opening Assistant Panel...")
        assistant_btn = page.locator('button:has-text("Trợ lý"), button[title*="Trợ lý"], button[aria-label*="Trợ lý"]').first
        if assistant_btn.is_visible():
            assistant_btn.click()
        else:
            page.evaluate("() => window.dispatchEvent(new CustomEvent('open-campus-assistant'))")
        time.sleep(1.5)

        panel = page.locator('section[role="dialog"][aria-labelledby="assistant-panel-title"]').first
        assert panel.is_visible(), "Assistant dialog panel must be open"
        print("Assistant dialog opened successfully.")

        # Query 1: Schedule / Thời khóa biểu
        print("\nTesting Query 1: Lịch học...")
        input_box = page.locator('section[role="dialog"] textarea').first
        input_box.fill("Thời khóa biểu tuần này của tôi")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=2)
        
        shot1 = os.path.join(ARTIFACT_DIR, "verified_chatbot_schedule.png")
        page.screenshot(path=shot1)
        print(f"Captured Schedule query reply: {shot1}")

        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Schedule reply preview:\n{last_msg[-300:]}\n---")

        # Query 2: Conduct Points (ĐRL)
        print("\nTesting Query 2: Điểm rèn luyện (ĐRL)...")
        input_box.fill("Điểm rèn luyện học kỳ này của tôi là bao nhiêu?")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=4)

        shot2 = os.path.join(ARTIFACT_DIR, "verified_chatbot_conduct.png")
        page.screenshot(path=shot2)
        print(f"Captured Conduct query reply: {shot2}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Conduct reply preview:\n{last_msg[-300:]}\n---")

        # Query 3: Grades & GPA
        print("\nTesting Query 3: Điểm GPA tích lũy...")
        input_box.fill("Điểm trung bình tích lũy GPA của tôi là mấy?")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=6)

        shot3 = os.path.join(ARTIFACT_DIR, "verified_chatbot_grades.png")
        page.screenshot(path=shot3)
        print(f"Captured Grades query reply: {shot3}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Grades reply preview:\n{last_msg[-300:]}\n---")

        # Query 4: Course Registration Cap
        print("\nTesting Query 4: Tín chỉ đăng ký tối đa...")
        input_box.fill("Tôi được đăng ký tối đa bao nhiêu tín chỉ học kỳ này?")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=8)

        shot4 = os.path.join(ARTIFACT_DIR, "verified_chatbot_registration.png")
        page.screenshot(path=shot4)
        print(f"Captured Registration Cap reply: {shot4}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Registration reply preview:\n{last_msg[-300:]}\n---")

        # Query 5: Graduation Thesis / Capstone
        print("\nTesting Query 5: Đồ án tốt nghiệp...")
        input_box.fill("Thông tin đồ án tốt nghiệp của tôi")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=10)

        shot5 = os.path.join(ARTIFACT_DIR, "verified_chatbot_thesis.png")
        page.screenshot(path=shot5)
        print(f"Captured Thesis reply: {shot5}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Thesis reply preview:\n{last_msg[-300:]}\n---")

        # Query 6: Tuition Payment
        print("\nTesting Query 6: Học phí...")
        input_box.fill("Học phí học kỳ này nộp như thế nào?")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=12)

        shot6 = os.path.join(ARTIFACT_DIR, "verified_chatbot_tuition.png")
        page.screenshot(path=shot6)
        print(f"Captured Tuition reply: {shot6}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Tuition reply preview:\n{last_msg[-300:]}\n---")

        # Query 7: Graduation Requirements
        print("\nTesting Query 7: Điều kiện tốt nghiệp...")
        input_box.fill("Điều kiện tốt nghiệp của tôi như thế nào?")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=14)

        shot_grad = os.path.join(ARTIFACT_DIR, "verified_chatbot_graduation.png")
        page.screenshot(path=shot_grad)
        print(f"Captured Graduation reply: {shot_grad}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Graduation reply preview:\n{last_msg[-300:]}\n---")

        # Query 8: Scholarship Eligibility
        print("\nTesting Query 8: Học bổng khuyến khích...")
        input_box.fill("Tôi có đủ điều kiện nhận học bổng không?")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=16)

        shot_schol = os.path.join(ARTIFACT_DIR, "verified_chatbot_scholarship.png")
        page.screenshot(path=shot_schol)
        print(f"Captured Scholarship reply: {shot_schol}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Scholarship reply preview:\n{last_msg[-300:]}\n---")

        # Query 9: Profile & MSSV
        print("\nTesting Query 9: Thông tin cá nhân & MSSV...")
        input_box.fill("Thông tin cá nhân và MSSV của tôi")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=18)

        shot_prof = os.path.join(ARTIFACT_DIR, "verified_chatbot_profile.png")
        page.screenshot(path=shot_prof)
        print(f"Captured Profile reply: {shot_prof}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Profile reply preview:\n{last_msg[-300:]}\n---")

        # ==========================================
        # PART 2: LECTURER CHATBOT PERSONALIZATION
        # ==========================================
        print("\n--- [PART 2] Lecturer Assistant Verification ---")
        context.clear_cookies()
        page.goto(f"{FRONTEND_URL}/login?portal=lecturer")
        page.wait_for_selector('input#email', timeout=15000)
        page.fill('input#email', 'lecturer@campuscore.edu')
        page.fill('input#password', 'password123')
        page.click('button[type="submit"]')
        page.wait_for_url("**/dashboard**", timeout=15000)
        time.sleep(2)

        # Open Assistant Panel
        print("Opening Assistant Panel for Lecturer...")
        assistant_btn = page.locator('button:has-text("Trợ lý"), button[title*="Trợ lý"], button[aria-label*="Trợ lý"]').first
        if assistant_btn.is_visible():
            assistant_btn.click()
        else:
            page.evaluate("() => window.dispatchEvent(new CustomEvent('open-campus-assistant'))")
        time.sleep(1.5)

        input_box = page.locator('section[role="dialog"] textarea').first
        input_box.fill("Lịch giảng dạy tuần này của tôi")
        page.keyboard.press("Enter")
        time.sleep(2)
        wait_for_assistant_reply(page, min_messages=2)

        shot7 = os.path.join(ARTIFACT_DIR, "verified_chatbot_lecturer_schedule.png")
        page.screenshot(path=shot7)
        print(f"Captured Lecturer Schedule query reply: {shot7}")
        last_msg = page.locator('[role="log"]').first.inner_text()
        print(f"Lecturer Schedule reply preview:\n{last_msg[-300:]}\n---")

        browser.close()
        print("\nAll Chatbot Personalization tests completed successfully!")

if __name__ == '__main__':
    run_tests()
