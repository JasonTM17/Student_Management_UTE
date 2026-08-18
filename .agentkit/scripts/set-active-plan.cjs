#!/usr/bin/env node
'use strict';

/**
 * Select the active AgentKit plan without depending on one adapter name.
 *
 * Usage: node .agentkit/scripts/set-active-plan.cjs <plan-dir-or-plan.md>
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

function samePath(left, right) {
  const a = path.resolve(left);
  const b = path.resolve(right);
  return process.platform === 'win32' ? a.toLowerCase() === b.toLowerCase() : a === b;
}

function canonicalProjectRoot(value) {
  const absolute = path.resolve(value);
  try {
    return fs.realpathSync.native(absolute);
  } catch {
    return absolute;
  }
}

function regularRealFile(file) {
  try {
    const stat = fs.lstatSync(file);
    return stat.isFile() && !stat.isSymbolicLink()
      && samePath(fs.realpathSync.native(file), file);
  } catch {
    return false;
  }
}

function regularRealDirectory(directory) {
  try {
    const stat = fs.lstatSync(directory);
    return stat.isDirectory() && !stat.isSymbolicLink()
      && samePath(fs.realpathSync.native(directory), directory);
  } catch {
    return false;
  }
}

function hookCandidates(root) {
  return [
    '.codex/hooks/lib/ck-config-utils.cjs',
    '.claude/hooks/lib/ck-config-utils.cjs',
    '.cursor/hooks/lib/ck-config-utils.cjs',
    'engineer/.codex/hooks/lib/ck-config-utils.cjs',
  ].map((relative) => ({ relative, absolute: path.join(root, ...relative.split('/')) }));
}

const installedProjectRoot = canonicalProjectRoot(path.resolve(__dirname, '..', '..'));

function candidateProjectRoots() {
  // The wrapper is installed below <project>/.agentkit/scripts.  Prefer that
  // project and the actual cwd before adapter environment hints: desktop apps
  // can leave a still-existing CLAUDE_PROJECT_DIR from another workspace.
  const roots = [
    installedProjectRoot,
    process.cwd(),
    process.env.CLAUDE_PROJECT_DIR,
    process.env.CODEX_PROJECT_DIR,
    process.env.CURSOR_PROJECT_DIR,
    process.env.CK_PROJECT_ROOT,
  ];
  const seen = new Set();
  return roots.flatMap((value, index) => {
    if (typeof value !== 'string' || !value.trim()) return [];
    if (index >= 2 && !path.isAbsolute(value)) return [];
    // Hosted runners and desktop tools may expose the same project through a
    // junction or symlinked ancestor.  Canonicalize the root once, then keep
    // the existing link-free checks strict for every path below that root.
    const resolved = canonicalProjectRoot(value);
    const identity = process.platform === 'win32' ? resolved.toLowerCase() : resolved;
    if (seen.has(identity)) return [];
    seen.add(identity);
    return [resolved];
  });
}

let projectRoot = null;
let available = [];
const installedHookCandidates = hookCandidates(installedProjectRoot);
const installedHooks = installedHookCandidates.filter((candidate) => regularRealFile(candidate.absolute));
const geminiValidationOnly = installedHooks.length === 0
  && regularRealFile(path.join(installedProjectRoot, '.gemini', 'settings.json'))
  && regularRealDirectory(path.join(installedProjectRoot, '.agents', 'skills'));
const opencodeValidationOnly = installedHooks.length === 0
  && !geminiValidationOnly
  && regularRealFile(path.join(installedProjectRoot, '.opencode', 'opencode.json'))
  && regularRealDirectory(path.join(installedProjectRoot, '.agents', 'skills'));

if (geminiValidationOnly || opencodeValidationOnly) {
  // Gemini and OpenCode consume .agents/skills directly, but these adapters
  // intentionally ship no Claude/Codex hook runtime. Keep plan selection
  // useful and honest: validate below, but never claim session persistence
  // without a state bridge.
  projectRoot = canonicalProjectRoot(installedProjectRoot);
} else {
  for (const root of candidateProjectRoots()) {
    const rootCandidates = hookCandidates(root);
    const rootAvailable = rootCandidates.filter((candidate) => regularRealFile(candidate.absolute));
    if (rootAvailable.length) {
      projectRoot = root;
      available = rootAvailable;
      break;
    }
  }
}

if (!projectRoot) {
  console.error('Error: no project-local AgentKit hook runtime was found.');
  console.error('Expected one of .codex/hooks, .claude/hooks, or .cursor/hooks.');
  process.exit(1);
}

let sessionContext = null;
let updateSessionState = null;
if (available.length) {
  const digests = new Set(available.map((candidate) => (
    crypto.createHash('sha256').update(fs.readFileSync(candidate.absolute)).digest('hex')
  )));
  if (digests.size !== 1) {
    console.error('Error: installed AgentKit hook mirrors differ; active-plan state is ambiguous.');
    process.exit(1);
  }

  const stateRuntime = require(available[0].absolute);
  updateSessionState = stateRuntime.updateSessionState;
  sessionContext = stateRuntime.createSessionStateContext({
    sessionId: process.env.CK_SESSION_ID,
    cwd: projectRoot,
    requireBinding: true,
  });
}
const newPlan = process.argv[2];

if (!newPlan) {
  console.error('Error: plan path required');
  console.log('Usage: node .agentkit/scripts/set-active-plan.cjs <plan-dir-or-plan.md>');
  console.log('Example: node .agentkit/scripts/set-active-plan.cjs plans/2026-08-13-feature-name/');
  process.exit(1);
}

const launchRoot = sessionContext?.sessionLaunchRoot || projectRoot;
const requestedPath = path.resolve(launchRoot, newPlan);
const relativeRequestedPath = path.relative(launchRoot, requestedPath);
if (
  !relativeRequestedPath
  || relativeRequestedPath === '..'
  || relativeRequestedPath.startsWith(`..${path.sep}`)
  || path.isAbsolute(relativeRequestedPath)
) {
  console.error('Error: active plan must be below the session launch directory.');
  process.exit(1);
}
const planDir = path.basename(requestedPath).toLowerCase() === 'plan.md'
  ? path.dirname(requestedPath)
  : requestedPath;
const phaseFiles = regularRealDirectory(planDir)
  ? fs.readdirSync(planDir, { withFileTypes: true })
    .filter((entry) => entry.isFile() && /^phase-\d{2}-[a-z0-9][a-z0-9._-]*\.md$/i.test(entry.name))
    .map((entry) => path.join(planDir, entry.name))
    .filter(regularRealFile)
  : [];
if (!regularRealDirectory(planDir) || !regularRealFile(path.join(planDir, 'plan.md')) || !phaseFiles.length) {
  console.error('Error: active plan must be a real directory containing plan.md and at least one real phase-NN-*.md file.');
  process.exit(1);
}
const relativePlan = path.relative(launchRoot, planDir);

if (!sessionContext) {
  console.warn(geminiValidationOnly
    ? 'Warning: Gemini adapter has no reviewed session-state bridge; active plan state will not persist.'
    : opencodeValidationOnly
      ? 'Warning: OpenCode adapter has no reviewed session-state bridge; active plan state will not persist.'
      : 'Warning: CK_SESSION_ID is absent; active plan state will not persist.');
  console.log(`Validated active plan: ${relativePlan.split(path.sep).join('/')}`);
  process.exit(0);
}

const success = updateSessionState(sessionContext, (current) => ({
  ...current,
  activePlan: planDir,
  timestamp: Date.now(),
}));

if (!success) {
  console.error('Failed to update active plan state.');
  process.exit(1);
}
console.log(`Active plan set: ${relativePlan.split(path.sep).join('/')}`);
