# UI Testing Workflow

Use `ak:agent-browser` for live browser interaction when a fresh/tool-managed browser is enough. Use `ak:chrome-profile` only when the test needs the user's real Chrome profile, cookies, or already-logged-in state. Use `ak:web-testing` or project-native Playwright/Vitest/k6 commands for repeatable test runs.

## Purpose
Run comprehensive UI tests on a website and generate a detailed report.

## Arguments
- $1: URL - The URL of the website to test
- $2: OPTIONS - Optional test configuration (e.g., --headless, --mobile, --auth)

## Testing Protected Routes (Authentication)

### Step 1: User Manual Login
Instruct the user to:
1. Open the target site in their browser
2. Log in manually with their credentials
3. Open browser DevTools (F12) → Application tab → Cookies/Storage

### Step 2: Persist Auth State Or Select The Chrome Profile
Prefer project-native auth helpers for repeatable tests. For ad-hoc browser driving with real user auth/cookies, invoke `ak:chrome-profile` and run:

```bash
chrome-profile doctor
chrome-profile setup
chrome-profile list
```

If real user Chrome state is not needed, use `agent-browser` state commands after manual login when available.

### Step 3: Run Tests
After auth is available, run tests normally. If real user Chrome state is not needed:
```bash
agent-browser open https://example.com/dashboard
agent-browser screenshot -o profile.png
```

If real user Chrome state is needed:

```bash
chrome-profile open --json work https://example.com/dashboard
```

Then select the MCP page whose URL contains the returned `bind_selector` such as `cdp-open=<token>`, verify it also contains `cdp-profile=work`, and capture screenshots or snapshots through the active bridge.

This restriction applies only when real user Chrome state is required. For profile-scoped testing, do not use raw Chrome MCP `new_page` or `navigate_page` as the opening path. Those tools use whichever profile/page the bridge currently targets.

## Workflow
- Use `ak:plan` skill to organize the test plan & report
- All screenshots saved in the same report directory
- Browse URL, discover all pages, components, endpoints
- Create test plan based on discovered structure
- Use multiple `tester` subagents in parallel for: pages, forms, navigation, user flows, accessibility, responsive layouts, performance, security, seo
- Use `ak:ai-multimodal` to analyze all screenshots
- Generate comprehensive Markdown report
- Ask user if they want to preview with `/ak:preview`

## Output Requirements
- Clear, structured Markdown with headers, lists, code blocks
- Include test results summary, key findings, screenshot references
- Ensure token efficiency while maintaining high quality
- Sacrifice grammar for concision

**Do not** start implementing fixes.
