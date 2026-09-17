const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const loginPage = fs.readFileSync(path.join(root, 'src/app/login/page.tsx'), 'utf8');

/**
 * The seeded demo passwords live in this repository's history, so a build that
 * was not explicitly handed them must not be able to print one. These assertions
 * are about the shipped source, not about a runtime value, which is what makes
 * them meaningful: a literal reappearing here would silently re-open the exposure.
 */
test('login page carries no demo password literal', () => {
  assert.doesNotMatch(loginPage, /admin123/, 'the admin demo password must not be a source literal');
  assert.doesNotMatch(loginPage, /password123/, 'the student/lecturer demo password must not be a source literal');
});

test('demo quick-fill is opt-in rather than opt-out', () => {
  const flag = loginPage.match(/const SHOW_DEMO_CREDENTIALS = (.+);/);
  assert.ok(flag, 'the demo-credentials flag must be declared');
  assert.match(
    flag[1],
    /===\s*'true'/,
    `the flag must require an explicit 'true', but reads: ${flag[1]}`,
  );
  assert.doesNotMatch(flag[1], /!==\s*'false'/, 'an opt-out default publishes credentials by omission');
});

test('demo passwords come from the environment and have no in-source fallback', () => {
  const passwords = loginPage.match(/const DEMO_PASSWORDS[\s\S]*?\n};/);
  assert.ok(passwords, 'the demo password map must be declared');
  assert.match(passwords[0], /process\.env\.NEXT_PUBLIC_DEMO_STUDENT_PASSWORD/);
  assert.match(passwords[0], /process\.env\.NEXT_PUBLIC_DEMO_LECTURER_PASSWORD/);
  assert.match(passwords[0], /process\.env\.NEXT_PUBLIC_DEMO_ADMIN_PASSWORD/);
  // Static member access only: Next.js inlines process.env.NEXT_PUBLIC_* when it
  // can see the literal key, so a computed lookup would read undefined at runtime.
  assert.doesNotMatch(passwords[0], /process\.env\[/, 'computed env lookup is not inlined by Next.js');
});

test('the quick-fill panel is hidden whenever a password is missing', () => {
  const helper = loginPage.match(/function demoCredentialsFor[\s\S]*?\n}/);
  assert.ok(helper, 'demoCredentialsFor must exist to gate the panel');
  assert.match(helper[0], /if \(!SHOW_DEMO_CREDENTIALS \|\| !password\)/, 'a missing password must hide the panel');
  assert.match(helper[0], /return null/);
});
