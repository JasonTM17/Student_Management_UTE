import json
import sys
import time
import requests

if sys.platform == 'win32':
    sys.stdout.reconfigure(encoding='utf-8')
    sys.stderr.reconfigure(encoding='utf-8')

DIRECT_API_URL = "http://127.0.0.1:4010/api/v1"
WEB_PROXY_URL = "http://127.0.0.1:3100/api/v1"

def test_login_rate_limiting():
    print("\n=== [1] Testing Login Rate Limiting (Limit: 5 req/min) ===")
    url = f"{DIRECT_API_URL}/auth/login"
    payload = {"email": "ratelimit-test@campuscore.edu", "password": "wrongpassword"}
    headers = {
        "Content-Type": "application/json",
        "X-Forwarded-For": f"192.0.2.{int(time.time() * 1000) % 250 + 1}"
    }

    for i in range(1, 6):
        res = requests.post(url, json=payload, headers=headers)
        limit = res.headers.get("X-RateLimit-Limit")
        remaining = res.headers.get("X-RateLimit-Remaining")
        reset = res.headers.get("X-RateLimit-Reset")
        print(f"Request {i}: Status={res.status_code}, Limit={limit}, Remaining={remaining}, Reset={reset}")
        assert res.status_code in [400, 401], f"Request {i} expected 401 or 400, got {res.status_code}"
        assert limit == "5", f"Limit expected 5, got {limit}"

    # 6th request must be blocked with 429
    res_blocked = requests.post(url, json=payload, headers=headers)
    print(f"\nRequest 6 (Exceeded): Status={res_blocked.status_code}")
    print(f"Headers: Retry-After={res_blocked.headers.get('Retry-After')}, X-RateLimit-Remaining={res_blocked.headers.get('X-RateLimit-Remaining')}")
    print(f"Response Body: {res_blocked.text}")

    assert res_blocked.status_code == 429, f"Expected 429 Too Many Requests, got {res_blocked.status_code}"
    assert res_blocked.headers.get("Retry-After") is not None, "Retry-After header must be present on 429"
    assert "RATE_LIMIT_EXCEEDED" in res_blocked.text, "Response body must contain RATE_LIMIT_EXCEEDED"
    print(">>> PASS: Login Rate Limiting strictly enforces 5 req/min and returns HTTP 429!")

def test_get_requests_unthrottled():
    print("\n=== [2] Testing GET Requests Are Unthrottled ===")
    url = f"{DIRECT_API_URL}/contract"
    for i in range(1, 15):
        res = requests.get(url)
        assert res.status_code == 200, f"GET request {i} expected 200, got {res.status_code}"
    print(">>> PASS: 14 consecutive GET requests passed with 200 OK without throttling!")

def test_registration_rate_limiting():
    print("\n=== [3] Testing Registration Rate Limiting (Limit: 3 req/min) ===")
    # Using a distinct simulated IP via X-Forwarded-For to test IP isolation
    url = f"{DIRECT_API_URL}/auth/register"
    custom_headers = {
        "Content-Type": "application/json",
        "X-Forwarded-For": f"198.51.100.{int(time.time() * 1000 + 7) % 250 + 1}"
    }
    payload = {
        "email": "bad-reg@campuscore.edu",
        "password": "short",
        "firstName": "Test",
        "lastName": "User"
    }

    for i in range(1, 4):
        res = requests.post(url, json=payload, headers=custom_headers)
        limit = res.headers.get("X-RateLimit-Limit")
        remaining = res.headers.get("X-RateLimit-Remaining")
        print(f"Registration Request {i}: Status={res.status_code}, Limit={limit}, Remaining={remaining}")
        assert limit == "3", f"Limit expected 3, got {limit}"

    # 4th request must be blocked
    res_blocked = requests.post(url, json=payload, headers=custom_headers)
    print(f"Registration Request 4 (Exceeded): Status={res_blocked.status_code}, Body={res_blocked.text}")
    assert res_blocked.status_code == 429, f"Expected 429, got {res_blocked.status_code}"
    print(">>> PASS: Registration Rate Limiting strictly enforces 3 req/min!")

def test_nextjs_proxy_rate_limit_headers():
    print("\n=== [4] Testing Next.js Proxy Forwarding 429 & Rate Limit Headers ===")
    # Using a distinct simulated IP
    url = f"{WEB_PROXY_URL}/auth/login"
    custom_headers = {
        "Content-Type": "application/json",
        "X-Forwarded-For": f"203.0.113.{int(time.time() * 1000 + 42) % 250 + 1}"
    }
    payload = {"email": "proxy-test@campuscore.edu", "password": "wrongpassword"}

    for i in range(1, 6):
        res = requests.post(url, json=payload, headers=custom_headers)
        print(f"Proxy Request {i}: Status={res.status_code}, Remaining={res.headers.get('X-RateLimit-Remaining')}")

    res_blocked = requests.post(url, json=payload, headers=custom_headers)
    print(f"Proxy Request 6 (Blocked): Status={res_blocked.status_code}")
    print(f"Forwarded Retry-After: {res_blocked.headers.get('Retry-After')}")
    print(f"Forwarded Body: {res_blocked.text}")

    assert res_blocked.status_code == 429, f"Expected 429 through Next.js proxy, got {res_blocked.status_code}"
    assert res_blocked.headers.get("Retry-After") is not None, "Retry-After header must be forwarded through proxy"
    print(">>> PASS: Next.js API proxy properly forwards HTTP 429 and rate limit headers!")

if __name__ == '__main__':
    test_login_rate_limiting()
    test_get_requests_unthrottled()
    test_registration_rate_limiting()
    test_nextjs_proxy_rate_limit_headers()
    print("\n=======================================================")
    print("ALL LIVE RATE LIMITING TESTS PASSED SUCCESSFULLY 100%!")
    print("=======================================================")
