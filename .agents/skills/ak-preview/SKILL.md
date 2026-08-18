---
name: ak:preview
description: "View files or generate visual explanations, slides, and diagrams. Use for code walkthroughs, architecture visualization, HTML/Markdown presentations."
user-invocable: true
when_to_use: "Invoke for visual explanations, file previews, or diagrams."
category: utilities
keywords: [preview, visual, slides, diagrams, HTML]
argument-hint: "[path] OR [--html] --explain|--slides|--diagram|--ascii [topic] OR --html --diff|--plan-review|--recap"
metadata:
  author: agentkit
  version: "1.2.0"
  attribution: "Visual self-review pattern for diagram output adapted from fireworks-tech-graph by yizhiyanhua-ai (MIT)"
  license: MIT
---

# Preview

Universal viewer + visual generator. View existing content OR generate new visual explanations.

## Default (No Arguments)

If invoked without arguments, use `ask_user capability` to present available preview operations:

| Operation | Description |
|-----------|-------------|
| `(view)` | View a file or directory |
| `--explain` | Generate visual explanation |
| `--slides` | Generate presentation slides |
| `--diagram` | Generate architecture diagram |
| `--ascii` | Terminal-friendly diagram |
| `--stop` | Stop preview server |
| `--html --explain` | Self-contained HTML explanation (opens in browser) |
| `--html --diagram` | Self-contained HTML diagram with zoom controls |
| `--html --slides` | Magazine-quality HTML slide deck |
| `--html --diff` | Visual diff review (HTML) |
| `--html --plan-review` | Plan vs codebase comparison (HTML) |
| `--html --recap` | Project context snapshot (HTML) |

Present as options via `ask_user capability` with header "Preview Operation", question "What would you like to do?".

## Usage

### View Mode
- `/ak:preview <file.md>` - View markdown file in novel-reader UI
- `/ak:preview <directory/>` - Browse directory contents
- `/ak:preview --stop` - Stop running server

### Generation Mode (Markdown)
- `/ak:preview --explain <topic>` - Generate visual explanation (ASCII + Mermaid + prose)
- `/ak:preview --slides <topic>` - Generate presentation slides (one concept per slide)
- `/ak:preview --diagram <topic>` - Generate focused diagram (ASCII + Mermaid)
- `/ak:preview --ascii <topic>` - Generate ASCII-only diagram (terminal-friendly)

### Generation Mode (HTML)
- `/ak:preview --html --explain <topic>` - Self-contained HTML explanation
- `/ak:preview --html --slides <topic>` - Magazine-quality HTML slide deck
- `/ak:preview --html --diagram <topic>` - HTML diagram with zoom controls
- `/ak:preview --html --diff [ref]` - Visual diff review
- `/ak:preview --html --plan-review [plan-file]` - Plan vs codebase comparison
- `/ak:preview --html --recap [timeframe]` - Project context snapshot

## Argument Resolution

When processing arguments, follow this priority order:

1. **`--stop`** → Stop server (exit)
2. **`--html` flag present** → Set HTML output mode flag (continues to next step)
3. **Generation flags** (`--explain`, `--slides`, `--diagram`, `--ascii`) → Generation mode. Load `references/generation-modes.md`
4. **HTML-only flags** (`--diff`, `--plan-review`, `--recap`) → Auto-set HTML mode, then generation mode. Load `references/generation-modes.md`
5. **Visual workflow without explicit mode** → Load `references/visual-explanation-routing.md` to choose mode
6. **Resolve path from argument:**
   - If argument is an explicit path → use directly
   - If argument is a contextual reference → resolve from recent conversation context
7. **Resolved path exists on filesystem** → View mode. Load `references/view-mode.md`
8. **Path doesn't exist or can't resolve** → Ask user to clarify

**Topic-to-slug conversion:**
- Lowercase the topic
- Replace spaces/special chars with hyphens
- Remove non-alphanumeric except hyphens
- Collapse multiple hyphens → single hyphen
- Trim leading/trailing hyphens
- **Max 80 chars** - truncate at word boundary if longer

**Multiple flags:** If multiple generation flags provided, use first one; remaining treated as topic.

**Placeholder `{topic}`:** Replaced with original user input in title case (not the slug).

## Error Handling

