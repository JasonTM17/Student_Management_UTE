import { createWriteStream } from 'node:fs';
import { mkdir, rm, writeFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import { createServer as createNetServer } from 'node:net';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawn } from 'node:child_process';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');
const backendDir = path.join(repoRoot, 'backend');
const authDir = path.join(repoRoot, 'auth-service');
const notificationDir = path.join(repoRoot, 'notification-service');
const financeDir = path.join(repoRoot, 'finance-service');
const academicDir = path.join(repoRoot, 'academic-service');
const engagementDir = path.join(repoRoot, 'engagement-service');
const peopleDir = path.join(repoRoot, 'people-service');
const analyticsDir = path.join(repoRoot, 'analytics-service');
const frontendDir = path.join(repoRoot, 'frontend');
const logsDir = path.join(frontendDir, 'test-results', 'fast-e2e-stack');
const playwrightCli = path.join(frontendDir, 'node_modules', 'playwright', 'cli.js');
const nextCli = path.join(frontendDir, 'node_modules', 'next', 'dist', 'bin', 'next');

const postgresContainer =
  process.env.E2E_POSTGRES_CONTAINER ?? 'campuscore-fast-e2e-postgres';
const postgresPort = Number(process.env.E2E_DB_PORT ?? '55433');
const postgresUser = process.env.E2E_POSTGRES_USER ?? 'campuscore';
const postgresPassword =
  process.env.E2E_POSTGRES_PASSWORD ?? 'campuscore_password';
const postgresDatabase = process.env.E2E_POSTGRES_DB ?? 'campuscore_e2e';
const publicDatabaseUrl =
  process.env.E2E_DATABASE_URL ??
  `postgresql://${postgresUser}:${postgresPassword}@127.0.0.1:${postgresPort}/${postgresDatabase}?schema=public`;
const notificationsDatabaseUrl = publicDatabaseUrl.replace(
  'schema=public',
  'schema=notifications'
);
const authDatabaseUrl = publicDatabaseUrl.replace('schema=public', 'schema=auth');
const financeDatabaseUrl = publicDatabaseUrl.replace('schema=public', 'schema=finance');
const academicDatabaseUrl = publicDatabaseUrl.replace(
  'schema=public',
  'schema=academic'
);
const engagementDatabaseUrl = publicDatabaseUrl.replace(
  'schema=public',
  'schema=engagement'
);
const peopleDatabaseUrl = publicDatabaseUrl.replace('schema=public', 'schema=people');
const analyticsDatabaseUrl = publicDatabaseUrl;

const frontendBaseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:3101';
const frontendPort = Number(new URL(frontendBaseURL).port || '80');
const proxyPort = Number(process.env.E2E_GATEWAY_PORT ?? '4180');
const apiBaseURL = process.env.E2E_API_URL ?? `http://127.0.0.1:${proxyPort}/api/v1`;
const backendPort = Number(process.env.E2E_CORE_API_PORT ?? '4100');
const authPort = Number(process.env.E2E_AUTH_PORT ?? '4107');
const notificationPort = Number(process.env.E2E_NOTIFICATION_PORT ?? '4101');
const financePort = Number(process.env.E2E_FINANCE_PORT ?? '4102');
const academicPort = Number(process.env.E2E_ACADEMIC_PORT ?? '4103');
const engagementPort = Number(process.env.E2E_ENGAGEMENT_PORT ?? '4104');
const peoplePort = Number(process.env.E2E_PEOPLE_PORT ?? '4105');
const analyticsPort = Number(process.env.E2E_ANALYTICS_PORT ?? '4106');
const e2eProfile = process.env.E2E_PROFILE ?? 'full';
const playwrightFilter = process.env.E2E_PLAYWRIGHT_FILTER;
const internalServiceToken =
  process.env.INTERNAL_SERVICE_TOKEN ?? 'local-fast-e2e-internal-token-12345';
const keepPostgres = process.env.E2E_KEEP_POSTGRES === '1';
const managedProcesses = [];
const frontendNodeOptions = [process.env.NODE_OPTIONS, '--max-old-space-size=4096']
  .filter(Boolean)
  .join(' ');
