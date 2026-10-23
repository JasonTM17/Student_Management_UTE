const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function blockAfter(source, marker, length) {
  const index = source.indexOf(marker);
  assert.ok(index >= 0, `marker not found: ${marker}`);
  return source.slice(index, index + length);
}

test('the web profile date-of-birth field is read-only like the other school-managed fields', () => {
  const page = read('src/app/dashboard/profile/page.tsx');
  const dobInput = blockAfter(page, 'id="profile-date-of-birth"', 400);

  assert.match(dobInput, /disabled/, 'the date-of-birth input must be disabled');
  assert.ok(!/onChange/.test(dobInput), 'a school-managed field must not take edits');
});

test('the web profile save payload carries only self-service fields', () => {
  const page = read('src/app/dashboard/profile/page.tsx');
  const payload = blockAfter(page, 'authApi.updateProfile({', 220);

  assert.ok(!/dateOfBirth/.test(payload), 'dateOfBirth must not be sent');
  assert.ok(!/firstName|lastName/.test(payload), 'names must not be sent');
  assert.match(payload, /phone:/);
  assert.match(payload, /address:/);
  assert.match(payload, /avatar/);
});

test('the web updateProfile client type no longer offers school-managed keys', () => {
  const api = read('src/lib/api.ts');
  const signature = blockAfter(api, 'updateProfile: async (data: {', 200);

  assert.ok(!/dateOfBirth/.test(signature));
  assert.match(signature, /phone\?:/);
});

test('the mobile profile only writes self-service contact fields', () => {
  const screens = read('../mobile/src/screens/student/StudentScreens.tsx');

  const saveCall = blockAfter(screens, 'campusApi.updateProfile({', 160);
  assert.ok(!/firstName|lastName|dateOfBirth/.test(saveCall), 'names and DOB are school-managed');
  assert.match(saveCall, /phone/);
  assert.match(saveCall, /address/);

  assert.ok(!/label="First name"[^>]*onChangeText/.test(screens), 'no editable name field');
  assert.ok(!/label="Date of birth"[^>]*onChangeText/.test(screens), 'no editable DOB field');
});
