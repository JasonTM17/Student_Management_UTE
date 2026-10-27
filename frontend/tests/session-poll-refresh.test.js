const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');
const vm = require('node:vm');

const root = path.resolve(__dirname, '..');

function readSource(file) {
  return fs.readFileSync(path.join(root, file), 'utf8');
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

function settle() {
  return new Promise((resolve) => setImmediate(resolve));
}

const USER_A = {
  id: 'poll-user-a',
  email: 'poll-a@example.test',
  firstName: 'Poll',
  lastName: 'Student',
  status: 'ACTIVE',
  mustChangePassword: false,
  roles: ['STUDENT'],
  studentId: 'poll-user-a',
};

// ---------------------------------------------------------------------------
// Part 1: the 30s identity poll in AuthContext must attempt one silent token
// rotation before publishing 'signed-out'. An expired access cookie with a
// live refresh session is the normal steady state (JWT TTL is minutes, the
// poll interval is 30s); signing the user out on the first probe 401 turns
// every ordinary expiry into a forced logout.
// ---------------------------------------------------------------------------

function createReactRuntime() {
  const runtime = { slots: [], cursor: 0, pendingEffects: [], mounted: false };
  const react = {
    createElement: (type, props, ...children) => ({
      type,
      props: { ...(props ?? {}), children },
    }),
    createContext: (defaultValue) => {
      const context = { current: defaultValue };
      context.Provider = function Provider(props) {
        context.current = props.value;
        return props.children ?? null;
      };
      return context;
    },
    useState: (initial) => {
      const index = runtime.cursor;
      runtime.cursor += 1;
      if (!(index in runtime.slots)) {
        runtime.slots[index] = typeof initial === 'function' ? initial() : initial;
      }
      const setValue = (next) => {
        runtime.slots[index] =
          typeof next === 'function' ? next(runtime.slots[index]) : next;
      };
      return [runtime.slots[index], setValue];
    },
    useRef: (initial) => {
      const index = runtime.cursor;
      runtime.cursor += 1;
      if (!(index in runtime.slots)) {
        runtime.slots[index] = { current: initial };
      }
      return runtime.slots[index];
    },
    useCallback: (fn) => fn,
    useMemo: (fn) => fn(),
    useContext: (context) => context.current,
    useEffect: (fn) => {
      runtime.pendingEffects.push(fn);
    },
  };
  const mountEffects = () => {
    if (runtime.mounted) return;
    runtime.mounted = true;
    runtime.pendingEffects.splice(0).forEach((fn) => fn());
  };
  return { react, runtime, mountEffects };
}

function mountAuthProvider() {
  const intervalCallbacks = [];
  const locationCalls = { replace: [], hrefSets: [] };
  const previousWindow = globalThis.window;
  const previousDocument = globalThis.document;
  globalThis.window = {
    setInterval: (cb) => {
      intervalCallbacks.push(cb);
      return intervalCallbacks.length;
    },
    clearInterval: () => {},
    location: {
      pathname: '/en/dashboard',
      origin: 'http://poll.test',
      href: '',
      replace: (url) => {
        locationCalls.replace.push(url);
      },
    },
  };
  globalThis.document = {
    cookie: 'cc_csrf=poll-hint',
    visibilityState: 'visible',
  };

  const { react, runtime, mountEffects } = createReactRuntime();
  const meCalls = [];
  const refreshCalls = [];
  const sessionSync = { published: [] };
  const stubs = {
    react,
    'next/navigation': {
      useRouter: () => ({ replace: () => {}, push: () => {} }),
      usePathname: () => '/en/dashboard',
    },
    '@/i18n': { useI18n: () => ({ href: (p) => `/en${p}` }) },
    '@/types/api': {},
    '@/lib/api': {
      authApi: {
        me: () => {
          const call = deferred();
          meCalls.push(call);
          return call.promise;
        },
        login: () => deferred().promise,
        logout: () => deferred().promise,
        verifyTwoFactor: () => deferred().promise,
      },
      SessionVerificationError: class SessionVerificationError extends Error {},
      refreshSessionSingleFlight: () => {
        const call = deferred();
        refreshCalls.push(call);
        return call.promise;
      },
      waitForPendingSessionRefresh: () => Promise.resolve(),
    },
    '@/lib/two-factor': {
      isTwoFactorChallenge: (value) => Boolean(value && value.challengeId),
    },
    '@/lib/session-hint': {
      hasCsrfSessionHint: () => true,
      readCsrfSessionHint: (source) => {
        const raw =
          source ??
          (typeof document !== 'undefined' ? document.cookie : '');
        const match = /(?:^|; )cc_csrf=([^;]*)/.exec(raw);
        return match ? match[1] : null;
      },
      markLogoutInProgress: () => {},
      clearLogoutInProgress: () => {},
      isLogoutInProgress: () => false,
    },
    '@/lib/login-portal': {
      loginHref: (base, portal, reason) =>
        `${base}?portal=${portal}&reason=${reason}`,
      portalFromPathname: () => 'student',
      portalFromUser: () => 'student',
    },
    '@/lib/query': {
      getBrowserQueryClient: () => ({ clear: () => {} }),
    },
    '@/lib/session-sync': {
      publishSessionChange: (kind) => {
        sessionSync.published.push(kind);
      },
      subscribeSessionChanges: () => () => {},
    },
  };

  const source = readSource('src/context/AuthContext.tsx');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      jsx: ts.JsxEmit.React,
      esModuleInterop: true,
    },
    fileName: 'AuthContext.tsx',
  });
  const moduleRecord = { exports: {} };
  Function('module', 'exports', 'require', outputText)(
    moduleRecord,
    moduleRecord.exports,
    (specifier) => {
      if (Object.prototype.hasOwnProperty.call(stubs, specifier)) {
        return stubs[specifier];
      }
      throw new Error(`unexpected import in AuthContext.tsx: ${specifier}`);
    },
  );

  const render = () => {
    runtime.cursor = 0;
    return moduleRecord.exports.AuthProvider({ children: null }).props.value;
  };
  render();
  mountEffects();
  return {
    intervalCallbacks,
    locationCalls,
    meCalls,
    refreshCalls,
    sessionSync,
    restore: () => {
      globalThis.window = previousWindow;
      globalThis.document = previousDocument;
    },
  };
}

