/** Raised when a request path would climb out of the API prefix. */
export class ProxyPathError extends Error {
  constructor() {
    super('Dot segments are not allowed in proxied API paths');
    this.name = 'ProxyPathError';
  }
}

export function buildApiProxyUrl(
  origin: string,
  path: readonly string[],
  search: string,
): URL {
  // encodeURIComponent leaves "." and ".." untouched and the URL parser then
  // collapses them, which would let a caller reach upstream paths outside
  // /api/v1/. Refuse them before the URL is built.
  if (path.some((segment) => segment === '.' || segment === '..')) {
    throw new ProxyPathError();
  }
  const normalizedOrigin = origin.replace(/\/$/, '');
  const encodedPath = path.map((segment) => encodeURIComponent(segment)).join('/');
  const upstreamUrl = new URL(`${normalizedOrigin}/api/v1/${encodedPath}`);
  upstreamUrl.search = search;
  return upstreamUrl;
}
