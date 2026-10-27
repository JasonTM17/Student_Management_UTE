/** @type {import('next').NextConfig} */

/**
 * Content-Security-Policy is now ENFORCED with a
 * per-request nonce minted in src/middleware.ts (script-src nonce +
 * strict-dynamic). The policy cannot live in `headers()` — those values are
 * static while nonces must be minted per request. The inline scripts that
 * once required Report-Only (theme bootstrap, JsonLd, Next flight data) are
 * nonced; TinyMCE's dynamic script loading rides on 'strict-dynamic'.
 */
const nextConfig = {
  reactStrictMode: true,
  output: process.env.OUTPUT_STANDALONE === 'true' || process.platform !== 'win32' ? 'standalone' : undefined,
  compress: true,
  poweredByHeader: false,
  experimental: {
    serverActions: {
      bodyParser: false,
    },
    optimizePackageImports: [
      'lucide-react',
      'date-fns',
      'chart.js',
      'react-chartjs-2',
      '@tanstack/react-query',
    ],
  },
  images: {
    unoptimized: true,
  },
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          // CSP (incl. frame-ancestors) is enforced in middleware.ts;
          // X-Frame-Options stays as the fallback for legacy browsers.
          {
            key: 'X-Frame-Options',
            value: 'DENY',
          },
          {
            key: 'X-Content-Type-Options',
            value: 'nosniff',
          },
          {
            key: 'Referrer-Policy',
            value: 'strict-origin-when-cross-origin',
          },
          // The app never touches capture, positioning, payment, or device
          // APIs (clipboard-write stays enabled: the copy buttons use it).
          {
            key: 'Permissions-Policy',
            value: 'camera=(), microphone=(), geolocation=(), payment=(), usb=()',
          },
          // No cross-origin popup or OAuth window flow exists, so browsing-
          // context isolation costs nothing.
          {
            key: 'Cross-Origin-Opener-Policy',
            value: 'same-origin',
          },
        ],
      },
      {
        // The reset link carries a single-use credential in the query
        // string: it must never leak through Referer. In Next 15 a later,
        // more specific entry wins over the catch-all for the same header
        // key, so this overrides strict-origin-when-cross-origin only here.
        source: '/reset-password',
        headers: [
          {
            key: 'Referrer-Policy',
            value: 'no-referrer',
          },
        ],
      },
      {
        source: '/:locale(en|vi)/reset-password',
        headers: [
          {
            key: 'Referrer-Policy',
            value: 'no-referrer',
          },
        ],
      },
    ];
  },
};

export default nextConfig;
