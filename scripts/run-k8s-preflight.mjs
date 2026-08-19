import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');

const baseDir = path.join(repoRoot, 'k8s', 'base');
const bootstrapDir = path.join(repoRoot, 'k8s', 'bootstrap');
const dockerDesktopOverlayDir = path.join(
  repoRoot,
  'k8s',
  'overlays',
  'docker-desktop',
);
const thesisPilotOverlayDir = path.join(
  repoRoot,
  'k8s',
  'overlays',
  'thesis-pilot',
);
const stagingGenericOverlayDir = path.join(
  repoRoot,
  'k8s',
  'overlays',
  'staging-generic',
);
const prodGenericOverlayDir = path.join(
  repoRoot,
  'k8s',
  'overlays',
  'prod-generic',
);
const stagingOperatorOverlayDir = path.join(
  repoRoot,
  'k8s',
  'overlays',
  'staging-operator',
);
const prodOperatorOverlayDir = path.join(
  repoRoot,
  'k8s',
  'overlays',
  'prod-operator',
);
const privateTemplateRootDir = path.join(
  repoRoot,
  'k8s',
  'templates',
  'private-operator',
);
const stagingPrivateTemplateDir = path.join(
  privateTemplateRootDir,
  'staging',
);
const stagingPrivateBootstrapTemplateDir = path.join(
  stagingPrivateTemplateDir,
  'bootstrap',
);
const prodPrivateTemplateDir = path.join(
  privateTemplateRootDir,
  'prod',
);
const prodPrivateBootstrapTemplateDir = path.join(
  prodPrivateTemplateDir,
  'bootstrap',
);

const runtimeImages = [
  'campuscore-backend',
  'campuscore-auth-service',
  'campuscore-notification-service',
  'campuscore-finance-service',
  'campuscore-academic-service',
  'campuscore-engagement-service',
  'campuscore-people-service',
  'campuscore-analytics-service',
  'campuscore-frontend',
];

const bootstrapImages = runtimeImages.filter(
  (imageName) => imageName !== 'campuscore-frontend',
);

const localOverlayExpectations = [
  'COOKIE_SECURE: "false"',
  'SWAGGER_ENABLED: "true"',
  'FRONTEND_URL: http://127.0.0.1:8080',
  'campuscore-local-readiness-key-12345',
  'campuscore-local-internal-service-token-12345',
];

const genericOverlayExpectations = [
  {
    label: 'k8s/overlays/staging-generic',
    dir: stagingGenericOverlayDir,
    host: 'staging.campuscore.example.com',
    tlsSecret: 'campuscore-staging-tls',
    frontendUrl: 'https://staging.campuscore.example.com',
    placeholderPrefix: 'replace-me-with-staging-',
  },
  {
    label: 'k8s/overlays/prod-generic',
    dir: prodGenericOverlayDir,
    host: 'campuscore.example.com',
    tlsSecret: 'campuscore-prod-tls',
    frontendUrl: 'https://campuscore.example.com',
    placeholderPrefix: 'replace-me-with-prod-',
  },
];

const operatorOverlayExpectations = [
  {
    label: 'k8s/overlays/staging-operator',
    dir: stagingOperatorOverlayDir,
    host: 'staging.campuscore.example.com',
    tlsSecret: 'campuscore-staging-tls',
    frontendUrl: 'https://staging.campuscore.example.com',
    placeholderPrefix: 'replace-me-with-staging-',
    clusterIssuer: 'replace-with-staging-cluster-issuer',
    secretStoreName: 'replace-with-staging-cluster-secret-store',
    remoteSecretKey: 'replace-with-staging/campuscore/runtime',
  },
  {
    label: 'k8s/overlays/prod-operator',
    dir: prodOperatorOverlayDir,
    host: 'campuscore.example.com',
    tlsSecret: 'campuscore-prod-tls',
    frontendUrl: 'https://campuscore.example.com',
    placeholderPrefix: 'replace-me-with-prod-',
    clusterIssuer: 'replace-with-prod-cluster-issuer',
    secretStoreName: 'replace-with-prod-cluster-secret-store',
    remoteSecretKey: 'replace-with-prod/campuscore/runtime',
  },
];

