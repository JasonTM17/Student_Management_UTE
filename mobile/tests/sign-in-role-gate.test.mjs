import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ts = require('typescript');

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relativePath) {
  return fs.readFileSync(path.join(mobileRoot, relativePath), 'utf8');
}

/**
 * Executes the real SignInScreen module in-process: transpiled with the
 * project's typescript package, every import satisfied by a callable proxy,
 * and the module-level resolveRole exported for assertions.
 */
function loadSignInInternals() {
  const source = read('src/screens/auth/SignInScreen.tsx');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
      jsx: ts.JsxEmit.ReactJSX,
    },
    fileName: 'SignInScreen.tsx',
  });
  const module = { exports: {} };
  const anyValue = new Proxy(function () {}, {
    get: (_target, prop) => (prop === 'default' ? anyValue : anyValue),
    apply: () => anyValue,
  });
  new Function('exports', 'require', 'module', 'process', `${outputText}\nmodule.exports.__resolveRole = resolveRole;`)(
    module.exports,
    () => anyValue,
    module,
    { env: {} },
  );
  return module.exports;
}

test('resolveRole keeps the recognised web roles', () => {
  const { __resolveRole: resolveRole } = loadSignInInternals();

  assert.equal(resolveRole(['STUDENT']), 'student');
  assert.equal(resolveRole(['student']), 'student');
  assert.equal(resolveRole(['ADMIN']), 'admin');
  assert.equal(resolveRole(['LECTURER']), 'lecturer');
  assert.equal(resolveRole(['FACULTY']), 'lecturer');
  assert.equal(resolveRole(['TEACHER']), 'lecturer');
});

test('resolveRole rejects staff-only and unknown roles instead of defaulting to student', () => {
  const { __resolveRole: resolveRole } = loadSignInInternals();

  for (const roles of [
    ['SUPER_ADMIN'],
    ['TRUONG_KHOA'],
    ['TRUONG_BO_MON'],
    ['DEAN'],
    ['STAFF'],
    ['GUEST'],
    [],
    undefined,
  ]) {
    assert.equal(resolveRole(roles), null, JSON.stringify(roles));
  }
});

test('a student carrying an extra staff role still resolves to the student shell', () => {
  const { __resolveRole: resolveRole } = loadSignInInternals();

  assert.equal(resolveRole(['STUDENT', 'TRUONG_KHOA']), 'student');
});
