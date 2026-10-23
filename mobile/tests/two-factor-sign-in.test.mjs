import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const ts = require('typescript');

const mobileRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function read(relativePath) {
  return fs.readFileSync(path.join(mobileRoot, relativePath), 'utf8');
}

function loadClientModule() {
  const source = read('src/api/client.ts');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: 'client.ts',
  });
  const module = { exports: {} };
  const stubRequire = (id) =>
    id === 'react-native' ? { Platform: { OS: 'android' } } : require(id);
  new Function('exports', 'require', 'module', 'process', outputText)(
    module.exports,
    stubRequire,
    module,
    { env: {} },
  );
  return module.exports;
}

function loadSignInInternals(clientModule) {
  const source = read('src/screens/auth/SignInScreen.tsx');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
      jsx: ts.JsxEmit.ReactJSX,
    },
    fileName: 'SignInScreen.tsx',
  });
  const module = { exports: {} };
  const anyValue = new Proxy(function () {}, {
    get: (_target, prop) => (prop === 'default' ? anyValue : anyValue),
    apply: () => anyValue,
  });
  const stubRequire = (id) =>
    id === '../../api/client' ? clientModule : anyValue;
  new Function(
    'exports',
    'require',
    'module',
    'process',
    `${outputText}\nmodule.exports.__twoFactorErrorMessage = twoFactorErrorMessage;\nmodule.exports.__twoFactorCopy = twoFactorCopy;`,
  )(module.exports, stubRequire, module, { env: {} });
  return module.exports;
}

function installFetchStub(handler) {
  const calls = [];
  globalThis.fetch = async (url, init) => {
    const call = {
      url: String(url),
      method: init?.method ?? 'GET',
      body: typeof init?.body === 'string' ? JSON.parse(init.body) : undefined,
      authorization: init?.headers?.get?.('authorization') ?? null,
    };
    calls.push(call);
    const spec = handler(call, calls.length);
    return new Response(JSON.stringify(spec.body ?? {}), {
      status: spec.status,
      headers: { 'content-type': 'application/json' },
    });
  };
  return calls;
}

test('a two-factor login answer is detected as a challenge, not a broken session', async () => {
  const { campusApi, isTwoFactorChallenge } = loadClientModule();
  installFetchStub(() => ({
    status: 200,
    body: {
      twoFactorRequired: true,
      challengeId: 'challenge-1',
      email: 's***@gmail.com',
      user: null,
      accessToken: null,
      refreshToken: null,
    },
  }));

  const response = await campusApi.login('student@campuscore.edu', 'secret');

  assert.equal(isTwoFactorChallenge(response), true);
  assert.equal(response.challengeId, 'challenge-1');
  assert.equal(response.email, 's***@gmail.com');
  assert.equal(response.accessToken, null);
});

test('a plain login answer is not mistaken for a challenge', async () => {
  const { campusApi, isTwoFactorChallenge } = loadClientModule();
  installFetchStub(() => ({
    status: 200,
    body: {
      user: { id: 'u1', roles: ['STUDENT'] },
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
    },
  }));

  const response = await campusApi.login('student@campuscore.edu', 'secret');

  assert.equal(isTwoFactorChallenge(response), false);
  assert.equal(response.accessToken, 'access-1');
});

test('verifyTwoFactor posts the challenge to the verify endpoint and adopts the session pair', async () => {
  const { apiClient, campusApi } = loadClientModule();
  const calls = installFetchStub((call) => {
    assert.equal(call.url, 'http://10.0.2.2:4010/api/v1/auth/two-factor/verify');
    assert.deepEqual(call.body, { challengeId: 'challenge-1', code: '123456' });
    return {
      status: 200,
      body: {
        user: { id: 'u1', roles: ['STUDENT'] },
        accessToken: 'access-2fa',
        refreshToken: 'refresh-2fa',
      },
    };
  });

  const response = await campusApi.verifyTwoFactor('challenge-1', '123456');

  assert.equal(calls.length, 1);
  assert.equal(response.accessToken, 'access-2fa');
  assert.equal(apiClient.getRefreshToken(), 'refresh-2fa');
});

test('a failed two-factor verify never triggers a session refresh retry', async () => {
  const { apiClient } = loadClientModule();
  apiClient.setSessionTokens('stale-access', 'stale-refresh');
  const calls = installFetchStub((call) => {
    if (call.url.endsWith('/auth/two-factor/verify')) {
      return {
        status: 401,
        body: { code: 'TWO_FACTOR_CODE_INVALID', message: 'The verification code is invalid.' },
      };
    }
    if (call.url.endsWith('/auth/refresh')) {
      return {
        status: 200,
        body: { user: { id: 'u1' }, accessToken: 'rotated-access', refreshToken: 'rotated-refresh' },
      };
    }
    return { status: 500, body: {} };
  });

  await assert.rejects(
    apiClient.post('/auth/two-factor/verify', { challengeId: 'challenge-1', code: '000000' }),
    (error) => error.status === 401 && error.code === 'TWO_FACTOR_CODE_INVALID',
  );
  assert.equal(calls.some((call) => call.url.endsWith('/auth/refresh')), false);
});

test('two-factor error copy maps backend codes and rate limits in both locales', () => {
  const clientModule = loadClientModule();
  const { __twoFactorErrorMessage: map, __twoFactorCopy: copy } = loadSignInInternals(clientModule);
  const { ApiClientError } = clientModule;
  const make = (status, code) => new ApiClientError('raw', status, 'req', code);

  assert.equal(map(make(400, 'TWO_FACTOR_CODE_INVALID'), copy.en), copy.en.invalidCode);
  assert.equal(map(make(401, 'TWO_FACTOR_CODE_EXPIRED'), copy.en), copy.en.expiredChallenge);
  assert.equal(map(make(401, 'TWO_FACTOR_CHALLENGE_INVALID'), copy.en), copy.en.expiredChallenge);
  assert.equal(map(make(423, 'TWO_FACTOR_CODE_LOCKED'), copy.en), copy.en.locked);
  assert.equal(map(make(429, 'RATE_LIMIT_EXCEEDED'), copy.en), copy.en.rateLimited);
  assert.equal(map(make(500, 'UNEXPECTED'), copy.en), 'raw');
  assert.equal(map(new Error('boom'), copy.en), copy.en.failed);

  assert.equal(map(make(400, 'TWO_FACTOR_CODE_INVALID'), copy.vi), copy.vi.invalidCode);
  assert.equal(map(make(429, 'RATE_LIMIT_EXCEEDED'), copy.vi), copy.vi.rateLimited);
  assert.ok(copy.vi.title.length > 0 && copy.en.title.length > 0);
});
