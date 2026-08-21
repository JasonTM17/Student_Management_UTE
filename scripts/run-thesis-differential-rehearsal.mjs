import crypto from 'node:crypto';
import http from 'node:http';
import { once } from 'node:events';

const ROUND_ID = '11111111-1111-1111-1111-111111111111';
const TOPIC_ID = '22222222-2222-2222-2222-222222222222';
const GROUP_ID = '33333333-3333-3333-3333-333333333333';
const COUNCIL_ID = '44444444-4444-4444-4444-444444444444';

const corpus = [
  { name: 'rounds all', method: 'GET', path: '/api/v1/thesis/rounds' },
  { name: 'rounds by status', method: 'GET', path: '/api/v1/thesis/rounds?status=DRAFT' },
  { name: 'published topics', method: 'GET', path: `/api/v1/thesis/topics?roundId=${ROUND_ID}` },
  { name: 'groups by round', method: 'GET', path: `/api/v1/thesis/groups?roundId=${ROUND_ID}` },
  { name: 'group detail', method: 'GET', path: `/api/v1/thesis/groups/${GROUP_ID}` },
  { name: 'councils by round', method: 'GET', path: `/api/v1/thesis/councils?roundId=${ROUND_ID}` },
  { name: 'unknown round topics', method: 'GET', path: '/api/v1/thesis/topics?roundId=00000000-0000-0000-0000-000000000000' },
  { name: 'malformed round groups', method: 'GET', path: '/api/v1/thesis/groups?roundId=not-a-uuid' },
];

const selfTest = process.argv.includes('--self-test');

if (selfTest) {
  await withSelfTestServers(async ({ legacyBaseUrl, javaBaseUrl }) => {
    await runDifferential({ legacyBaseUrl, javaBaseUrl, token: 'self-test-token' });
  });
} else {
  const legacyBaseUrl = requiredEnv('THESIS_DIFF_LEGACY_BASE_URL');
  const javaBaseUrl = requiredEnv('THESIS_DIFF_JAVA_BASE_URL');
  const token = process.env.THESIS_DIFF_JWT ?? signJwt(requiredEnv('JWT_SECRET'));
  await runDifferential({ legacyBaseUrl, javaBaseUrl, token });
}

async function runDifferential({ legacyBaseUrl, javaBaseUrl, token }) {
  const report = {
    generatedAt: new Date().toISOString(),
    corpus: corpus.map(({ name, method, path }) => ({ name, method, path })),
    endpoints: {
      legacy: redactUrl(legacyBaseUrl),
      java: redactUrl(javaBaseUrl),
    },
    comparisons: [],
    routeSequence: [],
  };
  const failures = [];

  for (const item of corpus) {
    const legacy = await requestJson(legacyBaseUrl, item, token);
    const java = await requestJson(javaBaseUrl, item, token);
    const comparison = compareResponses(item, legacy, java);
    report.comparisons.push(comparison);
    if (comparison.result !== 'PASS') {
      failures.push(`${item.name}: ${comparison.reason}`);
    }
  }

  for (const owner of ['legacy-before', 'java-candidate', 'legacy-after']) {
    const baseUrl = owner === 'java-candidate' ? javaBaseUrl : legacyBaseUrl;
    const probe = await requestJson(baseUrl, corpus[0], token);
    report.routeSequence.push({
      owner,
      status: probe.status,
      contentType: probe.contentType,
      bodyHash: hashStable(probe.body),
    });
  }

  const firstLegacyHash = report.routeSequence[0].bodyHash;
  const finalLegacyHash = report.routeSequence[2].bodyHash;
  if (firstLegacyHash !== finalLegacyHash) {
    failures.push('legacy rollback sequence changed the legacy response hash');
  }

  if (failures.length > 0) {
    console.error(JSON.stringify({ result: 'FAIL', failures, report }, null, 2));
    process.exitCode = 1;
    return;
  }

  console.log(JSON.stringify({ result: 'PASS', report }, null, 2));
}

function compareResponses(item, legacy, java) {
  if (legacy.status !== java.status) {
    return {
      name: item.name,
      result: 'FAIL',
      reason: `status mismatch legacy=${legacy.status} java=${java.status}`,
      legacy,
      java,
    };
  }
  if (legacy.contentType !== java.contentType) {
    return {
      name: item.name,
      result: 'FAIL',
      reason: `content-type mismatch legacy=${legacy.contentType} java=${java.contentType}`,
      legacy,
      java,
    };
  }
  const legacyHash = hashStable(legacy.body);
  const javaHash = hashStable(java.body);
  if (legacyHash !== javaHash) {
    return {
      name: item.name,
      result: 'FAIL',
      reason: `body mismatch legacyHash=${legacyHash} javaHash=${javaHash}`,
      legacy,
      java,
    };
  }
  return {
    name: item.name,
    result: 'PASS',
    status: legacy.status,
    contentType: legacy.contentType,
    bodyHash: legacyHash,
  };
}

