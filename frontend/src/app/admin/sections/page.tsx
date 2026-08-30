'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { ClipboardList, Pencil, Plus, Trash2, X } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import {
  adminSectionsApi,
  adminSemestersApi,
  classroomsApi,
  coursesApi,
  lecturersApi,
} from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminDialogFooter,
  AdminFormField,
  AdminFormSection,
  AdminPaginationFooter,
  AdminRowActions,
  AdminTableCard,
  AdminTableScroll,
  AdminToolbarCard,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import {
  getLocalizedCourseLabel,
  getLocalizedName,
} from '@/lib/academic-content';
import {
  ACADEMIC_REFERENCE_LIMIT,
  PEOPLE_REFERENCE_LIMIT,
} from '@/lib/reference-data';

interface Section {
  id: string;
  sectionNumber: string;
  courseId: string;
  semesterId: string;
  lecturerId?: string;
  capacity: number;
  status: 'OPEN' | 'CLOSED' | 'CANCELLED';
  course?: {
    code: string;
    name: string;
    nameEn?: string;
    nameVi?: string;
    department?: { name: string; nameEn?: string; nameVi?: string };
  };
  semester?: { name: string; nameEn?: string; nameVi?: string; type?: string };
  lecturer?: { user?: { firstName: string; lastName: string } };
  schedules?: {
    id: string;
    dayOfWeek: number;
    startTime: string;
    endTime: string;
    classroom?: { id?: string; building: string; roomNumber: string };
  }[];
}

function sectionStatusTone(status: Section['status']): StatusTone {
  if (status === 'OPEN') return 'success';
  if (status === 'CANCELLED') return 'danger';
  return 'neutral';
}

interface Course {
  id: string;
  code: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
}

interface Semester {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  type?: string;
}

interface Lecturer {
  id: string;
  employeeId: string;
  user?: { firstName: string; lastName: string };
}

interface Classroom {
  id: string;
  building: string;
  roomNumber: string;
}

interface ScheduleEntry {
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  classroomId: string;
}

const sectionStatuses = ['OPEN', 'CLOSED', 'CANCELLED'] as const;
type SectionStatus = (typeof sectionStatuses)[number];

function isSectionStatus(value: string): value is SectionStatus {
  return sectionStatuses.includes(value as SectionStatus);
}

