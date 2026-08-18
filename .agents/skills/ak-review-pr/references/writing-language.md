# Writing language for GitHub prose

Shared by `ak:ship` and `ak:review-pr` (#1195).

## Resolve before authoring

```bash
WL_BIN=.codex/hooks/lib/writing-language.cjs
test -f "$WL_BIN" || { echo "Missing AgentKit Codex hooks; copy .codex alongside .agentkit."; exit 1; }
node "$WL_BIN" --json
```

Use `language` from the JSON for all human-facing GitHub prose. If
`fallbackReason` is set, state the fallback explicitly in the PR/review body
(do not claim the unsupported tag was honored).

## Precedence

1. `AGENTKIT_LANGUAGE`
2. `CK_RESPONSE_LANGUAGE`
3. Resolved AgentKit preferences from `<project>/.agentkit/config.yaml`, then
   `$AGENTKIT_HOME/config.yaml` (default `~/.agentkit/config.yaml`) →
   `locale.response_language`
4. Default: `en`

The resolver deliberately does not scrape legacy `.ck.json` files. The `ak`
CLI owns the merged YAML preferences, so every runtime sees the same project
configuration.

## What is localized

- PR description headings and prose (`ak:ship`)
- Review Summary / Risk / Findings / Verdict / handoff text (`ak:review-pr`)
- Checklist labels and human-action requests

## What stays intact

- Conventional-commit **PR titles** (English)
- Code, commands, paths, URLs, identifiers
- GitHub keywords (`Closes #123`, `Relates to #456`)
- User-provided quotes, issue titles, error output, evidence blobs

## Invalid tags

Normalize to lowercase BCP47-like `/^[a-z]{2,3}(-[a-z0-9]+)*$/`. Invalid
candidates are **skipped** so lower-precedence sources can still win. If every
configured value is invalid/empty, use `en` and record `fallbackReason` plus
`rejected[]`.

## YAML parser limits

The resolver uses a minimal line parser (no YAML dependency). Supported shapes:

```yaml
language: vi
locale:
  response_language: vi
```

Inline maps like `locale: { response_language: vi }` are **not** read. Prefer
block form until #1093 ships a full YAML consumer.
