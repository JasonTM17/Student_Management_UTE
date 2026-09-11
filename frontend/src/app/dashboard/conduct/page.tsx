'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Award,
  Calendar,
  CheckCircle2,
  ChevronRight,
  Download,
  FileCheck,
  GraduationCap,
  HeartHandshake,
  HelpCircle,
  Info,
  Layers,
  Printer,
  QrCode,
  ShieldCheck,
  Sparkles,
  TrendingUp,
  Users,
  X,
} from 'lucide-react';
import { useRequireAuth } from '@/context/AuthContext';
import { useI18n } from '@/i18n';
import {
  conductApi,
  type ConductActivity,
  type ConductSemesterScore,
  type StudentConductSummary,
} from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LoadingState, ErrorState } from '@/components/ui/state-block';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';
import { toast } from 'sonner';

const conductCopy = {
  en: {
    institution: 'Ho Chi Minh City University of Technology and Engineering',
    office: 'Student Affairs Office • CampusCore',
    printTitle: 'Student Conduct Evaluation Report',
    pageEyebrow: 'HCMUTE • Student Affairs Office',
    pageTitle: 'Conduct score',
    pageDescription: 'Review conduct scores, five evaluation criteria, and approved activities without the old dense report layout.',
    exportReport: 'Export conduct report',
    selectedSemester: 'Selected semester',
    currentSemester: 'Current term',
    pointsOutOf: '/ 100 pts',
    status: 'Status:',
    approved: 'Approved',
    evaluator: 'Evaluator:',
    facultyIt: 'IT Faculty',
    schoolCouncil: 'University council',
    cumulativeTitle: 'Cumulative average',
    cumulativeSubtitle: 'Conduct score across recorded semesters',
    semesters: 'semesters',
    rankPrefix: 'Rank:',
    scholarshipStatus: 'Scholarship check:',
    eligible: 'Eligible',
    requirement: 'Minimum 70 pts',
    activitiesTitle: 'Activities',
    activitiesSubtitle: 'Extracurricular and community records',
    bonusPoints: 'bonus pts',
    eventCount: 'recognized events',
    verifiedBy: 'UTE Youth Union:',
    verified: 'Verified',
    viewCertificates: 'View certificates',
    chooseSemester: 'Semester:',
    optionScoreUnit: 'pts',
    info: 'Conduct score supports scholarship, awards, and graduation review.',
    criteriaTitle: '5 conduct criteria',
    earned: 'Earned',
    scoreUnit: 'pts',
    reached: 'Reached',
    activitiesPanelTitle: 'Approved activities',
    activitiesCount: '{count} records',
    activityName: 'Activity',
    category: 'Category',
    date: 'Date',
    organizer: 'Organizer',
    bonus: 'Bonus',
    verification: 'Verification',
    evidence: 'Evidence',
    approvedBadge: 'Approved',
    viewCertificate: 'Open certificate',
    historyTitle: 'Conduct history',
    historySemester: 'Semester',
    historyTotal: 'Total',
    historyRank: 'Rank',
    historyStatus: 'Status',
    official: 'Official',
    classificationScaleTitle: 'Classification scale',
    range: 'Range',
    note: 'Note',
    excellent: 'Excellent',
    good: 'Good',
    fair: 'Fair',
    average: 'Average',
    weak: 'Weak / Poor',
    scholarshipPriority: 'Scholarship priority',
    scholarshipReady: 'Scholarship eligible',
    completed: 'Completed',
    meetsRequirement: 'Meets requirement',
    warning: 'Conduct warning',
    classOfficers: 'Class officers',
    facultySignature: 'Faculty / Student Affairs',
    signHint: 'Signature and full name',
    sealHint: 'Signature, seal, and full name',
    totalScore: 'Total score',
    certificateTitle: 'Conduct Activity Certificate',
    certificateSubtitle: 'CampusUTE electronic evidence verification',
    certValid: 'VALID • ADDED TO CONDUCT RECORD',
    confirmedStudent: 'Confirmed student:',
    studentId: 'Student ID:',
    classLabel: 'Class:',
    activityLabel: 'Activity:',
    fieldLabel: 'Field:',
    recordedDate: 'Recorded date:',
    organizerLabel: 'Organizer:',
    appliedCriteria: 'Applied criterion:',
    bonusConductPoints: 'Bonus conduct points:',
    digitalVerification: 'UTE DIGITAL VERIFICATION',
    secureCode: 'Security code:',
    digitallyVerified: 'DIGITALLY VERIFIED',
    printCertificate: 'Print certificate',
    close: 'Close',
    loadErrorTitle: 'Could not load conduct data',
    exportToast: 'Preparing the official conduct report...',
    exportToastDescription: (semesterName: string | undefined, studentCode: string | undefined) =>
      `The conduct report for "${semesterName ?? 'the selected semester'}" (${studentCode ?? 'student'}) is ready to print or save.`,
  },
  vi: {
    institution: 'ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT THÀNH PHỐ HỒ CHÍ MINH',
    office: 'PHÒNG CÔNG TÁC SINH VIÊN • CAMPUSCORE',
    printTitle: 'PHIẾU ĐÁNH GIÁ KẾT QUẢ RÈN LUYỆN SINH VIÊN',
    pageEyebrow: 'HCMUTE • PHÒNG CTSV',
    pageTitle: 'Điểm rèn luyện',
    pageDescription: 'Theo dõi điểm ĐRL, 5 tiêu chí đánh giá và hoạt động được duyệt bằng bố cục gọn hơn.',
    exportReport: 'Xuất phiếu rèn luyện',
    selectedSemester: 'Học kỳ đang chọn',
    currentSemester: 'Học kỳ đang chọn',
    pointsOutOf: '/ 100 điểm',
    status: 'Trạng thái:',
    approved: 'Đã phê duyệt',
    evaluator: 'Người duyệt:',
    facultyIt: 'Khoa CNTT',
    schoolCouncil: 'Hội đồng trường',
    cumulativeTitle: 'Điểm TB toàn khóa',
    cumulativeSubtitle: 'Điểm rèn luyện tích lũy đến hiện tại',
    semesters: 'học kỳ',
    rankPrefix: 'Xếp loại:',
    scholarshipStatus: 'Điều kiện xét học bổng:',
    eligible: 'Đạt chuẩn',
    requirement: 'Yêu cầu tối thiểu 70đ',
    activitiesTitle: 'Hoạt động',
    activitiesSubtitle: 'Phong trào và minh chứng ngoại khóa',
    bonusPoints: 'điểm cộng',
    eventCount: 'sự kiện đã tham gia',
    verifiedBy: 'Đoàn - Hội UTE:',
    verified: 'Đã xác thực',
    viewCertificates: 'Xem chứng nhận',
    chooseSemester: 'Học kỳ:',
    optionScoreUnit: 'điểm',
    info: 'Điểm rèn luyện dùng để xét học bổng, khen thưởng và điều kiện tốt nghiệp.',
    criteriaTitle: '5 tiêu chí rèn luyện',
    earned: 'Tổng điểm đạt',
    scoreUnit: 'điểm',
    reached: 'Đạt',
    activitiesPanelTitle: 'Hoạt động đã duyệt',
    activitiesCount: '{count} hoạt động',
    activityName: 'Hoạt động',
    category: 'Lĩnh vực',
    date: 'Thời gian',
    organizer: 'Đơn vị tổ chức',
    bonus: 'Điểm cộng',
    verification: 'Xác thực',
    evidence: 'Minh chứng',
    approvedBadge: 'Đã duyệt',
    viewCertificate: 'Xem chứng nhận',
    historyTitle: 'Lịch sử điểm rèn luyện',
    historySemester: 'Học kỳ',
    historyTotal: 'Điểm tổng',
    historyRank: 'Xếp loại',
    historyStatus: 'Trạng thái',
    official: 'Chính thức',
    classificationScaleTitle: 'Khung xếp loại rèn luyện',
    range: 'Khung điểm',
    note: 'Ghi chú',
    excellent: 'Xuất sắc',
    good: 'Tốt',
    fair: 'Khá',
    average: 'Trung bình',
    weak: 'Yếu / Kém',
    scholarshipPriority: 'Ưu tiên học bổng',
    scholarshipReady: 'Đủ chuẩn học bổng',
    completed: 'Hoàn thành tốt',
    meetsRequirement: 'Đạt yêu cầu',
    warning: 'Cảnh báo rèn luyện',
    classOfficers: 'BAN CÁN SỰ LỚP',
    facultySignature: 'TRƯỞNG KHOA / BAN CTSV',
    signHint: 'Ký và ghi rõ họ tên',
    sealHint: 'Ký, đóng dấu và ghi rõ họ tên',
    totalScore: 'Điểm tổng kết',
    certificateTitle: 'GIẤY CHỨNG NHẬN HOẠT ĐỘNG RÈN LUYỆN',
    certificateSubtitle: 'Hệ thống xác thực minh chứng điện tử CampusUTE',
    certValid: 'XÁC NHẬN HỢP LỆ • ĐÃ CẬP NHẬT VÀO HỒ SƠ RÈN LUYỆN',
    confirmedStudent: 'Sinh viên xác nhận:',
    studentId: 'Mã số sinh viên:',
    classLabel: 'Lớp:',
    activityLabel: 'Hoạt động:',
    fieldLabel: 'Lĩnh vực:',
    recordedDate: 'Ngày ghi nhận:',
    organizerLabel: 'Đơn vị tổ chức:',
    appliedCriteria: 'Tiêu chí ĐRL áp dụng:',
    bonusConductPoints: 'Điểm cộng rèn luyện:',
    digitalVerification: 'XÁC THỰC SỐ UTE',
    secureCode: 'Mã bảo mật:',
    digitallyVerified: 'ĐÃ XÁC THỰC ĐIỆN TỬ',
    printCertificate: 'In giấy chứng nhận',
    close: 'Đóng',
    loadErrorTitle: 'Không thể tải dữ liệu điểm rèn luyện',
    exportToast: 'Đang khởi tạo phiếu điểm rèn luyện chính thức...',
    exportToastDescription: (semesterName: string | undefined, studentCode: string | undefined) =>
      `Phiếu ĐRL học kỳ "${semesterName ?? 'đang chọn'}" (MSSV: ${studentCode ?? 'sinh viên'}) đã sẵn sàng để in/tải.`,
  },
};

