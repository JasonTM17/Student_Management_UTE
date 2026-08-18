'use strict';

const crypto = require('crypto');
const fs = require('fs');
const path = require('path');

const MAX_JSON_BYTES = 256 * 1024;
const MAX_JSON_DEPTH = 8;
const MAX_JSON_ITEMS = 1000;

function privateOwnershipIsSafe(info, platform = process.platform, userId = typeof process.getuid === 'function' ? process.getuid() : null) {
  if (platform === 'win32' || userId == null) return true;
  return info?.uid === userId && (info.mode & 0o077) === 0;
}

function assertPrivateOwnership(info, kind) {
  if (!privateOwnershipIsSafe(info)) throw new Error(`unsafe ${kind} owner or permissions`);
}

function secureDirectory(directory) {
  const info = fs.lstatSync(directory);
  if (info.isSymbolicLink() || !info.isDirectory()) throw new Error('unsafe state directory');
  if (process.platform !== 'win32') fs.chmodSync(directory, 0o700);
  const secured = fs.lstatSync(directory);
  assertPrivateOwnership(secured, 'state directory');
}

function ensurePrivateDirectory(directory, anchor) {
  const absoluteAnchor = path.resolve(anchor);
  const absoluteDirectory = path.resolve(directory);
  const relative = path.relative(absoluteAnchor, absoluteDirectory);
  if (relative.startsWith('..') || path.isAbsolute(relative)) throw new Error('state path escapes private root');

  let current = absoluteAnchor;
  fs.mkdirSync(current, { recursive: true, mode: 0o700 });
  secureDirectory(current);
  for (const segment of relative.split(path.sep).filter(Boolean)) {
    current = path.join(current, segment);
    try {
      const info = fs.lstatSync(current);
      if (info.isSymbolicLink() || !info.isDirectory()) throw new Error('unsafe state directory');
    } catch (error) {
      if (error?.code !== 'ENOENT') throw error;
      fs.mkdirSync(current, { mode: 0o700 });
    }
    secureDirectory(current);
  }
}

function privatePathIsSafe(target, root, expectedType) {
  if (!root) return false;
  const absoluteRoot = path.resolve(root);
  const absoluteTarget = path.resolve(target);
  const relative = path.relative(absoluteRoot, absoluteTarget);
  if (relative.startsWith('..') || path.isAbsolute(relative)) return false;

  try {
    const segments = relative.split(path.sep).filter(Boolean);
    let current = absoluteRoot;
    for (let index = 0; index <= segments.length; index += 1) {
      if (index > 0) current = path.join(current, segments[index - 1]);
      const info = fs.lstatSync(current);
      if (info.isSymbolicLink() || !privateOwnershipIsSafe(info)) return false;
      const final = index === segments.length;
      if ((!final || expectedType === 'directory') && !info.isDirectory()) return false;
      if (final && expectedType === 'file' && !info.isFile()) return false;
    }
    return true;
  } catch {
    return false;
  }
}

function privateDirectoryIsSafe(directory, root) {
  return privatePathIsSafe(directory, root, 'directory');
}

function privateFileIsSafe(filePath, root) {
  return privatePathIsSafe(filePath, root, 'file');
}

function validateJsonShape(value, depth = 0, budget = { items: 0 }) {
  if (depth > MAX_JSON_DEPTH) return false;
  if (value == null || typeof value === 'boolean' || typeof value === 'number') return true;
  if (typeof value === 'string') return Buffer.byteLength(value, 'utf8') <= MAX_JSON_BYTES;
  if (typeof value !== 'object') return false;
  const values = Array.isArray(value) ? value : Object.values(value);
  budget.items += values.length;
  if (budget.items > MAX_JSON_ITEMS) return false;
  return values.every(entry => validateJsonShape(entry, depth + 1, budget));
}

function readJsonFile(filePath, root) {
  try {
    if (!privateFileIsSafe(filePath, root)) return null;
    const info = fs.lstatSync(filePath);
    if (info.isSymbolicLink() || !info.isFile() || info.size > MAX_JSON_BYTES) return null;
    const data = fs.readFileSync(filePath, 'utf8');
    if (Buffer.byteLength(data, 'utf8') > MAX_JSON_BYTES) return null;
    const value = JSON.parse(data);
    return validateJsonShape(value) ? value : null;
  } catch {
    return null;
  }
}

function serializeJson(value) {
  if (!validateJsonShape(value)) return null;
  const data = `${JSON.stringify(value, null, 2)}\n`;
  return Buffer.byteLength(data, 'utf8') <= MAX_JSON_BYTES ? data : null;
}

function writeJsonFile({ root, filePath, value, verify = null }) {
  const serialized = serializeJson(value);
  if (!root || !serialized) return false;
  let temporaryPath = null;
  let descriptor = null;
  try {
    ensurePrivateDirectory(path.dirname(filePath), root);
    try {
      const current = fs.lstatSync(filePath);
      if (current.isSymbolicLink() || !current.isFile()) return false;
    } catch (error) {
      if (error?.code !== 'ENOENT') return false;
    }
    temporaryPath = `${filePath}.${process.pid}.${crypto.randomBytes(8).toString('hex')}.tmp`;
    descriptor = fs.openSync(temporaryPath, 'wx', 0o600);
    fs.writeFileSync(descriptor, serialized, 'utf8');
    fs.fsyncSync(descriptor);
    fs.closeSync(descriptor);
    descriptor = null;
    if (verify && !verify()) return false;
    fs.renameSync(temporaryPath, filePath);
    temporaryPath = null;
    if (process.platform !== 'win32') fs.chmodSync(filePath, 0o600);
    assertPrivateOwnership(fs.lstatSync(filePath), 'state file');
    return true;
  } catch {
    return false;
  } finally {
    if (descriptor != null) try { fs.closeSync(descriptor); } catch { /* ignore */ }
    if (temporaryPath) try { fs.unlinkSync(temporaryPath); } catch { /* ignore */ }
  }
}

function writeJsonFileExclusive({ root, filePath, value }) {
  const serialized = serializeJson(value);
  if (!root || !serialized) return false;
  let temporaryPath = null;
  let descriptor = null;
  try {
    ensurePrivateDirectory(path.dirname(filePath), root);
    temporaryPath = `${filePath}.${process.pid}.${crypto.randomBytes(8).toString('hex')}.candidate`;
    descriptor = fs.openSync(temporaryPath, 'wx', 0o600);
    fs.writeFileSync(descriptor, serialized, 'utf8');
    fs.fsyncSync(descriptor);
    fs.closeSync(descriptor);
    descriptor = null;
    fs.linkSync(temporaryPath, filePath);
    if (process.platform !== 'win32') fs.chmodSync(filePath, 0o600);
    assertPrivateOwnership(fs.lstatSync(filePath), 'state file');
    return true;
  } catch {
    return false;
  } finally {
    if (descriptor != null) try { fs.closeSync(descriptor); } catch { /* ignore */ }
    if (temporaryPath) try { fs.unlinkSync(temporaryPath); } catch { /* ignore */ }
  }
}

module.exports = {
  MAX_JSON_BYTES,
  ensurePrivateDirectory,
  privateDirectoryIsSafe,
  privateFileIsSafe,
  privateOwnershipIsSafe,
  readJsonFile,
  serializeJson,
  validateJsonShape,
  writeJsonFile,
  writeJsonFileExclusive
};
