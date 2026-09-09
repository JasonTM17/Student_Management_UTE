'use client';

import { Fragment, useCallback, useEffect, useMemo, useState } from 'react';
import {
  Calendar,
  CalendarDays,
  Clock,
  GraduationCap,
  LayoutGrid,
  List,
  MapPin,
  Printer,
  Sparkles,
} from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { LinkButton } from '@/components/ui/link-button';
import { useRequireAuth } from '@/context/AuthContext';
import { enrollmentsApi, semestersApi } from '@/lib/api';
import { getLocalizedCourseLabel, getLocalizedName } from '@/lib/academic-content';
import { pickPreferredSemesterId } from '@/lib/semesters';
import { buildWeeklyGrid } from '@/lib/weekly-grid';
import { Enrollment, Semester } from '@/types/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';
import {
  ClassScheduleDetailModal,
  ScheduleDetailData,
} from '@/components/schedule/ClassScheduleDetailModal';

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
  credits?: number;
  lecturerName?: string;
  status?: string;
};

const DAY_LABELS_VI: Record<number, string> = {
  1: 'Chủ Nhật',
  2: 'Thứ Hai',
  3: 'Thứ Ba',
  4: 'Thứ Tư',
  5: 'Thứ Năm',
  6: 'Thứ Sáu',
  7: 'Thứ Bảy',
};

const DAY_LABELS_EN: Record<number, string> = {
  1: 'Sunday',
  2: 'Monday',
  3: 'Tuesday',
  4: 'Wednesday',
  5: 'Thursday',
  6: 'Friday',
  7: 'Saturday',
};

// Standard academic week display order: Monday to Sunday
const ORDERED_DAYS = [2, 3, 4, 5, 6, 7, 1];

/**
 * Token-based accent pairs that stay readable in light and dark themes. The
 * palette starts with the semantic primary pair and then uses hue pairs whose
 * text color swaps to a lighter step in dark mode.
 */
const weeklyAccents = [
  'bg-primary/15 text-primary border-primary/30',
  'bg-emerald-600/15 text-emerald-700 dark:text-emerald-300 border-emerald-600/30',
  'bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30',
  'bg-sky-600/15 text-sky-700 dark:text-sky-300 border-sky-600/30',
  'bg-violet-600/15 text-violet-700 dark:text-violet-300 border-violet-600/30',
] as const;

/** Stable accent per course: char-code hash of the course code, deterministic across reloads. */
const accentForCourse = (courseCode: string): string => {
  let hash = 0;
  for (let index = 0; index < courseCode.length; index += 1) {
    hash += courseCode.charCodeAt(index);
  }
  return weeklyAccents[hash % weeklyAccents.length];
};

