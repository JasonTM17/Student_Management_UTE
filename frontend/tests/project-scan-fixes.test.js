const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

test('registration E2E fixture refuses developer projects and fails closed on missing seed data', async () => {
  const { pathToFileURL } = require('node:url');
  const fixture = await import(pathToFileURL(path.join(root, '../scripts/course-e2e-fixture.mjs')).href);
  const calls = [];
  for (const project of ['student_management', 'campuscore-course-e2e', '', 'campuscore-course-e2e-../main']) {
    await assert.rejects(fixture.seedCourseE2e(project, async (args) => calls.push(args)), /disposable E2E/);
  }
  assert.equal(calls.length, 0);
  await fixture.seedCourseE2e('campuscore-course-e2e-fixture-test', async (args) => calls.push(args));
  assert.deepEqual(calls[0].slice(0, 4), ['exec', '-T', 'postgres', 'sh']);
  assert.match(calls[0][5], /ON_ERROR_STOP=1/);
  assert.equal(calls[0].at(-1), fixture.registrationWindowSql);
  assert.match(fixture.registrationWindowSql, /CURRENT_TIMESTAMP - INTERVAL '1 day'/);
  assert.match(fixture.registrationWindowSql, /CURRENT_TIMESTAMP \+ INTERVAL '1 day'/);
  assert.match(fixture.registrationWindowSql, /"semesterId" = 'semester-demo'/);
  assert.match(fixture.registrationWindowSql, /updated <> 2/);
  assert.doesNotMatch(fixture.registrationWindowSql, /SET\s+"status"|DELETE|TRUNCATE|\b20\d\d-/);
  const runner = fs.readFileSync(path.join(root, '../scripts/run-course-e2e.mjs'), 'utf8');
  assert.match(runner, /await seedCourseE2e\(projectName, compose\)/);
  assert.match(runner, /E2E_PLAYWRIGHT_PROJECT/);
  assert.match(runner, /\['chromium', 'tablet-768', 'desktop-1024', 'desktop-1440', 'mobile-390'\]/);
  assert.match(runner, /args\.push\('--project', project\)/);
  assert.ok(runner.indexOf('assertDisposableProject(projectName)') < runner.indexOf('await seedCourseE2e'));
  await assert.rejects(fixture.seedCourseE2e('campuscore-course-e2e-fixture-test', async () => {
    throw new Error('seed missing');
  }), /seed missing/);
});

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('lecturer grading guard tracks records the lecturer actually edited', () => {
  const page = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.match(
    page,
    /const \[editedIds, setEditedIds\] = useState<Set<string>>\(new Set\(\)\)/,
  );
  assert.match(
    page,
    /hasCompletedGrade\(\s*applyScoreDraft\(grades\.get\(enrollment\.id\), scoreDrafts\[enrollment\.id\]\),\s*\)/,
  );
  assert.match(page, /markEdited\(/);
  // Seeding every roster row with 0 made the old guard always pass.
  assert.doesNotMatch(page, /finalGrade \?\? 0/);
  assert.doesNotMatch(page, /Number\(event\.target\.value\) \|\| 0/);
});

test('lecturer grade saves submit only edited rows, not the seeded roster', () => {
  const page = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.match(page, /\.filter\(\(enrollment\) => editedIds\.has\(enrollment\.id\)\)/);
  assert.match(page, /applyScoreDraft\(/);
  assert.match(page, /updates\.length === 0/);
  assert.doesNotMatch(page, /Array\.from\(grades\.values\(\)\)/);
  assert.match(read('src/lib/api.ts'), /finalGrade: number \| null/);
});

test('grade score inputs keep decimal drafts until they are committed', () => {
  const page = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.match(page, /scoreDrafts/);
  assert.match(page, /value=\{\s*scoreDrafts\[enrollment\.id\] \?\?/);
  assert.match(page, /onBlur=\{\(\) => commitScoreDraft\(enrollment\.id\)\}/);
  assert.match(page, /step="0\.1"/);
});

test('semester switching cannot let stale responses overwrite newer grades', () => {
  const pages = [
    read('src/app/dashboard/grades/page.tsx'),
    read('src/app/dashboard/transcript/page.tsx'),
  ];

  for (const page of pages) {
    assert.match(page, /loadGeneration = useRef\(0\)/);
    assert.match(page, /const generation = \+\+loadGeneration\.current/);
    assert.match(page, /if \(generation !== loadGeneration\.current\) return;/);
    assert.match(page, /if \(generation === loadGeneration\.current\) \{/);
  }
});

test('admin user search commits one query per submit instead of per keystroke', () => {
  const page = read('src/app/admin/users/page.tsx');

  assert.match(page, /const \[searchInput, setSearchInput\] = useState\(''\)/);
  assert.match(page, /value=\{searchInput\}/);
  assert.match(page, /setSearchInput\(e\.target\.value\)/);
  assert.match(page, /setSearch\(searchInput\)/);
  assert.match(page, /setPage\(1\)/);
  assert.doesNotMatch(page, /value=\{search\}/);
  assert.doesNotMatch(page, /onChange=\{\(e\) => setSearch\(e\.target\.value\)\}/);
});

test('registration idempotency keeps PostgreSQL duplicate claims inside the transaction', () => {
  const service = fs.readFileSync(
    path.join(root, '../java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/registration/RegistrationService.java'),
    'utf8',
  );

  assert.match(service, /private final boolean postgres/);
  assert.ok(service.includes('ON CONFLICT (\\"ownerId\\", \\"idempotencyKey\\") DO NOTHING'));
  assert.match(service, /if \(inserted == 1\) \{\s*return null;/s);
  assert.match(service, /H2 does not implement PostgreSQL's ON CONFLICT syntax/);
});

test('published image tags are full-SHA, revision-bound, and never auto-promote latest', () => {
  const workflow = fs.readFileSync(path.join(root, '../.github/workflows/publish-ghcr.yml'), 'utf8');
  const overlay = fs.readFileSync(path.join(root, '../docker-compose.rag.override.yml'), 'utf8');

  assert.match(workflow, /type=raw,value=\$\{\{ needs\.release-gate\.outputs\.sha \}\}/);
  assert.match(workflow, /org\.opencontainers\.image\.revision=\$\{\{ needs\.release-gate\.outputs\.sha \}\}/);
  assert.match(workflow, /\.Image\.Config\.Labels/);
  assert.match(workflow, /--format '\{\{\.Manifest\.Digest\}\}'/);
  assert.doesNotMatch(workflow, /\|\s*awk/);
  assert.match(workflow, /refusing wrong-revision|revision.*not \$full_sha/s);
  assert.match(workflow, /partially published; refusing to create a cross-registry manifest/);
  assert.doesNotMatch(workflow, /promote-latest|imagetools create/);
  assert.match(overlay, /CAMPUSCORE_IMAGE_TAG:\?Set CAMPUSCORE_IMAGE_TAG/);
});

test('Playwright defaults to the collision-checked CampusCore frontend port', () => {
  const config = fs.readFileSync(path.join(root, 'playwright.config.ts'), 'utf8');
  assert.match(config, /E2E_BASE_URL\s*\?\?\s*['"]http:\/\/127\.0\.0\.1:3101['"]/);
  assert.doesNotMatch(config, /E2E_BASE_URL\s*\?\?\s*['"]http:\/\/127\.0\.0\.1:3000['"]/);
});

test('admin announcement reference lookup stays within the section API page limit', () => {
  const page = fs.readFileSync(path.join(root, 'src/app/admin/announcements/page.tsx'), 'utf8');
  assert.match(page, /sectionsApi\.getAll\(\{\s*page: 1, limit: 100\s*\}\)/);
  assert.doesNotMatch(page, /sectionsApi\.getAll\(\{\s*page: 1, limit: 200\s*\}\)/);
});

test('deep persona route matrix has a bounded timeout large enough for cold compilation', () => {
  const spec = fs.readFileSync(path.join(root, 'e2e/professional-quality.spec.ts'), 'utf8');
  assert.match(spec, /test\.describe\.configure\(\{\s*timeout:\s*600_000\s*\}\)/);
});

test('thesis lecturer-student multi-role flow provides group approval, rejection, and topic proposal', () => {
  const thesisApiSource = read('src/lib/thesis-api.ts');
  const thesisPageSource = read('src/app/dashboard/thesis/page.tsx');
  const messagesSource = read('src/i18n/messages.ts');

  // API surface
  assert.match(thesisApiSource, /approveGroup:\s*async\s*\(\s*groupId:\s*string\s*\)/);
  assert.match(thesisApiSource, /post<ThesisGroup>\(\s*`\/thesis\/groups\/\$\{groupId\}\/approve`,?\s*\)/);
  assert.match(thesisApiSource, /rejectGroup:\s*async\s*\(\s*groupId:\s*string,\s*reason:\s*string,?\s*\)/);
  assert.match(thesisApiSource, /post<ThesisGroup>\(\s*`\/thesis\/groups\/\$\{groupId\}\/reject`,\s*\{\s*reason\s*\},?\s*\)/);

  // UI Lecturer Capabilities
  assert.match(thesisPageSource, /thesisApi\.approveGroup/);
  assert.match(thesisPageSource, /thesisApi\.rejectGroup/);
  assert.match(thesisPageSource, /thesisApi\.createTopic/);
  assert.match(thesisPageSource, /isRejectModalOpen/);
  assert.match(thesisPageSource, /isProposeModalOpen/);
  assert.match(thesisPageSource, /supervisedGroupsTitle/);

  // i18n parity
  assert.match(messagesSource, /supervisedGroupsTitle/);
  assert.match(messagesSource, /proposeTopic/);
  assert.match(messagesSource, /rejectModalTitle/);
  assert.match(messagesSource, /myProposedTopics/);
  assert.match(messagesSource, /noProposedTopics/);

  // Topic selection approvalStatus guard, filtering, and listTopics status signature
  assert.match(thesisPageSource, /currentGroup\.approvalStatus !== 'APPROVED'/);
  assert.match(thesisPageSource, /topicFilter/);
  assert.match(thesisPageSource, /messages\.thesis\.myProposedTopics/);
  assert.match(thesisApiSource, /listTopics:\s*async\s*\(\s*roundId:\s*string,\s*status\?:/);
});

test('dogfood audit: registration conflicts surface the specific backend code', () => {
  const libSource = read('src/lib/campus-error.ts');
  const registerPage = read('src/app/dashboard/register/page.tsx');
  const messagesSource = read('src/i18n/messages.ts');

  assert.match(libSource, /export function campusCodeMessage/);
  assert.match(libSource, /copy\.codes\?\.\[code\]/);
  // Register enroll/drop prefer the code-specific copy over the generic kind.
  assert.doesNotMatch(registerPage, /campusErrorMessage\(cause, messages\.common\.campusErrors\)/);
  assert.match(registerPage, /campusCodeMessage\(cause, messages\.common\.campusErrors\)/);
  assert.match(messagesSource, /SCHEDULE_CONFLICT:\s*\n?\s*'This class overlaps your current timetable/);
  assert.match(messagesSource, /SCHEDULE_CONFLICT:\s*\n?\s*'Lớp này trùng lịch/);
  assert.match(messagesSource, /SECTION_FULL: 'This section has just filled up/);
  assert.match(messagesSource, /SECTION_FULL: 'Lớp vừa hết chỗ/);
});

test('dogfood audit: admin enrollments section lookup respects the 100 cap', () => {
  const page = read('src/app/admin/enrollments/page.tsx');
  assert.match(page, /sectionsApi\.getAll\(\{\s*courseId,[\s\S]{0,300}?limit: 100,\s*\}\)/);
  assert.doesNotMatch(page, /sectionsApi\.getAll\(\{\s*courseId,\s*limit: ACADEMIC_REFERENCE_LIMIT/);
});

test('dogfood audit: student group card shows the real group status', () => {
  const page = read('src/app/dashboard/thesis/page.tsx');
  // The old derived label claimed SUBMITTED whenever a topic existed, even
  // while the group was still DRAFT and the supervisor saw no approve button.
  assert.doesNotMatch(page, /currentGroup\.topicId \? messages\.thesis\.status\.SUBMITTED :/);
  assert.match(page, /messages\.thesis\.status\[currentGroup\.status\]/);
});

test('dogfood audit: profile form is gated on the loaded user and keeps local dates', () => {
  const page = read('src/app/dashboard/profile/page.tsx');
  assert.match(page, /profileFormState\(user\)/);
  assert.match(page, /if \(!user\) \{/);
  assert.doesNotMatch(page, /toISOString\(\)\.split\('T'\)\[0\]/);
  assert.match(page, /function localDateInput/);
});

test('dogfood audit: weekly timetable grid maps Sunday dayOfWeek 0 to day 7 and lecturer agenda covers full week', () => {
  const { buildWeeklyGrid } = require(path.join(root, 'src/lib/weekly-grid.ts'));
  const grid = buildWeeklyGrid([
    { dayOfWeek: 0, startTime: '08:00', endTime: '10:00', courseCode: 'SE499', sectionNumber: 'SE499-01' },
    { dayOfWeek: 7, startTime: '13:00', endTime: '15:00', courseCode: 'SE498', sectionNumber: 'SE498-01' },
    { dayOfWeek: 3, startTime: '', endTime: '12:00', courseCode: 'SE497', sectionNumber: 'SE497-01' },
    { dayOfWeek: 99, startTime: '19:00', endTime: '21:00', courseCode: 'SE496', sectionNumber: 'SE496-01' },
  ]);
  assert.deepEqual(grid.slots, ['08:00', '13:00']);
  assert.equal(grid.cells['7-08:00']?.[0]?.courseCode, 'SE499');
  assert.equal(grid.cells['7-13:00']?.[0]?.courseCode, 'SE498');
  assert.equal(grid.cells['99-19:00'], undefined);

  const lecturerSchedule = read('src/app/dashboard/lecturer/schedule/page.tsx');
  assert.doesNotMatch(lecturerSchedule, /localizedDayNames\.slice\(1,\s*6\)/);
  assert.match(lecturerSchedule, /\[1,\s*2,\s*3,\s*4,\s*5,\s*6,\s*7\]\.map/);
});

test('dogfood audit: admin user management protects against self-deletion, self-demotion, and unauthorized super-admin actions', () => {
  const page = read('src/app/admin/users/page.tsx');
  assert.match(page, /user\.id === userRecord\.id \|\| user\.email === userRecord\.email/);
  assert.match(page, /users\.length === 1 && page > 1/);
  assert.match(page, /disabled=\{isSelf \|\| !canManageTarget\}/);
  assert.match(page, /disabled=\{!canManageTarget\}/);
  assert.match(page, /disabled=\{isSelfEditing\}/);
  assert.match(page, /isRecordSuperAdmin/);
});

test('dogfood audit: thesis topic proposal wires academic departments list', () => {
  const thesis = read('src/app/dashboard/thesis/page.tsx');
  assert.match(thesis, /departmentsApi\.getAll/);
  assert.match(thesis, /departments\.map/);
  assert.match(thesis, /getLocalizedName\(locale,\s*dept,\s*dept\.name\)/);
});

test('dogfood audit: i18n, metadata, and language toggle regressions stay guarded', () => {
  const messages = read('src/i18n/messages.ts');
  const server = read('src/i18n/server.ts');
  const sitemap = read('src/app/sitemap.ts');
  const languageToggle = read('src/components/LanguageToggle.tsx');
  const thesis = read('src/app/dashboard/thesis/page.tsx');

  assert.doesNotMatch(messages, /phonePlaceholder:\s*'\+66/);
  assert.match(messages, /phonePlaceholder:\s*'\+84/);
  assert.match(server, /pathname === '\/register'/);
  assert.match(server, /messages\.meta\.register\.title/);
  assert.doesNotMatch(sitemap, /forgot-password/);
  assert.match(languageToggle, /if \(nextLocale === locale\) \{\s*return;\s*\}/);
  assert.match(thesis, /messages\.thesis\.allFieldsRequired/);
  assert.match(thesis, /messages\.thesis\.reasonTooLong/);
  assert.doesNotMatch(thesis, /'Reason must be at most 500 characters\.'/);
  assert.doesNotMatch(thesis, /'All fields are required'/);
});
