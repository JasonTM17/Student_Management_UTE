import type { NextRequest } from 'next/server';

import { buildApiProxyUrl, ProxyPathError } from '@/lib/proxy-url';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';
export const maxDuration = 60;

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

// Truly public GET endpoints whose payload is identical for every caller may
// carry a short shared cache window; authenticated/private responses must
// never gain a cache directive here.
const PUBLIC_GET_PATHS = new Set(['site-appearance', 'article-taxonomy/v2/categories']);

async function handle(request: NextRequest, context: RouteContext) {
  const { path } = await context.params;
  const origin = (process.env.JAVA_API_ORIGIN ||
    process.env.BACKEND_INTERNAL_ORIGIN ||
    (process.env.NODE_ENV === 'production' ? 'https://campuscore-backend-p4em.onrender.com' : 'http://127.0.0.1:4010')).replace(/\/$/, '');
  const headers = new Headers(request.headers);
  headers.delete('host');
  headers.set('accept-encoding', 'identity');

  // Forwarded headers are client-controlled until we overwrite them: an
  // forwarded identity must come from runtime metadata, never a caller's XFF.
  // Vercel overwrites its platform header; trust it only on that platform.
  // Self-hosted runtimes without peer metadata use the shared proxy bucket.
  const clientIp =
    (request as any).ip ||
    (process.env.VERCEL === '1' ? request.headers.get('x-vercel-forwarded-for')?.split(',')[0]?.trim() : undefined) ||
    'web-proxy';
  headers.delete('x-real-ip');
  headers.delete('x-forwarded-for');
  headers.set('x-forwarded-for', clientIp);

  // Extra hop marker: Render appends this function's egress IP after our XFF
  // entry, so the backend's rightmost-XFF rule would see the edge, not the
  // user. A shared token authenticates the edge and lets the backend trust
  // this dedicated client-IP header instead.
  const proxyToken = process.env.CAMPUSCORE_PROXY_TOKEN;
  if (proxyToken) {
    headers.set('x-campuscore-proxy-token', proxyToken);
    headers.set('x-campuscore-client-ip', clientIp);
  }

  let upstreamUrl: URL;
  try {
    upstreamUrl = buildApiProxyUrl(origin, path, request.nextUrl.search);
  } catch (error) {
    if (error instanceof ProxyPathError) {
      return new Response(null, { status: 400 });
    }
    throw error;
  }

  const response = await fetch(upstreamUrl, {
    method: request.method,
    headers,
    body: ['GET', 'HEAD'].includes(request.method) ? undefined : await request.arrayBuffer(),
    redirect: 'manual',
    signal: request.signal,
  });

  const responseHeaders = new Headers(response.headers);
  responseHeaders.delete('transfer-encoding');
  responseHeaders.delete('content-encoding');
  responseHeaders.delete('content-length');
  responseHeaders.delete('connection');

  if (request.method === 'GET' && PUBLIC_GET_PATHS.has(path.join('/'))) {
    const upstreamCacheControl = response.headers.get('cache-control');
    responseHeaders.set(
      'Cache-Control',
      upstreamCacheControl || 'public, max-age=300, stale-while-revalidate=600',
    );
  }

  if (typeof response.headers.getSetCookie === 'function') {
    const cookies = response.headers.getSetCookie();
    if (cookies.length > 0) {
      responseHeaders.delete('set-cookie');
      for (const cookie of cookies) {
        responseHeaders.append('set-cookie', cookie);
      }
    }
  }

  // Fetch forbids even an empty ArrayBuffer for these statuses. In particular,
  // a missing thesis report is a legitimate 204, not a proxy server error.
  if (request.method === 'HEAD' || [204, 205, 304].includes(response.status)) {
    return new Response(null, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders,
    });
  }

  // SSE must reach the caller before upstream EOF: buffering hides every
  // delta and heartbeat until the complete answer has been generated.
  if (response.headers.get('content-type')?.split(';')[0].trim().toLowerCase() === 'text/event-stream') {
    responseHeaders.set('Cache-Control', 'no-cache, no-transform');
    responseHeaders.set('X-Accel-Buffering', 'no');
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers: responseHeaders,
    });
  }

  // Buffer non-streaming responses instead of piping `response.body`. Streaming the
  // body made the platform flush the outgoing headers from a different task
  // than the one that appended `set-cookie`, and on Vercel's Node runtime the
  // session cookies were dropped intermittently: the login POST answered 200
  // with no Set-Cookie, so the very next page load redirected the user to
  // /login?reason=session-expired (production repro 4/5 runs, fixed by this
  // buffering — response bodies here are JSON-sized, so memory is a non-issue).
  const bodyBuffer = await response.arrayBuffer();

  return new Response(bodyBuffer, {
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
