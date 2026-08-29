import { NextRequest, NextResponse } from 'next/server';
import { CSRF_COOKIE_NAME, hasCsrfSessionHint } from '@/lib/session-hint';
import { readSiteAppearance, writeSiteAppearance } from '@/lib/site-appearance-store';

export const runtime = 'nodejs';
export const dynamic = 'force-dynamic';

const CSRF_HEADER_NAME = 'X-CSRF-Token';

function cookieValue(cookieSource: string, name: string): string {
  const escapedName = name.replace(/([.*+?^${}()|[\]\\])/g, '\\$1');
  const match = cookieSource.match(new RegExp(`(?:^|; )${escapedName}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : '';
}

function javaOrigin(): string {
  return (process.env.JAVA_API_ORIGIN || 'http://127.0.0.1:4010').replace(/\/$/, '');
}

async function requireAdmin(request: NextRequest): Promise<boolean> {
  const cookie = request.headers.get('cookie') ?? '';
  if (!hasCsrfSessionHint(cookie)) {
    return false;
  }

  const csrf =
    request.headers.get(CSRF_HEADER_NAME) || cookieValue(cookie, CSRF_COOKIE_NAME);
  const response = await fetch(`${javaOrigin()}/api/v1/auth/me`, {
    method: 'GET',
    headers: {
      cookie,
      [CSRF_HEADER_NAME]: csrf,
    },
    cache: 'no-store',
  });

  if (!response.ok) {
    return false;
  }

  const user = (await response.json()) as { roles?: string[]; role?: string };
  const roles = user.roles ?? (user.role ? [user.role] : []);
  return roles.includes('ADMIN') || roles.includes('SUPER_ADMIN');
}

export async function GET() {
  const appearance = await readSiteAppearance();
  return NextResponse.json(appearance, {
    headers: {
      'Cache-Control': 'no-store',
    },
  });
}

export async function PUT(request: NextRequest) {
  const allowed = await requireAdmin(request);
  if (!allowed) {
    return NextResponse.json({ message: 'Forbidden' }, { status: 403 });
  }

  const body = await request.json().catch(() => null);
  const appearance = await writeSiteAppearance(body);
  return NextResponse.json(appearance, {
    headers: {
      'Cache-Control': 'no-store',
    },
  });
}