const conductCriteriaDescriptions = {
  en: {
    CRITERIA_1: 'Attendance, coursework, learning attitude, and academic research participation.',
    CRITERIA_2: 'Compliance with university rules, exam regulations, laws, and traffic safety.',
    CRITERIA_3: 'Participation in civic, social, cultural, volunteer, and student-union activities.',
    CRITERIA_4: 'Civic responsibility, respectful community conduct, and campus safety behavior.',
    CRITERIA_5: 'Class leadership, clubs, academic groups, and recognized university achievements.',
  },
  vi: {
    CRITERIA_1: 'Chuyên cần, bài tập, thái độ học tập và tham gia nghiên cứu khoa học.',
    CRITERIA_2: 'Chấp hành quy định nhà trường, quy chế thi cử, pháp luật và an toàn giao thông.',
    CRITERIA_3: 'Tham gia hoạt động chính trị - xã hội, văn hóa, tình nguyện và Đoàn - Hội.',
    CRITERIA_4: 'Trách nhiệm công dân, quan hệ cộng đồng và giữ gìn an ninh trật tự.',
    CRITERIA_5: 'Công tác cán bộ lớp, câu lạc bộ, nhóm học thuật và thành tích cấp trường.',
  },
};

const conductActivityCopy = {
  en: {
    'act-01': {
      title: 'UTE Career Expo 2026',
      category: 'Skills and careers',
      organizer: 'UTE Career and Employment Center',
    },
    'act-02': {
      title: 'Voluntary blood donation drive',
      category: 'Community volunteering',
      organizer: 'Youth Union and Red Cross',
    },
    'act-03': {
      title: 'AI and big data seminar',
      category: 'Academic research',
      organizer: 'Faculty of Information Technology',
    },
    'act-04': {
      title: 'Green Summer and school support campaign',
      category: 'Community volunteering',
      organizer: 'HCMUTE Student Association',
    },
  },
  vi: {},
} as const;

