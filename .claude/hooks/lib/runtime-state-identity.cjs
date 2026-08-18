'use strict';

const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

const MAX_SESSION_ID_LENGTH = 200;
const SAFE_SESSION_ID_PATTERN = /^[A-Za-z0-9_-][A-Za-z0-9._-]*$/;
const RUNTIME_MARKER_MAX_BYTES = 4096;
const RUNTIME_MARKER_FILE = '.agentkit-runtime.json';
const SUPPORTED_RUNTIMES = new Set(['claude-code', 'codex']);

function normalizeSessionId(sessionId) {
  if (typeof sessionId !== 'string') return null;
  const normalized = sessionId.trim();
  if (!normalized || normalized.length > MAX_SESSION_ID_LENGTH) return null;
  if (normalized === '.' || normalized === '..') return null;
  return SAFE_SESSION_ID_PATTERN.test(normalized) ? normalized : null;
}

function stableHash(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function normalizeProjectPath(value, platform = process.platform) {
  let normalized = path.normalize(value);
  if (platform === 'win32') {
    normalized = normalized.replace(/\\/g, '/').toLowerCase();
  }
  return normalized;
}

function pathContains(parent, child, platform = process.platform) {
  const normalizedParent = normalizeProjectPath(parent, platform);
  const normalizedChild = normalizeProjectPath(child, platform);
  const relative = path.relative(normalizedParent, normalizedChild);
  return relative === '' || (!relative.startsWith('..') && !path.isAbsolute(relative));
}

function gitEnvironment(environment = process.env) {
  const clean = { ...environment };
  for (const key of Object.keys(clean)) {
    if (key === 'GIT_CONFIG_COUNT' || key.startsWith('GIT_')) delete clean[key];
  }
  return clean;
}

function resolveCanonicalProjectRoot(cwd, dependencies = {}) {
  const platform = dependencies.platform || process.platform;
  const realpath = dependencies.realpath || fs.realpathSync.native;
  const runGit = dependencies.runGit || ((workingDirectory) => execFileSync(
    'git',
    ['rev-parse', '--show-toplevel'],
    {
      cwd: workingDirectory,
      env: gitEnvironment(),
      encoding: 'utf8',
      timeout: 3000,
      stdio: ['ignore', 'pipe', 'ignore'],
      windowsHide: true
    }
  ).trim());

  try {
    const canonicalCwd = realpath(path.resolve(cwd));
    let candidate = canonicalCwd;
    try {
      const gitRoot = runGit(canonicalCwd);
      if (gitRoot) {
        const canonicalGitRoot = realpath(path.resolve(gitRoot));
        if (pathContains(canonicalGitRoot, canonicalCwd, platform)) candidate = canonicalGitRoot;
      }
    } catch { /* non-Git or redirected root: use cwd */ }
    return normalizeProjectPath(candidate, platform);
  } catch {
    return null;
  }
}

function defaultRuntimeMarkerPath(moduleDirectory = __dirname) {
  return path.join(moduleDirectory, '..', RUNTIME_MARKER_FILE);
}

function readRuntimeMarker(markerPath = defaultRuntimeMarkerPath()) {
  try {
    const info = fs.lstatSync(markerPath);
    if (!info.isFile() || info.isSymbolicLink() || info.size > RUNTIME_MARKER_MAX_BYTES) return null;
    const data = fs.readFileSync(markerPath, 'utf8');
    if (Buffer.byteLength(data, 'utf8') > RUNTIME_MARKER_MAX_BYTES) return null;
    const parsed = JSON.parse(data);
    if (parsed?.schemaVersion !== 1 || !SUPPORTED_RUNTIMES.has(parsed.runtime)) return null;
    return parsed.runtime;
  } catch {
    return null;
  }
}

function currentUserKey() {
  if (typeof process.getuid === 'function') return `uid-${process.getuid()}`;
  try {
    return `user-${stableHash(os.userInfo().username).slice(0, 24)}`;
  } catch {
    return null;
  }
}

function createCandidateSessionStateContext(options = {}) {
  const sessionId = normalizeSessionId(options.sessionId);
  const runtime = options.runtime || readRuntimeMarker(options.markerPath);
  let sessionLaunchRoot = null;
  try {
    const realpath = options.dependencies?.realpath || fs.realpathSync.native;
    sessionLaunchRoot = normalizeProjectPath(
      realpath(path.resolve(options.cwd || process.cwd())),
      options.dependencies?.platform
    );
  } catch { /* invalid launch directory */ }
  const canonicalProjectRoot = sessionLaunchRoot
    ? resolveCanonicalProjectRoot(sessionLaunchRoot, options.dependencies)
    : null;
  const userKey = options.userKey || currentUserKey();
  if (!sessionId || !SUPPORTED_RUNTIMES.has(runtime) || !canonicalProjectRoot || !sessionLaunchRoot || !userKey) return null;

  return Object.freeze({
    schemaVersion: 2,
    runtime,
    canonicalProjectRoot,
    sessionLaunchRoot,
    projectKey: stableHash(canonicalProjectRoot),
    normalizedSessionId: sessionId,
    sessionKey: stableHash(sessionId),
    userKey,
    storageRoot: options.storageRoot ? path.resolve(options.storageRoot) : null
  });
}

function isSessionStateContext(value) {
  return Boolean(
    value && value.schemaVersion === 2 && SUPPORTED_RUNTIMES.has(value.runtime) &&
    typeof value.canonicalProjectRoot === 'string' &&
    typeof value.sessionLaunchRoot === 'string' &&
    pathContains(value.canonicalProjectRoot, value.sessionLaunchRoot) &&
    value.projectKey === stableHash(value.canonicalProjectRoot) &&
    normalizeSessionId(value.normalizedSessionId) === value.normalizedSessionId &&
    value.sessionKey === stableHash(value.normalizedSessionId) &&
    /^[A-Za-z0-9_-]{1,64}$/.test(value.userKey || '')
  );
}

module.exports = {
  RUNTIME_MARKER_FILE,
  SUPPORTED_RUNTIMES,
  createCandidateSessionStateContext,
  defaultRuntimeMarkerPath,
  gitEnvironment,
  isSessionStateContext,
  normalizeProjectPath,
  normalizeSessionId,
  pathContains,
  readRuntimeMarker,
  resolveCanonicalProjectRoot,
  stableHash
};