async function mountSignedIn() {
  const mounted = mountAuthProvider();
  assert.equal(mounted.meCalls.length, 1, 'bootstrap probes the session');
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  await settle();
  assert.equal(
    mounted.intervalCallbacks.length >= 1,
    true,
    'the identity poll must register a window interval',
  );
  return mounted;
}

test('poll: expired access token with a live refresh session must not sign out', async () => {
  const mounted = await mountSignedIn();
  try {
    const firePoll = mounted.intervalCallbacks[0];
    firePoll();
    await settle();
    assert.equal(mounted.meCalls.length, 2, 'the poll probes /auth/me');

    mounted.meCalls[1].reject({ response: { status: 401 } });
    await settle();
    await settle();
    assert.equal(
      mounted.refreshCalls.length,
      1,
      'a probe 401 must attempt one silent refresh before declaring the session dead',
    );

    mounted.refreshCalls[0].resolve({ user: USER_A });
    await settle();
    assert.equal(
      mounted.meCalls.length,
      3,
      'a successful rotation must re-probe /auth/me once',
    );
    mounted.meCalls[2].resolve(USER_A);
    await settle();
    await settle();

    assert.deepEqual(
      mounted.sessionSync.published.filter((kind) => kind === 'signed-out'),
      [],
      'a recovered session must never publish signed-out',
    );
    assert.equal(mounted.locationCalls.replace.length, 0);
  } finally {
    mounted.restore();
  }
});

test('poll: refresh 401 still signs the user out', async () => {
  const mounted = await mountSignedIn();
  try {
    const firePoll = mounted.intervalCallbacks[0];
    firePoll();
    await settle();
    mounted.meCalls[1].reject({ response: { status: 401 } });
    await settle();
    await settle();
    assert.equal(mounted.refreshCalls.length, 1);
    mounted.refreshCalls[0].reject({ response: { status: 401 } });
    await settle();
    await settle();

    assert.ok(
      mounted.sessionSync.published.includes('signed-out'),
      'a confirmed dead session must publish signed-out for sibling tabs',
    );
    assert.equal(mounted.locationCalls.replace.length, 1);
    assert.match(mounted.locationCalls.replace[0], /reason=signed-out/);
  } finally {
    mounted.restore();
  }
});

// ---------------------------------------------------------------------------
// Part 2: the api.ts death-confirm probe inside redirectToLogin has the same
// flaw — it re-checks /auth/me on the same stale access cookie, so an expired
// token with a live refresh session is declared dead.
// ---------------------------------------------------------------------------

