'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const { parseTranscript } = require('./transcript-parser.cjs');
const {
  gitEnvironment,
  pathContains
} = require('./runtime-state-identity.cjs');
const {
  readSessionState,
  updateSessionState
} = require('./ck-config-utils.cjs');
const {
  createEmptyActivitySnapshot,
  sanitizeActivitySnapshot
} = require('./statusline-session-cache.cjs');
const {
  allocateEventRevision,
  loadProjectCheckpoint,
  writeProjectCheckpoint
} = require('./project-handoff-store.cjs');

const TRANSCRIPT_MAX_BYTES = 4 * 1024 * 1024;
const TRANSCRIPT_MAX_LINES = 20000;
const TRANSCRIPT_BUDGET_MS = 250;
const EXEC_TIMEOUT_MS = 3000;
const TRANSCRIPT_IDENTITY_SCAN_BYTES = 256 * 1024;
const TRANSCRIPT_IDENTITY_SCAN_LINES = 64;

function execGit(args, cwd) {
  try {
    return execFileSync('git', args, {
      cwd,
      env: gitEnvironment(),
      encoding: 'utf8',
      timeout: EXEC_TIMEOUT_MS,
      stdio: ['ignore', 'pipe', 'ignore'],
      windowsHide: true
    }).trim();
  } catch {
    return '';
  }
}

function providerHome(context, environment = process.env) {
  const home = environment.HOME || environment.USERPROFILE || os.homedir();
  const candidate = context.runtime === 'codex'
    ? environment.CODEX_HOME || path.join(home, '.codex')
    : environment.AGENTKIT_CLAUDE_HOME || path.join(home, '.claude');
  try {
    return fs.realpathSync.native(candidate);
  } catch {
    return null;
  }
}

