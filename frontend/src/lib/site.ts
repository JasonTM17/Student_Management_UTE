const DEFAULT_SITE_URL = 'https://tienson.io.vn';

function normalizeUrl(value: string) {
  return value.replace(/\/$/, '');
}

export function getSiteUrl() {
  return normalizeUrl(process.env.NEXT_PUBLIC_SITE_URL || DEFAULT_SITE_URL);
}

export function buildSiteUrl(path = '/') {
  return new URL(path, `${getSiteUrl()}/`).toString();
}
