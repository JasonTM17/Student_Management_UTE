const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const ts = require('typescript');

const root = path.resolve(__dirname, '..');

function readSource(file) {
  return fs.readFileSync(path.join(root, file), 'utf8');
}

// Loads src/lib/query.ts with @tanstack/react-query stubbed so the module
// code really executes: the fake client records clear() calls and every
// `new QueryClient()` is observable through the instances array.
function loadQueryModule() {
  const instances = [];
  class FakeQueryClient {
    constructor(options) {
      this.options = options;
      this.clearCount = 0;
      instances.push(this);
    }
    clear() {
      this.clearCount += 1;
    }
  }
  const requireStub = (spec) => {
    if (spec === '@tanstack/react-query') {
      return { QueryClient: FakeQueryClient, useQuery: () => ({}) };
    }
    throw new Error(`unexpected import in query.ts: ${spec}`);
  };
  const source = readSource('src/lib/query.ts');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    fileName: 'query.ts',
  });
  const mod = { exports: {} };
  new Function('require', 'module', 'exports', outputText)(requireStub, mod, mod.exports);
  return { exports: mod.exports, instances };
}

test('server-side calls always receive a fresh QueryClient', () => {
  const hadWindow = 'window' in globalThis;
  delete globalThis.window;
  try {
    const { exports: query, instances } = loadQueryModule();
    const first = query.getBrowserQueryClient();
    const second = query.getBrowserQueryClient();
    assert.notEqual(first, second, 'SSR must never share a QueryClient between calls');
    assert.equal(instances.length, 2);
  } finally {
    if (hadWindow === false) delete globalThis.window;
  }
});

test('browser calls share one lazily created QueryClient', () => {
  const { exports: query, instances } = loadQueryModule();
  globalThis.window = {};
  try {
    const first = query.getBrowserQueryClient();
    const second = query.getBrowserQueryClient();
    assert.equal(first, second);
    assert.equal(instances.length, 1);
    assert.equal(typeof first.clear, 'function');
    first.clear();
    assert.equal(first.clearCount, 1);
  } finally {
    delete globalThis.window;
  }
});

test('the provider mounts the shared browser client', () => {
  const provider = readSource('src/components/providers/query-provider.tsx');
  assert.match(provider, /useState\(getBrowserQueryClient\)/);
  assert.doesNotMatch(provider, /useState\(createQueryClient\)/);
});

test('AuthContext drops the query cache on every identity transition', () => {
  const source = readSource('src/context/AuthContext.tsx');
  const clears = (source.match(/getBrowserQueryClient\(\)\.clear\(\)/g) ?? []).length;
  // logout (always), login success, two-factor success, identity change,
  // and the dead-session path must each clear the shared cache.
  assert.ok(clears >= 4, `expected at least 4 clear() call sites, found ${clears}`);
  assert.match(source, /lastKnownUserIdRef/);
  // Every user write must go through the identity-aware setter so a changed
  // user id can drop the cache.
  assert.equal(
    (source.match(/\bsetUser\(/g) ?? []).length,
    1,
    'setUser should only appear inside the setUserIdentity helper',
  );
});

test('the dashboard layout re-arms the notification fetch for a new identity', () => {
  const source = readSource('src/app/dashboard/layout.tsx');
  assert.match(source, /notificationsUserIdRef/);
  assert.match(source, /notificationsRequested\.current = false/);
});

const USER_A = {
  id: 'session-probe-a',
  email: 'session-a@example.test',
  firstName: 'Alpha',
  lastName: 'Student',
  status: 'ACTIVE',
  mustChangePassword: false,
  roles: ['STUDENT'],
  studentId: 'session-probe-a',
};
const USER_B = {
  id: 'session-probe-b',
  email: 'session-b@example.test',
  firstName: 'Beta',
  lastName: 'Lecturer',
  status: 'ACTIVE',
  mustChangePassword: false,
  roles: ['LECTURER'],
  lecturerId: 'session-probe-b',
};

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

function createReactRuntime() {
  const runtime = { slots: [], cursor: 0, pendingEffects: [], activeEffects: [], cleanups: [], mounted: false };
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
  const runEffects = (fns) => {
    fns.forEach((fn) => {
      const cleanup = fn();
      if (typeof cleanup === 'function') {
        runtime.cleanups.push(cleanup);
      }
    });
  };
  const mountEffects = () => {
    if (runtime.mounted) return;
    runtime.mounted = true;
    const fns = runtime.pendingEffects.splice(0);
    runtime.activeEffects.push(...fns);
    runEffects(fns);
  };
  const unmountEffects = () => {
    runtime.cleanups.splice(0).forEach((fn) => fn());
  };
  const remountEffects = () => {
    runEffects(runtime.activeEffects);
  };
  return { react, runtime, mountEffects, unmountEffects, remountEffects };
}

function mountAuthProvider(options = {}) {
  const { react, runtime, mountEffects, unmountEffects, remountEffects } = createReactRuntime();
  const queryClient = {
    clearCount: 0,
    clear() {
      this.clearCount += 1;
    },
  };
  const meCalls = [];
  const refreshCalls = [];
  const loginCalls = [];
  const twoFactorCalls = [];
  const logoutCalls = [];
  const refreshWaiterCalls = [];
  const sessionHint = { value: options.sessionHint ?? true };
  const authApi = {
    me: () => {
      const call = deferred();
      meCalls.push(call);
      return call.promise;
    },
    login: () => {
      const call = deferred();
      loginCalls.push(call);
      return call.promise;
    },
    logout: () => {
      const call = deferred();
      logoutCalls.push(call);
      return call.promise;
    },
    verifyTwoFactor: () => {
      const call = deferred();
      twoFactorCalls.push(call);
      return call.promise;
    },
  };
  const router = { replace: () => {}, push: () => {} };
  const pathname = { value: '/en/dashboard' };
  const sessionSync = { published: [], listeners: [], unsubscribed: 0 };
  const stubs = {
    react,
    'next/navigation': { useRouter: () => router, usePathname: () => pathname.value },
    '@/i18n': { useI18n: () => ({ href: (p) => `/en${p}` }) },
    '@/types/api': {},
    '@/lib/api': {
      authApi,
      SessionVerificationError: SessionVerificationErrorStub,
      refreshSessionSingleFlight: () => {
        const call = deferred();
        refreshCalls.push(call);
        return call.promise;
      },
      waitForPendingSessionRefresh: () => {
        const call = deferred();
        refreshWaiterCalls.push(call);
        return call.promise;
      },
    },
    '@/lib/two-factor': {
      isTwoFactorChallenge: (value) => Boolean(value && value.challengeId),
    },
    '@/lib/session-hint': {
      hasCsrfSessionHint: () => sessionHint.value,
      readCsrfSessionHint: (source) => {
        const raw =
          source ??
          (typeof document !== 'undefined' ? document.cookie : '');
        const match = /(?:^|; )cc_csrf=([^;]*)/.exec(raw);
        return match ? match[1] : null;
      },
      markLogoutInProgress: () => {
        sessionHint.logoutInProgress = true;
      },
      clearLogoutInProgress: () => {
        sessionHint.logoutInProgress = false;
      },
      isLogoutInProgress: () => sessionHint.logoutInProgress === true,
    },
    '@/lib/login-portal': {
      loginHref: () => '/en/login',
      portalFromPathname: () => 'student',
      portalFromUser: () => 'student',
    },
    '@/lib/query': { getBrowserQueryClient: () => queryClient },
    '@/lib/session-sync': {
      publishSessionChange: (kind) => {
        sessionSync.published.push(kind);
      },
      subscribeSessionChanges: (listener) => {
        sessionSync.listeners.push(listener);
        return () => {
          sessionSync.unsubscribed += 1;
        };
      },
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
  const stubRequire = (specifier) => {
    if (Object.prototype.hasOwnProperty.call(stubs, specifier)) {
      return stubs[specifier];
    }
    throw new Error(`unexpected import in AuthContext.tsx: ${specifier}`);
  };
  Function('module', 'exports', 'require', outputText)(
    moduleRecord,
    moduleRecord.exports,
    stubRequire,
  );

  const render = () => {
    runtime.cursor = 0;
    const element = moduleRecord.exports.AuthProvider({ children: null });
    return element.props.value;
  };
  const context = render();
  mountEffects();
  return {
    render,
    context,
    meCalls,
    refreshCalls,
    loginCalls,
    twoFactorCalls,
    logoutCalls,
    refreshWaiterCalls,
    queryClient,
    router,
    sessionSync,
    setSessionHint: (value) => {
      sessionHint.value = value;
    },
    isLogoutInProgress: () => sessionHint.logoutInProgress === true,
    setLogoutInProgress: (value) => {
      sessionHint.logoutInProgress = value;
    },
    setPathname: (value) => {
      pathname.value = value;
    },
    runLastPendingEffect: () => {
      const fn = runtime.pendingEffects.pop();
      if (fn) {
        const cleanup = fn();
        if (typeof cleanup === 'function') {
          runtime.cleanups.push(cleanup);
        }
      }
    },
    unmount: unmountEffects,
    remount: remountEffects,
  };
}

function loginResponse(user) {
  return { user, accessToken: 'stub-access', refreshToken: 'stub-refresh' };
}

class SessionVerificationErrorStub extends Error {
  constructor(message) {
    super(message);
    this.name = 'SessionVerificationError';
  }
}

test('session race: a late session-init /me response cannot overwrite a completed sign-in', async () => {
  const mounted = mountAuthProvider();
  assert.equal(mounted.meCalls.length, 1);
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 2, 'login must verify the session cookie via /auth/me');
  mounted.meCalls[1].resolve(USER_B);
  await loginPromise;
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);

  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);
});

test('session race: a late session-init failure cannot clear a completed sign-in', async () => {
  const mounted = mountAuthProvider();
  assert.equal(mounted.meCalls.length, 1);
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[1].resolve(USER_B);
  await loginPromise;
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);

  const clearsBefore = mounted.queryClient.clearCount;
  mounted.meCalls[0].reject(new Error('stale init request'));
  await settle();
  assert.equal(
    mounted.refreshCalls.length,
    0,
    'a stale /me rejection must not trigger the refresh path of a newer session',
  );
  assert.equal(mounted.render().user?.id, USER_B.id);
  assert.equal(mounted.queryClient.clearCount, clearsBefore);
});

test('session race: logout blocks pending refresh work and stale responses cannot resurrect a user', async () => {
  const mounted = mountAuthProvider();
  assert.equal(mounted.meCalls.length, 1);
  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  assert.equal(mounted.render().isLoggingOut, true);

  void mounted.context.refreshUser();
  await settle();
  assert.equal(
    mounted.meCalls.length,
    1,
    'refreshUser during a pending logout must not issue another /auth/me',
  );

  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().user, null);

  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user, null);
});

