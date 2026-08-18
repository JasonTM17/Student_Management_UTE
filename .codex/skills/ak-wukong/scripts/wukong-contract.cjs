#!/usr/bin/env node
'use strict';

const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const PROTOCOL_VERSION = '1.0';
const MODES = new Set([
  'challenge',
  'rescue',
  'counterexample',
  'portability',
  'shadow-review',
  'experiment',
  'chaos',
]);
const TARGET_KINDS = new Set([
  'repo',
  'file',
  'claim',
  'plan',
  'runtime',
  'API',
  'database',
  'workflow',
]);
const IDENTITY_KINDS = new Set(['git', 'file-sha256', 'runtime', 'artifact', 'custom']);
const SEVERITIES = new Set(['low', 'medium', 'high', 'critical']);
const SEVERITY_RANK = new Map([['low', 0], ['medium', 1], ['high', 2], ['critical', 3]]);
const DEPTH_LIMITS = new Map([
  ['quick', 1],
  ['standard', 3],
  ['deep', 7],
]);
const CLAIM_STATUSES = new Set([
  'FALSIFIED',
  'NOT_FALSIFIED',
  'INCONCLUSIVE',
  'UNDERDEFINED',
]);
const GATES = new Set([
  'BLOCK',
  'REPAIR_THEN_RETEST',
  'PROCEED_WITH_RESIDUAL_RISK',
]);
const CONFIDENCE = new Set(['low', 'medium', 'high']);
const EVIDENCE_KINDS = new Set(['OBSERVED', 'DERIVED', 'PRIOR', 'ASSUMED']);
const EVIDENCE_GRADES = new Set(['E0', 'E1', 'E2', 'E3', 'E4']);
const INDEPENDENCE_KINDS = new Set([
  'primary',
  'fresh-process',
  'clean-fixture',
  'alternate-observation',
  'independent-agent',
]);
const SENSITIVE_KEY = /^(?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|passwd|secret|private[_-]?key|credential)$/i;
const SECRET_VALUE_PATTERNS = [
  /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----/,
  /\b(?:ghp|github_pat|sk_live|sk_test|xox[baprs])[-_][-A-Za-z0-9_]{12,}\b/,
  /\bAKIA[0-9A-Z]{16}\b/,
];

class ContractError extends Error {
  constructor(errors) {
    super(errors.join('; '));
    this.name = 'ContractError';
    this.errors = errors;
  }
}

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function isNonEmptyString(value, max = 4096) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= max;
}

function isIsoUtc(value) {
  return (
    typeof value === 'string'
    && /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?Z$/.test(value)
    && !Number.isNaN(Date.parse(value))
  );
}

function isPortableRelative(value) {
  if (!isNonEmptyString(value, 512)) return false;
  const normalized = value.replaceAll('\\', '/');
  if (
    normalized.startsWith('/')
    || normalized.startsWith('//')
    || /^[A-Za-z]:\//.test(normalized)
    || normalized.includes('\0')
  ) return false;
  const parts = normalized.split('/');
  return !parts.includes('..') && !parts.includes('');
}

function redactSensitiveText(value) {
  if (typeof value !== 'string') return value;
  let redacted = value;
  for (const pattern of SECRET_VALUE_PATTERNS) {
    redacted = redacted.replace(pattern, '[REDACTED]');
  }
  redacted = redacted.replace(
    /\b(api[_-]?key|access[_-]?token|refresh[_-]?token|password|passwd|secret|credential)\s*[:=]\s*([^\s,;]+)/gi,
    '$1=[REDACTED]',
  );
  return redacted;
}

function findSensitiveMaterial(value, currentPath = '$', findings = []) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => findSensitiveMaterial(item, `${currentPath}[${index}]`, findings));
    return findings;
  }
  if (isObject(value)) {
    for (const [key, child] of Object.entries(value)) {
      const childPath = `${currentPath}.${key}`;
      if (SENSITIVE_KEY.test(key) && child !== null && child !== '' && child !== false) {
        findings.push(`${childPath} contains a forbidden secret-bearing field`);
      }
      findSensitiveMaterial(child, childPath, findings);
    }
    return findings;
  }
  if (typeof value === 'string') {
    for (const pattern of SECRET_VALUE_PATTERNS) {
      pattern.lastIndex = 0;
      if (pattern.test(value)) {
        findings.push(`${currentPath} appears to contain secret material`);
        break;
      }
    }
  }
  return findings;
}

