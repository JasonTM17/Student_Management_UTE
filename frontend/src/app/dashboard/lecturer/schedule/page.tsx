'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Calendar, Clock, MapPin, Printer, Sparkles, Users } from 'lucide-react';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { useRequireAuth } from '@/context/AuthContext';
import { sectionsApi, semestersApi } from '@/lib/api';
import {
  getLocalizedCourseLabel,
  getLocalizedFlatLabel,
  getLocalizedName,
} from '@/lib/academic-content';
import { LecturerSection, Semester } from '@/types/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import { metricToneClass } from '@/components/ui/status';
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

type TeachingSlot = {
  id: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  sectionNumber: string;
  dayOfWeek: number;
  rawDayOfWeek?: number;
  startTime: string;
  endTime: string;
  building: string;
  roomNumber: string;
  enrolledCount: number;
  departmentName?: string;
  departmentNameEn?: string;
  departmentNameVi?: string;
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

export default function LecturerSchedulePage() {
  const { user, hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatNumber, messages } = useI18n();
  const [sections, setSections] = useState<LecturerSection[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [selectedDetail, setSelectedDetail] = useState<ScheduleDetailData | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

  const todayIso = useMemo(() => {
    const jsDay = new Date().getDay(); // 0 = Sun, 1 = Mon ... 6 = Sat
    return jsDay === 0 ? 7 : jsDay;
  }, []);

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

  const slots = useMemo(() => {
    return sections
      .flatMap((section) =>
        section.schedules.map((schedule, index) => {
          const rawDay = schedule.dayOfWeek;
          const isoDay = rawDay === 0 || rawDay === 1 ? 7 : rawDay - 1;

          return {
            id: `${section.id}-${index}`,
            courseCode: section.courseCode,
            courseName: section.courseName,
            courseNameEn: section.courseNameEn,
            courseNameVi: section.courseNameVi,
            sectionNumber: section.sectionNumber,
            dayOfWeek: isoDay,
            rawDayOfWeek: rawDay,
            startTime: schedule.startTime,
            endTime: schedule.endTime,
            building: schedule.building,
            roomNumber: schedule.roomNumber,
            enrolledCount: section.enrolledCount,
            departmentName: section.departmentName,
            departmentNameEn: section.departmentNameEn,
            departmentNameVi: section.departmentNameVi,
          };
        }),
      )
      .sort((left, right) => {
        if (left.dayOfWeek !== right.dayOfWeek) {
          return left.dayOfWeek - right.dayOfWeek;
        }
        return (left.startTime || '').localeCompare(right.startTime || '');
      });
  }, [sections]);

  const slotsByDay = useMemo(() => {
    return slots.reduce<Record<number, TeachingSlot[]>>((groups, slot) => {
      if (!groups[slot.dayOfWeek]) {
        groups[slot.dayOfWeek] = [];
      }

      groups[slot.dayOfWeek].push(slot);
      return groups;
    }, {});
  }, [slots]);

  const selectedSemesterName = useMemo(() => {
    return (
      getLocalizedName(
        locale,
        semesters.find((semester) => semester.id === selectedSemester),
        locale === 'vi' ? 't\u1ea5t c\u1ea3 h\u1ecdc k\u1ef3' : 'all semesters',
      ) ??
      (locale === 'vi' ? 'tất cả học kỳ' : 'all semesters')
    );
  }, [locale, selectedSemester, semesters]);

  const localizedDayNames =
    locale === 'vi'
      ? ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy']
      : dayNames;

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Khu giảng viên',
          title: 'Lịch giảng dạy',
          description: `Giữ thời khóa biểu của ${selectedSemesterName} luôn hiển thị trong khi chấm điểm và quản lý lớp học phần vẫn chỉ cách một lần chạm.`,
          selectSemester: 'Chọn học kỳ cho lịch giảng dạy',
          allSemesters: 'Tất cả học kỳ',
          loading: 'Đang tải lịch giảng dạy',
          unavailableTitle: 'Lịch giảng dạy chưa sẵn sàng',
          emptyTitle: 'Chưa có phân công giảng dạy',
          emptyDescription:
            'Các lớp học phần có lịch học đang hoạt động sẽ xuất hiện tại đây sau khi được phân công.',
          teachingSlots: 'Ca giảng dạy',
          assignedSections: 'Lớp học phần được giao',
          studentsInScope: 'Sinh viên trong phạm vi',
          weeklyAgenda: 'Lịch dạy theo tuần',
          assignedSectionsTitle: 'Lớp học phần được giao',
          noTeachingSlot: 'Chưa có ca giảng dạy nào.',
          items: 'mục',
          item: 'mục',
          sectionPrefix: 'Lớp học phần',
          studentsSuffix: 'sinh viên',
          today: 'Hôm nay',
          printSchedule: 'In lịch',
        }
      : {
          eyebrow: 'Lecturer area',
          title: 'Teaching schedule',
          description: `Keep your timetable for ${selectedSemesterName} visible while grading and class work stays one click away.`,
          selectSemester: 'Select semester for teaching schedule',
          allSemesters: 'All semesters',
          loading: 'Loading teaching schedule',
          unavailableTitle: 'Teaching schedule unavailable',
          emptyTitle: 'No teaching assignments yet',
          emptyDescription:
            'Classes with active classroom schedules will appear here once they are assigned.',
          teachingSlots: 'Teaching slots',
          assignedSections: 'Assigned classes',
          studentsInScope: 'Students in scope',
          weeklyAgenda: 'Weekly agenda',
          assignedSectionsTitle: 'Assigned classes',
          noTeachingSlot: 'No teaching slot scheduled.',
          items: 'items',
          item: 'item',
          sectionPrefix: 'Class',
          studentsSuffix: 'students',
          today: 'Today',
          printSchedule: 'Print',
        };

  const statusLabel = (status: string) =>
    messages.common.statuses[status.toUpperCase() as keyof typeof messages.common.statuses] ??
    messages.common.statuses.UNKNOWN;

  if (authLoading) {
    return <LoadingState label={copy.loading} />;
  }

  if (!hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  return (
    <div className="space-y-8">
      <div className="print:hidden">
        <PageHeader
          eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
          title={copy.title}
          description={copy.description}
          actions={
            <div className="flex flex-wrap items-center gap-3">
              <div className="min-w-0 sm:min-w-[260px]">
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
                onClick={() => window.print()}
                className="flex items-center gap-2"
              >
                <Printer className="h-4 w-4" />
                <span>{copy.printSchedule}</span>
              </Button>
            </div>
          }
        />
      </div>

      {/* Official University Letterhead for Print */}
      <div className="hidden print:block text-center border-b-2 border-primary pb-4 mb-6">
        <div className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
          {locale === 'vi'
            ? 'TRƯỜNG ĐẠI HỌC SƯ PHẠM KỸ THUẬT TP. HỒ CHÍ MINH'
            : 'HCMC UNIVERSITY OF TECHNOLOGY AND EDUCATION'}
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
          <span><strong>{copy.teachingSlots}:</strong> {slots.length}</span>
          <span><strong>{copy.assignedSections}:</strong> {sections.length}</span>
          <span><strong>{copy.studentsInScope}:</strong> {sections.reduce((sum, s) => sum + s.enrolledCount, 0)}</span>
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
      ) : slots.length === 0 ? (
        <EmptyState
          icon={Calendar}
          title={copy.emptyTitle}
          description={copy.emptyDescription}
        />
      ) : (
        <>
          <div className="grid gap-4 md:grid-cols-3">
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.teachingSlots}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(slots.length)}
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
                  <div className="text-sm text-muted-foreground">{copy.assignedSections}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(sections.length)}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('success')}`}>
                  <Users className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
            <Card variant="elevated">
              <CardContent className="flex items-center justify-between gap-4 pt-6">
                <div>
                  <div className="text-sm text-muted-foreground">{copy.studentsInScope}</div>
                  <div className="mt-1 text-3xl font-semibold tracking-tight text-foreground">
                    {formatNumber(
                      sections.reduce((sum, section) => sum + section.enrolledCount, 0),
                    )}
                  </div>
                </div>
                <div className={`flex h-11 w-11 items-center justify-center rounded-lg ${metricToneClass('neutral')}`}>
                  <Users className="h-5 w-5" />
                </div>
              </CardContent>
            </Card>
          </div>

          <div className="grid gap-6 xl:grid-cols-[1.05fr_0.95fr]">
            <Card variant="muted">
              <CardHeader>
                <CardTitle className="text-xl">{copy.weeklyAgenda}</CardTitle>
              </CardHeader>
              <CardContent className="grid gap-4 md:grid-cols-2">
                {[1, 2, 3, 4, 5, 6, 7].map((dayOfWeek) => {
                  const dayName = localizedDayNames[dayOfWeek % 7];
                  const items = slotsByDay[dayOfWeek] ?? [];
                  const isToday = dayOfWeek === todayIso;

                  return (
                    <div
                      key={dayName}
                      className={`rounded-lg border px-4 py-4 transition-colors ${
                        isToday
                          ? 'border-primary/50 bg-primary/5 dark:bg-primary/10 shadow-sm ring-1 ring-primary/20'
                          : 'border-border/70 bg-card'
                      }`}
                    >
                      <div className="mb-3 flex items-center justify-between">
                        <div className="flex items-center gap-2">
                          <h2 className="font-semibold text-foreground">{dayName}</h2>
                          {isToday && (
                            <span className="rounded-full bg-primary/15 px-2 py-0.5 text-xs font-semibold text-primary">
                              {copy.today}
                            </span>
                          )}
                        </div>
                        <span className="text-xs uppercase tracking-[0.16em] text-muted-foreground">
                          {formatNumber(items.length)}{' '}
                          {items.length === 1 ? copy.item : copy.items}
                        </span>
                      </div>

                      {items.length === 0 ? (
                        <p className="text-sm text-muted-foreground">
                          {copy.noTeachingSlot}
                        </p>
                      ) : (
                        <div className="space-y-3">
                          {items.map((slot) => (
                            <div
                              key={slot.id}
                              role="button"
                              tabIndex={0}
                              onClick={() =>
                                setSelectedDetail({
                                  courseCode: slot.courseCode,
                                  courseName: slot.courseName,
                                  courseNameEn: slot.courseNameEn,
                                  courseNameVi: slot.courseNameVi,
                                  sectionNumber: slot.sectionNumber,
                                  dayOfWeek: slot.rawDayOfWeek ?? (slot.dayOfWeek === 7 ? 1 : slot.dayOfWeek + 1),
                                  startTime: slot.startTime,
                                  endTime: slot.endTime,
                                  building: slot.building,
                                  roomNumber: slot.roomNumber,
                                  departmentName: slot.departmentName,
                                  departmentNameEn: slot.departmentNameEn,
                                  departmentNameVi: slot.departmentNameVi,
                                  enrolledCount: slot.enrolledCount,
                                })
                              }
                              onKeyDown={(event) => {
                                if (event.key === 'Enter' || event.key === ' ') {
                                  event.preventDefault();
                                  setSelectedDetail({
                                    courseCode: slot.courseCode,
                                    courseName: slot.courseName,
                                    courseNameEn: slot.courseNameEn,
                                    courseNameVi: slot.courseNameVi,
                                    sectionNumber: slot.sectionNumber,
                                    dayOfWeek: slot.rawDayOfWeek ?? (slot.dayOfWeek === 7 ? 1 : slot.dayOfWeek + 1),
                                    startTime: slot.startTime,
                                    endTime: slot.endTime,
                                    building: slot.building,
                                    roomNumber: slot.roomNumber,
                                    departmentName: slot.departmentName,
                                    departmentNameEn: slot.departmentNameEn,
                                    departmentNameVi: slot.departmentNameVi,
                                    enrolledCount: slot.enrolledCount,
                                  });
                                }
                              }}
                              className="group cursor-pointer rounded-lg border border-border/60 bg-secondary/30 px-4 py-3 transition-all hover:border-primary/50 hover:bg-card hover:shadow-sm"
                            >
                              <div className="flex items-start justify-between gap-2">
                                <div className="font-medium text-foreground transition-colors group-hover:text-primary">
                                  {getLocalizedCourseLabel(
                                    locale,
                                    {
                                      code: slot.courseCode,
                                      name: slot.courseName,
                                      nameEn: slot.courseNameEn,
                                      nameVi: slot.courseNameVi,
                                    },
                                    `${slot.courseCode} - ${slot.courseName}`,
                                  )}
                                </div>
                                <span className="rounded bg-primary/10 px-1.5 py-0.5 font-mono text-xs font-medium text-primary">
                                  {slot.courseCode}
                                </span>
                              </div>
                              <div className="mt-1 flex items-center justify-between text-sm text-muted-foreground">
                                <span>{copy.sectionPrefix} {slot.sectionNumber}</span>
                                {slot.startTime && (
                                  <span
                                    className={`rounded-full border px-2 py-0.5 text-[10px] font-bold uppercase tracking-wider ${
                                      slot.startTime < '12:00'
                                        ? 'border-amber-300 bg-amber-100 text-amber-900 dark:border-amber-800 dark:bg-amber-950/60 dark:text-amber-300'
                                        : slot.startTime < '18:00'
                                        ? 'border-blue-300 bg-blue-100 text-blue-900 dark:border-blue-800 dark:bg-blue-950/60 dark:text-blue-300'
                                        : 'border-purple-300 bg-purple-100 text-purple-900 dark:border-purple-800 dark:bg-purple-950/60 dark:text-purple-300'
                                    }`}
                                  >
                                    {slot.startTime < '12:00'
                                      ? (locale === 'vi' ? 'Ca Sáng' : 'Morning')
                                      : slot.startTime < '18:00'
                                      ? (locale === 'vi' ? 'Ca Chiều' : 'Afternoon')
                                      : (locale === 'vi' ? 'Ca Tối' : 'Evening')}
                                  </span>
                                )}
                              </div>
                              <div className="mt-2 flex flex-wrap items-center gap-3 text-sm text-muted-foreground">
                                <span className="inline-flex items-center gap-1.5">
                                  <Clock className="h-3.5 w-3.5 text-primary" />
                                  {slot.startTime} - {slot.endTime}
                                </span>
                                <span className="inline-flex items-center gap-1.5">
                                  <MapPin className="h-3.5 w-3.5 text-primary" />
                                  {slot.building} {slot.roomNumber}
                                </span>
                                <span className="inline-flex items-center gap-1.5">
                                  <Users className="h-3.5 w-3.5 text-primary" />
                                  {formatNumber(slot.enrolledCount)} {copy.studentsSuffix}
                                </span>
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
                <CardTitle className="text-xl">{copy.assignedSectionsTitle}</CardTitle>
              </CardHeader>
              <CardContent className="space-y-3">
                {sections.map((section) => (
                  <div
                    key={section.id}
                    className="rounded-lg border border-border/70 bg-card px-4 py-4"
                  >
                    <div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                      <div>
                        <div className="font-medium text-foreground">
                          {getLocalizedCourseLabel(
                            locale,
                            {
                              code: section.courseCode,
                              name: section.courseName,
                              nameEn: section.courseNameEn,
                              nameVi: section.courseNameVi,
                            },
                            `${section.courseCode} - ${section.courseName}`,
                          )}
                        </div>
                        <div className="mt-1 text-sm text-muted-foreground">
                          {copy.sectionPrefix} {section.sectionNumber} - {getLocalizedFlatLabel(
                            locale,
                            section.departmentName,
                            section.departmentNameEn,
                            section.departmentNameVi,
                            section.departmentName,
                          )}
                        </div>
                      </div>
                      <div className="text-sm text-muted-foreground sm:text-right">
                        <div>
                          {formatNumber(section.enrolledCount)}/{formatNumber(section.capacity)}{' '}
                          {copy.studentsSuffix}
                        </div>
                        <div className="mt-1">
                          {statusLabel(section.status)}
                        </div>
                      </div>
                    </div>
                  </div>
                ))}
              </CardContent>
            </Card>
          </div>

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
        </>
      )}

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
