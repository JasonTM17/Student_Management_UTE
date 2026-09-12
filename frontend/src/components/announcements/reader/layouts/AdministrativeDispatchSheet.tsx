'use client';

import React from 'react';
import Image from 'next/image';
import {
  Building2,
  Calendar,
  FileCheck,
  ShieldCheck,
  Users,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { AnnouncementRecord } from '@/lib/api';
import type { Locale } from '@/i18n/config';
import { RichContentRenderer } from '@/components/ui/rich-content-renderer';
import {
  announcementAudienceBadge,
  announcementDistribution,
  announcementSalutation,
  announcementSectionLabel,
  formatAnnouncementPublisher,
  formatAnnouncementSemester,
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
  const publishDate = announcement.publishAt || announcement.createdAt;
  const dateObj = publishDate ? new Date(publishDate) : new Date();
  const day = String(dateObj.getDate()).padStart(2, '0');
  const month = String(dateObj.getMonth() + 1).padStart(2, '0');
  const year = dateObj.getFullYear();

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
        universityName: 'TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH',
        departmentName: 'KHOA CÔNG NGHỆ THÔNG TIN & PHÒNG ĐÀO TẠO',
        nationalMotto1: 'CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM',
        nationalMotto2: 'Độc lập - Tự do - Hạnh phúc',
        datePrefix: `TP. Hồ Chí Minh, ngày ${day} tháng ${month} năm ${year}`,
        docRef: `Số: ${docRefNumber}`,
        officialDocBadge: 'VĂN BẢN ĐIỆN TỬ',
        noticeHeader: 'THÔNG BÁO',
        audience: 'Đối tượng áp dụng',
        section: 'Lớp học phần',
        salutation: 'Kính gửi',
        distribution: 'Nơi nhận:',
        eSealOrg: 'TRƯỜNG ĐH CÔNG NGHỆ KỸ THUẬT TP.HCM',
        certStatus: 'Chứng thư số e-Office HCM-UTE hợp lệ',
      }
    : {
        ministryName: 'MINISTRY OF EDUCATION AND TRAINING',
        universityName: 'HO CHI MINH CITY UNIVERSITY OF TECHNOLOGY AND ENGINEERING',
        departmentName: 'FACULTY OF IT & ACADEMIC AFFAIRS OFFICE',
        nationalMotto1: 'SOCIALIST REPUBLIC OF VIETNAM',
        nationalMotto2: 'Independence - Freedom - Happiness',
        datePrefix: `Ho Chi Minh City, ${month}/${day}/${year}`,
        docRef: `Ref: ${docRefNumber}`,
        officialDocBadge: 'OFFICIAL DOCUMENT',
        noticeHeader: 'ANNOUNCEMENT',
        audience: 'Target audience',
        section: 'Section',
        salutation: 'To',
        distribution: 'Distribution:',
        eSealOrg: 'HCM-UTE OFFICIAL E-OFFICE',
        certStatus: 'HCM-UTE e-Office Digital Certificate Verified',
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
  const distributionRecipients = announcementDistribution(announcement, locale);

  const getSignerInfo = (pub: string, loc: string, ann: AnnouncementRecord) => {
    const p = pub.toLowerCase();
    const title = (ann.title || '').toLowerCase();

    // 1. Rector level documents
    if (
      p.includes('hiệu trưởng') ||
      p.includes('ban giám hiệu') ||
      p.includes('bgh') ||
      p.includes('hội đồng trường') ||
      p.includes('rector') ||
      p.includes('board of rectors') ||
      title.startsWith('quyết định')
    ) {
      return {
        signatureTitle: loc === 'vi' ? 'HIỆU TRƯỞNG' : 'RECTOR',
        role:
          loc === 'vi'
            ? 'TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP.HCM'
            : 'HO CHI MINH CITY UNIVERSITY OF TECHNOLOGY AND EDUCATION',
        name: 'PGS. TS. LÊ HIẾU GIANG',
        sealUnit:
          loc === 'vi'
            ? 'Ban Giám hiệu - Trường ĐH Sư phạm Kỹ thuật TP.HCM'
            : 'Board of Rectors - HCMUTE',
      };
    }

    // 2. Vice Rector
    if (p.includes('phó hiệu trưởng') || p.includes('vice rector')) {
      return {
        signatureTitle: loc === 'vi' ? 'KT. HIỆU TRƯỞNG' : 'FOR THE RECTOR',
        role: loc === 'vi' ? 'PHÓ HIỆU TRƯỞNG' : 'VICE RECTOR',
        name: 'TS. QUÁCH THANH HẢI',
        sealUnit:
          loc === 'vi'
            ? 'Ban Giám hiệu - Trường ĐH Công nghệ Kỹ thuật TP.HCM'
            : 'Board of Rectors - HCM-UTE',
      };
    }

    // 3. Student Affairs
    if (p.includes('công tác sinh viên') || p.includes('ctsv') || p.includes('student affairs')) {
      return {
        signatureTitle: loc === 'vi' ? 'TL. HIỆU TRƯỞNG' : 'FOR THE RECTOR',
        role: loc === 'vi' ? 'TRƯỞNG PHÒNG CÔNG TÁC SINH VIÊN' : 'HEAD OF STUDENT AFFAIRS',
        name: 'ThS. ĐẶNG BÁ NGOẠN',
        sealUnit: loc === 'vi' ? 'Phòng Công tác Sinh viên' : 'Student Affairs Office',
      };
    }

    // 4. Testing & QA
    if (
      p.includes('khảo thí') ||
      p.includes('đảm bảo chất lượng') ||
      p.includes('đbcl') ||
      p.includes('testing') ||
      p.includes('qa')
    ) {
      return {
        signatureTitle: loc === 'vi' ? 'TL. HIỆU TRƯỞNG' : 'FOR THE RECTOR',
        role: loc === 'vi' ? 'TRƯỞNG PHÒNG KHẢO THÍ & ĐBCL' : 'HEAD OF TESTING & QA',
        name: 'TS. NGUYỄN VĂN THÁI',
        sealUnit: loc === 'vi' ? 'Phòng Khảo thí & ĐBCL' : 'Testing & QA Office',
      };
    }

    // 5. IT Faculty
    if (p.includes('khoa') || p.includes('công nghệ thông tin') || p.includes('cntt')) {
      return {
        signatureTitle: loc === 'vi' ? 'TL. HIỆU TRƯỞNG' : 'FOR THE RECTOR',
        role: loc === 'vi' ? 'TRƯỞNG KHOA CÔNG NGHỆ THÔNG TIN' : 'DEAN OF FACULTY OF IT',
        name: 'PGS. TS. HOÀNG VĂN DŨNG',
        sealUnit: loc === 'vi' ? 'Khoa Công nghệ Thông tin' : 'Faculty of Information Technology',
      };
    }

    // 6. Academic Affairs Office (Default)
    return {
      signatureTitle: loc === 'vi' ? 'TL. HIỆU TRƯỞNG' : 'FOR THE RECTOR',
      role: loc === 'vi' ? 'TRƯỞNG PHÒNG ĐÀO TẠO' : 'HEAD OF ACADEMIC AFFAIRS',
      name: 'TS. QUÁCH THANH HẢI',
      sealUnit: loc === 'vi' ? 'Phòng Đào tạo' : 'Academic Affairs Office',
    };
  };

  const signer = getSignerInfo(publisherName, locale, announcement);

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
        preferences.fontFamily === 'serif' ? 'font-serif' : 'font-sans',
        preferences.theme === 'sepia'
          ? 'bg-[#FBF0D9] text-[#2D2A26]'
          : preferences.theme === 'dark'
            ? 'bg-card text-foreground'
            : 'bg-white text-foreground',
      )}
    >
      {/* Header Row: University Issuer & National Motto (Decree 30/2020) */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-2 sm:items-start border-b border-border/60 pb-6">
        {/* Left Column: University & Issuing Office */}
        <div className="flex items-start gap-3.5">
          <div className="relative h-14 w-14 shrink-0 overflow-hidden rounded-full border border-border/60 bg-white dark:bg-slate-100 p-1 shadow-xs">
            <Image
              src="/hcmute-logo.png"
              alt="HCMUTE Emblem"
              width={48}
              height={48}
              className="h-full w-full object-contain"
              priority
            />
          </div>
          <div className="space-y-0.5">
            <p className="text-[10.5px] font-medium uppercase tracking-tight text-muted-foreground">
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
        <p className="text-base font-bold text-primary sm:text-lg max-w-2xl mx-auto leading-snug">
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
            {day}/{month}/{year}
          </span>
        </div>
      </div>

      {/* Rich Body Content */}
      <div className="border-t border-b border-border/60 py-6">
        <div
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

      {/* Institutional Sign-off Section */}
      <div className="grid grid-cols-1 gap-6 pt-2 sm:grid-cols-2 sm:items-start text-xs">
        {/* Left: Distribution / Nơi nhận */}
        <div className="space-y-1.5">
          <p className="font-bold italic text-foreground text-[12.5px]">{copy.distribution}</p>
          <ul className="text-[11.5px] text-muted-foreground space-y-0.5 list-none pl-0 leading-tight">
            {distributionRecipients.map((rec, idx) => (
              <li key={idx}>{rec}</li>
            ))}
          </ul>
        </div>

        {/* Right: Signature & Electronic Seal */}
        <div className="text-center sm:text-right space-y-1">
          <p className="font-bold uppercase tracking-wider text-foreground text-xs">
            {signer.signatureTitle}
          </p>
          <p className="font-bold uppercase tracking-wider text-primary text-xs">
            {signer.role}
          </p>

          {/* Official Electronic Seal Box (Dấu Ký Số Điện Tử Chuẩn e-Office) */}
          <div className="my-2 sm:ml-auto max-w-[260px] rounded border-2 border-red-600 dark:border-red-500 bg-red-500/[0.06] dark:bg-red-950/40 p-2.5 text-left shadow-xs">
            <div className="flex items-center gap-1.5 border-b border-red-500/40 pb-1 text-[10.5px] font-bold uppercase tracking-tight text-red-600 dark:text-red-400">
              <ShieldCheck className="h-4 w-4 shrink-0 text-red-600 dark:text-red-400" />
              <span>KÝ BỞI: {copy.eSealOrg}</span>
            </div>
            <div className="pt-1.5 text-[10px] leading-snug text-red-700 dark:text-red-300 space-y-0.5 font-sans">
              <p className="font-semibold">Đơn vị: {signer.sealUnit || publisherName}</p>
              <p>Người ký: {signer.name}</p>
              <p>
                Ngày ký: {day}/{month}/{year} {dateObj.toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' })}
              </p>
              <p className="text-[9.5px] text-red-600 dark:text-red-400 italic">
                ✓ {copy.certStatus}
              </p>
            </div>
          </div>

          <p className="font-bold text-foreground text-sm pt-1">
            {signer.name}
          </p>
        </div>
      </div>
    </div>
  );
}
