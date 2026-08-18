---
name: ak:ai-multimodal
description: Analyze and generate image, audio, video, and document content through the pinned Multix CLI and live provider catalogs. Use for vision analysis, transcription, OCR, design extraction, and multimodal generation.
user-invocable: true
when_to_use: "Invoke for Gemini vision, OCR, media generation, or transcription."
category: ai-ml
keywords: [vision, image, video, audio, Gemini]
license: MIT
allowed-tools:
  - Bash
  - Read
  - Write
  - Edit
argument-hint: "[file-path] [prompt]"
---

# AI Multimodal

Process audio, images, videos, and documents with the exact-pinned
`@mrgoonie/multix@0.2.0` CLI. Use the `npx` invocation shown here; do not
install or call a floating global `multix`.

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix --version
```

## Setup

Requires Node.js 20+ and provider keys in process env, project `.env`, or
`~/.multix/.env`.

```bash
export GEMINI_API_KEY="your-key"          # https://aistudio.google.com/apikey
export OPENROUTER_API_KEY="your-key"      # optional image/video routing
export MINIMAX_API_KEY="your-key"         # optional MiniMax generation
```

Verify setup:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix check --verbose
```

The backend pin travels with the skill. Users update it by refreshing the
AgentKit kit; there is no auto-update path inside the skill.

### Backend ownership

- Treat the exact-pinned Multix CLI as the runtime contract for covered media
  operations; keep this skill focused on orchestration, provider setup, and
  examples.
- Report missing keys, FFmpeg, provider access, or `multix check` failures as
  environment blockers, not kit-loader failures.
- Track missing capability upstream, validate a new exact pin, then refresh the
  kit. Do not recreate a parallel AgentKit Python backend unless an accepted ADR
  or explicit maintainer decision changes backend ownership.
- When changing the pin, verify stale local-script references, skill metadata,
  adapter golden output, and provider setup guidance together.

## Quick Start

Analyze media:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix gemini analyze \
  --files input.png \
  --prompt "Analyze this content" \
  --format markdown \
  --output analysis.md
```

Transcribe audio or video:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix gemini transcribe \
  --files interview.mp4 \
  --prompt "Generate a transcript with timestamps" \
  --format markdown \
  --output transcript.md
```

Extract structured data:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix gemini extract \
  --files receipt.png \
  --prompt "Extract merchant, date, total, and line items as JSON" \
  --format json \
  --output receipt.json
```

Convert documents to Markdown:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix doc convert \
  --input report.pdf \
  --output report.md
```

Generate images after resolving an available model from the live provider catalog:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix gemini generate \
  --prompt "Studio product photo on white background" \
  --model <verified-model-id> \
  --aspect-ratio 1:1 \
  --size 2K \
  --output product.png
```

Generate images through OpenRouter:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix openrouter generate \
  --prompt "Editorial campaign key visual" \
  --model <provider-qualified-model-id> \
  --aspect-ratio 4:5 \
  --image-size 2K \
  --output campaign.png
```

Configure OpenRouter fallback models with:

```bash
export OPENROUTER_FALLBACK_MODELS="black-forest-labs/flux.2-flex,recraft-ai/recraft-v3"
```

Generate videos with a currently available provider model:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix gemini generate-video \
  --prompt "15-second product demo video" \
  --model <verified-model-id> \
  --resolution 1080p \
  --aspect-ratio 16:9 \
  --output demo.mp4
```

Generate with MiniMax:

```bash
# Image
npx -y -p @mrgoonie/multix@0.2.0 multix minimax generate \
  --prompt "A cyberpunk city" --model <verified-image-model> --aspect-ratio 16:9 --output city.png

# Video
npx -y -p @mrgoonie/multix@0.2.0 multix minimax generate-video \
  --prompt "A dancer" --model <verified-video-model> --duration <supported-seconds> --resolution <supported-resolution> --output dancer.mp4

