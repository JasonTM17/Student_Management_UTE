const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');

// Loads the real src/lib/api.ts with its imports stubbed: axios.create
// returns a controllable fake transport; browser globals stay absent so
// withSessionTransport uses the in-memory FIFO branch — the same
// serialization primitive a same-document interleaving takes.
function loadApiModule({ post, get } = {}) {
  const source = fs.readFileSync(path.join(root, 'src/lib/api.ts'), 'utf8');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.CommonJS,
      esModuleInterop: true,
    },
  });
  const apiInstance = Object.assign(
    async () => {
      throw new Error('direct api() retries are not wired in the harness');
    },
    {
      interceptors: {
        request: { use: () => 0 },
        response: { use: () => 0 },
      },
      post: post ?? (async () => { throw new Error('unstubbed POST'); }),
      get: get ?? (async () => { throw new Error('unstubbed GET'); }),
    },
  );
  const module_ = { exports: {} };
  const sandbox = {
    module: module_,
    exports: module_.exports,
    require: specifier => {
      if (specifier === 'axios') {
        return {
          create: () => apiInstance,
          isAxiosError: value => Boolean(value && value.isAxiosError),
        };
      }
      if (specifier === '@/types/api') return {};
      if (specifier === '@/i18n/paths') {
        return {
          addLocalePrefix: (pathname, locale) => `/${locale}${pathname}`,
          stripLocaleFromPathname: pathname => ({ locale: 'en', pathname }),
        };
      }
      if (specifier === '@/lib/public-api-url') {
        return { resolvePublicApiBaseUrl: () => 'http://harness.local/api/v1' };
      }
      if (specifier === '@/lib/session-hint') {
        return { CSRF_COOKIE_NAME: 'cc_csrf', isLogoutInProgress: () => false };
      }
      if (specifier === '@/lib/session-sync') {
        return { publishSessionChange: () => {} };
      }
      throw new Error(`unstubbed require: ${specifier}`);
    },
    process,
    console,
    setTimeout, clearTimeout, setInterval, clearInterval,
    queueMicrotask, Promise,
    AbortController, FormData, Blob, URL, URLSearchParams,
    TextEncoder, TextDecoder, Headers, Request, Response, fetch,
    crypto: globalThis.crypto,
  };
  sandbox.globalThis = sandbox;
  vm.createContext(sandbox);
  new vm.Script(outputText, { filename: 'api.ts' }).runInContext(sandbox);
  return module_.exports;
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

const tick = () => new Promise(resolve => setTimeout(resolve, 0));

test('a refresh queued behind an in-flight change-password carries the rotated cookie', async () => {
  const events = [];
  // The jar models the shared browser cookie store: change-password's
  // response commits the rotated token via Set-Cookie; a refresh may only
  // post once the jar carries it.
  const jar = { refreshCookie: 'refresh-t0' };
  const hold = deferred();
  const api = loadApiModule({
    post: async (url) => {
      events.push([url, jar.refreshCookie]);
      if (url === '/auth/change-password') {
        await hold.promise;
        jar.refreshCookie = 'refresh-t1';
        return {
          data: {
            message: 'changed',
            sessionPreserved: true,
            user: null,
            accessToken: 'access-t1',
            refreshToken: 'refresh-t1',
          },
        };
      }
      if (url === '/auth/refresh') {
        return { data: { accessToken: 'access-t2', refreshToken: 'refresh-t2' } };
      }
      throw new Error(`unexpected POST ${url}`);
    },
  });

  const change = api.authApi.changePassword('before-pass', 'AfterRotation9');
  await tick();
  assert.deepEqual(events.map(e => e[0]), ['/auth/change-password']);

  // The interleaving the server-side reuse detector punishes: a refresh
  // attempt while the credential rotation is still in flight. It must wait
  // for the transport, then carry the rotated cookie — never the
  // pre-rotation one.
  const refresh = api.authApi.refresh();
  await tick();
  await tick();
  assert.deepEqual(
    events.map(e => e[0]),
    ['/auth/change-password'],
    'the refresh must not post while the credential rotation is in flight',
  );

  hold.resolve();
  const changeResult = await change;
  assert.equal(changeResult.sessionPreserved, true);
  await refresh;
  assert.deepEqual(events.map(e => e[0]), ['/auth/change-password', '/auth/refresh']);
  assert.equal(
    events[1][1],
    'refresh-t1',
    'the queued refresh must carry the rotated cookie, not the pre-rotation one',
  );
});

test('a stale access token inside the transport rotates inline and retries', async () => {
  const events = [];
  let changeAttempts = 0;
  const api = loadApiModule({
    post: async (url) => {
      events.push(url);
      if (url === '/auth/change-password') {
        changeAttempts += 1;
        if (changeAttempts === 1) {
          const error = new Error('unauthorized');
          error.response = { status: 401 };
          throw error;
        }
        return {
          data: {
            message: 'changed',
            sessionPreserved: true,
            user: null,
            accessToken: 'access-t1',
            refreshToken: 'refresh-t1',
          },
        };
      }
      if (url === '/auth/refresh') {
        return { data: { accessToken: 'access-t2', refreshToken: 'refresh-t2' } };
      }
      throw new Error(`unexpected POST ${url}`);
    },
  });

  // A nested refreshSessionSingleFlight here would wait on the transport
  // lock this call already holds and dead-lock; the inline recovery must
  // run the rotation as a plain request instead.
  const result = await api.authApi.changePassword('before-pass', 'AfterRotation9');
  assert.equal(result.sessionPreserved, true);
  assert.deepEqual(events, ['/auth/change-password', '/auth/refresh', '/auth/change-password']);
});

test('a failed rotation releases the transport for the queued refresh', async () => {
  const events = [];
  const hold = deferred();
  const api = loadApiModule({
    post: async (url) => {
      events.push(url);
      if (url === '/auth/change-password') {
        await hold.promise;
        const error = new Error('bad request');
        error.response = { status: 400 };
        throw error;
      }
      if (url === '/auth/refresh') {
        return { data: { accessToken: 'access-t2', refreshToken: 'refresh-t2' } };
      }
      throw new Error(`unexpected POST ${url}`);
    },
  });

  const change = api.authApi.changePassword('wrong-pass', 'AfterRotation9');
  await tick();
  const refresh = api.authApi.refresh();
  hold.resolve();
  await assert.rejects(change);
  await refresh;
  assert.deepEqual(events, ['/auth/change-password', '/auth/refresh']);
});
