import type { NextRequest } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export function GET(request: NextRequest) {
  const origin = process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010';
  return fetch(`${origin}/api/v1/health/liveness`, {
    headers: {
      Accept: 'application/json',
      'X-Request-Id': request.headers.get('x-request-id') || 'web-health',
    },
    cache: 'no-store',
  });
}

export const HEAD = GET;
