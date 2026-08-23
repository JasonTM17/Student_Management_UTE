const DEFAULT_SITE_URL = 'http://localhost:3000';

function normalizeUrl(value: string) {
  return value.replace(/\/$/, '');
}

export function getSiteUrl() {
  return normalizeUrl(process.env.NEXT_PUBLIC_SITE_URL || DEFAULT_SITE_URL);
}

export function buildSiteUrl(path = '/') {
  return new URL(path, `${getSiteUrl()}/`).toString();
}
