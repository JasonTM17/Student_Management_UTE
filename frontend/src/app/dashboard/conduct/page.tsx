'use client';

import { useEffect, useMemo, useState } from 'react';
import {
  Award,
  Calendar,
  CheckCircle2,
  ChevronRight,
  FileCheck,
  GraduationCap,
  HeartHandshake,
  HelpCircle,
  Info,
  Layers,
  Printer,
  ShieldCheck,
  Sparkles,
  TrendingUp,
  Users,
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
import { Modal } from '@/components/ui/modal';
import { PageHeader, SectionEyebrow } from '@/components/ui/page-header';
import { LoadingState, ErrorState } from '@/components/ui/state-block';
import { WorkspaceForbiddenState } from '@/components/ProtectedRoute';

const conductCopy = {
  en: {
    institution: 'Ho Chi Minh City University of Technology and Engineering',
    office: 'Student Affairs Office • CampusUTE',
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
    viewCertificates: 'View certificates',
    chooseSemester: 'Semester:',
    optionScoreUnit: 'pts',
    info: 'Conduct score supports scholarship, awards, and graduation review.',
    criteriaTitle: '5 conduct criteria',
    criteriaEmpty: 'Criterion details have not been published for this semester yet.',
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
    notClassified: 'Not yet classified',
    statusPending: 'Pending approval',
    statusUnknown: 'Unknown',
    scholarshipBelow: 'Below requirement',
    recordBadge: 'Recorded in the conduct file',
    previewTitle: 'Activity record — unofficial preview',
    previewNote:
      'This is an on-screen preview of a recorded activity, not an issued certificate. Official confirmation letters are issued by the Student Affairs Office.',
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
    certificateTitle: 'CONDUCT ACTIVITY RECORD (PREVIEW)',
    certificateSubtitle: 'Recorded evidence entry — not an issued certificate',
    confirmedStudent: 'Confirmed student:',
    studentId: 'Student ID:',
    classLabel: 'Class:',
    activityLabel: 'Activity:',
    fieldLabel: 'Field:',
    recordedDate: 'Recorded date:',
    organizerLabel: 'Organizer:',
    bonusConductPoints: 'Bonus conduct points:',
    printCertificate: 'Print record',
    close: 'Close',
    loadErrorTitle: 'Could not load conduct data',
  },
  vi: {
    institution: 'TRƯỜNG ĐẠI HỌC CÔNG NGHỆ KỸ THUẬT TP. HỒ CHÍ MINH',
    office: 'PHÒNG CÔNG TÁC SINH VIÊN • CAMPUSUTE',
    printTitle: 'PHIẾU ĐÁNH GIÁ KẾT QUẢ RÈN LUYỆN SINH VIÊN',
    pageEyebrow: 'HCM-UTE • PHÒNG CTSV',
    pageTitle: 'Điểm rèn luyện',
    pageDescription: 'Theo dõi điểm ĐRL, 5 tiêu chí đánh giá và hoạt động được duyệt bằng bố cục gọn hơn.',
    exportReport: 'Xuất phiếu rèn luyện',
    selectedSemester: 'Học kỳ đang chọn',
    currentSemester: 'Học kỳ đang chọn',
    pointsOutOf: '/ 100 điểm',
    status: 'Trạng thái:',
    approved: 'Đã phê duyệt',
    evaluator: 'Người duyệt:',
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
    viewCertificates: 'Xem chứng nhận',
    chooseSemester: 'Học kỳ:',
    optionScoreUnit: 'điểm',
    info: 'Điểm rèn luyện dùng để xét học bổng, khen thưởng và điều kiện tốt nghiệp.',
    criteriaTitle: '5 tiêu chí rèn luyện',
    criteriaEmpty: 'Chi tiết từng tiêu chí chưa được công bố cho học kỳ này.',
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
    notClassified: 'Chưa xếp loại',
    statusPending: 'Chờ phê duyệt',
    statusUnknown: 'Chưa rõ',
    scholarshipBelow: 'Chưa đạt chuẩn',
    recordBadge: 'Đã ghi nhận trong hồ sơ rèn luyện',
    previewTitle: 'Hồ sơ hoạt động — bản xem trước',
    previewNote:
      'Đây là bản xem trước của hoạt động đã ghi nhận, không phải chứng nhận được cấp. Giấy xác nhận chính thức do Phòng Công tác Sinh viên phát hành.',
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
    certificateTitle: 'HỒ SƠ HOẠT ĐỘNG RÈN LUYỆN (BẢN XEM TRƯỚC)',
    certificateSubtitle: 'Bản ghi minh chứng đã lưu — không phải chứng nhận được cấp',
    confirmedStudent: 'Sinh viên xác nhận:',
    studentId: 'Mã số sinh viên:',
    classLabel: 'Lớp:',
    activityLabel: 'Hoạt động:',
    fieldLabel: 'Lĩnh vực:',
    recordedDate: 'Ngày ghi nhận:',
    organizerLabel: 'Đơn vị tổ chức:',
    bonusConductPoints: 'Điểm cộng rèn luyện:',
    printCertificate: 'In bản ghi',
    close: 'Đóng',
    loadErrorTitle: 'Không thể tải dữ liệu điểm rèn luyện',
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

export default function StudentConductPage() {
  // /conduct/my is hasRole('STUDENT')-only on the backend: a staff member has
  // no "my conduct", so admitting ADMIN/SUPER_ADMIN here only bought them a
  // load error plus a full-page reload instead of the forbidden screen.
  const { user, isLoading: authLoading, hasAccess, isForbidden } = useRequireAuth(['STUDENT']);
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
        // A failed load must never be papered over with an invented training
        // record: the student sees the real failure and can retry.
        setSummary(null);
        setError(copy.loadErrorTitle);
      } finally {
        if (mounted) setLoading(false);
      }
    }

    void loadConduct();
    return () => {
      mounted = false;
    };
  }, [copy.loadErrorTitle, hasAccess]);

  const activeSemesterScore = useMemo(() => {
    if (!summary) return null;
    const match = (summary.currentSemester && summary.currentSemester.semesterId === selectedSemesterId)
      ? summary.currentSemester
      : (summary.history.find((s) => s.semesterId === selectedSemesterId) || summary.currentSemester);
    if (!match) return null;
    return match;
  }, [summary, selectedSemesterId]);

  const formatSemesterName = (name: string | null | undefined) => {
    if (!name) return '—';
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
    // `fallback` is the raw classification text the API stored, so it is safe
    // to show; anything genuinely unrecognized gets a neutral label, never a
    // passing one.
    return fallback || copy.notClassified;
  };

  // The API reports a per-semester status; the UI must not claim an approval
  // the row does not carry. Unknown or blank values stay neutral.
  const statusView = (status: string | null | undefined) => {
    const normalized = (status || '').trim().toUpperCase();
    if (normalized === 'APPROVED') {
      return { label: copy.approved, approved: true as const };
    }
    if (normalized === 'PENDING' || normalized === 'IN_REVIEW' || normalized === 'DRAFT') {
      return { label: copy.statusPending, approved: false as const };
    }
    return { label: copy.statusUnknown, approved: false as const };
  };

  const criterionName = (criterion: ConductSemesterScore['criteria'][number]) =>
    vi ? criterion.nameVi : (criterion.nameEn || criterion.nameVi);

  const criterionDescription = (criterion: ConductSemesterScore['criteria'][number]) =>
    criterion.description
    || conductCriteriaDescriptions[locale][criterion.code as keyof typeof conductCriteriaDescriptions.en]
    || '—';

  const activityDetails = (activity: ConductActivity) => ({
    title: activity.title,
    category: activity.category,
    organizer: activity.organizer,
    date: formatDate(activity.activityDate),
  });

  if (authLoading || loading) {
    return <LoadingState label={messages.common.states.loadingContent} />;
  }

  if (isForbidden || !hasAccess) {
    return <WorkspaceForbiddenState signedIn={Boolean(user)} />;
  }

  if (error && !summary) {
    return <ErrorState title={copy.loadErrorTitle} description={error} onRetry={() => window.location.reload()} />;
  }

  const getRankBadgeClass = (rank: string | null | undefined) => {
    switch (rank?.toUpperCase()) {
      case 'XUAT_SAC':
      case 'XUẤT SẮC':
        return 'bg-status-success/15 text-status-success-foreground border-status-success/30';
      case 'TOT':
      case 'TỐT':
        return 'bg-status-info/15 text-status-info-foreground border-status-info/30';
      case 'KHA':
      case 'KHÁ':
        return 'bg-status-warning/15 text-status-warning-foreground border-status-warning/30';
      default:
        return 'bg-status-neutral/15 text-status-neutral-foreground border-status-neutral/30';
    }
  };

  const handleExportConductReport = () => {
    // No success toast here: the browser print dialog can still be cancelled,
    // and a cancelled print must not be reported as a produced document.
    window.print();
  };

  const selectedActivityDetails = selectedActivity ? activityDetails(selectedActivity) : null;
  const semesterStatus = statusView(activeSemesterScore?.status);

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
          <span><strong>{copy.totalScore}:</strong> {activeSemesterScore ? formatNumber(activeSemesterScore.totalScore) : '—'} / 100</span>
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
              <Printer className="h-4 w-4 text-primary" />
              <span>{copy.exportReport}</span>
            </Button>
          }
        />
      </div>

      {/* Top 3 KPI Cards — screen-only: "Export conduct report" prints the
          official header, the scores it carries, and the signatures block. */}
      <div className="grid gap-4 print:hidden sm:grid-cols-2 lg:grid-cols-3">
        {/* Card 1: Selected Semester Conduct Score */}
        <Card className="relative overflow-hidden border-border/70 shadow-sm bg-gradient-to-br from-card to-secondary/30">
          <CardHeader className="pb-2">
            <div className="flex items-center justify-between">
              <span className="text-xs font-semibold uppercase tracking-wider text-muted-foreground flex items-center gap-1.5">
                <Award className="h-4 w-4 text-primary" />
                {copy.currentSemester}
              </span>
              <span className={`inline-flex items-center gap-1 rounded-md border px-2.5 py-0.5 text-xs font-bold ${getRankBadgeClass(activeSemesterScore?.classification)}`}>
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
                {activeSemesterScore ? formatNumber(activeSemesterScore.totalScore) : '—'}
              </span>
              <span className="text-sm font-medium text-muted-foreground">{copy.pointsOutOf}</span>
            </div>
            <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground border-t border-border/50 pt-2">
              <span>
                {copy.status}{' '}
                <strong className={semesterStatus.approved ? 'text-status-success-foreground' : 'text-muted-foreground'}>
                  {semesterStatus.label}
                </strong>
              </span>
              <span>{copy.evaluator} {activeSemesterScore?.evaluatorName || '—'}</span>
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
              <span className="rounded-md bg-primary/10 px-2.5 py-0.5 text-xs font-semibold text-primary">
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
                {summary ? formatNumber(summary.cumulativeAverageScore) : '—'}
              </span>
              <span className="text-sm font-medium text-muted-foreground">/ 100</span>
              <span className="ml-2 rounded-md bg-primary/10 px-2 py-0.5 text-xs font-semibold text-primary">
                {copy.rankPrefix} {rankLabel(summary?.cumulativeClassificationVi, summary?.cumulativeClassificationVi)}
              </span>
            </div>
            <div className="mt-3 flex items-center justify-between text-xs text-muted-foreground border-t border-border/50 pt-2">
              {/* Scholarship eligibility is asserted only when the reported
                  cumulative score actually clears the 70-point threshold. */}
              {(() => {
                const cumulativeScore = summary?.cumulativeAverageScore;
                if (cumulativeScore == null) {
                  return (
                    <span>
                      {copy.scholarshipStatus} <strong className="text-muted-foreground">—</strong>
                    </span>
                  );
                }
                return cumulativeScore >= 70 ? (
                  <span>
                    {copy.scholarshipStatus}{' '}
                    <strong className="text-status-success-foreground">{copy.eligible}</strong>
                  </span>
                ) : (
                  <span>
                    {copy.scholarshipStatus}{' '}
                    <strong className="text-muted-foreground">{copy.scholarshipBelow}</strong>
                  </span>
                );
              })()}
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
              <span className="rounded-md bg-status-warning/15 px-2.5 py-0.5 text-xs font-bold text-status-warning-foreground">
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
              <span>
                {copy.status}{' '}
                <strong className={semesterStatus.approved ? 'text-foreground' : 'text-muted-foreground'}>
                  {semesterStatus.label}
                </strong>
              </span>
              <button
                type="button"
                onClick={() => {
                  document.getElementById('conduct-activities-section')?.scrollIntoView({ behavior: 'smooth' });
                }}
                className="inline-flex min-h-8 items-center text-primary font-medium cursor-pointer hover:underline focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                {copy.viewCertificates}
              </button>
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

      <div className="space-y-4 print:hidden">
        <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
          <h3 className="text-base font-bold text-foreground flex items-center gap-2">
            <Layers className="h-5 w-5 text-primary" />
            {copy.criteriaTitle}
          </h3>
          <span className="text-xs font-semibold text-muted-foreground">
            {copy.earned}: <strong className="text-primary text-sm">{activeSemesterScore ? formatNumber(activeSemesterScore.totalScore) : '—'}</strong> / 100
          </span>
        </div>

        <div className="grid gap-3">
          {(activeSemesterScore?.criteria || []).length === 0 && (
            <p className="rounded-lg border border-dashed border-border/70 bg-secondary/30 px-4 py-3 text-sm text-muted-foreground">
              {copy.criteriaEmpty}
            </p>
          )}
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

      <Card id="conduct-activities-section" className="border-border/70 shadow-sm overflow-hidden scroll-mt-6 print:hidden">
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
                    <span className="shrink-0 rounded-md bg-status-success/15 px-2 py-0.5 text-xs font-bold text-status-success-foreground">
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
                      <dd className={semesterStatus.approved ? 'font-medium text-status-success-foreground' : 'font-medium text-muted-foreground'}>
                        {semesterStatus.approved ? copy.approvedBadge : semesterStatus.label}
                      </dd>
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
                        <span className={`inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-[11px] font-semibold ${semesterStatus.approved ? 'bg-status-success/15 text-status-success-foreground' : 'bg-status-neutral/15 text-status-neutral-foreground'}`}>
                          {semesterStatus.approved ? (
                            <CheckCircle2 className="h-3 w-3" />
                          ) : (
                            <HelpCircle className="h-3 w-3" />
                          )}
                          {semesterStatus.approved ? copy.approvedBadge : semesterStatus.label}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-center whitespace-nowrap">
                        <Button
                          variant="outline"
                          size="sm"
                          onClick={() => setSelectedActivity(act)}
                          className="h-8 px-2.5 text-xs font-semibold gap-1 text-primary hover:bg-primary/10 transition-colors"
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

      {/* Historical DRL Record & Ranking Rules — screen-only reference */}
      <div className="grid gap-6 lg:grid-cols-12 print:hidden">
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
                    <span className={`shrink-0 rounded-md border px-2 py-0.5 text-xs font-bold ${getRankBadgeClass(item.classification)}`}>
                      {rankLabel(item.classification, item.classificationVi)}
                    </span>
                  </div>
                  <div className="flex items-center justify-between text-xs">
                    <span className="font-bold text-primary">
                      {formatNumber(item.totalScore)} {copy.scoreUnit}
                    </span>
                    {(() => {
                      const status = statusView(item.status);
                      return (
                        <span className={`flex items-center gap-1 ${status.approved ? 'text-status-success-foreground' : 'text-muted-foreground'}`}>
                          {status.approved ? (
                            <CheckCircle2 className="h-3.5 w-3.5" />
                          ) : (
                            <HelpCircle className="h-3.5 w-3.5" />
                          )}
                          {status.approved ? copy.official : status.label}
                        </span>
                      );
                    })()}
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
                  {summary?.history.map((item) => {
                    const status = statusView(item.status);
                    return (
                      <tr
                        key={item.semesterId}
                        onClick={() => setSelectedSemesterId(item.semesterId)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            setSelectedSemesterId(item.semesterId);
                          }
                        }}
                        role="button"
                        tabIndex={0}
                        aria-pressed={item.semesterId === selectedSemesterId}
                        className={`cursor-pointer transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-inset ${
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
                          <span className={`inline-flex rounded-md border px-2 py-0.5 text-xs font-bold ${getRankBadgeClass(item.classification)}`}>
                            {rankLabel(item.classification, item.classificationVi)}
                          </span>
                        </td>
                        <td className="px-4 py-3 text-xs text-muted-foreground">
                          <span className={`flex items-center gap-1 ${status.approved ? 'text-status-success-foreground' : 'text-muted-foreground'}`}>
                            {status.approved ? (
                              <CheckCircle2 className="h-3.5 w-3.5" />
                            ) : (
                              <HelpCircle className="h-3.5 w-3.5" />
                            )}
                            {status.approved ? copy.official : status.label}
                          </span>
                        </td>
                      </tr>
                    );
                  })}
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
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-success/30 bg-status-success/10 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-success-foreground">{copy.excellent}</span>
              <span className="font-semibold text-foreground tabular-nums">90-100 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-muted-foreground">{copy.scholarshipPriority}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-info/30 bg-status-info/10 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-info-foreground">{copy.good}</span>
              <span className="font-semibold text-foreground tabular-nums">80-89 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-muted-foreground">{copy.scholarshipReady}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-warning/30 bg-status-warning/10 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-warning-foreground">{copy.fair}</span>
              <span className="font-semibold text-foreground tabular-nums">65-79 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-muted-foreground">{copy.completed}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-neutral/30 bg-status-neutral/10 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
              <span className="break-words font-bold text-status-neutral-foreground">{copy.average}</span>
              <span className="font-semibold text-foreground tabular-nums">50-64 {copy.scoreUnit}</span>
              <span className="break-words text-right text-[11px] text-muted-foreground">{copy.meetsRequirement}</span>
            </div>
            <div className="grid grid-cols-[4.25rem_5.5rem_1fr] items-center gap-2 rounded-md border border-status-danger/30 bg-status-danger/10 px-2.5 py-2 sm:grid-cols-[5rem_7.5rem_1fr]">
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

      {/* Recorded Activity Preview (honest, unissued record — not a certificate) */}
      {selectedActivity && selectedActivityDetails && (
        <Modal
          isOpen
          onClose={() => setSelectedActivity(null)}
          title={copy.previewTitle}
          className="max-w-2xl"
          printable
        >
        <div className="space-y-5 print:p-0">
          {/* Institutional Record Layout */}
          <div className="space-y-5 bg-gradient-to-b from-primary/5 via-card to-card rounded-lg p-4 sm:p-6">
            {/* Record Header */}
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

            {/* Record Status Badge — bound to the status the API reported, and
                never asserting a validity no system granted */}
            <div className={`flex flex-col sm:flex-row sm:items-center justify-between gap-2 rounded-lg border p-3 text-xs font-bold ${semesterStatus.approved ? 'bg-status-success/15 border-status-success/30 text-status-success-foreground' : 'bg-status-neutral/15 border-status-neutral/30 text-status-neutral-foreground'}`}>
              <div className="flex items-center gap-2">
                {semesterStatus.approved ? (
                  <ShieldCheck className="h-4 w-4 shrink-0" />
                ) : (
                  <HelpCircle className="h-4 w-4 shrink-0" />
                )}
                <span>{semesterStatus.approved ? copy.recordBadge : semesterStatus.label}</span>
              </div>
            </div>

            {/* Student & Activity Information */}
            <div className="space-y-3 text-xs sm:text-sm">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 rounded-lg bg-secondary/30 p-3.5 border border-border/60">
                <div>
                  <span className="text-xs text-muted-foreground">{copy.confirmedStudent}</span>
                  <p className="font-bold text-foreground text-sm">
                    {summary?.fullName || '—'}
                  </p>
                </div>
                <div>
                  <span className="text-xs text-muted-foreground">{copy.studentId}</span>
                  <p className="font-bold font-mono text-foreground text-sm">
                    {summary?.studentCode || '—'}
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

                <div className="pt-2 border-t border-border/60 sm:text-right">
                  <span className="text-xs text-muted-foreground">{copy.bonusConductPoints}</span>
                  <p className="text-lg font-black text-status-success-foreground">
                    +{formatNumber(selectedActivity.points)} {copy.scoreUnit}
                  </p>
                </div>
              </div>
            </div>

            {/* Honest preview note (no digital-verification seal is claimed) */}
            <p className="rounded-lg border border-dashed border-border/80 bg-muted/40 px-3 py-2 text-[11px] leading-relaxed text-muted-foreground">
              {copy.previewNote}
            </p>

            {/* Modal Actions */}
            <div className="flex items-center justify-end gap-3 pt-1 print:hidden">
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
        </Modal>
      )}
    </div>
  );
}
