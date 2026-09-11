'use client';

import { useEffect } from 'react';
import Link from 'next/link';
import { AlertTriangle, RefreshCw, Home } from 'lucide-react';
import { Button } from '@/components/ui/button';

export default function ErrorBoundary({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    // Log client error details for diagnostics
    console.error('Unhandled CampusUTE application error:', error);
  }, [error]);

  return (
    <div className="min-h-screen flex items-center justify-center p-6 bg-background text-foreground">
      <div className="w-full max-w-md rounded-lg border border-border/80 bg-card p-8 shadow-xl text-center space-y-6">
        <div className="mx-auto flex h-16 w-16 items-center justify-center rounded-full bg-destructive/10 text-destructive">
          <AlertTriangle className="h-8 w-8" />
        </div>

        <div className="space-y-2">
          <h2 className="text-xl font-bold tracking-tight text-foreground">
            Đã xảy ra sự cố không mong muốn
          </h2>
          <p className="text-sm text-muted-foreground leading-relaxed">
            Hệ thống Cổng học vụ CampusUTE đã ghi nhận lỗi. Bạn có thể thử tải lại trang hoặc quay về trang chủ.
          </p>
          {error.digest && (
            <p className="text-xs font-mono text-muted-foreground/70 bg-muted py-1 px-2 rounded">
              Mã lỗi: {error.digest}
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
            Thử lại
          </Button>

          <Button asChild variant="outline">
            <Link href="/" className="flex items-center justify-center gap-2">
              <Home className="h-4 w-4" />
              Về trang chủ
            </Link>
          </Button>
        </div>
      </div>
    </div>
  );
}
