'use client';

import { useCallback, useMemo, useState } from 'react';
import {
  ArrowRight,
  BookOpen,
  CheckCircle2,
  ChevronDown,
  Clock,
  GraduationCap,
  Unlock,
} from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { curriculumApi, registrationApi, type RegistrationSection } from '@/lib/api';
import { getLocalizedName } from '@/lib/academic-content';
import { useApiQuery } from '@/lib/query';
import { LocalizedLink } from '@/components/LocalizedLink';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { StatusPill } from '@/components/ui/status-pill';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import type { MyCurriculumCourse } from '@/types/api';

type StatusFilter = 'ALL' | 'AVAILABLE' | 'COMPLETED' | 'IN_PROGRESS' | 'NOT_STARTED';

const STATUS_TONE: Record<MyCurriculumCourse['status'], 'success' | 'info' | 'neutral'> = {
  COMPLETED: 'success',
  IN_PROGRESS: 'info',
  NOT_STARTED: 'neutral',
};

/** Grouped year -> semester -> courses, same shape as the enrollments roadmap. */
function groupPlan(courses: MyCurriculumCourse[]) {
  const grouped = new Map<number, Map<number, MyCurriculumCourse[]>>();
  for (const course of courses) {
    const semesters = grouped.get(course.year) ?? new Map<number, MyCurriculumCourse[]>();
    const list = semesters.get(course.semester) ?? [];
    list.push(course);
    semesters.set(course.semester, list);
    grouped.set(course.year, semesters);
  }
  return [...grouped.entries()]
    .sort(([a], [b]) => a - b)
    .map(
      ([year, semesters]) =>
        [
          year,
          [...semesters.entries()]
            .sort(([a], [b]) => a - b)
            .map(([semester, list]) => [semester, list] as const),
        ] as const,
    );
}

function courseCountLabel(count: number, singular: string, plural: string) {
  return `${count} ${count === 1 ? singular : plural}`;
}

