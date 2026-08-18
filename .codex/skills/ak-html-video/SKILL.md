---
name: ak:html-video
description: "Create local MP4 videos from HTML/CSS/JS templates with nexu-io/html-video. Covers source checkout setup, template discovery, studio customization, preview, and render verification."
user-invocable: true
when_to_use: "Invoke for HTML-first video generation, template-driven promos, explainers, data videos, or social clips that should render locally through Chromium and ffmpeg."
category: frontend
keywords: [html, video, mp4, templates, animation, chromium, ffmpeg, studio, hyperframes]
license: Apache-2.0
argument-hint: "[video brief, source URL, repo, template id, or project id]"
metadata:
  author: agentkit
  version: "1.0.0"
  upstream: "nexu-io/html-video"
---

# html-video Skill

Create local videos with the `nexu-io/html-video` CLI and Studio. The tool turns HTML/CSS/JS templates and project assets into real MP4 exports through headless Chromium and ffmpeg.

Use this for template-driven product promos, explainers, data/social clips, article or repo summaries, and HTML-first motion prototypes.

## Route carefully

- Use `/ak:remotion` for React/Remotion compositions, frame math, or Remotion project code.
- Use `/ak:media-processing` when the task is only encoding, trimming, transcoding, thumbnails, HLS/DASH, or batch FFmpeg/ImageMagick work.
- Use `/ak:preview` or `the engineer show-off skill` for static HTML previews, slides, docs, diagrams, or demos that do not need MP4 rendering.
- Use `/ak:agent-browser` for browser QA or to operate the html-video Studio UI when no real Chrome profile state is needed.
- Use `/ak:chrome-profile` only if the Studio workflow explicitly needs the user's existing Chrome profile or logged-in browser state.

## Setup

Prefer a published `html-video` binary if one exists in the user's environment. If it does not, use a source checkout; do not vendor the upstream engine into the user's project or kit.

As of the upstream `main` metadata checked when this skill was authored, `html-video` declares Node `>=20`, pnpm `>=9`, and package manager `pnpm@9.15.0`. If upstream `package.json` differs, follow upstream.

```bash
# Source checkout path is a convention, not a requirement.
git clone https://github.com/nexu-io/html-video "$HOME/html-video"
cd "$HOME/html-video"
corepack enable
corepack prepare pnpm@9.15.0 --activate
pnpm install
pnpm -r build

# If source-checkout rendering reports a missing Playwright browser:
pnpm --filter @html-video/adapter-hyperframes exec playwright install chromium
```

Use this helper in shell sessions so commands work with either a global binary or a source checkout:

```bash
html_video() {
  if command -v html-video >/dev/null 2>&1; then
    html-video "$@"
    return
  fi

  local home="${HTML_VIDEO_HOME:-$HOME/html-video}"
  if [ -f "$home/packages/cli/dist/bin.js" ]; then
    node "$home/packages/cli/dist/bin.js" "$@"
    return
  fi

  echo "html-video CLI not found. Install it or set HTML_VIDEO_HOME to the source checkout." >&2
  return 127
}
```

Always start with diagnostics:

```bash
html_video doctor
html_video list-engines
```

The CLI defaults to JSON output. Add `--no-color` for logs and `--cwd <path>` when rendering projects outside the current directory.

## Standard workflow

1. Pin the video brief before creating anything:
   - audience and goal
   - duration and aspect ratio
   - source assets or URLs
   - template preference
   - output path and filename
   - whether the final should be a draft proof or polished export

2. Discover templates:

```bash
html_video search-templates --intent "short product promo for a developer tool" --aspect 16:9 --top 5
html_video inspect-template frame-product-promo
```

Inspect before choosing. Some templates expose variables that can be set by CLI; others rely on Studio/project editing and have no template input schema.

3. Create or locate a project:

```bash
html_video project-create \
  --name "AgentKit Promo" \
  --intent "Short product promo for a controlled agent engineering stack" \
  --aspect 16:9

html_video project-list
html_video project-show <project-id>
```

4. Select a template and add assets:

```bash
html_video project-set-template <project-id> --template frame-product-promo
html_video project-add-asset <project-id> --inline-text "AgentKit: controlled agent engineering workflows" --caption "core message"
html_video project-add-asset <project-id> --file ./path/to/logo-or-screenshot.png --caption "visual reference"
```

If `inspect-template` shows variables, set them explicitly:

```bash
html_video project-set-var <project-id> --key headline --value '"AgentKit"'
html_video project-set-vars <project-id> --vars-file ./video-vars.json
```

5. Preview:

```bash
html_video project-preview <project-id>
```

Open the returned `html_path` in a browser. If the user needs interactive editing, launch Studio:

```bash
html_video studio --port 3071
```

Use Studio for agent-assisted rewrite, layout tuning, and templates with empty variable schemas. Export from Studio, or render the finished project by CLI.

6. Render and verify:

```bash
html_video project-render <project-id> --output ./assets/videos/<slug>.mp4 --stream-progress
ffprobe -v error -show_streams -show_format -of json ./assets/videos/<slug>.mp4
```

The MP4 proof is not complete until `ffprobe` reports a nonzero duration and expected video dimensions.

## Output organization

Use the target repository's existing artifact conventions before choosing final paths. In a new or simple project, use:

- `assets/videos/<slug>.mp4` for finished local exports
- `plans/<plan-slug>/visuals/<slug>.mp4` for implementation proof artifacts
- `tmp/html-video/<slug>/` for disposable preview or Studio scratch state

Do not commit large generated MP4 files unless the user explicitly wants the artifact versioned.

## Maintenance guidance

`nexu-io/html-video` is moving quickly. Before relying on memorized commands, run:

```bash
html_video --help
html_video project-render --help
html_video studio --help
```

If a first-party `html-video` agent skill package becomes available, prefer its live instructions and update this wrapper to point at that package instead of duplicating command reference.

## Troubleshooting

| Symptom | Action |
| --- | --- |
| `html-video CLI not found` | Install a global binary if available, or set `HTML_VIDEO_HOME` to a built source checkout. |
| `doctor` reports missing browser | Install Playwright/Chromium using upstream instructions, then rerun `doctor`. |
| Render reports `Executable doesn't exist` for Playwright Chromium | From the source checkout, run `pnpm --filter @html-video/adapter-hyperframes exec playwright install chromium`. |
| `doctor` reports ffmpeg missing | Install ffmpeg with the platform package manager and verify `ffmpeg -version`. |
| Template has no variables | Use Studio to customize copy/layout; CLI variable commands cannot theme an empty schema. |
| Render starts but MP4 is blank | Preview first, inspect browser console if Studio exposes it, then rerun `project-render` with `--stream-progress`. |
| Output path is wrong | Re-render with an explicit `--output` path; do not move only partial render directories. |
