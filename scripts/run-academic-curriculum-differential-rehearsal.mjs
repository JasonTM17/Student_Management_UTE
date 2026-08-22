import crypto from 'node:crypto';
import http from 'node:http';
import { once } from 'node:events';

const DETAIL_ID = process.env.ACADEMIC_CURRICULUM_DIFF_CURRICULUM_ID ?? 'curriculum-cs';
const MISSING_ID = process.env.ACADEMIC_CURRICULUM_DIFF_MISSING_ID ?? 'missing-curriculum';

const corpus = [
  { name: 'curriculum list page', method: 'GET', path: '/api/v1/curricula?page=1&limit=1' },
  { name: 'curriculum detail', method: 'GET', path: `/api/v1/curricula/${encodeURIComponent(DETAIL_ID)}` },
  { name: 'missing curriculum detail', method: 'GET', path: `/api/v1/curricula/${encodeURIComponent(MISSING_ID)}` },
];

const selfTest = process.argv.includes('--self-test');
const edgeRouteSwitch = process.argv.includes('--edge-route-switch');

if (selfTest) {
  await withSelfTestServers(async ({ legacyBaseUrl, javaBaseUrl }) => {
    await executeRehearsal({
      legacyBaseUrl,
      javaBaseUrl,
      token: 'self-test-token',
      edgeRouteSwitch,
    });
  });
  runNormalizationSelfTest();
} else {
  const legacyBaseUrl = requiredEnv('ACADEMIC_CURRICULUM_DIFF_LEGACY_BASE_URL');
  const javaBaseUrl = requiredEnv('ACADEMIC_CURRICULUM_DIFF_JAVA_BASE_URL');
  const token = process.env.ACADEMIC_CURRICULUM_DIFF_JWT
    ?? signJwt(process.env.ACADEMIC_CURRICULUM_DIFF_JWT_SECRET ?? requiredEnv('JWT_SECRET'));

  await executeRehearsal({
    legacyBaseUrl,
    javaBaseUrl,
    token,
    edgeRouteSwitch,
  });
}

async function executeRehearsal({ legacyBaseUrl, javaBaseUrl, token, edgeRouteSwitch }) {
  if (edgeRouteSwitch) {
    await runEdgeRouteSwitch({ legacyBaseUrl, javaBaseUrl, token });
    return;
  }

  await runDifferential({ legacyBaseUrl, javaBaseUrl, token });
}

async function runDifferential({ legacyBaseUrl, javaBaseUrl, token }) {
  const { report, failures } = await evaluateDifferential({
    legacyBaseUrl,
    javaBaseUrl,
    token,
  });

  if (failures.length > 0) {
    console.error(JSON.stringify({ result: 'FAIL', failures, report }, null, 2));
    process.exitCode = 1;
    return;
  }

  console.log(JSON.stringify({ result: 'PASS', report }, null, 2));
}

async function evaluateDifferential({ legacyBaseUrl, javaBaseUrl, token }) {
  const report = {
    generatedAt: new Date().toISOString(),
    parameters: {
      detailId: DETAIL_ID,
      missingId: MISSING_ID,
    },
    corpus: corpus.map(({ name, method, path }) => ({ name, method, path })),
    endpoints: {
      legacy: redactUrl(legacyBaseUrl),
      java: redactUrl(javaBaseUrl),
    },
    comparisons: [],
    routeSequence: [],
  };
  const failures = [];
  const baselines = new Map();

  for (const item of corpus) {
    const legacy = await requestJson(legacyBaseUrl, item, token);
    const java = await requestJson(javaBaseUrl, item, token);
    const comparison = compareResponses(item, legacy, java);
    report.comparisons.push(comparison);
    baselines.set(item.name, { legacy, java });
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
      bodyHash: stableResponseHash(corpus[0], probe),
    });
  }

  const firstLegacyHash = report.routeSequence[0].bodyHash;
  const finalLegacyHash = report.routeSequence[2].bodyHash;
  if (firstLegacyHash !== finalLegacyHash) {
    failures.push('legacy rollback sequence changed the legacy curriculum list hash');
  }

  return { report, failures, baselines };
}