async function requestJson(baseUrl, item, token) {
  const response = await fetch(new URL(item.path, ensureTrailingSlash(baseUrl)), {
    method: item.method,
    headers: {
      authorization: `Bearer ${token}`,
      accept: 'application/json',
    },
  });
  const text = await response.text();
  return {
    status: response.status,
    contentType: normalizeContentType(response.headers.get('content-type')),
    body: parseBody(text),
  };
}

function parseBody(text) {
  if (text.length === 0) {
    return null;
  }
  try {
    return JSON.parse(text);
  } catch {
    return text;
  }
}

function normalizeContentType(value) {
  return (value ?? '').split(';', 1)[0].trim().toLowerCase();
}

function hashStable(value) {
  return crypto.createHash('sha256').update(stableJson(value)).digest('hex');
}

function stableJson(value) {
  if (Array.isArray(value)) {
    return `[${value.map(stableJson).join(',')}]`;
  }
  if (value && typeof value === 'object') {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableJson(value[key])}`)
      .join(',')}}`;
  }
  return JSON.stringify(value);
}

function signJwt(secret) {
  if (secret.length < 32) {
    throw new Error('JWT_SECRET must contain at least 32 characters');
  }
  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const now = Math.floor(Date.now() / 1000);
  const payload = base64Url(JSON.stringify({
    sub: 'thesis-differential-rehearsal',
    roles: ['STUDENT'],
    permissions: ['thesis:read'],
    iat: now,
    exp: now + 300,
  }));
  const data = `${header}.${payload}`;
  const signature = crypto.createHmac('sha256', secret).update(data).digest('base64url');
  return `${data}.${signature}`;
}

function base64Url(text) {
  return Buffer.from(text).toString('base64url');
}

function requiredEnv(name) {
  const value = process.env[name];
  if (!value) {
    throw new Error(`${name} is required. Use --self-test for a local harness check.`);
  }
  return value;
}

function ensureTrailingSlash(value) {
  return value.endsWith('/') ? value : `${value}/`;
}

function redactUrl(value) {
  const url = new URL(value);
  url.username = '';
  url.password = '';
  return url.toString();
}

async function withSelfTestServers(callback) {
  const legacy = createSelfTestServer('legacy');
  const java = createSelfTestServer('java');
  try {
    legacy.listen(0, '127.0.0.1');
    java.listen(0, '127.0.0.1');
    await Promise.all([once(legacy, 'listening'), once(java, 'listening')]);
    await callback({
      legacyBaseUrl: `http://127.0.0.1:${legacy.address().port}`,
      javaBaseUrl: `http://127.0.0.1:${java.address().port}`,
    });
  } finally {
    await Promise.all([closeServer(legacy), closeServer(java)]);
  }
}

function createSelfTestServer(label) {
  return http.createServer((request, response) => {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const payload = selfTestPayload(url);
    response.setHeader('content-type', 'application/json');
    response.setHeader('x-rehearsal-owner', label);
    response.statusCode = payload.status;
    response.end(JSON.stringify(payload.body));
  });
}

function selfTestPayload(url) {
  if (url.pathname === '/api/v1/thesis/rounds') {
    return { status: 200, body: [{ id: ROUND_ID, name: 'RO Thesis Round', status: 'DRAFT' }] };
  }
  if (url.pathname === '/api/v1/thesis/topics') {
    if (url.searchParams.get('roundId') === '00000000-0000-0000-0000-000000000000') {
      return { status: 404, body: { code: 'HTTP_404' } };
    }
    return { status: 200, body: [{ id: TOPIC_ID, roundId: ROUND_ID, title: 'RO Topic', status: 'PUBLISHED' }] };
  }
  if (url.pathname === '/api/v1/thesis/groups' && url.searchParams.get('roundId') === 'not-a-uuid') {
    return { status: 400, body: { code: 'INVALID_REQUEST' } };
  }
  if (url.pathname === '/api/v1/thesis/groups') {
    return { status: 200, body: [{ id: GROUP_ID, roundId: ROUND_ID, memberStudentIds: [] }] };
  }
  if (url.pathname === `/api/v1/thesis/groups/${GROUP_ID}`) {
    return { status: 200, body: { id: GROUP_ID, roundId: ROUND_ID, memberStudentIds: [] } };
  }
  if (url.pathname === '/api/v1/thesis/councils') {
    return { status: 200, body: [{ id: COUNCIL_ID, roundId: ROUND_ID, members: [] }] };
  }
  return { status: 404, body: { code: 'HTTP_404' } };
}

async function closeServer(server) {
  if (!server.listening) {
    return;
  }
  server.close();
  await once(server, 'close');
}
