/**
 * Dean (TRUONG_KHOA) faculty observation dashboard copy.
 *
 * Kept beside messages-thesis-workflow.ts rather than inside messages.ts so the
 * shared locale file stays reviewable; the `vi` object is typed against the
 * widened `en` shape so a missing or extra key is a compile error.
 */

type Widen<T> = T extends string
  ? string
  : T extends readonly (infer U)[]
    ? readonly Widen<U>[]
    : T extends object
      ? { readonly [K in keyof T]: Widen<T[K]> }
      : T;

export const deanOverviewEn = {
  title: 'Faculty thesis observation',
  subtitle: 'Read-only overview of thesis groups, members and supervising lecturers inside {faculty}.',
  departmentLine: 'Your department: {department}',
  roundLabel: 'Thesis round',
  roundPlaceholder: 'Select a thesis round',
  loading: 'Loading faculty overview…',
  loadError: 'The faculty overview could not be loaded. Please try again.',
  forbidden: 'This observation board is reserved for faculty heads. Your account does not have the required role.',
  emptyTitle: 'No thesis groups in this round',
  emptyBody: 'No group inside your faculty has registered for this round yet.',
  stats: {
    groups: 'Groups',
    pending: 'Pending approval',
    approved: 'Approved',
    withoutTopic: 'Without a topic',
    members: 'Students involved',
    topics: 'Faculty topics',
    supervisors: 'Supervising lecturers',
  },
  supervisorsTitle: 'Supervisor workload',
  supervisorsSubtitle: 'Lecturers guiding at least one group in this round.',
  groupsTitle: 'Groups in the faculty',
  groupsSubtitle: 'Every group whose topic or members belong to {faculty}.',
  table: {
    topic: 'Topic',
    group: 'Group',
    members: 'Members',
    supervisors: 'Supervisors',
    status: 'Group status',
    approval: 'Approval',
    department: 'Department',
    groupCount: 'Groups',
    topicCount: 'Topics',
    supervisedTopics: 'Topics',
    supervisedGroups: 'Groups',
    lecturer: 'Lecturer',
    noTopic: 'No topic assigned',
    noSupervisor: 'No supervisor yet',
    leaderBadge: 'Leader',
    externalBadge: 'External',
    departmentBreakdown: 'By department',
    expandMembers: 'Show members',
    collapseMembers: 'Hide members',
  },
  status: {
    DRAFT: 'Draft',
    ASSIGNED: 'Assigned',
    SUBMITTED: 'Submitted',
    APPROVED: 'Approved',
    REJECTED: 'Rejected',
    COMPLETED: 'Completed',
    CANCELLED: 'Cancelled',
    PENDING: 'Pending',
  },
} as const;

export const deanOverviewVi: Widen<typeof deanOverviewEn> = {
  title: 'Quan sát khóa luận toàn khoa',
  subtitle: 'Bảng xem chỉ-đọc các nhóm khóa luận, thành viên và giảng viên hướng dẫn trong {faculty}.',
  departmentLine: 'Bộ môn của bạn: {department}',
  roundLabel: 'Đợt khóa luận',
  roundPlaceholder: 'Chọn đợt khóa luận',
  loading: 'Đang tải tổng quan khoa…',
  loadError: 'Không tải được tổng quan khoa. Vui lòng thử lại.',
  forbidden: 'Bảng quan sát này chỉ dành cho Trưởng khoa. Tài khoản của bạn không có vai trò phù hợp.',
  emptyTitle: 'Chưa có nhóm khóa luận trong đợt này',
  emptyBody: 'Chưa có nhóm nào thuộc khoa của bạn đăng ký trong đợt này.',
  stats: {
    groups: 'Nhóm',
    pending: 'Chờ xét duyệt',
    approved: 'Đã duyệt',
    withoutTopic: 'Chưa có đề tài',
    members: 'Sinh viên tham gia',
    topics: 'Đề tài của khoa',
    supervisors: 'GV hướng dẫn',
  },
  supervisorsTitle: 'Khối lượng hướng dẫn',
  supervisorsSubtitle: 'Giảng viên đang hướng dẫn ít nhất một nhóm trong đợt.',
  groupsTitle: 'Các nhóm trong khoa',
  groupsSubtitle: 'Mọi nhóm có đề tài hoặc thành viên thuộc {faculty}.',
  table: {
    topic: 'Đề tài',
    group: 'Nhóm',
    members: 'Thành viên',
    supervisors: 'GV hướng dẫn',
    status: 'Trạng thái nhóm',
    approval: 'Xét duyệt',
    department: 'Bộ môn',
    groupCount: 'Nhóm',
    topicCount: 'Đề tài',
    supervisedTopics: 'Đề tài',
    supervisedGroups: 'Nhóm',
    lecturer: 'Giảng viên',
    noTopic: 'Chưa gán đề tài',
    noSupervisor: 'Chưa có GVHD',
    leaderBadge: 'Nhóm trưởng',
    externalBadge: 'Ngoài trường',
    departmentBreakdown: 'Theo bộ môn',
    expandMembers: 'Xem thành viên',
    collapseMembers: 'Thu gọn thành viên',
  },
  status: {
    DRAFT: 'Bản nháp',
    ASSIGNED: 'Đã phân đề tài',
    SUBMITTED: 'Đã gửi',
    APPROVED: 'Đã duyệt',
    REJECTED: 'Bị từ chối',
    COMPLETED: 'Hoàn thành',
    CANCELLED: 'Đã hủy',
    PENDING: 'Chờ xét duyệt',
  },
};
