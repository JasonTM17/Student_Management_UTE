# Vision Understanding

Use the exact-pinned Multix invocation from `../SKILL.md`. Resolve current
model availability, image formats, inline/upload boundaries, context use,
retention, and pricing from live provider documentation.

## Workflow

1. Define the evidence needed: OCR, layout, object/scene description, visual
   comparison, accessibility review, or structured extraction.
2. Inspect the pinned analysis command with `--help`.
3. Preserve source resolution needed for small text or fine details.
4. Ask the model to separate visible evidence, uncertainty, and inference.
5. For multiple images, label inputs and request explicit cross-image mapping.
6. Verify critical text, measurements, identities, and safety conclusions
   against the original image.

```bash
<pinned-multix> gemini analyze \
  --files input.png \
  --prompt "Extract visible text and describe uncertain regions separately" \
  --format markdown \
  --output analysis.md
```

Do not store token counts, cost examples, file-expiry windows, or model defaults
in this reference.