export default function CurriculumPage() {
  const { hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, messages, formatNumber } = useI18n();
  const copy = messages.curriculum;
  const [statusFilter, setStatusFilter] = useState<StatusFilter>('ALL');

  const curriculumQuery = useApiQuery(['curriculum', 'mine'], () => curriculumApi.getMyCurriculum(), {
    enabled: hasAccess,
    staleTime: 60_000,
  });
  // The catalog endpoint answers 4xx when no registration round is open; a
  // closed round degrades to "no open section" markers instead of an error.
  const sectionsQuery = useApiQuery(
    ['registration', 'sections', 'curriculum-page'],
    () => registrationApi.sections().catch(() => [] as RegistrationSection[]),
    { enabled: hasAccess, staleTime: 60_000 },
  );

  const courses = useMemo(() => curriculumQuery.data?.courses ?? [], [curriculumQuery.data]);

  const statusByCode = useMemo(
    () => new Map(courses.map((course) => [course.code, course.status])),
    [courses],
  );

  const isAvailable = useCallback(
    (course: MyCurriculumCourse) =>
      course.status === 'NOT_STARTED'
      && (course.prerequisites ?? []).every((code) => statusByCode.get(code) === 'COMPLETED'),
    [statusByCode],
  );

  /** Reverse map: course code -> codes of plan courses that require it. */
  const unlocksByCode = useMemo(() => {
    const map = new Map<string, string[]>();
    for (const course of courses) {
      for (const required of course.prerequisites ?? []) {
        const list = map.get(required) ?? [];
        list.push(course.code);
        map.set(required, list);
      }
    }
    map.forEach((list) => list.sort());
    return map;
  }, [courses]);

  const openSectionCountByCourseId = useMemo(() => {
    const map = new Map<string, number>();
    for (const section of sectionsQuery.data ?? []) {
      if (section.status === 'OPEN') {
        map.set(section.courseId, (map.get(section.courseId) ?? 0) + 1);
      }
    }
    return map;
  }, [sectionsQuery.data]);

  const availableCourses = useMemo(() => courses.filter(isAvailable), [courses, isAvailable]);
  const completedCourses = useMemo(() => courses.filter((c) => c.status === 'COMPLETED'), [courses]);
  const inProgressCourses = useMemo(() => courses.filter((c) => c.status === 'IN_PROGRESS'), [courses]);
  const notStartedCourses = useMemo(() => courses.filter((c) => c.status === 'NOT_STARTED'), [courses]);

  const filteredCourses = useMemo(() => {
    if (statusFilter === 'ALL') return courses;
    if (statusFilter === 'AVAILABLE') return availableCourses;
    return courses.filter((course) => course.status === statusFilter);
  }, [courses, statusFilter, availableCourses]);

  const grouped = useMemo(() => groupPlan(filteredCourses), [filteredCourses]);

  const curriculum = curriculumQuery.data?.curriculum;
  const totalCredits = curriculum?.totalCredits || courses.reduce((sum, c) => sum + (c.credits || 0), 0);
  const completedCredits = completedCourses.reduce((sum, c) => sum + (c.credits || 0), 0);
  const progressPercent = totalCredits > 0 ? Math.min(100, Math.round((completedCredits / totalCredits) * 100)) : 0;

  const statusPill = (course: MyCurriculumCourse) => {
    if (course.status === 'COMPLETED') {
      return (
        <StatusPill
          tone="success"
          icon={<CheckCircle2 className="h-3.5 w-3.5" />}
          className="gap-1.5 px-3 py-1 font-semibold"
        >
          {copy.filterCompleted}
          {course.finalGrade !== null && course.finalGrade !== undefined ? (
            <span className="ml-1 border-l border-border/80 pl-1 font-bold">
              {course.finalGrade.toFixed(1)} {course.letterGrade ? `(${course.letterGrade})` : ''}
            </span>
          ) : null}
        </StatusPill>
      );
    }
    if (course.status === 'IN_PROGRESS') {
      return (
        <StatusPill
          tone="info"
          icon={<Clock className="h-3.5 w-3.5" />}
          className="gap-1.5 px-3 py-1 font-semibold"
        >
          {copy.filterInProgress}
        </StatusPill>
      );
    }
    if (isAvailable(course)) {
      return (
        <StatusPill
          tone="success"
          icon={<Unlock className="h-3.5 w-3.5" />}
          className="gap-1.5 px-3 py-1 font-semibold"
        >
          {copy.filterAvailable}
        </StatusPill>
      );
    }
    return (
      <StatusPill tone="neutral" className="px-3 py-1">
        {copy.filterNotStarted}
      </StatusPill>
    );
  };

  const requirementChips = (course: MyCurriculumCourse) => {
    const prerequisites = course.prerequisites ?? [];
    const unlocks = unlocksByCode.get(course.code) ?? [];
    if (prerequisites.length === 0 && unlocks.length === 0) return null;
    return (
      <div className="mt-1.5 space-y-1">
        {prerequisites.length > 0 ? (
          <p className="flex flex-wrap items-center gap-1.5 text-[11px] text-muted-foreground">
            <span className="font-medium">{copy.prerequisiteLabel}</span>
            {prerequisites.map((code) => {
              const met = statusByCode.get(code) === 'COMPLETED';
              return (
                <span
                  key={code}
                  className={`rounded px-1.5 py-0.5 font-mono text-[11px] font-semibold ${
                    met
                      ? 'bg-status-success/10 text-status-success-foreground'
                      : 'bg-secondary text-foreground'
                  }`}
                  title={met ? copy.filterCompleted : code}
                >
                  {met ? `✓ ${code}` : code}
                </span>
              );
            })}
          </p>
        ) : null}
        {unlocks.length > 0 ? (
          <p className="flex flex-wrap items-center gap-1.5 text-[11px] text-muted-foreground">
            <span className="font-medium">{copy.unlocksLabel}</span>
            {unlocks.map((code) => (
              <span
                key={code}
                className="rounded bg-primary/10 px-1.5 py-0.5 font-mono text-[11px] font-semibold text-primary"
              >
                {code}
              </span>
            ))}
          </p>
        ) : null}
      </div>
    );
  };

  if (authLoading) {
    return <LoadingState label={copy.title} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />

      {curriculumQuery.isLoading ? (
        <LoadingState label={copy.title} />
      ) : curriculumQuery.isError ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={copy.loadFailed}
          onRetry={() => void curriculumQuery.refetch()}
        />
      ) : !curriculum || courses.length === 0 ? (
        <EmptyState icon={BookOpen} title={copy.emptyTitle} description={copy.emptyDescription} />
      ) : (
        <div className="space-y-6">
          <div className="rounded-xl border border-border/70 bg-card/60 p-4 sm:p-5">
            <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_minmax(280px,380px)] lg:items-center">
              <div>
                <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                  {curriculum.code}
                </p>
                <h3 className="text-lg font-bold text-foreground sm:text-xl">
                  {getLocalizedName(locale, curriculum, curriculum.name)}
                </h3>
              </div>
              <div aria-label={copy.statusSummaryLabel} className="space-y-3">
                <div className="flex items-center justify-between gap-3 text-xs text-muted-foreground">
                  <span className="font-medium">{copy.progressLabel}</span>
                  <span className="font-semibold tabular-nums text-foreground">
                    {formatNumber(completedCredits)} / {formatNumber(totalCredits)} {copy.creditsLabel}
                    {' · '}
                    {progressPercent}%
                  </span>
                </div>
                <div className="h-2 w-full overflow-hidden rounded-full bg-secondary">
                  <div
                    className="h-full rounded-full bg-primary transition-all duration-300"
                    style={{ width: `${progressPercent}%` }}
                  />
                </div>
                <div className="grid grid-cols-3 gap-2">
                  {(
                    [
                      { label: copy.filterCompleted, count: completedCourses.length, tone: 'bg-status-success/10 text-status-success-foreground' },
                      { label: copy.filterInProgress, count: inProgressCourses.length, tone: 'bg-status-info/10 text-status-info-foreground' },
                      { label: copy.filterAvailable, count: availableCourses.length, tone: 'bg-primary/10 text-primary' },
                    ] as const
                  ).map((item) => (
                    <div key={item.label} className={`rounded-lg px-2.5 py-2 text-center ${item.tone}`}>
                      <div className="text-base font-bold leading-none tabular-nums">
                        {formatNumber(item.count)}
                      </div>
                      <div className="mt-1 text-[11px] font-medium leading-tight">{item.label}</div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          </div>

          {availableCourses.length > 0 && (statusFilter === 'ALL' || statusFilter === 'AVAILABLE') ? (
            <Card variant="muted" className="overflow-hidden border-primary/25">
              <CardHeader className="border-b border-border/60 bg-primary/5 py-3">
                <CardTitle className="flex items-center gap-2 text-sm font-semibold text-foreground">
                  <Unlock className="h-4 w-4 text-primary" aria-hidden="true" />
                  {copy.availableTitle}
                  <span className="rounded-md bg-primary/10 px-2 py-0.5 text-xs font-bold text-primary">
                    {formatNumber(availableCourses.length)}
                  </span>
                </CardTitle>
                <p className="text-xs leading-5 text-muted-foreground">{copy.availableDescription}</p>
              </CardHeader>
              <CardContent className="grid gap-3 p-4 sm:grid-cols-2 xl:grid-cols-3">
                {availableCourses.map((course) => {
                  const openCount = openSectionCountByCourseId.get(course.courseId) ?? 0;
                  return (
                    <div
                      key={`available-${course.courseId}`}
                      className="flex flex-col justify-between gap-3 rounded-lg border border-border/70 bg-card p-3.5"
                    >
                      <div>
                        <div className="flex flex-wrap items-center gap-2">
                          <span className="font-mono text-xs font-bold text-primary">{course.code}</span>
                          <span className="text-xs text-muted-foreground">
                            {course.credits} {copy.creditsLabel}
                          </span>
                          <span
                            className={`rounded px-2 py-0.5 text-[11px] font-medium ${
                              course.isMandatory
                                ? 'bg-primary/10 text-primary'
                                : 'bg-secondary text-muted-foreground'
                            }`}
                          >
                            {course.isMandatory ? copy.mandatory : copy.elective}
                          </span>
                        </div>
                        <h4 className="mt-1 text-sm font-medium text-foreground">
                          {getLocalizedName(locale, course, course.name)}
                        </h4>
                        {requirementChips(course)}
                      </div>
                      <div className="flex items-center justify-between gap-2">
                        <span className="text-[11px] font-medium text-muted-foreground">
                          {openCount > 0
                            ? copy.openSections.replace('{count}', formatNumber(openCount))
                            : copy.noOpenSection}
                        </span>
                        {openCount > 0 ? (
                          <LocalizedLink
                            href={`/dashboard/register?q=${encodeURIComponent(course.code)}`}
                            className="inline-flex shrink-0 items-center gap-1 rounded-md bg-primary px-2.5 py-1.5 text-xs font-semibold text-primary-foreground transition hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                          >
                            {copy.registerCta}
                            <ArrowRight className="h-3.5 w-3.5" aria-hidden="true" />
                          </LocalizedLink>
                        ) : null}
                      </div>
                    </div>
                  );
                })}
              </CardContent>
            </Card>
          ) : null}

          <div className="flex flex-wrap items-center gap-1.5 text-xs">
            {(
              [
                { key: 'ALL', label: copy.filterAll, count: courses.length },
                { key: 'AVAILABLE', label: copy.filterAvailable, count: availableCourses.length },
                { key: 'COMPLETED', label: copy.filterCompleted, count: completedCourses.length },
                { key: 'IN_PROGRESS', label: copy.filterInProgress, count: inProgressCourses.length },
                { key: 'NOT_STARTED', label: copy.filterNotStarted, count: notStartedCourses.length },
              ] as const
            ).map((btn) => (
              <button
                key={btn.key}
                type="button"
                aria-pressed={statusFilter === btn.key}
                onClick={() => setStatusFilter(btn.key)}
                className={`inline-flex min-h-10 items-center rounded-md px-3 py-1 font-medium transition ${
                  statusFilter === btn.key
                    ? 'bg-foreground text-background font-semibold'
                    : 'border border-border/80 bg-card text-muted-foreground hover:text-foreground'
                }`}
              >
                {btn.label} ({btn.count})
              </button>
            ))}
          </div>

          {grouped.length === 0 ? (
            <EmptyState
              icon={BookOpen}
              title={copy.filterAvailable}
              description={copy.availableEmpty}
            />
          ) : (
            grouped.map(([year, semesters]) => (
              <div key={`year-${year}`} className="space-y-5">
                <h3 className="flex items-center gap-2 border-b border-border/60 pb-2 text-lg font-bold text-foreground">
                  <GraduationCap className="h-5 w-5 text-primary" />
                  {copy.yearPrefix} {formatNumber(year)}
                  <span className="text-xs font-medium text-muted-foreground">
                    {courseCountLabel(
                      semesters.reduce((total, [, list]) => total + list.length, 0),
                      copy.courseWord,
                      copy.coursesWord,
                    )}
                  </span>
                </h3>
                <div className="grid gap-6 lg:grid-cols-2">
                  {semesters.map(([semester, semesterCourses]) => (
                    <Card key={`year-${year}-sem-${semester}`} variant="muted" className="overflow-hidden">
                      <CardHeader className="border-b border-border/60 bg-secondary/20 py-3">
                        <CardTitle className="text-sm font-semibold uppercase tracking-wider text-muted-foreground">
                          {copy.semesterPrefix} {formatNumber(semester)} -{' '}
                          {courseCountLabel(semesterCourses.length, copy.courseWord, copy.coursesWord)}
                        </CardTitle>
                      </CardHeader>
                      <CardContent className="divide-y divide-border/60 p-0">
                        {semesterCourses.map((course) => (
                          <div
                            key={course.courseId}
                            className="flex flex-col gap-2 p-4 transition-colors hover:bg-secondary/15 sm:flex-row sm:items-center sm:justify-between"
                          >
                            <div className="min-w-0 flex-1">
                              <div className="flex flex-wrap items-center gap-2">
                                <span className="font-mono text-xs font-semibold text-primary">
                                  {course.code}
                                </span>
                                <span className="text-xs text-muted-foreground">
                                  {course.credits} {copy.creditsLabel}
                                </span>
                                <span
                                  className={`rounded px-2 py-0.5 text-[11px] font-medium ${
                                    course.isMandatory
                                      ? 'bg-primary/10 text-primary'
                                      : 'bg-secondary text-muted-foreground'
                                  }`}
                                >
                                  {course.isMandatory ? copy.mandatory : copy.elective}
                                </span>
                              </div>
                              <h4 className="mt-1 font-medium text-foreground">
                                {getLocalizedName(locale, course, course.name)}
                              </h4>
                              {requirementChips(course)}
                            </div>
                            <div className="flex shrink-0 items-center gap-2">
                              {statusPill(course)}
                            </div>
                          </div>
                        ))}
                      </CardContent>
                    </Card>
                  ))}
                </div>
              </div>
            ))
          )}
        </div>
      )}
    </div>
  );
}
