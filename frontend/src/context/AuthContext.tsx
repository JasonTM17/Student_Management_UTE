'use client';

import React, {
  ReactNode,
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from 'react';
import { useRouter } from 'next/navigation';
import { useI18n } from '@/i18n';
import { User } from '@/types/api';
import { authApi } from '@/lib/api';

interface AuthContextType {
  user: User | null;
  isLoading: boolean;
  isLoggingOut: boolean;
  isStudent: boolean;
  isLecturer: boolean;
  isAdmin: boolean;
  isSuperAdmin: boolean;
  login: (email: string, password: string) => Promise<User>;
  logout: () => Promise<void>;
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
    try {
      const userData = await authApi.me();
      setUser(userData);
    } catch {
      setUser(null);
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
    setIsLoggingOut(false);
    setUser(response.user);
    return response.user;
  }, []);

  const logout = useCallback(async () => {
    setIsLoggingOut(true);
    try {
      await authApi.logout();
    } catch {
      // Ignore logout API failures and still clear the client session.
    } finally {
      setUser(null);
      router.replace(`${href('/login')}?reason=signed-out`);
    }
  }, [href, router]);

  const isStudent = user?.roles?.includes('STUDENT') ?? false;
  const isLecturer = user?.roles?.includes('LECTURER') ?? false;
  const isAdmin = user?.roles?.includes('ADMIN') ?? false;
  const isSuperAdmin = user?.roles?.includes('SUPER_ADMIN') ?? false;

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
        login,
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

export function useRequireAuth(
  requiredRoles?: ('STUDENT' | 'LECTURER' | 'ADMIN' | 'SUPER_ADMIN')[],
) {
  const { user, isLoading, isLoggingOut } = useAuth();
  const router = useRouter();
  const { href } = useI18n();
  const [hasAccess, setHasAccess] = useState(false);
  const rolesKey = requiredRoles?.join(',') ?? '';

  useEffect(() => {
    if (isLoading || isLoggingOut) {
      return;
    }

    setHasAccess(false);

    if (!user) {
      router.push(href('/login'));
      return;
    }

    if (requiredRoles && requiredRoles.length > 0) {
      const hasRole = requiredRoles.some((role) => {
        if (role === 'ADMIN') {
          return user.roles?.includes('ADMIN') || user.roles?.includes('SUPER_ADMIN');
        }
        return user.roles?.includes(role);
      });

      if (!hasRole) {
        if (user.roles?.includes('ADMIN') || user.roles?.includes('SUPER_ADMIN')) {
          router.push(href('/admin'));
        } else if (user.roles?.includes('LECTURER')) {
          router.push(href('/dashboard/lecturer'));
        } else {
          router.push(href('/dashboard'));
        }
        return;
      }
    }

    setHasAccess(true);
  }, [href, user, isLoading, isLoggingOut, rolesKey, router]);

  return { user, isLoading, hasAccess };
}
