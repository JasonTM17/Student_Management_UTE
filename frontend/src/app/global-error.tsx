'use client';

import { useEffect } from 'react';

// app/error.tsx cannot catch a crash in the root layout or its providers
// (I18nProvider/AuthProvider live there) — only global-error can, and it
// renders without the root layout, so there is no Tailwind stylesheet, no
// Link context and no React context. Everything below is therefore plain
// markup with inline styles, and the locale comes straight from the
// persisted cookie exactly like app/error.tsx does.
const COPY = {
  vi: {
    title: 'Đã xảy ra sự cố không mong muốn',
    body: 'Cổng học vụ đã ghi nhận lỗi nghiêm trọng. Bạn có thể thử tải lại ứng dụng.',
    retry: 'Thử lại',
    home: 'Về trang chủ',
  },
  en: {
    title: 'Something went wrong',
    body: 'The academic portal logged a critical error. You can try reloading the application.',
    retry: 'Try again',
    home: 'Back to home',
  },
} as const;

function readLocale(): 'vi' | 'en' {
  if (typeof document === 'undefined') return 'vi';
  return /(?:^|;\s*)cc_locale=en(?:;|$)/i.test(document.cookie) ? 'en' : 'vi';
}

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  useEffect(() => {
    console.error('Unhandled application error in the root layout:', error);
  }, [error]);

  const copy = COPY[readLocale()];

  return (
    <html lang={readLocale()}>
      <body
        style={{
          margin: 0,
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '24px',
          background: '#f8fafc',
          color: '#0f172a',
          fontFamily:
            "system-ui, -apple-system, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif",
        }}
      >
        <div
          style={{
            maxWidth: '26rem',
            width: '100%',
            border: '1px solid #e2e8f0',
            borderRadius: '12px',
            background: '#ffffff',
            padding: '32px',
            textAlign: 'center',
            display: 'flex',
            flexDirection: 'column',
            gap: '16px',
            boxShadow: '0 20px 25px -5px rgba(15, 23, 42, 0.1)',
          }}
        >
          <h2 style={{ margin: 0, fontSize: '1.25rem', fontWeight: 700 }}>{copy.title}</h2>
          <p style={{ margin: 0, fontSize: '0.875rem', color: '#475569', lineHeight: 1.6 }}>
            {copy.body}
          </p>
          {error.digest && (
            <p
              style={{
                margin: 0,
                fontSize: '0.75rem',
                fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                color: '#64748b',
                background: '#f1f5f9',
                borderRadius: '6px',
                padding: '4px 8px',
              }}
            >
              {error.digest}
            </p>
          )}
          <div style={{ display: 'flex', gap: '12px', justifyContent: 'center', flexWrap: 'wrap' }}>
            <button
              type="button"
              onClick={() => reset()}
              style={{
                border: 'none',
                borderRadius: '8px',
                padding: '8px 16px',
                cursor: 'pointer',
                fontWeight: 600,
                background: '#0f172a',
                color: '#ffffff',
              }}
            >
              {copy.retry}
            </button>
            {/* A hard navigation is deliberate: the root layout crashed, so
                the router/context state Link depends on is unreliable — the
                home link must force a full reload. */}
            {/* eslint-disable-next-line @next/next/no-html-link-for-pages */}
            <a
              href="/"
              style={{
                borderRadius: '8px',
                padding: '8px 16px',
                fontWeight: 600,
                textDecoration: 'none',
                border: '1px solid #cbd5e1',
                color: '#0f172a',
              }}
            >
              {copy.home}
            </a>
          </div>
        </div>
      </body>
    </html>
  );
}