function requireObject(value, field, errors) {
  if (!isObject(value)) {
    errors.push(`${field} must be an object`);
    return false;
  }
  return true;
}

function requireString(value, field, errors, max = 4096) {
  if (!isNonEmptyString(value, max)) {
    errors.push(`${field} must be a non-empty string up to ${max} characters`);
    return false;
  }
  return true;
}

function requireStringArray(value, field, errors, { nonEmpty = true } = {}) {
  if (
    !Array.isArray(value)
    || (nonEmpty && value.length === 0)
    || value.some((item) => !isNonEmptyString(item, 4096))
  ) {
    errors.push(`${field} must be ${nonEmpty ? 'a non-empty ' : 'an '}array of strings`);
    return false;
  }
  return true;
}

function validateIdentity(identity, field, errors) {
  if (!requireObject(identity, field, errors)) return;
  if (!IDENTITY_KINDS.has(identity.kind)) {
    errors.push(`${field}.kind must be one of: ${[...IDENTITY_KINDS].join(', ')}`);
  }
  requireString(identity.value, `${field}.value`, errors, 512);
  if (!isIsoUtc(identity.captured_at)) {
    errors.push(`${field}.captured_at must be an ISO-8601 UTC timestamp`);
  }
}

function validateMission(mission) {
  const errors = [];
  if (!requireObject(mission, 'mission', errors)) return { valid: false, errors };

  if (mission.protocol_version !== PROTOCOL_VERSION) {
    errors.push(`protocol_version must be "${PROTOCOL_VERSION}"`);
  }
  if (
    typeof mission.mission_id !== 'string'
    || !/^[a-z0-9](?:[a-z0-9-]{4,94})[a-z0-9]$/.test(mission.mission_id)
  ) {
    errors.push('mission_id must be 6-96 lowercase letters, digits, or hyphens');
  }
  if (!MODES.has(mission.mode)) {
    errors.push(`mode must be one of: ${[...MODES].join(', ')}`);
  }

  if (requireObject(mission.target, 'target', errors)) {
    if (!TARGET_KINDS.has(mission.target.kind)) {
      errors.push(`target.kind must be one of: ${[...TARGET_KINDS].join(', ')}`);
    }
    if (!isPortableRelative(mission.target.locator)) {
      errors.push('target.locator must be a portable relative locator without parent traversal');
    }
    requireString(mission.target.claim, 'target.claim', errors, 1000);
    validateIdentity(mission.target.identity, 'target.identity', errors);
  }

  if (requireObject(mission.scope, 'scope', errors)) {
    if (requireStringArray(mission.scope.include, 'scope.include', errors)) {
      mission.scope.include.forEach((entry, index) => {
        if (!isPortableRelative(entry)) {
          errors.push(`scope.include[${index}] must be portable and relative`);
        }
      });
    }
    if (requireStringArray(mission.scope.exclude, 'scope.exclude', errors, { nonEmpty: false })) {
      mission.scope.exclude.forEach((entry, index) => {
        if (!isPortableRelative(entry)) {
          errors.push(`scope.exclude[${index}] must be portable and relative`);
        }
      });
    }
    requireStringArray(mission.scope.non_goals, 'scope.non_goals', errors);
  }

  if (!Array.isArray(mission.invariants) || mission.invariants.length === 0) {
    errors.push('invariants must be a non-empty array');
  } else {
    const ids = new Set();
    mission.invariants.forEach((invariant, index) => {
      const field = `invariants[${index}]`;
      if (!requireObject(invariant, field, errors)) return;
      if (typeof invariant.id !== 'string' || !/^INV-[0-9]{3,}$/.test(invariant.id)) {
        errors.push(`${field}.id must match INV- followed by at least three digits`);
      } else if (ids.has(invariant.id)) {
        errors.push(`${field}.id duplicates ${invariant.id}`);
      } else {
        ids.add(invariant.id);
      }
      requireString(invariant.statement, `${field}.statement`, errors, 1000);
      requireString(invariant.failure_signal, `${field}.failure_signal`, errors, 1000);
      if (!SEVERITIES.has(invariant.severity)) {
        errors.push(`${field}.severity must be low, medium, high, or critical`);
      }
    });
  }

  if (requireObject(mission.risk, 'risk', errors)) {
    if (!SEVERITIES.has(mission.risk.severity)) {
      errors.push('risk.severity must be low, medium, high, or critical');
    }
    requireStringArray(mission.risk.domains, 'risk.domains', errors);
    if (SEVERITIES.has(mission.risk.severity) && Array.isArray(mission.invariants)) {
      const higher = mission.invariants.find((item) => (
        SEVERITIES.has(item?.severity)
        && SEVERITY_RANK.get(item.severity) > SEVERITY_RANK.get(mission.risk.severity)
      ));
      if (higher) errors.push(`risk.severity understates invariant ${higher.id}`);
    }
  }

  if (requireObject(mission.authority, 'authority', errors)) {
    if (mission.authority.level !== 'R0') errors.push('authority.level must be R0');
    if (mission.authority.write_mode !== 'report-only') {
      errors.push('authority.write_mode must be report-only');
    }
    if (mission.authority.external_effects !== false) {
      errors.push('authority.external_effects must be false');
    }
  }

  if (requireObject(mission.budget, 'budget', errors)) {
    const limit = DEPTH_LIMITS.get(mission.budget.depth);
    if (limit === undefined) {
      errors.push('budget.depth must be quick, standard, or deep');
    }
    if (
      !Number.isInteger(mission.budget.max_probes)
      || mission.budget.max_probes < 1
      || (limit !== undefined && mission.budget.max_probes > limit)
    ) {
      errors.push(`budget.max_probes must be between 1 and the depth limit${limit ? ` (${limit})` : ''}`);
    }
    if (
      !Number.isInteger(mission.budget.timeout_seconds)
      || mission.budget.timeout_seconds < 1
      || mission.budget.timeout_seconds > 3600
    ) {
      errors.push('budget.timeout_seconds must be an integer from 1 to 3600');
    }
  }

  if (
    !isPortableRelative(mission.artifact_dir)
    || !mission.artifact_dir.replaceAll('\\', '/').startsWith('plans/reports/wukong-')
  ) {
    errors.push('artifact_dir must be a portable plans/reports/wukong-* relative path');
  }

  if (requireObject(mission.handoff, 'handoff', errors)) {
    requireString(mission.handoff.owner, 'handoff.owner', errors, 128);
    requireString(mission.handoff.strategy_owner, 'handoff.strategy_owner', errors, 128);
  }

  errors.push(...findSensitiveMaterial(mission));
  return { valid: errors.length === 0, errors };
}