const privateTemplateExpectations = [
  {
    label: 'k8s/templates/private-operator/staging',
    dir: stagingPrivateTemplateDir,
    bootstrapDir: stagingPrivateBootstrapTemplateDir,
    namespace: 'campuscore-staging',
    host: 'replace-with-real-staging-host.example.com',
    tlsSecret: 'replace-with-real-staging-tls-secret',
    frontendUrl: 'https://replace-with-real-staging-host.example.com',
    ingressClass: 'replace-with-staging-ingress-class',
    privateAnnotationKey:
      'replace-with-private-ingress-annotation-key.example.com/name',
    privateAnnotationValue: 'replace-with-private-ingress-annotation-value',
    secretStoreName: 'replace-with-real-staging-cluster-secret-store',
    clusterIssuer: 'replace-with-real-staging-cluster-issuer',
    remoteSecretKey: 'replace-with-real-staging/campuscore/runtime',
    publicHost: 'staging.campuscore.example.com',
    publicTlsSecret: 'campuscore-staging-tls',
    publicSecretStoreName: 'replace-with-staging-cluster-secret-store',
    publicClusterIssuer: 'replace-with-staging-cluster-issuer',
    publicRemoteSecretKey: 'replace-with-staging/campuscore/runtime',
  },
  {
    label: 'k8s/templates/private-operator/prod',
    dir: prodPrivateTemplateDir,
    bootstrapDir: prodPrivateBootstrapTemplateDir,
    namespace: 'campuscore-prod',
    host: 'replace-with-real-prod-host.example.com',
    tlsSecret: 'replace-with-real-prod-tls-secret',
    frontendUrl: 'https://replace-with-real-prod-host.example.com',
    ingressClass: 'replace-with-prod-ingress-class',
    privateAnnotationKey:
      'replace-with-private-ingress-annotation-key.example.com/name',
    privateAnnotationValue: 'replace-with-private-ingress-annotation-value',
    secretStoreName: 'replace-with-real-prod-cluster-secret-store',
    clusterIssuer: 'replace-with-real-prod-cluster-issuer',
    remoteSecretKey: 'replace-with-real-prod/campuscore/runtime',
    publicHost: 'campuscore.example.com',
    publicTlsSecret: 'campuscore-prod-tls',
    publicSecretStoreName: 'replace-with-prod-cluster-secret-store',
    publicClusterIssuer: 'replace-with-prod-cluster-issuer',
    publicRemoteSecretKey: 'replace-with-prod/campuscore/runtime',
  },
];

const bootstrapJobNames = [
  'core-api-init',
  'auth-service-init',
  'notification-service-init',
  'finance-service-init',
  'academic-service-init',
  'engagement-service-init',
  'people-service-init',
  'analytics-service-init',
];

const forbiddenPublicIngressMarkers = [
  'nginx.ingress.kubernetes.io/',
  'traefik.ingress.kubernetes.io/',
  'alb.ingress.kubernetes.io/',
  'haproxy.org/',
  'external-dns.alpha.kubernetes.io/',
  'kubernetes.io/ingress.class',
  'cert-manager.io/cluster-issuer',
];