const sessionCacheNamespace =
  process.env.E2E_SESSION_CACHE_NAMESPACE ?? `fast-e2e-${Date.now()}`;
let gatewayServer;
let gatewayLogStream;
const academicApiPrefixes = [
  '/api/v1/academic-years',
  '/api/v1/attendance',
  '/api/v1/classrooms',
  '/api/v1/courses',
  '/api/v1/curricula',
  '/api/v1/departments',
  '/api/v1/enrollments',
  '/api/v1/faculties',
  '/api/v1/grades',
  '/api/v1/schedules',
  '/api/v1/sections',
  '/api/v1/semesters',
  '/api/v1/waitlist'
];
const enabledServices = {
  engagement: e2eProfile !== 'finance',
  people: e2eProfile !== 'finance',
  analytics: e2eProfile !== 'finance'
};

function nestStartArgs(serviceDir) {
  return [path.join(serviceDir, 'node_modules', '@nestjs', 'cli', 'bin', 'nest.js'), 'start'];
}

function frontendDevArgs() {
  return [
    nextCli,
    'dev',
    '--hostname',
    '127.0.0.1',
    '--port',
    String(frontendPort),
  ];
}

async function main() {
  await rm(logsDir, { recursive: true, force: true });
  await mkdir(logsDir, { recursive: true });

  try {
    await assertDockerAvailable();
    await assertPortsAvailable();
    await cleanupPostgres();
    await startPostgres();
    await waitForPostgres();
    await prepareDatabase();
    await startApplicationServers();
    await waitForResponse(
      `http://127.0.0.1:${authPort}/api/v1/health/liveness`,
      (_, response) => response.ok
    );
    await waitForResponse(
      `http://127.0.0.1:${notificationPort}/api/v1/health/liveness`,
      (_, response) => response.ok
    );
    await waitForResponse(
      `http://127.0.0.1:${financePort}/api/v1/health/liveness`,
      (_, response) => response.ok
    );
    await waitForResponse(
      `http://127.0.0.1:${academicPort}/api/v1/health/liveness`,
      (_, response) => response.ok
    );
    if (enabledServices.engagement) {
      await waitForResponse(
        `http://127.0.0.1:${engagementPort}/api/v1/health/liveness`,
        (_, response) => response.ok
      );
    }
    if (enabledServices.people) {
      await waitForResponse(
        `http://127.0.0.1:${peoplePort}/api/v1/health/liveness`,
        (_, response) => response.ok
      );
    }
    if (enabledServices.analytics) {
      await waitForResponse(
        `http://127.0.0.1:${analyticsPort}/api/v1/health/liveness`,
        (_, response) => response.ok
      );
    }
    await waitForResponse(`${apiBaseURL}/health/liveness`, (_, response) => response.ok);
    await waitForResponse(frontendBaseURL, (_, response) => response.ok);

    const playwrightArgs = [playwrightCli, 'test'];
    if (playwrightFilter) {
      playwrightArgs.push(playwrightFilter);
    }

    await run(process.execPath, playwrightArgs, {
      cwd: frontendDir,
      env: {
        ...process.env,
        E2E_SKIP_WEBSERVER: '1',
        E2E_BASE_URL: frontendBaseURL,
        E2E_API_URL: apiBaseURL,
        E2E_DATABASE_URL: publicDatabaseUrl,
        E2E_REDIS_URL: 'disabled',
        E2E_RABBITMQ_URL: 'disabled',
        NODE_OPTIONS: frontendNodeOptions,
        E2E_SESSION_CACHE_NAMESPACE: sessionCacheNamespace
      }
    });
  } catch (error) {
    await collectArtifacts();
    throw error;
  } finally {
    await stopApplicationServers();
    await cleanupManagedPortListeners();

    if (!keepPostgres) {
      await cleanupPostgres();
    }
  }
}

