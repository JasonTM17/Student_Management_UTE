import { enrollmentsApi } from '@/lib/api';
import type { Enrollment } from '@/types/api';
import type { AssistantCitation } from '@/lib/thesis-api';

export interface StudentAssistantResolution {
  answer: string;
  citation: AssistantCitation;
}

const SCHEDULE_REGEX = /(lịch học|thời khóa biểu|\btkb\b|tiết học|buổi học|\bschedule\b|\btimetable\b|\bclasses\b)/i;
const MATERIALS_REGEX = /(học liệu|tài liệu|giáo trình|\bslide\b|bài giảng|\bmaterials\b|course material|lecture notes)/i;

const DAY_NAMES_VI: Record<number, string> = {
  1: 'Thứ Hai',
  2: 'Thứ Ba',
  3: 'Thứ Tư',
  4: 'Thứ Năm',
  5: 'Thứ Sáu',
  6: 'Thứ Bảy',
  7: 'Chủ Nhật',
};

const DAY_NAMES_EN: Record<number, string> = {
  1: 'Monday',
  2: 'Tuesday',
  3: 'Wednesday',
  4: 'Thursday',
  5: 'Friday',
  6: 'Saturday',
  7: 'Sunday',
};

function detectRequestedDay(message: string): number | null {
  const lower = message.toLowerCase();

  if (lower.includes('hôm nay') || lower.includes('today')) {
    const jsDay = new Date().getDay();
    return jsDay === 0 ? 7 : jsDay;
  }
  if (lower.includes('ngày mai') || lower.includes('tomorrow')) {
    const jsDay = (new Date().getDay() + 1) % 7;
    return jsDay === 0 ? 7 : jsDay;
  }

  if (/thứ\s*(?:hai|2)|\bt2\b|monday/i.test(lower)) return 1;
  if (/thứ\s*(?:ba|3)|\bt3\b|tuesday/i.test(lower)) return 2;
  if (/thứ\s*(?:tư|bốn|4)|\bt4\b|wednesday/i.test(lower)) return 3;
  if (/thứ\s*(?:năm|5)|\bt5\b|thursday/i.test(lower)) return 4;
  if (/thứ\s*(?:sáu|6)|\bt6\b|friday/i.test(lower)) return 5;
  if (/thứ\s*(?:bảy|7)|\bt7\b|saturday/i.test(lower)) return 6;
  if (/chủ\s*nhật|\bcn\b|sunday/i.test(lower)) return 7;

  return null;
}

export function isStudentAssistantQuery(message: string): boolean {
  return SCHEDULE_REGEX.test(message) || MATERIALS_REGEX.test(message);
}

interface ScheduleMeeting {
  courseCode: string;
  courseName: string;
  sectionNumber: string;
  dayOfWeek: number;
  startTime: string;
  endTime: string;
  room?: string;
  lecturerName?: string;
}

function extractMeetings(enrollments: Enrollment[], locale: 'vi' | 'en'): ScheduleMeeting[] {
  const meetings: ScheduleMeeting[] = [];

  const active = enrollments.filter(
    (e) => e.status === 'ENROLLED' || e.status === 'CONFIRMED' || e.status === 'PENDING',
  );

  for (const enrollment of active) {
    const sec = enrollment.section;
    if (!sec) continue;

    const courseCode = sec.course?.code ?? 'MH';
    const courseName =
      (locale === 'vi' ? sec.course?.nameVi : sec.course?.nameEn) ??
      sec.course?.name ??
      courseCode;
    const sectionNumber = sec.sectionNumber;
    const lecturerName =
      (sec.lecturer as { fullName?: string } | undefined)?.fullName ??
      (sec.lecturer?.user
        ? `${sec.lecturer.user.firstName} ${sec.lecturer.user.lastName}`.trim()
        : undefined);

    if (sec.schedules && sec.schedules.length > 0) {
      for (const sch of sec.schedules) {
        const rawDay = sch.dayOfWeek;
        const normalizedDay = rawDay === 0 ? 7 : rawDay;
        const room =
          sch.classroom?.roomNumber ??
          (sch.classroom?.building ? `${sch.classroom.building}-${sch.classroom.roomNumber ?? ''}` : undefined) ??
          sec.classroom?.roomNumber;

        meetings.push({
          courseCode,
          courseName,
          sectionNumber,
          dayOfWeek: normalizedDay,
          startTime: sch.startTime ?? '',
          endTime: sch.endTime ?? '',
          room,
          lecturerName,
        });
      }
    }
  }

  return meetings.sort((a, b) => {
    if (a.dayOfWeek !== b.dayOfWeek) return a.dayOfWeek - b.dayOfWeek;
    return a.startTime.localeCompare(b.startTime);
  });
}

