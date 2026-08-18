#!/usr/bin/env node
'use strict';

const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const test = require('node:test');

const {
  rankProbes,
  redactSensitiveText,
  validateBundle,
  validateMission,
  validateVerdict,
} = require('./wukong-contract.cjs');

const SCRIPT = path.join(__dirname, 'wukong-contract.cjs');

function mission() {
  return {
    protocol_version: '1.0',
    mission_id: 'wukong-portability-001',
    mode: 'portability',
    target: {
      kind: 'repo',
      locator: '.',
      claim: 'A clean Windows checkout can invoke every registered skill without absolute paths.',
      identity: {
        kind: 'git',
        value: '0123456789abcdef0123456789abcdef01234567',
        captured_at: '2026-08-12T00:00:00Z',
      },
    },
    scope: {
      include: ['.codex/**', 'engineer/**'],
      exclude: ['.env*', 'secrets/**'],
      non_goals: ['Modify ClaudeKit', 'Deploy a release'],
    },
    invariants: [{
      id: 'INV-001',
      statement: 'Every registered skill resolves from a project-relative path.',
      failure_signal: 'A registered skill contains or resolves through a machine-specific absolute path.',
      severity: 'high',
    }],
    risk: { severity: 'high', domains: ['portability'] },
    authority: { level: 'R0', write_mode: 'report-only', external_effects: false },
    budget: { depth: 'standard', max_probes: 3, timeout_seconds: 900 },
    artifact_dir: 'plans/reports/wukong-20260812T000000Z-portability',
    handoff: { owner: 'controller', strategy_owner: 'kongming' },
  };
}

function evidence(grade = 'E2', overrides = {}) {
  const observation = overrides.observation || 'The bounded validator returned the recorded result.';
  return {
    id: overrides.id || 'EV-001',
    kind: 'OBSERVED',
    grade,
    source: 'engineer/skills/ak-wukong/scripts/wukong-contract.test.cjs',
    observation,
    observation_digest: crypto.createHash('sha256').update(observation, 'utf8').digest('hex'),
    probe_id: overrides.probe_id || 'PR-001',
    execution_id: overrides.execution_id || 'contract-test-primary-001',
    invariant_ids: ['INV-001'],
    supports: overrides.supports || ['H0'],
    contradicts: overrides.contrads || ['H1'],
    independence_kind: overrides.independence_kind || 'primary',
    independent_of: overrides.independent_of || [],
    reproduction: 'node engineer/skills/ak-wukong/scripts/wukong-contract.test.cjs',
    observed_at: '2026-08-12T00:01:00Z',
    redactions: [],
  };
}

