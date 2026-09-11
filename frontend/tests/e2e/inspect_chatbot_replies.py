import os
import sys
import json
import time

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

from playwright.sync_api import sync_playwright

FRONTEND_URL = "http://127.0.0.1:3100"

def get_last_assistant_message(page):
    time.sleep(2)
    messages = page.locator('[role="log"] [role="article"]')
    count = messages.count()
    if count == 0:
        return ""
    # Find the last article that is assistant
    for i in range(count - 1, -1, -1):
        msg = messages.nth(i)
        aria = msg.get_attribute("aria-label") or ""
        if "Trợ lý" in aria or "Assistant" in aria:
            return msg.inner_text()
    return messages.last.inner_text()

def run_inspection():
    with sync_playwright() as p:
        browser = p.chromium.launch(channel="msedge", headless=True)
        
        # 1. STUDENT
        print("=== [1] INSPECTING STUDENT QUERIES ===")
        context = browser.new_context(viewport={"width": 1440, "height": 960})
        page = context.new_page()
        page.goto(f"{FRONTEND_URL}/login")
        page.wait_for_selector('input#email', timeout=15000)
        page.fill('input#email', 'student@campuscore.edu')
        page.fill('input#password', 'password123')
        page.click('button[type="submit"]')
        page.wait_for_url("**/dashboard**", timeout=15000)
        time.sleep(2)

        page.evaluate("() => window.dispatchEvent(new CustomEvent('open-campus-assistant'))")
        time.sleep(1.5)

        student_queries = [
            ("Thời khóa biểu hôm nay và tuần này", "Thời khóa biểu tuần này của tôi"),
            ("Lịch học thứ 3", "Thứ 3 tôi học môn gì và ở phòng nào?"),
            ("Điểm rèn luyện", "Điểm rèn luyện học kỳ này của tôi là bao nhiêu?"),
            ("Điểm GPA tích lũy", "Điểm trung bình tích lũy GPA của tôi là mấy?"),
            ("Đăng ký tín chỉ", "Tôi được đăng ký tối đa bao nhiêu tín chỉ học kỳ này?"),
            ("Đồ án tốt nghiệp", "Thông tin đồ án tốt nghiệp của tôi"),
            ("Học phí", "Học phí học kỳ này nộp như thế nào?"),
            ("Thông báo", "Có thông báo học vụ gì mới không?"),
            ("Tiến độ đào tạo", "Chương trình đào tạo của tôi còn nợ bao nhiêu tín chỉ?"),
            ("Thông tin cá nhân / MSSV", "Mã số sinh viên và thông tin cá nhân của tôi"),
        ]

        for label, q in student_queries:
            input_box = page.locator('section[role="dialog"] textarea').first
            input_box.fill(q)
            page.keyboard.press("Enter")
            time.sleep(2.5)
            # Wait for thinking indicator to disappear
            for _ in range(20):
                if not page.locator('text=Đang kiểm tra').is_visible() and not page.locator('text=Đang suy nghĩ').is_visible():
                    break
                time.sleep(0.5)
            ans = get_last_assistant_message(page)
            print(f"\n--- QUERY: [{label}] -> \"{q}\" ---")
            print(ans)

        # 2. LECTURER
        print("\n=== [2] INSPECTING LECTURER QUERIES ===")
        context2 = browser.new_context(viewport={"width": 1440, "height": 960})
        page2 = context2.new_page()
        page2.goto(f"{FRONTEND_URL}/login?portal=lecturer")
        page2.wait_for_selector('input#email', timeout=15000)
        page2.fill('input#email', 'lecturer@campuscore.edu')
        page2.fill('input#password', 'password123')
        page2.click('button[type="submit"]')
        page2.wait_for_url("**/dashboard**", timeout=15000)
        time.sleep(2)

        page2.evaluate("() => window.dispatchEvent(new CustomEvent('open-campus-assistant'))")
        time.sleep(1.5)

        lecturer_queries = [
            ("Lịch dạy giảng viên", "Lịch giảng dạy tuần này của tôi"),
            ("Danh sách lớp phụ trách", "Các lớp học phần tôi đang phụ trách giảng dạy"),
            ("Thông tin giảng viên", "Thông tin giảng viên của tôi"),
        ]

        for label, q in lecturer_queries:
            input_box = page2.locator('section[role="dialog"] textarea').first
            input_box.fill(q)
            page2.keyboard.press("Enter")
            time.sleep(2.5)
            for _ in range(20):
                if not page2.locator('text=Đang kiểm tra').is_visible() and not page2.locator('text=Đang suy nghĩ').is_visible():
                    break
                time.sleep(0.5)
            ans = get_last_assistant_message(page2)
            print(f"\n--- LECTURER QUERY: [{label}] -> \"{q}\" ---")
            print(ans)

        browser.close()

if __name__ == '__main__':
    run_inspection()