export default function SchedulePage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['STUDENT']);
  const { locale, formatNumber } = useI18n();
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  // Enhanced view options: 'grid' (Lưới tuần), 'day' (Theo ngày), 'list' (Danh sách)
  const [viewMode, setViewMode] = useState<'grid' | 'day' | 'list'>('grid');
  const [selectedDetail, setSelectedDetail] = useState<ScheduleDetailData | null>(null);

  // Today indicator
  const todayDow = useMemo(() => {
    const jsDay = new Date().getDay();
    return jsDay === 0 ? 1 : jsDay + 1; // 1 = Sunday, 2 = Monday...
  }, []);

  const [selectedDayTab, setSelectedDayTab] = useState<number>(todayDow);

  /** Loads semester choices and selects the best current term for the weekly agenda. */
  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
    const preferredSemesterId = pickPreferredSemesterId(response.data);
    if (preferredSemesterId) {
      setSelectedSemester((current) => current || preferredSemesterId);
    }
  }, []);

  /** Loads the student's enrollment records whose section schedules form the timetable. */
  const fetchEnrollments = useCallback(
    async (semesterId?: string) => {
      const response = await enrollmentsApi.getMyEnrollments(semesterId);
      setEnrollments(response);
    },
    [],
  );

  /** Loads schedule filters and maps failures to the page's truthful unavailable state. */
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

  /** Projects active enrollments into sorted weekday meeting cards for the selected term. */
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
        const lecturerName =
          (section?.lecturer as { fullName?: string } | undefined)?.fullName ??
          (section?.lecturer?.user
            ? `${section.lecturer.user.firstName} ${section.lecturer.user.lastName}`.trim()
            : undefined);

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
            dayOfWeek: schedule.dayOfWeek === 0 ? 1 : schedule.dayOfWeek,
            building: schedule.classroom?.building,
            roomNumber: schedule.classroom?.roomNumber,
            credits: section.course?.credits,
            lecturerName,
            status: enrollment.status,
          });
        });
      });

    return items.sort((left, right) => {
      if (left.dayOfWeek !== right.dayOfWeek) {
        return left.dayOfWeek - right.dayOfWeek;
      }
      return (left.startTime || '').localeCompare(right.startTime || '');
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

  // Unique courses count
  const uniqueCoursesCount = useMemo(() => {
    return new Set(agenda.map((item) => item.courseCode)).size;
  }, [agenda]);

  // Total credits count
  const totalCreditsCount = useMemo(() => {
    const seen = new Set<string>();
    let sum = 0;
    for (const item of agenda) {
      if (!seen.has(item.courseCode)) {
        seen.add(item.courseCode);
        sum += item.credits ?? 0;
      }
    }
    return sum;
  }, [agenda]);

  const selectedSemesterName = useMemo(() => {
    return (
      getLocalizedName(
        locale,
        semesters.find((semester) => semester.id === selectedSemester),
        locale === 'vi' ? 'tất cả học kỳ' : 'all terms',
      ) ??
      (locale === 'vi' ? 'tất cả học kỳ' : 'all terms')
    );
  }, [locale, selectedSemester, semesters]);

  const weeklyGrid = useMemo(() => buildWeeklyGrid(agenda), [agenda]);

  const handlePrint = () => {
    window.print();
  };

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu sinh viên',
          title: 'Thời khóa biểu',
          description: `Giữ lịch học theo tuần cho ${selectedSemesterName} trong tầm tay cùng các tiện ích sinh viên cần dùng mỗi ngày.`,
          selectSemester: 'Chọn học kỳ cho thời khóa biểu',
          allSemesters: 'Tất cả học kỳ',
          openCourses: 'Mở môn học của tôi',
          loading: 'Đang tải thời khóa biểu',
          unavailableTitle: 'Thời khóa biểu chưa sẵn sàng',
          emptyTitle: 'Chưa có lớp học theo lịch',
          emptyDescription:
            'Khi lớp học phần được xác nhận, các buổi học sẽ xuất hiện tại đây theo lịch tuần.',
          browseSections: 'Xem lớp học phần',
          weeklyAgenda: 'Lịch học theo tuần',
          weeklyGrid: 'Lưới thời khóa biểu',
          upcomingClassList: 'Danh sách lớp học phần',
          noMeetings: 'Chưa có buổi học nào.',
          noSlot: 'Chưa có lịch dạy.',
          items: 'mục',
          item: 'mục',
          sectionPrefix: 'Lớp học phần',
          roomPending: 'Đang chờ thông tin phòng học',
          today: 'HÔM NAY',
          viewGrid: 'Lưới tuần',
          viewDay: 'Theo ngày',
          viewList: 'Danh sách',
          print: 'In thời khóa biểu',
          totalCourses: 'Môn học',
          totalCredits: 'Tín chỉ',
          totalMeetings: 'Buổi học / tuần',
          filterDay: 'Chọn ngày trong tuần',
          clickToViewDetails: 'Bấm vào môn học để xem chi tiết đầy đủ',
          courseCode: 'Mã HP',
          courseName: 'Tên học phần',
          credits: 'Số TC',
          section: 'Lớp HP',
          day: 'Thứ',
          time: 'Giờ học',
          classroom: 'Phòng học',
          lecturer: 'Giảng viên',
          actions: 'Chi tiết',
        } as const
      : {
          eyebrow: 'Student area',
          title: 'Schedule',
          description: `Keep the weekly class agenda for ${selectedSemesterName} visible while the rest of the student area stays one click away.`,
          selectSemester: 'Select semester for schedule',
          allSemesters: 'All semesters',
          openCourses: 'Open my courses',
          loading: 'Loading schedule',
          unavailableTitle: 'Schedule unavailable',
          emptyTitle: 'No scheduled classes yet',
          emptyDescription:
            'Once sections are confirmed, their class meetings will appear here in a weekly agenda.',
          browseSections: 'Browse sections',
          weeklyAgenda: 'Weekly agenda',
          weeklyGrid: 'Weekly timetable grid',
          upcomingClassList: 'Class section list',
          noMeetings: 'No scheduled meetings.',
          noSlot: 'No teaching slot scheduled.',
          items: 'items',
          item: 'item',
          sectionPrefix: 'Class',
          roomPending: 'Room information pending',
          today: 'TODAY',
          viewGrid: 'Weekly Grid',
          viewDay: 'Day View',
          viewList: 'List View',
          print: 'Print Schedule',
          totalCourses: 'Courses',
          totalCredits: 'Credits',
          totalMeetings: 'Sessions / week',
          filterDay: 'Select day of week',
          clickToViewDetails: 'Click on any class to view complete details',
          courseCode: 'Course Code',
          courseName: 'Course Title',
          credits: 'Credits',
          section: 'Section',
          day: 'Day',
          time: 'Time',
          classroom: 'Room',
          lecturer: 'Instructor',
          actions: 'Details',
        } as const;

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-6">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
          <div className="flex flex-col gap-3 sm:flex-row sm:items-center print:hidden">
            <div className="min-w-0 sm:min-w-[280px]">
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
        <div className="space-y-6">
          {/* Printable Official Institutional Header */}
          <div className="hidden print:block mb-6 border-b-2 border-primary/40 pb-4 text-center">
            <div className="text-xs uppercase font-bold tracking-wider text-muted-foreground">
              {locale === 'vi'
                ? 'TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH'
                : 'HO CHI MINH CITY UNIVERSITY OF TECHNOLOGY AND EDUCATION'}
            </div>
            <div className="text-sm font-extrabold text-foreground">
              {locale === 'vi'
                ? 'PHÒNG ĐÀO TẠO — HỆ THỐNG QUẢN LÝ ĐÀO TẠO CAMPUSCORE'
                : 'ACADEMIC AFFAIRS OFFICE — CAMPUSCORE SYSTEM'}
            </div>
            <h1 className="text-xl font-black text-primary mt-2 uppercase tracking-wide">
              {locale === 'vi' ? 'THỜI KHÓA BIỂU HỌC KỲ' : 'OFFICIAL SEMESTER TIMETABLE'}
            </h1>
            <div className="flex flex-wrap justify-center gap-6 mt-3 text-xs text-foreground font-medium">
              <span><strong>{locale === 'vi' ? 'Học kỳ:' : 'Semester:'}</strong> {selectedSemesterName}</span>
              {user ? (
                <>
                  <span><strong>{locale === 'vi' ? 'Sinh viên:' : 'Student:'}</strong> {user.lastName} {user.firstName}</span>
                  <span><strong>{locale === 'vi' ? 'MSSV:' : 'Student ID:'}</strong> {user.studentId ?? user.id?.slice(0, 10)}</span>
                </>
              ) : null}
              <span><strong>{copy.totalCourses}:</strong> {uniqueCoursesCount}</span>
              <span><strong>{copy.totalCredits}:</strong> {totalCreditsCount}</span>
              <span><strong>{copy.totalMeetings}:</strong> {agenda.length}</span>
            </div>
          </div>

          {/* Top Control Bar & Quick Highlights */}
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-xl border border-border/80 bg-card p-4 shadow-sm print:hidden">
            {/* View Mode Tabs */}
            <div className="flex items-center gap-1 rounded-lg bg-secondary/60 p-1">
              <Button
                variant={viewMode === 'grid' ? 'default' : 'ghost'}
                size="sm"
                onClick={() => setViewMode('grid')}
                className="gap-2 text-xs font-semibold"
              >
                <LayoutGrid className="h-3.5 w-3.5" />
                {copy.viewGrid}
              </Button>
              <Button
                variant={viewMode === 'day' ? 'default' : 'ghost'}
                size="sm"
                onClick={() => setViewMode('day')}
                className="gap-2 text-xs font-semibold"
              >
                <CalendarDays className="h-3.5 w-3.5" />
                {copy.viewDay}
              </Button>
              <Button
                variant={viewMode === 'list' ? 'default' : 'ghost'}
                size="sm"
                onClick={() => setViewMode('list')}
                className="gap-2 text-xs font-semibold"
              >
                <List className="h-3.5 w-3.5" />
                {copy.viewList}
              </Button>
            </div>

            {/* Quick Metrics & Actions */}
            <div className="flex items-center gap-3">
              <div className="hidden sm:flex items-center gap-3 border-r border-border/80 pr-4 text-xs font-medium text-muted-foreground">
                <span className="flex items-center gap-1.5">
                  <GraduationCap className="h-4 w-4 text-primary" />
                  <strong className="text-foreground">{uniqueCoursesCount}</strong> {copy.totalCourses}
                </span>
                <span>•</span>
                <span className="flex items-center gap-1.5">
                  <Clock className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                  <strong className="text-foreground">{agenda.length}</strong> {copy.totalMeetings}
                </span>
                <span>•</span>
                <span className="flex items-center gap-1.5">
                  <span className="font-extrabold text-primary">TC</span>
                  <strong className="text-foreground">{totalCreditsCount}</strong> {copy.totalCredits}
                </span>
              </div>
              <Button
                variant="outline"
                size="sm"
                onClick={handlePrint}
                className="gap-2 text-xs font-medium text-foreground hover:bg-secondary"
              >
                <Printer className="h-3.5 w-3.5" />
                {copy.print}
              </Button>
            </div>
          </div>

          {/* MAIN SCHEDULE VIEW AREA */}
          {viewMode === 'grid' && (
            <div className="space-y-6">
              <Card variant="muted" className="min-w-0 overflow-hidden border-border/80 shadow-sm">
                <CardHeader className="border-b border-border/70 bg-card/50 pb-4">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <CardTitle className="text-xl font-bold flex items-center gap-2">
                      <Clock className="h-5 w-5 text-primary" />
                      {copy.weeklyAgenda}
                    </CardTitle>
                    <div className="flex items-center gap-2 text-xs text-muted-foreground">
                      <Sparkles className="h-3.5 w-3.5 text-primary" />
                      <span>{copy.clickToViewDetails}</span>
                    </div>
                  </div>
                </CardHeader>
                <CardContent className="p-4 sm:p-6">
                  {/* Desktop / Tablet Weekly Grid */}
                  <div className="hidden md:block">
                    <div
                      className="grid gap-2 overflow-x-auto pb-2"
                      style={{ gridTemplateColumns: '72px repeat(7, minmax(130px, 1fr))' }}
                      role="table"
                      aria-label={copy.weeklyGrid}
                    >
                      <div style={{ gridColumn: 1, gridRow: 1 }} />
                      {ORDERED_DAYS.map((dayNum, colIndex) => {
                        const dayName = locale === 'vi' ? DAY_LABELS_VI[dayNum] : DAY_LABELS_EN[dayNum];
                        const isToday = dayNum === todayDow;
                        return (
                          <div
                            key={`grid-header-${dayNum}`}
                            className={`rounded-lg py-2.5 px-1 text-center transition-all ${
                              isToday
                                ? 'border border-primary/50 bg-primary/10 text-primary shadow-sm font-black'
                                : 'bg-secondary/40 text-foreground font-bold'
                            }`}
                            style={{ gridColumn: colIndex + 2, gridRow: 1 }}
                          >
                            <div className="text-xs uppercase tracking-wider">{dayName}</div>
                            {isToday ? (
                              <span className="mt-0.5 inline-block rounded-full bg-primary px-2 py-0.2 text-[9px] font-bold text-primary-foreground">
                                {copy.today}
                              </span>
                            ) : null}
                          </div>
                        );
                      })}

                      {weeklyGrid.slots.map((slot, slotIndex) => (
                        <Fragment key={`slot-${slot}`}>
                          <div
                            className="flex items-start justify-end pr-2 pt-2 text-xs font-semibold tabular-nums text-muted-foreground"
                            style={{ gridColumn: 1, gridRow: slotIndex + 2 }}
                          >
                            {slot}
                          </div>
                          {ORDERED_DAYS.map((dayNum, colIndex) => {
                            const cellItems = weeklyGrid.cells[`${dayNum}-${slot}`] ?? [];
                            const isToday = dayNum === todayDow;

                            if (cellItems.length === 0) {
                              // Skip rendering cell if this time slot is covered by an earlier meeting spanning downwards
                              const isCoveredByEarlier = agenda.some(
                                (m) => m.dayOfWeek === dayNum && m.startTime < slot && slot < m.endTime,
                              );
                              if (isCoveredByEarlier) {
                                return null;
                              }

                              return (
                                <div
                                  key={`cell-${dayNum}-${slot}`}
                                  className={`min-h-[105px] rounded-lg border border-dashed border-border/70 transition hover:border-primary/40 hover:bg-card/90 ${
                                    isToday ? 'bg-primary/[0.02] border-primary/20' : 'bg-card/60'
                                  }`}
                                  style={{ gridColumn: colIndex + 2, gridRow: slotIndex + 2 }}
                                />
                              );
                            }

                            const rowSpan = Math.max(...cellItems.map((item) => item.span ?? 1));
                            return (
                              <div
                                key={`cell-${dayNum}-${slot}`}
                                className="flex min-w-0 flex-col gap-1.5"
                                style={{
                                  gridColumn: colIndex + 2,
                                  gridRow: `${slotIndex + 2} / span ${rowSpan}`,
                                }}
                              >
                                {cellItems.map((item) => {
                                  const matchingAgenda = agenda.find((a) => a.id === item.id);
                                  const courseName = getLocalizedName(
                                    locale,
                                    {
                                      code: item.courseCode,
                                      name: item.courseName,
                                      nameEn: item.courseNameEn,
                                      nameVi: item.courseNameVi,
                                    },
                                    item.courseName,
                                  );

                                  const isMorning = (item.startTime || '').localeCompare('12:00') < 0;
                                  const isAfternoon =
                                    (item.startTime || '').localeCompare('12:00') >= 0 &&
                                    (item.startTime || '').localeCompare('18:00') < 0;
                                  const shiftBadge = isMorning
                                    ? (locale === 'vi' ? 'Sáng' : 'AM')
                                    : isAfternoon
                                    ? (locale === 'vi' ? 'Chiều' : 'PM')
                                    : (locale === 'vi' ? 'Tối' : 'Eve');

                                  return (
                                    <div
                                      key={item.id}
                                      onClick={() => {
                                        setSelectedDetail({
                                          courseCode: item.courseCode,
                                          courseName: item.courseName ?? item.courseCode,
                                          courseNameEn: item.courseNameEn,
                                          courseNameVi: item.courseNameVi,
                                          sectionNumber: item.sectionNumber,
                                          dayOfWeek: item.dayOfWeek,
                                          startTime: item.startTime,
                                          endTime: item.endTime,
                                          building: item.building,
                                          roomNumber: item.roomNumber,
                                          credits: matchingAgenda?.credits,
                                          lecturerName: matchingAgenda?.lecturerName,
                                          status: matchingAgenda?.status,
                                        });
                                      }}
                                      className={`flex-1 rounded-xl border p-2.5 text-xs leading-tight transition-all hover:scale-[1.01] hover:shadow-md cursor-pointer ${accentForCourse(
                                        item.courseCode,
                                      )}`}
                                      title={`${item.courseCode} - ${item.startTime}-${item.endTime} (${copy.clickToViewDetails})`}
                                    >
                                      <div className="flex items-center justify-between gap-1">
                                        <span className="font-mono text-[11px] font-extrabold tracking-wide">
                                          {item.courseCode}
                                        </span>
                                        <div className="flex items-center gap-1">
                                          <span className="rounded bg-background/80 px-1.5 py-0.5 text-[9px] font-semibold text-foreground border border-border/40">
                                            {item.sectionNumber}
                                          </span>
                                          <span className="rounded bg-primary/10 text-primary px-1.5 py-0.5 text-[9px] font-bold">
                                            {shiftBadge}
                                          </span>
                                        </div>
                                      </div>
                                      <div
                                        className="mt-1.5 font-bold text-[12px] leading-snug line-clamp-2 text-foreground"
                                        title={courseName}
                                      >
                                        {courseName}
                                      </div>
                                      <div className="mt-2 space-y-1 text-[10px] text-muted-foreground border-t border-current/10 pt-1.5">
                                        <div className="flex items-center justify-between gap-1">
                                          <span className="inline-flex items-center gap-1 font-medium">
                                            <Clock className="h-3 w-3 text-primary" />
                                            {item.startTime}-{item.endTime}
                                          </span>
                                          {item.roomNumber ? (
                                            <span className="inline-flex items-center gap-1 font-bold text-foreground">
                                              <MapPin className="h-3 w-3 text-primary" />
                                              {item.building ? `${item.building}-` : ''}
                                              {item.roomNumber}
                                            </span>
                                          ) : null}
                                        </div>
                                        {matchingAgenda?.lecturerName ? (
                                          <div className="flex items-center gap-1 truncate text-foreground/80 font-medium" title={matchingAgenda.lecturerName}>
                                            <GraduationCap className="h-3 w-3 shrink-0 text-primary" />
                                            <span className="truncate">{matchingAgenda.lecturerName}</span>
                                          </div>
                                        ) : null}
                                      </div>
                                    </div>
                                  );
                                })}
                              </div>
                            );
                          })}
                        </Fragment>
                      ))}
                    </div>
                  </div>

                  {/* Mobile stacked fallback */}
                  <div className="grid gap-4 md:hidden">
                    {ORDERED_DAYS.map((dayNum) => {
                      const dayName = locale === 'vi' ? DAY_LABELS_VI[dayNum] : DAY_LABELS_EN[dayNum];
                      const items = agendaByDay[dayNum] ?? [];
                      const isToday = dayNum === todayDow;

                      return (
                        <div
                          key={`mobile-day-${dayNum}`}
                          className={`rounded-xl border p-4 transition-all ${
                            isToday
                              ? 'border-primary/50 bg-primary/[0.04]'
                              : 'border-border/70 bg-card'
                          }`}
                        >
                          <div className="mb-3 flex items-center justify-between">
                            <div className="flex items-center gap-2">
                              <h3 className="font-bold text-foreground text-sm">{dayName}</h3>
                              {isToday ? (
                                <span className="rounded-full bg-primary px-2 py-0.5 text-[9px] font-bold text-primary-foreground">
                                  {copy.today}
                                </span>
                              ) : null}
                            </div>
                            <span className="text-xs text-muted-foreground font-semibold">
                              {items.length} {items.length === 1 ? copy.item : copy.items}
                            </span>
                          </div>

                          {items.length === 0 ? (
                            <p className="text-xs text-muted-foreground italic">{copy.noMeetings}</p>
                          ) : (
                            <div className="space-y-2.5">
                              {items.map((item) => (
                                <div
                                  key={item.id}
                                  onClick={() => setSelectedDetail(item)}
                                  className={`rounded-lg border p-3 text-xs transition hover:shadow-sm cursor-pointer ${accentForCourse(
                                    item.courseCode,
                                  )}`}
                                >
                                  <div className="flex items-center justify-between">
                                    <span className="font-mono font-bold">{item.courseCode}</span>
                                    <span className="rounded bg-background/60 px-1.5 py-0.5 text-[10px]">
                                      {copy.sectionPrefix} {item.sectionNumber}
                                    </span>
                                  </div>
                                  <div className="mt-1 font-semibold text-foreground text-xs">
                                    {getLocalizedCourseLabel(
                                      locale,
                                      {
                                        code: item.courseCode,
                                        name: item.courseName,
                                        nameEn: item.courseNameEn,
                                        nameVi: item.courseNameVi,
                                      },
                                      item.courseName,
                                    )}
                                  </div>
                                  <div className="mt-2 flex items-center gap-3 text-[11px] text-muted-foreground">
                                    <span className="inline-flex items-center gap-1">
                                      <Clock className="h-3 w-3" />
                                      {item.startTime} - {item.endTime}
                                    </span>
                                    {item.roomNumber ? (
                                      <span className="inline-flex items-center gap-1 font-medium text-foreground">
                                        <MapPin className="h-3 w-3" />
                                        {item.building ? `${item.building}-` : ''}
                                        {item.roomNumber}
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
                  </div>
                </CardContent>
              </Card>
            </div>
          )}

          {/* DAY VIEW AREA */}
          {viewMode === 'day' && (
            <div className="space-y-6">
              {/* Day Selector Pills */}
              <div className="flex flex-wrap items-center gap-2">
                {ORDERED_DAYS.map((dayNum) => {
                  const dayName = locale === 'vi' ? DAY_LABELS_VI[dayNum] : DAY_LABELS_EN[dayNum];
                  const count = (agendaByDay[dayNum] ?? []).length;
                  const isSelected = selectedDayTab === dayNum;
                  const isToday = dayNum === todayDow;

                  return (
                    <button
                      key={`day-tab-${dayNum}`}
                      type="button"
                      onClick={() => setSelectedDayTab(dayNum)}
                      className={`flex items-center gap-2 rounded-xl px-4 py-2.5 text-xs font-bold transition-all ${
                        isSelected
                          ? 'bg-primary text-primary-foreground shadow-md'
                          : 'border border-border bg-card text-foreground hover:bg-secondary'
                      }`}
                    >
                      <span>{dayName}</span>
                      {isToday ? (
                        <span className="rounded bg-background/20 px-1 text-[9px] font-extrabold uppercase">
                          {copy.today}
                        </span>
                      ) : null}
                      <span
                        className={`rounded-full px-1.5 py-0.2 text-[10px] ${
                          isSelected ? 'bg-primary-foreground/20' : 'bg-secondary text-muted-foreground'
                        }`}
                      >
                        {count}
                      </span>
                    </button>
                  );
                })}
              </div>

              {/* Day Details Card */}
              <Card variant="muted" className="border-border/80 shadow-sm">
                <CardHeader className="border-b border-border/70 pb-4">
                  <div className="flex items-center justify-between">
                    <CardTitle className="text-lg font-bold flex items-center gap-2">
                      <Calendar className="h-5 w-5 text-primary" />
                      {locale === 'vi' ? DAY_LABELS_VI[selectedDayTab] : DAY_LABELS_EN[selectedDayTab]}
                    </CardTitle>
                    <span className="text-xs text-muted-foreground font-semibold">
                      {(agendaByDay[selectedDayTab] ?? []).length} {copy.items}
                    </span>
                  </div>
                </CardHeader>
                <CardContent className="p-4 sm:p-6">
                  {(agendaByDay[selectedDayTab] ?? []).length === 0 ? (
                    <div className="py-12 text-center text-muted-foreground">
                      <Clock className="mx-auto h-10 w-10 text-muted-foreground/50" />
                      <p className="mt-3 text-sm font-medium">{copy.noMeetings}</p>
                    </div>
                  ) : (
                    <div className="grid gap-4 sm:grid-cols-2">
                      {(agendaByDay[selectedDayTab] ?? []).map((item) => (
                        <div
                          key={item.id}
                          onClick={() => setSelectedDetail(item)}
                          className={`rounded-2xl border p-4 transition-all hover:scale-[1.01] hover:shadow-md cursor-pointer ${accentForCourse(
                            item.courseCode,
                          )}`}
                        >
                          <div className="flex items-center justify-between gap-2">
                            <span className="font-mono text-xs font-extrabold">{item.courseCode}</span>
                            <span className="rounded bg-background/70 px-2 py-0.5 text-xs font-semibold text-foreground">
                              {copy.sectionPrefix} {item.sectionNumber}
                            </span>
                          </div>
                          <h4 className="mt-2 text-sm font-bold text-foreground">
                            {getLocalizedCourseLabel(
                              locale,
                              {
                                code: item.courseCode,
                                name: item.courseName,
                                nameEn: item.courseNameEn,
                                nameVi: item.courseNameVi,
                              },
                              item.courseName,
                            )}
                          </h4>
                          <div className="mt-3 space-y-1.5 border-t border-current/10 pt-2.5 text-xs text-muted-foreground">
                            <div className="flex items-center gap-2">
                              <Clock className="h-3.5 w-3.5 text-primary" />
                              <span className="font-medium text-foreground">
                                {item.startTime} - {item.endTime}
                              </span>
                            </div>
                            {item.roomNumber ? (
                              <div className="flex items-center gap-2">
                                <MapPin className="h-3.5 w-3.5 text-primary" />
                                <span className="font-medium text-foreground">
                                  {locale === 'vi' ? 'Phòng' : 'Room'}: {item.building ? `${item.building}-` : ''}
                                  {item.roomNumber}
                                </span>
                              </div>
                            ) : null}
                            {item.lecturerName ? (
                              <div className="flex items-center gap-2">
                                <GraduationCap className="h-3.5 w-3.5 text-primary" />
                                <span>{item.lecturerName}</span>
                              </div>
                            ) : null}
                          </div>
                        </div>
                      ))}
                    </div>
                  )}
                </CardContent>
              </Card>
            </div>
          )}

          {/* LIST VIEW AREA: Executive Academic Timetable Table */}
          {viewMode === 'list' && (
            <Card variant="muted" className="border-border/80 shadow-sm overflow-hidden">
              <CardHeader className="border-b border-border/70 bg-card/60 pb-4">
                <div className="flex flex-wrap items-center justify-between gap-3">
                  <CardTitle className="text-lg font-bold flex items-center gap-2">
                    <List className="h-5 w-5 text-primary" />
                    {copy.upcomingClassList}
                  </CardTitle>
                  <span className="text-xs text-muted-foreground font-semibold">
                    {formatNumber(agenda.length)} {agenda.length === 1 ? copy.item : copy.items}
                  </span>
                </div>
              </CardHeader>
              <CardContent className="p-0">
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-collapse">
                    <thead>
                      <tr className="border-b border-border/80 bg-secondary/50 font-bold text-muted-foreground uppercase tracking-wider text-[11px]">
                        <th className="py-3 px-4">#</th>
                        <th className="py-3 px-4">{copy.courseCode}</th>
                        <th className="py-3 px-4">{copy.courseName}</th>
                        <th className="py-3 px-4 text-center">{copy.credits}</th>
                        <th className="py-3 px-4 text-center">{copy.section}</th>
                        <th className="py-3 px-4">{copy.day}</th>
                        <th className="py-3 px-4">{copy.time}</th>
                        <th className="py-3 px-4">{copy.classroom}</th>
                        <th className="py-3 px-4">{copy.lecturer}</th>
                        <th className="py-3 px-4 text-center">{copy.actions}</th>
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-border/60 bg-card">
                      {agenda.map((item, idx) => {
                        const dayName = locale === 'vi' ? DAY_LABELS_VI[item.dayOfWeek] : DAY_LABELS_EN[item.dayOfWeek];
                        const isToday = item.dayOfWeek === todayDow;
                        return (
                          <tr
                            key={item.id}
                            className={`transition hover:bg-secondary/30 ${isToday ? 'bg-primary/[0.03]' : ''}`}
                          >
                            <td className="py-3 px-4 font-mono text-muted-foreground font-semibold">{idx + 1}</td>
                            <td className="py-3 px-4 font-mono font-bold text-primary">{item.courseCode}</td>
                            <td className="py-3 px-4 font-semibold text-foreground max-w-[220px]">
                              {getLocalizedCourseLabel(
                                locale,
                                {
                                  code: item.courseCode,
                                  name: item.courseName,
                                  nameEn: item.courseNameEn,
                                  nameVi: item.courseNameVi,
                                },
                                item.courseName,
                              )}
                            </td>
                            <td className="py-3 px-4 text-center font-bold text-foreground">{item.credits ?? '—'}</td>
                            <td className="py-3 px-4 text-center">
                              <span className="rounded bg-secondary px-2 py-0.5 font-semibold text-[10px] text-foreground">
                                {item.sectionNumber}
                              </span>
                            </td>
                            <td className="py-3 px-4 font-medium">
                              <span className={isToday ? 'font-bold text-primary' : 'text-foreground'}>
                                {dayName}
                              </span>
                            </td>
                            <td className="py-3 px-4 font-mono font-medium text-foreground whitespace-nowrap">
                              {item.startTime} - {item.endTime}
                            </td>
                            <td className="py-3 px-4 font-semibold text-foreground">
                              {item.roomNumber ? (
                                <span className="inline-flex items-center gap-1">
                                  <MapPin className="h-3 w-3 text-primary" />
                                  {item.building ? `${item.building}-` : ''}{item.roomNumber}
                                </span>
                              ) : (
                                <span className="text-muted-foreground italic">{copy.roomPending}</span>
                              )}
                            </td>
                            <td className="py-3 px-4 text-muted-foreground max-w-[150px] truncate">
                              {item.lecturerName || '—'}
                            </td>
                            <td className="py-3 px-4 text-center">
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() => setSelectedDetail(item)}
                                className="h-7 px-2.5 text-xs font-semibold text-primary hover:text-primary hover:bg-primary/10"
                              >
                                {copy.actions}
                              </Button>
                            </td>
                          </tr>
                        );
                      })}
                    </tbody>
                  </table>
                </div>
              </CardContent>
            </Card>
          )}
        </div>
      )}

      {/* Class Schedule Detail Modal */}
      <ClassScheduleDetailModal
        isOpen={Boolean(selectedDetail)}
        onClose={() => setSelectedDetail(null)}
        data={selectedDetail}
        locale={locale}
      />
    </div>
  );
}