async function prepareDatabase() {
  await run('npm', ['run', 'prisma:generate'], {
    cwd: backendDir,
    env: { ...process.env, DATABASE_URL: publicDatabaseUrl }
  });
  await run('npm', ['exec', '--', 'prisma', 'db', 'push', '--skip-generate'], {
    cwd: backendDir,
    env: { ...process.env, DATABASE_URL: publicDatabaseUrl }
  });
  await run('npm', ['run', 'prisma:generate'], {
    cwd: authDir,
    env: { ...process.env, DATABASE_URL: authDatabaseUrl }
  });
  await run('npm', ['exec', '--', 'prisma', 'db', 'push', '--skip-generate'], {
    cwd: authDir,
    env: { ...process.env, DATABASE_URL: authDatabaseUrl }
  });
  await run('npm', ['run', 'prisma:generate'], {
    cwd: notificationDir,
    env: { ...process.env, DATABASE_URL: notificationsDatabaseUrl }
  });
  await run('npm', ['run', 'prisma:generate'], {
    cwd: financeDir,
    env: { ...process.env, DATABASE_URL: financeDatabaseUrl }
  });
  await run('npm', ['run', 'prisma:generate'], {
    cwd: academicDir,
    env: { ...process.env, DATABASE_URL: academicDatabaseUrl }
  });
  if (enabledServices.engagement) {
    await run('npm', ['run', 'prisma:generate'], {
      cwd: engagementDir,
      env: { ...process.env, DATABASE_URL: engagementDatabaseUrl }
    });
  }
  if (enabledServices.people) {
    await run('npm', ['run', 'prisma:generate'], {
      cwd: peopleDir,
      env: { ...process.env, DATABASE_URL: peopleDatabaseUrl }
    });
  }
  if (enabledServices.analytics) {
    await run('npm', ['run', 'prisma:generate'], {
      cwd: analyticsDir,
      env: { ...process.env, DATABASE_URL: analyticsDatabaseUrl }
    });
  }
  await run('npm', ['run', 'prisma:dbpush'], {
    cwd: notificationDir,
    env: { ...process.env, DATABASE_URL: notificationsDatabaseUrl }
  });
  await run('npm', ['run', 'prisma:dbpush'], {
    cwd: financeDir,
    env: { ...process.env, DATABASE_URL: financeDatabaseUrl }
  });
  await run('npm', ['run', 'prisma:dbpush'], {
    cwd: academicDir,
    env: { ...process.env, DATABASE_URL: academicDatabaseUrl }
  });
  if (enabledServices.engagement) {
    await run('npm', ['run', 'prisma:dbpush'], {
      cwd: engagementDir,
      env: { ...process.env, DATABASE_URL: engagementDatabaseUrl }
    });
  }
  if (enabledServices.people) {
    await run('npm', ['run', 'prisma:dbpush'], {
      cwd: peopleDir,
      env: { ...process.env, DATABASE_URL: peopleDatabaseUrl }
    });
  }
  if (enabledServices.analytics) {
    await run('npm', ['run', 'prisma:dbpush'], {
      cwd: analyticsDir,
      env: { ...process.env, DATABASE_URL: analyticsDatabaseUrl }
    });
  }
  await run('npm', ['run', 'prisma:seed'], {
    cwd: backendDir,
    env: { ...process.env, DATABASE_URL: publicDatabaseUrl }
  });
  await run('npm', ['run', 'data:migrate:legacy'], {
    cwd: authDir,
    env: {
      ...process.env,
      DATABASE_URL: authDatabaseUrl,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      JWT_REFRESH_SECRET:
        process.env.E2E_JWT_REFRESH_SECRET ?? 'e2e-jwt-refresh-secret',
      FRONTEND_URL: frontendBaseURL,
      INTERNAL_SERVICE_TOKEN: internalServiceToken,
      RABBITMQ_URL: 'disabled',
      REDIS_URL: 'disabled'
    }
  });
  await run('npm', ['run', 'data:migrate:legacy'], {
    cwd: notificationDir,
    env: {
      ...process.env,
      DATABASE_URL: notificationsDatabaseUrl,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      FRONTEND_URL: frontendBaseURL,
      INTERNAL_SERVICE_TOKEN: internalServiceToken
    }
  });
  await run('npm', ['run', 'data:migrate:legacy'], {
    cwd: financeDir,
    env: {
      ...process.env,
      DATABASE_URL: financeDatabaseUrl,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      FRONTEND_URL: frontendBaseURL,
      CORE_API_INTERNAL_URL: `http://127.0.0.1:${backendPort}`,
      INTERNAL_SERVICE_TOKEN: internalServiceToken
    }
  });
  await run('npm', ['run', 'data:migrate:legacy'], {
    cwd: academicDir,
    env: {
      ...process.env,
      DATABASE_URL: academicDatabaseUrl,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      FRONTEND_URL: frontendBaseURL
    }
  });
  if (enabledServices.engagement) {
    await run('npm', ['run', 'data:migrate:legacy'], {
      cwd: engagementDir,
      env: {
        ...process.env,
        DATABASE_URL: engagementDatabaseUrl,
        JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
        FRONTEND_URL: frontendBaseURL,
        RABBITMQ_URL: 'disabled'
      }
    });
  }
  if (enabledServices.people) {
    await run('npm', ['run', 'data:migrate:legacy'], {
      cwd: peopleDir,
      env: {
        ...process.env,
        DATABASE_URL: peopleDatabaseUrl,
        JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
        FRONTEND_URL: frontendBaseURL,
        AUTH_SERVICE_INTERNAL_URL: `http://127.0.0.1:${authPort}`,
        ACADEMIC_SERVICE_INTERNAL_URL: `http://127.0.0.1:${academicPort}`,
        INTERNAL_SERVICE_TOKEN: internalServiceToken,
        RABBITMQ_URL: 'disabled'
      }
    });
  }
}

