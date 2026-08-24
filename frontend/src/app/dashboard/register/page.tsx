'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useI18n } from '@/i18n';

/** Compatibility route kept during the registration contract deprecation window. */
export default function RegisterCompatibilityPage() {
  const router = useRouter();
  const { href } = useI18n();
  useEffect(() => { router.replace(href('/dashboard/registration')); }, [href, router]);
  return <p className="p-6 text-sm text-muted-foreground">Opening registration workspace…</p>;
}
