import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');
const frontendDir = path.join(repoRoot, 'frontend');
const composeFile = path.join(repoRoot, 'docker-compose.e2e.yml');
const logsDir = path.join(frontendDir, 'test-results', 'image-smoke-stack');
const projectName = process.env.IMAGE_SMOKE_COMPOSE_PROJECT ?? 'campuscore-image-smoke';
// Use a high default host port so image-smoke does not collide with a live
// local edge or unrelated services that already occupy 8080 on developer
// machines.
const edgePort = Number(process.env.IMAGE_SMOKE_EDGE_PORT ?? '18081');
const baseURL = `http://127.0.0.1:${edgePort}`;
const internalEdgeURL = process.env.IMAGE_SMOKE_INTERNAL_EDGE_URL ?? 'http://nginx';
const internalEdgeProbeService =
  process.env.IMAGE_SMOKE_INTERNAL_EDGE_PROBE_SERVICE ?? 'frontend';
const keepStack = process.env.IMAGE_SMOKE_KEEP_STACK === '1';
const usePrebuiltImages = process.env.E2E_USE_PREBUILT_IMAGES === '1';
const readinessKey =
  process.env.HEALTH_READINESS_KEY ?? 'image-smoke-readiness-key-12345';
const coreApiImage =
  process.env.E2E_CORE_API_IMAGE ?? 'campuscore-backend:e2e-local';
const authServiceImage =
  process.env.E2E_AUTH_SERVICE_IMAGE ?? 'campuscore-auth-service:e2e-local';
const notificationServiceImage =
  process.env.E2E_NOTIFICATION_SERVICE_IMAGE ??
  'campuscore-notification-service:e2e-local';
const financeServiceImage =
  process.env.E2E_FINANCE_SERVICE_IMAGE ??
  'campuscore-finance-service:e2e-local';
const academicServiceImage =
  process.env.E2E_ACADEMIC_SERVICE_IMAGE ??
  'campuscore-academic-service:e2e-local';
const engagementServiceImage =
  process.env.E2E_ENGAGEMENT_SERVICE_IMAGE ??
  'campuscore-engagement-service:e2e-local';
const peopleServiceImage =
  process.env.E2E_PEOPLE_SERVICE_IMAGE ??
  'campuscore-people-service:e2e-local';
const analyticsServiceImage =
  process.env.E2E_ANALYTICS_SERVICE_IMAGE ??
  'campuscore-analytics-service:e2e-local';
const frontendImage =
  process.env.E2E_FRONTEND_IMAGE ?? 'campuscore-frontend:e2e-local';
const internalServiceToken =
  process.env.INTERNAL_SERVICE_TOKEN ?? 'image-smoke-internal-service-token-12345';
const servicesForLogs = [
  'core-api-init',
  'core-api',
  'auth-service-init',
  'auth-service',
  'notification-service-init',
  'notification-service',
  'finance-service-init',
  'finance-service',
  'academic-service-init',
  'academic-service',
  'engagement-service-init',
  'engagement-service',
  'people-service-init',
  'people-service',
  'analytics-service-init',
  'analytics-service',
  'frontend',
  'nginx',
  'postgres',
  'redis',
  'rabbitmq',
  'minio',
  'minio-init',
];

const composeBaseArgs = ['compose', '-p', projectName, '-f', composeFile];