async function runEdgeRouteSwitch({ legacyBaseUrl, javaBaseUrl, token }) {
  const { report, failures, baselines } = await evaluateDifferential({
    legacyBaseUrl,
    javaBaseUrl,
    token,
  });

  if (failures.length > 0) {
    console.error(JSON.stringify({ result: 'FAIL', failures, report }, null, 2));
    process.exitCode = 1;
    return;
  }

  await withRouteSwitchProxy({ legacyBaseUrl, javaBaseUrl }, async ({ proxyBaseUrl, setOwner }) => {
    report.edgeProxy = redactUrl(proxyBaseUrl);
    report.routeSwitchSequence = [];

    const stages = [
      { owner: 'legacy-before', upstream: 'legacy' },
      { owner: 'java-candidate', upstream: 'java' },
      { owner: 'legacy-after', upstream: 'legacy' },
    ];

    for (const stage of stages) {
      setOwner(stage.upstream);
      const stageResponses = [];

      for (const item of corpus) {
        const observed = await requestJson(proxyBaseUrl, item, token);
        const expected = baselines.get(item.name)?.[stage.upstream];
        if (!expected) {
          failures.push(`missing baseline for ${item.name} during ${stage.owner}`);
          continue;
        }

        const comparison = compareResponses(item, expected, observed);
        if (comparison.result !== 'PASS') {
          failures.push(`${stage.owner}:${item.name}: ${comparison.reason}`);
        }

        stageResponses.push({
          name: item.name,
          status: observed.status,
          contentType: observed.contentType,
          bodyHash: stableResponseHash(item, observed),
          upstreamOwner:
            observed.headers['x-rehearsal-owner'] ??
            observed.headers['x-route-switch-owner'] ??
            stage.upstream,
        });
      }

      report.routeSwitchSequence.push({
        owner: stage.owner,
        upstream: stage.upstream,
        stageHash: hashStable(
          stageResponses.map(({ name, status, contentType, bodyHash }) => ({
            name,
            status,
            contentType,
            bodyHash,
          })),
        ),
        sampleOwner: stageResponses[0]?.upstreamOwner ?? stage.upstream,
        sampleHash: stageResponses[0]?.bodyHash ?? null,
      });
    }
  });

  const firstStageHash = report.routeSwitchSequence[0]?.stageHash;
  const finalStageHash = report.routeSwitchSequence[2]?.stageHash;
  if (!firstStageHash || !finalStageHash || firstStageHash !== finalStageHash) {
    failures.push('academic curriculum proxy rollback sequence changed the route-switch body hash');
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

  if (legacy.status >= 400) {
    const legacyError = normalizeErrorResponse(item, legacy);
    const javaError = normalizeErrorResponse(item, java);
    if (hashStable(legacyError) !== hashStable(javaError)) {
      return {
        name: item.name,
        result: 'FAIL',
        reason: `error mismatch legacyHash=${hashStable(legacyError)} javaHash=${hashStable(javaError)}`,
        legacy,
        java,
      };
    }
    return {
      name: item.name,
      result: 'PASS',
      status: legacy.status,
      contentType: legacy.contentType,
      bodyHash: hashStable(legacyError),
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

  const legacyHash = stableResponseHash(item, legacy);
  const javaHash = stableResponseHash(item, java);
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

function stableResponseHash(item, response) {
  if (response.status >= 400) {
    return hashStable(normalizeErrorResponse(item, response));
  }
  return hashStable(normalizeComparableBody(item, response.body));
}

function normalizeComparableBody(item, body) {
  if (Array.isArray(body)) {
    return body.map((entry) => normalizeCurriculum(entry, { list: false }));
  }

  if (!body || typeof body !== 'object') {
    return body;
  }

  if (Array.isArray(body.data) && body.meta && typeof body.meta === 'object') {
    return {
      ...body,
      data: body.data.map((entry) => normalizeCurriculum(entry, { list: true })),
    };
  }

  if (isCurriculumBody(body)) {
    return normalizeCurriculum(body, { list: item.path.startsWith('/api/v1/curricula?') });
  }

  return body;
}

function normalizeCurriculum(curriculum, { list }) {
  if (!curriculum || typeof curriculum !== 'object') {
    return curriculum;
  }

  const normalized = {
    ...curriculum,
    department: normalizeDepartment(curriculum.department),
  };

  if (list) {
    normalized.courses = normalizeListCourses(curriculum.courses);
    return normalized;
  }

  if (Array.isArray(curriculum.courses)) {
    normalized.courses = curriculum.courses.map(normalizeCurriculumCourse).sort(compareCurriculumCourses);
  }

  return normalized;
}

function normalizeListCourses(courses) {
  if (courses === undefined || courses === null) {
    return [];
  }
  if (!Array.isArray(courses)) {
    return courses;
  }
  return courses.map(normalizeCurriculumCourse).sort(compareCurriculumCourses);
}

function normalizeCurriculumCourse(course) {
  if (!course || typeof course !== 'object') {
    return course;
  }

  return {
    id: course.id,
    curriculumId: course.curriculumId,
    courseId: course.courseId,
    year: toNumber(course.year),
    semester: toNumber(course.semester),
    isMandatory: Boolean(course.isMandatory),
  };
}

function compareCurriculumCourses(first, second) {
  return toNumber(first.year) - toNumber(second.year)
    || toNumber(first.semester) - toNumber(second.semester)
    || String(first.id).localeCompare(String(second.id));
}

function normalizeDepartment(department) {
  if (!department || typeof department !== 'object') {
    return department;
  }

  return {
    ...department,
    isActive: typeof department.isActive === 'boolean' ? department.isActive : department.isActive,
  };
}

function normalizeErrorResponse(item, response) {
  const body = response.body && typeof response.body === 'object' ? response.body : {};
  const message = Array.isArray(body.message)
    ? body.message.join(' | ')
    : typeof body.message === 'string'
      ? body.message
      : '';
  return {
    status: response.status,
    message,
    path: typeof body.path === 'string' ? body.path : pathOnly(item.path),
  };
}

function isCurriculumBody(body) {
  return Object.hasOwn(body, 'code')
    && Object.hasOwn(body, 'departmentId')
    && Object.hasOwn(body, 'academicYearId')
    && Object.hasOwn(body, 'totalCredits');
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
    headers: Object.fromEntries(response.headers.entries()),
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
    throw new Error('JWT secret must contain at least 32 characters');
  }

  const header = base64Url(JSON.stringify({ alg: 'HS256', typ: 'JWT' }));
  const now = Math.floor(Date.now() / 1000);
  const payload = base64Url(JSON.stringify({
    sub: 'academic-curriculum-differential',
    email: 'academic-curriculum-diff@campuscore.local',
    roles: ['STUDENT'],
    permissions: [],
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

function pathOnly(value) {
  return new URL(value, 'http://localhost').pathname;
}

function redactUrl(value) {
  const url = new URL(value);
  url.username = '';
  url.password = '';
  return url.toString();
}

function toNumber(value) {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string' && value.trim() !== '') {
    const parsed = Number(value);
    return Number.isNaN(parsed) ? value : parsed;
  }
  return value;
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

async function withRouteSwitchProxy({ legacyBaseUrl, javaBaseUrl }, callback) {
  let currentOwner = 'legacy';
  const proxy = http.createServer(async (request, response) => {
    try {
      const owner = currentOwner;
      const targetBaseUrl = owner === 'java' ? javaBaseUrl : legacyBaseUrl;
      const method = request.method ?? 'GET';
      const targetUrl = new URL(request.url ?? '/', ensureTrailingSlash(targetBaseUrl));
      const headers = { ...request.headers };
      delete headers.host;
      delete headers.connection;
      delete headers['content-length'];

      let body;
      if (!['GET', 'HEAD'].includes(method.toUpperCase())) {
        body = await readRequestBody(request);
      }

      const upstream = await fetch(
        targetUrl,
        body
          ? {
              method,
              headers,
              body,
              duplex: 'half',
              redirect: 'manual',
            }
          : {
              method,
              headers,
              redirect: 'manual',
            },
      );

      const outgoingHeaders = Object.fromEntries(upstream.headers.entries());
      delete outgoingHeaders['transfer-encoding'];
      delete outgoingHeaders['content-encoding'];
      delete outgoingHeaders['content-length'];
      for (const [name, value] of Object.entries(outgoingHeaders)) {
        response.setHeader(name, value);
      }
      response.setHeader('x-rehearsal-owner', owner);
      response.setHeader('x-route-switch-owner', owner);
      response.statusCode = upstream.status;
      response.end(Buffer.from(await upstream.arrayBuffer()));
    } catch (error) {
      response.statusCode = 502;
      response.setHeader('content-type', 'application/json');
      response.end(JSON.stringify({
        code: 'PROXY_ERROR',
        message: error instanceof Error ? error.message : String(error),
        path: request.url ?? '/',
      }));
    }
  });

  try {
    proxy.listen(0, '127.0.0.1');
    await once(proxy, 'listening');
    await callback({
      proxyBaseUrl: `http://127.0.0.1:${proxy.address().port}`,
      setOwner(owner) {
        if (owner !== 'legacy' && owner !== 'java') {
          throw new Error(`Unsupported route owner: ${owner}`);
        }
        currentOwner = owner;
      },
    });
  } finally {
    await closeServer(proxy);
  }
}

function createSelfTestServer(label) {
  return http.createServer((request, response) => {
    const url = new URL(request.url, `http://${request.headers.host}`);
    const payload = selfTestPayload(label, url);
    response.setHeader('content-type', 'application/json');
    response.setHeader('x-rehearsal-owner', label);
    response.statusCode = payload.status;
    response.end(JSON.stringify(payload.body));
  });
}

async function readRequestBody(request) {
  const chunks = [];
  for await (const chunk of request) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks);
}

function selfTestPayload(label, url) {
  const legacy = label === 'legacy';

  if (url.pathname === '/api/v1/curricula') {
    return {
      status: 200,
      body: {
        data: [
          legacy
            ? withoutCourses(curriculumListItem())
            : { ...curriculumListItem(), courses: [] },
        ],
        meta: { total: 2, page: 1, limit: 1, totalPages: 2 },
      },
    };
  }

  if (url.pathname === `/api/v1/curricula/${DETAIL_ID}`) {
    return {
      status: 200,
      body: curriculumDetail(),
    };
  }

  if (url.pathname === `/api/v1/curricula/${MISSING_ID}`) {
    return errorResponse(404, 'Curriculum not found', url.pathname, legacy);
  }

  return errorResponse(404, 'Resource not found', url.pathname, legacy);
}

function curriculumListItem() {
  return {
    id: DETAIL_ID,
    name: 'Computer Science 2026',
    nameEn: 'Computer Science 2026',
    nameVi: 'Chương trình Khoa học máy tính 2026',
    code: 'CS2026',
    departmentId: 'department-cs',
    academicYearId: 'ay-2026',
    semesterId: 'fall-2026',
    totalCredits: 150,
    description: 'Description CS2026',
    descriptionEn: 'Computer Science curriculum for the 2026 intake',
    descriptionVi: 'Chương trình Khoa học máy tính cho khóa tuyển sinh 2026',
    isActive: true,
    createdAt: '2026-08-20T00:00:00.000Z',
    updatedAt: '2026-08-20T00:00:00.000Z',
    department: {
      id: 'department-cs',
      name: 'Computer Science',
      nameEn: 'Computer Science',
      nameVi: 'Khoa học máy tính',
      code: 'CSE',
      description: null,
      descriptionEn: null,
      descriptionVi: null,
      facultyId: 'faculty-cs',
      isActive: true,
    },
  };
}

function curriculumDetail() {
  return {
    ...curriculumListItem(),
    courses: [
      {
        id: 'curriculum-course-2',
        curriculumId: DETAIL_ID,
        courseId: 'se401',
        year: 2,
        semester: 1,
        isMandatory: false,
      },
      {
        id: 'curriculum-course-1',
        curriculumId: DETAIL_ID,
        courseId: 'cs101',
        year: 1,
        semester: 1,
        isMandatory: true,
      },
    ],
  };
}

function withoutCourses(curriculum) {
  const copy = { ...curriculum };
  delete copy.courses;
  return copy;
}

function errorResponse(status, message, path, legacy) {
  return legacy
    ? {
        status,
        body: {
          statusCode: status,
          message,
          error: status === 404 ? 'Not Found' : 'Bad Request',
          path,
          timestamp: new Date().toISOString(),
        },
      }
    : {
        status,
        body: {
          code: `HTTP_${status}`,
          message,
          path,
          requestId: 'self-test-request-id',
          timestamp: new Date().toISOString(),
          fields: {},
        },
      };
}

function runNormalizationSelfTest() {
  const item = corpus[0];
  const legacy = {
    status: 200,
    contentType: 'application/json',
    body: {
      data: [withoutCourses(curriculumListItem())],
      meta: { total: 1, page: 1, limit: 1, totalPages: 1 },
    },
  };
  const javaEmpty = {
    status: 200,
    contentType: 'application/json',
    body: {
      data: [{ ...curriculumListItem(), courses: [] }],
      meta: { total: 1, page: 1, limit: 1, totalPages: 1 },
    },
  };
  const javaHydrated = {
    status: 200,
    contentType: 'application/json',
    body: {
      data: [{ ...curriculumListItem(), courses: curriculumDetail().courses }],
      meta: { total: 1, page: 1, limit: 1, totalPages: 1 },
    },
  };

  if (compareResponses(item, legacy, javaEmpty).result !== 'PASS') {
    throw new Error('self-test expected missing list courses and empty list courses to compare as no hydration');
  }
  if (compareResponses(item, legacy, javaHydrated).result !== 'FAIL') {
    throw new Error('self-test expected hydrated list courses to fail the no-hydration comparison');
  }
}

async function closeServer(server) {
  if (!server.listening) {
    return;
  }
  server.close();
  await once(server, 'close');
}
