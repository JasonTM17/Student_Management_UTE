export function buildApiProxyUrl(
  origin: string,
  path: readonly string[],
  search: string,
): URL {
  const normalizedOrigin = origin.replace(/\/$/, '');
  const encodedPath = path.map((segment) => encodeURIComponent(segment)).join('/');
  const upstreamUrl = new URL(`${normalizedOrigin}/api/v1/${encodedPath}`);
  upstreamUrl.search = search;
  return upstreamUrl;
}