async function startApplicationServers() {
  await startManagedProcess('backend', process.execPath, nestStartArgs(backendDir), {
    cwd: backendDir,
    env: {
      ...process.env,
      DATABASE_URL: publicDatabaseUrl,
      PORT: String(backendPort),
      FRONTEND_URL: frontendBaseURL,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      JWT_REFRESH_SECRET:
        process.env.E2E_JWT_REFRESH_SECRET ?? 'e2e-jwt-refresh-secret',
      NODE_ENV: 'test',
      REDIS_URL: 'disabled',
      RABBITMQ_URL: 'disabled',
      INTERNAL_SERVICE_TOKEN: internalServiceToken
    }
  });

  await startManagedProcess('auth-service', process.execPath, nestStartArgs(authDir), {
    cwd: authDir,
    env: {
      ...process.env,
      DATABASE_URL: authDatabaseUrl,
      PORT: String(authPort),
      FRONTEND_URL: frontendBaseURL,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      JWT_REFRESH_SECRET:
        process.env.E2E_JWT_REFRESH_SECRET ?? 'e2e-jwt-refresh-secret',
      NODE_ENV: 'test',
      REDIS_URL: 'disabled',
      RABBITMQ_URL: 'disabled',
      COOKIE_SECURE: 'false',
      INTERNAL_SERVICE_TOKEN: internalServiceToken
    }
  });

  await startManagedProcess(
    'notification-service',
    process.execPath,
    nestStartArgs(notificationDir),
    {
    cwd: notificationDir,
    env: {
      ...process.env,
      DATABASE_URL: notificationsDatabaseUrl,
      PORT: String(notificationPort),
      FRONTEND_URL: frontendBaseURL,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      NODE_ENV: 'test',
      RABBITMQ_URL: 'disabled',
      COOKIE_SECURE: 'false'
    }
  });

  await startManagedProcess('finance-service', process.execPath, nestStartArgs(financeDir), {
    cwd: financeDir,
    env: {
      ...process.env,
      DATABASE_URL: financeDatabaseUrl,
      PORT: String(financePort),
      FRONTEND_URL: frontendBaseURL,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      NODE_ENV: 'test',
      RABBITMQ_URL: 'disabled',
      COOKIE_SECURE: 'false',
      CORE_API_INTERNAL_URL: `http://127.0.0.1:${backendPort}`,
      INTERNAL_SERVICE_TOKEN: internalServiceToken
    }
  });

  await startManagedProcess(
    'academic-service',
    process.execPath,
    nestStartArgs(academicDir),
    {
    cwd: academicDir,
    env: {
      ...process.env,
      DATABASE_URL: academicDatabaseUrl,
      PORT: String(academicPort),
      FRONTEND_URL: frontendBaseURL,
      JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
      NODE_ENV: 'test',
      RABBITMQ_URL: 'disabled',
      COOKIE_SECURE: 'false',
      INTERNAL_SERVICE_TOKEN: internalServiceToken
    }
  });

  if (enabledServices.engagement) {
    await startManagedProcess(
      'engagement-service',
      process.execPath,
      nestStartArgs(engagementDir),
      {
      cwd: engagementDir,
      env: {
        ...process.env,
        DATABASE_URL: engagementDatabaseUrl,
        PORT: String(engagementPort),
        FRONTEND_URL: frontendBaseURL,
        JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
        NODE_ENV: 'test',
        RABBITMQ_URL: 'disabled',
        COOKIE_SECURE: 'false'
      }
    });
  }

  if (enabledServices.people) {
    await startManagedProcess('people-service', process.execPath, nestStartArgs(peopleDir), {
      cwd: peopleDir,
      env: {
        ...process.env,
        DATABASE_URL: peopleDatabaseUrl,
        PORT: String(peoplePort),
        FRONTEND_URL: frontendBaseURL,
        JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
        NODE_ENV: 'test',
        RABBITMQ_URL: 'disabled',
        COOKIE_SECURE: 'false',
        AUTH_SERVICE_INTERNAL_URL: `http://127.0.0.1:${authPort}`,
        ACADEMIC_SERVICE_INTERNAL_URL: `http://127.0.0.1:${academicPort}`,
        INTERNAL_SERVICE_TOKEN: internalServiceToken
      }
    });
  }

  if (enabledServices.analytics) {
    await startManagedProcess(
      'analytics-service',
      process.execPath,
      nestStartArgs(analyticsDir),
      {
      cwd: analyticsDir,
      env: {
        ...process.env,
        DATABASE_URL: analyticsDatabaseUrl,
        PORT: String(analyticsPort),
        FRONTEND_URL: frontendBaseURL,
        JWT_SECRET: process.env.E2E_JWT_SECRET ?? 'e2e-jwt-secret',
        NODE_ENV: 'test',
        RABBITMQ_URL: 'disabled',
        COOKIE_SECURE: 'false'
      }
    });
  }

  await startApiGateway();

  await startManagedProcess(
    'frontend',
    process.execPath,
    frontendDevArgs(),
    {
      cwd: frontendDir,
      env: {
        ...process.env,
        NEXT_PUBLIC_API_URL: apiBaseURL,
        LOCAL_EDGE_ORIGIN: `http://127.0.0.1:${proxyPort}`,
        NODE_OPTIONS: frontendNodeOptions
      }
    }
  );
}

