'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LoadingState } from '@/components/ui/state-block';
import { curriculumApi } from '@/lib/api';
import { MyCurriculumResponse } from '@/types/api';
import { Button } from '@/components/ui/button';
import {
  Printer,
  Check,
  Award,
  FileCheck,
  Bus,
  Building,
  Briefcase,
  Info,
  TriangleAlert,
} from 'lucide-react';

type CertificatePurpose =
  | 'MILITARY_DEFERMENT'
  | 'STUDENT_LOAN'
  | 'BUS_PASS'
  | 'TAX_EXEMPTION'
  | 'INTERNSHIP';

interface PurposeOption {
  id: CertificatePurpose;
  titleVi: string;
  titleEn: string;
  decreeVi: string;
  decreeEn: string;
  bodyVi: string;
  bodyEn: string;
  defaultRecipientVi: string;
  defaultRecipientEn: string;
  icon: React.ElementType;
}

const PURPOSE_OPTIONS: PurposeOption[] = [
  {
    id: 'MILITARY_DEFERMENT',
    titleVi: 'Tạm hoãn nghĩa vụ quân sự',
    titleEn: 'Military Service Deferment',
    decreeVi: 'Theo Nghị định 13/2016/NĐ-CP & Luật Nghĩa vụ quân sự',
    decreeEn: 'Per Decree 13/2016/ND-CP & Military Service Law',
    bodyVi:
      'Bổ túc hồ sơ xin tạm hoãn gọi nhập ngũ trong thời gian đào tạo đại học chính quy theo quy định của pháp luật.',
    bodyEn:
      'Completing documentation for temporary deferment of military enlistment during full-time undergraduate studies per national regulations.',
    defaultRecipientVi: 'Ban Chỉ huy Quân sự cấp Xã/Phường/Thị trấn và Ban CHQS cấp Quận/Huyện/Thị xã',
    defaultRecipientEn: 'Local Military Command at Commune/Ward and District Levels',
    icon: ShieldCheckIcon,
  },
  {
    id: 'STUDENT_LOAN',
    titleVi: 'Vay vốn Ngân hàng Chính sách Xã hội',
    titleEn: 'Social Policy Student Loan',
    decreeVi: 'Theo Quyết định số 157/2007/QĐ-TTg & QĐ 05/2022/QĐ-TTg của Thủ tướng Chính phủ',
    decreeEn: 'Per Decision 157/2007/QD-TTg & Decision 05/2022/QD-TTg by Prime Minister',
    bodyVi:
      'Làm thủ tục đề nghị vay vốn chương trình tín dụng học sinh, sinh viên có hoàn cảnh khó khăn tại Ngân hàng Chính sách Xã hội.',
    bodyEn:
      'Applying for preferential student loan program at the Bank for Social Policies for tuition and living expenses.',
    defaultRecipientVi: 'Ngân hàng Chính sách Xã hội địa phương nơi sinh viên đăng ký cư trú',
    defaultRecipientEn: 'Local Bank for Social Policies where the student resides',
    icon: Building,
  },
  {
    id: 'BUS_PASS',
    titleVi: 'Đăng ký vé tháng xe buýt sinh viên',
    titleEn: 'Student Bus Pass Application',
    decreeVi: 'Chính sách trợ giá vận tải công cộng học sinh - sinh viên TP.HCM',
    decreeEn: 'Ho Chi Minh City Public Transit Student Subsidy Policy',
    bodyVi:
      'Đăng ký làm thẻ vé tháng hoặc mua vé tập xe buýt ưu đãi dành cho học sinh, sinh viên trên địa bàn Thành phố Hồ Chí Minh.',
    bodyEn:
      'Registering for monthly subsidized student bus passes across the Ho Chi Minh City transit network.',
    defaultRecipientVi: 'Trung tâm Quản lý Giao thông công cộng TP. Hồ Chí Minh và các đơn vị vận tải xe buýt',
    defaultRecipientEn: 'HCMC Public Transport Management Center and Bus Transit Agencies',
    icon: Bus,
  },
  {
    id: 'TAX_EXEMPTION',
    titleVi: 'Giảm trừ gia cảnh thuế TNCN cho phụ huynh',
    titleEn: 'Personal Income Tax Family Exemption',
    decreeVi: 'Căn cứ Thông tư 111/2013/TT-BTC của Bộ Tài chính',
    decreeEn: 'Per Circular 111/2013/TT-BTC by Ministry of Finance',
    bodyVi:
      'Làm thủ tục kê khai giảm trừ gia cảnh người phụ thuộc là con đang theo học đại học cho người nộp thuế thu nhập cá nhân.',
    bodyEn:
      'Submitting dependent deduction proof for personal income tax declaration of parents/guardians.',
    defaultRecipientVi: 'Cơ quan Thuế hoặc Cơ quan, đơn vị nơi cha/mẹ người nộp thuế công tác',
    defaultRecipientEn: 'Tax Administration Authority or Parent/Guardian Employer',
    icon: FileCheck,
  },
  {
    id: 'INTERNSHIP',
    titleVi: 'Giấy giới thiệu liên hệ thực tập tốt nghiệp',
    titleEn: 'Internship Recommendation Letter',
    decreeVi: 'Theo Kế hoạch đào tạo thực hành và thực tập doanh nghiệp HCMUTE',
    decreeEn: 'Per HCMUTE Practical Training and Corporate Internship Program',
    bodyVi:
      'Liên hệ cơ quan, doanh nghiệp để thực tập tốt nghiệp và tiếp cận môi trường thực tế phục vụ chương trình đào tạo kỹ sư/cử nhân.',
    bodyEn:
      'Contacting corporate partner organizations for graduation internship and professional practice.',
    defaultRecipientVi: 'Ban Giám đốc và Phòng Nhân sự / Đào tạo Quý Cơ quan, Doanh nghiệp',
    defaultRecipientEn: 'Management Board & Human Resources Department of Partner Enterprises',
    icon: Briefcase,
  },
];

