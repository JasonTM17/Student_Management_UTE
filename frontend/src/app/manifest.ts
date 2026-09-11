import type { MetadataRoute } from 'next';

export default function manifest(): MetadataRoute.Manifest {
  return {
    name: 'CampusUTE - Cổng Thông Tin Đào Tạo & Quản Lý Sinh Viên HCM-UTE',
    short_name: 'CampusUTE',
    description:
      'Cổng thông tin đào tạo và học vụ trực tuyến CampusUTE - Trường Đại học Công nghệ Kỹ thuật TP.HCM.',
    id: '/vi',
    start_url: '/vi',
    display: 'standalone',
    background_color: '#002D62',
    theme_color: '#003F87',
    categories: ['education', 'productivity', 'utilities'],
    icons: [
      {
        src: '/icon.svg',
        sizes: '512x512',
        type: 'image/svg+xml',
        purpose: 'any',
      },
      {
        src: '/icon.png',
        sizes: '512x512',
        type: 'image/png',
        purpose: 'any',
      },
      {
        src: '/apple-icon.png',
        sizes: '180x180',
        type: 'image/png',
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