async function startApiGateway() {
  gatewayLogStream = createWriteStream(path.join(logsDir, 'gateway.log'), {
    flags: 'a'
  });

  gatewayServer = createServer(async (req, res) => {
    try {
      const url = req.url ?? '/';
      const targetBase = resolveProxyTarget(url);
      const targetUrl = `${targetBase}${url}`;
      const method = req.method ?? 'GET';
      const headers = { ...req.headers };
      delete headers.host;
      delete headers.connection;

      const response = await fetch(targetUrl, {
        method,
        headers,
        body: ['GET', 'HEAD'].includes(method.toUpperCase()) ? undefined : req,
        duplex: ['GET', 'HEAD'].includes(method.toUpperCase()) ? undefined : 'half',
        redirect: 'manual'
      });

      const outgoingHeaders = Object.fromEntries(response.headers.entries());
      delete outgoingHeaders['transfer-encoding'];
      delete outgoingHeaders['content-encoding'];

      for (const [name, value] of Object.entries(outgoingHeaders)) {
        res.setHeader(name, value);
      }

      const setCookies = response.headers.getSetCookie?.() ?? [];
      if (setCookies.length > 0) {
        res.setHeader('set-cookie', setCookies);
      }

      res.statusCode = response.status;
      const buffer = Buffer.from(await response.arrayBuffer());
      res.end(buffer);
    } catch (error) {
      gatewayLogStream.write(
        `[gateway] ${req.method ?? 'GET'} ${req.url ?? '/'} -> ${
          error instanceof Error ? error.stack ?? error.message : String(error)
        }\n`
      );
      res.statusCode = 502;
      res.setHeader('content-type', 'application/json');
      res.end(JSON.stringify({ message: 'Gateway error' }));
    }
  });

  await new Promise((resolve, reject) => {
    gatewayServer.once('error', reject);
    gatewayServer.listen(proxyPort, '127.0.0.1', resolve);
  });
}

