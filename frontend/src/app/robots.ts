import type { MetadataRoute } from 'next';
import { buildSiteUrl, getSiteUrl } from '@/lib/site';

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: '*',
        allow: ['/', '/en/', '/vi/'],
        disallow: [
          '/admin/',
          '/dashboard/',
          '/en/admin/',
          '/vi/admin/',
          '/en/dashboard/',
          '/vi/dashboard/',
          '/api/',
        ],
      },
    ],
    sitemap: buildSiteUrl('/sitemap.xml'),
    host: getSiteUrl(),
  };
}
