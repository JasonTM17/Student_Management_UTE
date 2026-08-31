'use client';

import { useMemo, useState } from 'react';
import { useI18n } from '@/i18n';

export function HomeIdentityBoard() {
  const { messages } = useI18n();
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

  return (
    <aside className="flex flex-col rounded-lg border-l-4 border-[var(--portal-yellow)] bg-[var(--portal-sidebar)] p-6 text-[var(--portal-sidebar-text)]">
      <p className="text-xs font-semibold uppercase tracking-wide text-[var(--portal-yellow)]">
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
                  ? 'border-b-2 border-[var(--portal-yellow)] px-1 pb-3 text-sm font-semibold text-[var(--portal-sidebar-text)]'
                  : 'border-b-2 border-transparent px-1 pb-3 text-sm font-medium text-[var(--portal-sidebar-text)]/80 hover:text-[var(--portal-sidebar-text)]'
              }
            >
              {tab}
            </button>
          );
        })}
      </div>
      <div className="mt-1 flex-1 divide-y divide-white/15" role="tabpanel">
        {rows.map((row) => (
          <div
            key={`${activeIndex}-${row.code}`}
            className="grid grid-cols-[4.5rem_1fr_auto] items-baseline gap-3 py-3.5 text-sm"
          >
            <span className="font-semibold tabular-nums">{row.code}</span>
            <span className="min-w-0 truncate">{row.label}</span>
            <span className="tabular-nums text-[var(--portal-sidebar-text)]/80">{row.meta}</span>
          </div>
        ))}
      </div>
    </aside>
  );
}
