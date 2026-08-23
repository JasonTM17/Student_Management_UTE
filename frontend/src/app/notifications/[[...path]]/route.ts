import type { NextRequest } from 'next/server';

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
  const origin = process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010';
  const headers = new Headers({ Accept: 'application/json' });
  const authorization = request.headers.get('authorization');
  const cookie = request.headers.get('cookie');
  if (authorization) headers.set('Authorization', authorization);
  if (cookie) headers.set('Cookie', cookie);
  return fetch(`${origin}/api/v1/notifications${suffix}`, {
    method: request.method,
    headers,
    body: request.method === 'GET' || request.method === 'HEAD' ? undefined : await request.text(),
    cache: 'no-store',
  });
}

export const GET = handle;
export const POST = handle;
export const HEAD = handle;