function gradeNumber(grade) {
  return Number.parseInt(String(grade).slice(1), 10);
}

function sha256Text(value) {
  return crypto.createHash('sha256').update(Buffer.from(value, 'utf8')).digest('hex');
}

function validateHypotheses(hypotheses, errors, { required = true } = {}) {
  if (!Array.isArray(hypotheses) || (required && hypotheses.length < 3)) {
    errors.push(`hypotheses must contain H0 and at least two competing alternatives${required ? '' : ' when supplied'}`);
    return new Set();
  }
  const ids = new Set();
  hypotheses.forEach((hypothesis, index) => {
    const field = `hypotheses[${index}]`;
    if (!requireObject(hypothesis, field, errors)) return;
    if (typeof hypothesis.id !== 'string' || !/^H(?:0|[1-9][0-9]*)$/.test(hypothesis.id)) {
      errors.push(`${field}.id must be H0 or H followed by a positive integer`);
    } else if (ids.has(hypothesis.id)) {
      errors.push(`${field}.id duplicates ${hypothesis.id}`);
    } else {
      ids.add(hypothesis.id);
    }
    requireString(hypothesis.statement, `${field}.statement`, errors, 1000);
    requireStringArray(hypothesis.predicted_observations, `${field}.predicted_observations`, errors);
    requireString(hypothesis.falsifier, `${field}.falsifier`, errors, 1000);
  });
  if (required && !ids.has('H0')) errors.push('hypotheses must include H0');
  if (required && [...ids].filter((id) => id !== 'H0').length < 2) {
    errors.push('hypotheses must include at least two alternatives to H0');
  }
  return ids;
}

