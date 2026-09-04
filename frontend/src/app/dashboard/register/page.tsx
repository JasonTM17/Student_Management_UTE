'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { BookOpen, CheckCircle2, Search, UserPlus } from 'lucide-react';
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
import { campusErrorCode, campusErrorMessage } from '@/lib/campus-error';
import { toast } from 'sonner';

type CatalogSection = Awaited<ReturnType<typeof registrationApi.sections>>[number];

const ROUND_UNAVAILABLE_CODES = new Set(['WINDOW_CLOSED', 'COHORT_INELIGIBLE', 'ROUND_NOT_FOUND']);

export default function RegisterPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth(['STUDENT']);
  const { confirm, confirmationDialog } = useConfirmationDialog();
  const { messages, formatNumber } = useI18n();
  const copy = messages.courseRegistration;
  const [sections, setSections] = useState<CatalogSection[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [roundOpen, setRoundOpen] = useState(true);
  const [search, setSearch] = useState('');
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
      if (enrollment.status === 'DROPPED') continue;
      map.set(enrollment.sectionId, enrollment);
    }
    return map;
  }, [enrollments]);

  const filteredSections = useMemo(() => {
    const query = search.trim().toLowerCase();
    if (!query) {
      return sections;
    }
    return sections.filter((section) =>
      `${section.courseCode} ${section.courseName} ${section.sectionNumber}`.toLowerCase().includes(query),
    );
  }, [search, sections]);

  const registered = enrollments.filter((item) => item.status !== 'DROPPED');

  /** Confirms and submits one section enrollment, then refreshes the live catalog. */
  const register = async (sectionId: string) => {
    const ok = await confirm({
      title: copy.confirmRegister,
      message: copy.confirmRegister,
      confirmText: copy.register,
    });
    if (!ok) return;
    setPending(sectionId);
    try {
      await enrollmentsApi.enroll(sectionId);
      toast.success(copy.success);
      await load();
    } catch (cause) {
      toast.error(campusErrorMessage(cause, messages.common.campusErrors));
    } finally {
      setPending('');
    }
  };

  /** Confirms and drops one active enrollment, then refreshes the live schedule data. */
  const drop = async (enrollment: Enrollment) => {
    const ok = await confirm({
      title: copy.confirmDrop,
      message: copy.confirmDrop,
      confirmText: copy.drop,
    });
    if (!ok) return;
    setPending(enrollment.id);
    try {
      await enrollmentsApi.drop(enrollment.id);
      toast.success(copy.success);
      await load();
    } catch (cause) {
      toast.error(campusErrorMessage(cause, messages.common.campusErrors));
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
      ) : null}
      <div className="grid gap-6 lg:grid-cols-12">
        <div className="space-y-6 lg:col-span-8">
          <Card>
            <CardContent className="flex flex-col gap-3 p-4 md:flex-row">
              <label className="flex min-w-0 flex-1 items-center gap-2">
                <Search className="h-4 w-4 text-muted-foreground" />
                <Input
                  value={search}
                  onChange={(event) => setSearch(event.target.value)}
                  placeholder={copy.searchPlaceholder}
                />
              </label>
            </CardContent>
          </Card>
          <Card className="overflow-hidden">
            <CardHeader className="border-b border-border/70 bg-[hsl(var(--surface-alt))]">
              <CardTitle className="flex items-center gap-2 text-base">
                <BookOpen className="h-4 w-4 text-primary" />
                {copy.sectionCount.replace('{count}', formatNumber(filteredSections.length))}
              </CardTitle>
            </CardHeader>
            {!roundOpen ? (
              <EmptyState icon={BookOpen} title={copy.roundUnavailable} description={copy.exportUnavailable} />
            ) : filteredSections.length === 0 ? (
              <EmptyState icon={BookOpen} title={copy.emptyTitle} description={copy.emptyDescription} />
            ) : (
              <>
                <div className="hidden overflow-x-auto md:block">
                  <table className="w-full min-w-[760px] text-left text-sm">
                    <thead className="bg-[var(--registration-navy)] text-white">
                      <tr>
                        <th className="px-4 py-3 font-semibold">{copy.columns.course}</th>
                        <th className="px-4 py-3 font-semibold">{copy.columns.section}</th>
                        <th className="px-4 py-3 font-semibold">{copy.seatsLeft}</th>
                        <th className="px-4 py-3 text-right font-semibold">{copy.columns.action}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/70">
                      {filteredSections.map((section) => {
                        const enrollment = enrollmentBySection.get(section.id);
                        const seats = section.remainingSeats;
                        return (
                          <tr key={section.id} className="odd:bg-card even:bg-[hsl(var(--surface-alt))/0.55]">
                            <td className="px-4 py-4">
                              <div className="font-semibold text-foreground">{section.courseCode}</div>
                              <div className="mt-1 text-muted-foreground">{section.courseName}</div>
                            </td>
                            <td className="px-4 py-4 text-muted-foreground">{section.sectionNumber}</td>
                            <td className="px-4 py-4">
                              <span className={seats > 0 ? 'font-semibold text-status-success' : 'font-semibold text-muted-foreground'}>
                                {seats > 0 ? formatNumber(seats) : copy.full}
                              </span>
                            </td>
                            <td className="px-4 py-4 text-right">
                              {enrollment ? (
                                <Button type="button" size="sm" variant="outline" onClick={() => void drop(enrollment)} disabled={pending === enrollment.id}>
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
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
                <div className="space-y-3 p-3 md:hidden">
                  {filteredSections.map((section) => {
                    const enrollment = enrollmentBySection.get(section.id);
                    const seats = section.remainingSeats;
                    return (
                      <article key={section.id} className="rounded-md border border-border/70 bg-card p-4 shadow-sm">
                        <p className="font-semibold text-foreground">{section.courseCode}</p>
                        <p className="mt-1 text-sm text-muted-foreground">{section.courseName}</p>
                        <p className="mt-3 text-sm font-semibold">{seats > 0 ? `${formatNumber(seats)} ${copy.seatsLeft}` : copy.full}</p>
                        <div className="mt-4 flex justify-end">
                          {enrollment ? (
                            <Button type="button" size="sm" variant="outline" onClick={() => void drop(enrollment)} disabled={pending === enrollment.id}>
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
                              {pending === section.id ? copy.working : copy.register}
                            </Button>
                          )}
                        </div>
                      </article>
                    );
                  })}
                </div>
              </>
            )}
          </Card>
        </div>
        <Card className="lg:col-span-4">
          <CardHeader>
            <CardTitle>{copy.enrolledRail}</CardTitle>
          </CardHeader>
          <CardContent className="space-y-3">
            {registered.length === 0 ? (
              <p className="text-sm text-muted-foreground">{copy.emptyDescription}</p>
            ) : (
              registered.map((item) => (
                <div key={item.id} className="rounded-md border border-border/70 px-3 py-2 text-sm">
                  {item.section?.course?.code ?? item.sectionId}
                </div>
              ))
            )}
          </CardContent>
        </Card>
      </div>
      {confirmationDialog}
    </div>
  );
}
