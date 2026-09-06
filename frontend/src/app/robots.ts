import type { MetadataRoute } from 'next';
import { buildSiteUrl, getSiteUrl } from '@/lib/site';

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: '*',
        allow: [
          '/',
          '/en/',
          '/vi/',
          '/login',
          '/en/login',
          '/vi/login',
          '/register',
          '/en/register',
          '/vi/register',
          '/icon.svg',
          '/icon.png',
          '/favicon.ico',
        ],
        disallow: [
          '/admin/',
          '/dashboard/',
          '/en/admin/',
          '/vi/admin/',
          '/en/dashboard/',
          '/vi/dashboard/',
          '/api/',
          '/_next/',
        ],
      },
    ],
    sitemap: buildSiteUrl('/sitemap.xml'),
    host: getSiteUrl(),
  };
}

