const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('path');
const vm = require('vm');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

const root = path.resolve(__dirname, '..');

function read(relativePath) {
  return fs.readFileSync(path.join(root, relativePath), 'utf8');
}

function loadCjs(relativePath, overrides = {}) {
  const { outputText } = ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      jsx: ts.JsxEmit.ReactJSX,
    },
    fileName: path.basename(relativePath),
  });
  const moduleRef = { exports: {} };
  const anyValue = new Proxy(function () {}, {
    get: (_target, prop) => (prop === 'default' ? anyValue : anyValue),
    apply: () => anyValue,
  });
  const requireStub = (id) => {
    if (Object.prototype.hasOwnProperty.call(overrides, id)) {
      return overrides[id];
    }
    if (id === 'react') {
      return React;
    }
    if (id === 'react/jsx-runtime') {
      return require('react/jsx-runtime');
    }
    return anyValue;
  };
  vm.runInNewContext(outputText, {
    module: moduleRef,
    exports: moduleRef.exports,
    require: requireStub,
  });
  return moduleRef.exports;
}

const safePath = loadCjs('src/lib/safe-path.ts');

test('safe internal path accepts single-slash relative paths with query and hash', () => {
  assert.equal(safePath.isSafeInternalPath('/dashboard'), true);
  assert.equal(safePath.isSafeInternalPath('/dashboard/register?q=CS101'), true);
  assert.equal(safePath.isSafeInternalPath('/dashboard/thesis?roundId=1&tab=repository'), true);
  assert.equal(safePath.isSafeInternalPath('/'), true);
  assert.equal(safePath.isSafeInternalPath('/vi/dashboard'), true);
  assert.equal(safePath.safeInternalPath('/dashboard'), '/dashboard');
});

test('safe internal path rejects schemes, protocol-relative hosts, backslashes and whitespace', () => {
  for (const bad of [
    'javascript:alert(1)',
    'JaVaScRiPt:alert(1)',
    ' javascript:alert(1)',
    '\tjavascript:alert(1)',
    'data:text/html;base64,PGI+',
    'vbscript:msgbox(1)',
    '//evil.example/phish',
    '\\\\evil',
    '/\\evil',
    '/dashboard\\..\\admin',
    '/dashboard ok',
    '/dashboard\tid=1',
    '/dashboard\njavascript:alert(1)',
    'javascript%3aalert(1)',
    '&#x6a;avascript:alert(1)',
    'https://evil.example',
    'mailto:a@b.c',
    '',
    null,
    undefined,
  ]) {
    assert.equal(safePath.isSafeInternalPath(bad), false, JSON.stringify(bad));
  }
});

function localizedLinkFor({ isPrefixed }) {
  const LinkStub = React.forwardRef(function LinkStub({ href, ...props }, ref) {
    return React.createElement('a', { href: typeof href === 'string' ? href : '#obj', ref, ...props });
  });
  return loadCjs('src/components/LocalizedLink.tsx', {
    'next/link': { __esModule: true, default: LinkStub },
    '@/lib/safe-path': safePath,
    '@/i18n': {
      useI18n: () => ({
        isPrefixed,
        href: (pathname) => {
          const normalized = pathname.startsWith('/') ? pathname : `/${pathname}`;
          return isPrefixed ? `/vi${normalized}` : normalized;
        },
      }),
    },
  }).LocalizedLink;
}

test('LocalizedLink never emits a server-supplied unsafe href as an anchor', () => {
  for (const isPrefixed of [false, true]) {
    const LocalizedLink = localizedLinkFor({ isPrefixed });
    const markup = renderToStaticMarkup(
      React.createElement(LocalizedLink, { href: 'javascript:alert(1)' }, 'open'),
    );
    assert.ok(!markup.includes('javascript'), markup);
    assert.ok(!markup.includes('href='), markup);
  }
});

test('LocalizedLink still localizes legitimate internal paths', () => {
  const unprefixed = localizedLinkFor({ isPrefixed: false });
  const markup = renderToStaticMarkup(
    React.createElement(unprefixed, { href: '/dashboard/schedule' }, 'open'),
  );
  assert.ok(markup.includes('href="/dashboard/schedule"'), markup);

  const prefixed = localizedLinkFor({ isPrefixed: true });
  const prefixedMarkup = renderToStaticMarkup(
    React.createElement(prefixed, { href: '/dashboard/schedule' }, 'open'),
  );
  assert.ok(prefixedMarkup.includes('href="/vi/dashboard/schedule"'), prefixedMarkup);
});

function loadResolver(relativePath, exportSnippet) {
  const { outputText } = ts.transpileModule(read(relativePath), {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2022,
      jsx: ts.JsxEmit.ReactJSX,
    },
    fileName: path.basename(relativePath),
  });
  const moduleRef = { exports: {} };
  const anyValue = new Proxy(function () {}, {
    get: (_target, prop) => (prop === 'default' ? anyValue : anyValue),
    apply: () => anyValue,
  });
  const realSafePath = loadCjs('src/lib/safe-path.ts');
  vm.runInNewContext(`${outputText}\n${exportSnippet}`, {
    module: moduleRef,
    exports: moduleRef.exports,
    require: (id) => (id === '@/lib/safe-path' ? realSafePath : anyValue),
  });
  return moduleRef.exports.__resolve;
}

test('shared link corpus classifies identically through safe-path', () => {
  const corpus = JSON.parse(
    read('../java-services/restful-api/src/test/resources/notification/link-corpus.json'),
  );
  for (const value of corpus.safe) {
    assert.equal(safePath.isSafeInternalPath(value), true, JSON.stringify(value));
  }
  for (const { value, why } of corpus.unsafe) {
    assert.equal(safePath.isSafeInternalPath(value), false, `${JSON.stringify(value)} (${why})`);
  }
});

test('notification targets ignore unsafe authored links and keep the type fallback', () => {
  const resolvers = [
    loadResolver(
      'src/app/dashboard/layout.tsx',
      'module.exports.__resolve = resolveNotificationTarget;',
    ),
    loadResolver(
      'src/components/dashboard/NotificationsCenterPage.tsx',
      'module.exports.__resolve = resolveNotificationTarget;',
    ),
  ];
  for (const resolve of resolvers) {
    assert.equal(resolve({ type: 'REGISTRATION', link: 'javascript:alert(1)' }), '/dashboard/register');
    assert.equal(resolve({ type: 'SCHEDULE', link: '//evil.example' }), '/dashboard/schedule');
    assert.equal(resolve({ type: 'REGISTRATION', link: ' /bad\x01ctrl ' }), '/dashboard/register');
    assert.equal(resolve({ type: 'REGISTRATION', link: '/dashboard/register?cohort=A' }), '/dashboard/register?cohort=A');
  }
});
