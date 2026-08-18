'use strict';

// Portable runtime paths for AgentKit JavaScript skill scripts.  Do not infer
// a provider from a hard-coded directory: use the project adapter that is
// actually installed around the current task or skill.

const fs = require('fs');
const os = require('os');
const path = require('path');

const ADAPTER_DIR_NAMES = ['.codex', '.claude', '.cursor', '.agents'];
const PROJECT_MARKERS = ['.git', 'package.json', 'pyproject.toml', 'Cargo.toml', 'go.mod'];

function ancestors(start) {
  const values = [];
  let current = path.resolve(start);
  for (;;) {
    values.push(current);
    const parent = path.dirname(current);
    if (parent === current) return values;
    current = parent;
  }
}

function isProjectRoot(directory) {
  return PROJECT_MARKERS.some((marker) => fs.existsSync(path.join(directory, marker)))
    || ADAPTER_DIR_NAMES.some((adapter) => fs.existsSync(path.join(directory, adapter)));
}

function findProjectRoot(start = process.cwd()) {
  return ancestors(start).find(isProjectRoot) || path.resolve(start);
}

function projectRootForSkill(skillDir) {
  return ancestors(process.cwd()).find(isProjectRoot) || findProjectRoot(skillDir);
}

function adapterDirectory(skillDir, projectRoot = projectRootForSkill(skillDir)) {
  const requested = String(process.env.AGENTKIT_ADAPTER || '').trim();
  for (const adapter of [requested, ...ADAPTER_DIR_NAMES]) {
    if (ADAPTER_DIR_NAMES.includes(adapter)) {
      const candidate = path.join(projectRoot, adapter);
      if (fs.existsSync(candidate)) return candidate;
    }
  }
  return ancestors(skillDir).find((directory) => ADAPTER_DIR_NAMES.includes(path.basename(directory))) || null;
}

function environmentPaths(skillDir, { projectRoot = projectRootForSkill(skillDir), includeUserRuntime = true } = {}) {
  const adapter = adapterDirectory(skillDir, projectRoot);
  const candidates = [
    ...(includeUserRuntime ? [path.join(os.homedir(), '.agentkit', '.env')] : []),
    path.join(projectRoot, '.env'),
    ...(adapter ? [path.join(adapter, '.env'), path.join(adapter, 'skills', '.env')] : []),
    path.join(skillDir, '.env'),
  ];
  return [...new Set(candidates.map((candidate) => path.resolve(candidate)))];
}

module.exports = {
  ADAPTER_DIR_NAMES,
  adapterDirectory,
  environmentPaths,
  findProjectRoot,
  projectRootForSkill,
};