function claudeProjectSlug(projectRoot) {
  return projectRoot.replace(/\\/g, '/').replace(/:/g, '-').replace(/\//g, '-');
}

function pathsEqual(left, right) {
  const normalizedLeft = path.normalize(left);
  const normalizedRight = path.normalize(right);
  return process.platform === 'win32'
    ? normalizedLeft.toLowerCase() === normalizedRight.toLowerCase()
    : normalizedLeft === normalizedRight;
}

function codexTranscriptMatchesProject(context, transcriptPath) {
  try {
    const descriptor = fs.openSync(transcriptPath, 'r');
    try {
      const buffer = Buffer.alloc(TRANSCRIPT_IDENTITY_SCAN_BYTES);
      const bytesRead = fs.readSync(descriptor, buffer, 0, buffer.length, 0);
      const lines = buffer.subarray(0, bytesRead).toString('utf8').split(/\r?\n/).slice(0, TRANSCRIPT_IDENTITY_SCAN_LINES);
      for (const line of lines) {
        if (!line.trim()) continue;
        let record;
        try { record = JSON.parse(line); } catch { continue; }
        const candidates = [
          record?.payload?.cwd,
          record?.payload?.environment_context?.cwd,
          record?.cwd
        ].filter(value => typeof value === 'string' && value.trim());
        for (const candidate of candidates) {
          let canonical;
          try { canonical = fs.realpathSync.native(path.resolve(candidate)); } catch { continue; }
          if (pathContains(context.canonicalProjectRoot, canonical)) return true;
        }
      }
      return false;
    } finally {
      fs.closeSync(descriptor);
    }
  } catch {
    return false;
  }
}

function ownedTranscriptPath(context, candidate, environment = process.env) {
  if (!context || typeof candidate !== 'string' || !candidate.trim()) return null;
  try {
    const info = fs.lstatSync(candidate);
    if (!info.isFile() || info.isSymbolicLink()) return null;
    const resolved = fs.realpathSync.native(candidate);
    const root = providerHome(context, environment);
    if (!root || !pathContains(root, resolved)) return null;

    const basename = path.basename(resolved);
    if (context.runtime === 'claude-code') {
      const expectedDirectory = path.join(root, 'projects', claudeProjectSlug(context.sessionLaunchRoot));
      if (!pathsEqual(path.dirname(resolved), expectedDirectory)) return null;
      if (basename !== `${context.normalizedSessionId}.jsonl`) return null;
    } else {
      const sessionsRoot = path.join(root, 'sessions');
      const expectedSuffix = `-${context.normalizedSessionId}.jsonl`;
      if (!pathContains(sessionsRoot, resolved)) return null;
      if (basename !== `${context.normalizedSessionId}.jsonl` && !basename.endsWith(expectedSuffix)) return null;
      if (!codexTranscriptMatchesProject(context, resolved)) return null;
    }
    return { path: resolved, size: info.size };
  } catch {
    return null;
  }
}

function resolveTranscript(context, stdinData, existingState, environment = process.env) {
  const direct = ownedTranscriptPath(context, stdinData.transcript_path, environment);
  const cached = ownedTranscriptPath(context, existingState.lastTranscriptPath, environment);
  if (direct && cached && direct.path !== cached.path) return null;
  return direct || cached;
}

function applyStatuslineEvent(snapshot, stdinData, now) {
  const normalized = sanitizeActivitySnapshot({ ...snapshot, updatedAt: now });
  if (stdinData.hook_event_name !== 'SubagentStop') return normalized;
  const agentId = stdinData.agent_id == null ? null : String(stdinData.agent_id);
  const agentType = typeof stdinData.agent_type === 'string' ? stdinData.agent_type : null;
  if (!agentId && !agentType) return normalized;
  const agents = normalized.agents.map(agent => ({ ...agent }));
  let matched = false;
  for (let index = agents.length - 1; index >= 0; index -= 1) {
    const agent = agents[index];
    if ((agentId && agent.id === agentId) || (!agentId && agentType && agent.status === 'running' && agent.type === agentType)) {
      agent.status = 'completed';
      agent.endTime = agent.endTime || now;
      matched = true;
      break;
    }
  }
  return matched ? { ...normalized, agents, updatedAt: now } : normalized;
}

function hasSnapshotActivity(snapshot) {
  return Boolean(
    snapshot && ((Array.isArray(snapshot.agents) && snapshot.agents.length) ||
      (Array.isArray(snapshot.todos) && snapshot.todos.length))
  );
}

function shouldPreserveExistingSnapshot(existing, parsed, transcript) {
  if (!hasSnapshotActivity(existing) || existing.warmed !== true) return false;
  const existingTime = Date.parse(existing.updatedAt || '');
  const transcriptTime = Date.parse(transcript?.lastActivityAt || transcript?.lastValidEntryAt || '');
  if (Number.isFinite(existingTime) && Number.isFinite(transcriptTime) && existingTime >= transcriptTime) return true;
  if (!transcript || transcript.invalidLineCount === 0) {
    return !hasSnapshotActivity(parsed) && (!transcript || transcript.statuslineActivityCount === 0);
  }
  return !Number.isFinite(existingTime) || !Number.isFinite(transcriptTime) || existingTime >= transcriptTime;
}

async function refreshStatuslineSnapshot(context, stdinData, options = {}) {
  try {
    if (!context) return { success: false, reason: 'missing-session-context' };
    const now = new Date(options.now || Date.now()).toISOString();
    const existingState = readSessionState(context) || {};
    const transcriptSource = resolveTranscript(context, stdinData, existingState, options.environment);

    if (!transcriptSource) {
      if (options.requireOwnedTranscript) {
        return { success: false, reason: 'missing-owned-transcript' };
      }
      const success = updateSessionState(context, state => ({
        ...state,
        statusline: applyStatuslineEvent(state.statusline || createEmptyActivitySnapshot(), stdinData, now)
      }));
      const current = success ? readSessionState(context) : null;
      return current
        ? { success: true, warmed: Boolean(current.statusline?.warmed), snapshotRevision: current.stateRevision }
        : { success: false, reason: 'write-failed' };
    }

    const start = Math.max(0, transcriptSource.size - TRANSCRIPT_MAX_BYTES);
    const transcript = await parseTranscript(transcriptSource.path, {
      start,
      end: transcriptSource.size > 0 ? transcriptSource.size - 1 : undefined,
      maxLines: TRANSCRIPT_MAX_LINES,
      deadline: Date.now() + TRANSCRIPT_BUDGET_MS
    });
    const success = updateSessionState(context, state => {
      const currentSnapshot = state.statusline || createEmptyActivitySnapshot();
      const parsedSnapshot = applyStatuslineEvent({
        sessionStart: transcript.sessionStart ? new Date(transcript.sessionStart).toISOString() : currentSnapshot.sessionStart || now,
        updatedAt: now,
        warmed: true,
        agents: transcript.agents || [],
        todos: transcript.todos || []
      }, stdinData, now);
      const preserve = shouldPreserveExistingSnapshot(currentSnapshot, parsedSnapshot, transcript);
      return {
        ...state,
        statusline: sanitizeActivitySnapshot(preserve ? applyStatuslineEvent(currentSnapshot, stdinData, now) : parsedSnapshot),
        lastTranscriptPath: preserve && state.lastTranscriptPath ? state.lastTranscriptPath : transcriptSource.path
      };
    });
    const current = success ? readSessionState(context) : null;
    return current
      ? { success: true, warmed: true, snapshotRevision: current.stateRevision }
      : { success: false, reason: 'write-failed' };
  } catch {
    return { success: false, reason: 'snapshot-refresh-failed' };
  }
}

function extractSessionData(context, stdinData) {
  const state = readSessionState(context);
  if (!state) return null;
  const cwd = context.canonicalProjectRoot;
  const modified = execGit(['diff', '--name-only', 'HEAD'], cwd);
  return {
    branch: execGit(['branch', '--show-current'], cwd),
    activePlan: typeof state.activePlan === 'string' ? state.activePlan : null,
    todos: Array.isArray(state.statusline?.todos) ? state.statusline.todos : [],
    modifiedFiles: modified ? modified.split('\n').filter(Boolean).slice(0, 20) : []
  };
}

async function persistProjectCheckpoint(context, stdinData, options = {}) {
  const generatedAt = new Date(options.generatedAt || Date.now()).toISOString();
  const eventRevision = allocateEventRevision(context, options);
  if (!eventRevision) return { success: false, reason: 'revision-allocation-failed' };
  const refresh = await refreshStatuslineSnapshot(context, stdinData, {
    ...options,
    requireOwnedTranscript: true
  });
  if (!refresh.success || !refresh.snapshotRevision) return { success: false, reason: refresh.reason || 'refresh-failed' };
  const data = extractSessionData(context, stdinData);
  if (!data) return { success: false, reason: 'missing-fresh-state' };
  const success = writeProjectCheckpoint(context, data, {
    ...options,
    generatedAt,
    eventRevision,
    snapshotRevision: refresh.snapshotRevision
  });
  return success ? { success: true, eventRevision } : { success: false, reason: 'checkpoint-write-failed' };
}

module.exports = {
  TRANSCRIPT_BUDGET_MS,
  TRANSCRIPT_MAX_BYTES,
  TRANSCRIPT_MAX_LINES,
  extractSessionData,
  loadProjectCheckpoint,
  ownedTranscriptPath,
  persistProjectCheckpoint,
  refreshStatuslineSnapshot,
  shouldPreserveExistingSnapshot
};
