'use client';

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { useI18n } from '@/i18n';
import { LoadingState } from '@/components/ui/state-block';

export const dynamic = 'force-dynamic';

export default function LecturerThesisRedirectPage() {
  const router = useRouter();
  const { href, locale } = useI18n();

  useEffect(() => {
    router.replace(href('/dashboard/thesis'));
  }, [router, href]);

  return (
    <div className="flex min-h-[50vh] items-center justify-center p-6">
      <LoadingState
        label={
          locale === 'vi'
            ? 'Đang chuyển tiếp đến Cổng Đồ án – Khóa luận tốt nghiệp...'
            : 'Redirecting to Thesis & Graduation Portal...'
        }
      />
    </div>
  );
}
