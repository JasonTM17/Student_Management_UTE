#!/usr/bin/env node
'use strict';

const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const test = require('node:test');
const {
  fileSha256,
  manifestIdentityValue,
  stampArtifact,
  validateChain,
} = require('./wukong-linkage.cjs');

const capturedAt = '2026-08-12T00:00:00Z';
const artifactRoot = __dirname;
const inputManifest = [{ path: 'wukong-linkage.cjs', sha256: fileSha256(path.join(artifactRoot, 'wukong-linkage.cjs')) }];
const resultManifest = [{ path: 'wukong-linkage.test.cjs', sha256: fileSha256(path.join(artifactRoot, 'wukong-linkage.test.cjs')) }];
const inputIdentity = {
  kind: 'file-sha256',
  value: manifestIdentityValue(inputManifest),
  captured_at: capturedAt,
};
const resultIdentity = {
  kind: 'file-sha256',
  value: manifestIdentityValue(resultManifest),
  captured_at: '2026-08-12T00:01:00Z',
};
const stageNames = ['advisor', 'kongming', 'wukong', 'fix', 'test', 'review'];

function controller() {
  return stampArtifact({
    protocol_version: '1.0',
    run_id: 'wukong-linkage-simulation-001',
    kind: 'ak.controller-input',
    controller_skill: 'ak:agentkit',
    authority: 'R0/orchestration',
    claim: 'The adapter remains portable after moving to another Windows drive.',
    target_identity: { ...inputIdentity },
    target_identity_manifest: inputManifest.map((item) => ({ ...item })),
    repair_contract: {
      replace_path: inputManifest[0].path,
      result_path: resultManifest[0].path,
    },
    evidence: ['Controller captured scope and acceptance boundaries.'],
    handoff_to: 'advisor',
  });
}

function artifact(stage, consumesArtifactId, overrides = {}) {
  const next = { advisor: 'kongming', kongming: 'wukong', wukong: 'fix', fix: 'test', test: 'review', review: 'controller' }[stage];
  const target = stage === 'test' || stage === 'review' ? resultIdentity : inputIdentity;
  return stampArtifact({
    protocol_version: '1.0',
    run_id: 'wukong-linkage-simulation-001',
    stage,
    role: stage,
    agent: { advisor: 'advisor', kongming: 'kongming', wukong: 'wukong', fix: 'fullstack-developer', test: 'tester', review: 'code-reviewer' }[stage],
    skills_used: [{ advisor: 'ak:advise', kongming: 'ak:fable-thinking', wukong: 'ak:wukong', fix: 'ak:fix', test: 'ak:test', review: 'ak:code-review' }[stage]],
    controller_skill: 'ak:agentkit',
    authority: { advisor: 'advisory', kongming: 'advisory', wukong: 'R0/report-only', fix: 'R1/scoped-write', test: 'R0/verification', review: 'R0/independent-review' }[stage],
    claim: 'The adapter remains portable after moving to another Windows drive.',
    target_identity: { ...target },
    status: stage === 'wukong' ? 'FALSIFIED' : stage === 'test' ? 'PASS' : stage === 'review' ? 'PASS_WITH_RESIDUAL_RISK' : 'DONE',
    evidence: [`${stage} evidence`],
    produced_files: stage === 'fix'
      ? [resultManifest[0].path, 'plans/reports/fix.json']
      : [`plans/reports/${stage}.json`],
    consumes_artifact_id: consumesArtifactId,
    handoff_to: next,
    ...(stage === 'wukong' ? { claim_status: 'FALSIFIED', recommended_gate: 'REPAIR_THEN_RETEST' } : {}),
    ...(stage === 'fix' ? {
      input_status: 'FALSIFIED',
      result_identity: { ...resultIdentity },
      result_identity_manifest: resultManifest.map((item) => ({ ...item })),
    } : {}),
    ...(stage === 'test' ? { input_stage: 'fix' } : {}),
    ...(stage === 'review' ? { input_stage: 'test' } : {}),
    ...overrides,
  });
}

function validWorkflow() {
  const controllerInput = controller();
  const stages = [];
  let previous = controllerInput.artifact_id;
  for (const stage of stageNames) {
    const value = artifact(stage, previous);
    stages.push(value);
    previous = value.artifact_id;
  }
  const controllerReceipt = stampArtifact({
    protocol_version: '1.0',
    run_id: 'wukong-linkage-simulation-001',
    kind: 'ak.controller-receipt',
    controller_skill: 'ak:agentkit',
    authority: 'R0/orchestration',
    claim: 'The adapter remains portable after moving to another Windows drive.',
    target_identity: { ...resultIdentity },
    status: 'ACCEPTED_WITH_RESIDUAL_RISK',
    evidence: ['Controller received the exact independently reviewed artifact.'],
    produced_files: ['plans/reports/summary.json'],
    consumes_artifact_id: stages.at(-1).artifact_id,
  });
  return {
    protocol_version: '1.0',
    run_id: 'wukong-linkage-simulation-001',
    controller_input: controllerInput,
    stages,
    controller_receipt: controllerReceipt,
  };
}

