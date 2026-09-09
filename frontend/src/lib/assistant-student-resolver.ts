import {
  authApi,
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

// 3. GRADES, GPA & TRANSCRIPT REGEX
const GRADES_REGEX =
  /(?:điểm|diem)\s*(?:số|so|thi|chữ|chu|học\s*phần|hoc\s*phan|môn|mon|của\s*tôi|cua\s*toi|tổng\s*kết|tong\s*ket|rèn\s*luyện|ren\s*luyen)?|(?:kết\s*quả\s*học\s*tập|ket\s*qua\s*hoc\s*tap)|\bgpa\b|(?:bảng\s*điểm|bang\s*diem)|học\s*lực|hoc\s*luc|tín\s*chỉ\s*tích\s*lũy|tin\s*chi\s*tich\s*luy|\btranscript\b|\bgrades?\b|\bgrade\s*point\b/i;

// 4. TUITION & FINANCIAL REGEX
const TUITION_REGEX =
  /(?:học\s*phí|hoc\s*phi|tiền\s*học|tien\s*hoc|công\s*nợ|cong\s*no|nợ\s*học\s*phí|no\s*hoc\s*phi|đóng\s*học\s*phí|dong\s*hoc\s*phi|nộp\s*học\s*phí|nop\s*hoc\s*phi|hạn\s*(?:đóng|nộp)\s*học\s*phí|han\s*(?:dong|nop)\s*hoc\s*phi|\btuition\b|\btuition\s*fee\b)/i;

// 5. THESIS & GRADUATION CAPSTONE REGEX
const THESIS_REGEX =
  /(?:đồ\s*án|do\s*an|khóa\s*luận|khoa\s*luan|tốt\s*nghiệp|tot\s*nghiep|bảo\s*vệ\s*đồ\s*án|bao\s*ve\s*do\s*an|hướng\s*dẫn\s*đồ\s*án|huong\s*dan\s*do\s*an|giảng\s*viên\s*hướng\s*dẫn|giang\s*vien\s*huong\s*dan|\bthesis\b|\bcapstone\b|\bgraduation\s*project\b)/i;

// 6. CURRICULUM & DEGREE PROGRESS REGEX
const CURRICULUM_REGEX =
  /(?:chương\s*trình\s*đào\s*tạo|chuong\s*trinh\s*dao\s*tao|khung\s*đào\s*tạo|khung\s*dao\s*tao|tiến\s*độ\s*(?:học\s*tập|đào\s*tạo)|tien\s*do\s*(?:hoc\s*tap|dao\s*tao)|bao\s*nhiêu\s*tín\s*chỉ|bao\s*nhieu\s*tin\s*chi|\bcurriculum\b|\bdegree\s*progress\b)/i;

// 7. STUDENT / LECTURER PROFILE & MSSV REGEX
const PROFILE_REGEX =
  /(?:thông\s*tin\s*(?:cá\s*nhân|sinh\s*viên|giảng\s*viên|tài\s*khoản|của\s*tôi)|thong\s*tin\s*(?:ca\s*nhan|sinh\s*vien|giang\s*vien|tai\s*khoan|cua\s*toi)|mã\s*số\s*(?:sinh\s*viên|của\s*tôi)|ma\s*so\s*(?:sinh\s*vien|cua\s*toi)|\bmssv\b|email\s*(?:sinh\s*viên|của\s*tôi)|lớp\s*sinh\s*hoạt|khoa\s*của\s*tôi|\bprofile\b|\bstudent\s*id\b)/i;

// 8. COURSE REGISTRATION & ELIGIBILITY REGEX
const REGISTRATION_REGEX =
  /(?:đăng\s*ký\s*học\s*phần|dang\s*ky\s*hoc\s*phan|đăng\s*ký\s*môn|dang\s*ky\s*mon|đợt\s*đăng\s*ký|dot\s*dang\s*ky|tín\s*chỉ\s*tối\s*đa|tin\s*chi\s*toi\s*da|hạn\s*đăng\s*ký|han\s*dang\s*ky|được\s*đăng\s*ký\s*không|\bcourse\s*registration\b|\benrollment\s*window\b)/i;

// 9. LECTURER TEACHING SECTIONS REGEX
const TEACHING_REGEX =
  /(?:lớp\s*(?:tôi\s*)?(?:đang\s*)?dạy|lop\s*(?:toi\s*)?(?:dang\s*)?day|danh\s*sách\s*lớp\s*giảng\s*dạy|danh\s*sach\s*lop\s*giang\s*day|ca\s*dạy\s*của\s*tôi|ca\s*day\s*cua\s*toi|lịch\s*giảng\s*dạy|lich\s*giang\s*day|\bteaching\s*sections?\b|\bteaching\s*schedule\b)/i;

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
    GRADES_REGEX.test(message) ||
    TUITION_REGEX.test(message) ||
    THESIS_REGEX.test(message) ||
    CURRICULUM_REGEX.test(message) ||
    PROFILE_REGEX.test(message) ||
    REGISTRATION_REGEX.test(message) ||
    TEACHING_REGEX.test(message)
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
  // Check user profile for role awareness if needed
  let currentUser: any = null;
  try {
    currentUser = await authApi.me();
  } catch {
    // Unauthenticated or background fallback
  }

  // A. Handle Learning Materials queries
  if (MATERIALS_REGEX.test(message) && !SCHEDULE_REGEX.test(message)) {
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

  // B. Handle Grades, GPA & Transcript queries
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
            ? 'Quy định và hướng dẫn nộp học phí cho sinh viên.'
            : 'Tuition fee schedule and payment instructions for students.',
        domain: 'POLICY',
      },
    };
  }

  // D. Handle Thesis / Capstone Graduation queries
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

  // E. Handle Degree Progress & Curriculum queries
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

  // F. Handle Student / Lecturer Profile & MSSV queries
  if (PROFILE_REGEX.test(message)) {
    if (currentUser) {
      const fullName = `${currentUser.lastName ?? ''} ${currentUser.firstName ?? ''}`.trim() || currentUser.email;
      const roleName =
        currentUser.role === 'STUDENT'
          ? 'Sinh viên'
          : currentUser.role === 'LECTURER'
          ? 'Giảng viên'
          : 'Quản trị viên';
      const mssv = currentUser.studentNumber ?? currentUser.username ?? currentUser.id?.slice(0, 10);

      const answer =
        locale === 'vi'
          ? `Thông tin hồ sơ cá nhân của bạn:\n\n` +
            `• **Họ và tên:** **${fullName}**\n` +
            `• **Vai trò:** **${roleName}**\n` +
            (currentUser.role === 'STUDENT' ? `• **Mã số sinh viên (MSSV):** **${mssv}**\n` : '') +
            `• **Email:** **${currentUser.email}**\n` +
            (currentUser.phone ? `• **Số điện thoại:** ${currentUser.phone}\n` : '') +
            `\n💡 Bạn có thể cập nhật thông tin liên hệ và ảnh đại diện tại trang **Hồ sơ cá nhân** (/dashboard/profile).`
          : `Here is your profile information:\n\n` +
            `• **Full Name:** **${fullName}**\n` +
            `• **Role:** **${currentUser.role}**\n` +
            (currentUser.role === 'STUDENT' ? `• **Student ID (MSSV):** **${mssv}**\n` : '') +
            `• **Email:** **${currentUser.email}**\n` +
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

  // G. Handle Course Registration Eligibility queries
  if (REGISTRATION_REGEX.test(message)) {
    try {
      const eligibility = await registrationApi.eligibility();
      if (eligibility) {
        const statusText = eligibility.eligible
          ? (locale === 'vi' ? 'Được phép đăng ký' : 'Eligible')
          : (locale === 'vi' ? 'Chưa trong đợt hoặc chưa đủ điều kiện' : 'Ineligible or Window Closed');

        const answer =
          locale === 'vi'
            ? `Tình trạng đăng ký học phần của bạn:\n\n` +
              `• **Trạng thái:** **${statusText}**\n` +
              `• **Hạn mức tín chỉ:** Tối đa **${eligibility.creditLimit}** tín chỉ\n` +
              `• **Tín chỉ đã đăng ký:** **${eligibility.creditsUsed}** tín chỉ\n` +
              `• **Tín chỉ còn lại:** **${eligibility.creditsRemaining}** tín chỉ\n` +
              (eligibility.windowStart ? `• **Thời gian mở đợt:** ${eligibility.windowStart} đến ${eligibility.windowEnd}\n` : '') +
              `\n💡 Truy cập ngay mục **Đăng ký học phần** (/dashboard/register) để thực hiện đăng ký hoặc điều chỉnh môn học.`
            : `Here is your course registration eligibility status:\n\n` +
              `• **Status:** **${statusText}**\n` +
              `• **Credit Limit:** Maximum **${eligibility.creditLimit}** credits\n` +
              `• **Credits Used:** **${eligibility.creditsUsed}** credits\n` +
              `• **Credits Remaining:** **${eligibility.creditsRemaining}** credits\n` +
              `\n💡 Register or modify course sections under **Course Registration** (/dashboard/register).`;

        return {
          answer,
          citation: {
            id: 'registration-eligibility-info',
            slug: 'registration-eligibility',
            title: locale === 'vi' ? 'Điều kiện đăng ký học phần' : 'Registration Eligibility',
            source: 'academic-catalog',
            locale,
            excerpt:
              locale === 'vi'
                ? 'Thông tin hạn mức và thời gian đợt đăng ký môn học.'
                : 'Registration window and credit allocation limits.',
            domain: 'REGISTRATION',
          },
        };
      }
    } catch {
      // Fallback
    }
  }

  // H. Handle Teaching Schedule for Lecturers
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
              const room = s.roomNumber ? ` (Phòng: ${s.roomNumber})` : '';
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

  // I. Handle Schedule / Timetable queries (Student & Lecturer)
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
          const roomPart = m.room ? ` - Phòng: **${m.room}**` : '';
          const lecturerPart = m.lecturerName ? ` (GV: ${m.lecturerName})` : '';
          const prefix = isLecturer ? 'Ca dạy' : 'Giờ học';
          return `• **${m.courseCode} - ${m.courseName}** (Lớp ${m.sectionNumber})\n  - ${prefix}: **${m.startTime} - ${m.endTime}**${roomPart}${lecturerPart}`;
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