export async function resolveStudentAssistantQuery(
  message: string,
  locale: 'vi' | 'en',
): Promise<StudentAssistantResolution | null> {
  const isSchedule = SCHEDULE_REGEX.test(message);
  const isMaterials = MATERIALS_REGEX.test(message);

  if (!isSchedule && !isMaterials) {
    return null;
  }

  // Handle Learning Materials queries
  if (isMaterials && !isSchedule) {
    if (locale === 'vi') {
      return {
        answer:
          'Học liệu và tài liệu học tập của các môn học được quản lý như sau:\n\n' +
          '• **Giáo trình & Slide bài giảng:** Giảng viên phụ trách từng lớp học phần sẽ cung cấp học liệu trực tiếp cho sinh viên trong lớp qua trang học phần hoặc mục thông báo của lớp.\n' +
          '• **Danh sách lớp học phần đang học:** Bạn có thể kiểm tra danh sách học phần đã đăng ký tại mục **Đăng ký học phần** (/dashboard/enrollments).\n' +
          '• **Thông báo & Tài liệu chung:** Thường xuyên theo dõi mục **Thông báo** (/dashboard/announcements) để nhận các tài liệu, hướng dẫn và biểu mẫu học vụ mới nhất từ Khoa và Nhà trường.',
        citation: {
          id: 'academic-materials-guide-vi',
          slug: 'materials-guide-vi',
          title: 'Hướng dẫn học liệu & tài liệu học phần',
          source: 'academic-catalog',
          locale: 'vi',
          excerpt:
            'Học liệu và bài giảng do giảng viên từng học phần cung cấp qua hệ thống lớp học phần và trang thông báo học vụ.',
          domain: 'ACADEMIC_CATALOG',
        },
      };
    } else {
      return {
        answer:
          'Course materials and academic resources are organized as follows:\n\n' +
          '• **Syllabus & Lecture Slides:** Course instructors upload lectures, slides, and reading materials directly for their enrolled classes.\n' +
          '• **Your Enrolled Classes:** Review your active courses and classes under **Course Registration** (/dashboard/enrollments).\n' +
          '• **Announcements & Attachments:** Check **Announcements** (/dashboard/announcements) regularly for official resources and guidelines from the department.',
        citation: {
          id: 'academic-materials-guide-en',
          slug: 'materials-guide-en',
          title: 'Course Materials & Academic Resources',
          source: 'academic-catalog',
          locale: 'en',
          excerpt:
            'Course materials and lecture notes are provided directly by instructors through section announcements and the student portal.',
          domain: 'ACADEMIC_CATALOG',
        },
      };
    }
  }

  // Handle Schedule / Timetable queries
  try {
    const enrollments = await enrollmentsApi.getMyEnrollments();
    const meetings = extractMeetings(enrollments, locale);
    const requestedDay = detectRequestedDay(message);

    if (requestedDay !== null) {
      const dayName = locale === 'vi' ? DAY_NAMES_VI[requestedDay] : DAY_NAMES_EN[requestedDay];
      const dayMeetings = meetings.filter((m) => m.dayOfWeek === requestedDay);

      if (dayMeetings.length > 0) {
        const lines = dayMeetings.map((m) => {
          const roomPart = m.room ? ` - Phòng: ${m.room}` : '';
          const lecturerPart = m.lecturerName ? ` (GV: ${m.lecturerName})` : '';
          return `• **${m.courseCode} - ${m.courseName}** (Lớp ${m.sectionNumber})\n  - Giờ học: ${m.startTime} - ${m.endTime}${roomPart}${lecturerPart}`;
        });

        const answer =
          locale === 'vi'
            ? `Lịch học **${dayName}** của bạn trong học kỳ hiện tại gồm có:\n\n` +
              lines.join('\n\n') +
              '\n\n💡 Bạn có thể xem toàn bộ thời khóa biểu trực quan theo tuần tại mục **Thời khóa biểu** (/dashboard/schedule).'
            : `Here is your **${dayName}** schedule for the current semester:\n\n` +
              lines.join('\n\n') +
              '\n\n💡 You can view your full visual weekly timetable under **Schedule** (/dashboard/schedule).';

        return {
          answer,
          citation: {
            id: 'personal-schedule-guide',
            slug: 'schedule-overview',
            title: locale === 'vi' ? `Lịch học ${dayName} của sinh viên` : `Student ${dayName} Schedule`,
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? `Thời khóa biểu cá nhân được tự động tổng hợp từ các lớp học phần đăng ký thành công.`
                : 'Personal schedule automatically compiled from active enrolled class sections.',
            domain: 'ACADEMIC_CATALOG',
          },
        };
      } else {
        const answer =
          locale === 'vi'
            ? `Theo thời khóa biểu hiện tại, bạn **không có lịch học** vào **${dayName}**.\n\n` +
              '💡 Để xem lịch học các ngày khác trong tuần hoặc kiểm tra danh sách lớp học phần đã đăng ký, bạn hãy truy cập mục **Thời khóa biểu** (/dashboard/schedule).'
            : `According to your current timetable, you have **no scheduled classes** on **${dayName}**.\n\n` +
              '💡 To check your schedule for other days, visit **Schedule** (/dashboard/schedule).';

        return {
          answer,
          citation: {
            id: 'personal-schedule-guide',
            slug: 'schedule-overview',
            title: locale === 'vi' ? `Thời khóa biểu ${dayName}` : `${dayName} Schedule`,
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? `Không có lớp học phần nào được xếp lịch vào ${dayName}.`
                : `No classes scheduled on ${dayName}.`,
            domain: 'ACADEMIC_CATALOG',
          },
        };
      }
    }

    // General schedule question (e.g. "thời khóa biểu của tôi", "xem lịch học ở đâu")
    if (meetings.length > 0) {
      const dayGroups = [1, 2, 3, 4, 5, 6, 7]
        .map((d) => ({
          day: d,
          dayName: locale === 'vi' ? DAY_NAMES_VI[d] : DAY_NAMES_EN[d],
          items: meetings.filter((m) => m.dayOfWeek === d),
        }))
        .filter((g) => g.items.length > 0);

      const summaryLines = dayGroups.map((g) => {
        const courseNames = g.items.map((it) => `${it.courseCode} (${it.startTime}-${it.endTime})`).join(', ');
        return `• **${g.dayName}:** ${courseNames}`;
      });

      const answer =
        locale === 'vi'
          ? 'Thời khóa biểu tổng quan các ngày học trong tuần của bạn:\n\n' +
            summaryLines.join('\n') +
            '\n\n💡 Bạn có thể xem chi tiết phòng học, giảng viên và thời khóa biểu trực quan tại trang **Thời khóa biểu** (/dashboard/schedule).'
          : 'Summary of your weekly class schedule:\n\n' +
            summaryLines.join('\n') +
            '\n\n💡 View your visual weekly timetable and classrooms under **Schedule** (/dashboard/schedule).';

      return {
        answer,
        citation: {
          id: 'personal-schedule-guide',
          slug: 'schedule-overview',
          title: locale === 'vi' ? 'Thời khóa biểu sinh viên' : 'Student Schedule',
          source: 'academic-catalog',
          locale,
          excerpt:
            locale === 'vi'
              ? 'Thời khóa biểu sinh viên hiển thị đầy đủ các ngày trong tuần từ Thứ Hai đến Chủ Nhật tại /dashboard/schedule.'
              : 'Student schedule displays weekly meetings Monday through Sunday at /dashboard/schedule.',
          domain: 'ACADEMIC_CATALOG',
        },
      };
    } else {
      const answer =
        locale === 'vi'
          ? 'Hiện tại bạn chưa có môn học nào trong thời khóa biểu học kỳ này.\n\n' +
            '• Nếu đang trong đợt đăng ký học phần, bạn hãy vào mục **Đăng ký học phần** (/dashboard/register) để chọn và đăng ký các lớp học phần.\n' +
            '• Sau khi đăng ký thành công, thời khóa biểu sẽ tự động cập nhật tại mục **Thời khóa biểu** (/dashboard/schedule).'
          : 'You currently have no scheduled classes for this semester.\n\n' +
            '• If registration is open, register for classes under **Course Registration** (/dashboard/register).\n' +
            '• Enrolled classes will automatically appear on your visual timetable under **Schedule** (/dashboard/schedule).';

      return {
        answer,
        citation: {
          id: 'personal-schedule-guide',
          slug: 'schedule-overview',
          title: locale === 'vi' ? 'Hướng dẫn thời khóa biểu' : 'Schedule Guide',
          source: 'academic-catalog',
          locale,
          excerpt:
            locale === 'vi'
              ? 'Sinh viên đăng ký lớp học phần để tự động tạo thời khóa biểu.'
              : 'Students enroll in section classes to generate their personal schedule.',
          domain: 'ACADEMIC_CATALOG',
        },
      };
    }
  } catch {
    // If fetching enrollments fails (e.g. network/session), provide a clear guiding answer
    const answer =
      locale === 'vi'
        ? 'Để xem lịch học và thời khóa biểu cá nhân của bạn (bao gồm lịch học từng thứ, phòng học, giảng viên và ca học), bạn vui lòng vào mục **Thời khóa biểu** (/dashboard/schedule) trên thanh điều hướng.\n\n' +
          'Hệ thống hiển thị thời khóa biểu dạng lưới tuần trực quan và danh sách các buổi học sắp diễn ra.'
        : 'To view your personal schedule and class timetable, please visit **Schedule** (/dashboard/schedule) from the navigation menu.';

    return {
      answer,
      citation: {
        id: 'personal-schedule-guide',
        slug: 'schedule-overview',
        title: locale === 'vi' ? 'Hướng dẫn xem thời khóa biểu' : 'Schedule Guide',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Xem thời khóa biểu cá nhân trực quan tại /dashboard/schedule.'
            : 'View your visual schedule at /dashboard/schedule.',
        domain: 'ACADEMIC_CATALOG',
      },
    };
  }
}
