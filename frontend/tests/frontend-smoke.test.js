const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

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

function loadTypeScriptModule(relativePath) {
  const source = read(relativePath);
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const loadedModule = { exports: {} };
  Function('module', 'exports', output)(loadedModule, loadedModule.exports);
  return loadedModule.exports;
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

test('the Java API proxy preserves query parameters and encodes path segments', () => {
  const { buildApiProxyUrl } = loadTypeScriptModule('src/lib/proxy-url.ts');
  const upstreamUrl = buildApiProxyUrl(
    'http://127.0.0.1:4010/',
    ['sections', 'course with spaces'],
    '?semesterId=semester-demo&page=2&search=REST%20API',
  );

  assert.equal(
    upstreamUrl.toString(),
    'http://127.0.0.1:4010/api/v1/sections/course%20with%20spaces?semesterId=semester-demo&page=2&search=REST%20API',
  );
});

test('site metadata defaults to the local course demo and contains no retired domain', () => {
  const siteSource = read('src/lib/site.ts');
  const envExample = read('.env.example');

  assert.match(siteSource, /http:\/\/localhost:3000/);
  assert.match(envExample, /NEXT_PUBLIC_SITE_URL=http:\/\/localhost:3000/);
  assert.doesNotMatch(`${siteSource}\n${envExample}`, /tienson\.io\.vn/i);
});

test('assistant UI exposes provenance and degraded state', () => {
  const assistantSource = read('src/components/assistant/AssistantPanel.tsx');
  const assistantApi = read('src/lib/thesis-api.ts');
  assert.match(assistantApi, /citations/);
  assert.match(assistantApi, /reasonCode/);
  assert.match(assistantSource, /citation\.title/);
  assert.match(assistantSource, /KNOWLEDGE_UNAVAILABLE/);
  assert.match(assistantSource, /messages\.assistant\.degraded/);
});

test('frontend source contains only retained routes and the auth lifecycle contract', () => {
  const source = walk('src')
    .map((relativePath) => read(relativePath))
    .join('\n');
  assert.doesNotMatch(source, /\/admin\/analytics|\/dashboard\/invoices|financeApi|analyticsApi|waitlistApi|socket\.io/);
  assert.match(source, /\/forgot-password/);
  assert.match(source, /\/verify-email/);
  assert.match(source, /confirmPasswordReset/);
  assert.match(source, /resendVerification/);
});
