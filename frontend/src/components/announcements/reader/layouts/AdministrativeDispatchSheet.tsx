'use client';

import React from 'react';
import Image from 'next/image';
import {
  Building2,
  Calendar,
  FileCheck,
  Users,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AnnouncementRecord } from '@/lib/api';
import type { Locale } from '@/i18n/config';
import { localeCodes } from '@/i18n/config';
import { useI18n } from '@/i18n';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import {
  announcementAudienceBadge,
  announcementSalutation,
  announcementSectionLabel,
  formatAnnouncementPublisher,
  formatAnnouncementSemester,
  isVietnameseText,
} from '@/lib/announcement-presentation';
import { DocumentAttachmentsList } from '../DocumentAttachmentsList';
import { TableOfContents } from '../TableOfContents';
import type { ReadingPreferences } from '../ReadingToolbar';

interface AdministrativeDispatchSheetProps {
  announcement: AnnouncementRecord;
  preferences: ReadingPreferences;
  locale?: Locale;
}

export function AdministrativeDispatchSheet({
  announcement,
  preferences,
  locale = 'vi',
}: AdministrativeDispatchSheetProps) {
  const isVi = locale === 'vi';
  const { formatDate, formatDateTime } = useI18n();
  const publishDate = announcement.publishAt || announcement.createdAt;
  const dateObj = publishDate ? new Date(publishDate) : new Date();
  // Both date lines go through the shared locale-aware formatter (localeCodes →
  // vi-VN) instead of re-deriving day/month here; the signature block already
  // renders this DD/MM/YYYY shape.
  const officialDate = formatDate(dateObj, {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  });
  // Vietnamese dispatch headings are prose ("ngày 21 tháng 9 năm 2026"), not a
  // slashed date. formatDate always merges year+month+day defaults, so partial
  // options would still render the full date — ask Intl for each single part
  // through the same BCP-47 tag the provider uses.
  const proseDay = new Intl.DateTimeFormat(localeCodes[locale], { day: 'numeric' }).format(dateObj);
  const proseMonthRaw = new Intl.DateTimeFormat(localeCodes[locale], { month: 'long' }).format(dateObj);
  // vi-VN emits the standalone month capitalized ("Tháng 9"); prose dates
  // lowercase it ("ngày 21 tháng 9 năm 2026").
  const proseMonth = proseMonthRaw.charAt(0).toLowerCase() + proseMonthRaw.slice(1);
  const proseYear = new Intl.DateTimeFormat(localeCodes[locale], { year: 'numeric' }).format(dateObj);

  // Deterministic official reference number based on ID/date
  const docHash =
    Math.abs(
      (announcement.id || 'notice')
        .split('')
        .reduce((acc, char) => (acc * 31 + char.charCodeAt(0)) | 0, 100),
    ) % 900 + 100;

  const isRectorDoc =
    (announcement.publishedBy || '').toLowerCase().includes('ban giám hiệu') ||
    (announcement.publishedBy || '').toLowerCase().includes('hiệu trưởng') ||
    (announcement.title || '').toLowerCase().startsWith('quyết định');
  const isFacultyDoc = (announcement.publishedBy || '').toLowerCase().includes('khoa');
  const docRefSuffix = isRectorDoc ? 'TB-ĐHCNKT' : isFacultyDoc ? 'TB-CNTT' : 'TB-ĐHCNKT-ĐT';
  const docRefNumber = `${docHash}/${docRefSuffix}`;

  const copy = isVi
    ? {
        ministryName: 'BỘ GIÁO DỤC VÀ ĐÀO TẠO',
        universityName: 'TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP. HỒ CHÍ MINH',
        departmentName: 'KHOA CÔNG NGHỆ THÔNG TIN & PHÒNG ĐÀO TẠO',
        nationalMotto1: 'CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM',
        nationalMotto2: 'Độc lập - Tự do - Hạnh phúc',
        datePrefix: `TP. Hồ Chí Minh, ngày ${proseDay} ${proseMonth} năm ${proseYear}`,
        docRef: `Số: ${docRefNumber}`,
        officialDocBadge: 'VĂN BẢN ĐIỆN TỬ',
        noticeHeader: 'THÔNG BÁO',
        audience: 'Đối tượng áp dụng',
        section: 'Lớp học phần',
        salutation: 'Kính gửi',
        issuedByLabel: 'Đơn vị ban hành',
        signOffHint: 'Thông báo được ban hành điện tử trên Cổng học vụ',
      }
    : {
        ministryName: 'MINISTRY OF EDUCATION AND TRAINING',
        universityName: 'HO CHI MINH CITY UNIVERSITY OF TECHNOLOGY AND ENGINEERING',
        departmentName: 'FACULTY OF IT & ACADEMIC AFFAIRS OFFICE',
        nationalMotto1: 'SOCIALIST REPUBLIC OF VIETNAM',
        nationalMotto2: 'Independence - Freedom - Happiness',
        datePrefix: `Ho Chi Minh City, ${formatDate(dateObj)}`,
        docRef: `Ref: ${docRefNumber}`,
        officialDocBadge: 'OFFICIAL DOCUMENT',
        noticeHeader: 'ANNOUNCEMENT',
        audience: 'Target audience',
        section: 'Section',
        salutation: 'To',
        issuedByLabel: 'Issuing unit',
        signOffHint: 'Published electronically on the Campus Portal',
      };

  const publisherName = formatAnnouncementPublisher(
    announcement.publishedBy,
    locale,
    copy.departmentName,
  );
  const semesterFormatted = formatAnnouncementSemester(announcement, locale);
  const sectionLabel = announcementSectionLabel(announcement);
  const audienceBadge = announcementAudienceBadge(announcement, locale);
  const salutationTarget = announcementSalutation(announcement, locale);
  // Sign-off shows only data the record actually carries: the issuing unit
  // (publishedBy) and the publish timestamp. Fabricating a named signatory or
  // a "verified digital certificate" seal would present invented authority as
  // fact on an official-looking document (audit finding 1).

  const subjectHeading = announcement.title.toUpperCase().startsWith('THÔNG BÁO')
    ? announcement.title.replace(/^THÔNG BÁO\s*[:-]?\s*/i, 'V/v ')
    : `V/v ${announcement.title}`;

  const getFontSizeClass = () => {
    switch (preferences.fontSize) {
      case 'sm':
        return 'text-xs sm:text-sm';
      case 'lg':
        return 'text-base sm:text-lg';
      case 'xl':
        return 'text-lg sm:text-xl';
      default:
        return 'text-sm sm:text-base';
    }
  };

  return (
    <div
      className={cn(
        'mx-auto max-w-4xl rounded-xl border border-border/80 p-6 sm:p-10 shadow-sm space-y-6 transition-colors duration-200 print:border-none print:shadow-none print:p-0',
        `reader-theme-${preferences.theme}`,
        preferences.fontFamily === 'serif' ? 'font-serif' : 'font-sans',
        preferences.theme === 'sepia'
          ? 'bg-[#FBF0D9] text-[#2D2A26]'
          : preferences.theme === 'dark'
            ? 'bg-card text-foreground'
            : 'bg-white text-foreground',
      )}
      data-reader-theme={preferences.theme}
    >
      {/* Header Row: University Issuer & National Motto (Decree 30/2020) */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 sm:items-start border-b border-border/60 pb-6">
        {/* Left Column: University & Issuing Office */}
        <div className="flex items-start gap-3.5">
          <div className="relative h-14 w-14 shrink-0 overflow-hidden rounded-full border border-border/60 bg-white dark:bg-slate-100 p-1 shadow-xs">
            <Image
              src="/hcmute-logo.png"
              alt="HCMUTE Emblem"
              fill
              sizes="56px"
              className="object-contain"
              priority
            />
          </div>
          <div className="space-y-0.5">
            <p className="text-[11px] font-medium uppercase tracking-tight text-muted-foreground">
              {copy.ministryName}
            </p>
            <p className="text-[11.5px] font-bold uppercase tracking-tight text-primary leading-tight">
              {copy.universityName}
            </p>
            <p className="text-xs font-bold uppercase tracking-tight text-foreground border-b border-foreground/30 pb-0.5 inline-block">
              {publisherName.toUpperCase()}
            </p>
            <p className="text-[11px] font-medium text-muted-foreground pt-0.5">
              {copy.docRef}
            </p>
          </div>
        </div>

        {/* Right Column: National Motto & Date */}
        <div className="space-y-1 text-left sm:text-right">
          <p className="text-[12px] font-bold uppercase tracking-tight text-foreground sm:text-[13px]">
            {copy.nationalMotto1}
          </p>
          <p className="text-xs font-bold text-foreground">
            {copy.nationalMotto2}
          </p>
          <div className="w-28 sm:ml-auto border-b border-foreground/50 my-1" />
          <p className="text-[11.5px] italic text-muted-foreground pt-0.5">
            {copy.datePrefix}
          </p>
        </div>
      </div>

      {/* Document Title Header */}
      <div className="py-4 text-center space-y-2">
        <h1 className="text-xl font-extrabold uppercase tracking-wide text-foreground sm:text-2xl">
          {copy.noticeHeader}
        </h1>
        <p
          lang={!isVi && isVietnameseText(subjectHeading) ? 'vi' : undefined}
          className="text-base font-bold text-primary sm:text-lg max-w-2xl mx-auto leading-snug"
        >
          {subjectHeading}
        </p>
        <div className="w-20 border-b border-primary/40 mx-auto pt-1" />
      </div>

      {/* Recipient & Scope Section (Administrative Salutation) */}
      <div className="rounded-md bg-secondary/40 border border-border/50 p-3.5 text-xs text-muted-foreground space-y-1">
        <p className="text-sm font-semibold text-foreground">
          <span className="italic font-bold text-primary">{copy.salutation}:</span>{' '}
          {salutationTarget}
        </p>
        <div className="flex flex-wrap items-center gap-3 pt-1 text-[11.5px]">
          <span className="inline-flex items-center gap-1 font-medium">
            <Building2 className="h-3.5 w-3.5 text-primary" />
            {publisherName}
          </span>
          <span className="text-foreground/30">•</span>
          <span className="inline-flex items-center gap-1 font-semibold text-foreground">
            <Users className="h-3.5 w-3.5 text-primary" />
            {copy.audience}: {audienceBadge.label}
          </span>
          {semesterFormatted ? (
            <>
              <span className="text-foreground/30">•</span>
              <span className="font-semibold text-foreground">
                {semesterFormatted}
              </span>
            </>
          ) : null}
          {sectionLabel ? (
            <>
              <span className="text-foreground/30">•</span>
              <span className="font-medium">
                {copy.section}: {sectionLabel}
              </span>
            </>
          ) : null}
          <span className="text-foreground/30">•</span>
          <span className="inline-flex items-center gap-1">
            <Calendar className="h-3.5 w-3.5 text-primary" />
            {officialDate}
          </span>
        </div>
      </div>

      {/* Rich Body Content */}
      <div className="border-t border-b border-border/60 py-6">
        <div
          lang={!isVi && isVietnameseText(announcement.content) ? 'vi' : undefined}
          className={cn(
            'leading-relaxed text-foreground/90 transition-all',
            getFontSizeClass(),
          )}
        >
          <RichContentRenderer content={announcement.content} />
        </div>

        {/* Attached Documents */}
        <DocumentAttachmentsList content={announcement.content} locale={locale} />
      </div>

      {/* Institutional sign-off — honest issuing-unit block; no invented
          signatory name or certificate seal (the record has neither). */}
      <div className="pt-2 text-xs sm:text-right sm:ml-auto sm:max-w-xs space-y-1">
        <p className="font-bold uppercase tracking-wider text-foreground text-xs">
          {copy.issuedByLabel}
        </p>
        <p className="font-bold uppercase tracking-wider text-primary text-xs">
          {publisherName}
        </p>
        <p className="text-[11px] italic text-muted-foreground">
          {copy.signOffHint} — {formatDateTime(dateObj)}
        </p>
      </div>
    </div>
  );
}
