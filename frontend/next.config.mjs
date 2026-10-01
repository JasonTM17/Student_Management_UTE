/** @type {import('next').NextConfig} */

/**
 * RT-P2-4: staged Content-Security-Policy. This is deliberately
 * Report-Only, not enforced — an enforced `script-src 'self'` would break
 * this app in three known places (see plan phase-05 §5.6):
 *   1. app/layout.tsx has an inline theme-bootstrap <script>.
 *   2. Next 15 streams flight data as inline `self.__next_f.push(...)` on
 *      every page.
 *   3. TinyMCE runs inside an iframe (frame-src 'self').
 * `headers()` is static and cannot mint per-request nonces; real enforcement
 * needs middleware.ts and would cost static HTML optimization. Enforcement is
 * gated on the browser walkthrough that proves the policy keeps login, editor
 * and chatbot working (follow-up, not part of this phase).
 */
const contentSecurityPolicyReportOnly = [
  "default-src 'self'",
  "script-src 'self'",
  // Next injects inline styles; TinyMCE composes with inline content_style.
  "style-src 'self' 'unsafe-inline'",
  // Announcements may embed https images and the editor inlines data:/blob:.
  "img-src 'self' data: blob: https:",
  "font-src 'self' data:",
  "media-src 'self' data: blob: https:",
  "frame-src 'self' blob:",
  "worker-src 'self' blob:",
  "object-src 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "frame-ancestors 'self'",
].join('; ');

const nextConfig = {
  reactStrictMode: true,
  output: process.env.OUTPUT_STANDALONE === 'true' || process.platform !== 'win32' ? 'standalone' : undefined,
  experimental: {
    serverActions: {
      bodyParser: false,
    },
  },
  images: {
    unoptimized: true,
  },
  async headers() {
    return [
      {
        source: '/(.*)',
        headers: [
          {
            key: 'Content-Security-Policy-Report-Only',
            value: contentSecurityPolicyReportOnly,
          },
          // Round-3 contract ct-6: frame-ancestors lives inside the
          // Report-Only CSP, so nothing ENFORCED blocked framing — the prod
          // site could be embedded by a foreign page (clickjacking).
          // X-Frame-Options is the enforced fallback until the staged CSP
          // graduates to enforced (see RT-P2-4 comment above).
          {
            key: 'X-Frame-Options',
            value: 'DENY',
          },
        ],
      },
    ];
  },
};

export default nextConfig;
