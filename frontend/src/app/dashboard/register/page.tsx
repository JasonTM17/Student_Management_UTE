'use client';

import { useCallback, useEffect, useMemo, useState } from 'react';
import { AxiosError } from 'axios';
import {
  AlertCircle,
  BookOpen,
  CalendarClock,
  CheckCircle2,
  ClipboardList,
  Clock3,
  ListOrdered,
  ShieldAlert,
  Users,
} from 'lucide-react';
import { LocalizedLink } from '@/components/LocalizedLink';
import {
  WorkspaceMetricCard,
  WorkspacePanel,
} from '@/components/dashboard/WorkspaceSurface';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import {
  enrollmentsApi,
  sectionsApi,
  semestersApi,
  waitlistApi,
} from '@/lib/api';
import { getLocalizedCourseLabel, getLocalizedName } from '@/lib/academic-content';
import { pickPreferredSemesterId } from '@/lib/semesters';
import type {
  Course,
  Department,
  Enrollment,
  Section,
  Semester,
  WaitlistEntry,
} from '@/types/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent } from '@/components/ui/card';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { Select } from '@/components/ui/select';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/state-block';

type RegistrationLoadState = 'ready' | 'backend-unavailable' | 'unavailable';
const SEAT_CONSUMING_ENROLLMENT_STATUSES = new Set([
  'PENDING',
  'CONFIRMED',
  'COMPLETED',
]);

function parseTimeSlot(value: string) {
  const [hours, minutes] = value.split(':').map((item) => Number(item));
  return hours * 60 + minutes;
}

function rangesOverlap(
  leftStart: string,
  leftEnd: string,
  rightStart: string,
  rightEnd: string,
) {
  return parseTimeSlot(leftStart) < parseTimeSlot(rightEnd) &&
    parseTimeSlot(rightStart) < parseTimeSlot(leftEnd);
}

function sectionsConflict(left?: Section, right?: Section) {
  if (!left?.schedules?.length || !right?.schedules?.length) {
    return false;
  }

  return left.schedules.some((leftSchedule) =>
    right.schedules?.some(
      (rightSchedule) =>
        leftSchedule.dayOfWeek === rightSchedule.dayOfWeek &&
        rangesOverlap(
          leftSchedule.startTime,
          leftSchedule.endTime,
          rightSchedule.startTime,
          rightSchedule.endTime,
        ),
    ),
  );
}

function getDayName(dayOfWeek: number, locale: 'en' | 'vi') {
  const names =
    locale === 'vi'
      ? ['Chủ nhật', 'Thứ hai', 'Thứ ba', 'Thứ tư', 'Thứ năm', 'Thứ sáu', 'Thứ bảy']
      : ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];

  return names[dayOfWeek] ?? '';
}

function getRegistrationWindowState(semester?: Semester | null) {
  if (!semester) {
    return 'planning-only' as const;
  }

  const now = new Date();
  const registrationStart = semester.registrationStart
    ? new Date(semester.registrationStart)
    : null;
  const registrationEnd = semester.registrationEnd
    ? new Date(semester.registrationEnd)
    : null;
  const addDropEnd = semester.addDropEnd ? new Date(semester.addDropEnd) : null;

  if (semester.status === 'REGISTRATION_OPEN') {
    return 'registration-open' as const;
  }

  if (
    semester.status === 'ADD_DROP_OPEN' ||
    semester.status === 'IN_PROGRESS'
  ) {
    return 'add-drop' as const;
  }

  if (registrationStart && registrationStart > now) {
    return 'upcoming' as const;
  }

  if (registrationEnd && registrationEnd < now && addDropEnd && addDropEnd < now) {
    return 'closed' as const;
  }

  if (registrationStart && registrationEnd && registrationStart <= now && registrationEnd >= now) {
    return 'registration-open' as const;
  }

  if (registrationEnd && addDropEnd && registrationEnd < now && addDropEnd >= now) {
    return 'add-drop' as const;
  }

  return 'planning-only' as const;
}

function deriveDepartmentsFromSections(sections: Section[]) {
  const departments = new Map<string, Department>();

  for (const section of sections) {
    const department = section.course?.department;
    if (department?.id) {
      departments.set(department.id, department);
    }
  }

  return [...departments.values()].sort((left, right) =>
    left.name.localeCompare(right.name),
  );
}

function deriveCoursesFromSections(sections: Section[], departmentId: string) {
  const courses = new Map<string, Course>();

  for (const section of sections) {
    if (section.course?.id && section.course.departmentId === departmentId) {
      courses.set(section.course.id, section.course);
    }
  }

  return [...courses.values()].sort((left, right) =>
    left.code.localeCompare(right.code),
  );
}

