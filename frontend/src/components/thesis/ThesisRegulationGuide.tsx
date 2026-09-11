'use client';

import { useState } from 'react';
import {
  Award,
  BookOpen,
  CalendarDays,
  CheckCircle,
  ChevronDown,
  ChevronUp,
  FileCheck2,
  FileText,
  GraduationCap,
  Layers,
  Scale,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  UsersRound,
} from 'lucide-react';
import { cn } from '@/lib/utils';

export function ThesisRegulationGuide() {
  const [isOpen, setIsOpen] = useState(false);
  const [activeRuleId, setActiveRuleId] = useState<string>('R1');

  const rules = [
    {
      id: 'R1',
      number: 'Điều R1',
      title: 'Phân Loại 4 Đợt Đăng Ký & Các Mốc Thời Gian Bắt Buộc',
      badge: 'Đợt & Kế hoạch',
      icon: CalendarDays,
      content: (
        <div className="space-y-3 text-xs leading-relaxed text-foreground/90">
          <p>
            Trưởng khoa phê duyệt và ban hành đợt đăng ký đề tài cho <strong>4 loại hình</strong> chính thức của Khoa Công nghệ Thông tin:
          </p>
          <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 pt-1">
            <div className="rounded-lg border border-primary/20 bg-primary/[0.03] p-3">
              <span className="font-bold text-primary">1. Khóa luận tốt nghiệp (KLTN):</span>
              <p className="mt-1 text-muted-foreground">
                Hình thức tốt nghiệp cao nhất. Bắt buộc cấu hình đầy đủ: Thời gian GV nộp đề tài, thời gian SV đăng ký, <strong>hạn chót GVPB nộp điểm về Khoa</strong>, và <strong>ngày báo cáo Hội đồng đánh giá</strong>.
              </p>
            </div>
            <div className="rounded-lg border border-primary/20 bg-primary/[0.03] p-3">
              <span className="font-bold text-primary">2. Tiểu luận chuyên ngành (TLCN):</span>
              <p className="mt-1 text-muted-foreground">
                Học phần chuẩn bị tốt nghiệp. Bắt buộc có <strong>hạn chót GVPB nộp điểm về Khoa</strong>.
              </p>
            </div>
            <div className="rounded-lg border border-border/80 bg-muted/20 p-3">
              <span className="font-bold text-foreground">3. Nghiên cứu khoa học (NCKH):</span>
              <p className="mt-1 text-muted-foreground">
                Đề tài nghiên cứu ứng dụng chuyên sâu dành cho sinh viên và giảng viên.
              </p>
            </div>
            <div className="rounded-lg border border-border/80 bg-muted/20 p-3">
              <span className="font-bold text-foreground">4. Đồ án môn học (MON_HOC):</span>
              <p className="mt-1 text-muted-foreground">
                Đồ án thực hành tích hợp các học phần chuyên môn trong chương trình đào tạo.
              </p>
            </div>
          </div>
        </div>
      ),
    },
    {
      id: 'R2',
      number: 'Điều R2',
      title: 'Quy Trình 2 Giai Đoạn Độc Lập',
      badge: 'Trình tự triển khai',
      icon: Layers,
      content: (
        <div className="space-y-2 text-xs leading-relaxed text-foreground/90">
          <p>
            Mỗi đợt đề tài bắt buộc tuân thủ <strong>2 giai đoạn riêng biệt</strong> với khung thời gian độc lập:
          </p>
          <ul className="list-disc pl-5 space-y-1 text-muted-foreground">
            <li>
              <strong className="text-foreground">Giai đoạn 1 (Xây dựng & Công bố đề tài):</strong> Giảng viên bộ môn nộp đề xuất đề tài; Hội đồng khoa học bộ môn thẩm định tính khoa học, độ trùng lặp và công bố danh sách đề tài chính thức.
            </li>
            <li>
              <strong className="text-foreground">Giai đoạn 2 (Nhóm SV đăng ký đề tài):</strong> Nhóm sinh viên đăng ký 1 đề tài cụ thể trong danh sách đã công bố. Đăng ký chỉ được thực hiện trong thời gian quy định của giai đoạn này.
            </li>
          </ul>
        </div>
      ),
    },
    {
      id: 'R3',
      number: 'Điều R3',
      title: 'Quản Lý Đề Tài Theo Từng Bộ Môn & 1–2 Giảng Viên Hướng Dẫn',
      badge: 'Chuyên môn & GVHD',
      icon: BookOpen,
      content: (
        <div className="space-y-2 text-xs leading-relaxed text-foreground/90">
          <p>
            Đề tài được quản lý chặt chẽ theo từng Bộ môn trực thuộc Khoa CNTT (Khoa học máy tính, Kỹ thuật dữ liệu, Kỹ thuật phần mềm, Hệ thống thông tin, Mạng máy tính & An ninh thông tin):
          </p>
          <ul className="list-disc pl-5 space-y-1 text-muted-foreground">
            <li>Mỗi đề tài thuộc 1 bộ môn cụ thể, có đề cương và yêu cầu kết quả rõ ràng.</li>
            <li>
              Được hướng dẫn bởi <strong className="text-foreground">ít nhất 1 và tối đa 2 Giảng viên</strong> (GVHD 1 và GVHD 2).
            </li>
            <li>Giảng viên hướng dẫn chịu trách nhiệm đôn đốc tiến độ và chất lượng khoa học của đề tài.</li>
          </ul>
        </div>
      ),
    },
    {
      id: 'R4',
      number: 'Điều R4',
      title: 'Quy Định Nhóm Sinh Viên Thực Hiện & Phê Duyệt',
      badge: 'Cơ cấu nhóm',
      icon: UsersRound,
      content: (
        <div className="space-y-2 text-xs leading-relaxed text-foreground/90">
          <ul className="list-disc pl-5 space-y-1.5 text-muted-foreground">
            <li>
              Thực hiện theo nhóm, <strong className="text-foreground">tối đa 3 sinh viên</strong>, có <strong className="text-foreground">1 nhóm trưởng (Leader)</strong> đại diện.
            </li>
            <li>
              Mỗi sinh viên <strong className="text-destructive font-semibold">chỉ tham gia duy nhất 1 nhóm</strong> trong toàn bộ đợt; không được đứng tên 2 nhóm cùng lúc.
            </li>
            <li>
              Mỗi nhóm <strong className="text-foreground">chỉ đăng ký duy nhất 1 đề tài</strong> từ danh sách đã công bố.
            </li>
            <li>
              Sau khi sinh viên nộp nguyện vọng, Giảng viên hướng dẫn sẽ xem xét và <strong>phê duyệt (Approve)</strong> hoặc <strong>từ chối (Reject kèm lý do)</strong>. Khi được duyệt, nhóm chính thức thực hiện đề tài.
            </li>
          </ul>
        </div>
      ),
    },
    {
      id: 'R5',
      number: 'Điều R5',
      title: 'Quy Chế Nộp Báo Cáo: Chỉ Nhóm Trưởng Nộp Báo Cáo',
      badge: 'Trách nhiệm nộp',
      icon: FileText,
      content: (
        <div className="space-y-2 text-xs leading-relaxed text-foreground/90">
          <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-amber-800 dark:text-amber-300">
            <strong>Nguyên tắc Điều R5:</strong> Việc nộp báo cáo đề tài (tập tin PDF, liên kết Google Drive, OneDrive, slide báo cáo, mã nguồn) <strong>CHỈ DO NHÓM TRƯỞNG</strong> đại diện nhóm thực hiện trên hệ thống.
          </div>
          <p className="text-muted-foreground">
            Các thành viên trong nhóm có quyền truy cập để xem báo cáo đã nộp, thời gian nộp và ghi chú của nhóm trưởng.
          </p>
        </div>
      ),
    },
    {
      id: 'R6',
      number: 'Điều R6',
      title: 'Hội Đồng Phản Biện & Cơ Cấu Thành Viên',
      badge: 'Hội đồng bảo vệ',
      icon: UserCheck,
      content: (
        <div className="space-y-2 text-xs leading-relaxed text-foreground/90">
          <ul className="list-disc pl-5 space-y-1 text-muted-foreground">
            <li>
              Khoa thành lập các Hội đồng đánh giá luận văn, <strong className="text-foreground">mỗi hội đồng gồm từ 3 đến 5 Giảng viên</strong>.
            </li>
            <li>
              Cơ cấu hội đồng bắt buộc gồm: <strong className="text-foreground">1 Chủ tịch</strong>, <strong className="text-foreground">1 Thư ký</strong>, và các <strong className="text-foreground">Ủy viên</strong>.
            </li>
            <li>
              Chủ tịch hội đồng chủ trì phiên bảo vệ, tổng hợp đánh giá và điểm số của tất cả các thành viên để ra kết quả cuối cùng.
            </li>
          </ul>
        </div>
      ),
    },
    {
      id: 'R7',
      number: 'Điều R7',
      title: 'Công Thức Điểm Số: Trung Bình Cộng Điểm Thành Phần',
      badge: 'Công thức tính điểm',
      icon: Scale,
      content: (
        <div className="space-y-2 text-xs leading-relaxed text-foreground/90">
          <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-emerald-800 dark:text-emerald-300">
            <strong>Công thức tính:</strong> Điểm cuối cùng của đề tài = <strong>Trung bình cộng số học</strong> của các điểm thành phần hợp lệ do các thành viên Hội đồng chấm (thang điểm 10, làm tròn đến 2 chữ số thập phân).
          </div>
          <p className="text-muted-foreground">
            Điểm số sau đó được quy đổi sang điểm chữ (A, B+, B, C+, C, D+, D, F) và xếp loại tốt nghiệp chính thức theo thang điểm tín chỉ của Trường ĐH Sư phạm Kỹ thuật TP.HCM.
          </p>
        </div>
      ),
    },
    {
      id: 'R8',
      number: 'Điều R8',
      title: 'Quy Tắc Loại Trừ Xung Đột Lợi Ích: GVHD Không Được Chấm Đề Tài Của Mình',
      badge: 'Công tâm & Minh bạch',
      icon: ShieldAlert,
      content: (
        <div className="space-y-2 text-xs leading-relaxed text-foreground/90">
          <div className="rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-destructive">
            <strong>Quy tắc bất biến:</strong> Giảng viên được tham gia chấm nhiều đề tài khác nhau trong hội đồng, nhưng <strong className="underline">TUYỆT ĐỐI KHÔNG ĐƯỢC CHẤM</strong> đề tài mà mình đang làm Giảng viên hướng dẫn (GVHD).
          </div>
          <p className="text-muted-foreground">
            Hệ thống tự động phát hiện và khóa chức năng nhập điểm của GVHD đối với đề tài đó, đảm bảo 100% tính khách quan, công bằng và tuân thủ chuẩn kiểm định chất lượng giáo dục đại học.
          </p>
        </div>
      ),
    },
    {
      id: 'R9',
      number: 'Điều R9',
      title: 'Công Bố & Tra Cứu Kết Quả Công Khai',
      badge: 'Tra cứu điểm',
      icon: Award,
      content: (
        <div className="space-y-2 text-xs leading-relaxed text-foreground/90">
          <p>
            Sau khi phiên phản biện kết thúc và Chủ tịch hội đồng chốt điểm:
          </p>
          <ul className="list-disc pl-5 space-y-1 text-muted-foreground">
            <li>Kết quả đánh giá và điểm số được <strong>công bố công khai</strong> trên cổng học vụ.</li>
            <li>Sinh viên tra cứu chi tiết điểm số thang 10, điểm chữ, xếp loại tốt nghiệp và các nhận xét đóng góp từ Hội đồng.</li>
          </ul>
        </div>
      ),
    },
  ];

  const currentRule = rules.find((r) => r.id === activeRuleId) || rules[0];

  return (
    <div className="rounded-2xl border border-border/80 bg-card text-card-foreground shadow-xs overflow-hidden">
      {/* Banner Header */}
      <button
        type="button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex w-full items-center justify-between gap-4 p-5 sm:p-6 text-left transition-colors hover:bg-muted/30 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
      >
        <div className="flex items-center gap-3">
          <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary border border-primary/20">
            <GraduationCap className="h-5 w-5" />
          </div>
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-full bg-primary/10 px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wider text-primary border border-primary/20">
                Quy Chế Đào Tạo HCMUTE
              </span>
              <span className="text-xs text-muted-foreground font-medium">
                Khoa Công Nghệ Thông Tin
              </span>
            </div>
            <h3 className="mt-1 text-base font-bold text-foreground sm:text-lg">
              Cẩm Nang Quy Chế Đề Tài & Khóa Luận Tốt Nghiệp (Quy Định R1 — R13)
            </h3>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <span className="text-xs font-semibold text-primary hidden sm:inline-block">
            {isOpen ? 'Thu gọn cẩm nang' : 'Mở xem 9 điều quy chế'}
          </span>
          <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-border/70 bg-background text-muted-foreground">
            {isOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </div>
        </div>
      </button>

      {/* Expandable Accordion Body */}
      {isOpen && (
        <div className="border-t border-border/60 bg-muted/10 p-5 sm:p-6">
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[280px_1fr]">
            {/* Rule Selector List */}
            <div className="space-y-1.5">
              <p className="px-2 pb-1 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                Danh Mục Điều Khoản
              </p>
              {rules.map((rule) => {
                const isSelected = rule.id === activeRuleId;
                const RuleIcon = rule.icon;
                return (
                  <button
                    key={rule.id}
                    type="button"
                    onClick={() => setActiveRuleId(rule.id)}
                    className={cn(
                      'flex w-full items-center gap-2.5 rounded-lg px-3 py-2 text-left text-xs transition-colors border',
                      isSelected
                        ? 'border-primary bg-primary/10 font-bold text-primary shadow-2xs'
                        : 'border-transparent text-muted-foreground hover:bg-card hover:text-foreground',
                    )}
                  >
                    <RuleIcon className={cn('h-4 w-4 shrink-0', isSelected ? 'text-primary' : 'text-muted-foreground')} />
                    <div className="min-w-0 flex-1 truncate">
                      <span className="font-semibold mr-1.5">{rule.number}:</span>
                      <span>{rule.title}</span>
                    </div>
                  </button>
                );
              })}
            </div>

            {/* Selected Rule Detail Card */}
            <div className="rounded-xl border border-border/80 bg-card p-5 shadow-2xs">
              <div className="flex flex-wrap items-center justify-between gap-2 border-b border-border/50 pb-3">
                <div className="flex items-center gap-2">
                  <span className="rounded-md bg-primary/10 px-2 py-0.5 text-xs font-bold text-primary font-mono">
                    {currentRule.number}
                  </span>
                  <h4 className="text-sm font-bold text-foreground sm:text-base">
                    {currentRule.title}
                  </h4>
                </div>
                <span className="rounded-full bg-muted px-2.5 py-0.5 text-[11px] font-semibold text-muted-foreground">
                  {currentRule.badge}
                </span>
              </div>

              <div className="mt-4">{currentRule.content}</div>

              <div className="mt-5 flex items-center gap-2 border-t border-border/40 pt-3 text-[11px] text-muted-foreground">
                <ShieldCheck className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
                <span>Quy chuẩn hành chính & học thuật được áp dụng tự động trong toàn bộ hệ thống CampusUTE.</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
