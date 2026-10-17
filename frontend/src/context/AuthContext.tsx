'use client';

import React, {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
} from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { useI18n } from '@/i18n';
import { User, TwoFactorChallengeResponse } from '@/types/api';
import { authApi, refreshSessionSingleFlight, waitForPendingSessionRefresh, SessionVerificationError } from '@/lib/api';
import { isTwoFactorChallenge } from '@/lib/two-factor';
import { clearLogoutInProgress, hasCsrfSessionHint, markLogoutInProgress, readCsrfSessionHint } from '@/lib/session-hint';
import { loginHref, portalFromPathname, portalFromUser } from '@/lib/login-portal';
import { getBrowserQueryClient } from '@/lib/query';
import { publishSessionChange, subscribeSessionChanges, type SessionChangeKind } from '@/lib/session-sync';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isLoggingOut: boolean;
  isStudent: boolean;
  isLecturer: boolean;
  isAdmin: boolean;
  isSuperAdmin: boolean;
  isFacultyHead: boolean;
  isDepartmentHead: boolean;
  login: (
    email: string,
    password: string,
  ) => Promise<User | TwoFactorChallengeResponse>;
  verifyTwoFactor: (challengeId: string, code: string) => Promise<User>;
  logout: (options?: { redirect?: boolean | string }) => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();
  const { href } = useI18n();
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  // The shared browser QueryClient outlives a sign-in because the provider
  // tree keeps it mounted; several query keys carry no user id, so the cache
  // is dropped whenever the authenticated identity changes or a session dies
  // — otherwise the next user could read the previous user's cached data.
  const lastKnownUserIdRef = useRef<string | null>(null);
  const sessionGenerationRef = useRef(0);
  const latestRefreshRequestRef = useRef(0);
  // The 30s identity poll gets its own counter. Sharing
  // latestRefreshRequestRef meant a poll firing mid-refresh discarded the
  // explicit refreshUser() result (isCurrent failed) even though the poll
  // itself never writes identity — the refreshed profile silently stayed
  // stale until the next call.
  const latestPollRequestRef = useRef(0);
  const refreshInFlightRef = useRef(false);
  const sessionMutationPendingRef = useRef(false);
  const logoutPendingRef = useRef(false);
  const logoutPathnameRef = useRef<string | null>(null);
  const logoutTargetPathRef = useRef<string | null>(null);
  const sessionReloadPendingRef = useRef(false);
  const bootstrapPendingRef = useRef(true);
  const latestIdentityRef = useRef<User | null>(null);

  const setUserIdentity = useCallback((nextUser: User | null) => {
    const nextId = nextUser?.id ?? null;
    if (nextId && lastKnownUserIdRef.current && lastKnownUserIdRef.current !== nextId) {
      getBrowserQueryClient().clear();
    }
    lastKnownUserIdRef.current = nextId;
    latestIdentityRef.current = nextUser;
    setUser(nextUser);
  }, []);

  const clearSessionState = useCallback(() => {
    getBrowserQueryClient().clear();
    setUserIdentity(null);
  }, [setUserIdentity]);

  const refreshUser = useCallback(async () => {
    if (
      sessionMutationPendingRef.current ||
      logoutPendingRef.current ||
      sessionReloadPendingRef.current
    ) {
      return;
    }
    const generation = sessionGenerationRef.current;
    const request = ++latestRefreshRequestRef.current;
    refreshInFlightRef.current = true;
    const isCurrent = () =>
      generation === sessionGenerationRef.current &&
      request === latestRefreshRequestRef.current &&
      !sessionReloadPendingRef.current;

    if (!hasCsrfSessionHint()) {
      refreshInFlightRef.current = false;
      if (isCurrent()) {
        clearSessionState();
      }
      return;
    }

    try {
      const nextUser = await authApi.me();
      if (!isCurrent()) {
        return;
      }
      setUserIdentity(nextUser);
    } catch {
      if (!isCurrent()) {
        return;
      }
      try {
        await refreshSessionSingleFlight();
        if (!isCurrent()) {
          return;
        }
        const nextUser = await authApi.me();
        if (!isCurrent()) {
          return;
        }
        setUserIdentity(nextUser);
      } catch {
        if (isCurrent()) {
          // The refresh rejected: the session is gone, so the cached
          // per-user query data must go with it.
          clearSessionState();
        }
      }
    } finally {
      refreshInFlightRef.current = false;
    }
  }, [clearSessionState, setUserIdentity]);

  useEffect(() => {
    let active = true;
    const initAuth = async () => {
      try {
        await refreshUser();
      } finally {
        if (active && !sessionReloadPendingRef.current) {
          bootstrapPendingRef.current = false;
          setIsLoading(false);
        }
      }
    };

    void initAuth();
    return () => {
      active = false;
      sessionGenerationRef.current += 1;
    };
  }, [refreshUser]);

  const login = useCallback(async (email: string, password: string) => {
    const generation = ++sessionGenerationRef.current;
    sessionMutationPendingRef.current = true;
    logoutPendingRef.current = false;
    clearLogoutInProgress();
    const isCurrent = () =>
      generation === sessionGenerationRef.current &&
      !sessionReloadPendingRef.current;
    try {
      await waitForPendingSessionRefresh();
      if (!isCurrent()) {
        throw new Error('sign-in superseded');
      }
      const response = await authApi.login(email, password);
      // Publish as soon as a session-cookie-bearing response arrives, BEFORE
      // the probe commits. The Set-Cookie already landed in the shared jar,
      // so siblings must resync even when this tab later fails verification
      // or was superseded — publishing only on a verified commit would leave
      // them on the old identity while the jar holds the new session.
      if (!isTwoFactorChallenge(response)) {
        publishSessionChange('changed');
      }
      if (!isCurrent()) {
        throw new Error('sign-in superseded');
      }
      // Two-factor accounts stop after the password step: no session is opened
      // here, the caller must collect a one-time code and call verifyTwoFactor.
      if (isTwoFactorChallenge(response)) {
        return response;
      }
      // Session-cookie verification with one silent retry: the same-origin
      // proxy intermittently drops the login response's Set-Cookie, which
      // surfaced as an instant "Lần đăng nhập của bạn đã kết thúc" banner right
      // after a successful sign-in (production repro, PR #26 thread). me() runs
      // with skipAuthRedirect, so a failed probe can never bounce the user
      // through redirectToLogin mid-login.
      let sessionVerified = false;
      let sessionMismatch = false;
      for (let attempt = 0; attempt < 2 && !sessionVerified; attempt += 1) {
        if (!isCurrent()) {
          throw new Error('sign-in superseded');
        }
        if (attempt > 0) {
          try {
            const retryResponse = await authApi.login(email, password);
            if (!isTwoFactorChallenge(retryResponse)) {
              publishSessionChange('changed');
            }
          } catch {
            break;
          }
        }
        if (!isCurrent()) {
          throw new Error('sign-in superseded');
        }
        try {
          // A probe that merely succeeds is not enough: a stale cookie from a
          // previous account would answer me() too, and committing
          // response.user would render that account's portal under this
          // identity. Require the probed session to be the same user.
          const probed = await authApi.me();
          sessionVerified = probed.id === response.user.id;
          sessionMismatch = !sessionVerified;
        } catch {
          sessionVerified = false;
          sessionMismatch = false;
        }
      }
      if (!isCurrent()) {
        throw new Error('sign-in superseded');
      }
      if (sessionMismatch) {
        clearSessionState();
        throw new SessionVerificationError('sign-in session mismatch');
      }
      if (!sessionVerified) {
        clearSessionState();
        throw new SessionVerificationError('sign-in session unverified');
      }
      logoutPathnameRef.current = null;
      setIsLoggingOut(false);
      getBrowserQueryClient().clear();
      setUserIdentity(response.user);
      return response.user;
    } finally {
      if (isCurrent()) {
        sessionMutationPendingRef.current = false;
      }
    }
  }, [clearSessionState, setUserIdentity]);

  const verifyTwoFactor = useCallback(async (challengeId: string, code: string) => {
    const generation = ++sessionGenerationRef.current;
    sessionMutationPendingRef.current = true;
    logoutPendingRef.current = false;
    clearLogoutInProgress();
    const isCurrent = () =>
      generation === sessionGenerationRef.current &&
      !sessionReloadPendingRef.current;
    try {
      await waitForPendingSessionRefresh();
      if (!isCurrent()) {
        throw new Error('verification superseded');
      }
      const response = await authApi.verifyTwoFactor(challengeId, code);
      // Same publish-on-arrival rule as login: the Set-Cookie landed with
      // this response, so siblings must resync even if this tab's probe
      // later fails or the call was superseded.
      publishSessionChange('changed');
      if (!isCurrent()) {
        throw new Error('verification superseded');
      }
      let sessionVerified = false;
      let sessionMismatch = false;
      for (let attempt = 0; attempt < 2 && !sessionVerified; attempt += 1) {
        if (!isCurrent()) {
          throw new Error('verification superseded');
        }
        try {
          const probed = await authApi.me();
          sessionVerified = probed.id === response.user.id;
          sessionMismatch = !sessionVerified;
        } catch {
          sessionVerified = false;
          sessionMismatch = false;
        }
      }
      if (!isCurrent()) {
        throw new Error('verification superseded');
      }
      if (sessionMismatch) {
        clearSessionState();
        throw new SessionVerificationError('verification session mismatch');
      }
      if (!sessionVerified) {
        clearSessionState();
        throw new SessionVerificationError('verification session unverified');
      }
      logoutPathnameRef.current = null;
      setIsLoggingOut(false);
      getBrowserQueryClient().clear();
      setUserIdentity(response.user);
      return response.user;
    } finally {
      if (isCurrent()) {
        sessionMutationPendingRef.current = false;
      }
    }
  }, [clearSessionState, setUserIdentity]);

  const logout = useCallback(async (options?: { redirect?: boolean | string }) => {
    const generation = ++sessionGenerationRef.current;
    logoutPendingRef.current = true;
    markLogoutInProgress();
    sessionMutationPendingRef.current = false;
    setIsLoggingOut(true);
    const recordedPath = pathname;
    logoutPathnameRef.current = recordedPath;
    try {
      await waitForPendingSessionRefresh();
      if (generation === sessionGenerationRef.current) {
        await authApi.logout();
      }
    } catch {
      // Ignore logout API failures and still clear the client session.
    } finally {
      if (generation === sessionGenerationRef.current) {
        getBrowserQueryClient().clear();
        setUserIdentity(null);
        publishSessionChange('signed-out');
        if (options?.redirect === false) {
          logoutPathnameRef.current = null;
          logoutTargetPathRef.current = null;
          setIsLoggingOut(false);
          clearLogoutInProgress();
          return;
        }

        // A string redirect keeps the navigation inside this finally, atomic
        // with the user-clear commit — a caller-side router.replace after
        // logout resolves lets guarded shells see !user && !isLoggingOut and
        // overwrite the intended target with their own session-death redirect
        // (profile rotation landed on reason=unauthorized instead of
        // password-rotated).
        const target = typeof options?.redirect === 'string'
          ? options.redirect
          : loginHref(href, portalFromUser(user), 'signed-out');
        logoutTargetPathRef.current = target.split('?')[0];
        router.replace(target);
        // Same-path navigation never fires the pathname effect below — clear
        // inline or isLoggingOut stays stuck when logout starts on /login.
        if (target.split('?')[0] === pathname) {
          logoutPathnameRef.current = null;
          logoutTargetPathRef.current = null;
          setIsLoggingOut(false);
          clearLogoutInProgress();
        }
      } else if (logoutPathnameRef.current === recordedPath) {
        // A newer login/verify superseded this logout. If it has not claimed
        // the flag (its success path clears it, but a failed call never gets
        // there), this call still owns isLoggingOut and must release it or
        // every protected page spins forever. The shared logout marker is
        // deliberately not cleared here: the superseding call manages it —
        // clearing would unguard the interceptor while that logout is still
        // in flight.
        logoutPathnameRef.current = null;
        logoutTargetPathRef.current = null;
        setIsLoggingOut(false);
      }
    }
  }, [href, pathname, router, setUserIdentity, user]);

  const handleExternalSessionChange = useCallback(
    (kind: SessionChangeKind) => {
      if (sessionReloadPendingRef.current) {
        return;
      }
      sessionReloadPendingRef.current = true;
      sessionGenerationRef.current += 1;
      sessionMutationPendingRef.current = false;
      logoutPendingRef.current = false;
      logoutPathnameRef.current = null;
      logoutTargetPathRef.current = null;
      setIsLoading(true);
      setIsLoggingOut(false);
      clearLogoutInProgress();
      clearSessionState();
      if (kind === 'signed-out') {
        window.location.replace(
          loginHref(href, portalFromPathname(window.location.pathname), 'signed-out'),
        );
      } else {
        window.location.reload();
      }
    },
    [clearSessionState, href],
  );

  useEffect(() => {
    const unsubscribe = subscribeSessionChanges(handleExternalSessionChange);
    return unsubscribe;
  }, [handleExternalSessionChange]);

  useEffect(() => {
    if (typeof window === 'undefined' || typeof document === 'undefined') {
      return;
    }
    let active = true;
    let pollInFlight = false;
    const pollSessionIdentity = async () => {
      if (pollInFlight) {
        return;
      }
      if (document.visibilityState !== 'visible') {
        return;
      }
      if (!latestIdentityRef.current) {
        return;
      }
      if (
        bootstrapPendingRef.current ||
        sessionMutationPendingRef.current ||
        logoutPendingRef.current ||
        sessionReloadPendingRef.current
      ) {
        return;
      }
      pollInFlight = true;
      const generation = sessionGenerationRef.current;
      const request = ++latestPollRequestRef.current;
      const isCurrent = () =>
        active &&
        generation === sessionGenerationRef.current &&
        request === latestPollRequestRef.current &&
        !refreshInFlightRef.current &&
        !sessionReloadPendingRef.current;
      const csrfHintBefore = readCsrfSessionHint();
      try {
        const probed = await authApi.me();
        if (!isCurrent()) {
          return;
        }
        const previous = latestIdentityRef.current;
        if (!previous) {
          return;
        }
        if (identitySignature(probed) !== identitySignature(previous)) {
          handleExternalSessionChange('changed');
        }
      } catch (error) {
        if (!isCurrent()) {
          return;
        }
        const status = (error as { response?: { status?: number } } | undefined)
          ?.response?.status;
        if (status === 401 && readCsrfSessionHint() === csrfHintBefore) {
          publishSessionChange('signed-out');
          handleExternalSessionChange('signed-out');
        }
      } finally {
        pollInFlight = false;
      }
    };
    const timer = window.setInterval(() => {
      void pollSessionIdentity();
    }, 30000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [handleExternalSessionChange]);

  useEffect(() => {
    if (
      isLoggingOut &&
      logoutPathnameRef.current !== null &&
      pathname !== logoutPathnameRef.current &&
      pathname === logoutTargetPathRef.current
    ) {
      // Only the logout's own destination releases the flag. An unrelated
      // navigation landing mid-logout (e.g. a role-bounce layout effect)
      // must not unguard the interceptor or re-arm page guards.
      logoutPathnameRef.current = null;
      logoutTargetPathRef.current = null;
      setIsLoggingOut(false);
      clearLogoutInProgress();
    }
  }, [isLoggingOut, pathname]);

  const isStudent = user?.roles?.includes('STUDENT') ?? false;
  const isLecturer = user?.roles?.includes('LECTURER') ?? false;
  const isAdmin = user?.roles?.includes('ADMIN') ?? false;
  const isSuperAdmin = user?.roles?.includes('SUPER_ADMIN') ?? false;
  const isFacultyHead = user?.roles?.includes('TRUONG_KHOA') ?? false;
  const isDepartmentHead = user?.roles?.includes('TRUONG_BO_MON') ?? false;

  return (
    <AuthContext.Provider
      value={{
        user,
        isLoading,
        isLoggingOut,
        isStudent,
        isLecturer,
        isAdmin,
        isSuperAdmin,
        isFacultyHead,
        isDepartmentHead,
        login,
        verifyTwoFactor,
        logout,
        refreshUser,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}

function identitySignature(user: User): string {
  return JSON.stringify([
    user.id,
    [...(user.roles ?? [])].sort(),
    user.status ?? '',
    user.mustChangePassword ?? false,
    user.studentId ?? null,
    user.lecturerId ?? null,
  ]);
}

type AppRole = 'STUDENT' | 'LECTURER' | 'ADMIN' | 'SUPER_ADMIN' | 'TRUONG_KHOA' | 'TRUONG_BO_MON';

function userHasRequiredRole(
  user: User | null,
  requiredRoles: AppRole[],
) {
  if (!user) {
    return false;
  }

  if (requiredRoles.length === 0) {
    return true;
  }

  return requiredRoles.some((role) => {
    if (role === 'ADMIN') {
      return user.roles?.includes('ADMIN') || user.roles?.includes('SUPER_ADMIN');
    }
    return user.roles?.includes(role);
  });
}

export function useRequireAuth(
  requiredRoles?: AppRole[],
) {
  const { user, isLoading, isLoggingOut } = useAuth();
  const router = useRouter();
  const { href } = useI18n();
  const rolesKey = requiredRoles?.join(',') ?? '';
  const requiredRolesList = useMemo(
    () =>
      rolesKey
        ? (rolesKey.split(',') as AppRole[])
        : [],
    [rolesKey],
  );
  const resolving = isLoading || isLoggingOut;
  const hasRole = userHasRequiredRole(user, requiredRolesList);
  const hasAccess = !resolving && Boolean(user) && hasRole;
  const isForbidden = !resolving && Boolean(user) && !hasRole;

  useEffect(() => {
    if (resolving) {
      return;
    }

    if (!user) {
      router.push(loginHref(href, portalFromPathname(typeof window === 'undefined' ? '' : window.location.pathname), 'session-expired'));
      return;
    }

    if (!hasRole) {
      if (user.roles?.includes('ADMIN') || user.roles?.includes('SUPER_ADMIN')) {
        router.push(href('/admin'));
      } else if (user.roles?.includes('LECTURER')) {
        router.push(href('/dashboard/lecturer'));
      } else {
        router.push(href('/dashboard'));
      }
    }
  }, [hasRole, href, resolving, router, user]);

  return { user, isLoading: resolving, hasAccess, isForbidden };
}
