'use client';

import { useState, type ReactNode } from 'react';
import {
  Award,
  BookOpen,
  CalendarDays,
  ChevronDown,
  ChevronUp,
  FileText,
  GraduationCap,
  Layers,
  Scale,
  ShieldAlert,
  ShieldCheck,
  UserCheck,
  UsersRound,
} from 'lucide-react';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';

/**
 * The nine rules are authored with different block combinations, so the i18n
 * tuple is a union of shapes. This view model makes every optional block
 * explicit for the renderer.
 */
interface GuideRuleCopy {
  readonly number: string;
  readonly title: string;
  readonly badge: string;
  readonly lead?: string;
  readonly definitions?: readonly { readonly label: string; readonly text: string }[];
  readonly bullets?: readonly string[];
  readonly callout?: string;
  readonly tail?: string;
}

/** Renders `**bold**` spans inside a copy string as inline emphasis. */
function renderInlineBold(text: string): ReactNode[] {
  return text
    .split('**')
    .map((part, index) =>
      index % 2 === 1 ? <strong key={index}>{part}</strong> : <span key={index}>{part}</span>,
    );
}

const RULE_ICONS = [CalendarDays, Layers, BookOpen, UsersRound, FileText, UserCheck, Scale, ShieldAlert, Award];
const RULE_TONES = ['primary', 'muted', 'primary', 'muted', 'amber', 'muted', 'emerald', 'destructive', 'muted'] as const;

const CALLOUT_CLASS: Record<string, string> = {
  amber: 'rounded-lg border border-amber-500/30 bg-amber-500/10 p-3 text-amber-800 dark:text-amber-300',
  emerald:
    'rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-3 text-emerald-800 dark:text-emerald-300',
  destructive: 'rounded-lg border border-destructive/30 bg-destructive/10 p-3 text-destructive',
  primary: 'rounded-lg border border-primary/30 bg-primary/[0.06] p-3 text-foreground',
  muted: 'rounded-lg border border-border/70 bg-muted/20 p-3 text-foreground/90',
};

const DEFINITION_CLASS: Record<string, string> = {
  primary: 'rounded-lg border border-primary/20 bg-primary/[0.03] p-3',
  muted: 'rounded-lg border border-border/80 bg-muted/20 p-3',
};

export function ThesisRegulationGuide() {
  const { messages } = useI18n();
  const copy = messages.thesisWorkflow.guide;

  const [isOpen, setIsOpen] = useState(false);
  const [activeRuleId, setActiveRuleId] = useState<number>(0);

  const currentRule = (copy.rules[activeRuleId] ?? copy.rules[0]) as GuideRuleCopy;
  const tone = RULE_TONES[activeRuleId] ?? 'muted';

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
              <span className="rounded-md bg-primary/10 px-2.5 py-0.5 text-[11px] font-bold uppercase tracking-wider text-primary border border-primary/20">
                {copy.badge}
              </span>
              <span className="text-xs text-muted-foreground font-medium">
                {copy.faculty}
              </span>
            </div>
            <h3 className="mt-1 text-base font-bold text-foreground sm:text-lg">
              {copy.title}
            </h3>
          </div>
        </div>

        <div className="flex shrink-0 items-center gap-2">
          <span className="text-xs font-semibold text-primary hidden sm:inline-block">
            {isOpen ? copy.collapse : copy.open}
          </span>
          <div className="flex h-8 w-8 items-center justify-center rounded-lg border border-border/70 bg-background text-muted-foreground">
            {isOpen ? <ChevronUp className="h-4 w-4" /> : <ChevronDown className="h-4 w-4" />}
          </div>
        </div>
      </button>

      {/* Expandable Accordion Body */}
      {isOpen && (
        <div className="border-t border-border/60 bg-muted/10 p-5 sm:p-6 space-y-5">
          {/* Hall & Council Visual Banner Strip */}
          <div className="overflow-hidden rounded-xl border border-border/70 bg-card shadow-2xs">
            <div className="grid grid-cols-1 sm:grid-cols-12 items-center">
              <div className="p-4 sm:col-span-8 space-y-1.5">
                <span className="text-[11px] font-bold uppercase tracking-wider text-primary">
                  HCMUTE Academic Council Guidelines
                </span>
                <p className="text-xs text-muted-foreground leading-relaxed">
                  Quy chuẩn quy trình thực hiện và đánh giá khóa luận tốt nghiệp theo chuẩn chất lượng đào tạo đại học chính quy HCMUTE.
                </p>
              </div>
              <div className="relative h-28 sm:h-full sm:col-span-4 min-h-[90px] overflow-hidden border-t sm:border-t-0 sm:border-l border-border/60">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src="/images/banners/thesis_defense_hall.jpg"
                  alt="HCMUTE Thesis Defense Hall"
                  className="h-full w-full object-cover object-center"
                />
                <div className="absolute inset-0 bg-gradient-to-t from-black/50 via-transparent to-transparent" />
                <div className="absolute bottom-1.5 left-2 rounded-md bg-background/85 px-1.5 py-0.5 text-[9px] font-bold text-foreground backdrop-blur-xs">
                  Hội đồng Đánh giá Khóa luận
                </div>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 gap-6 lg:grid-cols-[280px_1fr]">
            {/* Rule Selector List */}
            <div className="space-y-1.5">
              <p className="px-2 pb-1 text-xs font-bold uppercase tracking-wider text-muted-foreground">
                {copy.indexTitle}
              </p>
              {copy.rules.map((rule, index) => {
                const isSelected = index === activeRuleId;
                const RuleIcon = RULE_ICONS[index] ?? BookOpen;
                return (
                  <button
                    key={rule.number}
                    type="button"
                    onClick={() => setActiveRuleId(index)}
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
                <span className="rounded-md bg-muted px-2.5 py-0.5 text-[11px] font-semibold text-muted-foreground">
                  {currentRule.badge}
                </span>
              </div>

              <div className="mt-4 space-y-3 text-xs leading-relaxed text-foreground/90">
                {currentRule.lead ? <p>{renderInlineBold(currentRule.lead)}</p> : null}

                {currentRule.definitions ? (
                  <div className="grid grid-cols-1 gap-2.5 sm:grid-cols-2 pt-1">
                    {currentRule.definitions.map((definition, definitionIndex) => (
                      <div
                        key={definition.label}
                        className={DEFINITION_CLASS[definitionIndex < 2 ? 'primary' : 'muted']}
                      >
                        <span className="font-bold text-primary">{definition.label}</span>
                        <p className="mt-1 text-muted-foreground">{renderInlineBold(definition.text)}</p>
                      </div>
                    ))}
                  </div>
                ) : null}

                {currentRule.bullets ? (
                  <ul className="list-disc pl-5 space-y-1.5 text-muted-foreground marker:text-primary">
                    {currentRule.bullets.map((bullet) => (
                      <li key={bullet}>{renderInlineBold(bullet)}</li>
                    ))}
                  </ul>
                ) : null}

                {currentRule.callout ? (
                  <div className={CALLOUT_CLASS[tone] ?? CALLOUT_CLASS.muted}>
                    {renderInlineBold(currentRule.callout)}
                  </div>
                ) : null}

                {currentRule.tail ? (
                  <p className="text-muted-foreground">{renderInlineBold(currentRule.tail)}</p>
                ) : null}
              </div>

              <div className="mt-5 flex items-center gap-2 border-t border-border/40 pt-3 text-[11px] text-muted-foreground">
                <ShieldCheck className="h-3.5 w-3.5 text-emerald-600 dark:text-emerald-400" />
                <span>{copy.footer}</span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
