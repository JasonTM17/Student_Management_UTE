import { getSiteUrl } from '@/lib/site';

interface JsonLdProps {
  locale?: string;
}

export function JsonLd({ locale = 'vi' }: JsonLdProps) {
  const siteUrl = getSiteUrl();

  const organizationSchema = {
    '@context': 'https://schema.org',
    '@type': 'CollegeOrUniversity',
    name: 'Trường Đại học Công nghệ Kỹ thuật TP.HCM',
    alternateName: ['HCM-UTE', 'HCMUTE', 'CampusUTE', 'UTE'],
    url: siteUrl,
    logo: `${siteUrl}/icon.png`,
    image: `${siteUrl}/icon.png`,
    description:
      'Cổng thông tin đào tạo và học vụ trực tuyến CampusUTE - Trường Đại học Công nghệ Kỹ thuật TP.HCM.',
    address: {
      '@type': 'PostalAddress',
      streetAddress: '01 Võ Văn Ngân, Phường Linh Chiểu',
      addressLocality: 'Thành phố Thủ Đức',
      addressRegion: 'Thành phố Hồ Chí Minh',
      addressCountry: 'VN',
    },
    sameAs: [
      'https://hcmute.edu.vn',
      'https://www.facebook.com/hcmute.spkt/',
    ],
  };

  const webAppSchema = {
    '@context': 'https://schema.org',
    '@type': 'WebApplication',
    name: 'CampusUTE',
    alternateName: 'CampusUTE Portal',
    applicationCategory: 'EducationalApplication',
    operatingSystem: 'All',
    browserRequirements: 'Requires JavaScript. Requires HTML5.',
    url: siteUrl,
    offers: {
      '@type': 'Offer',
      price: '0',
      priceCurrency: 'VND',
    },
    author: {
      '@type': 'EducationalOrganization',
      name: 'Trường Đại học Công nghệ Kỹ thuật TP.HCM',
    },
  };

  const webSiteSchema = {
    '@context': 'https://schema.org',
    '@type': 'WebSite',
    name: 'CampusUTE',
    url: siteUrl,
    inLanguage: ['vi-VN', 'en-US'],
    publisher: {
      '@type': 'CollegeOrUniversity',
      name: 'Trường Đại học Công nghệ Kỹ thuật TP.HCM',
    },
  };

  return (
    <>
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(organizationSchema) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(webAppSchema) }}
      />
      <script
        type="application/ld+json"
        dangerouslySetInnerHTML={{ __html: JSON.stringify(webSiteSchema) }}
      />
    </>
  );
}
