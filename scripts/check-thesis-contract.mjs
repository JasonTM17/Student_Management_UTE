import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const repoRoot = path.resolve(path.dirname(__filename), '..');

const contract = readJson('contracts/thesis-public-contract.json');
const controller = readText(
  'java-services/thesis-service/src/main/java/io/campuscore/thesis/web/ThesisController.java',
);
const thesisClient = readText('frontend/src/lib/thesis-api.ts');
const apiClient = readText('frontend/src/lib/api.ts');
const enabledRoutes = readText('nginx/thesis-pilot-routes.conf');
const disabledRoutes = readText('nginx/thesis-pilot-routes.disabled.conf');
const disabledUpstream = readText('nginx/thesis-pilot-upstream.disabled.conf');

const failures = [];
const controllerBasePath = controller.match(
  /@RequestMapping\(\s*"([^"]+)"\s*\)/u,
)?.[1];

if (controllerBasePath !== contract.basePath) {
  failures.push(
    `Java controller base path is ${controllerBasePath ?? '(missing)'}, expected ${contract.basePath}.`,
  );
}

const mappings = [...controller.matchAll(
  /@(Get|Post|Put|Patch|Delete)Mapping\(\s*"([^"]*)"\s*\)/gu,
)].map((match) => ({
  method: match[1].toUpperCase(),
  path: joinPath(controllerBasePath ?? '', match[2]),
}));

const expectedKeys = contract.endpoints.map((endpoint) =>
  endpointKey(endpoint.method, joinPath(contract.basePath, endpoint.path)),
);
const actualKeys = mappings.map((mapping) =>
  endpointKey(mapping.method, mapping.path),
);

for (const duplicate of duplicates(actualKeys)) {
  failures.push(`Java controller maps ${duplicate} more than once.`);
}

for (const expectedKey of expectedKeys) {
  if (!actualKeys.includes(expectedKey)) {
    failures.push(`Java controller is missing ${expectedKey}.`);
  }
}

for (const actualKey of actualKeys) {
  if (!expectedKeys.includes(actualKey)) {
    failures.push(`Java controller exposes undocumented ${actualKey}.`);
  }
}

for (const endpoint of contract.endpoints) {
  if (endpoint.clientSource && !thesisClient.includes(endpoint.clientSource)) {
    failures.push(
      `Frontend thesis client is missing the bound source fragment for ${endpoint.method} ${endpoint.path}: ${endpoint.clientSource}`,
    );
  }
}

if (!apiClient.includes("process.env.NEXT_PUBLIC_API_URL || '/api/v1'")) {
  failures.push('Frontend API client no longer defaults to the /api/v1 gateway base.');
}

for (const marker of [
  `location = ${contract.gateway.enabledRoute}`,
  `location ^~ ${contract.gateway.enabledPrefixRoute}`,
  'proxy_pass http://thesis_service_upstream;',
]) {
  if (!enabledRoutes.includes(marker)) {
    failures.push(`Pilot nginx routes are missing ${marker}.`);
  }
}

for (const [label, contents] of [
  ['disabled thesis upstream', disabledUpstream],
  ['disabled thesis routes', disabledRoutes],
]) {
  if (contents.includes(contract.gateway.disabledUpstreamMarker)) {
    failures.push(`${label} must not define ${contract.gateway.disabledUpstreamMarker}.`);
  }
  if (contents.includes(contract.gateway.disabledProxyMarker)) {
    failures.push(`${label} must not define ${contract.gateway.disabledProxyMarker}.`);
  }
}

if (failures.length > 0) {
  throw new Error(
    `[thesis-contract] FAIL\n${failures.map((failure) => `- ${failure}`).join('\n')}`,
  );
}

const clientBindings = contract.endpoints.filter((endpoint) => endpoint.clientSource).length;
console.log(
  `[thesis-contract] PASS: ${contract.endpoints.length} Java endpoints, ${clientBindings} FE bindings, and pilot/production gateway markers are aligned.`,
);
console.log(
  '[thesis-contract] Static source contract only; runtime response, auth, mutation, data, and rollback parity remain separate gates.',
);

function readText(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function readJson(relativePath) {
  return JSON.parse(readText(relativePath));
}

function joinPath(basePath, childPath) {
  const base = basePath.replace(/\/+$/u, '');
  const child = childPath.replace(/^\/+|\/+$/gu, '');
  return child ? `${base}/${child}` : base;
}

function endpointKey(method, endpointPath) {
  return `${method.toUpperCase()} ${endpointPath}`;
}

function duplicates(values) {
  const counts = new Map();
  for (const value of values) {
    counts.set(value, (counts.get(value) ?? 0) + 1);
  }
  return [...counts.entries()]
    .filter(([, count]) => count > 1)
    .map(([value]) => value);
}
