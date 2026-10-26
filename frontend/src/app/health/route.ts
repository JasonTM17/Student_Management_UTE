import type { NextRequest } from 'next/server';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

export async function GET(request: NextRequest) {
  const origin = process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010';
  try {
    return await fetch(`${origin}/api/v1/health/liveness`, {
      headers: {
        Accept: 'application/json',
        'X-Request-Id': request.headers.get('x-request-id') || 'web-health',
      },
      cache: 'no-store',
      signal: request.signal,
    });
  } catch {
    // A refused or timed-out probe is a liveness answer, not an unhandled
    // error: report the backend as down instead of leaking a framework 500.
    return Response.json({ status: 'DOWN' }, { status: 503 });
  }
}

export const HEAD = GET;
