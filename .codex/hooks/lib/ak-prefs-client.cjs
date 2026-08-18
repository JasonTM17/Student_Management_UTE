/**
 * Reads AgentKit preferences by asking the `ak` binary for them.
 *
 * Preferences live in a YAML file that hooks cannot parse: the hook surface
 * ships with no dependencies, so there is no YAML parser here, and scraping the
 * file with a regex only ever worked for a single scalar key. The binary owns
 * reading, merging the project file over the user file, and withholding the
 * credential sections, and hands hooks a JSON payload.
 *
 * There is deliberately no cache file. A generated file on disk invites hand
 * edits that silently disagree with the config it came from, so the only way to
 * read or refresh preferences is to call the command.
 */

const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');

// The payload shape this module understands. A binary announcing anything else
// is treated as unreadable rather than guessed at.
const SUPPORTED_SCHEMA_VERSION = 1;

// Long enough to absorb a cold binary start, short enough that a wedged call
// cannot stall the tool invocation the hook is gating.
const RESOLVE_TIMEOUT_MS = 2000;

// Argument vectors are frozen here and chosen by key, so no caller can shape a
// command line. Adding an entry is the only way to add a call.
const AK_OPERATIONS = Object.freeze({
  'prefs-resolve': Object.freeze(['config', 'prefs', 'resolve', '--json'])
});

let cachedBinary;
// Keyed by the project directory the prefs were resolved for. A hook receives
// the session's directory in its payload, which is not always the directory the
// process was started in, and the project file that wins the merge is chosen by
// that directory — so one cache slot per scope, not one per process.
const cachedPayloads = new Map();

/**
 * Locate the `ak` executable.
 *
 * PATH alone is not enough: a GUI-launched harness inherits a minimal PATH that
 * omits the directories the installers write to, which would leave exactly the
 * users who never open a terminal unable to resolve their own settings.
 *
 * @returns {string|null} Absolute path or bare command name, or null.
 */
function resolveAkBinary() {
  if (cachedBinary !== undefined) return cachedBinary;

  const candidates = [];

  // An explicit override wins, and is how a sandboxed test points at a build.
  const override = process.env.AGENTKIT_BIN;
  if (override && path.isAbsolute(override)) candidates.push(override);

  const home = os.homedir();
  if (process.platform === 'win32') {
    candidates.push(path.join(home, 'bin', 'ak.exe'));
  } else {
    candidates.push(path.join(home, '.local', 'bin', 'ak'));
  }

  // Scan PATH directly rather than letting the spawn fail: a host without the
  // binary is an ordinary state (a sandbox, or a kit copied without a CLI), and
  // it must be distinguishable from a binary that ran and misbehaved.
  const executable = process.platform === 'win32' ? 'ak.exe' : 'ak';
  for (const entry of (process.env.PATH || '').split(path.delimiter)) {
    if (entry) candidates.push(path.join(entry, executable));
  }

  for (const candidate of candidates) {
    try {
      if (fs.existsSync(candidate)) {
        cachedBinary = candidate;
        return cachedBinary;
      }
    } catch (e) {
      // An unreadable candidate is simply not the binary.
    }
  }

  cachedBinary = null;
  return cachedBinary;
}

/**
 * Emit a diagnostic once per process, but only when explicitly asked.
 *
 * Hooks are expected to produce no output of their own: stdout is consumed as
 * model context, and stderr is asserted empty across the hook suites. A binary
 * that is absent or too old to know the subcommand is also the ordinary state
 * during a rollout, because kits and the CLI update on separate schedules — so
 * reporting it here would put a line on every hook of every turn for a
 * condition the user cannot act on mid-session. `ak doctor` owns that report.
 *
 * Set AGENTKIT_HOOK_DEBUG to trace a resolve that is not behaving.
 *
 * @param {string} message
 */
function debugOnce(message) {
  if (debugOnce.reported || !process.env.AGENTKIT_HOOK_DEBUG) return;
  debugOnce.reported = true;
  try {
    process.stderr.write(`[agentkit] ${message}\n`);
  } catch (e) {
    // A closed stderr must not turn a diagnostic into a crash.
  }
}

/**
 * Ask the binary for the resolved preferences.
 *
 * Every failure — binary absent, too old to know the subcommand, non-zero exit,
 * malformed output, unexpected schema version, timeout — returns null, and the
 * caller falls back to its own defaults. The result is remembered for the life
 * of the process, including the failure, so one hook run costs at most one
 * spawn per project scope.
 *
 * @param {object} [options]
 * @param {string} [options.cwd] Project directory whose config participates in
 *   the merge. Defaults to the process directory.
 * @returns {Object|null} The `prefs` tree, or null when it cannot be read.
 */
function resolvePrefs(options = {}) {
  const cwd = options.cwd || process.cwd();
  if (cachedPayloads.has(cwd)) return cachedPayloads.get(cwd);
  cachedPayloads.set(cwd, null);

  // No binary on this host: fall back to defaults without comment. Hooks are
  // expected to stay silent, and `ak doctor` is where a missing CLI belongs —
  // narrating it on every hook of every turn would be noise, not a diagnosis.
  const binary = resolveAkBinary();
  if (!binary) return null;

  let stdout;
  try {
    stdout = execFileSync(binary, AK_OPERATIONS['prefs-resolve'], {
      encoding: 'utf8',
      timeout: RESOLVE_TIMEOUT_MS,
      cwd,
      // The environment carries AGENTKIT_HOME and friends, which decide which
      // config the binary reads; stripping them would send it at another host's
      // state, including a test's.
      env: process.env,
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true
    });
  } catch (e) {
    debugOnce(
      `could not read preferences (${e && e.code ? e.code : 'failed'}); using defaults. ` +
      'Install or update the ak CLI to apply your saved settings.'
    );
    return null;
  }

  let payload;
  try {
    payload = JSON.parse(stdout);
  } catch (e) {
    debugOnce('preferences came back unreadable; using defaults.');
    return null;
  }

  if (!payload || payload.schema_version !== SUPPORTED_SCHEMA_VERSION) {
    debugOnce(
      `preferences use an unsupported format (version ${payload && payload.schema_version}); using defaults.`
    );
    return null;
  }

  const prefs = payload.prefs && typeof payload.prefs === 'object' ? payload.prefs : {};
  cachedPayloads.set(cwd, prefs);
  return prefs;
}

/**
 * Read one preference section, or an empty object when it is absent or is not a
 * map. Callers merge it over their own defaults, so an unreadable host and an
 * unset section reach the same place.
 *
 * @param {string} name Section key, in the spelling the payload uses.
 * @param {object} [options] Forwarded to resolvePrefs.
 * @returns {Object}
 */
function resolvePrefsSection(name, options) {
  const prefs = resolvePrefs(options);
  const section = prefs ? prefs[name] : null;
  return section && typeof section === 'object' && !Array.isArray(section) ? section : {};
}

/** Clear the memoised binary and payloads. Exposed for tests. */
function resetPrefsCache() {
  cachedBinary = undefined;
  cachedPayloads.clear();
  debugOnce.reported = false;
}

module.exports = {
  SUPPORTED_SCHEMA_VERSION,
  RESOLVE_TIMEOUT_MS,
  resolveAkBinary,
  resolvePrefs,
  resolvePrefsSection,
  resetPrefsCache
};
