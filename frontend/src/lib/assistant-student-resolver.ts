import { enrollmentsApi, sectionsApi } from '@/lib/api';
import type { Enrollment } from '@/types/api';
import type { AssistantCitation } from '@/lib/thesis-api';

export interface StudentAssistantResolution {
  answer: string;
  citation: AssistantCitation;
}

const SCHEDULE_REGEX =
  /(lịch\s*(?:học|dạy|giảng\s*dạy|tuần|hôm\s*nay|ngày\s*mai|của\s*tôi|thứ\s*[2-7]|thứ\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|chủ\s*nhật|t[2-7]|cn)?|lich\s*(?:hoc|day|giang\s*day|tuan|hom\s*nay|ngay\s*mai|cua\s*toi|thu\s*[2-7]|thu\s*(?:hai|ba|tu|bon|nam|sau|bay)|chu\s*nhat|t[2-7]|cn)?|thời\s*(?:khoá|khóa|khoa)\s*biểu|thoi\s*khoa\s*bieu|\btkb\b|tiết\s*học|buổi\s*học|ca\s*học|ca\s*dạy|tiết\s*dạy|(?:thứ\s*[2-7]|thứ\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|hôm\s*nay|ngày\s*mai|chủ\s*nhật)\s*(?:tôi\s*)?(?:có\s*)?(?:học|dạy|lịch|tiết|môn|buổi|ca)|\bschedule\b|\btimetable\b|\bclasses\b)/i;
const MATERIALS_REGEX = /(học liệu|tài liệu|giáo trình|\bslide\b|bài giảng|\bmaterials\b|course material|lecture notes)/i;

const DAY_NAMES_VI: Record<number, string> = {
  1: 'Chủ Nhật',
  2: 'Thứ Hai',
  3: 'Thứ Ba',
  4: 'Thứ Tư',
  5: 'Thứ Năm',
  6: 'Thứ Sáu',
  7: 'Thứ Bảy',
};

const DAY_NAMES_EN: Record<number, string> = {
  1: 'Sunday',
  2: 'Monday',
  3: 'Tuesday',
  4: 'Wednesday',
  5: 'Thursday',
  6: 'Friday',
  7: 'Saturday',
};

