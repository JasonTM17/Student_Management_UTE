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
  ShieldCheck,
  Sparkles,
  Users,
} from 'lucide-react';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import type { ThesisRound } from '@/lib/thesis-api';

interface ThesisWorkflowStepperProps {
  round: ThesisRound;
  formatDateTime: (value: string | number | Date) => string;
}

/** Icons are positional, matching the stage order in the i18n copy. */
const STAGE_ICONS = [BookOpen, FileSpreadsheet, Users, FileCheck, GraduationCap];

/** Fills `{name}` placeholders in a copy template. */
function fill(template: string, values: Record<string, string | number>): string {
  return template.replace(/\{(\w+)\}/g, (match, key: string) =>
    key in values ? String(values[key]) : match,
  );
}

export function ThesisWorkflowStepper({ round, formatDateTime }: ThesisWorkflowStepperProps) {
  const { messages } = useI18n();
  const copy = messages.thesisWorkflow.stepper;

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
    return copy.stages.map((stage, index) => {
      const step = index + 1;
      const timeline =
        step === 1
          ? `${formatDateTime(round.lecturerSubmitStart)} → ${formatDateTime(round.lecturerSubmitEnd)}`
          : step === 2
            ? round.proposalPublishAt
              ? formatDateTime(round.proposalPublishAt)
              : copy.fallbackBeforeRegistration
            : step === 3
              ? `${formatDateTime(round.registrationStart)} → ${formatDateTime(round.registrationEnd)}`
              : step === 4
                ? round.gvpbDeadline
                  ? fill(copy.reportDeadline, { date: formatDateTime(round.gvpbDeadline) })
                  : copy.fallbackBeforeDefence
                : round.reportDate
                  ? fill(copy.reportDate, { date: formatDateTime(round.reportDate) })
                  : copy.fallbackPerAssignment;

      // Stage 4 is the only stage whose regulation note becomes date-specific.
      const deadlineNote =
        step === 4 && round.gvpbDeadline
          ? fill(copy.reviewerScoreDeadline, { date: formatDateTime(round.gvpbDeadline) })
          : stage.deadlineNote;

      return {
        step,
        title: stage.title,
        shortDesc: stage.shortDesc,
        role: stage.role,
        rule: stage.rule,
        status: activeStage > step ? 'completed' : activeStage === step ? 'active' : 'upcoming',
        timeline,
        icon: STAGE_ICONS[index],
        details: {
          objective: stage.objective,
          actions: stage.actions,
          deadlineNote,
        },
      };
    });
  }, [copy, round, activeStage, formatDateTime]);

  const currentStageInfo = stages.find((s) => s.step === selectedStage) || stages[activeStage - 1] || stages[0];

  return (
    <div className="rounded-2xl border border-primary/25 bg-gradient-to-b from-primary/[0.04] via-card to-card p-5 sm:p-6 shadow-sm">
      {/* Header with Title and Explanatory Badge */}
      <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between border-b border-border/60 pb-4">
        <div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 rounded-full bg-primary/10 px-3 py-1 text-xs font-bold uppercase tracking-wider text-primary border border-primary/20">
              <Sparkles className="h-3.5 w-3.5" />
              {copy.badge}
            </span>
            <span className="text-xs font-semibold text-muted-foreground">
              {copy.authorityNote}
            </span>
          </div>
          <h3 className="mt-1.5 text-lg font-bold tracking-tight text-foreground sm:text-xl">
            {copy.title}
          </h3>
        </div>

        <button
          type="button"
          onClick={() => setIsDetailExpanded(!isDetailExpanded)}
          className="inline-flex items-center gap-1.5 self-start sm:self-auto rounded-lg border border-border/80 bg-background/80 px-3 py-1.5 text-xs font-medium text-muted-foreground hover:text-foreground hover:border-primary/40 transition-colors"
        >
          {isDetailExpanded ? (
            <>
              <span>{copy.collapse}</span>
              <ChevronUp className="h-4 w-4" />
            </>
          ) : (
            <>
              <span>{copy.expand}</span>
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
                    {copy.stageProgress.done}
                  </span>
                ) : isCurrentActive ? (
                  <span className="inline-flex items-center gap-1 text-[11px] font-bold text-emerald-600 dark:text-emerald-400">
                    <span className="relative flex h-2 w-2">
                      <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                      <span className="relative inline-flex rounded-full h-2 w-2 bg-emerald-500"></span>
                    </span>
                    {copy.stageProgress.current}
                  </span>
                ) : (
                  <span className="text-[11px] text-muted-foreground font-medium">
                    {copy.stageProgress.upcoming}
                  </span>
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
                  {fill(copy.stageHeading, { step: currentStageInfo.step, title: currentStageInfo.title })}
                </h4>
                <p className="text-xs text-muted-foreground">
                  {copy.ownerLabel}{' '}
                  <strong className="text-foreground">{currentStageInfo.role}</strong> · {copy.basisLabel}{' '}
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
              <span>
                {copy.regulationNote} {currentStageInfo.details.deadlineNote}
              </span>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
