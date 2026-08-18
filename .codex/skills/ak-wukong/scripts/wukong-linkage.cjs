#!/usr/bin/env node
'use strict';

/**
 * Deterministic handoff contract for the Advisor -> Kongming -> Wukong ->
 * Fix -> Test -> Review chain.  It validates orchestration evidence only; it
 * never dispatches a model or performs a write.
 */

const fs = require('node:fs');
const crypto = require('node:crypto');
const path = require('node:path');

const PROTOCOL_VERSION = '1.0';
const STAGES = ['advisor', 'kongming', 'wukong', 'fix', 'test', 'review'];
const NEXT_STAGE = {
  advisor: 'kongming',
  kongming: 'wukong',
  wukong: 'fix',
  fix: 'test',
  test: 'review',
  review: 'controller',
};
const EXPECTED_AGENT = {
  advisor: 'advisor',
  kongming: 'kongming',
  wukong: 'wukong',
  fix: 'fullstack-developer',
  test: 'tester',
  review: 'code-reviewer',
};
const EXPECTED_SKILL = {
  advisor: 'ak:advise',
  kongming: 'ak:fable-thinking',
  wukong: 'ak:wukong',
  fix: 'ak:fix',
  test: 'ak:test',
  review: 'ak:code-review',
};
const EXPECTED_AUTHORITY = {
  advisor: 'advisory',
  kongming: 'advisory',
  wukong: 'R0/report-only',
  fix: 'R1/scoped-write',
  test: 'R0/verification',
  review: 'R0/independent-review',
};
const CLAIM_STATUS = new Set(['FALSIFIED', 'NOT_FALSIFIED', 'INCONCLUSIVE', 'UNDERDEFINED']);
const IDENTITY_KINDS = new Set(['git', 'file-sha256', 'artifact', 'runtime', 'custom']);
const SHA256 = /^[0-9a-f]{64}$/;
const ARTIFACT_ID = /^sha256:[0-9a-f]{64}$/;
const SECRET_KEY = /^(?:api[_-]?key|access[_-]?token|refresh[_-]?token|password|passwd|secret|private[_-]?key|credential)$/i;
const SECRET_VALUE = /-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|\b(?:ghp|github_pat|sk_live|sk_test|xox[baprs])[-_][-A-Za-z0-9_]{12,}\b|\bAKIA[0-9A-Z]{16}\b/;

function isObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function nonEmpty(value, max = 4096) {
  return typeof value === 'string' && value.trim().length > 0 && value.length <= max;
}

