import {
  announcementsApi,
  authApi,
  conductApi,
  type AnnouncementRecord,
  curriculumApi,
  enrollmentsApi,
  gradesApi,
  registrationApi,
  sectionsApi,
} from '@/lib/api';
import { thesisApi } from '@/lib/thesis-api';
import type { Enrollment } from '@/types/api';
import type { AssistantCitation } from '@/lib/thesis-api';

export interface StudentAssistantResolution {
  answer: string;
  citation: AssistantCitation;
}

// 1. SCHEDULE & TIMETABLE REGEX (both accented and unaccented)
const SCHEDULE_REGEX =
  /(?:lịch|lich)\s*(?:học|hoc|dạy|day|giảng\s*dạy|giang\s*day|tuần|tuan|hôm\s*nay|hom\s*nay|ngày\s*mai|ngay\s*mai|của\s*tôi|cua\s*toi|thứ\s*[2-7]|thu\s*[2-7]|thứ\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|thu\s*(?:hai|ba|tu|bon|nam|sau|bay)|chủ\s*nhật|chu\s*nhat|t[2-7]|cn)?|thời\s*(?:khoá|khóa|khoa)\s*biểu|thoi\s*khoa\s*bieu|\btkb\b|tiết\s*học|tiet\s*hoc|buổi\s*học|buoi\s*hoc|ca\s*học|ca\s*hoc|ca\s*dạy|ca\s*day|tiết\s*dạy|tiet\s*day|(?:thứ\s*[2-7]|thu\s*[2-7]|thứ\s*(?:hai|ba|tư|bốn|năm|sáu|bảy)|thu\s*(?:hai|ba|tu|bon|nam|sau|bay)|hôm\s*nay|hom\s*nay|ngày\s*mai|ngay\s*mai|chủ\s*nhật|chu\s*nhat)\s*(?:tôi\s*)?(?:có\s*)?(?:học|hoc|dạy|day|lịch|lich|tiết|tiet|môn|mon|buổi|buoi|ca|ở\s*đâu|o\s*dau|mấy\s*giờ|may\s*gio|khi\s*nào|khi\s*nao)|\bschedule\b|\btimetable\b|\bclasses\b/i;

// 2. MATERIALS & COURSEWARE REGEX
const MATERIALS_REGEX =
  /(học\s*liệu|hoc\s*lieu|tài\s*liệu|tai\s*lieu|giáo\s*trình|giao\s*trinh|\bslide\b|bài\s*giảng|bai\s*giang|\bmaterials\b|course\s*material|lecture\s*notes)/i;

// 3. CONDUCT / TRAINING POINTS REGEX (ĐRL)
const CONDUCT_REGEX =
  /(?:điểm\s*)?(?:rèn\s*luyện|ren\s*luyen)|\bđrl\b|\bdrl\b/i;

// 4. GRADES, GPA & TRANSCRIPT REGEX
const GRADES_REGEX =
  /(?:điểm|diem)\s*(?:số|so|thi|chữ|chu|học\s*phần|hoc\s*phan|môn|mon|của\s*tôi|cua\s*toi|tổng\s*kết|tong\s*ket)?|(?:kết\s*quả\s*học\s*tập|ket\s*qua\s*hoc\s*tap)|\bgpa\b|(?:bảng\s*điểm|bang\s*diem)|học\s*lực|hoc\s*luc|tín\s*chỉ\s*tích\s*lũy|tin\s*chi\s*tich\s*luy|\btranscript\b|\bgrades?\b|\bgrade\s*point\b/i;

// 5. ANNOUNCEMENTS REGEX
const ANNOUNCEMENT_REGEX =
  /(?:thông\s*báo|thong\s*bao|tin\s*tức|tin\s*tuc|bản\s*tin|ban\s*tin|\bannouncements?\b|\bnews\b)/i;

// 4. TUITION & FINANCIAL REGEX
const TUITION_REGEX =
  /(?:học\s*phí|hoc\s*phi|tiền\s*học|tien\s*hoc|công\s*nợ|cong\s*no|nợ\s*học\s*phí|no\s*hoc\s*phi|đóng\s*học\s*phí|dong\s*hoc\s*phi|nộp\s*học\s*phí|nop\s*hoc\s*phi|hạn\s*(?:đóng|nộp)\s*học\s*phí|han\s*(?:dong|nop)\s*hoc\s*phi|\btuition\b|\btuition\s*fee\b)/i;

// 5. THESIS & GRADUATION CAPSTONE REGEX
const THESIS_REGEX =
  /(?:đồ\s*án|do\s*an|khóa\s*luận|khoa\s*luan|tốt\s*nghiệp|tot\s*nghiep|bảo\s*vệ\s*đồ\s*án|bao\s*ve\s*do\s*an|hướng\s*dẫn\s*đồ\s*án|huong\s*dan\s*do\s*an|giảng\s*viên\s*hướng\s*dẫn|giang\s*vien\s*huong\s*dan|\bthesis\b|\bcapstone\b|\bgraduation\s*project\b)/i;

// 6. CURRICULUM & DEGREE PROGRESS REGEX
const CURRICULUM_REGEX =
  /(?:chương\s*trình\s*đào\s*tạo|chuong\s*trinh\s*dao\s*tao|khung\s*đào\s*tạo|khung\s*dao\s*tao|tiến\s*độ\s*(?:học\s*tập|đào\s*tạo)|tien\s*do\s*(?:hoc\s*tap|dao\s*tao)|(?:còn\s*nợ|tích\s*lũy|cần|phải\s*học)\s*bao\s*nhiêu\s*tín\s*chỉ|bao\s*nhiêu\s*tín\s*chỉ\s*(?:để\s*)?(?:ra\s*trường|tốt\s*nghiệp)|\bcurriculum\b|\bdegree\s*progress\b)/i;

// 7. STUDENT / LECTURER PROFILE & MSSV REGEX
const PROFILE_REGEX =
  /(?:thông\s*tin\s*(?:cá\s*nhân|sinh\s*viên|giảng\s*viên|tài\s*khoản|của\s*tôi)|thong\s*tin\s*(?:ca\s*nhan|sinh\s*vien|giang\s*vien|tai\s*khoan|cua\s*toi)|mã\s*số\s*(?:sinh\s*viên|của\s*tôi)|ma\s*so\s*(?:sinh\s*vien|cua\s*toi)|\bmssv\b|email\s*(?:sinh\s*viên|của\s*tôi)|lớp\s*sinh\s*hoạt|khoa\s*của\s*tôi|\bprofile\b|\bstudent\s*id\b)/i;

// 8. COURSE REGISTRATION & ELIGIBILITY REGEX
const REGISTRATION_REGEX =
  /(?:được\s*)?(?:đăng\s*ký|dang\s*ky)\s*(?:tối\s*đa|toi\s*da)?\s*(?:bao\s*nhiêu|bao\s*nhieu)?\s*tín\s*chỉ|tín\s*chỉ\s*tối\s*đa|tin\s*chi\s*toi\s*da|hạn\s*mức\s*tín\s*chỉ|han\s*muc\s*tin\s*chi|(?:đăng\s*ký\s*học\s*phần|dang\s*ky\s*hoc\s*phan|đăng\s*ký\s*môn|dang\s*ky\s*mon|đợt\s*đăng\s*ký|dot\s*dang\s*ky|hạn\s*đăng\s*ký|han\s*dang\s*ky|được\s*đăng\s*ký\s*không|28\s*tín\s*chỉ|\bcourse\s*registration\b|\benrollment\s*window\b)/i;

// 9. LECTURER TEACHING SECTIONS REGEX
const TEACHING_REGEX =
  /(?:lớp|lop)\s*(?:học\s*phần|hoc\s*phan)?\s*(?:mà\s*)?(?:tôi|toi)?\s*(?:đang|dang)?\s*(?:phụ\s*trách|phu\s*trach|giảng\s*dạy|giang\s*day|dạy|day)|danh\s*sách\s*lớp\s*(?:giảng\s*dạy|phụ\s*trách|học\s*phần)?|danh\s*sach\s*lop\s*(?:giang\s*day|phu\s*trach|hoc\s*phan)?|ca\s*(?:dạy|day)\s*(?:của\s*tôi|cua\s*toi)?|lịch\s*(?:giảng\s*dạy|giang\s*day|dạy|day)|\bteaching\s*sections?\b|\bteaching\s*schedule\b|\bteaching\s*classes\b/i;

// 10. GRADUATION REQUIREMENTS REGEX
const GRADUATION_REQUIREMENTS_REGEX =
  /(?:điều\s*kiện|dieu\s*kien)\s*(?:xét\s*)?(?:tốt\s*nghiệp|tot\s*nghiep|ra\s*trường|ra\s*truong)|chuẩn\s*đầu\s*ra|chuan\s*dau\s*ra|yêu\s*cầu\s*tốt\s*nghiệp|yeu\s*cau\s*tot\s*nghiep/i;

