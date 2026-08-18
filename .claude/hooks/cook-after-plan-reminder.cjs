#!/usr/bin/env node
/**
 * Plan Subagent Stop Hook - Next Step Reminder
 *
 * Fires when Plan subagent completes. Presents user-choice next steps before implementation.
 * Also outputs full absolute path so new sessions (after /clear) can find the plan in worktrees.
 *
 * Exit Codes:
 *   0 - Success (non-blocking)
 */

// Crash wrapper
try {
  const fs = require('fs');
  const path = require('path');
  const { createSessionStateContext, isHookEnabled, readSessionState, toDisplayPath } = require('./lib/ck-config-utils.cjs');

  // Early exit if hook disabled in config
  if (!isHookEnabled('cook-after-plan-reminder')) {
    process.exit(0);
  }

  const { safeDisplayValue } = require('./lib/session-state-renderer.cjs');

  async function main() {
  try {
    const stdin = fs.readFileSync(0, 'utf-8').trim();
    if (!stdin) process.exit(0);
    let payload = {};
    try {
      payload = JSON.parse(stdin);
    } catch (_) {
      payload = {};
    }

    // Get active plan path from the explicit hook ownership context.
    const sessionContext = createSessionStateContext({
      sessionId: payload.session_id,
      cwd: process['env'].CK_PROJECT_ROOT || payload.cwd || process.cwd(),
      requireBinding: true
    });
    let planPath = null;

    if (sessionContext) {
      const state = readSessionState(sessionContext);
      if (state?.activePlan) {
        planPath = state.activePlan;
        // Ensure it's absolute
        if (!path.isAbsolute(planPath) && state.sessionLaunchRoot) {
          planPath = path.resolve(state.sessionLaunchRoot, planPath);
        }
      }
    }

    // Relevance gate for the Codex/plugin `Stop` registration, which uses a
    // wildcard matcher (hooks.json) and therefore fires on every main-loop turn,
    // not only after planning. With no active plan bound there is nothing to
    // remind about, so exit silently (empty stdout is valid on both runtimes)
    // rather than surfacing the reminder every turn. The native Claude
    // `SubagentStop:Plan` registration is already scoped by its matcher and is
    // intentionally left ungated here so it still fires (with the fallback line)
    // even when the plan path cannot be resolved.
    if (payload.hook_event_name === 'Stop' && !planPath) {
      process.exit(0);
    }

    // Output neutral next-step options with full absolute path if available.
    // Codex Stop hooks require JSON stdout on exit 0; plain text is invalid.
    const lines = [
      'Planning complete. Stop here and ask the user which next step they want: implement, validate, red-team, revise, or end.'
    ];
    if (planPath) {
      // This lands inside a command the model runs verbatim and unquoted, so a
      // backslash path would lose its separators the moment it reaches a shell.
      // path.join hands back native separators; render it before interpolating.
      const planMdPath = toDisplayPath(path.join(planPath, 'plan.md'));
      lines.push(`Optional implementation command after user approval: /ak:cook ${safeDisplayValue(planMdPath)}`);
    } else {
      // Fallback when plan path unavailable
      lines.push('Optional implementation command after user approval: /ak:cook {full-absolute-path-to-plan.md}');
    }
    lines.push('Add --auto only if the user explicitly asks for autonomous implementation.');

    // Codex rejects non-JSON stdout from a Stop hook at exit 0 ("hook returned
    // invalid stop hook JSON output"); Claude Code accepts the same JSON. Emit
    // the non-blocking shape unconditionally instead of gating on payload.model,
    // which was an unreliable runtime discriminator: any path to a plain-text
    // branch (a stale installed hook, or a stdin parse failure leaving payload
    // empty) produced contract-invalid output. Only `continue` and
    // `systemMessage` are emitted — the Codex Stop wire is deny_unknown_fields,
    // and `continue: true` is a no-op (only decision:"block" forces continuation).
    process.stdout.write(JSON.stringify({
      continue: true,
      systemMessage: lines.join('\n')
    }));

    process.exit(0);
  } catch (error) {
    // Silent fail - non-blocking
    process.exit(0);
  }
  }

  main();
} catch (e) {
  // Minimal crash logging (zero deps — only Node builtins)
  try {
    const fs = require('fs');
    const p = require('path');
    const logDir = p.join(__dirname, '.logs');
    if (!fs.existsSync(logDir)) fs.mkdirSync(logDir, { recursive: true });
    fs.appendFileSync(p.join(logDir, 'hook-log.jsonl'),
      JSON.stringify({ ts: new Date().toISOString(), hook: p.basename(__filename, '.cjs'), status: 'crash', error: e.message }) + '\n');
  } catch (_) {}
  process.exit(0); // fail-open
}
