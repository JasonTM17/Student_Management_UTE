import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import vm from 'node:vm';
import ts from 'typescript';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function loadClient(fetchImpl) {
  const source = fs.readFileSync(path.join(root, 'src/api/client.ts'), 'utf8');
  const compiled = ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.CommonJS,
      esModuleInterop: true,
    },
  }).outputText;
  const module = { exports: {} };
  const context = vm.createContext({
    module,
    exports: module.exports,
    require(specifier) {
      if (specifier === 'react-native') return { Platform: { OS: 'android' } };
      throw new Error(`Unexpected test dependency: ${specifier}`);
    },
    fetch: fetchImpl,
    Headers,
    Response,
    Uint8Array,
    ArrayBuffer,
    AbortSignal,
    Date,
    Math,
    JSON,
    URL,
    process: { env: {} },
    console,
  });
  vm.runInContext(compiled, context, { filename: 'mobile/src/api/client.ts' });
  return module.exports;
}

function jsonResponse(status, body) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'content-type': 'application/json' },
  });
}

test('concurrent JSON and PDF 401 responses share one rotated refresh token', async () => {
  let refreshCalls = 0;
  const protectedAttempts = [];
  const { createApiClient } = loadClient(async (url, init = {}) => {
    const requestUrl = String(url);
    const authorization = new Headers(init.headers).get('authorization');
    if (requestUrl.endsWith('/auth/refresh')) {
      refreshCalls += 1;
      assert.deepEqual(JSON.parse(String(init.body)), { refreshToken: 'refresh-1' });
      return jsonResponse(200, { accessToken: 'access-2', refreshToken: 'refresh-2', user: {} });
    }
    protectedAttempts.push({ requestUrl, authorization });
    if (authorization === 'Bearer expired-access') {
      return jsonResponse(401, { code: 'SESSION_EXPIRED', message: 'expired' });
    }
    assert.equal(authorization, 'Bearer access-2');
    if (requestUrl.endsWith('/registration/slip')) {
      return new Response(new Uint8Array([37, 80, 68, 70]), {
        status: 200,
        headers: { 'content-type': 'application/pdf' },
      });
    }
    return jsonResponse(200, { ok: true });
  });

  const client = createApiClient({ baseUrl: 'https://campuscore.test/api/v1', mode: 'live' });
  client.setSessionTokens('expired-access', 'refresh-1');
  const [json, pdf] = await Promise.all([
    client.get('/me'),
    client.requestBytes('/me/registration/slip'),
  ]);

  assert.deepEqual(json, { ok: true });
  assert.deepEqual(Array.from(pdf.data), [37, 80, 68, 70]);
  assert.equal(refreshCalls, 1);
  assert.equal(client.getRefreshToken(), 'refresh-2');
  assert.equal(protectedAttempts.filter((attempt) => attempt.authorization === 'Bearer expired-access').length, 2);
  assert.equal(protectedAttempts.filter((attempt) => attempt.authorization === 'Bearer access-2').length, 2);
});

test('a stale refresh response cannot overwrite a newer login generation', async () => {
  let releaseRefresh;
  let refreshStarted;
  const refreshEntered = new Promise((resolve) => { refreshStarted = resolve; });
  const refreshGate = new Promise((resolve) => { releaseRefresh = resolve; });
  const seenAuthorizations = [];
  const { createApiClient } = loadClient(async (url, init = {}) => {
    const requestUrl = String(url);
    const authorization = new Headers(init.headers).get('authorization');
    if (requestUrl.endsWith('/auth/refresh')) {
      refreshStarted();
      await refreshGate;
      return jsonResponse(200, { accessToken: 'stale-access', refreshToken: 'stale-refresh', user: {} });
    }
    seenAuthorizations.push(authorization);
    if (authorization === 'Bearer old-access') {
      return jsonResponse(401, { code: 'SESSION_EXPIRED', message: 'expired' });
    }
    assert.equal(authorization, 'Bearer new-login-access');
    return jsonResponse(200, { ok: true });
  });

  const client = createApiClient({ baseUrl: 'https://campuscore.test/api/v1', mode: 'live' });
  client.setSessionTokens('old-access', 'old-refresh');
  const pending = client.get('/me');
  await refreshEntered;
  client.setSessionTokens('new-login-access', 'new-login-refresh');
  releaseRefresh();

  assert.deepEqual(await pending, { ok: true });
  assert.equal(client.getRefreshToken(), 'new-login-refresh');
  assert.deepEqual(seenAuthorizations, ['Bearer old-access', 'Bearer new-login-access']);
});

test('public lifecycle requests never attach a stale bearer token', async () => {
  const { createApiClient } = loadClient(async (url, init = {}) => {
    assert.match(String(url), /\/auth\/login$/);
    assert.equal(new Headers(init.headers).get('authorization'), null);
    return jsonResponse(200, { accessToken: 'fresh', refreshToken: 'fresh-refresh', user: {} });
  });
  const client = createApiClient({ baseUrl: 'https://campuscore.test/api/v1', mode: 'live' });
  client.setSessionTokens('stale-access', 'stale-refresh');

  await client.post('/auth/login', { email: 'student@campuscore.test', password: 'password123' });
});
