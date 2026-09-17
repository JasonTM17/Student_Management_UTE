const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const repoRoot = path.resolve(root, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function readFromRepo(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

// ---------------------------------------------------------------------------
// Phase 03 — the certificate surface mints nothing official and invents nobody
// ---------------------------------------------------------------------------

test('certificate preview asserts no authority the backend never granted', () => {
  const source = read('src/app/dashboard/certificates/page.tsx');

  // Fabricated serials, QR, signature claims, signer, verification link, and
  // the 60-day validity claim must all be gone.
  assert.doesNotMatch(source, /XN-ĐHCNKT/);
  assert.doesNotMatch(source, /UTE-CERT-/);
  assert.doesNotMatch(source, /Chữ ký số hợp lệ/);
  assert.doesNotMatch(source, /NGUYỄN VĂN HẢI/);
  assert.doesNotMatch(source, /\/verify\/cert\//);
  assert.doesNotMatch(source, /60 ngày/);
  assert.doesNotMatch(source, /Valid for 60/);
  assert.doesNotMatch(source, /ĐÃ KÝ ĐIỆN TỬ/);
  assert.doesNotMatch(source, /XÁC THỰC ĐIỆN TỬ/);
  assert.doesNotMatch(source, /Simulated SVG QR/);
  assert.doesNotMatch(source, /certNumber/);
  assert.doesNotMatch(source, /verificationUrl/);

  // The page is explicitly labelled as a preview where a reader cannot miss it.
  assert.match(source, /certCopy\.previewBanner/);
  assert.match(source, /certCopy\.previewBannerNote/);
  // And it truthfully says where a real certificate comes from.
  assert.match(source, /certCopy\.issuedByValue/);
});

test('certificate preview never substitutes another student for missing fields', () => {
  const source = read('src/app/dashboard/certificates/page.tsx');

  // The seeded other-student identity fallbacks are gone.
  assert.doesNotMatch(source, /24110054/);
  assert.doesNotMatch(source, /NGUYỄN VĂN A/);
  assert.doesNotMatch(source, /15\/08\/2004/);
  assert.doesNotMatch(source, /'Nam'/);
  assert.doesNotMatch(source, /'Nữ'/);

  // Missing session fields render an em-dash (or nothing), never a guess.
  assert.match(source, /studentId \|\| null/);
  assert.match(source, /studentName \|\| missing/);
  assert.match(source, /user\?\.dateOfBirth \? formatDate\(user\.dateOfBirth\) : missing/);
  assert.match(source, /genderValue \|\| missing/);
  assert.match(source, /cohort \|\| missing/);
  assert.match(source, /departmentName \|\| missing/);

  // No unconditional academic-standing claim: the session supplies no
  // enrollment or discipline field, so the draft asserts nothing.
  assert.doesNotMatch(source, /Còn đang theo học/);
  assert.doesNotMatch(source, /không bị kỷ luật/);
  assert.doesNotMatch(source, /Tình trạng học tập/);
});

test('certificate labels live in the shared dictionary for both locales', () => {
  const source = read('src/i18n/messages.ts');

  // The certificates section exists in the en and the vi block (each carries
  // its own translated copy).
  assert.match(source, /serviceBadge: 'Self-service draft • Not an issued document'/);
  assert.match(source, /serviceBadge: 'Bản thảo tự phục vụ • Chưa phải văn bản được cấp'/);
  assert.match(source, /genderMale: 'Male'/);
  assert.match(source, /genderMale: 'Nam'/);
  assert.match(source, /missingValue: '—'/);

  // The page itself no longer hardcodes certificate labels inline.
  const page = read('src/app/dashboard/certificates/page.tsx');
  assert.doesNotMatch(page, /Họ và tên sinh viên: /);
  assert.doesNotMatch(page, /Mã số sinh viên \(MSSV\): /);
  assert.doesNotMatch(page, /Giới tính: /);
  assert.doesNotMatch(page, /Nơi nhận:/);
});

// ---------------------------------------------------------------------------
// Phase 03 / STUD-P1-4 — the conduct record modal invents nothing
// ---------------------------------------------------------------------------

test('conduct activity record uses only real fields and claims no issuance', () => {
  const source = read('src/app/dashboard/conduct/page.tsx');

  assert.doesNotMatch(source, /24110054/);
  assert.doesNotMatch(source, /24110CLA/);
  assert.doesNotMatch(source, /UTE-CERT-/);
  assert.doesNotMatch(source, /'88\.3'/);
  assert.doesNotMatch(source, /certValid/);
  assert.doesNotMatch(source, /digitallyVerified/);
  assert.doesNotMatch(source, /ĐÃ XÁC THỰC ĐIỆN TỬ/);

  // Real fields only, em-dash otherwise.
  assert.match(source, /summary\?\.studentCode \|\| '—'/);
  // Explicitly labelled as a preview record.
  assert.match(source, /copy\.previewTitle/);
  assert.match(source, /copy\.previewNote/);
});

// ---------------------------------------------------------------------------
// Phase 04 / STUD-P1-7 — announcements never invent official notices
// ---------------------------------------------------------------------------

test('announcements page renders no fabricated notices on error or empty feed', () => {
  const source = read('src/app/dashboard/announcements/page.tsx');

  assert.doesNotMatch(source, /FALLBACK_OFFICIAL_NOTICES/);
  assert.doesNotMatch(source, /notice-official-/);

  // The catch surfaces the real failure; the shared ErrorState retries it.
  assert.match(source, /setError\(copy\.loadFailed\)/);
  assert.match(source, /setItems\(\[\]\)/);
  assert.match(source, /<ErrorState/);
  assert.match(source, /onRetry=\{\(\) => void fetchFeed\(\)\}/);
  // The shared EmptyState covers the empty feed.
  assert.match(source, /<EmptyState/);
});

// ---------------------------------------------------------------------------
// Phase 04 / STUD-P1-5 + STUD-P2-7 — conduct UI binds to reported state only
// ---------------------------------------------------------------------------

test('conduct badges and claims follow the status and score the API returned', () => {
  const source = read('src/app/dashboard/conduct/page.tsx');

  // Status helper: only an APPROVED row earns the approved label.
  assert.match(source, /normalized === 'APPROVED'/);
  assert.match(source, /statusView\(activeSemesterScore\?\.status\)/);
  assert.match(source, /statusView\(item\.status\)/);
  // No unconditional "approved" badge renders anywhere.
  assert.doesNotMatch(source, />\{copy\.approvedBadge\}</);
  assert.match(source, /semesterStatus\.approved \? copy\.approvedBadge/);

  // Unknown or blank classifications never read as a passing rank.
  assert.match(source, /copy\.notClassified/);
  assert.doesNotMatch(source, /return fallback \|\| copy\.good/);
  // No default-to-TOT badge class.
  assert.doesNotMatch(source, /getRankBadgeClass\(activeSemesterScore\?\.classification \|\| 'TOT'\)/);

  // Scholarship claim is gated on the real score (>= 70).
  assert.match(source, /cumulativeScore >= 70/);
  assert.match(source, /copy\.scholarshipBelow/);
});

// ---------------------------------------------------------------------------
// Phase 04 / STUD-P2-1 — the conduct page matches the backend role gate
// ---------------------------------------------------------------------------

test('conduct page is student-only so staff see the forbidden screen', () => {
  const source = read('src/app/dashboard/conduct/page.tsx');
  assert.match(source, /useRequireAuth\(\['STUDENT'\]\)/);
  assert.doesNotMatch(source, /useRequireAuth\(\['STUDENT', 'ADMIN', 'SUPER_ADMIN'\]\)/);
});

// ---------------------------------------------------------------------------
// Phase 04 / STUD-P2-3, UX-P1-4, UX-P1-5 — dialogs use the shared modal
// ---------------------------------------------------------------------------

test('shared modal traps focus and closes on Escape', () => {
  const source = read('src/components/ui/modal.tsx');
  assert.match(source, /role="dialog"/);
  assert.match(source, /aria-modal="true"/);
  assert.match(source, /event\.key === 'Escape'/);
  // Tab-cycling focus trap over the dialog contents.
  assert.match(source, /querySelectorAll<HTMLElement>\(/);
  assert.match(source, /event\.shiftKey && \(active === first/);
});

test('conduct history rows and record modal are keyboard operable', () => {
  const source = read('src/app/dashboard/conduct/page.tsx');

  // History rows act as buttons: focusable, announced, Enter/Space activatable.
  assert.match(source, /role="button"/);
  assert.match(source, /tabIndex=\{0\}/);
  assert.match(source, /e\.key === 'Enter' \|\| e\.key === ' '/);

  // The record modal uses the shared dialog component.
  assert.match(source, /import \{ Modal \} from '@\/components\/ui\/modal'/);
  assert.match(
    source,
    /selectedActivity && selectedActivityDetails && \(\n\s*<Modal\n\s+isOpen\n\s+onClose=\{\(\) => setSelectedActivity\(null\)\}/,
  );
});

test('forced password rotation gate uses the shared non-dismissible dialog', () => {
  const source = read('src/components/auth/ForcedPasswordRotationGate.tsx');
  assert.match(source, /import \{ Modal \} from '@\/components\/ui\/modal'/);
  assert.match(source, /dismissible=\{false\}/);
  // The old hand-rolled overlay is gone.
  assert.doesNotMatch(source, /fixed inset-0/);
});

// ---------------------------------------------------------------------------
// Phase 04 / STUD-P2-2 — no literal-id demo identity in the production path
// ---------------------------------------------------------------------------

test('conduct controller no longer aliases literal demo ids to a real student', () => {
  const controller = readFromRepo(
    'java-services/restful-api/src/main/java/io/campuscore/restfulapi/academic/web/AcademicConductController.java',
  );
  assert.doesNotMatch(controller, /"student-profile"\.equals/);
  assert.doesNotMatch(controller, /"student-user"\.equals/);
  assert.doesNotMatch(controller, /24110054/);
  assert.doesNotMatch(controller, /Nguyễn Tiến Sơn/);
  assert.doesNotMatch(controller, /BigDecimal\.valueOf\(88\.0\)/);

  // Demo activity fixtures moved into seed data on the normal query path.
  const migration = readFromRepo(
    'java-services/restful-api/src/main/resources/db/migration/V58__seed_conduct_activities_per_student.sql',
  );
  assert.match(migration, /conduct_activity/);
  assert.match(migration, /JOIN academic\.conduct_semester_score/);
});

// ---------------------------------------------------------------------------
// Phase 04 / LEC-P2-3, LEC-P2-4, LEC-P2-6 — lecturer-facing honesty fixes
// ---------------------------------------------------------------------------

test('grade component inputs have distinct screen-reader labels', () => {
  const source = read('src/app/dashboard/lecturer/grades/[id]/page.tsx');
  assert.match(source, /processScoreLabel:/);
  assert.match(source, /finalExamScoreLabel:/);
  assert.match(source, /aria-label=\{copy\.processScoreLabel\(/);
  assert.match(source, /aria-label=\{copy\.finalExamScoreLabel\(/);
  assert.doesNotMatch(source, /finalScoreLabel/);
});

test('thesis council matching no longer falls back to the auth user id', () => {
  const source = read('src/app/dashboard/thesis/page.tsx');
  assert.doesNotMatch(source, /user\?\.lecturerId \|\| user\?\.id/);
  assert.match(source, /const myLecturerId = user\?\.lecturerId \|\| ''/);
  // The missing profile claim is surfaced, not silently swallowed.
  assert.match(source, /messages\.thesis\.councils\.profileClaimMissing/);
});

test('removing a supervised group member asks for confirmation', () => {
  const source = read('src/components/dashboard/thesis/SupervisedGroupMembers.tsx');
  assert.match(source, /window\.confirm\(messages\.thesis\.removeMemberConfirm\)/);
});

// ---------------------------------------------------------------------------
// Phase 04 / STUD-P2-6 — the transcript GPA card is labelled cumulative
// ---------------------------------------------------------------------------

test('transcript header and GPA card describe the cumulative figure', () => {
  const source = read('src/app/dashboard/transcript/page.tsx');
  assert.match(source, /Cumulative GPA, all semesters \(4\.0 scale\)/);
  assert.match(source, /GPA hệ 4 tích lũy toàn khóa/);
  // The page description no longer claims a per-semester GPA.
  assert.doesNotMatch(source, /GPA, tín chỉ và kết quả môn học cho \$\{selectedSemesterName\}/);
  assert.doesNotMatch(source, /GPA, credits, and course outcomes for \$\{selectedSemesterName\}/);
});

// ---------------------------------------------------------------------------
// Phase 04 / ADM-P1-4 — failed branding writes are reported, not faked
// ---------------------------------------------------------------------------

test('site appearance PUT fails loudly when persistence fails', () => {
  const store = read('src/lib/site-appearance-store.ts');
  // No swallow-and-succeed: the write either lands or throws.
  assert.doesNotMatch(store, /console\.warn\('Could not persist/);
  assert.match(store, /await fs\.writeFile/);
  // Memory is only updated after a successful write.
  assert.match(store, /await fs\.writeFile[\s\S]*?memory = next/);

  const route = read('src/app/api/site-appearance/route.ts');
  assert.match(route, /status: 500/);
  assert.match(route, /SITE_APPEARANCE_NOT_SAVED/);
});
