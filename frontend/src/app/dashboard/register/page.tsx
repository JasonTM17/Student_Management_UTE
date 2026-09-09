'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BookOpen, CheckCircle2, Search, Trash2, UserPlus } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { enrollmentsApi, registrationApi } from '@/lib/api';
import type { Enrollment } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { campusCodeMessage, campusErrorCode } from '@/lib/campus-error';
import { toast } from 'sonner';

type CatalogSection = Awaited<ReturnType<typeof registrationApi.sections>>[number];
type CatalogCourseGroup = {
  courseId: string;
  courseCode: string;
  courseName: string;
  credits: number;
  sections: CatalogSection[];
};

const ROUND_UNAVAILABLE_CODES = new Set(['WINDOW_CLOSED', 'COHORT_INELIGIBLE', 'ROUND_NOT_FOUND']);

/** Registrations that still bind a seat; matches the schedule page convention. */
const ACTIVE_ENROLLMENT_STATUSES = new Set(['ENROLLED', 'CONFIRMED', 'PENDING']);

export default function RegisterPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth(['STUDENT']);
  const { confirm, confirmationDialog } = useConfirmationDialog();
  const { messages, formatNumber } = useI18n();
  const copy = messages.courseRegistration;
  const [sections, setSections] = useState<CatalogSection[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [roundOpen, setRoundOpen] = useState(true);
  const [courseCodeSearch, setCourseCodeSearch] = useState('');
  const [courseNameSearch, setCourseNameSearch] = useState('');
  const [selectedCourseId, setSelectedCourseId] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pending, setPending] = useState('');
  const loadGeneration = useRef(0);

  /** Loads the student's enrollments, active registration rounds, and section catalog. */
  const load = useCallback(async () => {
    const generation = ++loadGeneration.current;
    setLoading(true);
    setError('');
    try {
      const enrollmentData = await enrollmentsApi.getMyEnrollments();
      if (generation !== loadGeneration.current) return;
      setEnrollments(enrollmentData);
      try {
        const rounds = await registrationApi.rounds();
        if (generation !== loadGeneration.current) return;
        const open = rounds.some((round) => round.status === 'OPEN');
        setRoundOpen(open);
        if (!open) {
          setSections([]);
          return;
        }
        const catalog = await registrationApi.sections();
        if (generation !== loadGeneration.current) return;
        setSections(catalog);
      } catch (catalogError) {
        if (generation !== loadGeneration.current) return;
        if (ROUND_UNAVAILABLE_CODES.has(campusErrorCode(catalogError) ?? '')) {
          setRoundOpen(false);
          setSections([]);
          return;
        }
        throw catalogError;
      }
    } catch {
      if (generation !== loadGeneration.current) return;
      setError(copy.loadFailed);
    } finally {
      if (generation === loadGeneration.current) {
        setLoading(false);
      }
    }
  }, [copy.loadFailed]);

  useEffect(() => {
    if (hasAccess) {
      void load();
    }
  }, [hasAccess, load]);

  const enrollmentBySection = useMemo(() => {
    const map = new Map<string, Enrollment>();
    for (const enrollment of enrollments) {
      // Only an active registration claims a catalog slot. Completed or
      // withdrawn history from earlier semesters shares section ids with the
      // current catalog and must never surface as a drop action here.
      if (!ACTIVE_ENROLLMENT_STATUSES.has(enrollment.status)) continue;
      map.set(enrollment.sectionId, enrollment);
    }
    return map;
  }, [enrollments]);

  const filteredSections = useMemo(() => {
    const codeQuery = courseCodeSearch.trim().toLowerCase();
    const nameQuery = courseNameSearch.trim().toLowerCase();
    if (!codeQuery && !nameQuery) {
      return sections;
    }
    return sections.filter((section) =>
      (!codeQuery ||
        `${section.courseCode} ${section.sectionNumber}`.toLowerCase().includes(codeQuery)) &&
      (!nameQuery || section.courseName.toLowerCase().includes(nameQuery)),
    );
  }, [courseCodeSearch, courseNameSearch, sections]);

  const courseGroups = useMemo(() => {
    const groups = new Map<string, CatalogCourseGroup>();
    for (const section of filteredSections) {
      const group =
        groups.get(section.courseId) ??
        ({
          courseId: section.courseId,
          courseCode: section.courseCode,
          courseName: section.courseName,
          credits: section.credits,
          sections: [],
        } satisfies CatalogCourseGroup);
      group.sections.push(section);
      groups.set(section.courseId, group);
    }
    return Array.from(groups.values());
  }, [filteredSections]);

  /**
   * Two-level selection: the explicit pick wins while it survives the search
   * filters, otherwise the first visible course is selected automatically so a
   * section list is always on screen once any course matches.
   */
  const activeCourse =
    courseGroups.find((group) => group.courseId === selectedCourseId) ?? courseGroups[0] ?? null;

  const registered = enrollments.filter((item) => ACTIVE_ENROLLMENT_STATUSES.has(item.status));
  const totalRegisteredCredits = registered.reduce(
    (sum, item) => sum + (item.section?.course?.credits ?? 0),
    0,
  );

  /** Human-readable section label so confirmation dialogs name the exact class. */
  const sectionLabel = (
    section: { sectionNumber?: string; courseCode?: string; course?: { code?: string } } | undefined,
    fallback: string,
  ) => {
    const code = section?.courseCode ?? section?.course?.code;
    const number = section?.sectionNumber;
    // Catalog section numbers often already carry the course prefix (SE013-01).
    if (number && (!code || !number.startsWith(code))) return `${code}-${number}`;
    return number || code || fallback;
  };

  /** Confirms and submits one section enrollment, then refreshes the live catalog. */
  const register = async (sectionId: string) => {
    const section = sections.find((item) => item.id === sectionId);
    const ok = await confirm({
      title: copy.confirmRegister,
      message: copy.confirmRegisterMessage.replace(
        '{section}',
        sectionLabel(section, sectionId),
      ),
      confirmText: copy.register,
    });
    if (!ok) return;
    setPending(sectionId);
    try {
      await enrollmentsApi.enroll(sectionId);
      toast.success(copy.success);
      await load();
    } catch (cause) {
      toast.error(campusCodeMessage(cause, messages.common.campusErrors));
    } finally {
      setPending('');
    }
  };

  /** Confirms and drops one active enrollment, then refreshes the live schedule data. */
  const drop = async (enrollment: Enrollment) => {
    const ok = await confirm({
      title: copy.confirmDrop,
      message: copy.confirmDropMessage.replace(
        '{section}',
        sectionLabel(enrollment.section, enrollment.sectionId),
      ),
      confirmText: copy.drop,
    });
    if (!ok) return;
    setPending(enrollment.id);
    try {
      await enrollmentsApi.drop(enrollment.id);
      toast.success(copy.success);
      await load();
    } catch (cause) {
      toast.error(campusCodeMessage(cause, messages.common.campusErrors));
    } finally {
      setPending('');
    }
  };

  if (authLoading) {
    return <LoadingState label={messages.common.states.loadingContent} />;
  }
  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }
  if (loading) {
    return <LoadingState label={messages.common.states.loadingContent} />;
  }
  if (error) {
    return <ErrorState title={copy.loadFailed} description={error} onRetry={() => void load()} />;
  }

  return (
    <div className="registration-workspace space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />
      {!roundOpen ? (
        <EmptyState icon={BookOpen} title={copy.roundUnavailable} description={copy.exportUnavailable} />
      ) : (
        <div className="grid min-w-0 gap-6 lg:grid-cols-12">
          <div className="min-w-0 space-y-6 lg:col-span-9">
            <Card>
              <CardContent className="grid gap-3 p-4 md:grid-cols-2">
                <label className="min-w-0 space-y-2">
                  <span className="text-sm font-medium text-foreground">{copy.searchByCode}</span>
                  <Input
                    value={courseCodeSearch}
                    onChange={(event) => setCourseCodeSearch(event.target.value)}
                    placeholder={copy.courseCodePlaceholder}
                    icon={<Search className="h-4 w-4" />}
                  />
                </label>
                <label className="min-w-0 space-y-2">
                  <span className="text-sm font-medium text-foreground">{copy.searchByName}</span>
                  <Input
                    value={courseNameSearch}
                    onChange={(event) => setCourseNameSearch(event.target.value)}
                    placeholder={copy.courseNamePlaceholder}
                    icon={<Search className="h-4 w-4" />}
                  />
                </label>
              </CardContent>
            </Card>
            <Card className="min-w-0 overflow-hidden">
              <CardHeader className="border-b border-border/70 bg-[hsl(var(--surface-alt))]">
                <CardTitle className="flex items-center gap-2 text-base">
                  <BookOpen className="h-4 w-4 text-primary" />
                  {copy.sectionCount.replace('{count}', formatNumber(filteredSections.length))}
                </CardTitle>
              </CardHeader>
              {!activeCourse ? (
                <EmptyState icon={BookOpen} title={copy.emptyTitle} description={copy.emptyDescription} />
              ) : (
              <div className="min-w-0">
                <div className="space-y-2 border-b border-border/70 p-3 sm:p-4">
                  <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    {copy.courseListLabel}
                  </p>
                  <div className="space-y-1.5">
                    {courseGroups.map((group) => {
                      const selected = group.courseId === activeCourse.courseId;
                      const openSeats = group.sections.reduce(
                        (total, section) => total + section.remainingSeats,
                        0,
                      );
                      return (
                        <button
                          key={group.courseId}
                          type="button"
                          onClick={() => setSelectedCourseId(group.courseId)}
                          aria-pressed={selected}
                          className={`flex w-full items-center justify-between gap-3 rounded-md border px-3 py-2.5 text-left transition ${
                            selected
                              ? 'border-primary/50 bg-primary/10'
                              : 'border-border/70 bg-card hover:border-primary/30 hover:bg-secondary/50'
                          }`}
                        >
                          <span className="min-w-0">
                            <span className="block truncate text-sm font-semibold text-foreground">
                              {group.courseCode}
                            </span>
                            <span className="block truncate text-xs text-muted-foreground">
                              {group.courseName}
                            </span>
                          </span>
                          <span className="flex shrink-0 flex-wrap items-center justify-end gap-1">
                            <span className="text-xs text-muted-foreground">
                              {formatNumber(group.credits)} {copy.creditsUnit}
                            </span>
                            <span className="rounded-full bg-secondary px-2 py-0.5 text-[11px] font-medium text-muted-foreground">
                              {copy.groupSectionCount.replace('{count}', formatNumber(group.sections.length))}
                            </span>
                            <span
                              className={`rounded-full px-2 py-0.5 text-[11px] font-medium ${
                                openSeats > 0
                                  ? 'bg-primary/10 text-status-success'
                                  : 'bg-secondary text-muted-foreground'
                              }`}
                            >
                              {copy.seatsOpen.replace('{count}', formatNumber(openSeats))}
                            </span>
                          </span>
                        </button>
                      );
                    })}
                  </div>
                </div>
                <div className="space-y-3 p-3 sm:p-4">
                  <div className="flex flex-wrap items-baseline justify-between gap-2">
                    <p className="text-sm font-semibold text-foreground">
                      {activeCourse.courseCode} - {activeCourse.courseName}
                    </p>
                    <p className="text-xs text-muted-foreground">
                      {copy.groupSectionCount.replace('{count}', formatNumber(activeCourse.sections.length))}
                    </p>
                  </div>
                  {activeCourse.sections.map((section) => {
                    const enrollment = enrollmentBySection.get(section.id);
                    const seats = section.remainingSeats;
                    return (
                      <article key={section.id} className="rounded-md border border-border/70 bg-card p-4 shadow-sm">
                        <div className="flex flex-wrap items-center gap-2">
                          <p className="font-semibold text-foreground">
                            {copy.columns.section} {section.sectionNumber}
                          </p>
                          {section.alreadyEnrolled ? (
                            <span className="rounded-full border border-primary/30 bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                              {copy.registered}
                            </span>
                          ) : null}
                          {section.scheduleConflict ? (
                            <span className="rounded-full border border-destructive/30 bg-destructive/10 px-2 py-0.5 text-xs font-medium text-destructive">
                              {copy.conflictBadge}
                            </span>
                          ) : null}
                        </div>
                        <p
                          className={`mt-2 text-sm font-semibold ${
                            seats > 0 ? 'text-status-success' : 'text-muted-foreground'
                          }`}
                        >
                          {seats > 0 ? `${formatNumber(seats)} ${copy.seatsLeft}` : copy.full}
                        </p>
                        <div className="mt-4 flex justify-end">
                          {enrollment ? (
                            <Button
                              type="button"
                              size="sm"
                              variant="outline"
                              onClick={() => void drop(enrollment)}
                              disabled={pending === enrollment.id}
                            >
                              <CheckCircle2 className="mr-2 h-4 w-4" />
                              {pending === enrollment.id ? copy.working : copy.drop}
                            </Button>
                          ) : (
                            <Button
                              type="button"
                              size="sm"
                              variant="registration"
                              onClick={() => void register(section.id)}
                              disabled={!roundOpen || seats === 0 || pending === section.id || section.status !== 'OPEN'}
                            >
                              <UserPlus className="mr-2 h-4 w-4" />
                              {pending === section.id ? copy.working : copy.register}
                            </Button>
                          )}
                        </div>
                      </article>
                    );
                  })}
                </div>
              </div>
            )}
          </Card>
        </div>
        <Card className="min-w-0 lg:col-span-3">
          <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0 pb-3">
            <CardTitle>{copy.enrolledRail}</CardTitle>
            {registered.length > 0 ? (
              <span className="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
                {formatNumber(totalRegisteredCredits)} {copy.creditsUnit}
              </span>
            ) : null}
          </CardHeader>
          <CardContent className="space-y-3">
            {registered.length === 0 ? (
              <p className="text-sm text-muted-foreground">{copy.railEmpty}</p>
            ) : (
              registered.map((item) => {
                const section = item.section;
                return (
                  <div key={item.id} className="rounded-md border border-border/70 p-3 text-sm transition-colors hover:border-primary/30">
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0">
                        <div className="font-semibold text-foreground">
                          {section?.course?.code ?? item.sectionId}
                          {section ? ` - ${section.sectionNumber}` : ''}
                        </div>
                        {section?.course?.name ? (
                          <div className="mt-1 truncate text-muted-foreground">{section.course.name}</div>
                        ) : null}
                        {section?.course?.credits ? (
                          <div className="mt-1 text-xs text-muted-foreground">
                            {formatNumber(section.course.credits)} {copy.creditsUnit}
                          </div>
                        ) : null}
                      </div>
                      <Button
                        type="button"
                        variant="destructive"
                        size="icon"
                        className="h-8 w-8 shrink-0"
                        aria-label={copy.drop}
                        title={copy.drop}
                        onClick={() => void drop(item)}
                        disabled={pending === item.id}
                      >
                        <Trash2 className="h-4 w-4" />
                      </Button>
                    </div>
                  </div>
                );
              })
            )}
          </CardContent>
        </Card>
      </div>
      )}
      {confirmationDialog}
    </div>
  );
}