function restampFrom(workflow, startIndex) {
  for (let index = startIndex; index < workflow.stages.length; index += 1) {
    const upstream = index === 0 ? workflow.controller_input : workflow.stages[index - 1];
    workflow.stages[index].consumes_artifact_id = upstream.artifact_id;
    workflow.stages[index] = stampArtifact(workflow.stages[index]);
  }
  workflow.controller_receipt.target_identity = { ...workflow.stages.at(-1).target_identity };
  workflow.controller_receipt.consumes_artifact_id = workflow.stages.at(-1).artifact_id;
  workflow.controller_receipt = stampArtifact(workflow.controller_receipt);
}

function validate(workflow) {
  return validateChain(workflow, { artifactRoot });
}

test('accepts the controller-bound supervised six-stage workflow', () => {
  const result = validate(validWorkflow());
  assert.equal(result.valid, true);
  assert.equal(result.validated_handoffs, 7);
  assert.equal(result.validated_stages, 6);
});

test('requires the controller to consume the exact Review artifact', () => {
  const workflow = validWorkflow();
  workflow.controller_receipt.consumes_artifact_id = workflow.stages[4].artifact_id;
  workflow.controller_receipt = stampArtifact(workflow.controller_receipt);
  assert.match(validate(workflow).errors.join('\n'), /must match the Review artifact/);
});

test('rejects a bare stage array without a controller artifact', () => {
  const result = validate(validWorkflow().stages);
  assert.equal(result.valid, false);
  assert.match(result.errors.join('\n'), /workflow must be an object/);
});

test('rejects stage reordering and broken predecessor consumption', () => {
  const workflow = validWorkflow();
  [workflow.stages[2], workflow.stages[3]] = [workflow.stages[3], workflow.stages[2]];
  const errors = validate(workflow).errors.join('\n');
  assert.match(errors, /stage order/);
  assert.match(errors, /immediately preceding artifact/);
});

test('requires exact stage authority', () => {
  const workflow = validWorkflow();
  workflow.stages[3].authority = 'advisory';
  workflow.stages[4].authority = 'R1/scoped-write';
  workflow.stages[5].authority = 'advisory';
  const errors = validate(workflow).errors.join('\n');
  assert.match(errors, /authority must be R1\/scoped-write/);
  assert.match(errors, /authority must be R0\/verification/);
  assert.match(errors, /authority must be R0\/independent-review/);
});

test('requires exact stage status and non-null evidence', () => {
  const workflow = validWorkflow();
  workflow.stages[3].status = 'BROKEN';
  workflow.stages[3].evidence = [null];
  const errors = validate(workflow).errors.join('\n');
  assert.match(errors, /Fix status must be DONE/);
  assert.match(errors, /evidence strings/);
});

test('requires the stage-specific AgentKit agent and skill owner', () => {
  const workflow = validWorkflow();
  workflow.stages[2].agent = 'debugger';
  workflow.stages[4].skills_used = ['ak:fix'];
  const errors = validate(workflow).errors.join('\n');
  assert.match(errors, /agent must be wukong/);
  assert.match(errors, /skills_used must include ak:test/);
});

test('requires Wukong FALSIFIED and its repair gate', () => {
  const workflow = validWorkflow();
  workflow.stages[2].recommended_gate = 'PROCEED_WITH_RESIDUAL_RISK';
  assert.match(validate(workflow).errors.join('\n'), /REPAIR_THEN_RETEST/);
});

test('requires Fix to consume Wukong status', () => {
  const workflow = validWorkflow();
  workflow.stages[3].input_status = 'NOT_FALSIFIED';
  assert.match(validate(workflow).errors.join('\n'), /input_status/);
});

test('does not treat a timestamp-only change as a repaired identity', () => {
  const workflow = validWorkflow();
  workflow.stages[3].result_identity = { ...inputIdentity, captured_at: '2026-08-12T00:02:00Z' };
  workflow.stages[3].result_identity_manifest = inputManifest.map((item) => ({ ...item }));
  workflow.stages[4].target_identity = { ...workflow.stages[3].result_identity };
  workflow.stages[5].target_identity = { ...workflow.stages[3].result_identity };
  restampFrom(workflow, 3);
  assert.match(validate(workflow).errors.join('\n'), /not the investigated input/);
});

test('rejects a fabricated manifest even when its self-declared identity matches', () => {
  const workflow = validWorkflow();
  workflow.stages[3].result_identity_manifest[0].sha256 = 'c'.repeat(64);
  const fabricated = manifestIdentityValue(workflow.stages[3].result_identity_manifest);
  workflow.stages[3].result_identity.value = fabricated;
  workflow.stages[4].target_identity.value = fabricated;
  workflow.stages[5].target_identity.value = fabricated;
  restampFrom(workflow, 3);
  assert.match(validate(workflow).errors.join('\n'), /file hash mismatch/);
});