function validateEvidence(evidence, index, errors) {
  const field = `evidence[${index}]`;
  if (!requireObject(evidence, field, errors)) return;
  if (typeof evidence.id !== 'string' || !/^EV-[0-9]{3,}$/.test(evidence.id)) {
    errors.push(`${field}.id must match EV- followed by at least three digits`);
  }
  if (!EVIDENCE_KINDS.has(evidence.kind)) {
    errors.push(`${field}.kind must be OBSERVED, DERIVED, PRIOR, or ASSUMED`);
  }
  if (!EVIDENCE_GRADES.has(evidence.grade)) {
    errors.push(`${field}.grade must be E0 through E4`);
  }
  if (!isPortableRelative(evidence.source)) {
    errors.push(`${field}.source must be a portable relative locator`);
  }
  requireString(evidence.observation, `${field}.observation`, errors);
  if (typeof evidence.observation === 'string'
      && evidence.observation_digest !== sha256Text(evidence.observation)) {
    errors.push(`${field}.observation_digest must bind the exact observation text`);
  }
  if (typeof evidence.probe_id !== 'string' || !/^PR-[0-9]{3,}$/.test(evidence.probe_id)) {
    errors.push(`${field}.probe_id must match PR- followed by at least three digits`);
  }
  if (typeof evidence.execution_id !== 'string'
      || !/^[a-z0-9][a-z0-9._:-]{5,127}$/i.test(evidence.execution_id)) {
    errors.push(`${field}.execution_id must be a stable 6-128 character execution identity`);
  }
  requireStringArray(evidence.invariant_ids, `${field}.invariant_ids`, errors);
  requireStringArray(evidence.supports, `${field}.supports`, errors, { nonEmpty: false });
  requireStringArray(evidence.contradicts, `${field}.contradicts`, errors, { nonEmpty: false });
  if (Array.isArray(evidence.supports) && Array.isArray(evidence.contradicts)) {
    if (evidence.supports.length + evidence.contradicts.length === 0) {
      errors.push(`${field} must discriminate at least one hypothesis`);
    }
    const overlap = evidence.supports.find((id) => evidence.contradicts.includes(id));
    if (overlap) errors.push(`${field} cannot both support and contradict ${overlap}`);
  }
  if (!INDEPENDENCE_KINDS.has(evidence.independence_kind)) {
    errors.push(`${field}.independence_kind is invalid`);
  }
  requireStringArray(evidence.independent_of, `${field}.independent_of`, errors, { nonEmpty: false });
  if (evidence.independence_kind === 'primary' && Array.isArray(evidence.independent_of)
      && evidence.independent_of.length > 0) {
    errors.push(`${field} primary evidence cannot declare independent_of`);
  }
  if (evidence.independence_kind && evidence.independence_kind !== 'primary'
      && (!Array.isArray(evidence.independent_of) || evidence.independent_of.length === 0)) {
    errors.push(`${field} confirmation evidence must reference its primary evidence`);
  }
  requireString(evidence.reproduction, `${field}.reproduction`, errors);
  if (!isIsoUtc(evidence.observed_at)) {
    errors.push(`${field}.observed_at must be an ISO-8601 UTC timestamp`);
  }
  requireStringArray(evidence.redactions, `${field}.redactions`, errors, { nonEmpty: false });
}

