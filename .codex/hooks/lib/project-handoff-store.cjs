'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { isSessionStateContext, pathContains } = require('./runtime-state-identity.cjs');
const { privateFileIsSafe } = require('./bounded-json-file.cjs');
const {
  allocateRevision,
  readHighestRevision,
  revisionName,
  writeRevision
} = require('./immutable-revision-journal.cjs');

const EXPIRY_DAYS = 7;

function canonicalCandidate(candidate) {
  const absolute = path.resolve(candidate);
  try {
    const info = fs.lstatSync(absolute);
    if (info.isSymbolicLink() || !info.isDirectory()) return null;
    return fs.realpathSync.native(absolute);
  } catch (error) {
    if (error?.code !== 'ENOENT') return null;
    try {
      return path.join(fs.realpathSync.native(path.dirname(absolute)), path.basename(absolute));
    } catch {
      return null;
    }
  }
}

function homeDirectory(environment) {
  return environment.HOME || environment.USERPROFILE || os.homedir();
}

function resolveAgentKitHome(environment = process.env) {
  const home = homeDirectory(environment);
  const candidate = canonicalCandidate(environment.AGENTKIT_HOME || path.join(home, '.agentkit'));
  if (!candidate) return null;
  const providerCandidates = [
    environment.AGENTKIT_CLAUDE_HOME,
    environment.CODEX_HOME,
    path.join(home, '.claude'),
    path.join(home, '.codex')
  ].filter(Boolean).map(canonicalCandidate).filter(Boolean);
  return providerCandidates.some(provider => pathContains(provider, candidate)) ? null : candidate;
}

function projectStatePaths(context, environment = process.env) {
  const root = resolveAgentKitHome(environment);
  if (!root || !isSessionStateContext(context)) return null;
  const directory = path.join(root, 'session-states', 'v2', context.runtime, context.projectKey);
  return {
    root,
    directory,
    checkpoints: path.join(directory, 'checkpoints'),
    revisions: path.join(directory, 'revisions')
  };
}

function validCheckpoint(value, context) {
  if (!value || value.schemaVersion !== 2 || value.runtime !== context.runtime || value.projectKey !== context.projectKey) return false;
  const allowedFields = new Set([
    'schemaVersion', 'runtime', 'projectKey', 'sourceSessionKey', 'eventRevision',
    'snapshotRevision', 'generatedAt', 'expiresAt', 'branch', 'activePlan',
    'todos', 'modifiedFiles'
  ]);
  if (Object.keys(value).some(key => !allowedFields.has(key))) return false;
  if (!/^[a-f0-9]{64}$/.test(value.sourceSessionKey || '')) return false;
  if (!Number.isSafeInteger(value.eventRevision) || value.eventRevision < 1) return false;
  if (!Number.isSafeInteger(value.snapshotRevision) || value.snapshotRevision < 1) return false;
  if (!Number.isFinite(Date.parse(value.generatedAt || '')) || !Number.isFinite(Date.parse(value.expiresAt || ''))) return false;
  if (typeof value.branch !== 'string' || Buffer.byteLength(value.branch, 'utf8') > 512) return false;
  if (value.activePlan != null && (typeof value.activePlan !== 'string' || Buffer.byteLength(value.activePlan, 'utf8') > 4096)) return false;
  if (!Array.isArray(value.todos) || value.todos.length > 200 || !value.todos.every(todo => (
    todo && typeof todo === 'object' && typeof todo.content === 'string' &&
    Buffer.byteLength(todo.content, 'utf8') <= 4096 && typeof todo.status === 'string'
  ))) return false;
  return Array.isArray(value.modifiedFiles) && value.modifiedFiles.length <= 100 &&
    value.modifiedFiles.every(file => typeof file === 'string' && Buffer.byteLength(file, 'utf8') <= 4096);
}

function loadProjectCheckpoint(context, options = {}) {
  const paths = projectStatePaths(context, options.environment);
  if (!paths) return null;
  const latest = readHighestRevision(paths.checkpoints, Number.MAX_SAFE_INTEGER, paths.root);
  const value = latest?.value;
  if (!value || value.eventRevision !== latest.revision || !validCheckpoint(value, context) ||
      Date.parse(value.expiresAt) <= (options.now || Date.now())) return null;
  if (value.sourceSessionKey === context.sessionKey) return null;
  return value;
}

function allocateEventRevision(context, options = {}) {
  const paths = projectStatePaths(context, options.environment);
  return paths ? allocateRevision({ root: paths.root, directory: paths.revisions }) : null;
}

function hasRevisionReservation(paths, revision) {
  return privateFileIsSafe(path.join(paths.revisions, `${revisionName(revision)}.reserve`), paths.root);
}

function writeProjectCheckpoint(context, data, options = {}) {
  const paths = projectStatePaths(context, options.environment);
  if (!paths || !Number.isSafeInteger(options.eventRevision) || !Number.isSafeInteger(options.snapshotRevision) ||
      !hasRevisionReservation(paths, options.eventRevision)) return false;
  const generatedAt = new Date(options.generatedAt || Date.now()).toISOString();
  const checkpoint = {
    schemaVersion: 2,
    runtime: context.runtime,
    projectKey: context.projectKey,
    sourceSessionKey: context.sessionKey,
    eventRevision: options.eventRevision,
    snapshotRevision: options.snapshotRevision,
    generatedAt,
    expiresAt: new Date(Date.parse(generatedAt) + EXPIRY_DAYS * 86400000).toISOString(),
    branch: typeof data.branch === 'string' ? data.branch : '',
    activePlan: typeof data.activePlan === 'string' ? data.activePlan : null,
    todos: Array.isArray(data.todos) ? data.todos.map(todo => ({
      content: String(todo?.content || ''),
      status: String(todo?.status || 'pending')
    })) : [],
    modifiedFiles: Array.isArray(data.modifiedFiles) ? data.modifiedFiles.map(String) : []
  };
  if (!validCheckpoint(checkpoint, context)) return false;
  const existing = readHighestRevision(paths.checkpoints, options.eventRevision + 1, paths.root);
  if (existing?.revision === options.eventRevision) return validCheckpoint(existing.value, context);
  return writeRevision({
    root: paths.root,
    directory: paths.checkpoints,
    revision: options.eventRevision,
    value: checkpoint
  });
}

module.exports = {
  EXPIRY_DAYS,
  allocateEventRevision,
  loadProjectCheckpoint,
  projectStatePaths,
  resolveAgentKitHome,
  validCheckpoint,
  writeProjectCheckpoint
};
