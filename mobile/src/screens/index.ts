import type { ComponentType } from 'react';

import { SignInScreen } from './auth/SignInScreen';
import { AssistantChatScreen } from './assistant/AssistantScreen';
import {
  AdminDashboardScreen,
  AdminLecturersScreen,
  AdminStudentsScreen,
  LecturerAttendanceScreen,
  LecturerDashboardScreen,
  LecturerGradingScreen,
  LecturerScheduleScreen,
} from './operations/OperationsScreens';
import {
  AttendanceScreen,
  CoursesScreen,
  GradesScreen,
  InvoicesScreen,
  NotificationsScreen,
  ProfileScreen,
  RegistrationScreen,
  ScheduleScreen,
  StudentDashboardScreen,
} from './student/StudentScreens';
import {
  ThesisDetailScreen,
  ThesisEvaluationScreen,
  ThesisProgressScreen,
  ThesisRegistrationScreen,
  ThesisTopicsScreen,
} from './thesis/ThesisScreens';
import type { ScreenName } from '../navigation/routes';
import type { MobileScreenProps } from '../navigation/types';

export const screenComponents: Record<ScreenName, ComponentType<MobileScreenProps>> = {
  'auth.signIn': SignInScreen,
  'dashboard.student': StudentDashboardScreen,
  schedule: ScheduleScreen,
  courses: CoursesScreen,
  grades: GradesScreen,
  attendance: AttendanceScreen,
  registration: RegistrationScreen,
  invoices: InvoicesScreen,
  notifications: NotificationsScreen,
  profile: ProfileScreen,
  'thesis.topics': ThesisTopicsScreen,
  'thesis.detail': ThesisDetailScreen,
  'thesis.registration': ThesisRegistrationScreen,
  'thesis.progress': ThesisProgressScreen,
  'thesis.evaluation': ThesisEvaluationScreen,
  'assistant.chat': AssistantChatScreen,
  'admin.dashboard': AdminDashboardScreen,
  'admin.students': AdminStudentsScreen,
  'admin.lecturers': AdminLecturersScreen,
  'lecturer.dashboard': LecturerDashboardScreen,
  'lecturer.schedule': LecturerScheduleScreen,
  'lecturer.grading': LecturerGradingScreen,
  'lecturer.attendance': LecturerAttendanceScreen,
};