test('session race: between overlapping refreshes the latest-started result wins', async () => {
  const mounted = mountAuthProvider();
  assert.equal(mounted.meCalls.length, 1);

  const second = mounted.context.refreshUser();
  await settle();
  assert.equal(mounted.meCalls.length, 2);

  mounted.meCalls[1].resolve(USER_B);
  await second;
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);

  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);
});

test('session race: a refresh that resolves the same user still commits normally', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);
  assert.equal(mounted.render().isLoading, false);

  const refresh = mounted.context.refreshUser();
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].resolve(USER_A);
  await refresh;
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);
});

test('session race: a refresh during a pending sign-in issues no /auth/me', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  assert.equal(mounted.refreshWaiterCalls.length, 1);

  void mounted.context.refreshUser();
  await settle();
  assert.equal(
    mounted.meCalls.length,
    1,
    'refreshUser during a pending login must not issue another /auth/me',
  );

  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.loginCalls.length, 1);
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].resolve(USER_B);
  await loginPromise;
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);
});

test('session race: two-factor verification invalidates a pending /auth/me', async () => {
  const mounted = mountAuthProvider();
  assert.equal(mounted.meCalls.length, 1);

  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  assert.equal(mounted.refreshWaiterCalls.length, 1);
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.twoFactorCalls.length, 1);

  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user, null);

  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  assert.equal(mounted.meCalls.length, 2, 'two-factor must verify the session cookie via /auth/me');
  mounted.meCalls[1].resolve(USER_B);
  const verified = await verifyPromise;
  await settle();
  assert.equal(verified.id, USER_B.id);
  assert.equal(mounted.render().user?.id, USER_B.id);
});

test('session race: a current-generation /me failure refreshes once, then commits or clears', async () => {
  const mounted = mountAuthProvider();
  assert.equal(mounted.meCalls.length, 1);

  mounted.meCalls[0].reject(new Error('expired access token'));
  await settle();
  assert.equal(mounted.refreshCalls.length, 1);
  mounted.refreshCalls[0].resolve({});
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);
  assert.equal(mounted.render().isLoading, false);

  const refresh = mounted.context.refreshUser();
  await settle();
  assert.equal(mounted.meCalls.length, 3);
  mounted.meCalls[2].reject(new Error('expired again'));
  await settle();
  assert.equal(mounted.refreshCalls.length, 2);
  mounted.refreshCalls[1].reject(new Error('refresh rejected'));
  await settle();
  assert.equal(mounted.meCalls.length, 3, 'a rejected refresh must not probe /auth/me again');
  await refresh;
  await settle();
  assert.equal(mounted.render().user, null);
});

test('session race: unmount cleanup invalidates pending results and a remount owns loading', async () => {
  const mounted = mountAuthProvider();
  assert.equal(mounted.meCalls.length, 1);

  mounted.unmount();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user, null);
  assert.equal(mounted.render().isLoading, true);

  mounted.remount();
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].resolve(USER_B);
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);
  assert.equal(mounted.render().isLoading, false);
});

test('session race: a stale sign-in completing after logout cannot commit', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  assert.equal(mounted.refreshWaiterCalls.length, 1);

  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().user, null);

  mounted.refreshWaiterCalls[0].resolve();
  await assert.rejects(loginPromise);
  await settle();
  assert.equal(mounted.render().user, null);
  assert.equal(mounted.loginCalls.length, 0, 'a superseded sign-in must not reach the login API');
  assert.equal(mounted.meCalls.length, 1, 'a superseded sign-in must not issue a verification /me');
});

test('session race: a stale two-factor completion after logout cannot commit', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  assert.equal(mounted.refreshWaiterCalls.length, 1);

  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().user, null);

  mounted.refreshWaiterCalls[0].resolve();
  await assert.rejects(verifyPromise);
  await settle();
  assert.equal(mounted.render().user, null);
  assert.equal(mounted.twoFactorCalls.length, 0, 'a superseded verification must not reach the API');
});

test('session race: a failed or challenged sign-in releases the mutation guard', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const failed = mounted.context.login(USER_A.email, 'bad');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].reject(new Error('bad credentials'));
  await assert.rejects(failed);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  void mounted.context.refreshUser();
  await settle();
  assert.equal(mounted.meCalls.length, 2, 'refresh must resume once the failed sign-in released the guard');
  mounted.meCalls[1].resolve(USER_A);
  await settle();

  const challenged = mounted.context.login(USER_A.email, 'needs-2fa');
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  mounted.loginCalls[1].resolve({ challengeId: 'ch-1', expiresIn: 300 });
  const challenge = await challenged;
  await settle();
  assert.equal(challenge.challengeId, 'ch-1');
  assert.equal(mounted.render().user?.id, USER_A.id);

  void mounted.context.refreshUser();
  await settle();
  assert.equal(mounted.meCalls.length, 3, 'refresh must resume once the 2FA challenge released the guard');
  mounted.meCalls[2].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);
});

test('session race: a hint-less refresh invalidates the older in-flight /me', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  void mounted.context.refreshUser();
  await settle();
  assert.equal(mounted.meCalls.length, 2);

  mounted.setSessionHint(false);
  void mounted.context.refreshUser();
  await settle();
  assert.equal(mounted.render().user, null);

  mounted.meCalls[1].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user, null);
});

test('session race: a superseded logout cannot clear a newer session or cache', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);

  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].resolve(USER_B);
  await loginPromise;
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);
  const clearsAfterLogin = mounted.queryClient.clearCount;

  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);
  assert.equal(mounted.queryClient.clearCount, clearsAfterLogin);
});

test('session race: explicit session mutations wait out a pending refresh', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  assert.equal(mounted.refreshWaiterCalls.length, 1);
  assert.equal(mounted.loginCalls.length, 0, 'login must not run while the refresh waiter is unsettled');
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.loginCalls.length, 1);
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].resolve(USER_B);
  await loginPromise;
  await settle();
  assert.equal(mounted.render().user?.id, USER_B.id);

  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  assert.equal(mounted.refreshWaiterCalls.length, 2);
  assert.equal(mounted.twoFactorCalls.length, 0, '2FA must not run while the refresh waiter is unsettled');
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  assert.equal(mounted.twoFactorCalls.length, 1);
  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  assert.equal(mounted.meCalls.length, 3, 'two-factor must verify the session cookie via /auth/me');
  mounted.meCalls[2].resolve(USER_B);
  await verifyPromise;
  await settle();

  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  assert.equal(mounted.refreshWaiterCalls.length, 3);
  assert.equal(mounted.logoutCalls.length, 0, 'logout must not run while the refresh waiter is unsettled');
  mounted.refreshWaiterCalls[2].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().user, null);
});

test('session race: logout marks the shared logout window while in flight and clears it on teardown', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);
  assert.equal(mounted.isLogoutInProgress(), false);

  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  assert.equal(
    mounted.isLogoutInProgress(),
    true,
    'the marker must be visible to the axios interceptor before identity teardown',
  );
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().user, null);
  assert.equal(
    mounted.isLogoutInProgress(),
    false,
    'a redirect:false logout clears the marker inline when teardown commits',
  );
});

test('session race: an explicit logout navigation holds the marker until the pathname changes', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();

  const logoutPromise = mounted.context.logout({
    redirect: '/en/login?reason=signed-out',
  });
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(
    mounted.isLogoutInProgress(),
    true,
    'the marker must outlive logout() while the navigation to the login page is pending',
  );
});

test('session race: a role-bounce navigation mid-logout cannot release the suppress flag early', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();

  const logoutPromise = mounted.context.logout();
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().isLoggingOut, true);

  // A layout guard bounces the user somewhere else before the login commit
  // (observed: the dashboard layout redirected an admin to /admin mid-logout
  // and the page's !user guard then won with reason=session-expired).
  mounted.setPathname('/en/admin');
  mounted.render();
  mounted.runLastPendingEffect();
  await settle();
  assert.equal(
    mounted.render().isLoggingOut,
    true,
    'only the logout destination may release the suppress flag',
  );
  assert.equal(
    mounted.isLogoutInProgress(),
    true,
    'the interceptor stays guarded until the login commit',
  );

  mounted.setPathname('/en/login');
  mounted.render();
  mounted.runLastPendingEffect();
  await settle();
  assert.equal(mounted.render().isLoggingOut, false);
  assert.equal(mounted.isLogoutInProgress(), false);
});

test('login clears a stale shared logout marker before starting', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  mounted.setLogoutInProgress(true);

  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  assert.equal(
    mounted.isLogoutInProgress(),
    false,
    'starting a new login must clear the marker so a post-login 401 is not misclassified',
  );
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[1].resolve(USER_B);
  await loginPromise;
  await settle();
});

function loadApiModule() {
  const postCalls = [];
  const getCalls = [];
  const logoutInProgress = { value: false };
  const apiCalls = [];
  const timers = [];
  const sessionSyncPublished = [];
  const interceptors = { responseError: null };
  const fakeSetTimeout = (fn, ms) => {
    const entry = { fn, ms, fired: false, cleared: false };
    timers.push(entry);
    return entry;
  };
  const fakeClearTimeout = (entry) => {
    if (entry) entry.cleared = true;
  };
  const fireTimers = (ms) => {
    timers
      .filter((entry) => entry.ms === ms && !entry.fired && !entry.cleared)
      .forEach((entry) => {
        entry.fired = true;
        entry.fn();
      });
  };
  const fakeApi = Object.assign(
    (config) => {
      const call = deferred();
      call.args = [config];
      apiCalls.push(call);
      return call.promise;
    },
    {
      interceptors: {
        request: { use: () => {} },
        response: {
          use: (_success, error) => {
            interceptors.responseError = error;
          },
        },
      },
      post: (...args) => {
        const call = deferred();
        call.args = args;
        postCalls.push(call);
        return call.promise;
      },
      get: (...args) => {
        const call = deferred();
        call.args = args;
        getCalls.push(call);
        return call.promise;
      },
    },
  );
  const stubs = {
    axios: {
      __esModule: true,
      default: { create: () => fakeApi },
      AxiosError: class extends Error {},
    },
    '@/i18n/paths': {
      addLocalePrefix: (value) => value,
      stripLocaleFromPathname: (value) => {
        const match = value.match(/^\/(en|vi)(?=\/|$)/);
        return {
          locale: match ? match[1] : '',
          pathname: match ? value.replace(/^\/(en|vi)/, '') || '/' : value,
        };
      },
    },
    '@/lib/public-api-url': { resolvePublicApiBaseUrl: () => '/api/v1' },
    '@/lib/session-hint': {
      CSRF_COOKIE_NAME: 'cc_csrf',
      isLogoutInProgress: () => logoutInProgress.value,
    },
    '@/lib/session-sync': {
      publishSessionChange: (kind) => sessionSyncPublished.push(kind),
    },
  };
  const source = readSource('src/lib/api.ts');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: {
      module: ts.ModuleKind.CommonJS,
      target: ts.ScriptTarget.ES2020,
      esModuleInterop: true,
    },
    fileName: 'api.ts',
  });
  const moduleRecord = { exports: {} };
  const stubRequire = (specifier) => {
    if (Object.prototype.hasOwnProperty.call(stubs, specifier)) {
      return stubs[specifier];
    }
    throw new Error(`unexpected import in api.ts: ${specifier}`);
  };
  Function('module', 'exports', 'require', 'setTimeout', 'clearTimeout', outputText)(
    moduleRecord,
    moduleRecord.exports,
    stubRequire,
    fakeSetTimeout,
    fakeClearTimeout,
  );
  return {
    exports: moduleRecord.exports,
    postCalls,
    getCalls,
    apiCalls,
    interceptors,
    fireTimers,
    sessionSyncPublished,
    setLogoutInProgress: (value) => {
      logoutInProgress.value = value;
    },
  };
}