export default function RegisterPage() {
  const { user, isLoading: authLoading, hasAccess } = useRequireAuth(['STUDENT']);
  const { locale, formatDate, formatNumber } = useI18n();
  const [sections, setSections] = useState<Section[]>([]);
  const [enrollments, setEnrollments] = useState<Enrollment[]>([]);
  const [waitlistEntries, setWaitlistEntries] = useState<WaitlistEntry[]>([]);
  const [semesters, setSemesters] = useState<Semester[]>([]);
  const [departments, setDepartments] = useState<Department[]>([]);
  const [courses, setCourses] = useState<Course[]>([]);
  const [selectedSemester, setSelectedSemester] = useState('');
  const [selectedDepartment, setSelectedDepartment] = useState('');
  const [selectedCourse, setSelectedCourse] = useState('');
  const [plannedSectionIds, setPlannedSectionIds] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState('');
  const [loadState, setLoadState] = useState<RegistrationLoadState>('ready');

  const copy =
    locale === 'vi'
      ? {
          eyebrow: 'Không gian sinh viên',
          title: 'Đăng ký học phần',
          descriptionPrefix: 'Xây kế hoạch đăng ký cho',
          descriptionSuffix:
            'với section đang mở, sức chứa hiện tại và lựa chọn vào danh sách chờ rõ ràng.',
          openCourses: 'Mở môn học của tôi',
          loading: 'Đang tải dữ liệu đăng ký',
          metrics: {
            openSections: 'Section đang mở',
            currentEnrollments: 'Đăng ký hiện tại',
            waitlistOnly: 'Chỉ còn danh sách chờ',
            planned: 'Trong kế hoạch',
          },
          filters: {
            title: 'Lọc theo học kỳ và khoa',
            semester: 'Học kỳ',
            department: 'Khoa',
            course: 'Môn học',
            allSemesters: 'Tất cả học kỳ',
            allDepartments: 'Tất cả khoa',
            allDepartmentCourses: 'Tất cả môn của khoa',
            selectDepartmentFirst: 'Hãy chọn khoa trước',
            clear: 'Xóa bộ lọc',
          },
          planner: {
            title: 'Ba lô đăng ký',
            description:
              'Giữ các section bạn đang cân nhắc, xem xung đột lịch và gửi đăng ký theo lô.',
            emptyTitle: 'Chưa có section nào trong kế hoạch',
            emptyDescription:
              'Thêm section vào kế hoạch để so sánh lịch học, tín chỉ và trạng thái còn chỗ trước khi gửi.',
            submit: 'Gửi kế hoạch đăng ký',
            submitting: 'Đang gửi kế hoạch',
            creditLimit: 'Giới hạn tín chỉ',
            projectedCredits: 'Tín chỉ sau khi thêm',
            conflictCount: 'Xung đột lịch',
            remove: 'Bỏ khỏi kế hoạch',
            review: 'Rà soát kế hoạch',
            planningMessage:
              'Bạn vẫn có thể lên kế hoạch trước, nhưng chỉ gửi đăng ký khi cửa sổ học vụ đang mở.',
          },
          readiness: {
            title: 'Mức sẵn sàng đăng ký',
            description:
              'Đọc nhanh cửa sổ đăng ký, sức chứa section và tình hình danh sách chờ trước khi gửi quyết định.',
            registrationOpenTitle: 'Đang trong cửa sổ đăng ký',
            registrationOpenBody:
              'Bạn có thể đăng ký ngay hoặc chuyển sang danh sách chờ cho các section đã đầy.',
            addDropTitle: 'Đang trong giai đoạn thêm/rút',
            addDropBody:
              'Các thay đổi vẫn được phép, nhưng hãy rà lại lịch và học phí trước khi xác nhận.',
            upcomingTitle: 'Cửa sổ đăng ký chưa mở',
            upcomingBody:
              'Bạn vẫn có thể lên kế hoạch trước để giảm tải khi đợt đăng ký bắt đầu.',
            closedTitle: 'Cửa sổ đăng ký đã đóng',
            closedBody:
              'Bạn chỉ nên xem dữ liệu hiện tại hoặc liên hệ quản trị học vụ nếu cần hỗ trợ thêm.',
            planningTitle: 'Đang ở chế độ lên kế hoạch',
            planningBody:
              'Môi trường này đang ưu tiên so sánh section, lịch học và các bước tiếp theo trước khi ghi nhận đăng ký.',
          },
          waitlist: {
            title: 'Danh sách chờ của tôi',
            description:
              'Theo dõi vị trí chờ hiện tại và rời hàng đợi nếu bạn muốn đổi sang section khác.',
            emptyTitle: 'Chưa có mục nào trong danh sách chờ',
            emptyDescription:
              'Các section đầy chỗ mà bạn tham gia chờ sẽ xuất hiện ở đây cùng thứ tự hiện tại.',
            position: (position: number) => `Vị trí ${position}`,
            leave: 'Rời danh sách chờ',
            confirmTitle: 'Rời danh sách chờ?',
            confirmMessage:
              'Mục này sẽ bị gỡ khỏi hàng chờ hiện tại. Bạn vẫn có thể quay lại trang đăng ký để chọn section khác.',
            confirmAction: 'Rời hàng chờ',
          },
          states: {
            backendUnavailableTitle: 'Hệ thống đăng ký hiện chưa phản hồi',
            backendUnavailableDescription:
              'Dịch vụ đăng ký hoặc học vụ hiện chưa sẵn sàng. Hãy thử lại sau ít phút.',
            unavailableTitle: 'Đăng ký học phần chưa sẵn sàng',
            unavailableDescription:
              'Hiện chưa thể tải dữ liệu đăng ký học phần.',
            emptyCatalogTitle: 'Chưa có section nào cho phạm vi hiện tại',
            emptyCatalogDescription:
              'Khi học phần và section được mở cho học kỳ đang xét, chúng sẽ xuất hiện tại đây.',
            filteredEmptyTitle: 'Không có section khớp bộ lọc',
            filteredEmptyDescription:
              'Hãy thử một tổ hợp học kỳ, khoa hoặc môn học khác để mở rộng danh sách đăng ký.',
            waitlistOnlyTitle: 'Các section đang hiển thị đều đã đầy',
            waitlistOnlyDescription:
              'Bạn vẫn có thể thêm vào kế hoạch và chuyển sang danh sách chờ cho các section phù hợp.',
          },
          section: {
            prefix: 'Section',
            departmentFallback: 'Chưa có thông tin khoa',
            creditsSuffix: 'tín chỉ',
            seatsLeft: (count: number) => `${count} chỗ còn lại`,
            full: 'Đã đầy',
            alreadyEnrolled: 'Đã đăng ký',
            addToPlan: 'Thêm vào kế hoạch',
            removeFromPlan: 'Bỏ khỏi kế hoạch',
            joinWaitlist: 'Vào danh sách chờ',
            enrollNow: 'Đăng ký ngay',
            resolveConflict: 'Xử lý xung đột trước',
            planningOnly: 'Đang ở chế độ lên kế hoạch',
            statusClosed: 'Đã đóng',
            statusCancelled: 'Đã hủy',
            submitting: 'Đang gửi',
            scheduleConflict: 'Xung đột lịch',
            nearCapacity: 'Sắp đầy',
            statusOpen: 'Đang mở',
          },
          toasts: {
            missingProfile: 'Chưa tìm thấy hồ sơ sinh viên trong phiên hiện tại.',
            waitlistSuccess: (position: number) =>
              `Đã vào danh sách chờ ở vị trí ${position}.`,
            enrollSuccess: 'Đã gửi đăng ký.',
            enrollFailed: 'Hiện chưa thể hoàn tất đăng ký này.',
            waitlistRemoved: 'Đã rời danh sách chờ.',
            waitlistRemoveFailed: 'Không thể rời danh sách chờ này.',
            plannedSubmitted: (enrolled: number, waitlisted: number) =>
              `Đã xử lý ${enrolled} đăng ký và ${waitlisted} mục danh sách chờ từ kế hoạch hiện tại.`,
            noPlannedSections: 'Hãy thêm ít nhất một section vào kế hoạch trước.',
            registrationWindowClosed:
              'Cửa sổ đăng ký hiện chưa cho phép ghi nhận thay đổi mới.',
            resolveScheduleConflict:
              'Hãy xử lý xung đột lịch trước khi gửi section này.',
            sectionUnavailable:
              'Section này hiện chưa mở cho thao tác đăng ký.',
            planNeedsReview:
              'Kế hoạch hiện còn section bị đóng hoặc xung đột lịch. Hãy rà soát trước khi gửi.',
          },
        }
      : {
          eyebrow: 'Student workspace',
          title: 'Course registration',
          descriptionPrefix: 'Build a registration plan for',
          descriptionSuffix:
            'with open sections, live seat counts, and clear waitlist options.',
          openCourses: 'Open my courses',
          loading: 'Loading registration data',
          metrics: {
            openSections: 'Open sections',
            currentEnrollments: 'Current enrollments',
            waitlistOnly: 'Waitlist only',
            planned: 'In planning',
          },
          filters: {
            title: 'Filter by term and department',
            semester: 'Semester',
            department: 'Department',
            course: 'Course',
            allSemesters: 'All semesters',
            allDepartments: 'All departments',
            allDepartmentCourses: 'All department courses',
            selectDepartmentFirst: 'Select a department first',
            clear: 'Clear filters',
          },
          planner: {
            title: 'Registration backpack',
            description:
              'Keep the sections you are considering, preview schedule conflicts, and submit enrollments in one pass.',
            emptyTitle: 'No sections in the plan yet',
            emptyDescription:
              'Add sections to the plan to compare timing, credits, and seat posture before you submit.',
            submit: 'Submit registration plan',
            submitting: 'Submitting plan',
            creditLimit: 'Credit limit',
            projectedCredits: 'Projected credits',
            conflictCount: 'Schedule conflicts',
            remove: 'Remove from plan',
            review: 'Review plan',
            planningMessage:
              'You can keep planning now, but final registration only opens during the active academic window.',
          },
          readiness: {
            title: 'Registration readiness',
            description:
              'Check the current registration window, section capacity, and waitlist posture before submitting changes.',
            registrationOpenTitle: 'Registration window is open',
            registrationOpenBody:
              'You can enroll now or move into waitlists for sections that are already full.',
            addDropTitle: 'Add/drop is active',
            addDropBody:
              'Changes are still allowed, but this is the right moment to recheck schedules and capacity.',
            upcomingTitle: 'Registration has not opened yet',
            upcomingBody:
              'You can still build a plan now so the actual registration window starts with less friction.',
            closedTitle: 'Registration is closed',
            closedBody:
              'Use this view for planning and review, or contact academic operations if you need an exception.',
            planningTitle: 'Planning mode',
            planningBody:
              'This environment is currently focused on comparing sections, schedules, and the next action before enrollment is recorded.',
          },
          waitlist: {
            title: 'My waitlist',
            description:
              'Track your current queue position and leave the line if you decide to switch sections.',
            emptyTitle: 'No active waitlist entries yet',
            emptyDescription:
              'Sections that are full and added to your waitlist will appear here with the current queue order.',
            position: (position: number) => `Position ${position}`,
            leave: 'Leave waitlist',
            confirmTitle: 'Leave this waitlist?',
            confirmMessage:
              'This will remove the section from your active queue. You can still return to registration and choose another section afterward.',
            confirmAction: 'Leave waitlist',
          },
          states: {
            backendUnavailableTitle: 'Registration services are not responding',
            backendUnavailableDescription:
              'Registration or academic services are unavailable right now. Try again in a moment.',
            unavailableTitle: 'Registration unavailable',
            unavailableDescription:
              'Registration data could not be loaded.',
            emptyCatalogTitle: 'No sections are available in this scope yet',
            emptyCatalogDescription:
              'Once sections are published for the selected planning scope, they will appear here.',
            filteredEmptyTitle: 'No sections match the active filters',
            filteredEmptyDescription:
              'Try another semester, department, or course combination to widen the planning view.',
            waitlistOnlyTitle: 'Every visible section is already full',
            waitlistOnlyDescription:
              'You can still add them to the plan and join the waitlist for the best-fit sections.',
          },
          section: {
            prefix: 'Section',
            departmentFallback: 'Department information unavailable',
            creditsSuffix: 'credits',
            seatsLeft: (count: number) => `${count} seat(s) left`,
            full: 'Full',
            alreadyEnrolled: 'Already enrolled',
            addToPlan: 'Add to plan',
            removeFromPlan: 'Remove from plan',
            joinWaitlist: 'Join waitlist',
            enrollNow: 'Enroll now',
            resolveConflict: 'Resolve conflict first',
            planningOnly: 'Planning only',
            statusClosed: 'Closed',
            statusCancelled: 'Cancelled',
            submitting: 'Submitting',
            scheduleConflict: 'Schedule conflict',
            nearCapacity: 'Nearly full',
            statusOpen: 'Open',
          },
          toasts: {
            missingProfile: 'Your student profile is not available in this session.',
            waitlistSuccess: (position: number) =>
              `Joined the waitlist at position ${position}.`,
            enrollSuccess: 'Enrollment submitted.',
            enrollFailed: 'This enrollment could not be completed.',
            waitlistRemoved: 'You left the waitlist.',
            waitlistRemoveFailed: 'This waitlist entry could not be removed.',
            plannedSubmitted: (enrolled: number, waitlisted: number) =>
              `Processed ${enrolled} enrollment(s) and ${waitlisted} waitlist move(s) from the current plan.`,
            noPlannedSections: 'Add at least one section to the plan first.',
            registrationWindowClosed:
              'The current registration window is not accepting new changes.',
            resolveScheduleConflict:
              'Resolve the schedule conflict before you submit this section.',
            sectionUnavailable:
              'This section is not currently open for registration changes.',
            planNeedsReview:
              'The plan still contains closed sections or schedule conflicts. Review it before submitting.',
          },
        };

  const readOnlyCopy =
    locale === 'vi'
      ? 'Danh mục học phần và danh sách chờ đang ở chế độ chỉ đọc. API Java hiện chưa sở hữu thao tác đăng ký hoặc rời danh sách chờ.'
      : 'The course catalog and waitlist are read-only. The Java API does not own enrollment or waitlist mutations yet.';

  const fetchBaseData = useCallback(async () => {
    setIsLoading(true);
    setError('');
    setLoadState('ready');

    try {
      const [
        sectionsResult,
        enrollmentsResult,
        waitlistResult,
        semestersResult,
      ] = await Promise.allSettled([
        sectionsApi.getAll({ limit: 150 }),
        enrollmentsApi.getMyEnrollments(),
        waitlistApi.getMyWaitlist(),
        semestersApi.getAll(),
      ]);

      if (
        sectionsResult.status !== 'fulfilled' ||
        enrollmentsResult.status !== 'fulfilled' ||
        semestersResult.status !== 'fulfilled'
      ) {
        const primaryFailure = [
          sectionsResult,
          enrollmentsResult,
          semestersResult,
        ].find((result) => result.status === 'rejected');

        if (
          primaryFailure?.status === 'rejected' &&
          primaryFailure.reason instanceof AxiosError &&
          !primaryFailure.reason.response
        ) {
          setLoadState('backend-unavailable');
          setError(copy.states.backendUnavailableDescription);
        } else {
          setLoadState('unavailable');
          setError(copy.states.unavailableDescription);
        }

        return;
      }

      const loadedSections = sectionsResult.value.data ?? [];
      setSections(loadedSections);
      setEnrollments(enrollmentsResult.value);
      setWaitlistEntries(waitlistResult.status === 'fulfilled' ? waitlistResult.value : []);
      setSemesters(semestersResult.value.data ?? []);
      setDepartments(deriveDepartmentsFromSections(loadedSections));

      const preferredSemesterId = pickPreferredSemesterId(
        semestersResult.value.data,
      );
      if (preferredSemesterId) {
        setSelectedSemester((current) => current || preferredSemesterId);
      }
    } catch (loadError) {
      if (loadError instanceof AxiosError && !loadError.response) {
        setLoadState('backend-unavailable');
        setError(copy.states.backendUnavailableDescription);
      } else {
        setLoadState('unavailable');
        setError(copy.states.unavailableDescription);
      }
    } finally {
      setIsLoading(false);
    }
  }, [copy.states.backendUnavailableDescription, copy.states.unavailableDescription]);

  useEffect(() => {
    if (hasAccess) {
      void fetchBaseData();
    }
  }, [fetchBaseData, hasAccess]);

  useEffect(() => {
    if (!user?.studentId || typeof window === 'undefined') {
      return;
    }

    const raw = window.localStorage.getItem(
      `campuscore.registration-plan.${user.studentId}`,
    );

    if (!raw) {
      setPlannedSectionIds([]);
      return;
    }

    try {
      const parsed = JSON.parse(raw) as string[];
      setPlannedSectionIds(Array.isArray(parsed) ? parsed : []);
    } catch {
      setPlannedSectionIds([]);
    }
  }, [user?.studentId]);

  useEffect(() => {
    if (!user?.studentId || typeof window === 'undefined') {
      return;
    }

    window.localStorage.setItem(
      `campuscore.registration-plan.${user.studentId}`,
      JSON.stringify(plannedSectionIds),
    );
  }, [plannedSectionIds, user?.studentId]);

  useEffect(() => {
    if (!selectedDepartment) {
      setCourses([]);
      setSelectedCourse('');
      return;
    }

    setCourses(deriveCoursesFromSections(sections, selectedDepartment));
  }, [sections, selectedDepartment]);

  useEffect(() => {
    if (!selectedSemester) {
      return;
    }

    const semesterSectionIds = new Set(
      sections
        .filter((section) => section.semesterId === selectedSemester)
        .map((section) => section.id),
    );

    setPlannedSectionIds((current) =>
      current.filter((id) => semesterSectionIds.has(id)),
    );

    const currentDepartmentSections = sections.filter(
      (section) =>
        section.semesterId === selectedSemester &&
        section.course?.departmentId === selectedDepartment,
    );

    if (currentDepartmentSections.length === 0 && selectedDepartment) {
      setSelectedDepartment('');
      setSelectedCourse('');
    }
  }, [selectedSemester, sections, selectedDepartment]);

  const selectedSemesterRecord = useMemo(
    () => semesters.find((semester) => semester.id === selectedSemester) ?? null,
    [selectedSemester, semesters],
  );

  const selectedSemesterName = useMemo(
    () =>
      getLocalizedName(
        locale,
        selectedSemesterRecord,
        locale === 'vi' ? 'h\u1ecdc k\u1ef3 hi\u1ec7n t\u1ea1i' : 'the current term',
      ) ??
      (locale === 'vi' ? 'học kỳ hiện tại' : 'the current term'),
    [locale, selectedSemesterRecord],
  );

  const filteredSections = useMemo(() => {
    return sections.filter((section) => {
      if (selectedSemester && section.semesterId !== selectedSemester) {
        return false;
      }
      if (selectedDepartment && section.course?.departmentId !== selectedDepartment) {
        return false;
      }
      if (selectedCourse && section.courseId !== selectedCourse) {
        return false;
      }
      return true;
    });
  }, [sections, selectedCourse, selectedDepartment, selectedSemester]);

  const seatConsumingEnrollments = useMemo(
    () =>
      enrollments.filter((enrollment) =>
        SEAT_CONSUMING_ENROLLMENT_STATUSES.has(enrollment.status),
      ),
    [enrollments],
  );

  const enrolledSectionIds = useMemo(
    () =>
      new Set(seatConsumingEnrollments.map((enrollment) => enrollment.sectionId)),
    [seatConsumingEnrollments],
  );

  const plannedSections = useMemo(
    () =>
      plannedSectionIds
        .map((sectionId) => sections.find((section) => section.id === sectionId))
        .filter(Boolean) as Section[],
    [plannedSectionIds, sections],
  );

  const creditLimit = useMemo(() => {
    const discovered = sections.reduce((max, section) => {
      return Math.max(max, section.maxCredits ?? 0);
    }, 0);

    return discovered || 18;
  }, [sections]);

  const currentCredits = useMemo(
    () =>
      seatConsumingEnrollments
        .reduce(
          (total, enrollment) => total + (enrollment.section?.course?.credits ?? 0),
          0,
        ),
    [seatConsumingEnrollments],
  );

  const projectedCredits = useMemo(
    () =>
      currentCredits +
      plannedSections.reduce((total, section) => {
        if (enrolledSectionIds.has(section.id)) {
          return total;
        }
        return total + (section.course?.credits ?? 0);
      }, 0),
    [currentCredits, enrolledSectionIds, plannedSections],
  );

  const conflictSectionIds = useMemo(() => {
    const conflicts = new Set<string>();
    const confirmedSections = seatConsumingEnrollments
      .map((enrollment) => enrollment.section)
      .filter(Boolean) as Section[];

    plannedSections.forEach((section, index) => {
      if (confirmedSections.some((confirmed) => sectionsConflict(section, confirmed))) {
        conflicts.add(section.id);
      }

      plannedSections.slice(index + 1).forEach((otherSection) => {
        if (sectionsConflict(section, otherSection)) {
          conflicts.add(section.id);
          conflicts.add(otherSection.id);
        }
      });
    });

    return conflicts;
  }, [plannedSections, seatConsumingEnrollments]);

  const registrationWindowState = getRegistrationWindowState(selectedSemesterRecord);
  const waitlistOnlyCount = filteredSections.filter((section) => {
    const seatsLeft = Math.max(section.capacity - (section.enrolledCount ?? 0), 0);
    return section.status === 'OPEN' && seatsLeft === 0;
  }).length;
  const waitlistOnlyView =
    filteredSections.length > 0 &&
    filteredSections.every((section) => {
      const seatsLeft = Math.max(section.capacity - (section.enrolledCount ?? 0), 0);
      return section.status !== 'OPEN' || seatsLeft === 0;
    });
  const togglePlannedSection = (sectionId: string) => {
    setPlannedSectionIds((current) =>
      current.includes(sectionId)
        ? current.filter((id) => id !== sectionId)
        : [...current, sectionId],
    );
  };

  if (authLoading || !hasAccess) {
    return <LoadingState label={copy.loading} />;
  }

  const readinessCard =
    registrationWindowState === 'registration-open'
      ? {
          icon: CheckCircle2,
          title: copy.readiness.registrationOpenTitle,
          description: copy.readiness.registrationOpenBody,
          tone: 'bg-emerald-500/10 text-emerald-600 dark:text-emerald-300',
        }
      : registrationWindowState === 'add-drop'
        ? {
            icon: Clock3,
            title: copy.readiness.addDropTitle,
            description: copy.readiness.addDropBody,
            tone: 'bg-amber-500/10 text-amber-600 dark:text-amber-300',
          }
        : registrationWindowState === 'upcoming'
          ? {
              icon: CalendarClock,
              title: copy.readiness.upcomingTitle,
              description: copy.readiness.upcomingBody,
              tone: 'bg-blue-500/10 text-blue-600 dark:text-blue-300',
            }
          : registrationWindowState === 'closed'
            ? {
                icon: ShieldAlert,
                title: copy.readiness.closedTitle,
                description: copy.readiness.closedBody,
                tone: 'bg-rose-500/10 text-rose-600 dark:text-rose-300',
              }
            : {
                icon: ClipboardList,
                title: copy.readiness.planningTitle,
                description: copy.readiness.planningBody,
                tone: 'bg-secondary text-foreground',
              };

  return (
    <div className="space-y-5">
      <PageHeader
        eyebrow={<SectionEyebrow>{copy.eyebrow}</SectionEyebrow>}
        title={copy.title}
        description={`${copy.descriptionPrefix} ${selectedSemesterName} ${copy.descriptionSuffix}`}
        actions={
          <LocalizedLink href="/dashboard/enrollments">
            <Button variant="outline">{copy.openCourses}</Button>
          </LocalizedLink>
        }
      />

      <div className="portal-readonly-notice" role="status">
        <ShieldAlert className="h-4 w-4 shrink-0" />
        <span>{readOnlyCopy}</span>
      </div>

      {error ? (
        <ErrorState
          title={
            loadState === 'backend-unavailable'
              ? copy.states.backendUnavailableTitle
              : copy.states.unavailableTitle
          }
          description={error}
          onRetry={() => void fetchBaseData()}
        />
      ) : isLoading ? (
        <LoadingState label={copy.loading} />
      ) : (
        <>
          <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
            <WorkspaceMetricCard
              compact
              label={copy.metrics.openSections}
              value={formatNumber(filteredSections.filter((section) => section.status === 'OPEN').length)}
              icon={<ClipboardList className="h-5 w-5" />}
              detail={locale === 'vi' ? 'Danh sách đang phản ánh bộ lọc hiện tại.' : 'Based on the active planning filters.'}
              toneClassName="bg-blue-500/12 text-blue-600 dark:text-blue-300"
            />
            <WorkspaceMetricCard
              compact
              label={copy.metrics.currentEnrollments}
              value={formatNumber(enrollments.length)}
              icon={<BookOpen className="h-5 w-5" />}
              detail={locale === 'vi' ? 'Bao gồm cả các mục đang chờ xử lý.' : 'Includes any enrollment still waiting on a decision.'}
              toneClassName="bg-emerald-500/12 text-emerald-600 dark:text-emerald-300"
            />
            <WorkspaceMetricCard
              compact
              label={copy.metrics.waitlistOnly}
              value={formatNumber(waitlistOnlyCount)}
              icon={<Users className="h-5 w-5" />}
              detail={locale === 'vi' ? 'Section đang mở nhưng đã hết chỗ trực tiếp.' : 'Open sections where only the waitlist path remains.'}
              toneClassName="bg-amber-500/12 text-amber-600 dark:text-amber-300"
            />
            <WorkspaceMetricCard
              compact
              label={copy.metrics.planned}
              value={formatNumber(plannedSections.length)}
              icon={<CalendarClock className="h-5 w-5" />}
              detail={locale === 'vi' ? 'Được lưu cục bộ cho tài khoản hiện tại.' : 'Saved locally for the active student account.'}
              toneClassName="bg-violet-500/12 text-violet-600 dark:text-violet-300"
            />
          </div>

          <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_0.95fr]">
            <WorkspacePanel
              title={copy.filters.title}
              description={locale === 'vi' ? 'Thu hẹp danh sách section trước khi đưa vào kế hoạch.' : 'Narrow the section list before adding anything to the plan.'}
              variant="muted"
              contentClassName="grid gap-4 lg:grid-cols-3"
            >
              <Select
                label={copy.filters.semester}
                value={selectedSemester}
                onChange={(event) => setSelectedSemester(event.target.value)}
                options={[
                  { value: '', label: copy.filters.allSemesters },
                  ...semesters.map((semester) => ({
                    value: semester.id,
                    label: getLocalizedName(locale, semester, semester.name),
                  })),
                ]}
              />
              <Select
                label={copy.filters.department}
                value={selectedDepartment}
                onChange={(event) => setSelectedDepartment(event.target.value)}
                options={[
                  { value: '', label: copy.filters.allDepartments },
                  ...departments.map((department) => ({
                    value: department.id,
                    label: getLocalizedName(locale, department, department.name),
                  })),
                ]}
              />
              <Select
                label={copy.filters.course}
                value={selectedCourse}
                onChange={(event) => setSelectedCourse(event.target.value)}
                disabled={!selectedDepartment}
                options={[
                  {
                    value: '',
                    label: selectedDepartment
                      ? copy.filters.allDepartmentCourses
                      : copy.filters.selectDepartmentFirst,
                  },
                  ...courses.map((course) => ({
                    value: course.id,
                    label: getLocalizedCourseLabel(locale, course, course.code),
                  })),
                ]}
              />
              <div className="lg:col-span-3">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSelectedSemester('');
                    setSelectedDepartment('');
                    setSelectedCourse('');
                  }}
                >
                  {copy.filters.clear}
                </Button>
              </div>
            </WorkspacePanel>

            <WorkspacePanel
              title={copy.readiness.title}
              description={copy.readiness.description}
              variant="muted"
              contentClassName="space-y-4"
            >
              <div className="portal-section-card rounded-md px-4 py-4">
                <div className="flex items-start gap-3">
                  <div
                    className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${readinessCard.tone}`}
                  >
                    <readinessCard.icon className="h-5 w-5" />
                  </div>
                  <div>
                    <div className="font-semibold text-foreground">
                      {readinessCard.title}
                    </div>
                    <p className="mt-1 text-sm leading-6 text-muted-foreground">
                      {readinessCard.description}
                    </p>
                  </div>
                </div>
              </div>
            </WorkspacePanel>
          </div>

          <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_0.95fr]">
            <WorkspacePanel
              title={copy.planner.title}
              description={copy.planner.description}
              contentClassName="space-y-4"
              footer={
                <div className="portal-readonly-note">
                  <ShieldAlert className="h-4 w-4 shrink-0" />
                  <span>{readOnlyCopy}</span>
                </div>
              }
            >
              <div className="grid gap-3 md:grid-cols-3">
                <div className="rounded-lg border border-border/70 bg-secondary/30 px-4 py-4">
                  <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {copy.planner.creditLimit}
                  </div>
                  <div className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                    {formatNumber(creditLimit)}
                  </div>
                </div>
                <div className="rounded-lg border border-border/70 bg-secondary/30 px-4 py-4">
                  <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {copy.planner.projectedCredits}
                  </div>
                  <div className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                    {formatNumber(projectedCredits)}
                  </div>
                </div>
                <div className="rounded-lg border border-border/70 bg-secondary/30 px-4 py-4">
                  <div className="text-xs font-semibold uppercase tracking-[0.16em] text-muted-foreground">
                    {copy.planner.conflictCount}
                  </div>
                  <div className="mt-2 text-2xl font-semibold tracking-tight text-foreground">
                    {formatNumber(conflictSectionIds.size)}
                  </div>
                </div>
              </div>

              {plannedSections.length === 0 ? (
                <EmptyState
                  icon={CalendarClock}
                  title={copy.planner.emptyTitle}
                  description={copy.planner.emptyDescription}
                  className="min-h-[220px] border-none bg-transparent px-0 py-0"
                />
              ) : (
                <div className="space-y-3">
                  {plannedSections.map((section) => {
                    const localizedCourseName = getLocalizedName(
                      locale,
                      section.course,
                      section.course?.name ?? section.course?.code ?? '',
                    );

                    return (
                      <div
                        key={section.id}
                        className="portal-section-card rounded-md px-4 py-4"
                      >
                        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                          <div className="space-y-2">
                            <div className="font-semibold text-foreground">
                              {section.course?.code} - {localizedCourseName}
                            </div>
                            <div className="text-sm text-muted-foreground">
                              {copy.section.prefix} {section.sectionNumber}
                            </div>
                            <div className="flex flex-wrap gap-2">
                              {conflictSectionIds.has(section.id) ? (
                                <span className="rounded-full bg-rose-500/12 px-2.5 py-1 text-xs font-medium text-rose-600 dark:text-rose-300">
                                  {copy.section.scheduleConflict}
                                </span>
                              ) : null}
                              <span className="rounded-full bg-secondary px-2.5 py-1 text-xs font-medium text-foreground">
                                {formatNumber(section.course?.credits ?? 0)} {copy.section.creditsSuffix}
                              </span>
                            </div>
                          </div>
                          <Button
                            type="button"
                            variant="ghost"
                            onClick={() => togglePlannedSection(section.id)}
                          >
                            {copy.planner.remove}
                          </Button>
                        </div>
                      </div>
                    );
                  })}
                </div>
              )}
            </WorkspacePanel>

            <div className="space-y-6">
              {waitlistOnlyView ? (
                <Card variant="elevated" className="border-amber-500/25">
                  <CardContent className="flex items-start gap-4 pt-6">
                    <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-lg bg-amber-500/12 text-amber-600 dark:text-amber-300">
                      <AlertCircle className="h-5 w-5" />
                    </div>
                    <div>
                      <div className="font-semibold text-foreground">
                        {copy.states.waitlistOnlyTitle}
                      </div>
                      <p className="mt-1 text-sm leading-6 text-muted-foreground">
                        {copy.states.waitlistOnlyDescription}
                      </p>
                    </div>
                  </CardContent>
                </Card>
              ) : null}

              <WorkspacePanel
                title={copy.waitlist.title}
                description={copy.waitlist.description}
                contentClassName="space-y-3"
              >
                {waitlistEntries.length === 0 ? (
                  <EmptyState
                    icon={ListOrdered}
                    title={copy.waitlist.emptyTitle}
                    description={copy.waitlist.emptyDescription}
                    className="min-h-[220px] border-none bg-transparent px-0 py-0"
                  />
                ) : (
                  waitlistEntries.map((entry) => {
                    const localizedCourseName = getLocalizedName(
                      locale,
                      entry.section?.course,
                      entry.section?.course?.name ?? entry.section?.course?.code ?? '',
                    );
                    const localizedSemesterName = getLocalizedName(
                      locale,
                      entry.section?.semester,
                      entry.section?.semester?.name ?? selectedSemesterName,
                    );

                    return (
                      <div
                        key={entry.id}
                        className="portal-section-card rounded-md px-4 py-4"
                      >
                        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                          <div className="space-y-2">
                            <div className="font-semibold text-foreground">
                              {entry.section?.course?.code} - {localizedCourseName}
                            </div>
                            <div className="text-sm text-muted-foreground">
                              {copy.section.prefix} {entry.section?.sectionNumber} - {localizedSemesterName}
                            </div>
                            <div className="flex flex-wrap gap-2">
                              <span className="rounded-full bg-amber-500/12 px-2.5 py-1 text-xs font-medium text-amber-700 dark:text-amber-300">
                                {copy.waitlist.position(entry.position)}
                              </span>
                            </div>
                          </div>
                          <span className="max-w-[12rem] text-right text-xs leading-5 text-muted-foreground">
                            {locale === 'vi' ? 'Chỉ đọc trong API Java' : 'Read-only in the Java API'}
                          </span>
                        </div>
                      </div>
                    );
                  })
                )}
              </WorkspacePanel>
            </div>
          </div>

          {!sections.length ? (
            <EmptyState
              icon={BookOpen}
              title={copy.states.emptyCatalogTitle}
              description={copy.states.emptyCatalogDescription}
            />
          ) : filteredSections.length === 0 ? (
            <EmptyState
              icon={BookOpen}
              title={copy.states.filteredEmptyTitle}
              description={copy.states.filteredEmptyDescription}
              action={
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    setSelectedSemester('');
                    setSelectedDepartment('');
                    setSelectedCourse('');
                  }}
                >
                  {copy.filters.clear}
                </Button>
              }
            />
          ) : (
            <section className="portal-section-card overflow-hidden">
              <div className="flex flex-col gap-2 border-b border-border/70 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <h2 className="font-bold text-foreground">
                    {locale === 'vi' ? 'Danh sách lớp học phần' : 'Available sections'}
                  </h2>
                  <p className="mt-1 text-xs text-muted-foreground">
                    {formatNumber(filteredSections.length)} {locale === 'vi' ? 'kết quả theo bộ lọc' : 'results in the current filter'}
                  </p>
                </div>
                <span className="portal-status-badge">
                  {locale === 'vi' ? 'Chỉ đọc' : 'Read-only'}
                </span>
              </div>

              <div className="hidden overflow-x-auto md:block">
                <table className="portal-table w-full min-w-[980px] text-sm">
                  <thead>
                    <tr>
                      <th className="px-3 py-3 text-left">{locale === 'vi' ? 'Môn học' : 'Course'}</th>
                      <th className="px-3 py-3 text-left">{locale === 'vi' ? 'Bộ môn' : 'Department'}</th>
                      <th className="px-3 py-3 text-left">{locale === 'vi' ? 'Lớp' : 'Section'}</th>
                      <th className="px-3 py-3 text-left">{locale === 'vi' ? 'Lịch học' : 'Schedule'}</th>
                      <th className="px-3 py-3 text-center">{locale === 'vi' ? 'Tín chỉ' : 'Credits'}</th>
                      <th className="px-3 py-3 text-center">{locale === 'vi' ? 'Chỗ trống' : 'Seats'}</th>
                      <th className="px-3 py-3 text-right">{locale === 'vi' ? 'Kế hoạch' : 'Plan'}</th>
                    </tr>
                  </thead>
                  <tbody>
                    {filteredSections.map((section) => {
                      const seatsLeft = Math.max(section.capacity - (section.enrolledCount ?? 0), 0);
                      const isEnrolled = enrolledSectionIds.has(section.id);
                      const inPlan = plannedSectionIds.includes(section.id);
                      const localizedCourseName = getLocalizedName(
                        locale,
                        section.course,
                        section.course?.name ?? section.course?.code ?? '',
                      );
                      const localizedDepartmentName = getLocalizedName(
                        locale,
                        section.course?.department,
                        copy.section.departmentFallback,
                      );

                      return (
                        <tr key={section.id}>
                          <td className="px-3 py-3">
                            <div className="font-semibold text-foreground">{section.course?.code}</div>
                            <div className="max-w-[18rem] text-xs text-muted-foreground">{localizedCourseName}</div>
                          </td>
                          <td className="px-3 py-3 text-xs text-muted-foreground">{localizedDepartmentName}</td>
                          <td className="px-3 py-3">
                            <div className="font-medium text-foreground">{section.sectionNumber}</div>
                            <div className="text-xs text-muted-foreground">{section.status}</div>
                          </td>
                          <td className="px-3 py-3 text-xs text-muted-foreground">
                            {section.schedules?.length
                              ? section.schedules.map((schedule) => (
                                  <div key={schedule.id} className="whitespace-nowrap">
                                    {getDayName(schedule.dayOfWeek, locale)} {schedule.startTime}-{schedule.endTime}
                                  </div>
                                ))
                              : '-'}
                          </td>
                          <td className="px-3 py-3 text-center">{formatNumber(section.course?.credits ?? 0)}</td>
                          <td className="px-3 py-3 text-center">
                            <span className={seatsLeft > 0 ? 'text-emerald-600' : 'text-rose-600'}>
                              {formatNumber(seatsLeft)}/{formatNumber(section.capacity)}
                            </span>
                          </td>
                          <td className="px-3 py-3 text-right">
                            <Button
                              type="button"
                              size="sm"
                              variant={inPlan ? 'secondary' : 'outline'}
                              onClick={() => togglePlannedSection(section.id)}
                              disabled={isEnrolled}
                            >
                              {isEnrolled
                                ? copy.section.alreadyEnrolled
                                : inPlan
                                  ? copy.section.removeFromPlan
                                  : copy.section.addToPlan}
                            </Button>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>

              <div className="space-y-3 p-3 md:hidden">
                {filteredSections.map((section) => {
                  const seatsLeft = Math.max(section.capacity - (section.enrolledCount ?? 0), 0);
                  const isEnrolled = enrolledSectionIds.has(section.id);
                  const inPlan = plannedSectionIds.includes(section.id);
                  const localizedCourseName = getLocalizedName(
                    locale,
                    section.course,
                    section.course?.name ?? section.course?.code ?? '',
                  );

                  return (
                    <article key={`${section.id}-mobile`} className="rounded-md border border-border/70 bg-card p-3">
                      <div className="flex items-start justify-between gap-3">
                        <div className="min-w-0">
                          <p className="text-xs font-bold uppercase text-primary">{section.course?.code}</p>
                          <h3 className="mt-1 break-words font-semibold text-foreground">{localizedCourseName}</h3>
                          <p className="mt-1 text-xs text-muted-foreground">
                            {copy.section.prefix} {section.sectionNumber} · {formatNumber(section.course?.credits ?? 0)} {copy.section.creditsSuffix}
                          </p>
                        </div>
                        <span className="portal-status-badge min-w-0">{section.status}</span>
                      </div>
                      <div className="mt-3 flex items-center justify-between gap-3 border-t border-border/60 pt-3">
                        <span className="text-xs text-muted-foreground">
                          {seatsLeft > 0 ? copy.section.seatsLeft(seatsLeft) : copy.section.full}
                        </span>
                        <Button
                          type="button"
                          size="sm"
                          variant={inPlan ? 'secondary' : 'outline'}
                          onClick={() => togglePlannedSection(section.id)}
                          disabled={isEnrolled}
                        >
                          {isEnrolled
                            ? copy.section.alreadyEnrolled
                            : inPlan
                              ? copy.section.removeFromPlan
                              : copy.section.addToPlan}
                        </Button>
                      </div>
                    </article>
                  );
                })}
              </div>
            </section>
          )}
        </>
      )}
    </div>
  );
}
