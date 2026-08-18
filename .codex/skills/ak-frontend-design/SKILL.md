---
name: ak:frontend-design
description: Create polished frontend interfaces from designs/screenshots/videos. Use for web components, 3D experiences, replicating UI designs, quick prototypes, immersive interfaces, avoiding AI slop.
user-invocable: true
when_to_use: "Invoke when visual fidelity and polished UI are primary."
category: frontend
keywords: [ui, design, screenshots, prototyping]
license: Complete terms in LICENSE.txt
metadata:
  author: agentkit
  version: "2.0.0"
---

This skill guides creation of distinctive, production-grade frontend interfaces that avoid generic "AI slop" aesthetics. Implement real working code with exceptional attention to aesthetic details and creative choices.

**IMPORTANT**: MUST follow the Decision Procedure, Aesthetic Direction Menu, Non-Negotiable Craft Rules, Layout Discipline, Absolute Bans, and the Self-Review Gate below. They apply to EVERY model and runtime executing this skill (Claude Code, Codex/GPT, others) — hard requirements, not stylistic suggestions. If your instinct conflicts with a rule here, the rule wins.

## Know Your Own Defaults (why models produce slop)

- **Mode collapse**: you have one favorite answer per brief type (Inter + slate, purple gradient, centered hero + 3 equal cards, cream + serif for anything "artisan"). Reaching for it instead of reading the brief is the root failure. The seeded variation step below exists to break it.
- **Decoration is cheaper than design**: when unsure, models add meta-ornament (eyebrow labels, section numbers, status dots, fake version stamps) instead of composition. Delete ornament; compose instead.
- **Brevity bias**: models silently omit states, imagery, and motion to reduce risk. The production bar below forces completeness.
- Countermeasures baked into this skill: seeded variation, numeric rules, countable checks, binary self-review. Follow them mechanically — they work precisely because they leave no room for "felt about right".

## Workflow Selection

Choose workflow based on input type:

| Input | Workflow | Reference |
|-------|----------|-----------|
| Screenshot | Replicate exactly | `./references/workflow-screenshot.md` |
| Video | Replicate with animations | `./references/workflow-video.md` |
| Screenshot/Video (describe only) | Document for devs | `./references/workflow-describe.md` |
| 3D/WebGL request | Three.js immersive | `./references/workflow-3d.md` |
| Quick task | Rapid implementation | `./references/workflow-quick.md` |
| Complex/award-quality | Full immersive | `./references/workflow-immersive.md` |
| From scratch | Decision Procedure below | - |

**Precedence:** The rules in this skill are self-contained design intelligence. When any other skill or recommendation conflicts with the rules below (e.g., Inter font, AI Purple palette, Lucide-only icons), prefer the rules below unless the user explicitly requested the conflicting choice.

## Screenshot/Video Replication (Quick Reference)

1. **Analyze** with `ak:ai-multimodal` skill - extract colors, fonts, spacing, effects
2. **Plan** with `ui-ux-designer` subagent - create phased implementation
3. **Implement** - match source precisely
4. **Verify** - compare to original
5. **Document** - update `./docs/design-guidelines.md` if approved

When replicating, the source is the contract — the craft rules below yield to it.

## Design Dials

Three configurable parameters that drive design decisions. Set from the preset table (or user override via chat):

| Dial | Default | Range | Low (1-3) | High (8-10) |
|------|---------|-------|-----------|-------------|
| `DESIGN_VARIANCE` | 8 | 1-10 | Perfect symmetry, centered layouts, equal grids | Asymmetric, masonry, massive empty zones, fractional CSS Grid |
| `MOTION_INTENSITY` | 6 | 1-10 | CSS hover/active states only | Scroll reveals, spring physics, perpetual micro-animations |
| `VISUAL_DENSITY` | 4 | 1-10 | Art gallery — huge whitespace, expensive/clean | Cockpit — tiny paddings, 1px dividers, monospace numbers everywhere |

Presets by surface (variance/motion/density): SaaS landing 7/6/4 · agency/creative 9/8/3 · premium consumer 7/6/3 · designer portfolio 8/7/3 · dev portfolio 6/5/4 · editorial 6/4/3 · dashboard/product UI 3/2/6 · public sector 3/2/5. Redesigns: infer the existing page's dial values first; preserve-mode matches them, overhaul-mode adds +2 variance/motion.

