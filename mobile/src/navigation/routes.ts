export type UserRole = 'student' | 'lecturer' | 'admin';

export type ScreenName =
  | 'auth.signIn'
  | 'dashboard.student'
  | 'schedule'
  | 'courses'
  | 'grades'
  | 'attendance'
  | 'registration'
  | 'invoices'
  | 'notifications'
  | 'profile'
  | 'thesis.topics'
  | 'thesis.detail'
  | 'thesis.registration'
  | 'thesis.progress'
  | 'thesis.evaluation'
  | 'assistant.chat'
  | 'admin.dashboard'
  | 'admin.students'
  | 'admin.lecturers'
  | 'lecturer.dashboard'
  | 'lecturer.schedule'
  | 'lecturer.grading'
  | 'lecturer.attendance';

export type ScreenFamily =
  | 'auth'
  | 'student'
  | 'thesis'
  | 'assistant'
  | 'operations';

export interface ScreenDefinition {
  name: ScreenName;
  title: string;
  family: ScreenFamily;
  roles: readonly UserRole[];
  icon: string;
}

export interface BottomNavigationItem {
  route: ScreenName;
  label: string;
  icon: string;
}

export interface StitchMobileReference {
  id: string;
  title: string;
}

const allRoles: readonly UserRole[] = ['student', 'lecturer', 'admin'];
const studentRoles: readonly UserRole[] = ['student'];
const lecturerRoles: readonly UserRole[] = ['lecturer', 'admin'];
const adminRoles: readonly UserRole[] = ['admin'];

export const screenRegistry: readonly ScreenDefinition[] = [
  { name: 'auth.signIn', title: 'Sign in', family: 'auth', roles: allRoles, icon: '→' },
  { name: 'dashboard.student', title: 'Dashboard', family: 'student', roles: studentRoles, icon: '⌂' },
  { name: 'schedule', title: 'Schedule', family: 'student', roles: studentRoles, icon: '◷' },
  { name: 'courses', title: 'Courses', family: 'student', roles: studentRoles, icon: '▦' },
  { name: 'grades', title: 'Grades', family: 'student', roles: studentRoles, icon: '✓' },
  { name: 'attendance', title: 'Attendance', family: 'student', roles: studentRoles, icon: '◉' },
  { name: 'registration', title: 'Registration', family: 'student', roles: studentRoles, icon: '+' },
  { name: 'invoices', title: 'Invoices', family: 'student', roles: studentRoles, icon: '$' },
  { name: 'notifications', title: 'Notifications', family: 'student', roles: allRoles, icon: '!' },
  { name: 'profile', title: 'Profile', family: 'student', roles: allRoles, icon: '○' },
  { name: 'thesis.topics', title: 'Thesis topics', family: 'thesis', roles: studentRoles, icon: 'T' },
  { name: 'thesis.detail', title: 'Topic detail', family: 'thesis', roles: studentRoles, icon: 'i' },
  { name: 'thesis.registration', title: 'Thesis registration', family: 'thesis', roles: studentRoles, icon: 'R' },
  { name: 'thesis.progress', title: 'Thesis progress', family: 'thesis', roles: studentRoles, icon: '↗' },
  { name: 'thesis.evaluation', title: 'Thesis evaluation', family: 'thesis', roles: studentRoles, icon: '★' },
  { name: 'assistant.chat', title: 'Academic assistant', family: 'assistant', roles: allRoles, icon: '?' },
  { name: 'admin.dashboard', title: 'Admin dashboard', family: 'operations', roles: adminRoles, icon: 'A' },
  { name: 'admin.students', title: 'Manage students', family: 'operations', roles: adminRoles, icon: 'S' },
  { name: 'admin.lecturers', title: 'Manage lecturers', family: 'operations', roles: adminRoles, icon: 'L' },
  { name: 'lecturer.dashboard', title: 'Lecturer dashboard', family: 'operations', roles: lecturerRoles, icon: 'D' },
  { name: 'lecturer.schedule', title: 'Teaching schedule', family: 'operations', roles: lecturerRoles, icon: 'C' },
  { name: 'lecturer.grading', title: 'Gradebook', family: 'operations', roles: lecturerRoles, icon: 'G' },
  { name: 'lecturer.attendance', title: 'Class attendance', family: 'operations', roles: lecturerRoles, icon: 'P' },
];

