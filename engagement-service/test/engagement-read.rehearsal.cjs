'use strict';

const assert = require('node:assert/strict');
const { execFileSync, spawn } = require('node:child_process');
const { createHash, createHmac } = require('node:crypto');
const { once } = require('node:events');
const fs = require('node:fs');
const path = require('node:path');
const { isDeepStrictEqual } = require('node:util');

const EXPECTED_META_LIMIT = 20;

async function main() {
  const runRoot = assertSafeEnvironment();
  const repoRoot = path.resolve(__dirname, '../..');
  const sourceHead = requireCleanCheckout(repoRoot);
  const corpus = buildCorpus();
  const referenceFile = resolveReferenceFile(runRoot);
  const ownedDifferential = process.env.RUN_OWNED_JAVA_DIFFERENTIAL === 'true';
  if (process.env.CAPTURE_JAVA_REFERENCE === 'true') {
    throw new Error(
      'CAPTURE_JAVA_REFERENCE is retired; use RUN_OWNED_JAVA_DIFFERENTIAL',
    );
  }
  if (referenceFile && !ownedDifferential) {
    throw new Error('External Java reference comparison is not permitted');
  }
  if (!ownedDifferential) {
    throw new Error('RUN_OWNED_JAVA_DIFFERENTIAL must be true');
  }

  let reference = null;
  let buildIdentity = null;
  if (ownedDifferential) {
    const javaBaseUrl = requireLoopbackJavaBaseUrl();
    const javaExecutable = requireExecutable(
      'REHEARSAL_JAVA_EXECUTABLE',
      'java.exe',
    );
    buildIdentity = rebuildJavaArtifact(
      repoRoot,
      runRoot,
      sourceHead,
      javaExecutable,
    );
    const ownedJava = await startOwnedJava(
      runRoot,
      sourceHead,
      buildIdentity,
      javaExecutable,
    );
    try {
      await waitForJavaReady(javaBaseUrl, ownedJava.child);
      const javaIdentity = requireOwnedJavaIdentity(
        repoRoot,
        buildIdentity,
        ownedJava,
      );
      reference = await captureJavaReference(
        javaBaseUrl,
        corpus,
        sourceHead,
        javaIdentity,
      );
      assert.equal(
        fileSha256(buildIdentity.artifactFile),
        buildIdentity.artifactSha256,
        'Java artifact must not change during capture',
      );
    } finally {
      await stopOwnedJava(ownedJava.child);
    }
    reference.javaLogSha256 = fileSha256(ownedJava.javaLog);
    fs.mkdirSync(path.dirname(referenceFile), { recursive: true });
    fs.writeFileSync(referenceFile, `${JSON.stringify(reference, null, 2)}\n`, {
      encoding: 'utf8',
      flag: 'wx',
    });
  }

  const legacyIdentity = buildLegacyIdentity(
    repoRoot,
    runRoot,
    sourceHead,
    buildIdentity,
  );
  const cases = await runLegacyProbe(corpus, reference, legacyIdentity.moduleRoot);
  const report = {
    status: 'PASS',
    mode: 'owned-java-sequential-differential',
    referenceSourceHead: reference?.sourceHead ?? null,
    referenceArtifactSha256: referenceFile ? fileSha256(referenceFile) : null,
    javaArtifactPath: reference?.artifactPath ?? null,
    javaArtifactSha256: reference?.artifactSha256 ?? null,
    javaSourceArchiveSha256: reference?.sourceArchiveSha256 ?? null,
    tarExecutableSha256: reference?.tarExecutableSha256 ?? null,
    powershellExecutableSha256:
      reference?.powershellExecutableSha256 ?? null,
    javaBuildLogSha256: reference?.buildLogSha256 ?? null,
    cmdExecutableSha256: reference?.cmdExecutableSha256 ?? null,
    gitExecutableSha256: reference?.gitExecutableSha256 ?? null,
    mavenExecutableSha256: reference?.mavenExecutableSha256 ?? null,
    javaExecutableSha256: reference?.javaExecutableSha256 ?? null,
    javaLogSha256: reference?.javaLogSha256 ?? null,
    javaProcessId: reference?.javaProcessId ?? null,
    legacySourceHead: legacyIdentity.sourceHead,
    legacyArtifactPath: legacyIdentity.artifactPath,
    legacyArtifactSha256: legacyIdentity.artifactSha256,
    legacyBuildLogSha256: legacyIdentity.buildLogSha256,
    legacyInstallLogSha256: legacyIdentity.installLogSha256,
    legacyNpmConfigSha256: legacyIdentity.npmConfigSha256,
    legacyPackageLockSha256: legacyIdentity.packageLockSha256,
    legacyDependencyManifestSha256:
      legacyIdentity.dependencyManifestSha256,
    legacyDependencyFiles: legacyIdentity.dependencyFiles,
    nodeExecutableSha256: legacyIdentity.nodeExecutableSha256,
    npmCliSha256: legacyIdentity.npmCliSha256,
    typescriptCompilerSha256: legacyIdentity.typescriptCompilerSha256,
    cases,
  };
  writeOptionalReport(report, runRoot);
  process.stdout.write(`${JSON.stringify(report, null, 2)}\n`);
}