function detectRequestedDay(message: string): number | null {
  const lower = message.toLowerCase();

  if (lower.includes('hôm nay') || lower.includes('hom nay') || lower.includes('today')) {
    const jsDay = new Date().getDay();
    return jsDay === 0 ? 1 : jsDay + 1;
  }
  if (lower.includes('ngày mai') || lower.includes('ngay mai') || lower.includes('tomorrow')) {
    const jsDay = (new Date().getDay() + 1) % 7;
    return jsDay === 0 ? 1 : jsDay + 1;
  }

  if (/thứ\s*(?:hai|2)|\bt2\b|monday/i.test(lower)) return 2;
  if (/thứ\s*(?:ba|3)|\bt3\b|tuesday/i.test(lower)) return 3;
  if (/thứ\s*(?:tư|bốn|4)|\bt4\b|wednesday/i.test(lower)) return 4;
  if (/thứ\s*(?:năm|5)|\bt5\b|thursday/i.test(lower)) return 5;
  if (/thứ\s*(?:sáu|6)|\bt6\b|friday/i.test(lower)) return 6;
  if (/thứ\s*(?:bảy|7)|\bt7\b|saturday/i.test(lower)) return 7;
  if (/chủ\s*nhật|chu\s*nhat|\bcn\b|sunday/i.test(lower)) return 1;

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
  isTeaching?: boolean;
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
        const normalizedDay = rawDay === 0 ? 1 : rawDay;
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

function extractLecturerMeetings(sections: any[], locale: 'vi' | 'en'): ScheduleMeeting[] {
  const meetings: ScheduleMeeting[] = [];
  for (const sec of sections) {
    const courseCode = sec.courseCode ?? 'MH';
    const courseName =
      (locale === 'vi' ? sec.courseNameVi : sec.courseNameEn) ?? sec.courseName ?? courseCode;
    for (const sch of sec.schedules ?? []) {
      const rawDay = sch.dayOfWeek;
      const normalizedDay = rawDay === 0 ? 1 : rawDay;
      const room = sch.roomNumber
        ? sch.building
          ? `${sch.building}-${sch.roomNumber}`
          : sch.roomNumber
        : undefined;
      meetings.push({
        courseCode,
        courseName,
        sectionNumber: sec.sectionNumber,
        dayOfWeek: normalizedDay,
        startTime: sch.startTime ?? '',
        endTime: sch.endTime ?? '',
        room,
        isTeaching: true,
      });
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
    let meetings: ScheduleMeeting[] = [];
    let isLecturer = false;

    try {
      const enrollments = await enrollmentsApi.getMyEnrollments();
      if (enrollments && enrollments.length > 0) {
        meetings = extractMeetings(enrollments, locale);
      }
    } catch {
      // If student enrollment fetch fails, could be lecturer
    }

    if (meetings.length === 0) {
      try {
        const teachingSections = await sectionsApi.getMySchedule();
        if (teachingSections && teachingSections.length > 0) {
          meetings = extractLecturerMeetings(teachingSections, locale);
          isLecturer = true;
        }
      } catch {
        // Not a lecturer or unauthenticated
      }
    }

    const requestedDay = detectRequestedDay(message);

    if (requestedDay !== null) {
      const dayName = locale === 'vi' ? DAY_NAMES_VI[requestedDay] : DAY_NAMES_EN[requestedDay];
      const dayMeetings = meetings.filter((m) => m.dayOfWeek === requestedDay);

      if (dayMeetings.length > 0) {
        const lines = dayMeetings.map((m) => {
          const roomPart = m.room ? ` - Phòng: ${m.room}` : '';
          const lecturerPart = m.lecturerName ? ` (GV: ${m.lecturerName})` : '';
          const prefix = isLecturer ? 'Ca dạy' : 'Giờ học';
          return `• **${m.courseCode} - ${m.courseName}** (Lớp ${m.sectionNumber})\n  - ${prefix}: ${m.startTime} - ${m.endTime}${roomPart}${lecturerPart}`;
        });

        const targetUrl = isLecturer ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
        const targetLabel = isLecturer
          ? (locale === 'vi' ? 'Lịch giảng dạy' : 'Teaching Schedule')
          : (locale === 'vi' ? 'Thời khóa biểu' : 'Schedule');

        const answer =
          locale === 'vi'
            ? `${isLecturer ? 'Lịch giảng dạy' : 'Lịch học'} **${dayName}** của bạn gồm có:\n\n` +
              lines.join('\n\n') +
              `\n\n💡 Bạn có thể xem toàn bộ lịch trực quan theo tuần tại mục **${targetLabel}** (${targetUrl}).`
            : `Here is your **${dayName}** ${isLecturer ? 'teaching schedule' : 'schedule'}:\n\n` +
              lines.join('\n\n') +
              `\n\n💡 You can view your full visual weekly timetable under **${targetLabel}** (${targetUrl}).`;

        return {
          answer,
          citation: {
            id: 'personal-schedule-guide',
            slug: 'schedule-overview',
            title: locale === 'vi' ? `${isLecturer ? 'Lịch dạy' : 'Lịch học'} ${dayName}` : `${dayName} Schedule`,
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? `Thời khóa biểu cá nhân được tự động tổng hợp từ dữ liệu học vụ.`
                : 'Personal timetable compiled from academic records.',
            domain: 'ACADEMIC_CATALOG',
          },
        };
      } else {
        const targetUrl = isLecturer ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
        const targetLabel = isLecturer
          ? (locale === 'vi' ? 'Lịch giảng dạy' : 'Teaching Schedule')
          : (locale === 'vi' ? 'Thời khóa biểu' : 'Schedule');

        const answer =
          locale === 'vi'
            ? `Theo lịch hiện tại, bạn **không có ${isLecturer ? 'ca giảng dạy nào' : 'lịch học'}** vào **${dayName}**.\n\n` +
              `💡 Để xem lịch các ngày khác trong tuần, bạn hãy truy cập mục **${targetLabel}** (${targetUrl}).`
            : `According to your current schedule, you have **no ${isLecturer ? 'teaching sessions' : 'scheduled classes'}** on **${dayName}**.\n\n` +
              `💡 To check your schedule for other days, visit **${targetLabel}** (${targetUrl}).`;

        return {
          answer,
          citation: {
            id: 'personal-schedule-guide',
            slug: 'schedule-overview',
            title: locale === 'vi' ? `Lịch ${dayName}` : `${dayName} Schedule`,
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? `Không có lịch hoạt động vào ${dayName}.`
                : `No activities scheduled on ${dayName}.`,
            domain: 'ACADEMIC_CATALOG',
          },
        };
      }
    }

    // General schedule question (e.g. "thời khóa biểu của tôi", "xem lịch học ở đâu")
    if (meetings.length > 0) {
      const dayGroups = [2, 3, 4, 5, 6, 7, 1]
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

      const targetUrl = isLecturer ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
      const targetLabel = isLecturer
        ? (locale === 'vi' ? 'Lịch giảng dạy' : 'Teaching Schedule')
        : (locale === 'vi' ? 'Thời khóa biểu' : 'Schedule');

      const answer =
        locale === 'vi'
          ? `${isLecturer ? 'Lịch giảng dạy' : 'Thời khóa biểu'} tổng quan các ngày trong tuần của bạn:\n\n` +
            summaryLines.join('\n') +
            `\n\n💡 Bạn có thể xem chi tiết phòng học, giảng viên và thời khóa biểu trực quan tại trang **${targetLabel}** (${targetUrl}).`
          : `Summary of your weekly ${isLecturer ? 'teaching' : 'class'} schedule:\n\n` +
            summaryLines.join('\n') +
            `\n\n💡 View your visual weekly timetable and classrooms under **${targetLabel}** (${targetUrl}).`;

      return {
        answer,
        citation: {
          id: 'personal-schedule-guide',
          slug: 'schedule-overview',
          title: locale === 'vi' ? `${isLecturer ? 'Lịch giảng dạy' : 'Thời khóa biểu'}` : 'Schedule',
          source: 'academic-catalog',
          locale,
          excerpt:
            locale === 'vi'
              ? 'Thời khóa biểu hiển thị đầy đủ các ngày trong tuần từ Thứ Hai đến Chủ Nhật.'
              : 'Schedule displays weekly meetings Monday through Sunday.',
          domain: 'ACADEMIC_CATALOG',
        },
      };
    } else {
      const targetUrl = isLecturer ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
      const targetLabel = isLecturer
        ? (locale === 'vi' ? 'Lịch giảng dạy' : 'Teaching Schedule')
        : (locale === 'vi' ? 'Thời khóa biểu' : 'Schedule');

      const answer =
        locale === 'vi'
          ? (isLecturer
              ? 'Hiện tại bạn chưa có ca giảng dạy nào được xếp lịch trong học kỳ này.\n\n' +
                `• Khi có phân công chính thức, lịch dạy sẽ tự động hiển thị tại mục **${targetLabel}** (${targetUrl}).`
              : 'Hiện tại bạn chưa có môn học nào trong thời khóa biểu học kỳ này.\n\n' +
                '• Nếu đang trong đợt đăng ký học phần, bạn hãy vào mục **Đăng ký học phần** (/dashboard/register) để chọn và đăng ký các lớp học phần.\n' +
                `• Sau khi đăng ký thành công, thời khóa biểu sẽ tự động cập nhật tại mục **${targetLabel}** (${targetUrl}).`)
          : (isLecturer
              ? 'You currently have no teaching assignments scheduled for this semester.\n\n' +
                `• Teaching slots will appear automatically under **${targetLabel}** (${targetUrl}) once assigned.`
              : 'You currently have no scheduled classes for this semester.\n\n' +
                '• If registration is open, register for classes under **Course Registration** (/dashboard/register).\n' +
                `• Enrolled classes will automatically appear on your visual timetable under **${targetLabel}** (${targetUrl}).`);

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
              ? 'Dữ liệu thời khóa biểu cá nhân được cập nhật tự động từ hệ thống.'
              : 'Personal schedule is updated automatically from academic records.',
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