test('waitForPendingSessionRefresh waits on an existing refresh only and never starts one', async () => {
  const { exports: apiModule, postCalls } = loadApiModule();

  await apiModule.waitForPendingSessionRefresh();
  assert.equal(postCalls.length, 0, 'with no pending refresh the waiter must not start one');

  const refresh = apiModule.refreshSessionSingleFlight();
  await settle();
  assert.equal(postCalls.length, 1);
  const refreshConfig = postCalls[0].args[2];
  assert.equal(refreshConfig.timeout, 15000, 'the refresh transport must carry a bounded timeout');
  assert.equal(refreshConfig.skipAuthRefresh, true);
  assert.equal(refreshConfig.skipAuthRedirect, true);

  let waiterSettled = false;
  const waiter = apiModule.waitForPendingSessionRefresh().then(() => {
    waiterSettled = true;
  });
  await settle();
  assert.equal(waiterSettled, false, 'the waiter must stay pending while the refresh is in flight');
  assert.equal(postCalls.length, 1);

  postCalls[0].resolve({ data: { user: USER_B } });
  await waiter;
  await refresh;
  assert.equal(waiterSettled, true);
  assert.equal(postCalls.length, 1, 'waiting must never issue another transport request');

  const failingRefresh = apiModule.refreshSessionSingleFlight();
  await settle();
  assert.equal(postCalls.length, 2);
  const failedExpectation = assert.rejects(failingRefresh);
  const failedWaiter = apiModule.waitForPendingSessionRefresh();
  postCalls[1].reject(new Error('refresh rejected'));
  await failedWaiter;
  await failedExpectation;
  assert.equal(postCalls.length, 2, 'a rejected refresh must release the waiter without a retry');
});

test('session race: an in-flight sign-in response arriving after logout cannot commit', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.loginCalls.length, 1);

  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().user, null);

  const rejection = assert.rejects(loginPromise, /superseded/i);
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await rejection;
  await settle();
  assert.equal(mounted.render().user, null);
  assert.equal(mounted.meCalls.length, 1, 'a superseded in-flight sign-in must not issue a verification /me');
});

test('session race: an in-flight two-factor response arriving after logout cannot commit', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.twoFactorCalls.length, 1);

  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(mounted.render().user, null);

  const rejection = assert.rejects(verifyPromise, /superseded/i);
  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await rejection;
  await settle();
  assert.equal(mounted.render().user, null);
});

test('session race: a redirecting logout clears the suppress flag only after navigation commits', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const logoutPromise = mounted.context.logout();
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();

  assert.equal(mounted.render().isLoggingOut, true,
      'the suppress flag must hold while the redirect is still in flight');

  mounted.setPathname('/en/login');
  mounted.render();
  mounted.runLastPendingEffect();
  await settle();
  assert.equal(mounted.render().isLoggingOut, false,
      'the flag must clear once the logout navigation has committed');
});

test('session race: password-rotated custom logout preserves its target until navigation commits', async () => {
  const mounted = mountAuthProvider();
  const replacements = [];
  mounted.router.replace = (target) => replacements.push(target);
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  const target = '/en/login?reason=password-rotated';

  const logoutPromise = mounted.render().logout({ redirect: target });
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.render().isLoggingOut, true);
  assert.deepEqual(replacements, []);
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();

  const signedOut = mounted.render();
  assert.equal(signedOut.user, null);
  assert.deepEqual(replacements, [target]);
  assert.equal(signedOut.isLoggingOut, true,
    'protected shells must remain suppressed while the custom redirect is in flight');

  mounted.setPathname('/en/login');
  mounted.render();
  mounted.runLastPendingEffect();
  await settle();
  assert.equal(mounted.render().isLoggingOut, false);
  assert.deepEqual(replacements, [target], 'pathname commit must not replace the custom target');
});

test('session race: a superseded password-rotated custom logout cannot redirect a newer session', async () => {
  const mounted = mountAuthProvider();
  const replacements = [];
  mounted.router.replace = (target) => replacements.push(target);
  mounted.meCalls[0].resolve(USER_A);
  await settle();

  const logoutPromise = mounted.render().logout({ redirect: '/en/login?reason=password-rotated' });
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);

  const loginPromise = mounted.render().login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[1].resolve(USER_B);
  await loginPromise;
  await settle();
  const clearsAfterLogin = mounted.queryClient.clearCount;

  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  const current = mounted.render();
  assert.equal(current.user?.id, USER_B.id);
  assert.equal(current.isLoggingOut, false);
  assert.equal(mounted.queryClient.clearCount, clearsAfterLogin);
  assert.deepEqual(replacements, [], 'stale logout must never navigate away from the newer session');
});

test('session race: two-factor sign-in verifies the session cookie before committing', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  assert.equal(mounted.meCalls.length, 0, 'no session hint means no bootstrap /me');

  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.twoFactorCalls.length, 1);

  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  assert.equal(mounted.meCalls.length, 1, 'two-factor must probe /auth/me before committing');
  assert.equal(mounted.render().user, null, 'identity must not commit before the cookie probe');

  mounted.meCalls[0].resolve(USER_B);
  const verified = await verifyPromise;
  await settle();
  assert.equal(verified.id, USER_B.id);
  assert.equal(mounted.render().user?.id, USER_B.id);
});

test('session race: two-factor must reject when the cookie probe cannot confirm the session', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  assert.equal(mounted.meCalls.length, 1);
  mounted.meCalls[0].reject(new Error('cookie not visible yet'));
  await settle();
  assert.equal(mounted.meCalls.length, 2, 'the probe retries once');
  const clearsBefore = mounted.queryClient.clearCount;
  mounted.meCalls[1].reject(new Error('cookie still not visible'));

  await assert.rejects(
    verifyPromise,
    (error) =>
      error instanceof SessionVerificationErrorStub && /verification session unverified/.test(error.message),
    'probe failures must reject with the dedicated session-verification error',
  );
  await settle();
  assert.equal(mounted.render().user, null, 'an unverified session must never commit the claimed identity');
  assert.ok(
    mounted.queryClient.clearCount > clearsBefore,
    'a failed verification must clear cached identity data',
  );
});

test('session race transport: a pending sign-in must hold the refresh dispatch', async () => {
  const { exports: apiModule, postCalls } = loadApiModule();

  const login = apiModule.authApi.login(USER_B.email, 'pw-irrelevant');
  const loginOutcome = login.then(() => 'resolved', () => 'rejected');
  await settle();
  assert.equal(postCalls.length, 1);
  assert.equal(postCalls[0].args[0], '/auth/login');

  const refresh = apiModule.refreshSessionSingleFlight();
  const refreshOutcome = refresh.then(() => 'resolved', () => 'rejected');
  await settle();
  assert.equal(
    postCalls.length,
    1,
    'a second cookie-writing transport must not dispatch while a sign-in is in flight',
  );

  postCalls[0].resolve({ data: loginResponse(USER_B) });
  await settle();
  assert.equal(postCalls.length, 2, 'the refresh must dispatch once the sign-in settles');
  assert.equal(postCalls[1].args[0], '/auth/refresh');
  postCalls[1].resolve({ data: loginResponse(USER_B) });
  assert.equal(await refreshOutcome, 'resolved');
  assert.equal(await loginOutcome, 'resolved');
});

test('session race transport: a pending sign-in must hold the logout dispatch', async () => {
  const { exports: apiModule, postCalls } = loadApiModule();

  const login = apiModule.authApi.login(USER_B.email, 'pw-irrelevant');
  const loginOutcome = login.then(() => 'resolved', () => 'rejected');
  await settle();
  assert.equal(postCalls.length, 1);
  assert.equal(postCalls[0].args[0], '/auth/login');

  const logout = apiModule.authApi.logout();
  const logoutOutcome = logout.then(() => 'resolved', () => 'rejected');
  await settle();
  assert.equal(
    postCalls.length,
    1,
    'a cookie-clearing logout must not dispatch while a sign-in is in flight',
  );

  postCalls[0].resolve({ data: loginResponse(USER_B) });
  await settle();
  assert.equal(postCalls.length, 2, 'the logout must dispatch once the sign-in settles');
  assert.equal(postCalls[1].args[0], '/auth/logout');
  postCalls[1].resolve({ data: {} });
  assert.equal(await logoutOutcome, 'resolved');
  assert.equal(await loginOutcome, 'resolved');
});

test('session race transport: a pending two-factor verify must hold the logout dispatch', async () => {
  const { exports: apiModule, postCalls } = loadApiModule();

  const verify = apiModule.authApi.verifyTwoFactor('challenge-1', '123456');
  const verifyOutcome = verify.then(() => 'resolved', () => 'rejected');
  await settle();
  assert.equal(postCalls.length, 1);
  assert.equal(postCalls[0].args[0], '/auth/two-factor/verify');

  const logout = apiModule.authApi.logout();
  const logoutOutcome = logout.then(() => 'resolved', () => 'rejected');
  await settle();
  assert.equal(
    postCalls.length,
    1,
    'a cookie-clearing logout must not dispatch while a two-factor verify is in flight',
  );

  postCalls[0].resolve({ data: loginResponse(USER_B) });
  await settle();
  assert.equal(postCalls.length, 2, 'the logout must dispatch once the verify settles');
  assert.equal(postCalls[1].args[0], '/auth/logout');
  postCalls[1].resolve({ data: {} });
  assert.equal(await logoutOutcome, 'resolved');
  assert.equal(await verifyOutcome, 'resolved');
});


