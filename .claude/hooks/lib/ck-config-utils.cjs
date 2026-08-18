/**
 * Shared utilities for AgentKit hooks
 *
 * Contains config loading, path sanitization, and common constants
 * used by session-init.cjs and dev-rules-reminder.cjs
 */

const fs = require('fs');
const path = require('path');
const os = require('os');
const { execFileSync } = require('child_process');
const {
  createCandidateSessionStateContext,
  gitEnvironment,
  normalizeSessionId
} = require('./runtime-state-identity.cjs');
const sessionStore = require('./private-json-store.cjs');
const { resolvePrefs } = require('./ak-prefs-client.cjs');

const DEFAULT_CONFIG = {
  plan: {
    namingFormat: '{date}-{issue}-{slug}',
    dateFormat: 'YYMMDD-HHmm',
    issuePrefix: null,
    reportsDir: 'reports',
    resolution: {
      // CHANGED: Removed 'mostRecent' - only explicit session state activates plans
      // Branch matching now returns 'suggested' not 'active'
      order: ['session', 'branch'],
      branchPattern: '(?:feat|fix|chore|refactor|docs)/(?:[^/]+/)?(.+)'
    },
    validation: {
      mode: 'prompt',  // 'auto' | 'prompt' | 'off'
      minQuestions: 3,
      maxQuestions: 8,
      focusAreas: ['assumptions', 'risks', 'tradeoffs', 'architecture']
    }
  },
  paths: {
    docs: 'docs',
    plans: 'plans'
  },
  docs: {
    maxLoc: 800  // Maximum lines of code per doc file before warning
  },
  locale: {
    thinkingLanguage: null,  // Language for reasoning (e.g., "en" for precision)
    responseLanguage: null   // Language for user-facing output (e.g., "vi")
  },
  trust: {
    passphrase: null,
    enabled: false
  },
  project: {
    type: 'auto',
    packageManager: 'auto',
    framework: 'auto'
  },
  skills: {
    research: {
      useGemini: false  // Legacy compatibility input; active workflows ignore this retired CLI toggle
    }
  },
  assertions: [],
  statusline: 'full',
  statuslineColors: true,
  statuslineQuota: true,
  hooks: {
    'session-init': true,
    'subagent-init': true,
    'dev-rules-reminder': true,
    'usage-context-awareness': true,
    'context-tracking': true,
    'scout-block': true,
    'privacy-block': true,
    'secret-output-guardrail': true,
    'final-subagent-reminder': false,
    'simplify-gate': true,
    'session-state': true
  }
};

/**
 * Deep merge objects (source values override target, nested objects merged recursively)
 * Arrays are replaced entirely (not concatenated) to avoid duplicate entries
 *
 * IMPORTANT: Empty objects {} are treated as "inherit from parent", not "replace with empty".
 * This allows global config to set hooks.foo: false and have it persist even when
 * local config has hooks: {} (empty = inherit, not reset to defaults).
 *
 * @param {Object} target - Base object
 * @param {Object} source - Object to merge (takes precedence)
 * @returns {Object} Merged object
 */
function deepMerge(target, source) {
  if (!source || typeof source !== 'object') return target;
  if (!target || typeof target !== 'object') return source;

  const result = { ...target };
  for (const key of Object.keys(source)) {
    const sourceVal = source[key];
    const targetVal = target[key];

    // Arrays: replace entirely (don't concatenate)
    if (Array.isArray(sourceVal)) {
      result[key] = [...sourceVal];
    }
    // Objects: recurse (but not null)
    // SKIP empty objects - treat {} as "inherit from parent"
    else if (sourceVal !== null && typeof sourceVal === 'object' && !Array.isArray(sourceVal)) {
      // Empty object = inherit (don't override parent values)
      if (Object.keys(sourceVal).length === 0) {
        // Keep target value unchanged - empty source means "no override"
        continue;
      }
      result[key] = deepMerge(targetVal || {}, sourceVal);
    }
    // Primitives: source wins
    else {
      result[key] = sourceVal;
    }
  }
  return result;
}

