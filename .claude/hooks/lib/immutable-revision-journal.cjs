'use strict';

const fs = require('fs');
const path = require('path');
const {
  ensurePrivateDirectory,
  privateDirectoryIsSafe,
  privateFileIsSafe,
  readJsonFile,
  writeJsonFileExclusive
} = require('./bounded-json-file.cjs');

const REVISION_WIDTH = 16;
const MAX_REVISIONS = 128;
const PREDECESSOR_WAIT_MS = 5000;

function revisionName(revision) {
  return String(revision).padStart(REVISION_WIDTH, '0');
}

function parseRevisionName(name, suffix) {
  const match = new RegExp(`^(\\d{${REVISION_WIDTH}})\\${suffix}$`).exec(name);
  if (!match) return null;
  const revision = Number(match[1]);
  return Number.isSafeInteger(revision) && revision > 0 ? revision : null;
}

function listRevisions(directory, suffix, root) {
  try {
    if (!privateDirectoryIsSafe(directory, root)) return [];
    return fs.readdirSync(directory, { withFileTypes: true })
      .filter(entry => entry.isFile() && !entry.isSymbolicLink())
      .map(entry => parseRevisionName(entry.name, suffix))
      .filter(Boolean)
      .sort((left, right) => left - right);
  } catch {
    return [];
  }
}

function reservationPath(directory, revision) {
  return path.join(directory, `${revisionName(revision)}.reserve`);
}

function recordPath(directory, revision) {
  return path.join(directory, `${revisionName(revision)}.json`);
}

function abandonedPath(directory, revision) {
  return path.join(directory, `${revisionName(revision)}.abandoned`);
}

function pruneOldEntries(directory, suffix, root, keep = MAX_REVISIONS) {
  const revisions = listRevisions(directory, suffix, root);
  for (const revision of revisions.slice(0, Math.max(0, revisions.length - keep))) {
    try { fs.unlinkSync(path.join(directory, `${revisionName(revision)}${suffix}`)); } catch { /* best effort */ }
  }
}

function allocateRevision({ root, directory }) {
  if (!root || !directory) return null;
  try {
    ensurePrivateDirectory(directory, root);
    for (let attempt = 0; attempt < 64; attempt += 1) {
      const revisions = listRevisions(directory, '.reserve', root);
      const revision = (revisions[revisions.length - 1] || 0) + 1;
      let descriptor = null;
      try {
        descriptor = fs.openSync(reservationPath(directory, revision), 'wx', 0o600);
        fs.writeFileSync(descriptor, `${process.pid}\n`, 'utf8');
        fs.fsyncSync(descriptor);
        fs.closeSync(descriptor);
        descriptor = null;
        pruneOldEntries(directory, '.reserve', root);
        return revision;
      } catch (error) {
        if (descriptor != null) try { fs.closeSync(descriptor); } catch { /* ignore */ }
        if (error?.code !== 'EEXIST') return null;
      }
    }
  } catch { /* fail open to the hook caller */ }
  return null;
}

function writeRevision({ root, directory, revision, value }) {
  if (!Number.isSafeInteger(revision) || revision < 1) return false;
  const success = writeJsonFileExclusive({
    root,
    filePath: recordPath(directory, revision),
    value
  });
  if (success) pruneOldEntries(directory, '.json', root);
  return success;
}

function abandonRevision({ root, directory, revision, blockedByRevision = null }) {
  const value = { schemaVersion: 1, revision };
  if (Number.isSafeInteger(blockedByRevision) && blockedByRevision > 0 && blockedByRevision < revision) {
    value.blockedByRevision = blockedByRevision;
  }
  const success = writeJsonFileExclusive({
    root,
    filePath: abandonedPath(directory, revision),
    value
  });
  if (success) pruneOldEntries(directory, '.abandoned', root);
  return success;
}

function readHighestRevision(directory, beforeRevision = Number.MAX_SAFE_INTEGER, root) {
  const revisions = listRevisions(directory, '.json', root).filter(revision => revision < beforeRevision);
  const revision = revisions[revisions.length - 1];
  return revision ? { revision, value: readJsonFile(recordPath(directory, revision), root) } : null;
}

function reservationOwnerIsAlive(filePath, root) {
  if (!privateFileIsSafe(filePath, root)) return false;
  try {
    const processId = Number.parseInt(fs.readFileSync(filePath, 'utf8').trim(), 10);
    if (!Number.isSafeInteger(processId) || processId < 1) return false;
    process.kill(processId, 0);
    return true;
  } catch (error) {
    return error?.code === 'EPERM';
  }
}

function unresolvedPredecessor(directory, sequenceDirectory, revision, root) {
  let predecessorRevision = revision - 1;
  for (let depth = 0; predecessorRevision > 0 && depth < MAX_REVISIONS; depth += 1) {
    if (privateFileIsSafe(recordPath(directory, predecessorRevision), root)) return null;
    const abandoned = readJsonFile(abandonedPath(directory, predecessorRevision), root);
    if (abandoned?.schemaVersion === 1 && abandoned.revision === predecessorRevision) {
      if (Number.isSafeInteger(abandoned.blockedByRevision) && abandoned.blockedByRevision > 0 &&
          abandoned.blockedByRevision < predecessorRevision) {
        predecessorRevision = abandoned.blockedByRevision;
        continue;
      }
      return null;
    }
    return reservationOwnerIsAlive(reservationPath(sequenceDirectory, predecessorRevision), root)
      ? predecessorRevision
      : null;
  }
  return predecessorRevision > 0 ? predecessorRevision : null;
}

function waitForPredecessor(directory, sequenceDirectory, revision, root, timeoutMs = PREDECESSOR_WAIT_MS) {
  if (!Number.isSafeInteger(revision) || revision <= 1) return true;
  const deadline = Date.now() + timeoutMs;
  const signal = new Int32Array(new SharedArrayBuffer(4));
  while (Date.now() < deadline) {
    if (unresolvedPredecessor(directory, sequenceDirectory, revision, root) == null) return true;
    Atomics.wait(signal, 0, 0, Math.min(10, Math.max(1, deadline - Date.now())));
  }
  return false;
}

module.exports = {
  MAX_REVISIONS,
  PREDECESSOR_WAIT_MS,
  abandonRevision,
  allocateRevision,
  listRevisions,
  readHighestRevision,
  recordPath,
  revisionName,
  waitForPredecessor,
  writeRevision
};