async function main() {
  await rm(logsDir, { recursive: true, force: true });
  await mkdir(logsDir, { recursive: true });

  try {
    await compose(['down', '-v', '--remove-orphans'], { allowFailure: true });
    if (usePrebuiltImages) {
      await compose([
        'pull',
        'core-api',
        'auth-service',
        'notification-service',
        'finance-service',
        'academic-service',
        'engagement-service',
        'people-service',
        'analytics-service',
        'frontend',
      ]);
      await compose(['up', '-d']);
    } else {
      await buildStackSequentially();
      await compose(['up', '-d']);
    }

    const liveness = await waitForEdgeJson('/health', (payload) => {
      return payload?.status === 'ok' && payload?.service === 'campuscore-api';
    });
    const coreReadiness = await getInternalReadiness('core-api', 4000);
    const authReadiness = await getInternalReadiness('auth-service', 4007);
    const notificationReadiness = await getInternalReadiness(
      'notification-service',
      4001,
    );
    const financeReadiness = await getInternalReadiness('finance-service', 4002);
    const academicReadiness = await getInternalReadiness('academic-service', 4003);
    const engagementReadiness = await getInternalReadiness(
      'engagement-service',
      4004,
    );
    const peopleReadiness = await getInternalReadiness('people-service', 4005);
    const analyticsReadiness = await getInternalReadiness(
      'analytics-service',
      4006,
    );

    await waitForEdgeResponse('/', (_, response) => response.ok, {
      parseJson: false,
    });
    await waitForEdgeResponse('/login', (_, response) => response.ok, {
      parseJson: false,
    });
    await waitForEdgeResponse('/api/docs', (_, response) => response.ok, {
      parseJson: false,
    });
    await waitForEdgeResponse(
      '/api/v1/health/readiness',
      (_, response) => response.status === 403,
      { parseJson: false },
    );
    await waitForEdgeResponse(
      '/api/v1/internal/people-context/users/test-user',
      (_, response) => response.status === 403,
      { parseJson: false },
    );
    await waitForEdgeResponse(
      '/api/v1/internal/auth-context/users/test-user',
      (_, response) => response.status === 403,
      { parseJson: false },
    );
    await waitForEdgeResponse(
      '/api/v1/students',
      (_, response) => [401, 403].includes(response.status),
      { parseJson: false },
    );
    await waitForEdgeResponse(
      '/api/v1/announcements',
      (_, response) => [401, 403].includes(response.status),
      { parseJson: false },
    );
    await waitForEdgeResponse(
      '/api/v1/analytics/overview',
      (_, response) => [401, 403].includes(response.status),
      { parseJson: false },
    );

    const coreApiCmd = await inspectServiceCommand('core-api');
    const authCmd = await inspectServiceCommand('auth-service');
    const notificationCmd = await inspectServiceCommand(
      'notification-service',
    );
    const financeCmd = await inspectServiceCommand('finance-service');
    const academicCmd = await inspectServiceCommand('academic-service');
    const engagementCmd = await inspectServiceCommand('engagement-service');
    const peopleCmd = await inspectServiceCommand('people-service');
    const analyticsCmd = await inspectServiceCommand('analytics-service');
    const frontendCmd = await inspectServiceCommand('frontend');

    if (!coreApiCmd.some((part) => part.includes('dist/src/main.js'))) {
      throw new Error(
        `Core API runtime is not using dist/src/main.js: ${coreApiCmd.join(' ')}`,
      );
    }

    if (!authCmd.some((part) => part.includes('dist/src/main.js'))) {
      throw new Error(
        `Auth service runtime is not using dist/src/main.js: ${authCmd.join(' ')}`,
      );
    }

    if (!notificationCmd.some((part) => part.includes('dist/src/main.js'))) {
      throw new Error(
        `Notification service runtime is not using dist/src/main.js: ${notificationCmd.join(' ')}`,
      );
    }

    if (!financeCmd.some((part) => part.includes('dist/src/main.js'))) {
      throw new Error(
        `Finance service runtime is not using dist/src/main.js: ${financeCmd.join(' ')}`,
      );
    }

    if (!academicCmd.some((part) => part.includes('dist/src/main.js'))) {
      throw new Error(
        `Academic service runtime is not using dist/src/main.js: ${academicCmd.join(' ')}`,
      );
    }

    if (!engagementCmd.some((part) => part.includes('dist/src/main.js'))) {
      throw new Error(
        `Engagement service runtime is not using dist/src/main.js: ${engagementCmd.join(' ')}`,
      );
    }

    if (!peopleCmd.some((part) => part.includes('dist/src/main.js'))) {
      throw new Error(
        `People service runtime is not using dist/src/main.js: ${peopleCmd.join(' ')}`,
      );
    }

    if (!analyticsCmd.some((part) => part.includes('dist/src/main.js'))) {
      throw new Error(
        `Analytics service runtime is not using dist/src/main.js: ${analyticsCmd.join(' ')}`,
      );
    }

    if (!frontendCmd.some((part) => part.includes('.next/standalone/server.js'))) {
      throw new Error(
        `Frontend runtime is not using standalone server.js: ${frontendCmd.join(' ')}`,
      );
    }

    await writeFile(
      path.join(logsDir, 'liveness.json'),
      JSON.stringify(liveness, null, 2),
      'utf8',
    );
    await writeFile(
      path.join(logsDir, 'readiness.json'),
      JSON.stringify(
        {
          coreApi: coreReadiness,
          authService: authReadiness,
          notificationService: notificationReadiness,
          financeService: financeReadiness,
          academicService: academicReadiness,
          engagementService: engagementReadiness,
          peopleService: peopleReadiness,
          analyticsService: analyticsReadiness,
        },
        null,
        2,
      ),
      'utf8',
    );
    await writeFile(
      path.join(logsDir, 'runtime-commands.json'),
      JSON.stringify(
        {
          coreApi: coreApiCmd,
          authService: authCmd,
          notificationService: notificationCmd,
          financeService: financeCmd,
          academicService: academicCmd,
          engagementService: engagementCmd,
          peopleService: peopleCmd,
          analyticsService: analyticsCmd,
          frontend: frontendCmd,
        },
        null,
        2,
      ),
      'utf8',
    );
    await writeFile(
      path.join(logsDir, 'image-mode.json'),
      JSON.stringify(
        {
          usePrebuiltImages,
          coreApiImage,
          authServiceImage,
          notificationServiceImage,
          financeServiceImage,
          academicServiceImage,
          engagementServiceImage,
          peopleServiceImage,
          analyticsServiceImage,
          frontendImage,
        },
        null,
        2,
      ),
      'utf8',
    );
  } catch (error) {
    await collectArtifacts();
    throw error;
  } finally {
    if (!keepStack) {
      await compose(['down', '-v', '--remove-orphans'], { allowFailure: true });
    }
  }
}