function loadApiModule({ post, get, windowObj, documentObj, published }) {
  const source = readSource('src/lib/api.ts');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      target: ts.ScriptTarget.ES2022,
      module: ts.ModuleKind.CommonJS,
      esModuleInterop: true,
    },
  });
  const responseInterceptors = [];
  const apiInstance = Object.assign(
    async () => {
      throw new Error('direct api() retries are not wired in the harness');
    },
    {
      interceptors: {
        request: { use: () => 0 },
        response: {
          use: (_ok, onError) => {
            responseInterceptors.push(onError);
            return responseInterceptors.length;
          },
        },
      },
      post: post ?? (async () => ({ data: {} })),
      get: get ?? (async () => ({ data: {} })),
    },
  );
  const module_ = { exports: {} };
  const sandbox = {
    module: module_,
    exports: module_.exports,
    window: windowObj,
    document: documentObj,
    require: (specifier) => {
      if (specifier === 'axios') {
        return {
          create: () => apiInstance,
          isAxiosError: (value) => Boolean(value && value.isAxiosError),
        };
      }
      if (specifier === '@/types/api') return {};
      if (specifier === '@/lib/announcement-presentation') return {};
      if (specifier === '@/i18n/paths') {
        return {
          addLocalePrefix: (pathname, locale) => `/${locale}${pathname}`,
          stripLocaleFromPathname: (pathname) => ({ locale: 'en', pathname }),
        };
      }
      if (specifier === '@/lib/public-api-url') {
        return { resolvePublicApiBaseUrl: () => 'http://harness.local/api/v1' };
      }
      if (specifier === '@/lib/session-hint') {
        return { CSRF_COOKIE_NAME: 'cc_csrf', isLogoutInProgress: () => false };
      }
      if (specifier === '@/lib/session-sync') {
        return { publishSessionChange: (kind) => published.push(kind) };
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
  return { exports: module_.exports, responseInterceptors };
}

test('interceptor: a request 401 must rotate once before the session-death redirect', async () => {
  const published = [];
  const windowObj = {
    location: {
      pathname: '/dashboard',
      origin: 'http://harness.local',
      href: '',
    },
  };
  const documentObj = { cookie: 'cc_csrf=probe-hint' };
  const meProbe = deferred();
  const meReprobe = deferred();
  const getCalls = [];
  const get = async () => {
    getCalls.push(1);
    return getCalls.length === 1 ? meProbe.promise : meReprobe.promise;
  };
  const refresh = deferred();
  const post = async (url) => {
    if (url === '/auth/refresh') {
      return refresh.promise.then(() => ({ data: { user: USER_A } }));
    }
    throw new Error(`unstubbed POST ${url}`);
  };
  const { responseInterceptors } = loadApiModule({
    get,
    post,
    windowObj,
    documentObj,
    published,
  });
  const onError = responseInterceptors[0];

  const rejected = onError({
    response: { status: 401 },
    config: { url: '/enrollments', method: 'get', skipAuthRefresh: true },
  });
  rejected.catch(() => {});
  await settle();

  meProbe.reject({ response: { status: 401 } });
  await settle();
  await settle();
  refresh.resolve();
  await settle();
  assert.equal(
    getCalls.length,
    2,
    'the death probe must re-check the session after a successful rotation',
  );
  meReprobe.resolve({ data: USER_A });
  await settle();
  await settle();
  await settle();

  assert.deepEqual(
    published.filter((kind) => kind === 'signed-out'),
    [],
    'a recovered session must not broadcast signed-out',
  );
  assert.equal(
    windowObj.location.href,
    '',
    'a recovered session must not navigate to login',
  );
});

test('interceptor: refresh 401 still redirects to login', async () => {
  const published = [];
  const windowObj = {
    location: {
      pathname: '/dashboard',
      origin: 'http://harness.local',
      href: '',
    },
  };
  const documentObj = { cookie: 'cc_csrf=probe-hint' };
  const get = async () => {
    throw { response: { status: 401 } };
  };
  const post = async (url) => {
    if (url === '/auth/refresh') {
      throw { response: { status: 401 } };
    }
    throw new Error(`unstubbed POST ${url}`);
  };
  const { responseInterceptors } = loadApiModule({
    get,
    post,
    windowObj,
    documentObj,
    published,
  });
  const onError = responseInterceptors[0];

  const rejected = onError({
    response: { status: 401 },
    config: { url: '/enrollments', method: 'get', skipAuthRefresh: true },
  });
  rejected.catch(() => {});
  await settle();
  await settle();
  await settle();

  assert.ok(
    published.includes('signed-out'),
    'a confirmed dead session must broadcast signed-out',
  );
  assert.match(windowObj.location.href, /reason=unauthorized/);
});