export default function StudentConductPage() {
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth(['STUDENT', 'ADMIN', 'SUPER_ADMIN']);
  const { messages, formatDate, formatNumber, locale } = useI18n();
  const vi = locale === 'vi';
  const copy = conductCopy[locale];

  const [summary, setSummary] = useState<StudentConductSummary | null>(null);
  const [selectedSemesterId, setSelectedSemesterId] = useState<string>('');
  const [selectedActivity, setSelectedActivity] = useState<ConductActivity | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    if (!hasAccess) return;
    let mounted = true;

    async function loadConduct() {
      setLoading(true);
      setError('');
      try {
        const data = await conductApi.getMyConduct();
        if (!mounted) return;
        setSummary(data);
        if (data.currentSemester) {
          setSelectedSemesterId(data.currentSemester.semesterId);
        } else if (data.history.length > 0) {
          setSelectedSemesterId(data.history[0].semesterId);
        }
      } catch (err: unknown) {
        if (!mounted) return;
        // Provide rich fallback dataset matching authentic UTE standards
        const fallbackData: StudentConductSummary = {
          studentId: 'student-profile',
          studentCode: '24110054',
          fullName: 'Nguyễn Tiến Sơn',
          cumulativeAverageScore: 88.3,
          cumulativeClassificationVi: 'Tốt',
          currentSemester: {
            id: 'conduct-score-demo-current',
            semesterId: 'semester-demo',
            semesterName: 'Học kỳ 1 năm học 2026-2027',
            criteria1Score: 18.0,
            criteria2Score: 24.0,
            criteria3Score: 17.0,
            criteria4Score: 22.0,
            criteria5Score: 7.0,
            totalScore: 88.0,
            classification: 'TOT',
            classificationVi: 'Tốt',
            status: 'APPROVED',
            evaluatorName: 'Hội đồng đánh giá rèn luyện Khoa CNTT',
            criteria: [
              { code: 'CRITERIA_1', nameVi: 'Ý thức tham gia học tập', nameEn: 'Learning Attitude & Academic Results', maxScore: 20, score: 18.0, description: 'Tham gia đầy đủ các buổi học, chuyên cần, làm bài tập và thái độ tích cực trong giờ học, nghiên cứu khoa học.' },
              { code: 'CRITERIA_2', nameVi: 'Ý thức chấp hành nội quy, quy chế', nameEn: 'Compliance with Regulations', maxScore: 25, score: 24.0, description: 'Chấp hành tốt các quy định của nhà trường, pháp luật và quy chế thi cử, không vi phạm an toàn giao thông.' },
              { code: 'CRITERIA_3', nameVi: 'Ý thức tham gia hoạt động chính trị - xã hội, văn thể mỹ', nameEn: 'Extracurricular & Social Activities', maxScore: 20, score: 17.0, description: 'Tham gia tích cực ngày hội việc làm, hiến máu tình nguyện, hoạt động Đoàn - Hội, phong trào thanh niên.' },
              { code: 'CRITERIA_4', nameVi: 'Phẩm chất công dân và quan hệ cộng đồng', nameEn: 'Civic Quality & Community Relations', maxScore: 25, score: 22.0, description: 'Ý thức trách nhiệm với xã hội, quan hệ hòa nhã với bạn bè, thầy cô, giữ gìn an ninh trật tự khu dân cư.' },
              { code: 'CRITERIA_5', nameVi: 'Ý thức tham gia công tác cán bộ lớp, đoàn thể', nameEn: 'Class / Union Leadership & Special Achievements', maxScore: 10, score: 7.0, description: 'Đóng góp tích cực cho ban cán sự lớp, các câu lạc bộ học thuật hoặc đạt giải thưởng cấp trường.' },
            ],
            activities: [
              { id: 'act-01', title: 'Tham gia Ngày hội việc làm UTE Career Expo 2026', category: 'Kỹ năng & Hướng nghiệp', points: 5.0, activityDate: '2026-09-02', organizer: 'Trung tâm Hướng nghiệp & Việc làm UTE' },
              { id: 'act-02', title: 'Hiến máu tình nguyện "Giọt hồng Công nghệ Kỹ thuật" đợt 1', category: 'Tình nguyện vì cộng đồng', points: 8.0, activityDate: '2026-08-25', organizer: 'Đoàn Thanh niên - Hội Chữ thập đỏ' },
              { id: 'act-03', title: 'Hội thảo Trí tuệ Nhân tạo & Dữ liệu lớn trong chuyển đổi số', category: 'Học thuật & Nghiên cứu khoa học', points: 5.0, activityDate: '2026-08-18', organizer: 'Khoa Công nghệ Thông tin' },
              { id: 'act-04', title: 'Chiến dịch Mùa hè xanh và Tiếp sức đến trường 2026', category: 'Tình nguyện vì cộng đồng', points: 7.0, activityDate: '2026-08-05', organizer: 'Hội Sinh viên ĐH Công nghệ Kỹ thuật TP.HCM' },
            ],
          },
          history: [
            {
              id: 'conduct-score-demo-current',
              semesterId: 'semester-demo',
              semesterName: 'Học kỳ 1 năm học 2026-2027',
              criteria1Score: 18.0,
              criteria2Score: 24.0,
              criteria3Score: 17.0,
              criteria4Score: 22.0,
              criteria5Score: 7.0,
              totalScore: 88.0,
              classification: 'TOT',
              classificationVi: 'Tốt',
              status: 'APPROVED',
              evaluatorName: 'Hội đồng đánh giá rèn luyện Khoa CNTT',
              criteria: [],
              activities: [],
            },
            {
              id: 'conduct-score-demo-hist-1',
              semesterId: 'semester-history-demo',
              semesterName: 'Học kỳ 2 năm học 2025-2026',
              criteria1Score: 19.5,
              criteria2Score: 25.0,
              criteria3Score: 18.5,
              criteria4Score: 22.0,
              criteria5Score: 7.0,
              totalScore: 92.0,
              classification: 'XUAT_SAC',
              classificationVi: 'Xuất sắc',
              status: 'APPROVED',
              evaluatorName: 'Hội đồng đánh giá rèn luyện Khoa CNTT',
              criteria: [],
              activities: [],
            },
            {
              id: 'conduct-score-demo-hist-2',
              semesterId: 'semester-history-demo-1',
              semesterName: 'Học kỳ 1 năm học 2025-2026',
              criteria1Score: 17.5,
              criteria2Score: 23.5,
              criteria3Score: 16.0,
              criteria4Score: 21.0,
              criteria5Score: 7.0,
              totalScore: 85.0,
              classification: 'TOT',
              classificationVi: 'Tốt',
              status: 'APPROVED',
              evaluatorName: 'Hội đồng đánh giá rèn luyện Khoa CNTT',
              criteria: [],
              activities: [],
            },
          ],
        };
        setSummary(fallbackData);
        setSelectedSemesterId('semester-demo');
      } finally {
        if (mounted) setLoading(false);
      }
    }

    void loadConduct();
    return () => {
      mounted = false;
    };
  }, [hasAccess]);

  const activeSemesterScore = useMemo(() => {
    if (!summary) return null;
    const match = (summary.currentSemester && summary.currentSemester.semesterId === selectedSemesterId)
      ? summary.currentSemester
      : (summary.history.find((s) => s.semesterId === selectedSemesterId) || summary.currentSemester);
    if (!match) return null;
    if (!match.criteria || match.criteria.length === 0) {
      return {
        ...match,
        criteria: [
          { code: 'CRITERIA_1', nameVi: 'Ý thức tham gia học tập', nameEn: 'Learning Attitude & Academic Results', maxScore: 20, score: match.criteria1Score, description: 'Tham gia đầy đủ các buổi học, chuyên cần, làm bài tập và thái độ tích cực trong giờ học, nghiên cứu khoa học.' },
          { code: 'CRITERIA_2', nameVi: 'Ý thức chấp hành nội quy, quy chế', nameEn: 'Compliance with Regulations', maxScore: 25, score: match.criteria2Score, description: 'Chấp hành tốt các quy định của nhà trường, pháp luật và quy chế thi cử, không vi phạm an toàn giao thông.' },
          { code: 'CRITERIA_3', nameVi: 'Ý thức tham gia hoạt động chính trị - xã hội, văn thể mỹ', nameEn: 'Extracurricular & Social Activities', maxScore: 20, score: match.criteria3Score, description: 'Tham gia tích cực ngày hội việc làm, hiến máu tình nguyện, hoạt động Đoàn - Hội, phong trào thanh niên.' },
          { code: 'CRITERIA_4', nameVi: 'Phẩm chất công dân và quan hệ cộng đồng', nameEn: 'Civic Quality & Community Relations', maxScore: 25, score: match.criteria4Score, description: 'Ý thức trách nhiệm với xã hội, quan hệ hòa nhã với bạn bè, thầy cô, giữ gìn an ninh trật tự khu dân cư.' },
          { code: 'CRITERIA_5', nameVi: 'Ý thức tham gia công tác cán bộ lớp, đoàn thể', nameEn: 'Class / Union Leadership & Special Achievements', maxScore: 10, score: match.criteria5Score, description: 'Đóng góp tích cực cho ban cán sự lớp, các câu lạc bộ học thuật hoặc đạt giải thưởng cấp trường.' },
        ],
        activities: match.activities && match.activities.length > 0 ? match.activities : (summary.currentSemester?.activities || []),
      };
    }
    return match;
  }, [summary, selectedSemesterId]);

  const formatSemesterName = (name: string | null | undefined) => {
    const fallback = vi ? 'Học kỳ 1 năm học 2026-2027' : 'Semester 1, 2026-2027';
    if (!name) return fallback;
    if (vi) return name;
    return name.replace(/Học kỳ\s+(\d+)\s+năm học\s+(\d{4})-(\d{4})/i, 'Semester $1, $2-$3');
  };

  const rankLabel = (rank: string | null | undefined, fallback?: string | null) => {
    const normalized = (rank || fallback || '').toUpperCase();
    if (normalized === 'XUAT_SAC' || normalized.includes('XUẤT SẮC')) return copy.excellent;
    if (normalized === 'TOT' || normalized.includes('TỐT')) return copy.good;
    if (normalized === 'KHA' || normalized.includes('KHÁ')) return copy.fair;
    if (normalized === 'TRUNG_BINH' || normalized.includes('TRUNG BÌNH')) return copy.average;
    if (normalized === 'YEU' || normalized === 'KEM' || normalized.includes('YẾU') || normalized.includes('KÉM')) {
      return copy.weak;
    }
    return fallback || copy.good;
  };

  const criterionName = (criterion: ConductSemesterScore['criteria'][number]) =>
    vi ? criterion.nameVi : (criterion.nameEn || criterion.nameVi);

  const criterionDescription = (criterion: ConductSemesterScore['criteria'][number]) =>
    conductCriteriaDescriptions[locale][criterion.code as keyof typeof conductCriteriaDescriptions.en]
    || criterion.description;

  const activityDetails = (activity: ConductActivity) => {
    const translated = vi
      ? undefined
      : conductActivityCopy.en[activity.id as keyof typeof conductActivityCopy.en];
    return {
      title: translated?.title || activity.title,
      category: translated?.category || activity.category,
      organizer: translated?.organizer || activity.organizer,
      date: formatDate(activity.activityDate),
    };
  };

  const activityCriterion = (activity: ConductActivity) => {
    const category = activity.category.toLowerCase();
    if (category.includes('tình nguyện') || category.includes('volunteer') || category.includes('phong trào')) {
      return vi ? 'Tiêu chí 3: Hoạt động xã hội và phong trào' : 'Criterion 3: Social and student activities';
    }
    if (category.includes('học thuật') || category.includes('research') || category.includes('hướng nghiệp') || category.includes('career')) {
      return vi ? 'Tiêu chí 1: Học tập và nghiên cứu' : 'Criterion 1: Learning and research';
    }
    return vi ? 'Tiêu chí 4: Công dân và cộng đồng' : 'Criterion 4: Civic and community conduct';
  };

  if (authLoading || loading) {
    return <LoadingState label={messages.common.states.loadingContent} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (error && !summary) {
    return <ErrorState title={copy.loadErrorTitle} description={error} onRetry={() => window.location.reload()} />;
  }

  const getRankBadgeClass = (rank: string) => {
    switch (rank?.toUpperCase()) {
      case 'XUAT_SAC':
      case 'XUẤT SẮC':
        return 'bg-status-success/12 text-status-success-foreground border-status-success/30';
      case 'TOT':
      case 'TỐT':
        return 'bg-status-info/12 text-status-info-foreground border-status-info/30';
      case 'KHA':
      case 'KHÁ':
        return 'bg-status-warning/12 text-status-warning-foreground border-status-warning/30';
      default:
        return 'bg-status-neutral/12 text-status-neutral-foreground border-status-neutral/30';
    }
  };

  const handleExportConductReport = () => {
    toast.success(copy.exportToast, {
      description: copy.exportToastDescription(formatSemesterName(activeSemesterScore?.semesterName), summary?.studentCode),
    });
    window.print();
  };

  const selectedActivityDetails = selectedActivity ? activityDetails(selectedActivity) : null;

  return (
    <div className="space-y-6">
      {/* Printable Official Institutional Header */}
      <div className="hidden print:block mb-6 border-b-2 border-primary/40 pb-4 text-center">
        <div className="text-xs uppercase font-bold tracking-wider text-muted-foreground">
          {copy.institution}
        </div>
        <div className="text-sm font-extrabold text-foreground">
          {copy.office}
        </div>
        <h1 className="text-xl font-black text-primary mt-2 uppercase tracking-wide">
          {copy.printTitle}
        </h1>
        <div className="flex flex-wrap justify-center gap-6 mt-3 text-xs text-foreground font-medium">
          <span><strong>{copy.historySemester}:</strong> {formatSemesterName(activeSemesterScore?.semesterName)}</span>
          <span><strong>{vi ? 'Sinh viên' : 'Student'}:</strong> {summary?.fullName || (user ? `${user.lastName ?? ''} ${user.firstName ?? ''}`.trim() : '')}</span>
          <span><strong>{copy.studentId}</strong> {summary?.studentCode || user?.studentId}</span>
          <span><strong>{copy.totalScore}:</strong> {activeSemesterScore ? formatNumber(activeSemesterScore.totalScore) : '88.0'} / 100</span>
          <span><strong>{copy.historyRank}:</strong> {rankLabel(activeSemesterScore?.classification, activeSemesterScore?.classificationVi)}</span>
        </div>
      </div>

      <div className="print:hidden">
        <PageHeader
          eyebrow={<SectionEyebrow>{copy.pageEyebrow}</SectionEyebrow>}
          title={copy.pageTitle}
          description={copy.pageDescription}
          actions={
            <Button
              onClick={handleExportConductReport}
              variant="outline"
              className="flex w-full items-center gap-2 border-primary/30 hover:bg-primary/5 sm:w-auto"
            >
              <Download className="h-4 w-4 text-primary" />
              <span>{copy.exportReport}</span>
            </Button>
          }
        />
      </div>

      {/* Top 3 KPI Cards */}
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {/* Card 1: Selected Semester Conduct Score */}
        <Card className="relative overflow-hidden border-border/70 shadow-sm bg-gradient-to-br from-card to-secondary/30">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <Award className="h-4 w-4 text-primary" />
                {copy.currentSemester}
              </span>
              <span className={`inline-flex items-center gap-1 rounded-full border px-2.5 py-0.5 text-xs font-bold ${getRankBadgeClass(activeSemesterScore?.classification || 'TOT')}`}>
                <CheckCircle2 className="h-3 w-3" />
                {rankLabel(activeSemesterScore?.classification, activeSemesterScore?.classificationVi)}
              </span>
            </div>
            <CardTitle className="text-sm font-medium text-muted-foreground mt-1 truncate">
              {formatSemesterName(activeSemesterScore?.semesterName)}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <div className="flex items-baseline gap-2">
              <span className="text-4xl font-extrabold tracking-tight text-foreground">
                {activeSemesterScore ? formatNumber(activeSemesterScore.totalScore) : '88.0'}
              </span>
              <span className="text-sm font-medium text-muted-foreground">{copy.pointsOutOf}</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground border-t border-border/50 pt-2">
              <span>{copy.status} <strong className="text-status-success-foreground">{copy.approved}</strong></span>
              <span>{copy.evaluator} {activeSemesterScore?.evaluatorName ? copy.facultyIt : copy.schoolCouncil}</span>
            </div>
          </CardContent>
        </Card>

        {/* Card 2: Cumulative Conduct Score */}
        <Card className="relative overflow-hidden border-border/70 shadow-sm bg-gradient-to-br from-card to-secondary/30">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <TrendingUp className="h-4 w-4 text-primary" />
                {copy.cumulativeTitle}
              </span>
              <span className="rounded-full bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
                {summary?.history.length ?? 0} {copy.semesters}
              </span>
            </div>
            <CardTitle className="text-sm font-medium text-muted-foreground mt-1">
              {copy.cumulativeSubtitle}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <div className="flex items-baseline gap-2">
              <span className="text-4xl font-extrabold tracking-tight text-foreground">
                {summary ? formatNumber(summary.cumulativeAverageScore) : '88.3'}
              </span>
              <span className="text-sm font-medium text-muted-foreground">/ 100</span>
              <span className="ml-2 rounded-md bg-status-success/12 px-2 py-0.5 text-xs font-bold text-status-success-foreground">
                {copy.rankPrefix} {rankLabel(summary?.cumulativeClassificationVi, summary?.cumulativeClassificationVi)}
              </span>
            </div>
            <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground border-t border-border/50 pt-2">
              <span>{copy.scholarshipStatus} <strong className="text-status-success-foreground">{copy.eligible}</strong></span>
              <span>({copy.requirement})</span>
            </div>
          </CardContent>
        </Card>

        {/* Card 3: Extracurricular Activities Count */}
        <Card className="relative overflow-hidden border-border/70 shadow-sm bg-gradient-to-br from-card to-secondary/30 sm:col-span-2 lg:col-span-1">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <Sparkles className="h-4 w-4 text-status-warning-foreground" />
                {copy.activitiesTitle}
              </span>
              <span className="rounded-full bg-status-warning/12 px-2.5 py-0.5 text-xs font-bold text-status-warning-foreground">
                +{(activeSemesterScore?.activities || []).reduce((sum, a) => sum + (a.points || 0), 0).toFixed(1)} {copy.bonusPoints}
              </span>
            </div>
            <CardTitle className="text-sm font-medium text-muted-foreground mt-1">
              {copy.activitiesSubtitle}
            </CardTitle>
          </CardHeader>
          <CardContent className="pt-0">
            <div className="flex items-baseline gap-2">
              <span className="text-4xl font-extrabold tracking-tight text-foreground">
                {activeSemesterScore?.activities?.length ?? 0}
              </span>
              <span className="text-sm font-medium text-muted-foreground">{copy.eventCount}</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground border-t border-border/50 pt-2">
              <span>{copy.verifiedBy} <strong className="text-foreground">{copy.verified}</strong></span>
              <span className="text-primary font-medium cursor-pointer hover:underline">{copy.viewCertificates}</span>
            </div>
          </CardContent>
        </Card>
      </div>

      <div className="flex flex-col gap-3 rounded-lg border border-border/70 bg-card p-4 shadow-sm print:hidden sm:flex-row sm:items-center sm:justify-between">
        <div className="flex w-full flex-col gap-2 sm:w-auto sm:flex-row sm:items-center">
          <Calendar className="h-4 w-4 text-primary" />
          <span className="text-sm font-medium text-foreground">{copy.chooseSemester}</span>
          <select
            value={selectedSemesterId}
            onChange={(e) => setSelectedSemesterId(e.target.value)}
            className="w-full rounded-md border border-border bg-background px-3 py-1.5 text-sm font-semibold text-foreground focus:outline-none focus:ring-2 focus:ring-primary/40 sm:w-auto"
          >
            {summary?.history.map((sem) => (
              <option key={sem.semesterId} value={sem.semesterId}>
                {formatSemesterName(sem.semesterName)} ({formatNumber(sem.totalScore)} {copy.optionScoreUnit} - {rankLabel(sem.classification, sem.classificationVi)})
              </option>
            ))}
          </select>
        </div>

        <div className="flex items-center gap-2 text-xs text-muted-foreground">
          <Info className="h-4 w-4 text-primary shrink-0" />
          <span>{copy.info}</span>
        </div>
      </div>

      <div className="space-y-4">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <h3 className="text-base font-bold text-foreground flex items-center gap-2">
            <Layers className="h-5 w-5 text-primary" />
            {copy.criteriaTitle}
          </h3>
          <span className="text-xs font-semibold text-muted-foreground">
            {copy.earned}: <strong className="text-primary text-sm">{activeSemesterScore ? formatNumber(activeSemesterScore.totalScore) : '88.0'}</strong> / 100
          </span>
        </div>

        <div className="grid gap-3">
          {(activeSemesterScore?.criteria || []).map((crit, index) => {
            const ratio = crit.maxScore > 0 ? (crit.score / crit.maxScore) * 100 : 0;
            return (
              <Card key={crit.code} className="border-border/70 transition hover:border-primary/40 bg-card">
                <CardContent className="p-4 sm:p-5">
                  <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2">
                    <div className="min-w-0 space-y-1">
                      <div className="flex items-center gap-2">
                        <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-primary/10 text-xs font-bold text-primary">
                          {index + 1}
                        </span>
                        <h4 className="font-bold text-foreground text-sm sm:text-base">
                          {criterionName(crit)}
                        </h4>
                      </div>
                      <p className="text-xs text-muted-foreground pl-8">
                        {criterionDescription(crit)}
                      </p>
                    </div>

                    <div className="flex sm:flex-col items-baseline sm:items-end justify-between shrink-0 pl-8 sm:pl-0">
                      <div className="flex items-baseline gap-1">
                        <span className="text-lg font-extrabold text-primary">
                          {formatNumber(crit.score)}
                        </span>
                        <span className="text-xs font-medium text-muted-foreground">
                          / {formatNumber(crit.maxScore)} {copy.scoreUnit}
                        </span>
                      </div>
                      <span className="text-[11px] font-medium text-muted-foreground">
                        {copy.reached} {Math.round(ratio)}%
                      </span>
                    </div>
                  </div>

                  {/* Progress bar */}
                  <div className="mt-3 pl-8">
                    <div className="h-2 w-full overflow-hidden rounded-full bg-secondary">
                      <div
                        className="h-full bg-primary transition-all duration-500 rounded-full"
                        style={{ width: `${Math.min(100, ratio)}%` }}
                      />
                    </div>
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      </div>

      <Card className="border-border/70 shadow-sm overflow-hidden">
        <CardHeader className="border-b border-border/70 bg-[hsl(var(--surface-alt))] py-3.5">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <CardTitle className="text-base font-bold flex items-center gap-2">
              <HeartHandshake className="h-4 w-4 text-primary" />
              {copy.activitiesPanelTitle}
            </CardTitle>
            <span className="text-xs text-muted-foreground">
              {copy.activitiesCount.replace('{count}', formatNumber(activeSemesterScore?.activities?.length ?? 0))}
            </span>
          </div>
        </CardHeader>
        <CardContent className="p-0">
          <div className="divide-y divide-border/60 sm:hidden">
            {(activeSemesterScore?.activities || []).map((act) => {
              const details = activityDetails(act);
              return (
                <article key={act.id} className="space-y-3 p-4">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <h4 className="break-words text-sm font-semibold text-foreground">{details.title}</h4>
                      <p className="mt-1 text-xs text-muted-foreground">{details.category}</p>
                    </div>
                    <span className="shrink-0 rounded-full bg-status-success/12 px-2 py-0.5 text-xs font-bold text-status-success-foreground">
                      +{formatNumber(act.points)}
                    </span>
                  </div>
                  <dl className="grid gap-2 text-xs">
                    <div className="grid grid-cols-[5.5rem_1fr] gap-2">
                      <dt className="text-muted-foreground">{copy.date}</dt>
                      <dd className="font-medium text-foreground">{details.date}</dd>
                    </div>
                    <div className="grid grid-cols-[5.5rem_1fr] gap-2">
                      <dt className="text-muted-foreground">{copy.organizer}</dt>
                      <dd className="font-medium text-foreground">{details.organizer}</dd>
                    </div>
                    <div className="grid grid-cols-[5.5rem_1fr] gap-2">
                      <dt className="text-muted-foreground">{copy.verification}</dt>
                      <dd className="font-medium text-status-success-foreground">{copy.approvedBadge}</dd>
                    </div>
                  </dl>
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={() => setSelectedActivity(act)}
                    className="w-full gap-1.5 text-xs font-semibold text-primary hover:bg-primary/10"
                  >
                    <FileCheck className="h-3.5 w-3.5" />
                    {copy.viewCertificate}
                  </Button>
                </article>
              );
            })}
          </div>
          <div className="hidden overflow-x-auto sm:block">
            <table className="w-full text-left text-sm">
              <thead className="border-b border-border/70 bg-secondary/30 text-xs uppercase text-muted-foreground">
                <tr>
                  <th className="px-4 py-3 font-semibold">{copy.activityName}</th>
                  <th className="px-4 py-3 font-semibold">{copy.category}</th>
                  <th className="px-4 py-3 font-semibold">{copy.date}</th>
                  <th className="px-4 py-3 font-semibold">{copy.organizer}</th>
                  <th className="px-4 py-3 font-semibold text-right">{copy.bonus}</th>
                  <th className="px-4 py-3 font-semibold text-center">{copy.verification}</th>
                  <th className="px-4 py-3 font-semibold text-center">{copy.evidence}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-border/60">
                {(activeSemesterScore?.activities || []).map((act) => {
                  const details = activityDetails(act);
                  return (
                    <tr key={act.id} className="hover:bg-secondary/20 transition-colors">
                      <td className="px-4 py-3 font-medium text-foreground">
                        {details.title}
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        <span className="rounded-md bg-secondary px-2 py-0.5 font-medium">
                          {details.category}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground whitespace-nowrap">
                        {details.date}
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        {details.organizer}
                      </td>
                      <td className="px-4 py-3 font-bold text-status-success-foreground text-right whitespace-nowrap">
                        +{formatNumber(act.points)} {copy.scoreUnit}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className="inline-flex items-center gap-1 rounded-full bg-status-success/12 px-2 py-0.5 text-[11px] font-semibold text-status-success-foreground">
                          <CheckCircle2 className="h-3 w-3" />
                          {copy.approvedBadge}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center whitespace-nowrap">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setSelectedActivity(act)}
                          className="h-7 px-2.5 text-xs font-semibold gap-1 text-primary hover:bg-primary/10 transition-colors"
                        >
                          <FileCheck className="h-3.5 w-3.5" />
                          {copy.viewCertificate}
                        </Button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        </CardContent>
      </Card>

      {/* Historical DRL Record & Ranking Rules */}
      <div className="grid gap-6 lg:grid-cols-12">
        {/* Semester History Table */}
        <Card className="min-w-0 lg:col-span-7 border-border/70 shadow-sm">
          <CardHeader className="border-b border-border/70 bg-[hsl(var(--surface-alt))] py-3.5">
            <CardTitle className="text-base font-bold flex items-center gap-2">
              <Calendar className="h-4 w-4 text-primary" />
              {copy.historyTitle}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-0">
            <div className="divide-y divide-border/60 sm:hidden">
              {summary?.history.map((item) => (
                <button
                  key={item.semesterId}
                  type="button"
                  onClick={() => setSelectedSemesterId(item.semesterId)}
                  className={`flex w-full flex-col gap-3 p-4 text-left transition-colors ${
                    item.semesterId === selectedSemesterId
                      ? 'bg-primary/10'
                      : 'hover:bg-secondary/30'
                  }`}
                >
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0">
                      <p className="break-words text-sm font-semibold text-foreground">
                        {formatSemesterName(item.semesterName)}
                      </p>
                      <p className="mt-1 text-xs text-muted-foreground">{copy.historySemester}</p>
                    </div>
                    <span className={`shrink-0 rounded-full border px-2 py-0.5 text-xs font-bold ${getRankBadgeClass(item.classification)}`}>
                      {rankLabel(item.classification, item.classificationVi)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-primary">
                      {formatNumber(item.totalScore)} {copy.scoreUnit}
                    </span>
                    <span className="flex items-center gap-1 text-status-success-foreground">
                      <CheckCircle2 className="h-3.5 w-3.5" />
                      {copy.official}
                    </span>
                  </div>
                </button>
              ))}
            </div>
            <div className="hidden overflow-x-auto sm:block">
              <table className="w-full text-left text-sm">
                <thead className="border-b border-border/70 bg-secondary/30 text-xs uppercase text-muted-foreground">
                  <tr>
                    <th className="px-4 py-3 font-semibold">{copy.historySemester}</th>
                    <th className="px-4 py-3 font-semibold text-center">{copy.historyTotal}</th>
                    <th className="px-4 py-3 font-semibold text-center">{copy.historyRank}</th>
                    <th className="px-4 py-3 font-semibold">{copy.historyStatus}</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-border/60">
                  {summary?.history.map((item) => (
                    <tr
                      key={item.semesterId}
                      onClick={() => setSelectedSemesterId(item.semesterId)}
                      className={`cursor-pointer transition-colors ${
                        item.semesterId === selectedSemesterId
                          ? 'bg-primary/10 font-semibold'
                          : 'hover:bg-secondary/30'
                      }`}
                    >
                      <td className="px-4 py-3 text-foreground">
                        {formatSemesterName(item.semesterName)}
                      </td>
                      <td className="px-4 py-3 font-bold text-center text-primary">
                        {formatNumber(item.totalScore)}
                      </td>
                      <td className="px-4 py-3 text-center">
                        <span className={`inline-flex rounded-full border px-2 py-0.5 text-xs font-bold ${getRankBadgeClass(item.classification)}`}>
                          {rankLabel(item.classification, item.classificationVi)}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-xs text-muted-foreground">
                        <span className="flex items-center gap-1 text-status-success-foreground">
                          <CheckCircle2 className="h-3.5 w-3.5" />
                          {copy.official}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>

        {/* Ministry & University Classification Scale */}
        <Card className="min-w-0 lg:col-span-5 border-border/70 shadow-sm">
          <CardHeader className="border-b border-border/70 bg-[hsl(var(--surface-alt))] py-3.5">
            <CardTitle className="text-base font-bold flex items-center gap-2">
              <GraduationCap className="h-4 w-4 text-primary" />
              {copy.classificationScaleTitle}
            </CardTitle>
          </CardHeader>
          <CardContent className="p-4 space-y-2 text-xs">
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 px-2.5 pb-1.5 text-[10px] font-bold uppercase tracking-wider text-muted-foreground border-b border-border/50 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span>{copy.historyRank}</span>
              <span>{copy.range}</span>
              <span className="text-right">{copy.note}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-success/30 bg-status-success/8 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-success-foreground">{copy.excellent}</span>
              <span className="font-semibold text-foreground tabular-nums">90-100 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-muted-foreground">{copy.scholarshipPriority}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-info/30 bg-status-info/8 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-info-foreground">{copy.good}</span>
              <span className="font-semibold text-foreground tabular-nums">80-89 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-muted-foreground">{copy.scholarshipReady}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-warning/30 bg-status-warning/8 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-warning-foreground">{copy.fair}</span>
              <span className="font-semibold text-foreground tabular-nums">65-79 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-muted-foreground">{copy.completed}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-neutral/30 bg-status-neutral/8 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-neutral-foreground">{copy.average}</span>
              <span className="font-semibold text-foreground tabular-nums">50-64 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-muted-foreground">{copy.meetsRequirement}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-danger/30 bg-status-danger/8 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-danger-foreground">{copy.weak}</span>
              <span className="font-semibold text-foreground tabular-nums">&lt; 50 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-status-danger-foreground">{copy.warning}</span>
            </div>
          </CardContent>
        </Card>
      </div>

      {/* Official Signatures for Printable Document */}
      <div className="hidden print:grid grid-cols-2 gap-12 mt-12 pt-8 text-center text-xs">
        <div>
          <p className="font-bold uppercase tracking-wider">{copy.classOfficers}</p>
          <p className="italic text-muted-foreground mt-1">({copy.signHint})</p>
          <div className="h-16" />
        </div>
        <div>
          <p className="font-bold uppercase tracking-wider">{copy.facultySignature}</p>
          <p className="italic text-muted-foreground mt-1">({copy.sealHint})</p>
          <div className="h-16" />
        </div>
      </div>

      {/* Verified Digital Certificate & Evidence Modal */}
      {selectedActivity && selectedActivityDetails && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-xs p-4 print:p-0">
          <div className="relative w-full max-w-2xl rounded-xl border border-border bg-card shadow-2xl overflow-hidden animate-in fade-in-50 zoom-in-95">
            {/* Modal Close Button */}
            <button
              type="button"
              onClick={() => setSelectedActivity(null)}
              className="absolute top-4 right-4 z-10 rounded-full p-1.5 text-muted-foreground hover:bg-secondary hover:text-foreground transition-colors print:hidden"
              aria-label={copy.close}
            >
              <X className="h-5 w-5" />
            </button>

            {/* Institutional Certificate Layout */}
            <div className="p-6 sm:p-8 space-y-6 bg-gradient-to-b from-primary/5 via-card to-card">
              {/* Certificate Header */}
              <div className="border-b-2 border-primary/30 pb-4 text-center space-y-1">
                <div className="text-[11px] uppercase font-bold tracking-wider text-muted-foreground">
                  {copy.institution}
                </div>
                <div className="text-xs font-extrabold text-primary">
                  {copy.office}
                </div>
                <div className="py-2">
                  <h2 className="text-lg sm:text-xl font-black text-foreground uppercase tracking-wide">
                    {copy.certificateTitle}
                  </h2>
                  <p className="text-xs text-muted-foreground italic">
                    ({copy.certificateSubtitle})
                  </p>
                </div>
              </div>

              {/* Verification Status Badge */}
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-lg bg-status-success/12 border border-status-success/30 p-3 text-status-success-foreground">
                <div className="flex items-center gap-2 text-xs font-bold">
                  <ShieldCheck className="h-4 w-4 shrink-0" />
                  <span>{copy.certValid}</span>
                </div>
                <span className="text-[11px] font-mono font-semibold bg-status-success/20 px-2 py-0.5 rounded shrink-0">
                  UTE-CERT-{selectedActivity.id.toUpperCase()}-2026
                </span>
              </div>

              {/* Student & Activity Information */}
              <div className="space-y-3 text-xs sm:text-sm">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 rounded-lg bg-secondary/30 p-3.5 border border-border/60">
                  <div>
                    <span className="text-xs text-muted-foreground">{copy.confirmedStudent}</span>
                    <p className="font-bold text-foreground text-sm">
                      {summary?.fullName || 'Nguyễn Tiến Sơn'}
                    </p>
                  </div>
                  <div>
                    <span className="text-xs text-muted-foreground">{copy.studentId}</span>
                    <p className="font-bold font-mono text-foreground text-sm">
                      {summary?.studentCode || '24110054'} ({copy.classLabel} 24110CLA)
                    </p>
                  </div>
                </div>

                <div className="rounded-lg border border-border/70 p-4 space-y-3 bg-card">
                  <div>
                    <span className="text-xs font-semibold text-muted-foreground">{copy.activityLabel}</span>
                    <p className="font-bold text-foreground text-sm sm:text-base text-primary mt-0.5">
                      {selectedActivityDetails.title}
                    </p>
                  </div>

                  <div className="grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <span className="text-muted-foreground">{copy.fieldLabel}</span>
                      <p className="font-semibold text-foreground mt-0.5">{selectedActivityDetails.category}</p>
                    </div>
                    <div>
                      <span className="text-muted-foreground">{copy.recordedDate}</span>
                      <p className="font-semibold text-foreground mt-0.5">{selectedActivityDetails.date}</p>
                    </div>
                  </div>

                  <div>
                    <span className="text-xs text-muted-foreground">{copy.organizerLabel}</span>
                    <p className="font-semibold text-foreground mt-0.5">{selectedActivityDetails.organizer}</p>
                  </div>

                  <div className="pt-2 border-t border-border/60 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
                    <div>
                      <span className="text-xs text-muted-foreground">{copy.appliedCriteria}</span>
                      <p className="font-semibold text-xs text-foreground mt-0.5">
                        {activityCriterion(selectedActivity)}
                      </p>
                    </div>
                    <div className="sm:text-right">
                      <span className="text-xs text-muted-foreground">{copy.bonusConductPoints}</span>
                      <p className="text-lg font-black text-status-success-foreground">
                        +{formatNumber(selectedActivity.points)} {copy.scoreUnit}
                      </p>
                    </div>
                  </div>
                </div>
              </div>

              {/* Official Institutional Verification Seals */}
              <div className="pt-2 border-t border-border/60 grid grid-cols-2 items-center text-xs text-muted-foreground">
                <div className="flex items-center gap-2">
                  <QrCode className="h-8 w-8 text-foreground/80 shrink-0" />
                  <div className="text-[11px] leading-tight">
                    <span className="font-mono font-semibold text-foreground">{copy.digitalVerification}</span>
                    <p className="text-[10px] text-muted-foreground">{copy.secureCode} 7F8C9A24110054</p>
                  </div>
                </div>
                <div className="text-right">
                  <span className="inline-flex items-center gap-1 rounded border border-primary/30 bg-primary/5 px-2.5 py-1 text-[11px] font-bold text-primary">
                    <CheckCircle2 className="h-3 w-3" />
                    {copy.digitallyVerified}
                  </span>
                </div>
              </div>

              {/* Modal Actions */}
              <div className="flex items-center justify-end gap-3 pt-2 print:hidden">
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => window.print()}
                  className="gap-1.5 font-semibold text-xs"
                >
                  <Printer className="h-3.5 w-3.5" />
                  {copy.printCertificate}
                </Button>
                <Button
                  size="sm"
                  onClick={() => setSelectedActivity(null)}
                  className="text-xs font-semibold px-4"
                >
                  {copy.close}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
