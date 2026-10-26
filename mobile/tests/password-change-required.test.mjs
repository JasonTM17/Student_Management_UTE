import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relativePath) {
  return fs.readFileSync(path.join(mobileRoot, relativePath), 'utf8');
}

function screenSlice(name) {
  const screens = read('src/screens/student/StudentScreens.tsx');
  const start = screens.indexOf(`export function ${name}`);
  const end = screens.indexOf('export function', start + 1);
  return end === -1 ? screens.slice(start) : screens.slice(start, end);
}

test('PASSWORD_CHANGE_REQUIRED maps to actionable copy instead of a generic failure', () => {
  const screens = read('src/screens/student/StudentScreens.tsx');

  // The backend answers every non-rotation call with this 403 code for
  // office-issued temporary passwords; a generic "unavailable" leaves the
  // user with no idea that a password change unblocks the app.
  assert.match(screens, /PASSWORD_CHANGE_REQUIRED: '[^']+'/);
});

test('profile screen surfaces the mustChangePassword flag from the account payload', () => {
  const client = read('src/api/client.ts');
  const profile = screenSlice('ProfileScreen');

  assert.match(client, /mustChangePassword\?: boolean/);
  assert.match(profile, /account\?\.mustChangePassword/);
});