function resolveProxyTarget(url) {
  if (
    url.startsWith('/api/v1/auth') ||
    url.startsWith('/api/v1/users') ||
    url.startsWith('/api/v1/roles') ||
    url.startsWith('/api/v1/permissions') ||
    url.startsWith('/auth') ||
    url.startsWith('/users') ||
    url.startsWith('/roles') ||
    url.startsWith('/permissions')
  ) {
    return `http://127.0.0.1:${authPort}`;
  }

  if (url.startsWith('/api/v1/analytics') || url.startsWith('/analytics')) {
    if (!enabledServices.analytics) {
      return `http://127.0.0.1:${backendPort}`;
    }
    return `http://127.0.0.1:${analyticsPort}`;
  }

  if (url.startsWith('/api/v1/finance')) {
    return `http://127.0.0.1:${financePort}`;
  }

  if (
    url.startsWith('/api/v1/students') ||
    url.startsWith('/api/v1/lecturers') ||
    url.startsWith('/students') ||
    url.startsWith('/lecturers')
  ) {
    if (!enabledServices.people) {
      return `http://127.0.0.1:${backendPort}`;
    }
    return `http://127.0.0.1:${peoplePort}`;
  }

  if (
    url.startsWith('/api/v1/announcements') ||
    url.startsWith('/api/v1/support-tickets') ||
    url.startsWith('/announcements') ||
    url.startsWith('/support-tickets')
  ) {
    if (!enabledServices.engagement) {
      return `http://127.0.0.1:${backendPort}`;
    }
    return `http://127.0.0.1:${engagementPort}`;
  }

  if (academicApiPrefixes.some((prefix) => url.startsWith(prefix))) {
    return `http://127.0.0.1:${academicPort}`;
  }

  if (url.startsWith('/api/v1/notifications') || url.startsWith('/socket.io/')) {
    return `http://127.0.0.1:${notificationPort}`;
  }

  return `http://127.0.0.1:${backendPort}`;
}

async function startManagedProcess(name, command, args, options) {
  const logStream = createWriteStream(path.join(logsDir, `${name}.log`), {
    flags: 'a'
  });
  const resolvedCommand = resolveCommand(command);
  const spawnCommand =
    process.platform === 'win32' && shouldUseCmdWrapper(resolvedCommand)
      ? 'cmd.exe'
      : resolvedCommand;
  const spawnArgs =
    spawnCommand === 'cmd.exe'
      ? ['/d', '/s', '/c', buildWindowsCommand(resolvedCommand, args)]
      : args;

  const child = spawn(spawnCommand, spawnArgs, {
    cwd: options.cwd,
    env: options.env,
    stdio: ['ignore', 'pipe', 'pipe'],
    shell: false,
    detached: process.platform !== 'win32'
  });

  child.stdout.pipe(logStream, { end: false });
  child.stderr.pipe(logStream, { end: false });
  child.once('exit', (code, signal) => {
    logStream.write(
      `\n[${name}] exited with code ${code ?? 'null'} signal ${signal ?? 'null'}\n`
    );
  });

  managedProcesses.push({ name, child, logStream });

  await new Promise((resolve, reject) => {
    child.once('spawn', resolve);
    child.once('error', reject);
  });
}

async function stopApplicationServers() {
  if (gatewayServer) {
    await new Promise((resolve) => gatewayServer.close(resolve));
    gatewayServer = undefined;
  }

  if (gatewayLogStream) {
    await new Promise((resolve) => gatewayLogStream.end(resolve));
    gatewayLogStream = undefined;
  }

  for (const processInfo of managedProcesses.reverse()) {
    const { child, logStream } = processInfo;

    if (child.exitCode === null && child.signalCode === null) {
      await terminateManagedProcess(child);
    }

    await new Promise((resolve) => logStream.end(resolve));
  }

  managedProcesses.length = 0;
}

