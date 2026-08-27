const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');
const repoRoot = path.resolve(root, '..');
const LIVE_FAIL_ORIGIN = 'http://127.0.0.1:4010/api/v1';

function readRepo(relativePath) {
  return fs.readFileSync(path.join(repoRoot, relativePath), 'utf8');
}

function loadTypeScriptModule(relativePath) {
  const source = fs.readFileSync(path.join(root, relativePath), 'utf8');
  const output = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  const loadedModule = { exports: {} };
  Function('module', 'exports', output)(loadedModule, loadedModule.exports);
  return loadedModule.exports;
}

function extractComposeService(compose, name) {
  const lines = compose.split(/\r?\n/);
  const start = lines.findIndex((line) => line === `  ${name}:`);
  assert.notEqual(start, -1, `docker-compose.yml is missing the ${name} service`);
  let end = lines.length;
  for (let index = start + 1; index < lines.length; index += 1) {
    if (/^  [A-Za-z0-9_-]+:\s*$/.test(lines[index]) || lines[index] === 'volumes:') {
      end = index;
      break;
    }
  }
  return lines.slice(start, end).join('\n');
}

test('anonymous browsers without cc_csrf do not look like a live session', () => {
  const { hasCsrfSessionHint } = loadTypeScriptModule('src/lib/session-hint.ts');
  assert.equal(hasCsrfSessionHint(''), false);
  assert.equal(hasCsrfSessionHint('theme=dark'), false);
  assert.equal(hasCsrfSessionHint('cc_csrf=token'), true);
  assert.equal(hasCsrfSessionHint('foo=1; cc_csrf=token; bar=2'), true);
});

test('theme helpers apply html.dark immediately for a click that races hydration', () => {
  const { applyThemeClass, nextTheme, resolveStoredTheme } = loadTypeScriptModule(
    'src/lib/apply-theme.ts',
  );
  assert.equal(nextTheme('light'), 'dark');
  assert.equal(resolveStoredTheme(null), 'light');
  assert.equal(resolveStoredTheme('dark'), 'dark');

  const classList = {
    dark: false,
    toggle(token, force) {
      if (token === 'dark') {
        this.dark = Boolean(force);
      }
    },
  };
  const root = { classList, dataset: {} };
  applyThemeClass('dark', root);
  assert.equal(classList.dark, true);
  assert.equal(root.dataset.theme, 'dark');
});

test('theme provider click cannot be overwritten by the hydration read of localStorage', () => {
  const provider = fs.readFileSync(path.join(root, 'src/components/ThemeProvider.tsx'), 'utf8');
  const layout = fs.readFileSync(path.join(root, 'src/app/layout.tsx'), 'utf8');
  const auth = fs.readFileSync(path.join(root, 'src/context/AuthContext.tsx'), 'utf8');
  const api = fs.readFileSync(path.join(root, 'src/lib/api.ts'), 'utf8');

  assert.match(provider, /userToggled/);
  assert.match(provider, /applyThemeClass\(next/);
  assert.match(layout, /localStorage\.getItem\('theme'\)/);
  assert.match(auth, /hasCsrfSessionHint\(\)/);
  assert.match(api, /skipAuthRefresh:\s*true/);
});

test('shipped resolver refuses the host Expo 4010 origin for the browser API', () => {
  const { resolvePublicApiBaseUrl } = loadTypeScriptModule('src/lib/public-api-url.ts');

  assert.equal(typeof resolvePublicApiBaseUrl, 'function');
  assert.equal(resolvePublicApiBaseUrl(LIVE_FAIL_ORIGIN), '/api/v1');
  assert.equal(resolvePublicApiBaseUrl('http://localhost:4010/api/v1'), '/api/v1');
  assert.equal(resolvePublicApiBaseUrl(undefined), '/api/v1');
  assert.equal(resolvePublicApiBaseUrl('/api/v1'), '/api/v1');
  assert.equal(
    resolvePublicApiBaseUrl('https://api.example.edu/api/v1'),
    'https://api.example.edu/api/v1',
  );
});

test('course registration asks the Java sections API within the page-size maximum of 100', () => {
  const register = fs.readFileSync(path.join(root, 'src/app/dashboard/register/page.tsx'), 'utf8');
  assert.match(register, /sectionsApi\.getAll\(\{ limit: 100/);
  assert.doesNotMatch(register, /limit:\s*150/);
  assert.match(register, /title=\{copy\.title\}/);
});

test('docker web bake cannot interpolate host NEXT_PUBLIC_API_URL=http://127.0.0.1:4010/api/v1', () => {
  const previous = process.env.NEXT_PUBLIC_API_URL;
  process.env.NEXT_PUBLIC_API_URL = LIVE_FAIL_ORIGIN;

  try {
    const compose = readRepo('docker-compose.yml');
    const dockerfile = readRepo('frontend/Dockerfile');
    const apiClient = fs.readFileSync(path.join(root, 'src/lib/api.ts'), 'utf8');
    const web = extractComposeService(compose, 'web');

    assert.match(web, /NEXT_PUBLIC_API_URL:\s*\/api\/v1/);
    assert.doesNotMatch(web, /\$\{NEXT_PUBLIC_API_URL/);
    assert.doesNotMatch(web, /127\.0\.0\.1:4010/);
    assert.match(dockerfile, /ARG NEXT_PUBLIC_API_URL=\/api\/v1/);
    assert.match(dockerfile, /ENV NEXT_PUBLIC_API_URL=\/api\/v1/);
    assert.doesNotMatch(dockerfile, /127\.0\.0\.1:4010/);
    assert.match(apiClient, /resolvePublicApiBaseUrl\(process\.env\.NEXT_PUBLIC_API_URL\)/);
    assert.equal(process.env.NEXT_PUBLIC_API_URL, LIVE_FAIL_ORIGIN);
  } finally {
    if (previous === undefined) {
      delete process.env.NEXT_PUBLIC_API_URL;
    } else {
      process.env.NEXT_PUBLIC_API_URL = previous;
    }
  }
});
