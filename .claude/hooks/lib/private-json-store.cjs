'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { isSessionStateContext } = require('./runtime-state-identity.cjs');
const {
  MAX_JSON_BYTES,
  readJsonFile,
  serializeJson,
  writeJsonFile: writeBoundedJsonFile,
  writeJsonFileExclusive
} = require('./bounded-json-file.cjs');
const {
  abandonRevision,
  allocateRevision,
  readHighestRevision,
  waitForPredecessor,
  writeRevision
} = require('./immutable-revision-journal.cjs');

function privateRoot(context) {
  if (!isSessionStateContext(context)) return null;
  if (context.storageRoot) return path.resolve(context.storageRoot);
  try {
    return path.join(fs.realpathSync.native(os.tmpdir()), 'agentkit-session-v2', context.userKey);
  } catch {
    return null;
  }
}

function sessionRoot(context) {
  const root = privateRoot(context);
  return root ? path.join(root, context.runtime, context.sessionKey) : null;
}

function sessionDirectory(context) {
  const root = sessionRoot(context);
  return root ? path.join(root, context.projectKey) : null;
}

function getSessionBindingPath(context) {
  const root = sessionRoot(context);
  return root ? path.join(root, 'binding.json') : null;
}

function getSessionTempPath(context) {
  const directory = sessionDirectory(context);
  return directory ? path.join(directory, 'live.json') : null;
}

function getContextTempPath(context) {
  const directory = sessionDirectory(context);
  return directory ? path.join(directory, 'context.json') : null;
}

function sessionJournalPaths(context, name) {
  const directory = sessionDirectory(context);
  const root = privateRoot(context);
  return directory && root ? {
    root,
    records: path.join(directory, `${name}-revisions`),
    sequence: path.join(directory, `${name}-sequence`)
  } : null;
}

function bindingValue(context) {
  return {
    schemaVersion: 2,
    runtime: context.runtime,
    projectKey: context.projectKey,
    sessionKey: context.sessionKey,
    normalizedSessionId: context.normalizedSessionId,
    canonicalProjectRoot: context.canonicalProjectRoot,
    sessionLaunchRoot: context.sessionLaunchRoot
  };
}

function bindingMatchesContext(binding, context) {
  return Boolean(
    binding && binding.schemaVersion === 2 && binding.runtime === context.runtime &&
    binding.sessionKey === context.sessionKey && binding.normalizedSessionId === context.normalizedSessionId &&
    binding.projectKey === context.projectKey && binding.canonicalProjectRoot === context.canonicalProjectRoot
  );
}

function contextFromBinding(candidate, binding) {
  if (!bindingMatchesContext(binding, candidate)) return null;
  if (candidate.sessionLaunchRoot === binding.sessionLaunchRoot) return candidate;
  const bound = { ...candidate, sessionLaunchRoot: binding.sessionLaunchRoot };
  return isSessionStateContext(bound) ? bound : null;
}

function bindSessionStateContext(candidate) {
  if (!isSessionStateContext(candidate)) return null;
  const filePath = getSessionBindingPath(candidate);
  const root = privateRoot(candidate);
  if (!filePath || !root) return null;
  const existing = readJsonFile(filePath, root);
  if (existing) return contextFromBinding(candidate, existing);
  if (!writeJsonFileExclusive({ root, filePath, value: bindingValue(candidate) })) {
    const winner = readJsonFile(filePath, root);
    return contextFromBinding(candidate, winner);
  }
  return candidate;
}

function resolveBoundSessionContext(candidate) {
  if (!isSessionStateContext(candidate)) return null;
  const root = privateRoot(candidate);
  return root ? contextFromBinding(candidate, readJsonFile(getSessionBindingPath(candidate), root)) : null;
}

function writeJsonFile(context, filePath, value, verify = null) {
  const root = privateRoot(context);
  return Boolean(root && writeBoundedJsonFile({ root, filePath, value, verify }));
}

function stateMatchesContext(state, context, revision = state?.stateRevision) {
  return Boolean(
    state && state.schemaVersion === 2 && state.runtime === context.runtime &&
    state.projectKey === context.projectKey && state.sessionKey === context.sessionKey &&
    state.canonicalProjectRoot === context.canonicalProjectRoot &&
    state.sessionLaunchRoot === context.sessionLaunchRoot &&
    Number.isSafeInteger(state.stateRevision) && state.stateRevision === revision
  );
}