function verdict(status = 'INCONCLUSIVE') {
  const value = {
    protocol_version: '1.0',
    mission_id: 'wukong-portability-001',
    target_identity: mission().target.identity,
    claim_status: status,
    recommended_gate: 'BLOCK',
    severity: 'high',
    confidence: 'low',
    evidence_grade: 'E2',
    mechanism: 'A decisive portability observation is still missing.',
    hypotheses: [{
      id: 'H0',
      statement: 'Every registered path remains portable.',
      predicted_observations: ['All registry checks resolve project-relative paths.'],
      falsifier: 'Any registry check resolves a machine-specific path.',
    }, {
      id: 'H1',
      statement: 'A registry contains a machine-specific path.',
      predicted_observations: ['At least one registry check reports an absolute path.'],
      falsifier: 'Every registry check resolves project-relative paths.',
    }, {
      id: 'H2',
      statement: 'Registries are relative but disagree on required files.',
      predicted_observations: ['Registry hash sets differ.'],
      falsifier: 'All registry hash sets are identical.',
    }],
    evidence: [evidence()],
    decisive_evidence_ids: ['EV-001'],
    tested_invariants: [],
    counterexamples: [],
    probe_summary: { attempted: 1, passed: 0, failed: 0, blocked: 1 },
    coverage_limits: ['No clean-machine runtime execution.'],
    residual_risks: ['Runtime discovery may differ on another machine.'],
    missing_fields: [],
    handoff: {
      owner: 'tester',
      reason: 'A clean fixture must execute the adapter.',
      next_action: 'Run a project-local discovery smoke test.',
      exact_retest: 'Open a fresh runtime and invoke /ak:wukong against the fixture.',
    },
  };
  if (status === 'NOT_FALSIFIED') {
    value.recommended_gate = 'PROCEED_WITH_RESIDUAL_RISK';
    value.confidence = 'medium';
    value.mechanism = 'No tested invariant failed within bounded coverage.';
    value.tested_invariants = ['INV-001'];
    value.probe_summary = { attempted: 1, passed: 1, failed: 0, blocked: 0 };
  } else if (status === 'UNDERDEFINED') {
    value.evidence_grade = 'E0';
    value.evidence = [];
    value.decisive_evidence_ids = [];
    value.mechanism = 'The target identity is not sufficiently defined.';
    value.missing_fields = ['target.identity'];
  } else if (status === 'FALSIFIED') {
    value.recommended_gate = 'REPAIR_THEN_RETEST';
    value.confidence = 'high';
    value.evidence_grade = 'E3';
    value.evidence = [
      evidence('E2', { supports: ['H1'], contrads: ['H0'] }),
      evidence('E3', {
        id: 'EV-002',
        probe_id: 'PR-002',
        execution_id: 'contract-test-confirmation-002',
        supports: ['H1'],
        contrads: ['H0'],
        independence_kind: 'fresh-process',
        independent_of: ['EV-001'],
        observation: 'A fresh process reproduced the same project-relative resolution failure.',
      }),
    ];
    value.decisive_evidence_ids = ['EV-001', 'EV-002'];
    value.mechanism = 'A hard-coded drive path prevents project-relative resolution.';
    value.tested_invariants = ['INV-001'];
    value.counterexamples = [{
      id: 'CE-001',
      invariant_id: 'INV-001',
      reproduction: 'Copy the adapter to a different drive and resolve the registered command.',
      minimized: true,
      evidence_ids: ['EV-001', 'EV-002'],
      confirmation_evidence_id: 'EV-002',
    }];
    value.probe_summary = { attempted: 2, passed: 0, failed: 2, blocked: 0 };
  }
  return value;
}

test('accepts a complete report-only mission', () => {
  assert.deepEqual(validateMission(mission()), { valid: true, errors: [] });
});

test('rejects Windows absolute paths and parent traversal', () => {
  const value = mission();
  value.target.locator = 'C:\\repo\\source';
  value.artifact_dir = '..\\outside';
  const result = validateMission(value);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /target\.locator/);
  assert.match(result.errors.join('\n'), /artifact_dir/);
});

test('enforces report-only R0 authority', () => {
  const value = mission();
  value.authority = { level: 'R2', write_mode: 'source', external_effects: true };
  const result = validateMission(value);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /authority\.level/);
  assert.match(result.errors.join('\n'), /write_mode/);
  assert.match(result.errors.join('\n'), /external_effects/);
});

test('enforces depth-specific probe budgets', () => {
  const value = mission();
  value.budget = { depth: 'quick', max_probes: 2, timeout_seconds: 30 };
  assert.equal(validateMission(value).valid, false);
});

test('rejects secret-bearing fields and redacts common token shapes', () => {
  const value = mission();
  value.api_key = 'do-not-store-this';
  assert.match(validateMission(value).errors.join('\n'), /forbidden secret-bearing field/);
  assert.equal(redactSensitiveText('token=github_pat_abcdefghijklmnopqrstuvwxyz'), 'token=[REDACTED]');
});

test('accepts a bounded inconclusive verdict', () => {
  assert.deepEqual(validateVerdict(verdict()), { valid: true, errors: [] });
});

test('requires E2, coverage, and residual risk for NOT_FALSIFIED', () => {
  const valid = verdict('NOT_FALSIFIED');
  assert.equal(validateVerdict(valid).valid, true);
  valid.evidence_grade = 'E1';
  valid.coverage_limits = [];
  valid.residual_risks = [];
  const result = validateVerdict(valid);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /E2/);
  assert.match(result.errors.join('\n'), /coverage_limits/);
  assert.match(result.errors.join('\n'), /residual_risks/);
});

test('requires an independently confirmed counterexample for high FALSIFIED', () => {
  const valid = verdict('FALSIFIED');
  assert.equal(validateVerdict(valid).valid, true);
  valid.counterexamples[0].confirmation_evidence_id = null;
  const result = validateVerdict(valid);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /confirmation evidence/);
});

test('rejects duplicate evidence identifiers', () => {
  const value = verdict('FALSIFIED');
  value.evidence.push({ ...value.evidence[0] });
  const result = validateVerdict(value);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /duplicate evidence id/);
});

