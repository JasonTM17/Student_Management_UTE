'use client';

import { useEffect, useState } from 'react';
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
import { useI18n } from '@/i18n';
import type { ThesisRound } from '@/lib/thesis-api';
import { thesisWindowRemaining } from '@/lib/thesis-datetime';

interface RoundMilestoneCardProps {
  round: ThesisRound;
  formatDateTime: (value: string | number | Date) => string;
  statusLabel: (status: string) => string;
}

function fill(template: string, values: Record<string, number>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) =>
    key in values ? String(values[key]) : match,
  );
}

export function RoundMilestoneCard({
  round,
  formatDateTime,
  statusLabel,
}: RoundMilestoneCardProps) {
  const { messages } = useI18n();
  const rm = messages.roundMilestone;
  // Remaining time only exists on the client clock — computing it during SSR
  // would stamp the server's instant and then hydrate to a different text.
  const [now, setNow] = useState<number | null>(null);
  useEffect(() => {
    setNow(Date.now());
    const timer = setInterval(() => setNow(Date.now()), 60_000);
    return () => clearInterval(timer);
  }, []);

  const remainingLabel = (end: string | null | undefined) => {
    if (now === null) return null;
    const left = thesisWindowRemaining(end, now);
    if (!left) return null;
    const template =
      left.days > 0
        ? rm.remainingDaysHours
        : left.hours > 0
          ? rm.remainingHoursMinutes
          : rm.remainingMinutes;
    return fill(template, { d: left.days, h: left.hours, m: left.minutes });
  };

  // A window counts down to its close only while it is actually open; before
  // it starts the open/close line already tells the story.
  const remainingWindowLabel = (start: string | null | undefined, end: string | null | undefined) => {
    if (now === null) return null;
    const startMs = start ? new Date(start).getTime() : NaN;
    if (!Number.isFinite(startMs) || now < startMs) return null;
    return remainingLabel(end);
  };
  const isKLTN = round.thesisType === 'KLTN';
  const isTLCN = round.thesisType === 'TLCN';
  const isHasGVPB = isKLTN || isTLCN;

  const phase1Remaining = remainingWindowLabel(round.lecturerSubmitStart, round.lecturerSubmitEnd);
  const phase2Remaining = remainingWindowLabel(round.registrationStart, round.registrationEnd);
  const gvpbRemaining = remainingLabel(round.gvpbDeadline);
  const councilRemaining = remainingLabel(round.defenseDate || round.reportDate);

  const typeConfig: Record<string, { label: string; sub: string; badgeClass: string }> = {
    KLTN: {
      label: rm.typeKLTN,
      sub: rm.typeKLTNSub,
      badgeClass: 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30',
    },
    TLCN: {
      label: rm.typeTLCN,
      sub: rm.typeTLCNSub,
      badgeClass: 'bg-indigo-500/15 text-indigo-700 dark:text-indigo-300 border-indigo-500/30',
    },
    NCKH: {
      label: rm.typeNCKH,
      sub: rm.typeNCKHSub,
      badgeClass: 'bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30',
    },
    MON_HOC: {
      label: rm.typeMonHoc,
      sub: rm.typeMonHocSub,
      badgeClass: 'bg-blue-500/15 text-blue-700 dark:text-blue-300 border-blue-500/30',
    },
  };

  const currentType = typeConfig[round.thesisType] || {
    label: round.thesisType,
    sub: rm.typeFallbackSub,
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
                  'inline-flex items-center gap-1.5 rounded-md px-3 py-1 text-xs font-bold uppercase tracking-wider border',
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
                {rm.statusLabel}
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
            <span>{rm.phase1}</span>
          </div>
          <p className="text-xs text-foreground/90 font-medium leading-relaxed">
            {formatDateTime(round.lecturerSubmitStart)}
            <br />
            <span className="text-muted-foreground">{rm.to}</span>{' '}
            {formatDateTime(round.lecturerSubmitEnd)}
          </p>
          {phase1Remaining ? (
            <p className="text-[11px] font-semibold text-primary" role="status">
              {phase1Remaining}
            </p>
          ) : null}
          <p className="text-[11px] text-muted-foreground">
            {rm.phase1Desc}
          </p>
        </div>

        {/* Milestone 2: Phase 2 SV */}
        <div className="p-4 sm:p-5 space-y-1.5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
            <Users className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
            <span>{rm.phase2}</span>
          </div>
          <p className="text-xs text-foreground/90 font-medium leading-relaxed">
            {formatDateTime(round.registrationStart)}
            <br />
            <span className="text-muted-foreground">{rm.to}</span>{' '}
            {formatDateTime(round.registrationEnd)}
          </p>
          {phase2Remaining ? (
            <p className="text-[11px] font-semibold text-primary" role="status">
              {phase2Remaining}
            </p>
          ) : null}
          <p className="text-[11px] text-muted-foreground">
            {rm.phase2Desc}
          </p>
        </div>

        {/* Milestone 3: GVPB Deadline (TLCN & KLTN only) */}
        <div className="p-4 sm:p-5 space-y-1.5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
            <Clock className="h-4 w-4 text-amber-600 dark:text-amber-400" />
            <span>{rm.gvpbDeadline}</span>
          </div>
          {isHasGVPB && round.gvpbDeadline ? (
            <>
              <p className="text-xs font-semibold text-foreground leading-relaxed">
                {formatDateTime(round.gvpbDeadline)}
              </p>
              {gvpbRemaining ? (
                <p className="text-[11px] font-semibold text-primary" role="status">
                  {gvpbRemaining}
                </p>
              ) : null}
              <p className="text-[11px] text-muted-foreground">
                {rm.gvpbDesc}
              </p>
            </>
          ) : (
            <>
              <p className="text-xs text-muted-foreground italic">
                {isHasGVPB ? rm.notScheduled : rm.notApplicable}
              </p>
              <p className="text-[11px] text-muted-foreground">
                {isHasGVPB ? rm.gvpbScope : rm.gvpbScopeOnly}
              </p>
            </>
          )}
        </div>

        {/* Milestone 4: report/defense date (TLCN/KLTN) */}
        <div className="p-4 sm:p-5 space-y-1.5">
          <div className="flex items-center gap-2 text-xs font-bold uppercase tracking-wider text-muted-foreground">
            <GraduationCap className="h-4 w-4 text-indigo-600 dark:text-indigo-400" />
            <span>{rm.councilReport}</span>
          </div>
          {(isKLTN || isHasGVPB) && (round.defenseDate || round.reportDate) ? (
            <>
              <p className="text-xs font-semibold text-foreground leading-relaxed">
                {formatDateTime(round.defenseDate || round.reportDate || '')}
              </p>
              {councilRemaining ? (
                <p className="text-[11px] font-semibold text-primary" role="status">
                  {councilRemaining}
                </p>
              ) : null}
              <p className="text-[11px] text-muted-foreground">
                {rm.councilDesc}
              </p>
            </>
          ) : (
            <>
              <p className="text-xs text-muted-foreground italic">
                {isKLTN || isHasGVPB ? rm.notScheduled : rm.notApplicable}
              </p>
              <p className="text-[11px] text-muted-foreground">
                {isKLTN || isHasGVPB ? rm.councilScope : rm.councilScopeOnly}
              </p>
            </>
          )}
        </div>
      </div>
    </div>
  );
}
