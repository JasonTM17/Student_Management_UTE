'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useI18n } from '@/i18n';
import { LoadingState } from '@/components/ui/state-block';

export const dynamic = 'force-dynamic';

export default function AdminEditorRedirectPage() {
  const router = useRouter();
  const { href, locale } = useI18n();

  useEffect(() => {
    // The announcements console deep-links here with ?editId=..., so the
    // forward must keep the incoming query intact.
    const search = typeof window === 'undefined' ? '' : window.location.search;
    router.replace(`${href('/dashboard/editor')}${search}`);
  }, [router, href]);

  return (
    <div className="flex min-h-[50vh] items-center justify-center p-6">
      <LoadingState
        label={
          locale === 'vi'
            ? 'Đang chuyển tiếp đến Trình soạn thông báo...'
            : 'Redirecting to the Announcement Editor...'
        }
      />
    </div>
  );
}
