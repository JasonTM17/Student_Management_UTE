# Phase 01 — Implement and validate the Gemini adapter

## Objective

Create a generated, copy-ready Gemini CLI adapter and prove its static/runtime
catalog integrity without changing other harness behavior.

## Tasks

- [x] Add the Gemini adapter catalog and deterministic generator.
- [x] Generate `.gemini/commands/ak/*.toml` and `.gemini/agents/*.md`.
- [x] Add root `GEMINI.md` and minimal `.gemini/settings.json`.
- [x] Add an exact structural/copy-smoke validator.
- [x] Extend AgentKit project validation and install documentation.
- [x] Regenerate the install manifest and run focused/full gates.
- [x] Run local tester/reviewer-equivalent gates after the independent
  child-agent attempt was blocked by the local usage quota.

## Acceptance criteria

- Generator `--check` reports no drift.
- Validator reports expected command, skill, and agent counts with no invalid
  paths or unsafe Gemini settings.
- A documented minimal-copy fixture resolves every command target.
- Existing harness parity and Wukong gates are unchanged.
- Working tree contains only intentional Gemini adapter, documentation, plan,
  validator, and generated-manifest changes.

## Rollback

Remove the generated `.gemini` tree, `GEMINI.md`, Gemini generator/validator,
documentation additions, and this plan; then regenerate the install manifest.