function portable(value) {
  if (!nonEmpty(value, 512)) return false;
  const normalized = value.replaceAll('\\', '/');
  if (normalized !== value) return false;
  if (normalized.startsWith('/') || normalized.startsWith('//') || /^[A-Za-z]:\//.test(normalized)) return false;
  if (normalized !== path.posix.normalize(normalized)) return false;
  const segments = normalized.split('/');
  const invalidWindowsSegment = segments.some((segment) => {
    if (segment !== segment.normalize('NFC')) return true;
    if (/[<>:"|?*\u0000-\u001f]/.test(segment) || /[. ]$/.test(segment)) return true;
    const base = segment.split('.')[0].toUpperCase();
    return /^(?:CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])$/.test(base);
  });
  return normalized !== '.'
    && !normalized.startsWith('./')
    && !normalized.endsWith('/')
    && !segments.includes('..')
    && !segments.includes('')
    && !invalidWindowsSegment;
}

function canonicalJson(value) {
  if (Array.isArray(value)) return `[${value.map(canonicalJson).join(',')}]`;
  if (isObject(value)) {
    return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${canonicalJson(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}

function sha256(value) {
  return crypto.createHash('sha256').update(value).digest('hex');
}

function fileSha256(filePath) {
  return sha256(fs.readFileSync(filePath));
}

function artifactId(artifact) {
  const payload = { ...artifact };
  delete payload.artifact_id;
  return `sha256:${sha256(canonicalJson(payload))}`;
}

function stampArtifact(artifact) {
  return { ...artifact, artifact_id: artifactId(artifact) };
}

function manifestIdentityValue(manifest) {
  const normalized = [...manifest]
    .map((item) => ({ path: item.path, sha256: item.sha256 }))
    .sort((left, right) => left.path.localeCompare(right.path));
  return sha256(canonicalJson(normalized));
}

function sensitive(value, currentPath = '$', findings = []) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => sensitive(item, `${currentPath}[${index}]`, findings));
  } else if (isObject(value)) {
    for (const [key, child] of Object.entries(value)) {
      if (SECRET_KEY.test(key) && child !== '' && child !== false && child !== null) {
        findings.push(`${currentPath}.${key} is secret-bearing`);
      }
      sensitive(child, `${currentPath}.${key}`, findings);
    }
  } else if (typeof value === 'string' && SECRET_VALUE.test(value)) {
    findings.push(`${currentPath} appears to contain secret material`);
  }
  return findings;
}

function validateIdentity(identity, field, errors) {
  if (!isObject(identity)) {
    errors.push(`${field} must be an object`);
    return;
  }
  if (!IDENTITY_KINDS.has(identity.kind)) errors.push(`${field}.kind is not supported`);
  if (!nonEmpty(identity.value, 512)) errors.push(`${field}.value must be non-empty`);
  if (identity.kind === 'file-sha256' && !SHA256.test(identity.value)) {
    errors.push(`${field}.value must be a lowercase SHA-256 digest`);
  }
  if (identity.kind === 'git' && !/^[0-9a-f]{7,64}(?:-dirty)?$/.test(identity.value)) {
    errors.push(`${field}.value must be a Git object identity`);
  }
  if (!nonEmpty(identity.captured_at, 64) || Number.isNaN(Date.parse(identity.captured_at)) || !identity.captured_at.endsWith('Z')) {
    errors.push(`${field}.captured_at must be an ISO-8601 UTC timestamp`);
  }
}

function sameIdentity(left, right) {
  return isObject(left) && isObject(right) && left.kind === right.kind && left.value === right.value;
}

function validateIdentityManifest(identity, manifest, field, errors, artifactRoot) {
  if (identity?.kind !== 'file-sha256') return;
  if (!Array.isArray(manifest) || manifest.length === 0) {
    errors.push(`${field} must be a non-empty manifest for file-sha256 identity`);
    return;
  }
  const paths = new Set();
  for (const [index, item] of manifest.entries()) {
    if (!isObject(item) || !portable(item.path) || !SHA256.test(item.sha256 || '')) {
      errors.push(`${field}[${index}] requires a portable path and lowercase SHA-256 digest`);
      continue;
    }
    const portableKey = typeof item?.path === 'string' ? item.path.normalize('NFC').toLowerCase() : item?.path;
    if (paths.has(portableKey)) errors.push(`${field} contains duplicate/case-aliased path: ${item.path}`);
    paths.add(portableKey);
  }
  if (errors.some((error) => error.startsWith(field))) return;
  if (manifestIdentityValue(manifest) !== identity.value) {
    errors.push(`${field} does not bind to the declared identity value`);
  }
  if (!artifactRoot) {
    errors.push(`${field} requires an artifact root for file verification`);
    return;
  }
  let realRoot;
  try {
    realRoot = fs.realpathSync(path.resolve(artifactRoot));
  } catch (error) {
    errors.push(`${field} artifact root is not readable: ${error.message}`);
    return;
  }
  for (const item of manifest) {
    const target = path.resolve(realRoot, item.path);
    try {
      const realTarget = fs.realpathSync(target);
      if (realTarget !== realRoot && !realTarget.startsWith(`${realRoot}${path.sep}`)) {
        errors.push(`${field} path escapes artifact root: ${item.path}`);
      } else if (!fs.statSync(realTarget).isFile()) {
        errors.push(`${field} path is not a file: ${item.path}`);
      } else if (fileSha256(realTarget) !== item.sha256) {
        errors.push(`${field} file hash mismatch: ${item.path}`);
      }
    } catch (error) {
      errors.push(`${field} file is not readable: ${item.path} (${error.code || error.message})`);
    }
  }
}

function manifestPaths(manifest) {
  return Array.isArray(manifest) ? manifest.map((item) => item.path) : [];
}

function validateRepairManifest(controller, fix, errors) {
  const inputPaths = manifestPaths(controller?.target_identity_manifest);
  const resultPaths = manifestPaths(fix?.result_identity_manifest);
  if (inputPaths.length === 0 || resultPaths.length === 0) return;
  if (inputPaths.length !== resultPaths.length) {
    errors.push('Fix result_identity_manifest must preserve the investigated manifest file count');
  }
  const inputPathSet = new Set(inputPaths);
  const resultPathSet = new Set(resultPaths);
  const removed = inputPaths.filter((item) => !resultPathSet.has(item));
  const added = resultPaths.filter((item) => !inputPathSet.has(item));
  if (removed.length !== 1 || added.length !== 1) {
    errors.push('Fix result_identity_manifest must replace exactly one investigated path');
  }
  const repairContract = controller?.repair_contract;
  if (isObject(repairContract)
    && (removed[0] !== repairContract.replace_path || added[0] !== repairContract.result_path)) {
    errors.push('Fix result_identity_manifest must implement the controller repair_contract exactly');
  }
  const unchangedInput = inputPaths.filter((item) => !removed.includes(item));
  const unchangedResult = resultPaths.filter((item) => !added.includes(item));
  if (JSON.stringify([...unchangedInput].sort()) !== JSON.stringify([...unchangedResult].sort())) {
    errors.push('Fix result_identity_manifest must retain every non-replaced investigated path');
  }
  const produced = new Set(fix?.produced_files || []);
  if (added.length === 1 && !produced.has(added[0])) {
    errors.push('Fix produced_files must include the replacement path in result_identity_manifest');
  }
  const inputByPath = new Map((controller?.target_identity_manifest || []).map((item) => [item.path, item.sha256]));
  const resultByPath = new Map((fix?.result_identity_manifest || []).map((item) => [item.path, item.sha256]));
  for (const retained of unchangedInput) {
    if (inputByPath.get(retained) !== resultByPath.get(retained)) {
      errors.push(`Fix may not drift retained manifest path: ${retained}`);
    }
  }
  if (removed.length === 1 && added.length === 1 && path.posix.dirname(removed[0]) !== path.posix.dirname(added[0])) {
    errors.push('Fix replacement path must remain in the same manifest directory');
  }
  if (removed.length === 1 && added.length === 1 && inputByPath.get(removed[0]) === resultByPath.get(added[0])) {
    errors.push('Fix replacement content hash must differ from the investigated input');
  }
}

function validateEvidence(evidence, field, errors) {
  if (!Array.isArray(evidence) || evidence.length === 0 || evidence.some((item) => !nonEmpty(item, 4096))) {
    errors.push(`${field} must be a non-empty array of evidence strings`);
  }
}

function validateArtifactIntegrity(artifact, field, errors) {
  if (!ARTIFACT_ID.test(artifact.artifact_id || '')) {
    errors.push(`${field}.artifact_id must be a sha256 artifact ID`);
  } else if (artifact.artifact_id !== artifactId(artifact)) {
    errors.push(`${field}.artifact_id does not match canonical artifact content`);
  }
}

function validateControllerInput(controller, errors, artifactRoot) {
  const field = 'controller_input';
  if (!isObject(controller)) {
    errors.push(`${field} must be an object`);
    return;
  }
  if (controller.protocol_version !== PROTOCOL_VERSION) errors.push(`${field}.protocol_version must be ${PROTOCOL_VERSION}`);
  if (!nonEmpty(controller.run_id, 128)) errors.push(`${field}.run_id must be non-empty`);
  if (controller.kind !== 'ak.controller-input') errors.push(`${field}.kind must be ak.controller-input`);
  if (controller.controller_skill !== 'ak:agentkit') errors.push(`${field}.controller_skill must be ak:agentkit`);
  if (controller.authority !== 'R0/orchestration') errors.push(`${field}.authority must be R0/orchestration`);
  if (!nonEmpty(controller.claim, 1000)) errors.push(`${field}.claim must be non-empty`);
  validateIdentity(controller.target_identity, `${field}.target_identity`, errors);
  validateIdentityManifest(controller.target_identity, controller.target_identity_manifest, `${field}.target_identity_manifest`, errors, artifactRoot);
  if (!isObject(controller.repair_contract)) {
    errors.push(`${field}.repair_contract must be an object`);
  } else {
    const { replace_path: replacePath, result_path: resultPath } = controller.repair_contract;
    if (!portable(replacePath) || !portable(resultPath)) {
      errors.push(`${field}.repair_contract paths must be canonical portable paths`);
    }
    if (replacePath === resultPath) errors.push(`${field}.repair_contract must change the target path`);
    const investigatedPaths = new Set(manifestPaths(controller.target_identity_manifest));
    if (!investigatedPaths.has(replacePath)) {
      errors.push(`${field}.repair_contract.replace_path must exist in the investigated manifest`);
    }
    if (investigatedPaths.has(resultPath)) {
      errors.push(`${field}.repair_contract.result_path must not already exist in the investigated manifest`);
    }
  }
  validateEvidence(controller.evidence, `${field}.evidence`, errors);
  if (controller.handoff_to !== 'advisor') errors.push(`${field}.handoff_to must be advisor`);
  validateArtifactIntegrity(controller, field, errors);
  errors.push(...sensitive(controller));
}

function validateControllerReceipt(receipt, errors) {
  const field = 'controller_receipt';
  if (!isObject(receipt)) {
    errors.push(`${field} must be an object`);
    return;
  }
  if (receipt.protocol_version !== PROTOCOL_VERSION) errors.push(`${field}.protocol_version must be ${PROTOCOL_VERSION}`);
  if (!nonEmpty(receipt.run_id, 128)) errors.push(`${field}.run_id must be non-empty`);
  if (receipt.kind !== 'ak.controller-receipt') errors.push(`${field}.kind must be ak.controller-receipt`);
  if (receipt.controller_skill !== 'ak:agentkit') errors.push(`${field}.controller_skill must be ak:agentkit`);
  if (receipt.authority !== 'R0/orchestration') errors.push(`${field}.authority must be R0/orchestration`);
  if (!nonEmpty(receipt.claim, 1000)) errors.push(`${field}.claim must be non-empty`);
  validateIdentity(receipt.target_identity, `${field}.target_identity`, errors);
  if (receipt.status !== 'ACCEPTED_WITH_RESIDUAL_RISK') {
    errors.push(`${field}.status must be ACCEPTED_WITH_RESIDUAL_RISK`);
  }
  validateEvidence(receipt.evidence, `${field}.evidence`, errors);
  if (!Array.isArray(receipt.produced_files) || receipt.produced_files.length === 0 || receipt.produced_files.some((item) => !portable(item))) {
    errors.push(`${field}.produced_files must be a non-empty array of portable relative paths`);
  }
  if (!ARTIFACT_ID.test(receipt.consumes_artifact_id || '')) {
    errors.push(`${field}.consumes_artifact_id must be a sha256 artifact ID`);
  }
  validateArtifactIntegrity(receipt, field, errors);
  errors.push(...sensitive(receipt));
}

function validateStageArtifact(artifact, index = 0) {
  const errors = [];
  const field = `chain[${index}]`;
  if (!isObject(artifact)) return { valid: false, errors: [`${field} must be an object`] };
  if (artifact.protocol_version !== PROTOCOL_VERSION) errors.push(`${field}.protocol_version must be ${PROTOCOL_VERSION}`);
  if (!nonEmpty(artifact.run_id, 128)) errors.push(`${field}.run_id must be non-empty`);
  if (!STAGES.includes(artifact.stage)) errors.push(`${field}.stage is unknown`);
  if (artifact.role !== artifact.stage) errors.push(`${field}.role must match its stage`);
  if (artifact.agent !== EXPECTED_AGENT[artifact.stage]) {
    errors.push(`${field}.agent must be ${EXPECTED_AGENT[artifact.stage]}`);
  }
  if (!Array.isArray(artifact.skills_used) || !artifact.skills_used.includes(EXPECTED_SKILL[artifact.stage])) {
    errors.push(`${field}.skills_used must include ${EXPECTED_SKILL[artifact.stage]}`);
  }
  if (artifact.controller_skill !== 'ak:agentkit') {
    errors.push(`${field}.controller_skill must be ak:agentkit`);
  }
  if (artifact.authority !== EXPECTED_AUTHORITY[artifact.stage]) {
    errors.push(`${field}.authority must be ${EXPECTED_AUTHORITY[artifact.stage]}`);
  }
  if (!nonEmpty(artifact.claim, 1000)) errors.push(`${field}.claim must be non-empty`);
  validateIdentity(artifact.target_identity, `${field}.target_identity`, errors);
  if (!nonEmpty(artifact.status, 128)) errors.push(`${field}.status must be non-empty`);
  if (!ARTIFACT_ID.test(artifact.consumes_artifact_id || '')) {
    errors.push(`${field}.consumes_artifact_id must be a sha256 artifact ID`);
  }
  if (artifact.stage && artifact.handoff_to !== NEXT_STAGE[artifact.stage]) {
    errors.push(`${field}.handoff_to must be ${NEXT_STAGE[artifact.stage]}`);
  }
  validateEvidence(artifact.evidence, `${field}.evidence`, errors);
  if (artifact.produced_files !== undefined) {
    if (!Array.isArray(artifact.produced_files) || artifact.produced_files.length === 0 || artifact.produced_files.some((item) => !portable(item))) {
      errors.push(`${field}.produced_files must be a non-empty array of portable relative paths`);
    }
  } else {
    errors.push(`${field}.produced_files must be present`);
  }
  validateArtifactIntegrity(artifact, field, errors);
  errors.push(...sensitive(artifact));
  return { valid: errors.length === 0, errors };
}

function validateChain(workflow, { artifactRoot } = {}) {
  const errors = [];
  if (!isObject(workflow)) {
    return { valid: false, errors: ['workflow must be an object containing controller_input and stages'], validated_handoffs: 0, validated_stages: 0 };
  }
  if (workflow.protocol_version !== PROTOCOL_VERSION) errors.push(`workflow.protocol_version must be ${PROTOCOL_VERSION}`);
  if (!nonEmpty(workflow.run_id, 128)) errors.push('workflow.run_id must be non-empty');
  validateControllerInput(workflow.controller_input, errors, artifactRoot);
  const chain = workflow.stages;
  if (!Array.isArray(chain) || chain.length !== STAGES.length) {
    return { valid: false, errors: [...errors, `workflow.stages must contain exactly ${STAGES.length} stage artifacts`], validated_handoffs: 0, validated_stages: 0 };
  }
  const results = chain.map((artifact, index) => validateStageArtifact(artifact, index));
  results.forEach((result) => errors.push(...result.errors));
  validateControllerReceipt(workflow.controller_receipt, errors);
  const stages = chain.map((artifact) => artifact && artifact.stage);
  if (JSON.stringify(stages) !== JSON.stringify(STAGES)) {
    errors.push(`stage order must be ${STAGES.join(' -> ')}`);
  }
  const runIds = new Set(chain.map((artifact) => artifact && artifact.run_id));
  if (runIds.size !== 1 || !runIds.has(workflow.run_id) || workflow.controller_input?.run_id !== workflow.run_id) {
    errors.push('controller and all stages must share workflow.run_id');
  }
  const claims = new Set(chain.map((artifact) => artifact && artifact.claim));
  if (claims.size !== 1
    || !claims.has(workflow.controller_input?.claim)
    || workflow.controller_receipt?.claim !== workflow.controller_input?.claim) {
    errors.push('controller and all stages must share one claim');
  }
  const [advisor, kongming, wukong, fix, test, review] = chain;
  const investigated = workflow.controller_input?.target_identity;
  const investigatedIdentities = [advisor, kongming, wukong, fix]
    .map((artifact) => artifact && artifact.target_identity);
  if (!investigatedIdentities.every((identity) => sameIdentity(identity, investigated))) {
    errors.push('Advisor, Kongming, Wukong, and Fix must share the investigated target_identity');
  }
  validateIdentity(fix?.result_identity, 'chain[3].result_identity', errors);
  validateIdentityManifest(fix?.result_identity, fix?.result_identity_manifest, 'chain[3].result_identity_manifest', errors, artifactRoot);
  validateRepairManifest(workflow.controller_input, fix, errors);
  if (fix?.result_identity?.kind !== fix?.target_identity?.kind) {
    errors.push('Fix result_identity must use the same identity kind as its investigated input');
  }
  if (sameIdentity(fix?.target_identity, fix?.result_identity)) {
    errors.push('Fix result_identity must identify the repaired result, not the investigated input');
  }
  if (!sameIdentity(test?.target_identity, fix?.result_identity)) {
    errors.push('Test target_identity must match Fix result_identity');
  }
  if (!sameIdentity(review?.target_identity, test?.target_identity)) {
    errors.push('Review target_identity must match the tested target_identity');
  }
  if (!sameIdentity(workflow.controller_receipt?.target_identity, review?.target_identity)) {
    errors.push('Controller receipt target_identity must match the reviewed target_identity');
  }
  const upstreamArtifacts = [workflow.controller_input, advisor, kongming, wukong, fix, test];
  chain.forEach((artifact, index) => {
    if (artifact?.consumes_artifact_id !== upstreamArtifacts[index]?.artifact_id) {
      errors.push(`chain[${index}].consumes_artifact_id must match the immediately preceding artifact`);
    }
  });
  if (workflow.controller_receipt?.consumes_artifact_id !== review?.artifact_id) {
    errors.push('controller_receipt.consumes_artifact_id must match the Review artifact');
  }
  if (workflow.controller_receipt?.run_id !== workflow.run_id) {
    errors.push('controller_receipt.run_id must match workflow.run_id');
  }
  if (advisor?.status !== 'DONE' || kongming?.status !== 'DONE') {
    errors.push('Advisor and Kongming status must be DONE');
  }
  if (!CLAIM_STATUS.has(wukong?.claim_status)) errors.push('Wukong must emit a machine claim_status');
  if (wukong?.claim_status !== 'FALSIFIED' || wukong?.status !== wukong?.claim_status) {
    errors.push('This repair linkage requires Wukong status and claim_status FALSIFIED');
  }
  if (wukong?.claim_status === 'FALSIFIED' && wukong?.recommended_gate !== 'REPAIR_THEN_RETEST') {
    errors.push('FALSIFIED Wukong finding must hand off through REPAIR_THEN_RETEST');
  }
  if (fix?.input_status !== wukong?.claim_status) errors.push('Fix input_status must match Wukong claim_status');
  if (fix?.status !== 'DONE') errors.push('Fix status must be DONE');
  if (test?.input_stage !== 'fix' || test?.status !== 'PASS') errors.push('Test must verify the Fix handoff and pass');
  if (review?.input_stage !== 'test' || !['PASS', 'PASS_WITH_RESIDUAL_RISK'].includes(review?.status)) {
    errors.push('Review must consume a passing Test artifact');
  }
  if (chain.some((artifact) => artifact?.handoff_to === artifact?.stage)) errors.push('self-handoff is forbidden');
  return {
    valid: errors.length === 0,
    errors,
    validated_handoffs: errors.length === 0 ? STAGES.length + 1 : 0,
    validated_stages: errors.length === 0 ? STAGES.length : 0,
  };
}

function readJson(filePath) {
  return JSON.parse(fs.readFileSync(filePath, 'utf8'));
}

function main() {
  const args = process.argv.slice(2);
  if (args[0] !== 'validate-chain' || !args[1]) {
    console.error('Usage: node wukong-linkage.cjs validate-chain <workflow.json> [--artifact-root <dir>] [--json]');
    process.exitCode = 2;
    return;
  }
  try {
    const artifactRootIndex = args.indexOf('--artifact-root');
    const artifactRoot = artifactRootIndex >= 0 ? args[artifactRootIndex + 1] : process.cwd();
    if (!artifactRoot) throw new Error('--artifact-root requires a directory');
    const result = validateChain(readJson(args[1]), { artifactRoot });
    if (args.includes('--json')) console.log(JSON.stringify(result, null, 2));
    else console.log(result.valid ? 'Wukong linkage valid' : `Wukong linkage invalid: ${result.errors.join('; ')}`);
    process.exitCode = result.valid ? 0 : 1;
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

module.exports = {
  EXPECTED_AUTHORITY,
  EXPECTED_AGENT,
  EXPECTED_SKILL,
  NEXT_STAGE,
  STAGES,
  artifactId,
  fileSha256,
  manifestIdentityValue,
  stampArtifact,
  validateChain,
  validateStageArtifact,
};
if (require.main === module) main();
