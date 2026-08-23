const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function walk(relativeDirectory) {
  const directory = path.join(root, relativeDirectory);
  const files = [];
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const relativePath = path.join(relativeDirectory, entry.name);
    if (entry.isDirectory()) {
      files.push(...walk(relativePath));
    } else if (/\.(tsx?|jsx?)$/.test(entry.name)) {
      files.push(relativePath);
    }
  }
  return files;
}

test('the retained portal surfaces are present', () => {
  for (const relativePath of [
    'src/app/login/page.tsx',
    'src/app/dashboard/register/page.tsx',
    'src/app/dashboard/page.tsx',
    'src/app/dashboard/register/page.tsx',
    'src/app/dashboard/announcements/page.tsx',
    'src/app/dashboard/notifications/page.tsx',
    'src/app/dashboard/thesis/page.tsx',
    'src/app/admin/page.tsx',
  ]) {
    assert.equal(fs.existsSync(path.join(root, relativePath)), true, relativePath);
  }
});

test('the web client has one Java API boundary and no removed domain client', () => {
  const apiSource = read('src/lib/api.ts');
  const nextConfig = read('next.config.mjs');
  assert.doesNotMatch(apiSource, /analyticsApi|financeApi|waitlistApi|socket\.io/);
  assert.doesNotMatch(apiSource, /forgot-password|reset-password|verify-email|resend-verification/);
  assert.doesNotMatch(nextConfig, /LOCAL_EDGE_ORIGIN|socket\.io|redis|rabbitmq/i);
  assert.match(nextConfig, /JAVA_API_ORIGIN/);
});

test('assistant UI exposes provenance and degraded state', () => {
  const assistantSource = read('src/components/assistant/AssistantPanel.tsx');
  const assistantApi = read('src/lib/thesis-api.ts');
  assert.match(assistantApi, /citations/);
  assert.match(assistantApi, /reasonCode/);
  assert.match(assistantSource, /citation\.title/);
  assert.match(assistantSource, /DEGRADED/);
});

test('frontend source contains no removed route or runtime reference', () => {
  const source = walk('src')
    .map((relativePath) => read(relativePath))
    .join('\n');
  assert.doesNotMatch(source, /\/admin\/analytics|\/dashboard\/invoices|financeApi|analyticsApi|waitlistApi|socket\.io/);
  assert.doesNotMatch(source, /href=["']\/forgot-password|authApi\.(forgotPassword|resetPassword|verifyEmail|resendVerification)/);
});
