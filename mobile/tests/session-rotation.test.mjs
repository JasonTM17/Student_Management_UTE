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

/**
 * Executes the real TypeScript api client in-process: transpiled with the
 * project's typescript package, with react-native and process.env stubbed
 * and globalThis.fetch replaced by the scenario's handler. Assertions look
 * at the wire bodies the client actually produced, not at source shape.
 */
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

test('change-password sends the session refresh token in the body and adopts the rotated pair', async () => {
  const { apiClient, campusApi } = loadClientModule();
  apiClient.setSessionTokens('access-a0', 'refresh-r0');
  const calls = installFetchStub(() => ({
    status: 200,
    body: {
      message: 'Password changed successfully',
      sessionPreserved: true,
      user: { id: 'student-user' },
      accessToken: 'access-a2',
      refreshToken: 'refresh-r2',
    },
  }));

  const result = await campusApi.changePassword('old-secret', 'new-secret-9');

  assert.equal(calls.length, 1);
  assert.equal(calls[0].url, 'http://10.0.2.2:4010/api/v1/auth/change-password');
  assert.equal(calls[0].body.refreshToken, 'refresh-r0',
    'the session-bound request must name the session to preserve');
  assert.equal(calls[0].body.oldPassword, 'old-secret');
  assert.equal(calls[0].authorization, 'Bearer access-a0');
  assert.equal(result.sessionPreserved, true);
  assert.equal(apiClient.getRefreshToken(), 'refresh-r2',
    'a preserved rotation returns fresh tokens the client must adopt');
});

test('a 401-triggered rotation re-serializes the retry body with the rotated token', async () => {
  const { apiClient, campusApi } = loadClientModule();
  apiClient.setSessionTokens('stale-access', 'refresh-r0');
  const calls = installFetchStub((call, count) => {
    if (call.url.endsWith('/auth/refresh')) {
      return {
        status: 200,
        body: {
          user: { id: 'student-user' },
          accessToken: 'access-a1',
          refreshToken: 'refresh-r1',
        },
      };
    }
    if (call.url.endsWith('/auth/change-password') && count === 1) {
      return { status: 401, body: { code: 'SESSION_INVALID' } };
    }
    return {
      status: 200,
      body: {
        message: 'Password changed successfully',
        sessionPreserved: true,
        user: { id: 'student-user' },
        accessToken: 'access-a2',
        refreshToken: 'refresh-r2',
      },
    };
  });

  const result = await campusApi.changePassword('old-secret', 'new-secret-9');

  assert.equal(calls.length, 3);
  assert.equal(calls[0].body.refreshToken, 'refresh-r0');
  assert.equal(calls[1].url, 'http://10.0.2.2:4010/api/v1/auth/refresh');
  // The rotation committed between the first attempt and the retry, so the
  // retry must name the row's CURRENT token — resending r0 here would pin
  // nothing and the server would revoke the family instead of preserving.
  assert.equal(calls[2].body.refreshToken, 'refresh-r1',
    'the retried body must carry the rotated token, not the captured one');
  assert.equal(calls[2].authorization, 'Bearer access-a1');
  assert.equal(result.sessionPreserved, true);
  assert.equal(apiClient.getRefreshToken(), 'refresh-r2');
});

test('a non-preserved rotation leaves adoption to the caller and stores nothing new', async () => {
  const { apiClient, campusApi } = loadClientModule();
  apiClient.setSessionTokens('access-a0', 'refresh-r0');
  installFetchStub(() => ({
    status: 200,
    body: {
      message: 'Password changed successfully',
      sessionPreserved: false,
      user: null,
      accessToken: null,
      refreshToken: null,
    },
  }));

  const result = await campusApi.changePassword('old-secret', 'new-secret-9');

  assert.equal(result.sessionPreserved, false);
  // The revoked pair stays in place: the screen's navigation.signOut() is
  // what clears it and routes to sign-in, mirroring an expired session.
  assert.equal(apiClient.getRefreshToken(), 'refresh-r0');
});

test('a preserved rotation missing its pair is a contract violation, not a fallback', async () => {
  const { apiClient, campusApi, ApiClientError } = loadClientModule();
  apiClient.setSessionTokens('access-a0', 'refresh-r0');
  installFetchStub(() => ({
    status: 200,
    body: {
      message: 'Password changed successfully',
      sessionPreserved: true,
      user: null,
      accessToken: null,
      refreshToken: null,
    },
  }));

  await assert.rejects(
    () => campusApi.changePassword('old-secret', 'new-secret-9'),
    (error) => {
      assert.ok(error instanceof ApiClientError);
      assert.equal(error.code, 'INVALID_CHANGE_PASSWORD_RESPONSE');
      return true;
    },
  );
  assert.equal(apiClient.getRefreshToken(), 'refresh-r0');
});

test('profile rotation wires the session contract through source', () => {
  const client = read('src/api/client.ts');
  const screens = read('src/screens/student/StudentScreens.tsx');

  assert.match(client, /sessionBoundRefresh\?: boolean/);
  assert.match(client, /interface ChangePasswordResponse/);
  assert.match(client, /sessionPreserved/);
  const changePassword = client.slice(
    client.indexOf('changePassword: async'),
    client.indexOf('changePassword: async') + 1400,
  );
  assert.match(changePassword, /sessionBoundRefresh: true/);
  assert.match(changePassword, /setSessionTokens\(response\.accessToken, response\.refreshToken\)/);

  const profile = screens.slice(
    screens.indexOf('const updatePassword'),
    screens.indexOf('const updatePassword') + 1200,
  );
  assert.match(profile, /sessionPreserved/);
  assert.match(profile, /navigation\.signOut\(\)/);
});
