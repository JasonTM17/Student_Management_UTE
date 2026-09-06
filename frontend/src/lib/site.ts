const PRODUCTION_SITE_URL = 'https://campusute.io.vn';
const DEFAULT_SITE_URL = 'http://localhost:3000';

function normalizeUrl(value: string) {
  return value.replace(/\/$/, '');
}

export function getSiteUrl() {
  if (process.env.NEXT_PUBLIC_SITE_URL) {
    return normalizeUrl(process.env.NEXT_PUBLIC_SITE_URL);
  }
  if (process.env.VERCEL_PROJECT_PRODUCTION_URL) {
    return normalizeUrl(`https://${process.env.VERCEL_PROJECT_PRODUCTION_URL}`);
  }
  if (process.env.VERCEL_URL) {
    return normalizeUrl(`https://${process.env.VERCEL_URL}`);
  }
  if (process.env.NODE_ENV === 'production') {
    return PRODUCTION_SITE_URL;
  }
  return DEFAULT_SITE_URL;
}

export function buildSiteUrl(path = '/') {
  return new URL(path, `${getSiteUrl()}/`).toString();
}

