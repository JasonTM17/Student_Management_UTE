'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import { BookOpen, Pencil, Plus, Search, Trash2 } from 'lucide-react';
import { toast } from 'sonner';
import { useAuth } from '@/context/AuthContext';
import { coursesApi, departmentsApi } from '@/lib/api';
import { AdminFrame } from '@/components/admin/AdminFrame';
import {
  AdminDialogFooter,
  AdminFormField,
  AdminPaginationFooter,
  AdminRowActions,
  AdminTableCard,
  AdminTableScroll,
  AdminToolbarCard,
  AdminToolbarMeta,
} from '@/components/admin/AdminSurface';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Modal } from '@/components/ui/modal';
import { Select } from '@/components/ui/select';
import {
  EmptyState,
  ErrorState,
  LoadingState,
} from '@/components/ui/state-block';
import { Textarea } from '@/components/ui/textarea';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import {
  getLocalizedDescription,
  getLocalizedName,
} from '@/lib/academic-content';
import { ACADEMIC_REFERENCE_LIMIT } from '@/lib/reference-data';

interface Course {
  id: string;
  code: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
  description?: string;
  descriptionEn?: string;
  descriptionVi?: string;
  credits: number;
  departmentId: string;
  department?: { name: string; nameEn?: string; nameVi?: string };
  isActive: boolean;
}

interface Department {
  id: string;
  name: string;
  nameEn?: string;
  nameVi?: string;
}

