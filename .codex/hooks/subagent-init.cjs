#!/usr/bin/env node
/**
 * SubagentStart Hook - Injects context to subagents (Optimized)
 *
 * Fires: When a subagent (Task tool call) is started
 * Purpose: Inject minimal context using env vars from SessionStart
 * Target: ~200 tokens (down from ~350)
 *
 * Exit Codes:
 *   0 - Success (non-blocking, allows continuation)
 */

// Crash wrapper
try {
  const fs = require('fs');
  const path = require('path');
  const {
    loadConfig,
    createSessionStateContext,
    resolveNamingPattern,
    getGitBranch,
    getGitRoot,
    resolvePlanPath,
    getReportsPath,
    normalizePath,
    toDisplayPath,
    extractTaskListId,
    isHookEnabled
  } = require('./lib/ck-config-utils.cjs');
  const { resolveSkillsVenv } = require('./lib/context-builder.cjs');
  const { createHookTimer, logHookCrash } = require('./lib/hook-logger.cjs');
  const { safeDisplayValue } = require('./lib/session-state-renderer.cjs');

  // Early exit if hook disabled in config
  if (!isHookEnabled('subagent-init')) {
    process.exit(0);
  }

/**
 * Get agent-specific context from config
 */
function getAgentContext(agentType, config) {
  const agentConfig = config.subagent?.agents?.[agentType];
  if (!agentConfig?.contextPrefix) return null;
  return agentConfig.contextPrefix;
}

// Agent types that interact with plan status updates or save plan-scoped reports
const PLAN_AWARE_AGENTS = new Set([
  'planner', 'project-manager', 'code-simplifier',
  'fullstack-developer'
]);

/**
 * Route plan-aware agents to the live CLI contract instead of copying syntax.
 */
function buildPlanCliSection(agentType) {
  if (!PLAN_AWARE_AGENTS.has(agentType)) return [];
  return [
    ``,
    `## Plan state`,
    `Run \`ak plan --help\` before changing plan status, then follow the current CLI contract.`,
    `Do not edit plan status cells directly.`
  ];
}

/**
 * Build trust verification section if enabled
 */
function buildTrustVerification(config) {
  if (!config.trust?.enabled || !config.trust?.passphrase) return [];
  return [
    ``,
    `## Trust Verification`,
    `Passphrase: "${config.trust.passphrase}"`
  ];
}

/**
 * Main hook execution
 */
async function main() {
  const timer = createHookTimer('subagent-init', { event: 'SubagentStart' });
  let agentType = 'unknown';
  try {
    const stdin = fs.readFileSync(0, 'utf-8').trim();
    if (!stdin) {
      timer.end({ status: 'skip', exit: 0, note: 'empty-input' });
      process.exit(0);
    }

    const payload = JSON.parse(stdin);
    agentType = payload.agent_type || 'unknown';
    const agentId = payload.agent_id || 'unknown';

    // Load config for trust verification, naming, and agent-specific context
    const config = loadConfig({ includeProject: false, includeAssertions: false });

    // Use payload.cwd if provided for git operations (monorepo support)
    // This ensures subagent resolves paths relative to its own CWD, not process.cwd()
    // Trim so whitespace-only payload paths fall back safely.
    const effectiveCwd = payload.cwd?.trim() || process.cwd();

    // Compute naming pattern directly (don't rely on env vars which may not propagate)
    // Pass effectiveCwd to git commands to support monorepo/submodule scenarios
    const gitBranch = getGitBranch(effectiveCwd);
    const gitRoot = getGitRoot(effectiveCwd);
    // Use CWD as the base for subdirectory workflow support.
    // Git root is kept for reference but CWD determines where files are created
    const baseDir = effectiveCwd;

    // Debug logging for path resolution troubleshooting
    if (process['env'].CK_DEBUG) {
      console.error(`[subagent-init] effectiveCwd=${effectiveCwd}, gitRoot=${gitRoot}, baseDir=${baseDir}`);
    }
    const namePattern = resolveNamingPattern(config.plan, gitBranch);

    // Resolve plan and report paths absolutely from CWD.
    // Use the payload session ID to resolve active plan context.
    const sessionContext = createSessionStateContext({
      sessionId: payload.session_id,
      cwd: process['env'].CK_PROJECT_ROOT || effectiveCwd,
      requireBinding: true
    });
    const resolved = resolvePlanPath(sessionContext, config);
    const reportsPath = getReportsPath(resolved.path, resolved.resolvedBy, config.plan, config.paths, baseDir);
    const activePlan = resolved.resolvedBy === 'session' ? resolved.path : '';
    const suggestedPlan = resolved.resolvedBy === 'branch' ? resolved.path : '';

    // Extract task list ID for Claude Code Tasks coordination (shared helper, DRY)
    const taskListId = extractTaskListId(resolved);
    // Rendered at construction: both are emitted straight into the prompt below
    // and joined onto further down, so normalising once here keeps every use in
    // display form instead of relying on each caller to remember.
    const plansPath = toDisplayPath(path.join(baseDir, normalizePath(config.paths?.plans) || 'plans'));
    const docsPath = toDisplayPath(path.join(baseDir, normalizePath(config.paths?.docs) || 'docs'));
    const thinkingLanguage = config.locale?.thinkingLanguage || '';
    const responseLanguage = config.locale?.responseLanguage || '';
    // Auto-default thinkingLanguage to 'en' when only responseLanguage is set
    const effectiveThinking = thinkingLanguage || (responseLanguage ? 'en' : '');

    // Build compact context (~200 tokens)
    const lines = [];

    // Subagent identification
    lines.push(`## Subagent: ${safeDisplayValue(agentType)}`);
    lines.push(`ID: ${safeDisplayValue(agentId)} | CWD: ${safeDisplayValue(effectiveCwd)}`);
    lines.push(``);

    // Plan context (from env vars)
    lines.push(`## Context`);
    if (activePlan) {
      lines.push(`- Plan: ${safeDisplayValue(activePlan)}`);
      if (taskListId) {
        lines.push(`- Task List: ${taskListId} (shared with session)`);
      }
    } else if (suggestedPlan) {
      lines.push(`- Plan: none | Suggested: ${safeDisplayValue(suggestedPlan)}`);
    } else {
      lines.push(`- Plan: none`);
    }
    lines.push(`- Reports: ${safeDisplayValue(reportsPath)}`);
    lines.push(`- Paths: ${safeDisplayValue(plansPath)} | ${safeDisplayValue(docsPath)}`);
    lines.push(``);

    // Language (thinking + response, if configured)
    const hasThinking = effectiveThinking && effectiveThinking !== responseLanguage;
    if (hasThinking || responseLanguage) {
      lines.push(`## Language`);
      if (hasThinking) {
        lines.push(`- Thinking: Use ${effectiveThinking} for reasoning (logic, precision).`);
      }
      if (responseLanguage) {
        lines.push(`- Response: Respond in ${responseLanguage} (natural, fluent).`);
      }
      lines.push(``);
    }

    // Resolve Python venv path for subagent instructions
    const skillsVenv = resolveSkillsVenv('.codex');

    // Core rules (minimal)
    lines.push(`## Rules`);
    lines.push(`- Reports → ${reportsPath}`);
    lines.push(`- YAGNI / KISS / DRY`);
    lines.push(`- Concise, list unresolved Qs at end`);
    // Python venv rules (if venv exists)
    if (skillsVenv) {
      lines.push(`- Python skill scripts: Use \`${skillsVenv}\``);
      lines.push(`- Never use global pip install`);
    }

    // Naming templates (computed directly for reliable injection)
    lines.push(``);
    lines.push(`## Naming`);
    // path.join renders native separators, which would undo the display form the
    // path already arrived in. These two lines are read by the model and handed
    // back to a tool call, so they stay forward-slash on Windows too.
    lines.push(`- Report: ${toDisplayPath(path.join(reportsPath, `${agentType}-${namePattern}.md`))}`);
    lines.push(`- Plan dir: ${toDisplayPath(path.join(plansPath, namePattern))}/`);

    // Add plan CLI commands for plan-aware agents.
    lines.push(...buildPlanCliSection(agentType));

    // Trust verification (if enabled)
    lines.push(...buildTrustVerification(config));

    // Agent-specific context (if configured)
    const agentContext = getAgentContext(agentType, config);
    if (agentContext) {
      lines.push(``);
      lines.push(`## Agent Instructions`);
      lines.push(agentContext);
    }

    // CRITICAL: SubagentStart requires hookSpecificOutput.additionalContext format
    const output = {
      hookSpecificOutput: {
        hookEventName: "SubagentStart",
        additionalContext: lines.join('\n')
      }
    };

    console.log(JSON.stringify(output));
    timer.end({ status: 'ok', exit: 0, target: agentType, note: 'context-injected' });
    process.exit(0);
  } catch (error) {
    console.error(`SubagentStart hook error: ${error.message}`);
    logHookCrash('subagent-init', error, { event: 'SubagentStart', target: agentType });
    process.exit(0); // Fail-open
  }
  }

  main();
} catch (e) {
  try {
    const { logHookCrash } = require('./lib/hook-logger.cjs');
    logHookCrash('subagent-init', e, { event: 'SubagentStart' });
  } catch (_) {}
  process.exit(0); // fail-open
}
