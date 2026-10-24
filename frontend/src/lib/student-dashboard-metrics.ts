import { isActiveEnrollment, isCancelledSection } from '@/lib/enrollment-status';

export interface StudentDashboardEnrollment {
  status: string;
  section?: {
    status?: string;
    course?: { credits?: number };
  };
}

export function deriveStudentDashboardMetrics<T extends StudentDashboardEnrollment>(
  enrollments: T[],
  creditCap: number | null,
) {
  const activeCourses = enrollments.filter(
    (enrollment) => isActiveEnrollment(enrollment.status) && !isCancelledSection(enrollment),
  );
  const pendingCourses = enrollments.filter(
    (enrollment) => enrollment.status === 'PENDING' && !isCancelledSection(enrollment),
  );
  const enrolledCourseCount = enrollments.filter(
    (enrollment) => enrollment.status === 'ENROLLED' && !isCancelledSection(enrollment),
  ).length;
  const currentCredits = activeCourses.reduce(
    (sum, enrollment) => sum + (enrollment.section?.course?.credits ?? 0),
    0,
  );
  const creditPercent = creditCap
    ? Math.min(100, Math.round((currentCredits / creditCap) * 100))
    : null;
  const creditRemaining = creditCap ? Math.max(0, creditCap - currentCredits) : null;

  return {
    activeCourses,
    pendingCourses,
    enrolledCourseCount,
    currentCredits,
    creditCap,
    creditPercent,
    creditRemaining,
  };
}