export default function AdminSectionsPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { formatNumber, href, locale, messages } = useI18n();
  const router = useRouter();
  const [sections, setSections] = useState<Section[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [lecturers, setLecturers] = useState<Lecturer[]>([]);
  const [classrooms, setClassrooms] = useState<Classroom[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [referenceError, setReferenceError] = useState('');
  const [semesterFilter, setSemesterFilter] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingSection, setEditingSection] = useState<Section | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formData, setFormData] = useState({
    courseId: '',
    semesterId: '',
    sectionNumber: '',
    capacity: 30,
    status: 'OPEN',
    lecturerId: '',
  });
  const [schedules, setSchedules] = useState<ScheduleEntry[]>([]);
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const { confirm, confirmationDialog } = useConfirmationDialog();

  useEffect(() => {
    if (isAuthLoading || isLoggingOut) {
      return;
    }

    if (!user) {
      router.replace(`${href('/login')}?portal=admin&reason=session-expired`);
      return;
    }

    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [href, isAdmin, isSuperAdmin, isAuthLoading, isLoggingOut, router, user]);

  const fetchDropdownData = useCallback(async () => {
    setReferenceError('');
    const [coursesResult, semestersResult, lecturersResult, classroomsResult] =
      await Promise.allSettled([
        coursesApi.getAll({ limit: ACADEMIC_REFERENCE_LIMIT }),
        adminSemestersApi.getAll({ limit: ACADEMIC_REFERENCE_LIMIT }),
        lecturersApi.getAll({ limit: PEOPLE_REFERENCE_LIMIT }),
        classroomsApi.getAll({ limit: ACADEMIC_REFERENCE_LIMIT }),
      ]);

    if (coursesResult.status === 'fulfilled') setCourses(coursesResult.value.data || []);
    if (semestersResult.status === 'fulfilled') setSemesters(semestersResult.value.data || []);
    if (lecturersResult.status === 'fulfilled') setLecturers(lecturersResult.value.data || []);
    if (classroomsResult.status === 'fulfilled') setClassrooms(classroomsResult.value.data || []);

    if ([coursesResult, semestersResult, lecturersResult, classroomsResult].some(
      (result) => result.status === 'rejected',
    )) {
      setReferenceError(
        locale === 'vi'
          ? 'Một hoặc nhiều danh sách cần cho biểu mẫu lớp học phần chưa tải được.'
          : 'Some course lists needed by this page could not be loaded.',
      );
    }
  }, [locale]);

  const fetchSections = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await adminSectionsApi.getAll({
        page,
        limit: 20,
        semesterId: semesterFilter || undefined,
      });
      setSections(response.data || []);
      setTotalPages(response.meta?.totalPages || 1);
    } catch {
      setError(
        locale === 'vi'
          ? 'Hiện chưa thể tải danh sách lớp học phần.'
          : 'Sections could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, page, semesterFilter]);

  const dayNames = useMemo(
    () =>
      locale === 'vi'
        ? [
            'Chủ nhật',
            'Thứ hai',
            'Thứ ba',
            'Thứ tư',
            'Thứ năm',
            'Thứ sáu',
            'Thứ bảy',
          ]
        : [
            'Sunday',
            'Monday',
            'Tuesday',
            'Wednesday',
            'Thursday',
            'Friday',
            'Saturday',
          ],
    [locale],
  );

  const copy =
    locale === 'vi'
      ? {
          loading: 'Đang tải lớp học phần',
          title: 'Lớp học phần',
          description:
            'Quản lý sức chứa, phân công giảng dạy và lịch phòng học trong một quy trình nhất quán.',
          create: 'Tạo lớp học phần',
          semester: 'Học kỳ',
          allSemesters: 'Tất cả học kỳ',
          selectCourse: 'Chọn môn học',
          selectSemester: 'Chọn học kỳ',
          noLecturerAssigned: 'Chưa phân công giảng viên',
          selectRoom: 'Chọn phòng',
          pageSummary: (currentPage: number, pages: number) =>
            `Trang ${currentPage} / ${pages}`,
          unavailableTitle: 'Lớp học phần chưa sẵn sàng',
          emptyTitle: 'Không có lớp học phần phù hợp',
          emptyDescription:
            'Hãy tạo lớp học phần để liên kết môn học, giảng viên và lịch phòng học trong cùng một thông tin.',
          tableTitle: 'Danh sách lớp học phần',
          headers: {
            course: 'Môn học',
            section: 'Lớp học phần',
            semester: 'Học kỳ',
            lecturer: 'Giảng viên',
            capacity: 'Sức chứa',
            schedule: 'Lịch học',
            status: 'Trạng thái',
            actions: 'Tác vụ',
          },
          unknownCourse: 'Chưa rõ môn học',
          noCourseName: 'Chưa có tên môn',
          unassigned: 'Chưa gán',
          noSchedule: 'Chưa có lịch',
          deleteTitle: 'Xóa lớp học phần',
          deleteMessage: (sectionNumber: string, courseCode: string) =>
            `Xóa lớp học phần ${sectionNumber} của ${courseCode}?`,
          deleteConfirm: 'Xóa lớp học phần',
          deleted: 'Đã xóa lớp học phần',
          deleteFailed: 'Hiện chưa thể xóa lớp học phần này.',
          updated: 'Đã cập nhật lớp học phần',
          created: 'Đã tạo lớp học phần',
          saveFailed: 'Hiện chưa thể lưu lớp học phần.',
          editTitle: 'Chỉnh sửa lớp học phần',
          createTitle: 'Tạo lớp học phần',
          fields: {
            course: 'Môn học',
            semester: 'Học kỳ',
            sectionNumber: 'Mã lớp học phần',
            capacity: 'Sức chứa',
            status: 'Trạng thái',
            lecturer: 'Giảng viên',
          },
          schedulesTitle: 'Lịch học',
          schedulesDescription:
            'Chỉ thêm lịch khi lớp học phần đã có học kỳ, phòng và người phụ trách rõ ràng.',
          addSchedule: 'Thêm lịch',
          noSchedules: 'Chưa có lịch nào.',
          removeSchedule: (index: number) => `Xóa lịch ${index}`,
          saving: 'Đang lưu...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (sectionNumber: string, courseCode: string) =>
            `Chỉnh sửa lớp học phần ${sectionNumber} của ${courseCode}`,
          deleteLabel: (sectionNumber: string, courseCode: string) =>
            `Xóa lớp học phần ${sectionNumber} của ${courseCode}`,
        }
      : {
          loading: 'Loading sections',
          title: 'Sections',
          description:
            'Manage class capacity, teaching assignments, and classroom schedules from one consistent workflow.',
          create: 'Create section',
          semester: 'Semester',
          allSemesters: 'All semesters',
          selectCourse: 'Select course',
          selectSemester: 'Select semester',
          noLecturerAssigned: 'No lecturer assigned',
          selectRoom: 'Select room',
          pageSummary: (currentPage: number, pages: number) =>
            `Page ${currentPage} of ${pages}`,
          unavailableTitle: 'Sections unavailable',
          emptyTitle: 'No matching sections',
          emptyDescription:
            'Create a class to connect course details, lecturer assignment, and room scheduling in one record.',
          tableTitle: 'Section records',
          headers: {
            course: 'Course',
            section: 'Section',
            semester: 'Semester',
            lecturer: 'Lecturer',
            capacity: 'Capacity',
            schedule: 'Schedule',
            status: 'Status',
            actions: 'Actions',
          },
          unknownCourse: 'Unknown course',
          noCourseName: 'No course name',
          unassigned: 'Unassigned',
          noSchedule: 'No schedule',
          deleteTitle: 'Delete section',
          deleteMessage: (sectionNumber: string, courseCode: string) =>
            `Delete section ${sectionNumber} for ${courseCode}?`,
          deleteConfirm: 'Delete section',
          deleted: 'Section deleted',
          deleteFailed: 'We could not delete that section.',
          updated: 'Section updated',
          created: 'Section created',
          saveFailed: 'The section could not be saved.',
          editTitle: 'Edit section',
          createTitle: 'Create section',
          fields: {
            course: 'Course',
            semester: 'Semester',
            sectionNumber: 'Section number',
            capacity: 'Capacity',
            status: 'Status',
            lecturer: 'Lecturer',
          },
          schedulesTitle: 'Schedules',
          schedulesDescription:
            'Add meeting times only when the class already has a semester, room, and assigned lecturer.',
          addSchedule: 'Add schedule',
          noSchedules: 'No schedules added yet.',
          removeSchedule: (index: number) => `Remove schedule ${index}`,
          saving: 'Saving...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (sectionNumber: string, courseCode: string) =>
            `Edit section ${sectionNumber} for ${courseCode}`,
          deleteLabel: (sectionNumber: string, courseCode: string) =>
            `Delete section ${sectionNumber} for ${courseCode}`,
        };

  useEffect(() => {
    if (canAccess) {
      void fetchDropdownData();
      void fetchSections();
    }
  }, [canAccess, fetchDropdownData, fetchSections]);

  const semesterFilterOptions = useMemo(
    () => [
      { value: '', label: copy.allSemesters },
      ...semesters.map((semester) => ({
        value: semester.id,
        label: getLocalizedName(locale, semester, semester.name),
      })),
    ],
    [copy.allSemesters, locale, semesters],
  );

  const courseOptions = useMemo(
    () => [
      { value: '', label: copy.selectCourse },
      ...courses.map((course) => ({
        value: course.id,
        label: getLocalizedCourseLabel(locale, course, `${course.code} - ${course.name}`),
      })),
    ],
    [copy.selectCourse, courses, locale],
  );

  const semesterOptions = useMemo(
    () => [
      { value: '', label: copy.selectSemester },
      ...semesters.map((semester) => ({
        value: semester.id,
        label: getLocalizedName(locale, semester, semester.name),
      })),
    ],
    [copy.selectSemester, locale, semesters],
  );

  const lecturerOptions = useMemo(
    () => [
      { value: '', label: copy.noLecturerAssigned },
      ...lecturers.map((lecturer) => ({
        value: lecturer.id,
        label: lecturer.user
          ? `${lecturer.user.firstName} ${lecturer.user.lastName} (${lecturer.employeeId})`
          : lecturer.employeeId,
      })),
    ],
    [copy.noLecturerAssigned, lecturers],
  );

  const classroomOptions = useMemo(
    () => [
      { value: '', label: copy.selectRoom },
      ...classrooms.map((classroom) => ({
        value: classroom.id,
        label: `${classroom.building} ${classroom.roomNumber}`,
      })),
    ],
    [classrooms, copy.selectRoom],
  );

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = () => {
    setEditingSection(null);
    setFormData({
      courseId: '',
      semesterId: '',
      sectionNumber: '',
      capacity: 30,
      status: 'OPEN',
      lecturerId: '',
    });
    setSchedules([]);
  };

  const openCreate = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEdit = async (section: Section) => {
    setEditingSection(section);
    setFormData({
      courseId: section.courseId,
      semesterId: section.semesterId,
      sectionNumber: section.sectionNumber,
      capacity: section.capacity,
      status: section.status,
      lecturerId: section.lecturerId || '',
    });

    try {
      const fullSection = await adminSectionsApi.getById(section.id);
      setSchedules(
        (fullSection.schedules || []).map((schedule) => ({
          dayOfWeek: schedule.dayOfWeek,
          startTime: schedule.startTime,
          endTime: schedule.endTime,
          classroomId: schedule.classroom?.id || '',
        })),
      );
    } catch {
      setSchedules([]);
    }

    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    resetForm();
  };

  const handleDelete = async (section: Section) => {
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(
        section.sectionNumber,
        section.course?.code || copy.unknownCourse,
      ),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await adminSectionsApi.delete(section.id);
      toast.success(copy.deleted);
      await fetchSections();
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setIsSaving(true);

    try {
      const payload = {
        ...formData,
        capacity: Number(formData.capacity),
        schedules: schedules.length > 0 ? schedules : undefined,
      };

      if (editingSection) {
        await adminSectionsApi.update(editingSection.id, payload);
        toast.success(copy.updated);
      } else {
        await adminSectionsApi.create(payload);
        toast.success(copy.created);
      }

      closeModal();
      await fetchSections();
    } catch (requestError: any) {
      toast.error(requestError.response?.data?.message ?? copy.saveFailed);
    } finally {
      setIsSaving(false);
    }
  };

  const addSchedule = () => {
    setSchedules((current) => [
      ...current,
      { dayOfWeek: 1, startTime: '09:00', endTime: '10:30', classroomId: '' },
    ]);
  };

  const removeSchedule = (index: number) => {
    setSchedules((current) => current.filter((_, currentIndex) => currentIndex !== index));
  };

  const updateSchedule = (
    index: number,
    field: keyof ScheduleEntry,
    value: string | number,
  ) => {
    setSchedules((current) =>
      current.map((schedule, currentIndex) =>
        currentIndex === index ? { ...schedule, [field]: value } : schedule,
      ),
    );
  };

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={
        <Button onClick={openCreate}>
          <Plus className="mr-2 h-4 w-4" />
          {copy.create}
        </Button>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
            <div className="flex flex-col gap-4 md:flex-row md:items-end md:justify-between">
              <div className="w-full max-w-sm">
                <Select
                  label={copy.semester}
                  value={semesterFilter}
                  onChange={(event) => {
                    setSemesterFilter(event.target.value);
                    setPage(1);
                  }}
                  options={semesterFilterOptions}
                />
              </div>
              <div className="text-sm text-muted-foreground">
                {copy.pageSummary(page, totalPages)}
              </div>
            </div>
        </AdminToolbarCard>

        {referenceError ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={referenceError}
            onRetry={() => void fetchDropdownData()}
          />
        ) : null}

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchSections()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : sections.length === 0 ? (
          <EmptyState
            icon={ClipboardList}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
            action={<Button onClick={openCreate}>{copy.create}</Button>}
          />
        ) : (
          <AdminTableCard
            title={copy.tableTitle}
            footer={
              <AdminPaginationFooter
                summary={copy.pageSummary(page, totalPages)}
                page={page}
                totalPages={totalPages}
                onPrevious={() => setPage((current) => current - 1)}
                onNext={() => setPage((current) => current + 1)}
              />
            }
          >
              <div className="space-y-3 md:hidden" role="list" aria-label={copy.tableTitle}>
                {sections.map((section) => {
                  const courseLabel = section.course
                    ? getLocalizedCourseLabel(
                        locale,
                        section.course,
                        `${section.course.code} - ${section.course.name}`,
                      )
                    : copy.unknownCourse;
                  const semesterLabel = section.semester
                    ? getLocalizedName(
                        locale,
                        section.semester,
                        section.semester.name,
                      )
                    : copy.unassigned;
                  const lecturerLabel = section.lecturer?.user
                    ? `${section.lecturer.user.firstName} ${section.lecturer.user.lastName}`
                    : copy.unassigned;

                  return (
                    <article
                      key={`${section.id}-mobile`}
                      className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                      role="listitem"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                            {copy.headers.course}
                          </p>
                          <h3 className="mt-1 font-semibold text-foreground">
                            {courseLabel}
                          </h3>
                        </div>
                        <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(sectionStatusTone(section.status))}`}>
                          {messages.common.statuses[section.status as keyof typeof messages.common.statuses] ?? messages.common.statuses.UNKNOWN}
                        </span>
                      </div>

                      <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.section}
                          </dt>
                          <dd className="mt-1 text-foreground">{section.sectionNumber}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.semester}
                          </dt>
                          <dd className="mt-1 text-foreground">{semesterLabel}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.lecturer}
                          </dt>
                          <dd className="mt-1 text-foreground">{lecturerLabel}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.capacity}
                          </dt>
                          <dd className="mt-1 text-foreground">
                            {formatNumber(section.capacity)}
                          </dd>
                        </div>
                      </dl>

                      <div className="mt-4 border-t border-border/60 pt-3">
                        <p className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                          {copy.headers.schedule}
                        </p>
                        {section.schedules && section.schedules.length > 0 ? (
                          <div className="mt-2 space-y-1">
                            {section.schedules.map((schedule, index) => (
                              <div
                                key={schedule.id || index}
                                className="text-sm text-foreground"
                              >
                                <span className="font-medium">
                                  {dayNames[schedule.dayOfWeek]}
                                </span>{' '}
                                <span className="text-muted-foreground">
                                  {schedule.startTime}-{schedule.endTime}
                                </span>{' '}
                                <span className="text-muted-foreground">
                                  {schedule.classroom?.building}
                                  {schedule.classroom?.roomNumber
                                    ? ` ${schedule.classroom.roomNumber}`
                                    : ''}
                                </span>
                              </div>
                            ))}
                          </div>
                        ) : (
                          <p className="mt-2 text-sm text-muted-foreground">
                            {copy.noSchedule}
                          </p>
                        )}
                      </div>

                      <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                        <Button
                          size="icon"
                          variant="ghost"
                          onClick={() => void openEdit(section)}
                          aria-label={copy.editLabel(
                            section.sectionNumber,
                            section.course?.code || copy.unknownCourse,
                          )}
                          title={copy.editLabel(
                            section.sectionNumber,
                            section.course?.code || copy.unknownCourse,
                          )}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => void handleDelete(section)}
                          aria-label={copy.deleteLabel(
                            section.sectionNumber,
                            section.course?.code || copy.unknownCourse,
                          )}
                          title={copy.deleteLabel(
                            section.sectionNumber,
                            section.course?.code || copy.unknownCourse,
                          )}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </AdminRowActions>
                    </article>
                  );
                })}
              </div>
              <AdminTableScroll className="hidden md:block">
                <table className="w-full min-w-[1120px] text-sm">
                  <thead>
                    <tr className="bg-secondary text-left text-muted-foreground">
                      <th className="px-2 py-3 font-medium">{copy.headers.course}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.section}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.semester}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.lecturer}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.capacity}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.schedule}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                      <th className="px-2 py-3 text-right font-medium">{copy.headers.actions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {sections.map((section) => {
                      const courseLabel = section.course
                        ? getLocalizedCourseLabel(
                            locale,
                            section.course,
                            `${section.course.code} - ${section.course.name}`,
                          )
                        : copy.unknownCourse;
                      const semesterLabel = section.semester
                        ? getLocalizedName(
                            locale,
                            section.semester,
                            section.semester.name,
                          )
                        : copy.unassigned;

                      return (
                      <tr key={section.id}>
                        <td className="px-2 py-4">
                            <div className="space-y-1">
                              <p className="font-medium text-foreground">
                              {section.course?.code || copy.unknownCourse}
                              </p>
                              <p className="text-xs text-muted-foreground">
                              {section.course ? courseLabel : copy.noCourseName}
                              </p>
                            </div>
                          </td>
                        <td className="px-2 py-4 text-foreground">
                          {section.sectionNumber}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {semesterLabel}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {section.lecturer?.user
                            ? `${section.lecturer.user.firstName} ${section.lecturer.user.lastName}`
                            : copy.unassigned}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {formatNumber(section.capacity)}
                        </td>
                        <td className="px-2 py-4">
                          {section.schedules && section.schedules.length > 0 ? (
                            <div className="space-y-1">
                              {section.schedules.map((schedule, index) => (
                                <div key={schedule.id || index} className="text-xs">
                                  <span className="font-medium text-foreground">
                                    {dayNames[schedule.dayOfWeek]}
                                  </span>{' '}
                                  <span className="text-muted-foreground">
                                    {schedule.startTime}-{schedule.endTime}
                                  </span>{' '}
                                  <span className="text-muted-foreground">
                                    {schedule.classroom?.building}
                                    {schedule.classroom?.roomNumber
                                      ? ` ${schedule.classroom.roomNumber}`
                                      : ''}
                                  </span>
                                </div>
                              ))}
                            </div>
                          ) : (
                            <span className="text-xs text-muted-foreground">
                              {copy.noSchedule}
                            </span>
                          )}
                        </td>
                        <td className="px-2 py-4">
                          <span className={`inline-flex rounded-full px-2.5 py-1 text-xs font-medium ${statusToneClass(sectionStatusTone(section.status))}`}>
                            {messages.common.statuses[section.status as keyof typeof messages.common.statuses] ?? messages.common.statuses.UNKNOWN}
                          </span>
                        </td>
                        <td className="px-2 py-4">
                          <AdminRowActions>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => void openEdit(section)}
                              aria-label={copy.editLabel(
                                section.sectionNumber,
                                section.course?.code || copy.unknownCourse,
                              )}
                              title={copy.editLabel(
                                section.sectionNumber,
                                section.course?.code || copy.unknownCourse,
                              )}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(section)}
                              aria-label={copy.deleteLabel(
                                section.sectionNumber,
                                section.course?.code || copy.unknownCourse,
                              )}
                              title={copy.deleteLabel(
                                section.sectionNumber,
                                section.course?.code || copy.unknownCourse,
                              )}
                            >
                              <Trash2 className="h-4 w-4" />
                            </Button>
                          </AdminRowActions>
                        </td>
                      </tr>
                      );
                    })}
                  </tbody>
                </table>
              </AdminTableScroll>
          </AdminTableCard>
        )}
      </div>

      <Modal
        isOpen={isModalOpen}
        onClose={closeModal}
        title={editingSection ? copy.editTitle : copy.createTitle}
        className="max-w-3xl"
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <Select
              label={copy.fields.course}
              value={formData.courseId}
              onChange={(event) =>
                setFormData((current) => ({
                  ...current,
                  courseId: event.target.value,
                }))
              }
              options={courseOptions}
              required
            />
            <Select
              label={copy.fields.semester}
              value={formData.semesterId}
              onChange={(event) =>
                setFormData((current) => ({
                  ...current,
                  semesterId: event.target.value,
                }))
              }
              options={semesterOptions}
              required
            />
          </div>

          <div className="grid gap-4 sm:grid-cols-3">
            <AdminFormField label={copy.fields.sectionNumber}>
              <Input
                type="text"
                value={formData.sectionNumber}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    sectionNumber: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
            <AdminFormField label={copy.fields.capacity}>
              <Input
                type="number"
                min="1"
                value={formData.capacity}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    capacity: Number(event.target.value) || 30,
                  }))
                }
                required
              />
            </AdminFormField>
            <Select
              label={copy.fields.status}
              value={formData.status}
              onChange={(event) => {
                const nextStatus = event.target.value;
                if (isSectionStatus(nextStatus)) {
                  setFormData((current) => ({
                    ...current,
                    status: nextStatus,
                  }));
                }
              }}
              options={sectionStatuses.map((status) => ({
                value: status,
                label: status,
              }))}
            />
          </div>

          <Select
            label={copy.fields.lecturer}
            value={formData.lecturerId}
            onChange={(event) =>
              setFormData((current) => ({
                ...current,
                lecturerId: event.target.value,
              }))
            }
            options={lecturerOptions}
          />

          <AdminFormSection
            title={copy.schedulesTitle}
            description={copy.schedulesDescription}
          >
            <div className="flex items-center justify-between">
              <Button type="button" variant="outline" size="sm" onClick={addSchedule}>
                <Plus className="mr-2 h-4 w-4" />
                {copy.addSchedule}
              </Button>
            </div>

            {schedules.length === 0 ? (
              <div className="rounded-lg border border-dashed border-border/80 bg-secondary/20 px-4 py-6 text-sm text-muted-foreground">
                {copy.noSchedules}
              </div>
            ) : (
              <div className="space-y-3">
                {schedules.map((schedule, idx) => (
                  <div
                    key={`${schedule.dayOfWeek}-${schedule.startTime}-${idx}`}
                    className="grid gap-3 rounded-lg border border-border/70 bg-secondary/20 p-4 lg:grid-cols-[180px_1fr_1fr_220px_auto]"
                  >
                    <Select
                      value={String(schedule.dayOfWeek)}
                      onChange={(event) =>
                        updateSchedule(idx, 'dayOfWeek', Number(event.target.value))
                      }
                      options={dayNames.map((day, dayIndex) => ({
                        value: String(dayIndex),
                        label: day,
                      }))}
                    />
                    <Input
                      type="time"
                      value={schedule.startTime}
                      onChange={(event) =>
                        updateSchedule(idx, 'startTime', event.target.value)
                      }
                    />
                    <Input
                      type="time"
                      value={schedule.endTime}
                      onChange={(event) =>
                        updateSchedule(idx, 'endTime', event.target.value)
                      }
                    />
                    <Select
                      value={schedule.classroomId}
                      onChange={(event) =>
                        updateSchedule(idx, 'classroomId', event.target.value)
                      }
                      options={classroomOptions}
                    />
                    <Button
                      type="button"
                      variant="ghost"
                      size="icon"
                      onClick={() => removeSchedule(idx)}
                      aria-label={copy.removeSchedule(idx + 1)}
                      title={copy.removeSchedule(idx + 1)}
                    >
                      <X className="h-4 w-4 text-destructive" />
                    </Button>
                  </div>
                ))}
              </div>
            )}
          </AdminFormSection>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving
                ? copy.saving
                : editingSection
                  ? copy.editAction
                  : copy.create}
            </Button>
          </AdminDialogFooter>
        </form>
      </Modal>

      {confirmationDialog}
    </AdminFrame>
  );
}
