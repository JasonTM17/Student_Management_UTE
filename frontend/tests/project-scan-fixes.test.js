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
    /hasCompletedGrade\(grades\.get\(enrollment\.id\)\)/,
  );
  assert.match(page, /markEdited\(/);
  // Seeding every roster row with 0 made the old guard always pass.
  assert.doesNotMatch(page, /finalGrade \?\? 0/);
  assert.doesNotMatch(page, /Number\(event\.target\.value\) \|\| 0/);
});

test('lecturer grade saves submit only edited rows, not the seeded roster', () => {
  const page = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.match(page, /\.filter\(\(enrollment\) => editedIds\.has\(enrollment\.id\)\)/);
  assert.match(page, /processScore/);
  assert.match(page, /finalExamScore/);
  assert.match(page, /updates\.length === 0/);
  assert.doesNotMatch(page, /Array\.from\(grades\.values\(\)\)/);
  assert.match(read('src/lib/api.ts'), /processScore: number; finalExamScore: number/);
  assert.doesNotMatch(read('src/lib/api.ts'), /grades: \{ enrollmentId: string; finalGrade/);
});

test('grade score inputs use canonical 50-50 components and derive the letter grade', () => {
  const page = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');

  assert.match(page, /'processScore' \| 'finalExamScore'/);
  assert.match(page, /\(update!\.processScore! \+ update!\.finalExamScore!\) \/ 2/);
  assert.match(page, /calculateGrade\(totalScore\(current\)!\)/);
  assert.doesNotMatch(page, /letterGrades/);
  assert.match(page, /step="0\.1"/);
});

test('student grade views never invent component scores and lecturer notices expose four templates', () => {
  const gradesPage = read('src/app/dashboard/grades/page.tsx');
  const detailModal = read('src/components/dashboard/GradeDetailModal.tsx');
  const notices = read('src/app/dashboard/lecturer/announcements/page.tsx');

  assert.doesNotMatch(gradesPage, /getComponentScores/);
  assert.doesNotMatch(detailModal, /2 \* finalScore|finalScore - 0\.2/);
  assert.match(detailModal, /Điểm quá trình \(ĐQT - 50%\)/);
  for (const label of ['Nghỉ học & Học bù', 'Nhắc nhở nộp bài tập lớn/đồ án', 'Lịch thi & Kiểm tra', 'Thông báo lớp học phần']) {
    assert.match(notices, new RegExp(label.replace(/[&/]/g, '\\$&')));
  }
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

test('feedback polish: registration separates searches, groups classes, and keeps drop nearby', () => {
  const page = read('src/app/dashboard/register/page.tsx');
  const messages = read('src/i18n/messages.ts');

  assert.match(page, /courseCodeSearch/);
  assert.match(page, /courseNameSearch/);
  // Sections are grouped by course; the student picks a course first and then
  // sees only that course's class groups (two-level selection).
  assert.match(page, /courseGroups/);
  assert.match(page, /selectedCourseId/);
  assert.match(page, /lg:col-span-9/);
  assert.match(page, /lg:col-span-3/);
  assert.match(page, /copy\.courseCodePlaceholder/);
  assert.match(page, /copy\.courseNamePlaceholder/);
  assert.match(page, /registered\.map\(\(item\) =>/);
  assert.match(page, /onClick=\{\(\) => void drop\(item\)\}/);
  assert.match(messages, /searchByCode/);
  assert.match(messages, /searchByName/);
  assert.match(messages, /groupSectionCount/);
});

test('feedback polish: schedule makes the timetable primary and removes summary cards', () => {
  const page = read('src/app/dashboard/schedule/page.tsx');

  assert.match(page, /gridTemplateColumns: '72px repeat\(7, minmax\(130px, 1fr\)\)'/);
  assert.match(page, /min-h-\[105px\]/);
  assert.doesNotMatch(page, /className="grid gap-4 md:grid-cols-3"/);
  assert.doesNotMatch(page, /metricToneClass/);
});

test('feedback polish: profile supports local photo preview and password visibility controls', () => {
  const page = read('src/app/dashboard/profile/page.tsx');
  const layout = read('src/app/dashboard/layout.tsx');
  const api = read('src/lib/api.ts');
  const types = read('src/types/api.ts');
  const messages = read('src/i18n/messages.ts');

  assert.match(page, /createProfileAvatarDataUrl/);
  assert.match(page, /MAX_AVATAR_DATA_URL_LENGTH = 200_000/);
  assert.match(page, /accept="image\/png,image\/jpeg,image\/webp"/);
  assert.match(page, /avatar: avatarPreview/);
  assert.match(layout, /user\?\.avatar/);
  assert.match(api, /avatar\?: string/);
  assert.match(types, /avatar\?: string \| null/);
  assert.match(page, /type=\{visible \? 'text' : 'password'\}/);
  assert.match(page, /oldPassword: false/);
  assert.match(page, /newPassword: false/);
  assert.match(page, /confirmPassword: false/);
  assert.match(messages, /photoLabel/);
  assert.match(messages, /uploadPhoto/);
  assert.match(messages, /removePhoto/);
  assert.match(messages, /photoUploadFailed/);
});

test('feedback polish: account creation and admin temporary passwords can be revealed intentionally', () => {
  const signup = read('src/app/register/page.tsx');
  const adminUsers = read('src/app/admin/users/page.tsx');

  assert.match(signup, /const \[showPassword, setShowPassword\] = useState\(false\)/);
  assert.match(signup, /type=\{showPassword \? 'text' : 'password'\}/);
  assert.match(signup, /messages\.login\.showPassword/);
  assert.match(adminUsers, /const \[showTemporaryPassword, setShowTemporaryPassword\] = useState\(false\)/);
  assert.match(adminUsers, /type=\{showTemporaryPassword \? 'text' : 'password'\}/);
  assert.match(adminUsers, /messages\.login\.hidePassword/);
});

test('feedback polish: curriculum counts stay localized and enrolled schedules use CampusCore weekdays', () => {
  const page = read('src/app/dashboard/enrollments/page.tsx');
  const grid = read('src/lib/weekly-grid.ts');

  assert.match(page, /const courseCountLabel = useCallback/);
  assert.match(page, /value: courseCountLabel\(curriculumCourses\.length\)/);
  assert.match(page, /value: courseCountLabel\(completedCurriculum\.length\)/);
  assert.match(page, /value: courseCountLabel\(inProgressCurriculum\.length\)/);
  assert.match(page, /curriculumCreditPercent/);
  assert.match(page, /curriculumStatusSummary/);
  assert.match(page, /aria-label=\{copy\.statusSummaryLabel\}/);
  assert.match(page, /aria-pressed=\{statusFilter === btn\.key\}/);
  assert.match(page, /filterNotStarted: 'Not completed'/);
  assert.match(page, /\{copy\.semesterPrefix\} \{sem\} - \{courseCountLabel\(courses\.length\)\}/);
  assert.match(page, /DB convention: 1 = Sunday\/Chủ nhật/);
  assert.match(page, /const normalizedDay = day === 0 \? 1 : day/);
  assert.doesNotMatch(page, /\$\{formatNumber\(curriculumCourses\.length\)\} môn/);
  assert.doesNotMatch(page, /\$\{formatNumber\(completedCurriculum\.length\)\} môn/);
  assert.doesNotMatch(page, /\$\{formatNumber\(inProgressCurriculum\.length\)\} môn/);
  assert.doesNotMatch(page, /\{curriculumCourses\.length\} môn/);
  assert.doesNotMatch(page, /filterNotStarted: 'Not Started'/);

  assert.match(grid, /Sunday\(1\) through Saturday\(7\)/);
  assert.match(grid, /const day = item\.dayOfWeek === 0 \? 1 : item\.dayOfWeek/);
  assert.doesNotMatch(grid, /item\.dayOfWeek === 0 \? 7/);
});

test('feedback polish: transcript semester selector is singular and chart controls expose state', () => {
  const page = read('src/app/dashboard/transcript/page.tsx');

  assert.equal((page.match(/aria-label=\{copy\.selectSemester\}/g) ?? []).length, 1);
  assert.match(page, /aria-pressed=\{gpaMode === 'cumulative'\}/);
  assert.match(page, /aria-pressed=\{gpaMode === 'semester'\}/);
  assert.match(page, /aria-pressed=\{gpaMode === 'both'\}/);
  assert.match(page, /description: `GPA, credits, and course outcomes for \$\{selectedSemesterName\}\.`/);
  assert.match(page, /clickToViewDetail: 'Midterm\/final detail'/);
  assert.doesNotMatch(page, /Click on a course to view Midterm & Final score breakdown/);
  assert.doesNotMatch(page, /Nhấn vào môn học để xem chi tiết điểm/);
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
  const adminEditor = read('src/app/admin/editor/page.tsx');
  const localizedAdminEditor = read('src/app/[locale]/admin/editor/page.tsx');

  assert.match(spec, /test\.describe\.configure\(\{\s*timeout:\s*600_000\s*\}\)/);
  assert.match(spec, /'\/dashboard\/conduct'/);
  assert.match(spec, /'\/dashboard\/editor'/);
  assert.match(spec, /'\/admin\/editor'/);
  assert.match(adminEditor, /redirect\('\/dashboard\/editor'\)/);
  assert.match(localizedAdminEditor, /redirect\(`\/\$\{locale\}\/dashboard\/editor`\)/);
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
  assert.match(thesisApiSource, /studentNumber\?: string \| null/);
  assert.match(thesisPageSource, /const memberDisplayName =/);
  assert.match(thesisPageSource, /member\.displayName \|\| member\.studentNumber \|\| member\.studentId/);
  assert.match(thesisPageSource, /const memberIdentifier = member\.studentNumber \|\| member\.studentId/);
  assert.match(thesisPageSource, /aria-pressed=\{memberType === 'internal'\}/);
  assert.match(thesisPageSource, /aria-pressed=\{memberType === 'external'\}/);
  assert.match(thesisPageSource, /htmlFor="thesis-external-member-name"/);
  assert.match(thesisPageSource, /id="thesis-external-member-contact"/);
  assert.doesNotMatch(thesisPageSource, /MSSV:[\s\S]{0,90}\{member\.studentId\}/);
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

test('registration seat badges keep contrast on selected course rows', () => {
  const page = read('src/app/dashboard/register/page.tsx');

  assert.match(page, /bg-status-success\/12 text-status-success-foreground/);
  assert.doesNotMatch(page, /bg-primary\/10 text-status-success/);
});

test('conduct score page localizes dense records and gives mobile its own cards', () => {
  const page = read('src/app/dashboard/conduct/page.tsx');

  assert.match(page, /const \{ messages, formatDate, formatNumber, locale \} = useI18n\(\)/);
  assert.match(page, /const conductCopy =/);
  assert.match(page, /const activityDetails = \(activity: ConductActivity\)/);
  assert.match(page, /const selectedActivityDetails = selectedActivity \? activityDetails\(selectedActivity\) : null/);
  assert.match(page, /copy\.historyTitle/);
  assert.match(page, /formatSemesterName\(item\.semesterName\)/);
  assert.match(page, /copy\.classificationScaleTitle/);
  assert.match(page, /selectedActivity && selectedActivityDetails/);
  assert.match(page, /copy\.certificateTitle/);
  assert.match(page, /activityCriterion\(selectedActivity\)/);
  assert.match(page, /className="divide-y divide-border\/60 sm:hidden"/);
  assert.match(page, /className="hidden overflow-x-auto sm:block"/);
  assert.doesNotMatch(page, /Lịch Sử Điểm Rèn Luyện Qua Các Học Kỳ/);
  assert.doesNotMatch(page, /Khung Xếp Loại Rèn Luyện \(Quy chế UTE\)/);
  assert.doesNotMatch(page, /aria-label="Đóng"/);
  assert.doesNotMatch(page, /selectedActivity\.(title|category|organizer|activityDate)/);
});

test('dogfood audit: admin enrollments section lookup respects the 100 cap', () => {
  const page = read('src/app/admin/enrollments/page.tsx');
  assert.match(page, /sectionsApi\.getAll\(\{\s*courseId,[\s\S]{0,300}?limit: 100,\s*\}\)/);
  assert.doesNotMatch(page, /sectionsApi\.getAll\(\{\s*courseId,\s*limit: ACADEMIC_REFERENCE_LIMIT/);
});

test('feedback polish: admin enrollment opens a student-focused detail profile', () => {
  const page = read('src/app/admin/enrollments/page.tsx');

  assert.match(page, /function getLearnerLabel\(enrollment: Enrollment\)/);
  assert.match(page, /function getStudentCodeLabel\(enrollment: Enrollment\)/);
  assert.match(page, /viewStudentLabel: \(learnerLabel: string\) =>/);
  assert.match(page, /type="button"[\s\S]{0,500}?copy\.viewStudentLabel\(learnerLabel\)/);
  assert.match(page, /title=\{copy\.detail\.title\}/);
  assert.match(page, /studentProfile: 'Student profile'/);
  assert.match(page, /studentCode: 'Student code'/);
  assert.match(page, /enrollmentId: 'Enrollment ID'/);
  assert.match(page, /getStudentCodeLabel\(selectedEnrollment\)/);
  assert.match(page, /selectedEnrollment\.finalGrade !== null &&[\s\S]{0,90}?selectedEnrollment\.finalGrade !== undefined/);
  assert.doesNotMatch(page, /selectedEnrollment\.finalGrade \?/);
});

test('feedback polish: grade breakdown opens through native controls and guards numeric scores', () => {
  const page = read('src/app/dashboard/grades/page.tsx');
  const modal = read('src/components/dashboard/GradeDetailModal.tsx');
  const spec = read('e2e/demo-polish.spec.ts');

  assert.match(page, /function isFiniteScore\(value: number \| null \| undefined\): value is number/);
  assert.match(page, /const scoreLabel = \(value: number \| null \| undefined\) =>/);
  assert.match(page, /componentSummary: 'Component scores by course'/);
  assert.match(page, /openBreakdown: \(courseCode: string, courseName: string\) =>/);
  assert.match(page, /type="button"[\s\S]{0,520}?aria-label=\{openLabel\}/);
  assert.doesNotMatch(page, /<tr[\s\S]{0,140}?onClick=\{\(\) => setSelectedRecord\(record\)\}/);
  assert.doesNotMatch(page, /record\.finalGrade !== null\s*\?\s*record\.finalGrade\.toFixed\(1\)/);

  assert.match(modal, /getStudentGradesByEnrollment\(record\.id\)/);
  assert.match(modal, /const emptyValue = locale === 'vi' \? 'Chưa có' : 'N\/A'/);
  assert.match(modal, /isFiniteScore\(record\.finalGrade\)/);
  assert.match(modal, /isFiniteScore\(item\.score\)/);
  assert.doesNotMatch(modal, /'—'/);

  assert.match(spec, /\/api\/v1\/enrollments\/my\/grades/);
  assert.match(spec, /\/api\/v1\/grades\/student-grades\/enrollment\/grade-row-midterm-final/);
  assert.match(spec, /Open grade breakdown for AI201 Applied AI/);
  assert.match(spec, /page\.keyboard\.press\('Enter'\)/);
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

test('dogfood audit: weekly timetable grid maps Sunday dayOfWeek 0 to day 1 and lecturer agenda covers full week', () => {
  const { buildWeeklyGrid } = load('src/lib/weekly-grid.ts');
  const grid = buildWeeklyGrid([
    { dayOfWeek: 0, startTime: '08:00', endTime: '10:00', courseCode: 'SE499', sectionNumber: 'SE499-01' },
    { dayOfWeek: 7, startTime: '13:00', endTime: '15:00', courseCode: 'SE498', sectionNumber: 'SE498-01' },
    { dayOfWeek: 3, startTime: '', endTime: '12:00', courseCode: 'SE497', sectionNumber: 'SE497-01' },
    { dayOfWeek: 99, startTime: '19:00', endTime: '21:00', courseCode: 'SE496', sectionNumber: 'SE496-01' },
  ]);
  assert.deepEqual(grid.slots, ['08:00', '13:00']);
  assert.equal(grid.cells['1-08:00']?.[0]?.courseCode, 'SE499');
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
