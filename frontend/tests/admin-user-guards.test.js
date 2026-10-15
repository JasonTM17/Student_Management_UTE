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

test('ADMIN, SUPER_ADMIN and TRUONG_KHOA targets are privileged', async () => {
  const { isPrivilegedAccountTarget } = await loadGuardsModule();
  // Mirrors AdminUserMutationService.SUPER_ADMIN_ONLY_ROLES.
  assert.equal(isPrivilegedAccountTarget(['ADMIN']), true);
  assert.equal(isPrivilegedAccountTarget(['SUPER_ADMIN']), true);
  assert.equal(isPrivilegedAccountTarget(['TRUONG_KHOA']), true);
});

test('privilege wins over any unprivileged role on the same account', async () => {
  const { isPrivilegedAccountTarget } = await loadGuardsModule();
  assert.equal(isPrivilegedAccountTarget(['LECTURER', 'TRUONG_KHOA']), true);
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
