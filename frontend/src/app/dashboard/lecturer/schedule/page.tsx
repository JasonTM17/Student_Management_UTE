'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { Calendar, Clock, MapPin, Users } from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { sectionsApi, semestersApi } from '@/lib/api';
import {
  getLocalizedCourseLabel,
  getLocalizedFlatLabel,
  getLocalizedName,
} from '@/lib/academic-content';
import { LecturerSection, Semester } from '@/types/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useI18n } from '@/i18n';

type TeachingSlot = {
  id: string;
  courseCode: string;
  courseName: string;
  courseNameEn?: string;
  courseNameVi?: string;
  sectionNumber: string;
  dayOfWeek: number;
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
  const { hasAccess, isLoading: authLoading } = useRequireAuth(['LECTURER']);
  const { locale, formatNumber } = useI18n();
  const [sections, setSections] = useState<LecturerSection[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');

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
        section.schedules.map((schedule, index) => ({
          id: `${section.id}-${index}`,
          courseCode: section.courseCode,
          courseName: section.courseName,
          courseNameEn: section.courseNameEn,
          courseNameVi: section.courseNameVi,
          sectionNumber: section.sectionNumber,
          dayOfWeek: schedule.dayOfWeek,
          startTime: schedule.startTime,
          endTime: schedule.endTime,
          building: schedule.building,
          roomNumber: schedule.roomNumber,
          enrolledCount: section.enrolledCount,
          departmentName: section.departmentName,
          departmentNameEn: section.departmentNameEn,
          departmentNameVi: section.departmentNameVi,
        })),
      )
      .sort((left, right) => {
        if (left.dayOfWeek !== right.dayOfWeek) {
          return left.dayOfWeek - right.dayOfWeek;
        }
        return left.startTime.localeCompare(right.startTime);
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
          eyebrow: 'Không gian giảng viên',
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
        }
      : {
          eyebrow: 'Lecturer workspace',
          title: 'Teaching schedule',
          description: `Keep your timetable for ${selectedSemesterName} visible while grading and section work stays one click away.`,
          selectSemester: 'Select semester for teaching schedule',
          allSemesters: 'All semesters',
          loading: 'Loading teaching schedule',
          unavailableTitle: 'Teaching schedule unavailable',
          emptyTitle: 'No teaching assignments yet',
          emptyDescription:
            'Sections with active classroom schedules will appear here once they are assigned.',
          teachingSlots: 'Teaching slots',
          assignedSections: 'Assigned sections',
          studentsInScope: 'Students in scope',
          weeklyAgenda: 'Weekly agenda',
          assignedSectionsTitle: 'Assigned sections',
          noTeachingSlot: 'No teaching slot scheduled.',
          items: 'items',
          item: 'item',
          sectionPrefix: 'Section',
          studentsSuffix: 'students',
        };

  if (authLoading || !hasAccess) {
    return <LoadingState label={copy.loading} />;
  }

  return (
    <div className="space-y-8">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={copy.description}
        actions={
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
        }
      />

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
                <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-blue-500/12 text-blue-600 dark:text-blue-400">
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
                <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-emerald-500/12 text-emerald-600 dark:text-emerald-400">
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
                <div className="flex h-11 w-11 items-center justify-center rounded-lg bg-violet-500/12 text-violet-600 dark:text-violet-400">
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
                {localizedDayNames.slice(1, 6).map((dayName, index) => {
                  const dayOfWeek = index + 1;
                  const items = slotsByDay[dayOfWeek] ?? [];

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
                          {copy.noTeachingSlot}
                        </p>
                      ) : (
                        <div className="space-y-3">
                          {items.map((slot) => (
                            <div
                              key={slot.id}
                              className="rounded-lg border border-border/60 bg-secondary/30 px-4 py-3"
                            >
                              <div className="font-medium text-foreground">
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
                              <div className="mt-1 text-sm text-muted-foreground">
                                {copy.sectionPrefix} {slot.sectionNumber}
                              </div>
                              <div className="mt-2 flex flex-wrap gap-3 text-sm text-muted-foreground">
                                <span className="inline-flex items-center gap-2">
                                  <Clock className="h-4 w-4" />
                                  {slot.startTime} - {slot.endTime}
                                </span>
                                <span className="inline-flex items-center gap-2">
                                  <MapPin className="h-4 w-4" />
                                  {slot.building} {slot.roomNumber}
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
                          {section.status}
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