async function main() {
  const baseRender = await kubectlKustomize(baseDir);
  const bootstrapRender = await kubectlKustomize(bootstrapDir);
  const dockerDesktopRender = await kubectlKustomize(dockerDesktopOverlayDir);
  const thesisPilotRender = await kubectlKustomize(thesisPilotOverlayDir);
  const genericRenders = await Promise.all(
    genericOverlayExpectations.map(async (overlay) => ({
      ...overlay,
      renderedYaml: await kubectlKustomize(overlay.dir),
    })),
  );
  const operatorRenders = await Promise.all(
    operatorOverlayExpectations.map(async (overlay) => ({
      ...overlay,
      renderedYaml: await kubectlKustomize(overlay.dir),
    })),
  );
  const privateTemplateRenders = await Promise.all(
    privateTemplateExpectations.map(async (template) => ({
      ...template,
      renderedYaml: await kubectlKustomize(template.dir),
      bootstrapRenderedYaml: await kubectlKustomize(template.bootstrapDir),
    })),
  );

  assertCanonicalBaseHasNoThesisRoute(baseRender);

  const baseTag = getResolvedTag(baseRender, runtimeImages);
  const bootstrapTag = getResolvedTag(bootstrapRender, bootstrapImages);
  const overlayTag = getResolvedTag(dockerDesktopRender, runtimeImages);
  const thesisPilotTag = getResolvedTag(thesisPilotRender, runtimeImages);
  const genericTags = genericRenders.map((overlay) =>
    getResolvedTag(overlay.renderedYaml, runtimeImages),
  );
  const operatorTags = operatorRenders.map((overlay) =>
    getResolvedTag(overlay.renderedYaml, runtimeImages),
  );
  const privateTemplateTags = privateTemplateRenders.map((template) =>
    getResolvedTag(template.renderedYaml, runtimeImages),
  );
  const privateTemplateBootstrapTags = privateTemplateRenders.map((template) =>
    getResolvedTag(template.bootstrapRenderedYaml, bootstrapImages),
  );

  if (
    baseTag !== bootstrapTag ||
    baseTag !== overlayTag ||
    baseTag !== thesisPilotTag ||
    genericTags.some((tag) => tag !== baseTag) ||
    operatorTags.some((tag) => tag !== baseTag) ||
    privateTemplateTags.some((tag) => tag !== baseTag) ||
    privateTemplateBootstrapTags.some((tag) => tag !== baseTag)
  ) {
    throw new Error(
      `Kustomize image tags drifted across base/bootstrap/overlays: base=${baseTag}, bootstrap=${bootstrapTag}, docker-desktop=${overlayTag}, generic=${genericTags.join(
        ', ',
      )}, operator=${operatorTags.join(', ')}, private-runtime=${privateTemplateTags.join(
        ', ',
      )}, private-bootstrap=${privateTemplateBootstrapTags.join(', ')}`,
    );
  }

  assertGhcrImages(baseRender, runtimeImages, baseTag, 'k8s/base');
  assertGhcrImages(bootstrapRender, bootstrapImages, baseTag, 'k8s/bootstrap');
  assertGhcrImages(
    dockerDesktopRender,
    runtimeImages,
    baseTag,
    'k8s/overlays/docker-desktop',
  );
  assertGhcrImages(
    thesisPilotRender,
    runtimeImages,
    baseTag,
    'k8s/overlays/thesis-pilot',
  );
  assertThesisPilotOverlay(thesisPilotRender);
  for (const overlay of genericRenders) {
    assertGhcrImages(
      overlay.renderedYaml,
      runtimeImages,
      baseTag,
      overlay.label,
    );
    assertGenericOverlay(overlay);
  }
  for (const overlay of operatorRenders) {
    assertGhcrImages(
      overlay.renderedYaml,
      runtimeImages,
      baseTag,
      overlay.label,
    );
    assertOperatorOverlay(overlay);
  }
  for (const template of privateTemplateRenders) {
    assertGhcrImages(
      template.renderedYaml,
      runtimeImages,
      baseTag,
      template.label,
    );
    assertGhcrImages(
      template.bootstrapRenderedYaml,
      bootstrapImages,
      baseTag,
      `${template.label}/bootstrap`,
    );
    assertPrivateTemplateOverlay(template);
    assertPrivateTemplateBootstrap(template);
  }

  if (dockerDesktopRender.includes('replace-me-before-apply')) {
    throw new Error(
      'Docker Desktop overlay still renders placeholder secrets from k8s/base/secrets.example.yaml.',
    );
  }

  for (const expected of localOverlayExpectations) {
    if (!dockerDesktopRender.includes(expected)) {
      throw new Error(
        `Docker Desktop overlay is missing the expected local override: ${expected}`,
      );
    }
  }

  const nginxServiceDoc = extractDocument(
    dockerDesktopRender,
    'Service',
    'campuscore-nginx',
  );
  if (!/type:\s*ClusterIP/u.test(nginxServiceDoc)) {
    throw new Error(
      'Docker Desktop overlay must patch campuscore-nginx to ClusterIP for port-forward based local access.',
    );
  }

  console.log('[k8s-preflight] Runtime base renders clean.');
  console.log('[k8s-preflight] Bootstrap renders clean.');
  console.log(
    `[k8s-preflight] Docker Desktop overlay renders clean with release tag ${baseTag}.`,
  );
  console.log(
    `[k8s-preflight] Thesis pilot overlay renders clean with canonical release tag ${baseTag}; Java image remains local-only and is not a release image.`,
  );
  for (const overlay of genericRenders) {
    console.log(
      `[k8s-preflight] ${overlay.label} renders clean with release tag ${baseTag}.`,
    );
  }
  for (const overlay of operatorRenders) {
    console.log(
      `[k8s-preflight] ${overlay.label} renders clean with release tag ${baseTag}.`,
    );
  }
  for (const template of privateTemplateRenders) {
    console.log(
      `[k8s-preflight] ${template.label} and ${template.label}/bootstrap render clean with release tag ${baseTag}.`,
    );
  }
  console.log(
    '[k8s-preflight] Local overlay sets Swagger on, secure cookies off, and localhost frontend URL for browser-safe local validation.',
  );
  console.log(
    '[k8s-preflight] Generic overlays stay cloud-agnostic: ingress host/TLS placeholders are present, nginx stays behind ClusterIP, and ingressClassName is intentionally unset.',
  );
  console.log(
    '[k8s-preflight] Operator overlays replace static placeholder secrets with ExternalSecret and Certificate resources for cert-manager/external-secrets handoff.',
  );
  console.log(
    '[k8s-preflight] Private operator templates provide tracked copy-out examples for runtime, ingress/TLS, secret-store bindings, and environment-specific bootstrap namespaces.',
  );
}

