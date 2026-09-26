'use client';

import { useCallback, useEffect, useMemo, useState, type FormEvent } from 'react';
import {
  AlertCircle,
  CalendarClock,
  CheckCircle2,
  ChevronDown,
  Clock3,
  Filter,
  Search,
  Send,
  ShieldCheck,
  Trash2,
  TriangleAlert,
  UserPlus,
} from 'lucide-react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import {
  enrollmentsApi,
  registrationApi,
  type CreditLimitApplication,
  type RegistrationCurriculumRelevance,
  type RegistrationSection,
} from '@/lib/api';
import type { Enrollment } from '@/types/api';
import { useApiQuery } from '@/lib/query';
import {
  countdownParts,
  formatCountdownClock,
  matchesCurriculumFilter,
  matchesSectionSearch,
  resolveRegistrationActionMessage,
  scheduleOverlaps,
  seatTier,
  sortSectionGroups,
  type CourseGroup,
  type CurriculumFilter,
  type RegistrationSectionSchedule,
  type SectionSortMode,
  type TimeWindow,
} from '@/lib/registration-browse';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Select } from '@/components/ui/select';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { WeeklyGrid, shortDayLabel, type WeeklyGridAgendaItem } from '@/components/schedule/weekly-grid';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { campusCodeMessage, campusErrorCode } from '@/lib/campus-error';
import { toast } from 'sonner';

const ROUND_UNAVAILABLE_CODES = new Set(['WINDOW_CLOSED', 'COHORT_INELIGIBLE', 'ROUND_NOT_FOUND']);

/** Registrations that still bind a seat; matches the schedule page convention. */
const ACTIVE_ENROLLMENT_STATUSES = new Set(['ENROLLED', 'CONFIRMED', 'PENDING']);

const CURRICULUM_FILTERS: CurriculumFilter[] = ['ALL', 'MANDATORY', 'ELECTIVE', 'OUTSIDE'];

const SEVEN_DAYS_MS = 7 * 24 * 60 * 60 * 1000;

/** Groups the catalog by course so each course header owns its section rows. */
function groupByCourse(sections: RegistrationSection[]): CourseGroup<RegistrationSection>[] {
  const groups = new Map<string, CourseGroup<RegistrationSection>>();
  for (const section of sections) {
    const group =
      groups.get(section.courseId) ??
      ({
        courseId: section.courseId,
        courseCode: section.courseCode,
        courseName: section.courseName,
        credits: section.credits,
        sections: [],
      } satisfies CourseGroup<RegistrationSection>);
    group.sections.push(section);
    groups.set(section.courseId, group);
  }
  return Array.from(groups.values());
}

const chipTone = (pressed: boolean) =>
  `inline-flex items-center gap-1.5 rounded-full border px-3 py-1.5 text-xs font-semibold transition focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 ${
    pressed
      ? 'border-primary/60 bg-primary/10 text-primary'
      : 'border-border/80 bg-card text-muted-foreground hover:border-primary/30 hover:text-foreground'
  }`;

const SEAT_TONE: Record<string, string> = {
  open: 'bg-status-success/12 text-status-success-foreground',
  low: 'bg-status-warning/12 text-status-warning-foreground',
  full: 'bg-secondary text-muted-foreground',
};

