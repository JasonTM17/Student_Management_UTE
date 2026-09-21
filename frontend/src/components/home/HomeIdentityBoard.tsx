'use client';

import { useMemo, useState } from 'react';
import { ArrowRight, CheckCircle2 } from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import { useI18n } from '@/i18n';

export function HomeIdentityBoard() {
  const { messages, locale } = useI18n();
  const [activeIndex, setActiveIndex] = useState(0);
  const lanes = useMemo(
    () => [
      messages.home.identityRows,
      messages.home.lecturerIdentityRows,
      messages.home.adminIdentityRows,
    ],
    [
      messages.home.adminIdentityRows,
      messages.home.identityRows,
      messages.home.lecturerIdentityRows,
    ],
  );
  const rows = lanes[activeIndex] ?? messages.home.identityRows;

  const targetPortals = [
    {
      href: '/login?portal=student',
      label: locale === 'vi' ? 'Vào Cổng Sinh viên' : 'Enter Student Portal',
    },
    {
      href: '/login?portal=lecturer',
      label: locale === 'vi' ? 'Vào Cổng Giảng viên' : 'Enter Lecturer Portal',
    },
    {
      href: '/login?portal=admin',
      label: locale === 'vi' ? 'Vào Cổng Quản trị' : 'Enter Admin Portal',
    },
  ];
  const activePortal = targetPortals[activeIndex] ?? targetPortals[0];

  return (
    <aside className="flex flex-col justify-between rounded-xl border-l-4 border-[var(--portal-chrome-accent)] bg-[var(--portal-sidebar)] p-6 text-[var(--portal-sidebar-text)] shadow-xs">
      <div>
        <p className="text-xs font-bold uppercase tracking-wider text-[var(--portal-chrome-accent)]">
          {messages.home.snapshotEyebrow}
        </p>
        <div
          className="mt-4 flex gap-2 border-b border-white/15"
          role="tablist"
          aria-label={messages.home.snapshotEyebrow}
        >
          {messages.home.identityTabs.map((tab, index) => {
            const selected = index === activeIndex;
            return (
              <button
                key={tab}
                type="button"
                role="tab"
                aria-selected={selected}
                onClick={() => setActiveIndex(index)}
                className={
                  selected
                    ? 'border-b-2 border-[var(--portal-chrome-accent)] px-2 pb-3 text-sm font-semibold text-[var(--portal-sidebar-text)]'
                    : 'border-b-2 border-transparent px-2 pb-3 text-sm font-medium text-[var(--portal-sidebar-text)]/70 hover:text-[var(--portal-sidebar-text)]'
                }
              >
                {tab}
              </button>
            );
          })}
        </div>
        <div className="mt-2 flex-1 divide-y divide-white/10" role="tabpanel">
          {rows.map((row) => (
            <div key={`${activeIndex}-${row.title}`} className="py-3 text-sm">
              <div className="flex items-center gap-1.5 font-medium">
                <CheckCircle2 className="h-3.5 w-3.5 shrink-0 text-[var(--portal-chrome-accent)]" />
                <span className="min-w-0">{row.title}</span>
              </div>
              <p className="mt-0.5 pl-5 text-xs leading-5 text-[var(--portal-sidebar-text)]/70">
                {row.description}
              </p>
            </div>
          ))}
        </div>
      </div>

      <div className="mt-6 border-t border-white/15 pt-4">
        <LocalizedLink
          href={activePortal.href}
          className="group flex w-full items-center justify-between rounded-lg bg-white px-4 py-2.5 text-sm font-bold text-[#003f87] transition-all hover:bg-white/95 shadow-sm"
        >
          <span>{activePortal.label}</span>
          <ArrowRight className="h-4 w-4 transition-transform group-hover:translate-x-1" />
        </LocalizedLink>
      </div>
    </aside>
  );
}