function validateVerdict(verdict) {
  const errors = [];
  if (!requireObject(verdict, 'verdict', errors)) return { valid: false, errors };

  if (verdict.protocol_version !== PROTOCOL_VERSION) {
    errors.push(`protocol_version must be "${PROTOCOL_VERSION}"`);
  }
  if (
    typeof verdict.mission_id !== 'string'
    || !/^[a-z0-9](?:[a-z0-9-]{4,94})[a-z0-9]$/.test(verdict.mission_id)
  ) {
    errors.push('mission_id must be 6-96 lowercase letters, digits, or hyphens');
  }
  validateIdentity(verdict.target_identity, 'target_identity', errors);
  if (!CLAIM_STATUSES.has(verdict.claim_status)) {
    errors.push(`claim_status must be one of: ${[...CLAIM_STATUSES].join(', ')}`);
  }
  if (!GATES.has(verdict.recommended_gate)) {
    errors.push(`recommended_gate must be one of: ${[...GATES].join(', ')}`);
  }
  if (!SEVERITIES.has(verdict.severity)) {
    errors.push('severity must be low, medium, high, or critical');
  }
  if (!CONFIDENCE.has(verdict.confidence)) {
    errors.push('confidence must be low, medium, or high');
  }
  if (!EVIDENCE_GRADES.has(verdict.evidence_grade)) {
    errors.push('evidence_grade must be E0 through E4');
  }
  requireString(verdict.mechanism, 'mechanism', errors);

  const hypothesisIds = validateHypotheses(verdict.hypotheses, errors, {
    required: verdict.claim_status !== 'UNDERDEFINED',
  });

  const evidenceById = new Map();
  if (!Array.isArray(verdict.evidence)) {
    errors.push('evidence must be an array');
  } else {
    verdict.evidence.forEach((item, index) => {
      validateEvidence(item, index, errors);
      if (typeof item?.id === 'string') {
        if (evidenceById.has(item.id)) errors.push(`duplicate evidence id: ${item.id}`);
        else evidenceById.set(item.id, item);
      }
    });
    for (const [id, item] of evidenceById) {
      for (const hypothesisId of [...(item.supports || []), ...(item.contradicts || [])]) {
        if (!hypothesisIds.has(hypothesisId)) errors.push(`${id} references unknown hypothesis: ${hypothesisId}`);
      }
      for (const parentId of item.independent_of || []) {
        const parent = evidenceById.get(parentId);
        if (!parent) errors.push(`${id} references unknown primary evidence: ${parentId}`);
        else if (parentId === id) errors.push(`${id} cannot be independent of itself`);
        else if (parent.execution_id === item.execution_id) {
          errors.push(`${id} confirmation evidence must have a distinct execution identity from ${parentId}`);
        }
      }
    }
  }

  const decisiveIds = verdict.decisive_evidence_ids;
  if (!Array.isArray(decisiveIds)
      || (verdict.claim_status !== 'UNDERDEFINED' && decisiveIds.length === 0)
      || decisiveIds.some((id) => typeof id !== 'string')) {
    errors.push('decisive_evidence_ids must identify the evidence that decides the verdict');
  }
  if (Array.isArray(decisiveIds) && new Set(decisiveIds).size !== decisiveIds.length) {
    errors.push('decisive_evidence_ids must be unique');
  }
  const decisiveEvidence = Array.isArray(decisiveIds)
    ? decisiveIds.map((id) => evidenceById.get(id)).filter(Boolean)
    : [];
  for (const id of decisiveIds || []) {
    if (!evidenceById.has(id)) errors.push(`decisive evidence does not exist: ${id}`);
  }
  if (decisiveEvidence.length > 0 && EVIDENCE_GRADES.has(verdict.evidence_grade)) {
    const highest = Math.max(...decisiveEvidence.map((item) => (
      EVIDENCE_GRADES.has(item.grade) ? gradeNumber(item.grade) : 0
    )));
    if (gradeNumber(verdict.evidence_grade) > highest) {
      errors.push('evidence_grade cannot exceed the highest decisive evidence grade');
    }
  }

  requireStringArray(verdict.tested_invariants, 'tested_invariants', errors, { nonEmpty: false });
  if (!Array.isArray(verdict.counterexamples)) {
    errors.push('counterexamples must be an array');
  } else {
    verdict.counterexamples.forEach((item, index) => {
      const field = `counterexamples[${index}]`;
      if (!requireObject(item, field, errors)) return;
      if (typeof item.id !== 'string' || !/^CE-[0-9]{3,}$/.test(item.id)) {
        errors.push(`${field}.id must match CE- followed by at least three digits`);
      }
      if (typeof item.invariant_id !== 'string' || !/^INV-[0-9]{3,}$/.test(item.invariant_id)) {
        errors.push(`${field}.invariant_id must identify an invariant`);
      }
      requireString(item.reproduction, `${field}.reproduction`, errors);
      if (item.minimized !== true) errors.push(`${field}.minimized must be true`);
      requireStringArray(item.evidence_ids, `${field}.evidence_ids`, errors);
      if (!(item.confirmation_evidence_id === null
          || (typeof item.confirmation_evidence_id === 'string' && /^EV-[0-9]{3,}$/.test(item.confirmation_evidence_id)))) {
        errors.push(`${field}.confirmation_evidence_id must be null or an evidence ID`);
      }
      if (Object.prototype.hasOwnProperty.call(item, 'independently_confirmed')) {
        errors.push(`${field}.independently_confirmed is self-asserted; bind confirmation_evidence_id instead`);
      }
    });
  }

  if (requireObject(verdict.probe_summary, 'probe_summary', errors)) {
    for (const field of ['attempted', 'passed', 'failed', 'blocked']) {
      if (!Number.isInteger(verdict.probe_summary[field]) || verdict.probe_summary[field] < 0) {
        errors.push(`probe_summary.${field} must be a non-negative integer`);
      }
    }
    if (
      Number.isInteger(verdict.probe_summary.attempted)
      && ['passed', 'failed', 'blocked'].every((field) => Number.isInteger(verdict.probe_summary[field]))
      && verdict.probe_summary.passed + verdict.probe_summary.failed + verdict.probe_summary.blocked
        !== verdict.probe_summary.attempted
    ) {
      errors.push('probe_summary outcomes must sum to attempted');
    }
  }

  requireStringArray(verdict.coverage_limits, 'coverage_limits', errors, { nonEmpty: false });
  requireStringArray(verdict.residual_risks, 'residual_risks', errors, { nonEmpty: false });
  requireStringArray(verdict.missing_fields, 'missing_fields', errors, { nonEmpty: false });

  if (requireObject(verdict.handoff, 'handoff', errors)) {
    requireString(verdict.handoff.owner, 'handoff.owner', errors, 128);
    requireString(verdict.handoff.reason, 'handoff.reason', errors);
    requireString(verdict.handoff.next_action, 'handoff.next_action', errors);
    requireString(verdict.handoff.exact_retest, 'handoff.exact_retest', errors);
  }

  const status = verdict.claim_status;
  const gate = verdict.recommended_gate;
  const grade = EVIDENCE_GRADES.has(verdict.evidence_grade) ? gradeNumber(verdict.evidence_grade) : -1;
  const decisiveObserved = decisiveEvidence.filter((item) => (
    item.kind === 'OBSERVED' && EVIDENCE_GRADES.has(item.grade) && gradeNumber(item.grade) >= 2
  ));
  if (status === 'FALSIFIED') {
    if (!['BLOCK', 'REPAIR_THEN_RETEST'].includes(gate)) {
      errors.push('FALSIFIED requires BLOCK or REPAIR_THEN_RETEST');
    }
    if (grade < 2) errors.push('FALSIFIED requires evidence_grade E2 or higher');
    if (!Array.isArray(verdict.counterexamples) || verdict.counterexamples.length === 0) {
      errors.push('FALSIFIED requires at least one minimized counterexample');
    }
    if (!Array.isArray(verdict.tested_invariants) || verdict.tested_invariants.length === 0) {
      errors.push('FALSIFIED requires a tested invariant');
    }
    if (decisiveObserved.length === 0) errors.push('FALSIFIED requires decisive OBSERVED evidence at E2 or higher');
    if (!decisiveObserved.some((item) => item.contradicts?.includes('H0')
      && item.supports?.some((id) => id !== 'H0'))) {
      errors.push('FALSIFIED decisive evidence must contradict H0 and support a failure hypothesis');
    }
    for (const counterexample of verdict.counterexamples || []) {
      const linked = new Set(counterexample.evidence_ids || []);
      if (![...(counterexample.evidence_ids || [])].every((id) => evidenceById.has(id))) {
        errors.push(`${counterexample.id} references unknown evidence`);
      }
      if (!(counterexample.evidence_ids || []).some((id) => decisiveIds?.includes(id))) {
        errors.push(`${counterexample.id} must link to decisive evidence`);
      }
      for (const id of counterexample.evidence_ids || []) {
        const item = evidenceById.get(id);
        if (item && !item.invariant_ids?.includes(counterexample.invariant_id)) {
          errors.push(`${counterexample.id} evidence ${id} is not bound to ${counterexample.invariant_id}`);
        }
      }
      if (['high', 'critical'].includes(verdict.severity)) {
        const confirmation = evidenceById.get(counterexample.confirmation_evidence_id);
        const primaryIds = (confirmation?.independent_of || []).filter((id) => linked.has(id));
        if (!confirmation
            || !linked.has(counterexample.confirmation_evidence_id)
            || confirmation.kind !== 'OBSERVED'
            || !EVIDENCE_GRADES.has(confirmation.grade)
            || gradeNumber(confirmation.grade) < 3
            || confirmation.independence_kind === 'primary'
            || primaryIds.length === 0
            || !confirmation.invariant_ids?.includes(counterexample.invariant_id)) {
          errors.push(`${counterexample.id} requires bound confirmation evidence from a distinct E3 execution`);
        }
      }
    }
  } else if (status === 'NOT_FALSIFIED') {
    if (gate !== 'PROCEED_WITH_RESIDUAL_RISK') {
      errors.push('NOT_FALSIFIED requires PROCEED_WITH_RESIDUAL_RISK');
    }
    if (grade < 2) errors.push('NOT_FALSIFIED requires evidence_grade E2 or higher');
    if (!Array.isArray(verdict.tested_invariants) || verdict.tested_invariants.length === 0) {
      errors.push('NOT_FALSIFIED requires at least one tested invariant');
    }
    if (!Array.isArray(verdict.coverage_limits) || verdict.coverage_limits.length === 0) {
      errors.push('NOT_FALSIFIED requires coverage_limits');
    }
    if (!Array.isArray(verdict.residual_risks) || verdict.residual_risks.length === 0) {
      errors.push('NOT_FALSIFIED requires residual_risks');
    }
    if (!isObject(verdict.probe_summary) || verdict.probe_summary.attempted < 1) {
      errors.push('NOT_FALSIFIED requires at least one attempted probe');
    }
    if (decisiveObserved.length === 0) errors.push('NOT_FALSIFIED requires decisive OBSERVED evidence at E2 or higher');
    if (!decisiveObserved.some((item) => item.supports?.includes('H0'))) {
      errors.push('NOT_FALSIFIED decisive evidence must support H0');
    }
  } else if (status === 'INCONCLUSIVE') {
    if (!['BLOCK', 'REPAIR_THEN_RETEST'].includes(gate)) {
      errors.push('INCONCLUSIVE cannot proceed without residual-risk gate');
    }
    if (!Array.isArray(verdict.residual_risks) || verdict.residual_risks.length === 0) {
      errors.push('INCONCLUSIVE requires residual_risks');
    }
  } else if (status === 'UNDERDEFINED') {
    if (gate !== 'BLOCK') errors.push('UNDERDEFINED requires BLOCK');
    if (!Array.isArray(verdict.missing_fields) || verdict.missing_fields.length === 0) {
      errors.push('UNDERDEFINED requires missing_fields');
    }
  }

  errors.push(...findSensitiveMaterial(verdict));
  return { valid: errors.length === 0, errors };
}