test('session race: sign-in must reject when every session probe fails', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 1);
  mounted.meCalls[0].reject(new Error('network down'));
  await settle();
  assert.equal(mounted.loginCalls.length, 2, 'the probe retry resubmits the credentials transport');
  mounted.loginCalls[1].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  const clearsBefore = mounted.queryClient.clearCount;
  mounted.meCalls[1].reject(new Error('still down'));

  await assert.rejects(
    loginPromise,
    (error) =>
      error instanceof SessionVerificationErrorStub && /sign-in session unverified/.test(error.message),
    'probe failures must reject with the dedicated session-verification error',
  );
  await settle();
  assert.equal(mounted.render().user, null, 'an unverified sign-in must never commit the claimed identity');
  assert.ok(
    mounted.queryClient.clearCount > clearsBefore,
    'a failed sign-in must clear cached identity data',
  );

  mounted.setSessionHint(true);
  void mounted.context.refreshUser();
  await settle();
  assert.equal(mounted.meCalls.length, 3, 'the rejected sign-in must release the mutation guard');
});

test('session race: a re-login challenge answer must not bypass session verification', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[0].reject(new Error('network down'));
  await settle();
  assert.equal(mounted.loginCalls.length, 2);
  mounted.loginCalls[1].resolve({ challengeId: 'ch-second', expiresIn: 300 });
  await settle();
  assert.equal(mounted.meCalls.length, 2, 'a challenged retry answer still requires the matching probe');
  mounted.meCalls[1].reject(new Error('still down'));

  await assert.rejects(loginPromise, /sign-in session unverified/);
  await settle();
  assert.equal(mounted.render().user, null, 'the first response identity must never commit unverified');
});

test('session race: a stale-account probe followed by a network failure must reject the sign-in', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.loginCalls.length, 2, 'a mismatched probe retries the sign-in transport');
  mounted.loginCalls[1].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].reject(new Error('network down'));

  await assert.rejects(loginPromise, /sign-in session unverified/);
  await settle();
  assert.equal(mounted.render().user, null, 'a stale cookie must never commit the claimed identity');
});

test('session race: a stale-account probe followed by a network failure must reject two-factor', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.meCalls.length, 2, 'a mismatched probe retries the session probe');
  mounted.meCalls[1].reject(new Error('network down'));

  await assert.rejects(verifyPromise, /verification session unverified/);
  await settle();
  assert.equal(mounted.render().user, null);
});

test('session race: probes answering a different account twice reject with a mismatch error', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  mounted.loginCalls[1].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[1].resolve(USER_A);

  await assert.rejects(
    loginPromise,
    (error) =>
      error instanceof SessionVerificationErrorStub && /sign-in session mismatch/.test(error.message),
    'a foreign-account probe pair must reject with the dedicated mismatch error',
  );
  await settle();
  assert.equal(mounted.render().user, null);
});

test('session race: two-factor probes answering a different account twice reject with a mismatch error', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  mounted.meCalls[1].resolve(USER_A);

  await assert.rejects(
    verifyPromise,
    (error) =>
      error instanceof SessionVerificationErrorStub && /verification session mismatch/.test(error.message),
    'a foreign-account probe pair must reject with the dedicated mismatch error',
  );
  await settle();
  assert.equal(mounted.render().user, null);
});

test('session race: two-factor commits when the retried probe confirms the session', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  mounted.meCalls[0].reject(new Error('transient'));
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].resolve(USER_B);

  const verified = await verifyPromise;
  await settle();
  assert.equal(verified.id, USER_B.id);
  assert.equal(mounted.render().user?.id, USER_B.id);
});

test('session race: sign-in commits when the retried probe confirms the session', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[0].reject(new Error('transient'));
  await settle();
  assert.equal(mounted.loginCalls.length, 2);
  mounted.loginCalls[1].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 2);
  mounted.meCalls[1].resolve(USER_B);

  const user = await loginPromise;
  await settle();
  assert.equal(user.id, USER_B.id);
  assert.equal(mounted.render().user?.id, USER_B.id);
});

test('session race: a same-path logout clears the suppress flag inline', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  mounted.setPathname('/en/login');
  const context = mounted.render();
  const logoutPromise = context.logout();
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();

  assert.equal(
    mounted.render().isLoggingOut,
    false,
    'a logout that stays on the same path must clear the flag inline',
  );
});

test('session race: a failed superseding sign-in still releases the logout flag', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);

  const logoutPromise = mounted.context.logout();
  await settle();
  assert.equal(mounted.render().isLoggingOut, true);
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  assert.equal(mounted.logoutCalls.length, 1);

  const loginPromise = mounted.context.login(USER_B.email, 'bad');
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  mounted.loginCalls[0].reject(new Error('bad credentials'));
  await assert.rejects(loginPromise);

  mounted.logoutCalls[0].resolve({});
  await logoutPromise;
  await settle();
  assert.equal(
    mounted.render().isLoggingOut,
    false,
    'the superseded logout must release the flag when the newer sign-in failed',
  );
});

test('session transport: every cookie-writing call and the session probe carry bounded timeouts', async () => {
  const { exports: apiModule, postCalls, getCalls } = loadApiModule();

  const login = apiModule.authApi.login(USER_B.email, 'pw-irrelevant').then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(postCalls.length, 1);
  assert.equal(postCalls[0].args[2].timeout, 15000, 'login transport timeout');
  assert.equal(postCalls[0].args[2].skipAuthRefresh, true);
  assert.equal(postCalls[0].args[2].skipAuthRedirect, true);
  postCalls[0].resolve({ data: loginResponse(USER_B) });
  assert.equal(await login, 'resolved');

  const verify = apiModule.authApi.verifyTwoFactor('challenge-1', '123456').then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(postCalls.length, 2);
  assert.equal(postCalls[1].args[0], '/auth/two-factor/verify');
  assert.equal(postCalls[1].args[2].timeout, 15000, 'verify transport timeout');
  assert.equal(postCalls[1].args[2].skipAuthRefresh, true);
  assert.equal(postCalls[1].args[2].skipAuthRedirect, true);
  postCalls[1].resolve({ data: loginResponse(USER_B) });
  assert.equal(await verify, 'resolved');

  const logout = apiModule.authApi.logout().then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(postCalls.length, 3);
  assert.equal(postCalls[2].args[2].timeout, 15000, 'logout transport timeout');
  assert.equal(postCalls[2].args[2].skipAuthRefresh, true);
  assert.equal(postCalls[2].args[2].skipAuthRedirect, true);
  postCalls[2].resolve({ data: {} });
  assert.equal(await logout, 'resolved');

  const me = apiModule.authApi.me().then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(getCalls.length, 1);
  assert.equal(getCalls[0].args[0], '/auth/me');
  assert.equal(getCalls[0].args[1].timeout, 15000, 'the session probe must carry a bounded timeout');
  assert.equal(
    getCalls[0].args[1].skipColdStartRetry,
    true,
    'the session probe must opt out of the cold-start retry',
  );
  assert.equal(getCalls[0].args[1].skipAuthRefresh, true);
  assert.equal(getCalls[0].args[1].skipAuthRedirect, true);
  getCalls[0].resolve({ data: USER_B });
  assert.equal(await me, 'resolved');
});

test('session transport: the cold-start retry honors the opt-out flag only', async () => {
  const { apiCalls, interceptors, fireTimers } = loadApiModule();
  assert.ok(interceptors.responseError, 'the module must register a response error handler');

  const skippedConfig = { method: 'get', url: '/auth/me', skipColdStartRetry: true };
  const skippedOutcome = interceptors
    .responseError({ config: skippedConfig, response: undefined })
    .then(
      () => 'resolved',
      () => 'rejected',
    );
  fireTimers(3000);
  await settle();
  assert.equal(apiCalls.length, 0, 'an opted-out probe must not take the warming retry');
  assert.equal(await skippedOutcome, 'rejected');

  const normalConfig = { method: 'get', url: '/sections' };
  const warmed = interceptors.responseError({ config: normalConfig, response: undefined });
  fireTimers(3000);
  await settle();
  assert.equal(apiCalls.length, 1, 'ordinary reads keep the single warming retry');
  apiCalls[0].resolve({ data: [] });
  await warmed;
});

test('session transport: a discovered 401 publishes signed-out to sibling tabs before redirecting', async () => {
  const { interceptors, getCalls, sessionSyncPublished } = loadApiModule();
  const previousWindow = globalThis.window;
  const previousDocument = globalThis.document;
  const location = { pathname: '/dashboard', origin: 'http://localhost', href: '' };
  globalThis.window = { location };
  globalThis.document = { cookie: '' };
  try {
    const config = { method: 'get', url: '/sections', skipAuthRefresh: true };
    await assert.rejects(interceptors.responseError({ config, response: { status: 401 } }));
    const meCalls = getCalls.filter((call) => call.args[0] === '/auth/me');
    assert.equal(meCalls.length, 1, 'the redirect boundary confirms the cookie once');
    meCalls[0].reject({ response: { status: 401 } });
    await settle();
    assert.deepEqual(
      sessionSyncPublished,
      ['signed-out'],
      'siblings must hear about the dead shared session, not discover it one-by-one',
    );
    assert.ok(location.href.includes('/login'), 'the failing tab still navigates to login');
  } finally {
    globalThis.window = previousWindow;
    globalThis.document = previousDocument;
  }
});

test('session transport: a dead cookie on /login does not reload the page in a loop', async () => {
  const { interceptors, getCalls, sessionSyncPublished } = loadApiModule();
  const previousWindow = globalThis.window;
  const previousDocument = globalThis.document;
  const location = { pathname: '/vi/login', origin: 'http://localhost', href: '' };
  globalThis.window = { location };
  globalThis.document = { cookie: '' };
  try {
    const config = { method: 'get', url: '/sections', skipAuthRefresh: true };
    await assert.rejects(interceptors.responseError({ config, response: { status: 401 } }));
    const meCalls = getCalls.filter((call) => call.args[0] === '/auth/me');
    assert.equal(meCalls.length, 1, 'the redirect boundary confirms the cookie once');
    meCalls[0].reject({ response: { status: 401 } });
    await settle();
    assert.deepEqual(sessionSyncPublished, ['signed-out'], 'siblings still converge on the dead session');
    assert.equal(location.href, '', 'already on /login — navigating again would reload the login page forever');
  } finally {
    globalThis.window = previousWindow;
    globalThis.document = previousDocument;
  }
});

test('session transport: an opted-out 401 probe publishes nothing', async () => {
  const { interceptors, sessionSyncPublished } = loadApiModule();
  const previousWindow = globalThis.window;
  const previousDocument = globalThis.document;
  globalThis.window = { location: { pathname: '/dashboard', origin: 'http://localhost', href: '' } };
  globalThis.document = {};
  try {
    const config = { method: 'get', url: '/auth/me', skipAuthRedirect: true, skipAuthRefresh: true };
    await assert.rejects(interceptors.responseError({ config, response: { status: 401 } }));
    assert.deepEqual(sessionSyncPublished, [], 'auth probes must never broadcast');
  } finally {
    globalThis.window = previousWindow;
    globalThis.document = previousDocument;
  }
});

