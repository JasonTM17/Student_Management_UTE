'use strict';

const assert = require('node:assert/strict');
const { execFileSync, spawn } = require('node:child_process');
const { createHash } = require('node:crypto');
const { once } = require('node:events');
const fs = require('node:fs');
const path = require('node:path');
const { isDeepStrictEqual } = require('node:util');
const jsonwebtoken = require('jsonwebtoken');

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

  let reference = null;
  if (ownedDifferential) {
    const javaBaseUrl = requireLoopbackJavaBaseUrl();
    const javaExecutable = requireExecutable(
      'REHEARSAL_JAVA_EXECUTABLE',
      'java.exe',
    );
    const buildIdentity = rebuildJavaArtifact(
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

  const cases = await runLegacyProbe(corpus, reference);
  const legacyIdentity = requireLegacyIdentity(repoRoot, sourceHead);
  const report = {
    status: 'PASS',
    mode: reference ? 'owned-java-sequential-differential' : 'legacy-only',
    referenceSourceHead: reference?.sourceHead ?? null,
    referenceArtifactSha256: referenceFile ? fileSha256(referenceFile) : null,
    javaArtifactPath: reference?.artifactPath ?? null,
    javaArtifactSha256: reference?.artifactSha256 ?? null,
    javaBuildLogSha256: reference?.buildLogSha256 ?? null,
    mavenExecutableSha256: reference?.mavenExecutableSha256 ?? null,
    javaExecutableSha256: reference?.javaExecutableSha256 ?? null,
    javaLogSha256: reference?.javaLogSha256 ?? null,
    javaProcessId: reference?.javaProcessId ?? null,
    legacySourceHead: legacyIdentity.sourceHead,
    legacyArtifactSha256: legacyIdentity.artifactSha256,
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
  return requireRehearsalRunRoot();
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
  if (path.parse(runRoot).root.toUpperCase() !== 'D:\\') {
    throw new Error('REHEARSAL_RUN_ROOT must be on D:');
  }
  if (!path.basename(runRoot).toLowerCase().startsWith('phase11-engagement-')) {
    throw new Error('REHEARSAL_RUN_ROOT must identify the Phase 11 rehearsal');
  }
  const pgData = path.join(runRoot, 'pgdata');
  const lines = fs
    .readFileSync(path.join(pgData, 'postmaster.pid'), 'utf8')
    .split(/\r?\n/);
  assert.equal(path.resolve(lines[1]), path.resolve(pgData), 'PostgreSQL data root');
  assert.equal(lines[3], '56432', 'PostgreSQL rehearsal port');
  assert.equal(lines[5], '127.0.0.1', 'PostgreSQL listen address');
  assert.equal(lines[7], 'ready   ', 'PostgreSQL readiness marker');
  return runRoot;
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
  return execFileSync('git', args, {
    cwd: repoRoot,
    encoding: 'utf8',
    windowsHide: true,
  });
}

function rebuildJavaArtifact(repoRoot, runRoot, sourceHead, javaExecutable) {
  const mavenExecutable = requireExecutable(
    'REHEARSAL_MAVEN_EXECUTABLE',
    'mvn.cmd',
  );
  const moduleRoot = path.resolve(repoRoot, 'java-services/restful-api');
  const mavenCommand =
    `call "${mavenExecutable}" -o -DskipTests clean package`;
  const buildOutput = execFileSync(
    process.env.ComSpec || 'C:\\Windows\\System32\\cmd.exe',
    ['/d', '/s', '/c', mavenCommand],
    {
      cwd: moduleRoot,
      encoding: 'utf8',
      env: {
        ...process.env,
        JAVA_HOME: path.dirname(path.dirname(javaExecutable)),
        MAVEN_OPTS: '-Xms32m -Xmx192m -XX:MaxMetaspaceSize=160m',
      },
      maxBuffer: 20 * 1024 * 1024,
      windowsHide: true,
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
    repoRoot,
    'java-services/restful-api/target/campuscore-restful-api-0.1.0-SNAPSHOT.jar',
  );
  return {
    artifactFile,
    artifactPath: path.relative(repoRoot, artifactFile).replaceAll('\\', '/'),
    artifactSha256: fileSha256(artifactFile),
    buildLogSha256: fileSha256(buildLog),
    mavenExecutableSha256: fileSha256(mavenExecutable),
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
    env: {
      ...process.env,
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
    },
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
    buildLogSha256: buildIdentity.buildLogSha256,
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
    execFileSync('taskkill.exe', ['/PID', String(child.pid), '/T', '/F'], {
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
  const output = execFileSync('powershell.exe', [
    '-NoProfile',
    '-NonInteractive',
    '-Command',
    script,
  ], {
    encoding: 'utf8',
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
  return jsonwebtoken.sign(
    {
      sub: user.id,
      email: user.email,
      firstName: 'Phase',
      lastName: 'Eleven',
      roles: user.roles,
      permissions: [],
      studentId: user.studentId ?? null,
      lecturerId: user.lecturerId ?? null,
      student: user.student ?? null,
    },
    process.env.JWT_SECRET,
    { algorithm: 'HS256', expiresIn: '15m' },
  );
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
    buildLogSha256: javaIdentity.buildLogSha256,
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

async function runLegacyProbe(corpus, reference) {
  const { Test } = require('@nestjs/testing');
  const request = require('supertest');
  const { configureHttpApp } = require('../dist/src/bootstrap.js');
  const { AppModule } = require('../dist/src/app.module.js');
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

function requireLegacyIdentity(repoRoot, sourceHead) {
  const entry = path.resolve(__dirname, '../dist/src/main.js');
  assert.equal(
    entry.toLowerCase(),
    path.resolve(repoRoot, 'engagement-service/dist/src/main.js').toLowerCase(),
    'legacy Nest entry path',
  );
  const artifactSha256 = fileSha256(entry);
  return { sourceHead, artifactSha256 };
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
