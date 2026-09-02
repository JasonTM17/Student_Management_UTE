import { MaterialCommunityIcons } from '@expo/vector-icons';
import type { ComponentProps } from 'react';

export type NavigationIconName = ComponentProps<typeof MaterialCommunityIcons>['name'];
export type UserRole = 'student' | 'lecturer' | 'admin';

export type ScreenName =
  | 'auth.signIn'
  | 'dashboard.student'
  | 'schedule'
  | 'courses'
  | 'grades'
  | 'attendance'
  | 'registration'
  | 'notifications'
  | 'profile'
  | 'thesis.topics'
  | 'thesis.detail'
  | 'thesis.registration'
  | 'thesis.progress'
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
  | 'staff';

export interface ScreenDefinition {
  name: ScreenName;
  title: string;
  family: ScreenFamily;
  roles: readonly UserRole[];
  icon: NavigationIconName;
}

export interface BottomNavigationItem {
  route: ScreenName;
  label: string;
  icon: NavigationIconName;
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
  { name: 'auth.signIn', title: 'Sign in', family: 'auth', roles: allRoles, icon: 'login' },
  { name: 'dashboard.student', title: 'Dashboard', family: 'student', roles: studentRoles, icon: 'home-variant' },
  { name: 'schedule', title: 'Schedule', family: 'student', roles: studentRoles, icon: 'calendar-week' },
  { name: 'courses', title: 'Courses', family: 'student', roles: studentRoles, icon: 'book-open-page-variant' },
  { name: 'grades', title: 'Grades', family: 'student', roles: studentRoles, icon: 'chart-box-outline' },
  { name: 'attendance', title: 'Attendance', family: 'student', roles: studentRoles, icon: 'clipboard-check-outline' },
  { name: 'registration', title: 'Registration', family: 'student', roles: studentRoles, icon: 'book-plus-outline' },
  { name: 'notifications', title: 'Notifications', family: 'student', roles: allRoles, icon: 'bell-outline' },
  { name: 'profile', title: 'Profile', family: 'student', roles: allRoles, icon: 'account-circle-outline' },
  { name: 'thesis.topics', title: 'Thesis topics', family: 'thesis', roles: studentRoles, icon: 'lightbulb-on-outline' },
  { name: 'thesis.detail', title: 'Topic detail', family: 'thesis', roles: studentRoles, icon: 'text-box-search-outline' },
  { name: 'thesis.registration', title: 'Thesis registration', family: 'thesis', roles: studentRoles, icon: 'account-group-outline' },
  { name: 'thesis.progress', title: 'Thesis progress', family: 'thesis', roles: studentRoles, icon: 'chart-timeline-variant' },
  { name: 'assistant.chat', title: 'CampusCore assistant', family: 'assistant', roles: allRoles, icon: 'robot-outline' },
  { name: 'admin.dashboard', title: 'Admin dashboard', family: 'staff', roles: adminRoles, icon: 'shield-account-outline' },
  { name: 'admin.students', title: 'Manage students', family: 'staff', roles: adminRoles, icon: 'school-outline' },
  { name: 'admin.lecturers', title: 'Manage lecturers', family: 'staff', roles: adminRoles, icon: 'account-tie-outline' },
  { name: 'lecturer.dashboard', title: 'Lecturer dashboard', family: 'staff', roles: lecturerRoles, icon: 'view-dashboard-outline' },
  { name: 'lecturer.schedule', title: 'Teaching schedule', family: 'staff', roles: lecturerRoles, icon: 'calendar-account-outline' },
  { name: 'lecturer.grading', title: 'Gradebook', family: 'staff', roles: lecturerRoles, icon: 'notebook-edit-outline' },
  { name: 'lecturer.attendance', title: 'Class attendance', family: 'staff', roles: lecturerRoles, icon: 'account-check-outline' },
];

const bottomNavigationByRole: Readonly<Record<UserRole, readonly BottomNavigationItem[]>> = {
  student: [
    { route: 'dashboard.student', label: 'Home', icon: 'home-variant' },
    { route: 'schedule', label: 'Schedule', icon: 'calendar-week' },
    { route: 'registration', label: 'Register', icon: 'book-plus-outline' },
    { route: 'notifications', label: 'Alerts', icon: 'bell-outline' },
  ],
  lecturer: [
    { route: 'lecturer.dashboard', label: 'Home', icon: 'home-variant' },
    { route: 'lecturer.schedule', label: 'Schedule', icon: 'calendar-account-outline' },
    { route: 'lecturer.grading', label: 'Gradebook', icon: 'notebook-edit-outline' },
    { route: 'notifications', label: 'Alerts', icon: 'bell-outline' },
  ],
  admin: [
    { route: 'admin.dashboard', label: 'Home', icon: 'shield-account-outline' },
    { route: 'admin.students', label: 'Students', icon: 'school-outline' },
    { route: 'admin.lecturers', label: 'Lecturers', icon: 'account-tie-outline' },
    { route: 'notifications', label: 'Alerts', icon: 'bell-outline' },
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
    title: 'Staff',
    families: ['staff'] as const,
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