const bottomNavigationByRole: Readonly<Record<UserRole, readonly BottomNavigationItem[]>> = {
  student: [
    { route: 'dashboard.student', label: 'Home', icon: '⌂' },
    { route: 'schedule', label: 'Schedule', icon: '◷' },
    { route: 'courses', label: 'Courses', icon: '▦' },
    { route: 'notifications', label: 'Alerts', icon: '!' },
  ],
  lecturer: [
    { route: 'lecturer.dashboard', label: 'Home', icon: '⌂' },
    { route: 'lecturer.schedule', label: 'Schedule', icon: '◷' },
    { route: 'lecturer.grading', label: 'Gradebook', icon: 'G' },
    { route: 'notifications', label: 'Alerts', icon: '!' },
  ],
  admin: [
    { route: 'admin.dashboard', label: 'Home', icon: '⌂' },
    { route: 'admin.students', label: 'Students', icon: 'S' },
    { route: 'admin.lecturers', label: 'Lecturers', icon: 'L' },
    { route: 'notifications', label: 'Alerts', icon: '!' },
  ],
};

// Direct references only. Other registry screens derive from the same token and
// interaction contract until a dedicated Stitch screen is added.
export const stitchMobileReferences: Readonly<
  Partial<Record<ScreenName, readonly StitchMobileReference[]>>
> = {
  'auth.signIn': [
    { id: '67e517e324ef454c810e2b41b4acdc99', title: 'Đăng nhập - Hệ thống Quản lý Đề tài' },
  ],
  'dashboard.student': [
    { id: '55abc697e13e416aa87c89fd331e6c62', title: 'Dashboard - Mobile App' },
    { id: '89b8434a99dd4fcb87ce3c84469e3186', title: 'Trang chủ - Dashboard' },
  ],
  registration: [
    { id: '05a2b1b196034830bc559ae72b82b6f9', title: 'Danh sách Đợt đăng ký' },
    { id: '4f5dfbb559504aa38f58b22599f361e4', title: 'Danh sách đợt đăng ký - Mobile App' },
  ],
  notifications: [
    { id: 'a8940ff416834cf59245e878780e2135', title: 'Trung tâm thông báo - Mobile App' },
  ],
  profile: [
    { id: '1a12e27f4365459eb1d4ed274514d04d', title: 'Hồ sơ Sinh viên - Mobile App' },
  ],
  'thesis.topics': [
    { id: '3d1a0cf1dd494919ab9095b738f0c8e4', title: 'Danh sách Đề tài' },
  ],
  'thesis.detail': [
    { id: 'be1ea000e675492d829254077907039a', title: 'Chi tiết Đề tài' },
  ],
  'thesis.registration': [
    { id: '4ac0f22ff9c14155b071bf3c8ffa699c', title: 'Đăng ký Đề tài - Mobile App' },
  ],
  'thesis.progress': [
    { id: '5cb62add07304fa689a98c8512495482', title: 'Theo dõi tiến độ - Mobile App' },
  ],
  'thesis.evaluation': [
    { id: '3073bce589eb4d4e97ef9775e921a506', title: 'Đánh giá Đề tài - Mobile App' },
  ],
  'admin.lecturers': [
    { id: '36c60dec6ed9458a80bc5b1cfe6f82a5', title: 'Danh sách Giảng viên - Mobile App' },
  ],
};

export const menuSections = [
  {
    title: 'Student workspace',
    families: ['student', 'thesis', 'assistant'] as const,
  },
  {
    title: 'Operations',
    families: ['operations'] as const,
  },
] as const;

export function getScreenDefinition(route: ScreenName) {
  return screenRegistry.find((screen) => screen.name === route);
}

export function canAccessScreen(role: UserRole, route: ScreenName) {
  return getScreenDefinition(route)?.roles.includes(role) ?? false;
}

export function getBottomNavigation(role: UserRole) {
  return bottomNavigationByRole[role];
}
