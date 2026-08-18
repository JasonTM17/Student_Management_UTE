/**
 * Resolve the writing language for human-facing GitHub prose authored by
 * ak:ship and ak:review-pr.
 *
 * Precedence (first non-empty wins):
 *   1. AGENTKIT_LANGUAGE
 *   2. CK_RESPONSE_LANGUAGE
 *   3. AgentKit config  locale.responseLanguage
 *   4. default "en"
 *
 * The config value arrives from `ak config prefs resolve`, which merges the
 * project file over the user file. This module used to scrape both YAML files
 * with a regex, which only ever understood the two shapes it was written for
 * and silently returned nothing for a config that expressed the same setting
 * any other legal way — a quoted key, a flow mapping, an anchor.
 *
 * The scraper also honored a bare top-level `language:`. No config surface ever
 * wrote that key — it was an affordance of the regex, not a setting — so it is
 * gone rather than carried forward into a contract.
 *
 * Invalid / unsupported tags fall back to "en" with an explicit fallbackReason.
 * Titles stay conventional English; only human-facing prose is localized.
 */

'use strict';

const { resolvePrefs } = require('./ak-prefs-client.cjs');

const DEFAULT_LANGUAGE = 'en';
const LANGUAGE_TAG_RE = /^[a-z]{2,3}(-[a-z0-9]+)*$/;

/**
 * @param {unknown} raw
 * @returns {{ ok: true, value: string } | { ok: false, reason: string }}
 */
function normalizeLanguageTag(raw) {
  if (raw === null || raw === undefined) {
    return { ok: false, reason: 'empty' };
  }
  if (typeof raw !== 'string') {
    return { ok: false, reason: 'not-a-string' };
  }
  const trimmed = raw.trim();
  if (!trimmed) {
    return { ok: false, reason: 'empty' };
  }
  const value = trimmed.toLowerCase();
  if (!LANGUAGE_TAG_RE.test(value)) {
    return { ok: false, reason: 'invalid-tag' };
  }
  return { ok: true, value };
}

/**
 * @param {object} [options]
 * @param {string} [options.cwd]
 * @param {NodeJS.ProcessEnv} [options.env]
 * @returns {{
 *   language: string,
 *   source: string,
 *   requested: string|null,
 *   fallbackReason: string|null,
 *   rejected: Array<{ source: string, raw: string, reason: string }>,
 *   defaultLanguage: string
 * }}
 */
function resolveWritingLanguage(options = {}) {
  const cwd = options.cwd || process.cwd();
  const env = options.env || process.env;

  const prefs = resolvePrefs({ cwd }) || {};
  const locale = prefs.locale && typeof prefs.locale === 'object' ? prefs.locale : {};

  /** @type {Array<{ source: string, raw: unknown }>} */
  const candidates = [
    { source: 'env:AGENTKIT_LANGUAGE', raw: env.AGENTKIT_LANGUAGE },
    { source: 'env:CK_RESPONSE_LANGUAGE', raw: env.CK_RESPONSE_LANGUAGE },
    { source: 'config:locale.responseLanguage', raw: locale.responseLanguage }
  ];

  /** @type {Array<{ source: string, raw: string, reason: string }>} */
  const rejected = [];

  for (const candidate of candidates) {
    if (candidate.raw === null || candidate.raw === undefined || candidate.raw === '') {
      continue;
    }
    const normalized = normalizeLanguageTag(candidate.raw);
    if (normalized.ok) {
      return {
        language: normalized.value,
        source: candidate.source,
        requested: String(candidate.raw).trim(),
        fallbackReason: null,
        rejected,
        defaultLanguage: DEFAULT_LANGUAGE
      };
    }
    // Skip invalid candidates and continue the precedence chain.
    rejected.push({
      source: candidate.source,
      raw: String(candidate.raw).trim(),
      reason: normalized.reason
    });
  }

  const lastRejected = rejected.length > 0 ? rejected[rejected.length - 1] : null;
  return {
    language: DEFAULT_LANGUAGE,
    source: 'default',
    requested: lastRejected ? lastRejected.raw : null,
    fallbackReason: lastRejected ? lastRejected.reason : null,
    rejected,
    defaultLanguage: DEFAULT_LANGUAGE
  };
}

function main(argv = process.argv.slice(2)) {
  const asJson = argv.includes('--json');
  const result = resolveWritingLanguage();
  if (asJson) {
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
    return;
  }
  process.stdout.write(`${result.language}\n`);
}

module.exports = {
  DEFAULT_LANGUAGE,
  LANGUAGE_TAG_RE,
  normalizeLanguageTag,
  resolveWritingLanguage
};

if (require.main === module) {
  main();
}