# Speech
npx -y -p @mrgoonie/multix@0.2.0 multix minimax generate-speech \
  --text "Hello world" --model <verified-speech-model> --voice <verified-voice> --output hello.mp3

# Music
npx -y -p @mrgoonie/multix@0.2.0 multix minimax generate-music \
  --lyrics "La la la\nOh yeah" --prompt "upbeat pop" --model <verified-music-model> --output song.mp3
```

Optimize media before provider uploads:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix media optimize \
  --input raw-video.mp4 \
  --output optimized-video.mp4 \
  --target-size 20
```

## Provider and Model Resolution

The pinned Multix CLI owns command syntax. Provider catalogs own model IDs,
availability, features, limits, pricing, and deprecations. Before generation:

1. Run the relevant pinned `multix ... --help` command.
2. Check the provider's current model and pricing documentation.
3. Select an explicit model that supports the requested modality and controls.
4. Record that model in project configuration when reproducibility matters.

Never infer "latest," "default," or "recommended" from this skill.

## Failure UX

- **First run / offline**: `npx` fetches `@mrgoonie/multix@0.2.0` on first use, then reuses the npm cache. For sandboxed or offline sessions, pre-warm with `npx -y -p @mrgoonie/multix@0.2.0 multix --version`.
- **Node <20**: install Node.js 20+ and rerun the command.
- **Provider key missing**: `multix` reports the missing env var. Export keys in the shell, project `.env`, or `~/.multix/.env`.
- **Environment discovery**: use the locations reported by the pinned CLI; do not infer provider-key search paths from an older backend.
- **Provider API error**: keep the full provider error, redact keys, and retry only after fixing auth, billing, quota, model access, or request parameters.
- **Codex installs**: `skill.yaml` is AgentKit runtime metadata only. Codex uses the pinned `npx` commands in this file, so pre-warm the npm cache before network-restricted runs.

If the pinned CLI does not expose a required operation, report the observed gap
and check the upstream issue tracker. Do not revive a parallel local backend.

## References

Load for detailed guidance:

| Topic | File | Description |
|-------|------|-------------|
| Music | `references/music-generation.md` | Stable music brief and review workflow; resolve live provider controls. |
| Audio | `references/audio-processing.md` | Stable transcription and generation workflow; resolve live formats, models, limits, and pricing. |
| Images | `references/vision-understanding.md` | Stable OCR and visual-analysis workflow; resolve live input limits. |
| Image Gen | `references/image-generation.md` | Stable generation/editing workflow; resolve live model capabilities and pricing. |
| Video | `references/video-analysis.md` | Stable video-analysis workflow; resolve live inputs and limits. |
| Video Gen | `references/video-generation.md` | Stable video-generation workflow; resolve live controls and models. |
| MiniMax | `references/minimax-generation.md` | Stable multimodal workflow; resolve the live MiniMax catalog. |

## Limits

Provider limits still apply. Resolve current inline/file-upload size,
retention, duration, context, and output limits before execution. When input or
output exceeds the verified limit, split media with `ffmpeg` or the pinned
Multix media command, process segments, then combine the results.

Transcript output should be Markdown with metadata, chunk status, and timestamped
lines:

```text
[HH:MM:SS -> HH:MM:SS] transcript content
```

## Outputs

Invoke `ak:project-organization` when generated assets need to be grouped into a
project, campaign, report, or deliverable folder.

## Resources

- [multix CLI](https://github.com/mrgoonie/multix-cli)
- [Gemini API Docs](https://ai.google.dev/gemini-api/docs/)
- [Gemini Pricing](https://ai.google.dev/pricing)
- [OpenRouter Image Generation Docs](https://openrouter.ai/docs/guides/overview/multimodal/image-generation)
- [OpenRouter Provider Routing](https://openrouter.ai/docs/features/provider-routing)
- [MiniMax API Docs](https://platform.minimax.io/docs/api-reference/api-overview)
- [MiniMax Pricing](https://platform.minimax.io/pricing)
