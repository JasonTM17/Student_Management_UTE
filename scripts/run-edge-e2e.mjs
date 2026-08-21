import { mkdir, rm, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';
import net from 'node:net';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');
const frontendDir = path.join(repoRoot, 'frontend');
const composeFile = path.join(repoRoot, 'docker-compose.e2e.yml');
const logsDir = path.join(frontendDir, 'test-results', 'edge-e2e-stack');
const projectName = process.env.E2E_COMPOSE_PROJECT ?? 'campuscore-edge-e2e';
const requestedEdgePort = Number(process.env.E2E_EDGE_PORT ?? '80');
let edgePort = requestedEdgePort;
let baseURL = `http://127.0.0.1${edgePort === 80 ? '' : `:${edgePort}`}`;
let apiURL = `${baseURL}/api/v1`;
const keepStack = process.env.E2E_KEEP_STACK === '1';
const usePrebuiltImages = process.env.E2E_USE_PREBUILT_IMAGES === '1';
const playwrightCli = path.join(frontendDir, 'node_modules', 'playwright', 'cli.js');
const readinessKey =
  process.env.HEALTH_READINESS_KEY ?? 'edge-e2e-readiness-key-12345';
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
  process.env.INTERNAL_SERVICE_TOKEN ?? 'edge-e2e-internal-service-token-12345';
const sessionCacheNamespace =
  process.env.E2E_SESSION_CACHE_NAMESPACE ??
  `${projectName}-${Date.now()}`;

const composeBaseArgs = ['compose', '-p', projectName, '-f', composeFile];
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

async function main() {
  await rm(logsDir, { recursive: true, force: true });
  await mkdir(logsDir, { recursive: true });
  edgePort = await resolveEdgePort();
  baseURL = `http://127.0.0.1${edgePort === 80 ? '' : `:${edgePort}`}`;
  apiURL = `${baseURL}/api/v1`;

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

    await waitForResponse(`${baseURL}/health`, (payload) => {
      return payload?.status === 'ok' && payload?.service === 'campuscore-api';
    });

    await waitForResponse(`${baseURL}/login`, (_, response) => response.ok, {
      parseJson: false,
    });

    await waitForResponse(`${baseURL}/api/docs`, (_, response) => response.ok, {
      parseJson: false,
    });

    await writeFile(
      path.join(logsDir, 'liveness.json'),
      JSON.stringify(await getJson(`${baseURL}/health`), null, 2),
      'utf8',
    );
    await writeFile(
      path.join(logsDir, 'readiness.json'),
      JSON.stringify(
        {
          coreApi: await getInternalReadiness('core-api', 4000),
          authService: await getInternalReadiness('auth-service', 4007),
          notificationService: await getInternalReadiness(
            'notification-service',
            4001,
          ),
          financeService: await getInternalReadiness('finance-service', 4002),
          academicService: await getInternalReadiness('academic-service', 4003),
          engagementService: await getInternalReadiness(
            'engagement-service',
            4004,
          ),
          peopleService: await getInternalReadiness('people-service', 4005),
          analyticsService: await getInternalReadiness('analytics-service', 4006),
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

    await run(process.execPath, [playwrightCli, 'test'], {
      cwd: frontendDir,
      env: {
        ...process.env,
        E2E_EXTERNAL_STACK: '1',
        E2E_BASE_URL: baseURL,
        E2E_API_URL: apiURL,
        E2E_SESSION_CACHE_NAMESPACE: sessionCacheNamespace,
      },
    });
  } catch (error) {
    await collectArtifacts();
    throw error;
  } finally {
    if (!keepStack) {
      await compose(['down', '-v', '--remove-orphans'], { allowFailure: true });
    }
  }
}

async function waitForResponse(
  url,
  predicate,
  options = { parseJson: true, timeoutMs: 180_000, intervalMs: 2_000 },
) {
  const timeoutMs = options.timeoutMs ?? 180_000;
  const intervalMs = options.intervalMs ?? 2_000;
  const deadline = Date.now() + timeoutMs;
  let lastError = null;

  while (Date.now() < deadline) {
    try {
      const response = await fetch(url);
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

async function getJson(url) {
  const response = await fetch(url);
  if (!response.ok) {
    throw new Error(`Failed to fetch ${url}: ${response.status}`);
  }

  return response.json();
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
  const command = [
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
  ];
  const output = await compose(command, { captureOutput: true });

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

      reject(
        new Error(
          `${command} ${args.join(' ')} exited with code ${code}`,
        ),
      );
    });
  });
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function resolveEdgePort() {
  if (process.env.E2E_EDGE_PORT) {
    return requestedEdgePort;
  }

  if (await isPortAvailable(requestedEdgePort)) {
    return requestedEdgePort;
  }

  for (const fallbackPort of [8088, 8089, 18080]) {
    if (await isPortAvailable(fallbackPort)) {
      console.warn(
        `[run-edge-e2e] Port ${requestedEdgePort} is already in use; falling back to ${fallbackPort}.`,
      );
      return fallbackPort;
    }
  }

  throw new Error(
    `[run-edge-e2e] Port ${requestedEdgePort} is busy and no fallback port was available. Set E2E_EDGE_PORT explicitly to an open port and retry.`,
  );
}

async function isPortAvailable(port) {
  return new Promise((resolve) => {
    const server = net.createServer();

    server.once('error', () => resolve(false));
    server.once('listening', () => {
      server.close(() => resolve(true));
    });

    server.listen(port, '127.0.0.1');
  });
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
