const javaApiOrigin = (process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010').replace(/\/$/, '');

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
  async rewrites() {
    return [
      {
        source: '/api/v1/:path*',
        destination: `${javaApiOrigin}/api/v1/:path*`,
      },
      {
        source: '/api/docs',
        destination: `${javaApiOrigin}/api/docs`,
      },
    ];
  },
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