function createLockManager() {
  const queued = [];
  const calls = [];
  let busy = false;
  const runNext = () => {
    if (busy) return;
    const next = queued.shift();
    if (!next) return;
    busy = true;
    calls.push(next.name);
    Promise.resolve()
      .then(() => next.task())
      .then(next.resolve, next.reject)
      .finally(() => {
        busy = false;
        runNext();
      });
  };
  return {
    calls,
    request(name, options, task) {
      if (typeof options === 'function') {
        task = options;
        options = {};
      }
      return new Promise((resolve, reject) => {
        const entry = { name, task, resolve, reject };
        const signal = options && options.signal;
        if (signal) {
          const onAbort = () => {
            const index = queued.indexOf(entry);
            if (index >= 0) {
              queued.splice(index, 1);
              reject(signal.reason ?? new Error('acquisition aborted'));
            }
          };
          if (signal.aborted) {
            reject(signal.reason ?? new Error('acquisition aborted'));
            return;
          }
          signal.addEventListener('abort', onAbort, { once: true });
        }
        queued.push(entry);
        runNext();
      });
    },
  };
}

function withLockGlobals(locks, fn) {
  const previousWindow = globalThis.window;
  const previousDocument = globalThis.document;
  const navigatorDescriptor = Object.getOwnPropertyDescriptor(globalThis, 'navigator');
  globalThis.window = {};
  globalThis.document = {};
  Object.defineProperty(globalThis, 'navigator', {
    value: { locks },
    configurable: true,
    writable: true,
  });
  return Promise.resolve()
    .then(fn)
    .finally(() => {
      delete globalThis.window;
      if (previousWindow !== undefined) {
        globalThis.window = previousWindow;
      }
      delete globalThis.document;
      if (previousDocument !== undefined) {
        globalThis.document = previousDocument;
      }
      if (navigatorDescriptor) {
        Object.defineProperty(globalThis, 'navigator', navigatorDescriptor);
      } else {
        delete globalThis.navigator;
      }
    });
}

function waitable(waiter, ms = 2000) {
  let timer;
  const safety = new Promise((resolve) => {
    timer = setTimeout(() => resolve('still-pending'), ms);
  });
  return Promise.race([waiter, safety]).finally(() => clearTimeout(timer));
}

test('session transport lock: a queued waiter aborted by the acquisition timeout never dispatches', async () => {
  const locks = createLockManager();
  await withLockGlobals(locks, async () => {
    const { exports: apiModule, postCalls, fireTimers } = loadApiModule();

    const holder = apiModule.authApi.login(USER_B.email, 'pw-irrelevant').then(
      () => 'resolved',
      () => 'rejected',
    );
    await settle();
    assert.equal(postCalls.length, 1);
    assert.equal(postCalls[0].args[0], '/auth/login');
    assert.equal(locks.calls.length, 1);

    const waiter = apiModule.authApi.logout().then(
      () => 'resolved',
      () => 'rejected',
    );
    await settle();
    assert.equal(postCalls.length, 1, 'the waiter must queue behind the held lock');

    fireTimers(30000);
    const waiterOutcome = await waitable(waiter);
    assert.equal(waiterOutcome, 'rejected', 'the acquisition timeout must reject the queued waiter');

    postCalls[0].resolve({ data: loginResponse(USER_B) });
    await settle();
    assert.equal(postCalls.length, 1, 'the aborted waiter must never dispatch its transport');
    assert.equal(await holder, 'resolved');

    const next = apiModule.authApi.logout().then(
      () => 'resolved',
      () => 'rejected',
    );
    await settle();
    assert.equal(postCalls.length, 2, 'a later call must still acquire the lock');
    postCalls[1].resolve({ data: {} });
    assert.equal(await next, 'resolved');
  });
});

test('session transport lock: the acquisition abort works without AbortSignal.timeout', async () => {
  const locks = createLockManager();
  const originalTimeout = AbortSignal.timeout;
  AbortSignal.timeout = function disabledTimeout() {
    throw new Error('AbortSignal.timeout must not be required');
  };
  try {
    await withLockGlobals(locks, async () => {
      const { exports: apiModule, postCalls, fireTimers } = loadApiModule();

      const holder = apiModule.authApi.login(USER_B.email, 'pw-irrelevant').then(
        () => 'resolved',
        () => 'rejected',
      );
      await settle();
      assert.equal(postCalls.length, 1);

      const waiter = apiModule.authApi.logout().then(
        () => 'resolved',
        () => 'rejected',
      );
      await settle();
      assert.equal(postCalls.length, 1);

      fireTimers(30000);
      const waiterOutcome = await waitable(waiter);
      assert.equal(
        waiterOutcome,
        'rejected',
        'the waiter must abort through the controller, not AbortSignal.timeout',
      );

      postCalls[0].resolve({ data: loginResponse(USER_B) });
      await settle();
      assert.equal(postCalls.length, 1);
      assert.equal(await holder, 'resolved');
    });
  } finally {
    AbortSignal.timeout = originalTimeout;
  }
});

test('session transport lock: a pre-aborted acquisition signal rejects instead of hanging', async () => {
  const locks = createLockManager();
  const controller = new AbortController();
  const reason = new Error('acquisition already aborted');
  controller.abort(reason);

  let dispatched = false;
  await assert.rejects(
    locks.request('campuscore-session-transport', { signal: controller.signal }, () => {
      dispatched = true;
      return Promise.resolve();
    }),
    /acquisition already aborted/,
  );
  assert.equal(dispatched, false);
});

test('session transport queue: a waiter times out without navigator.locks and never dispatches', async () => {
  const { exports: apiModule, postCalls, fireTimers } = loadApiModule();

  const holder = apiModule.authApi.login(USER_B.email, 'pw-irrelevant').then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(postCalls.length, 1);
  assert.equal(postCalls[0].args[0], '/auth/login');

  const waiter = apiModule.authApi.logout().then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(postCalls.length, 1, 'the waiter must queue behind the held login');

  fireTimers(30000);
  const waiterOutcome = await waitable(waiter);
  assert.equal(waiterOutcome, 'rejected', 'the bounded queue must abort the stale waiter');

  postCalls[0].resolve({ data: loginResponse(USER_B) });
  await settle();
  assert.equal(await holder, 'resolved');
  assert.equal(postCalls.length, 1, 'an expired waiter must never dispatch its cookie write');

  const next = apiModule.authApi.logout().then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(postCalls.length, 2, 'a fresh call must still acquire the queue after an expiry');
  postCalls[1].resolve({ data: {} });
  assert.equal(await next, 'resolved');
});

test('session transport queue: locks without AbortController still bound the acquisition wait', async () => {
  const locks = createLockManager();
  const originalController = globalThis.AbortController;
  try {
    globalThis.AbortController = undefined;
    await withLockGlobals(locks, async () => {
      const { exports: apiModule, postCalls, fireTimers } = loadApiModule();

      const holder = apiModule.authApi.login(USER_B.email, 'pw-irrelevant').then(
        () => 'resolved',
        () => 'rejected',
      );
      await settle();
      assert.equal(postCalls.length, 1);

      const waiter = apiModule.authApi.logout().then(
        () => 'resolved',
        () => 'rejected',
      );
      await settle();
      assert.equal(postCalls.length, 1);

      fireTimers(30000);
      const waiterOutcome = await waitable(waiter);
      assert.equal(waiterOutcome, 'rejected');
      assert.equal(locks.calls.length, 0, 'the unbounded lock branch must not run without AbortController');

      postCalls[0].resolve({ data: loginResponse(USER_B) });
      await settle();
      assert.equal(await holder, 'resolved');
      assert.equal(postCalls.length, 1, 'an expired waiter must never dispatch its cookie write');
    });
  } finally {
    globalThis.AbortController = originalController;
  }
});

test('session transport: a rejected cookie write releases the queue for the next call', async () => {
  const { exports: apiModule, postCalls } = loadApiModule();

  const login = apiModule.authApi.login(USER_B.email, 'pw-irrelevant').then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(postCalls.length, 1);
  postCalls[0].reject(new Error('login failed'));
  assert.equal(await login, 'rejected');

  const logout = apiModule.authApi.logout().then(
    () => 'resolved',
    () => 'rejected',
  );
  await settle();
  assert.equal(
    postCalls.length,
    2,
    'the next transport must dispatch once the rejected call releases the queue',
  );
  assert.equal(postCalls[1].args[0], '/auth/logout');
  postCalls[1].resolve({ data: {} });
  assert.equal(await logout, 'resolved');
});

function createSyncEnvironment({ storageMode = 'ok', broadcast = true, visibility = 'visible', focused = true } = {}) {
  const listeners = { window: new Map(), document: new Map() };
  const add = (target) => (type, fn) => {
    let set = listeners[target].get(type);
    if (!set) {
      set = new Set();
      listeners[target].set(type, set);
    }
    set.add(fn);
  };
  const remove = (target) => (type, fn) => {
    listeners[target].get(type)?.delete(fn);
  };
  const fire = (target, type, event = {}) => {
    [...(listeners[target].get(type) ?? [])].forEach((fn) => fn(event));
  };

  const realStorage = new Map();
  const storage = {
    getItem: (key) => (realStorage.has(key) ? realStorage.get(key) : null),
    setItem: (key, value) => {
      realStorage.set(key, String(value));
    },
    removeItem: (key) => {
      realStorage.delete(key);
    },
    clear: () => realStorage.clear(),
    key: (index) => [...realStorage.keys()][index] ?? null,
    get length() {
      return realStorage.size;
    },
  };
  const blockedStorage = {
    getItem: () => {
      throw new Error('storage blocked');
    },
    setItem: () => {
      throw new Error('storage blocked');
    },
    removeItem: () => {
      throw new Error('storage blocked');
    },
    clear: () => {},
    key: () => null,
    length: 0,
  };

  const windowObj = {
    localStorage: storageMode === 'blocked' ? blockedStorage : storage,
    addEventListener: add('window'),
    removeEventListener: remove('window'),
    fire: (type, event) => fire('window', type, event),
  };
  const documentObj = {
    visibilityState: visibility,
    focused,
    hasFocus() {
      return this.focused;
    },
    addEventListener: add('document'),
    removeEventListener: remove('document'),
    fire: (type, event) => fire('document', type, event),
  };

  const channels = [];
  let FakeBroadcastChannel;
  if (broadcast === 'throws') {
    FakeBroadcastChannel = class {
      constructor() {
        throw new Error('BroadcastChannel blocked');
      }
    };
  } else if (broadcast) {
    FakeBroadcastChannel = class {
      constructor(name) {
        this.name = name;
        this.onmessage = null;
        this.closed = false;
        this.posted = [];
        channels.push(this);
      }
      postMessage(data) {
        this.posted.push(data);
        for (const other of channels) {
          if (
            other !== this &&
            !other.closed &&
            other.name === this.name &&
            typeof other.onmessage === 'function'
          ) {
            other.onmessage({ data });
          }
        }
      }
      close() {
        this.closed = true;
      }
    };
  }

  return {
    window: windowObj,
    document: documentObj,
    BroadcastChannel: FakeBroadcastChannel,
    channels,
    storage,
    listeners,
    fire,
  };
}

