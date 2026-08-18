---
name: ak:show-off
description: "Create preference-aware self-contained HTML pages to showcase work. Use for demos, visual presentations, interactive showcases."
user-invocable: true
when_to_use: "Invoke to create a self-contained showcase or demo page."
category: other
keywords: [HTML, showcase, demo, presentation]
argument-hint: "[markdown-or-prompt]"
license: Complete terms in LICENSE.txt
metadata:
  author: agentkit
  version: "1.0.0"
---

ultrathink
Activate `ak:frontend-design` skill to create a showcase HTML presentation for the following request:

## REQUEST / MISSION:
$ARGUMENTS

## PURPOSE:
Showcase, social media posting, and optional output images for articles.

## PERSISTED PREFERENCES (MANDATORY - run BEFORE project-management)

`show-off` has user-level workflow preferences. Defaults preserve legacy behavior:

```json
{
  "screenshots": true,
  "publishing": true,
  "languages": ["vi", "en"]
}
```

Before reading/analyzing the mission content, resolve preferences:

```bash
PREF_SCRIPT="scripts/preferences.js"
node "$PREF_SCRIPT" get
```

The helper stores preferences at `$AGENTKIT_HOME/show-off/preferences.json`, or
`~/.agentkit/show-off/preferences.json` when `AGENTKIT_HOME` is unset.
`SHOW_OFF_PREFS_PATH` may override the path for tests or one-off advanced use.

Only parse workflow-control intent before project-management:
- Screenshot capture: phrases like "no screenshots", "skip screenshots", "turn off screenshots", or `--no-screenshots`.
- Publishing: phrases like "no publish", "skip publishing", "local only", "do not publish", or `--no-publish`.
- Language mode: phrases like "English only", "Vietnamese only", "disable dual language", "no bilingual", or `--languages en`.
- Reset: phrases like "reset show-off preferences" or "use defaults again".
- One-time override: if the user says "for this run only", apply it for this invocation but do not persist it.

If the user explicitly changes one of these settings and does not say it is only for this run, persist it immediately:

```bash
# Examples
node "$PREF_SCRIPT" set --no-screenshots
node "$PREF_SCRIPT" set --no-publish
node "$PREF_SCRIPT" set --languages en
node "$PREF_SCRIPT" set --screenshots on --publishing on --languages vi,en
node "$PREF_SCRIPT" reset
```

Use the resolved preferences for the current run. The latest explicit user instruction
wins over stored preferences. Do not ask the user to repeat a persisted opt-out.

## PREREQUISITE (MANDATORY - run BEFORE content workflow)

After resolving preferences, invoke `/ak:project-management` **before** reading/analyzing the request content or doing any content workflow work. This skill owns plan/task lifecycle; `show-off` is a consumer.

Purpose:
- Create a dated plan directory under `plans/` (naming from hook injection: `{date}-{issue}-{slug}`).
- Register the resolved checklist below as trackable tasks:
  - Always: request-analysis, content, HTML, local open/review.
  - If `screenshots=true`: capture.
  - If `publishing=true`: publish content/static site.
- Set the active plan context so downstream skills (`frontend-design`, `agent-browser`, capture script) share the same plan folder and assets root.
- Record the invocation arguments and resolved preferences (`screenshots`, `publishing`, `languages`) in `plan.md`.

Hard gate: do NOT proceed to the DETAILED INSTRUCTIONS below until the plan directory exists and the checklist is registered. If `project-management` returns `BLOCKED` / `NEEDS_CONTEXT`, resolve it first.

## DETAILED INSTRUCTIONS
Follow these steps strictly in order, one by one:
- Read and analyze the request carefully, split into topics/sections (minimum 2, maximum 6, including hero section).
- Update the registered tasks in the active plan as each step starts/completes (via `project-management`).
- Search the internet for supporting evidence or fact-checking information in the request/mission.
- Write showcase content as markdown at `assets/showoff/<mission-name>/content.md` with all content organized by sections/topics.
  **NOTE:**
  - Check if one of these files existed:
    [
      `/Volumes/GOON/www/assets/writing-styles/`,
      `~/www/writing-styles/`,
      `~/.agentkit/writing-styles/`,
      `~/writing-styles/`
    ]
    -> Read it to use writing style (if none of them exists, just skip).
  - Attach citation URLs in references/footnotes at end of file.
- If `publishing=true`, use `agentwiki` CLI to publish this document (organize or create appropriate folder).
  If `publishing=false`, keep the document local and mark the publish task skipped.
- Activate `ak:frontend-design` skill to create a stunning HTML file:
  - Include visual diagrams/illustrations
  - Include decorative elements (optional)
  - Micro-animation or subtle animation (optional)
  - Attach citation URLs in references/footnotes at bottom of page
- First section (hero section): always an impressive, eye-catching, glamorous design that hooks and entices into subsequent sections.
- Layout organized into multiple sections corresponding to request topics -> user scrolls smoothly top-to-bottom with parallax effects.
  Remember id/class names of each section for screenshot capture later.
- Content MUST use the resolved language preference:
  - `["vi", "en"]`: provide Vietnamese and English content with a clear language toggle or parallel bilingual treatment.
  - `["en"]`: English only. Do not add Vietnamese copy or a language toggle.
  - `["vi"]`: Vietnamese only. Do not add English copy or a language toggle.