export default function AdminCoursesPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, locale, formatNumber, messages } = useI18n();
  const router = useRouter();
  const [courses, setCourses] = useState<Course[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [referenceError, setReferenceError] = useState('');
  const [search, setSearch] = useState('');
  const [departmentFilter, setDepartmentFilter] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingCourse, setEditingCourse] = useState<Course | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formData, setFormData] = useState({
    code: '',
    nameEn: '',
    nameVi: '',
    credits: 3,
    departmentId: '',
    descriptionEn: '',
    descriptionVi: '',
  });
  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const { confirm, confirmationDialog } = useConfirmationDialog();

  useEffect(() => {
    if (isAuthLoading || isLoggingOut) {
      return;
    }

    if (!user) {
      router.replace(`${href('/login')}?reason=session-expired`);
      return;
    }

    if (!isAdmin && !isSuperAdmin) {
      router.replace(href('/dashboard'));
    }
  }, [href, isAdmin, isSuperAdmin, isAuthLoading, isLoggingOut, router, user]);

  const fetchDepartments = useCallback(async () => {
    setReferenceError('');
    try {
      const response = await departmentsApi.getAll({ limit: ACADEMIC_REFERENCE_LIMIT });
      setDepartments(response.data || []);
    } catch {
      setReferenceError(
        locale === 'vi'
          ? 'Hiện chưa thể tải dữ liệu khoa cho bộ lọc và biểu mẫu.'
          : 'Department options could not be loaded.',
      );
    }
  }, [locale]);

  const fetchCourses = useCallback(async () => {
    setIsLoading(true);
    setError('');

    try {
      const response = await coursesApi.getAll({
        page,
        limit: 20,
        departmentId: departmentFilter || undefined,
      });
      const filteredCourses = search
        ? response.data.filter(
            (course: Course) =>
              course.code.toLowerCase().includes(search.toLowerCase()) ||
              course.name.toLowerCase().includes(search.toLowerCase()) ||
              course.nameEn?.toLowerCase().includes(search.toLowerCase()) ||
              course.nameVi?.toLowerCase().includes(search.toLowerCase()),
          )
        : response.data;

      setCourses(filteredCourses);
      setTotalPages(response.meta?.totalPages || 1);
    } catch {
      setError(
        locale === 'vi' ? 'Hiện chưa thể tải môn học.' : 'Courses could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [departmentFilter, locale, page, search]);

  useEffect(() => {
    if (canAccess) {
      void fetchDepartments();
      void fetchCourses();
    }
  }, [canAccess, fetchCourses, fetchDepartments]);

  const pageSummary = useMemo(() => {
    if (courses.length === 0) {
      return locale === 'vi' ? 'Không có bản ghi phù hợp' : 'No matching records';
    }

    return locale === 'vi'
      ? `Trang ${page} / ${totalPages}`
      : `Page ${page} of ${totalPages}`;
  }, [courses.length, locale, page, totalPages]);

  const departmentOptions = useMemo(
    () => [
      { value: '', label: locale === 'vi' ? 'Tất cả khoa' : 'All departments' },
      ...departments.map((department) => ({
        value: department.id,
          label: getLocalizedName(locale, department, department.name),
      })),
    ],
    [departments, locale],
  );

  const formDepartmentOptions = useMemo(
    () => [
      { value: '', label: locale === 'vi' ? 'Chọn khoa' : 'Select department' },
      ...departments.map((department) => ({
        value: department.id,
          label: getLocalizedName(locale, department, department.name),
      })),
    ],
    [departments, locale],
  );

  const copy =
    locale === 'vi'
      ? {
          loading: 'Đang tải môn học',
          title: 'Môn học',
          description:
            'Quản lý catalog môn học với ownership rõ ràng, số tín chỉ nhất quán và cấu trúc học thuật ổn định.',
          create: 'Tạo môn học',
          searchLabel: 'Tìm môn học',
          searchPlaceholder: 'Tìm theo mã hoặc tên môn',
          department: 'Khoa',
          unavailableTitle: 'Môn học chưa sẵn sàng',
          emptyTitle: 'Không có môn học phù hợp',
          emptyDescription:
            'Hãy tạo môn học để lớp học phần và đăng ký cùng dùng một nguồn catalog rõ ràng.',
          tableTitle: 'Bản ghi môn học',
          headers: {
            code: 'Mã',
            name: 'Tên môn',
            credits: 'Tín chỉ',
            department: 'Khoa',
            status: 'Trạng thái',
            actions: 'Tác vụ',
          },
          unassigned: 'Chưa gán',
          active: 'Đang hoạt động',
          inactive: 'Ngừng hoạt động',
          deleteTitle: 'Xóa môn học',
          deleteMessage: (code: string) =>
            `Xóa ${code}? Hành động này sẽ gỡ môn học khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa môn học',
          deleted: 'Đã xóa môn học',
          deleteFailed: 'Hiện chưa thể xóa môn học này.',
          updated: 'Đã cập nhật môn học',
          created: 'Đã tạo môn học',
          saveFailed: 'Hiện chưa thể lưu môn học.',
          editTitle: 'Chỉnh sửa môn học',
          createTitle: 'Tạo môn học',
          courseCode: 'Mã môn học',
          creditsLabel: 'Tín chỉ',
          courseName: 'Tên môn học',
          descriptionLabel: 'Mô tả',
          descriptionPlaceholder: 'Ghi chú catalog tùy chọn',
          saving: 'Đang lưu...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (code: string) => `Chỉnh sửa môn học ${code}`,
          deleteLabel: (code: string) => `Xóa môn học ${code}`,
        }
      : {
          loading: 'Loading courses',
          title: 'Courses',
          description:
            'Maintain the course catalog with clean ownership, clear credit values, and a consistent academic structure.',
          create: 'Create course',
          searchLabel: 'Search courses',
          searchPlaceholder: 'Search by code or name',
          department: 'Department',
          unavailableTitle: 'Courses unavailable',
          emptyTitle: 'No matching courses',
          emptyDescription:
            'Create a course so sections and registration share a clean catalog source.',
          tableTitle: 'Course records',
          headers: {
            code: 'Code',
            name: 'Name',
            credits: 'Credits',
            department: 'Department',
            status: 'Status',
            actions: 'Actions',
          },
          unassigned: 'Unassigned',
          active: 'Active',
          inactive: 'Inactive',
          deleteTitle: 'Delete course',
          deleteMessage: (code: string) =>
            `Delete ${code}? This removes the course from the current admin view.`,
          deleteConfirm: 'Delete course',
          deleted: 'Course deleted',
          deleteFailed: 'We could not delete that course.',
          updated: 'Course updated',
          created: 'Course created',
          saveFailed: 'The course could not be saved.',
          editTitle: 'Edit course',
          createTitle: 'Create course',
          courseCode: 'Course code',
          creditsLabel: 'Credits',
          courseName: 'Course name',
          descriptionLabel: 'Description',
          descriptionPlaceholder: 'Optional catalog notes',
          saving: 'Saving...',
          editAction: messages.common.actions.saveChanges,
          editLabel: (code: string) => `Edit course ${code}`,
          deleteLabel: (code: string) => `Delete course ${code}`,
        };

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = () => {
    setEditingCourse(null);
    setFormData({
      code: '',
      nameEn: '',
      nameVi: '',
      credits: 3,
      departmentId: '',
      descriptionEn: '',
      descriptionVi: '',
    });
  };

  const openCreate = () => {
    resetForm();
    setIsModalOpen(true);
  };

  const openEdit = (course: Course) => {
    setEditingCourse(course);
    setFormData({
      code: course.code,
      nameEn: course.nameEn || course.name,
      nameVi: course.nameVi || '',
      credits: course.credits,
      departmentId: course.departmentId,
      descriptionEn: course.descriptionEn || course.description || '',
      descriptionVi: course.descriptionVi || '',
    });
    setIsModalOpen(true);
  };

  const closeModal = () => {
    setIsModalOpen(false);
    resetForm();
  };

  const handleSearch = async (event: React.FormEvent) => {
    event.preventDefault();
    setPage(1);
    await fetchCourses();
  };

  const handleDelete = async (course: Course) => {
    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(course.code),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await coursesApi.delete(course.id);
      toast.success(copy.deleted);
      await fetchCourses();
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
        name: formData.nameEn || formData.nameVi,
        description: formData.descriptionEn || formData.descriptionVi,
        descriptionEn: formData.descriptionEn,
        descriptionVi: formData.descriptionVi,
      };
      if (editingCourse) {
        await coursesApi.update(editingCourse.id, payload);
        toast.success(copy.updated);
      } else {
        await coursesApi.create(payload);
        toast.success(copy.created);
      }

      closeModal();
      await fetchCourses();
    } catch (requestError: any) {
      toast.error(
        requestError.response?.data?.message ?? copy.saveFailed,
      );
    } finally {
      setIsSaving(false);
    }
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
            <form
              onSubmit={handleSearch}
              className="flex flex-col gap-4 xl:flex-row xl:items-end xl:justify-between"
            >
              <div className="grid w-full gap-4 xl:max-w-3xl xl:grid-cols-[minmax(0,1fr)_260px]">
                <div>
                  <label className="mb-2 block text-sm font-medium text-foreground">
                    {copy.searchLabel}
                  </label>
                  <Input
                    type="text"
                    value={search}
                    onChange={(event) => setSearch(event.target.value)}
                    placeholder={copy.searchPlaceholder}
                    icon={<Search className="h-4 w-4" />}
                  />
                </div>
                <Select
                  label={copy.department}
                  value={departmentFilter}
                  onChange={(event) => {
                    setDepartmentFilter(event.target.value);
                    setPage(1);
                  }}
                  options={departmentOptions}
                />
              </div>
              <AdminToolbarMeta
                summary={pageSummary}
                actions={
                  <Button type="submit" variant="outline">
                    {messages.common.actions.search}
                  </Button>
                }
              />
            </form>
        </AdminToolbarCard>

        {referenceError ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={referenceError}
            onRetry={() => void fetchDepartments()}
          />
        ) : null}

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchCourses()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : courses.length === 0 ? (
          <EmptyState
            icon={BookOpen}
            title={copy.emptyTitle}
            description={copy.emptyDescription}
            action={<Button onClick={openCreate}>{copy.create}</Button>}
          />
        ) : (
          <AdminTableCard
            title={copy.tableTitle}
            footer={
              <AdminPaginationFooter
                summary={pageSummary}
                page={page}
                totalPages={totalPages}
                onPrevious={() => setPage((current) => current - 1)}
                onNext={() => setPage((current) => current + 1)}
              />
            }
          >
              <div className="space-y-3 md:hidden" role="list" aria-label={copy.tableTitle}>
                {courses.map((course) => {
                  const courseLabel = getLocalizedName(locale, course, course.name);
                  const courseDescription = getLocalizedDescription(locale, course, '');
                  const departmentLabel = course.department
                    ? getLocalizedName(locale, course.department, course.department.name)
                    : copy.unassigned;

                  return (
                    <article
                      key={`${course.id}-mobile`}
                      className="rounded-lg border border-border/70 bg-card p-4 shadow-sm"
                      role="listitem"
                    >
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-primary">
                            {course.code}
                          </p>
                          <h3 className="mt-1 font-semibold text-foreground">{courseLabel}</h3>
                          {courseDescription ? (
                            <p className="mt-1 text-sm leading-5 text-muted-foreground">
                              {courseDescription}
                            </p>
                          ) : null}
                        </div>
                        <span className="shrink-0 rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                          {course.isActive ? copy.active : copy.inactive}
                        </span>
                      </div>
                      <dl className="mt-4 grid grid-cols-2 gap-3 border-t border-border/60 pt-3 text-sm">
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.credits}
                          </dt>
                          <dd className="mt-1 text-foreground">{formatNumber(course.credits)}</dd>
                        </div>
                        <div>
                          <dt className="text-xs font-medium uppercase tracking-wide text-muted-foreground">
                            {copy.headers.department}
                          </dt>
                          <dd className="mt-1 text-foreground">{departmentLabel}</dd>
                        </div>
                      </dl>
                      <AdminRowActions className="mt-4 border-t border-border/60 pt-3">
                        <Button
                          size="icon"
                          variant="ghost"
                          onClick={() => openEdit(course)}
                          aria-label={copy.editLabel(courseLabel)}
                          title={copy.editLabel(courseLabel)}
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          size="icon"
                          variant="ghost"
                          className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                          onClick={() => void handleDelete(course)}
                          aria-label={copy.deleteLabel(courseLabel)}
                          title={copy.deleteLabel(courseLabel)}
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </AdminRowActions>
                    </article>
                  );
                })}
              </div>
              <AdminTableScroll className="hidden md:block">
                <table className="w-full min-w-[760px] text-sm">
                  <thead>
                    <tr className="border-b border-border/70 text-left text-muted-foreground">
                      <th className="px-2 py-3 font-medium">{copy.headers.code}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.name}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.credits}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.department}</th>
                      <th className="px-2 py-3 font-medium">{copy.headers.status}</th>
                      <th className="px-2 py-3 text-right font-medium">{copy.headers.actions}</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-border/60">
                    {courses.map((course) => {
                      const courseLabel = getLocalizedName(locale, course, course.name);
                      const courseDescription = getLocalizedDescription(
                        locale,
                        course,
                        '',
                      );
                      const departmentLabel = course.department
                        ? getLocalizedName(
                            locale,
                            course.department,
                            course.department.name,
                          )
                        : copy.unassigned;

                      return (
                      <tr key={course.id}>
                        <td className="px-2 py-4 font-medium text-foreground">
                          {course.code}
                        </td>
                        <td className="px-2 py-4 text-foreground">
                          <div className="space-y-1">
                            <div>{courseLabel}</div>
                            {courseDescription ? (
                              <div className="text-xs text-muted-foreground">
                                {courseDescription}
                              </div>
                            ) : null}
                          </div>
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {formatNumber(course.credits)}
                        </td>
                        <td className="px-2 py-4 text-muted-foreground">
                          {departmentLabel}
                        </td>
                        <td className="px-2 py-4">
                          <span className="inline-flex rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                            {course.isActive ? copy.active : copy.inactive}
                          </span>
                        </td>
                        <td className="px-2 py-4">
                          <AdminRowActions>
                            <Button
                              size="icon"
                              variant="ghost"
                              onClick={() => openEdit(course)}
                              aria-label={copy.editLabel(courseLabel)}
                              title={copy.editLabel(courseLabel)}
                            >
                              <Pencil className="h-4 w-4" />
                            </Button>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => void handleDelete(course)}
                              aria-label={copy.deleteLabel(courseLabel)}
                              title={copy.deleteLabel(courseLabel)}
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
        title={editingCourse ? copy.editTitle : copy.createTitle}
      >
        <form onSubmit={handleSubmit} className="space-y-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField label={copy.courseCode}>
              <Input
                type="text"
                value={formData.code}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    code: event.target.value,
                  }))
                }
                disabled={Boolean(editingCourse)}
                required
              />
            </AdminFormField>
            <AdminFormField label={copy.creditsLabel}>
              <Input
                type="number"
                min="1"
                max="12"
                value={formData.credits}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    credits: Number(event.target.value) || 3,
                  }))
                }
                required
              />
            </AdminFormField>
          </div>

          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField label={locale === 'vi' ? 'Tên tiếng Anh' : 'English name'}>
              <Input
                type="text"
                value={formData.nameEn}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    nameEn: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
            <AdminFormField label={locale === 'vi' ? 'Tên tiếng Việt' : 'Vietnamese name'}>
              <Input
                type="text"
                value={formData.nameVi}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    nameVi: event.target.value,
                  }))
                }
                required
              />
            </AdminFormField>
          </div>

          <Select
            label={copy.department}
            value={formData.departmentId}
            onChange={(event) =>
              setFormData((current) => ({
                ...current,
                departmentId: event.target.value,
              }))
            }
            options={formDepartmentOptions}
            required
          />

          <div className="grid gap-4 sm:grid-cols-2">
            <AdminFormField
              label={locale === 'vi' ? 'Mô tả tiếng Anh' : 'English description'}
            >
              <Textarea
                value={formData.descriptionEn}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    descriptionEn: event.target.value,
                  }))
                }
                rows={4}
                placeholder={copy.descriptionPlaceholder}
              />
            </AdminFormField>
            <AdminFormField
              label={locale === 'vi' ? 'Mô tả tiếng Việt' : 'Vietnamese description'}
            >
              <Textarea
                value={formData.descriptionVi}
                onChange={(event) =>
                  setFormData((current) => ({
                    ...current,
                    descriptionVi: event.target.value,
                  }))
                }
                rows={4}
                placeholder={copy.descriptionPlaceholder}
              />
            </AdminFormField>
          </div>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving ? copy.saving : editingCourse ? copy.editAction : copy.create}
            </Button>
          </AdminDialogFooter>
        </form>
      </Modal>

      {confirmationDialog}
    </AdminFrame>
  );
}
