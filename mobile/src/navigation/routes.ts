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

export const bottomNavigation = [
  { route: 'dashboard.student', label: 'Home', icon: '⌂' },
  { route: 'schedule', label: 'Schedule', icon: '◷' },
  { route: 'courses', label: 'Courses', icon: '▦' },
  { route: 'notifications', label: 'Alerts', icon: '!' },
] as const satisfies ReadonlyArray<{
  route: ScreenName;
  label: string;
  icon: string;
}>;

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