function validateBundle(mission, verdict) {
  const missionResult = validateMission(mission);
  const verdictResult = validateVerdict(verdict);
  const errors = [
    ...missionResult.errors.map((error) => `mission: ${error}`),
    ...verdictResult.errors.map((error) => `verdict: ${error}`),
  ];
  if (missionResult.valid && verdictResult.valid) {
    if (mission.mission_id !== verdict.mission_id) {
      errors.push('bundle mission_id mismatch');
    }
    if (JSON.stringify(mission.target.identity) !== JSON.stringify(verdict.target_identity)) {
      errors.push('bundle target identity mismatch');
    }
    if (mission.risk.severity !== verdict.severity) {
      errors.push('bundle verdict severity must equal mission risk severity');
    }
    const invariantIds = new Set(mission.invariants.map((item) => item.id));
    for (const id of verdict.tested_invariants) {
      if (!invariantIds.has(id)) errors.push(`verdict tested invariant not in mission: ${id}`);
    }
    for (const counterexample of verdict.counterexamples) {
      if (!invariantIds.has(counterexample.invariant_id)) {
        errors.push(`counterexample invariant not in mission: ${counterexample.invariant_id}`);
      }
    }
    const evidenceById = new Map(verdict.evidence.map((item) => [item.id, item]));
    for (const item of verdict.evidence) {
      for (const invariantId of item.invariant_ids || []) {
        if (!invariantIds.has(invariantId)) {
          errors.push(`evidence ${item.id} invariant not in mission: ${invariantId}`);
        }
      }
    }
    for (const invariantId of verdict.tested_invariants) {
      const covered = (verdict.decisive_evidence_ids || []).some((id) => (
        evidenceById.get(id)?.invariant_ids?.includes(invariantId)
      ));
      if (!covered) errors.push(`tested invariant has no decisive evidence: ${invariantId}`);
    }
  }
  return { valid: errors.length === 0, errors };
}

