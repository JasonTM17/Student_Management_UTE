'use client';

import * as React from 'react';
import { ArrowLeft, LogOut } from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { BrandMark } from '@/components/BrandMark';
import { LanguageToggle } from '@/components/LanguageToggle';
import { ThemeToggle } from '@/components/ThemeToggle';
import { Button } from '@/components/ui/button';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useI18n } from '@/i18n';

interface AdminFrameProps {
  title: string;
  description: string;
  eyebrow?: string;
  backHref?: string;
  backLabel?: string;
  actions?: React.ReactNode;
  children: React.ReactNode;
}

export function AdminFrame({
  title,
  description,
  eyebrow,
  backHref = '/admin',
  backLabel,
  actions,
  children,
}: AdminFrameProps) {
  const { user, logout } = useAuth();
  const { messages } = useI18n();

  const resolvedEyebrow = eyebrow || messages.adminShell.eyebrow;
  const resolvedBackLabel = backLabel || messages.adminShell.backToDashboard;

  return (
    <div className="min-h-screen bg-background">
      <nav className="border-b border-border/70 bg-background/95 backdrop-blur">
        <div className="mx-auto flex h-16 max-w-6xl items-center justify-between gap-4 px-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-3">
            <BrandMark href="/admin" compact />
            <LocalizedLink
              href={backHref}
              className="hidden items-center gap-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground sm:inline-flex"
              aria-label={resolvedBackLabel}
              title={resolvedBackLabel}
            >
              <ArrowLeft className="h-4 w-4" />
              {resolvedBackLabel}
            </LocalizedLink>
          </div>

          <div className="flex shrink-0 items-center gap-2">
            <LanguageToggle />
            <ThemeToggle />
            <div className="hidden text-sm text-muted-foreground md:block">
              {user?.firstName}
            </div>
            <Button
              type="button"
              variant="outline"
              className="h-10 w-10 px-0 sm:w-auto sm:px-4"
              onClick={() => void logout()}
              aria-label={messages.common.actions.signOut}
              title={messages.common.actions.signOut}
            >
              <LogOut className="h-4 w-4 sm:mr-2" />
              <span className="sr-only sm:not-sr-only">
                {messages.common.actions.signOut}
              </span>
            </Button>
          </div>
        </div>
      </nav>

      <main className="mx-auto min-w-0 max-w-6xl px-4 py-8 sm:px-6 lg:px-8">
        <PageHeader
          eyebrow={<SectionEyebrow>{resolvedEyebrow}</SectionEyebrow>}
          title={title}
          description={description}
          actions={actions}
        />
        <div className="min-w-0 pt-8">{children}</div>
      </main>
    </div>
  );
}
