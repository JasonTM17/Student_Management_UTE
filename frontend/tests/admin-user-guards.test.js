const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

async function loadGuardsModule() {
  const source = fs.readFileSync(path.join(root, 'src/lib/admin-user-guards.ts'), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.ESNext,
      target: ts.ScriptTarget.ES2022,
    },
    fileName: 'admin-user-guards.ts',
  });
  return import(`data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`);
}

test('student and lecturer accounts are manageable by a plain administrator', async () => {
  const { isPrivilegedAccountTarget } = await loadGuardsModule();
  assert.equal(isPrivilegedAccountTarget(['STUDENT']), false);
  assert.equal(isPrivilegedAccountTarget(['LECTURER']), false);
  assert.equal(isPrivilegedAccountTarget([]), false);
  assert.equal(isPrivilegedAccountTarget(null), false);
  assert.equal(isPrivilegedAccountTarget(undefined), false);
});

test('ADMIN, SUPER_ADMIN, TRUONG_KHOA and TRUONG_BO_MON targets are privileged', async () => {
  const { isPrivilegedAccountTarget } = await loadGuardsModule();
  // Mirrors AdminUserMutationService.SUPER_ADMIN_ONLY_ROLES.
  assert.equal(isPrivilegedAccountTarget(['ADMIN']), true);
  assert.equal(isPrivilegedAccountTarget(['SUPER_ADMIN']), true);
  assert.equal(isPrivilegedAccountTarget(['TRUONG_KHOA']), true);
  assert.equal(isPrivilegedAccountTarget(['TRUONG_BO_MON']), true);
});

test('privilege wins over any unprivileged role on the same account', async () => {
  const { isPrivilegedAccountTarget } = await loadGuardsModule();
  assert.equal(isPrivilegedAccountTarget(['LECTURER', 'TRUONG_KHOA']), true);
  assert.equal(isPrivilegedAccountTarget(['LECTURER', 'TRUONG_BO_MON']), true);
  assert.equal(isPrivilegedAccountTarget(['STUDENT', 'ADMIN']), true);
});

test('role names are normalized before matching', async () => {
  const { isPrivilegedAccountTarget } = await loadGuardsModule();
  assert.equal(isPrivilegedAccountTarget([' truong_khoa ']), true);
  assert.equal(isPrivilegedAccountTarget(['admin']), true);
});

test('custom non-system roles are privileged targets like the reserved roles', async () => {
  const { isPrivilegedAccountTarget } = await loadGuardsModule();
  assert.equal(isPrivilegedAccountTarget(['AUDITOR']), true);
  assert.equal(isPrivilegedAccountTarget(['STUDENT', 'AUDITOR']), true);
  assert.equal(isPrivilegedAccountTarget(['']), false);
  assert.equal(isPrivilegedAccountTarget(['  ']), false);
});

test('primaryManagedRole picks the highest managed role regardless of array order', async () => {
  const { primaryManagedRole } = await loadGuardsModule();
  assert.equal(primaryManagedRole(['STUDENT']), 'STUDENT');
  assert.equal(primaryManagedRole(['LECTURER']), 'LECTURER');
  assert.equal(primaryManagedRole(['LECTURER', 'TRUONG_BO_MON']), 'TRUONG_BO_MON');
  assert.equal(primaryManagedRole(['TRUONG_BO_MON', 'LECTURER']), 'TRUONG_BO_MON');
  assert.equal(primaryManagedRole(['LECTURER', 'TRUONG_KHOA']), 'TRUONG_KHOA');
  assert.equal(primaryManagedRole(['TRUONG_KHOA', 'LECTURER']), 'TRUONG_KHOA');
  assert.equal(primaryManagedRole(['LECTURER', 'ADMIN']), 'ADMIN');
  assert.equal(primaryManagedRole(['STUDENT', 'ADMIN', 'SUPER_ADMIN']), 'SUPER_ADMIN');
  assert.equal(primaryManagedRole(['ADMIN', 'SUPER_ADMIN']), 'SUPER_ADMIN');
});

test('primaryManagedRole normalizes case and whitespace', async () => {
  const { primaryManagedRole } = await loadGuardsModule();
  assert.equal(primaryManagedRole([' truong_khoa ', 'lecturer']), 'TRUONG_KHOA');
  assert.equal(primaryManagedRole(['admin']), 'ADMIN');
});

test('primaryManagedRole returns null for custom-only or empty role sets', async () => {
  const { primaryManagedRole } = await loadGuardsModule();
  assert.equal(primaryManagedRole(['AUDITOR']), null);
  assert.equal(primaryManagedRole(['AUDITOR', 'ROLE_X']), null);
  assert.equal(primaryManagedRole([]), null);
  assert.equal(primaryManagedRole(null), null);
  assert.equal(primaryManagedRole(undefined), null);
});