function rankProbes(input) {
  const probes = Array.isArray(input) ? input : input?.probes;
  if (!Array.isArray(probes) || probes.length === 0) {
    throw new ContractError(['probes must be a non-empty array']);
  }
  const seen = new Set();
  const scored = probes.map((probe, index) => {
    const errors = [];
    if (!isObject(probe)) throw new ContractError([`probes[${index}] must be an object`]);
    if (!isNonEmptyString(probe.id, 128)) errors.push('id is required');
    else if (seen.has(probe.id)) errors.push(`duplicate id ${probe.id}`);
    else seen.add(probe.id);
    for (const field of ['discrimination', 'impact', 'reversibility', 'cost', 'risk']) {
      if (!Number.isInteger(probe[field]) || probe[field] < 1 || probe[field] > 5) {
        errors.push(`${field} must be an integer from 1 to 5`);
      }
    }
    const safetyReasons = [];
    if (probe.authority_level !== 'R0') safetyReasons.push('authority is not R0');
    if (probe.mutates !== false) safetyReasons.push('probe must explicitly declare mutates=false');
    if (probe.external_effects !== false) safetyReasons.push('probe must explicitly declare external_effects=false');
    if (errors.length) throw new ContractError(errors.map((error) => `probes[${index}]: ${error}`));
    const score = (
      4 * probe.discrimination
      + 2 * probe.impact
      + probe.reversibility
      - probe.cost
      - 2 * probe.risk
    );
    return {
      ...probe,
      eligible: safetyReasons.length === 0,
      safety_reasons: safetyReasons,
      score,
    };
  });
  return scored.sort((left, right) => (
    Number(right.eligible) - Number(left.eligible)
    || right.score - left.score
    || left.id.localeCompare(right.id)
  ));
}

