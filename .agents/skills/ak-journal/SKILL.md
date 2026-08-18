---
name: ak:journal
description: "Write chronological technical journals for session reflection and change analysis. Journals preserve work history; they do not replace current docs or ADRs."
user-invocable: true
when_to_use: "Invoke for technical session reflection or chronological work records."
category: utilities
keywords: [journal, reflection, changes, session]
argument-hint: "[topic or reflection]"
metadata:
  author: agentkit
  version: "1.1.0"
---

# Journal

Capture a concise technical journal for the current session, then persist it with the first-class CLI.

Journals are work history under `<project>/plans/journals/`. They are not durable product or decision authority — record lasting decisions in the project's ADR or current docs owner.

## Workflow

1. Gather the important events: root cause, key changes, impacts, decisions, and next steps.
2. Draft a short title and body (markdown). Prefer concrete errors, paths, and outcomes over vague summaries.
3. Persist with the CLI (scriptable; no `$EDITOR`):

```bash
ak journal create "<title>" --summary "<one-line summary>" --stdin <<'EOF'
## What happened
...

## Decision
...

## Next steps
...
EOF
```

Optional flags: `--date YYYY-MM-DD`, `--project <registry-name>`.

4. Validate when needed:

```bash
ak journal validate <slug-or-filename-stem>
```

5. AgentWiki publish from this skill is **deferred**. Report `AgentWiki publish skipped` and keep the local file as the source of truth.

6. Browse existing entries with `ak journal list` / `ak journal show <slug>`, or the Journals page in desktop/dashboard.

**Optional:** Invoke the `journal-writer` subagent when emotional honesty and failure archaeology are the point of the entry; still persist through `ak journal create`.

## Naming

Created files use `YYYY-MM-DD-<slug>.md` with `-2`, `-3`, … collision suffixes.

## Workflow Position

**Typically follows:** `ak:ship` (journal after shipping), `/ak:cook` (journal after implementation), `/ak:fix` (journal after bug fix)
**Terminal skill** — no typical successor.
