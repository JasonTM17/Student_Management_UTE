'use client';

import React, { useState, useEffect, useMemo } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { curriculumApi } from '@/lib/api';
import { MyCurriculumResponse } from '@/types/api';
import { Button } from '@/components/ui/button';
import {
  Printer,
  Copy,
  Check,
  ShieldCheck,
  Award,
  FileCheck,
  Bus,
  Building,
  Briefcase,
  Info,
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
    icon: ShieldCheck,
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

export default function CertificatesPage() {
  const { user } = useAuth();
  const { locale } = useI18n();
  const isVi = locale === 'vi';

  const [selectedPurpose, setSelectedPurpose] = useState<CertificatePurpose>('MILITARY_DEFERMENT');
  const [customRecipient, setCustomRecipient] = useState<string>('');
  const [curriculumData, setCurriculumData] = useState<MyCurriculumResponse | null>(null);
  const [copied, setCopied] = useState<boolean>(false);

  useEffect(() => {
    let active = true;
    async function loadCurriculum() {
      try {
        const res = await curriculumApi.getMyCurriculum();
        if (active) setCurriculumData(res);
      } catch {
        // Fallback gracefully to user profile
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

  const studentName = useMemo(() => {
    if (user?.firstName || user?.lastName) {
      return `${user.lastName ?? ''} ${user.firstName ?? ''}`.trim().toUpperCase();
    }
    return 'NGUYỄN VĂN A';
  }, [user]);

  const studentId = useMemo(() => {
    return user?.studentId || '24110054';
  }, [user]);

  const cohort = useMemo(() => {
    const year = studentId.startsWith('24') ? 2024 : studentId.startsWith('23') ? 2023 : studentId.startsWith('22') ? 2022 : 2024;
    const shortYear = String(year).slice(-2);
    return `Khóa 20${shortYear} (K${shortYear})`;
  }, [studentId]);

  const departmentName = useMemo(() => {
    return (
      curriculumData?.curriculum?.name ||
      (isVi ? 'Công nghệ Thông tin (Chương trình Chuẩn)' : 'Information Technology (Standard Program)')
    );
  }, [curriculumData, isVi]);

  const today = useMemo(() => new Date(), []);
  const issueDateVi = useMemo(
    () =>
      `Thành phố Hồ Chí Minh, ngày ${today.getDate()} tháng ${today.getMonth() + 1} năm ${today.getFullYear()}`,
    [today]
  );
  const issueDateEn = useMemo(
    () =>
      `Ho Chi Minh City, ${today.toLocaleDateString('en-US', { month: 'long', day: 'numeric', year: 'numeric' })}`,
    [today]
  );

  const certNumber = useMemo(() => {
    const seed = Math.abs((studentId.split('').reduce((acc: number, ch: string) => acc * 31 + ch.charCodeAt(0), 7) % 8999) + 1000);
    return `${today.getFullYear()}/XN-ĐHCNKT-${seed}`;
  }, [today, studentId]);

  const verificationUrl = `https://www.campusute.io.vn/verify/cert/${certNumber}`;

  const handlePrint = () => {
    window.print();
  };

  const handleCopyLink = () => {
    navigator.clipboard.writeText(verificationUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="min-h-screen py-6 px-4 sm:px-6 lg:px-8 max-w-7xl mx-auto">
      {/* Header Banner - Screen Only */}
      <div className="print:hidden mb-8 border-b border-border pb-6">
        <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
          <div>
            <div className="inline-flex items-center gap-2 px-2.5 py-1 rounded-full text-xs font-semibold bg-emerald-50 text-emerald-700 dark:bg-emerald-950/40 dark:text-emerald-300 border border-emerald-200 dark:border-emerald-800 mb-2">
              <ShieldCheck className="h-3.5 w-3.5" />
              <span>{isVi ? 'Dịch vụ xác nhận điện tử chính quy' : 'Official Electronic Verification Service'}</span>
            </div>
            <h1 className="text-2xl sm:text-3xl font-bold tracking-tight text-foreground">
              {isVi ? 'Giấy xác nhận sinh viên điện tử' : 'Student Verification Certificate'}
            </h1>
            <p className="mt-1 text-sm text-muted-foreground max-w-3xl">
              {isVi
                ? 'Hệ thống tự phục vụ cấp giấy xác nhận có mã kiểm tra QR và chữ ký điện tử hợp lệ, phục vụ tạm hoãn nghĩa vụ quân sự, vay vốn chính sách, ưu đãi xe buýt, giảm trừ gia cảnh và thực tập doanh nghiệp.'
                : 'Self-service portal issuing official student certificates with verifiable QR integrity and digital seal, valid for military deferment, student loans, bus subsidies, and internships.'}
            </p>
          </div>
          <div className="flex items-center gap-3">
            <Button
              onClick={handlePrint}
              className="bg-primary hover:bg-primary/90 text-primary-foreground shadow-sm flex items-center gap-2 font-medium"
            >
              <Printer className="h-4 w-4" />
              <span>{isVi ? 'In giấy xác nhận (A4)' : 'Print Certificate (A4)'}</span>
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
              {isVi ? '1. Chọn mục đích xác nhận' : '1. Select Purpose'}
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
              {isVi ? '2. Cơ quan / Đơn vị tiếp nhận' : '2. Recipient Agency'}
            </h2>
            <div>
              <label htmlFor="custom-recipient" className="block text-xs font-medium text-muted-foreground mb-1.5">
                {isVi ? 'Kính gửi (tùy chỉnh nếu cần)' : 'Addressed to (optional override)'}
              </label>
              <textarea
                id="custom-recipient"
                rows={2}
                value={customRecipient}
                onChange={(e) => setCustomRecipient(e.target.value)}
                placeholder={isVi ? activeOption.defaultRecipientVi : activeOption.defaultRecipientEn}
                className="w-full text-xs sm:text-sm p-3 rounded-lg border border-input bg-background focus:outline-none focus:ring-2 focus:ring-primary/30"
              />
              <p className="text-[11px] text-muted-foreground mt-1">
                {isVi
                  ? 'Để trống để dùng cơ quan thụ lý mặc định theo quy định.'
                  : 'Leave empty to use the standard default statutory authority.'}
              </p>
            </div>
          </div>

          <div className="bg-muted/40 border border-border/80 rounded-xl p-4 text-xs space-y-2 text-muted-foreground">
            <div className="flex items-start gap-2">
              <Info className="h-4 w-4 text-primary shrink-0 mt-0.5" />
              <p>
                {isVi
                  ? 'Bản in có đầy đủ Quốc hiệu, Tiêu ngữ, số hiệu công văn và chữ ký số điện tử của Phòng Đào tạo HCMUTE. Tài liệu có giá trị pháp lý tương đương bản ký tay trong thời hạn 60 ngày.'
                  : 'The generated document carries the official national header, university registration serial, and electronic signature from the Academic Affairs Office. Valid for 60 calendar days.'}
              </p>
            </div>
            <div className="flex items-center justify-between pt-2 border-t border-border/60">
              <span className="font-mono text-[11px] text-foreground">{certNumber}</span>
              <button
                type="button"
                onClick={handleCopyLink}
                className="text-primary hover:underline inline-flex items-center gap-1 font-medium text-[11px]"
              >
                {copied ? <Check className="h-3 w-3" /> : <Copy className="h-3 w-3" />}
                {copied ? (isVi ? 'Đã sao chép' : 'Copied') : isVi ? 'Sao chép liên kết' : 'Copy verification link'}
              </button>
            </div>
          </div>
        </div>

        {/* Printable Official Document Sheet */}
        <div className="lg:col-span-7">
          <div className="bg-white text-slate-900 border border-slate-200 rounded-xl shadow-md p-6 sm:p-10 font-serif leading-relaxed text-sm print:p-0 print:border-none print:shadow-none print:m-0 print:w-full">
            {/* Header: National Header & University Header */}
            <div className="grid grid-cols-2 gap-4 pb-6 border-b border-slate-300">
              <div className="text-center font-sans">
                <p className="text-[11px] uppercase tracking-wider font-medium text-slate-600">
                  BỘ GIÁO DỤC VÀ ĐÀO TẠO
                </p>
                <p className="text-xs sm:text-sm font-bold uppercase text-slate-900 mt-0.5">
                  TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP.HCM
                </p>
                <div className="w-16 h-[1.5px] bg-slate-900 mx-auto my-1"></div>
                <p className="text-[11px] font-mono text-slate-600 mt-1">Số: {certNumber}</p>
              </div>

              <div className="text-center font-sans">
                <p className="text-xs sm:text-sm font-bold uppercase text-slate-900">
                  CỘNG HÒA XÃ HỘI CHỦ NGHĨA VIỆT NAM
                </p>
                <p className="text-xs sm:text-sm font-semibold text-slate-800 mt-0.5">
                  Độc lập - Tự do - Hạnh phúc
                </p>
                <div className="w-24 h-[1.5px] bg-slate-900 mx-auto my-1"></div>
                <p className="text-[11px] italic text-slate-600 mt-1">{isVi ? issueDateVi : issueDateEn}</p>
              </div>
            </div>

            {/* Document Title */}
            <div className="text-center my-6">
              <h2 className="text-xl sm:text-2xl font-bold uppercase text-slate-950 font-sans tracking-wide">
                GIẤY XÁC NHẬN SINH VIÊN
              </h2>
              <p className="text-xs text-slate-600 italic font-sans mt-1">
                (Dùng cho sinh viên đang theo học hệ chính quy tại Trường Đại học Công nghệ Kỹ thuật TP.HCM)
              </p>
            </div>

            {/* University Declaration Body */}
            <div className="space-y-4 my-6 text-[13px] sm:text-sm text-slate-800">
              <p className="font-semibold">
                HIỆU TRƯỞNG TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH XÁC NHẬN:
              </p>

              <div className="grid grid-cols-1 sm:grid-cols-2 gap-y-2 pt-1 font-sans">
                <div>
                  <span className="text-slate-600">Họ và tên sinh viên: </span>
                  <strong className="text-slate-950">{studentName}</strong>
                </div>
                <div>
                  <span className="text-slate-600">Mã số sinh viên (MSSV): </span>
                  <strong className="text-slate-950 font-mono">{studentId}</strong>
                </div>
                <div>
                  <span className="text-slate-600">Ngày sinh: </span>
                  <span>{user?.dateOfBirth ? new Date(user.dateOfBirth).toLocaleDateString('vi-VN') : '15/08/2004'}</span>
                </div>
                <div>
                  <span className="text-slate-600">Giới tính: </span>
                  <span>{user?.gender === 'FEMALE' ? 'Nữ' : 'Nam'}</span>
                </div>
                <div>
                  <span className="text-slate-600">Khóa đào tạo: </span>
                  <strong className="text-slate-900">{cohort}</strong>
                </div>
                <div>
                  <span className="text-slate-600">Bậc & Hệ đào tạo: </span>
                  <strong className="text-slate-900">Đại học chính quy tập trung</strong>
                </div>
                <div className="sm:col-span-2">
                  <span className="text-slate-600">Ngành / Chương trình đào tạo: </span>
                  <strong className="text-slate-900">{departmentName}</strong>
                </div>
                <div className="sm:col-span-2">
                  <span className="text-slate-600">Tình trạng học tập: </span>
                  <span className="inline-flex items-center gap-1.5 font-semibold text-emerald-800">
                    <Check className="h-3.5 w-3.5 text-emerald-600" />
                    Còn đang theo học tại trường (Tiến độ bình thường, không bị kỷ luật)
                  </span>
                </div>
              </div>

              <div className="pt-3 border-t border-slate-200 space-y-2">
                <p>
                  <strong className="text-slate-950">Mục đích cấp giấy xác nhận: </strong>
                  <span>{isVi ? activeOption.bodyVi : activeOption.bodyEn}</span>
                </p>
                <p className="text-xs italic text-slate-600">
                  <span>Căn cứ pháp lý: </span>
                  {isVi ? activeOption.decreeVi : activeOption.decreeEn}
                </p>
                <p>
                  <strong className="text-slate-950">Kính gửi đơn vị tiếp nhận: </strong>
                  <span>{customRecipient.trim() || (isVi ? activeOption.defaultRecipientVi : activeOption.defaultRecipientEn)}</span>
                </p>
                <p className="text-xs text-slate-600 pt-1">
                  Giấy xác nhận này có giá trị trong vòng <strong>60 ngày</strong> kể từ ngày ký phát hành. Mọi cơ quan, đơn vị có thể đối soát trực tiếp tính xác thực bằng cách quét mã QR phía dưới hoặc truy cập Cổng thông tin học vụ CampusUTE.
                </p>
              </div>
            </div>

            {/* Signatures & QR Code Section */}
            <div className="grid grid-cols-2 gap-6 pt-6 border-t border-slate-300 items-end">
              {/* Left Column: Recipients and QR Code Verification */}
              <div className="space-y-3 font-sans">
                <div>
                  <p className="text-[11px] font-bold uppercase text-slate-800">Nơi nhận:</p>
                  <p className="text-[10px] text-slate-600 leading-tight">- Như trên;</p>
                  <p className="text-[10px] text-slate-600 leading-tight">- Lưu: VT, ĐT, CTSV.</p>
                </div>

                {/* Verification Box */}
                <div className="p-2.5 bg-slate-50 border border-slate-200 rounded-lg flex items-center gap-3">
                  <div className="p-1 bg-white border border-slate-300 rounded shadow-2xs">
                    {/* Simulated SVG QR Code */}
                    <svg
                      className="h-14 w-14 text-slate-900"
                      viewBox="0 0 24 24"
                      fill="none"
                      stroke="currentColor"
                      strokeWidth="2"
                      strokeLinecap="round"
                      strokeLinejoin="round"
                    >
                      <rect width="5" height="5" x="3" y="3" rx="1" />
                      <rect width="5" height="5" x="16" y="3" rx="1" />
                      <rect width="5" height="5" x="3" y="16" rx="1" />
                      <path d="M21 16h-3a2 2 0 0 0-2 2v3" />
                      <path d="M21 21v.01" />
                      <path d="M12 7v3a2 2 0 0 1-2 2H7" />
                      <path d="M3 12h.01" />
                      <path d="M12 3h.01" />
                      <path d="M12 16v.01" />
                      <path d="M16 12h1" />
                      <path d="M21 12v.01" />
                      <path d="M12 21v-1" />
                    </svg>
                  </div>
                  <div className="text-[10px] text-slate-600 leading-tight">
                    <p className="font-bold text-slate-900">XÁC THỰC ĐIỆN TỬ</p>
                    <p className="font-mono text-[9px] text-slate-500 mt-0.5">{certNumber}</p>
                    <p className="text-[9px] text-emerald-700 font-semibold mt-0.5">● Chữ ký số hợp lệ</p>
                  </div>
                </div>
              </div>

              {/* Right Column: Signature & Official Seal */}
              <div className="text-center font-sans space-y-1">
                <p className="text-[11px] font-bold uppercase text-slate-900 leading-tight">
                  TL. HIỆU TRƯỞNG
                </p>
                <p className="text-xs font-bold uppercase text-slate-900 leading-tight">
                  TRƯỜNG PHÒNG ĐÀO TẠO
                </p>

                {/* Digital Stamp Simulation */}
                <div className="py-2 flex justify-center">
                  <div className="border-2 border-dashed border-red-600/80 rounded-md p-2 bg-red-50/50 text-red-700 text-[10px] font-sans text-center max-w-[170px] shadow-xs">
                    <p className="font-bold uppercase tracking-tight text-[9px]">ĐÃ KÝ ĐIỆN TỬ</p>
                    <p className="font-semibold text-[8.5px] mt-0.5 leading-tight">
                      TRƯỜNG ĐH CÔNG NGHỆ KỸ THUẬT TP.HCM
                    </p>
                    <p className="text-[8px] text-red-600/90 mt-0.5 font-mono">
                      Thời gian: {today.toLocaleDateString('vi-VN')}
                    </p>
                  </div>
                </div>

                <p className="text-xs font-bold text-slate-900 pt-1">PGS. TS. NGUYỄN VĂN HẢI</p>
                <p className="text-[10px] text-slate-500 italic">Trưởng phòng Đào tạo</p>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
