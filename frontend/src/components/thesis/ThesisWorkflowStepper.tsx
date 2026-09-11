'use client';

import { useState, useMemo } from 'react';
import {
  BookOpen,
  CalendarClock,
  CheckCircle2,
  ChevronDown,
  ChevronUp,
  FileCheck,
  FileSpreadsheet,
  GraduationCap,
  Scale,
  ShieldCheck,
  Sparkles,
  Users,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { ThesisRound } from '@/lib/thesis-api';

interface ThesisWorkflowStepperProps {
  round: ThesisRound;
  formatDateTime: (value: string | number | Date) => string;
}

export function ThesisWorkflowStepper({ round, formatDateTime }: ThesisWorkflowStepperProps) {
  // Determine current active stage (1 to 5) based on round status & dates
  const activeStage = useMemo(() => {
    switch (round.status) {
      case 'DRAFT':
      case 'PROPOSAL_OPEN':
        return 1;
      case 'PROPOSALS_PUBLISHED':
        return 2;
      case 'REGISTRATION_OPEN':
        return 3;
      case 'REGISTRATION_CLOSED':
        return 4;
      case 'RESULTS_PUBLISHED':
      case 'CLOSED':
        return 5;
      default:
        return 3;
    }
  }, [round.status]);

  const [selectedStage, setSelectedStage] = useState<number>(activeStage);
  const [isDetailExpanded, setIsDetailExpanded] = useState<boolean>(true);

  const stages = useMemo(() => {
    return [
      {
        step: 1,
        title: 'GV Đề Xuất Đề Tài',
        shortDesc: 'GV bộ môn gửi đề xuất',
        role: 'Giảng viên Bộ môn (1-2 GVHD)',
        rule: 'Điều R1, R2, R3',
        status: activeStage > 1 ? 'completed' : activeStage === 1 ? 'active' : 'upcoming',
        timeline: `${formatDateTime(round.lecturerSubmitStart)} → ${formatDateTime(round.lecturerSubmitEnd)}`,
        icon: BookOpen,
        details: {
          objective: 'Giảng viên thuộc các Bộ môn xây dựng mục tiêu, yêu cầu công nghệ và chỉ tiêu số lượng nhóm cho từng đề tài.',
          actions: [
            'Mỗi đề tài thuộc 1 Bộ môn cụ thể và có từ 1 đến 2 Giảng viên hướng dẫn (GVHD chính & GVHD phối hợp).',
            'Giảng viên soạn thảo tóm tắt nội dung, công nghệ sử dụng, và số nhóm tối đa (1-20 nhóm).',
            'Đề tài được lưu dạng bản nháp (Draft) hoặc gửi lên Hội đồng Khoa phê duyệt.',
          ],
          deadlineNote: 'Hạn cuối nộp đề tài được quy định nghiêm ngặt theo thời gian biểu của Khoa.',
        },
      },
      {
        step: 2,
        title: 'Thẩm Định & Công Bố',
        shortDesc: 'Khoa xét duyệt & công bố',
        role: 'Trưởng Bộ môn & Khoa CNTT',
        rule: 'Điều R2, R3',
        status: activeStage > 2 ? 'completed' : activeStage === 2 ? 'active' : 'upcoming',
        timeline: round.proposalPublishAt ? formatDateTime(round.proposalPublishAt) : 'Trước ngày mở đăng ký SV',
        icon: FileSpreadsheet,
        details: {
          objective: 'Hội đồng Khoa học và Trưởng Bộ môn thẩm định tính khoa học, độ trùng lặp và tính khả thi của đề tài trước khi công bố.',
          actions: [
            'Trưởng bộ môn rà soát khối lượng kiến thức và tính khả thi đối với sinh viên đại học.',
            'Khoa ban hành danh mục đề tài chính thức được phê duyệt công khai trên cổng học vụ.',
            'Sinh viên có thể tra cứu toàn bộ danh mục đề tài theo từng bộ môn chuyên ngành.',
          ],
          deadlineNote: 'Sau khi công bố, danh mục đề tài sẽ sẵn sàng cho sinh viên đăng ký ở Giai đoạn 2.',
        },
      },
      {
        step: 3,
        title: 'Nhóm SV Đăng Ký',
        shortDesc: 'Lập nhóm ≤3 SV & chọn đề tài',
        role: 'Sinh viên & GVHD',
        rule: 'Điều R2, R4',
        status: activeStage > 3 ? 'completed' : activeStage === 3 ? 'active' : 'upcoming',
        timeline: `${formatDateTime(round.registrationStart)} → ${formatDateTime(round.registrationEnd)}`,
        icon: Users,
        details: {
          objective: 'Sinh viên thành lập nhóm nghiên cứu và đăng ký đúng 1 đề tài trong danh mục đã công bố.',
          actions: [
            'Mỗi nhóm tối đa 3 sinh viên, có đúng 1 Nhóm trưởng (Leader) đại diện.',
            'Mỗi sinh viên chỉ được tham gia duy nhất 1 nhóm trong toàn bộ đợt đăng ký.',
            'Mỗi nhóm chỉ đăng ký đúng 1 đề tài; Giảng viên hướng dẫn sẽ xét duyệt (Approve) hoặc từ chối (Reject kèm lý do).',
          ],
          deadlineNote: 'Chỉ được phép tạo nhóm và đăng ký trong khung giờ quy định của Giai đoạn 2.',
        },
      },
      {
        step: 4,
        title: 'Thực Hiện & Nộp Báo Cáo',
        shortDesc: 'Nghiên cứu & nộp báo cáo',
        role: 'Nhóm trưởng (Chỉ trưởng nhóm nộp)',
        rule: 'Điều R4, R5',
        status: activeStage > 4 ? 'completed' : activeStage === 4 ? 'active' : 'upcoming',
        timeline: round.gvpbDeadline ? `Hạn nộp báo cáo trước: ${formatDateTime(round.gvpbDeadline)}` : 'Trước ngày phản biện',
        icon: FileCheck,
        details: {
          objective: 'Nhóm sinh viên tiến hành nghiên cứu, viết báo cáo luận văn, xây dựng sản phẩm và nộp tài liệu nghiệm thu.',
          actions: [
            'Nhóm sinh viên làm việc thường xuyên dưới sự chỉ dẫn khoa học của Giảng viên hướng dẫn.',
            'Quy chế Điều R5: CHỈ NHÓM TRƯỞNG mới có quyền nộp hoặc cập nhật báo cáo luận văn (PDF/Google Drive/OneDrive).',
            'Giảng viên phản biện (GVPB) đọc báo cáo và chấm điểm phản biện nộp về Khoa.',
          ],
          deadlineNote: round.gvpbDeadline ? `Hạn chót GVPB nộp điểm về Khoa: ${formatDateTime(round.gvpbDeadline)}` : 'Theo kế hoạch đợt',
        },
      },
      {
        step: 5,
        title: 'Bảo Vệ & Chốt Điểm',
        shortDesc: 'Hội đồng 3-5 GV chấm bảo vệ',
        role: 'Hội đồng 3-5 GV & Chủ tịch',
        rule: 'Điều R6, R7, R8, R9',
        status: activeStage === 5 ? 'active' : 'upcoming',
        timeline: round.reportDate ? `Ngày báo cáo: ${formatDateTime(round.reportDate)}` : 'Theo lịch phân công',
        icon: GraduationCap,
        details: {
          objective: 'Sinh viên báo cáo trước Hội đồng đánh giá luận văn. Hội đồng chấm điểm, tổng hợp điểm trung bình và công bố kết quả.',
          actions: [
            'Hội đồng gồm 3 đến 5 giảng viên (1 Chủ tịch, 1 Thư ký, các Ủy viên).',
            'Quy tắc Điều R8: Giảng viên hướng dẫn KHÔNG ĐƯỢC CHẤM đề tài do chính mình hướng dẫn để đảm bảo tính khách quan.',
            'Quy tắc Điều R7: Điểm cuối cùng là TRUNG BÌNH CỘNG các điểm thành phần hợp lệ của thành viên hội đồng.',
            'Quy tắc Điều R9: Chủ tịch chốt điểm và Khoa công bố điểm số, xếp loại và nhận xét công khai trên hệ thống.',
          ],
          deadlineNote: 'Kết quả được lưu trữ chính thức vào hồ sơ tốt nghiệp đại học của sinh viên.',
        },
      },
    ];
  }, [round, activeStage, formatDateTime]);

  const currentStageInfo = stages.find((s) => s.step === selectedStage) || stages[activeStage - 1] || stages[0];

  return (
    <div className="rounded-2xl border border-primary/25 bg-gradient-to-b from-primary/[0.04] via-card to-card p-5 sm:p-6 shadow-sm">
      {/* Header with Title and Explanatory Badge */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between border-b border-border/60 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-3 py-1 text-xs font-bold uppercase tracking-wider text-primary border border-primary/20">
              <Sparkles className="h-3.5 w-3.5" />
              Quy Trình Chuẩn 5 Giai Đoạn
            </span>
            <span className="text-xs font-semibold text-muted-foreground">
              (Theo Quy chế Đồ án Cuối kỳ Khoa CNTT - HCMUTE)
            </span>
          </div>
          <h3 className="mt-1.5 text-lg font-bold tracking-tight text-foreground sm:text-xl">
            Tiến Trình Thực Hiện Đề Tài & Khóa Luận Tốt Nghiệp
          </h3>
        </div>

        <button
          type="button"
          onClick={() => setIsDetailExpanded(!isDetailExpanded)}
          className="inline-flex items-center gap-1.5 self-start sm:self-auto rounded-lg border border-border/80 bg-background/80 px-3 py-1.5 text-xs font-medium text-muted-foreground hover:text-foreground hover:border-primary/40 transition-colors"
        >
          {isDetailExpanded ? (
            <>
              <span>Thu gọn hướng dẫn</span>
              <ChevronUp className="h-4 w-4" />
            </>
          ) : (
            <>
              <span>Xem chi tiết giai đoạn</span>
              <ChevronDown className="h-4 w-4" />
            </>
          )}
        </button>
      </div>

      {/* Stepper Timeline Bar */}
      <div className="mt-6 grid grid-cols-1 gap-3 sm:grid-cols-5">
        {stages.map((stage) => {
          const isCurrentActive = stage.step === activeStage;
          const isSelected = stage.step === selectedStage;
          const isCompleted = stage.status === 'completed';
          const StageIcon = stage.icon;

          return (
            <button
              key={stage.step}
              type="button"
              onClick={() => {
                setSelectedStage(stage.step);
                setIsDetailExpanded(true);
              }}
              className={cn(
                'group relative flex flex-col items-start rounded-xl p-3.5 text-left transition-all border',
                isSelected
                  ? 'border-primary bg-primary/[0.08] shadow-xs ring-2 ring-primary/20'
                  : isCurrentActive
                  ? 'border-emerald-500/50 bg-emerald-500/[0.06] hover:border-emerald-500'
                  : isCompleted
                  ? 'border-border/70 bg-card hover:border-primary/40 hover:bg-primary/[0.02]'
                  : 'border-dashed border-border/60 bg-muted/20 opacity-75 hover:opacity-100 hover:border-border',
              )}
            >
              {/* Top Step Number & Status Icon */}
              <div className="flex w-full items-center justify-between gap-2">
                <span
                  className={cn(
                    'flex h-6 w-6 items-center justify-center rounded-full text-xs font-bold',
                    isCurrentActive
                      ? 'bg-emerald-600 text-white'
                      : isCompleted
                      ? 'bg-primary text-primary-foreground'
                      : 'bg-muted text-muted-foreground',
                  )}
                >
                  {stage.step}
                </span>

                {isCompleted ? (
                  <span className="inline-flex items-center gap-1 text-[11px] font-medium text-emerald-600 dark:text-emerald-400">
                    <CheckCircle2 className="h-3.5 w-3.5" />
                    Đã xong
                  </span>
                ) : isCurrentActive ? (
                  <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-600 dark:text-emerald-400">
                    <span className="relative flex h-2 w-2">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                    </span>
                    Hiện tại
                  </span>
                ) : (
                  <span className="text-[11px] text-muted-foreground font-medium">Sắp tới</span>
                )}
              </div>

              {/* Stage Title */}
              <h4 className="mt-2.5 text-xs font-bold text-foreground group-hover:text-primary transition-colors flex items-center gap-1.5">
                <StageIcon className="h-3.5 w-3.5 text-primary shrink-0" />
                <span className="line-clamp-1">{stage.title}</span>
              </h4>

              {/* Subtitle & Role */}
              <p className="mt-1 text-[11px] text-muted-foreground line-clamp-1 font-medium">
                {stage.shortDesc}
              </p>

              <span className="mt-2 text-[10px] font-semibold text-primary/80 uppercase tracking-wider">
                {stage.rule}
              </span>
            </button>
          );
        })}
      </div>

      {/* Expanded Stage Detail Card */}
      {isDetailExpanded && (
        <div className="mt-5 rounded-xl border border-primary/20 bg-card p-4 sm:p-5 shadow-2xs transition-all">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between border-b border-border/50 pb-3">
            <div className="flex items-center gap-2.5">
              <div className="flex h-8 w-8 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <currentStageInfo.icon className="h-4 w-4" />
              </div>
              <div>
                <h4 className="text-sm font-bold text-foreground">
                  Giai đoạn {currentStageInfo.step}: {currentStageInfo.title}
                </h4>
                <p className="text-xs text-muted-foreground">
                  Chủ thể thực hiện:{' '}
                  <strong className="text-foreground">{currentStageInfo.role}</strong> · Căn cứ{' '}
                  <strong className="text-primary">{currentStageInfo.rule}</strong>
                </p>
              </div>
            </div>

            <div className="inline-flex items-center gap-1.5 rounded-lg bg-muted/60 px-3 py-1 text-xs text-muted-foreground border border-border/40">
              <CalendarClock className="h-3.5 w-3.5 text-primary" />
              <span>{currentStageInfo.timeline}</span>
            </div>
          </div>

          <div className="mt-3 space-y-2.5 text-xs text-foreground/90">
            <p className="font-medium text-muted-foreground">
              {currentStageInfo.details.objective}
            </p>
            <ul className="space-y-1.5 pl-4 list-disc text-muted-foreground marker:text-primary">
              {currentStageInfo.details.actions.map((act, idx) => (
                <li key={idx} className="leading-relaxed">
                  {act}
                </li>
              ))}
            </ul>
            <div className="mt-2 flex items-center gap-1.5 rounded-md bg-primary/5 px-2.5 py-1.5 text-[11px] font-medium text-primary border border-primary/15">
              <ShieldCheck className="h-3.5 w-3.5 shrink-0" />
              <span>Lưu ý quy chế: {currentStageInfo.details.deadlineNote}</span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