- If `screenshots=false`, skip screenshot capture entirely. Do not run the local capture script or `rws`; mark the capture task skipped and report the local HTML path.
- If `screenshots=true`, capture each section as images (JPG/PNG) at `assets/showoff/<mission-name>/images/` with ratio-based prefix (`horizontal`, `vertical`, `square`).
  **NOTE:** The capture script now auto-waits for fonts, `<img>` completion, and CSS background-image loading before each shot. `--settle-delay` adds an extra cushion for animations / lazy reveals.
  **IMPORTANT:** Use the parallel capture script for efficiency:
  ```bash
  node scripts/capture-sections.js \
    --url "file:///path/to/index.html" \
    --output-dir "assets/showoff/<mission-name>/images" \
    --sections "#hero,#section-2,#section-3" \
    --ratios "horizontal,vertical,square" \
    --settle-delay 1500
  ```
  **FALLBACK - `rws` CLI**: if `publishing=true` and the local script fails (puppeteer missing, headless Chrome unavailable, sandbox error, script exit non-zero) AND the `rws` command is on PATH AND `$RWEB_API_KEY` is set, fall back to the ReviewWeb screenshot API. The HTML must be publicly reachable (publish via `agentwiki` first, then use the public URL + `#section-id` anchors).

  Detection:
  ```bash
  command -v rws >/dev/null && [ -n "$RWEB_API_KEY" ] && echo "rws fallback available"
  ```

  Per (section, ratio) capture loop:
  ```bash
  # Viewports: horizontal=1920x1080, vertical=1080x1920, square=1080x1080
  rws screenshot \
    --url "https://public-host/mission/#hero" \
    --width 1920 --height 1080 \
    --delay 1500 \
    --format json \
    | jq -r '.imageUrl' \
    | xargs -I{} curl -sSL {} -o "assets/showoff/<mission-name>/images/horizontal-hero.png"
  ```

  Fallback rules:
  - Run per (section, ratio) combo; parallelise with `xargs -P` or shell `&`.
  - `--delay` passes the same settle-delay value used by the local script.
  - Skip `rws` fallback whenever `publishing=false`; the user explicitly chose a local-only flow.
  - Skip `rws` fallback if the HTML is only reachable via `file://` and cannot be published yet — in that case, surface the local script error to the user and stop.
  - Never pass `$RWEB_API_KEY` on the command line; rely on the env var resolution (`rws` reads it automatically).
  - On `rws` exit code 2 (auth error) or missing `$RWEB_API_KEY`, stop and report — do not silently skip capture.
- If `publishing=true`, use `agentwiki` CLI to publish/update this static site when complete.
  If `publishing=false`, do not publish and report the local artifact path instead.
- Use `open` CLI (or equivalent) to open the resulting HTML page.

## OUTPUT REQUIREMENTS
- Each section's components MUST fit within browser viewport
- Support responsive layout, especially good display for ratios 16:9, 9:16 and 1:1
- Font must support Vietnamese characters well when Vietnamese is enabled
- Theme toggle button: system (default), light & dark
- Ensure layout never breaks, section content never gets clipped on any side, displays well on all screen sizes
- Output images MUST be in proper sizes according to their ratios when `screenshots=true`.
- Modularization & maintainable code

## PREFERENCE HELPER USAGE

The preference helper at `scripts/preferences.js` supports:

```bash
# Print resolved user preferences as JSON
node scripts/preferences.js get

# Persist workflow opt-outs
node scripts/preferences.js set --no-screenshots --no-publish --languages en

# Re-enable defaults
node scripts/preferences.js reset
```

Options for `set`:
- `--screenshots on|off`, `--no-screenshots`
- `--publishing on|off`, `--publish on|off`, `--no-publishing`, `--no-publish`
- `--languages en|vi|en,vi`, `--language en|vi`
- `--dual-language on|off`, `--no-dual-language`

## CAPTURE SCRIPT USAGE

The parallel capture script at `scripts/capture-sections.js` supports:

```bash
# Capture all sections in parallel across multiple ratios
node scripts/capture-sections.js \
  --url "file:///path/to/page.html" \
  --output-dir "./assets/showoff/my-mission/images" \
  --sections "#hero,#about,#features,#footer" \
  --ratios "horizontal,vertical,square" \
  --settle-delay 1500 \
  --format png \
  --quality 90

# Single ratio capture
node scripts/capture-sections.js \
  --url "http://localhost:3000" \
  --output-dir "./output" \
  --sections "#hero" \
  --ratios "horizontal"
```

Options:
- `--url` (required): Page URL to capture
- `--output-dir` (required): Output directory for images
- `--sections` (required): Comma-separated CSS selectors for sections
- `--ratios` (default: "horizontal,vertical,square"): Capture ratios
- `--settle-delay` (default: 1500): Ms to wait AFTER the page is visually ready (fonts + images + CSS backgrounds all resolved). Alias: `--delay` (back-compat).
- `--render-timeout` (default: 15000): Max ms to wait for any single readiness signal (fonts, images, bg-images). Prevents a broken asset from hanging the run.
- `--format` (default: "png"): Image format (png/jpg/webp)
- `--quality` (default: 90): Image quality (1-100, for jpg/webp)
- `--max-size` (default: 5): Max file size in MB before compression
- `--executable-path`: Optional Chrome/Chromium executable path. Also reads `CHROME_EXECUTABLE_PATH` or `PUPPETEER_EXECUTABLE_PATH`.

**Readiness chain before each capture:**
1. `networkidle0` (no in-flight requests)
2. `document.fonts.ready` (web fonts loaded)
3. Every `<img>` complete (or errored)
4. Every CSS `background-image` URL preloaded
5. Double `requestAnimationFrame` (layout + compositor settle)
6. `--settle-delay` ms (animations / JS-triggered reveals)

Same chain runs again after `scrollIntoView()` per section, so reveal-on-scroll animations capture correctly.

## SECURITY POLICY
This skill handles HTML generation and screenshot capture only.
Does NOT handle: authentication, database access, server deployment, or sensitive data processing.
Never include API keys or credentials in generated HTML files.
