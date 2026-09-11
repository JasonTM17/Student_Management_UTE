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
  Users,
} from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { useRequireAuth } from '@/context/AuthContext';
import { sectionsApi, semestersApi } from '@/lib/api';
import {
  getLocalizedCourseLabel,
  getLocalizedFlatLabel,
  getLocalizedName,
} from '@/lib/academic-content';
import { buildWeeklyGrid } from '@/lib/weekly-grid';
import { LecturerSection, Semester } from '@/types/api';
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

type LecturerAgendaItem = {
  id: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  sectionNumber: string;
  dayOfWeek: number; // 1=Sunday, 2=Monday, ..., 7=Saturday
  startTime: string;
  endTime: string;
  building?: string;
  roomNumber?: string;
  departmentName?: string;
  departmentNameEn?: string;
  departmentNameVi?: string;
  enrolledCount: number;
  capacity?: number;
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
 * Unified enterprise academic theme for all class blocks across all roles.
 * Clean, consistent, and distraction-free institutional styling without rainbow colors.
 */
const UNIFIED_COURSE_ACCENT =
  'bg-primary/[0.04] dark:bg-primary/[0.08] text-foreground border-primary/25 dark:border-primary/35 hover:border-primary/60 hover:bg-primary/[0.08] dark:hover:bg-primary/[0.12]';

const accentForCourse = (_courseCode?: string): string => UNIFIED_COURSE_ACCENT;

export default function LecturerSchedulePage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatNumber } = useI18n();
  const [sections, setSections] = useState<LecturerSection[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [selectedDetail, setSelectedDetail] = useState<ScheduleDetailData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  // 3 view options matching student schedule: 'grid' (Lưới tuần), 'day' (Theo ngày), 'list' (Danh sách)
  const [viewMode, setViewMode] = useState<'grid' | 'day' | 'list'>('grid');

  // Today indicator: 1 = Sunday, 2 = Monday ... 7 = Saturday
  const todayDow = useMemo(() => {
    const jsDay = new Date().getDay();
    return jsDay === 0 ? 1 : jsDay + 1;
  }, []);

  const [selectedDayTab, setSelectedDayTab] = useState<number>(todayDow);

  const fetchSemesters = useCallback(async () => {
    const response = await semestersApi.getAll();
    setSemesters(response.data ?? []);
  }, []);

  const fetchSchedule = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const data = await sectionsApi.getMySchedule(selectedSemester || undefined);
      setSections(data);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải phân công giảng dạy.'
          : 'Teaching assignments could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, selectedSemester]);

  useEffect(() => {
    if (hasAccess) {
      void fetchSemesters();
    }
  }, [fetchSemesters, hasAccess]);

  useEffect(() => {
    if (hasAccess) {
      void fetchSchedule();
    }
  }, [fetchSchedule, hasAccess]);

  /** Maps assigned sections into meeting agenda items for timetable display */
  const agenda = useMemo(() => {
    const items: LecturerAgendaItem[] = [];

    sections.forEach((section) => {
      section.schedules?.forEach((schedule, index) => {
        const day = schedule.dayOfWeek === 0 ? 1 : schedule.dayOfWeek;
        items.push({
          id: `${section.id}-${index}`,
          courseCode: section.courseCode,
          courseName: section.courseName,
          courseNameEn: section.courseNameEn,
          courseNameVi: section.courseNameVi,
          sectionNumber: section.sectionNumber,
          dayOfWeek: day,
          startTime: schedule.startTime,
          endTime: schedule.endTime,
          building: schedule.building,
          roomNumber: schedule.roomNumber,
          departmentName: section.departmentName,
          departmentNameEn: section.departmentNameEn,
          departmentNameVi: section.departmentNameVi,
          enrolledCount: section.enrolledCount,
          capacity: section.capacity,
          status: section.status,
        });
      });
    });

    return items.sort((left, right) => {
      if (left.dayOfWeek !== right.dayOfWeek) {
        return left.dayOfWeek - right.dayOfWeek;
      }
      return (left.startTime || '').localeCompare(right.startTime || '');
    });
  }, [sections]);

  /** Full academic week day mapping for agenda grouping */
  const slotsByDay = useMemo(() => {
    const map = {} as Record<number, LecturerAgendaItem[]>;
    [1, 2, 3, 4, 5, 6, 7].map((d) => {
      map[d] = agenda.filter((item) => item.dayOfWeek === d);
    });
    return map;
  }, [agenda]);

  const weeklyGrid = useMemo(() => buildWeeklyGrid(agenda), [agenda]);

  const totalStudentsCount = useMemo(() => {
    return sections.reduce((sum, section) => sum + (section.enrolledCount || 0), 0);
  }, [sections]);

  const selectedSemesterName = useMemo(() => {
    return (
      getLocalizedName(
        locale,
        semesters.find((semester) => semester.id === selectedSemester),
        locale === 'vi' ? 'tất cả học kỳ' : 'all semesters',
      ) ??
      (locale === 'vi' ? 'tất cả học kỳ' : 'all semesters')
    );
  }, [locale, selectedSemester, semesters]);

  const handlePrint = () => {
    window.print();
  };

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu giảng viên',
          title: 'Lịch giảng dạy',
          description: 'Thời khóa biểu giảng dạy và lịch công tác học kỳ của giảng viên.',
          selectSemester: 'Chọn học kỳ cho lịch giảng dạy',
          allSemesters: 'Tất cả học kỳ',
          loading: 'Đang tải lịch giảng dạy',
          unavailableTitle: 'Lịch giảng dạy chưa sẵn sàng',
          emptyTitle: 'Chưa có phân công giảng dạy',
          emptyDescription:
            'Khi lớp học phần được phân công, các buổi giảng dạy sẽ xuất hiện tại đây theo lịch tuần.',
          weeklyAgenda: 'Lịch dạy theo tuần',
          weeklyGrid: 'Lưới thời khóa biểu',
          assignedSectionsList: 'Danh sách lớp học phần giảng dạy',
          noTeachingSlot: 'Chưa có ca giảng dạy nào.',
          items: 'mục',
          item: 'mục',
          sectionPrefix: 'Lớp HP',
          roomPending: 'Chưa xếp phòng',
          today: 'HÔM NAY',
          viewGrid: 'Lưới tuần',
          viewDay: 'Theo ngày',
          viewList: 'Danh sách',
          print: 'In lịch giảng dạy',
          totalSections: 'Lớp học phần',
          totalSlots: 'Buổi dạy / tuần',
          totalStudents: 'Tổng sinh viên',
          clickToViewDetails: 'Bấm vào lớp học phần để xem chi tiết đầy đủ',
          courseCode: 'Mã HP',
          courseName: 'Tên học phần',
          section: 'Lớp HP',
          department: 'Khoa / Bộ môn',
          day: 'Thứ',
          time: 'Giờ dạy',
          classroom: 'Phòng học',
          enrolled: 'Sĩ số',
          actions: 'Chi tiết',
        }
      : {
          eyebrow: 'Lecturer area',
          title: 'Teaching schedule',
          description: 'Lecturer semester teaching timetable and class schedule.',
          selectSemester: 'Select semester for teaching schedule',
          allSemesters: 'All semesters',
          loading: 'Loading teaching schedule',
          unavailableTitle: 'Teaching schedule unavailable',
          emptyTitle: 'No teaching assignments yet',
          emptyDescription:
            'Once class sections are assigned, teaching meetings will appear here in the weekly schedule.',
          weeklyAgenda: 'Weekly teaching agenda',
          weeklyGrid: 'Weekly timetable grid',
          assignedSectionsList: 'Teaching class sections list',
          noTeachingSlot: 'No teaching slot scheduled.',
          items: 'items',
          item: 'item',
          sectionPrefix: 'Section',
          roomPending: 'Pending room',
          today: 'TODAY',
          viewGrid: 'Weekly Grid',
          viewDay: 'Day View',
          viewList: 'List View',
          print: 'Print Schedule',
          totalSections: 'Classes',
          totalSlots: 'Sessions / week',
          totalStudents: 'Students',
          clickToViewDetails: 'Click on any class to view complete details',
          courseCode: 'Code',
          courseName: 'Course Title',
          section: 'Section',
          department: 'Department',
          day: 'Day',
          time: 'Time',
          classroom: 'Room',
          enrolled: 'Enrolled',
          actions: 'Details',
        };

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
            <Button
              variant="outline"
              size="sm"
              onClick={handlePrint}
              className="gap-2 text-xs font-semibold"
            >
              <Printer className="h-3.5 w-3.5" />
              {copy.print}
            </Button>
          </div>
        }
      />

      {/* Official University Letterhead for Print */}
      <div className="hidden print:block text-center border-b-2 border-primary pb-4 mb-6">
        <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {locale === 'vi'
            ? 'ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH'
            : 'HO CHI MINH CITY UNIVERSITY OF TECHNOLOGY AND ENGINEERING'}
        </div>
        <div className="text-sm font-extrabold text-foreground">
          {locale === 'vi'
            ? 'PHÒNG ĐÀO TẠO — HỆ THỐNG QUẢN LÝ ĐÀO TẠO CAMPUSCORE'
            : 'ACADEMIC AFFAIRS OFFICE — CAMPUSCORE SYSTEM'}
        </div>
        <h1 className="text-xl font-black text-primary mt-2 uppercase tracking-wide">
          {locale === 'vi' ? 'LỊCH GIẢNG DẠY HỌC KỲ' : 'OFFICIAL TEACHING TIMETABLE'}
        </h1>
        <div className="flex flex-wrap justify-center gap-6 mt-3 text-xs text-foreground font-medium">
          <span><strong>{locale === 'vi' ? 'Học kỳ:' : 'Semester:'}</strong> {selectedSemesterName}</span>
          {user ? (
            <>
              <span><strong>{locale === 'vi' ? 'Giảng viên:' : 'Lecturer:'}</strong> {user.lastName} {user.firstName}</span>
              <span><strong>Email:</strong> {user.email}</span>
            </>
          ) : null}
          <span><strong>{copy.totalSections}:</strong> {sections.length}</span>
          <span><strong>{copy.totalSlots}:</strong> {agenda.length}</span>
          <span><strong>{copy.totalStudents}:</strong> {totalStudentsCount}</span>
        </div>
      </div>

      {error ? (
        <ErrorState
          title={copy.unavailableTitle}
          description={error}
          onRetry={() => void fetchSchedule()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : agenda.length === 0 ? (
        <EmptyState
          icon={Calendar}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
        />
      ) : (
        <div className="space-y-6">
          {/* Top Control Bar & Quick Highlights (Matching Student Schedule) */}
          <div className="flex flex-wrap items-center justify-between gap-4 rounded-lg border border-border/80 bg-card p-4 shadow-sm print:hidden">
            {/* View Mode Tabs */}
            <div
              role="tablist"
              aria-label={locale === 'vi' ? 'Chế độ xem lịch giảng dạy' : 'Schedule view mode'}
              className="flex items-center gap-1 rounded-lg bg-secondary/60 p-1"
            >
              <Button
                role="tab"
                aria-selected={viewMode === 'grid'}
                variant={viewMode === 'grid' ? 'default' : 'ghost'}
                size="sm"
                onClick={() => setViewMode('grid')}
                className="gap-2 text-xs font-semibold"
              >
                <LayoutGrid className="h-3.5 w-3.5" />
                {copy.viewGrid}
              </Button>
              <Button
                role="tab"
                aria-selected={viewMode === 'day'}
                variant={viewMode === 'day' ? 'default' : 'ghost'}
                size="sm"
                onClick={() => setViewMode('day')}
                className="gap-2 text-xs font-semibold"
              >
                <CalendarDays className="h-3.5 w-3.5" />
                {copy.viewDay}
              </Button>
              <Button
                role="tab"
                aria-selected={viewMode === 'list'}
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
                  <strong className="text-foreground">{sections.length}</strong> {copy.totalSections}
                </span>
                <span>•</span>
                <span className="flex items-center gap-1.5">
                  <Clock className="h-4 w-4 text-emerald-600 dark:text-emerald-400" />
                  <strong className="text-foreground">{agenda.length}</strong> {copy.totalSlots}
                </span>
                <span>•</span>
                <span className="flex items-center gap-1.5">
                  <Users className="h-4 w-4 text-blue-600 dark:text-blue-400" />
                  <strong className="text-foreground">{totalStudentsCount}</strong> {copy.totalStudents}
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
                                          departmentName: matchingAgenda?.departmentName,
                                          departmentNameEn: matchingAgenda?.departmentNameEn,
                                          departmentNameVi: matchingAgenda?.departmentNameVi,
                                          enrolledCount: matchingAgenda?.enrolledCount,
                                          capacity: matchingAgenda?.capacity,
                                          status: matchingAgenda?.status,
                                        });
                                      }}
                                      className={`flex-1 rounded-lg border p-2.5 text-xs leading-tight transition-all hover:scale-[1.01] hover:shadow-md cursor-pointer ${accentForCourse(
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
                                        {matchingAgenda?.enrolledCount !== undefined ? (
                                          <div className="flex items-center gap-1 truncate text-foreground/80 font-medium">
                                            <Users className="h-3 w-3 shrink-0 text-primary" />
                                            <span>{matchingAgenda.enrolledCount} {locale === 'vi' ? 'sinh viên' : 'students'}</span>
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
                      const items = slotsByDay[dayNum] ?? [];
                      const isToday = dayNum === todayDow;

                      return (
                        <div
                          key={`mobile-day-${dayNum}`}
                          className={`rounded-lg border p-4 transition-all ${
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
                            <p className="text-xs text-muted-foreground italic">{copy.noTeachingSlot}</p>
                          ) : (
                            <div className="space-y-2.5">
                              {items.map((item) => (
                                <div
                                  key={item.id}
                                  onClick={() =>
                                    setSelectedDetail({
                                      courseCode: item.courseCode,
                                      courseName: item.courseName,
                                      courseNameEn: item.courseNameEn,
                                      courseNameVi: item.courseNameVi,
                                      sectionNumber: item.sectionNumber,
                                      dayOfWeek: item.dayOfWeek,
                                      startTime: item.startTime,
                                      endTime: item.endTime,
                                      building: item.building,
                                      roomNumber: item.roomNumber,
                                      departmentName: item.departmentName,
                                      departmentNameEn: item.departmentNameEn,
                                      departmentNameVi: item.departmentNameVi,
                                      enrolledCount: item.enrolledCount,
                                      capacity: item.capacity,
                                      status: item.status,
                                    })
                                  }
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
                                  <div className="mt-2 flex items-center justify-between text-[11px] text-muted-foreground">
                                    <span className="inline-flex items-center gap-1">
                                      <Clock className="h-3 w-3 text-primary" />
                                      {item.startTime} - {item.endTime}
                                    </span>
                                    {item.roomNumber ? (
                                      <span className="inline-flex items-center gap-1 font-medium text-foreground">
                                        <MapPin className="h-3 w-3 text-primary" />
                                        {item.building ? `${item.building}-` : ''}{item.roomNumber}
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
                  const count = (slotsByDay[dayNum] ?? []).length;
                  const isSelected = selectedDayTab === dayNum;
                  const isToday = dayNum === todayDow;

                  return (
                    <button
                      key={`day-tab-${dayNum}`}
                      type="button"
                      onClick={() => setSelectedDayTab(dayNum)}
                      className={`flex items-center gap-2 rounded-lg px-4 py-2.5 text-xs font-bold transition-all ${
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
                      {(slotsByDay[selectedDayTab] ?? []).length} {copy.items}
                    </span>
                  </div>
                </CardHeader>
                <CardContent className="p-4 sm:p-6">
                  {(slotsByDay[selectedDayTab] ?? []).length === 0 ? (
                    <div className="py-12 text-center text-muted-foreground">
                      <Clock className="mx-auto h-10 w-10 text-muted-foreground/50" />
                      <p className="mt-3 text-sm font-medium">{copy.noTeachingSlot}</p>
                    </div>
                  ) : (
                    <div className="grid gap-4 sm:grid-cols-2">
                      {(slotsByDay[selectedDayTab] ?? []).map((item) => (
                        <div
                          key={item.id}
                          onClick={() =>
                            setSelectedDetail({
                              courseCode: item.courseCode,
                              courseName: item.courseName,
                              courseNameEn: item.courseNameEn,
                              courseNameVi: item.courseNameVi,
                              sectionNumber: item.sectionNumber,
                              dayOfWeek: item.dayOfWeek,
                              startTime: item.startTime,
                              endTime: item.endTime,
                              building: item.building,
                              roomNumber: item.roomNumber,
                              departmentName: item.departmentName,
                              departmentNameEn: item.departmentNameEn,
                              departmentNameVi: item.departmentNameVi,
                              enrolledCount: item.enrolledCount,
                              capacity: item.capacity,
                              status: item.status,
                            })
                          }
                          className={`rounded-lg border p-4 transition-all hover:scale-[1.01] hover:shadow-md cursor-pointer ${accentForCourse(
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
                            {item.enrolledCount !== undefined ? (
                              <div className="flex items-center gap-2">
                                <Users className="h-3.5 w-3.5 text-primary" />
                                <span>{item.enrolledCount} {locale === 'vi' ? 'sinh viên' : 'students'}</span>
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
                    {copy.assignedSectionsList}
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
                        <th className="py-3 px-4 text-center">{copy.section}</th>
                        <th className="py-3 px-4">{copy.department}</th>
                        <th className="py-3 px-4">{copy.day}</th>
                        <th className="py-3 px-4">{copy.time}</th>
                        <th className="py-3 px-4">{copy.classroom}</th>
                        <th className="py-3 px-4 text-center">{copy.enrolled}</th>
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
                            <td className="py-3 px-4 text-center">
                              <span className="rounded bg-secondary px-2 py-0.5 font-semibold text-[10px] text-foreground">
                                {item.sectionNumber}
                              </span>
                            </td>
                            <td className="py-3 px-4 text-muted-foreground max-w-[160px] truncate">
                              {getLocalizedFlatLabel(
                                locale,
                                item.departmentName,
                                item.departmentNameEn,
                                item.departmentNameVi,
                                item.departmentName || '—',
                              )}
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
                            <td className="py-3 px-4 text-center font-bold text-foreground">
                              {item.enrolledCount}
                              {item.capacity ? `/${item.capacity}` : ''}
                            </td>
                            <td className="py-3 px-4 text-center">
                              <Button
                                size="sm"
                                variant="ghost"
                                onClick={() =>
                                  setSelectedDetail({
                                    courseCode: item.courseCode,
                                    courseName: item.courseName,
                                    courseNameEn: item.courseNameEn,
                                    courseNameVi: item.courseNameVi,
                                    sectionNumber: item.sectionNumber,
                                    dayOfWeek: item.dayOfWeek,
                                    startTime: item.startTime,
                                    endTime: item.endTime,
                                    building: item.building,
                                    roomNumber: item.roomNumber,
                                    departmentName: item.departmentName,
                                    departmentNameEn: item.departmentNameEn,
                                    departmentNameVi: item.departmentNameVi,
                                    enrolledCount: item.enrolledCount,
                                    capacity: item.capacity,
                                    status: item.status,
                                  })
                                }
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

          {/* Official Signatures for Print View */}
          <div className="hidden print:grid grid-cols-2 gap-8 pt-8 mt-6 border-t border-border/80 text-center text-xs">
            <div>
              <p className="font-semibold">{locale === 'vi' ? 'TRƯỞNG KHOA / BỘ MÔN' : 'DEAN / DEPARTMENT HEAD'}</p>
              <p className="text-muted-foreground mt-1">{locale === 'vi' ? '(Ký và ghi rõ họ tên)' : '(Signature & Full name)'}</p>
              <div className="h-16" />
            </div>
            <div>
              <p className="font-semibold">{locale === 'vi' ? 'GIẢNG VIÊN GIẢNG DẠY' : 'COURSE INSTRUCTOR'}</p>
              <p className="text-muted-foreground mt-1">{locale === 'vi' ? '(Ký và ghi rõ họ tên)' : '(Signature & Full name)'}</p>
              <div className="h-16" />
              {user ? <p className="font-medium text-foreground">{user.lastName} {user.firstName}</p> : null}
            </div>
          </div>
        </div>
      )}

      {/* Class Schedule Detail Modal */}
      <ClassScheduleDetailModal
        isOpen={Boolean(selectedDetail)}
        onClose={() => setSelectedDetail(null)}
        data={selectedDetail}
        locale={locale}
        isLecturer={true}
      />
    </div>
  );
}