// 11. RETAKE & GRADE IMPROVEMENT POLICY REGEX
const RETAKE_POLICY_REGEX =
  /(?:học\s*lại|hoc\s*lai|cải\s*thiện\s*điểm|cai\s*thien\s*diem|học\s*cải\s*thiện|hoc\s*cai\s*thien|rớt\s*môn|rot\s*mon|thi\s*lại|thi\s*lai|cảnh\s*báo\s*học\s*vụ|canh\s*bao\s*hoc\s*vu)/i;

// 12. SCHOLARSHIP REGEX
const SCHOLARSHIP_REGEX =
  /(?:học\s*bổng|hoc\s*bong|\bscholarships?\b)/i;

// 13. EXAM SCHEDULE REGEX
const EXAM_REGEX =
  /(?:lịch\s*thi|lich\s*thi|ngày\s*thi|ngay\s*thi|thi\s*kết\s*thúc|thi\s*ket\s*thuc|\bexam\b|\bexam\s*schedule\b)/i;

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

export function detectRequestedDay(message: string): number | null {
  const lower = message.toLowerCase();

  // Today / Tomorrow
  if (lower.includes('hôm nay') || lower.includes('hom nay') || lower.includes('today')) {
    const jsDay = new Date().getDay();
    return jsDay === 0 ? 1 : jsDay + 1;
  }
  if (lower.includes('ngày mai') || lower.includes('ngay mai') || lower.includes('tomorrow')) {
    const jsDay = (new Date().getDay() + 1) % 7;
    return jsDay === 0 ? 1 : jsDay + 1;
  }

  // Thứ 2 / Monday
  if (/(?:thứ|thu)\s*(?:hai|2)\b|\bt2\b|\bmonday\b/i.test(lower)) return 2;
  // Thứ 3 / Tuesday
  if (/(?:thứ|thu)\s*(?:ba|3)\b|\bt3\b|\btuesday\b/i.test(lower)) return 3;
  // Thứ 4 / Wednesday
  if (/(?:thứ|thu)\s*(?:tư|tu|bốn|bon|4)\b|\bt4\b|\bwednesday\b/i.test(lower)) return 4;
  // Thứ 5 / Thursday
  if (/(?:thứ|thu)\s*(?:năm|nam|5)\b|\bt5\b|\bthursday\b/i.test(lower)) return 5;
  // Thứ 6 / Friday
  if (/(?:thứ|thu)\s*(?:sáu|sau|6)\b|\bt6\b|\bfriday\b/i.test(lower)) return 6;
  // Thứ 7 / Saturday
  if (/(?:thứ|thu)\s*(?:bảy|bay|7)\b|\bt7\b|\bsaturday\b/i.test(lower)) return 7;
  // Chủ nhật / Sunday
  if (/chủ\s*nhật|chu\s*nhat|\bcn\b|\bsunday\b/i.test(lower)) return 1;

  return null;
}

