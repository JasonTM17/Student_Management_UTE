const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const TECHNICAL = /Java API|RESTful|PostgreSQL|OpenAPI|Flyway|\/api\/v1/;
const ENVELOPE_PASSTHROUGH = /response\?\.data\?\.message/;

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function walk(relativeDirectory) {
  const directory = path.join(root, relativeDirectory);
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const relativePath = path.join(relativeDirectory, entry.name);
    if (entry.isDirectory()) return walk(relativePath);
    return /\.(tsx?|jsx?)$/.test(entry.name) ? [relativePath] : [];
  });
}

async function loadCampusErrorModule() {
  const source = fs.readFileSync(path.join(root, 'src/lib/campus-error.ts'), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: 'campus-error.ts',
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('general-user page and message sources ban technical internals', () => {
  const files = [
    'src/i18n/messages.ts',
    'src/app/page.tsx',
    'src/app/login/page.tsx',
    'src/app/opengraph-image.tsx',
    'src/app/twitter-image.tsx',
    path.join('src', 'app', 'social-image', '[locale]', 'route.tsx'),
    'src/lib/campus-error.ts',
    ...walk('src/app/dashboard'),
    ...walk('src/components/auth'),
    ...walk('src/components/home'),
    ...walk('src/components/dashboard'),
  ];
  const source = files.map(read).join('\n');

  assert.doesNotMatch(source, TECHNICAL);
  assert.match(read('src/i18n/messages.ts'), /campusErrors:/);
  assert.match(read('src/i18n/messages.ts'), /One academic portal/);
  assert.match(read('src/i18n/messages.ts'), /Một cổng học vụ/);
});

test('student and lecturer pages map failures instead of backend envelope fields', () => {
  const dashboard = walk('src/app/dashboard').map(read).join('\n');
  const register = read('src/app/dashboard/register/page.tsx');
  const enrollments = read('src/app/dashboard/enrollments/page.tsx');
  const profile = read('src/app/dashboard/profile/page.tsx');
  const lecturerGrades = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.doesNotMatch(dashboard, ENVELOPE_PASSTHROUGH);
  assert.match(register, /campusErrorMessage\(/);
  assert.match(enrollments, /campusErrorMessage\(/);
  assert.match(profile, /campusErrorMessage\(/);
  assert.match(lecturerGrades, /campusErrorMessage\(/);
  assert.doesNotMatch(register, /Java API/);
  assert.match(register, /if \(enrollment\.status === 'DROPPED'\) continue/);
});

test('demo thesis topic copy is rewritten to campus language', () => {
  const repoRoot = path.resolve(root, '..');
  const rewrite = fs.readFileSync(
    path.join(
      repoRoot,
      'java-services',
      'restful-api',
      'src',
      'main',
      'resources',
      'db',
      'migration',
      'V13__campus_facing_thesis_topic_copy.sql',
    ),
    'utf8',
  );
  assert.match(rewrite, /UPDATE thesis\.thesis_topic/);
  assert.match(rewrite, /22222222-2222-2222-2222-222222222201/);
  assert.doesNotMatch(rewrite, TECHNICAL);
});

test('shipped campusErrorMessage maps representative payloads without envelope text', async () => {
  // Keep this behavior test runnable on the Node 20 CI runner, which does not
  // natively load TypeScript files, while still executing the shipped module.
  const { campusErrorKind, campusErrorMessage, campusErrorCode } = await loadCampusErrorModule();
  const copy = {
    network: 'NETWORK_COPY',
    validation: 'VALIDATION_COPY',
    conflict: 'CONFLICT_COPY',
    unauthorized: 'UNAUTHORIZED_COPY',
    forbidden: 'FORBIDDEN_COPY',
    notFound: 'NOT_FOUND_COPY',
    server: 'SERVER_COPY',
    unknown: 'UNKNOWN_COPY',
  };
  const envelope = {
    message: 'SQLException at /api/v1/enrollments Flyway PostgreSQL OpenAPI Java API',
    code: 'ORG_HIBERNATE_EXCEPTION',
    path: '/api/v1/enrollments/enroll',
    requestId: 'req-1',
    status: 500,
  };
  const payloads = [
    { error: { code: 'ERR_NETWORK' }, kind: 'network' },
    { error: { response: { status: 400, data: envelope } }, kind: 'validation' },
    { error: { response: { status: 409, data: envelope } }, kind: 'conflict' },
    { error: { response: { status: 500, data: envelope } }, kind: 'server' },
  ];

  assert.equal(
    campusErrorCode({ response: { status: 409, data: { code: 'WINDOW_CLOSED' } } }),
    'WINDOW_CLOSED',
  );

  for (const { error, kind } of payloads) {
    assert.equal(campusErrorKind(error), kind);
    const text = campusErrorMessage(error, copy);
    assert.equal(text, copy[kind]);
    assert.doesNotMatch(
      text,
      /Java API|RESTful|PostgreSQL|OpenAPI|Flyway|\/api\/v1|SQLException|ORG_HIBERNATE|req-1|\b500\b/,
    );
  }
});
