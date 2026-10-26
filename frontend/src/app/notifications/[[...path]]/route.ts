import type { NextRequest } from 'next/server';

import { isSafeInternalPath } from '@/lib/safe-path';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

type RouteContext = {
  params: Promise<{
    path?: string[];
  }>;
};

async function handle(request: NextRequest, context: RouteContext) {
  const { path = [] } = await context.params;
  const suffix = path.length > 0 ? `/${path.join('/')}` : '/my';
  // Decoded catch-all segments can carry "." / ".." or encoded traversal
  // remnants; joined blindly they would escape the /api/v1/notifications
  // prefix and reach arbitrary backend paths with the caller's session.
  if (!isSafeInternalPath(suffix)) {
    return Response.json({ message: 'Bad Request' }, { status: 400 });
  }
  const origin = process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010';
  const upstreamUrl = new URL(`${origin}/api/v1/notifications${suffix}`);
  upstreamUrl.search = request.nextUrl.search;
  const headers = new Headers({ Accept: 'application/json' });
  for (const name of ['authorization', 'cookie', 'content-type', 'x-csrf-token']) {
    const value = request.headers.get(name);
    if (value) headers.set(name, value);
  }
  return fetch(upstreamUrl, {
    method: request.method,
    headers,
    body: request.method === 'GET' || request.method === 'HEAD' ? undefined : await request.text(),
    cache: 'no-store',
    signal: request.signal,
  });
}

export const GET = handle;
export const POST = handle;
export const HEAD = handle;