function assertGhcrImages(renderedYaml, imageNames, tag, label) {
  const namespaceMatch = renderedYaml.match(
    /ghcr\.io\/([^/\s]+)\/campuscore-backend:/u,
  );
  if (!namespaceMatch) {
    throw new Error(`${label} does not resolve published GHCR image names.`);
  }

  const ghcrNamespace = namespaceMatch[1];

  for (const imageName of imageNames) {
    const expectedImage = `ghcr.io/${ghcrNamespace}/${imageName}:${tag}`;
    if (!renderedYaml.includes(expectedImage)) {
      throw new Error(`${label} is missing resolved image ${expectedImage}`);
    }
  }
}

function getResolvedTag(renderedYaml, imageNames) {
  const tags = new Set();

  for (const imageName of imageNames) {
    const pattern = new RegExp(
      `ghcr\\.io\\/[^/\\s]+\\/${escapeForRegExp(imageName)}:([^\\s"']+)`,
      'gu',
    );

    for (const match of renderedYaml.matchAll(pattern)) {
      tags.add(match[1]);
    }
  }

  if (tags.size !== 1) {
    throw new Error(
      `Expected exactly one release tag in rendered manifests, found: ${[
        ...tags,
      ].join(', ') || '(none)'}`,
    );
  }

  return [...tags][0];
}

function extractDocument(renderedYaml, kind, name) {
  const documents = renderedYaml
    .split(/^---\s*$/gmu)
    .map((document) => document.trim())
    .filter(Boolean);

  const matched = documents.find((document) => {
    return (
      new RegExp(`^kind:\\s*${escapeForRegExp(kind)}\\s*$`, 'mu').test(document) &&
      new RegExp(
        `^metadata:\\s*[\\s\\S]*?^\\s*name:\\s*${escapeForRegExp(name)}\\s*$`,
        'mu',
      ).test(document)
    );
  });

  if (!matched) {
    throw new Error(`Could not find ${kind}/${name} in rendered manifests.`);
  }

  return matched;
}

function assertGenericOverlay(overlay) {
  const { label, placeholderPrefix, renderedYaml } = overlay;

  assertSharedClusterIngressContract(overlay);

  if (!renderedYaml.includes(`${placeholderPrefix}postgres-password`)) {
    throw new Error(`${label} is missing environment-specific secret placeholders.`);
  }
}

function assertCanonicalBaseHasNoThesisRoute(renderedYaml) {
  if (hasDocument(renderedYaml, 'Deployment', 'thesis-service')) {
    throw new Error('k8s/base must not render the Java thesis Deployment.');
  }
  if (hasDocument(renderedYaml, 'Service', 'thesis-service')) {
    throw new Error('k8s/base must not render the Java thesis Service.');
  }
  for (const forbidden of [
    'server thesis-service:4010;',
    'location = /api/v1/thesis {',
    'location ^~ /api/v1/thesis/ {',
  ]) {
    if (renderedYaml.includes(forbidden)) {
      throw new Error(`k8s/base must not render the Java thesis contract: ${forbidden}`);
    }
  }
}

