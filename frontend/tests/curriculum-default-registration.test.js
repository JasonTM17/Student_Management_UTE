const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadMessages() {
  const source = read('src/i18n/messages.ts');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, (id) => {
    if (id === '@/i18n/messages-thesis-workflow') return { thesisWorkflowEn: {}, thesisWorkflowVi: {} };
    if (id === '@/i18n/messages-dean-overview') return { deanOverviewEn: {}, deanOverviewVi: {} };
    return {};
  });
  return moduleRecord.exports;
}

test('curriculum plan API client exposes the student and admin contract routes', () => {
  const api = read('src/lib/api.ts');
  assert.match(api, /'\/me\/registration\/curriculum-plan'/);
  assert.match(api, /'\/me\/registration\/curriculum-plan\/register'/);
  assert.match(api, /'\/admin\/registration\/curriculum-defaults\/dry-run'/);
  assert.match(api, /'\/admin\/registration\/curriculum-defaults\/apply'/);
  assert.match(api, /'\/admin\/registration\/curriculum-defaults\/runs'/);
  for (const state of [
    'ALREADY_REGISTERED', 'REGISTERABLE', 'BLOCKED_PREREQUISITE',
    'NO_OPEN_SECTION', 'FULL', 'CONFLICT', 'DISMISSED',
  ]) {
    assert.ok(api.includes(`'${state}'`), `API state ${state} is declared`);
  }
});

test('student panel and admin page keep accessible route and control contracts', () => {
  const registerPage = read('src/app/dashboard/register/page.tsx');
  const adminPage = read('src/app/admin/registration/curriculum-defaults/page.tsx');
  assert.match(registerPage, /role="region"[^>]+aria-labelledby="curriculum-plan-title"/);
  assert.match(registerPage, /data-testid="curriculum-plan-panel"/);
  assert.match(registerPage, /aria-live="polite"/);
  assert.match(registerPage, /aria-label=\{curriculumPlanCopy\.chooseYear\}/);
  assert.match(registerPage, /aria-label=\{curriculumPlanCopy\.chooseSemester\}/);
  assert.match(adminPage, /htmlFor="curriculum-defaults-semester"/);
  assert.match(adminPage, /data-testid="curriculum-defaults-panel"/);
  assert.match(registerPage, /focus-visible:ring-2/);
  assert.match(adminPage, /focus-visible:ring-2/);
  assert.match(adminPage, /onClick=\{\(\) => void run\('dry-run'\)\}/);
  assert.match(adminPage, /onClick=\{\(\) => void run\('apply'\)\}/);
  assert.ok(fs.existsSync(path.join(root, 'src/app/[locale]/admin/registration/curriculum-defaults/page.tsx')));
});

test('curriculum course links sync the catalog search after soft navigation', () => {
  const registerPage = read('src/app/dashboard/register/page.tsx');
  assert.match(registerPage, /planSearchHref = .*\?q=\$\{encodeURIComponent\(course\.courseCode\)\}/);
  assert.match(registerPage, /lastSearchParamRef = useRef\(searchParams\.get\('q'\) \?\? ''\)/);
  assert.match(registerPage, /const routeSearchQuery = searchParams\.get\('q'\) \?\? ''/);
  assert.match(registerPage, /setSearchText\(routeSearchQuery\)/);
  assert.match(registerPage, /searchInput\.focus\(\{ preventScroll: true \}\)/);
  assert.match(registerPage, /searchInput\.scrollIntoView\(\{ block: 'center', behavior: 'smooth' \}\)/);
});

test('English and Vietnamese curriculum copy has key parity and meaningful Vietnamese text', () => {
  const { en, vi } = loadMessages();
  const sameKeys = (left, right, label) => {
    assert.deepEqual(Object.keys(left).sort(), Object.keys(right).sort(), `${label} key parity`);
  };
  sameKeys(en.courseRegistration.curriculumPlan, vi.courseRegistration.curriculumPlan, 'student plan');
  sameKeys(en.admin.curriculumDefaults, vi.admin.curriculumDefaults, 'admin panel');
  assert.equal(typeof en.adminShell.curriculumDefaults, 'string');
  assert.equal(typeof vi.adminShell.curriculumDefaults, 'string');
  assert.match(vi.courseRegistration.curriculumPlan.title, /CTĐT/);
  assert.match(vi.courseRegistration.curriculumPlan.unknownTerm, /chọn năm và học kỳ/i);
  assert.match(vi.admin.curriculumDefaults.title, /đăng ký/i);
  assert.match(vi.common.campusErrors.codes.CURRICULUM_TERM_UNKNOWN, /học kỳ/i);
  assert.match(en.common.campusErrors.codes.CURRENT_SEMESTER_NOT_FOUND, /current semester/i);
  assert.match(vi.common.campusErrors.codes.CURRENT_SEMESTER_NOT_FOUND, /học kỳ hiện tại/i);
});

test('default source markers reach the student timetable and enrollment surfaces', () => {
  assert.match(read('src/app/dashboard/schedule/page.tsx'), /registrationSource === 'CURRICULUM_DEFAULT'/);
  assert.match(read('src/components/schedule/weekly-grid.tsx'), /agendaItem\.curriculumDefault/);
  assert.match(read('src/app/dashboard/enrollments/page.tsx'), /registrationSource === 'CURRICULUM_DEFAULT'/);
});
