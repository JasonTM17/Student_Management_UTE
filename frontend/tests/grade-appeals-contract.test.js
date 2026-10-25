const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function transpile(relativePath) {
  return ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
}

function loadModule(relativePath, extraNamespaces = {}) {
  const output = transpile(relativePath);
  const customRequire = (id) => {
    const namespace = Object.keys(extraNamespaces).find((name) => id.includes(name));
    return namespace ? loadModule(extraNamespaces[namespace], {}) : require(id);
  };
  const moduleRecord = { exports: {} };
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, customRequire);
  return moduleRecord.exports;
}

function loadMessages() {
  return loadModule('src/i18n/messages.ts', {
    'messages-thesis-workflow': 'src/i18n/messages-thesis-workflow.ts',
    'messages-dean-overview': 'src/i18n/messages-dean-overview.ts',
  });
}

const APPEAL_CODES = [
  'GRADE_APPEAL_FORBIDDEN',
  'GRADE_APPEAL_ENROLLMENT_INELIGIBLE',
  'GRADE_APPEAL_TIMESTAMP_UNAVAILABLE',
  'GRADE_APPEAL_WINDOW_CLOSED',
  'GRADE_APPEAL_ALREADY_SUBMITTED',
  'GRADE_APPEAL_NOT_FOUND',
  'GRADE_APPEAL_NOT_PENDING',
  'GRADE_APPEAL_REASON_INVALID',
  'GRADE_APPEAL_DECISION_INVALID',
  'GRADE_APPEAL_STATUS_INVALID',
  'GRADE_APPEAL_REVIEW_REASON_INVALID',
  'GRADE_APPEAL_CORRECTED_SCORE_INVALID',
  'GRADE_UNPUBLISH_CONFLICT',
  'GRADE_UNPUBLISH_APPEAL_PENDING',
  'GRADE_UNPUBLISH_ENROLLMENTS_INVALID',
  'GRADE_UNPUBLISH_REASON_INVALID',
];

test('grade appeals have matching English and Vietnamese messages and stable error mappings', () => {
  const { en, vi } = loadMessages();
  const { campusCodeMessage } = loadModule('src/lib/campus-error.ts');
  for (const namespace of ['student', 'review', 'unpublish']) {
    assert.deepEqual(
      Object.keys(en.gradeAppeals[namespace]).sort(),
      Object.keys(vi.gradeAppeals[namespace]).sort(),
      `gradeAppeals.${namespace} copy keys diverged between locales`,
    );
  }
  for (const code of APPEAL_CODES) {
    assert.ok(en.common.campusErrors.codes[code], `en missing ${code}`);
    assert.ok(vi.common.campusErrors.codes[code], `vi missing ${code}`);
  }
  assert.match(vi.gradeAppeals.student.appeal, /phúc khảo/i);
  assert.match(vi.gradeAppeals.review.accept, /chấp nhận/i);
  assert.match(vi.gradeAppeals.unpublish.title, /gỡ công bố/i);
  for (const code of APPEAL_CODES) {
    const error = { response: { status: 409, data: { code } } };
    assert.equal(campusCodeMessage(error, en.common.campusErrors), en.common.campusErrors.codes[code]);
    assert.equal(campusCodeMessage(error, vi.common.campusErrors), vi.common.campusErrors.codes[code]);
  }
});

test('student grade detail uses the API deadline and preserves the one-appeal/cancel path', () => {
  const modal = read('src/components/dashboard/GradeDetailModal.tsx');
  assert.match(modal, /getMyGradeAppeals\(\)/);
  assert.match(modal, /getGradeAppealEligibility\(\)/);
  assert.match(modal, /submitGradeAppeal\(record\.id, reason\)/);
  assert.match(modal, /cancelGradeAppeal\(currentAppeal\.id\)/);
  assert.match(modal, /appealStatusLabel/);
  assert.match(modal, /formatDateTime\(appealDeadline, \{ timeZone: 'Asia\/Bangkok' \}\)/);
  assert.match(modal, /reason\.length < 20 \|\| reason\.length > 1000/);
  assert.match(modal, /appealConfirmed/);
  assert.match(modal, /cancelConfirmed/);
  assert.match(modal, /campusCodeMessage/);
});

test('APPEALED is official in grade editing, publication, student reads, and assistant summaries', () => {
  const lecturerGrades = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');
  const studentModal = read('src/components/dashboard/GradeDetailModal.tsx');
  const studentRead = read('../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicEnrollmentReadService.java');
  const assistant = read('../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/assistant/service/AssistantPersonalContextAdvisor.java');
  assert.match(lecturerGrades, /status === 'PUBLISHED' \|\| status === 'APPEALED'/);
  assert.match(lecturerGrades, /isOfficialGradeStatus\(enrollment\.gradeStatus\)/);
  assert.match(studentModal, /\['PUBLISHED', 'APPEALED'\]/);
  assert.match(studentRead, /"PUBLISHED"\.equals\(gradeStatus\) \|\| "APPEALED"\.equals\(gradeStatus\)/);
  assert.match(assistant, /!"PUBLISHED"\.equalsIgnoreCase\(item\.gradeStatus\(\)\)\s*&& !"APPEALED"\.equalsIgnoreCase\(item\.gradeStatus\(\)\)/);
});

test('review and unpublish pages expose the role-specific routes and specific error copy', () => {
  const review = read('src/components/academic/GradeAppealReviewPage.tsx');
  const unpublish = read('src/components/academic/AdminGradeUnpublishPanel.tsx');
  const api = read('src/lib/api.ts');
  assert.match(review, /gradeAppealsApi\.lecturerQueue/);
  assert.match(review, /gradeAppealsApi\.adminQueue/);
  assert.match(review, /reviewAsLecturer/);
  assert.match(review, /reviewAsAdmin/);
  assert.match(review, /campusCodeMessage/);
  assert.match(unpublish, /gradeAppealsApi\.unpublishSection/);
  assert.match(unpublish, /enrollmentIds: scope === 'SELECTED' \? \[\.\.\.selectedIds\]/);
  assert.match(unpublish, /campusCodeMessage/);
  assert.match(api, /'\/lecturer\/grade-appeals'/);
  assert.match(api, /'\/admin\/grade-appeals'/);
  assert.match(read(path.join('src', 'app', '[locale]', 'dashboard', 'lecturer', 'grade-appeals', 'page.tsx')),
    /export \{ default \} from '\.\.\/\.\.\/\.\.\/\.\.\/dashboard\/lecturer\/grade-appeals\/page'/);
  assert.match(read(path.join('src', 'app', '[locale]', 'admin', 'grade-appeals', 'page.tsx')),
    /export \{ default \} from '\.\.\/\.\.\/\.\.\/admin\/grade-appeals\/page'/);
});
