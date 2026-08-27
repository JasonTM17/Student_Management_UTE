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
};

export default nextConfig;