function loadSessionSyncModule(env) {
  const source = readSource('src/lib/session-sync.ts');
  const { outputText } = ts.transpileModule(source, {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
    fileName: 'session-sync.ts',
  });
  const moduleRecord = { exports: {} };
  const stubRequire = (specifier) => {
    if (specifier === '@/lib/api') {
      return { createRequestId: () => crypto.randomUUID() };
    }
    throw new Error(`unexpected import in session-sync.ts: ${specifier}`);
  };
  Function('module', 'exports', 'require', 'window', 'document', 'BroadcastChannel', outputText)(
    moduleRecord,
    moduleRecord.exports,
    stubRequire,
    env?.window,
    env?.document,
    env?.BroadcastChannel,
  );
  return moduleRecord.exports;
}

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function externalPayload(overrides = {}) {
  return {
    revision: crypto.randomUUID(),
    source: crypto.randomUUID(),
    kind: 'changed',
    ...overrides,
  };
}

test('session sync: a published marker carries exactly revision, source and kind', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  sync.publishSessionChange('changed');

  const raw = env.storage.getItem('campuscore-session-revision');
  assert.ok(raw, 'the storage marker must be written');
  const payload = JSON.parse(raw);
  assert.deepEqual(Object.keys(payload).sort(), ['kind', 'revision', 'source']);
  assert.match(payload.revision, UUID_RE);
  assert.match(payload.source, UUID_RE);
  assert.equal(payload.kind, 'changed');
  assert.doesNotMatch(raw, /email|token|password|Bearer/i, 'no identity data may leak into the marker');

  assert.equal(env.channels.length, 1, 'publish uses a short-lived channel object');
  assert.deepEqual(env.channels[0].posted[0], payload);
  assert.equal(env.channels[0].closed, true, 'the publisher channel is closed after posting');
});

test('session sync: an own-source broadcast from a separate channel object is ignored', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));
  assert.equal(env.channels.length, 1, 'the subscriber owns its channel object');

  sync.publishSessionChange('changed');
  assert.equal(received.length, 0, 'a same-document broadcast must be filtered by source id');
});

test('session sync: duplicate revisions are delivered once and foreign payloads apply', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  const ext = new env.BroadcastChannel('campuscore-session-events');
  const payload = externalPayload({ kind: 'signed-out' });
  ext.postMessage(payload);
  ext.postMessage(payload);
  assert.deepEqual(received, ['signed-out']);
});

test('session sync: malformed payloads are ignored on every transport', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  const ext = new env.BroadcastChannel('campuscore-session-events');
  ext.postMessage('not-an-object');
  ext.postMessage(null);
  ext.postMessage({ revision: crypto.randomUUID(), source: crypto.randomUUID() });
  ext.postMessage({ revision: 'nope', source: crypto.randomUUID(), kind: 'changed' });
  ext.postMessage(externalPayload({ kind: 'wiped' }));
  ext.postMessage(externalPayload({ extra: 'field' }));
  assert.equal(received.length, 0);

  env.window.fire('storage', { key: 'campuscore-session-revision', newValue: '{broken' });
  env.window.fire('storage', { key: 'unrelated-key', newValue: '{}' });
  env.window.fire('storage', { key: 'campuscore-session-revision', newValue: null });
  assert.equal(received.length, 0);
});

test('session sync: a storage event delivers the change when BroadcastChannel is missing', () => {
  const env = createSyncEnvironment({ broadcast: false });
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  const payload = externalPayload({ kind: 'signed-out' });
  env.window.fire('storage', {
    key: 'campuscore-session-revision',
    newValue: JSON.stringify(payload),
  });
  assert.deepEqual(received, ['signed-out']);
});

test('session sync: a channel message delivers the change when storage is blocked', () => {
  const env = createSyncEnvironment({ storageMode: 'blocked' });
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  const ext = new env.BroadcastChannel('campuscore-session-events');
  ext.postMessage(externalPayload());
  assert.deepEqual(received, ['changed']);
});

test('session sync: resume forces a resync when storage is unreadable even with a live channel', () => {
  const env = createSyncEnvironment({ storageMode: 'blocked' });
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));
  assert.equal(env.channels.length, 1, 'the channel is still live');

  env.window.fire('blur');
  env.window.fire('focus');
  assert.deepEqual(received, ['changed'], 'blocked storage must force a fail-closed resync');
});

test('session sync: a stored marker is absorbed without an initial resync', () => {
  const env = createSyncEnvironment();
  env.storage.setItem(
    'campuscore-session-revision',
    JSON.stringify(externalPayload({ kind: 'signed-out' })),
  );
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  assert.equal(received.length, 0, 'subscribing must never resync from a pre-existing marker');
  env.window.fire('pageshow', { persisted: false });
  env.window.fire('focus');
  assert.equal(received.length, 0, 'initial pageshow and untouched focus must not resync');
});

test('session sync: a BFCache restore forces a resync without waiting for a message', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  env.window.fire('pageshow', { persisted: true });
  assert.deepEqual(received, ['changed']);
});

test('session sync: a BFCache restore applies an unseen stored marker kind', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  env.storage.setItem(
    'campuscore-session-revision',
    JSON.stringify(externalPayload({ kind: 'signed-out' })),
  );
  env.window.fire('pageshow', { persisted: true });
  assert.deepEqual(received, ['signed-out']);
});

test('session sync: a hidden-to-visible resume applies a marker missed while away', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  env.document.visibilityState = 'hidden';
  env.document.fire('visibilitychange');
  env.storage.setItem(
    'campuscore-session-revision',
    JSON.stringify(externalPayload({ kind: 'signed-out' })),
  );
  env.document.visibilityState = 'visible';
  env.document.fire('visibilitychange');
  assert.deepEqual(received, ['signed-out']);
});

test('session sync: a document hidden at subscribe resyncs on the first visible transition', () => {
  const env = createSyncEnvironment({
    storageMode: 'blocked',
    broadcast: false,
    visibility: 'hidden',
  });
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));
  assert.equal(received.length, 0, 'subscribing must never fire the listener');

  env.document.visibilityState = 'visible';
  env.document.fire('visibilitychange');
  assert.deepEqual(received, ['changed'], 'a document that was already hidden must treat becoming visible as a resume');
});

test('session sync: a window blurred at subscribe resyncs on the first focus event', () => {
  const env = createSyncEnvironment({
    storageMode: 'blocked',
    broadcast: false,
    focused: false,
  });
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));
  assert.equal(received.length, 0);

  env.document.focused = true;
  env.window.fire('focus');
  assert.deepEqual(received, ['changed'], 'a window that was already blurred must treat the next focus as a resume');
});

test('session sync: a focused visible document never resyncs on untouched resume events', () => {
  const env = createSyncEnvironment({ broadcast: false, focused: true });
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  env.window.fire('focus');
  env.document.fire('visibilitychange');
  env.window.fire('pageshow', { persisted: false });
  assert.equal(received.length, 0, 'no blur or hidden gap means no missed message');
});

test('session sync: a healthy-storage resume without a marker does nothing', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));

  env.window.fire('blur');
  env.window.fire('focus');
  assert.equal(received.length, 0, 'a readable empty marker must never force a reload');
});

test('session sync: cleanup removes every listener and closes the channel once', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  const unsubscribe = sync.subscribeSessionChanges((kind) => received.push(kind));
  const channel = env.channels[0];

  unsubscribe();
  unsubscribe();
  assert.equal(channel.closed, true);

  const ext = new env.BroadcastChannel('campuscore-session-events');
  ext.postMessage(externalPayload());
  env.window.fire('storage', {
    key: 'campuscore-session-revision',
    newValue: JSON.stringify(externalPayload()),
  });
  env.window.fire('pageshow', { persisted: true });
  assert.equal(received.length, 0, 'nothing may fire after cleanup');
  for (const set of env.listeners.window.values()) {
    assert.equal(set.size, 0, 'all window listeners must be removed');
  }
  for (const set of env.listeners.document.values()) {
    assert.equal(set.size, 0, 'all document listeners must be removed');
  }
});

test('session sync: a verified sign-in publishes the change marker exactly once', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  mounted.meCalls[0].resolve(USER_B);
  await loginPromise;
  await settle();
  assert.deepEqual(mounted.sessionSync.published, ['changed']);
});

test('session sync: publishes follow cookie-bearing responses — challenges and refreshes never publish', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });

  const challenged = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve({ challengeId: 'ch-1', expiresIn: 300 });
  await challenged;
  await settle();
  assert.deepEqual(mounted.sessionSync.published, [], 'a 2FA challenge is not a session change');

  // The verify response carries Set-Cookie: the shared jar changed the moment
  // it arrived, so siblings must resync even though this tab's probe then
  // fails and the tab cannot commit the session.
  const failed = mounted.context.verifyTwoFactor('ch-1', '000000');
  await settle();
  mounted.refreshWaiterCalls[1].resolve();
  await settle();
  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  mounted.meCalls[0].reject(new Error('probe down'));
  await settle();
  mounted.meCalls[1].reject(new Error('still down'));
  await assert.rejects(failed);
  await settle();
  assert.deepEqual(
    mounted.sessionSync.published,
    ['changed'],
    'a cookie-bearing response publishes even when this tab cannot verify it',
  );

  void mounted.context.refreshUser();
  await settle();
  assert.deepEqual(mounted.sessionSync.published, ['changed'], 'an ordinary refresh must not publish');

  const superseded = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[2].resolve();
  await settle();
  const logout = mounted.context.logout({ redirect: false });
  await settle();
  mounted.refreshWaiterCalls[3].resolve();
  await settle();
  mounted.logoutCalls[0].resolve({});
  await logout;
  // The superseded sign-in's response still carried Set-Cookie into the
  // shared jar — it publishes 'changed' so siblings converge to jar truth.
  mounted.loginCalls[1].resolve(loginResponse(USER_B));
  await assert.rejects(superseded);
  await settle();
  assert.deepEqual(
    mounted.sessionSync.published,
    ['changed', 'signed-out', 'changed'],
    'logout publishes signed-out; the superseded response still publishes because its cookie landed',
  );
});

test('session sync: a verified two-factor sign-in publishes the change marker', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const verifyPromise = mounted.context.verifyTwoFactor('challenge-1', '123456');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.twoFactorCalls[0].resolve({ user: USER_B });
  await settle();
  mounted.meCalls[0].resolve(USER_B);
  await verifyPromise;
  await settle();
  assert.deepEqual(mounted.sessionSync.published, ['changed']);
});