// Small local icon so the purpose list keeps its shield glyph without
// importing lucide's ShieldCheck (which read as a security endorsement on the
// old fabricated page).
function ShieldCheckIcon({ className }: { className?: string }) {
  return (
    <svg
      className={className}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d="M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z" />
      <path d="m9 12 2 2 4-4" />
    </svg>
  );
}

export default function CertificatesPage() {
  // The generator mints a student certificate from student identity claims, so a
  // staff account reaching it by URL used to render a sheet prefilled with blank
  // student attributes instead of a forbidden state.
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, messages, formatDate } = useI18n();
  const isVi = locale === 'vi';
  const certCopy = messages.certificates;

  const [selectedPurpose, setSelectedPurpose] = useState<CertificatePurpose>('MILITARY_DEFERMENT');
  const [customRecipient, setCustomRecipient] = useState<string>('');
  const [curriculumData, setCurriculumData] = useState<MyCurriculumResponse | null>(null);

  useEffect(() => {
    let active = true;
    async function loadCurriculum() {
      try {
        const res = await curriculumApi.getMyCurriculum();
        if (active) setCurriculumData(res);
      } catch {
        // No curriculum data: the sheet renders an em-dash, never a made-up major.
      }
    }
    void loadCurriculum();
    return () => {
      active = false;
    };
  }, []);

  const activeOption = useMemo(
    () => PURPOSE_OPTIONS.find((p) => p.id === selectedPurpose) || PURPOSE_OPTIONS[0],
    [selectedPurpose]
  );

  // Identity comes only from the signed-in session. When a field is missing we
  // render an em-dash — never another student's name or MSSV.
  const studentName = useMemo(() => {
    if (user?.firstName || user?.lastName) {
      return `${user.lastName ?? ''} ${user.firstName ?? ''}`.trim().toUpperCase();
    }
    return null;
  }, [user]);

  const studentId = user?.studentId || null;

  const cohort = useMemo(() => {
    if (!studentId) return null;
    const match = studentId.match(/^(\d{2})/);
    if (!match) return null;
    return `20${match[1]} (K${match[1]})`;
  }, [studentId]);

  const departmentName = curriculumData?.curriculum?.name || null;

  const today = useMemo(() => new Date(), []);

  const handlePrint = () => {
    window.print();
  };

  const genderValue = (() => {
    if (user?.gender === 'MALE') return certCopy.genderMale;
    if (user?.gender === 'FEMALE') return certCopy.genderFemale;
    if (user?.gender === 'OTHER') return certCopy.genderOther;
    return null;
  })();

  const missing = certCopy.missingValue;

  if (authLoading) {
    return (
      <div className="min-h-screen py-6 px-4 max-w-7xl mx-auto">
        <LoadingState label={messages.common.states.loadingContent} />
      </div>
    );
  }
  if (!hasAccess) {
    return <WorkspaceForbiddenState />;
  }

  return (
    <div className="min-h-screen py-6 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto">
      {/* Header Banner - Screen Only */}
      <div className="print:hidden mb-8 border-b border-border pb-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-md text-xs font-semibold bg-amber-50 text-amber-800 dark:bg-amber-950/40 dark:text-amber-300 border border-amber-200 dark:border-amber-800 mb-2">
              <TriangleAlert className="h-3.5 w-3.5" />
              <span>{certCopy.serviceBadge}</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
              {certCopy.pageTitle}
            </h1>
            <p className="mt-1 text-sm text-muted-foreground max-w-3xl">
              {certCopy.pageDescription}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <Button
              onClick={handlePrint}
              className="bg-primary hover:bg-primary/90 text-primary-foreground shadow-sm flex items-center gap-2 font-medium"
            >
              <Printer className="h-4 w-4" />
              <span>{certCopy.printAction}</span>
            </Button>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
        {/* Purpose Selector Panel - Screen Only */}
        <div className="print:hidden lg:col-span-5 space-y-6">
          <div className="bg-card border border-border rounded-xl p-5 shadow-sm">
            <h2 className="text-base font-semibold text-foreground flex items-center gap-2 mb-3">
              <Award className="h-4 w-4 text-primary" />
              {certCopy.selectPurposeTitle}
            </h2>
            <div className="space-y-2.5">
              {PURPOSE_OPTIONS.map((opt) => {
                const Icon = opt.icon;
                const isSelected = opt.id === selectedPurpose;
                return (
                  <button
                    key={opt.id}
                    type="button"
                    onClick={() => setSelectedPurpose(opt.id)}
                    className={`w-full text-left p-3.5 rounded-lg border transition-all flex items-start gap-3.5 ${
                      isSelected
                        ? 'border-primary bg-primary/5 ring-1 ring-primary/20 shadow-sm'
                        : 'border-border/70 hover:border-border hover:bg-accent/40'
                    }`}
                  >
                    <div
                      className={`p-2 rounded-md shrink-0 mt-0.5 ${
                        isSelected ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground'
                      }`}
                    >
                      <Icon className="h-4 w-4" />
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-sm font-semibold text-foreground flex items-center justify-between">
                        <span>{isVi ? opt.titleVi : opt.titleEn}</span>
                        {isSelected && <Check className="h-4 w-4 text-primary shrink-0 ml-2" />}
                      </div>
                      <p className="text-xs text-muted-foreground mt-0.5 line-clamp-2">
                        {isVi ? opt.decreeVi : opt.decreeEn}
                      </p>
                    </div>
                  </button>
                );
              })}
            </div>
          </div>

          <div className="bg-card border border-border rounded-xl p-5 shadow-sm space-y-4">
            <h2 className="text-base font-semibold text-foreground flex items-center gap-2">
              <Building className="h-4 w-4 text-primary" />
              {certCopy.recipientTitle}
            </h2>
            <div>
              <label htmlFor="custom-recipient" className="block text-xs font-medium text-muted-foreground mb-1.5">
                {certCopy.recipientCustomLabel}
              </label>
              <textarea
                id="custom-recipient"
                rows={2}
                value={customRecipient}
                onChange={(e) => setCustomRecipient(e.target.value)}
                placeholder={isVi ? activeOption.defaultRecipientVi : activeOption.defaultRecipientEn}
                className="w-full text-xs sm:text-sm p-3 rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <p className="text-[11px] text-muted-foreground mt-1">{certCopy.recipientHelper}</p>
            </div>
          </div>

          <div className="bg-muted/40 border border-border/80 rounded-xl p-4 text-xs space-y-2 text-muted-foreground">
            <div className="flex items-start gap-2">
              <Info className="h-4 w-4 text-primary shrink-0 mt-0.5" />
              <p>
                <strong className="text-foreground">{certCopy.issuedByLabel} </strong>
                {certCopy.issuedByValue}. {certCopy.issuedByNote}
              </p>
            </div>
          </div>
        </div>

        {/* Printable Preview Document Sheet */}
        <div className="lg:col-span-7">
          <div className="bg-white text-slate-900 border border-slate-200 rounded-xl shadow-md p-6 sm:p-10 font-serif leading-relaxed text-sm print:p-0 print:border-none print:shadow-none print:m-0 print:w-full">
            {/* Honest preview banner: the first thing any reader sees, on screen
                and on paper. This page never mints an official document. */}
            <div className="mb-6 rounded-lg border-2 border-dashed border-amber-500 bg-amber-50 px-4 py-3 text-center font-sans">
              <p className="text-sm sm:text-base font-black uppercase tracking-wide text-amber-800">
                {certCopy.previewBanner}
              </p>
              <p className="mt-1 text-[11px] sm:text-xs text-amber-700">{certCopy.previewBannerNote}</p>
            </div>

            {/* Header: National Header & University Header */}
            <div className="grid grid-cols-2 gap-4 pb-6 border-b border-slate-300">
              <div className="text-center font-sans">
                <p className="text-[11px] uppercase tracking-wider font-medium text-slate-600">
                  {certCopy.nationalHeader}
                </p>
                <p className="text-xs sm:text-sm font-bold uppercase text-slate-900 mt-0.5">
                  {certCopy.universityHeader}
                </p>
                <div className="w-16 h-[1.5px] bg-slate-900 mx-auto my-1"></div>
                <p className="text-[11px] font-mono text-slate-600 mt-1">
                  {certCopy.serialLabel} {missing}
                </p>
              </div>

              <div className="text-center font-sans">
                <p className="text-xs sm:text-sm font-bold uppercase text-slate-900">
                  {certCopy.socialistHeader}
                </p>
                <p className="text-xs sm:text-sm font-semibold text-slate-800 mt-0.5">
                  {certCopy.independenceHeader}
                </p>
                <div className="w-24 h-[1.5px] bg-slate-900 mx-auto my-1"></div>
                <p className="text-[11px] italic text-slate-600 mt-1">
                  {isVi
                    ? `Thành phố Hồ Chí Minh, ngày ${today.getDate()} tháng ${today.getMonth() + 1} năm ${today.getFullYear()}`
                    : `Ho Chi Minh City, ${formatDate(today, { month: 'long', day: 'numeric', year: 'numeric' })}`}
                </p>
              </div>
            </div>

            {/* Document Title */}
            <div className="text-center my-6">
              <h2 className="text-xl sm:text-2xl font-bold uppercase text-slate-950 font-sans tracking-wide">
                {certCopy.docTitle}
              </h2>
              <p className="text-xs text-slate-600 italic font-sans mt-1">{certCopy.docSubtitle}</p>
            </div>

            {/* Document Body */}
            <div className="space-y-4 my-6 text-[13px] sm:text-sm text-slate-800">
              <p className="font-semibold">{certCopy.declarationIntro}</p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-y-2 pt-1 font-sans">
                <div>
                  <span className="text-slate-600">{certCopy.nameLabel}</span>
                  <strong className="text-slate-950">{studentName || missing}</strong>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.idLabel}</span>
                  <strong className="text-slate-950 font-mono">{studentId || missing}</strong>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.dobLabel}</span>
                  <span>{user?.dateOfBirth ? formatDate(user.dateOfBirth) : missing}</span>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.genderLabel}</span>
                  <span>{genderValue || missing}</span>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.cohortLabel}</span>
                  <strong className="text-slate-900">{cohort || missing}</strong>
                </div>
                <div>
                  <span className="text-slate-600">{certCopy.programLevelLabel}</span>
                  <strong className="text-slate-900">{certCopy.programLevelValue}</strong>
                </div>
                <div className="sm:col-span-2">
                  <span className="text-slate-600">{certCopy.majorLabel}</span>
                  <strong className="text-slate-900">{departmentName || missing}</strong>
                </div>
              </div>

              {/* No academic-standing claim: the session supplies no enrollment /
                  discipline field, so the draft asserts nothing about standing. */}

              <div className="pt-3 border-t border-slate-200 space-y-2">
                <p>
                  <strong className="text-slate-950">{certCopy.purposeLabel} </strong>
                  <span>{isVi ? activeOption.bodyVi : activeOption.bodyEn}</span>
                </p>
                <p className="text-xs italic text-slate-600">
                  <span>{certCopy.legalBasisLabel} </span>
                  {isVi ? activeOption.decreeVi : activeOption.decreeEn}
                </p>
                <p>
                  <strong className="text-slate-950">{certCopy.recipientLineLabel} </strong>
                  <span>{customRecipient.trim() || (isVi ? activeOption.defaultRecipientVi : activeOption.defaultRecipientEn)}</span>
                </p>
              </div>
            </div>

            {/* Issuance Guidance (replaces the simulated QR / signature area) */}
            <div className="grid grid-cols-2 gap-6 pt-6 border-t border-slate-300 items-start">
              <div className="space-y-3 font-sans">
                <div>
                  <p className="text-[11px] font-bold uppercase text-slate-800">{certCopy.receiverTitle}</p>
                  <p className="text-[10px] text-slate-600 leading-tight">{certCopy.receiverFirst}</p>
                  <p className="text-[10px] text-slate-600 leading-tight">{certCopy.receiverSecond}</p>
                </div>
              </div>

              <div className="font-sans space-y-2 text-center">
                <p className="text-[11px] font-bold uppercase text-slate-800 leading-tight">
                  {certCopy.issuedByLabel}
                </p>
                <p className="text-xs font-bold uppercase text-slate-900 leading-tight">
                  {certCopy.issuedByValue}
                </p>
                <p className="text-[10px] italic text-slate-600 leading-snug">{certCopy.issuedByNote}</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