function assertSafeEnvironment() {
  if (process.env.RUN_PHASE11_ENGAGEMENT_REHEARSAL !== 'true') {
    throw new Error('RUN_PHASE11_ENGAGEMENT_REHEARSAL must be true');
  }
  if (process.env.NODE_ENV !== 'test') {
    throw new Error('NODE_ENV must be test so the service cannot load .env');
  }
  if (process.env.RABBITMQ_URL?.trim()) {
    throw new Error('RABBITMQ_URL must be absent for the read-only rehearsal');
  }
  delete process.env.RABBITMQ_URL;
  assertDatabaseUrl();
  if (!process.env.JWT_SECRET || process.env.JWT_SECRET.length < 32) {
    throw new Error('JWT_SECRET must contain at least 32 characters');
  }
  const runRoot = requireRehearsalRunRoot();
  assertSafeTemporaryDirectory(runRoot);
  return runRoot;
}

function assertDatabaseUrl() {
  let databaseUrl;
  try {
    databaseUrl = new URL(process.env.DATABASE_URL);
  } catch {
    throw new Error('DATABASE_URL must be a valid PostgreSQL URL');
  }
  const expected = {
    protocol: 'postgresql:',
    hostname: '127.0.0.1',
    port: '56432',
    pathname: '/engagement_rehearsal',
    username: 'engagement_reader',
    schema: 'engagement',
  };
  for (const [field, value] of Object.entries(expected)) {
    const actual =
      field === 'schema' ? databaseUrl.searchParams.get('schema') : databaseUrl[field];
    if (actual !== value) {
      throw new Error(`DATABASE_URL ${field} must be ${value}`);
    }
  }
  if (databaseUrl.password) {
    throw new Error('Disposable trust-auth rehearsal URL must not contain a password');
  }
}

function requireRehearsalRunRoot() {
  const configured = process.env.REHEARSAL_RUN_ROOT;
  if (!configured) {
    throw new Error('REHEARSAL_RUN_ROOT is required');
  }
  const runRoot = path.resolve(configured);
  const physicalRunRoot = fs.realpathSync.native(runRoot);
  if (path.parse(physicalRunRoot).root.toUpperCase() !== 'D:\\') {
    throw new Error('REHEARSAL_RUN_ROOT must be on D:');
  }
  assert.equal(
    physicalRunRoot.toLowerCase(),
    runRoot.toLowerCase(),
    'REHEARSAL_RUN_ROOT must not traverse a junction or symbolic link',
  );
  if (!path.basename(runRoot).toLowerCase().startsWith('phase11-engagement-')) {
    throw new Error('REHEARSAL_RUN_ROOT must identify the Phase 11 rehearsal');
  }
  const pgData = path.join(runRoot, 'pgdata');
  assert.equal(
    fs.realpathSync.native(pgData).toLowerCase(),
    pgData.toLowerCase(),
    'PostgreSQL data root must not traverse a junction or symbolic link',
  );
  const lines = fs
    .readFileSync(path.join(pgData, 'postmaster.pid'), 'utf8')
    .split(/\r?\n/);
  assert.equal(path.resolve(lines[1]), path.resolve(pgData), 'PostgreSQL data root');
  assert.equal(lines[3], '56432', 'PostgreSQL rehearsal port');
  assert.equal(lines[5], '127.0.0.1', 'PostgreSQL listen address');
  assert.equal(lines[7], 'ready   ', 'PostgreSQL readiness marker');
  return runRoot;
}

function assertSafeTemporaryDirectory(runRoot) {
  for (const key of ['TEMP', 'TMP']) {
    const configured = process.env[key];
    if (!configured) {
      throw new Error(`${key} is required`);
    }
    const resolved = path.resolve(configured);
    const physical = fs.realpathSync.native(resolved);
    assert.equal(
      physical.toLowerCase(),
      resolved.toLowerCase(),
      `${key} must not traverse a junction or symbolic link`,
    );
    if (!isInside(runRoot, physical)) {
      throw new Error(`${key} must stay inside REHEARSAL_RUN_ROOT`);
    }
  }
}

function requireCleanCheckout(repoRoot) {
  const trackedStatus = runGit(repoRoot, [
    'status',
    '--porcelain',
    '--untracked-files=no',
  ]);
  assert.equal(trackedStatus.trim(), '', 'tracked checkout must be clean');
  const sourceHead = runGit(repoRoot, ['rev-parse', 'HEAD']).trim();
  assert.match(sourceHead, /^[0-9a-f]{40}$/i, 'actual Git HEAD');
  return sourceHead.toLowerCase();
}

function runGit(repoRoot, args) {
  const gitExecutable = requireExecutable(
    'REHEARSAL_GIT_EXECUTABLE',
    'git.exe',
  );
  return execFileSync(gitExecutable, args, {
    cwd: repoRoot,
    encoding: 'utf8',
    env: buildChildEnvironment({ GIT_CONFIG_NOSYSTEM: '1' }),
    windowsHide: true,
  });
}