function assertThesisPilotOverlay(renderedYaml) {
  extractDocument(renderedYaml, 'Namespace', 'campuscore-thesis-pilot');
  const deployment = extractDocument(renderedYaml, 'Deployment', 'thesis-service');
  const service = extractDocument(renderedYaml, 'Service', 'thesis-service');
  const nginxDeployment = extractDocument(renderedYaml, 'Deployment', 'nginx');
  const nginxService = extractDocument(renderedYaml, 'Service', 'campuscore-nginx');

  for (const expected of [
    'image: campuscore-thesis-service:pilot-local',
    'imagePullPolicy: IfNotPresent',
    'containerPort: 4010',
    'SPRING_DATASOURCE_URL',
    'currentSchema=thesis',
    'REDIS_URL',
    'FLYWAY_ENABLED',
    'HEALTH_READINESS_KEY',
    '/api/v1/health/readiness',
    '/api/v1/health/liveness',
  ]) {
    if (!deployment.includes(expected)) {
      throw new Error(
        `k8s/overlays/thesis-pilot deployment is missing the expected contract: ${expected}`,
      );
    }
  }

  if (!service.includes('port: 4010') || !service.includes('targetPort: 4010')) {
    throw new Error(
      'k8s/overlays/thesis-pilot service must expose thesis-service on port 4010.',
    );
  }

  if (!/type:\s*ClusterIP/u.test(nginxService)) {
    throw new Error(
      'k8s/overlays/thesis-pilot must keep campuscore-nginx behind ClusterIP.',
    );
  }

  for (const expected of [
    'name: thesis-pilot-config',
    '/etc/nginx/thesis-pilot-upstream.conf',
    '/etc/nginx/thesis-pilot-routes.conf',
    'campuscore-nginx-thesis-pilot-config',
  ]) {
    if (!nginxDeployment.includes(expected)) {
      throw new Error(
        `k8s/overlays/thesis-pilot nginx Deployment is missing the actual fragment mount: ${expected}`,
      );
    }
  }

  for (const expected of [
    'server thesis-service:4010;',
    'location = /api/v1/thesis {',
    'location ^~ /api/v1/thesis/ {',
  ]) {
    if (!renderedYaml.includes(expected)) {
      throw new Error(
        `k8s/overlays/thesis-pilot is missing the expected nginx contract: ${expected}`,
      );
    }
  }

  if (renderedYaml.includes('ghcr.io/jasontm17/campuscore-thesis-service:')) {
    throw new Error(
      'k8s/overlays/thesis-pilot must not imply that the unpublished Java image is a public release image.',
    );
  }
}

function assertOperatorOverlay(overlay) {
  const {
    clusterIssuer,
    label,
    placeholderPrefix,
    remoteSecretKey,
    renderedYaml,
    secretStoreName,
    tlsSecret,
    host,
  } = overlay;

  assertSharedClusterIngressContract(overlay);

  if (renderedYaml.includes(`${placeholderPrefix}postgres-password`)) {
    throw new Error(
      `${label} must delete the static placeholder Secret before layering operator-managed secrets.`,
    );
  }

  if (hasDocument(renderedYaml, 'Secret', 'campuscore-secrets')) {
    throw new Error(
      `${label} still renders a plain Secret named campuscore-secrets. Operator overlays must hand secret ownership to ExternalSecret.`,
    );
  }

  const certificateDoc = extractDocument(renderedYaml, 'Certificate', 'campuscore-tls');
  for (const expected of [
    `secretName: ${tlsSecret}`,
    `name: ${clusterIssuer}`,
    `- ${host}`,
  ]) {
    if (!certificateDoc.includes(expected)) {
      throw new Error(
        `${label} is missing the expected cert-manager setting: ${expected}`,
      );
    }
  }

  const externalSecretDoc = extractDocument(
    renderedYaml,
    'ExternalSecret',
    'campuscore-secrets',
  );
  for (const expected of [
    'kind: ClusterSecretStore',
    `name: ${secretStoreName}`,
    'name: campuscore-secrets',
    'creationPolicy: Owner',
    `key: ${remoteSecretKey}`,
    'secretKey: POSTGRES_PASSWORD',
    'secretKey: JWT_SECRET',
    'secretKey: JWT_REFRESH_SECRET',
    'secretKey: HEALTH_READINESS_KEY',
    'secretKey: INTERNAL_SERVICE_TOKEN',
    'secretKey: RABBITMQ_PASSWORD',
    'secretKey: MINIO_USER',
    'secretKey: MINIO_PASSWORD',
    'secretKey: SMTP_USER',
    'secretKey: SMTP_PASSWORD',
  ]) {
    if (!externalSecretDoc.includes(expected)) {
      throw new Error(
        `${label} is missing the expected external-secrets setting: ${expected}`,
      );
    }
  }
}