Dial-gated rules: `VARIANCE > 4` bans centered heroes (use split-screen or left-aligned). `MOTION > 3` makes `prefers-reduced-motion` handling mandatory; `MOTION > 4` means the page must actually move — otherwise lower the dial honestly. `DENSITY > 7` bans card boxes — use spacing, 1px hairlines, and monospace numerals.

## Register: Brand vs Product

Identify the register before designing — the rules differ:

| | **Brand** (landing, marketing, portfolio) | **Product** (app UI, dashboard, tool) |
|---|---|---|
| Slop test | "Would someone say AI made that?" — bar is distinctiveness | "Would a Linear/Figma-fluent user trust it?" — bar is earned familiarity |
| Type scale | Fluid `clamp()`, ratio ≥ 1.25 | Fixed `rem`, ratio 1.125–1.2; one family often right |
| Color | Committed/Full/Drenched strategies allowed — one saturated color owning a hero is voice | Restrained floor: accent = primary action + selection + state, nothing else |
| Motion | One orchestrated page-load entrance allowed | 150–250ms state-conveying only; page-load choreography NEVER |
| Layout | Asymmetry, grid-breaking, art direction per section | Density, consistency, structural responsiveness (collapse sidebar, not shrink type) |
| Failure mode | Restraint without intent reads as mediocre | Strangeness without purpose destroys trust |

## Design Thinking

Before coding, commit to a BOLD aesthetic direction:
- **Purpose**: What problem does this interface solve? Who uses it?
- **Tone**: Pick an extreme: brutally minimal, maximalist chaos, retro-futuristic, organic/natural, luxury/refined, playful/toy-like, editorial/magazine, brutalist/raw, art deco/geometric, soft/pastel, industrial/utilitarian, etc.
- **Constraints**: Technical requirements (framework, performance, accessibility).
- **Differentiation**: What makes this UNFORGETTABLE? What's the one thing someone will remember?

**CRITICAL**: Choose a clear conceptual direction and execute it with precision. Bold maximalism and refined minimalism both work - the key is intentionality, not intensity.

### Mandatory Decision Procedure (BEFORE writing any code)

Taste is not improvised. Follow this exact sequence and show steps 1-3 in your response:

1. **Design Read declaration** — one line: `Reading this as: <page kind> for <audience>, with a <vibe> language, leaning <aesthetic direction>.` This forces brief inference before your default aesthetic fires. If the brief is genuinely ambiguous, ask exactly ONE question — never a question dump.
2. **Seeded variation (break mode collapse)** — derive a seed from the request (e.g. character count of the user's prompt). Use `seed % <row count>` to pick the direction from the menu below, then pick the hero archetype and 2-3 component patterns from that direction. NEVER repeat the direction, font pairing, or palette family of your previous generation in this project. If the seeded pick is a poor fit for the audience, step to the adjacent row and say so — deviation must be justified, not silent.
3. **Aesthetic thesis** — one sentence: `<direction> for <audience>: <palette in 5 words>, <type character>, <layout signature>, <one memorable element>`. Also state where the form came from in the CONTENT (a motif, a domain object, a word in the copy). If you cannot state it, you are templating — re-derive.
4. **Tokens first** — CSS variables for colors (OKLCH), font families, type scale, spacing scale, radii, shadows, easings. Every value in the implementation traces to a token. No ad-hoc hex codes or magic pixels mid-file.
5. **Escalate exactly ONE dimension** to a memorable extreme (type scale, color, layout, motion, or density). Keep the others disciplined and quiet. Everything-loud is slop; everything-timid is slop.
6. **If you cannot justify a value, re-derive it from the scale.** "It looked about right" is not a justification.

## Aesthetic Direction Menu

When designing from scratch, PICK ONE direction (or blend two at most), then execute it fully. Vague middle-ground produces slop. These are **anchors, not recipes** — re-derive exact palette values from the actual brand/content, and rotate: never reuse your previous generation's direction or fonts.

| Direction | Display / Body fonts | Palette recipe | Layout signature |
|-----------|---------------------|----------------|------------------|
| Swiss / editorial | Archivo Expanded, Schibsted Grotesk / Libre Franklin | Bone `#F7F5F0` bg, ink `#1A1815` text, single red or cobalt accent | Hairline dividers, exposed grid, flush-left, big margins |
| Luxury / refined | Libre Caslon Display, Italiana / Figtree | Deep charcoal or cream bg, gold/bronze accent, muted warm neutrals | Centered serif display, generous whitespace, thin rules |
| Brutalist / raw | Archivo Black, Bricolage Grotesque / JetBrains Mono | Unmixed primaries on white or near-black, hard `2-3px` borders, `4px 4px 0` shadows | Visible borders, no rounded corners, stacked blocks, marquee text |
| Retro-futuristic / terminal | Chakra Petch, Orbitron / JetBrains Mono | Phosphor green or amber on `#0C0F0A` tinted black, scanline texture | Monospace tables, ASCII dividers, status-bar chrome |
| Organic / natural | Gloock, Young Serif / Nunito Sans | Moss, clay, sand, cream — desaturated earth ramp, no pure white | Blob/arch shapes, irregular grid, photography-forward |
| Soft / pastel play | Baloo 2, Quicksand / Karla | Cream bg, 2-3 chalky pastels + one saturated pop | Pill shapes, chunky radii `16-24px`, pressed-button 3D (`box-shadow: 0 4px 0` + active `translateY(4px)`) |
| Industrial / utilitarian | Barlow Condensed, Oswald / Source Sans 3 | Concrete grays tinted cool, safety-orange or yellow accent | Dense data tables, uppercase labels, corner brackets |
| Art deco / geometric | Marcellus, Poiret One / Josefin Sans | Black + champagne + one jewel tone | Symmetric frames, inline SVG line ornament, letter-spaced caps |
| Editorial dark / cinematic | Bodoni Moda, Literata / Hanken Grotesk | `#101014` blue-tinted black, warm white text, one desaturated accent | Full-bleed imagery, overlapping type, huge display sizes |
| Neo-grotesque product | Familjen Grotesk, Sora / Geist | Tinted off-white bg, near-black text, one confident brand hue | Split-screen hero, asymmetric 5/7 grid, floating detail cards |

Verify chosen fonts exist on Google Fonts (or self-host an equivalent); if the content is Vietnamese or CJK, confirm the subset support before committing.

## Non-Negotiable Craft Rules

Concrete numbers. Apply unless the user's reference design contradicts them.

**Typography**
- Max 2 families: one display, one body — paired on a CONTRAST axis (serif + sans, geometric + humanist, or one family in multiple weights). Never two similar-but-not-identical sans. Max 3-4 weights; preload only the critical body weight.
- Modular scale by register: 1.2 (dense UI), 1.25 (default web), 1.333 (editorial/marketing). More than 6 size steps = hierarchy failure.
- Body: 16-18px in `rem`, line-height 1.5-1.7, measure 45-75ch (65ch sweet spot). Headings: line-height 1.1-1.2. Heading:body size ratio ≥ 2.5x.
- Display type is large but capped: `clamp(2.75rem, 6vw + 1rem, 6rem)`. Above ~6rem the page is shouting. Letter-spacing floor: **≥ -0.04em** (-0.02 to -0.03em is plenty for tight grotesque display; tighter and letters touch). ALL-CAPS micro-labels: +0.05 to 0.12em at 11-12px.
- **The 2-line iron rule**: hero H1 never exceeds 2-3 lines. Use a wide container (`max-w-5xl`/`max-w-6xl`) and shrink the font before letting it wrap to 4+ lines. A 4-line headline is a font-size error, not a copy error.
- `text-wrap: balance` on h1-h3; `text-wrap: pretty` on prose — free typographic quality.
- Dark mode compensation (light-on-dark reads heavier): line-height +0.05-0.1, letter-spacing +0.01-0.02em, drop body weight one notch (400 → 350 if available).
- `font-variant-numeric: tabular-nums` for data, prices, counters, tables.
- Multilingual: put the Latin font FIRST in the fallback chain (`"Geist", "Noto Sans SC", sans-serif` — matching is per-codepoint). CJK: +0.2 line-height over Latin values, never negative tracking. Vietnamese: verify diacritics render in the chosen face. Inputs ≥ 16px font (avoids mobile zoom).

**Spacing**
- 4pt scale only: 4, 8, 12, 16, 24, 32, 48, 64, 96. No 13px, no 22px.
- Proximity encodes hierarchy: 8-12px between related siblings, 48-96px between sections — intra-group gap < inter-group gap by ≥ 2 scale steps.
- Whitespace ≥ 40% of the surface at default density (60%+ for minimal styles). Blank space is a composition problem, not a content-filling problem.

**Color**
- OKLCH for construction. Ramp recipe: hold hue + chroma, vary lightness; reduce chroma near white/black. Neutral ramp 9-11 steps, tinted 0.005-0.015 chroma toward THIS brand's hue — not reflex-warm or reflex-cool.
- Pick a **color strategy** before colors: **Restrained** (tinted neutrals + one accent ≤ 10% — product default) · **Committed** (one saturated color carries 30-60% — brand identity pages) · **Full palette** (3-4 named roles) · **Drenched** (the surface IS the color — campaign heroes).
- **Anti-cream rule**: the warm cream/sand/beige body background is the saturated AI default. "Warm/artisan/editorial" briefs do NOT translate to a near-white warm bg — carry warmth via accent, typography, and imagery; pick a saturated brand color, a chroma-0 off-white, or a darker brand-tinted midtone instead.
- Dark vs light is never a default. Write one sentence of physical scene (who uses this, where, under what light, in what mood) — if the sentence doesn't force the answer, add detail until it does.
- Chroma tiers (low saturation reads premium): large backgrounds 0.01-0.04, brand/accent 0.08-0.15, small CTA pops 0.15-0.22.
- 60/30/10 as visual weight: 60% neutral/whitespace, 30% secondary, 10% accent. The accent works BECAUSE it is rare — never on inactive states.
- Never raw `#000`/`#FFF`. Dark themes: tinted near-black at 12-18% L; elevate surfaces by lightening (3 steps ≈ 15/20/25% L, same hue), not by piling shadows.
- Contrast: ≥ 4.5:1 body (placeholders too), ≥ 3:1 large text and meaningful UI. Never gray text on a colored background — use a darker shade of the background's own hue. Muted text from the neutral ramp, not `opacity`. Heavy `rgba()` everywhere = incomplete palette; define explicit overlay colors.
- One gray family per page — never mix warm and cool grays. Sample palette hues from real brand assets/content imagery when they exist; write one sentence justifying the palette (can't write it = you're copying a recipe).

**Depth & surfaces**
- ONE depth strategy per surface — hairline borders, layered shadows, or surface-tint elevation. Mixing all three on one card is slop. The ghost-card combo (1px border + soft wide ≥16px-blur shadow) is banned: pick one.
- Shadows layered and tinted with the background hue: `0 1px 2px hsl(var(--shadow-hue) 30% 10% / 0.06), 0 4px 12px … / 0.08, 0 16px 32px … / 0.08`. Never the default gray `0 4px 8px rgba(0,0,0,0.1)`.
- **Shape lock**: one radius system per page — all-sharp (0), all-soft (8-16px), or all-pill. Cards top out at 16px; 24px+ on cards is the over-round tell. Nested radius = parent radius − parent padding.
- **Theme lock**: one theme per page. `bg-zinc-950` next to `bg-zinc-900` is fine; a light section sandwiched into a dark page is broken. Max one deliberate theme-switch device per page.

**Motion** (for scroll animation, GSAP, or `MOTION_INTENSITY > 4` builds, read `./references/motion-craft.md` before implementing)
- The 100/300/500 rule: 100-150ms instant feedback (press, toggle) · 200-300ms state changes (hover, menu, tooltip) · 300-500ms layout changes (accordion, modal, drawer) · 500-800ms entrances (hero only). Exits run at ~75% of entrance duration.
- Easing tokens: `--ease-out-quart: cubic-bezier(0.25,1,0.5,1)` · `--ease-out-quint: cubic-bezier(0.22,1,0.36,1)` · `--ease-out-expo: cubic-bezier(0.16,1,0.3,1)`. Springs fine (`stiffness: 100, damping: 20`). **Banned**: `linear` for UI, bounce `cubic-bezier(0.34,1.56,0.64,1)` and elastic easings — dated and tacky (small overshoot is OK on toggles only).
- Stagger 30-60ms per item, total sequence ≤ 500ms; more items → shorter per-item delay.
- Animate only `transform`, `opacity`, `color`, `box-shadow` (grid-template-rows or FLIP for expansion; blur/clip-path allowed when bounded and verified smooth). Never `transition: all`. Never `width/height/top/left/margin`.
- **Reveal safety**: content must be visible by default; animation enhances it. Never gate visibility on a class-triggered transition (hidden tabs and headless renderers ship the section blank).
- The uniform whole-section fade-and-rise applied to every section is a tell. Stagger within one list is legitimate; each reveal should fit what it reveals. But suppressing the reflex is never a reason to ship a page with zero motion.
- Motion must be motivated by hierarchy, feedback, story, or state — "looked cool" is invalid. Product UI: state-conveying 150-250ms only, no load choreography ever. Pause ≥ 300ms before a key reveal (reaction time); end sequences with a hard stop, not a fade.
- Scroll tech: `useScroll`/`useMotionValue`/`ScrollTrigger`/`IntersectionObserver`/CSS `animation-timeline` — never raw scroll listeners or `useState` for continuous values. GSAP pins: `start: "top top"` (not `"top center"` — the #1 pin failure), `pin: true`; horizontal pan: `end: "+=" + (track.scrollWidth - innerWidth)`, `scrub: 1`, `invalidateOnRefresh: true`. Max 1 marquee per page.
- `@media (prefers-reduced-motion: reduce)` alternative for every animation. Non-negotiable.

**Interaction states**
- Every interactive element ships: default, hover, `:focus-visible`, active, disabled, loading, error/success where applicable. Focus ring: 2-3px, offset outside the element, ≥ 3:1 contrast, on-brand.
- Hover states move or reveal something (lift `translateY(-2px)`, underline slide, icon nudge) — not just a color dim. Press: `translateY(2px)` or `scale-[0.98]` at ~100ms.
- Touch targets ≥ 44×44px even when the visual is smaller (expand via `::before { inset: -10px }`).
- Dropdown clipping: use the Popover API, native `<dialog>`, or a portal + `position: fixed` — never `position: absolute` inside `overflow: hidden` (the single most common generated-code bug).
- Forms: validate on blur (not per keystroke), errors below the field with `aria-describedby`, placeholders are not labels. Skeletons > spinners. Undo > confirm (confirm only for irreversible/batch).
- Working-memory caps: ≤ 4 metrics above the fold, ≤ 5 top-level nav items, ≤ 4 fields per visual group, ≤ 3 pricing tiers, 1 primary button per view.

**Imagery & icons**
- Image-led briefs (restaurant, hotel, travel, fashion, product, photography) REQUIRE real imagery — CSS scenery, decorative gradient panels, or div-built fake screenshots/dashboards are broken implementations, not interpretations.
- Source order: generation tools → seeded placeholders (`https://picsum.photos/seed/{descriptive-keyword}/1600/900`) → labeled TODO slots. Verify real URLs before referencing (guessed photo IDs ship as broken images). Apply CSS treatment (grayscale, `contrast-125`, duotone, `mix-blend-luminosity`) so photos don't read as stock. One decisive photo > five mediocre.
- ONE icon family per project (Phosphor, Heroicons, Tabler — or the project's existing set), one stroke width (1.5 or 2.0). No emoji as icons. No hand-rolled "sketchy" SVG illustration scenes — no illustration beats bad illustration. Real brand logos via `https://cdn.simpleicons.org/{slug}`.

**Content & copy**
- Per section: headline ≤ 8 words, supporting text ≤ 25 words, one visual or CTA. Quotes ≤ 3 lines with name + role. Lists > 5 items need a different component (grouped columns, tabs, cards) — never a long `<ul>` with dividers.
- Realistic messy numbers (`$48,217`, `+7.3%`, `12,304 users`) — never fabricated stats presented as real, never fake-round (`10,000+ customers`, `99.99%`). An honest labeled placeholder beats an invented metric.
- Banned copy: "Elevate", "Seamless", "Unleash", "Empower", "Supercharge", "Next-Gen", "Game-changer". Banned furniture: scroll cues ("Scroll to explore"), version stamps (BETA / v1.4.2), fake photo credits, decorative status dots, locale/time/weather strips. Step labels are verb-nouns ("Install, Configure, Ship"), not "Stage 1/2/3".
- No em-dash (`—`) in visible UI copy — zero tolerance; it is the most reliable AI copy tell. Use a period, comma, or rewrite.
- Copy self-audit before shipping: re-read every visible string; rewrite anything grammatically broken, referent-less, or "trying to sound thoughtful". Plain and specific beats cute.

## Layout Discipline

**Hero**
- Fits the initial viewport (`min-h-[100dvh]`, never `h-screen`). Max 4 text elements: (eyebrow OR brand strip) + headline + subtext (≤ 20 words) + CTAs (1 primary + ≤ 1 secondary, labels ≤ 3 words).
- Banned inside the hero: trust micro-strips, avatar rows, pricing teasers, feature bullets, logo walls (own section below the fold), floating badge/stamp icons, pills overlaid on images, raw stat blocks.
- One CTA label per intent page-wide ("Get in touch" and "Let's talk" on one page = fail). Button text contrast always perfect: dark bg → white text, light bg → dark text.

**Section rhythm**
- **Eyebrow rationing**: the tiny uppercase-tracked kicker above a heading — max 1 per 3 sections, hero included. Countable check: `uppercase tracking` occurrences ≤ ceil(sections/3). Default fix: delete it; the headline is enough.
- Numbered section markers (01 / 02 / 03) only when the content IS a real ordered sequence. Meta-labels ("SECTION 01", "ABOUT US" as decoration) are banned outright.
- **Layout diversity quota**: a layout family (split hero, zigzag pair, card grid, full-bleed band, editorial columns…) appears at most ONCE per page — 8 sections need ≥ 4 distinct families. Zigzag image/text alternation caps at 2 consecutive.
- Section vertical rhythm at low density: `py-24`–`py-48` desktop, roughly half on mobile. Sections read as distinct chapters.

**Grids & cards**
- Three-equal-feature-cards is banned. Use asymmetric fractions (`grid-template-columns: 2fr 1fr 1fr`), split-screens, masonry, or spacing-and-divider layouts.
- Bento grids: exactly N cells for N items — no blank filler tiles; `grid-auto-flow: dense` and verify col/row spans interlock with zero voids. 3-5 intentional cells beat 8 messy ones; at least a third must carry real visual variation (image, chart, pattern), not all white-on-white text.
- Cards are the lazy default — use them only when they're truly the best affordance; never nest cards in cards. Breakpoint-free grids: `repeat(auto-fit, minmax(280px, 1fr))`.
- Nav: single line at desktop, 64-72px tall. Semantic z-index scale (dropdown → sticky → backdrop → modal → toast → tooltip); never `z-[9999]`.

## Absolute Bans (match-and-refuse)

If you're about to write any of these, stop and rewrite the element with different structure:

- **Fonts**: Inter/Roboto/Arial/system-ui as display type. Burned-out AI-tell faces: Fraunces, Space Grotesk, Playfair Display, Instrument Serif (substitutes: Schibsted Grotesk, Archivo, Libre Caslon, Bodoni Moda). Never the same serif or palette family twice in a row across generations. Display fonts in labels, buttons, or data.
- **Color**: purple-gradient-on-white; raw `#000`/`#FFF`; oversaturated evenly-distributed palettes; cream/beige-by-default (see anti-cream rule); mixing warm and cool grays; full-saturation accents on inactive elements; flag-color palettes for cultural briefs.
- **Surfaces**: side-stripe borders (`border-left` > 1px as colored accent on cards/callouts); gradient text (`background-clip: text`); glassmorphism as default; ghost cards (1px border + wide soft shadow); over-rounding (24px+ card radius); `repeating-linear-gradient` stripe backgrounds; decorative grid-line backgrounds (unless the surface is literally a canvas/map/blueprint); neon outer glows; custom cursors (unless asked).
- **Layout**: centered hero + 3 equal cards template; hero-metric template (big number + label + stats + gradient); identical icon-heading-text card grids; eyebrow kicker on every section; numbered markers as scaffolding; `h-screen`; big rounded icon above every heading; split-header (huge left headline + small right paragraph).
- **Components**: default unstyled shadcn; mixed icon families; monospace as costume for "technical"; custom scrollbars and reinvented form controls; modal as the first thought in product UI.
- **Content**: "John Doe", "Acme Corp", lorem ipsum, round fake numbers, AI copy clichés, meta-labels, em-dashes in UI copy.

Every ban has a legitimate exception path: the user explicitly asked for it, or the existing brand genuinely uses it. Exceptions are stated out loud, never silent.

## Asset & Analysis References

| Task | Reference |
|------|-----------|
| Generate assets | `./references/asset-generation.md` |
| Analyze quality | `./references/visual-analysis-overview.md` |
| Extract guidelines | `./references/design-extraction-overview.md` |
| Optimization | `./references/technical-overview.md` |
| Motion timing, GSAP/Motion recipes | `./references/motion-craft.md` |
| Animations (anime.js) | `./references/animejs.md` |
Quick start: `./references/ai-multimodal-overview.md`

**Assets**: Generate images with `ak:ai-multimodal`, process with `ak:media-processing`

## Self-Review Gate (mandatory before delivering)

Run this against your output. Each item is pass/fail — fix EVERY failure before presenting. Do not rationalize a failure as a stylistic choice.

**Countable checks (mechanically verifiable — actually count):**
1. Uppercase-tracked kickers ≤ ceil(sections / 3).
2. Em-dash count in visible copy = 0. Banned-word grep ("Elevate", "Seamless", "Unleash"…) = 0.
3. No layout family appears twice. Marquees ≤ 1. Zigzag runs ≤ 2.
4. Every spacing value sits on the 4pt scale; every color/size traces to a token.

**Binary checks:**
5. Fonts are not Inter/Roboto/Arial/system as display; display ≠ body; neither is on the burned-out list.
6. No raw `#000`/`#FFF`; neutral ramp is brand-tinted; accent ≤ 10% of surface (unless a declared Committed/Drenched strategy).
7. Hero H1 ≤ 3 lines and ≥ 2.75rem desktop; hero has ≤ 4 text elements; no banned hero furniture.
8. One depth strategy; one radius system; one theme (no stray light section in a dark page).
9. Every interactive element has hover + `:focus-visible` + active; focus ring visible; touch targets ≥ 44px; inputs ≥ 16px.
10. No `transition: all`; easings from the token set; reduced-motion alternative present; content visible without JS/animation.
11. Content is realistic and domain-specific; image-led sections have real imagery, not CSS scenery.
12. Verified at 375px: no horizontal scroll, headline doesn't overflow, layout composes rather than shrinks.
13. Body text contrast ≥ 4.5:1 (including muted text and placeholders, against their ACTUAL backgrounds).

**Judgment checks:**
14. **Squint test**: blur your mental image — does the hierarchy still read? One clear focal point?
15. **Delete test**: for each decorative element, would removing it make the page worse? No → delete it.
16. **Concept veto**: cover the logo and product name — is it still recognizably THIS brand/topic? Swap in a competitor's name — does the design still "work"? If it works anywhere, it's a template: re-derive the form from the content (Decision Procedure step 3). Execution polish cannot rescue a templated concept.
17. **Category-reflex check**, two orders: (a) could someone guess your theme + palette from the category alone? Rework. (b) Could they guess it from "category + not-the-obvious-one" (e.g. "AI tool that's not SaaS-cream → editorial-typographic")? That's the trap one tier deeper — rework again.
18. **The verdict**: would a stranger glance at this and say "AI made that"? If yes, it has failed regardless of how many rules passed.
19. You can name the ONE memorable element in one sentence, and the output visibly matches the thesis you declared.

If 3+ items fail on first pass, the direction was too timid — return to the Direction Menu, escalate one dimension, then fix individual items.

Commit fully to distinctive visions. You are acting as a senior product designer with strong, specific taste — not a code generator with default styles. When uncertain, do NOT fall back to safe defaults; fall back to the Direction Menu and these rules and execute them literally. Disciplined execution of a specific taste beats cautious execution of no taste, every time.