function rebuildJavaArtifact(repoRoot, runRoot, sourceHead, javaExecutable) {
  const mavenExecutable = requireExecutable(
    'REHEARSAL_MAVEN_EXECUTABLE',
    'mvn.cmd',
  );
  const mavenRepository = requireDirectory('REHEARSAL_MAVEN_REPOSITORY');
  const sourceArchive = path.resolve(
    runRoot,
    `java-source-${sourceHead.slice(0, 12)}.tar`,
  );
  const sourceSnapshot = path.resolve(
    runRoot,
    `java-source-${sourceHead.slice(0, 12)}`,
  );
  if (fs.existsSync(sourceArchive) || fs.existsSync(sourceSnapshot)) {
    throw new Error('Isolated Java source snapshot must not already exist');
  }
  runGit(repoRoot, [
    'archive',
    '--format=tar',
    '-o',
    sourceArchive,
    sourceHead,
    'java-services/restful-api',
    'engagement-service',
    'packages/platform-auth',
  ]);
  fs.mkdirSync(sourceSnapshot, { recursive: false });
  const tarExecutable = requireExecutable(
    'REHEARSAL_TAR_EXECUTABLE',
    'tar.exe',
  );
  const cmdExecutable = requireExecutable(
    'REHEARSAL_CMD_EXECUTABLE',
    'cmd.exe',
  );
  execFileSync(tarExecutable, ['-xf', sourceArchive, '-C', sourceSnapshot], {
    env: buildChildEnvironment({}),
    windowsHide: true,
  });
  const moduleRoot = path.resolve(
    sourceSnapshot,
    'java-services/restful-api',
  );
  assert.ok(fs.existsSync(path.join(moduleRoot, 'pom.xml')), 'archived Java pom');
  assert.ok(!fs.existsSync(path.join(sourceSnapshot, '.git')), 'archive has no Git metadata');
  const settingsFile = path.resolve(
    sourceSnapshot,
    'rehearsal-maven-settings.xml',
  );
  fs.writeFileSync(
    settingsFile,
    [
      '<settings xmlns="http://maven.apache.org/SETTINGS/1.2.0">',
      `  <localRepository>${escapeXml(mavenRepository)}</localRepository>`,
      '  <offline>true</offline>',
      '</settings>',
      '',
    ].join('\n'),
    { encoding: 'utf8', flag: 'wx' },
  );
  const mavenCommand =
    `call ${quoteCmdArgument(mavenExecutable)} -o ` +
    `-s ${quoteCmdArgument(settingsFile)} ` +
    `-gs ${quoteCmdArgument(settingsFile)} -DskipTests clean package`;
  const buildOutput = execFileSync(
    cmdExecutable,
    ['/d', '/s', '/c', mavenCommand],
    {
      cwd: moduleRoot,
      encoding: 'utf8',
      env: buildChildEnvironment({
        ComSpec: cmdExecutable,
        JAVA_HOME: path.dirname(path.dirname(javaExecutable)),
        MAVEN_OPTS: '-Xms32m -Xmx192m -XX:MaxMetaspaceSize=160m',
        MAVEN_SKIP_RC: 'true',
      }),
      maxBuffer: 20 * 1024 * 1024,
      windowsHide: true,
      windowsVerbatimArguments: true,
    },
  );
  assert.equal(
    requireCleanCheckout(repoRoot),
    sourceHead,
    'source HEAD after owned clean build',
  );
  const buildLog = path.resolve(
    runRoot,
    `java-build-${sourceHead.slice(0, 12)}.log`,
  );
  fs.writeFileSync(buildLog, buildOutput, { encoding: 'utf8', flag: 'wx' });
  const artifactFile = path.resolve(
    sourceSnapshot,
    'java-services/restful-api/target/campuscore-restful-api-0.1.0-SNAPSHOT.jar',
  );
  return {
    artifactFile,
    artifactPath: path.relative(runRoot, artifactFile).replaceAll('\\', '/'),
    artifactSha256: fileSha256(artifactFile),
    buildLogSha256: fileSha256(buildLog),
    mavenExecutableSha256: fileSha256(mavenExecutable),
    cmdExecutableSha256: fileSha256(cmdExecutable),
    gitExecutableSha256: fileSha256(
      requireExecutable('REHEARSAL_GIT_EXECUTABLE', 'git.exe'),
    ),
    sourceArchiveSha256: fileSha256(sourceArchive),
    tarExecutableSha256: fileSha256(tarExecutable),
    powershellExecutableSha256: fileSha256(
      requireExecutable('REHEARSAL_POWERSHELL_EXECUTABLE', 'powershell.exe'),
    ),
    cmdExecutable,
    sourceSnapshot,
  };
}

function requireExecutable(environmentKey, expectedName) {
  const configured = process.env[environmentKey];
  if (!configured) {
    throw new Error(`${environmentKey} is required`);
  }
  const executable = fs.realpathSync(path.resolve(configured));
  assert.equal(
    path.basename(executable).toLowerCase(),
    expectedName,
    `${environmentKey} executable name`,
  );
  return executable;
}