export default function RegisterPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth(['STUDENT']);
  const { confirm, confirmationDialog } = useConfirmationDialog();
  const { locale, messages, formatNumber, formatDate } = useI18n();
  const copy = messages.courseRegistration;
  const queryClient = useQueryClient();

  // ---- browse state (kept across mutations; no full reload anywhere) ----
  const [searchText, setSearchText] = useState('');
  const [curriculumFilter, setCurriculumFilter] = useState<CurriculumFilter>('ALL');
  const [seatsOnly, setSeatsOnly] = useState(false);
  const [noConflictOnly, setNoConflictOnly] = useState(false);
  const [sortMode, setSortMode] = useState<SectionSortMode>('code');
  const [previewSectionId, setPreviewSectionId] = useState('');
  const [collapsedCourseIds, setCollapsedCourseIds] = useState<Set<string>>(new Set());
  const [policyOpen, setPolicyOpen] = useState(false);
  const [applicationReason, setApplicationReason] = useState('');
  const [applicationBusy, setApplicationBusy] = useState(false);
  const [pending, setPending] = useState('');
  // Feedback item 10: enrollment failures also render inline above the
  // catalog instead of vanishing with the toast.
  const [actionError, setActionError] = useState('');

  // On desktop the credit-limit policy stays expanded above the catalog; on
  // mobile the catalog leads and the policy collapses behind a toggle.
  useEffect(() => {
    const mediaQuery = window.matchMedia('(min-width: 1024px)');
    const apply = () => setPolicyOpen(mediaQuery.matches);
    apply();
    mediaQuery.addEventListener('change', apply);
    return () => mediaQuery.removeEventListener('change', apply);
  }, []);

  // ---- server state (react-query; per-mutation invalidation) ----
  const roundsQuery = useApiQuery(['registration', 'rounds'], () => registrationApi.rounds(), {
    enabled: hasAccess,
    staleTime: 30_000,
  });

  const now = Date.now();
  // Re-evaluate the live-round pick once per minute so a window rollover is
  // noticed without a per-second recompute.
  const nowBucket = Math.floor(now / 60_000);
  const currentRound = useMemo(() => {
    const rounds = roundsQuery.data ?? [];
    const withinWindow = (round: { status: string; windowStart: string; windowEnd: string }) =>
      round.status === 'OPEN'
      && new Date(round.windowStart).getTime() <= now
      && now <= new Date(round.windowEnd).getTime();
    const liveRounds = rounds.filter(withinWindow);
    // The banner must speak for the live REGISTRATION round of the current
    // semester when several rounds are open at once (e.g. add/drop windows).
    return liveRounds.find((round) => round.kind === 'REGISTRATION') ?? liveRounds[0] ?? null;
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [roundsQuery.data, nowBucket]);

  const roundId = currentRound?.id;
  const semesterId = currentRound?.semesterId;

  const eligibilityQuery = useApiQuery(
    ['registration', 'eligibility', roundId ?? ''],
    () => registrationApi.eligibility({ semesterId, roundId }),
    { enabled: hasAccess && Boolean(roundId), staleTime: 30_000 },
  );
  const sectionsQuery = useApiQuery(
    ['registration', 'sections', roundId ?? ''],
    () => registrationApi.sections({ semesterId, roundId }),
    { enabled: hasAccess && Boolean(roundId), staleTime: 30_000 },
  );
  const enrollmentsQuery = useApiQuery(
    ['registration', 'enrollments', semesterId ?? ''],
    () => enrollmentsApi.getMyEnrollments(semesterId),
    { enabled: hasAccess && Boolean(semesterId), staleTime: 30_000 },
  );
  const summaryQuery = useApiQuery(
    ['registration', 'summary', semesterId ?? ''],
    () => registrationApi.summary(semesterId),
    { enabled: hasAccess && Boolean(semesterId), staleTime: 30_000 },
  );
  const applicationQuery = useApiQuery(
    ['registration', 'credit-limit-application', roundId ?? ''],
    () => registrationApi.creditLimitApplication(roundId ?? ''),
    { enabled: hasAccess && Boolean(roundId), staleTime: 60_000 },
  );

  const eligibilityErrorCode = eligibilityQuery.error ? campusErrorCode(eligibilityQuery.error) : undefined;
  const roundOpen =
    Boolean(roundId)
    && !(eligibilityQuery.isError && ROUND_UNAVAILABLE_CODES.has(eligibilityErrorCode ?? ''))
    && eligibilityQuery.data?.eligible !== false;

  const sections = useMemo(() => sectionsQuery.data ?? [], [sectionsQuery.data]);
  const enrollments = useMemo(() => enrollmentsQuery.data ?? [], [enrollmentsQuery.data]);

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

  const registered = enrollments.filter((item) => ACTIVE_ENROLLMENT_STATUSES.has(item.status));
  const totalRegisteredCredits = registered.reduce(
    (sum, item) => sum + (item.section?.course?.credits ?? 0),
    0,
  );

  // Render exactly the limit the API grants — never a local default. The
  // approved cap arrives via eligibility (round-specific) and is mirrored in
  // the term summary; when neither carries it we say so instead of guessing.
  const eligibilityData = eligibilityQuery.data;
  const creditLimit =
    eligibilityData && typeof eligibilityData.creditLimit === 'number' && eligibilityData.creditLimit > 0
      ? eligibilityData.creditLimit
      : summaryQuery.data && typeof summaryQuery.data.creditLimit === 'number' && summaryQuery.data.creditLimit > 0
        ? summaryQuery.data.creditLimit
        : null;

  // ---- filters (all client-side, live counts) ----
  const searchedSections = useMemo(
    () => sections.filter((section) => matchesSectionSearch(section, searchText)),
    [sections, searchText],
  );

  const secondaryFiltered = useMemo(
    () =>
      searchedSections.filter(
        (section) => (!seatsOnly || section.remainingSeats > 0) && (!noConflictOnly || !section.scheduleConflict),
      ),
    [searchedSections, seatsOnly, noConflictOnly],
  );

  const curriculumCounts = useMemo(() => {
    const counts = new Map<CurriculumFilter, number>();
    for (const filter of CURRICULUM_FILTERS) {
      counts.set(filter, searchedSections.filter((section) => matchesCurriculumFilter(section, filter)).length);
    }
    return counts;
  }, [searchedSections]);

  const seatsCount = useMemo(
    () => searchedSections.filter((section) => section.remainingSeats > 0).length,
    [searchedSections],
  );
  const noConflictCount = useMemo(
    () => searchedSections.filter((section) => !section.scheduleConflict).length,
    [searchedSections],
  );

  const filteredSections = useMemo(
    () => secondaryFiltered.filter((section) => matchesCurriculumFilter(section, curriculumFilter)),
    [secondaryFiltered, curriculumFilter],
  );

  const courseGroups = useMemo(
    () => sortSectionGroups(groupByCourse(filteredSections), sortMode),
    [filteredSections, sortMode],
  );

  const sectionsById = useMemo(() => {
    const map = new Map<string, RegistrationSection>();
    for (const section of sections) {
      map.set(section.id, section);
    }
    return map;
  }, [sections]);

  // ---- mutations: optimistic row/seat/rail updates, reconciled per response ----
  const rollbackKeys = useCallback(
    (round: { id: string; semesterId: string }) => ({
      sections: ['registration', 'sections', round.id] as const,
      enrollments: ['registration', 'enrollments', round.semesterId] as const,
    }),
    [],
  );

  const patchSection = (sectionId: string, patch: (section: RegistrationSection) => RegistrationSection) => {
    if (!roundId) return;
    queryClient.setQueryData<RegistrationSection[]>(['registration', 'sections', roundId], (old) =>
      (old ?? []).map((section) => (section.id === sectionId ? patch(section) : section)),
    );
  };

  const enrollMutation = useMutation({
    mutationFn: (sectionId: string) => enrollmentsApi.enroll(sectionId, locale),
    onMutate: async (sectionId) => {
      if (!roundId || !semesterId) return undefined;
      await queryClient.cancelQueries({ queryKey: ['registration'] });
      const keys = rollbackKeys({ id: roundId, semesterId });
      const previousSections = queryClient.getQueryData<RegistrationSection[]>(keys.sections);
      const previousEnrollments = queryClient.getQueryData<Enrollment[]>(keys.enrollments);
      patchSection(sectionId, (section) => ({
        ...section,
        alreadyEnrolled: true,
        enrolledCount: section.enrolledCount + 1,
        remainingSeats: Math.max(0, section.remainingSeats - 1),
      }));
      // A pending placeholder keeps the rail progress moving during the
      // round-trip; the success path swaps it for the server record.
      const placeholderSection = sectionsById.get(sectionId);
      const placeholder: Enrollment = {
        id: `pending:${sectionId}`,
        studentId: '',
        sectionId,
        semesterId,
        status: 'PENDING',
        enrolledAt: new Date().toISOString(),
        gradeStatus: 'NOT_GRADED',
        createdAt: new Date().toISOString(),
        section: placeholderSection
          ? {
              id: placeholderSection.id,
              sectionNumber: placeholderSection.sectionNumber,
              courseId: placeholderSection.courseId,
              semesterId,
              capacity: placeholderSection.capacity,
              enrolledCount: placeholderSection.enrolledCount + 1,
              status: 'OPEN',
              createdAt: '',
              course: {
                id: placeholderSection.courseId,
                code: placeholderSection.courseCode,
                name: placeholderSection.courseName,
                nameEn: placeholderSection.courseName,
                nameVi: placeholderSection.courseName,
                credits: placeholderSection.credits,
                createdAt: '',
                departmentId: '',
                isActive: true,
              },
            }
          : undefined,
      };
      queryClient.setQueryData<Enrollment[]>(keys.enrollments, (old) => [placeholder, ...(old ?? [])]);
      return { keys, previousSections, previousEnrollments };
    },
    onSuccess: (enrollment, sectionId, context) => {
      if (context) {
        queryClient.setQueryData<Enrollment[]>(context.keys.enrollments, (old) =>
          (old ?? [])
            .filter((item) => item.id !== `pending:${sectionId}` && item.id !== enrollment.id)
            .concat(enrollment),
        );
      }
      void queryClient.invalidateQueries({ queryKey: ['registration', 'sections'] });
      void queryClient.invalidateQueries({ queryKey: ['registration', 'enrollments'] });
      void queryClient.invalidateQueries({ queryKey: ['registration', 'summary'] });
    },
    onError: (cause, _sectionId, context) => {
      if (context) {
        if (context.previousSections) {
          queryClient.setQueryData(context.keys.sections, context.previousSections);
        }
        if (context.previousEnrollments) {
          queryClient.setQueryData(context.keys.enrollments, context.previousEnrollments);
        }
      }
    },
  });

  const dropMutation = useMutation({
    mutationFn: (enrollmentId: string) => enrollmentsApi.drop(enrollmentId),
    onMutate: async (enrollmentId) => {
      if (!roundId || !semesterId) return undefined;
      await queryClient.cancelQueries({ queryKey: ['registration'] });
      const keys = rollbackKeys({ id: roundId, semesterId });
      const previousSections = queryClient.getQueryData<RegistrationSection[]>(keys.sections);
      const previousEnrollments = queryClient.getQueryData<Enrollment[]>(keys.enrollments);
      const target = (previousEnrollments ?? []).find((item) => item.id === enrollmentId);
      if (target) {
        patchSection(target.sectionId, (section) => ({
          ...section,
          alreadyEnrolled: false,
          enrolledCount: Math.max(0, section.enrolledCount - 1),
          remainingSeats: section.remainingSeats + 1,
        }));
      }
      queryClient.setQueryData<Enrollment[]>(keys.enrollments, (old) =>
        (old ?? []).filter((item) => item.id !== enrollmentId),
      );
      return { keys, previousSections, previousEnrollments };
    },
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['registration', 'sections'] });
      void queryClient.invalidateQueries({ queryKey: ['registration', 'enrollments'] });
      void queryClient.invalidateQueries({ queryKey: ['registration', 'summary'] });
    },
    onError: (_cause, _enrollmentId, context) => {
      if (context) {
        if (context.previousSections) {
          queryClient.setQueryData(context.keys.sections, context.previousSections);
        }
        if (context.previousEnrollments) {
          queryClient.setQueryData(context.keys.enrollments, context.previousEnrollments);
        }
      }
    },
  });

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

  /** Maps backend codes to the registration-specific copy, then falls back. */
  const actionMessage = useCallback(
    (cause: unknown) => {
      const specific = resolveRegistrationActionMessage(
        campusErrorCode(cause),
        messages.courseRegistration.errorCodes,
      );
      return specific ?? campusCodeMessage(cause, messages.common.campusErrors);
    },
    [messages],
  );

  /** Confirms and submits one section enrollment without a page reload. */
  const register = async (sectionId: string) => {
    const section = sectionsById.get(sectionId);
    if (!section) return;
    if (creditLimit !== null && totalRegisteredCredits + section.credits > creditLimit) {
      const message = copy.creditLimitExceeded.replace('{limit}', formatNumber(creditLimit));
      setActionError(message);
      toast.error(message);
      return;
    }
    const ok = await confirm({
      title: copy.confirmRegister,
      message: copy.confirmRegisterMessage.replace('{section}', sectionLabel(section, sectionId)),
      confirmText: copy.register,
      // Soft policy: outside-curriculum classes warn but never block.
      warning: section.curriculumRelevance === 'OUTSIDE' ? copy.outsideWarningBanner : undefined,
    });
    if (!ok) return;
    setPending(sectionId);
    setActionError('');
    try {
      await enrollMutation.mutateAsync(sectionId);
      toast.success(copy.success);
    } catch (cause) {
      const message = actionMessage(cause);
      setActionError(message);
      toast.error(message);
    } finally {
      setPending('');
    }
  };

  /** Confirms and drops one active enrollment without a page reload. */
  const drop = async (enrollment: Enrollment) => {
    const ok = await confirm({
      title: copy.confirmDrop,
      message: copy.confirmDropMessage.replace(
        '{section}',
        sectionLabel(enrollment.section, enrollment.sectionId),
      ),
      confirmText: copy.drop,
      variant: 'destructive',
    });
    if (!ok) return;
    setPending(enrollment.id);
    setActionError('');
    try {
      await dropMutation.mutateAsync(enrollment.id);
      toast.success(copy.success);
    } catch (cause) {
      const message = actionMessage(cause);
      setActionError(message);
      toast.error(message);
    } finally {
      setPending('');
    }
  };

  const submitCreditLimitApplication = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const reason = applicationReason.trim();
    if (!roundId || reason.length < 20) {
      const message = copy.applicationReasonInvalid;
      setActionError(message);
      toast.error(message);
      return;
    }

    setApplicationBusy(true);
    setActionError('');
    try {
      const application = await registrationApi.submitCreditLimitApplication(roundId, reason);
      queryClient.setQueryData(['registration', 'credit-limit-application', roundId], application);
      setApplicationReason('');
      // A pending request never changes the effective limit, so this handler
      // must not touch the credit limit at all. The raised cap arrives via
      // eligibility once PĐT approves it.
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

  // ---- live countdown (ticks every second; hidden beyond seven days) ----
  const [nowTick, setNowTick] = useState(() => Date.now());
  useEffect(() => {
    if (!roundId) return undefined;
    const timer = window.setInterval(() => setNowTick(Date.now()), 1000);
    return () => window.clearInterval(timer);
  }, [roundId]);

  const windowEndMs = currentRound ? new Date(currentRound.windowEnd).getTime() : 0;
  const windowStartMs = currentRound ? new Date(currentRound.windowStart).getTime() : 0;
  const countdown = countdownParts(windowEndMs, nowTick);
  const roundClosed = Boolean(currentRound) && countdown === null;
  const showCountdown = countdown !== null && countdown.totalMs <= SEVEN_DAYS_MS;

  // ---- mini weekly timetable (registered + hovered preview) ----
  const timetableItems = useMemo<WeeklyGridAgendaItem[]>(() => {
    const items: WeeklyGridAgendaItem[] = [];
    for (const item of registered) {
      const section = item.section;
      section?.schedules?.forEach((schedule, index) => {
        items.push({
          id: `reg:${item.id}:${index}`,
          dayOfWeek: schedule.dayOfWeek,
          startTime: schedule.startTime,
          endTime: schedule.endTime,
          courseCode: section.course?.code ?? item.sectionId,
          sectionNumber: section.sectionNumber,
          courseName: section.course?.name,
          roomNumber: schedule.classroom?.roomNumber,
        });
      });
    }
    return items;
  }, [registered]);

  const previewTimetable = useMemo<WeeklyGridAgendaItem[]>(() => {
    const section = previewSectionId ? sectionsById.get(previewSectionId) : undefined;
    if (!section || section.alreadyEnrolled) return [];
    return (section.schedules ?? []).map((schedule, index) => ({
      id: `preview:${index}`,
      dayOfWeek: schedule.dayOfWeek,
      startTime: schedule.startTime,
      endTime: schedule.endTime,
      courseCode: section.courseCode,
      sectionNumber: section.sectionNumber,
      courseName: section.courseName,
      roomNumber: schedule.room ?? undefined,
    }));
  }, [previewSectionId, sectionsById]);

  const previewConflictIds = useMemo(() => {
    const conflicts = new Set<string>();
    if (previewTimetable.length === 0) return conflicts;
    const registeredWindows: TimeWindow[] = timetableItems.map((item) => ({
      dayOfWeek: item.dayOfWeek,
      startTime: item.startTime,
      endTime: item.endTime,
    }));
    previewTimetable.forEach((item) => {
      const candidate: RegistrationSectionSchedule | TimeWindow = {
        dayOfWeek: item.dayOfWeek,
        startTime: item.startTime,
        endTime: item.endTime,
        room: null,
        lecturer: null,
      };
      if (registeredWindows.some((window) => scheduleOverlaps(candidate, window))) {
        conflicts.add(item.id ?? '');
      }
    });
    return conflicts;
  }, [previewTimetable, timetableItems]);

  if (authLoading) {
    return <LoadingState label={messages.common.states.loadingContent} />;
  }
  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  const application: CreditLimitApplication | null = applicationQuery.data ?? null;
  const applicationStatus = application?.status === 'APPROVED'
    ? {
        label: copy.applicationStatusApproved,
        description: copy.applicationApproved,
        icon: CheckCircle2,
        tone: 'text-status-success-foreground bg-status-success/12',
      }
    : application?.status === 'REJECTED'
      ? {
          label: copy.applicationStatusRejected,
          description: copy.applicationRejected,
          icon: AlertCircle,
          tone: 'text-status-danger-foreground bg-status-danger/12',
        }
      : application
        ? {
            label: copy.applicationStatusPending,
            description: copy.applicationPending,
            icon: Clock3,
            tone: 'text-status-warning-foreground bg-status-warning/12',
          }
        : null;
  const canSubmitApplication = !application || application.status === 'REJECTED';

  const toggleCourse = (courseId: string) => {
    setCollapsedCourseIds((current) => {
      const next = new Set(current);
      if (next.has(courseId)) {
        next.delete(courseId);
      } else {
        next.add(courseId);
      }
      return next;
    });
  };

  const retryRegistrationData = () => {
    void roundsQuery.refetch();
    void eligibilityQuery.refetch();
    void sectionsQuery.refetch();
    void summaryQuery.refetch();
  };

  const workspaceError =
    (roundsQuery.isError ? roundsQuery.error : null)
    ?? (eligibilityQuery.isError && !ROUND_UNAVAILABLE_CODES.has(eligibilityErrorCode ?? '') ? eligibilityQuery.error : null)
    ?? (sectionsQuery.isError ? sectionsQuery.error : null);

  const policyCard = (
    <Card
      className="order-3 overflow-hidden border-primary/20 bg-gradient-to-br from-primary/[0.06] via-card to-card lg:order-first"
      data-testid="credit-limit-policy"
    >
      <button
        type="button"
        className="flex w-full items-center justify-between gap-3 border-b border-primary/10 px-4 py-3 text-left"
        aria-expanded={policyOpen}
        onClick={() => setPolicyOpen((open) => !open)}
      >
        <span className="flex min-w-0 items-center gap-2">
          <ShieldCheck className="h-5 w-5 shrink-0 text-primary" aria-hidden="true" />
          <span className="min-w-0">
            <span className="block text-base font-semibold text-foreground">{copy.limitPolicyTitle}</span>
            <span className="mt-0.5 block text-xs leading-5 text-muted-foreground sm:text-sm">
              {copy.limitPolicyDescription}
            </span>
          </span>
        </span>
        <ChevronDown
          className={`h-4 w-4 shrink-0 text-muted-foreground transition-transform ${policyOpen ? 'rotate-180' : ''}`}
          aria-hidden="true"
        />
      </button>
      {policyOpen ? (
        <CardContent className="space-y-4 p-4">
          <div className="grid gap-3 sm:grid-cols-2">
            <div className="rounded-xl border border-border/70 bg-card/80 p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-muted-foreground">
                {copy.termCreditLimitLabel}
              </p>
              <p className="mt-2 text-2xl font-semibold text-foreground">
                {creditLimit === null ? (
                  <span className="text-muted-foreground">{copy.creditLimitUnavailable}</span>
                ) : (
                  <>
                    {formatNumber(creditLimit)}{' '}
                    <span className="text-sm font-medium text-muted-foreground">{copy.creditsUnit}</span>
                  </>
                )}
              </p>
            </div>
            <div className="rounded-xl border border-primary/20 bg-primary/[0.05] p-4">
              <p className="text-xs font-semibold uppercase tracking-[0.14em] text-primary">
                {copy.approvedLimitLabel}
              </p>
              <p className="mt-2 text-2xl font-semibold text-foreground">
                {creditLimit === null ? (
                  <span className="text-muted-foreground">{copy.creditLimitUnavailable}</span>
                ) : (
                  <>
                    {formatNumber(creditLimit)}{' '}
                    <span className="text-sm font-medium text-muted-foreground">{copy.creditsUnit}</span>
                  </>
                )}
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
                  {application?.reviewerNote ? (
                    <p className="border-t border-current/15 pt-2 text-sm leading-6">
                      <span className="font-semibold">{copy.applicationReviewNote}: </span>
                      {application.reviewerNote}
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
      ) : null}
    </Card>
  );

  return (
    <div className="registration-workspace space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />
      {roundsQuery.isLoading ? (
        <LoadingState label={messages.common.states.loadingContent} />
      ) : roundsQuery.isError ? (
        <ErrorState
          title={copy.loadFailed}
          description={actionMessage(roundsQuery.error)}
          onRetry={() => void roundsQuery.refetch()}
        />
      ) : !roundOpen ? (
        <EmptyState icon={CalendarClock} title={copy.roundUnavailable} description={copy.roundUnavailableDescription} />
      ) : (
        <>
          {/* Sticky round banner with a live countdown to the window end. */}
          <div
            className="sticky top-[var(--portal-header-height)] z-20 -mx-1 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-xl border border-primary/25 bg-card/95 px-4 py-3 shadow-sm backdrop-blur"
            role="status"
          >
            <div className="flex min-w-0 items-center gap-2.5">
              <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-primary/10 text-primary">
                <CalendarClock className="h-5 w-5" aria-hidden="true" />
              </span>
              <span className="min-w-0">
                <span className="block truncate text-sm font-bold text-foreground">
                  {currentRound?.name}
                </span>
                <span className="block text-xs text-muted-foreground">
                  {copy.roundWindowHint
                    .replace('{start}', formatDate(new Date(windowStartMs)))
                    .replace('{end}', formatDate(new Date(windowEndMs)))}
                </span>
              </span>
            </div>
            <div className="ml-auto flex items-center gap-2">
              {roundClosed ? (
                <span className="rounded-md bg-secondary px-3 py-1.5 text-xs font-bold text-muted-foreground">
                  {copy.roundClosedBadge}
                </span>
              ) : showCountdown && countdown ? (
                <span
                  className="rounded-md bg-primary/10 px-3 py-1.5 text-xs font-bold tabular-nums text-primary"
                  aria-live="polite"
                >
                  {copy.roundCountdownPrefix}:{' '}
                  {countdown.days > 0
                    ? `${copy.roundCountdownDays.replace('{days}', formatNumber(countdown.days))} `
                    : ''}
                  {formatCountdownClock(countdown)}
                </span>
              ) : null}
            </div>
          </div>

          {workspaceError ? (
            <ErrorState
              title={copy.loadFailed}
              description={actionMessage(workspaceError)}
              onRetry={retryRegistrationData}
            />
          ) : null}

          <div className="grid min-w-0 grid-cols-1 gap-6 lg:grid-cols-12">
            {/* Left column: search + catalog lead; the credit-limit card follows on mobile. */}
            <div className="flex min-w-0 flex-col gap-6 lg:col-span-9">
              <Card className="order-1 min-w-0 overflow-hidden">
                <CardContent className="space-y-3 p-4">
                  <label className="block min-w-0 space-y-2">
                    <span className="text-sm font-medium text-foreground">{copy.searchLabel}</span>
                    <Input
                      value={searchText}
                      onChange={(event) => setSearchText(event.target.value)}
                      placeholder={copy.searchPlaceholder}
                      icon={<Search className="h-4 w-4" />}
                    />
                  </label>
                  <div className="flex flex-wrap items-center gap-2" role="group" aria-label={copy.filterAll}>
                    <Filter className="h-4 w-4 text-muted-foreground" aria-hidden="true" />
                    {CURRICULUM_FILTERS.map((filter) => (
                      <button
                        key={filter}
                        type="button"
                        className={chipTone(curriculumFilter === filter)}
                        aria-pressed={curriculumFilter === filter}
                        onClick={() => setCurriculumFilter(filter)}
                      >
                        {filter === 'ALL'
                          ? copy.filterAll
                          : filter === 'MANDATORY'
                            ? copy.filterMandatory
                            : filter === 'ELECTIVE'
                              ? copy.filterElective
                              : copy.filterOutside}
                        <span className="rounded-full bg-secondary px-1.5 text-[10px] font-bold text-muted-foreground">
                          {formatNumber(curriculumCounts.get(filter) ?? 0)}
                        </span>
                      </button>
                    ))}
                    <span className="mx-1 hidden h-5 w-px bg-border sm:block" aria-hidden="true" />
                    <button
                      type="button"
                      className={chipTone(seatsOnly)}
                      aria-pressed={seatsOnly}
                      onClick={() => setSeatsOnly((value) => !value)}
                    >
                      {copy.filterSeats}
                      <span className="rounded-full bg-secondary px-1.5 text-[10px] font-bold text-muted-foreground">
                        {formatNumber(seatsCount)}
                      </span>
                    </button>
                    <button
                      type="button"
                      className={chipTone(noConflictOnly)}
                      aria-pressed={noConflictOnly}
                      onClick={() => setNoConflictOnly((value) => !value)}
                    >
                      {copy.filterNoConflict}
                      <span className="rounded-full bg-secondary px-1.5 text-[10px] font-bold text-muted-foreground">
                        {formatNumber(noConflictCount)}
                      </span>
                    </button>
                  </div>
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-semibold text-foreground">
                      {copy.sectionCount.replace('{count}', formatNumber(filteredSections.length))}
                    </p>
                    <label className="flex items-center gap-2 text-xs text-muted-foreground">
                      <span>{copy.sortLabel}</span>
                      <Select
                        aria-label={copy.sortLabel}
                        value={sortMode}
                        onChange={(event) => setSortMode(event.target.value as SectionSortMode)}
                        className="h-9 w-auto"
                        options={[
                          { value: 'code', label: copy.sortCode },
                          { value: 'credits', label: copy.sortCredits },
                          { value: 'seats', label: copy.sortSeats },
                        ]}
                      />
                    </label>
                  </div>
                </CardContent>
              </Card>

              <Card className="order-2 min-w-0 overflow-hidden">
                <CardHeader className="border-b border-border/70 bg-[hsl(var(--surface-alt))]">
                  <CardTitle className="flex items-center gap-2 text-base">
                    <CalendarClock className="h-4 w-4 text-primary" aria-hidden="true" />
                    {copy.courseListLabel}
                  </CardTitle>
                </CardHeader>
                {sectionsQuery.isLoading ? (
                  <LoadingState label={messages.common.states.loadingContent} />
                ) : courseGroups.length === 0 ? (
                  <EmptyState icon={Search} title={copy.emptyTitle} description={copy.emptyDescription} />
                ) : (
                  <div className="divide-y divide-border/60">
                    {courseGroups.map((group) => {
                      const collapsed = collapsedCourseIds.has(group.courseId);
                      const openSeats = group.sections.reduce(
                        (total, section) => total + section.remainingSeats,
                        0,
                      );
                      return (
                        <section key={group.courseId} aria-label={`${group.courseCode} ${group.courseName}`}>
                          <div className="flex flex-wrap items-center gap-x-3 gap-y-1.5 bg-[hsl(var(--surface-alt))]/60 px-3 py-2.5 sm:px-4">
                            <button
                              type="button"
                              className="flex min-w-0 flex-1 items-center gap-2 text-left"
                              aria-expanded={!collapsed}
                              onClick={() => toggleCourse(group.courseId)}
                            >
                              <ChevronDown
                                className={`h-4 w-4 shrink-0 text-muted-foreground transition-transform ${collapsed ? '-rotate-90' : ''}`}
                                aria-hidden="true"
                              />
                              <span className="min-w-0">
                                <span className="block truncate text-sm font-bold text-foreground">
                                  {group.courseCode} — {group.courseName}
                                </span>
                                <span className="block text-xs text-muted-foreground">
                                  {formatNumber(group.credits)} {copy.creditsUnit} ·{' '}
                                  {copy.groupSectionCount.replace('{count}', formatNumber(group.sections.length))}
                                </span>
                              </span>
                            </button>
                            <span
                              className={`rounded-md px-2 py-0.5 text-[11px] font-semibold ${
                                openSeats > 0
                                  ? 'bg-status-success/12 text-status-success-foreground'
                                  : 'bg-secondary text-muted-foreground'
                              }`}
                            >
                              {openSeats > 0
                                ? copy.seatsOpen.replace('{count}', formatNumber(openSeats))
                                : copy.full}
                            </span>
                          </div>
                          {!collapsed ? (
                            <div className="space-y-2 p-3 sm:p-4">
                              {group.sections.map((section) => {
                                const enrollment = enrollmentBySection.get(section.id);
                                const isRegistered = Boolean(enrollment) || section.alreadyEnrolled;
                                const tier = seatTier(section);
                                const isFull = tier === 'full';
                                const willExceedLimit =
                                  creditLimit !== null
                                  && totalRegisteredCredits + section.credits > creditLimit;
                                const alreadyHasCourse = enrolledCourseIds.has(section.courseId);
                                const isSectionClosed = section.status !== 'OPEN';
                                const disabledTitle = willExceedLimit
                                  ? copy.creditLimitExceeded.replace(
                                      '{limit}',
                                      creditLimit === null ? copy.creditLimitUnavailable : formatNumber(creditLimit),
                                    )
                                  : isFull
                                    ? copy.full
                                    : section.scheduleConflict
                                      ? copy.scheduleConflictTooltip
                                      : alreadyHasCourse
                                        ? copy.duplicateCourseTooltip
                                        : isSectionClosed
                                          ? messages.common.campusErrors.codes.SECTION_CLOSED
                                          : undefined;
                                const isDisabled =
                                  !isRegistered
                                  && (Boolean(disabledTitle) || pending === section.id || !roundOpen);
                                const schedules: RegistrationSectionSchedule[] = section.schedules ?? [];
                                return (
                                  <article
                                    key={section.id}
                                    className={`rounded-md border p-4 shadow-sm transition-colors ${
                                      previewSectionId === section.id
                                        ? 'border-[var(--registration-gold)]'
                                        : 'border-border/70 bg-card hover:border-primary/30'
                                    }`}
                                    onMouseEnter={() => setPreviewSectionId(section.id)}
                                    onFocus={() => setPreviewSectionId(section.id)}
                                  >
                                    <div className="flex flex-wrap items-center gap-2">
                                      <p className="font-semibold text-foreground">
                                        {copy.columns.section} {section.sectionNumber}
                                      </p>
                                      {isRegistered ? (
                                        <span className="rounded-md border border-primary/30 bg-primary/10 px-2 py-0.5 text-xs font-medium text-primary">
                                          {copy.registered}
                                        </span>
                                      ) : null}
                                      {section.scheduleConflict ? (
                                        <span className="rounded-md border border-destructive/30 bg-destructive/10 px-2 py-0.5 text-xs font-medium text-destructive">
                                          {copy.conflictBadge}
                                        </span>
                                      ) : null}
                                      {section.curriculumRelevance === 'OUTSIDE' ? (
                                        <span className="rounded-md border border-status-warning/40 bg-status-warning/12 px-2 py-0.5 text-xs font-medium text-status-warning-foreground">
                                          {copy.outsideBadge}
                                        </span>
                                      ) : null}
                                    </div>
                                    <div className="mt-2 flex flex-wrap items-center gap-1.5">
                                      {schedules.length === 0 ? (
                                        <span className="rounded-md border border-dashed border-border bg-secondary/40 px-2 py-0.5 text-xs italic text-muted-foreground">
                                          {copy.scheduleNone}
                                        </span>
                                      ) : (
                                        schedules.map((schedule, index) => (
                                          <span
                                            key={`${section.id}-schedule-${index}`}
                                            className="inline-flex flex-wrap items-center gap-1 rounded-md bg-secondary/60 px-2 py-0.5 text-xs text-foreground"
                                          >
                                            <span className="font-bold text-primary">
                                              {shortDayLabel(locale, schedule.dayOfWeek)}
                                            </span>
                                            <span className="tabular-nums">
                                              {schedule.startTime}–{schedule.endTime}
                                            </span>
                                            {schedule.room ? (
                                              <span className="text-muted-foreground">
                                                · {copy.scheduleChipRoom.replace('{room}', schedule.room)}
                                              </span>
                                            ) : null}
                                            {schedule.lecturer ? (
                                              <span className="text-muted-foreground">
                                                · {copy.scheduleChipLecturer.replace('{name}', schedule.lecturer)}
                                              </span>
                                            ) : null}
                                          </span>
                                        ))
                                      )}
                                    </div>
                                    <div className="mt-3 flex flex-col items-start gap-2 sm:flex-row sm:items-center sm:justify-between">
                                      <span
                                        className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-semibold ${SEAT_TONE[tier]}`}
                                      >
                                        {isFull
                                          ? copy.full
                                          : `${formatNumber(section.remainingSeats)} ${copy.seatsLeft}`}
                                      </span>
                                      <div className="flex flex-col items-start gap-1 sm:items-end">
                                        {enrollment ? (
                                          <Button
                                            type="button"
                                            size="sm"
                                            variant="outline"
                                            onClick={() => void drop(enrollment)}
                                            disabled={pending === enrollment.id || enrollment.id.startsWith('pending:')}
                                          >
                                            <CheckCircle2 className="mr-2 h-4 w-4" aria-hidden="true" />
                                            {pending === enrollment.id ? copy.working : copy.drop}
                                          </Button>
                                        ) : (
                                          <Button
                                            type="button"
                                            size="sm"
                                            variant="registration"
                                            onClick={() => void register(section.id)}
                                            disabled={isDisabled}
                                            title={disabledTitle}
                                          >
                                            <UserPlus className="mr-2 h-4 w-4" aria-hidden="true" />
                                            {pending === section.id ? copy.working : copy.register}
                                          </Button>
                                        )}
                                        {/* A hover title is invisible on touch; state the
                                            same reason as a visible hint next to the button. */}
                                        {isDisabled && disabledTitle ? (
                                          <p className="text-[11px] leading-4 text-muted-foreground sm:text-right">
                                            {disabledTitle}
                                          </p>
                                        ) : null}
                                      </div>
                                    </div>
                                  </article>
                                );
                              })}
                            </div>
                          ) : null}
                        </section>
                      );
                    })}
                  </div>
                )}
              </Card>

              {policyCard}
            </div>

            {/* Right rail: live summary, warnings, and the weekly preview. */}
            <Card className="order-first min-w-0 lg:col-span-3 lg:order-none">
              <CardHeader className="flex flex-row items-center justify-between gap-2 space-y-0 pb-3">
                <CardTitle>{copy.enrolledRail}</CardTitle>
                <span className="rounded-md bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
                  {formatNumber(totalRegisteredCredits)} / {creditLimit === null ? copy.creditLimitUnavailable : formatNumber(creditLimit)}{' '}
                  {copy.creditsUnit}
                </span>
              </CardHeader>
              <CardContent className="space-y-3">
                <div className="space-y-2 rounded-lg border border-border/70 bg-secondary/30 p-3">
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-medium text-muted-foreground">{copy.termCreditLimitLabel}</span>
                    <span className="font-bold text-foreground">
                      {totalRegisteredCredits}
                      /
                      {creditLimit === null ? copy.creditLimitUnavailable : formatNumber(creditLimit)}{' '}
                      {copy.creditsUnit}
                    </span>
                  </div>
                  {creditLimit === null ? (
                    <div className="space-y-2">
                      <p
                        className="flex items-start gap-1.5 text-[11px] leading-4 text-status-warning-foreground"
                        role="alert"
                      >
                        <TriangleAlert className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                        {copy.creditLimitRetryWarning}
                      </p>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        className="h-8 w-full"
                        onClick={retryRegistrationData}
                      >
                        {messages.common.actions.retry}
                      </Button>
                    </div>
                  ) : (
                    <>
                      <div className="h-2 w-full overflow-hidden rounded-full bg-secondary">
                        <div
                          className={`h-full transition-all duration-300 ${
                            totalRegisteredCredits >= creditLimit ? 'bg-status-warning' : 'bg-primary'
                          }`}
                          style={{
                            width: `${Math.min(100, (totalRegisteredCredits / creditLimit) * 100)}%`,
                          }}
                        />
                      </div>
                      <p className="text-[11px] text-muted-foreground">
                        {totalRegisteredCredits >= creditLimit
                          ? copy.creditLimitReached.replace('{limit}', formatNumber(creditLimit))
                          : copy.creditLimitRemaining.replace(
                              '{count}',
                              formatNumber(creditLimit - totalRegisteredCredits),
                            )}
                      </p>
                    </>
                  )}
                </div>

                {registered.length === 0 ? (
                  <p className="text-sm text-muted-foreground">{copy.railEmpty}</p>
                ) : (
                  registered.map((item) => {
                    const section = item.section;
                    return (
                      <div
                        key={item.id}
                        className="rounded-md border border-border/70 p-3 text-sm transition-colors hover:border-primary/30"
                      >
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
                            disabled={pending === item.id || item.id.startsWith('pending:')}
                          >
                            <Trash2 className="h-4 w-4" aria-hidden="true" />
                          </Button>
                        </div>
                      </div>
                    );
                  })
                )}

                {(() => {
                  const warnings: string[] = [];
                  for (const item of registered) {
                    const catalogSection = sectionsById.get(item.sectionId);
                    const label = sectionLabel(item.section, item.sectionId);
                    if (catalogSection?.curriculumRelevance === 'OUTSIDE') {
                      warnings.push(copy.warningOutsideLine.replace('{section}', label));
                    }
                    if (catalogSection?.scheduleConflict) {
                      warnings.push(copy.warningConflictLine.replace('{section}', label));
                    }
                  }
                  if (warnings.length === 0) return null;
                  return (
                    <div
                      className="space-y-1.5 rounded-lg border border-status-warning/40 bg-status-warning/12 p-3"
                      role="status"
                    >
                      <p className="flex items-center gap-1.5 text-xs font-bold text-status-warning-foreground">
                        <TriangleAlert className="h-3.5 w-3.5" aria-hidden="true" />
                        {copy.railWarningsTitle}
                      </p>
                      {warnings.map((warning) => (
                        <p key={warning} className="text-[11px] leading-4 text-status-warning-foreground">
                          {warning}
                        </p>
                      ))}
                    </div>
                  );
                })()}

                <div className="space-y-2 rounded-lg border border-border/70 p-3">
                  <p className="text-xs font-bold uppercase tracking-wider text-muted-foreground">
                    {copy.miniTimetableTitle}
                  </p>
                  <WeeklyGrid
                    compact
                    items={[...timetableItems, ...previewTimetable]}
                    locale={locale}
                    labels={{
                      gridAriaLabel: copy.miniTimetableTitle,
                      today: copy.legendRegistered,
                      sectionPrefix: copy.columns.section,
                      roomPending: copy.scheduleNone,
                      noSlot: copy.scheduleNone,
                      blockHint: '',
                      item: '',
                      items: '',
                    }}
                    highlightedIds={new Set<string>(previewTimetable.map((item) => item.id ?? ''))}
                    conflictIds={previewConflictIds}
                  />
                  <div className="flex flex-wrap items-center gap-x-3 gap-y-1 pt-1 text-[10px] text-muted-foreground">
                    <span className="inline-flex items-center gap-1">
                      <span className="h-2.5 w-2.5 rounded-sm border border-primary/40 bg-primary/10" aria-hidden="true" />
                      {copy.legendRegistered}
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <span
                        className="h-2.5 w-2.5 rounded-sm border border-[var(--registration-gold)] bg-[color-mix(in_srgb,var(--registration-gold)_20%,transparent)]"
                        aria-hidden="true"
                      />
                      {copy.legendPreview}
                    </span>
                    <span className="inline-flex items-center gap-1">
                      <span
                        className="h-2.5 w-2.5 rounded-sm border border-status-danger/60 bg-status-danger/10"
                        aria-hidden="true"
                      />
                      {copy.legendConflict}
                    </span>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {actionError ? (
            <div
              role="alert"
              aria-live="polite"
              className="rounded-lg border border-destructive/30 bg-destructive/5 px-3 py-2 text-sm text-destructive"
            >
              {actionError}
            </div>
          ) : null}
        </>
      )}
      {confirmationDialog}
    </div>
  );
}