function waitForExit(child, timeoutMs) {
  return new Promise((resolve) => {
    if (child.exitCode !== null || child.signalCode !== null) {
      resolve();
      return;
    }

    const timeoutId = setTimeout(resolve, timeoutMs);
    child.once('exit', () => {
      clearTimeout(timeoutId);
      resolve();
    });
  });
}

async function terminateManagedProcess(child) {
  if (process.platform === 'win32') {
    killProcess(child.pid);
    await waitForExit(child, 5_000);

    if (child.exitCode === null && child.signalCode === null) {
      killProcess(child.pid);
      await waitForExit(child, 5_000);
    }

    return;
  }

  signalUnixProcessTree(child.pid, 'SIGTERM');
  await waitForExit(child, 5_000);

  if (child.exitCode === null && child.signalCode === null) {
    signalUnixProcessTree(child.pid, 'SIGKILL');
    await waitForExit(child, 5_000);
  }
}

async function assertDockerAvailable() {
  try {
    await run('docker', ['info'], {
      captureOutput: true
    });
  } catch (error) {
    const message =
      error instanceof Error ? error.message : String(error);

    throw new Error(
      [
        '[fast-e2e] Docker daemon is not reachable.',
        '[fast-e2e] Start Docker Desktop/Linux engine before running the fast E2E lane.',
        message
      ].join('\n')
    );
  }
}

async function assertPortsAvailable() {
  const ports = [
    postgresPort,
    backendPort,
    authPort,
    notificationPort,
    financePort,
    academicPort,
    engagementPort,
    peoplePort,
    analyticsPort,
    proxyPort,
    frontendPort
  ];

  for (const port of ports) {
    await assertPortAvailable(port);
  }
}

async function assertPortAvailable(port) {
  await new Promise((resolve, reject) => {
    const server = createNetServer();

    server.once('error', (error) => {
      reject(
        new Error(
          `[fast-e2e] Required local port ${port} is already in use. Free the port or override the E2E_* port env before rerunning the lane. ${error instanceof Error ? error.message : String(error)}`
        )
      );
    });

    server.listen(port, () => {
      server.close((closeError) => {
        if (closeError) {
          reject(closeError);
          return;
        }

        resolve();
      });
    });
  });
}

function killProcess(pid) {
  if (!pid) {
    return;
  }

  try {
    process.kill(pid, 'SIGKILL');
  } catch (error) {
    if (error && typeof error === 'object' && 'code' in error && error.code === 'ESRCH') {
      return;
    }

    throw error;
  }
}

async function cleanupManagedPortListeners() {
  if (process.platform !== 'win32') {
    return;
  }

  const managedPorts = new Set([
    backendPort,
    authPort,
    notificationPort,
    financePort,
    academicPort,
    engagementPort,
    peoplePort,
    analyticsPort,
    proxyPort,
    Number(new URL(frontendBaseURL).port || '80')
  ]);
  const output = await run('netstat', ['-ano'], {
    allowFailure: true,
    captureOutput: true
  });
  const listenerPids = new Set();

  for (const line of (output ?? '').split(/\r?\n/u)) {
    if (!/\bLISTENING\b/u.test(line)) {
      continue;
    }

    const parts = line.trim().split(/\s+/u);
    const localAddress = parts[1] ?? '';
    const pid = Number(parts[parts.length - 1]);
    const portMatch = localAddress.match(/:(\d+)$/u);

    if (!portMatch || Number.isNaN(pid) || pid <= 0) {
      continue;
    }

    if (managedPorts.has(Number(portMatch[1]))) {
      listenerPids.add(pid);
    }
  }

  for (const pid of listenerPids) {
    if (pid !== process.pid) {
      killProcess(pid);
    }
  }
}

function signalUnixProcessTree(pid, signal) {
  try {
    process.kill(-pid, signal);
  } catch (error) {
    if (error && typeof error === 'object' && 'code' in error && error.code === 'ESRCH') {
      return;
    }

    throw error;
  }
}