async function waitForJson(url, predicate, options = {}) {
  return waitForResponse(url, predicate, {
    parseJson: true,
    timeoutMs: options.timeoutMs ?? 180_000,
    intervalMs: options.intervalMs ?? 2_000,
  });
}

async function waitForEdgeJson(pathname, predicate, options = {}) {
  return waitForEdgeResponse(pathname, predicate, {
    ...options,
    parseJson: true,
    timeoutMs: options.timeoutMs ?? 180_000,
    intervalMs: options.intervalMs ?? 2_000,
  });
}

async function waitForEdgeResponse(pathname, predicate, options = {}) {
  const timeoutMs = options.timeoutMs ?? 180_000;
  const hostDeadline = Math.min(timeoutMs, options.hostTimeoutMs ?? 45_000);
  const hostUrl = `${baseURL}${pathname}`;

  try {
    return await waitForResponse(hostUrl, predicate, {
      ...options,
      timeoutMs: hostDeadline,
    });
  } catch (hostError) {
    if (!shouldFallbackToInternalEdge(hostError)) {
      throw hostError;
    }

    return waitForServiceResponse(
      internalEdgeProbeService,
      `${internalEdgeURL}${pathname}`,
      predicate,
      options,
      hostError,
    );
  }
}

async function waitForResponse(
  url,
  predicate,
  options = { parseJson: true, timeoutMs: 180_000, intervalMs: 2_000 },
) {
  const timeoutMs = options.timeoutMs ?? 180_000;
  const intervalMs = options.intervalMs ?? 2_000;
  const requestTimeoutMs = options.requestTimeoutMs ?? Math.min(15_000, timeoutMs);
  const deadline = Date.now() + timeoutMs;
  let lastError = null;

  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, {
        headers: {
          Connection: 'close',
          ...(options.headers ?? {}),
        },
        signal: AbortSignal.timeout(requestTimeoutMs),
      });
      const payload = options.parseJson === false ? null : await response.json();

      if (predicate(payload, response)) {
        return payload;
      }

      lastError = new Error(
        `Received unexpected response from ${url}: ${response.status}`,
      );
    } catch (error) {
      lastError = error;
    }

    await delay(intervalMs);
  }

  throw lastError ?? new Error(`Timed out waiting for ${url}`);
}

async function waitForServiceResponse(
  serviceName,
  url,
  predicate,
  options = { parseJson: true, timeoutMs: 180_000, intervalMs: 2_000 },
  hostError = null,
) {
  const timeoutMs = options.timeoutMs ?? 180_000;
  const intervalMs = options.intervalMs ?? 2_000;
  const requestTimeoutMs = options.requestTimeoutMs ?? Math.min(15_000, timeoutMs);
  const deadline = Date.now() + timeoutMs;
  let lastError = hostError;

  while (Date.now() < deadline) {
    try {
      const output = await compose(
        [
          'exec',
          '-T',
          serviceName,
          'node',
          '-e',
          `
            const url = ${JSON.stringify(url)};
            const headers = ${JSON.stringify(options.headers ?? {})};
            const requestTimeoutMs = ${requestTimeoutMs};

            fetch(url, {
              headers,
              signal: AbortSignal.timeout(requestTimeoutMs),
            })
              .then(async (response) => {
                const body = await response.text();
                process.stdout.write(JSON.stringify({
                  ok: response.ok,
                  status: response.status,
                  body,
                }));
              })
              .catch((error) => {
                console.error(error?.stack ?? String(error));
                process.exit(1);
              });
          `,
        ],
        { captureOutput: true },
      );

      const result = JSON.parse(output.trim());
      const payload =
        options.parseJson === false ? null : JSON.parse(result.body || 'null');
      const response = {
        ok: result.ok,
        status: result.status,
      };

      if (predicate(payload, response)) {
        return payload;
      }

      lastError = new Error(
        `Received unexpected response from ${url}: ${result.status}`,
      );
    } catch (error) {
      lastError = error;
    }

    await delay(intervalMs);
  }

  throw lastError ?? new Error(`Timed out waiting for ${url}`);
}

