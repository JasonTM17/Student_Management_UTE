# Retired Gemini CLI Integration

Gemini CLI dispatch is retired in AgentKit. Do not install or invoke the old
binary for MCP discovery, research, scouting, or tool execution.

This file intentionally keeps its historical path. Existing AgentKit updates
are non-destructive by default, so rewriting the manifest-owned reference in
place avoids leaving an actionable stale guide on upgraded installations.

## Supported Migration

Use one of the active paths in the parent skill:

1. Prefer MCP tools already registered with the current Claude Code or Codex runtime.
2. Use `scripts/cli.ts` for deterministic access to servers declared only in the active adapter MCP JSON (for Codex, `.codex/.mcp.json`).
3. Use `ak:chrome-profile` before Chrome DevTools MCP when real profile state matters.

Legacy `.claude/.ck.json` keys such as `gemini.model` and
`skills.research.useGemini` remain accepted as harmless compatibility input.
They do not enable CLI dispatch.

Antigravity (`agy`) remains AgentKit's third-runtime direction. It is not an
AgentKit install target or a substitute MCP bridge until its adapter and config
contract pass the repository's verification gate.