async function startPostgres() {
  await run('docker', [
    'run',
    '-d',
    '--name',
    postgresContainer,
    '--health-cmd',
    `pg_isready -U ${postgresUser} -d ${postgresDatabase}`,
    '--health-interval',
    '5s',
    '--health-timeout',
    '5s',
    '--health-retries',
    '20',
    '-e',
    `POSTGRES_USER=${postgresUser}`,
    '-e',
    `POSTGRES_PASSWORD=${postgresPassword}`,
    '-e',
    `POSTGRES_DB=${postgresDatabase}`,
    '-p',
    `${postgresPort}:5432`,
    'postgres:15-alpine'
  ]);
}

async function waitForPostgres(timeoutMs = 180_000, intervalMs = 2_000) {
  const deadline = Date.now() + timeoutMs;

  while (Date.now() < deadline) {
    const status = await inspectPostgresHealth();
    if (status === 'healthy') {
      return;
    }

    await delay(intervalMs);
  }

  throw new Error(
    `Timed out waiting for postgres container ${postgresContainer} to become healthy`
  );
}

async function inspectPostgresHealth() {
  const output = await run(
    'docker',
    [
      'inspect',
      '-f',
      '{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}',
      postgresContainer
    ],
    {
      allowFailure: true,
      captureOutput: true
    }
  );

  return output?.trim() ?? 'unknown';
}

async function cleanupPostgres() {
  await run('docker', ['rm', '-f', postgresContainer], {
    allowFailure: true,
    captureOutput: true
  });
}

async function waitForResponse(
  url,
  predicate,
  options = { timeoutMs: 120_000, intervalMs: 1_000 }
) {
  const timeoutMs = options.timeoutMs ?? 120_000;
  const intervalMs = options.intervalMs ?? 1_000;
  const requestTimeoutMs = options.requestTimeoutMs ?? Math.min(15_000, timeoutMs);
  const deadline = Date.now() + timeoutMs;
  let lastError = null;

  while (Date.now() < deadline) {
    try {
      const response = await fetch(url, {
        headers: { Connection: 'close' },
        signal: AbortSignal.timeout(requestTimeoutMs)
      });

      if (predicate(null, response)) {
        return;
      }

      lastError = new Error(
        `Received unexpected response from ${url}: ${response.status}`
      );
    } catch (error) {
      lastError = error;
    }

    await delay(intervalMs);
  }

  throw lastError ?? new Error(`Timed out waiting for ${url}`);
}

async function collectArtifacts() {
  const postgresLog = await run('docker', ['logs', postgresContainer], {
    allowFailure: true,
    captureOutput: true
  });

  await writeFile(path.join(logsDir, 'postgres.log'), postgresLog ?? '', 'utf8');
}

function run(command, args, options = {}) {
  const {
    cwd = repoRoot,
    env = process.env,
    allowFailure = false,
    captureOutput = false
  } = options;

  return new Promise((resolve, reject) => {
    const resolvedCommand = resolveCommand(command);
    const spawnCommand =
      process.platform === 'win32' && shouldUseCmdWrapper(resolvedCommand)
        ? 'cmd.exe'
        : resolvedCommand;
    const spawnArgs =
      spawnCommand === 'cmd.exe'
        ? ['/d', '/s', '/c', buildWindowsCommand(resolvedCommand, args)]
        : args;

    const child = spawn(spawnCommand, spawnArgs, {
      cwd,
      env,
      stdio: captureOutput ? ['ignore', 'pipe', 'pipe'] : 'inherit',
      shell: false
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

function resolveCommand(command) {
  if (process.platform === 'win32' && command === 'npm') {
    return 'npm.cmd';
  }

  return command;
}

function shouldUseCmdWrapper(command) {
  return /\.cmd$/iu.test(command) || /\.bat$/iu.test(command);
}

function buildWindowsCommand(command, args) {
  return [command, ...args].map((part) => quoteWindowsArg(part)).join(' ');
}

function quoteWindowsArg(value) {
  if (!/[\s"]/u.test(value)) {
    return value;
  }

  return `"${value.replace(/"/gu, '\\"')}"`;
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