function createSessionStateContext(options = {}) {
  const candidate = createCandidateSessionStateContext(options);
  if (!candidate) return null;
  if (options.bindSession === true) return sessionStore.bindSessionStateContext(candidate);
  if (options.requireBinding === true) return sessionStore.resolveBoundSessionContext(candidate);
  return candidate;
}

const getSessionTempPath = sessionStore.getSessionTempPath;
const getContextTempPath = sessionStore.getContextTempPath;
const readSessionState = sessionStore.readSessionState;
const writeSessionState = sessionStore.writeSessionState;
const updateSessionState = sessionStore.updateSessionState;
const readContextState = sessionStore.readContextState;
const writeContextState = sessionStore.writeContextState;

/**
 * Characters invalid in filenames across Windows, macOS, Linux
 * Windows: < > : " / \ | ? *
 * macOS/Linux: / and null byte
 * Also includes control characters and other problematic chars
 */
const INVALID_FILENAME_CHARS = /[<>:"/\\|?*\x00-\x1f\x7f]/g;

/**
 * Sanitize slug for safe filesystem usage
 * - Removes invalid filename characters
 * - Replaces non-alphanumeric (except hyphen) with hyphen
 * - Collapses multiple hyphens
 * - Removes leading/trailing hyphens
 * - Limits length to prevent filesystem issues
 *
 * @param {string} slug - Slug to sanitize
 * @returns {string} Sanitized slug (empty string if nothing valid remains)
 */
function sanitizeSlug(slug) {
  if (!slug || typeof slug !== 'string') return '';

  let sanitized = slug
    // Remove invalid filename chars first
    .replace(INVALID_FILENAME_CHARS, '')
    // Replace any non-alphanumeric (except hyphen) with hyphen
    .replace(/[^a-z0-9-]/gi, '-')
    // Collapse multiple consecutive hyphens
    .replace(/-+/g, '-')
    // Remove leading/trailing hyphens
    .replace(/^-+|-+$/g, '')
    // Limit length (most filesystems support 255, but keep reasonable)
    .slice(0, 100);

  return sanitized;
}

/**
 * Extract feature slug from git branch name
 * Pattern: (?:feat|fix|chore|refactor|docs)/(?:[^/]+/)?(.+)
 * @param {string} branch - Git branch name
 * @param {string} pattern - Regex pattern (optional)
 * @returns {string|null} Extracted slug or null
 */
function extractSlugFromBranch(branch, pattern) {
  if (!branch) return null;
  const defaultPattern = /(?:feat|fix|chore|refactor|docs)\/(?:[^\/]+\/)?(.+)/;
  const regex = pattern ? new RegExp(pattern) : defaultPattern;
  const match = branch.match(regex);
  return match ? sanitizeSlug(match[1]) : null;
}

/**
 * Find most recent plan folder by timestamp prefix
 * @param {string} plansDir - Plans directory path
 * @returns {string|null} Most recent plan path or null
 */
function findMostRecentPlan(plansDir) {
  try {
    if (!fs.existsSync(plansDir)) return null;
    const entries = fs.readdirSync(plansDir, { withFileTypes: true });
    const planDirs = entries
      .filter(e => e.isDirectory() && /^\d{6}/.test(e.name))
      .map(e => e.name)
      .sort()
      .reverse();
    return planDirs.length > 0 ? path.join(plansDir, planDirs[0]) : null;
  } catch (e) {
    return null;
  }
}

/**
 * Default timeout for git commands (5 seconds)
 * Prevents indefinite hangs on network mounts or corrupted repos
 */
const DEFAULT_EXEC_TIMEOUT_MS = 5000;

/**
 * Safely execute shell command (internal helper)
 * SECURITY: Only accepts whitelisted git read commands
 * @param {string} cmd - Command to execute
 * @param {Object} options - Execution options
 * @param {string} options.cwd - Working directory (optional)
 * @param {number} options.timeout - Timeout in ms (default: 5000)
 * @returns {string|null} Command output or null
 */
function execSafe(cmd, options = {}) {
  const allowedCommands = {
    'git branch --show-current': ['git', ['branch', '--show-current']],
    'git rev-parse --abbrev-ref HEAD': ['git', ['rev-parse', '--abbrev-ref', 'HEAD']],
    'git rev-parse --show-toplevel': ['git', ['rev-parse', '--show-toplevel']]
  };
  const commandSpec = allowedCommands[cmd];
  if (!commandSpec) {
    return null;
  }

  const { cwd = undefined, timeout = DEFAULT_EXEC_TIMEOUT_MS } = options;
  const [file, args] = commandSpec;

  try {
    return execFileSync(file, args, {
      encoding: 'utf8',
      timeout,
      cwd,
      env: gitEnvironment(),
      stdio: ['pipe', 'pipe', 'pipe'],
      windowsHide: true
    }).trim();
  } catch (e) {
    return null;
  }
}

/**
 * Resolve active plan path using cascading resolution with tracking
 *
 * Resolution semantics:
 * - 'session': Explicitly set via set-active-plan.cjs → ACTIVE (directive)
 * - 'branch': Matched from git branch name → SUGGESTED (hint only)
 * - 'mostRecent': REMOVED - was causing stale plan pollution
 *
 * @param {Object|null} sessionContext - Explicit session state context
 * @param {Object} config - AgentKit config
 * @returns {{ path: string|null, resolvedBy: 'session'|'branch'|null }} Resolution result with tracking
 */
function resolvePlanPath(sessionContext, config) {
  const plansDir = config?.paths?.plans || 'plans';
  const resolution = config?.plan?.resolution || {};
  const order = resolution.order || ['session', 'branch'];
  const branchPattern = resolution.branchPattern;

  for (const method of order) {
    switch (method) {
      case 'session': {
        const state = readSessionState(sessionContext);
        if (state?.activePlan) {
          // Handle absolute paths directly and resolve relative paths from the
          // immutable launch root stored by the v2 session context.
          let resolvedPath = state.activePlan;
          if (!path.isAbsolute(resolvedPath) && state.sessionLaunchRoot) {
            resolvedPath = path.join(state.sessionLaunchRoot, resolvedPath);
          }
          return { path: toDisplayPath(resolvedPath), resolvedBy: 'session' };
        }
        break;
      }
      case 'branch': {
        try {
          if (!sessionContext?.canonicalProjectRoot) break;
          const projectRoot = sessionContext.canonicalProjectRoot;
          const launchRoot = sessionContext.sessionLaunchRoot;
          const projectPlansDir = path.isAbsolute(plansDir) ? plansDir : path.join(launchRoot, plansDir);
          const branch = execSafe('git branch --show-current', { cwd: projectRoot });
          const slug = extractSlugFromBranch(branch, branchPattern);
          if (slug && fs.existsSync(projectPlansDir)) {
            const entries = fs.readdirSync(projectPlansDir, { withFileTypes: true })
              .filter(e => e.isDirectory() && e.name.includes(slug));
            if (entries.length > 0) {
              let planPath = path.join(projectPlansDir, entries[entries.length - 1].name);
              // projectPlansDir descends from the session launch root, which is stored in
              // identity form — lowercased on Windows so it can be compared. That form is
              // for matching, not for display; emitting it would hand the consumer a
              // case-mangled path where the session branch hands back the real one.
              // Restoring the on-disk casing is Windows-only on purpose: on POSIX the
              // resolve would also follow symlinks and rewrite a symlinked plans/ dir.
              if (path.sep === '\\') {
                try {
                  planPath = fs.realpathSync.native(planPath);
                } catch {
                  // Keep the joined path when the directory vanishes between readdir and resolve.
                }
              }
              return {
                path: toDisplayPath(planPath),
                resolvedBy: 'branch'
              };
            }
          }
        } catch (e) {
          // Ignore errors reading plans dir
        }
        break;
      }
      // NOTE: 'mostRecent' case intentionally removed - was causing stale plan pollution
    }
  }
  return { path: null, resolvedBy: null };
}

/**
 * Render a path for output rather than for the filesystem.
 *
 * Every path these hooks emit leaves as text: injected prompt sections the model
 * reads and echoes back into tool calls, and env values a shell interpolates.
 * Windows accepts forward slashes everywhere that matters here, while a
 * backslash path pasted unquoted into bash loses its separators — so the text
 * form is forward slashes on every platform.
 *
 * Only rewrites when the platform separator is a backslash: on POSIX a
 * backslash is a legal filename character, and replacing it would corrupt a
 * real path.
 *
 * @param {string} pathValue - Path to render
 * @returns {string} Path with forward slashes, unchanged on POSIX
 */
function toDisplayPath(pathValue) {
  if (!pathValue || typeof pathValue !== 'string') return pathValue;
  if (path.sep !== '\\') return pathValue;
  return pathValue.replace(/\\/g, '/');
}

/**
 * Normalize path value (trim, remove trailing slashes, handle empty)
 * @param {string} pathValue - Path to normalize
 * @returns {string|null} Normalized path or null if invalid
 */
function normalizePath(pathValue) {
  if (!pathValue || typeof pathValue !== 'string') return null;

  // Trim whitespace
  let normalized = pathValue.trim();

  // Empty after trim = invalid
  if (!normalized) return null;

  // Remove trailing slashes (but keep root "/" or "C:\")
  normalized = normalized.replace(/[/\\]+$/, '');

  // If it became empty (was just slashes), return null
  if (!normalized) return null;

  return normalized;
}

/**
 * Check if path is absolute
 * @param {string} pathValue - Path to check
 * @returns {boolean} True if absolute path
 */
function isAbsolutePath(pathValue) {
  if (!pathValue) return false;
  // Unix absolute: starts with /
  // Windows absolute: starts with drive letter (C:\) or UNC (\\)
  return path.isAbsolute(pathValue);
}

/**
 * Sanitize path values
 * - Normalizes path (trim, remove trailing slashes)
 * - Allows absolute paths (for consolidated plans use case)
 * - Prevents obvious security issues (null bytes, etc.)
 *
 * @param {string} pathValue - Path to sanitize
 * @param {string} projectRoot - Project root for relative path resolution
 * @returns {string|null} Sanitized path or null if invalid
 */
function sanitizePath(pathValue, projectRoot) {
  // Normalize first
  const normalized = normalizePath(pathValue);
  if (!normalized) return null;

  // Block null bytes and other dangerous chars
  if (/[\x00]/.test(normalized)) return null;

  // Allow absolute paths (user explicitly wants consolidated plans elsewhere)
  if (isAbsolutePath(normalized)) {
    return normalized;
  }

  // For relative paths, resolve and validate
  const resolved = path.resolve(projectRoot, normalized);

  // Prevent path traversal outside project (../ attacks).
  // But allow if user explicitly set absolute path.
  //
  // Asked via path.relative rather than a startsWith on projectRoot + sep: a
  // project at a drive or filesystem root makes that prefix a doubled separator
  // ("C:\\", "//"), which nothing starts with, so every relative path in the
  // config was blocked and silently reset to its default.
  const relative = path.relative(projectRoot, resolved);
  if (relative.startsWith('..') || path.isAbsolute(relative)) {
    // This is a relative path trying to escape - block it
    return null;
  }

  return normalized;
}

/**
 * Validate and sanitize config paths
 */
function sanitizeConfig(config, projectRoot) {
  const result = { ...config };

  if (result.plan) {
    result.plan = { ...result.plan };
    if (!sanitizePath(result.plan.reportsDir, projectRoot)) {
      result.plan.reportsDir = DEFAULT_CONFIG.plan.reportsDir;
    }
    // Merge resolution defaults
    result.plan.resolution = {
      ...DEFAULT_CONFIG.plan.resolution,
      ...result.plan.resolution
    };
    // Merge validation defaults
    result.plan.validation = {
      ...DEFAULT_CONFIG.plan.validation,
      ...result.plan.validation
    };
  }

  if (result.paths) {
    result.paths = { ...result.paths };
    if (!sanitizePath(result.paths.docs, projectRoot)) {
      result.paths.docs = DEFAULT_CONFIG.paths.docs;
    }
    if (!sanitizePath(result.paths.plans, projectRoot)) {
      result.paths.plans = DEFAULT_CONFIG.paths.plans;
    }
  }

  if (result.locale) {
    result.locale = { ...result.locale };
  }

  return result;
}

/**
 * Load config with cascading resolution: DEFAULT → resolved AgentKit prefs
 *
 * The preference values come from `ak config prefs resolve --json`, which reads
 * the user config and deep-merges the project config over it. Defaults stay
 * here rather than in the binary: hooks and the binary update on separate
 * schedules, so each side owning its own defaults keeps a newer hook's unknown
 * setting from resolving against an older binary's idea of it. A setting the
 * binary does not report simply falls back to DEFAULT_CONFIG, which is also
 * what happens when the binary cannot be reached at all.
 *
 * @param {Object} options - Options for config loading
 * @param {boolean} options.includeProject - Include project section (default: true)
 * @param {boolean} options.includeAssertions - Include assertions (default: true)
 * @param {boolean} options.includeLocale - Include locale section (default: true)
 * @param {string} options.cwd - Project directory whose config participates in
 *   the merge. A hook is handed the session's directory in its payload, which
 *   is not always the directory the process started in; passing it keeps one
 *   hook run on one project scope, which is also one resolve instead of two.
 */
function loadConfig(options = {}) {
  const {
    includeProject = true,
    includeAssertions = true,
    includeLocale = true,
    cwd = process.cwd()
  } = options;
  const projectRoot = cwd;

  const prefs = resolvePrefs({ cwd });

  // Nothing readable - use defaults
  if (!prefs || Object.keys(prefs).length === 0) {
    return getDefaultConfig(includeProject, includeAssertions, includeLocale);
  }

  try {
    // Deep merge: DEFAULT → resolved prefs (resolved wins)
    const merged = deepMerge(deepMerge({}, DEFAULT_CONFIG), prefs);

    // Build result with optional sections
    const result = {
      plan: merged.plan || DEFAULT_CONFIG.plan,
      paths: merged.paths || DEFAULT_CONFIG.paths,
      docs: merged.docs || DEFAULT_CONFIG.docs
    };

    if (includeLocale) {
      result.locale = merged.locale || DEFAULT_CONFIG.locale;
    }
    // Always include trust config for verification
    result.trust = merged.trust || DEFAULT_CONFIG.trust;
    if (includeProject) {
      result.project = merged.project || DEFAULT_CONFIG.project;
    }
    if (includeAssertions) {
      result.assertions = merged.assertions || [];
    }
    // Coding level for output style selection (-1 to 5, default: -1 = disabled)
    // -1 = disabled (no injection, saves tokens)
    // 0-5 = inject corresponding level guidelines
    result.codingLevel = merged.codingLevel ?? -1;
    // Skills configuration
    result.skills = merged.skills || DEFAULT_CONFIG.skills;
    // Hooks configuration
    result.hooks = merged.hooks || DEFAULT_CONFIG.hooks;
    // Statusline mode
    result.statusline = merged.statusline || 'full';
    result.statuslineColors = merged.statuslineColors ?? true;
    result.statuslineQuota = merged.statuslineQuota ?? true;
    result.statuslineLayout = merged.statuslineLayout || undefined;

    return sanitizeConfig(result, projectRoot);
  } catch (e) {
    return getDefaultConfig(includeProject, includeAssertions, includeLocale);
  }
}

/**
 * Get default config with optional sections
 */
function getDefaultConfig(includeProject = true, includeAssertions = true, includeLocale = true) {
  const result = {
    plan: { ...DEFAULT_CONFIG.plan },
    paths: { ...DEFAULT_CONFIG.paths },
    docs: { ...DEFAULT_CONFIG.docs },
    codingLevel: -1,  // Default: disabled (no injection, saves tokens)
    skills: { ...DEFAULT_CONFIG.skills },
    hooks: { ...DEFAULT_CONFIG.hooks },
    statusline: 'full',
    statuslineColors: true,
    statuslineQuota: true
  };
  if (includeLocale) {
    result.locale = { ...DEFAULT_CONFIG.locale };
  }
  if (includeProject) {
    result.project = { ...DEFAULT_CONFIG.project };
  }
  if (includeAssertions) {
    result.assertions = [];
  }
  return result;
}

/**
 * Escape shell special characters for env file values
 * Handles: backslash, double quote, dollar sign, backtick
 */
function escapeShellValue(str) {
  if (typeof str !== 'string') return str;
  return str
    .replace(/\\/g, '\\\\')   // Backslash first
    .replace(/"/g, '\\"')     // Double quotes
    .replace(/\$/g, '\\$')    // Dollar sign
    .replace(/`/g, '\\`');    // Backticks (command substitution)
}

/**
 * Write environment variable to CLAUDE_ENV_FILE (with escaping)
 */
function writeEnv(envFile, key, value) {
  if (envFile && value !== null && value !== undefined) {
    const escaped = escapeShellValue(String(value));
    fs.appendFileSync(envFile, `export ${key}="${escaped}"\n`);
  }
}

/**
 * Get reports path based on plan resolution
 * Only uses plan-specific path for 'session' resolved plans (explicitly active)
 * Branch-matched (suggested) plans use default path to avoid pollution
 *
 * @param {string|null} planPath - The plan path
 * @param {string|null} resolvedBy - How plan was resolved ('session'|'branch'|null)
 * @param {Object} planConfig - Plan configuration
 * @param {Object} pathsConfig - Paths configuration
 * @param {string|null} baseDir - Optional base directory for absolute path resolution
 * @returns {string} Reports path (absolute if baseDir provided, relative otherwise)
 */
function getReportsPath(planPath, resolvedBy, planConfig, pathsConfig, baseDir = null) {
  const reportsDir = normalizePath(planConfig?.reportsDir) || 'reports';
  const plansDir = normalizePath(pathsConfig?.plans) || 'plans';

  let reportPath;
  // Only use plan-specific reports path if explicitly active (session state)
  // Validate the normalized path so whitespace cannot create invalid directories.
  const normalizedPlanPath = planPath && resolvedBy === 'session' ? normalizePath(planPath) : null;
  if (normalizedPlanPath) {
    reportPath = `${normalizedPlanPath}/${reportsDir}`;
  } else {
    // Default path for no plan or suggested (branch-matched) plans
    reportPath = `${plansDir}/${reportsDir}`;
  }

  // Return absolute path if baseDir provided
  // If reportPath is already absolute,
  // don't double-join with baseDir — path.join concatenates, not resolves
  if (baseDir) {
    return toDisplayPath(path.isAbsolute(reportPath) ? reportPath : path.join(baseDir, reportPath));
  }
  return reportPath + '/';
}

/**
 * Format issue ID with prefix
 */
function formatIssueId(issueId, planConfig) {
  if (!issueId) return null;
  return planConfig.issuePrefix ? `${planConfig.issuePrefix}${issueId}` : `#${issueId}`;
}

/**
 * Extract issue ID from branch name
 */
function extractIssueFromBranch(branch) {
  if (!branch) return null;
  const patterns = [
    /(?:issue|gh|fix|feat|bug)[/-]?(\d+)/i,
    /[/-](\d+)[/-]/,
    /#(\d+)/
  ];
  for (const pattern of patterns) {
    const match = branch.match(pattern);
    if (match) return match[1];
  }
  return null;
}

/**
 * Format date according to dateFormat config
 * Supports: YYMMDD, YYMMDD-HHmm, YYYYMMDD, etc.
 * @param {string} format - Date format string
 * @returns {string} Formatted date
 */
function formatDate(format) {
  const now = new Date();
  const pad = (n, len = 2) => String(n).padStart(len, '0');

  const tokens = {
    'YYYY': now.getFullYear(),
    'YY': String(now.getFullYear()).slice(-2),
    'MM': pad(now.getMonth() + 1),
    'DD': pad(now.getDate()),
    'HH': pad(now.getHours()),
    'mm': pad(now.getMinutes()),
    'ss': pad(now.getSeconds())
  };

  let result = format;
  for (const [token, value] of Object.entries(tokens)) {
    result = result.replace(token, value);
  }
  return result;
}

/**
 * Validate naming pattern result
 * Ensures pattern resolves to a usable directory name
 *
 * @param {string} pattern - Resolved naming pattern
 * @returns {{ valid: boolean, error?: string }} Validation result
 */
function validateNamingPattern(pattern) {
  if (!pattern || typeof pattern !== 'string') {
    return { valid: false, error: 'Pattern is empty or not a string' };
  }

  // After removing {slug} placeholder, should still have content
  const withoutSlug = pattern.replace(/\{slug\}/g, '').replace(/-+/g, '-').replace(/^-|-$/g, '');
  if (!withoutSlug) {
    return { valid: false, error: 'Pattern resolves to empty after removing {slug}' };
  }

  // Check for remaining unresolved placeholders (besides {slug})
  const unresolvedMatch = withoutSlug.match(/\{[^}]+\}/);
  if (unresolvedMatch) {
    return { valid: false, error: `Unresolved placeholder: ${unresolvedMatch[0]}` };
  }

  // Pattern must contain {slug} for agents to substitute
  if (!pattern.includes('{slug}')) {
    return { valid: false, error: 'Pattern must contain {slug} placeholder' };
  }

  return { valid: true };
}

/**
 * Resolve naming pattern with date and optional issue prefix
 * Keeps {slug} as placeholder for agents to substitute
 *
 * Example: namingFormat="{date}-{issue}-{slug}", dateFormat="YYMMDD-HHmm", issue="GH-88"
 * Returns: "251212-1830-GH-88-{slug}" (if issue exists)
 * Returns: "251212-1830-{slug}" (if no issue)
 *
 * @param {Object} planConfig - Plan configuration
 * @param {string|null} gitBranch - Current git branch (for issue extraction)
 * @returns {string} Resolved naming pattern with {slug} placeholder
 */
function resolveNamingPattern(planConfig, gitBranch) {
  const { namingFormat, dateFormat, issuePrefix } = planConfig;
  const formattedDate = formatDate(dateFormat);

  // Try to extract issue ID from branch name
  const issueId = extractIssueFromBranch(gitBranch);
  const fullIssue = issueId && issuePrefix ? `${issuePrefix}${issueId}` : null;

  // Build pattern by substituting {date} and {issue}, keep {slug}
  let pattern = namingFormat;
  pattern = pattern.replace('{date}', formattedDate);

  if (fullIssue) {
    pattern = pattern.replace('{issue}', fullIssue);
  } else {
    // Remove {issue} and any trailing/leading dash
    pattern = pattern.replace(/-?\{issue\}-?/, '-').replace(/--+/g, '-');
  }

  // Clean up the result:
  // - Remove leading/trailing hyphens
  // - Collapse multiple hyphens (except around {slug})
  pattern = pattern
    .replace(/^-+/, '')           // Remove leading hyphens
    .replace(/-+$/, '')           // Remove trailing hyphens
    .replace(/-+(\{slug\})/g, '-$1')  // Single hyphen before {slug}
    .replace(/(\{slug\})-+/g, '$1-')  // Single hyphen after {slug}
    .replace(/--+/g, '-');        // Collapse other multiple hyphens

  // Validate the resulting pattern
  const validation = validateNamingPattern(pattern);
  if (!validation.valid) {
    // Log warning but return pattern anyway (fail-safe)
    if (process.env.CK_DEBUG) {
      console.error(`[ck-config] Warning: ${validation.error}`);
    }
  }

  return pattern;
}

/**
 * Get current git branch (safe execution)
 * @param {string|null} cwd - Working directory to run git command from (optional)
 * @returns {string|null} Current branch name or null
 */
function getGitBranch(cwd = null) {
  return execSafe('git branch --show-current', { cwd: cwd || undefined });
}

/**
 * Get git repository root directory
 * @param {string|null} cwd - Working directory to run git command from (optional)
 * @returns {string|null} Git root absolute path or null if not in git repo
 */
function getGitRoot(cwd = null) {
  return execSafe('git rev-parse --show-toplevel', { cwd: cwd || undefined });
}

/**
 * Extract task list ID from plan resolution for Claude Code Tasks coordination
 * Only returns ID for session-resolved plans (explicitly active, not branch-suggested)
 *
 * Cross-platform: path.basename() handles both Unix/Windows separators
 *
 * @param {{ path: string|null, resolvedBy: 'session'|'branch'|null }} resolved - Plan resolution result
 * @returns {string|null} Task list ID (plan directory name) or null
 */
function extractTaskListId(resolved) {
  if (!resolved || resolved.resolvedBy !== 'session' || !resolved.path) {
    return null;
  }
  return path.basename(resolved.path);
}

/**
 * Check if a hook is enabled in config
 * Returns true if hook is not defined (default enabled)
 *
 * @param {string} hookName - Hook name (script basename without .cjs)
 * @param {Object} [options]
 * @param {string} [options.cwd] - Project directory whose config decides the
 *   toggle. Pass the payload's cwd where the hook has one, so the toggle and
 *   the settings the hook goes on to read come from the same project.
 * @returns {boolean} Whether hook is enabled
 */
function isHookEnabled(hookName, options = {}) {
  const config = loadConfig({
    includeProject: false,
    includeAssertions: false,
    includeLocale: false,
    ...(options.cwd ? { cwd: options.cwd } : {})
  });
  const hooks = config.hooks || {};
  // Return true if undefined (default enabled), otherwise return the boolean value
  return hooks[hookName] !== false;
}

module.exports = {
  DEFAULT_CONFIG,
  INVALID_FILENAME_CHARS,
  deepMerge,
  loadConfig,
  normalizePath,
  toDisplayPath,
  isAbsolutePath,
  sanitizePath,
  sanitizeSlug,
  sanitizeConfig,
  escapeShellValue,
  writeEnv,
  normalizeSessionId,
  createSessionStateContext,
  getSessionTempPath,
  getContextTempPath,
  readContextState,
  writeContextState,
  readSessionState,
  writeSessionState,
  updateSessionState,
  resolvePlanPath,
  extractSlugFromBranch,
  findMostRecentPlan,
  getReportsPath,
  formatIssueId,
  extractIssueFromBranch,
  formatDate,
  validateNamingPattern,
  resolveNamingPattern,
  getGitBranch,
  getGitRoot,
  extractTaskListId,
  isHookEnabled
};
