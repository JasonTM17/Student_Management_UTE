import type { MetadataRoute } from 'next';
import { buildSiteUrl } from '@/lib/site';

export default function sitemap(): MetadataRoute.Sitemap {
  const lastModified = new Date();

  const publicRoutes = [
    { path: '', priority: 1.0, changeFrequency: 'daily' as const },
    { path: '/vi', priority: 1.0, changeFrequency: 'daily' as const },
    { path: '/en', priority: 0.9, changeFrequency: 'daily' as const },
    { path: '/vi/login', priority: 0.8, changeFrequency: 'monthly' as const },
    { path: '/en/login', priority: 0.7, changeFrequency: 'monthly' as const },
    { path: '/vi/register', priority: 0.8, changeFrequency: 'monthly' as const },
    { path: '/en/register', priority: 0.7, changeFrequency: 'monthly' as const },
    { path: '/vi/forgot-password', priority: 0.5, changeFrequency: 'monthly' as const },
    { path: '/en/forgot-password', priority: 0.5, changeFrequency: 'monthly' as const },
  ];

  return publicRoutes.map((route) => ({
    url: buildSiteUrl(route.path),
    lastModified,
    changeFrequency: route.changeFrequency,
    priority: route.priority,
    alternates: {
      languages: {
        vi: buildSiteUrl(route.path.replace(/^\/en/, '/vi') || '/vi'),
        en: buildSiteUrl(route.path.replace(/^\/vi/, '/en') || '/en'),
      },
    },
  }));
}