function readJson(filePath) {
  try {
    return JSON.parse(fs.readFileSync(filePath, 'utf8'));
  } catch (error) {
    throw new ContractError([`cannot read JSON ${filePath}: ${redactSensitiveText(error.message)}`]);
  }
}

function printHelp() {
  console.log(`wukong-contract

Usage:
  node scripts/wukong-contract.cjs validate-mission <mission.json> [--json]
  node scripts/wukong-contract.cjs validate-verdict <verdict.json> [--json]
  node scripts/wukong-contract.cjs validate-bundle <mission.json> <verdict.json> [--json]
  node scripts/wukong-contract.cjs rank-probes <probes.json> [--json]

Validation is read-only. Exit 0 means valid; exit 1 means contract violations;
exit 2 means invalid CLI usage.`);
}

function main(argv) {
  const args = argv.filter((arg) => arg !== '--json');
  const asJson = argv.includes('--json');
  const [command, ...paths] = args;
  if (!command || command === '--help' || command === '-h') {
    printHelp();
    return 0;
  }
  try {
    let result;
    if (command === 'validate-mission' && paths.length === 1) {
      result = validateMission(readJson(path.resolve(paths[0])));
    } else if (command === 'validate-verdict' && paths.length === 1) {
      result = validateVerdict(readJson(path.resolve(paths[0])));
    } else if (command === 'validate-bundle' && paths.length === 2) {
      result = validateBundle(readJson(path.resolve(paths[0])), readJson(path.resolve(paths[1])));
    } else if (command === 'rank-probes' && paths.length === 1) {
      const ranked = rankProbes(readJson(path.resolve(paths[0])));
      result = { valid: true, ranked };
    } else {
      printHelp();
      return 2;
    }
    if (asJson) {
      console.log(JSON.stringify(result, null, 2));
    } else if (result.valid) {
      console.log(command === 'rank-probes' ? 'Probe ranking valid' : 'Wukong contract valid');
      if (result.ranked) {
        for (const probe of result.ranked) {
          console.log(`${probe.eligible ? 'ELIGIBLE' : 'INELIGIBLE'} ${probe.id}: ${probe.score}`);
        }
      }
    } else {
      console.error('Wukong contract invalid');
      result.errors.forEach((error) => console.error(`- ${redactSensitiveText(error)}`));
    }
    return result.valid ? 0 : 1;
  } catch (error) {
    const errors = error instanceof ContractError ? error.errors : [error.message];
    const result = { valid: false, errors: errors.map(redactSensitiveText) };
    if (asJson) console.log(JSON.stringify(result, null, 2));
    else {
      console.error('Wukong contract invalid');
      result.errors.forEach((item) => console.error(`- ${item}`));
    }
    return 1;
  }
}

module.exports = {
  ContractError,
  findSensitiveMaterial,
  isPortableRelative,
  rankProbes,
  redactSensitiveText,
  validateBundle,
  validateMission,
  validateVerdict,
};

if (require.main === module) {
  process.exitCode = main(process.argv.slice(2));
}
