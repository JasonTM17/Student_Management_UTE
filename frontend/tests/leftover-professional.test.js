const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

test('homepage header CTA stays mounted during auth load', () => {
  const home = read('src/app/page.tsx');
  assert.doesNotMatch(home, /!isLoading \? \(/);
  assert.match(home, /variant="warm"/);
  assert.match(home, /messages\.common\.actions\.signIn/);
  assert.match(home, /messages\.common\.actions\.openDashboard/);
});

test('leftover permission pages no longer spinner-gate authLoading or hasAccess', () => {
  const files = [
    'src/app/dashboard/announcements/page.tsx',
    'src/app/dashboard/lecturer/announcements/page.tsx',
    'src/components/dashboard/NotificationsCenterPage.tsx',
    'src/components/dashboard/thesis/ThesisTopicCatalogPage.tsx',
    'src/components/dashboard/thesis/ThesisTopicDetailPage.tsx',
    'src/components/dashboard/thesis/ThesisProgressPage.tsx',
  ];
  for (const file of files) {
    const source = read(file);
    assert.doesNotMatch(source, /authLoading \|\| !hasAccess/);
    assert.match(source, /isForbidden/);
    assert.match(source, /WorkspaceForbiddenState/);
  }
});

test('public signup explains that student accounts are issued by the Academic Office', () => {
  const page = read('src/app/register/page.tsx');
  const api = read('src/lib/api.ts');
  const context = read('src/context/AuthContext.tsx');
  const login = read('src/app/login/page.tsx');
  assert.match(page, /issuedByOfficeTitle/);
  assert.match(page, /issuedByOfficeSteps/);
  assert.doesNotMatch(page, /<form|authApi\.register/);
  // Self-registration is removed as a contract: no client may call it.
  assert.doesNotMatch(api, /auth\/register/);
  assert.doesNotMatch(context, /register/);
  assert.doesNotMatch(login, /forgot-password|forgotPassword/);
});

test('leftover sources do not add new locale === vi UI dictionaries', () => {
  const protectedRoute = read('src/components/ProtectedRoute.tsx');
  assert.doesNotMatch(protectedRoute, /locale === 'vi'/);
  assert.match(protectedRoute, /messages\.workspaceForbidden/);
});

test('register dashboard uses bilingual messages and local gold CTA', () => {
  const page = read('src/app/dashboard/register/page.tsx');
  const api = read('src/lib/api.ts');
  assert.doesNotMatch(page, /locale === 'vi'/);
  assert.doesNotMatch(page, /\.catch\(\s*\(\)\s*=>\s*\[\]\s*\)/);
  assert.match(page, /messages\.courseRegistration/);
  assert.match(page, /variant="registration"/);
  assert.match(page, /variant="outline"/);
  assert.match(page, /WINDOW_CLOSED/);
  assert.match(page, /!roundOpen/);
  assert.match(page, /copy\.roundUnavailable/);
  assert.match(api, /Idempotency-Key/);
  assert.match(api, /Idempotency-Key': createRequestId\(\)/);
  assert.match(api, /typeof globalThis\.crypto\?\.randomUUID === 'function'/);
  assert.doesNotMatch(api, /'Idempotency-Key': crypto\.randomUUID\(\)/);
  assert.match(api, /\/me\/enrollments/);
});
