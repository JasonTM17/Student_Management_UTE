'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { BookOpen, CheckCircle2, Search, UserPlus } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import { enrollmentsApi, sectionsApi, semestersApi } from '@/lib/api';
import type { Enrollment, Section, Semester } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { toast } from 'sonner';

export default function RegisterPage() {
  const { isLoading: authLoading, hasAccess } = useRequireAuth(['STUDENT']);
  const { locale, messages, formatNumber } = useI18n();
  const [sections, setSections] = useState<Section[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [semesterId, setSemesterId] = useState('');
  const [search, setSearch] = useState('');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [pending, setPending] = useState('');

  const copy = locale === 'vi'
    ? {
        eyebrow: 'Cổng sinh viên',
        title: 'Đăng ký học phần',
        description: 'Tra cứu section đang mở, kiểm tra số chỗ và xác nhận đăng ký trực tiếp với Java API.',
        search: 'Tìm theo mã hoặc tên môn học',
        semester: 'Học kỳ',
        allSemesters: 'Tất cả học kỳ',
        seats: 'Còn chỗ',
        full: 'Đã đầy',
        register: 'Đăng ký',
        registered: 'Đã đăng ký',
        drop: 'Hủy đăng ký',
        empty: 'Chưa có section phù hợp',
        emptyDescription: 'Thử đổi học kỳ hoặc từ khóa tìm kiếm.',
        loadFailed: 'Không thể tải dữ liệu đăng ký học phần.',
        success: 'Đã cập nhật đăng ký học phần.',
        confirmDrop: 'Bạn có chắc muốn hủy học phần này không?',
        confirmRegister: 'Xác nhận đăng ký học phần này?',
      }
    : {
        eyebrow: 'Student portal',
        title: 'Course registration',
        description: 'Browse open sections, check live seats, and confirm enrollment directly with the Java API.',
        search: 'Search by course code or name',
        semester: 'Semester',
        allSemesters: 'All semesters',
        seats: 'Seats left',
        full: 'Full',
        register: 'Register',
        registered: 'Registered',
        drop: 'Drop course',
        empty: 'No matching sections',
        emptyDescription: 'Try another semester or search term.',
        loadFailed: 'Course registration data could not be loaded.',
        success: 'Enrollment updated.',
        confirmDrop: 'Are you sure you want to drop this course?',
        confirmRegister: 'Confirm course registration?',
      };

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const [sectionResponse, enrollmentData, semesterResponse] = await Promise.all([
        sectionsApi.getAll({ limit: 150, semesterId: semesterId || undefined }),
        enrollmentsApi.getMyEnrollments(semesterId || undefined),
        semestersApi.getAll(),
      ]);
      setSections(sectionResponse.data);
      setEnrollments(enrollmentData);
      setSemesters(semesterResponse.data);
      if (!semesterId && semesterResponse.data[0]?.id) {
        setSemesterId(semesterResponse.data[0].id);
      }
    } catch {
      setError(copy.loadFailed);
    } finally {
      setLoading(false);
    }
  }, [copy.loadFailed, semesterId]);

  useEffect(() => {
    if (!authLoading && hasAccess) {
      void load();
    }
  }, [authLoading, hasAccess, load]);

  const filteredSections = useMemo(() => {
    const query = search.trim().toLocaleLowerCase();
    if (!query) return sections;
    return sections.filter((section) => {
      const course = section.course;
      return [course?.code, course?.name, course?.nameEn, course?.nameVi]
        .filter(Boolean)
        .some((value) => String(value).toLocaleLowerCase().includes(query));
    });
  }, [search, sections]);

  const enrollmentBySection = useMemo(
    () => new Map(enrollments.map((enrollment) => [enrollment.sectionId, enrollment])),
    [enrollments],
  );

  async function register(sectionId: string) {
    if (!window.confirm(copy.confirmRegister)) return;
    setPending(sectionId);
    try {
      await enrollmentsApi.enroll(sectionId, locale);
      toast.success(copy.success);
      await load();
    } catch (nextError: any) {
      toast.error(nextError?.response?.data?.message || copy.loadFailed);
    } finally {
      setPending('');
    }
  }

  async function drop(enrollment: Enrollment) {
    if (!window.confirm(copy.confirmDrop)) return;
    setPending(enrollment.id);
    try {
      await enrollmentsApi.drop(enrollment.id);
      toast.success(copy.success);
      await load();
    } catch (nextError: any) {
      toast.error(nextError?.response?.data?.message || copy.loadFailed);
    } finally {
      setPending('');
    }
  }

  if (authLoading || !hasAccess) return <LoadingState label={messages.dashboardShell.menu.courseRegistration} />;
  if (loading && sections.length === 0) return <LoadingState label={copy.title} />;
  if (error && sections.length === 0) {
    return <ErrorState title={copy.loadFailed} description={error} onRetry={() => void load()} />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
      />

      <Card>
        <CardContent className="grid gap-4 p-4 md:grid-cols-[minmax(0,1fr)_16rem]">
          <label className="relative block">
            <span className="sr-only">{copy.search}</span>
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input value={search} onChange={(event) => setSearch(event.target.value)} placeholder={copy.search} className="pl-10" />
          </label>
          <label className="flex items-center gap-3 text-sm font-medium text-foreground">
            <span className="shrink-0">{copy.semester}</span>
            <Select
              value={semesterId}
              onChange={(event) => setSemesterId(event.target.value)}
              aria-label={copy.semester}
              options={[
                { value: '', label: copy.allSemesters },
                ...semesters.map((semester) => ({ value: semester.id, label: semester.name })),
              ]}
            />
          </label>
        </CardContent>
      </Card>

      <Card className="overflow-hidden">
        <CardHeader className="border-b border-border/70 bg-[hsl(var(--surface-alt))]">
          <CardTitle className="flex items-center gap-2 text-base"><BookOpen className="h-4 w-4 text-primary" />{formatNumber(filteredSections.length)} sections</CardTitle>
        </CardHeader>
        {filteredSections.length === 0 ? (
          <EmptyState icon={BookOpen} title={copy.empty} description={copy.emptyDescription} />
        ) : (
          <>
            <div className="hidden overflow-x-auto md:block">
            <table className="w-full min-w-[760px] text-left text-sm">
              <thead className="bg-primary text-primary-foreground">
                <tr>
                  <th className="px-4 py-3 font-semibold">{locale === 'vi' ? 'Học phần' : 'Course'}</th>
                  <th className="px-4 py-3 font-semibold">{locale === 'vi' ? 'Section' : 'Section'}</th>
                  <th className="px-4 py-3 font-semibold">{locale === 'vi' ? 'Lịch học' : 'Schedule'}</th>
                  <th className="px-4 py-3 font-semibold">{copy.seats}</th>
                  <th className="px-4 py-3 text-right font-semibold">{locale === 'vi' ? 'Thao tác' : 'Action'}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/70">
                {filteredSections.map((section) => {
                  const enrollment = enrollmentBySection.get(section.id);
                  const seats = Math.max(0, (section.capacity ?? 0) - (section.enrolledCount ?? 0));
                  const name = locale === 'vi' ? section.course?.nameVi || section.course?.name : section.course?.nameEn || section.course?.name;
                  return (
                    <tr key={section.id} className="odd:bg-card even:bg-[hsl(var(--surface-alt))/0.55]">
                      <td className="px-4 py-4"><div className="font-semibold text-foreground">{section.course?.code}</div><div className="mt-1 text-muted-foreground">{name}</div></td>
                      <td className="px-4 py-4 text-muted-foreground">{section.sectionNumber}</td>
                      <td className="px-4 py-4 text-muted-foreground">{section.schedules?.map((schedule) => schedule.dayOfWeek + ' · ' + schedule.startTime + '-' + schedule.endTime).join(', ') || '—'}</td>
                      <td className="px-4 py-4"><span className={seats > 0 ? 'font-semibold text-emerald-700 dark:text-emerald-300' : 'font-semibold text-muted-foreground'}>{seats > 0 ? formatNumber(seats) : copy.full}</span></td>
                      <td className="px-4 py-4 text-right">
                        {enrollment && enrollment.status !== 'DROPPED' ? (
                          <Button type="button" size="sm" variant="outline" onClick={() => void drop(enrollment)} disabled={pending === enrollment.id}><CheckCircle2 className="mr-2 h-4 w-4" />{pending === enrollment.id ? '...' : copy.drop}</Button>
                        ) : (
                          <Button type="button" size="sm" onClick={() => void register(section.id)} disabled={seats === 0 || pending === section.id}><UserPlus className="mr-2 h-4 w-4" />{pending === section.id ? '...' : copy.register}</Button>
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
              const seats = Math.max(0, (section.capacity ?? 0) - (section.enrolledCount ?? 0));
              const name = locale === 'vi' ? section.course?.nameVi || section.course?.name : section.course?.nameEn || section.course?.name;
              const schedule = section.schedules?.map((item) => `${item.dayOfWeek} · ${item.startTime}-${item.endTime}`).join(', ') || '—';
              return (
                <article key={section.id} className="rounded-md border border-border/70 bg-card p-4 shadow-sm">
                  <div className="flex items-start justify-between gap-4">
                    <div>
                      <p className="font-semibold text-foreground">{section.course?.code}</p>
                      <p className="mt-1 text-sm text-muted-foreground">{name}</p>
                    </div>
                    <span className="shrink-0 text-sm font-semibold text-muted-foreground">{section.sectionNumber}</span>
                  </div>
                  <dl className="mt-4 grid gap-3 text-sm">
                    <div>
                      <dt className="font-semibold text-foreground">{locale === 'vi' ? 'Lịch học' : 'Schedule'}</dt>
                      <dd className="mt-1 text-muted-foreground">{schedule}</dd>
                    </div>
                    <div>
                      <dt className="font-semibold text-foreground">{copy.seats}</dt>
                      <dd className={seats > 0 ? 'mt-1 font-semibold text-emerald-700 dark:text-emerald-300' : 'mt-1 font-semibold text-muted-foreground'}>
                        {seats > 0 ? formatNumber(seats) : copy.full}
                      </dd>
                    </div>
                  </dl>
                  <div className="mt-4 flex justify-end">
                    {enrollment && enrollment.status !== 'DROPPED' ? (
                      <Button type="button" size="sm" variant="outline" onClick={() => void drop(enrollment)} disabled={pending === enrollment.id}>
                        <CheckCircle2 className="mr-2 h-4 w-4" />{pending === enrollment.id ? '...' : copy.drop}
                      </Button>
                    ) : (
                      <Button type="button" size="sm" onClick={() => void register(section.id)} disabled={seats === 0 || pending === section.id}>
                        <UserPlus className="mr-2 h-4 w-4" />{pending === section.id ? '...' : copy.register}
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
  );
}
