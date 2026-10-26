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

function transpile(relativePath) {
  return ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: path.basename(relativePath),
  }).outputText;
}

function loadValidationModule() {
  const module = { exports: {} };
  new Function(
    'exports',
    'require',
    'module',
    'process',
    transpile('src/lib/profile-validation.ts'),
  )(module.exports, () => ({}), module, { env: {} });
  return module.exports;
}

test('profilePhoneIssue mirrors the backend phone column and pattern', () => {
  const { profilePhoneIssue } = loadValidationModule();

  assert.equal(profilePhoneIssue(''), null);
  assert.equal(profilePhoneIssue('+84 912 345 678'), null);
  assert.equal(profilePhoneIssue('0912345678'), null);
  assert.equal(profilePhoneIssue('abc'), 'PHONE_INVALID');
  assert.equal(profilePhoneIssue('1234'), 'PHONE_INVALID');
  assert.equal(profilePhoneIssue('  '), 'PHONE_INVALID');
  assert.equal(profilePhoneIssue('x'.repeat(81)), 'PHONE_TOO_LONG');
  // 80 chars passes @Size but overflows the pattern's 1+78 shape.
  assert.equal(profilePhoneIssue('0'.repeat(80)), 'PHONE_INVALID');
});

test('profileAddressIssue enforces the backend 500-character column', () => {
  const { profileAddressIssue } = loadValidationModule();

  assert.equal(profileAddressIssue('12 Nguyen Trai'), null);
  assert.equal(profileAddressIssue('x'.repeat(500)), null);
  assert.equal(profileAddressIssue('x'.repeat(501)), 'ADDRESS_TOO_LONG');
});

test('passwordIssue applies the backend policy the mobile save used to skip', () => {
  const { passwordIssue } = loadValidationModule();

  assert.equal(passwordIssue('short1'), 'PASSWORD_TOO_SHORT');
  assert.equal(passwordIssue('x'.repeat(129)), 'PASSWORD_TOO_LONG');
  assert.equal(passwordIssue('é'.repeat(40) + 'a1'), 'PASSWORD_TOO_LONG');
  assert.equal(passwordIssue('abcdefghi'), 'PASSWORD_TOO_WEAK');
  assert.equal(passwordIssue('123456789'), 'PASSWORD_TOO_WEAK');
  assert.equal(passwordIssue('Valid123'), null);
  assert.equal(
    passwordIssue('minh.nguyen123', { email: 'minh.nguyen@ute.edu.vn' }),
    'PASSWORD_CONTAINS_IDENTITY',
  );
  assert.equal(
    passwordIssue('Other123', { email: 'minh.nguyen@ute.edu.vn' }),
    null,
  );
  assert.equal(
    passwordIssue('SamePass1', { currentPassword: 'SamePass1' }),
    'PASSWORD_REUSED',
  );
});

test('profile screen validates phone, address and password before saving', () => {
  const screens = read('src/screens/student/StudentScreens.tsx');
  const start = screens.indexOf('export function ProfileScreen');
  const profile = screens.slice(start);

  // Without these checks the form submits values the backend always
  // rejects, surfacing only a generic "could not be saved" message.
  assert.match(profile, /profilePhoneIssue\(phone\.trim\(\)\)/);
  assert.match(profile, /profileAddressIssue\(address\.trim\(\)\)/);
  assert.match(profile, /passwordIssue\(newPassword/);
  assert.doesNotMatch(profile, /newPassword\.length < 8/);
});
