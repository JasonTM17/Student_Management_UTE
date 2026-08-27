import { execFileSync } from 'node:child_process';
import net from 'node:net';
import { spawn } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const repoRoot = path.resolve(__dirname, '..');
const frontendDir = path.join(repoRoot, 'frontend');
const composeFile = path.join(repoRoot, 'docker-compose.e2e.yml');
const playwrightCli = path.join(frontendDir, 'node_modules', 'playwright', 'cli.js');
const projectName = resolveProjectName();
const apiBaseURL = process.env.E2E_API_URL ?? 'http://127.0.0.1:4100/api/v1';
const frontendBaseURL = process.env.E2E_BASE_URL ?? 'http://127.0.0.1:3101';
const frontendPort = new URL(frontendBaseURL).port || '3101';

async function main() {
  assertDisposableProject(projectName);
  await assertPortAvailable(new URL(apiBaseURL).port || '4100', 'API');
  await assertPortAvailable(process.env.POSTGRES_HOST_PORT ?? '5433', 'PostgreSQL');
  await assertPortAvailable(new URL(frontendBaseURL).port || '3101', 'Frontend');
  const frontend = await startFrontend();
  let stackStarted = false;
  try {
    // Mark the exact, preflighted project before `up`: Compose can create a
    // subset of resources before reporting a build failure.
    stackStarted = true;
    await compose(['up', '-d', '--build', 'postgres', 'restful-api']);
    await waitForResponse(`${apiBaseURL}/health/liveness`, (_, response) => response.ok);
    await waitForResponse(frontendBaseURL, (_, response) => response.ok, {
      parseJson: false,
    });

    const args = ['test'];
    const filter = process.env.E2E_PLAYWRIGHT_FILTER;
    if (filter) {
      args.push('-g', filter);
    }

    await run(process.execPath, [playwrightCli, ...args], {
      cwd: frontendDir,
      env: {
        ...process.env,
        E2E_EXTERNAL_STACK: '1',
        E2E_API_URL: apiBaseURL,
        E2E_BASE_URL: frontendBaseURL,
      },
    });
  } finally {
    await stopProcess(frontend);
    if (stackStarted) {
      // The project name was preflighted as disposable and is unique to this
      // invocation; no developer/default Compose project can be touched.
      await compose(['down', '-v', '--remove-orphans'], { allowFailure: true });
    }
  }
}

function resolveProjectName() {
  const supplied = process.env.E2E_COMPOSE_PROJECT;
  const generated = `campuscore-course-e2e-${process.pid}-${Date.now()}`;
  const value = supplied?.trim() || generated;
  if (!/^campuscore-course-e2e-[a-z0-9-]+$/i.test(value)) {
    throw new Error('E2E_COMPOSE_PROJECT must be a disposable campuscore-course-e2e-* name');
  }
  return value;
}

function assertDisposableProject(project) {
  const existingContainers = dockerQuery(['ps', '-aq', '--filter', `label=com.docker.compose.project=${project}`]);
  const existingVolumes = dockerQuery(['volume', 'ls', '-q', '--filter', `label=com.docker.compose.project=${project}`]);
  if (existingContainers || existingVolumes) {
    throw new Error(`Refusing E2E project collision for disposable project ${project}`);
  }
}

function dockerQuery(args) {
  try {
    return execFileSync('docker', args, { cwd: repoRoot, encoding: 'utf8' }).trim();
  } catch (error) {
    throw new Error(`Docker preflight failed; no E2E cleanup was attempted (${error instanceof Error ? error.name : 'unknown'})`);
  }
}

async function assertPortAvailable(portValue, label) {
  const port = Number(portValue);
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`${label} port is invalid; refusing E2E startup`);
  }
  // An occupied host port is a hard collision, not a reason to stop or
  // reconfigure another developer's service.
  const available = await new Promise((resolve) => {
    const socket = new net.Socket();
    const finish = (value) => { socket.destroy(); resolve(value); };
    socket.setTimeout(250);
    socket.once('connect', () => finish(false));
    socket.once('error', () => finish(true));
    socket.once('timeout', () => finish(true));
    socket.connect(port, '127.0.0.1');
  });
  if (!available) throw new Error(`${label} port ${port} is already in use; refusing E2E collision`);
}

async function startFrontend() {
  const onWindows = process.platform === 'win32';
  const command = onWindows ? (process.env.ComSpec || 'cmd.exe') : 'npm';
  const args = onWindows
    ? ['/d', '/s', '/c', 'npm.cmd', 'run', 'dev', '--', '--hostname', '127.0.0.1', '--port', frontendPort]
    : ['run', 'dev', '--', '--hostname', '127.0.0.1', '--port', frontendPort];
  return spawn(
    command,
    args,
    {
    cwd: frontendDir,
    env: {
      ...process.env,
      // Keep browser requests same-origin so the Next proxy forwards the
      // session/CSRF cookies to the isolated API.  A direct cross-origin
      // browser URL would bypass the proxy and make authenticated evidence
      // depend on an unconfigured CORS policy.
      NEXT_PUBLIC_API_URL: '/api/v1',
      JAVA_API_ORIGIN: new URL(apiBaseURL).origin,
    },
    stdio: 'inherit',
    shell: false,
    },
  );
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

      lastError = new Error(`Received unexpected response from ${url}: ${response.status}`);
    } catch (error) {
      lastError = error;
    }

    await delay(intervalMs);
  }

  throw lastError ?? new Error(`Timed out waiting for ${url}`);
}

async function compose(args, options = {}) {
  return run('docker', ['compose', '-p', projectName, '-f', composeFile, ...args], {
    cwd: repoRoot,
    env: {
      ...process.env,
      E2E_API_HOST_PORT: new URL(apiBaseURL).port || '4100',
      POSTGRES_HOST_PORT: process.env.POSTGRES_HOST_PORT ?? '5433',
    },
    allowFailure: options.allowFailure ?? false,
  });
}

async function stopProcess(child) {
  if (!child || child.exitCode !== null || child.signalCode !== null) {
    return;
  }

  if (process.platform === 'win32' && child.pid) {
    // npm.cmd/cmd.exe can leave the Next child alive after SIGTERM. Kill only
    // this exact process tree so a failed run cannot leave port 3101 occupied;
    // the runner never broadens this to a name- or port-based kill.
    try {
      execFileSync('taskkill', ['/PID', String(child.pid), '/T', '/F'], {
        stdio: 'ignore',
      });
    } catch {
      // The process may have exited between the status check and taskkill.
    }
  } else {
  child.kill('SIGTERM');
  }
  await new Promise((resolve) => {
    if (child.exitCode !== null || child.signalCode !== null) {
      resolve();
      return;
    }
    child.once('close', resolve);
  });
}

async function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function run(command, args, options = {}) {
  const { cwd = repoRoot, env = process.env, allowFailure = false } = options;

  return new Promise((resolve, reject) => {
    const child = spawn(command, args, {
      cwd,
      env,
      stdio: 'inherit',
      shell: false,
    });

    child.on('error', reject);
    child.on('close', (code) => {
      if (code === 0 || allowFailure) {
        resolve();
        return;
      }

      reject(new Error(`${command} ${args.join(' ')} exited with code ${code}`));
    });
  });
}

await main();