function requireDirectory(environmentKey) {
  const configured = process.env[environmentKey];
  if (!configured) {
    throw new Error(`${environmentKey} is required`);
  }
  const directory = fs.realpathSync(path.resolve(configured));
  assert.ok(fs.statSync(directory).isDirectory(), `${environmentKey} directory`);
  return directory;
}

function quoteCmdArgument(value) {
  if (/["&|<>^%!]/.test(value)) {
    throw new Error('Maven command path contains an unsupported cmd metacharacter');
  }
  return `"${value}"`;
}

function escapeXml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&apos;');
}

function buildChildEnvironment(overrides) {
  const environment = {};
  for (const key of ['SystemRoot', 'WINDIR', 'TEMP', 'TMP']) {
    const inheritedKey = Object.keys(process.env).find(
      (candidate) => candidate.toLowerCase() === key.toLowerCase(),
    );
    if (inheritedKey && process.env[inheritedKey]) {
      environment[key] = process.env[inheritedKey];
    }
  }
  return { ...environment, ...overrides };
}

async function startOwnedJava(
  runRoot,
  sourceHead,
  buildIdentity,
  javaExecutable,
) {
  const javaArgs = [
    '-Xms32m',
    '-Xmx192m',
    '-XX:MaxMetaspaceSize=160m',
    '-jar',
    buildIdentity.artifactFile,
  ];
  const javaLog = path.resolve(
    runRoot,
    `java-owned-${sourceHead.slice(0, 12)}.log`,
  );
  const logHandle = fs.openSync(javaLog, 'wx');
  const child = spawn(javaExecutable, javaArgs, {
    cwd: runRoot,
    env: buildChildEnvironment({
      SERVER_PORT: '56410',
      SERVER_ADDRESS: '127.0.0.1',
      SPRING_PROFILES_ACTIVE: 'persistence',
      SPRING_DATASOURCE_URL:
        `jdbc:postgresql://127.0.0.1:56432/engagement_rehearsal` +
        `?currentSchema=thesis&ApplicationName=java-engagement-${sourceHead.slice(0, 7)}`,
      SPRING_DATASOURCE_USERNAME: 'engagement_reader',
      SPRING_DATASOURCE_PASSWORD: '',
      ENGAGEMENT_READ_ENABLED: 'true',
      FLYWAY_ENABLED: 'false',
      JWT_SECRET: process.env.JWT_SECRET,
    }),
    stdio: ['ignore', logHandle, logHandle],
    windowsHide: true,
  });
  try {
    await Promise.race([
      once(child, 'spawn'),
      once(child, 'error').then(([error]) => Promise.reject(error)),
    ]);
  } finally {
    fs.closeSync(logHandle);
  }
  return {
    child,
    javaArgs,
    javaExecutableSha256: fileSha256(javaExecutable),
    javaLog,
  };
}

