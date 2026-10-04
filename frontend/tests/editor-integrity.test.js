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

function loadDraftCleanup(windowMock) {
  const ts = require('typescript');
  const source = read('src/components/ui/tinymce-editor.tsx');
  const ast = ts.createSourceFile('tinymce-editor.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const declarations = ast.statements.filter((node) =>
    (ts.isFunctionDeclaration(node) && node.name?.text === 'clearTinyMceAutosaveDrafts')
    || (ts.isVariableStatement(node) && node.declarationList.declarations.some((declaration) =>
      ts.isIdentifier(declaration.name) && declaration.name.text === 'AUTOSAVE_KEY_PREFIX')));
  assert.equal(declarations.length, 2, 'execute the actual exported cleanup and its prefix');
  const compiled = ts.transpileModule(declarations.map(node => node.getText(ast)).join('\n'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const moduleRecord = { exports: {} };
  Function('window', 'module', 'exports', compiled)(windowMock, moduleRecord, moduleRecord.exports);
  return moduleRecord.exports.clearTinyMceAutosaveDrafts;
}

test('discarding one TinyMCE editor preserves recovery drafts of other editors and routes', () => {
  const location = { pathname: '/vi/dashboard/editor', search: '?editId=demo-1' };
  const target = `campuscore-tinymce-${location.pathname}${location.search}-studio-`;
  const other = `campuscore-tinymce-${location.pathname}${location.search}-modal-`;
  const otherRoute = 'campuscore-tinymce-/en/dashboard/announcements-studio-';
  const entries = new Map([
    ...[target, other, otherRoute].flatMap(prefix => [[`${prefix}draft`, '<p>Unsaved</p>'], [`${prefix}time`, '1770000000000']]),
    ['unrelated-app-setting', 'retained'],
  ]);
  const storage = {
    get length() { return entries.size; },
    key: index => [...entries.keys()][index] ?? null,
    removeItem: key => entries.delete(key),
  };
  loadDraftCleanup({ location, localStorage: storage })('studio');
  assert.equal(entries.has(`${target}draft`), false);
  assert.equal(entries.has(`${target}time`), false);
  for (const prefix of [other, otherRoute]) {
    assert.equal(entries.get(`${prefix}draft`), '<p>Unsaved</p>', `${prefix} draft survives`);
    assert.equal(entries.get(`${prefix}time`), '1770000000000', `${prefix} retention clock survives`);
  }
  assert.equal(entries.get('unrelated-app-setting'), 'retained');
  assert.equal(entries.size, 5);
});

test('TinyMCE cleanup without an initialized editor never deletes any draft', () => {
  const entries = new Map([['campuscore-tinymce-/vi/dashboard/editor-studio-draft', '<p>Recover me</p>']]);
  const storage = {
    get length() { return entries.size; },
    key: index => [...entries.keys()][index] ?? null,
    removeItem: key => entries.delete(key),
  };
  loadDraftCleanup({ location: { pathname: '/vi/dashboard/editor', search: '' }, localStorage: storage })();
  assert.equal(entries.size, 1);
});

// --- T-P0-1: the editor must never reseed a live document on locale switch ---

const editorPolicy = loadTs('src/lib/editor-document.ts');
const editorPage = read('src/app/dashboard/editor/page.tsx');

test('self-hosted TinyMCE autosave tolerates a half-initialized editor during navigation', () => {
  const plugin = read('public/tinymce/plugins/autosave/plugin.min.js');
  assert.match(plugin, /if\(!t\.dom\|\|!t\.getBody\(\)\)return!0;if\(o\(e\)\)return t\.dom\.isEmpty/);
});

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

// --- K1/K2: the studio draft keeps its edit context and exports keep their title ---

const editorDraft = loadTs('src/lib/editor-document.ts');

function loadStudioDraftActions(state, storage, updates) {
  const ts = require('typescript');
  const ast = ts.createSourceFile('page.tsx', editorPage, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  const pageFunction = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'AcademicEditorPage');
  const audience = ast.statements.find(node => ts.isFunctionDeclaration(node) && node.name?.text === 'audienceFor');
  const wanted = ['saveDraft', 'handleUpdateAnnouncement'];
  const variables = pageFunction.body.statements.filter(node => ts.isVariableStatement(node)
    && node.declarationList.declarations.some(declaration => wanted.includes(declaration.name.getText(ast))));
  const hydrate = pageFunction.body.statements.find(node => ts.isExpressionStatement(node)
    && ts.isCallExpression(node.expression) && node.expression.expression.getText(ast) === 'useEffect'
    && node.expression.arguments[0].getText(ast).includes('parseStoredEditorDocument'));
  assert.equal(variables.length, 2, 'execute actual save and update callbacks');
  assert.ok(hydrate, 'execute actual storage hydration effect');
  const source = [audience.getText(ast), ...variables.map(node => node.getText(ast)),
    `const hydrateDraft = ${hydrate.expression.arguments[0].getText(ast)};`].join('\n');
  const compiled = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const scope = {
    ...state, copy: {}, locale: 'vi', isVi: true, useCallback: fn => fn,
    localStorage: { getItem: key => storage.get(key) ?? null, setItem: (key, value) => storage.set(key, value) },
    STORAGE_KEY: 'campuscore_editor_document', EDITOR_TYPE_KEY: 'campuscore_editor_engine',
    toast: { success() {}, error() {} }, parseStoredEditorDocument: editorDraft.parseStoredEditorDocument,
    findAnnouncementLengthViolation: () => null, fetchPublishedNotices: () => Promise.resolve(),
    announcementsApi: { update: async (id, payload) => { updates.push({ id, payload }); return payload; } },
    announcementLengthViolationMessage: () => '',
  };
  for (const property of ['title', 'category', 'content', 'editorType', 'editingId', 'editingVersion', 'priority', 'targetRole', 'priorityKnown', 'audienceKnown', 'lastSaved', 'isPublishingNotice']) {
    scope[`set${property[0].toUpperCase()}${property.slice(1)}`] = value => {
      state[property] = typeof value === 'function' ? value(state[property]) : value;
    };
  }
  return Function('scope', `const { ${Object.keys(scope).join(', ')} } = scope;\n${compiled}\nreturn { saveDraft, hydrateDraft, handleUpdateAnnouncement };`)(scope);
}

for (const [targetRole, priority, roles] of [['BOTH', 'HIGH', ['STUDENT', 'LECTURER']], ['STUDENT', 'URGENT', ['STUDENT']]]) {
  test(`stored ${targetRole}/${priority} edit keeps audience, priority and version on reload/update`, async () => {
    const storage = new Map();
    const updates = [];
    const state = { title: 'Targeted notice', category: 'notice', content: '<p>Edited</p>', editorType: 'tinymce',
      editingId: 'ann-targeted', editingVersion: 7, targetRole, priority, audienceKnown: true, priorityKnown: true };
    loadStudioDraftActions(state, storage, updates).saveDraft();
    const restored = { title: '', category: 'notice', content: '', editorType: 'tinymce', editingId: null,
      editingVersion: 0, targetRole: 'ALL', priority: 'NORMAL', audienceKnown: true, priorityKnown: true };
    loadStudioDraftActions(restored, storage, updates).hydrateDraft();
    await loadStudioDraftActions(restored, storage, updates).handleUpdateAnnouncement();
    assert.equal(updates.length, 1);
    assert.equal(updates[0].id, 'ann-targeted');
    assert.equal(updates[0].payload.expectedVersion, 7);
    assert.equal(updates[0].payload.priority, priority);
    assert.equal(updates[0].payload.isGlobal, false);
    assert.deepEqual(updates[0].payload.targetRoles, roles);
    assert.equal(Object.hasOwn(updates[0].payload, 'targetYears'), false, 'unexposed year filters are preserved server-side');
  });
}

test('legacy edit draft omits unestablished metadata instead of widening an existing notice', async () => {
  const storage = new Map([['campuscore_editor_document', JSON.stringify({ title: 'Legacy', content: '<p>Body</p>',
    editingId: 'ann-old', editingVersion: 4 })]]);
  const updates = [];
  const state = { title: '', category: 'notice', content: '', editorType: 'tinymce', editingId: null,
    editingVersion: 0, targetRole: 'ALL', priority: 'NORMAL', audienceKnown: true, priorityKnown: true };
  loadStudioDraftActions(state, storage, updates).hydrateDraft();
  await loadStudioDraftActions(state, storage, updates).handleUpdateAnnouncement();
  assert.equal(updates.length, 1);
  assert.equal(updates[0].payload.expectedVersion, 4);
  for (const field of ['priority', 'isGlobal', 'targetRoles', 'targetYears']) {
    assert.equal(Object.hasOwn(updates[0].payload, field), false, `${field} remains authoritative on the server`);
  }
});

test('K1 editor-document round-trips the edit context stored with a draft', () => {
  const draft = editorDraft.parseStoredEditorDocument(
    JSON.stringify({
      title: 'Thông báo học vụ',
      category: 'notice',
      content: '<p>Nội dung</p>',
      editorType: 'tinymce',
      updatedAt: '10:00:00',
      editingId: 'ann-7',
      editingVersion: 3,
    }),
  );
  assert.equal(draft.editingId, 'ann-7');
  assert.equal(draft.editingVersion, 3);
});

test('K1 legacy and malformed drafts hydrate without an edit context', () => {
  const legacy = editorDraft.parseStoredEditorDocument(
    JSON.stringify({ title: 'x', content: 'y', updatedAt: '09:00' }),
  );
  assert.equal(legacy.editingId, null);
  assert.equal(legacy.editingVersion, 0);
  assert.equal(editorDraft.parseStoredEditorDocument('{not json'), null);
  assert.equal(editorDraft.parseStoredEditorDocument(null), null);
});

test('K1 the studio persists the edit context and Publish routes to update', () => {
  assert.match(
    editorPage,
    /editingId,\s*\n\s*editingVersion,/,
    'saveDraft must store the edit context',
  );
  assert.match(
    editorPage,
    /setEditingId\(parsed\.editingId\)/,
    'hydrating a draft must restore editingId',
  );
  assert.match(
    editorPage,
    /setEditingVersion\(parsed\.editingVersion \?\? 0\)/,
    'hydrating a draft must restore the version',
  );
  assert.match(
    editorPage,
    /if \(editingId\) \{\s*\n\s*await handleUpdateAnnouncement\(\);/,
    'Publish in edit mode must update the record instead of creating a copy',
  );
});

test('K1 the two edit-mode save buttons carry distinct labels and both update', () => {
  // Both buttons still run the update path in edit mode.
  assert.match(editorPage, /onClick=\{handleUpdateAnnouncement\}/);
  assert.match(editorPage, /onClick=\{handlePublishAnnouncement\}/);
  // The blue button is the plain save; the green one publishes the revision.
  assert.match(editorPage, /editorCopy\.saving : editorCopy\.saveChanges/);
  assert.match(editorPage, /\? editorCopy\.publishing[\s\S]{0,60}\? editorCopy\.publishChanges/);
  // Neither locale may fall back to the same wording for both buttons.
  const messages = read('src/i18n/messages.ts');
  const labelsOf = (key) =>
    [...messages.matchAll(new RegExp(`${key}: '([^']+)'`, 'g'))].map((match) => match[1]);
  const saveLabels = labelsOf('saveChanges');
  const publishLabels = labelsOf('publishChanges');
  assert.ok(saveLabels.length >= 2, 'saveChanges must exist in both locales');
  assert.equal(publishLabels.length, 2, 'publishChanges must exist in both locales');
  assert.ok(
    publishLabels.every((label) => !saveLabels.includes(label)),
    'the two update buttons must not share a label',
  );
});

test('K2 the export slug keeps Vietnamese words and both export paths share it', () => {
  assert.equal(
    editorDraft.slugifyDocumentTitle('Đề cương đề tài khóa luận tốt nghiệp'),
    'de-cuong-de-tai-khoa-luan-tot-nghiep',
  );
  assert.equal(editorDraft.slugifyDocumentTitle('THÔNG BÁO HỌC VỤ'), 'thong-bao-hoc-vu');
  assert.equal(editorDraft.slugifyDocumentTitle('   '), 'academic-document');
  assert.equal(editorDraft.buildDocumentFilename('Đề tài tốt nghiệp', 'html'), 'de-tai-tot-nghiep.html');
  assert.equal(editorDraft.buildDocumentFilename('', 'html'), 'academic-document.html');
  assert.match(editorPage, /buildDocumentFilename\(title, ext\)/);
  assert.doesNotMatch(editorPage, /\[\^a-z0-9-_\]/);
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
    dashboardPage.includes('formatNumber(activeCourses.length)'),
    'the chip counts live registrations, including pending rows',
  );
});
