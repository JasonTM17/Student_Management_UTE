const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadTs(relativePath) {
  const ts = require('typescript');
  const output = ts.transpileModule(read(relativePath), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('module', 'exports', output)(moduleRecord, moduleRecord.exports);
  return moduleRecord.exports;
}

// --- T-P0-1: the editor must never reseed a live document on locale switch ---

const editorPolicy = loadTs('src/lib/editor-document.ts');
const editorPage = read('src/app/dashboard/editor/page.tsx');

test('pristine editor without draft or edit reseeds the default document', () => {
  assert.equal(
    editorPolicy.shouldSeedDefaultEditorDocument({ hasStoredDraft: false, editingId: null }),
    true,
  );
});

test('a stored draft blocks default reseeding', () => {
  assert.equal(
    editorPolicy.shouldSeedDefaultEditorDocument({ hasStoredDraft: true, editingId: null }),
    false,
  );
});

test('an announcement in edit mode blocks default reseeding', () => {
  assert.equal(
    editorPolicy.shouldSeedDefaultEditorDocument({ hasStoredDraft: false, editingId: 'ann-1' }),
    false,
  );
});

test('studio page wires the seeding guard into a locale-independent init effect', () => {
  assert.ok(
    editorPage.includes('shouldSeedDefaultEditorDocument'),
    'page must consult the seeding policy helper',
  );
  assert.ok(
    editorPage.includes('editingIdRef.current'),
    'the guard must read the live editing id through a ref',
  );
  assert.ok(
    !/setTitle\(isVi \? 'Thông báo kế hoạch[^`]*', \s*setContent\(isVi \? DEFAULT_TINYMCE_VI : DEFAULT_TINYMCE_EN\);?\s*\}, \[isVi\]\);/.test(editorPage),
    'the locale-rekeyed reseeding effect must be gone',
  );
});

// --- A-P0-1: closing the create form must not wipe the issued credential ---

const adminUsersPage = read('src/app/admin/users/page.tsx');

test('admin user form close is split from credential dismissal', () => {
  assert.ok(
    adminUsersPage.includes('const closeFormModal = () => {'),
    'a form-only close helper must exist',
  );
  const closeFormModal = adminUsersPage.slice(
    adminUsersPage.indexOf('const closeFormModal = () => {'),
    adminUsersPage.indexOf('const closeModal = () => {'),
  );
  assert.ok(
    !closeFormModal.includes('setIssuedCredential(null)'),
    'closing the form must not clear the one-time credential',
  );
  assert.ok(
    /closeFormModal\(\);\s*\n\s*await fetchUsers\(\)/.test(adminUsersPage),
    'create/update success must close only the form so the credential modal renders',
  );
});

// --- S-P0-1 / S-P0-2: official documents carry no fabricated data ---

const transcriptPage = read('src/app/dashboard/transcript/page.tsx');

test('printed transcript carries blank signature blocks, not pre-stamped names', () => {
  assert.ok(!transcriptPage.includes('Hoàng Văn Dũng'), 'fabricated dean name must be gone');
  assert.ok(!transcriptPage.includes('Quách Thanh Hải'), 'fabricated vice rector name must be gone');
  assert.ok(!/năm 2026<\/p>/.test(transcriptPage), 'the print date must be a fill-in blank, not a fixed year');
  assert.ok(transcriptPage.includes('Ký và ghi rõ họ tên'), 'signature guidance stays');
});

test('conversion band upper bound never overlaps the next band', () => {
  const gradeScale = loadTs('src/lib/grade-scale.ts');
  assert.equal(gradeScale.bandUpperDisplay(9.0), '8.9');
  assert.equal(gradeScale.bandUpperDisplay(10.01), '10.0');
  assert.equal(gradeScale.bandUpperDisplay(8.5), '8.4');
  assert.equal(gradeScale.bandUpperDisplay(7.0), '6.9');
});

const gradeView = read('src/components/dashboard/StudentUteProfileGradeView.tsx');

test('grade overview never invents credit or score numbers', () => {
  assert.ok(!/\|\| 144/.test(gradeView), 'no fabricated 144-credit fallback');
  assert.ok(!/return 98;/.test(gradeView), 'no fabricated 98-credit benchmark');
  assert.ok(!/0\.68/.test(gradeView), 'no fabricated 68% ratio');
  assert.ok(!/gradePoint \* 2\.5/.test(gradeView), 'no invented 10-scale score from grade points');
  assert.ok(!gradeView.includes('24110CTN'), 'decorative curriculum selector removed');
  assert.ok(!gradeView.includes('isRefreshing'), 'dead refresh state removed');
  assert.ok(!gradeView.includes('RotateCw'), 'dead icon import removed');
  assert.ok(gradeView.includes('creditsKnown'), 'credit progress is gated on real numbers');
});

// --- S-P0-3: dashboard term count excludes dropped rows ---

const dashboardPage = read('src/app/dashboard/page.tsx');

test('dashboard course chip counts live registrations only', () => {
  assert.ok(
    !dashboardPage.includes('formatNumber(enrollments.length)'),
    'the raw enrollment list length (includes DROPPED/CANCELLED) must not feed the chip',
  );
  assert.ok(
    dashboardPage.includes('formatNumber(activeCourses.length + pendingCourses.length)'),
    'the chip counts active + pending rows',
  );
});
