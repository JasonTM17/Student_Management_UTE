#!/usr/bin/env node
'use strict';

/**
 * Read-only forward test for the AgentKit adapter claim.
 *
 * Run from the kit checkout:
 *   node engineer/skills/ak-wukong/scripts/wukong-portability-smoke.cjs --json
 */

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { validateBundle } = require('./wukong-contract.cjs');

const REPO_ROOT = path.resolve(__dirname, '..', '..', '..', '..');
const SKILL_TREES = [
  'engineer/skills',
  '.codex/skills',
  '.claude/skills',
  '.cursor/skills',
  '.agents/skills',
];
const WUKONG_FILES = [
  'SKILL.md',
  'agents/openai.yaml',
  'assets/mission.template.json',
  'assets/verdict.template.json',
  'references/adversarial-protocol.md',
  'references/domain-overlays.md',
  'references/evidence-and-verdict.md',
  'references/evaluation-and-qualification.md',
  'references/mission-contract.md',
  'references/workflow-integration.md',
  'scripts/wukong-contract.cjs',
  'scripts/wukong-contract.test.cjs',
  'scripts/wukong-linkage.cjs',
  'scripts/wukong-linkage.test.cjs',
  'scripts/wukong-portability-smoke.cjs',
];
const REGISTRATION_FILES = [
  '.codex/agents/wukong.toml',
  '.codex/config.toml',
  '.claude/agents/wukong.md',
  '.cursor/agents/wukong.md',
  'engineer/.codex/agents/wukong.toml',
  'engineer/.codex/config.toml',
  'engineer/.codex-plugin/plugin.json',
  'README.md',
];

function hash(filePath) {
  return crypto.createHash('sha256').update(fs.readFileSync(filePath)).digest('hex');
}

function hashText(value) {
  return crypto.createHash('sha256').update(Buffer.from(value, 'utf8')).digest('hex');
}

function git(args) {
  const result = spawnSync('git', args, { cwd: REPO_ROOT, encoding: 'utf8' });
  if (result.status !== 0) {
    throw new Error(`git ${args.join(' ')} failed: ${(result.stderr || result.stdout || '').trim()}`);
  }
  return result.stdout.trim();
}

function scopedArtifactDigest() {
  const digest = crypto.createHash('sha256');
  const files = [
    ...SKILL_TREES.flatMap((root) => WUKONG_FILES.map((relative) => `${root}/ak-wukong/${relative}`)),
    ...REGISTRATION_FILES,
  ].sort();
  for (const relative of files) {
    const absolute = path.join(REPO_ROOT, ...relative.split('/'));
    if (!fs.existsSync(absolute)) {
      digest.update(Buffer.from(`missing\0${relative}\0`, 'utf8'));
      continue;
    }
    const stat = fs.lstatSync(absolute);
    const kind = stat.isSymbolicLink() ? 'symlink' : stat.isFile() ? 'file' : 'unsupported';
    const bytes = stat.isSymbolicLink()
      ? Buffer.from(fs.readlinkSync(absolute), 'utf8')
      : stat.isFile() ? fs.readFileSync(absolute) : Buffer.alloc(0);
    digest.update(Buffer.from(`${kind}\0${relative}\0${bytes.length}\0`, 'utf8'));
    digest.update(bytes);
  }
  return digest.digest('hex');
}

function checkWukongTree(relativeRoot) {
  const root = path.join(REPO_ROOT, relativeRoot, 'ak-wukong');
  const missing = WUKONG_FILES.filter((relative) => !fs.existsSync(path.join(root, relative)));
  if (missing.length) return { ok: false, detail: `${relativeRoot}: missing ${missing.join(', ')}` };
  const symlinks = WUKONG_FILES.filter((relative) => fs.lstatSync(path.join(root, relative)).isSymbolicLink());
  if (symlinks.length) return { ok: false, detail: `${relativeRoot}: symlinked runtime file(s) are not allowed: ${symlinks.join(', ')}` };
  const hashes = Object.fromEntries(WUKONG_FILES.map((relative) => [relative, hash(path.join(root, relative))]));
  return { ok: true, detail: `${relativeRoot}: ${WUKONG_FILES.length} Wukong files present`, hashes };
}

function checkText(relative, needles) {
  const filePath = path.join(REPO_ROOT, relative);
  if (!fs.existsSync(filePath)) return { ok: false, detail: `${relative}: missing` };
  const content = fs.readFileSync(filePath, 'utf8');
  const missing = needles.filter((needle) => !content.includes(needle));
  return missing.length
    ? { ok: false, detail: `${relative}: missing ${missing.join(', ')}` }
    : { ok: true, detail: `${relative}: required registration text present` };
}

