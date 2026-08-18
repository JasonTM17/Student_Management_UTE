# Extraction Best Practices

Guidelines for capturing and analyzing design references effectively.

## Capture Quality Guidelines

### Screenshot Requirements
- High-resolution (minimum 1920px wide for desktop)
- Accurate color reproduction (disable browser extensions that alter colors)
- Actual viewport size, not full-page scrolls
- Device-specific resolutions (desktop 1920x1080, mobile 390x844)
- Multiple states: default, hover, active, responsive breakpoints

### Multiple Examples
- Analyze 3-5 screens minimum for pattern recognition
- Include different page types (home, product, about, contact)
- Single screenshots miss patterns
- Capture from same site to identify consistency

## Analysis Best Practices

### 1. Demand Specifics
âŒ Accept: "Uses blue and gray colors"
âœ“ Demand: "Primary: #1E40AF, Secondary: #6B7280, Accent: #F59E0B"

âŒ Accept: "Modern sans-serif font"
âœ“ Demand: "Inter, weight 600, 48px for h1, tracking -0.02em"

### 2. Document Rationale
Understand *why* design decisions work, not just *what* they are:
- Why does this color palette create trust?
- Why does this spacing scale improve readability?
- Why does this typography hierarchy guide user attention?

### 3. Create Actionable Guidelines
Output should be directly implementable in code:

```css
/* Immediately usable CSS from extraction */
:root {
  --font-display: 'Bebas Neue', sans-serif;
  --font-body: 'Inter', sans-serif;

  --color-primary-600: #1E40AF;
  --color-accent-500: #F59E0B;

  --spacing-xs: 4px;
  --spacing-sm: 8px;
  --spacing-md: 16px;
  --spacing-lg: 24px;

  --radius-sm: 4px;
  --radius-md: 8px;

  --shadow-sm: 0 1px 2px rgba(0,0,0,0.05);
  --shadow-md: 0 4px 6px rgba(0,0,0,0.1);
}
```

### 4. Cross-Reference
- Validate font predictions against Google Fonts library
- Use font identification tools (WhatFont, Font Ninja) for accuracy
- Manually verify extracted colors with eyedropper tools

### 5. Iterate Analysis
First pass may miss subtleties:
- Run initial comprehensive analysis
- Review output, identify gaps
- Run focused follow-up queries for specific elements

## Advanced Techniques

### Design System Mining
Extract complete design system from single brand (10+ screens):

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix gemini analyze \
  --files docs/inspiration/brand/*.png \
  --prompt "Extract complete, production-ready design system:
- All color tokens (20+ colors)
- All typography specs (sizes, weights, line-heights)
- All spacing tokens
- All component variants
- All animation timings
Output as CSS variables ready for implementation." \
  --output docs/design-guidelines/brand-design-system.md \
  --model gemini-2.5-flash
```

### Trend Analysis
Analyze multiple top designs to identify current trends:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix gemini analyze \
  --files docs/inspiration/awwwards-*.png \
  --prompt "Trend analysis across 10 award-winning designs:
1. Dominant aesthetic movements
2. Common color strategies
3. Typography trends
4. Layout innovations
5. Animation patterns
Identify what's trending in 2024 web design." \
  --output docs/design-guidelines/trend-analysis.md \
  --model gemini-2.5-flash
```

### Historical Evolution
Track design evolution of single brand over time:

```bash
npx -y -p @mrgoonie/multix@0.2.0 multix gemini analyze \
  --files docs/inspiration/brand-2020.png docs/inspiration/brand-2024.png \
  --prompt "Compare 2020 vs 2024 design evolution:
1. What changed and why
2. What remained consistent (brand identity)
3. How trends influenced changes
4. Lessons for our design evolution" \
  --output docs/design-guidelines/evolution-analysis.md \
  --model gemini-2.5-flash
```

## Common Pitfalls

### âŒ Surface-Level Analysis
"Uses blue colors and sans-serif fonts"
**Fix**: Demand specificsâ€”hex codes, font names, size values

### âŒ Missing Context
Extracting design without understanding target audience or purpose
**Fix**: Research brand context before analysis

### âŒ Blind Copying
Extracting and applying design 1:1 to your project
**Fix**: Extract principles, adapt to your unique context

### âŒ Single Source
Learning from one example only
**Fix**: Analyze 3-5 examples to identify patterns vs. anomalies