function assertSharedClusterIngressContract(overlay) {
  const { frontendUrl, host, label, renderedYaml, tlsSecret } = overlay;

  if (renderedYaml.includes('campuscore-local-readiness-key-12345')) {
    throw new Error(`${label} must not leak Docker Desktop local secrets.`);
  }

  if (renderedYaml.includes('replace-me-before-apply')) {
    throw new Error(`${label} still renders base placeholder secrets.`);
  }

  for (const expected of [
    'COOKIE_SECURE: "true"',
    'SWAGGER_ENABLED: "false"',
    `FRONTEND_URL: ${frontendUrl}`,
  ]) {
    if (!renderedYaml.includes(expected)) {
      throw new Error(`${label} is missing the expected override: ${expected}`);
    }
  }

  if (/ingressClassName:/u.test(renderedYaml)) {
    throw new Error(
      `${label} must stay cloud-agnostic and therefore must not hard-code ingressClassName.`,
    );
  }

  const nginxServiceDoc = extractDocument(renderedYaml, 'Service', 'campuscore-nginx');
  if (!/type:\s*ClusterIP/u.test(nginxServiceDoc)) {
    throw new Error(`${label} must keep campuscore-nginx behind a ClusterIP service.`);
  }

  const ingressDoc = extractDocument(renderedYaml, 'Ingress', 'campuscore');
  for (const expected of [
    `- ${host}`,
    `host: ${host}`,
    `secretName: ${tlsSecret}`,
  ]) {
    if (!ingressDoc.includes(expected)) {
      throw new Error(`${label} is missing the expected ingress setting: ${expected}`);
    }
  }

  for (const marker of forbiddenPublicIngressMarkers) {
    if (ingressDoc.includes(marker)) {
      throw new Error(
        `${label} must stay vendor-neutral in public overlays and therefore must not include ingress marker: ${marker}`,
      );
    }
  }
}

