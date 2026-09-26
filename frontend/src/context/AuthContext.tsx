'use client';

import React, {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
} from 'react';
import { useRouter } from 'next/navigation';
import { useI18n } from '@/i18n';
import { User, TwoFactorChallengeResponse } from '@/types/api';
import { authApi, refreshSessionSingleFlight } from '@/lib/api';
import { isTwoFactorChallenge } from '@/lib/two-factor';
import { hasCsrfSessionHint } from '@/lib/session-hint';
import { loginHref, portalFromPathname, portalFromUser } from '@/lib/login-portal';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isLoggingOut: boolean;
  isStudent: boolean;
  isLecturer: boolean;
  isAdmin: boolean;
  isSuperAdmin: boolean;
  isFacultyHead: boolean;
  login: (
    email: string,
    password: string,
  ) => Promise<User | TwoFactorChallengeResponse>;
  verifyTwoFactor: (challengeId: string, code: string) => Promise<User>;
  logout: (options?: { redirect?: boolean }) => Promise<void>;
  refreshUser: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const router = useRouter();
  const { href } = useI18n();
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  const refreshUser = useCallback(async () => {
    if (!hasCsrfSessionHint()) {
      setUser(null);
      return;
    }

    try {
      setUser(await authApi.me());
    } catch {
      try {
        await refreshSessionSingleFlight();
        setUser(await authApi.me());
      } catch {
        setUser(null);
      }
    }
  }, []);

  useEffect(() => {
    const initAuth = async () => {
      try {
        await refreshUser();
      } finally {
        setIsLoading(false);
      }
    };

    void initAuth();
  }, [refreshUser]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await authApi.login(email, password);
    // Two-factor accounts stop after the password step: no session is opened
    // here, the caller must collect a one-time code and call verifyTwoFactor.
    if (isTwoFactorChallenge(response)) {
      return response;
    }
    setIsLoggingOut(false);
    setUser(response.user);
    return response.user;
  }, []);

  const verifyTwoFactor = useCallback(async (challengeId: string, code: string) => {
    const response = await authApi.verifyTwoFactor(challengeId, code);
    setIsLoggingOut(false);
    setUser(response.user);
    return response.user;
  }, []);

  const logout = useCallback(async (options?: { redirect?: boolean }) => {
    setIsLoggingOut(true);
    try {
      await authApi.logout();
    } catch {
      // Ignore logout API failures and still clear the client session.
    } finally {
      setUser(null);
      if (options?.redirect === false) {
        setIsLoggingOut(false);
        return;
      }

      router.replace(loginHref(href, portalFromUser(user), 'signed-out'));
    }
  }, [href, router, user]);

  const isStudent = user?.roles?.includes('STUDENT') ?? false;
  const isLecturer = user?.roles?.includes('LECTURER') ?? false;
  const isAdmin = user?.roles?.includes('ADMIN') ?? false;
  const isSuperAdmin = user?.roles?.includes('SUPER_ADMIN') ?? false;
  const isFacultyHead = user?.roles?.includes('TRUONG_KHOA') ?? false;

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

function userHasRequiredRole(
  user: User | null,
  requiredRoles: ('STUDENT' | 'LECTURER' | 'ADMIN' | 'SUPER_ADMIN')[],
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
  requiredRoles?: ('STUDENT' | 'LECTURER' | 'ADMIN' | 'SUPER_ADMIN')[],
) {
  const { user, isLoading, isLoggingOut } = useAuth();
  const router = useRouter();
  const { href } = useI18n();
  const rolesKey = requiredRoles?.join(',') ?? '';
  const requiredRolesList = useMemo(
    () =>
      rolesKey
        ? (rolesKey.split(',') as ('STUDENT' | 'LECTURER' | 'ADMIN' | 'SUPER_ADMIN')[])
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