test('rejects a dot-path alias masquerading as a repaired identity', () => {
  const workflow = validWorkflow();
  workflow.stages[3].result_identity_manifest[0].path = `./${resultManifest[0].path}`;
  const aliased = manifestIdentityValue(workflow.stages[3].result_identity_manifest);
  workflow.stages[3].result_identity.value = aliased;
  workflow.stages[4].target_identity.value = aliased;
  workflow.stages[5].target_identity.value = aliased;
  workflow.stages[3].produced_files[0] = `./${resultManifest[0].path}`;
  restampFrom(workflow, 3);
  assert.match(validate(workflow).errors.join('\n'), /canonical portable|implement the controller repair_contract/);
});

test('rejects an unrelated real file as the repaired manifest', () => {
  const workflow = validWorkflow();
  const unrelatedPath = 'wukong-contract.cjs';
  workflow.stages[3].result_identity_manifest = [{
    path: unrelatedPath,
    sha256: fileSha256(path.join(artifactRoot, unrelatedPath)),
  }];
  const unrelatedIdentity = manifestIdentityValue(workflow.stages[3].result_identity_manifest);
  workflow.stages[3].result_identity.value = unrelatedIdentity;
  workflow.stages[4].target_identity.value = unrelatedIdentity;
  workflow.stages[5].target_identity.value = unrelatedIdentity;
  workflow.stages[3].produced_files[0] = unrelatedPath;
  restampFrom(workflow, 3);
  assert.match(validate(workflow).errors.join('\n'), /implement the controller repair_contract exactly/);
});

test('requires Test and Review to consume the repaired identity', () => {
  const workflow = validWorkflow();
  workflow.stages[4].target_identity = { ...inputIdentity };
  workflow.stages[5].target_identity = { ...inputIdentity };
  restampFrom(workflow, 4);
  assert.match(validate(workflow).errors.join('\n'), /Test target_identity must match Fix result_identity/);
});

test('detects predecessor artifact drift even when the changed artifact is restamped', () => {
  const workflow = validWorkflow();
  workflow.stages[0].evidence.push('Additional advisor evidence.');
  workflow.stages[0] = stampArtifact(workflow.stages[0]);
  assert.match(validate(workflow).errors.join('\n'), /immediately preceding artifact/);
});

test('rejects stale or fabricated artifact IDs', () => {
  const workflow = validWorkflow();
  workflow.stages[2].evidence.push('Content changed after artifact ID was issued.');
  assert.match(validate(workflow).errors.join('\n'), /artifact_id does not match canonical artifact content/);
});

test('requires Test pass before Review', () => {
  const workflow = validWorkflow();
  workflow.stages[4].status = 'FAIL';
  assert.match(validate(workflow).errors.join('\n'), /Test must verify/);
});

test('rejects absolute artifact paths and secret-bearing fields', () => {
  const workflow = validWorkflow();
  workflow.stages[2].produced_files = ['C:\\Users\\Admin\\secret.json'];
  workflow.stages[2].api_key = 'should-not-be-here';
  const errors = validate(workflow).errors.join('\n');
  assert.match(errors, /portable relative|secret-bearing/);
});

test('file-sha256 hashes raw bytes without lossy UTF-8 canonicalization', () => {
  const temporary = fs.mkdtempSync(path.join(os.tmpdir(), 'wukong-raw-hash-'));
  try {
    const left = path.join(temporary, 'left.bin');
    const right = path.join(temporary, 'right.bin');
    fs.writeFileSync(left, Buffer.from([0x80]));
    fs.writeFileSync(right, Buffer.from([0x81]));
    assert.notEqual(fileSha256(left), fileSha256(right));
  } finally {
    fs.rmSync(temporary, { recursive: true, force: true });
  }
});

test('rejects Windows ADS, reserved names, and case-aliased manifest paths', () => {
  for (const invalidPath of ['dir/file.txt:secret', 'dir/CON', 'dir/trailing.']) {
    const workflow = validWorkflow();
    workflow.controller_input.target_identity_manifest[0].path = invalidPath;
    workflow.controller_input.target_identity.value = manifestIdentityValue(workflow.controller_input.target_identity_manifest);
    restampFrom(workflow, 0);
    assert.match(validate(workflow).errors.join('\n'), /portable path/);
  }
  const workflow = validWorkflow();
  workflow.controller_input.target_identity_manifest.push({
    path: workflow.controller_input.target_identity_manifest[0].path.toUpperCase(),
    sha256: workflow.controller_input.target_identity_manifest[0].sha256,
  });
  workflow.controller_input.target_identity.value = manifestIdentityValue(workflow.controller_input.target_identity_manifest);
  restampFrom(workflow, 0);
  assert.match(validate(workflow).errors.join('\n'), /duplicate\/case-aliased path/);
});
