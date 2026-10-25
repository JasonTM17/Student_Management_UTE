const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function pageSource(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

// Unauthorized roles must leave restricted surfaces instead of reading a
// forbidden panel inside the admin shell — the pattern every other admin page
// already follows (see src/app/admin/users/page.tsx).
const redirectPages = [
  {
    name: 'thesis admin',
    file: 'src/app/admin/thesis/page.tsx',
    loginPortal: 'portal=admin',
  },
  {
    name: 'assistant knowledge admin',
    file: 'src/app/admin/assistant-knowledge/page.tsx',
    loginPortal: 'portal=admin',
  },
  {
    name: 'dean overview',
    file: 'src/app/dashboard/dean/page.tsx',
    loginPortal: 'portal=dean',
  },
];

for (const { name, file, loginPortal } of redirectPages) {
  test(`${name} redirects signed-out visitors to the matching login portal`, () => {
    const source = pageSource(file);
    assert.match(source, /router\.replace\(`\$\{href\('\/login'\)\}\?portal=/, `${file} must redirect signed-out users through href('/login')`);
    assert.ok(source.includes(loginPortal), `${file} must carry ${loginPortal} in the login redirect`);
  });

  test(`${name} redirects unauthorized signed-in users to the dashboard`, () => {
    const source = pageSource(file);
    assert.match(source, /router\.replace\(href\('\/dashboard'\)\)/, `${file} must redirect unauthorized roles to href('/dashboard')`);
  });

  test(`${name} never renders ForbiddenState inside the admin shell for unauthorized roles`, () => {
    const source = pageSource(file);
    assert.doesNotMatch(source, /<ForbiddenState/, `${file} must not render a forbidden panel — it redirects instead`);
  });

  test(`${name} shows a neutral loading state while the redirect resolves`, () => {
    const source = pageSource(file);
    assert.match(source, /!canAccess|!isFacultyHead/, `${file} must gate rendering on the access check`);
    assert.match(source, /<LoadingState/, `${file} must render a loading state during the access hand-off`);
  });
}