test('rejects self-declared independent confirmation without distinct evidence', () => {
  const value = verdict('FALSIFIED');
  value.evidence = [value.evidence[0]];
  value.decisive_evidence_ids = ['EV-001'];
  value.counterexamples[0].evidence_ids = ['EV-001'];
  value.counterexamples[0].confirmation_evidence_id = null;
  const result = validateVerdict(value);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /confirmation evidence/);
});

test('enforces claim-status to gate mapping', () => {
  const value = verdict('INCONCLUSIVE');
  value.recommended_gate = 'PROCEED_WITH_RESIDUAL_RISK';
  assert.match(validateVerdict(value).errors.join('\n'), /INCONCLUSIVE/);
});

test('requires missing fields for UNDERDEFINED', () => {
  const value = verdict('UNDERDEFINED');
  assert.equal(validateVerdict(value).valid, true);
  value.missing_fields = [];
  assert.match(validateVerdict(value).errors.join('\n'), /missing_fields/);
});

test('bundle rejects identity drift and unknown invariants', () => {
  const value = verdict('NOT_FALSIFIED');
  value.target_identity = { ...value.target_identity, value: 'different' };
  value.tested_invariants.push('INV-999');
  const result = validateBundle(mission(), value);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /identity mismatch/);
  assert.match(result.errors.join('\n'), /INV-999/);
});

test('bundle rejects severity downgrade below the frozen mission risk', () => {
  const value = verdict('FALSIFIED');
  value.severity = 'medium';
  value.counterexamples[0].confirmation_evidence_id = null;
  const result = validateBundle(mission(), value);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /severity must equal mission risk severity/);
});

test('mission risk cannot understate a higher-severity invariant', () => {
  const value = mission();
  value.risk.severity = 'medium';
  const result = validateMission(value);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /understates invariant/);
});

test('ranks high-information safe probes ahead of unsafe probes', () => {
  const ranked = rankProbes([
    {
      id: 'safe-high',
      discrimination: 5,
      impact: 4,
      reversibility: 5,
      cost: 1,
      risk: 1,
      authority_level: 'R0',
      mutates: false,
      external_effects: false,
    },
    {
      id: 'unsafe-high',
      discrimination: 5,
      impact: 5,
      reversibility: 1,
      cost: 1,
      risk: 5,
      authority_level: 'R3',
      mutates: true,
      external_effects: true,
    },
    {
      id: 'safe-low',
      discrimination: 2,
      impact: 2,
      reversibility: 5,
      cost: 2,
      risk: 1,
      authority_level: 'R0',
      mutates: false,
      external_effects: false,
    },
  ]);
  assert.deepEqual(ranked.map((item) => item.id), ['safe-high', 'safe-low', 'unsafe-high']);
  assert.equal(ranked[2].eligible, false);
  assert.equal(ranked[2].safety_reasons.length, 3);
});

test('probe safety declarations fail closed when omitted or mistyped', () => {
  const base = {
    id: 'missing-safety', discrimination: 3, impact: 3, reversibility: 3, cost: 1, risk: 1, authority_level: 'R0',
  };
  const ranked = rankProbes([
    base,
    { ...base, id: 'mistyped-safety', mutates: 'false', external_effects: 0 },
  ]);
  assert.equal(ranked.every((probe) => probe.eligible === false), true);
  assert.equal(ranked.every((probe) => probe.safety_reasons.length === 2), true);
});

test('CLI emits machine-readable success and failure', () => {
  const temp = fs.mkdtempSync(path.join(os.tmpdir(), 'wukong-contract-'));
  try {
    const validPath = path.join(temp, 'mission.json');
    fs.writeFileSync(validPath, JSON.stringify(mission()), 'utf8');
    const success = spawnSync(process.execPath, [SCRIPT, 'validate-mission', validPath, '--json'], {
      encoding: 'utf8',
    });
    assert.equal(success.status, 0);
    assert.equal(JSON.parse(success.stdout).valid, true);

    const invalid = mission();
    invalid.authority.level = 'R3';
    fs.writeFileSync(validPath, JSON.stringify(invalid), 'utf8');
    const failure = spawnSync(process.execPath, [SCRIPT, 'validate-mission', validPath, '--json'], {
      encoding: 'utf8',
    });
    assert.equal(failure.status, 1);
    assert.equal(JSON.parse(failure.stdout).valid, false);
  } finally {
    fs.rmSync(temp, { recursive: true, force: true });
  }
});
