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
  // Register enroll/drop use the code-aware wrapper so students see the
  // specific business reason (e.g. SCHEDULE_CONFLICT) instead of a generic
  // conflict toast; the other pages keep the kind-level mapper.
  assert.match(register, /campusCodeMessage\(cause, messages\.common\.campusErrors\)/);
  assert.match(enrollments, /campusErrorMessage\(/);
  // Password changes report backend PASSWORD_* / INVALID_OLD_PASSWORD codes,
  // so the profile form uses the code-aware mapper like register does.
  assert.match(profile, /campusCodeMessage\(/);
  assert.match(lecturerGrades, /campusErrorMessage\(/);
  assert.doesNotMatch(register, /Java API/);
  assert.match(register, /ACTIVE_ENROLLMENT_STATUSES\.has\(enrollment\.status\)\) continue/);
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

test('academic registration and catalog failures have bilingual code copy', () => {
  const source = read('src/i18n/messages.ts');
  const viStart = source.indexOf('export const vi: I18nMessages = {');
  assert.notEqual(viStart, -1);
  const en = source.slice(source.indexOf('export const en = {'), viStart);
  const vi = source.slice(viStart);
  const codes = [
    'CAPACITY_BELOW_ENROLLED',
    'CAPACITY_EXCEEDS_ROOM',
    'CLASSROOM_INACTIVE',
    'COURSE_CREDITS_LOCKED_BY_GRADES',
    'COURSE_INACTIVE',
    'CREDIT_LIMIT_APPLICATION_DECISION_INVALID',
    'CREDIT_LIMIT_APPLICATION_NOT_FOUND',
    'CREDIT_LIMIT_APPLICATION_NOT_PENDING',
    'CREDIT_LIMIT_APPLICATION_NOTE_INVALID',
    'CREDIT_LIMIT_APPLICATION_REASON_INVALID',
    'CREDIT_LIMIT_APPLICATION_STATUS_INVALID',
    'ENROLLMENT_FORBIDDEN',
    'ENROLLMENT_NOT_ACTIVE',
    'IDEMPOTENCY_CONFLICT',
    'IDEMPOTENCY_KEY_REQUIRED',
    'INVALID_CAPACITY',
    'INVALID_CREDITS',
    'INVALID_DATE_RANGE',
    'INVALID_DATE_VALUE',
    'INVALID_SCHEDULE',
    'INVALID_SECTION_STATUS',
    'INVALID_SEMESTER_STATUS',
    'REFERENCE_NOT_FOUND',
    'RESOURCE_IN_USE',
    'SECTION_NOT_FOUND',
    'SEMESTER_DATES_OVERLAP',
    'STUDENT_NOT_FOUND',
  ];

  for (const code of codes) {
    const key = new RegExp(`\\b${code}:\\s*['\"]`);
    assert.ok(key.test(en), `English campusErrors.codes is missing ${code}`);
    assert.ok(key.test(vi), `Vietnamese campusErrors.codes is missing ${code}`);
  }

  for (const page of [
    'src/app/admin/courses/page.tsx',
    'src/app/admin/semesters/page.tsx',
    'src/app/admin/sections/page.tsx',
    'src/app/admin/credit-limit-applications/page.tsx',
  ]) {
    assert.match(read(page), /campusCodeMessage\(/, `${page} should show mapped business errors`);
  }
});

test('admin user-management guard codes have bilingual code copy', () => {
  const source = read('src/i18n/messages.ts');
  const viStart = source.indexOf('export const vi: I18nMessages = {');
  assert.notEqual(viStart, -1);
  const en = source.slice(source.indexOf('export const en = {'), viStart);
  const vi = source.slice(viStart);
  // Emitted by AdminUserMutationService and surfaced through the users page's
  // campusCodeMessage; without an entry they collapse to a wrong kind copy
  // ("no access") or a vague conflict line.
  const codes = [
    'SELF_PASSWORD_RESET_NOT_ALLOWED',
    'SELF_ROLE_CHANGE_NOT_ALLOWED',
    'SELF_DEACTIVATION_NOT_ALLOWED',
    'SELF_DELETION_NOT_ALLOWED',
    'INVALID_ACCOUNT_STATUS',
    'PROFILE_IN_USE',
    'PROFILE_HAS_HISTORY',
    'USER_IN_USE',
  ];

  // Emitted by CsrfCookieFilter on any mutating call whose token is missing or
  // stale; the 403 kind copy ("no access") reads as a permission refusal.
  const sessionCodes = ['CSRF_INVALID'];

  for (const code of sessionCodes) {
    const key = new RegExp(`\\b${code}:\\s*['\"]`);
    assert.ok(key.test(en), `English campusErrors.codes is missing ${code}`);
    assert.ok(key.test(vi), `Vietnamese campusErrors.codes is missing ${code}`);
  }

  for (const code of codes) {
    const key = new RegExp(`\\b${code}:\\s*['\"]`);
    assert.ok(key.test(en), `English campusErrors.codes is missing ${code}`);
    assert.ok(key.test(vi), `Vietnamese campusErrors.codes is missing ${code}`);
  }

  assert.match(
    read('src/app/admin/users/page.tsx'),
    /campusCodeMessage\(/,
    'admin users page should show mapped business errors',
  );
});
