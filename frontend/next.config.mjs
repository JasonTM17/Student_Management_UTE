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
};

export default nextConfig;