test('session sync: a current-generation logout publishes signed-out after cleanup even when the API rejects', async () => {
  const mounted = mountAuthProvider();
  mounted.meCalls[0].resolve(USER_A);
  await settle();
  assert.equal(mounted.render().user?.id, USER_A.id);
  const clearsBefore = mounted.queryClient.clearCount;

  const logoutPromise = mounted.context.logout({ redirect: false });
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.logoutCalls[0].reject(new Error('logout endpoint down'));
  await logoutPromise;
  await settle();

  assert.equal(mounted.render().user, null);
  assert.ok(mounted.queryClient.clearCount > clearsBefore, 'local cleanup must complete first');
  assert.deepEqual(mounted.sessionSync.published, ['signed-out']);
});

test('session sync: the receiver fences once, drops identity and blocks a late bootstrap commit', async () => {
  const reloads = [];
  const replaces = [];
  const hadWindow = 'window' in globalThis;
  globalThis.window = {
    location: {
      pathname: '/en/dashboard',
      reload: () => reloads.push('reload'),
      replace: (url) => replaces.push(url),
    },
  };
  try {
    const mounted = mountAuthProvider();
    assert.equal(mounted.meCalls.length, 1, 'the bootstrap probe is still pending');
    assert.equal(mounted.sessionSync.listeners.length, 1, 'the provider subscribes on mount');

    mounted.sessionSync.listeners[0]('changed');
    mounted.sessionSync.listeners[0]('changed');
    assert.equal(reloads.length, 1, 'the receiver must reload exactly once');
    assert.equal(replaces.length, 0);
    assert.equal(mounted.render().isLoading, true, 'the fence keeps the loading guard raised');
    assert.equal(mounted.render().user, null);

    mounted.meCalls[0].resolve(USER_A);
    await settle();
    assert.equal(mounted.render().user, null, 'a stale bootstrap response must not resurrect');
    assert.equal(mounted.render().isLoading, true, 'the late bootstrap must not lower the fence');

    void mounted.context.refreshUser();
    await settle();
    assert.equal(mounted.meCalls.length, 1, 'refreshUser must not run while a reload is pending');
  } finally {
    if (hadWindow === false) {
      delete globalThis.window;
    }
  }
});

test('session sync: a signed-out receiver navigates to the localized login once', () => {
  const reloads = [];
  const replaces = [];
  const hadWindow = 'window' in globalThis;
  globalThis.window = {
    location: {
      pathname: '/en/dashboard/grades',
      reload: () => reloads.push('reload'),
      replace: (url) => replaces.push(url),
    },
  };
  try {
    const mounted = mountAuthProvider();
    mounted.sessionSync.listeners[0]('signed-out');
    mounted.sessionSync.listeners[0]('signed-out');
    assert.equal(reloads.length, 0);
    assert.equal(replaces.length, 1);
    assert.match(replaces[0], /\/en\/login/);
  } finally {
    if (hadWindow === false) {
      delete globalThis.window;
    }
  }
});

test('session sync: strict-mode remount re-subscribes and cleans up each subscription', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  assert.equal(mounted.sessionSync.listeners.length, 1);
  mounted.unmount();
  assert.equal(mounted.sessionSync.unsubscribed, 1);
  mounted.remount();
  assert.equal(mounted.sessionSync.listeners.length, 2, 'a second mount owns a fresh subscription');
  mounted.unmount();
  assert.equal(mounted.sessionSync.unsubscribed, 2);
});

test('session sync: a prerendered document forces one resync on activation even with a seen marker', () => {
  const env = createSyncEnvironment({ visibility: 'prerender' });
  env.window.localStorage.setItem(
    'campuscore-session-revision',
    JSON.stringify(externalPayload({ kind: 'changed' })),
  );
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));
  assert.equal(received.length, 0, 'subscribing while prerendering must not fire');

  env.document.prerendering = false;
  env.document.visibilityState = 'visible';
  env.document.fire('prerenderingchange');
  assert.deepEqual(
    received,
    ['changed'],
    'activation forces a resync that the seeded marker dedupe would have suppressed',
  );

  env.document.fire('visibilitychange');
  assert.deepEqual(
    received,
    ['changed'],
    'the trailing visibilitychange must not double-deliver',
  );
});

test('session sync: a prerendered document resyncs once when only visibilitychange fires', () => {
  const env = createSyncEnvironment({ broadcast: false, visibility: 'prerender' });
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));
  assert.equal(received.length, 0);

  env.document.visibilityState = 'visible';
  env.document.fire('visibilitychange');
  assert.deepEqual(received, ['changed']);

  env.document.fire('prerenderingchange');
  assert.deepEqual(received, ['changed'], 'a late prerenderingchange must not redeliver');
});

test('session sync: prerenderingchange on a never-prerendered document does nothing', () => {
  const env = createSyncEnvironment();
  const sync = loadSessionSyncModule(env);
  const received = [];
  sync.subscribeSessionChanges((kind) => received.push(kind));
  env.document.fire('prerenderingchange');
  assert.equal(received.length, 0);
});

function mountApiBrowserGlobals() {
  const navigations = [];
  const hadWindow = 'window' in globalThis;
  const hadDocument = 'document' in globalThis;
  const documentObj = { cookie: 'cc_csrf=hint-a', visibilityState: 'visible' };
  globalThis.document = documentObj;
  globalThis.window = {
    location: {
      pathname: '/en/dashboard',
      origin: 'http://localhost',
      set href(value) {
        navigations.push(value);
      },
      get href() {
        return navigations[navigations.length - 1] ?? '';
      },
    },
  };
  return {
    navigations,
    documentObj,
    restore() {
      if (!hadWindow) delete globalThis.window;
      if (!hadDocument) delete globalThis.document;
    },
  };
}

test('api 401: a protected 401 confirms me() first and stays put while the cookie still works', async () => {
  const env = mountApiBrowserGlobals();
  try {
    const { interceptors, getCalls, sessionSyncPublished } = loadApiModule();
    const rejection = interceptors
      .responseError({
        response: { status: 401 },
        config: { url: '/sections', method: 'get', skipAuthRefresh: true },
      })
      .catch((error) => error);
    await settle();
    const meCalls = getCalls.filter((call) => call.args[0] === '/auth/me');
    assert.equal(meCalls.length, 1, 'the redirect boundary must re-probe the cookie once');
    meCalls[0].resolve({ data: USER_A });
    await rejection;
    await settle();
    assert.deepEqual(sessionSyncPublished, [], 'a live cookie means no signed-out broadcast');
    assert.equal(env.navigations.length, 0, 'a live cookie means no navigation');
  } finally {
    env.restore();
  }
});

test('api 401: a dead shared cookie publishes signed-out and navigates once under concurrent 401s', async () => {
  const env = mountApiBrowserGlobals();
  try {
    const { interceptors, getCalls, sessionSyncPublished } = loadApiModule();
    const first = interceptors
      .responseError({
        response: { status: 401 },
        config: { url: '/sections', method: 'get', skipAuthRefresh: true },
      })
      .catch((error) => error);
    const second = interceptors
      .responseError({
        response: { status: 401 },
        config: { url: '/grades', method: 'get', skipAuthRefresh: true },
      })
      .catch((error) => error);
    await settle();
    const meCalls = getCalls.filter((call) => call.args[0] === '/auth/me');
    assert.equal(meCalls.length, 1, 'concurrent 401s share a single confirmation probe');
    meCalls[0].reject({ response: { status: 401 } });
    await first;
    await second;
    await settle();
    assert.deepEqual(sessionSyncPublished, ['signed-out']);
    assert.equal(env.navigations.length, 1, 'concurrent 401s share one redirect');
    assert.match(env.navigations[0], /\/login/);
    assert.match(env.navigations[0], /reason=unauthorized/);
  } finally {
    env.restore();
  }
});

test('api 401: a dead shared cookie during an in-flight logout never hijacks the explicit redirect', async () => {
  const env = mountApiBrowserGlobals();
  try {
    const { interceptors, getCalls, sessionSyncPublished, setLogoutInProgress } =
      loadApiModule();
    setLogoutInProgress(true);
    const rejection = interceptors
      .responseError({
        response: { status: 401 },
        config: { url: '/sections', method: 'get', skipAuthRefresh: true },
      })
      .catch((error) => error);
    await settle();
    const settled = await rejection;
    assert.equal(settled.response.status, 401);
    await settle();
    assert.equal(
      getCalls.filter((call) => call.args[0] === '/auth/me').length,
      0,
      'an explicit logout owns the teardown — no confirmation probe may run',
    );
    assert.deepEqual(
      sessionSyncPublished,
      [],
      'logout publishes its own signed-out event; the interceptor must stay silent',
    );
    assert.equal(
      env.navigations.length,
      0,
      'the generic session-expired/unauthorized redirect must not overwrite the signed-out navigation',
    );
  } finally {
    env.restore();
  }
});

test('api 401: a network-failed refresh never confirms the session dead', async () => {
  const env = mountApiBrowserGlobals();
  try {
    const { interceptors, getCalls, postCalls, sessionSyncPublished } = loadApiModule();
    const rejection = interceptors
      .responseError({
        response: { status: 401 },
        config: { url: '/sections', method: 'get' },
      })
      .catch((error) => error);
    // The real navigator.locks grant resolves on its own schedule — wait
    // until the refresh attempt is actually dispatched instead of assuming a
    // single microtask tick is enough (CI runners are slower than laptops).
    let refreshCalls = [];
    for (let attempt = 0; attempt < 50 && refreshCalls.length === 0; attempt += 1) {
      await settle();
      refreshCalls = postCalls.filter((call) => call.args[0] === '/auth/refresh');
    }
    assert.equal(refreshCalls.length, 1, 'a plain 401 attempts the refresh first');
    refreshCalls[0].reject(new Error('socket hangup'));
    const settled = await rejection;
    assert.equal(settled.message, 'socket hangup', 'the producer sees the refresh failure');
    await settle();
    assert.equal(
      getCalls.filter((call) => call.args[0] === '/auth/me').length,
      0,
      'a non-401 refresh failure must not confirm the session dead',
    );
    assert.deepEqual(sessionSyncPublished, []);
    assert.equal(env.navigations.length, 0);
  } finally {
    env.restore();
  }
});

test('api 401: an opted-out probe failure never recurses into confirmation', async () => {
  const env = mountApiBrowserGlobals();
  try {
    const { interceptors, getCalls, sessionSyncPublished } = loadApiModule();
    const settled = await interceptors
      .responseError({
        response: { status: 401 },
        config: {
          url: '/auth/me',
          method: 'get',
          skipAuthRefresh: true,
          skipAuthRedirect: true,
        },
      })
      .catch((error) => error);
    assert.equal(settled.response.status, 401);
    await settle();
    assert.equal(
      getCalls.filter((call) => call.args[0] === '/auth/me').length,
      0,
      'the confirmation probe itself is opted out and must not self-confirm',
    );
    assert.deepEqual(sessionSyncPublished, []);
    assert.equal(env.navigations.length, 0);
  } finally {
    env.restore();
  }
});