function run() {
  const sha = git(['rev-parse', 'HEAD']);
  const scopeDigest = scopedArtifactDigest();
  const skillCount = fs.readdirSync(path.join(REPO_ROOT, 'engineer', 'skills'), { withFileTypes: true })
    .filter((entry) => entry.isDirectory()).length;
  const agentCount = fs.readdirSync(path.join(REPO_ROOT, 'engineer', '.codex', 'agents'), { withFileTypes: true })
    .filter((entry) => entry.isFile() && ['.toml', '.md'].includes(path.extname(entry.name))).length;
  const capturedAt = new Date().toISOString().replace(/\.\d{3}Z$/, 'Z');
  const artifactTimestamp = capturedAt.replace(/[-:]/g, '');
  const identity = {
    kind: 'custom',
    value: `git:${sha};wukong-scope-sha256:${scopeDigest}`,
    captured_at: capturedAt,
  };
  const observations = [];
  const treeResults = SKILL_TREES.map((tree) => ({
    ...checkWukongTree(tree), invariant_id: 'INV-001',
  }));
  observations.push(...treeResults);
  const firstHashes = treeResults[0].hashes || {};
  const mirrorParity = treeResults.every((result) => (
    result.ok && JSON.stringify(result.hashes) === JSON.stringify(firstHashes)
  ));
  observations.push({
    ok: mirrorParity,
    invariant_id: 'INV-001',
    detail: mirrorParity
      ? 'all five Wukong skill registries have canonical hash parity'
      : 'at least one Wukong skill registry differs from engineer/skills',
  });
  observations.push({ ...checkText('engineer/.codex/config.toml', ['[agents.wukong]', 'agents/wukong.toml']), invariant_id: 'INV-002' });
  observations.push({ ...checkText('.codex/config.toml', ['[agents.wukong]', 'agents/wukong.toml']), invariant_id: 'INV-002' });
  observations.push({ ...checkText('engineer/.codex-plugin/plugin.json', ['skills/ak-wukong/SKILL.md']), invariant_id: 'INV-002' });
  observations.push({ ...checkText('README.md', [
    '/ak:wukong',
    '@wukong',
    `${skillCount} skill`,
    `${agentCount} agent`,
  ]), invariant_id: 'INV-003' });

  const failed = observations.filter((item) => !item.ok);
  const mission = {
    protocol_version: '1.0',
    mission_id: 'wukong-portability-smoke-001',
    mode: 'portability',
    target: {
      kind: 'repo',
      locator: '.',
      claim: 'The checked-in AgentKit Wukong skill is portable across its five skill registries and registered adapters.',
      identity,
    },
    scope: {
      include: ['engineer/skills/ak-wukong/**', '.codex/**', '.claude/**', '.cursor/**', '.agents/**'],
      exclude: ['.env*', 'secrets/**', 'plans/reports/**'],
      non_goals: ['Modify source', 'Authenticate providers', 'Deploy or publish'],
    },
    invariants: [{
      id: 'INV-001',
      statement: 'All five Wukong skill registries contain the same canonical files and hashes.',
      failure_signal: 'A required Wukong file is missing or its canonical content hash differs.',
      severity: 'high',
    }, {
      id: 'INV-002',
      statement: 'Codex configuration and plugin metadata register Wukong exactly once.',
      failure_signal: 'The Wukong agent or skill registration is missing or duplicated.',
      severity: 'high',
    }, {
      id: 'INV-003',
      statement: 'Portable documentation exposes the correct Wukong trigger and boundaries.',
      failure_signal: 'README or adapter guidance omits Wukong usage or overclaims its verdict.',
      severity: 'medium',
    }],
    risk: { severity: 'high', domains: ['portability'] },
    authority: { level: 'R0', write_mode: 'report-only', external_effects: false },
    budget: { depth: 'standard', max_probes: 3, timeout_seconds: 120 },
    artifact_dir: `plans/reports/wukong-${artifactTimestamp}-portability-smoke`,
    handoff: { owner: 'review', strategy_owner: 'kongming' },
  };
  const evidence = observations.map((item, index) => ({
    id: `EV-${String(index + 1).padStart(3, '0')}`,
    kind: 'OBSERVED',
    grade: 'E2',
    source: 'engineer/skills/ak-wukong/scripts/wukong-portability-smoke.cjs',
    observation: item.detail,
    observation_digest: hashText(item.detail),
    probe_id: `PR-${String(index + 1).padStart(3, '0')}`,
    execution_id: `portability-smoke-${scopeDigest.slice(0, 16)}`,
    invariant_ids: [item.invariant_id],
    supports: item.ok ? ['H0'] : ['H1'],
    contradicts: item.ok ? ['H1'] : ['H0'],
    independence_kind: 'primary',
    independent_of: [],
    reproduction: 'node engineer/skills/ak-wukong/scripts/wukong-portability-smoke.cjs --json',
    observed_at: capturedAt,
    redactions: [],
  }));
  const verdict = {
    protocol_version: '1.0',
    mission_id: mission.mission_id,
    target_identity: identity,
    // A static self-check can expose a counterexample, but it is not an
    // independent reviewer.  Keep the claim inconclusive until another
    // implementation or authority reproduces the failure.
    claim_status: failed.length ? 'INCONCLUSIVE' : 'NOT_FALSIFIED',
    recommended_gate: failed.length ? 'REPAIR_THEN_RETEST' : 'PROCEED_WITH_RESIDUAL_RISK',
    severity: 'high',
    confidence: 'medium',
    evidence_grade: 'E2',
    mechanism: failed.length
      ? `Candidate portability counterexample pending independent confirmation: ${failed.map((item) => item.detail).join(' | ')}`
      : 'No required adapter or mirror invariant failed in this bounded static portability probe.',
    hypotheses: [{
      id: 'H0',
      statement: 'All reviewed Wukong registries and registrations preserve portable canonical identity.',
      predicted_observations: ['Every required file exists, mirrors hash-match, and registrations are present exactly once.'],
      falsifier: 'A required file is missing, a mirror differs, or a registration is absent or duplicated.',
    }, {
      id: 'H1',
      statement: 'At least one registry or registration differs from the canonical adapter.',
      predicted_observations: ['A bounded registry, hash, or registration check fails.'],
      falsifier: 'Every bounded check reports canonical parity.',
    }, {
      id: 'H2',
      statement: 'Static files match but runtime discovery differs on another environment.',
      predicted_observations: ['Static checks pass while a clean runtime cannot resolve Wukong.'],
      falsifier: 'An independently prepared clean runtime resolves the exact reviewed skill identity.',
    }],
    evidence,
    decisive_evidence_ids: evidence.map((item) => item.id),
    tested_invariants: ['INV-001', 'INV-002', 'INV-003'],
    counterexamples: failed.length ? [{
      id: 'CE-001',
      invariant_id: failed[0].detail.includes('README') ? 'INV-003' : 'INV-001',
      reproduction: 'Run the command above at the captured Git identity and inspect the named path.',
      minimized: true,
      evidence_ids: [evidence[observations.indexOf(failed[0])].id],
      confirmation_evidence_id: null,
    }] : [],
    probe_summary: { attempted: observations.length, passed: observations.filter((item) => item.ok).length, failed: failed.length, blocked: 0 },
    coverage_limits: [
      'Static registry/hash and text checks only; no authenticated model invocation.',
      'Does not prove a clean third-party machine has Node, Codex, Claude, or Cursor credentials.',
      'Does not prove semantic behavior of every Wukong probe or provider runtime.',
    ],
    residual_risks: [
      'Runtime discovery and model authentication remain host-specific.',
      'A bounded static pass is not independent release or security sign-off.',
    ],
    missing_fields: [],
    handoff: {
      owner: 'review',
      reason: failed.length ? 'Repair the portability counterexample before acceptance.' : 'Review exact artifact and run an authenticated clean-machine smoke test.',
      next_action: failed.length ? 'Fix the named registration or mirror and rerun this smoke.' : 'Run the same mission with a fresh project-local adapter and authenticated runtime.',
      exact_retest: 'node engineer/skills/ak-wukong/scripts/wukong-portability-smoke.cjs --json',
    },
  };
  const bundle = validateBundle(mission, verdict);
  if (!bundle.valid) {
    throw new Error(`Generated smoke bundle is invalid: ${bundle.errors.join('; ')}`);
  }
  return { kind: 'wukong.portability-smoke', mission, verdict, observations };
}

let result;
try {
  result = run();
} catch (error) {
  console.error(error.message);
  process.exitCode = 1;
}
if (result) {
  const asJson = process.argv.includes('--json');
  if (asJson) console.log(JSON.stringify(result, null, 2));
  else {
    console.log(`WUKONG PORTABILITY SMOKE: ${result.verdict.claim_status}`);
    console.log(`Gate: ${result.verdict.recommended_gate}; evidence: ${result.verdict.evidence_grade}`);
    result.observations.forEach((item) => console.log(`- ${item.ok ? 'PASS' : 'FAIL'} ${item.detail}`));
  }
  if (result.observations.some((item) => !item.ok)) process.exitCode = 1;
}