function shouldFallbackToInternalEdge(error) {
  const message = error?.stack ?? error?.message ?? String(error);
  return (
    /ECONNRESET/u.test(message) ||
    /TimeoutError/u.test(message) ||
    /timed out/u.test(message) ||
    /fetch failed/u.test(message)
  );
}

async function collectArtifacts() {
  for (const service of servicesForLogs) {
    const logPath = path.join(logsDir, `${service}.log`);
    const output = await compose(['logs', '--no-color', service], {
      allowFailure: true,
      captureOutput: true,
    });
    await writeFile(logPath, output ?? '', 'utf8');
  }

  const psOutput = await compose(['ps', '--format', 'json'], {
    allowFailure: true,
    captureOutput: true,
  });
  await writeFile(path.join(logsDir, 'compose-ps.json'), psOutput ?? '', 'utf8');
}

async function inspectServiceCommand(service) {
  const containerId = (
    await compose(['ps', '-q', service], {
      captureOutput: true,
    })
  )
    ?.trim()
    .split(/\r?\n/u)[0];

  if (!containerId) {
    throw new Error(`Could not resolve container id for service ${service}`);
  }

  const commandJson = await run(
    'docker',
    ['inspect', '-f', '{{json .Config.Cmd}}', containerId],
    {
      cwd: repoRoot,
      captureOutput: true,
    },
  );

  return JSON.parse(commandJson.trim());
}

async function compose(args, options = {}) {
  return run('docker', [...composeBaseArgs, ...args], {
    cwd: repoRoot,
    env: {
      ...process.env,
      E2E_EDGE_PORT: String(edgePort),
      E2E_FRONTEND_URL: baseURL,
      HEALTH_READINESS_KEY: readinessKey,
      E2E_CORE_API_IMAGE: coreApiImage,
      E2E_AUTH_SERVICE_IMAGE: authServiceImage,
      E2E_NOTIFICATION_SERVICE_IMAGE: notificationServiceImage,
      E2E_FINANCE_SERVICE_IMAGE: financeServiceImage,
      E2E_ACADEMIC_SERVICE_IMAGE: academicServiceImage,
      E2E_ENGAGEMENT_SERVICE_IMAGE: engagementServiceImage,
      E2E_PEOPLE_SERVICE_IMAGE: peopleServiceImage,
      E2E_ANALYTICS_SERVICE_IMAGE: analyticsServiceImage,
      E2E_FRONTEND_IMAGE: frontendImage,
      INTERNAL_SERVICE_TOKEN: internalServiceToken,
    },
    ...options,
  });
}

async function buildStackSequentially() {
  const buildOrder = [
    'core-api-init',
    'auth-service-init',
    'notification-service-init',
    'finance-service-init',
    'academic-service-init',
    'engagement-service-init',
    'people-service-init',
    'analytics-service-init',
    'core-api',
    'auth-service',
    'notification-service',
    'finance-service',
    'academic-service',
    'engagement-service',
    'people-service',
    'analytics-service',
    'frontend',
  ];

  for (const service of buildOrder) {
    await compose(['build', service]);
  }
}

async function getInternalReadiness(serviceName, port) {
  const output = await compose(
    [
      'exec',
      '-T',
      serviceName,
      'node',
      '-e',
      `
        fetch('http://127.0.0.1:${port}/api/v1/health/readiness', {
          headers: { 'X-Health-Key': ${JSON.stringify(readinessKey)} },
        })
          .then(async (response) => {
            const body = await response.text();
            if (!response.ok) {
              console.error(body);
              process.exit(1);
            }
            process.stdout.write(body);
          })
          .catch((error) => {
            console.error(error);
            process.exit(1);
          });
      `,
    ],
    { captureOutput: true },
  );

  return JSON.parse(output.trim());
}

function run(command, args, options = {}) {
  const {
    cwd = repoRoot,
    env = process.env,
    allowFailure = false,
    captureOutput = false,
  } = options;

  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      env,
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

    child.on('error', (error) => {
      reject(error);
    });

    child.on('close', (code) => {
      if (code === 0 || allowFailure) {
        resolve(captureOutput ? `${stdout}${stderr}` : undefined);
        return;
      }

      reject(new Error(`${command} ${args.join(' ')} exited with code ${code}`));
    });
  });
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

const keepAlive = setInterval(() => {}, 1_000);

main()
  .catch((error) => {
    console.error(error);
    process.exitCode = 1;
  })
  .finally(() => {
    clearInterval(keepAlive);
  });