async function submitExistingUser({ roles, nextRole, self = false, needsLecturerProfile = false, needsStudentProfile = false, fields = {} }) {
  const { primaryManagedRole } = await loadGuardsModule();
  const primary = primaryManagedRole(roles);
  const source = fs.readFileSync(path.join(root, 'src/app/admin/users/page.tsx'), 'utf8');
  const tree = ts.createSourceFile('page.tsx', source, ts.ScriptTarget.Latest, true, ts.ScriptKind.TSX);
  let handler;
  function visit(node) {
    if (ts.isVariableDeclaration(node) && node.name.getText(tree) === 'handleSubmit') handler = node.initializer;
    ts.forEachChild(node, visit);
  }
  visit(tree);
  assert.ok(handler && ts.isArrowFunction(handler), 'Actual account submit handler required');
  const updates = [];
  const errors = [];
  const noop = () => {};
  const dependencies = {
    editingUser: { id: 'target', email: 'target@example.invalid', roles },
    user: { id: self ? 'target' : 'actor', email: self ? 'target@example.invalid' : 'actor@example.invalid' },
    formData: { firstName: 'Renamed', lastName: 'Synthetic', status: 'ACTIVE', role: nextRole ?? primary ?? 'STUDENT', employeeId: '', departmentId: '', studentId: '', curriculumId: '', year: 2, ...fields },
    editingPrimaryRole: primary, roleUnsupported: primary === null,
    editNeedsStudentFields: needsStudentProfile, editNeedsLecturerFields: needsLecturerProfile,
    setFormError: value => { if (value) errors.push(value); },
    setIsSaving: noop, setFieldErrors: value => { if (Object.keys(value).length) errors.push(value); },
    usersApi: { update: async (id, payload) => updates.push({ id, payload }) },
    toast: { success: noop, error: noop },
    copy: { updated: 'Updated', saveFailed: 'Failed', errors: { employeeIdRequired: 'Employee ID required', departmentRequired: 'Department required' } },
    locale: 'en', departments: [], curricula: [], closeFormModal: noop, fetchUsers: async () => {},
    campusErrorMessage: () => 'Failed', messages: { common: { campusErrors: {} } },
  };
  const output = ts.transpileModule(`(${handler.getText(tree)})`, {
    compilerOptions: { target: ts.ScriptTarget.ES2020, module: ts.ModuleKind.None },
  }).outputText;
  const submit = Function(...Object.keys(dependencies), `return ${output}`)(...Object.values(dependencies));
  await submit({ preventDefault() {} });
  return { updates, errors };
}

for (const [name, roles] of [
  ['department head', ['LECTURER', 'TRUONG_BO_MON']],
  ['reversed department head', ['TRUONG_BO_MON', 'LECTURER']],
  ['mixed managed roles', ['LECTURER', 'ADMIN']],
  ['dean', ['LECTURER', 'TRUONG_KHOA']],
]) {
  test(`name-only account edit preserves unchanged ${name} roles by omitting a conversion`, async () => {
    const { updates, errors } = await submitExistingUser({ roles });
    assert.deepEqual(errors, []);
    assert.deepEqual(updates, [{ id: 'target', payload: {
      firstName: 'Renamed', lastName: 'Synthetic', status: 'ACTIVE',
    } }]);
  });
}

test('custom-only account edit still omits unsupported role conversion', async () => {
  const { updates, errors } = await submitExistingUser({ roles: ['AUDITOR'] });
  assert.deepEqual(errors, []);
  assert.equal(updates.length, 1);
  assert.equal(Object.hasOwn(updates[0].payload, 'role'), false);
});

test('explicit account role change still sends the selected conversion', async () => {
  const { updates, errors } = await submitExistingUser({ roles: ['LECTURER'], nextRole: 'ADMIN' });
  assert.deepEqual(errors, []);
  assert.equal(updates.length, 1);
  assert.equal(updates[0].payload.role, 'ADMIN');
});

test('role conversion missing official lecturer fields stays blocked before update', async () => {
  const { updates, errors } = await submitExistingUser({ roles: ['STUDENT'], nextRole: 'LECTURER', needsLecturerProfile: true });
  assert.deepEqual(updates, []);
  assert.deepEqual(errors, [{ employeeId: 'Employee ID required', departmentId: 'Department required' }]);
});

test('self role change stays blocked before update', async () => {
  const { updates, errors } = await submitExistingUser({ roles: ['ADMIN'], nextRole: 'SUPER_ADMIN', self: true });
  assert.deepEqual(updates, []);
  assert.deepEqual(errors, ['You cannot change your own system role.']);
});

test('unchanged head role carries valid missing lecturer profile without a conversion', async () => {
  const { updates, errors } = await submitExistingUser({
    roles: ['LECTURER', 'TRUONG_BO_MON'], needsLecturerProfile: true,
    fields: { employeeId: 'SYNTHETIC-EMPLOYEE', departmentId: 'synthetic-department' },
  });
  assert.deepEqual(errors, []);
  assert.deepEqual(updates, [{ id: 'target', payload: {
    firstName: 'Renamed', lastName: 'Synthetic', status: 'ACTIVE',
    employeeId: 'SYNTHETIC-EMPLOYEE', departmentId: 'synthetic-department',
  } }]);
});

test('unchanged student role carries valid missing student profile without a conversion', async () => {
  const { updates, errors } = await submitExistingUser({
    roles: ['STUDENT'], needsStudentProfile: true,
    fields: { studentId: 'SYNTHETIC-STUDENT', curriculumId: 'synthetic-curriculum', year: 3 },
  });
  assert.deepEqual(errors, []);
  assert.deepEqual(updates, [{ id: 'target', payload: {
    firstName: 'Renamed', lastName: 'Synthetic', status: 'ACTIVE',
    studentId: 'SYNTHETIC-STUDENT', curriculumId: 'synthetic-curriculum', year: 3,
  } }]);
});