| Error | Action |
|-------|--------|
| Invalid topic (empty) | Ask user to provide a topic |
| Flag without topic | Ask user: "Please provide a topic: `/ak:preview --explain <topic>`" |
| Topic becomes empty after sanitization | Ask for topic with alphanumeric characters |
| File write failure | Report error, suggest checking disk space and permissions |
| Server startup failure | Check if port in use, try `/ak:preview --stop` first |
| No generation flag + unresolvable reference | Ask user to clarify which file they meant |
| Existing file at output path | Overwrite with new content (no prompt) |
| Server already running | Reuse existing server instance, just open new URL |
| Parent `plans/` dir missing | Create directories recursively before write |
| `--diff` without git context | Explain: "No git repo detected. Run inside a git repository." |
| `--plan-review` without plan file or active plan | Explain: "Provide a plan file path or run from a session with an active plan." |
| `--recap` without git history | Explain: "No git history found. Run inside a git repository with commits." |
| `--html --ascii` combination | Not supported — `--ascii` is terminal-only by design. Suggest `--html --diagram` instead |
| `--diff` with PR number but `gh` unavailable | Explain: "GitHub CLI (gh) is required for PR diffs. Install from https://cli.github.com/" |

## HTML Output Mode (`--html`)

Adding `--html` to any generation flag switches output from Markdown to a self-contained HTML file.

**Output:** Single `.html` file with all CSS/JS inline. Opens directly in browser — no server needed.
**Location:** `{plan_dir}/visuals/{slug}.html` (same plan-aware logic as markdown mode)
**Browser open:** `open` (macOS) / `xdg-open` (Linux) / `start` (Windows)
**MANDATORY — Theme Toggle:** Every HTML page MUST include a light/dark theme toggle button. See `html-css-patterns.md` → "Theme Toggle Button" for the exact CSS, HTML, and JS to include. Pages without the toggle are considered incomplete.

### Reference Loading (HTML mode)

Before generating, agent MUST read these references:

| Mode | Always read | Mode-specific |
|------|-------------|---------------|
| All HTML modes | `html-design-guidelines.md` | — |
| `--explain` | `html-css-patterns.md`, `html-libraries.md` | Template: `architecture.html` |
| `--diagram` | `html-css-patterns.md`, `html-libraries.md` | Template: `mermaid-flowchart.html` or `architecture.html` |
| `--slides` | `html-slide-patterns.md`, `html-css-patterns.md`, `html-libraries.md` | Template: `slide-deck.html` |
| `--diff` | `html-css-patterns.md`, `html-libraries.md` | Templates: `data-table.html`, `architecture.html` |
| `--plan-review` | `html-css-patterns.md`, `html-libraries.md` | Templates: `architecture.html`, `data-table.html` |
| `--recap` | `html-css-patterns.md`, `html-libraries.md` | Templates: `architecture.html`, `data-table.html` |

Multi-section pages (`--explain`, `--diff`, `--plan-review`, `--recap`): also read `html-responsive-nav.md`.

Use `/ak:mermaidjs-v11` skill for Mermaid syntax validation.

### HTML-Only Modes

#### `--diff [ref]` (implies --html)
Visual diff review. Scope detection: branch name, commit hash, HEAD, PR number, commit range, default=main.
Data: git diff --stat, --name-status, changed files, new API surface, CHANGELOG.
Output: executive summary, KPI dashboard, module architecture (Mermaid), feature comparisons (side-by-side), flow diagrams, file map, test coverage, code review cards (Good/Bad/Ugly/Questions), decision log, re-entry context.

#### `--plan-review [plan-file]` (implies --html)
Plan vs codebase comparison. Input: plan file path or detect from active plan context.
Data: read plan, read all referenced files, map blast radius, cross-reference assumptions.
Output: plan summary, impact dashboard, current vs planned architecture (paired Mermaid), change breakdown (side-by-side), dependency analysis, risk assessment, review cards, understanding gaps.
Visual language: blue=current, green=planned, amber=concern, red=gap.

#### `--recap [timeframe]` (implies --html)
Project context snapshot. Time window: shorthand (2w, 30d, 3m) or default 2w.
Data: project identity, git log, git status, decision context, architecture scan.
Output: project identity, architecture snapshot (Mermaid), recent activity, decision log, state KPI cards, mental model essentials, cognitive debt hotspots, next steps.

### Style Strategy
- Default: static anti-slop rules from `html-design-guidelines.md` (6 curated presets)
- For `--slides`: consider invoking `/ak:ui-ux-pro-max` for richer style selection
- Agent must vary aesthetics between consecutive HTML outputs (different font pair, palette)
