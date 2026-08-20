'use strict';

const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');
const { isDeepStrictEqual } = require('node:util');
const jsonwebtoken = require('jsonwebtoken');

const EXPECTED_META_LIMIT = 20;

async function main() {
  const runRoot = assertSafeEnvironment();
  const corpus = buildCorpus();
  const referenceFile = resolveReferenceFile(runRoot);

  if (process.env.CAPTURE_JAVA_REFERENCE === 'true') {
    const javaBaseUrl = requireLoopbackJavaBaseUrl();
    const artifact = await captureJavaReference(javaBaseUrl, corpus);
    fs.mkdirSync(path.dirname(referenceFile), { recursive: true });
    fs.writeFileSync(referenceFile, `${JSON.stringify(artifact, null, 2)}\n`, {
      encoding: 'utf8',
      flag: 'wx',
    });
    process.stdout.write(
      `${JSON.stringify({
        status: 'PASS',
        mode: 'java-reference-capture',
        sourceHead: artifact.sourceHead,
        cases: summarizeReferenceCases(artifact.cases),
      }, null, 2)}\n`,
    );
    return;
  }

  const reference = referenceFile ? readReference(referenceFile) : null;
  const cases = await runLegacyProbe(corpus, reference);
  const legacyIdentity = requireLegacyIdentity();
  const report = {
    status: 'PASS',
    mode: reference ? 'sequential-differential' : 'legacy-only',
    referenceSourceHead: reference?.sourceHead ?? null,
    referenceArtifactSha256: referenceFile ? fileSha256(referenceFile) : null,
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

function resolveReferenceFile(runRoot) {
  const configured = process.env.REHEARSAL_REFERENCE_FILE;
  if (!configured) {
    if (process.env.CAPTURE_JAVA_REFERENCE === 'true') {
      throw new Error('REHEARSAL_REFERENCE_FILE is required for Java capture');
    }
    return null;
  }
  const resolved = path.resolve(configured);
  if (!isInside(runRoot, resolved)) {
    throw new Error('Reference artifact must stay inside REHEARSAL_RUN_ROOT');
  }
  return resolved;
}

function requireLoopbackJavaBaseUrl() {
  const javaBaseUrl = process.env.JAVA_REHEARSAL_BASE_URL;
  if (!javaBaseUrl?.startsWith('http://127.0.0.1:')) {
    throw new Error('JAVA_REHEARSAL_BASE_URL must be loopback-only');
  }
  return javaBaseUrl.replace(/\/$/, '');
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

async function captureJavaReference(javaBaseUrl, corpus) {
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
  return {
    schemaVersion: 1,
    sourceHead: requireSha('REHEARSAL_SOURCE_HEAD', 40),
    artifactSha256: requireSha('REHEARSAL_ARTIFACT_SHA256', 64),
    capturedAt: new Date().toISOString(),
    cases,
  };
}

async function invokeJava(javaBaseUrl, spec) {
  const response = await fetch(`${javaBaseUrl}${spec.requestPath}`, {
    headers: spec.headers,
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

function readReference(referenceFile) {
  const artifact = JSON.parse(fs.readFileSync(referenceFile, 'utf8'));
  assert.equal(artifact.schemaVersion, 1, 'reference schema version');
  assert.ok(Array.isArray(artifact.cases), 'reference cases');
  assert.match(artifact.sourceHead, /^[0-9a-f]{40}$/i, 'reference source HEAD');
  assert.match(artifact.artifactSha256, /^[0-9a-f]{64}$/i, 'reference JAR SHA-256');
  return artifact;
}

function requireLegacyIdentity() {
  const sourceHead = requireSha('REHEARSAL_LEGACY_SOURCE_HEAD', 40);
  const expectedArtifactSha256 = requireSha(
    'REHEARSAL_LEGACY_ARTIFACT_SHA256',
    64,
  );
  const entry = path.resolve(__dirname, '../dist/src/main.js');
  const artifactSha256 = fileSha256(entry);
  assert.equal(
    artifactSha256,
    expectedArtifactSha256,
    'legacy Nest entry artifact SHA-256',
  );
  return { sourceHead, artifactSha256 };
}

function requireSha(name, length) {
  const value = process.env[name];
  const pattern = new RegExp(`^[0-9a-f]{${length}}$`, 'i');
  if (!value || !pattern.test(value)) {
    throw new Error(`${name} must be a ${length}-character hexadecimal identity`);
  }
  return value.toLowerCase();
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

function summarizeReferenceCases(cases) {
  return cases.map(({ name, status, contentType, body }) => ({
    name,
    status,
    contentType,
    code: body?.code ?? null,
    ids: Array.isArray(body?.data) ? body.data.map(({ id }) => id) : null,
  }));
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exitCode = 1;
});
