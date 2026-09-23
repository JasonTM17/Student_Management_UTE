'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import {
  CheckCircle2,
  ChevronDown,
  GraduationCap,
  KeyRound,
  Pencil,
  Plus,
  School,
  Search,
  ShieldCheck,
  Trash2,
  User,
  UserCheck,
  Users,
} from 'lucide-react';
import { useAuth } from '@/context/AuthContext';
import { curriculaApi, departmentsApi, usersApi } from '@/lib/api';
import { Curriculum } from '@/types/api';
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
import { statusToneClass, type StatusTone } from '@/components/ui/status';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';
import { useConfirmationDialog } from '@/components/ui/use-confirmation-dialog';
import { useI18n } from '@/i18n';
import { cn } from '@/lib/utils';
import { toast } from 'sonner';
import { campusErrorMessage } from '@/lib/campus-error';

interface UserRecord {
  id: string;
  email: string;
  firstName: string;
  lastName: string;
  status: string;
  // The admin API serializes roles as an array (matching AuthUserResponse);
  // it used to send a comma-joined string and every consumer split it back.
  roles?: string[];
  createdAt: string;
}

type ManagedRole = 'STUDENT' | 'LECTURER' | 'ADMIN' | 'SUPER_ADMIN';
const defaultRole: ManagedRole = 'STUDENT';

function primaryRole(roles?: string[]): ManagedRole {
  return (
    (roles ?? []).find((role): role is ManagedRole =>
      ['STUDENT', 'LECTURER', 'ADMIN', 'SUPER_ADMIN'].includes(role.trim().toUpperCase()),
    )?.trim().toUpperCase() as ManagedRole | undefined
  ) ?? defaultRole;
}

function roleLabel(roles?: string[]) {
  return (roles ?? []).filter(Boolean).join(', ') || defaultRole;
}

function isRecordSuperAdmin(u: UserRecord): boolean {
  return recordRoles(u).some((role) => role.trim().toUpperCase() === 'SUPER_ADMIN');
}

function recordRoles(record: UserRecord): string[] {
  return record.roles ?? [];
}

function isRecordAdministrator(u: UserRecord): boolean {
  const values = recordRoles(u).map((role) => role.trim().toUpperCase());
  return values.includes('ADMIN') || values.includes('SUPER_ADMIN');
}

function userStatusTone(status: string): StatusTone {
  switch (status.toUpperCase()) {
    case 'ACTIVE':
      return 'success';
    case 'PENDING':
      return 'warning';
    case 'LOCKED':
    case 'SUSPENDED':
    case 'DISABLED':
      return 'danger';
    default:
      return 'neutral';
  }
}