function assertPrivateTemplateOverlay(template) {
  const {
    clusterIssuer,
    frontendUrl,
    host,
    ingressClass,
    label,
    privateAnnotationKey,
    privateAnnotationValue,
    publicClusterIssuer,
    publicHost,
    publicRemoteSecretKey,
    publicSecretStoreName,
    publicTlsSecret,
    remoteSecretKey,
    renderedYaml,
    secretStoreName,
    tlsSecret,
  } = template;

  if (renderedYaml.includes('replace-me-before-apply')) {
    throw new Error(`${label} still renders base placeholder secrets.`);
  }

  for (const leakedPublicValue of [
    publicHost,
    publicTlsSecret,
    publicSecretStoreName,
    publicClusterIssuer,
    publicRemoteSecretKey,
  ]) {
    if (renderedYaml.includes(leakedPublicValue)) {
      throw new Error(
        `${label} must replace public handoff placeholders before landing in the tracked private template tree: ${leakedPublicValue}`,
      );
    }
  }

  const configMapDoc = extractDocument(renderedYaml, 'ConfigMap', 'campuscore-shared-env');
  if (!configMapDoc.includes(`FRONTEND_URL: ${frontendUrl}`)) {
    throw new Error(
      `${label} must override FRONTEND_URL to the private host placeholder ${frontendUrl}.`,
    );
  }

  const nginxServiceDoc = extractDocument(renderedYaml, 'Service', 'campuscore-nginx');
  if (!/type:\s*ClusterIP/u.test(nginxServiceDoc)) {
    throw new Error(`${label} must keep campuscore-nginx behind a ClusterIP service.`);
  }

  const ingressDoc = extractDocument(renderedYaml, 'Ingress', 'campuscore');
  for (const expected of [
    `host: ${host}`,
    `- ${host}`,
    `secretName: ${tlsSecret}`,
    `ingressClassName: ${ingressClass}`,
    privateAnnotationKey,
    privateAnnotationValue,
    `external-dns.alpha.kubernetes.io/hostname: ${host}`,
  ]) {
    if (!ingressDoc.includes(expected)) {
      throw new Error(
        `${label} is missing the expected private ingress placeholder: ${expected}`,
      );
    }
  }

  if (hasDocument(renderedYaml, 'Secret', 'campuscore-secrets')) {
    throw new Error(
      `${label} must keep secret ownership in ExternalSecret form, not reintroduce a plain Secret.`,
    );
  }

  const certificateDoc = extractDocument(renderedYaml, 'Certificate', 'campuscore-tls');
  for (const expected of [
    `secretName: ${tlsSecret}`,
    `name: ${clusterIssuer}`,
    `- ${host}`,
  ]) {
    if (!certificateDoc.includes(expected)) {
      throw new Error(
        `${label} is missing the expected private certificate placeholder: ${expected}`,
      );
    }
  }

  const externalSecretDoc = extractDocument(
    renderedYaml,
    'ExternalSecret',
    'campuscore-secrets',
  );
  for (const expected of [
    'kind: ClusterSecretStore',
    `name: ${secretStoreName}`,
    `key: ${remoteSecretKey}`,
    'secretKey: POSTGRES_PASSWORD',
    'secretKey: JWT_SECRET',
    'secretKey: JWT_REFRESH_SECRET',
    'secretKey: HEALTH_READINESS_KEY',
    'secretKey: INTERNAL_SERVICE_TOKEN',
    'secretKey: RABBITMQ_PASSWORD',
    'secretKey: MINIO_USER',
    'secretKey: MINIO_PASSWORD',
    'secretKey: SMTP_USER',
    'secretKey: SMTP_PASSWORD',
  ]) {
    if (!externalSecretDoc.includes(expected)) {
      throw new Error(
        `${label} is missing the expected private ExternalSecret placeholder: ${expected}`,
      );
    }
  }

  if (!renderedYaml.includes('replicas: 2') && !renderedYaml.includes('replicas: 3')) {
    throw new Error(
      `${label} must include tracked runtime override examples for replicas/resources/rollout policy.`,
    );
  }

  const deploymentDoc = extractDocument(renderedYaml, 'Deployment', 'core-api');
  for (const expected of [
    'type: RollingUpdate',
    'maxUnavailable: 0',
    'maxSurge: 1',
  ]) {
    if (!deploymentDoc.includes(expected)) {
      throw new Error(
        `${label} must include tracked rolling update examples for stateless runtime deployments.`,
      );
    }
  }
}

function assertPrivateTemplateBootstrap(template) {
  const { bootstrapRenderedYaml, label, namespace } = template;

  for (const jobName of bootstrapJobNames) {
    const jobDoc = extractDocument(bootstrapRenderedYaml, 'Job', jobName);
    if (!jobDoc.includes(`namespace: ${namespace}`)) {
      throw new Error(
        `${label}/bootstrap must rewrite ${jobName} into namespace ${namespace}.`,
      );
    }
  }
}

function hasDocument(renderedYaml, kind, name) {
  try {
    extractDocument(renderedYaml, kind, name);
    return true;
  } catch {
    return false;
  }
}

async function kubectlKustomize(targetDir) {
  return run('kubectl', ['kustomize', targetDir], { captureOutput: true });
}

function run(command, args, options = {}) {
  const { cwd = repoRoot, captureOutput = false } = options;

  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      stdio: captureOutput ? ['ignore', 'pipe', 'pipe'] : 'inherit',
      shell: false,
    });

    let stdout = '';
    let stderr = '';

    if (captureOutput) {
      child.stdout.on('data', (chunk) => {
        stdout += chunk.toString();
      });
      child.stderr.on('data', (chunk) => {
        stderr += chunk.toString();
      });
    }

    child.on('error', (error) => reject(error));
    child.on('close', (code) => {
      if (code === 0) {
        resolve(captureOutput ? stdout : undefined);
        return;
      }

      reject(
        new Error(
          `${command} ${args.join(' ')} exited with code ${code}${
            stderr ? `\n${stderr}` : ''
          }`,
        ),
      );
    });
  });
}

function escapeForRegExp(value) {
  return value.replace(/[.*+?^${}()|[\]\\]/gu, '\\$&');
}

await main();
