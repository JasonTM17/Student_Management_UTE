'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { LinkButton } from '@/components/ui/link-button';
import { ForbiddenState, LoadingState } from '@/components/ui/state-block';
import { useI18n } from '@/i18n';

export function WorkspaceForbiddenState({
  signedIn = true,
}: {
  signedIn?: boolean;
}) {
  const { isLecturer, isAdmin, isSuperAdmin } = useAuth();
  const { messages } = useI18n();
  const copy = messages.workspaceForbidden;
  const title = copy.title;
  const description = signedIn ? copy.signedInDescription : copy.signedOutDescription;
  const actionHref = signedIn
    ? isAdmin || isSuperAdmin
      ? '/admin'
      : isLecturer
        ? '/dashboard/lecturer'
        : '/dashboard'
    : '/login';
  const actionLabel = signedIn
    ? messages.common.actions.openDashboard
    : messages.common.actions.signIn;

  return (
    <ForbiddenState
      title={title}
      description={description}
      action={
        <LinkButton href={actionHref} variant="outline">
          {actionLabel}
        </LinkButton>
      }
    />
  );
}

export function ProtectedRoute({ children }: { children: React.ReactNode }) {
  const { user, isLoading } = useAuth();
  const router = useRouter();
  const { href, messages } = useI18n();

  useEffect(() => {
    if (!isLoading && !user) {
      router.push(
        `${href('/login')}?portal=${window.location.pathname.includes('/admin') ? 'admin' : window.location.pathname.includes('/lecturer') ? 'lecturer' : 'student'}`,
      );
    }
  }, [href, user, isLoading, router]);

  if (isLoading) {
    return <LoadingState label={messages.common.states.loadingContent} />;
  }

  if (!user) {
    return <WorkspaceForbiddenState signedIn={false} />;
  }

  return <>{children}</>;
}
