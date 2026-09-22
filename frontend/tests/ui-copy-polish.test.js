const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('student status badges use localized labels instead of raw service enums', () => {
  const dashboard = read('src/app/dashboard/page.tsx');

  assert.match(dashboard, /enrollmentStatusLabel/);
  assert.match(dashboard, /messages\.common\.statuses/);
  assert.doesNotMatch(dashboard, /\{enrollment\.status\}/);
});

test('assistant never exposes model identifiers or unknown reason codes', () => {
  const assistantPanel = read('src/components/assistant/AssistantPanel.tsx');
  const assistantMessages = read('src/components/assistant/AssistantMessages.tsx');

  assert.match(assistantMessages, /message\.reasonCode === 'CANCELLED'/);
  assert.match(assistantMessages, /messages\.assistant\.answered/);
  for (const assistant of [assistantPanel, assistantMessages]) {
    assert.doesNotMatch(assistant, /messages\.assistant\.model\}: \{message\.model\}/);
    assert.doesNotMatch(assistant, /: message\.reasonCode;/);
  }
});

test('visible portal copy avoids known implementation jargon', () => {
  const messages = read('src/i18n/messages.ts');
  const forbiddenVisiblePhrases = [
    'workspace services',
    'dịch vụ workspace',
    'giao diện workspace',
    'demo local',
    'database đồ án',
    'client tập trung',
    'Reset token',
    'Token hết hạn',
    'Management console',
    'curated retrieval boundary',
    'AI assistant knowledge',
    'Chrome campus',
    'owner của',
    'broadcast chính',
  ];

  for (const phrase of forbiddenVisiblePhrases) {
    assert.doesNotMatch(messages, new RegExp(phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'), phrase);
  }
});

test('student and admin surfaces keep copy task-focused', () => {
  const files = [
    'src/app/dashboard/enrollments/page.tsx',
    'src/app/dashboard/schedule/page.tsx',
    'src/app/dashboard/announcements/page.tsx',
    'src/app/dashboard/lecturer/announcements/page.tsx',
    'src/app/admin/assistant-knowledge/page.tsx',
    'src/app/admin/semesters/page.tsx',
    'src/app/admin/sections/page.tsx',
    'src/app/admin/departments/page.tsx',
    'src/app/admin/courses/page.tsx',
    'src/app/admin/lecturers/page.tsx',
    'src/app/dashboard/lecturer/grades/page.tsx',
    'src/app/dashboard/lecturer/page.tsx',
    'src/app/dashboard/lecturer/schedule/page.tsx',
    'src/app/dashboard/grades/page.tsx',
    'src/app/dashboard/transcript/page.tsx',
    'src/app/admin/users/page.tsx',
    'src/app/dashboard/lecturer/grades/[id]/page.tsx',
    'src/components/thesis/StatusBadge.tsx',
  ];
  const source = files.map(read).join('\n');
  const retiredCopy = [
    'Workspace sinh viên',
    'Student workspace',
    'Không gian sinh viên',
    'protected student workspace',
    'workspace dùng chung',
    'section status',
    'catalog môn học',
    'ownership rõ ràng',
    'metadata học thuật',
    'timeline học thuật',
    'curated sources',
    'Public catalog coverage',
    'status drift',
    'Workspace giảng viên',
    'Lecturer workspace',
    'Trạng thái section',
    'Section status',
    'Không gian giảng viên',
    'workspace giảng dạy',
    'Shared notices for your teaching workspace',
  ];

  for (const phrase of retiredCopy) {
    assert.doesNotMatch(source, new RegExp(phrase.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'), phrase);
  }
  assert.doesNotMatch(source, /\{(?:enrollment|section|semester)\.status\}/);
  assert.doesNotMatch(source, /\{(?:record|enrollment)\.(?:status|gradeStatus|enrollmentStatus)\}/);
  assert.doesNotMatch(source, /\{sectionData\.status/);
});

test('student enrollment cards progressively disclose dense class details on mobile', () => {
  const page = read('src/app/dashboard/enrollments/page.tsx');

  assert.match(page, /detailsLabel: 'Chi tiết lớp'/);
  assert.match(page, /detailsLabel: 'Class details'/);
  assert.match(page, /<details className="group mt-3 sm:hidden">/);
  assert.match(page, /className="mt-3 hidden space-y-3 sm:block"/);
  assert.match(page, /className="hidden shrink-0 sm:inline-flex"/);
  assert.match(page, /className="w-full"/);
  assert.match(page, /Theo dõi chương trình đào tạo và các lớp đã đăng ký\./);
  assert.doesNotMatch(
    page,
    /Theo dõi lộ trình toàn khóa theo chương trình đào tạo và cập nhật lớp học phần đã đăng ký\./,
  );
});

test('enrollment and thesis status fallbacks stay human-readable', () => {
  const enrollmentPage = read('src/app/admin/enrollments/page.tsx');
  assert.match(enrollmentPage, /ENROLLED: 'Đã đăng ký'/);
  assert.match(enrollmentPage, /ENROLLED: 'Registered'/);
  assert.match(enrollmentPage, /messages\.common\.statuses\.UNKNOWN/);
  assert.doesNotMatch(enrollmentPage, /\?\?\s*(?:enrollment|selectedEnrollment)\.status/);

  const thesisPages = [
    read('src/components/dashboard/thesis/useThesisWorkspace.ts'),
    read('src/app/dashboard/thesis/page.tsx'),
    read('src/app/dashboard/thesis/[roundId]/page.tsx'),
  ].join('\n');
  assert.match(thesisPages, /messages\.common\.statuses\.UNKNOWN/);
  assert.doesNotMatch(thesisPages, /messages\.thesis\.status\[[^\n]+\]\s*\?\?\s*status/);
});

test('thesis workflow copy is localized and the English side carries no Vietnamese', () => {
  const VI = /[ăâêôơưđáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹ]/i;
  const namespace = read('src/i18n/messages-thesis-workflow.ts');

  // The two components previously rendered Vietnamese literals directly, so the
  // English portal showed Vietnamese regulation text.
  for (const component of [
    'src/components/thesis/ThesisWorkflowStepper.tsx',
    'src/components/thesis/ThesisRegulationGuide.tsx',
  ]) {
    const source = read(component);
    const literals = source.match(/'[^']*'/g) || [];
    const leaked = literals.filter((literal) => VI.test(literal));
    assert.deepEqual(leaked, [], `${component} still has Vietnamese literals: ${leaked.join(', ')}`);
    assert.match(source, /useI18n\(\)/);
    assert.match(source, /messages\.thesisWorkflow/);
  }

  // Both dictionaries must exist and stay in step.
  assert.match(namespace, /export const thesisWorkflowEn/);
  assert.match(namespace, /export const thesisWorkflowVi: Widen<typeof thesisWorkflowEn>/);

  // Load the real module so the assertions run against the shipped copy.
  const ts = require('typescript');
  const output = ts.transpileModule(namespace, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  const { thesisWorkflowEn, thesisWorkflowVi } = moduleRecord.exports;

  const flat = (value, trail = '') => {
    if (typeof value === 'string') return [[trail, value]];
    if (Array.isArray(value)) return value.flatMap((item, index) => flat(item, `${trail}[${index}]`));
    if (value && typeof value === 'object') {
      return Object.entries(value).flatMap(([key, child]) => flat(child, trail ? `${trail}.${key}` : key));
    }
    return [];
  };

  const english = flat(thesisWorkflowEn);
  const vietnamese = flat(thesisWorkflowVi);

  assert.ok(english.length > 60, `expected a substantial English dictionary, got ${english.length}`);
  assert.equal(vietnamese.length, english.length, 'vi and en must expose the same number of strings');
  assert.deepEqual(
    vietnamese.map(([key]) => key),
    english.map(([key]) => key),
    'vi and en must expose the same keys',
  );

  const leakedToEnglish = english.filter(([, value]) => VI.test(value));
  assert.deepEqual(
    leakedToEnglish.map(([key]) => key),
    [],
    'English thesis copy must not contain Vietnamese characters',
  );

  // Guard the inverse: the Vietnamese side must actually be Vietnamese, so a
  // copy/paste of the English strings cannot pass silently.
  const translated = vietnamese.filter(([, value]) => VI.test(value));
  assert.ok(
    translated.length > english.length / 2,
    `expected most vi strings to be Vietnamese, only ${translated.length}/${english.length} are`,
  );

  // Spot-check a few user-visible strings in both directions.
  assert.equal(thesisWorkflowEn.stepper.badge, 'Standard 5-stage process');
  assert.equal(thesisWorkflowVi.stepper.badge, 'Quy trình chuẩn 5 giai đoạn');
  assert.match(thesisWorkflowEn.stepper.stages[0].title, /Lecturer proposes a topic/);
  assert.match(thesisWorkflowVi.stepper.stages[0].title, /GV đề xuất đề tài/);
  assert.equal(thesisWorkflowEn.guide.rules[0].number, 'Article R1');
  assert.equal(thesisWorkflowVi.guide.rules[0].number, 'Điều R1');
});

test('thesis page labels come from the dictionary instead of Vietnamese literals', () => {
  const VI = /[ăâêôơưđáàảãạấầẩẫậắằẳẵặéèẻẽẹếềểễệíìỉĩịóòỏõọốồổỗộớờởỡợúùủũụứừửữựýỳỷỹ]/i;
  const page = read('src/app/dashboard/thesis/page.tsx');

  // The page used Vietnamese default values, so an English reader saw Vietnamese
  // whenever the API returned nothing (rejection reason, council name, document
  // title) and for every validation message.
  const fallbackDefaults = page.match(/\|\|\s*'([^']*)'/g) || [];
  const leakedFallbacks = fallbackDefaults.filter((entry) => VI.test(entry));
  assert.deepEqual(leakedFallbacks, [], `Vietnamese default values remain: ${leakedFallbacks.join(', ')}`);

  // Validation copy is dictionary-driven too. `setActionError('')` is the reset
  // call, so only a non-empty literal is a leak.
  assert.doesNotMatch(page, /setActionError\('[^']+'/);
  assert.match(page, /pageCopy\.defenceScoreRange/);
  assert.match(page, /pageCopy\.councilScoresPending/);
  assert.match(page, /pageCopy\.topicAlreadyFinalised/);
  assert.match(page, /pageCopy\.groupLeaderFallback/);

  // Graduation classification labels are resolved from a stable band key rather
  // than being baked into the scoring helper. The band table itself now lives
  // in grade-scale.ts (feedback item 2), so the page only carries the badge
  // styling keyed by the same stable band names.
  assert.match(page, /EXCELLENT: 'bg-emerald/);
  assert.match(page, /classifyThesisScore\(score\)/);
  assert.match(page, /pageCopy\.classification\[studentGradeInfo\.band\]/);
  assert.doesNotMatch(page, /\brank: '/);
});

test('milestone 2: meta.defaults.keywords array length parity between en and vi', () => {
  const ts = require('typescript');
  const source = read('src/i18n/messages.ts');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const customRequire = (id) => {
    if (id.includes('messages-thesis-workflow')) {
      const target = path.join(root, 'src/i18n/messages-thesis-workflow.ts');
      const modSource = fs.readFileSync(target, 'utf8');
      const modOutput = ts.transpileModule(modSource, {
        compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
      }).outputText;
      const rec = { exports: {} };
      Function('module', 'exports', modOutput)(rec, rec.exports);
      return rec.exports;
    }
    return require(id);
  };
  const moduleRecord = { exports: {} };
  Function('module', 'exports', 'require', output)(moduleRecord, moduleRecord.exports, customRequire);
  const { en, vi } = moduleRecord.exports;

  assert.equal(
    en.meta.defaults.keywords.length,
    vi.meta.defaults.keywords.length,
    'en and vi keywords arrays must have the exact same length',
  );
  assert.equal(en.meta.defaults.keywords.length, 11);
  assert.ok(en.meta.defaults.keywords.includes('Undergraduate Thesis'));
  assert.ok(en.meta.defaults.keywords.includes('HCM-UTE'));
  assert.equal(en.certificates.issuedByValue, 'Academic Affairs Office (OAA) — HCMUTE');
  assert.ok(!en.certificates.issuedByValue.includes('Phòng Đào tạo'));
  assert.equal(vi.certificates.issuedByValue, 'Phòng Đào tạo — HCMUTE');
});

test('milestone 2: component i18n localization in admin and announcements', () => {
  const adminSurface = read('src/components/admin/AdminSurface.tsx');
  assert.match(adminSurface, /useI18n/);
  assert.match(adminSurface, /isVi \? 'Chi tiết →' : 'Details →'/);
  assert.doesNotMatch(adminSurface, /<span>\s*Chi tiết &rarr;\s*<\/span>/);

  const adminFrame = read('src/components/admin/AdminFrame.tsx');
  assert.match(adminFrame, /\{locale === 'vi' \? 'PĐT' : 'OAA'\}/);
  const rawPdt = adminFrame.match(/>\s*PĐT\s*</g);
  assert.equal(rawPdt, null, 'all PĐT avatar badges must be localized');

  const editModal = read('src/components/announcements/AnnouncementEditModal.tsx');
  assert.match(editModal, /isVi\s*\?\s*'Chỉnh sửa nhanh qua giao diện quản trị Bảng tin'\s*:\s*'Quick edit via Admin Announcements'/);
  assert.match(editModal, /publishedBy \|\| \(isVi \? 'Phòng Đào tạo' : 'Office of Academic Affairs'\)/);

  const createModal = read('src/components/announcements/LecturerAnnouncementCreateModal.tsx');
  assert.match(createModal, /defaultContentVi:/);
  assert.match(createModal, /defaultContentEn:/);
  assert.match(createModal, /isVi \? PRESETS\[0\]\.defaultContentVi : PRESETS\[0\]\.defaultContentEn/);
  assert.match(createModal, /setContent\(isVi \? preset\.defaultContentVi : preset\.defaultContentEn\)/);

  const notFound = read('src/app/not-found.tsx');
  assert.match(notFound, /useI18n/);
  assert.match(notFound, /LocalizedLink/);
  assert.match(notFound, /isVi \? 'Trang không tồn tại' : 'Page Not Found'/);
  assert.match(notFound, /isVi \? 'Quay lại trang chủ' : 'Back to Home'/);
});

test('milestone 2: a11y keyboard focus indicators and accessible names', () => {
  const transcript = read('src/app/dashboard/transcript/page.tsx');
  assert.match(
    transcript,
    /<tr[\s\S]*?role="button"[\s\S]*?focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset/,
  );

  const editor = read('src/components/ui/rich-text-editor.tsx');
  assert.match(
    editor,
    /aria-label=\{isVi \? 'Hoàn tác \(Ctrl\+Z\)' : 'Undo \(Ctrl\+Z\)'\}/,
  );
  assert.match(
    editor,
    /aria-label=\{isVi \? 'Chèn liên kết \(Ctrl\+K\)' : 'Insert Link \(Ctrl\+K\)'\}/,
  );

  const feedCard = read('src/components/announcements/feed/AnnouncementFeedCard.tsx');
  const matches = feedCard.match(
    /onClick=\{handleShare\}[^>]*focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2/g,
  );
  assert.ok(matches && matches.length >= 2, 'both featured and standard share buttons must have focus rings');
});

