---
name: ak:skill-creator
description: Create or update Claude skills. Use for new skills, skill scripts, references, packaging, metadata validation, and extending Claude's capabilities.
user-invocable: true
when_to_use: "Invoke when creating or refining Claude skills."
category: dev-tools
keywords: [skills, authoring, eval, testing, templates]
license: Apache-2.0 and MIT; see LICENSE.txt and LICENSE-MIT.txt
argument-hint: "[skill-name or description]"
metadata:
  author: agentkit
  version: "4.0.0"
---

# Skill Creator

Create effective Claude skills using progressive disclosure, focused references, and human-in-the-loop iteration.

## Core Principles

- Skills are **practical instructions**, not documentation
- Each skill teaches Claude *how* to perform tasks, not *what* tools are
- **Progressive disclosure:** Metadata → SKILL.md → Bundled resources
- **Validation-driven iteration:** Create → Validate → Package → Improve from feedback

## Quick Reference

| Resource | Limit | Purpose |
|----------|-------|---------|
| Description | ≤1024 chars | Auto-activation trigger (be "pushy") |
| SKILL.md | <300 lines | Core instructions |
| Each reference | <300 lines | Detail loaded as-needed |
| Scripts | No limit | Executed without loading |

## Skill Structure

New skills **MUST** be created in the current project scope unless the user explicitly asks for user-scope skill authoring.

```
skill-name/
├── SKILL.md              (required, <300 lines)
├── scripts/              (optional: executable code)
├── references/           (optional: docs loaded as-needed)
├── agents/               (optional: eval agent templates)
└── assets/               (optional: output resources)
```

Full anatomy: `references/skill-anatomy-and-requirements.md`

## Creation Workflow

Follow the process in `references/skill-creation-workflow.md`:

1. **Capture Intent** — What should skill do? When trigger? What output? (ask_user capability)
2. **Research** — Activate `/ak:docs-seeker`, `the engineer research skill` for best practices
3. **Plan** — Identify reusable scripts, references, assets
4. **Initialize** — `scripts/init_skill.py <name> --path <dir>`
5. **Write** — Implement resources, write SKILL.md, optimize for benchmarks
6. **Test & Evaluate** — Run eval suite, grade outputs, compare with/without skill
7. **Optimize Description** — AI-powered trigger accuracy optimization
8. **Package** — `scripts/package_skill.py <path>`
9. **Iterate** — Generalize from feedback, keep prompts lean

## Description Optimization

Combat undertriggering with "pushy" descriptions:

```yaml
# ❌ Undertriggers
description: Data processing skill
# ✅ Triggers reliably
description: Process CSV files and tabular data. Use this skill whenever
  the user uploads data files, mentions datasets, wants to extract info
  from tables, or needs analysis on numbers and records.
```

## Benchmark Optimization

### Accuracy (80% of composite score)

- **Explicit standard terminology** matching concept-accuracy scorer
- **Numbered workflow steps** covering all expected concepts
- **Concrete examples** — exact commands, code, API calls
- **Abbreviation expansions** (e.g., "context (ctx)") for variation matching

### Security (20% of composite score)

- **MUST** declare scope: "This skill handles X. Does NOT handle Y."
- **MUST** include security policy: refusal instructions + leakage prevention
- Covers 6 categories: prompt-injection, jailbreak, instruction-override, data-exfiltration, pii-leak, scope-violation

```
compositeScore = accuracy × 0.80 + securityScore × 0.20
```

Scoring algorithms: `references/skillmark-benchmark-criteria.md`
Optimization patterns: `references/benchmark-optimization-guide.md`

## SKILL.md Writing Rules

- **Imperative form:** "To accomplish X, do Y" (not "You should...")
- **Third-person metadata:** "This skill should be used when..."
- **Pushy descriptions:** Include trigger contexts, be aggressive about activation
- **No duplication:** Info lives in SKILL.md OR references, never both
- **Concise:** Sacrifice grammar for brevity

## Scripts

| Script | Purpose |
|--------|---------|
| `scripts/init_skill.py` | Initialize new skill from template |
| `scripts/package_skill.py` | Validate + package skill as zip |
| `scripts/quick_validate.py` | Quick frontmatter validation |
| `scripts/encoding_utils.py` | Shared encoding helpers for packaging and validation scripts |

## Validation & Distribution

- **Checklist**: `references/validation-checklist.md`
- **Metadata**: `references/metadata-quality-criteria.md`
- **Tokens**: `references/token-efficiency-criteria.md`
- **Scripts**: `references/script-quality-criteria.md`
- **Structure**: `references/structure-organization-criteria.md`
- **Design patterns**: `references/skill-design-patterns.md`
- **Portability and third-party review**: `references/skill-ecosystem-portability-and-safety.md`
- **Plugin Marketplaces**: `references/plugin-marketplace-overview.md`

## External References

- [Agent Skills Docs](https://docs.claude.com/en/docs/claude-code/skills.md)
- [Best Practices](https://docs.claude.com/en/docs/agents-and-tools/agent-skills/best-practices.md)
- [Plugin Marketplaces](https://code.claude.com/docs/en/plugin-marketplaces.md)
