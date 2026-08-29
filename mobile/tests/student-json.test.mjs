import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

test('student live JSON uses new registration contract and profile APIs', () => {
  const client = fs.readFileSync(path.join(mobileRoot, 'src/api/client.ts'), 'utf8');
  const screens = fs.readFileSync(path.join(mobileRoot, 'src/screens/student/StudentScreens.tsx'), 'utf8');
  const operations = fs.readFileSync(path.join(mobileRoot, 'src/screens/operations/OperationsScreens.tsx'), 'utf8');

  assert.match(client, /Idempotency-Key/);
  assert.match(client, /\/me\/enrollments/);
  assert.match(client, /\/me\/registration\/sections/);
  assert.match(client, /\/enrollments\/my\/transcript/);
  assert.match(client, /\/announcements\/my/);
  assert.match(client, /\/auth\/profile/);
  assert.match(client, /oldPassword/);
  assert.match(screens, /campusApi\.announcements/);
  assert.match(screens, /campusApi\.transcript/);
  assert.match(screens, /campusApi\.updateProfile/);
  assert.match(screens, /campusApi\.changePassword/);
  assert.doesNotMatch(operations, /campusApi\./);

  const registrationStart = screens.indexOf('export function RegistrationScreen');
  const registrationEnd = screens.indexOf('export function', registrationStart + 1);
  const registrationScreen = registrationEnd === -1
    ? screens.slice(registrationStart)
    : screens.slice(registrationStart, registrationEnd);
  assert.match(registrationScreen, /campusApi\.registrationSections/);
  assert.match(registrationScreen, /campusApi\.registrationRounds/);
  assert.doesNotMatch(registrationScreen, /campusApi\.sections\(/);
});