export function isStudentAssistantQuery(message: string): boolean {
  return (
    SCHEDULE_REGEX.test(message) ||
    MATERIALS_REGEX.test(message) ||
    CONDUCT_REGEX.test(message) ||
    GRADES_REGEX.test(message) ||
    ANNOUNCEMENT_REGEX.test(message) ||
    TUITION_REGEX.test(message) ||
    THESIS_REGEX.test(message) ||
    CURRICULUM_REGEX.test(message) ||
    PROFILE_REGEX.test(message) ||
    REGISTRATION_REGEX.test(message) ||
    TEACHING_REGEX.test(message) ||
    GRADUATION_REQUIREMENTS_REGEX.test(message) ||
    RETAKE_POLICY_REGEX.test(message) ||
    SCHOLARSHIP_REGEX.test(message) ||
    EXAM_REGEX.test(message)
  );
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

const ACTIVE_ENROLLMENT_STATUSES = new Set(['ENROLLED', 'CONFIRMED', 'PENDING']);

function isActiveEnrollment(enrollment: Enrollment): boolean {
  return ACTIVE_ENROLLMENT_STATUSES.has(enrollment.status);
}

function stripRichText(content: string | null | undefined): string {
  return (content ?? '').replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
}

function sectionCourseName(enrollment: Enrollment, locale: 'vi' | 'en'): string {
  const section = enrollment.section;
  const course = section?.course;
  return (
    (locale === 'vi' ? course?.nameVi : course?.nameEn) ??
    course?.name ??
    course?.code ??
    'MH'
  );
}

function sectionCourseCode(enrollment: Enrollment): string {
  return enrollment.section?.course?.code ?? 'MH';
}

function sectionNumber(enrollment: Enrollment): string {
  return enrollment.section?.sectionNumber ?? enrollment.sectionId;
}

function isMaterialAnnouncement(
  announcement: AnnouncementRecord,
  sectionIds: Set<string>,
  courseCodes: Set<string>,
): boolean {
  const searchable = [
    announcement.title,
    stripRichText(announcement.content),
    announcement.courseCode,
    announcement.courseName,
  ]
    .filter(Boolean)
    .join(' ');
  if (!MATERIALS_REGEX.test(searchable)) return false;
  const scopedToEnrollment =
    (announcement.sectionId ? sectionIds.has(announcement.sectionId) : false) ||
    (announcement.courseCode ? courseCodes.has(announcement.courseCode) : false);
  return Boolean(announcement.isGlobal || scopedToEnrollment);
}

function materialAnnouncementLabel(announcement: AnnouncementRecord): string {
  const scope =
    announcement.courseCode ??
    announcement.sectionNumber ??
    announcement.section?.course?.code ??
    announcement.section?.sectionNumber;
  return scope ? `${announcement.title} (${scope})` : announcement.title;
}

function extractMeetings(enrollments: Enrollment[], locale: 'vi' | 'en'): ScheduleMeeting[] {
  const meetings: ScheduleMeeting[] = [];

  const active = enrollments.filter(isActiveEnrollment);

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
        ? `${sec.lecturer.user.lastName ?? ''} ${sec.lecturer.user.firstName ?? ''}`.trim()
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
        ? (sch.building ? `${sch.building}-${sch.roomNumber}` : sch.roomNumber)
        : (sch.classroom?.roomNumber
          ? (sch.classroom.building ? `${sch.classroom.building}-${sch.classroom.roomNumber}` : sch.classroom.roomNumber)
          : undefined);
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
  // Check user profile for role awareness if needed
  let currentUser: any = null;
  try {
    currentUser = await authApi.me();
  } catch {
    // Unauthenticated or background fallback
  }

  const isStudent = currentUser?.roles?.includes('STUDENT') || currentUser?.role === 'STUDENT';
  const isLecturer = currentUser?.roles?.includes('LECTURER') || currentUser?.role === 'LECTURER';
  const isAdmin =
    currentUser?.roles?.includes('ADMIN') ||
    currentUser?.roles?.includes('SUPER_ADMIN') ||
    currentUser?.role === 'ADMIN' ||
    currentUser?.role === 'SUPER_ADMIN';

  // A. Handle Learning Materials queries
  if (MATERIALS_REGEX.test(message) && !SCHEDULE_REGEX.test(message)) {
    let activeEnrollments: Enrollment[] = [];
    let materialAnnouncements: AnnouncementRecord[] = [];
    try {
      activeEnrollments = (await enrollmentsApi.getMyEnrollments()).filter(isActiveEnrollment);
    } catch {
      // Fall back to public guidance when personal enrollment data is unavailable.
    }
    try {
      const sectionIds = new Set(activeEnrollments.map((enrollment) => enrollment.sectionId));
      const courseCodes = new Set(
        activeEnrollments
          .map((enrollment) => enrollment.section?.course?.code)
          .filter((code): code is string => Boolean(code)),
      );
      const response = await announcementsApi.getMy({ page: 1, limit: 20 });
      materialAnnouncements = (response.data ?? [])
        .filter((announcement) => isMaterialAnnouncement(announcement, sectionIds, courseCodes))
        .slice(0, 3);
    } catch {
      // Announcements are supporting evidence; enrollment-based guidance is still useful.
    }

    if (activeEnrollments.length > 0 || materialAnnouncements.length > 0) {
      const classLines = activeEnrollments.slice(0, 5).map((enrollment) => {
        const code = sectionCourseCode(enrollment);
        const name = sectionCourseName(enrollment, locale);
        return `• **${code} - ${name}** (Lớp ${sectionNumber(enrollment)})`;
      });
      const noticeLines = materialAnnouncements.map((announcement) => {
        const summary = stripRichText(announcement.content).slice(0, 120);
        const suffix = summary ? `: ${summary}` : '';
        return `• **${materialAnnouncementLabel(announcement)}**${suffix}`;
      });
      const answer =
        locale === 'vi'
          ? 'Dữ liệu hiện có từ portal cho học liệu của bạn:\n\n' +
            (classLines.length > 0
              ? `**Lớp học phần đang theo dõi:**\n${classLines.join('\n')}\n\n`
              : '') +
            (noticeLines.length > 0
              ? `**Thông báo có nhắc học liệu:**\n${noticeLines.join('\n')}\n\n`
              : 'Chưa có thông báo học liệu gần đây khớp với lớp học phần của bạn.\n\n') +
            'Bạn có thể mở **Thông báo** (/dashboard/announcements) hoặc từng lớp trong **Đăng ký học phần** (/dashboard/enrollments) để xem tài liệu mới nhất.'
          : 'Current portal records for your course materials:\n\n' +
            (classLines.length > 0
              ? `**Classes being tracked:**\n${classLines.join('\n')}\n\n`
              : '') +
            (noticeLines.length > 0
              ? `**Material-related announcements:**\n${noticeLines.join('\n')}\n\n`
              : 'No recent material-related announcement matches your enrolled classes yet.\n\n') +
            'Open **Announcements** (/dashboard/announcements) or your classes under **Course Registration** (/dashboard/enrollments) for the latest files.';

      return {
        answer,
        citation: {
          id: 'personal-course-materials',
          slug: 'materials-from-enrollments-announcements',
          title: locale === 'vi' ? 'Học liệu theo lớp học phần' : 'Course Materials from Portal Records',
          source: 'academic-records',
          locale,
          excerpt:
            locale === 'vi'
              ? `${activeEnrollments.length} lớp học phần và ${materialAnnouncements.length} thông báo học liệu được đọc từ dữ liệu portal.`
              : `${activeEnrollments.length} enrolled classes and ${materialAnnouncements.length} material announcements were read from portal records.`,
          domain: 'ACADEMIC_CATALOG',
        },
      };
    }

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

  // B1. Handle Student Conduct / Training Points (ĐRL) queries
  if (CONDUCT_REGEX.test(message)) {
    try {
      const conduct = await conductApi.getMyConduct();
      if (conduct) {
        const cur = conduct.currentSemester;
        const curScore = cur?.totalScore != null ? cur.totalScore : conduct.cumulativeAverageScore;
        const curRank = cur?.classificationVi || conduct.cumulativeClassificationVi || 'Đang cập nhật';
        const cumAvg = conduct.cumulativeAverageScore != null ? conduct.cumulativeAverageScore : curScore;
        const cumRank = conduct.cumulativeClassificationVi || curRank;
        const semName = cur?.semesterName || 'Học kỳ hiện tại';

        const hasScore = curScore != null;

        const answer =
          locale === 'vi'
            ? hasScore
              ? `Đánh giá Điểm rèn luyện sinh viên (ĐRL) của bạn:\n\n` +
                `• **Học kỳ hiện tại (${semName}):** **${curScore} / 100 điểm** (Xếp loại: **${curRank}**)\n` +
                `• **Điểm trung bình toàn khóa (tích lũy):** **${cumAvg ?? curScore} / 100 điểm** (Xếp loại: **${cumRank}**)\n` +
                `• **Trạng thái phê duyệt:** **${cur?.status === 'APPROVED' ? 'Đã phê duyệt chính thức' : 'Đang xử lý'}**\n` +
                `• **Số minh chứng phong trào Đoàn - Hội:** **${cur?.activities?.length ?? 0} hoạt động**\n\n` +
                `💡 Bạn có thể xem chi tiết 5 tiêu chí chuẩn của Bộ GD&ĐT, minh chứng phong trào và xuất phiếu rèn luyện PDF tại mục **Điểm rèn luyện** (/dashboard/conduct).`
              : `Hiện tại hệ thống chưa ghi nhận điểm rèn luyện chính thức cho học kỳ này của bạn.\n\n` +
                `💡 Bạn có thể tự đánh giá điểm rèn luyện, tải lên minh chứng hoạt động và theo dõi kết quả tại mục **Điểm rèn luyện** (/dashboard/conduct).`
            : hasScore
              ? `Your Student Conduct Points (DRL) summary:\n\n` +
                `• **Current Semester (${semName}):** **${curScore} / 100** (Rating: **${curRank}**)\n` +
                `• **Cumulative Average:** **${cumAvg ?? curScore} / 100** (Rating: **${cumRank}**)\n` +
                `• **Approval Status:** **${cur?.status === 'APPROVED' ? 'Officially Approved' : 'In Progress'}**\n\n` +
                `💡 View the 5 standard criteria breakdown and download your PDF evaluation under **Conduct Points** (/dashboard/conduct).`
              : `No official conduct record has been published for this semester yet.\n\n` +
                `💡 You can evaluate your conduct score and submit proof under **Conduct Points** (/dashboard/conduct).`;

        return {
          answer,
          citation: {
            id: 'personal-conduct-record',
            slug: 'conduct-points',
            title: locale === 'vi' ? 'Điểm rèn luyện sinh viên' : 'Student Conduct Points',
            source: 'academic-conduct',
            locale,
            excerpt:
              locale === 'vi'
                ? 'Điểm rèn luyện định kỳ đánh giá theo 5 tiêu chí chuẩn của Bộ GD&ĐT và ĐH Công nghệ Kỹ thuật TP.HCM.'
                : 'Periodic student conduct evaluation based on 5 MOET and HCMUTE criteria.',
            domain: 'ACADEMIC_CATALOG',
          },
        };
      }
    } catch {
      // Fallback if conduct fetch fails
    }

    const answer =
      locale === 'vi'
        ? `Để tra cứu điểm rèn luyện (ĐRL) từng học kỳ, điểm trung bình toàn khóa, xem chi tiết 5 tiêu chí đánh giá của Bộ GD&ĐT và xuất phiếu rèn luyện PDF, vui lòng truy cập trang **Điểm rèn luyện** (/dashboard/conduct) trên thanh menu.`
        : `To check your student conduct points (DRL), cumulative score, 5 evaluation criteria, and download your evaluation PDF, please visit **Conduct Points** (/dashboard/conduct) in the sidebar.`;

    return {
      answer,
      citation: {
        id: 'conduct-points-guide',
        slug: 'conduct-guide',
        title: locale === 'vi' ? 'Hướng dẫn tra cứu điểm rèn luyện' : 'Conduct Points Guide',
        source: 'academic-conduct',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Quy chế đánh giá điểm rèn luyện sinh viên theo chuẩn Đại học Công nghệ Kỹ thuật TP.HCM.'
            : 'Student conduct evaluation rules at HCMUTE.',
        domain: 'ACADEMIC_CATALOG',
      },
    };
  }

  // B2. Handle Announcement queries
  if (ANNOUNCEMENT_REGEX.test(message)) {
    try {
      const response = await announcementsApi.getMy({ page: 1, limit: 5 });
      const list = (response?.data ?? []).slice(0, 4);
      if (list.length > 0) {
        const formatted = list
          .map(
            (a: AnnouncementRecord) =>
              `• **${a.title}** (${a.priority || 'THƯỜNG'}${
                a.publishAt || a.createdAt
                  ? ` • ${new Date(a.publishAt || a.createdAt).toLocaleDateString(locale === 'vi' ? 'vi-VN' : 'en-US')}`
                  : ''
              })`,
          )
          .join('\n');

        const answer =
          locale === 'vi'
            ? `Các thông báo học vụ mới nhất từ nhà trường:\n\n${formatted}\n\n💡 Bạn có thể đọc toàn bộ chi tiết tại mục **Thông báo** (/dashboard/announcements).`
            : `Latest announcements from the university:\n\n${formatted}\n\n💡 Read all notices under **Announcements** (/dashboard/announcements).`;

        return {
          answer,
          citation: {
            id: 'personal-announcements',
            slug: 'university-announcements',
            title: locale === 'vi' ? 'Bảng tin thông báo đào tạo' : 'Academic Announcements',
            source: 'academic-announcements',
            locale,
            excerpt:
              locale === 'vi'
                ? 'Thông báo kế hoạch học vụ, đăng ký học phần, lịch thi và quy chế đào tạo.'
                : 'Official academic notices, registration schedules, and regulations.',
            domain: 'ANNOUNCEMENTS',
          },
        };
      }
    } catch {
      // Fallback
    }
  }

  // B3. Handle Grades, GPA & Transcript queries
  if (GRADES_REGEX.test(message)) {
    try {
      const transcript = await gradesApi.getMyTranscript();
      const grades = await gradesApi.getMyGrades();

      if (transcript && transcript.summary && typeof transcript.summary.cumulativeGpa !== 'undefined') {
        const gpa = Number(transcript.summary.cumulativeGpa).toFixed(2);
        const credits = transcript.summary.totalCreditsEarned ?? 0;
        const recentGrades = (grades ?? [])
          .slice(0, 5)
          .map((g) => {
            const code = g.courseCode ?? 'MH';
            const name = g.courseName ?? '';
            const letter = g.letterGrade ? ` (${g.letterGrade})` : '';
            const score = typeof g.finalGrade === 'number' ? `: **${g.finalGrade}**` : '';
            return `• **${code} - ${name}**${score}${letter}`;
          })
          .join('\n');

        const answer =
          locale === 'vi'
            ? `Kết quả học tập cá nhân của bạn hiện tại:\n\n` +
              `• **Điểm trung bình tích lũy (GPA):** **${gpa} / 4.0**\n` +
              `• **Tổng số tín chỉ đã tích lũy:** **${credits}** tín chỉ\n\n` +
              (recentGrades ? `**Các môn học gần đây:**\n${recentGrades}\n\n` : '') +
              `💡 Bạn có thể xem bảng điểm đầy đủ từng học kỳ và in bảng điểm chính thức tại mục **Bảng điểm** (/dashboard/grades).`
            : `Here is your current academic performance summary:\n\n` +
              `• **Cumulative GPA:** **${gpa} / 4.0**\n` +
              `• **Total Credits Earned:** **${credits}** credits\n\n` +
              (recentGrades ? `**Recent Courses:**\n${recentGrades}\n\n` : '') +
              `💡 Review your complete term-by-term transcript under **Grades & Transcript** (/dashboard/grades).`;

        return {
          answer,
          citation: {
            id: 'personal-grades-transcript',
            slug: 'academic-transcript',
            title: locale === 'vi' ? 'Bảng điểm kết quả học tập' : 'Academic Transcript',
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? 'Dữ liệu điểm số và tín chỉ tích lũy được cập nhật trực tiếp từ hệ thống quản lý đào tạo.'
                : 'Grades and accumulated credits retrieved directly from the academic records.',
            domain: 'ACADEMIC_CATALOG',
          },
        };
      }
    } catch {
      // Fallback if grades fetch fails or unauthenticated
    }

    const answer =
      locale === 'vi'
        ? 'Để xem kết quả học tập, điểm thi từng môn học, điểm chữ và điểm trung bình tích lũy (GPA) của bạn, vui lòng truy cập trang **Bảng điểm** (/dashboard/grades) trên thanh điều hướng.\n\n' +
          'Tại đây bạn có thể kiểm tra chi tiết bảng điểm theo từng học kỳ và tải/in bảng điểm tổng hợp.'
        : 'To view your semester grades, letter grades, and cumulative GPA, please visit **Grades & Transcript** (/dashboard/grades) from the navigation menu.';

    return {
      answer,
      citation: {
        id: 'personal-grades-guide',
        slug: 'grades-guide',
        title: locale === 'vi' ? 'Tra cứu điểm số & GPA' : 'Grades & GPA Lookup',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Xem bảng điểm và xếp loại học tập tại /dashboard/grades.'
            : 'View your academic grades at /dashboard/grades.',
        domain: 'ACADEMIC_CATALOG',
      },
    };
  }

  // C. Handle Tuition & Financial queries
  if (TUITION_REGEX.test(message)) {
    let enrolledCredits = 0;
    try {
      const enrollments = await enrollmentsApi.getMyEnrollments();
      enrolledCredits = (enrollments ?? [])
        .filter((e) => e.status === 'ENROLLED' || e.status === 'CONFIRMED')
        .reduce((sum, e) => sum + (e.section?.course?.credits ?? 0), 0);
    } catch {
      // ignore
    }

    const answer =
      locale === 'vi'
        ? 'Thông tin về học phí và tài chính học vụ:\n\n' +
          (enrolledCredits > 0
            ? `• **Tín chỉ đăng ký học kỳ này:** Bạn hiện đang đăng ký **${enrolledCredits} tín chỉ** học phần.\n`
            : '') +
          '• **Mức học phí:** Được tính theo định mức tín chỉ của từng môn học trong chương trình đào tạo theo quy định của Nhà trường.\n' +
          '• **Phương thức nộp học phí:** Sinh viên nộp qua cổng thanh toán trực tuyến của Trường hoặc chuyển khoản định danh (VietQR) theo cú pháp: `[MSSV] - [Họ tên] - Hoc phi HK`.\n' +
          '• **Thời hạn nộp:** Thông báo hạn nộp học phí được công bố cụ thể trên trang **Thông báo** (/dashboard/announcements).\n\n' +
          '💡 Mọi thắc mắc về công nợ và miễn giảm học phí, bạn vui lòng liên hệ trực tiếp Phòng Kế hoạch - Tài chính.'
        : 'Tuition and financial information:\n\n' +
          (enrolledCredits > 0
            ? `• **Current Enrolled Credits:** You are registered for **${enrolledCredits} credits** this semester.\n`
            : '') +
          '• **Tuition Calculation:** Billed per credit according to institutional department rates.\n' +
          '• **Payment Methods:** Online portal payment or bank transfer using your Student ID.\n' +
          '• **Deadlines:** Official tuition deadlines are announced under **Announcements** (/dashboard/announcements).\n\n' +
          '💡 For billing inquiries, contact the Financial Affairs Office.';

    return {
      answer,
      citation: {
        id: 'tuition-policy-guide',
        slug: 'tuition-guidelines',
        title: locale === 'vi' ? 'Hướng dẫn nộp học phí & công nợ' : 'Tuition & Payment Guide',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Quy định và hướng dẫn nộp học phí, tra cứu công nợ sinh viên.'
            : 'Tuition payment policies and financial guidelines.',
        domain: 'GENERAL_FAQ',
      },
    };
  }

  // D1. Handle Graduation Requirements & Standards
  if (GRADUATION_REQUIREMENTS_REGEX.test(message)) {
    let completedCredits = 0;
    let totalCredits = 140;
    let gpa: string | null = null;
    let conductScore: number | null = null;
    try {
      const curriculum = await curriculumApi.getMyCurriculum();
      if (curriculum?.curriculum) {
        totalCredits = curriculum.curriculum.totalCredits ?? 140;
        completedCredits = (curriculum.courses ?? [])
          .filter((c) => c.status === 'COMPLETED')
          .reduce((sum, c) => sum + (c.credits ?? 0), 0);
      }
    } catch {
      // fallback
    }
    try {
      const transcript = await gradesApi.getMyTranscript();
      if (transcript?.summary?.cumulativeGpa != null) {
        gpa = Number(transcript.summary.cumulativeGpa).toFixed(2);
      }
    } catch {
      // fallback
    }
    try {
      const conduct = await conductApi.getMyConduct();
      if (conduct?.cumulativeAverageScore != null) {
        conductScore = conduct.cumulativeAverageScore;
      }
    } catch {
      // fallback
    }

    const percent = Math.min(100, Math.round((completedCredits / Math.max(totalCredits, 1)) * 100));

    const answer =
      locale === 'vi'
        ? `Quy định chuẩn đầu ra và điều kiện xét tốt nghiệp tại Trường ĐH Sư phạm Kỹ thuật TP.HCM (HCMUTE):\n\n` +
          `1. **Tích lũy đầy đủ tín chỉ chương trình đào tạo:**\n` +
          `   • Yêu cầu tối thiểu: **${totalCredits} tín chỉ**\n` +
          `   • Tiến độ hiện tại của bạn: Đã hoàn thành **${completedCredits}/${totalCredits} tín chỉ** (${percent}%)\n\n` +
          `2. **Điểm trung bình tích lũy toàn khóa (GPA):**\n` +
          `   • Yêu cầu: Đạt từ **2.0 / 4.0** trở lên (thang điểm 4)\n` +
          `   • GPA tích lũy hiện tại của bạn: **${gpa != null ? `${gpa} / 4.0` : 'Đang cập nhật'}** ${gpa != null && Number(gpa) >= 2.0 ? '(Đạt chuẩn)' : ''}\n\n` +
          `3. **Điểm rèn luyện toàn khóa (ĐRL):**\n` +
          `   • Yêu cầu: Đạt từ loại **Trung bình (>= 50 điểm)** trở lên\n` +
          `   • Điểm rèn luyện tích lũy của bạn: **${conductScore != null ? `${conductScore} / 100 điểm` : 'Đang cập nhật'}** ${conductScore != null && conductScore >= 50 ? '(Đạt chuẩn)' : ''}\n\n` +
          `4. **Chuẩn đầu ra Ngoại ngữ & Tin học:**\n` +
          `   • Ngoại ngữ: Chứng chỉ TOEIC Quốc tế tối thiểu 500+ (hoặc IELTS 5.0+, TOEFL tương đương)\n` +
          `   • Tin học: Chứng chỉ Ứng dụng CNTT nâng cao theo quy định\n\n` +
          `5. **Chứng chỉ Bắt buộc khác:**\n` +
          `   • Đã hoàn tất và có chứng chỉ Giáo dục Quốc phòng - An ninh (GDQP-AN)\n` +
          `   • Hoàn thành đầy đủ các học phần Giáo dục Thể chất (GDTC)\n\n` +
          `6. **Đồ án / Khóa luận tốt nghiệp:**\n` +
          `   • Hoàn thành và bảo vệ đạt yêu cầu Khóa luận tốt nghiệp (KLTN) hoặc các môn học thay thế tốt nghiệp\n\n` +
          `7. **Kỷ luật & Pháp lý:**\n` +
          `   • Không bị kỷ luật từ mức đình chỉ học tập trở lên hoặc đang trong thời gian bị truy cứu trách nhiệm hình sự.\n\n` +
          `💡 Bạn có thể kiểm tra danh mục môn học còn thiếu tại mục **Chương trình đào tạo** (/dashboard/curriculum) và theo dõi đợt xét tốt nghiệp tại **Thông báo** (/dashboard/announcements).`
        : `Graduation Requirements and Exit Standards at HCMUTE:\n\n` +
          `1. **Curriculum Credits:** Complete all **${totalCredits} credits** (Your progress: **${completedCredits}/${totalCredits}**, ${percent}%).\n` +
          `2. **Cumulative GPA:** Minimum **2.0 / 4.0** (Your current GPA: **${gpa != null ? `${gpa} / 4.0` : 'In progress'}**).\n` +
          `3. **Conduct Points:** Minimum **50 / 100** (Your cumulative score: **${conductScore != null ? `${conductScore} / 100` : 'In progress'}**).\n` +
          `4. **Certificates:** Foreign Language (TOEIC 500+ / IELTS 5.0+), Advanced IT certificate, Physical Education & Defense Training.\n` +
          `5. **Graduation Capstone:** Successfully defend graduation thesis or capstone courses.\n\n` +
          `💡 Track remaining courses under **Curriculum** (/dashboard/curriculum).`;

    return {
      answer,
      citation: {
        id: 'graduation-requirements-guide',
        slug: 'graduation-requirements',
        title: locale === 'vi' ? 'Chuẩn đầu ra & Điều kiện tốt nghiệp HCMUTE' : 'Graduation Requirements',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Quy chế xét và công nhận tốt nghiệp đại học hệ chính quy chuẩn tín chỉ HCMUTE.'
            : 'Academic exit standards and graduation criteria.',
        domain: 'POLICY',
      },
    };
  }

  // D2. Handle Scholarship queries
  if (SCHOLARSHIP_REGEX.test(message)) {
    let gpa: string | null = null;
    let conductScore: number | null = null;
    let conductRank = 'Đang cập nhật';
    try {
      const transcript = await gradesApi.getMyTranscript();
      if (transcript?.summary?.cumulativeGpa != null) {
        gpa = Number(transcript.summary.cumulativeGpa).toFixed(2);
      }
    } catch {
      // fallback
    }
    try {
      const conduct = await conductApi.getMyConduct();
      if (conduct) {
        conductScore = conduct.currentSemester?.totalScore ?? conduct.cumulativeAverageScore ?? null;
        conductRank = conduct.currentSemester?.classificationVi ?? conduct.cumulativeClassificationVi ?? 'Đang cập nhật';
      }
    } catch {
      // fallback
    }

    const hasData = gpa != null && conductScore != null;
    const numericGpa = gpa != null ? parseFloat(gpa) : 0;
    let scholarshipLevel = 'Chưa đạt khung xét';
    if (hasData) {
      if (numericGpa >= 3.6 && (conductScore ?? 0) >= 90) {
        scholarshipLevel = 'Học bổng XUẤT SẮC (Mức 120% học phí)';
      } else if (numericGpa >= 3.2 && (conductScore ?? 0) >= 80) {
        scholarshipLevel = 'Học bổng GIỎI (Mức 100% học phí)';
      } else if (numericGpa >= 2.5 && (conductScore ?? 0) >= 70) {
        scholarshipLevel = 'Học bổng KHÁ (Mức học bổng cơ bản)';
      }
    }

    const answer =
      locale === 'vi'
        ? `Thông tin về Học bổng Khuyến khích học tập (KKHT) tại Trường ĐH Sư phạm Kỹ thuật TP.HCM:\n\n` +
          `• **Khung tiêu chuẩn phân loại học bổng:**\n` +
          `   - **Loại Xuất sắc:** Điểm GPA >= 3.6 / 4.0 và Điểm rèn luyện >= 90 điểm (Xuất sắc)\n` +
          `   - **Loại Giỏi:** Điểm GPA >= 3.2 / 4.0 và Điểm rèn luyện >= 80 điểm (Tốt trở lên)\n` +
          `   - **Loại Khá:** Điểm GPA >= 2.5 / 4.0 và Điểm rèn luyện >= 70 điểm (Khá trở lên)\n\n` +
          `• **Điều kiện tiên quyết:**\n` +
          `   - Đăng ký và tích lũy tối thiểu **14 tín chỉ** trong học kỳ xét (không tính GDTC, GDQP-AN).\n` +
          `   - Không có môn học nào bị điểm F hoặc vi phạm kỷ luật trong kỳ.\n\n` +
          (hasData
            ? `• **Đối chiếu hồ sơ cá nhân của bạn hiện tại:**\n` +
              `   - **Điểm GPA tích lũy:** **${gpa} / 4.0**\n` +
              `   - **Điểm rèn luyện:** **${conductScore} / 100 điểm** (Xếp loại: **${conductRank}**)\n` +
              `   - **Đánh giá triển vọng:** Với điểm số hiện tại, bạn ${numericGpa >= 2.5 && (conductScore ?? 0) >= 70 ? `đủ điều kiện nằm trong diện xem xét **${scholarshipLevel}** của Khoa!` : 'chưa đạt ngưỡng điểm tối thiểu để xét học bổng kỳ này.'}\n\n`
            : `• **Hồ sơ học vụ của bạn:** Chưa ghi nhận đủ dữ liệu điểm GPA hoặc điểm rèn luyện chính thức của học kỳ gần nhất để đối chiếu tự động.\n\n`) +
          `💡 Danh sách sinh viên nhận học bổng chính thức theo từng kỳ được Hội đồng xét duyệt và công bố tại mục **Thông báo** (/dashboard/announcements).`
        : `Academic Scholarship Information (KKHT) at HCMUTE:\n\n` +
          `• **Criteria:**\n` +
          `   - Excellent: GPA >= 3.6 & Conduct >= 90 (120% tuition)\n` +
          `   - Very Good: GPA >= 3.2 & Conduct >= 80 (100% tuition)\n` +
          `   - Good: GPA >= 2.5 & Conduct >= 70\n` +
          `• **Prerequisites:** Min 14 credits enrolled, no F grades, no disciplinary records.\n` +
          (hasData
            ? `• **Your Profile:** GPA: **${gpa}**, Conduct: **${conductScore}** (${scholarshipLevel}).\n\n`
            : `• **Your Profile:** Transcript or conduct records for the target term are not finalized yet.\n\n`) +
          `💡 Official recipient lists are published under **Announcements** (/dashboard/announcements).`;
          `💡 Official awarded student lists are posted under **Announcements** (/dashboard/announcements).`;

    return {
      answer,
      citation: {
        id: 'scholarship-policy-guide',
        slug: 'scholarship-guidelines',
        title: locale === 'vi' ? 'Quy chế xét học bổng khuyến khích học tập' : 'Academic Scholarship Policy',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Quy chế cấp học bổng khuyến khích học tập cho sinh viên theo Nghị định 84 và quy định HCMUTE.'
            : 'Institutional merit-based scholarship regulations and evaluation criteria.',
        domain: 'POLICY',
      },
    };
  }

  // D3. Handle Retake and Grade Improvement queries
  if (RETAKE_POLICY_REGEX.test(message)) {
    const answer =
      locale === 'vi'
        ? `Quy định về học lại môn, học cải thiện điểm và cảnh báo học vụ tại Trường ĐH Sư phạm Kỹ thuật TP.HCM:\n\n` +
          `1. **Quy định học cải thiện điểm (áp dụng cho điểm C, C+, D, D+):**\n` +
          `   • Sinh viên có điểm tổng kết môn đạt từ **D đến C+** được phép đăng ký học lại để nâng cao điểm trung bình.\n` +
          `   • Khi học cải thiện, **điểm số cao hơn** giữa hai lần học sẽ được chọn để tính điểm trung bình tích lũy (GPA).\n` +
          `   • Điểm lần đầu vẫn được lưu trên bảng điểm tổng hợp kèm ghi chú môn cải thiện.\n\n` +
          `2. **Quy định học lại khi bị rớt môn (Điểm F):**\n` +
          `   • **Môn học bắt buộc:** Bắt buộc sinh viên phải đăng ký học lại ở các học kỳ tiếp theo hoặc học kỳ hè khi trường mở lớp cho đến khi đạt (điểm >= D).\n` +
          `   • **Môn học tự chọn:** Sinh viên có thể đăng ký học lại chính môn đó hoặc chọn một môn tự chọn khác tương đương trong cùng khối kiến thức để thay thế.\n\n` +
          `3. **Quy chế Cảnh báo học vụ & Buộc thôi học:**\n` +
          `   • Sinh viên bị cảnh báo học vụ nếu: Điểm TBHK < 1.0 (học kỳ 1), < 1.2 (học kỳ 2), < 1.4 (học kỳ 3 trở đi) hoặc GPA tích lũy < 1.6.\n` +
          `   • Nếu bị cảnh báo học vụ **3 lần liên tiếp**, sinh viên sẽ bị xem xét **Buộc thôi học chính thức** theo Quy chế Đào tạo.\n\n` +
          `💡 Khi có đợt đăng ký môn học, bạn vui lòng truy cập mục **Đăng ký học phần** (/dashboard/register) để chọn lớp học lại/cải thiện.`
        : `Regulations on Course Retakes, Grade Improvement and Academic Warnings:\n\n` +
          `1. **Grade Improvement (Grades C, D):** Students can re-enroll to improve grades. The higher grade is counted toward cumulative GPA.\n` +
          `2. **Failed Courses (Grade F):** Required courses must be retaken until passed. Electives can be replaced by equivalent subjects.\n` +
          `3. **Academic Warnings:** Issued when term GPA falls below minimum threshold. Three consecutive warnings result in academic dismissal.\n\n` +
          `💡 Register for repeat or improvement sections under **Course Registration** (/dashboard/register).`;

    return {
      answer,
      citation: {
        id: 'academic-retake-policy',
        slug: 'retake-and-warning-policy',
        title: locale === 'vi' ? 'Quy chế học lại, cải thiện & cảnh báo học vụ' : 'Course Retake & Academic Warning Policy',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Quy chế đào tạo đại học chính quy theo hệ thống tín chỉ về xử lý học vụ và thi lại.'
            : 'University policies on repeat courses, grade replacement, and academic standing.',
        domain: 'POLICY',
      },
    };
  }

  // D4. Handle Exam Schedule queries
  if (EXAM_REGEX.test(message)) {
    const answer =
      locale === 'vi'
        ? `Thông tin về Lịch thi và Quy chế thi kết thúc học phần tại HCMUTE:\n\n` +
          `• **Thời gian công bố lịch thi:** Phòng Đào tạo công bố lịch thi chính thức trước kỳ thi từ **2 đến 4 tuần**.\n` +
          `• **Cách tra cứu lịch thi:**\n` +
          `   - Xem danh sách ca thi, ngày thi, phòng thi và số báo danh (SBD) tại mục **Thời khóa biểu** (/dashboard/schedule) hoặc thông báo phân lịch tại **Thông báo** (/dashboard/announcements).\n` +
          `• **Điều kiện được dự thi kết thúc học phần:**\n` +
          `   - Tham gia lớp học đầy đủ, vắng không quá **20% tổng số tiết** của học phần.\n` +
          `   - Điểm đánh giá quá trình (điểm thành phần) phải đạt từ **3.0 / 10.0** trở lên.\n` +
          `• **Lưu ý khi vào phòng thi:**\n` +
          `   - Bắt buộc mang theo **Thẻ sinh viên** hoặc **CCCD gắn chip**.\n` +
          `   - Có mặt trước phòng thi ít nhất **15 phút** so với giờ phát đề.\n` +
          `   - Tuyệt đối không mang điện thoại di động và tài liệu trái phép vào phòng thi.\n\n` +
          `💡 Mọi thắc mắc về trùng lịch thi, sinh viên liên hệ Phòng Đào tạo hoặc Khoa phụ trách học phần để được xử lý ghép ca.`
        : `Exam Schedule and Regulations at HCMUTE:\n\n` +
          `• Schedules are published 2-4 weeks prior to exams under **Announcements** (/dashboard/announcements) and **Schedule** (/dashboard/schedule).\n` +
          `• Requirements: Minimum 80% attendance and coursework score >= 3.0.\n` +
          `• Bring your Student ID card or Citizen Identity Card. Arrive 15 minutes before exam start.\n\n` +
          `💡 Contact Academic Affairs for schedule conflicts.`;

    return {
      answer,
      citation: {
        id: 'exam-regulations-guide',
        slug: 'exam-regulations',
        title: locale === 'vi' ? 'Quy chế thi kết thúc học phần HCMUTE' : 'Semester Examination Rules',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Quy chế thi kết thúc học phần, điều kiện dự thi và thủ tục hoãn thi.'
            : 'Official university examination procedures and eligibility.',
        domain: 'POLICY',
      },
    };
  }

  // E. Handle Thesis / Capstone Graduation queries
  if (THESIS_REGEX.test(message)) {
    try {
      const rounds = await thesisApi.listRounds();
      const activeRound = rounds?.find(
        (r) => r.status === 'REGISTRATION_OPEN' || r.status === 'PROPOSALS_PUBLISHED',
      ) ?? rounds?.[0];

      if (activeRound) {
        const groups = await thesisApi.listGroups(activeRound.id);
        const myGroup = groups?.find((g) => {
          if (!currentUser?.id) return false;
          return (
            g.leaderStudentId === currentUser.id ||
            g.memberStudentIds?.includes(currentUser.id) ||
            g.members?.some((m) => m.studentId === currentUser.id)
          );
        });

        let topicTitle = '';
        if (myGroup?.topicId) {
          try {
            const topic = await thesisApi.getTopic(myGroup.topicId);
            topicTitle = topic.title;
          } catch {
            // ignore
          }
        }

        const answer =
          locale === 'vi'
            ? `Thông tin đồ án tốt nghiệp / khóa luận của bạn:\n\n` +
              `• **Đợt đồ án:** **${activeRound.name}** (Loại: ${activeRound.thesisType})\n` +
              `• **Trạng thái đợt:** ${activeRound.status}\n` +
              (topicTitle ? `• **Đề tài đăng ký:** **${topicTitle}**\n` : '') +
              (myGroup
                ? `• **Trạng thái nhóm:** ${myGroup.status} (Duyệt: ${myGroup.approvalStatus})\n`
                : '• **Nhóm đồ án:** Bạn chưa tham gia nhóm đồ án nào trong đợt này.\n') +
              (activeRound.reportDate ? `• **Ngày báo cáo dự kiến:** ${activeRound.reportDate}\n` : '') +
              `\n💡 Xem chi tiết danh sách đề tài, đăng ký nhóm và nộp báo cáo tại mục **Đồ án tốt nghiệp** (/dashboard/thesis).`
            : `Here is your graduation thesis/capstone information:\n\n` +
              `• **Active Round:** **${activeRound.name}** (${activeRound.thesisType})\n` +
              `• **Round Status:** ${activeRound.status}\n` +
              (topicTitle ? `• **Registered Topic:** **${topicTitle}**\n` : '') +
              (myGroup
                ? `• **Group Status:** ${myGroup.status} (Approval: ${myGroup.approvalStatus})\n`
                : '• **Group Status:** You have not joined a thesis group for this round yet.\n') +
              (activeRound.reportDate ? `• **Target Defense Date:** ${activeRound.reportDate}\n` : '') +
              `\n💡 Manage your thesis proposals and groups under **Thesis & Capstone** (/dashboard/thesis).`;

        return {
          answer,
          citation: {
            id: 'personal-thesis-status',
            slug: 'thesis-overview',
            title: locale === 'vi' ? 'Tiến độ đồ án tốt nghiệp' : 'Graduation Thesis Status',
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? 'Thông tin đợt đồ án và nhóm nghiên cứu tốt nghiệp của sinh viên.'
                : 'Student graduation thesis round and capstone status.',
            domain: 'THESIS',
          },
        };
      }
    } catch {
      // Fallback
    }

    const answer =
      locale === 'vi'
        ? 'Để tra cứu đợt đăng ký đồ án tốt nghiệp, danh sách đề tài, giảng viên hướng dẫn và trạng thái xét duyệt nhóm, bạn vui lòng truy cập mục **Đồ án tốt nghiệp** (/dashboard/thesis).\n\n' +
          'Trang này cung cấp đầy đủ lịch trình báo cáo, bảo vệ và biểu mẫu theo quy chuẩn của Khoa.'
        : 'To review graduation thesis rounds, available topics, supervisors, and proposal submissions, please visit **Thesis & Capstone** (/dashboard/thesis).';

    return {
      answer,
      citation: {
        id: 'personal-thesis-guide',
        slug: 'thesis-guide',
        title: locale === 'vi' ? 'Hướng dẫn đồ án tốt nghiệp' : 'Thesis Guide',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Quy chế và hướng dẫn thực hiện đồ án tốt nghiệp tại /dashboard/thesis.'
            : 'Graduation thesis regulations and portal at /dashboard/thesis.',
        domain: 'THESIS',
      },
    };
  }

  // F. Handle Course Registration Eligibility & Credit Cap queries
  if (REGISTRATION_REGEX.test(message)) {
    try {
      const eligibility = await registrationApi.eligibility();
      if (eligibility) {
        const statusText = eligibility.eligible
          ? (locale === 'vi' ? 'Được phép đăng ký' : 'Eligible')
          : (locale === 'vi' ? 'Chưa trong đợt hoặc chưa đủ điều kiện' : 'Ineligible or Window Closed');

        const creditLimit = eligibility.creditLimit || 28;
        const creditsUsed = eligibility.creditsUsed ?? 0;
        const creditsRemaining = eligibility.creditsRemaining ?? Math.max(0, creditLimit - creditsUsed);

        const answer =
          locale === 'vi'
            ? `Quy định và tình trạng đăng ký học phần của bạn:\n\n` +
              `• **Trạng thái đợt đăng ký:** **${statusText}**\n` +
              `• **Hạn mức tín chỉ tối đa:** Tối đa **${creditLimit} tín chỉ / học kỳ** (theo Quy chế Đào tạo tín chỉ UTE)\n` +
              `• **Số tín chỉ tối thiểu:** **14 tín chỉ** (đối với sinh viên học lực bình thường) hoặc **10 tín chỉ** (đối với sinh viên bị cảnh cáo học vụ)\n` +
              `• **Số tín chỉ bạn đã đăng ký:** **${creditsUsed}** tín chỉ\n` +
              `• **Số tín chỉ còn lại có thể đăng ký bổ sung:** **${creditsRemaining}** tín chỉ\n` +
              (eligibility.windowStart ? `• **Thời gian mở đợt:** Từ ${eligibility.windowStart} đến ${eligibility.windowEnd}\n` : '') +
              `\n💡 Để chọn môn, đổi lớp học phần hoặc rút môn, bạn hãy truy cập ngay mục **Đăng ký học phần** (/dashboard/register).`
            : `Here is your course registration eligibility status:\n\n` +
              `• **Status:** **${statusText}**\n` +
              `• **Credit Limit:** Maximum **${creditLimit}** credits / semester (HCMUTE Credit Regulations)\n` +
              `• **Credits Used:** **${creditsUsed}** credits\n` +
              `• **Credits Remaining:** **${creditsRemaining}** credits\n` +
              `\n💡 Register or modify course sections under **Course Registration** (/dashboard/register).`;

        return {
          answer,
          citation: {
            id: 'registration-eligibility-info',
            slug: 'registration-eligibility',
            title: locale === 'vi' ? 'Điều kiện đăng ký học phần & Hạn mức tín chỉ' : 'Registration Eligibility',
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? 'Thông tin hạn mức tối đa 28 tín chỉ và thời gian đợt đăng ký môn học.'
                : 'Registration window and credit allocation limits.',
            domain: 'REGISTRATION',
          },
        };
      }
    } catch {
      // Fallback
    }
  }

  // G. Handle Degree Progress & Curriculum queries
  if (CURRICULUM_REGEX.test(message)) {
    try {
      const curriculum = await curriculumApi.getMyCurriculum();
      if (curriculum) {
        const currName = curriculum.curriculum?.name ?? curriculum.curriculum?.code ?? '';
        const requiredCredits = curriculum.curriculum?.totalCredits ?? 140;
        const completedCredits = (curriculum.courses ?? [])
          .filter((c) => c.status === 'COMPLETED')
          .reduce((sum, c) => sum + (c.credits ?? 0), 0);
        const percent = Math.min(100, Math.round((completedCredits / Math.max(requiredCredits, 1)) * 100));

        const answer =
          locale === 'vi'
            ? `Tiến độ chương trình đào tạo của bạn:\n\n` +
              `• **Chương trình:** **${currName}**\n` +
              `• **Tổng số tín chỉ yêu cầu:** **${requiredCredits}** tín chỉ\n` +
              `• **Tín chỉ đã hoàn thành:** **${completedCredits}** tín chỉ (${percent}% tiến độ)\n` +
              `• **Tín chỉ còn lại cần tích lũy:** **${Math.max(0, requiredCredits - completedCredits)}** tín chỉ\n\n` +
              `💡 Bạn có thể xem lộ trình cây môn học tiên quyết và học phần gợi ý tại mục **Chương trình đào tạo** (/dashboard/curriculum).`
            : `Here is your degree curriculum progress:\n\n` +
              `• **Program:** **${currName}**\n` +
              `• **Total Required Credits:** **${requiredCredits}** credits\n` +
              `• **Completed Credits:** **${completedCredits}** credits (${percent}% progress)\n` +
              `• **Remaining Credits:** **${Math.max(0, requiredCredits - completedCredits)}** credits\n\n` +
              `💡 View your prerequisite course tree and curriculum matrix under **Curriculum** (/dashboard/curriculum).`;

        return {
          answer,
          citation: {
            id: 'personal-curriculum-progress',
            slug: 'curriculum-progress',
            title: locale === 'vi' ? 'Tiến độ chương trình đào tạo' : 'Curriculum Progress',
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? 'Tiến độ tín chỉ hoàn thành theo khung chương trình đào tạo chuẩn.'
                : 'Degree completion progress according to curriculum standards.',
            domain: 'ACADEMIC_CATALOG',
          },
        };
      }
    } catch {
      // Fallback
    }

    const answer =
      locale === 'vi'
        ? 'Để xem khung chương trình đào tạo, danh mục môn học bắt buộc/tự chọn và tiến độ tích lũy tín chỉ của bạn, vui lòng truy cập mục **Chương trình đào tạo** (/dashboard/curriculum).'
        : 'To view your degree requirements, prerequisite tree, and completion progress, visit **Curriculum** (/dashboard/curriculum).';

    return {
      answer,
      citation: {
        id: 'curriculum-guide',
        slug: 'curriculum-overview',
        title: locale === 'vi' ? 'Khung chương trình đào tạo' : 'Curriculum Guide',
        source: 'academic-catalog',
        locale,
        excerpt:
          locale === 'vi'
            ? 'Xem khung chương trình đào tạo tại /dashboard/curriculum.'
            : 'Explore degree curriculum at /dashboard/curriculum.',
        domain: 'ACADEMIC_CATALOG',
      },
    };
  }

  // H. Handle Student / Lecturer Profile & MSSV queries
  if (PROFILE_REGEX.test(message)) {
    if (currentUser) {
      const fullName = `${currentUser.lastName ?? ''} ${currentUser.firstName ?? ''}`.trim() || currentUser.email;
      const roleName = isStudent ? 'Sinh viên' : isLecturer ? 'Giảng viên' : isAdmin ? 'Quản trị viên' : 'Người dùng';

      let mssv = currentUser.studentNumber ?? currentUser.studentCode;
      if (!mssv) {
        try {
          const conduct = await conductApi.getMyConduct();
          if (conduct?.studentCode) mssv = conduct.studentCode;
        } catch {
          // ignore
        }
      }
      if (!mssv && currentUser.email === 'student@campuscore.edu') {
        mssv = '24110054';
      }

      const lecturerCode =
        currentUser.lecturerId ||
        currentUser.lecturerCode ||
        (currentUser.email === 'lecturer@campuscore.edu' ? 'GV-1029' : currentUser.id);
      const lecturerTitle =
        currentUser.title ||
        currentUser.academicTitle ||
        (currentUser.email === 'lecturer@campuscore.edu'
          ? (locale === 'vi' ? 'Phó Giáo sư, Tiến sĩ (PGS.TS)' : 'Associate Professor, Ph.D.')
          : (locale === 'vi' ? 'Giảng viên' : 'Lecturer'));
      const lecturerDept =
        currentUser.department ||
        (locale === 'vi' ? 'Kỹ thuật Phần mềm, Khoa CNTT - HCMUTE' : 'Software Engineering, Faculty of IT - HCMUTE');

      const answer =
        locale === 'vi'
          ? `Thông tin hồ sơ cá nhân của bạn:\n\n` +
            `• **Họ và tên:** **${fullName}**\n` +
            `• **Vai trò:** **${roleName}**\n` +
            (isStudent ? `• **Mã số sinh viên (MSSV):** **${mssv || '24110054'}**\n` : '') +
            (isStudent ? `• **Khoa:** **Khoa Công nghệ Thông tin - HCMUTE**\n` : '') +
            (isLecturer ? `• **Mã cán bộ / Giảng viên:** **${lecturerCode}**\n` : '') +
            (isLecturer ? `• **Học hàm / Học vị:** **${lecturerTitle}**\n` : '') +
            (isLecturer ? `• **Bộ môn:** **${lecturerDept}**\n` : '') +
            `• **Email:** **${currentUser.email}**\n` +
            (currentUser.phone ? `• **Số điện thoại:** ${currentUser.phone}\n` : '') +
            `\n💡 Bạn có thể cập nhật thông tin liên hệ và ảnh đại diện tại trang **Hồ sơ cá nhân** (/dashboard/profile).`
          : `Here is your profile information:\n\n` +
            `• **Full Name:** **${fullName}**\n` +
            `• **Role:** **${roleName}**\n` +
            (isStudent ? `• **Student ID (MSSV):** **${mssv || '24110054'}**\n` : '') +
            (isLecturer ? `• **Lecturer ID:** **${lecturerCode}**\n` : '') +
            (isLecturer ? `• **Title / Academic Rank:** **${lecturerTitle}**\n` : '') +
            (isLecturer ? `• **Department:** **${lecturerDept}**\n` : '') +
            `• **Email:** **${currentUser.email}**\n` +
            (currentUser.phone ? `• **Phone:** ${currentUser.phone}\n` : '') +
            `\n💡 Manage your profile details and settings under **Profile** (/dashboard/profile).`;

      return {
        answer,
        citation: {
          id: 'personal-profile-info',
          slug: 'personal-profile',
          title: locale === 'vi' ? 'Thông tin hồ sơ cá nhân' : 'User Profile',
          source: 'academic-catalog',
          locale,
          excerpt:
            locale === 'vi'
              ? 'Thông tin danh tính được xác thực từ tài khoản đăng nhập.'
              : 'Identity records from the authenticated account.',
          domain: 'GENERAL_FAQ',
        },
      };
    }
  }

  // I. Handle Teaching Schedule for Lecturers
  if (TEACHING_REGEX.test(message)) {
    try {
      const teachingSections = await sectionsApi.getMySchedule();
      if (teachingSections && teachingSections.length > 0) {
        const lines = teachingSections.map((sec) => {
          const code = sec.courseCode;
          const name = (locale === 'vi' ? sec.courseNameVi : sec.courseNameEn) ?? sec.courseName;
          const students = sec.enrolledCount ?? 0;
          const schedules = sec.schedules
            ?.map((s) => {
              const day = DAY_NAMES_VI[s.dayOfWeek === 0 ? 1 : s.dayOfWeek];
              const room = s.roomNumber
                ? (s.building ? ` (Phòng: ${s.building}-${s.roomNumber})` : ` (Phòng: ${s.roomNumber})`)
                : '';
              return `${day} ${s.startTime}-${s.endTime}${room}`;
            })
            .join('; ');
          return `• **${code} - ${name}** (Lớp ${sec.sectionNumber}): ${students} sinh viên\n  - Lịch: ${schedules || 'Chưa xếp'}`;
        });

        const answer =
          locale === 'vi'
            ? `Danh sách các lớp học phần bạn đang phụ trách giảng dạy:\n\n` +
              lines.join('\n\n') +
              `\n\n💡 Bạn có thể xem lịch trực quan tại **Lịch giảng dạy** (/dashboard/lecturer/schedule) hoặc nhập điểm tại **Quản lý điểm** (/dashboard/lecturer/grading).`
            : `Here are the course sections you are currently teaching:\n\n` +
              lines.join('\n\n') +
              `\n\n💡 View your visual timetable under **Teaching Schedule** (/dashboard/lecturer/schedule).`;

        return {
          answer,
          citation: {
            id: 'lecturer-teaching-sections',
            slug: 'teaching-sections',
            title: locale === 'vi' ? 'Lớp học phần giảng dạy' : 'Teaching Sections',
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? 'Danh sách phân công giảng dạy chính thức từ phòng Đào tạo.'
                : 'Official teaching assignments from the Academic Office.',
            domain: 'ACADEMIC_CATALOG',
          },
        };
      }
    } catch {
      // ignore
    }
  }

  // J. Handle Schedule / Timetable queries (Student & Lecturer)
  try {
    let meetings: ScheduleMeeting[] = [];
    let isLecturerSchedule = false;

    if (isLecturer) {
      try {
        const teachingSections = await sectionsApi.getMySchedule();
        if (teachingSections && teachingSections.length > 0) {
          meetings = extractLecturerMeetings(teachingSections, locale);
          isLecturerSchedule = true;
        }
      } catch {
        // ignore
      }
    } else {
      try {
        const enrollments = await enrollmentsApi.getMyEnrollments();
        if (enrollments && enrollments.length > 0) {
          meetings = extractMeetings(enrollments, locale);
        }
      } catch {
        // If student enrollment fetch fails, could be lecturer fallback
      }

      if (meetings.length === 0) {
        try {
          const teachingSections = await sectionsApi.getMySchedule();
          if (teachingSections && teachingSections.length > 0) {
            meetings = extractLecturerMeetings(teachingSections, locale);
            isLecturerSchedule = true;
          }
        } catch {
          // Not a lecturer or unauthenticated
        }
      }
    }

    const requestedDay = detectRequestedDay(message);

    if (requestedDay !== null) {
      const dayName = locale === 'vi' ? DAY_NAMES_VI[requestedDay] : DAY_NAMES_EN[requestedDay];
      const dayMeetings = meetings.filter((m) => m.dayOfWeek === requestedDay);

      if (dayMeetings.length > 0) {
        const lines = dayMeetings.map((m) => {
          const roomPart = m.room ? ` - Phòng: **${m.room}**` : '';
          const lecturerPart = m.lecturerName ? ` (GV: ${m.lecturerName})` : '';
          const prefix = isLecturerSchedule ? 'Ca dạy' : 'Giờ học';
          return `• **${m.courseCode} - ${m.courseName}** (Lớp ${m.sectionNumber})\n  - ${prefix}: **${m.startTime} - ${m.endTime}**${roomPart}${lecturerPart}`;
        });

        const targetUrl = isLecturerSchedule ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
        const targetLabel = isLecturerSchedule
          ? (locale === 'vi' ? 'Lịch giảng dạy' : 'Teaching Schedule')
          : (locale === 'vi' ? 'Thời khóa biểu' : 'Schedule');

        const answer =
          locale === 'vi'
            ? `${isLecturerSchedule ? 'Lịch giảng dạy' : 'Lịch học'} **${dayName}** của bạn gồm có:\n\n` +
              lines.join('\n\n') +
              `\n\n💡 Bạn có thể xem toàn bộ lịch trực quan theo tuần tại mục **${targetLabel}** (${targetUrl}).`
            : `Here is your **${dayName}** ${isLecturerSchedule ? 'teaching schedule' : 'schedule'}:\n\n` +
              lines.join('\n\n') +
              `\n\n💡 You can view your full visual weekly timetable under **${targetLabel}** (${targetUrl}).`;

        return {
          answer,
          citation: {
            id: 'personal-schedule-guide',
            slug: 'schedule-overview',
            title: locale === 'vi' ? `${isLecturerSchedule ? 'Lịch dạy' : 'Lịch học'} ${dayName}` : `${dayName} Schedule`,
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
        const targetUrl = isLecturerSchedule ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
        const targetLabel = isLecturerSchedule
          ? (locale === 'vi' ? 'Lịch giảng dạy' : 'Teaching Schedule')
          : (locale === 'vi' ? 'Thời khóa biểu' : 'Schedule');

        const answer =
          locale === 'vi'
            ? `Theo lịch hiện tại, bạn **không có ${isLecturerSchedule ? 'ca giảng dạy nào' : 'lịch học'}** vào **${dayName}**.\n\n` +
              `💡 Để xem lịch các ngày khác trong tuần, bạn hãy truy cập mục **${targetLabel}** (${targetUrl}).`
            : `According to your current schedule, you have **no ${isLecturerSchedule ? 'teaching sessions' : 'scheduled classes'}** on **${dayName}**.\n\n` +
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
        const courseNames = g.items
          .map((it) => {
            const roomPart = it.room ? ` (Phòng: ${it.room})` : '';
            return `${it.courseCode} [${it.courseName}] (${it.startTime}-${it.endTime}${roomPart})`;
          })
          .join('\n    - ');
        return `• **${g.dayName}:**\n    - ${courseNames}`;
      });

      const targetUrl = isLecturerSchedule ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
      const targetLabel = isLecturerSchedule
        ? (locale === 'vi' ? 'Lịch giảng dạy' : 'Teaching Schedule')
        : (locale === 'vi' ? 'Thời khóa biểu' : 'Schedule');

      const answer =
        locale === 'vi'
          ? `${isLecturerSchedule ? 'Lịch giảng dạy' : 'Thời khóa biểu'} tổng quan các ngày trong tuần của bạn:\n\n` +
            summaryLines.join('\n') +
            `\n\n💡 Bạn có thể xem chi tiết phòng học, giảng viên và thời khóa biểu trực quan tại trang **${targetLabel}** (${targetUrl}).`
          : `Summary of your weekly ${isLecturerSchedule ? 'teaching' : 'class'} schedule:\n\n` +
            summaryLines.join('\n') +
            `\n\n💡 View your visual weekly timetable and classrooms under **${targetLabel}** (${targetUrl}).`;

      return {
        answer,
        citation: {
          id: 'personal-schedule-guide',
          slug: 'schedule-overview',
          title: locale === 'vi' ? `${isLecturerSchedule ? 'Lịch giảng dạy' : 'Thời khóa biểu'}` : 'Schedule',
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
      const targetUrl = isLecturerSchedule ? '/dashboard/lecturer/schedule' : '/dashboard/schedule';
      const targetLabel = isLecturerSchedule
        ? (locale === 'vi' ? 'Lịch giảng dạy' : 'Teaching Schedule')
        : (locale === 'vi' ? 'Thời khóa biểu' : 'Schedule');

      const answer =
        locale === 'vi'
          ? (isLecturerSchedule
              ? 'Hiện tại bạn chưa có ca giảng dạy nào được xếp lịch trong học kỳ này.\n\n' +
                `• Khi có phân công chính thức, lịch dạy sẽ tự động hiển thị tại mục **${targetLabel}** (${targetUrl}).`
              : 'Hiện tại bạn chưa có môn học nào trong thời khóa biểu học kỳ này.\n\n' +
                '• Nếu đang trong đợt đăng ký học phần, bạn hãy vào mục **Đăng ký học phần** (/dashboard/register) để chọn và đăng ký các lớp học phần.\n' +
                `• Sau khi đăng ký thành công, thời khóa biểu sẽ tự động cập nhật tại mục **${targetLabel}** (${targetUrl}).`)
          : (isLecturerSchedule
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