test('api 401: a late 401 from a superseded session stays silent when me() answers the new cookie', async () => {
  const env = mountApiBrowserGlobals();
  try {
    const { interceptors, getCalls, sessionSyncPublished } = loadApiModule();
    const rejection = interceptors
      .responseError({
        response: { status: 401 },
        config: { url: '/notifications', method: 'get', skipAuthRefresh: true },
      })
      .catch((error) => error);
    await settle();
    const meCalls = getCalls.filter((call) => call.args[0] === '/auth/me');
    assert.equal(meCalls.length, 1);
    meCalls[0].resolve({ data: USER_B });
    await rejection;
    await settle();
    assert.deepEqual(sessionSyncPublished, [], 'a live newer cookie must not sign out');
    assert.equal(env.navigations.length, 0);
  } finally {
    env.restore();
  }
});

function mountPollGlobals() {
  const ticks = [];
  const cleared = [];
  const reloads = [];
  const replaces = [];
  const hadWindow = 'window' in globalThis;
  const hadDocument = 'document' in globalThis;
  const documentObj = { visibilityState: 'visible', cookie: 'cc_csrf=hint-a' };
  globalThis.document = documentObj;
  globalThis.window = {
    location: {
      pathname: '/en/dashboard',
      reload: () => reloads.push('reload'),
      replace: (url) => replaces.push(url),
    },
    setInterval: (fn, ms) => {
      ticks.push({ fn, ms });
      return ticks.length;
    },
    clearInterval: (id) => cleared.push(id),
  };
  return {
    ticks,
    cleared,
    reloads,
    replaces,
    documentObj,
    restore() {
      if (!hadWindow) delete globalThis.window;
      if (!hadDocument) delete globalThis.document;
    },
  };
}

test('session poll: a changed identity signature reloads once on the next tick without republishing', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    assert.equal(env.ticks.length, 1);
    assert.equal(env.ticks[0].ms, 30000);
    mounted.meCalls[0].resolve(USER_A);
    await settle();
    assert.equal(mounted.render().user?.id, USER_A.id);

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 2, 'the tick probes me() exactly once');
    mounted.meCalls[1].resolve(USER_B);
    await settle();
    assert.equal(env.reloads.length, 1, 'a different cookie identity converges to a reload');
    assert.equal(mounted.render().user, null);
    assert.deepEqual(mounted.sessionSync.published, [], 'a polling receiver must not republish');
  } finally {
    env.restore();
  }
});

test('session poll: a stable signature survives consecutive ticks with no reload', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    mounted.meCalls[0].resolve(USER_A);
    await settle();

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 2);
    mounted.meCalls[1].resolve(USER_A);
    await settle();

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 3, 'each tick is bounded to one probe');
    mounted.meCalls[2].resolve(USER_A);
    await settle();

    assert.equal(env.reloads.length, 0);
    assert.equal(env.replaces.length, 0);
    assert.deepEqual(mounted.sessionSync.published, []);
    assert.equal(mounted.render().user?.id, USER_A.id);
    assert.equal(mounted.render().isLoading, false, 'an unchanged poll must not raise loading');
  } finally {
    env.restore();
  }
});

test('session poll: hidden, mutation, and in-flight states never probe', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    mounted.meCalls[0].resolve(USER_A);
    await settle();
    assert.equal(mounted.render().user?.id, USER_A.id);
    const baseline = mounted.meCalls.length;

    env.documentObj.visibilityState = 'hidden';
    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, baseline, 'a hidden tab never polls');

    env.documentObj.visibilityState = 'visible';
    const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
    await settle();
    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, baseline, 'a pending mutation blocks the poll');
    mounted.refreshWaiterCalls[0].resolve();
    await settle();
    mounted.loginCalls[0].resolve(loginResponse(USER_B));
    await settle();
    mounted.meCalls[baseline].resolve(USER_B);
    await loginPromise;
    await settle();
    assert.equal(mounted.render().user?.id, USER_B.id);

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, baseline + 2, 'a visible idle tab polls once after the sign-in probe');
    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, baseline + 2, 'an in-flight poll blocks the next tick');
    mounted.meCalls[baseline + 1].resolve(USER_B);
    await settle();

    env.documentObj.visibilityState = 'hidden';
    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, baseline + 2, 'hidden blocks again after a settled poll');
  } finally {
    env.restore();
  }
});

test('session poll: a logged-out document never probes', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider({ sessionHint: false });
    await settle();
    assert.equal(mounted.render().user, null);
    assert.equal(mounted.meCalls.length, 0, 'no hint means bootstrap never probed');
    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 0, 'a logged-out document never polls');
  } finally {
    env.restore();
  }
});

test('session poll: a confirmed 401 publishes signed-out once and navigates to localized login', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    mounted.meCalls[0].resolve(USER_A);
    await settle();

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 2);
    mounted.meCalls[1].reject({ response: { status: 401 } });
    await settle();
    assert.deepEqual(mounted.sessionSync.published, ['signed-out']);
    assert.equal(env.replaces.length, 1);
    assert.match(env.replaces[0], /\/en\/login/);
    assert.equal(env.reloads.length, 0);
    assert.equal(mounted.render().isLoading, true, 'the fence stays raised behind the redirect');
  } finally {
    env.restore();
  }
});

test('session poll: a 5xx keeps state and the next tick probes again', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    mounted.meCalls[0].resolve(USER_A);
    await settle();

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 2);
    mounted.meCalls[1].reject({ response: { status: 503 } });
    await settle();
    assert.equal(env.reloads.length, 0);
    assert.equal(env.replaces.length, 0);
    assert.deepEqual(mounted.sessionSync.published, []);
    assert.equal(mounted.render().user?.id, USER_A.id, 'transient failures preserve the session');

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 3, 'the next interval retries after a transient failure');
    mounted.meCalls[2].resolve(USER_A);
    await settle();
  } finally {
    env.restore();
  }
});

test('session poll: a stale tick response after a new sign-in never resets the identity', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    mounted.meCalls[0].resolve(USER_A);
    await settle();
    assert.equal(mounted.render().user?.id, USER_A.id);

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 2, 'the tick probe is in flight');

    const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
    await settle();
    mounted.refreshWaiterCalls[0].resolve();
    await settle();
    mounted.loginCalls[0].resolve(loginResponse(USER_B));
    await settle();
    mounted.meCalls[2].resolve(USER_B);
    await loginPromise;
    await settle();
    assert.equal(mounted.render().user?.id, USER_B.id);

    mounted.meCalls[1].resolve(USER_A);
    await settle();
    assert.equal(env.reloads.length, 0, 'a stale poll response must not reload');
    assert.equal(env.replaces.length, 0);
    assert.equal(mounted.render().user?.id, USER_B.id, 'the newer sign-in survives');
  } finally {
    env.restore();
  }
});

test('session poll: unmount clears the interval and strict remount owns one timer per mount', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    assert.equal(env.ticks.length, 1);
    mounted.remount();
    assert.equal(env.ticks.length, 2, 'a strict remount owns a fresh timer');
    mounted.unmount();
    assert.equal(env.cleared.length, 2, 'every mounted timer is cleared once');
    assert.deepEqual(new Set(env.cleared).size, 2);
  } finally {
    env.restore();
  }
});

test('api 401: a rotated CSRF hint during the held confirmation suppresses the sign-out', async () => {
  const env = mountApiBrowserGlobals();
  try {
    const { interceptors, getCalls, sessionSyncPublished } = loadApiModule();
    const rejection = interceptors
      .responseError({
        response: { status: 401 },
        config: { url: '/sections', method: 'get', skipAuthRefresh: true },
      })
      .catch((error) => error);
    await settle();
    const meCalls = getCalls.filter((call) => call.args[0] === '/auth/me');
    assert.equal(meCalls.length, 1, 'the redirect boundary still probes first');
    env.documentObj.cookie = 'cc_csrf=hint-b';
    meCalls[0].reject({ response: { status: 401 } });
    await rejection;
    await settle();
    assert.deepEqual(
      sessionSyncPublished,
      [],
      'a session write during the probe means the 401 belongs to a superseded cookie',
    );
    assert.equal(env.navigations.length, 0);
  } finally {
    env.restore();
  }
});

test('session poll: a 401 arriving after the CSRF hint rotated never signs the tab out', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    mounted.meCalls[0].resolve(USER_A);
    await settle();
    assert.equal(mounted.render().user?.id, USER_A.id);

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 2, 'the tick probe is in flight');

    env.documentObj.cookie = 'cc_csrf=hint-b';
    mounted.meCalls[1].reject({ response: { status: 401 } });
    await settle();
    assert.deepEqual(
      mounted.sessionSync.published,
      [],
      'a session write during the poll means the 401 is stale, not a revocation',
    );
    assert.equal(env.replaces.length, 0);
    assert.equal(env.reloads.length, 0);
    assert.equal(mounted.render().user?.id, USER_A.id, 'the newer session identity survives');
  } finally {
    env.restore();
  }
});

test('session poll: unmount during a pending tick cannot navigate or publish', async () => {
  const env = mountPollGlobals();
  try {
    const mounted = mountAuthProvider();
    mounted.meCalls[0].resolve(USER_A);
    await settle();

    env.ticks[0].fn();
    await settle();
    assert.equal(mounted.meCalls.length, 2, 'the tick probe is in flight');

    mounted.unmount();
    assert.ok(env.cleared.length >= 1, 'unmount clears the interval');
    mounted.meCalls[1].resolve(USER_B);
    await settle();
    assert.equal(env.reloads.length, 0, 'a detached poll result must not navigate');
    assert.equal(env.replaces.length, 0);
    assert.deepEqual(mounted.sessionSync.published, []);
  } finally {
    env.restore();
  }
});

test('session sync: a retried sign-in response publishes its cookie arrival even when the retry probe fails', async () => {
  const mounted = mountAuthProvider({ sessionHint: false });
  const loginPromise = mounted.context.login(USER_B.email, 'pw-irrelevant');
  await settle();
  mounted.refreshWaiterCalls[0].resolve();
  await settle();
  mounted.loginCalls[0].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 1, 'the first response is followed by a verification probe');
  mounted.meCalls[0].reject(new Error('probe dropped'));
  await settle();
  assert.equal(mounted.loginCalls.length, 2, 'a failed probe retries the sign-in once');
  mounted.loginCalls[1].resolve(loginResponse(USER_B));
  await settle();
  assert.equal(mounted.meCalls.length, 2, 'the retry probes the session again');
  mounted.meCalls[1].reject(new Error('second probe dropped'));
  await assert.rejects(loginPromise);
  await settle();
  assert.deepEqual(
    mounted.sessionSync.published,
    ['changed', 'changed'],
    'every cookie-bearing login response publishes, including the retry',
  );
});
