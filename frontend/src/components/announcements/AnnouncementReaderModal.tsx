'use client';

import React, { useEffect, useRef, useState } from 'react';
import type { AnnouncementRecord } from '@/lib/api';
import { Modal } from '@/components/ui/modal';
import { useI18n } from '@/i18n';
import { resolveAnnouncementDomain } from '@/lib/announcement-presentation';
import {
  ReadingToolbar,
  type ReadingPreferences,
} from './reader/ReadingToolbar';
import { ReadingProgressBar } from './reader/ReadingProgressBar';
import { EditorialArticleMagazine } from './reader/layouts/EditorialArticleMagazine';
import { AdministrativeDispatchSheet } from './reader/layouts/AdministrativeDispatchSheet';

interface AnnouncementReaderModalProps {
  announcement: AnnouncementRecord | null;
  isOpen: boolean;
  onClose: () => void;
  onEdit?: (announcement: AnnouncementRecord) => void;
  allAnnouncements?: AnnouncementRecord[];
  onSelectAnnouncement?: (announcement: AnnouncementRecord) => void;
}

export function AnnouncementReaderModal({
  announcement,
  isOpen,
  onClose,
  onEdit,
  allAnnouncements = [],
  onSelectAnnouncement,
}: AnnouncementReaderModalProps) {
  const { locale } = useI18n();
  const [copied, setCopied] = useState(false);
  const [scrollProgress, setScrollProgress] = useState(0);
  const scrollContainerRef = useRef<HTMLDivElement>(null);

  // Initialize reading preferences
  const [preferences, setPreferences] = useState<ReadingPreferences>({
    mode: 'OFFICIAL',
    theme: 'light',
    fontFamily: 'sans',
    fontSize: 'base',
  });

  // Whenever a new announcement opens, intelligently choose the initial mode
  useEffect(() => {
    if (announcement) {
      const resolved = resolveAnnouncementDomain(announcement, locale);
      setPreferences((prev) => ({
        ...prev,
        mode: resolved.domain === 'EDITORIAL_ARTICLE' ? 'EDITORIAL' : 'OFFICIAL',
      }));
      setScrollProgress(0);
    }
  }, [announcement, locale]);

  // Track reading scroll progress
  useEffect(() => {
    const container = scrollContainerRef.current;
    if (!container || !isOpen) return;

    const handleScroll = () => {
      const { scrollTop, scrollHeight, clientHeight } = container;
      const totalScroll = scrollHeight - clientHeight;
      if (totalScroll <= 0) {
        setScrollProgress(100);
      } else {
        const progress = Math.min(100, Math.max(0, (scrollTop / totalScroll) * 100));
        setScrollProgress(progress);
      }
    };

    container.addEventListener('scroll', handleScroll, { passive: true });
    // Initial check
    handleScroll();

    return () => container.removeEventListener('scroll', handleScroll);
  }, [isOpen, announcement, preferences.mode]);

  if (!announcement) return null;

  const handlePrint = () => {
    if (typeof window !== 'undefined') {
      window.print();
    }
  };

  const handleShare = async () => {
    try {
      if (typeof window !== 'undefined') {
        const url = `${window.location.origin}/dashboard/announcements?id=${encodeURIComponent(announcement.id)}`;
        await navigator.clipboard.writeText(url);
        setCopied(true);
        setTimeout(() => setCopied(false), 2000);
      }
    } catch {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const domain = resolveAnnouncementDomain(announcement, locale);

  return (
    <Modal
      isOpen={isOpen}
      onClose={onClose}
      showCloseButton={false}
      printable={true}
      className="max-w-4xl sm:max-w-5xl p-0 overflow-hidden"
    >
      <div className="flex flex-col max-h-[88vh] print:max-h-none print:overflow-visible">
        {/* Reading Progress Indicator (Top Micro-bar) */}
        <ReadingProgressBar
          progress={scrollProgress}
          tone={
            domain.domain === 'EDITORIAL_ARTICLE'
              ? 'from-blue-600 via-indigo-500 to-primary'
              : 'from-red-600 via-rose-500 to-amber-600'
          }
        />

        {/* Floating Sticky Reading Toolbar */}
        <ReadingToolbar
          preferences={preferences}
          onPreferencesChange={setPreferences}
          onPrint={handlePrint}
          onShare={handleShare}
          copied={copied}
          onClose={onClose}
          onEdit={
            onEdit
              ? () => {
                  onClose();
                  onEdit(announcement);
                }
              : undefined
          }
          locale={locale}
        />

        {/* Scrollable Document Body Container */}
        <div
          ref={scrollContainerRef}
          className="flex-1 overflow-y-auto p-4 sm:p-6 lg:p-8 space-y-6 print:p-0 print:overflow-visible"
        >
          {preferences.mode === 'EDITORIAL' ? (
            <EditorialArticleMagazine
              announcement={announcement}
              preferences={preferences}
              relatedAnnouncements={allAnnouncements}
              onSelectAnnouncement={onSelectAnnouncement}
              locale={locale}
            />
          ) : (
            <AdministrativeDispatchSheet
              announcement={announcement}
              preferences={preferences}
              locale={locale}
            />
          )}
        </div>
      </div>
    </Modal>
  );
}
