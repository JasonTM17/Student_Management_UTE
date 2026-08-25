/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: true,
  output: 'standalone',
  experimental: {
    serverActions: {
      bodyParser: false,
    },
  },
  images: {
    unoptimized: true,
  },
  // The `/api/v1` and `/api/docs` route handlers read JAVA_API_ORIGIN at
  // request time. Keeping the origin out of build-time rewrites makes the
  // standalone image portable across local, staging, and hosted networks.
  // JAVA_API_ORIGIN is intentionally runtime-configured by the container.
  async headers() {
    const noReferrer = [
      { key: 'Referrer-Policy', value: 'no-referrer' },
    ];
    return [
      { source: '/verify-email', headers: noReferrer },
      { source: '/reset-password', headers: noReferrer },
      { source: '/:locale(en|vi)/verify-email', headers: noReferrer },
      { source: '/:locale(en|vi)/reset-password', headers: noReferrer },
    ];
  },
};

export default nextConfig;
