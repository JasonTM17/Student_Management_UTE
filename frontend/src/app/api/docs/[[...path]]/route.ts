import type { NextRequest } from 'next/server';

import { isSafeInternalPath } from '@/lib/safe-path';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

type RouteContext = {
  params: Promise<{ path?: string[] }>;
};

async function handle(request: NextRequest, context: RouteContext) {
  const { path = [] } = await context.params;
  const suffix = path.length > 0 ? `/${path.join('/')}` : '';
  // Decoded catch-all segments can carry "." / ".." or encoded traversal
  // remnants; joining them blindly lets the upstream URL escape the
  // /v3/api-docs prefix (undici normalizes dot segments), so the proxy would
  // reach arbitrary backend paths with the caller's cookies attached.
  if (suffix && !isSafeInternalPath(suffix)) {
    return Response.json({ message: 'Bad Request' }, { status: 400 });
  }
  const origin = (process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010').replace(/\/$/, '');
  const backendPath = suffix === '/openapi.json' ? '/v3/api-docs' : `/v3/api-docs${suffix}`;
  const upstreamUrl = new URL(`${origin}${backendPath}`);
  upstreamUrl.search = request.nextUrl.search;
  const headers = new Headers(request.headers);
  headers.delete('host');
  headers.set('accept-encoding', 'identity');

  // Same rule as the v1 proxy: a caller's XFF is spoofable, so forwarded
  // identity comes from runtime metadata (or the shared proxy bucket), never
  // from the request.
  const clientIp =
    (request as any).ip ||
    (process.env.VERCEL === '1' ? request.headers.get('x-vercel-forwarded-for')?.split(',')[0]?.trim() : undefined) ||
    'web-proxy';
  headers.delete('x-real-ip');
  headers.delete('x-forwarded-for');
  headers.set('x-forwarded-for', clientIp);

  const response = await fetch(upstreamUrl, {
    method: request.method,
    headers,
    redirect: 'manual',
    signal: request.signal,
  });

  const responseHeaders = new Headers(response.headers);
  responseHeaders.delete('transfer-encoding');
  responseHeaders.delete('content-encoding');
  responseHeaders.delete('content-length');
  responseHeaders.delete('connection');

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: responseHeaders,
  });
}

export const GET = handle;
export const HEAD = handle;
