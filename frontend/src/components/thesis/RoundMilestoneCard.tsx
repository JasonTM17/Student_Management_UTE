'use client';

import {
  CalendarDays,
  CircleDot,
  Clock,
  GraduationCap,
  Scale,
  ShieldAlert,
  UserCheck,
  Users,
} from 'lucide-react';
import { cn } from '@/lib/utils';
import type { ThesisRound } from '@/lib/thesis-api';

interface RoundMilestoneCardProps {
  round: ThesisRound;
  formatDateTime: (value: string | number | Date) => string;
  statusLabel: (status: string) => string;
}

export function RoundMilestoneCard({
  round,
  formatDateTime,
  statusLabel,
}: RoundMilestoneCardProps) {
  const isKLTN = round.thesisType === 'KLTN';
  const isTLCN = round.thesisType === 'TLCN';
  const isHasGVPB = isKLTN || isTLCN;

  const typeConfig: Record<string, { label: string; sub: string; badgeClass: string }> = {
    KLTN: {
      label: 'Khóa luận tốt nghiệp (KLTN)',
      sub: 'Hình thức tốt nghiệp cao nhất · Yêu cầu Hội đồng 3-5 GV',
      badgeClass: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30',
    },
    TLCN: {
      label: 'Tiểu luận chuyên ngành (TLCN)',
      sub: 'Học phần chuyên sâu · Yêu cầu Giảng viên phản biện (GVPB)',
      badgeClass: 'bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border-indigo-500/30',
    },
    NCKH: {
      label: 'Nghiên cứu khoa học (NCKH)',
      sub: 'Đề tài nghiên cứu ứng dụng sinh viên',
      badgeClass: 'bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30',
    },
    MON_HOC: {
      label: 'Đồ án môn học (MON_HOC)',
      sub: 'Đồ án thực hành theo học phần chuyên ngành',
      badgeClass: 'bg-blue-500/15 text-blue-700 dark:text-blue-300 border-blue-500/30',
    },
  };

  const currentType = typeConfig[round.thesisType] || {
    label: round.thesisType,
    sub: 'Đợt đăng ký đề tài học vụ',
    badgeClass: 'bg-primary/10 text-primary border-primary/20',
  };

  return (
    <div className="overflow-hidden rounded-2xl border border-border/80 bg-card text-card-foreground shadow-sm">
      {/* Top Banner */}
      <div className="relative border-b border-border/60 bg-gradient-to-r from-primary/[0.05] via-card to-card p-6 sm:p-7">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
          <div className="space-y-2">
            <div className="flex flex-wrap items-center gap-2.5">
              <span
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-xs font-bold uppercase tracking-wider border',
                  currentType.badgeClass,
                )}
              >
                <CircleDot className="h-3.5 w-3.5" />
                {currentType.label}
              </span>
              <span className="text-xs text-muted-foreground font-medium">
                {currentType.sub}
              </span>
            </div>

            <h2 className="text-2xl font-bold tracking-tight text-foreground sm:text-3xl">
              {round.name}
            </h2>
          </div>

          {/* Current Status Pill */}
          <div className="flex shrink-0 items-center gap-3">
            <div className="rounded-xl border border-primary/20 bg-background/80 px-4 py-2.5 shadow-2xs text-right">
              <p className="text-[11px] font-bold uppercase tracking-wider text-muted-foreground">
                Trạng thái đợt
              </p>
              <p className="text-sm font-bold text-primary">{statusLabel(round.status)}</p>
            </div>
          </div>
        </div>
      </div>

      {/* 4 Regulatory Milestone Cards (R1) */}
      <div className="grid grid-cols-1 divide-y border-border/50 sm:grid-cols-2 sm:divide-y-0 sm:divide-x lg:grid-cols-4 bg-muted/5">
        {/* Milestone 1: Phase 1 GV */}
        <div className="p-4 sm:p-5 space-y-1.5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
            <CalendarDays className="h-4 w-4 text-primary" />
            <span>Giai đoạn 1 (GV nộp)</span>
          </div>
          <p className="text-xs text-foreground/90 font-medium leading-relaxed">
            {formatDateTime(round.lecturerSubmitStart)}
            <br />
            <span className="text-muted-foreground">đến</span>{' '}
            {formatDateTime(round.lecturerSubmitEnd)}
          </p>
          <p className="text-[11px] text-muted-foreground">
            GV bộ môn gửi đề tài (Điều R1, R2)
          </p>
        </div>

        {/* Milestone 2: Phase 2 SV */}
        <div className="p-4 sm:p-5 space-y-1.5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
            <Users className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
            <span>Giai đoạn 2 (SV đăng ký)</span>
          </div>
          <p className="text-xs text-foreground/90 font-medium leading-relaxed">
            {formatDateTime(round.registrationStart)}
            <br />
            <span className="text-muted-foreground">đến</span>{' '}
            {formatDateTime(round.registrationEnd)}
          </p>
          <p className="text-[11px] text-muted-foreground">
            Nhóm ≤ 3 SV chọn 1 đề tài (Điều R4)
          </p>
        </div>

        {/* Milestone 3: GVPB Deadline (TLCN & KLTN only) */}
        <div className="p-4 sm:p-5 space-y-1.5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
            <Clock className="h-4 w-4 text-amber-600 dark:text-amber-400" />
            <span>Hạn nộp điểm GVPB</span>
          </div>
          {isHasGVPB && round.gvpbDeadline ? (
            <>
              <p className="text-xs font-semibold text-foreground leading-relaxed">
                {formatDateTime(round.gvpbDeadline)}
              </p>
              <p className="text-[11px] text-muted-foreground">
                GVPB nộp điểm về Khoa (Điều R1)
              </p>
            </>
          ) : (
            <>
              <p className="text-xs text-muted-foreground italic">
                {isHasGVPB ? 'Chưa xếp lịch cụ thể' : 'Không áp dụng cho đợt này'}
              </p>
              <p className="text-[11px] text-muted-foreground">
                {isHasGVPB ? 'Áp dụng cho TLCN & KLTN' : 'Chỉ áp dụng cho TLCN & KLTN'}
              </p>
            </>
          )}
        </div>

        {/* Milestone 4: Council Report Date (KLTN only) */}
        <div className="p-4 sm:p-5 space-y-1.5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
            <GraduationCap className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
            <span>Ngày Báo Cáo Hội Đồng</span>
          </div>
          {isKLTN && round.reportDate ? (
            <>
              <p className="text-xs font-semibold text-foreground leading-relaxed">
                {formatDateTime(round.reportDate)}
              </p>
              <p className="text-[11px] text-muted-foreground">
                Hội đồng 3-5 GV chấm bảo vệ (Điều R1, R6)
              </p>
            </>
          ) : (
            <>
              <p className="text-xs text-muted-foreground italic">
                {isKLTN ? 'Chưa xếp lịch cụ thể' : 'Không áp dụng cho đợt này'}
              </p>
              <p className="text-[11px] text-muted-foreground">
                {isKLTN ? 'Hội đồng bảo vệ KLTN' : 'Chỉ áp dụng cho KLTN'}
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
