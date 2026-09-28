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

/**
 * Roadmap 1.5b: a deployment whose quick-fill still shows a known-stale seed
 * password warns inline instead of letting the value be submitted. The
 * predicate is a pure helper so the page itself stays free of literals.
 */
const ts = require('typescript');

function loadDemoCredentialsModule() {
  const source = fs.readFileSync(path.join(root, 'src/lib/demo-credentials.ts'), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const loadedModule = { exports: {} };
  Function('module', 'exports', output)(loadedModule, loadedModule.exports);
  return loadedModule.exports;
}

test('the stale quick-fill predicate flags rotated seed defaults only', () => {
  const { isStaleDemoPassword } = loadDemoCredentialsModule();
  assert.equal(isStaleDemoPassword('admin123'), true, 'the old admin seed must be flagged stale');
  assert.equal(isStaleDemoPassword(' password123'), false, 'values only match exactly');
  assert.equal(isStaleDemoPassword(''), false);
  assert.equal(isStaleDemoPassword(undefined), false);
});

test('the login page warns on stale quick-fill values and never auto-submits', () => {
  // The page delegates to the helper (no literal) and renders the warning.
  assert.match(loginPage, /isStaleDemoPassword\(demoCredentials\.password\)/);
  assert.match(loginPage, /role="note"/, 'the stale warning must be announced next to the credential');
  // The warning copy ships through the i18n dictionaries, not page literals.
  const messages = fs.readFileSync(path.join(root, 'src/i18n/messages.ts'), 'utf8');
  assert.match(messages, /staleDemoPasswordWarning:[\s\S]*?password123 per the updated guide/);
  assert.match(
    messages.slice(messages.indexOf('export const vi')),
    /staleDemoPasswordWarning:[\s\S]*?dùng password123 theo hướng dẫn mới/,
  );
  // Quick-fill is fill-only: no submit() call anywhere on the page.
  assert.doesNotMatch(loginPage, /\.submit\(\)|requestSubmit/, 'quick-fill must not auto-submit the form');
});