async function waitForJavaReady(javaBaseUrl, child) {
  for (let attempt = 0; attempt < 60; attempt += 1) {
    if (child.exitCode !== null) {
      throw new Error(`Owned Java process exited with code ${child.exitCode}`);
    }
    try {
      const response = await fetch(`${javaBaseUrl}/actuator/health`, {
        redirect: 'error',
        signal: AbortSignal.timeout(1_000),
      });
      if (response.ok) {
        return;
      }
    } catch {
      // Startup is bounded below; failed probes are retried until the deadline.
    }
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
  throw new Error('Owned Java process did not become ready within 30 seconds');
}

function requireOwnedJavaIdentity(repoRoot, buildIdentity, ownedJava) {
  const listener = readJavaListener();
  assert.equal(listener.localAddress, '127.0.0.1', 'Java listen address');
  assert.equal(
    listener.processId,
    ownedJava.child.pid,
    'Java listener must belong to the process spawned by this probe',
  );
  assert.deepEqual(
    ownedJava.javaArgs.slice(-2),
    ['-jar', buildIdentity.artifactFile],
    'owned Java launch artifact argv',
  );
  return {
    artifactPath: buildIdentity.artifactPath,
    artifactSha256: buildIdentity.artifactSha256,
    sourceArchiveSha256: buildIdentity.sourceArchiveSha256,
    tarExecutableSha256: buildIdentity.tarExecutableSha256,
    powershellExecutableSha256:
      buildIdentity.powershellExecutableSha256,
    buildLogSha256: buildIdentity.buildLogSha256,
    cmdExecutableSha256: buildIdentity.cmdExecutableSha256,
    gitExecutableSha256: buildIdentity.gitExecutableSha256,
    mavenExecutableSha256: buildIdentity.mavenExecutableSha256,
    javaExecutableSha256: ownedJava.javaExecutableSha256,
    processId: ownedJava.child.pid,
    sourceHead: requireCleanCheckout(repoRoot),
  };
}

async function stopOwnedJava(child) {
  if (child.exitCode !== null) {
    return;
  }
  child.kill('SIGTERM');
  const exited = await Promise.race([
    once(child, 'exit').then(() => true),
    new Promise((resolve) => setTimeout(() => resolve(false), 10_000)),
  ]);
  if (!exited) {
    const taskkillExecutable = requireExecutable(
      'REHEARSAL_TASKKILL_EXECUTABLE',
      'taskkill.exe',
    );
    execFileSync(taskkillExecutable, ['/PID', String(child.pid), '/T', '/F'], {
      env: buildChildEnvironment({}),
      windowsHide: true,
    });
    await once(child, 'exit');
  }
}

function readJavaListener() {
  if (process.platform !== 'win32') {
    throw new Error('The Phase 11 listener identity check requires Windows');
  }
  const script = [
    "$listener = Get-NetTCPConnection -State Listen -LocalPort 56410 | Where-Object LocalAddress -eq '127.0.0.1' | Select-Object -First 1",
    "if ($null -eq $listener) { throw 'Java rehearsal listener not found' }",
    "[pscustomobject]@{ localAddress=$listener.LocalAddress; processId=$listener.OwningProcess } | ConvertTo-Json -Compress",
  ].join('; ');
  const powershellExecutable = requireExecutable(
    'REHEARSAL_POWERSHELL_EXECUTABLE',
    'powershell.exe',
  );
  const output = execFileSync(powershellExecutable, [
    '-NoProfile',
    '-NonInteractive',
    '-Command',
    script,
  ], {
    encoding: 'utf8',
    env: buildChildEnvironment({}),
    windowsHide: true,
  });
  const listener = JSON.parse(output);
  assert.ok(Number.isInteger(listener.processId), 'Java listener process ID');
  return listener;
}

function resolveReferenceFile(runRoot) {
  const configured = process.env.REHEARSAL_REFERENCE_FILE;
  if (!configured) {
    if (process.env.RUN_OWNED_JAVA_DIFFERENTIAL === 'true') {
      throw new Error(
        'REHEARSAL_REFERENCE_FILE is required for the owned Java differential',
      );
    }
    return null;
  }
  const resolved = path.resolve(configured);
  if (!isInside(runRoot, resolved)) {
    throw new Error('Reference artifact must stay inside REHEARSAL_RUN_ROOT');
  }
  assert.equal(
    path.dirname(resolved).toLowerCase(),
    runRoot.toLowerCase(),
    'Reference artifact must be a direct child of REHEARSAL_RUN_ROOT',
  );
  if (
    process.env.RUN_OWNED_JAVA_DIFFERENTIAL === 'true' &&
    fs.existsSync(resolved)
  ) {
    throw new Error('Owned Java reference artifact must not already exist');
  }
  return resolved;
}

function requireLoopbackJavaBaseUrl() {
  let javaBaseUrl;
  try {
    javaBaseUrl = new URL(process.env.JAVA_REHEARSAL_BASE_URL);
  } catch {
    throw new Error('JAVA_REHEARSAL_BASE_URL must be a valid URL');
  }
  const expected = {
    protocol: 'http:',
    hostname: '127.0.0.1',
    port: '56410',
    pathname: '/',
    username: '',
    password: '',
    search: '',
    hash: '',
  };
  for (const [field, value] of Object.entries(expected)) {
    if (javaBaseUrl[field] !== value) {
      throw new Error(`JAVA_REHEARSAL_BASE_URL ${field} must be ${value}`);
    }
  }
  return javaBaseUrl.origin;
}

function buildCorpus() {
  const student = issueToken({
    id: 'student-user',
    email: 'student@campuscore.edu',
    roles: ['STUDENT'],
    studentId: 'student-profile',
    student: { year: 2 },
  });
  const lecturer = issueToken({
    id: 'lecturer-user',
    email: 'lecturer@campuscore.edu',
    roles: ['LECTURER'],
    lecturerId: 'lecturer-1',
  });
  const admin = issueToken({
    id: 'admin-user',
    email: 'admin@campuscore.edu',
    roles: ['ADMIN'],
  });

  return [
    listCase(
      'student-bearer',
      '/api/v1/announcements/my',
      { Authorization: `Bearer ${student}` },
      ['academic-high', 'student-year', 'global'],
    ),
    listCase(
      'student-cookie',
      '/api/v1/announcements/my',
      { Cookie: `cc_access_token=${student}` },
      ['academic-high', 'student-year', 'global'],
    ),
    listCase(
      'lecturer-bearer',
      '/api/v1/announcements/my',
      { Authorization: `Bearer ${lecturer}` },
      ['lecturer-mine', 'lecturer-general', 'global'],
    ),
    listCase(
      'admin-filter',
      '/api/v1/announcements?semesterId=semester-1&sectionId=section-1&priority=HIGH',
      { Authorization: `Bearer ${admin}` },
      ['academic-high', 'student-year'],
    ),
    listCase(
      'student-page-2',
      '/api/v1/announcements/my?page=2&limit=1',
      { Authorization: `Bearer ${student}` },
      ['student-year'],
      { total: 3, page: 2, limit: 1, totalPages: 3 },
    ),
    listCase(
      'admin-page-2',
      '/api/v1/announcements?page=2&limit=1',
      { Authorization: `Bearer ${admin}` },
      ['expired'],
      { total: 9, page: 2, limit: 1, totalPages: 9 },
    ),
    listCase(
      'admin-empty-page',
      '/api/v1/announcements?page=99&limit=20',
      { Authorization: `Bearer ${admin}` },
      [],
      { total: 9, page: 99, limit: 20, totalPages: 1 },
    ),
    statusCase(
      'blank-priority',
      '/api/v1/announcements?priority=',
      { Authorization: `Bearer ${admin}` },
      400,
    ),
    statusCase(
      'unknown-query',
      '/api/v1/announcements?unknown=value',
      { Authorization: `Bearer ${admin}` },
      400,
    ),
    statusCase(
      'limit-overflow',
      '/api/v1/announcements?limit=201',
      { Authorization: `Bearer ${admin}` },
      400,
    ),
    statusCase(
      'repeated-page',
      '/api/v1/announcements?page=1&page=2',
      { Authorization: `Bearer ${admin}` },
      400,
    ),
    statusCase('anonymous', '/api/v1/announcements/my', {}, 401),
    statusCase(
      'wrong-admin-role',
      '/api/v1/announcements',
      { Authorization: `Bearer ${student}` },
      403,
    ),
  ];
}

function listCase(name, requestPath, headers, expectedIds, expectedMeta) {
  return {
    name,
    requestPath,
    headers,
    expectedStatus: 200,
    expectedIds,
    expectedMeta: expectedMeta ?? {
      total: expectedIds.length,
      page: 1,
      limit: EXPECTED_META_LIMIT,
      totalPages: 1,
    },
  };
}

function statusCase(name, requestPath, headers, expectedStatus) {
  return {
    name,
    requestPath,
    headers,
    expectedStatus,
    expectedIds: null,
    expectedMeta: null,
  };
}

function issueToken(user) {
  const issuedAt = Math.floor(Date.now() / 1000);
  const header = Buffer.from(
    JSON.stringify({ alg: 'HS256', typ: 'JWT' }),
  ).toString('base64url');
  const payload = Buffer.from(
    JSON.stringify({
      sub: user.id,
      email: user.email,
      firstName: 'Phase',
      lastName: 'Eleven',
      roles: user.roles,
      permissions: [],
      studentId: user.studentId ?? null,
      lecturerId: user.lecturerId ?? null,
      student: user.student ?? null,
      iat: issuedAt,
      exp: issuedAt + 15 * 60,
    }),
  ).toString('base64url');
  const signingInput = `${header}.${payload}`;
  const signature = createHmac('sha256', process.env.JWT_SECRET)
    .update(signingInput)
    .digest('base64url');
  return `${signingInput}.${signature}`;
}

async function captureJavaReference(
  javaBaseUrl,
  corpus,
  sourceHead,
  javaIdentity,
) {
  const cases = [];
  for (const spec of corpus) {
    const result = await invokeJava(javaBaseUrl, spec);
    verifyExpectedResult(spec, result);
    cases.push({
      name: spec.name,
      requestPath: spec.requestPath,
      status: result.status,
      contentType: result.contentType,
      body: result.body,
    });
  }
  assert.equal(javaIdentity.sourceHead, sourceHead, 'owned Java source HEAD');
  return {
    schemaVersion: 3,
    sourceHead,
    artifactPath: javaIdentity.artifactPath,
    artifactSha256: javaIdentity.artifactSha256,
    sourceArchiveSha256: javaIdentity.sourceArchiveSha256,
    tarExecutableSha256: javaIdentity.tarExecutableSha256,
    powershellExecutableSha256:
      javaIdentity.powershellExecutableSha256,
    buildLogSha256: javaIdentity.buildLogSha256,
    cmdExecutableSha256: javaIdentity.cmdExecutableSha256,
    gitExecutableSha256: javaIdentity.gitExecutableSha256,
    mavenExecutableSha256: javaIdentity.mavenExecutableSha256,
    javaExecutableSha256: javaIdentity.javaExecutableSha256,
    javaProcessId: javaIdentity.processId,
    capturedAt: new Date().toISOString(),
    cases,
  };
}

async function invokeJava(javaBaseUrl, spec) {
  const response = await fetch(`${javaBaseUrl}${spec.requestPath}`, {
    headers: spec.headers,
    redirect: 'error',
  });
  const text = await response.text();
  return {
    status: response.status,
    contentType: response.headers.get('content-type'),
    body: text ? JSON.parse(text) : null,
  };
}

async function runLegacyProbe(corpus, reference, moduleRoot) {
  const { Test } = require(
    path.join(moduleRoot, 'node_modules/@nestjs/testing')
  );
  const request = require(path.join(moduleRoot, 'node_modules/supertest'));
  const { configureHttpApp } = require(
    path.join(moduleRoot, 'dist/src/bootstrap.js')
  );
  const { AppModule } = require(path.join(moduleRoot, 'dist/src/app.module.js'));
  const moduleFixture = await Test.createTestingModule({
    imports: [AppModule],
  }).compile();
  const app = moduleFixture.createNestApplication();

  try {
    configureHttpApp(app);
    await app.init();
    const server = app.getHttpServer();
    const summaries = [];
    for (const spec of corpus) {
      const response = await request(server)
        .get(spec.requestPath)
        .set(spec.headers);
      const legacy = {
        status: response.status,
        contentType: response.headers['content-type'] ?? null,
        body: response.body,
      };
      verifyExpectedResult(spec, legacy);
      summaries.push(compareWithReference(spec, legacy, reference));
    }
    return summaries;
  } finally {
    await app.close();
  }
}

function verifyExpectedResult(spec, result) {
  assert.equal(result.status, spec.expectedStatus, `${spec.name} status`);
  if (!spec.expectedIds) {
    return;
  }
  const ids = result.body.data.map(({ id }) => id);
  assert.deepEqual(ids, spec.expectedIds, `${spec.name} ids`);
  assert.deepEqual(result.body.meta, spec.expectedMeta);
}

function compareWithReference(spec, legacy, reference) {
  const java = reference?.cases.find(({ name }) => name === spec.name) ?? null;
  if (reference) {
    assert.ok(java, `${spec.name} missing from Java reference`);
    assert.equal(java.requestPath, spec.requestPath, `${spec.name} request identity`);
    assert.equal(java.status, legacy.status, `${spec.name} Java status`);
    assert.equal(
      normalizeContentType(java.contentType),
      normalizeContentType(legacy.contentType),
      `${spec.name} content type`,
    );
    if (spec.expectedStatus === 200) {
      assert.deepEqual(java.body, legacy.body, `${spec.name} full response parity`);
    }
  }

  return {
    name: spec.name,
    legacyStatus: legacy.status,
    javaStatus: java?.status ?? null,
    fullBodyParity:
      java && spec.expectedStatus === 200
        ? isDeepStrictEqual(java.body, legacy.body)
        : null,
    errorBodyParity:
      java && spec.expectedStatus !== 200
        ? isDeepStrictEqual(java.body, legacy.body)
        : null,
    contentTypeParity: java
      ? normalizeContentType(java.contentType) ===
        normalizeContentType(legacy.contentType)
      : null,
    legacyCode: legacy.body?.code ?? null,
    javaCode: java?.body?.code ?? null,
    ids: spec.expectedIds,
    meta: spec.expectedIds ? legacy.body.meta : null,
  };
}

function normalizeContentType(value) {
  return value?.split(';', 1)[0].trim().toLowerCase() ?? null;
}

function buildLegacyIdentity(repoRoot, runRoot, sourceHead, buildIdentity) {
  const moduleRoot = path.resolve(
    buildIdentity.sourceSnapshot,
    'engagement-service',
  );
  const nodeExecutable = fs.realpathSync.native(process.execPath);
  const npmCli = requireFile('REHEARSAL_NPM_CLI_FILE', 'npm-cli.js');
  const npmCache = requireDirectory('REHEARSAL_NPM_CACHE');
  if (path.parse(npmCache).root.toUpperCase() !== 'D:\\') {
    throw new Error('REHEARSAL_NPM_CACHE must be on D:');
  }
  const npmConfig = path.resolve(
    buildIdentity.sourceSnapshot,
    'rehearsal.npmrc',
  );
  fs.writeFileSync(
    npmConfig,
    ['offline=true', 'audit=false', 'fund=false', 'update-notifier=false', ''].join('\n'),
    { encoding: 'utf8', flag: 'wx' },
  );
  const packageLock = path.resolve(moduleRoot, 'package-lock.json');
  const packageLockSha256 = fileSha256(packageLock);
  const installOutput = execFileSync(
    nodeExecutable,
    [npmCli, 'ci', '--offline', '--no-audit', '--no-fund'],
    {
      cwd: moduleRoot,
      encoding: 'utf8',
      env: buildChildEnvironment({
        ComSpec: buildIdentity.cmdExecutable,
        NODE_ENV: 'test',
        NODE_OPTIONS: '--max-old-space-size=256',
        Path: path.dirname(nodeExecutable),
        NPM_CONFIG_CACHE: npmCache,
        NPM_CONFIG_OFFLINE: 'true',
        NPM_CONFIG_AUDIT: 'false',
        NPM_CONFIG_FUND: 'false',
        NPM_CONFIG_UPDATE_NOTIFIER: 'false',
        NPM_CONFIG_PROGRESS: 'false',
        NPM_CONFIG_USERCONFIG: npmConfig,
        NPM_CONFIG_GLOBALCONFIG: npmConfig,
      }),
      maxBuffer: 20 * 1024 * 1024,
      windowsHide: true,
    },
  );
  assert.equal(
    fileSha256(packageLock),
    packageLockSha256,
    'npm ci must not change the committed package lock',
  );
  const installLog = path.resolve(
    runRoot,
    `legacy-install-${sourceHead.slice(0, 12)}.log`,
  );
  fs.writeFileSync(installLog, installOutput, { encoding: 'utf8', flag: 'wx' });
  const dependencyManifest = path.resolve(
    runRoot,
    `legacy-dependencies-${sourceHead.slice(0, 12)}.jsonl`,
  );
  const dependencyFiles = writeDirectoryManifest(
    path.resolve(moduleRoot, 'node_modules'),
    dependencyManifest,
    buildIdentity.sourceSnapshot,
  );
  const typescriptCompiler = path.resolve(
    moduleRoot,
    'node_modules/typescript/bin/tsc',
  );
  const buildOutput = execFileSync(
    nodeExecutable,
    [typescriptCompiler, '-p', 'tsconfig.build.json'],
    {
      cwd: moduleRoot,
      encoding: 'utf8',
      env: buildChildEnvironment({
        NODE_ENV: 'test',
        NODE_OPTIONS: '--max-old-space-size=256',
      }),
      maxBuffer: 20 * 1024 * 1024,
      windowsHide: true,
    },
  );
  assert.equal(
    requireCleanCheckout(repoRoot),
    sourceHead,
    'source HEAD after isolated legacy build',
  );
  const buildLog = path.resolve(
    runRoot,
    `legacy-build-${sourceHead.slice(0, 12)}.log`,
  );
  fs.writeFileSync(buildLog, buildOutput, { encoding: 'utf8', flag: 'wx' });
  const entry = path.resolve(moduleRoot, 'dist/src/main.js');
  return {
    sourceHead,
    moduleRoot,
    artifactPath: path.relative(runRoot, entry).replaceAll('\\', '/'),
    artifactSha256: fileSha256(entry),
    buildLogSha256: fileSha256(buildLog),
    installLogSha256: fileSha256(installLog),
    npmConfigSha256: fileSha256(npmConfig),
    packageLockSha256,
    dependencyManifestSha256: fileSha256(dependencyManifest),
    dependencyFiles,
    nodeExecutableSha256: fileSha256(nodeExecutable),
    npmCliSha256: fileSha256(npmCli),
    typescriptCompilerSha256: fileSha256(typescriptCompiler),
  };
}

function requireFile(environmentKey, expectedName) {
  const configured = process.env[environmentKey];
  if (!configured) {
    throw new Error(`${environmentKey} is required`);
  }
  const file = fs.realpathSync.native(path.resolve(configured));
  assert.equal(
    path.basename(file).toLowerCase(),
    expectedName,
    `${environmentKey} file name`,
  );
  assert.ok(fs.statSync(file).isFile(), `${environmentKey} file`);
  return file;
}

function writeDirectoryManifest(root, manifestFile, allowedRoot) {
  const records = [];
  const visit = (current) => {
    const entries = fs
      .readdirSync(current, { withFileTypes: true })
      .sort((left, right) => left.name.localeCompare(right.name));
    for (const entry of entries) {
      const absolute = path.join(current, entry.name);
      const relative = path.relative(root, absolute).replaceAll('\\', '/');
      const stat = fs.lstatSync(absolute);
      if (stat.isSymbolicLink()) {
        const target = fs.realpathSync.native(absolute);
        if (!isInside(allowedRoot, target)) {
          throw new Error(`Dependency link escapes isolated snapshot: ${relative}`);
        }
        records.push(
          JSON.stringify({
            type: 'link',
            path: relative,
            target: path.relative(allowedRoot, target).replaceAll('\\', '/'),
          }),
        );
      } else if (stat.isDirectory()) {
        visit(absolute);
      } else if (stat.isFile()) {
        records.push(
          JSON.stringify({
            type: 'file',
            path: relative,
            bytes: stat.size,
            sha256: fileSha256(absolute),
          }),
        );
      } else {
        throw new Error(`Unsupported dependency entry: ${relative}`);
      }
    }
  };
  visit(root);
  fs.writeFileSync(manifestFile, `${records.join('\n')}\n`, {
    encoding: 'utf8',
    flag: 'wx',
  });
  return records.length;
}

function fileSha256(file) {
  return createHash('sha256').update(fs.readFileSync(file)).digest('hex');
}

function isInside(parent, candidate) {
  const relative = path.relative(parent, candidate);
  return relative !== '' && !relative.startsWith('..') && !path.isAbsolute(relative);
}

function writeOptionalReport(report, runRoot) {
  const configured = process.env.REHEARSAL_REPORT_FILE;
  if (!configured) {
    return;
  }
  const reportFile = path.resolve(configured);
  if (!isInside(runRoot, reportFile)) {
    throw new Error('Differential report must stay inside REHEARSAL_RUN_ROOT');
  }
  assert.equal(
    path.dirname(reportFile).toLowerCase(),
    runRoot.toLowerCase(),
    'Differential report must be a direct child of REHEARSAL_RUN_ROOT',
  );
  fs.mkdirSync(path.dirname(reportFile), { recursive: true });
  fs.writeFileSync(reportFile, `${JSON.stringify(report, null, 2)}\n`, {
    encoding: 'utf8',
    flag: 'wx',
  });
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exitCode = 1;
});