function safeCompatibilityPath(filePath) {
  try {
    const info = fs.lstatSync(filePath);
    return info.isFile() && !info.isSymbolicLink();
  } catch (error) {
    return error?.code === 'ENOENT';
  }
}

function readSessionState(context) {
  if (!isSessionStateContext(context)) return null;
  const paths = sessionJournalPaths(context, 'live');
  if (!paths) return null;
  const latest = readHighestRevision(paths.records, Number.MAX_SAFE_INTEGER, paths.root);
  return latest && stateMatchesContext(latest.value, context, latest.revision) ? latest.value : null;
}

function commitSessionState(context, buildState) {
  if (!isSessionStateContext(context) || !safeCompatibilityPath(getSessionTempPath(context))) return false;
  const paths = sessionJournalPaths(context, 'live');
  if (!paths) return false;
  const revision = allocateRevision({ root: paths.root, directory: paths.sequence });
  if (!revision) return false;
  if (!waitForPredecessor(paths.records, paths.sequence, revision, paths.root)) {
    abandonRevision({
      root: paths.root,
      directory: paths.records,
      revision,
      blockedByRevision: revision - 1
    });
    return false;
  }
  const currentEntry = readHighestRevision(paths.records, revision, paths.root);
  const current = currentEntry && stateMatchesContext(currentEntry.value, context, currentEntry.revision)
    ? currentEntry.value
    : {};
  const updated = buildState({ ...current });
  if (!updated || typeof updated !== 'object') {
    abandonRevision({ root: paths.root, directory: paths.records, revision });
    return false;
  }
  const next = {
    ...updated,
    schemaVersion: 2,
    runtime: context.runtime,
    projectKey: context.projectKey,
    sessionKey: context.sessionKey,
    canonicalProjectRoot: context.canonicalProjectRoot,
    sessionLaunchRoot: context.sessionLaunchRoot,
    stateRevision: revision
  };
  delete next.sessionOrigin;
  if (!writeRevision({ root: paths.root, directory: paths.records, revision, value: next })) {
    abandonRevision({ root: paths.root, directory: paths.records, revision });
    return false;
  }
  writeJsonFile(context, getSessionTempPath(context), next);
  return true;
}

function writeSessionState(context, state) {
  if (!state || typeof state !== 'object') return false;
  return commitSessionState(context, () => ({ ...state }));
}

function updateSessionState(context, updater) {
  return commitSessionState(context, current => (
    typeof updater === 'function' ? updater(current) : { ...current, ...(updater || {}) }
  ));
}

function readContextState(context) {
  if (!isSessionStateContext(context)) return null;
  const paths = sessionJournalPaths(context, 'context');
  return paths ? readHighestRevision(paths.records, Number.MAX_SAFE_INTEGER, paths.root)?.value || null : null;
}

function writeContextState(context, value) {
  if (!isSessionStateContext(context) || !value || typeof value !== 'object' ||
      !safeCompatibilityPath(getContextTempPath(context))) return false;
  const paths = sessionJournalPaths(context, 'context');
  if (!paths) return false;
  const revision = allocateRevision({ root: paths.root, directory: paths.sequence });
  if (!revision) return false;
  if (!waitForPredecessor(paths.records, paths.sequence, revision, paths.root)) {
    abandonRevision({
      root: paths.root,
      directory: paths.records,
      revision,
      blockedByRevision: revision - 1
    });
    return false;
  }
  if (!writeRevision({ root: paths.root, directory: paths.records, revision, value })) {
    abandonRevision({ root: paths.root, directory: paths.records, revision });
    return false;
  }
  writeJsonFile(context, getContextTempPath(context), value);
  return true;
}

module.exports = {
  MAX_JSON_BYTES,
  bindSessionStateContext,
  getContextTempPath,
  getSessionBindingPath,
  getSessionTempPath,
  privateRoot,
  readContextState,
  readJsonFile,
  readSessionState,
  resolveBoundSessionContext,
  serializeJson,
  sessionDirectory,
  updateSessionState,
  writeContextState,
  writeJsonFile,
  writeSessionState
};
