const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function load(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

function readSource(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

// ---------- Item 2: official conversion table ----------

test('the 10-point scale converts to the official HCMUTE letter bands', () => {
  const { letterFromScore, pointFromScore } = load('src/lib/grade-scale.ts');
  assert.equal(letterFromScore(9.5), 'A+');
  assert.equal(letterFromScore(9.0), 'A+');
  assert.equal(letterFromScore(8.7), 'A');
  assert.equal(letterFromScore(8.5), 'A');
  assert.equal(letterFromScore(8.0), 'B+');
  assert.equal(letterFromScore(7.5), 'B');
  assert.equal(letterFromScore(6.5), 'C+');
  assert.equal(letterFromScore(5.5), 'C');
  assert.equal(letterFromScore(5.0), 'D+');
  assert.equal(letterFromScore(4.0), 'D');
  assert.equal(letterFromScore(3.9), 'F');
  assert.equal(pointFromScore(9.0), 4);
  assert.equal(pointFromScore(8.0), 3.5);
  assert.equal(pointFromScore(6.5), 2.5);
  assert.equal(pointFromScore(5.0), 1.5);
  assert.equal(pointFromScore(3.9), 0);
});

test('the letter point map matches the official table, not the US-style one', () => {
  const { GRADE_POINTS, classifyThesisScore } = load('src/lib/grade-scale.ts');
  assert.equal(GRADE_POINTS['B+'], 3.5);
  assert.equal(GRADE_POINTS['C+'], 2.5);
  assert.equal(GRADE_POINTS['D+'], 1.5);
  assert.equal(GRADE_POINTS['A+'], 4);
  assert.equal(GRADE_POINTS['F'], 0);

  const excellent = classifyThesisScore(9.2);
  assert.equal(excellent.letter, 'A+');
  assert.equal(excellent.gpa4, '4.0');
  assert.equal(excellent.band, 'EXCELLENT');
  const good = classifyThesisScore(8.2);
  assert.equal(good.letter, 'B+');
  assert.equal(good.gpa4, '3.5');
  assert.equal(good.band, 'GOOD');
});

test('the thesis page classifies scores through the shared scale', () => {
  const source = readSource('src/app/dashboard/thesis/page.tsx');
  // The divergent local band table is gone; only the delegation remains.
  assert.ok(source.includes('classifyThesisScore(score)'), 'thesis page must delegate to the shared classifier');
  assert.ok(!/function getGradeClassification[\s\S]*?if \(score >= 8\.5\)[\s\S]*?letter: 'A'/.test(source.slice(0, source.indexOf('export default function ThesisPage'))), 'the local band table must be deleted');
});

test('the backend transcript map adopts the same 4.0 convention', () => {
  const source = readSource(
    '../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/service/AcademicEnrollmentReadService.java',
  );
  assert.ok(source.includes('Map.entry("B+", BigDecimal.valueOf(3.5))'), 'backend B+ must convert to 3.5');
  assert.ok(source.includes('Map.entry("C+", BigDecimal.valueOf(2.5))'), 'backend C+ must convert to 2.5');
  assert.ok(source.includes('Map.entry("D+", BigDecimal.valueOf(1.5))'), 'backend D+ must convert to 1.5');
  assert.ok(!source.includes('BigDecimal.valueOf(3.3)'), 'the US-style 3.3 point must be gone');
});

// ---------- Item 6: catalog is the only topic chooser ----------

test('the thesis page keeps no inline topic chooser', () => {
  const source = readSource('src/app/dashboard/thesis/page.tsx');
  assert.ok(!source.includes('handleCreateGroupWithTopic'), 'create-group-with-topic must be gone');
  assert.ok(!source.includes('void chooseTopic('), 'the inline chooser handler must be gone');
  // The catalog link stays as the single entry point.
  assert.ok(source.includes('/dashboard/thesis/topics'));
});

// ---------- Items 5/11: round-gated member management ----------

test('member management pre-hides its controls when the round is closed', () => {
  const component = readSource('src/components/dashboard/thesis/SupervisedGroupMembers.tsx');
  assert.ok(component.includes('roundOpen'), 'the component must accept a roundOpen prop');
  assert.ok(component.includes('membersClosedRound'), 'a closed round must show an explanation');
  const page = readSource('src/app/dashboard/thesis/page.tsx');
  assert.ok(
    page.includes("rounds.find((round) => round.id === group.roundId)?.status ===") &&
      page.includes("'REGISTRATION_OPEN'"),
    'the page must gate each group by its own round status',
  );
});

// ---------- Item 7: Word/PDF report submission ----------

test('the report form offers a Word/PDF upload alongside the link', () => {
  const page = readSource('src/app/dashboard/thesis/page.tsx');
  assert.ok(page.includes('accept=".pdf,.doc,.docx"'), 'the file input must accept Word/PDF');
  assert.ok(page.includes('submitReportFile'), 'the submit path must use the multipart endpoint');
  assert.ok(page.includes('downloadReportFile'), 'an attached document must be downloadable');
  assert.ok(page.includes('fileTooLarge'), 'oversize documents need a client-side message');
  const client = readSource('src/lib/thesis-api.ts');
  assert.ok(client.includes("'/thesis/groups/' + groupId + '/report/file'"), 'the API client must target the file endpoint');
  const policy = readSource(
    '../java-services/restful-api/src/main/java/io/campuscore/restfulapi/thesis/service/ReportFilePolicy.java',
  );
  // Content validation: the magic bytes must agree with the extension.
  assert.ok(policy.includes("'%', 'P', 'D', 'F'"), 'PDF signature check');
  assert.ok(policy.includes('0x4B, 0x03, 0x04'), 'DOCX (ZIP) signature check');
  assert.ok(policy.includes('(byte) 0xD0, (byte) 0xCF'), 'DOC (OLE2) signature check');
  const migration = readSource('../java-services/restful-api/src/main/resources/db/migration/V44__thesis_report_file_upload.sql');
  assert.ok(migration.includes('file_data'), 'V44 must add the document column');
});

test('the report-file browser verifier opens both first-submit and update states', () => {
  const verifier = readSource('scripts/verify-report-file.mjs');
  assert.ok(
    verifier.includes('const REPORT_FORM_TRIGGER = /^(?:Nộp|Cập nhật) báo cáo$/;'),
    'the verifier must recognize the first-submit and update labels',
  );
  assert.ok(
    verifier.includes('name: REPORT_FORM_TRIGGER'),
    'the report-form locator must use the shared two-state trigger',
  );
});

test('the local Mailpit profile does not require unsupported STARTTLS', () => {
  const compose = readSource('../docker-compose.yml');
  assert.ok(
    compose.includes('SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED: ${SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED:-false}'),
    'the local SMTP profile must explicitly disable required STARTTLS for Mailpit',
  );
});

// ---------- Item 10: field-level validation messages ----------

test('the signup form renders per-field validation errors', () => {
  const source = readSource('src/app/register/page.tsx');
  assert.ok(source.includes('role="alert"'), 'field errors must be announced');
  assert.ok(source.includes('aria-invalid'), 'the invalid field must be flagged to assistive tech');
  assert.ok(source.includes('passwordShort'), 'the password rule mirrors the server');
  assert.ok(source.includes('emailInvalid'), 'the email rule mirrors the server');
});

test('the course registration page surfaces failures inline, not only as toasts', () => {
  const source = readSource('src/app/dashboard/register/page.tsx');
  assert.ok(source.includes('aria-live'), 'inline errors must live-announce');
  assert.ok(source.includes('actionError'), 'enrollment failures set the inline region');
});

// ---------- Finding A: mail endpoints are no longer anonymous ----------

test('mail send endpoints require staff roles', () => {
  const security = readSource(
    '../java-services/restful-api/src/main/java/io/campuscore/restfulapi/security/SecurityConfig.java',
  );
  assert.ok(!security.includes('"/api/v1/mail/**"'), 'the blanket mail permitAll must be gone');
  const controller = readSource(
    '../java-services/restful-api/src/main/java/io/campuscore/restfulapi/mail/web/MailController.java',
  );
  assert.ok(controller.includes("@PreAuthorize(\"hasAnyRole('ADMIN','LECTURER')\")"), 'send endpoints are staff-only');
  assert.ok(controller.includes("@PreAuthorize(\"hasRole('ADMIN')\")"), 'the SMTP probe is admin-only');
});

// ---------- Finding B: the CI Flyway gate is self-maintaining ----------

test('the CI gate derives the expected schema boundary from the migrations', () => {
  const workflow = readSource('../.github/workflows/ci.yml');
  assert.ok(workflow.includes("sed -E 's/^V([0-9]+)__[^.]*\\.sql/\\1/p'"), 'the gate must derive the latest version');
  assert.ok(!workflow.includes('= "39"'), 'the stale literal boundary must be gone');
});

// ---------- Item 1: search prompt before catalog display ----------

test('the course registration page requires search query before showing courses', () => {
  const source = readSource('src/app/dashboard/register/page.tsx');
  assert.ok(source.includes('const hasSearchQuery ='), 'must have hasSearchQuery guard');
  assert.ok(source.includes('!hasSearchQuery ? ('), 'must guard catalog with search prompt');
  assert.ok(source.includes('searchPromptTitle'), 'must render search prompt title');
});

// ---------- Item 2: grade point conversion fallback ----------

test('resolveGradePoint falls back gracefully to letter grade and 10-scale score', () => {
  const { resolveGradePoint } = load('src/lib/grade-scale.ts');
  assert.equal(resolveGradePoint({ gradePoint: null, letterGrade: 'C+' }), 2.5);
  assert.equal(resolveGradePoint({ gradePoint: null, letterGrade: 'B+' }), 3.5);
  assert.equal(resolveGradePoint({ gradePoint: null, letterGrade: null, finalGrade: 6.7 }), 2.5);
  assert.equal(resolveGradePoint({ gradePoint: null, letterGrade: null, finalGrade: 9.2 }), 4.0);
  assert.equal(resolveGradePoint({ gradePoint: 3.5, letterGrade: 'B+' }), 3.5);
});

// ---------- Item 3: 4.0 GPA chart contains only 4.0 curves ----------

test('GpaTrendChart does not plot 10-scale curve on the 4.0 axis', () => {
  const source = readSource('src/components/dashboard/TranscriptCharts.tsx');
  assert.ok(!source.includes('avgTenScale'), 'GpaTrendChart must not contain 10-scale series');
  assert.ok(source.includes('TenScaleTrendChart'), '10-scale is separated into its own chart');
});

// ---------- Item 4: conduct certificate contains no QR code ----------

test('conduct certificate modal contains no QR code', () => {
  const source = readSource('src/app/dashboard/conduct/page.tsx');
  assert.ok(!source.includes('QrCode'), 'must not render QrCode icon');
  assert.ok(!source.includes('XÁC THỰC SỐ UTE'), 'must not render old QR verification text');
  assert.ok(source.includes('digitallyVerified'), 'retains modern digital verification badge');
});

// ---------- Item 8: profile names cannot be edited ----------

test('profile first and last name fields are disabled', () => {
  const source = readSource('src/app/dashboard/profile/page.tsx');
  assert.ok(source.includes('id="profile-first-name"'), 'first name input exists');
  assert.ok(source.includes('id="profile-last-name"'), 'last name input exists');
  assert.ok(source.includes('nameManagedHint'), 'has managed hint for name');
});

// ---------- Items 9 & 10: lecturer grading import and validation ----------

test('lecturer grading page includes Excel import and invalid score alert', () => {
  const source = readSource('src/app/dashboard/lecturer/grades/[id]/page.tsx');
  assert.ok(source.includes('GradeImportPanel'), 'must include GradeImportPanel');
  assert.ok(source.includes('scoreErrors'), 'must track scoreErrors state');
  assert.ok(source.includes('invalidScore'), 'must show invalidScore message');
});

