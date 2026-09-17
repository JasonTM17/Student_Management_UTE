'use client';

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { AlertCircle, BookOpen, CheckCircle2, Clock3, Search, Send, ShieldCheck, Trash2, UserPlus } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { enrollmentsApi, registrationApi, type CreditLimitApplication } from '@/lib/api';
import type { Enrollment } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
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
  const [creditLimit, setCreditLimit] = useState(28);
  const [currentRoundId, setCurrentRoundId] = useState('');
  const [creditApplication, setCreditApplication] = useState<CreditLimitApplication | null>(null);
  const [applicationReason, setApplicationReason] = useState('');
  const [applicationBusy, setApplicationBusy] = useState(false);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pending, setPending] = useState('');
  // Feedback item 10: enrollment failures also render inline above the
  // catalog instead of vanishing with the toast.
  const [actionError, setActionError] = useState('');
  const loadGeneration = useRef(0);

  /** Loads the student's enrollments, active registration rounds, and section catalog. */
  const load = useCallback(async () => {
    const generation = ++loadGeneration.current;
    setLoading(true);
    setError('');
    try {
      const [enrollmentData, rounds] = await Promise.all([
        enrollmentsApi.getMyEnrollments(),
        registrationApi.rounds(),
      ]);
      if (generation !== loadGeneration.current) return;
      setEnrollments(enrollmentData);
      try {
        const open = rounds.some((round) => round.status === 'OPEN');
        setRoundOpen(open);
        const currentRound = rounds.find((round) => round.status === 'OPEN');
        setCurrentRoundId(currentRound?.id ?? '');
        if (!open) {
          setCreditLimit(28);
          setCreditApplication(null);
          setSections([]);
          return;
        }
        if (!currentRound) return;
        const [eligibility, application, catalog] = await Promise.all([
          registrationApi.eligibility({
            semesterId: currentRound.semesterId,
            roundId: currentRound.id,
          }),
          registrationApi.creditLimitApplication(currentRound.id),
          registrationApi.sections({
            semesterId: currentRound.semesterId,
            roundId: currentRound.id,
          }),
        ]);
        if (generation !== loadGeneration.current) return;
        setCreditLimit(Math.min(30, Math.max(1, eligibility.creditLimit || 28)));
        setCreditApplication(application);
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

  const enrolledCourseIds = useMemo(() => {
    const set = new Set<string>();
    for (const enrollment of enrollments) {
      if (!ACTIVE_ENROLLMENT_STATUSES.has(enrollment.status)) continue;
      if (enrollment.section?.courseId) {
        set.add(enrollment.section.courseId);
      }
    }
    return set;
  }, [enrollments]);

  /**
   * The catalog used to dump every open section on load. It now stays behind
   * the search box so the page opens on a prompt instead of a wall of classes.
   */
  const hasSearchQuery = courseCodeSearch.trim() !== '' || courseNameSearch.trim() !== '';

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
  const activeCourse = hasSearchQuery
    ? (courseGroups.find((group) => group.courseId === selectedCourseId) ?? courseGroups[0] ?? null)
    : null;

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
    const creditsToAdd = section?.credits ?? 0;
    if (totalRegisteredCredits + creditsToAdd > creditLimit) {
      const message = copy.creditLimitExceeded.replace('{limit}', String(creditLimit));
      setActionError(message);
      toast.error(message);
      return;
    }
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
    setActionError('');
    try {
      await enrollmentsApi.enroll(sectionId);
      toast.success(copy.success);
      await load();
    } catch (cause) {
      const message = campusCodeMessage(cause, messages.common.campusErrors);
      setActionError(message);
      toast.error(message);
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
    setActionError('');
    try {
      await enrollmentsApi.drop(enrollment.id);
      toast.success(copy.success);
      await load();
    } catch (cause) {
      const message = campusCodeMessage(cause, messages.common.campusErrors);
      setActionError(message);
      toast.error(message);
    } finally {
      setPending('');
    }
  };

  const submitCreditLimitApplication = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const reason = applicationReason.trim();
    if (!currentRoundId || reason.length < 20) {
      const message = copy.applicationReasonInvalid;
      setActionError(message);
      toast.error(message);
      return;
    }

    setApplicationBusy(true);
    setActionError('');
    try {
      const application = await registrationApi.submitCreditLimitApplication(currentRoundId, reason);
      setCreditApplication(application);
      setApplicationReason('');
      // A pending request never changes the effective limit. Only the PĐT
      // approval returned by the API may move this student from 28 to 30.
      setCreditLimit(28);
      toast.success(copy.applicationSubmitted);
    } catch (cause) {
      const code = campusErrorCode(cause);
      const message = code === 'CREDIT_LIMIT_APPLICATION_EXISTS'
        ? copy.applicationAlreadyExists
        : campusCodeMessage(cause, messages.common.campusErrors);
      setActionError(message);
      toast.error(message);
    } finally {
      setApplicationBusy(false);
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

  const applicationStatus = creditApplication?.status === 'APPROVED'
    ? {
        label: copy.applicationStatusApproved,
        description: copy.applicationApproved,
        icon: CheckCircle2,
        tone: 'text-status-success-foreground bg-status-success/12',
      }
    : creditApplication?.status === 'REJECTED'
      ? {
          label: copy.applicationStatusRejected,
          description: copy.applicationRejected,
          icon: AlertCircle,
          tone: 'text-status-danger-foreground bg-status-danger/12',
        }
      : creditApplication
        ? {
            label: copy.applicationStatusPending,
            description: copy.applicationPending,
            icon: Clock3,
            tone: 'text-status-warning-foreground bg-status-warning/12',
          }
        : null;
  const canSubmitApplication = !creditApplication || creditApplication.status === 'REJECTED';

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
            <Card className="overflow-hidden border-primary/20 bg-gradient-to-br from-primary/[0.06] via-card to-card">
              <CardHeader className="border-b border-primary/10 pb-4">
                <CardTitle className="flex items-center gap-2 text-base">
                  <ShieldCheck className="h-5 w-5 text-primary" aria-hidden="true" />
                  {copy.limitPolicyTitle}
                </CardTitle>
                <p className="text-sm leading-6 text-muted-foreground">{copy.limitPolicyDescription}</p>
              </CardHeader>
              <CardContent className="space-y-4 p-4">
                <div className="grid gap-3 sm:grid-cols-2">
                  <div className="rounded-xl border border-border/70 bg-card/80 p-4">
                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                      {copy.standardLimitLabel}
                    </p>
                    <p className="mt-2 text-2xl font-semibold text-foreground">
                      28 <span className="text-sm font-medium text-muted-foreground">{copy.creditsUnit}</span>
                    </p>
                  </div>
                  <div className="rounded-xl border border-primary/20 bg-primary/[0.05] p-4">
                    <p className="text-xs font-semibold uppercase tracking-[0.14em] text-primary">
                      {copy.approvedLimitLabel}
                    </p>
                    <p className="mt-2 text-2xl font-semibold text-foreground">
                      30 <span className="text-sm font-medium text-muted-foreground">{copy.creditsUnit}</span>
                    </p>
                    <p className="mt-1 text-xs leading-5 text-muted-foreground">{copy.approvedLimitHint}</p>
                  </div>
                </div>

                {applicationStatus ? (
                  <div className={`rounded-xl p-3 ${applicationStatus.tone}`} role="status" aria-live="polite">
                    <div className="flex items-start gap-3">
                      <applicationStatus.icon className="mt-0.5 h-5 w-5 shrink-0" aria-hidden="true" />
                      <div className="min-w-0 space-y-1">
                        <p className="font-semibold">{applicationStatus.label}</p>
                        <p className="text-sm leading-6 opacity-90">{applicationStatus.description}</p>
                        {creditApplication?.reviewerNote ? (
                          <p className="border-t border-current/15 pt-2 text-sm leading-6">
                            <span className="font-semibold">{copy.applicationReviewNote}: </span>
                            {creditApplication.reviewerNote}
                          </p>
                        ) : null}
                      </div>
                    </div>
                  </div>
                ) : null}

                {canSubmitApplication ? (
                  <form className="space-y-3 border-t border-border/70 pt-4" onSubmit={submitCreditLimitApplication}>
                    <div>
                      <h3 className="text-sm font-semibold text-foreground">{copy.applicationTitle}</h3>
                      <p className="mt-1 text-sm leading-6 text-muted-foreground">{copy.applicationDescription}</p>
                    </div>
                    <label htmlFor="credit-limit-application-reason" className="block text-sm font-medium text-foreground">
                      {copy.applicationReasonLabel}
                    </label>
                    <Textarea
                      id="credit-limit-application-reason"
                      value={applicationReason}
                      onChange={(event) => setApplicationReason(event.target.value)}
                      placeholder={copy.applicationReasonPlaceholder}
                      minLength={20}
                      maxLength={1000}
                      required
                      hint={`${copy.applicationReasonHint} ${applicationReason.length}/1000`}
                    />
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                      <p className="text-xs leading-5 text-muted-foreground">{copy.applicationOfficeOnly}</p>
                      <Button type="submit" disabled={applicationBusy}>
                        <Send className="mr-2 h-4 w-4" aria-hidden="true" />
                        {applicationBusy ? copy.applicationSubmitting : copy.applicationSubmit}
                      </Button>
                    </div>
                  </form>
                ) : null}
              </CardContent>
            </Card>
            {actionError ? (
              <div
                role="alert"
                aria-live="polite"
                className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive"
              >
                {actionError}
              </div>
            ) : null}
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
                  {hasSearchQuery
                    ? copy.sectionCount.replace('{count}', formatNumber(filteredSections.length))
                    : copy.searchPromptTitle}
                </CardTitle>
              </CardHeader>
              {!hasSearchQuery ? (
                <EmptyState
                  icon={Search}
                  title={copy.searchPromptTitle}
                  description={copy.searchPromptDescription}
                />
              ) : !activeCourse ? (
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
                                    ? 'bg-status-success/12 text-status-success-foreground'
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
                          ) : (() => {
                            const willExceedLimit = totalRegisteredCredits + (section.credits ?? 0) > creditLimit;
                            const isConflict = Boolean(section.scheduleConflict);
                            const alreadyHasCourse = enrolledCourseIds.has(section.courseId);
                            const isDisabled = !roundOpen || seats === 0 || pending === section.id || section.status !== 'OPEN' || willExceedLimit || isConflict || alreadyHasCourse;
                            const disabledTitle = willExceedLimit
                              ? copy.creditLimitExceeded.replace('{limit}', String(creditLimit))
                              : isConflict
                                ? 'Trùng thời khóa biểu với môn đã đăng ký'
                                : alreadyHasCourse
                                  ? 'Bạn đã đăng ký một lớp học phần khác của môn học này'
                                  : undefined;
                            return (
                              <Button
                                type="button"
                                size="sm"
                                variant="registration"
                                onClick={() => void register(section.id)}
                                disabled={isDisabled}
                                title={disabledTitle}
                              >
                                <UserPlus className="mr-2 h-4 w-4" />
                                {pending === section.id ? copy.working : copy.register}
                              </Button>
                            );
                          })()}
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
            <span className="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
              {formatNumber(totalRegisteredCredits)} / {formatNumber(creditLimit)} {copy.creditsUnit}
            </span>
          </CardHeader>
          <CardContent className="space-y-3">
            <div className="rounded-lg border border-border/70 bg-secondary/30 p-3 space-y-2">
              <div className="flex items-center justify-between text-xs">
                <span className="font-medium text-muted-foreground">Định mức học kỳ:</span>
                <span className="font-bold text-foreground">
                  {totalRegisteredCredits}/{creditLimit} {copy.creditsUnit}
                </span>
              </div>
              <div className="h-2 w-full overflow-hidden rounded-full bg-secondary">
                <div
                  className={`h-full transition-all duration-300 ${
                    totalRegisteredCredits >= creditLimit
                      ? 'bg-amber-500'
                      : 'bg-primary'
                  }`}
                  style={{ width: `${Math.min(100, (totalRegisteredCredits / creditLimit) * 100)}%` }}
                />
              </div>
              <p className="text-[11px] text-muted-foreground">
                {totalRegisteredCredits >= creditLimit
                  ? copy.creditLimitReached.replace('{limit}', String(creditLimit))
                  : copy.creditLimitRemaining.replace('{count}', String(creditLimit - totalRegisteredCredits))}
              </p>
            </div>
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
