'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';
import { Button } from '@/components/ui/button';

const COPY = {
  vi: {
    title: 'Đã xảy ra sự cố không mong muốn',
    body: 'Hệ thống Cổng học vụ CampusCore đã ghi nhận lỗi. Bạn có thể thử tải lại trang hoặc quay về trang chủ.',
    digest: 'Mã lỗi',
    retry: 'Thử lại',
    home: 'Về trang chủ',
  },
  en: {
    title: 'Something went wrong',
    body: 'The CampusCore academic portal logged the error. You can retry the page or return to the homepage.',
    digest: 'Error code',
    retry: 'Try again',
    home: 'Back to home',
  },
} as const;

function readLocale(): 'vi' | 'en' {
  if (typeof document === 'undefined') return 'vi';
  return /(?:^|;\s*)cc_locale=en(?:;|$)/i.test(document.cookie) ? 'en' : 'vi';
}

export default function ErrorBoundary({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Log client error details for diagnostics
    console.error('Unhandled CampusCore application error:', error);
  }, [error]);

  // The i18n provider itself may be the thing that crashed, so the locale
  // comes straight from the persisted cookie instead of React context.
  const copy = COPY[readLocale()];

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-background text-foreground">
      <div className="w-full max-w-md rounded-lg border border-border/80 bg-card p-8 shadow-xl text-center space-y-6">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10 text-destructive">
          <AlertTriangle className="h-8 w-8" />
        </div>

        <div className="space-y-2">
          <h2 className="text-xl font-bold tracking-tight text-foreground">
            {copy.title}
          </h2>
          <p className="text-sm text-muted-foreground leading-relaxed">
            {copy.body}
          </p>
          {error.digest && (
            <p className="text-xs font-mono text-muted-foreground/70 bg-muted py-1 px-2 rounded">
              {copy.digest}: {error.digest}
            </p>
          )}
        </div>

        <div className="flex flex-col sm:flex-row gap-3 justify-center pt-2">
          <Button
            onClick={() => reset()}
            variant="default"
            className="flex items-center justify-center gap-2"
          >
            <RefreshCw className="h-4 w-4" />
            {copy.retry}
          </Button>

          <Button asChild variant="outline">
            <Link href="/" className="flex items-center justify-center gap-2">
              <Home className="h-4 w-4" />
              {copy.home}
            </Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