export default function AdminUsersPage() {
  const { user, isAdmin, isSuperAdmin, isLoading: isAuthLoading, isLoggingOut } = useAuth();
  const { href, locale, formatDate, messages } = useI18n();
  const router = useRouter();
  const searchParams = useSearchParams();

  const [users, setUsers] = useState<UserRecord[]>([]);
  const [departments, setDepartments] = useState<{ id: string; name: string; code?: string }[]>([]);
  const [curricula, setCurricula] = useState<Curriculum[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  // Server truth for the active filter: meta.total of the query that produced
  // the current rows, so the tab count cannot describe a different set than
  // the table.
  const [totalItems, setTotalItems] = useState(0);
  const [roleFilter, setRoleFilter] = useState<'ALL' | 'STUDENT' | 'LECTURER' | 'ADMIN'>('ALL');

  const [showCreateModal, setShowCreateModal] = useState(false);
  const [editingUser, setEditingUser] = useState<UserRecord | null>(null);
  const [isSaving, setIsSaving] = useState(false);
  const [formError, setFormError] = useState('');
  // One-time office-issued credential shown exactly once after create/reset.
  const [issuedCredential, setIssuedCredential] = useState<{ email: string; secret: string } | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  // 2-Column form data covering both general account & role-specific academic profiles
  const [formData, setFormData] = useState({
    email: '',
    firstName: '',
    lastName: '',
    role: defaultRole as ManagedRole,
    // Student-specific fields
    studentId: '',
    year: '1',
    curriculumId: '',
    // Lecturer-specific fields
    employeeId: '',
    departmentId: '',
    academicTitle: 'TS.',
    specialization: '',
  });

  const canAccess = Boolean(user && (isAdmin || isSuperAdmin));
  const { confirm, confirmationDialog } = useConfirmationDialog();

  // Sync role filter from URL query param if present
  useEffect(() => {
    const roleParam = searchParams.get('role');
    if (roleParam && ['STUDENT', 'LECTURER', 'ADMIN'].includes(roleParam.toUpperCase())) {
      setRoleFilter(roleParam.toUpperCase() as 'STUDENT' | 'LECTURER' | 'ADMIN');
    }
  }, [searchParams]);

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

  const fetchUsers = useCallback(async () => {
    setIsLoading(true);
    setError('');
    try {
      // The active tab is a server-side filter, not a browser-side narrowing
      // of the current 20-row page: with a filter applied, meta.total counts
      // the matching set, which is what the tabs and pagination display.
      const response = await usersApi.getAll({
        page,
        limit: 20,
        search: search || undefined,
        role: roleFilter === 'ALL' ? undefined : roleFilter,
      });
      const rows = Array.isArray(response.data)
        ? response.data
        : Array.isArray(response)
          ? response
          : [];
      setUsers(rows as UserRecord[]);
      setTotalItems(Number(response.meta?.total ?? rows.length));
      setTotalPages(response.meta?.totalPages || 1);
    } catch (loadError) {
      const status = (loadError as { response?: { status?: number } }).response?.status;
      setError(
        status === 403
          ? locale === 'vi'
            ? 'Bạn không có quyền xem danh sách người dùng.'
            : 'You do not have permission to view user records.'
          : locale === 'vi'
            ? 'Hiện chưa thể tải hồ sơ người dùng.'
            : 'User records could not be loaded.',
      );
    } finally {
      setIsLoading(false);
    }
  }, [locale, page, search, roleFilter]);

  const fetchDepartments = useCallback(async () => {
    try {
      const res = await departmentsApi.getAll({ limit: 100 });
      if (res.data) {
        setDepartments(res.data);
      }
    } catch {
      // Departments fallback list already available
    }
  }, []);

  const fetchCurricula = useCallback(async () => {
    try {
      const res = await curriculaApi.getAll({ limit: 100 });
      if (res.data) {
        setCurricula(res.data);
      }
    } catch {
      // Curricula fallback
    }
  }, []);

  useEffect(() => {
    if (canAccess) {
      void fetchUsers();
      void fetchDepartments();
      void fetchCurricula();
    }
  }, [canAccess, fetchUsers, fetchDepartments, fetchCurricula]);

  const pageSummary = useMemo(() => {
    if (totalItems === 0) {
      return locale === 'vi' ? 'Không có bản ghi phù hợp' : 'No matching records';
    }

    return locale === 'vi'
      ? `Trang ${page} / ${totalPages}`
      : `Page ${page} of ${totalPages}`;
  }, [locale, page, totalPages, totalItems]);

  const copy =
    locale === 'vi'
      ? {
          loading: 'Đang tải quản lý người dùng',
          title: 'Quản lý người dùng',
          description:
            'Rà soát tài khoản campus, tạo bản ghi mới và giữ các hành động nhạy cảm sau bước xác nhận rõ ràng.',
          issuancePolicy: 'Chính sách cấp tài khoản học vụ tập trung',
          issuancePolicyDescription:
            'Chỉ Phòng Đào tạo cấp tài khoản cho Sinh viên và Giảng viên, gắn liền với mã số và họ tên chính thức. Sinh viên và Giảng viên không tự đăng ký hoặc tự ý đổi tên.',
          createUser: 'Tạo người dùng',
          createStudent: 'Thêm sinh viên',
          createLecturer: 'Thêm giảng viên',
          searchUsers: 'Tìm người dùng',
          searchPlaceholder: 'Tìm theo email hoặc tên',
          unavailableTitle: 'Hồ sơ người dùng chưa sẵn sàng',
          emptyTitle: 'Không có người dùng phù hợp',
          emptyDescription:
            'Hãy thử từ khóa khác hoặc tạo tài khoản campus mới.',
          tableTitle: 'Tài khoản campus',
          headers: {
            name: 'Họ và tên',
            email: 'Email trường cấp',
            status: 'Trạng thái',
            role: 'Vai trò',
            created: 'Ngày tạo',
            actions: 'Tác vụ',
          },
          deleteTitle: 'Xóa người dùng',
          deleteMessage: (firstName: string, lastName: string) =>
            `Xóa ${lastName} ${firstName}? Hành động này sẽ gỡ bản ghi tài khoản khỏi màn hình quản trị hiện tại.`,
          deleteConfirm: 'Xóa người dùng',
          deleted: 'Đã xóa người dùng',
          deleteFailed: 'Hiện chưa thể xóa người dùng này.',
          updated: 'Đã cập nhật người dùng',
          createdStudent: 'Đã tạo tài khoản sinh viên thành công',
          createdLecturer: 'Đã tạo tài khoản giảng viên thành công',
          createdUser: 'Đã tạo người dùng thành công',
          saveFailed: 'Hiện chưa thể lưu hồ sơ người dùng.',
          editTitle: 'Chỉnh sửa tài khoản người dùng',
          createStudentTitle: 'Thêm Mới Tài Khoản Sinh Viên',
          createLecturerTitle: 'Thêm Mới Tài Khoản Giảng Viên',
          createAdminTitle: 'Thêm Mới Tài Khoản Quản Trị Viên',
          emailLabel: 'Địa chỉ Email trường cấp',
          issuedSecretTitle: 'Hệ thống tự cấp mật khẩu tạm thời',
          issuedSecretNote:
            'Bạn không cần nhập mật khẩu khởi tạo. Sau khi tạo, hệ thống sinh một mật khẩu tạm thời dùng một lần và người dùng phải đổi ngay khi đăng nhập đầu tiên.',
          resetAction: 'Cấp lại mật khẩu tạm thời',
          resetSuccess: 'Đã cấp lại mật khẩu tạm thời và đăng xuất mọi phiên của người dùng.',
          resetFailed: 'Hiện chưa thể cấp lại mật khẩu cho người dùng này.',
          credentialTitle: 'Mật khẩu tạm thời dùng một lần',
          credentialDescription:
            'Hãy chuyển mật khẩu tạm thời dưới đây cho người dùng qua kênh bảo mật. Hệ thống sẽ không hiển thị lại.',
          credentialShownOnce:
            'Mật khẩu chỉ hiển thị một lần duy nhất. Sau khi đóng, bạn không thể xem lại — người dùng sẽ phải đổi ngay khi đăng nhập.',
          copyAction: 'Sao chép',
          copied: 'Đã sao chép mật khẩu tạm thời.',
          errors: {
            studentIdRequired: 'Vui lòng nhập mã số sinh viên chính thức.',
            curriculumRequired: 'Vui lòng chọn khung chương trình đào tạo.',
            employeeIdRequired: 'Vui lòng nhập mã số giảng viên chính thức.',
            departmentRequired: 'Vui lòng chọn khoa / bộ môn công tác.',
            emailExists: 'Email này đã có trong hệ thống.',
            studentIdExists: 'Mã số sinh viên đã tồn tại.',
            employeeIdExists: 'Mã số giảng viên đã tồn tại.',
            adminReserved: 'Chỉ siêu quản trị viên mới được tạo tài khoản quản trị viên.',
          },
          firstName: 'Tên',
          lastName: 'Họ và tên đệm',
          roleLabel: 'Vai trò hệ thống',
          roles: {
            STUDENT: 'Sinh viên',
            LECTURER: 'Giảng viên',
            ADMIN: 'Quản trị viên',
            SUPER_ADMIN: 'Siêu quản trị viên',
          },
          saving: 'Đang lưu dữ liệu...',
          editAction: messages.common.actions.saveChanges,
          closeDialog: 'Đóng biểu mẫu người dùng',
          editUserLabel: (firstName: string, lastName: string) =>
            `Chỉnh sửa người dùng ${lastName} ${firstName}`,
          deleteUserLabel: (firstName: string, lastName: string) =>
            `Xóa người dùng ${lastName} ${firstName}`,
          filterByRole: 'Lọc theo phân loại:',
          filterAll: (count: number) => `Tất cả (${count})`,
          filterAllNoCount: 'Tất cả',
          editRecord: 'Sửa',
          createAudienceLabel: 'Phân loại đối tượng tạo mới:',
          createAudienceHint:
            'Chọn loại tài khoản để hệ thống tải hồ sơ học vụ chuyên biệt tương ứng',
          rolesShort: {
            ADMIN: 'Quản trị',
          },
          accountSectionTitle: '1. Thông Tin Tài Khoản & Đăng Nhập',
          accountSectionHint: 'Định danh đăng nhập và bảo mật tài khoản',
          lastNamePlaceholder: 'Nguyễn Văn',
          firstNamePlaceholder: 'An',
          initialStatusLabel: 'Trạng thái tài khoản ban đầu:',
          initialStatusActive: 'Đang hoạt động (ACTIVE)',
          assignmentSectionTitle: '2. Hồ Sơ Phân Công Học Vụ',
          assignmentHints: {
            STUDENT: 'Thông tin hồ sơ đào tạo sinh viên chính quy',
            LECTURER: 'Thông tin học hàm và bộ môn giảng dạy',
            ADMIN: 'Phân quyền quản trị hệ thống',
          },
          studentIdLabel: 'Mã số sinh viên (MSSV) *',
          studentIdDescription: 'Mã định danh sinh viên dùng tra cứu điểm, ĐRL và ĐKHP',
          studentIdPlaceholder: 'ví dụ: 24110054',
          cohortLabel: 'Khóa đào tạo *',
          yearOptions: {
            '1': 'Năm 1 (Khóa K2026)',
            '2': 'Năm 2 (Khóa K2025)',
            '3': 'Năm 3 (Khóa K2024)',
            '4': 'Năm 4 (Khóa K2023)',
          },
          curriculumLabel: 'Khung chương trình *',
          facultyLabel: 'Khoa / Viện đào tạo',
          facultyManagedHint:
            'Khoa / Viện đào tạo của sinh viên do Phòng Đào tạo gán cùng chương trình đào tạo sau khi tài khoản được tạo.',
          selectFacultyPlaceholder: '-- Chọn Khoa / Viện đào tạo --',
          employeeIdLabel: 'Mã số Giảng viên (MSGV) *',
          employeeIdDescription: 'Mã định danh cán bộ giảng dạy và chấm thi luận văn',
          employeeIdPlaceholder: 'ví dụ: GV2026001 hoặc GV2026002',
          academicTitleLabel: 'Học hàm / Học vị',
          academicTitleManagedHint:
            'Tài khoản tạo nhanh chỉ lưu bộ môn công tác; học hàm / học vị do Phòng Đào tạo cập nhật trong danh bạ Giảng viên.',
          academicTitleOptions: {
            'PGS.TS.': 'PGS.TS. (Phó Giáo sư - Tiến sĩ)',
            'GS.TS.': 'GS.TS. (Giáo sư - Tiến sĩ)',
            'TS.': 'TS. (Tiến sĩ)',
            'ThS.': 'ThS. (Thạc sĩ)',
            'KS.': 'KS. / Kỹ sư chính',
          },
          departmentLabel: 'Khoa / Bộ môn công tác *',
          selectDepartmentPlaceholder: '-- Chọn Khoa / Bộ môn --',
          specializationLabel: 'Lĩnh vực chuyên môn / Hướng nghiên cứu',
          specializationManagedHint:
            'Lĩnh vực chuyên môn do Phòng Đào tạo cập nhật trong danh bạ Giảng viên sau khi tạo tài khoản.',
          specializationDescription:
            'Ví dụ: Trí tuệ nhân tạo, Hệ thống nhúng, Kỹ thuật phần mềm',
          specializationPlaceholder: 'ví dụ: Kỹ thuật phần mềm & AI',
          adminPermissionsTitle: 'Phân quyền Quản Trị Hệ Thống',
          adminPermissionsDescription:
            'Tài khoản có toàn quyền truy cập phân hệ Quản trị viện, quản lý danh mục người dùng, giảng viên, môn học, lớp học phần, bảng tin thông báo và cấu hình trường học.',
          createStudentAction: '+ Tạo Tài Khoản Sinh Viên',
          createLecturerAction: '+ Tạo Tài Khoản Giảng Viên',
        }
      : {
          loading: 'Loading user management',
          title: 'User management',
          description:
            'Review campus accounts, create new records, and keep sensitive actions behind explicit confirmation.',
          issuancePolicy: 'Centralized academic account issuance',
          issuancePolicyDescription:
            'The Academic Office exclusively issues student and lecturer accounts with official IDs and verified names. Students and lecturers cannot self-register or rename themselves.',
          createUser: 'Create user',
          createStudent: 'Add Student',
          createLecturer: 'Add Lecturer',
          searchUsers: 'Search users',
          searchPlaceholder: 'Search by email or name',
          unavailableTitle: 'User records unavailable',
          emptyTitle: 'No matching users',
          emptyDescription:
            'Try another search term or create a new campus account.',
          tableTitle: 'Campus accounts',
          headers: {
            name: 'Full Name',
            email: 'Campus Email',
            status: 'Status',
            role: 'Role',
            created: 'Created Date',
            actions: 'Actions',
          },
          deleteTitle: 'Delete user',
          deleteMessage: (firstName: string, lastName: string) =>
            `Delete ${lastName} ${firstName}? This action removes the account record from the current admin view.`,
          deleteConfirm: 'Delete user',
          deleted: 'User deleted',
          deleteFailed: 'We could not delete that user.',
          updated: 'User updated',
          createdStudent: 'Student account created successfully',
          createdLecturer: 'Lecturer account created successfully',
          createdUser: 'User created successfully',
          saveFailed: 'The user record could not be saved.',
          editTitle: 'Edit user account',
          createStudentTitle: 'Add New Student Account',
          createLecturerTitle: 'Add New Lecturer Account',
          createAdminTitle: 'Add New Administrator Account',
          emailLabel: 'Campus Email Address',
          issuedSecretTitle: 'The system issues the temporary credential',
          issuedSecretNote:
            'You do not choose the start credential. After creation the system generates a one-time temporary password that the user must rotate at first sign-in.',
          resetAction: 'Re-issue temporary credential',
          resetSuccess: 'Issued a new temporary credential and signed the user out everywhere.',
          resetFailed: 'Could not re-issue a credential for this user.',
          credentialTitle: 'One-time temporary credential',
          credentialDescription:
            'Hand the temporary credential below to the user over a secure channel. The system will not show it again.',
          credentialShownOnce:
            'This secret is shown exactly once. After closing this dialog you cannot retrieve it — the user must rotate at first sign-in.',
          copyAction: 'Copy',
          copied: 'Temporary credential copied.',
          errors: {
            studentIdRequired: 'Enter the official student ID.',
            curriculumRequired: 'Select the curriculum.',
            employeeIdRequired: 'Enter the official employee ID.',
            departmentRequired: 'Select the department.',
            emailExists: 'This email is already registered.',
            studentIdExists: 'This student ID already exists.',
            employeeIdExists: 'This employee ID already exists.',
            adminReserved: 'Only a super administrator can create administrator accounts.',
          },
          firstName: 'First name',
          lastName: 'Last name',
          roleLabel: 'System role',
          roles: {
            STUDENT: 'Student',
            LECTURER: 'Lecturer',
            ADMIN: 'Administrator',
            SUPER_ADMIN: 'Super administrator',
          },
          saving: 'Saving...',
          editAction: messages.common.actions.saveChanges,
          closeDialog: 'Close user form',
          editUserLabel: (firstName: string, lastName: string) =>
            `Edit user ${lastName} ${firstName}`,
          deleteUserLabel: (firstName: string, lastName: string) =>
            `Delete user ${lastName} ${firstName}`,
          filterByRole: 'Filter by role:',
          filterAll: (count: number) => `All (${count})`,
          filterAllNoCount: 'All',
          editRecord: 'Edit',
          createAudienceLabel: 'Account type to create:',
          createAudienceHint:
            'Choose the account type so the system loads the matching academic profile',
          rolesShort: {
            ADMIN: 'Admin',
          },
          accountSectionTitle: '1. Account & Sign-in Details',
          accountSectionHint: 'Sign-in identity and account security',
          lastNamePlaceholder: 'e.g. Nguyen Van',
          firstNamePlaceholder: 'e.g. An',
          initialStatusLabel: 'Initial account status:',
          initialStatusActive: 'Active (ACTIVE)',
          assignmentSectionTitle: '2. Academic Assignment Profile',
          assignmentHints: {
            STUDENT: 'Academic profile details for the enrolled student',
            LECTURER: 'Academic title and teaching department details',
            ADMIN: 'System administration permissions',
          },
          studentIdLabel: 'Student ID (MSSV) *',
          studentIdDescription:
            'The identifier used to look up grades, conduct, and course registration',
          studentIdPlaceholder: 'e.g. 24110054',
          cohortLabel: 'Cohort *',
          yearOptions: {
            '1': 'Year 1 (Cohort K2026)',
            '2': 'Year 2 (Cohort K2025)',
            '3': 'Year 3 (Cohort K2024)',
            '4': 'Year 4 (Cohort K2023)',
          },
          curriculumLabel: 'Curriculum *',
          facultyLabel: 'Faculty / Training Institute',
          facultyManagedHint:
            'The student’s training faculty is assigned by the Academic Office together with the curriculum after the account is created.',
          selectFacultyPlaceholder: '-- Select Faculty / Institute --',
          employeeIdLabel: 'Employee ID (MSGV) *',
          employeeIdDescription:
            'The identifier for teaching staff and thesis examiners',
          employeeIdPlaceholder: 'e.g. GV2026001 or GV2026002',
          academicTitleLabel: 'Academic title / Degree',
          academicTitleManagedHint:
            'Quick account creation only stores the teaching department; academic titles are set by the Academic Office in the lecturer directory.',
          academicTitleOptions: {
            'PGS.TS.': 'PGS.TS. (Associate Professor - PhD)',
            'GS.TS.': 'GS.TS. (Professor - PhD)',
            'TS.': 'TS. (PhD)',
            'ThS.': 'ThS. (Master)',
            'KS.': 'KS. / Senior Engineer',
          },
          departmentLabel: 'Faculty / Department *',
          selectDepartmentPlaceholder: '-- Select Faculty / Department --',
          specializationLabel: 'Specialization / Research area',
          specializationManagedHint:
            'The specialization is set by the Academic Office in the lecturer directory after the account is created.',
          specializationDescription:
            'e.g. Artificial Intelligence, Embedded Systems, Software Engineering',
          specializationPlaceholder: 'e.g. Software Engineering & AI',
          adminPermissionsTitle: 'System Administration Permissions',
          adminPermissionsDescription:
            'This account has full access to the administration modules, managing users, lecturers, courses, sections, notices, and school configuration.',
          createStudentAction: '+ Create Student Account',
          createLecturerAction: '+ Create Lecturer Account',
        };

  const statusLabel = (status: string | null | undefined) =>
    messages.common.statuses[
      (status ?? 'UNKNOWN').toUpperCase() as keyof typeof messages.common.statuses
    ] ?? messages.common.statuses.UNKNOWN;

  if (isAuthLoading || isLoggingOut || !canAccess) {
    return <LoadingState label={copy.loading} className="m-8" />;
  }

  const resetForm = (role: ManagedRole = defaultRole) => {
    setEditingUser(null);
    setFormError('');
    setFieldErrors({});
    setFormData({
      email: '',
      firstName: '',
      lastName: '',
      role,
      studentId: '',
      year: '1',
      curriculumId: curricula[0]?.id || '',
      employeeId: '',
      departmentId: departments[0]?.id || '',
      academicTitle: 'TS.',
      specialization: '',
    });
  };

  const openCreate = (targetRole: ManagedRole = 'STUDENT') => {
    resetForm(targetRole);
    setShowCreateModal(true);
  };

  const openEdit = (userRecord: UserRecord) => {
    if (!isSuperAdmin && isRecordSuperAdmin(userRecord)) {
      toast.error(
        locale === 'vi'
          ? 'Chỉ siêu quản trị viên mới có thể chỉnh sửa tài khoản siêu quản trị viên.'
          : 'Only super administrators can edit super administrator accounts.',
      );
      return;
    }
    setEditingUser(userRecord);
    setFormError('');
    setFieldErrors({});
    setFormData({
      email: userRecord.email,
      firstName: userRecord.firstName,
      lastName: userRecord.lastName,
      role: primaryRole(userRecord.roles),
      studentId: '',
      year: '1',
      curriculumId: curricula[0]?.id || '',
      employeeId: '',
      departmentId: departments[0]?.id || '',
      academicTitle: 'TS.',
      specialization: '',
    });
    setShowCreateModal(true);
  };

  // Closing the form must never clear the one-time credential: both updates
  // batch in the same handler, and clearing it here made the handoff modal
  // vanish so the issued password was shown to nobody.
  const closeFormModal = () => {
    setShowCreateModal(false);
    setFieldErrors({});
    resetForm();
  };

  const closeModal = () => {
    closeFormModal();
    setIssuedCredential(null);
  };

  const handleResetCredential = async (userRecord: UserRecord) => {
    try {
      const result = await usersApi.resetPassword(userRecord.id);
      if (result.temporaryPassword) {
        setIssuedCredential({ email: result.email, secret: result.temporaryPassword });
      }
      toast.success(copy.resetSuccess);
      await fetchUsers();
    } catch {
      toast.error(copy.resetFailed);
    }
  };

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault();
    setSearch(searchInput);
    setPage(1);
  };

  const handleDelete = async (userRecord: UserRecord) => {
    if (user && (user.id === userRecord.id || user.email === userRecord.email)) {
      toast.error(
        locale === 'vi'
          ? 'Không thể xóa tài khoản của chính mình.'
          : 'You cannot delete your own account.',
      );
      return;
    }

    if (!isSuperAdmin && isRecordSuperAdmin(userRecord)) {
      toast.error(
        locale === 'vi'
          ? 'Chỉ siêu quản trị viên mới có thể xóa tài khoản siêu quản trị viên.'
          : 'Only super administrators can delete super administrator accounts.',
      );
      return;
    }

    const shouldDelete = await confirm({
      title: copy.deleteTitle,
      message: copy.deleteMessage(userRecord.firstName, userRecord.lastName),
      confirmText: copy.deleteConfirm,
      variant: 'destructive',
    });

    if (!shouldDelete) {
      return;
    }

    try {
      await usersApi.delete(userRecord.id);
      toast.success(copy.deleted);
      if (users.length === 1 && page > 1) {
        setPage((prev) => prev - 1);
      } else {
        await fetchUsers();
      }
    } catch {
      toast.error(copy.deleteFailed);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setFormError('');
    setIsSaving(true);

    try {
      if (editingUser) {
        const isSelf = Boolean(user && (user.id === editingUser.id || user.email === editingUser.email));
        if (isSelf && formData.role !== primaryRole(editingUser.roles)) {
          const selfRoleMsg =
            locale === 'vi'
              ? 'Không thể tự thay đổi vai trò của chính mình.'
              : 'You cannot change your own system role.';
          setFormError(selfRoleMsg);
          toast.error(selfRoleMsg);
          setIsSaving(false);
          return;
        }

        await usersApi.update(editingUser.id, {
          firstName: formData.firstName.trim(),
          lastName: formData.lastName.trim(),
          role: formData.role,
        });
        toast.success(copy.updated);
      } else {
        const nextFieldErrors: Record<string, string> = {};
        if (formData.role === 'STUDENT') {
          if (!formData.studentId.trim()) nextFieldErrors.studentId = copy.errors.studentIdRequired;
          if (!formData.curriculumId) nextFieldErrors.curriculumId = copy.errors.curriculumRequired;
        } else if (formData.role === 'LECTURER') {
          if (!formData.employeeId.trim()) nextFieldErrors.employeeId = copy.errors.employeeIdRequired;
          if (!formData.departmentId) nextFieldErrors.departmentId = copy.errors.departmentRequired;
        }
        if (Object.keys(nextFieldErrors).length > 0) {
          setFieldErrors(nextFieldErrors);
          setIsSaving(false);
          return;
        }
        setFieldErrors({});

        // The office never picks the start credential: the server issues a
        // one-time temporary password and the account must rotate it at first login.
        const payload: Record<string, any> = {
          email: formData.email.trim(),
          firstName: formData.firstName.trim(),
          lastName: formData.lastName.trim(),
          role: formData.role,
        };

        if (formData.role === 'STUDENT') {
          payload.studentId = formData.studentId.trim();
          payload.year = formData.year;
          payload.curriculumId = formData.curriculumId || curricula[0]?.id || '';
          // departmentId is deliberately NOT sent: the DTO accepts it but the
          // student profile writer ignores it, so collecting a faculty choice
          // here would silently discard the admin's input.
        } else if (formData.role === 'LECTURER') {
          // academicTitle and specialization are not fields of the user-create
          // DTO: they live on the lecturer directory record maintained by the
          // Academic Office, so the disabled inputs above must not pretend the
          // value travels with this account.
          payload.employeeId = formData.employeeId.trim();
          payload.departmentId = formData.departmentId;
        }

        const created = await usersApi.create(payload);
        if (created.temporaryPassword) {
          setIssuedCredential({ email: created.email, secret: created.temporaryPassword });
        }

        if (formData.role === 'STUDENT') {
          toast.success(copy.createdStudent);
        } else if (formData.role === 'LECTURER') {
          toast.success(copy.createdLecturer);
        } else {
          toast.success(copy.createdUser);
        }
      }

      closeFormModal();
      await fetchUsers();
    } catch (err: any) {
      const code = err?.response?.data?.code as string | undefined;
      const codeField: Record<string, string> = {
        EMAIL_EXISTS: 'email',
        STUDENT_ID_EXISTS: 'studentId',
        EMPLOYEE_ID_EXISTS: 'employeeId',
      };
      const fieldKey = code ? codeField[code] : undefined;
      if (fieldKey) {
        const fieldMessages: Record<string, string> = {
          EMAIL_EXISTS: copy.errors.emailExists,
          STUDENT_ID_EXISTS: copy.errors.studentIdExists,
          EMPLOYEE_ID_EXISTS: copy.errors.employeeIdExists,
        };
        setFieldErrors({ [fieldKey]: fieldMessages[code as string] });
      }
      const message = campusErrorMessage(
        err,
        messages.common.campusErrors,
        copy.saveFailed,
      );
      setFormError(message);
      toast.error(message);
    } finally {
      setIsSaving(false);
    }
  };

  const modalTitle = editingUser
    ? copy.editTitle
    : formData.role === 'STUDENT'
      ? copy.createStudentTitle
      : formData.role === 'LECTURER'
        ? copy.createLecturerTitle
        : copy.createAdminTitle;

  return (
    <AdminFrame
      title={copy.title}
      description={copy.description}
      actions={
        <div className="flex flex-wrap items-center gap-2">
          <Button
            onClick={() => openCreate('STUDENT')}
            className="bg-primary hover:bg-primary/90 text-primary-foreground shadow-xs"
          >
            <GraduationCap className="mr-2 h-4 w-4" />
            {copy.createStudent}
          </Button>
          <Button
            onClick={() => openCreate('LECTURER')}
            variant="outline"
            className="border-emerald-600/40 text-emerald-700 dark:text-emerald-400 hover:bg-emerald-500/10"
          >
            <School className="mr-2 h-4 w-4" />
            {copy.createLecturer}
          </Button>
          {isSuperAdmin ? (
            <Button
              onClick={() => openCreate('ADMIN')}
              variant="ghost"
              size="sm"
              className="text-xs text-muted-foreground hover:text-foreground"
            >
              <Plus className="mr-1 h-3.5 w-3.5" />
              {copy.roles.ADMIN}
            </Button>
          ) : null}
        </div>
      }
    >
      <div className="space-y-6">
        <AdminToolbarCard>
          <div className="flex flex-col gap-4">
            <div
              role="note"
              className="flex items-start gap-3 rounded-xl border border-primary/20 bg-primary/[0.04] p-3"
            >
              <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-primary" aria-hidden="true" />
              <div className="min-w-0">
                <p className="text-sm font-semibold text-foreground">{copy.issuancePolicy}</p>
                <p className="mt-0.5 text-xs leading-5 text-muted-foreground">
                  {copy.issuancePolicyDescription}
                </p>
              </div>
            </div>
            {/* Filter Tabs by Role */}
            <div className="flex flex-wrap items-center gap-2 border-b border-border/70 pb-3">
              <span className="text-xs font-semibold text-muted-foreground mr-1">
                {copy.filterByRole}
              </span>
              <button
                type="button"
                onClick={() => {
                  setRoleFilter('ALL');
                  setPage(1);
                }}
                className={cn(
                  'rounded-lg px-3 py-1.5 text-xs font-medium transition',
                  roleFilter === 'ALL'
                    ? 'bg-primary text-primary-foreground shadow-xs'
                    : 'bg-secondary/40 text-muted-foreground hover:text-foreground',
                )}
              >
                {roleFilter === 'ALL' ? copy.filterAll(totalItems) : copy.filterAllNoCount}
              </button>
              <button
                type="button"
                onClick={() => {
                  setRoleFilter('STUDENT');
                  setPage(1);
                }}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition',
                  roleFilter === 'STUDENT'
                    ? 'bg-status-info text-status-info-foreground shadow-xs'
                    : 'bg-secondary/40 text-muted-foreground hover:text-foreground',
                )}
              >
                <GraduationCap className="h-3.5 w-3.5" />
                {copy.roles.STUDENT}
              </button>
              <button
                type="button"
                onClick={() => {
                  setRoleFilter('LECTURER');
                  setPage(1);
                }}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition',
                  roleFilter === 'LECTURER'
                    ? 'bg-status-success text-status-success-foreground shadow-xs'
                    : 'bg-secondary/40 text-muted-foreground hover:text-foreground',
                )}
              >
                <School className="h-3.5 w-3.5" />
                {copy.roles.LECTURER}
              </button>
              <button
                type="button"
                onClick={() => {
                  setRoleFilter('ADMIN');
                  setPage(1);
                }}
                className={cn(
                  'inline-flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-xs font-medium transition',
                  roleFilter === 'ADMIN'
                    ? 'bg-status-neutral text-status-neutral-foreground shadow-xs'
                    : 'bg-secondary/40 text-muted-foreground hover:text-foreground',
                )}
              >
                <ShieldCheck className="h-3.5 w-3.5" />
                {copy.roles.ADMIN}
              </button>
            </div>

            <form onSubmit={handleSearch} className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
              <div className="w-full max-w-xl">
                <label className="mb-2 block text-sm font-medium text-foreground">
                  {copy.searchUsers}
                </label>
                <Input
                  type="text"
                  value={searchInput}
                  onChange={(e) => setSearchInput(e.target.value)}
                  placeholder={copy.searchPlaceholder}
                  aria-label={copy.searchUsers}
                  icon={<Search className="h-4 w-4" />}
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
          </div>
        </AdminToolbarCard>

        {error ? (
          <ErrorState
            title={copy.unavailableTitle}
            description={error}
            onRetry={() => void fetchUsers()}
          />
        ) : isLoading ? (
          <LoadingState label={copy.loading} />
        ) : users.length === 0 ? (
          <EmptyState
            title={copy.emptyTitle}
            description={copy.emptyDescription}
            icon={Users}
            action={
              <div className="flex gap-2">
                <Button onClick={() => openCreate('STUDENT')}>
                  <GraduationCap className="mr-2 h-4 w-4" />
                  {copy.createStudent}
                </Button>
                <Button onClick={() => openCreate('LECTURER')} variant="outline">
                  <School className="mr-2 h-4 w-4" />
                  {copy.createLecturer}
                </Button>
              </div>
            }
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
                previousLabel={locale === 'vi' ? 'Trang trước' : 'Previous'}
                nextLabel={locale === 'vi' ? 'Trang sau' : 'Next'}
              />
            }
          >
            <div className="divide-y divide-border/60 md:hidden">
              {users.map((record) => (
                <article key={record.id} className="space-y-3 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div>
                      <h3 className="font-semibold text-foreground">
                        {record.lastName} {record.firstName}
                      </h3>
                      <p className="text-xs text-muted-foreground">{record.email}</p>
                    </div>
                    <span className={`inline-flex rounded-md px-2.5 py-1 text-xs font-medium ${statusToneClass(userStatusTone(record.status))}`}>
                      {statusLabel(record.status)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs text-muted-foreground">
                    <span className="font-medium text-foreground">{roleLabel(record.roles)}</span>
                    <span>{formatDate(record.createdAt)}</span>
                  </div>
                  <AdminRowActions>
                    <Button size="sm" variant="outline" onClick={() => openEdit(record)}>
                      <Pencil className="mr-1.5 h-3.5 w-3.5" />
                      {copy.editRecord}
                    </Button>
                  </AdminRowActions>
                </article>
              ))}
            </div>

            <AdminTableScroll className="hidden md:block">
              <table className="w-full min-w-[720px] text-sm">
                <thead>
                  <tr className="bg-secondary text-left text-muted-foreground">
                    <th className="px-4 py-3 font-medium">{copy.headers.name}</th>
                    <th className="px-4 py-3 font-medium">{copy.headers.email}</th>
                    <th className="px-4 py-3 font-medium">{copy.headers.status}</th>
                    <th className="px-4 py-3 font-medium">{copy.headers.role}</th>
                    <th className="px-4 py-3 font-medium">{copy.headers.created}</th>
                    <th className="px-4 py-3 text-right font-medium">{copy.headers.actions}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {users.map((record) => {
                    const primary = primaryRole(record.roles);
                    return (
                      <tr key={record.id} className="hover:bg-secondary/20 transition-colors">
                        <td className="px-4 py-3.5">
                          <div className="font-medium text-foreground">
                            {record.lastName} {record.firstName}
                          </div>
                        </td>
                        <td className="px-4 py-3.5 text-muted-foreground font-mono text-xs">
                          {record.email}
                        </td>
                        <td className="px-4 py-3.5">
                          <span className={`inline-flex rounded-md px-2.5 py-0.5 text-xs font-medium ${statusToneClass(userStatusTone(record.status))}`}>
                            {statusLabel(record.status)}
                          </span>
                        </td>
                        <td className="px-4 py-3.5">
                          {primary === 'STUDENT' ? (
                            <span className="inline-flex items-center gap-1 rounded-md bg-blue-500/10 px-2 py-0.5 text-xs font-medium text-blue-600 dark:text-blue-400">
                              <GraduationCap className="h-3 w-3" />
                              {copy.roles.STUDENT}
                            </span>
                          ) : primary === 'LECTURER' ? (
                            <span className="inline-flex items-center gap-1 rounded-md bg-emerald-500/10 px-2 py-0.5 text-xs font-medium text-emerald-600 dark:text-emerald-400">
                              <School className="h-3 w-3" />
                              {copy.roles.LECTURER}
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 rounded-md bg-purple-500/10 px-2 py-0.5 text-xs font-medium text-purple-600 dark:text-purple-400">
                              <ShieldCheck className="h-3 w-3" />
                              {copy.roles.ADMIN}
                            </span>
                          )}
                        </td>
                        <td className="px-4 py-3.5 text-muted-foreground text-xs">
                          {formatDate(record.createdAt)}
                        </td>
                        <td className="px-4 py-3.5 text-right">
                          <AdminRowActions>
                            {(() => {
                              const isSelf = Boolean(user && (user.id === record.id || user.email === record.email));
                              // Mirror the server ceiling: a plain administrator
                              // can neither edit, reset, nor delete another
                              // administrator or super administrator.
                              const isPrivilegedTarget = isRecordAdministrator(record);
                              const canManageTarget = isSuperAdmin || !isPrivilegedTarget;
                              return (
                                <>
                                  <Button
                                    size="icon"
                                    variant="ghost"
                                    onClick={() => openEdit(record)}
                                    disabled={!canManageTarget}
                                    title={copy.editUserLabel(record.firstName, record.lastName)}
                                  >
                                    <Pencil className="h-4 w-4" />
                                  </Button>
                                  <Button
                                    size="icon"
                                    variant="ghost"
                                    onClick={() => void handleResetCredential(record)}
                                    disabled={isSelf || !canManageTarget}
                                    title={copy.resetAction}
                                  >
                                    <KeyRound className="h-4 w-4" />
                                  </Button>
                                  <Button
                                    size="icon"
                                    variant="ghost"
                                    className="text-destructive hover:bg-destructive/10 hover:text-destructive disabled:opacity-40 disabled:cursor-not-allowed"
                                    onClick={() => void handleDelete(record)}
                                    disabled={isSelf || !canManageTarget}
                                    title={copy.deleteUserLabel(record.firstName, record.lastName)}
                                  >
                                    <Trash2 className="h-4 w-4" />
                                  </Button>
                                </>
                              );
                            })()}
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

      {/* 2-COLUMN MODAL: TÁCH BIỆT THÊM SINH VIÊN VỚI GIẢNG VIÊN */}
      <Modal
        isOpen={showCreateModal}
        onClose={closeModal}
        title={modalTitle}
        closeLabel={copy.closeDialog}
        className="max-w-4xl"
      >
        <form onSubmit={handleSubmit} className="space-y-6">
          {formError ? (
            <div className="rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-3 text-sm text-destructive">
              {formError}
            </div>
          ) : null}

          {/* Quick role switcher inside modal if creating new */}
          {!editingUser && (
            <div className="rounded-xl border border-border/80 bg-secondary/20 p-3">
              <div className="flex items-center justify-between">
                <div>
                  <span className="text-xs font-semibold text-foreground">{copy.createAudienceLabel}</span>
                  <p className="text-xs text-muted-foreground">
                    {copy.createAudienceHint}
                  </p>
                </div>
                <div className="flex items-center gap-1.5 rounded-lg border border-border bg-background p-1">
                  <button
                    type="button"
                    onClick={() => {
                      setFormData((c) => ({ ...c, role: 'STUDENT' }));
                    }}
                    className={cn(
                      'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition',
                      formData.role === 'STUDENT'
                        ? 'bg-blue-600 text-white shadow-xs'
                        : 'text-muted-foreground hover:text-foreground',
                    )}
                  >
                    <GraduationCap className="h-3.5 w-3.5" />
                    {copy.roles.STUDENT}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setFormData((c) => ({ ...c, role: 'LECTURER' }));
                    }}
                    className={cn(
                      'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition',
                      formData.role === 'LECTURER'
                        ? 'bg-emerald-600 text-white shadow-xs'
                        : 'text-muted-foreground hover:text-foreground',
                    )}
                  >
                    <School className="h-3.5 w-3.5" />
                    {copy.roles.LECTURER}
                  </button>
                  <button
                    type="button"
                    onClick={() => setFormData((c) => ({ ...c, role: 'ADMIN' }))}
                    disabled={!isSuperAdmin}
                    title={!isSuperAdmin ? copy.errors.adminReserved : undefined}
                    className={cn(
                      'inline-flex items-center gap-1.5 rounded-md px-3 py-1.5 text-xs font-semibold transition',
                      formData.role === 'ADMIN'
                        ? 'bg-purple-600 text-white shadow-xs'
                        : 'text-muted-foreground hover:text-foreground disabled:cursor-not-allowed disabled:opacity-50',
                    )}
                  >
                    <ShieldCheck className="h-3.5 w-3.5" />
                    {copy.rolesShort.ADMIN}
                  </button>
                </div>
              </div>
            </div>
          )}

          {/* 2-COLUMN FORM GRID */}
          <div className="grid grid-cols-1 gap-6 md:grid-cols-2">
            {/* CỘT 1: THÔNG TIN TÀI KHOẢN & ĐĂNG NHẬP */}
            <div className="space-y-4 rounded-xl border border-border/80 bg-card p-4">
              <div className="flex items-center gap-2 border-b border-border/70 pb-3">
                <div className="flex h-7 w-7 items-center justify-center rounded-md bg-primary/10 text-primary">
                  <User className="h-4 w-4" />
                </div>
                <div>
                  <h4 className="text-sm font-semibold text-foreground">
                    {copy.accountSectionTitle}
                  </h4>
                  <p className="text-xs text-muted-foreground">
                    {copy.accountSectionHint}
                  </p>
                </div>
              </div>

              <AdminFormField label={copy.emailLabel} error={fieldErrors.email}>
                <Input
                  type="email"
                  value={formData.email}
                  placeholder={
                    formData.role === 'STUDENT'
                      ? 'sv.nguyenvana@student.ute.edu.vn'
                      : formData.role === 'LECTURER'
                        ? 'gv.tranvanb@ute.edu.vn'
                        : 'admin.hoangdung@ute.edu.vn'
                  }
                  onChange={(e) => setFormData((current) => ({ ...current, email: e.target.value }))}
                  disabled={Boolean(editingUser)}
                  required
                />
              </AdminFormField>

              {!editingUser ? (
                <div className="rounded-lg border border-primary/20 bg-primary/[0.04] p-3 text-xs leading-5 text-muted-foreground">
                  <div className="flex items-center gap-2 font-semibold text-foreground">
                    <KeyRound className="h-3.5 w-3.5" aria-hidden="true" />
                    {copy.issuedSecretTitle}
                  </div>
                  <p className="mt-1">{copy.issuedSecretNote}</p>
                </div>
              ) : null}

              <div className="grid grid-cols-2 gap-3">
                <AdminFormField label={copy.lastName}>
                  <Input
                    type="text"
                    value={formData.lastName}
                    placeholder={copy.lastNamePlaceholder}
                    onChange={(e) => setFormData((current) => ({ ...current, lastName: e.target.value }))}
                    required
                  />
                </AdminFormField>
                <AdminFormField label={copy.firstName}>
                  <Input
                    type="text"
                    value={formData.firstName}
                    placeholder={copy.firstNamePlaceholder}
                    onChange={(e) => setFormData((current) => ({ ...current, firstName: e.target.value }))}
                    required
                  />
                </AdminFormField>
              </div>

              <div className="rounded-lg border border-border/60 bg-secondary/15 p-3 text-xs">
                <div className="flex items-center justify-between">
                  <span className="text-muted-foreground">{copy.initialStatusLabel}</span>
                  <span className="inline-flex items-center gap-1 rounded-md bg-emerald-500/10 px-2 py-0.5 font-semibold text-emerald-600 dark:text-emerald-400">
                    <CheckCircle2 className="h-3 w-3" />
                    {copy.initialStatusActive}
                  </span>
                </div>
              </div>
            </div>

            {/* CỘT 2: HỒ SƠ HỌC VỤ CHUYÊN BIỆT THEO VAI TRÒ.
                The update endpoint only carries name/role/status, and openEdit
                has no profile values to echo, so the profile-fields section is
                hidden while a record is being edited: a required mark the form
                can neither fill honestly nor submit is worse than no field. */}
            {editingUser && (formData.role === 'STUDENT' || formData.role === 'LECTURER') ? null : (
            <div className="space-y-4 rounded-xl border border-border/80 bg-card p-4">
              {/* Header Cột 2 */}
              <div className="flex items-center justify-between border-b border-border/70 pb-3">
                <div className="flex items-center gap-2">
                  <div className={cn(
                    'flex h-7 w-7 items-center justify-center rounded-md',
                    formData.role === 'STUDENT'
                      ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400'
                      : formData.role === 'LECTURER'
                        ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                        : 'bg-purple-500/10 text-purple-600 dark:text-purple-400',
                  )}>
                    {formData.role === 'STUDENT' ? (
                      <GraduationCap className="h-4 w-4" />
                    ) : formData.role === 'LECTURER' ? (
                      <School className="h-4 w-4" />
                    ) : (
                      <ShieldCheck className="h-4 w-4" />
                    )}
                  </div>
                  <div>
                    <h4 className="text-sm font-semibold text-foreground">
                      {copy.assignmentSectionTitle}
                    </h4>
                    <p className="text-xs text-muted-foreground">
                      {formData.role === 'STUDENT'
                        ? copy.assignmentHints.STUDENT
                        : formData.role === 'LECTURER'
                          ? copy.assignmentHints.LECTURER
                          : copy.assignmentHints.ADMIN}
                    </p>
                  </div>
                </div>

                <span className={cn(
                  'rounded-md px-2.5 py-0.5 text-xs font-semibold',
                  formData.role === 'STUDENT'
                    ? 'bg-blue-500/10 text-blue-600 dark:text-blue-400'
                    : formData.role === 'LECTURER'
                      ? 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-400'
                      : 'bg-purple-500/10 text-purple-600 dark:text-purple-400',
                )}>
                  {formData.role === 'STUDENT'
                    ? copy.roles.STUDENT
                    : formData.role === 'LECTURER'
                      ? copy.roles.LECTURER
                      : copy.roles.ADMIN}
                </span>
              </div>

              {/* TRƯỜNG HỢP: SINH VIÊN */}
              {formData.role === 'STUDENT' && (
                <div className="space-y-3.5">
                  <AdminFormField
                    label={copy.studentIdLabel}
                    description={copy.studentIdDescription}
                    error={fieldErrors.studentId}
                  >
                    <Input
                      type="text"
                      value={formData.studentId}
                      placeholder={copy.studentIdPlaceholder}
                      onChange={(e) => setFormData((c) => ({ ...c, studentId: e.target.value }))}
                      required
                    />
                  </AdminFormField>

                  <div className="grid grid-cols-2 gap-3">
                    <AdminFormField label={copy.cohortLabel}>
                      <select
                        value={formData.year}
                        onChange={(e) => setFormData((c) => ({ ...c, year: e.target.value }))}
                        className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <option value="1">{copy.yearOptions['1']}</option>
                        <option value="2">{copy.yearOptions['2']}</option>
                        <option value="3">{copy.yearOptions['3']}</option>
                        <option value="4">{copy.yearOptions['4']}</option>
                      </select>
                    </AdminFormField>

                    <AdminFormField label={copy.curriculumLabel}>
                      <select
                        value={formData.curriculumId || (curricula[0]?.id ?? '')}
                        onChange={(e) => setFormData((c) => ({ ...c, curriculumId: e.target.value }))}
                        className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        {curricula.length > 0 ? (
                          curricula.map((c) => (
                            <option key={c.id} value={c.id}>
                              {c.nameVi || c.name} {c.totalCredits ? `(${c.totalCredits} TC)` : ''}
                            </option>
                          ))
                        ) : (
                          <option value="">{locale === 'vi' ? '-- Đang tải CTĐT --' : '-- Loading Curricula --'}</option>
                        )}
                      </select>
                    </AdminFormField>
                  </div>

                  <AdminFormField label={copy.facultyLabel} description={copy.facultyManagedHint}>
                    {/* AdminUserCreateRequest accepts departmentId but the student
                        profile writer ignores it — show the field as office-assigned
                        instead of collecting a value the server will drop. */}
                    <select
                      value=""
                      disabled
                      aria-disabled="true"
                      title={copy.facultyManagedHint}
                      className="flex h-11 w-full cursor-not-allowed rounded-md border border-input bg-muted/50 px-3 py-2 text-sm text-muted-foreground"
                    >
                      <option value="">{copy.facultyManagedHint}</option>
                    </select>
                  </AdminFormField>
                </div>
              )}

              {/* TRƯỜNG HỢP: GIẢNG VIÊN */}
              {formData.role === 'LECTURER' && (
                <div className="space-y-3.5">
                  <AdminFormField
                    label={copy.employeeIdLabel}
                    description={copy.employeeIdDescription}
                    error={fieldErrors.employeeId}
                  >
                    <Input
                      type="text"
                      value={formData.employeeId}
                      placeholder={copy.employeeIdPlaceholder}
                      onChange={(e) => setFormData((c) => ({ ...c, employeeId: e.target.value }))}
                      required
                    />
                  </AdminFormField>

                  <div className="grid grid-cols-2 gap-3">
                    <AdminFormField
                      label={copy.academicTitleLabel}
                      description={copy.academicTitleManagedHint}
                    >
                      <select
                        value={formData.academicTitle}
                        onChange={(e) => setFormData((c) => ({ ...c, academicTitle: e.target.value }))}
                        disabled
                        title={copy.academicTitleManagedHint}
                        className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring disabled:cursor-not-allowed disabled:opacity-60"
                      >
                        <option value="PGS.TS.">{copy.academicTitleOptions['PGS.TS.']}</option>
                        <option value="GS.TS.">{copy.academicTitleOptions['GS.TS.']}</option>
                        <option value="TS.">{copy.academicTitleOptions['TS.']}</option>
                        <option value="ThS.">{copy.academicTitleOptions['ThS.']}</option>
                        <option value="KS.">{copy.academicTitleOptions['KS.']}</option>
                      </select>
                    </AdminFormField>

                    <AdminFormField label={copy.departmentLabel}>
                      <select
                        value={formData.departmentId}
                        onChange={(e) => setFormData((c) => ({ ...c, departmentId: e.target.value }))}
                        className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring"
                      >
                        <option value="">{copy.selectDepartmentPlaceholder}</option>
                        {departments.map((d) => (
                          <option key={d.id} value={d.id}>
                            {d.name} {d.code ? `(${d.code})` : ''}
                          </option>
                        ))}
                      </select>
                    </AdminFormField>
                  </div>

                  <AdminFormField
                    label={copy.specializationLabel}
                    description={copy.specializationManagedHint}
                  >
                    <Input
                      type="text"
                      value={formData.specialization}
                      placeholder={copy.specializationDescription}
                      disabled
                      title={copy.specializationManagedHint}
                      onChange={(e) => setFormData((c) => ({ ...c, specialization: e.target.value }))}
                    />
                  </AdminFormField>
                </div>
              )}

              {/* TRƯỜNG HỢP: QUẢN TRỊ VIÊN */}
              {formData.role !== 'STUDENT' && formData.role !== 'LECTURER' && (
                <div className="space-y-4">
                  <div className="rounded-xl border border-purple-500/20 bg-purple-500/5 p-4 space-y-2">
                    <div className="flex items-center gap-2 text-purple-700 dark:text-purple-400">
                      <ShieldCheck className="h-5 w-5" />
                      <span className="text-sm font-semibold">{copy.adminPermissionsTitle}</span>
                    </div>
                    <p className="text-xs text-muted-foreground leading-5">
                      {copy.adminPermissionsDescription}
                    </p>
                  </div>

                  {(() => {
                    const isSelfEditing = Boolean(editingUser && user && (user.id === editingUser.id || user.email === editingUser.email));
                    return (
                      <AdminFormField
                        label={copy.roleLabel}
                        description={
                          isSelfEditing
                            ? (locale === 'vi'
                                ? 'Không thể tự thay đổi vai trò tài khoản của chính mình.'
                                : 'You cannot change your own system role.')
                            : undefined
                        }
                      >
                        <select
                          value={formData.role}
                          disabled={isSelfEditing}
                          onChange={(e) => setFormData((c) => ({ ...c, role: e.target.value as ManagedRole }))}
                          className="flex h-11 w-full rounded-md border border-input bg-background px-3 py-2 text-sm text-foreground outline-none transition focus-visible:ring-2 focus-visible:ring-ring disabled:opacity-60 disabled:cursor-not-allowed"
                        >
                          <option value="ADMIN">{copy.roles.ADMIN}</option>
                          {isSuperAdmin && <option value="SUPER_ADMIN">{copy.roles.SUPER_ADMIN}</option>}
                          <option value="STUDENT">{copy.roles.STUDENT}</option>
                          <option value="LECTURER">{copy.roles.LECTURER}</option>
                        </select>
                      </AdminFormField>
                    );
                  })()}
                </div>
              )}
            </div>
            )}
          </div>

          <AdminDialogFooter>
            <Button type="button" variant="outline" onClick={closeModal}>
              {messages.common.actions.cancel}
            </Button>
            <Button type="submit" disabled={isSaving}>
              {isSaving
                ? copy.saving
                : editingUser
                  ? copy.editAction
                  : formData.role === 'STUDENT'
                    ? copy.createStudentAction
                    : formData.role === 'LECTURER'
                      ? copy.createLecturerAction
                      : copy.createUser}
            </Button>
          </AdminDialogFooter>
        </form>
      </Modal>

      {confirmationDialog}

      {/* One-time office-issued credential handoff: shown exactly once. */}
      <Modal
        isOpen={Boolean(issuedCredential)}
        onClose={() => setIssuedCredential(null)}
        title={copy.credentialTitle}
        closeLabel={copy.closeDialog}
        className="max-w-md"
      >
        <div className="space-y-4">
          <p className="text-sm leading-6 text-muted-foreground">{copy.credentialDescription}</p>
          {issuedCredential ? (
            <>
              <p className="font-mono text-sm text-foreground">{issuedCredential.email}</p>
              <div className="flex items-center gap-2">
                <code
                  id="issued-temporary-secret"
                  className="min-w-0 flex-1 truncate rounded-lg border border-border bg-secondary/30 px-3 py-2 font-mono text-sm font-semibold text-foreground"
                >
                  {issuedCredential.secret}
                </code>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    void navigator.clipboard?.writeText(issuedCredential.secret);
                    toast.success(copy.copied);
                  }}
                >
                  {copy.copyAction}
                </Button>
              </div>
              <p
                role="alert"
                className="flex items-start gap-2 rounded-lg border border-amber-500/30 bg-amber-500/10 px-3 py-2 text-xs font-medium text-amber-700 dark:text-amber-400"
              >
                <ShieldCheck className="mt-0.5 h-3.5 w-3.5 shrink-0" aria-hidden="true" />
                {copy.credentialShownOnce}
              </p>
            </>
          ) : null}
        </div>
      </Modal>
    </AdminFrame>
  );
}
