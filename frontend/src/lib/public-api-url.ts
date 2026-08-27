const SAME_ORIGIN_API = '/api/v1';

function isHostLoopbackApiPort(url: URL) {
  const host = url.hostname.toLowerCase();
  return (
    (host === '127.0.0.1' || host === 'localhost') && url.port === '4010'
  );
}

/**
 * Browser clients in Docker must use the Next same-origin proxy.
 * Host/Expo `.env` may still point at http://127.0.0.1:4010/api/v1; that
 * value must never become the production web API origin.
 */
export function resolvePublicApiBaseUrl(configured?: string | null): string {
  const value = typeof configured === 'string' ? configured.trim() : '';
  if (!value) {
    return SAME_ORIGIN_API;
  }

  try {
    const parsed = new URL(value, 'http://campuscore.invalid');
    if (isHostLoopbackApiPort(parsed)) {
      return SAME_ORIGIN_API;
    }
  } catch {
    return value;
  }

  return value;
}
