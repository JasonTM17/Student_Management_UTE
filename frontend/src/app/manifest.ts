import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'CampusCore',
    short_name: 'CampusCore',
    description:
      'CampusCore is a student portal for identity, academics, announcements, notifications, and thesis workflows.',
    id: '/en',
    start_url: '/en',
    display: 'standalone',
    background_color: '#101826',
    theme_color: '#101826',
    categories: ['education', 'productivity', 'business'],
    icons: [
      {
        src: '/icons/campuscore-icon.svg',
        sizes: '512x512',
        type: 'image/svg+xml',
        purpose: 'any',
      },
      {
        src: '/icons/campuscore-maskable.svg',
        sizes: '512x512',
        type: 'image/svg+xml',
        purpose: 'maskable',
      },
    ],
    screenshots: [
      {
        src: '/screenshots/home-en.svg',
        sizes: '1440x1024',
        type: 'image/svg+xml',
        form_factor: 'wide',
        label: 'CampusCore homepage in English',
      },
      {
        src: '/screenshots/home-vi.svg',
        sizes: '1440x1024',
        type: 'image/svg+xml',
        form_factor: 'wide',
        label: 'Trang chủ CampusCore bằng tiếng Việt',
      },
    ],
  };
}
