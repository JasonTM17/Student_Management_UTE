import type { NextRequest } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

type RouteContext = {
  params: Promise<{ path?: string[] }>;
};

async function handle(request: NextRequest, context: RouteContext) {
  const { path = [] } = await context.params;
  const origin = (process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010').replace(/\/$/, '');
  const suffix = path.length > 0 ? `/${path.join('/')}` : '';
  const backendPath = suffix === '/openapi.json' ? '/v3/api-docs' : `/v3/api-docs${suffix}`;
  const headers = new Headers(request.headers);
  headers.delete('host');
  return fetch(`${origin}${backendPath}`, {
    method: request.method,
    headers,
    redirect: 'manual',
  });
}

export const GET = handle;
export const HEAD = handle;
