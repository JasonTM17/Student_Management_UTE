'use client';

import { useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';

export default function DashboardSignOutPage() {
  const { user, isLoading, logout } = useAuth();
  const { href, messages } = useI18n();
  const router = useRouter();
  const didRequestLogout = useRef(false);

  useEffect(() => {
    if (isLoading) {
      return;
    }

    if (!user) {
      router.replace(
        `${href('/login')}?portal=${window.location.pathname.includes('/lecturer') ? 'lecturer' : 'student'}&reason=signed-out`,
      );
      return;
    }

    if (didRequestLogout.current) {
      return;
    }

    didRequestLogout.current = true;
    void logout();
  }, [href, isLoading, logout, router, user]);

  return (
    <div className="rounded-[28px] border border-border/70 bg-card px-6 py-10 shadow-[0_24px_80px_rgba(15,23,42,0.08)] sm:px-8">
      <div className="max-w-xl">
        <p className="text-xs font-semibold uppercase tracking-[0.24em] text-primary/80">
          {messages.dashboardShell.signOutPage.eyebrow}
        </p>
        <h2 className="mt-4 text-3xl font-semibold tracking-tight text-foreground">
          {messages.dashboardShell.signOutPage.title}
        </h2>
        <p className="mt-3 max-w-lg text-sm leading-7 text-muted-foreground">
          {messages.dashboardShell.signOutPage.description}
        </p>
        <div className="mt-6 flex items-center gap-3 text-sm text-muted-foreground">
          <div className="h-5 w-5 animate-spin rounded-full border-2 border-primary/20 border-t-primary" />
          <span>{messages.dashboardShell.signOutPage.progress}</span>
        </div>
      </div>
    </div>
  );
}
