import type { NextRequest } from 'next/server';

import { buildApiProxyUrl } from '@/lib/proxy-url';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

async function handle(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  const origin = (process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010').replace(/\/$/, '');
  const headers = new Headers(request.headers);
  headers.delete('host');
  headers.set('accept-encoding', 'identity');

  // Forwarded headers are client-controlled until we overwrite them: an
  // attacker must not be able to rotate the rate-limit key (the Java filter
  // prefers X-Real-IP and the leftmost XFF entry when proxy trust is on).
  // Next.js exposes the peer IP only on platforms that set `request.ip`
  // (Vercel, Render, ...); everywhere else the hop is marked as our own proxy,
  // which keys every browser on one bucket exactly like the container IP did.
  const clientIp =
    (request as any).ip ||
    request.headers.get('x-forwarded-for')?.split(',')[0]?.trim() ||
    'web-proxy';
  headers.delete('x-real-ip');
  headers.delete('x-forwarded-for');
  headers.set('x-forwarded-for', clientIp);

  const upstreamUrl = buildApiProxyUrl(origin, path, request.nextUrl.search);

  const response = await fetch(upstreamUrl, {
    method: request.method,
    headers,
    body: ['GET', 'HEAD'].includes(request.method) ? undefined : await request.arrayBuffer(),
    redirect: 'manual',
  });

  const responseHeaders = new Headers(response.headers);
  responseHeaders.delete('transfer-encoding');
  responseHeaders.delete('content-encoding');
  responseHeaders.delete('content-length');
  responseHeaders.delete('connection');

  if (typeof response.headers.getSetCookie === 'function') {
    const cookies = response.headers.getSetCookie();
    if (cookies.length > 0) {
      responseHeaders.delete('set-cookie');
      for (const cookie of cookies) {
        responseHeaders.append('set-cookie', cookie);
      }
    }
  }

  return new Response(response.body, {
    status: response.status,
    statusText: response.statusText,
    headers: responseHeaders,
  });
}

export const GET = handle;
export const POST = handle;
export const PUT = handle;
export const PATCH = handle;
export const DELETE = handle;
export const OPTIONS = handle;
export const HEAD = handle;
