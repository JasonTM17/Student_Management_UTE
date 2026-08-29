'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Calendar, Clock, MapPin } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { metricToneClass } from '@/components/ui/status';
import { useRequireAuth } from '@/context/AuthContext';
import { enrollmentsApi, semestersApi } from '@/lib/api';
import { getLocalizedCourseLabel, getLocalizedName } from '@/lib/academic-content';
import { pickPreferredSemesterId } from '@/lib/semesters';
import { Enrollment, Semester } from '@/types/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';

type DayAgendaItem = {
  id: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  sectionNumber: string;
  startTime: string;
  endTime: string;
  dayOfWeek: number;
  building?: string;
  roomNumber?: string;
};

const dayNames = [
  'Sunday',
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
];

export default function SchedulePage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatNumber } = useI18n();
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
    const preferredSemesterId = pickPreferredSemesterId(response.data);
    if (preferredSemesterId) {
      setSelectedSemester((current) => current || preferredSemesterId);
    }
  }, []);

  const fetchEnrollments = useCallback(
    async (semesterId?: string) => {
      const response = await enrollmentsApi.getMyEnrollments(semesterId);
      setEnrollments(response);
    },
    [],
  );

  const loadData = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      await fetchSemesters();
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải bộ lọc thời khóa biểu.'
          : 'Schedule filters could not be loaded.',
      );
      setIsLoading(false);
    }
  }, [fetchSemesters, locale]);

  useEffect(() => {
    if (hasAccess) {
      void loadData();
    }
  }, [hasAccess, loadData]);

  useEffect(() => {
    if (!hasAccess) {
      return;
    }

    let cancelled = false;

    const run = async () => {
      setIsLoading(true);
      setError('');

      try {
        await fetchEnrollments(selectedSemester || undefined);
      } catch {
        if (!cancelled) {
          setError(
            locale === 'vi'
              ? 'Hiện chưa thể tải dữ liệu thời khóa biểu.'
              : 'Schedule data could not be loaded.',
          );
        }
      } finally {
        if (!cancelled) {
          setIsLoading(false);
        }
      }
    };

    void run();
    return () => {
      cancelled = true;
    };
  }, [fetchEnrollments, hasAccess, locale, selectedSemester]);

  const agenda = useMemo(() => {
    const items: DayAgendaItem[] = [];

    enrollments
      .filter(
        (enrollment) =>
          enrollment.status === 'ENROLLED' ||
          enrollment.status === 'CONFIRMED' ||
          enrollment.status === 'PENDING',
      )
      .forEach((enrollment) => {
        const section = enrollment.section;
        section?.schedules?.forEach((schedule, index) => {
          items.push({
            id: `${enrollment.id}-${index}`,
            courseCode: section.course?.code ?? 'Course',
            courseName: section.course?.name ?? 'Unavailable',
            courseNameEn: section.course?.nameEn,
            courseNameVi: section.course?.nameVi,
            sectionNumber: section.sectionNumber,
            startTime: schedule.startTime,
            endTime: schedule.endTime,
            dayOfWeek: schedule.dayOfWeek,
            building: schedule.classroom?.building,
            roomNumber: schedule.classroom?.roomNumber,
          });
        });
      });

    return items.sort((left, right) => {
      if (left.dayOfWeek !== right.dayOfWeek) {
        return left.dayOfWeek - right.dayOfWeek;
      }
      return left.startTime.localeCompare(right.startTime);
    });
  }, [enrollments]);

  const agendaByDay = useMemo(() => {
    return agenda.reduce<Record<number, DayAgendaItem[]>>((groups, item) => {
      if (!groups[item.dayOfWeek]) {
        groups[item.dayOfWeek] = [];
      }

      groups[item.dayOfWeek].push(item);
      return groups;
    }, {});
  }, [agenda]);

  const selectedSemesterName = useMemo(() => {
    return (
      getLocalizedName(
        locale,
        semesters.find((semester) => semester.id === selectedSemester),
        locale === 'vi' ? 't\u1ea5t c\u1ea3 h\u1ecdc k\u1ef3' : 'all terms',
      ) ??
      (locale === 'vi' ? 'tất cả học kỳ' : 'all terms')
    );
  }, [locale, selectedSemester, semesters]);

  const localizedDayNames =
    locale === 'vi'
      ? ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy']
      : dayNames;

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Không gian sinh viên',
          title: 'Thời khóa biểu',
          description: `Giữ lịch học theo tuần cho ${selectedSemesterName} trong tầm tay trong khi phần còn lại của không gian sinh viên vẫn chỉ cách một lần chạm.`,
          selectSemester: 'Chọn học kỳ cho thời khóa biểu',
          allSemesters: 'Tất cả học kỳ',
          openCourses: 'Mở môn học của tôi',
          loading: 'Đang tải thời khóa biểu',
          unavailableTitle: 'Thời khóa biểu chưa sẵn sàng',
          emptyTitle: 'Chưa có lớp học theo lịch',
          emptyDescription:
            'Khi section được xác nhận, các buổi học sẽ xuất hiện tại đây theo lịch tuần.',
          browseSections: 'Xem các section',
          weeklyMeetings: 'Buổi học mỗi tuần',
          coursesInRotation: 'Môn học đang diễn ra',
          teachingSpaces: 'Không gian học tập',
          weeklyAgenda: 'Lịch học theo tuần',
          upcomingClassList: 'Danh sách lớp sắp tới',
          noMeetings: 'Chưa có buổi học nào.',
          noSlot: 'Chưa có lịch dạy.',
          items: 'mục',
          item: 'mục',
          sectionPrefix: 'Lớp học phần',
          roomPending: 'Đang chờ thông tin phòng học',
        }
      : {
          eyebrow: 'Student workspace',
          title: 'Schedule',
          description: `Keep the weekly class agenda for ${selectedSemesterName} visible while the rest of the student workspace stays one click away.`,
          selectSemester: 'Select semester for schedule',
          allSemesters: 'All semesters',
          openCourses: 'Open my courses',
          loading: 'Loading schedule',
          unavailableTitle: 'Schedule unavailable',
          emptyTitle: 'No scheduled classes yet',
          emptyDescription:
            'Once sections are confirmed, their class meetings will appear here in a weekly agenda.',
          browseSections: 'Browse sections',
          weeklyMeetings: 'Weekly meetings',
          coursesInRotation: 'Courses in rotation',
          teachingSpaces: 'Teaching spaces',
          weeklyAgenda: 'Weekly agenda',
          upcomingClassList: 'Upcoming class list',
          noMeetings: 'No scheduled meetings.',
          noSlot: 'No teaching slot scheduled.',
          items: 'items',
          item: 'item',
          sectionPrefix: 'Section',
          roomPending: 'Room information pending',
        };

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center">
            <div className="min-w-[220px]">
              <Select
                aria-label={copy.selectSemester}
                value={selectedSemester}
                onChange={(event) => setSelectedSemester(event.target.value)}
                options={[
                  { value: '', label: copy.allSemesters },
                  ...semesters.map((semester) => ({
                    value: semester.id,
                    label: getLocalizedName(locale, semester, semester.name),
                  })),
                ]}
              />
            </div>
            <LinkButton href="/dashboard/enrollments" variant="outline">
              {copy.openCourses}
            </LinkButton>
          </div>
        }
      />

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void loadData()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : agenda.length === 0 ? (
        <EmptyState
          icon={Clock}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
          action={
            <LinkButton href="/dashboard/register">{copy.browseSections}</LinkButton>
          }
        />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.weeklyMeetings}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(agenda.length)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('info')}`}>
                  <Calendar className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.coursesInRotation}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(
                      new Set(
                        agenda.map((item) => `${item.courseCode}-${item.sectionNumber}`),
                      ).size,
                    )}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('success')}`}>
                  <Clock className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.teachingSpaces}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(
                      new Set(
                        agenda
                          .map((item) =>
                            item.building && item.roomNumber
                              ? `${item.building}-${item.roomNumber}`
                              : null,
                          )
                          .filter(Boolean),
                      ).size,
                    )}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('neutral')}`}>
                  <MapPin className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.1fr_0.9fr]">
            <Card variant="muted">
              <CardHeader>
                <CardTitle className="text-xl">{copy.weeklyAgenda}</CardTitle>
              </CardHeader>
              <CardContent className="grid gap-4 md:grid-cols-2">
                {localizedDayNames.slice(1, 6).map((dayName, index) => {
                  const dayOfWeek = index + 1;
                  const items = agendaByDay[dayOfWeek] ?? [];

                  return (
                    <div
                      key={dayName}
                      className="rounded-lg border border-border/70 bg-card px-4 py-4"
                    >
                      <div className="mb-3 flex items-center justify-between">
                        <h2 className="font-semibold text-foreground">{dayName}</h2>
                        <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                          {formatNumber(items.length)}{' '}
                          {items.length === 1 ? copy.item : copy.items}
                        </span>
                      </div>

                      {items.length === 0 ? (
                        <p className="text-sm text-muted-foreground">
                          {copy.noMeetings}
                        </p>
                      ) : (
                        <div className="space-y-3">
                          {items.map((item) => (
                            <div
                              key={item.id}
                              className="rounded-lg border border-border/60 bg-secondary/30 px-4 py-3"
                            >
                              <div className="font-medium text-foreground">
                                {getLocalizedCourseLabel(
                                  locale,
                                  {
                                    code: item.courseCode,
                                    name: item.courseName,
                                    nameEn: item.courseNameEn,
                                    nameVi: item.courseNameVi,
                                  },
                                  `${item.courseCode} - ${item.courseName}`,
                                )}
                              </div>
                              <div className="mt-1 text-sm text-muted-foreground">
                                {copy.sectionPrefix} {item.sectionNumber}
                              </div>
                              <div className="mt-2 flex flex-wrap gap-3 text-sm text-muted-foreground">
                                <span className="inline-flex items-center gap-2">
                                  <Clock className="h-4 w-4" />
                                  {item.startTime} - {item.endTime}
                                </span>
                                {item.building && item.roomNumber ? (
                                  <span className="inline-flex items-center gap-2">
                                    <MapPin className="h-4 w-4" />
                                    {item.building} {item.roomNumber}
                                  </span>
                                ) : null}
                              </div>
                            </div>
                          ))}
                        </div>
                      )}
                    </div>
                  );
                })}
              </CardContent>
            </Card>

            <Card variant="elevated">
              <CardHeader>
                <CardTitle className="text-xl">{copy.upcomingClassList}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {agenda.map((item) => (
                  <div
                    key={item.id}
                    className="rounded-lg border border-border/70 bg-card px-4 py-4"
                  >
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <div className="font-medium text-foreground">
                          {getLocalizedCourseLabel(
                            locale,
                            {
                              code: item.courseCode,
                              name: item.courseName,
                              nameEn: item.courseNameEn,
                              nameVi: item.courseNameVi,
                            },
                            `${item.courseCode} - ${item.courseName}`,
                          )}
                        </div>
                        <div className="mt-1 text-sm text-muted-foreground">
                          {localizedDayNames[item.dayOfWeek]} - {copy.sectionPrefix}{' '}
                          {item.sectionNumber}
                        </div>
                      </div>
                      <div className="text-sm text-muted-foreground sm:text-right">
                        <div>
                          {item.startTime} - {item.endTime}
                        </div>
                        <div>
                          {item.building && item.roomNumber
                            ? `${item.building} ${item.roomNumber}`
                            : copy.roomPending}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>
        </>
      )}
    </div>
  );
}
